package com.ssafy.smartcane.lumen2.ble

import com.ssafy.smartcane.lumen2.assist.AssistCommand
import com.ssafy.smartcane.lumen2.assist.AssistDecision
import com.ssafy.smartcane.lumen2.assist.SideSpaceStatus

/**
 * AssistCommand 기반 BLE 시퀀스 진동 컨트롤러.
 *
 * ── 진동 패턴 ───────────────────────────────────────────────
 *  STOP (0.8m)
 *    → B3 → 300ms → B3 → 300ms → B3  (강진동 3번, 1회 발화 후 잠금)
 *    → 안전한 커맨드가 오면 잠금 해제 (재장전)
 *
 *  FRONT_LIMIT / LEFT_SPACE / RIGHT_SPACE / BOTH_SIDE_SPACE (1.5m)
 *    → B1 → 300ms → B1 [→ 400ms → 방향]
 *    방향은 awareness.leftSpace / rightSpace 를 직접 읽어 결정:
 *      왼쪽만 여유  → L2
 *      오른쪽만 여유 → R2
 *      양쪽 여유    → B2
 *      여유 없음    → 방향 없음
 *
 *  나머지 → 진동 없음
 * ──────────────────────────────────────────────────────────────
 */
class ProximityVibrationController(
    private val send: (String) -> Unit,
    private val respectCooldown: Boolean = true
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var sequencePlaying = false
    private var lastActiveCmd: String? = null
    private var lastSentCommand: AssistCommand? = null
    private var lastSentAt = 0L

    // STOP 1회 발화 잠금: STOP 이후 안전 커맨드가 올 때까지 재발화 차단
    private var stopFired = false

    fun update(decision: AssistDecision) {
        val now = System.currentTimeMillis()
        val cmd = decision.command

        // 안전한 커맨드(비STOP) → stopFired 리셋 (재장전)
        if (cmd != AssistCommand.STOP) {
            stopFired = false
        }

        when {
            // ── 진동 대상 아님 → 시퀀스 취소 + OFF ────────────────
            !hasVibration(cmd) -> {
                if (lastActiveCmd != null) {
                    cancelSequence()
                    send("O")
                    lastActiveCmd = null
                    lastSentCommand = null
                }
            }

            // ── 시퀀스 재생 중: STOP만 즉시 인터럽트 허용 ──────────
            sequencePlaying && cmd != AssistCommand.STOP -> return

            // ── 진동 전송 ────────────────────────────────────────────
            else -> {
                val commandChanged  = cmd != lastSentCommand
                val cooldownElapsed = now - lastSentAt >= cooldownFor(cmd)
                val shouldSend      = !respectCooldown || commandChanged || cooldownElapsed

                if (!shouldSend) return

                when (cmd) {
                    // ── STOP: 1회 발화 후 잠금, 안전 커맨드 후 재장전 ──
                    AssistCommand.STOP -> {
                        if (stopFired) return  // 이미 발화됨 → 안전 커맨드 올 때까지 대기
                        stopFired = true
                        cancelSequence()
                        lastSentCommand = cmd
                        lastSentAt      = now
                        lastActiveCmd   = "B3"
                        playStop()
                    }

                    // ── FRONT_LIMIT 파이프라인 ──────────────────────────
                    // awareness 를 직접 읽어 방향 결정 (AssistEngine 커맨드 무관)
                    AssistCommand.FRONT_LIMIT,
                    AssistCommand.LEFT_SPACE,
                    AssistCommand.RIGHT_SPACE,
                    AssistCommand.BOTH_SIDE_SPACE -> {
                        val left  = decision.awareness.leftSpace  == SideSpaceStatus.AVAILABLE
                        val right = decision.awareness.rightSpace == SideSpaceStatus.AVAILABLE
                        val direction = when {
                            left && !right -> "L2"   // 왼쪽만 여유
                            right && !left -> "R2"   // 오른쪽만 여유
                            left && right  -> "B2"   // 양쪽 여유
                            else           -> null   // 여유 없음
                        }
                        cancelSequence()
                        lastSentCommand = cmd
                        lastSentAt      = now
                        lastActiveCmd   = "B1"
                        playFrontLimit(direction)
                    }

                    else -> Unit
                }
            }
        }
    }

    fun reset() {
        cancelSequence()
        if (lastActiveCmd != null) send("O")
        lastActiveCmd   = null
        lastSentCommand = null
        lastSentAt      = 0L
        stopFired       = false
    }

    // ── 시퀀스 ────────────────────────────────────────────────────

    /** B3 → 300ms → B3 → 300ms → B3 */
    private fun playStop() {
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

    /** B1 → 300ms → B1 [→ 400ms → directionCmd] */
    private fun playFrontLimit(directionCmd: String?) {
        sequencePlaying = true
        send("B1")
        handler.postDelayed({
            send("B1")
            if (directionCmd != null) {
                handler.postDelayed({
                    send(directionCmd)
                    handler.postDelayed({ sequencePlaying = false }, 300)
                }, 400)
            } else {
                handler.postDelayed({ sequencePlaying = false }, 300)
            }
        }, 300)
    }

    private fun cancelSequence() {
        handler.removeCallbacksAndMessages(null)
        sequencePlaying = false
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private fun hasVibration(cmd: AssistCommand): Boolean = when (cmd) {
        AssistCommand.STOP,
        AssistCommand.FRONT_LIMIT,
        AssistCommand.LEFT_SPACE,
        AssistCommand.RIGHT_SPACE,
        AssistCommand.BOTH_SIDE_SPACE -> true
        else -> false
    }

    /** AssistCommand 별 최소 재전송 간격 */
    private fun cooldownFor(cmd: AssistCommand): Long = when (cmd) {
        AssistCommand.STOP                                        -> 0L       // 잠금 로직으로 제어
        AssistCommand.FRONT_LIMIT,
        AssistCommand.LEFT_SPACE,
        AssistCommand.RIGHT_SPACE,
        AssistCommand.BOTH_SIDE_SPACE                             -> 2000L    // 시퀀스 ~1000ms + 여유
        else                                                      -> Long.MAX_VALUE
    }
}
