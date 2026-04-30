package com.ssafy.smartcane.network

import com.ssafy.smartcane.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class RoutePoint(
    val longitude: Double,
    val latitude: Double
)

data class RouteInstruction(
    val title: String,
    val distanceMeters: Int,
    val durationSeconds: Int
)

data class WalkingRoutePlan(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val points: List<RoutePoint>,
    val instructions: List<RouteInstruction>
)

class WalkingDirectionsService(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val TAG = "WalkingDirections"
    }

    suspend fun getWalkingRoute(
        originLongitude: Double,
        originLatitude: Double,
        destinationLongitude: Double,
        destinationLatitude: Double
    ): WalkingRoutePlan? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = BuildConfig.KAKAO_REST_API_KEY.trim()
            if (apiKey.isEmpty()) return@withContext null

            val url = "https://apis-navi.kakaomobility.com/affiliate/walking/v1/directions"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("origin", "$originLongitude,$originLatitude")
                .addQueryParameter("destination", "$destinationLongitude,$destinationLatitude")
                .addQueryParameter("priority", "DISTANCE")
                .addQueryParameter("summary", "false")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "KakaoAK $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Walking directions failed: status=${response.code}, body=$body")
                    return@withContext null
                }
                if (body.isBlank()) return@withContext null
                parseWalkingRoute(body)
            }
        }.onFailure {
            Log.e(TAG, "Walking directions request error", it)
        }.getOrNull()
    }

    private fun parseWalkingRoute(body: String): WalkingRoutePlan? {
        val root = JSONObject(body)
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val summary = route.optJSONObject("summary")
        val sections = route.optJSONArray("sections") ?: JSONArray()
        val points = mutableListOf<RoutePoint>()
        val instructions = mutableListOf<RouteInstruction>()

        val resultCode = route.optInt("result_code", 0)
        if (resultCode != 0) {
            Log.e(TAG, "Walking directions result failed: code=$resultCode, message=${route.optString("result_message")}")
            return null
        }

        for (sectionIndex in 0 until sections.length()) {
            val section = sections.optJSONObject(sectionIndex) ?: continue
            val roads = section.optJSONArray("roads") ?: JSONArray()
            for (roadIndex in 0 until roads.length()) {
                val road = roads.optJSONObject(roadIndex) ?: continue
                val vertexes = road.optJSONArray("vertexes") ?: JSONArray()
                var index = 0
                while (index + 1 < vertexes.length()) {
                    points += RoutePoint(
                        longitude = vertexes.optDouble(index),
                        latitude = vertexes.optDouble(index + 1)
                    )
                    index += 2
                }

                val distance = road.optInt("distance", 0)
                val duration = road.optInt("duration", 0)
                if (distance > 0 || duration > 0) {
                    instructions += RouteInstruction(
                        title = road.optString("name").ifBlank { "도보 이동" },
                        distanceMeters = distance,
                        durationSeconds = duration
                    )
                }
            }
        }

        val totalDistance = summary?.optInt("distance", 0)
            ?: instructions.sumOf { it.distanceMeters }
        val totalDuration = summary?.optInt("duration", 0)
            ?: instructions.sumOf { it.durationSeconds }

        if (points.isEmpty()) return null
        return WalkingRoutePlan(
            distanceMeters = totalDistance,
            durationSeconds = totalDuration,
            points = points,
            instructions = instructions.ifEmpty {
                listOf(
                    RouteInstruction(
                        title = "도착지까지 이동",
                        distanceMeters = totalDistance,
                        durationSeconds = totalDuration
                    )
                )
            }
        )
    }
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return "${minutes}분"
}

fun formatDistance(meters: Int): String {
    if (meters <= 0) return ""
    if (meters < 1000) return "${meters}m"
    val km = meters / 1000.0
    val text = String.format(java.util.Locale.US, "%.1f", km).removeSuffix(".0")
    return "${text}km"
}
