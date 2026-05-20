package com.ssafy.smartcane.crosswalk

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.ssafy.smartcane.R
import com.ssafy.smartcane.ble.BleNusManager
import com.ssafy.smartcane.network.NearestTrafficLight
import com.ssafy.smartcane.network.TrafficLightApiService
import com.ssafy.smartcane.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.*

class CrosswalkPipeline(
    private val context: Context,
    private val bleNusManager: BleNusManager,
    private val speechOutput: SpeechOutput
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile var currentHeading: Float = 0f
    @Volatile private var isRunning = false
    private var lastTriggerMs = 0L

    // 마지막으로 조회한 신호등 (onSignalDetected에서 타이밍 안내에 재사용)
    @Volatile private var lastFetchedLight: NearestTrafficLight? = null

    // 신호 전환 타이머
    private var signalTimerJob: kotlinx.coroutines.Job? = null

    // 녹색 신호 음향 재생
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "CrosswalkPipeline"
        private const val COOLDOWN_MS = 15_000L
        private const val GPS_SAMPLE_COUNT = 5
        private const val GPS_SAMPLE_INTERVAL_MS = 800L
        private const val GPS_OUTLIER_THRESHOLD_M = 15.0
    }

    // 신호 상태 변경 시에만 발화
    private var lastSignalState: Boolean? = null
    private var lastSignalDetectedMs = 0L
    private val SIGNAL_RESET_MS = 20_000L

    fun onSignalDetected(green: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastSignalDetectedMs > SIGNAL_RESET_MS) lastSignalState = null
        lastSignalDetectedMs = now
        if (green == lastSignalState) return
        lastSignalState = green

        val light = lastFetchedLight
        val timing = light?.let { calculateSignalInfo(it) }
        val text = if (timing != null) {
            if (green) "녹색 신호입니다. 약 ${timing.remainingSec}초 남았습니다."
            else "적색 신호입니다. 약 ${timing.remainingSec}초 후 녹색으로 바뀝니다."
        } else {
            if (green) "녹색 신호입니다. 건너세요." else "적색 신호입니다. 기다리세요."
        }
        speechOutput.speak(text)
    }

    fun onCrosswalkDetected() {
        val now = System.currentTimeMillis()
        if (isRunning || now - lastTriggerMs < COOLDOWN_MS) return
        isRunning = true
        lastTriggerMs = now

        scope.launch {
            try {
                runPipeline()
            } catch (e: Exception) {
                Log.e(TAG, "파이프라인 오류", e)
            } finally {
                isRunning = false
            }
        }
    }

    fun destroy() {
        signalTimerJob?.cancel()
        stopGreenSound()
        scope.cancel()
    }

    private fun playGreenSound() {
        stopGreenSound()
        mediaPlayer = MediaPlayer.create(context, R.raw.pedestrian)?.apply {
            isLooping = false
            setOnCompletionListener {
                scope.launch {
                    delay(1000L)
                    if (mediaPlayer != null) playGreenSound()
                }
            }
            start()
        }
    }

    private fun stopGreenSound() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private suspend fun runPipeline() {
        val samples = collectGpsSamples()
        val stableLoc = removeOutliersAndAverage(samples) ?: run {
            Log.w(TAG, "안정적인 GPS 위치를 얻을 수 없습니다")
            return
        }

        val lights = TrafficLightApiService.fetchNearest(stableLoc.first, stableLoc.second)
        if (lights.isEmpty()) {
            Log.d(TAG, "10m 이내 신호등 없음")
            return
        }

        val light = lights.firstOrNull()
        lastFetchedLight = light

        val guidance = buildGuidance(light)
        speechOutput.speak(guidance)
        Log.d(TAG, "음성 안내: $guidance")

        // 타이밍 데이터 있으면 신호 전환 자동 알림 스케줄
        val timing = light?.let { calculateSignalInfo(it) }
        if (light != null && timing != null) {
            if (timing.isGreen) playGreenSound() else stopGreenSound()
            scheduleSignalAlerts(light, timing)
        }
    }

    private fun buildGuidance(light: NearestTrafficLight?): String {
        val routeName = light?.roadRouteName
        val timing = light?.let { calculateSignalInfo(it) }

        val prefix = if (!routeName.isNullOrBlank()) "${routeName} 횡단보도입니다." else "근처에 횡단보도가 있습니다."

        return if (timing != null) {
            val signalText = if (timing.isGreen) {
                "현재 녹색 신호입니다. 약 ${timing.remainingSec}초 남았습니다."
            } else {
                "현재 적색 신호입니다. 약 ${timing.remainingSec}초 후 녹색으로 바뀝니다."
            }
            "$prefix $signalText"
        } else {
            prefix
        }
    }

    // 신호 타이밍 계산: lighting_sequence = "적색초|HH:MM:SS"
    private fun calculateSignalInfo(light: NearestTrafficLight): SignalInfo? {
        val greenSec = light.lightingDuration ?: return null
        val sequence = light.lightingSequence ?: return null

        val parts = sequence.split("|")
        if (parts.size != 2) return null
        val redSec = parts[0].toIntOrNull() ?: return null
        val timeParts = parts[1].split(":")
        if (timeParts.size != 3) return null

        val refH = timeParts[0].toIntOrNull() ?: return null
        val refM = timeParts[1].toIntOrNull() ?: return null
        val refS = timeParts[2].toIntOrNull() ?: return null
        val refSecondsOfDay = refH * 3600 + refM * 60 + refS

        val cal = Calendar.getInstance()
        val nowSecondsOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                              cal.get(Calendar.MINUTE) * 60 +
                              cal.get(Calendar.SECOND)

        val cycle = greenSec + redSec
        val elapsed = ((nowSecondsOfDay - refSecondsOfDay) % cycle + cycle) % cycle

        return if (elapsed < greenSec) {
            SignalInfo(isGreen = true, remainingSec = greenSec - elapsed)
        } else {
            SignalInfo(isGreen = false, remainingSec = cycle - elapsed)
        }
    }

    private data class SignalInfo(val isGreen: Boolean, val remainingSec: Int)

    // 잔여시간 후 신호 전환 알림 → 이후 계속 사이클 반복
    private fun scheduleSignalAlerts(light: NearestTrafficLight, timing: SignalInfo) {
        signalTimerJob?.cancel()
        val greenSec = light.lightingDuration ?: return
        val parts = light.lightingSequence?.split("|") ?: return
        val redSec = parts[0].toIntOrNull() ?: return

        signalTimerJob = scope.launch {
            var isGreen = timing.isGreen
            var remaining = timing.remainingSec

            while (true) {
                delay(remaining * 1000L)
                isGreen = !isGreen
                remaining = if (isGreen) greenSec else redSec
                val text = if (isGreen) {
                    "녹색 신호로 바뀌었습니다. 약 ${greenSec}초입니다."
                } else {
                    "적색 신호로 바뀌었습니다. 약 ${redSec}초 기다려주세요."
                }
                speechOutput.speak(text)
                if (isGreen) playGreenSound() else stopGreenSound()
            }
        }
    }

    // ── GPS 수집 ──────────────────────────────────────────────────────────

    private suspend fun collectGpsSamples(): List<Pair<Double, Double>> {
        val samples = mutableListOf<Pair<Double, Double>>()
        repeat(GPS_SAMPLE_COUNT) {
            LocationHelper.getLastKnownLocation(context)?.let { samples.add(it) }
            delay(GPS_SAMPLE_INTERVAL_MS)
        }
        return samples
    }

    private fun removeOutliersAndAverage(samples: List<Pair<Double, Double>>): Pair<Double, Double>? {
        if (samples.isEmpty()) return null
        if (samples.size == 1) return samples[0]

        val meanLat = samples.map { it.first }.average()
        val meanLng = samples.map { it.second }.average()

        val filtered = samples.filter { (lat, lng) ->
            haversine(lat, lng, meanLat, meanLng) < GPS_OUTLIER_THRESHOLD_M
        }.ifEmpty { samples }

        return Pair(filtered.map { it.first }.average(), filtered.map { it.second }.average())
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
