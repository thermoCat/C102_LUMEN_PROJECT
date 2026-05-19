package com.ssafy.smartcane.segformer.pose

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException

class WorldPoseProvider(
    private val port: Int = DEFAULT_UDP_PORT,
    private val maxPoseAgeMs: Long = DEFAULT_MAX_POSE_AGE_MS,
) {
    @Volatile private var latestPose: WorldCameraPose? = null
    @Volatile private var running = false
    private var receiverThread: Thread? = null
    private var socket: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true
        receiverThread = Thread(::receiveLoop, "orb-slam3-pose-udp").also { it.start() }
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
        receiverThread?.interrupt()
        receiverThread = null
    }

    fun snapshot(): WorldCameraPose? {
        val pose = latestPose ?: return null
        val ageMs = SystemClock.elapsedRealtime() - pose.receivedAtMs
        return if (ageMs <= maxPoseAgeMs) pose else null
    }

    private fun receiveLoop() {
        val buffer = ByteArray(MAX_PACKET_BYTES)
        try {
            DatagramSocket(port).use { datagramSocket ->
                socket = datagramSocket
                datagramSocket.reuseAddress = true
                datagramSocket.soTimeout = SOCKET_TIMEOUT_MS
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        datagramSocket.receive(packet)
                        val payload = String(
                            packet.data,
                            packet.offset,
                            packet.length,
                            Charsets.UTF_8,
                        ).trim()
                        latestPose = parsePose(payload)
                    } catch (ignored: SocketTimeoutException) {
                        // Keep the receiver responsive to stop().
                    } catch (exception: SocketException) {
                        if (running) throw exception
                    } catch (ignored: Throwable) {
                        // Ignore malformed packets and keep the last valid pose.
                    }
                }
            }
        } catch (ignored: Throwable) {
            running = false
        } finally {
            socket = null
        }
    }

    private fun parsePose(payload: String): WorldCameraPose {
        val json = JSONObject(payload)
        val matrixArray = json.optJSONArray("T_world_camera")
            ?: json.optJSONArray("t_world_camera")
            ?: json.optJSONArray("matrix")
            ?: error("Missing T_world_camera")
        val matrix = flattenMatrix(matrixArray)
        val timestampNs = when {
            json.has("timestamp_ns") -> json.optLong("timestamp_ns")
            json.has("timestampNs") -> json.optLong("timestampNs")
            else -> 0L
        }
        return WorldCameraPose(
            matrix = matrix,
            timestampNs = timestampNs,
            receivedAtMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun flattenMatrix(array: JSONArray): FloatArray {
        val values = mutableListOf<Float>()
        for (index in 0 until array.length()) {
            val value = array.get(index)
            if (value is JSONArray) {
                for (nestedIndex in 0 until value.length()) {
                    values += value.getDouble(nestedIndex).toFloat()
                }
            } else {
                values += array.getDouble(index).toFloat()
            }
        }
        require(values.size == MATRIX_SIZE) {
            "T_world_camera must contain 16 values"
        }
        return values.toFloatArray()
    }

    companion object {
        const val DEFAULT_UDP_PORT = 5066
        private const val DEFAULT_MAX_POSE_AGE_MS = 1_000L
        private const val SOCKET_TIMEOUT_MS = 500
        private const val MAX_PACKET_BYTES = 4096
        private const val MATRIX_SIZE = 16
    }
}
