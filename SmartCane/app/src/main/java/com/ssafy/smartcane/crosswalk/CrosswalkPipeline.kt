package com.ssafy.smartcane.crosswalk

import android.content.Context
import android.util.Log
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

    companion object {
        private const val TAG = "CrosswalkPipeline"
        private const val COOLDOWN_MS = 15_000L
        private const val GPS_SAMPLE_COUNT = 5
        private const val GPS_SAMPLE_INTERVAL_MS = 800L
        private const val GPS_OUTLIER_THRESHOLD_M = 15.0
    }

    // 신호 상태 변경 시에만 발화
    private var lastSignalState: Boolean? = null   // null=미감지, true=녹색, false=적색
    private var lastSignalDetectedMs = 0L
    private val SIGNAL_RESET_MS = 20_000L          // 20초간 신호 없으면 상태 초기화

    fun onSignalDetected(green: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastSignalDetectedMs > SIGNAL_RESET_MS) lastSignalState = null
        lastSignalDetectedMs = now
        if (green == lastSignalState) return        // 같은 신호 → 발화 안 함
        lastSignalState = green
        val text = if (green) "녹색 신호입니다. 건너세요." else "적색 신호입니다. 기다리세요."
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
        scope.cancel()
    }

    private suspend fun runPipeline() {
        // 1. GPS 수집 후 튀는값 제거
        val samples = collectGpsSamples()
        val stableLoc = removeOutliersAndAverage(samples) ?: run {
            Log.w(TAG, "안정적인 GPS 위치를 얻을 수 없습니다")
            return
        }

        // 2. 가장 가까운 신호등 1개 조회 (10m 이내)
        val lights = TrafficLightApiService.fetchNearest(stableLoc.first, stableLoc.second)
        if (lights.isEmpty()) {
            Log.d(TAG, "10m 이내 신호등 없음")
            return
        }

        // 3. road_route_name 기반 음성 안내
        val guidance = buildGuidanceFromAddress(lights)
        speechOutput.speak(guidance)
        Log.d(TAG, "음성 안내: $guidance")
    }

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

    // ── 가장 가까운 신호등 road_route_name 안내 ──────────────────────────
    private fun buildGuidanceFromAddress(lights: List<NearestTrafficLight>): String {
        val routeName = lights.firstOrNull()?.roadRouteName
        return if (!routeName.isNullOrBlank()) {
            "${routeName} 신호등입니다"
        } else {
            "근처에 신호등이 있습니다"
        }
    }

    // ── 각도 기반 방위 안내 (비활성화) ───────────────────────────────────
//    private fun buildGuidanceWithHeading(lights: List<NearestTrafficLight>, heading: Float): String {
//        if (lights.size == 1) return "${facingDirectionToName(lights[0].facingDirection)} 방면 신호등입니다"
//        val sorted = lights.sortedBy { angularDiff(heading.toDouble(), it.facingDirection ?: 999.0) }
//        val primaryDir = facingDirectionToName(sorted[0].facingDirection)
//        val secondaryDir = facingDirectionToName(sorted[1].facingDirection)
//        val turnLabel = if (isLeftRelative(heading, sorted[1].facingDirection)) "좌회전" else "우회전"
//        return "직진은 ${primaryDir} 방면, ${turnLabel} 시 ${secondaryDir} 방면 신호등입니다"
//    }

//    private fun facingDirectionToName(degrees: Double?): String {
//        if (degrees == null) return "전방"
//        val d = ((degrees % 360) + 360) % 360
//        return when {
//            d < 22.5 || d >= 337.5 -> "북쪽"
//            d < 67.5  -> "북동쪽"
//            d < 112.5 -> "동쪽"
//            d < 157.5 -> "남동쪽"
//            d < 202.5 -> "남쪽"
//            d < 247.5 -> "남서쪽"
//            d < 292.5 -> "서쪽"
//            else      -> "북서쪽"
//        }
//    }

//    private fun angularDiff(a: Double, b: Double): Double {
//        val diff = abs(a - b) % 360
//        return if (diff > 180) 360 - diff else diff
//    }

//    private fun isLeftRelative(heading: Float, targetDeg: Double?): Boolean {
//        targetDeg ?: return false
//        val diff = ((targetDeg - heading + 360) % 360)
//        return diff in 90.0..270.0
//    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
