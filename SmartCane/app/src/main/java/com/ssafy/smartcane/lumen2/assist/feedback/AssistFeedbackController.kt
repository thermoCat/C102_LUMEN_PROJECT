package com.ssafy.smartcane.lumen2.assist

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

class AssistFeedbackController(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val tts = TextToSpeech(appContext, this)
    private var ttsReady = false

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = Locale.KOREAN
            tts.setSpeechRate(1.08f)
        }
    }

    fun apply(decision: AssistDecision) {
        if (decision.shouldVibrate) vibrate(decision.state)
        val speech = decision.speech
        if (decision.shouldSpeak && speech != null && ttsReady) {
            val queueMode = if (decision.state == AssistState.CRITICAL_STOP) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(speech, queueMode, null, "assist-${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun vibrate(state: AssistState) {
        val pattern = when (state) {
            AssistState.CRITICAL_STOP -> longArrayOf(0, 650)
            AssistState.SYSTEM_UNSTABLE -> longArrayOf(0, 120, 160, 120)
            AssistState.CAUTION -> longArrayOf(0, 90, 100, 90)
            AssistState.CAMERA_ADJUST -> longArrayOf(0, 110, 140, 110)
            AssistState.RECOVERY -> longArrayOf(0, 70)
            AssistState.NORMAL -> return
        }
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(pattern, -1)
        } else {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }
}
