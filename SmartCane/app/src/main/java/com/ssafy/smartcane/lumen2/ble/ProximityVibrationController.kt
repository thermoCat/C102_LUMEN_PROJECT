package com.ssafy.smartcane.lumen2.ble

import com.ssafy.smartcane.lumen2.assist.AssistCommand
import com.ssafy.smartcane.lumen2.assist.AssistDecision

class ProximityVibrationController(
    private val send: (String) -> Unit,
    private val respectCooldown: Boolean = true
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var sequencePlaying = false
    private var lastActiveCmd: String? = null
    private var lastSentAt = 0L

    fun update(decision: AssistDecision) {
        val now = System.currentTimeMillis()
        val cmd = decision.command

        when {
            !hasVibration(cmd) -> {
                if (lastActiveCmd != null) {
                    cancelSequence()
                    send("O")
                    lastActiveCmd = null
                }
            }

            sequencePlaying -> return

            else -> {
                val cooldownElapsed = now - lastSentAt >= 2000L
                if (respectCooldown && !cooldownElapsed) return

                cancelSequence()
                lastSentAt = now
                lastActiveCmd = "B3"
                playTriple()
            }
        }
    }

    fun reset() {
        cancelSequence()
        if (lastActiveCmd != null) send("O")
        lastActiveCmd = null
        lastSentAt = 0L
    }

    // 둥둥둥: B3 × 3회, 300ms 간격
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

    private fun hasVibration(cmd: AssistCommand): Boolean = when (cmd) {
        AssistCommand.STOP,
        AssistCommand.FRONT_LIMIT -> true
        else -> false
    }
}
