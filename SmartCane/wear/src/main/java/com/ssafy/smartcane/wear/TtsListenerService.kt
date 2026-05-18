package com.ssafy.smartcane.wear

import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.util.Locale

class TtsListenerService : WearableListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        const val PATH_TTS = "/tts"
        private const val TAG = "TtsListenerService"
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.KOREAN
            tts?.setSpeechRate(1.08f)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_TTS) return
        val text = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "수신: $text")
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wear-tts-${System.currentTimeMillis()}")
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
