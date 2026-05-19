package com.ssafy.smartcane.segformer.ble

import android.os.Handler
import android.os.Looper
import com.ssafy.smartcane.segformer.model.SegmentationResult
import kotlin.math.abs

/**
 * SafetyWalk의 ProximityVibrationController 와 동일한 패턴(B3 × 3, 2s 쿨다운,
 * 해제 시 O)을 SegFormer 추론 결과 기반으로 재현한다.
 *
 * 정면 [dangerThresholdM] m 이내, 좌우 ±[lateralThresholdM] m 안에서 위험 클래스
 * (차도/자전거도로/골목/주의구역)가 감지되면 진동 시퀀스를 전송한다.
 */
class SegFormerProximityController(
    private val send: (String) -> Unit,
    private val dangerClassNames: Set<String> = DEFAULT_DANGER_CLASSES,
    private val dangerThresholdM: Float = 2.0f,
    private val lateralThresholdM: Float = 1.2f,
    private val cooldownMs: Long = 2000L,
    private val respectCooldown: Boolean = true,
    private val frameTimeoutMs: Long = 1000L,
    private val entryConfirmMs: Long = 300L,
    private val exitHangoverMs: Long = 1500L,
) {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var sequencePlaying = false
    private var lastActive = false
    private var lastSentAt = 0L

    // Phase 1-A: 마지막 update 호출 시각 — UVC hiccup 으로 분석이 끊겼을 때 잔존 상태 무효화
    private var lastUpdateAt = 0L
    // Phase 1-B: raw danger=true 스트릭 시작 시각 (0 = 진행 중 아님)
    private var dangerEnteredAt = 0L
    // Phase 1-C: raw danger=false 스트릭 시작 시각 (0 = 진행 중 아님)
    private var dangerExitedAt = 0L

    fun update(result: SegmentationResult) {
        val now = System.currentTimeMillis()

        // Phase 1-A: 프레임 신선도 — 1초 이상 update 누락 시 강제 초기화
        if (lastUpdateAt > 0L && now - lastUpdateAt > frameTimeoutMs) {
            cancelSequence()
            if (lastActive) send("O")
            lastActive = false
            dangerEnteredAt = 0L
            dangerExitedAt = 0L
            lastSentAt = 0L
        }
        lastUpdateAt = now

        val raw = hasDanger(result)

        // Phase 1-B/1-C: 스트릭 타이머 갱신
        if (raw) {
            if (dangerEnteredAt == 0L) dangerEnteredAt = now
            dangerExitedAt = 0L
        } else {
            dangerEnteredAt = 0L
            if (dangerExitedAt == 0L) dangerExitedAt = now
        }

        val effectiveDanger = if (lastActive) {
            // Phase 1-C: 활성 상태에서는 hangover 동안 유지
            raw || (now - dangerExitedAt < exitHangoverMs)
        } else {
            // Phase 1-B: 비활성 상태에서는 ENTRY_CONFIRM_MS 지속돼야 진입
            raw && (now - dangerEnteredAt >= entryConfirmMs)
        }

        when {
            !effectiveDanger -> {
                if (lastActive) {
                    cancelSequence()
                    send("O")
                    lastActive = false
                }
            }

            sequencePlaying -> return

            else -> {
                val cooldownElapsed = now - lastSentAt >= cooldownMs
                if (respectCooldown && !cooldownElapsed) return

                cancelSequence()
                lastSentAt = now
                lastActive = true
                playTriple()
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

    private fun hasDanger(result: SegmentationResult): Boolean {
        val projection = result.groundProjection ?: return false
        return projection.classSummaries.any { summary ->
            val name = result.classNames.getOrNull(summary.classIndex) ?: return@any false
            if (name !in dangerClassNames) return@any false
            summary.minForwardM in 0f..dangerThresholdM &&
                abs(summary.centroidLateralM) <= lateralThresholdM
        }
    }

    private fun playTriple() {
        sequencePlaying = true
        send("B3")
        handler.postDelayed({
            send("B3")
            handler.postDelayed({
                send("B3")
                handler.postDelayed({ sequencePlaying = false }, 300)
            }, 300)
        }, 300)
    }

    private fun cancelSequence() {
        handler.removeCallbacksAndMessages(null)
        sequencePlaying = false
    }

    companion object {
        val DEFAULT_DANGER_CLASSES: Set<String> = setOf(
            "caution_zone",
            "bike_lane",
            "alley",
            "roadway",
        )
    }
}
