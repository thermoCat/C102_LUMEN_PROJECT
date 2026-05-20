package com.ssafy.smartcane.segformer.ble

import android.os.Handler
import android.os.Looper
import com.ssafy.smartcane.detection.TFLiteRunner
import com.ssafy.smartcane.segformer.model.SegmentationResult

/**
 * 3×3 존 기반 위험 감지 컨트롤러.
 *
 * 존 배치:
 *   0 | 1 | 2
 *   ---------
 *   3 | 4 | 5
 *   ---------
 *   6 | 7 | 8
 *
 * Zone 7 (정면 하단): 주의구역/차도 감지 → W,B,500,2 (두 번 경고)
 * Zone 4 (정면 중단): 주의구역/차도 감지 → W,B,1200,2 (위요위용)
 * YOLO (횡단보도/적색신호):          → B3 × 3회
 *
 * 우선순위: Zone 7 > Zone 4 > YOLO
 */
class SegFormerProximityController(
    private val send: (String) -> Unit,
    private val dangerClassNames: Set<String> = DEFAULT_DANGER_CLASSES,
    private val yoloDangerLabels: Set<String> = DEFAULT_YOLO_DANGER_LABELS,
    private val zonePixelThreshold: Float = 0.03f,  // 존 내 위험 픽셀 비율 하한
    private val cooldownMs: Long = 4000L,
    private val respectCooldown: Boolean = true,
    private val frameTimeoutMs: Long = 3000L,
    private val entryConfirmMs: Long = 300L,
    private val exitHangoverMs: Long = 1500L,
) {
    private enum class DangerZone { NONE, ZONE4, ZONE7 }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var tripleSequencePlaying = false
    private var lastActive = false
    private var lastSentAt = 0L
    private var lastUpdateAt = 0L
    private var dangerEnteredAt = 0L
    private var dangerExitedAt = 0L

    fun update(
        result: SegmentationResult,
        yoloDetections: List<TFLiteRunner.Result> = emptyList(),
    ) {
        val now = System.currentTimeMillis()

        // 프레임 신선도 — 1초 이상 누락 시 초기화
        if (lastUpdateAt > 0L && now - lastUpdateAt > frameTimeoutMs) {
            cancelSequence()
            if (lastActive) send("O")
            lastActive = false
            dangerEnteredAt = 0L
            dangerExitedAt = 0L
            lastSentAt = 0L
        }
        lastUpdateAt = now

        val dangerZone = detectDangerZone(result)
        val yoloDanger = hasYoloDanger(yoloDetections)
        val rawDanger = dangerZone != DangerZone.NONE || yoloDanger

        // 스트릭 타이머 갱신
        if (rawDanger) {
            if (dangerEnteredAt == 0L) dangerEnteredAt = now
            dangerExitedAt = 0L
        } else {
            dangerEnteredAt = 0L
            if (dangerExitedAt == 0L) dangerExitedAt = now
        }

        val effectiveDanger = if (lastActive) {
            rawDanger || (dangerExitedAt > 0L && now - dangerExitedAt < exitHangoverMs)
        } else {
            rawDanger && (now - dangerEnteredAt >= entryConfirmMs)
        }

        when {
            !effectiveDanger -> {
                if (lastActive) {
                    cancelSequence()
                    send("O")
                    lastActive = false
                }
            }
            tripleSequencePlaying -> return
            else -> {
                if (respectCooldown && now - lastSentAt < cooldownMs) return
                cancelSequence()
                lastSentAt = now
                lastActive = true

                when {
                    dangerZone == DangerZone.ZONE7 -> send("W,B,500,2")
                    dangerZone == DangerZone.ZONE4 -> send("W,B,1200,2")
                    yoloDanger -> playTriple()
                }
            }
        }
    }

    fun reset() {
        cancelSequence()
        if (lastActive) send("O")
        lastActive = false
        lastSentAt = 0L
        lastUpdateAt = 0L
        dangerEnteredAt = 0L
        dangerExitedAt = 0L
    }

    // ── 존 기반 마스크 분석 ────────────────────────────────────────────────

    private fun detectDangerZone(result: SegmentationResult): DangerZone {
        if (isZoneDanger(result, zone = 7)) return DangerZone.ZONE7
        if (isZoneDanger(result, zone = 4, bottomHalfOnly = true)) return DangerZone.ZONE4
        return DangerZone.NONE
    }

    /**
     * 3×3 그리드에서 [zone]번 영역의 위험 픽셀 비율이 [zonePixelThreshold] 이상이면 true.
     * zone = row*3 + col 이므로:
     *   zone 4 → row=1, col=1 (정면 중단 — bottomHalfOnly=true 시 아래 절반만 검사)
     *   zone 7 → row=2, col=1 (정면 하단)
     */
    private fun isZoneDanger(
        result: SegmentationResult,
        zone: Int,
        bottomHalfOnly: Boolean = false
    ): Boolean {
        val mask = result.mask
        val w = result.maskWidth
        val h = result.maskHeight
        if (w == 0 || h == 0 || mask.isEmpty()) return false

        val col = zone % 3
        val row = zone / 3
        val zoneW = w / 3
        val zoneH = h / 3
        val xStart = col * zoneW
        val xEnd = if (col == 2) w else xStart + zoneW
        val zoneYStart = row * zoneH
        val zoneYEnd = if (row == 2) h else zoneYStart + zoneH
        val yStart = if (bottomHalfOnly) zoneYStart + zoneH / 2 else zoneYStart
        val yEnd = zoneYEnd

        var dangerPixels = 0
        var totalPixels = 0

        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val idx = y * w + x
                if (idx >= mask.size) continue
                val className = result.classNames.getOrNull(mask[idx]) ?: continue
                totalPixels++
                if (className in dangerClassNames) dangerPixels++
            }
        }

        return totalPixels > 0 && dangerPixels.toFloat() / totalPixels >= zonePixelThreshold
    }

    // ── YOLO 위험 감지 ────────────────────────────────────────────────────

    private fun hasYoloDanger(detections: List<TFLiteRunner.Result>): Boolean {
        if (detections.isEmpty() || yoloDangerLabels.isEmpty()) return false
        return detections.any { it.label in yoloDangerLabels }
    }

    // ── BLE 시퀀스 ────────────────────────────────────────────────────────

    private fun playTriple() {
        tripleSequencePlaying = true
        send("B3")
        handler.postDelayed({
            send("B3")
            handler.postDelayed({
                send("B3")
                handler.postDelayed({ tripleSequencePlaying = false }, 300)
            }, 300)
        }, 300)
    }

    private fun cancelSequence() {
        handler.removeCallbacksAndMessages(null)
        tripleSequencePlaying = false
    }

    companion object {
        val DEFAULT_DANGER_CLASSES: Set<String> = setOf(
            "caution_zone",
            "roadway",
        )

        val DEFAULT_YOLO_DANGER_LABELS: Set<String> = setOf(
            "crosswalk",
            "red_pedestrian_light",
        )
    }
}
