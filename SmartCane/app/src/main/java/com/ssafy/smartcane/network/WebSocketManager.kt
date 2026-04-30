package com.ssafy.smartcane.network

import android.util.Log
import com.ssafy.smartcane.data.model.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketManager(
    private val onStateReceived: (NavigationState) -> Unit
) {
    companion object {
        const val WS_URL = "ws://192.168.0.100:8765"
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(true)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() { scope.launch { tryConnect() } }

    private fun tryConnect() {
        if (!shouldReconnect.get()) return
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected.set(true)
            }
            override fun onMessage(webSocket: WebSocket, text: String) { parseAndDispatch(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected.set(false)
                scheduleReconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected.set(false)
                if (shouldReconnect.get()) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return
        scope.launch { delay(RECONNECT_DELAY_MS); tryConnect() }
    }

    private fun parseAndDispatch(text: String) {
        try {
            val json = JSONObject(text)
            val state = json.optString("state", "CENTER")
            val pathArray = json.optJSONArray("path") ?: JSONArray()
            val maskArray = json.optJSONArray("mask") ?: JSONArray()
            val path = (0 until pathArray.length()).map { i ->
                val pt = pathArray.getJSONArray(i)
                listOf(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat())
            }
            val mask = (0 until maskArray.length()).map { i ->
                val pt = maskArray.getJSONArray(i)
                listOf(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat())
            }
            onStateReceived(NavigationState(state = state, path = path, mask = mask))
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    fun disconnect() {
        shouldReconnect.set(false)
        webSocket?.close(1000, "Disconnect")
        scope.cancel()
    }
}
