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
    private var lastSentCommand: AssistCommand? = null
    private var lastSentAt = 0L
    private var stopFired = false

    fun update(decision: AssistDecision) {
        val now = System.currentTimeMillis()
        val cmd = decision.command

        if (cmd != AssistCommand.STOP) {
            stopFired = false
        }

        when {
            !hasVibration(cmd) -> {
                if (lastActiveCmd != null) {
                    cancelSequence()
                    send("O")
                    lastActiveCmd = null
                    lastSentCommand = null
                }
            }

            sequencePlaying && cmd != AssistCommand.STOP -> return

            else -> {
                val commandChanged = cmd != lastSentCommand
                val cooldownElapsed = now - lastSentAt >= cooldownFor(cmd)
                val shouldSend = !respectCooldown || commandChanged || cooldownElapsed
                if (!shouldSend) return

                when (cmd) {
                    AssistCommand.STOP -> {
                        if (stopFired) return
                        stopFired = true
                        cancelSequence()
                        lastSentCommand = cmd
                        lastSentAt = now
                        lastActiveCmd = "B3"
                        playStop()
                    }

                    AssistCommand.FRONT_LIMIT -> {
                        cancelSequence()
                        lastSentCommand = cmd
                        lastSentAt = now
                        lastActiveCmd = "B1"
                        playFrontLimit()
                    }

                    else -> Unit
                }
            }
        }
    }

    fun reset() {
        cancelSequence()
        if (lastActiveCmd != null) send("O")
        lastActiveCmd = null
        lastSentCommand = null
        lastSentAt = 0L
        stopFired = false
    }

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

    private fun playFrontLimit() {
        sequencePlaying = true
        send("B1")
        handler.postDelayed({
            send("B1")
            handler.postDelayed({ sequencePlaying = false }, 300)
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

    private fun cooldownFor(cmd: AssistCommand): Long = when (cmd) {
        AssistCommand.STOP -> 0L
        AssistCommand.FRONT_LIMIT -> 2000L
        else -> Long.MAX_VALUE
    }
}
