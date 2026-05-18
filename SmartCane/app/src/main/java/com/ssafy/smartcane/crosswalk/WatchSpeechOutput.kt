package com.ssafy.smartcane.crosswalk

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WatchSpeechOutput(
    private val context: Context,
    private val fallback: SpeechOutput
) : SpeechOutput {

    companion object {
        private const val TAG = "WatchSpeechOutput"
        private const val PATH_TTS = "/tts"
    }

    override fun speak(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                if (nodes.isEmpty()) {
                    fallback.speak(text)
                    return@launch
                }
                nodes.forEach { node ->
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, PATH_TTS, text.toByteArray(Charsets.UTF_8))
                        .await()
                    Log.d(TAG, "워치(${node.displayName})로 전송: $text")
                }
            } catch (e: Exception) {
                Log.e(TAG, "워치 전송 실패, 폰 TTS로 대체", e)
                fallback.speak(text)
            }
        }
    }
}
