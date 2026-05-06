package com.ssafy.smartcane.network

import android.util.Log
import com.ssafy.smartcane.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class RoutePoint(
    val longitude: Double,
    val latitude: Double
)

enum class DirectionCue {
    START,
    STRAIGHT,
    LEFT,
    RIGHT,
    DESTINATION
}

data class RouteInstruction(
    val title: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val cue: DirectionCue = DirectionCue.STRAIGHT
)

data class WalkingRoutePlan(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val points: List<RoutePoint>,
    val instructions: List<RouteInstruction>
)

class WalkingDirectionsService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
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
                .addQueryParameter("waypoints", "")
                .addQueryParameter("radius", "5000")
                .addQueryParameter("priority", "MAIN_STREET")
                .addQueryParameter("summary", "false")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "KakaoAK $apiKey")
                .addHeader("service", "smartcane")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            Log.d(TAG, "Request walking route origin=$originLongitude,$originLatitude destination=$destinationLongitude,$destinationLatitude priority=MAIN_STREET")
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "Walking directions response: status=${response.code}, bodyLength=${body.length}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Walking directions failed: status=${response.code}, body=$body")
                    return@withContext null
                }
                if (body.isBlank()) return@withContext null
                val parsed = parseWalkingRoute(body)
                Log.d(
                    TAG,
                    "Walking directions parsed: distance=${parsed?.distanceMeters}, duration=${parsed?.durationSeconds}, points=${parsed?.points?.size}, instructions=${parsed?.instructions?.size}"
                )
                parsed
            }
        }.onFailure {
            Log.e(TAG, "Walking directions request error", it)
        }.getOrNull()
    }

    private fun parseWalkingRoute(body: String): WalkingRoutePlan? {
        val root = JSONObject(body)
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val resultCode = route.optInt("result_code", 0)
        if (resultCode != 0) {
            Log.e(TAG, "Walking directions result failed: code=$resultCode, message=${route.optString("result_message")}")
            return null
        }

        val summary = route.optJSONObject("summary")
        val sections = route.optJSONArray("sections") ?: JSONArray()
        Log.d(
            TAG,
            "Walking directions parse start: resultCode=$resultCode, sections=${sections.length()}, summaryDistance=${summary?.optInt("distance", 0)}, summaryDuration=${summary?.optInt("duration", 0)}"
        )
        val points = mutableListOf<RoutePoint>()
        val instructions = mutableListOf<RouteInstruction>()

        for (sectionIndex in 0 until sections.length()) {
            val section = sections.optJSONObject(sectionIndex) ?: continue
            val roads = section.optJSONArray("roads") ?: JSONArray()
            Log.d(TAG, "Walking directions section[$sectionIndex]: roads=${roads.length()}")
            points += parseRoutePoints(roads)

            val guideInstructions = parseGuideInstructions(section.optJSONArray("guides") ?: JSONArray())
            instructions += guideInstructions.ifEmpty { buildTurnInstructions(parseRoadSegments(roads)) }
        }

        val totalDistance = summary?.optInt("distance", 0)
            ?: instructions.sumOf { it.distanceMeters }
        val totalDuration = summary?.optInt("duration", 0)
            ?: instructions.sumOf { it.durationSeconds }

        Log.d(TAG, "Walking directions parse result: points=${points.size}, instructions=${instructions.size}, totalDistance=$totalDistance, totalDuration=$totalDuration")
        if (points.isEmpty()) return null
        return WalkingRoutePlan(
            distanceMeters = totalDistance,
            durationSeconds = totalDuration,
            points = points,
            instructions = instructions.ifEmpty {
                listOf(
                    RouteInstruction(
                        title = "\ubaa9\uc801\uc9c0\uae4c\uc9c0 \uc774\ub3d9",
                        distanceMeters = totalDistance,
                        durationSeconds = totalDuration,
                        cue = DirectionCue.DESTINATION
                    )
                )
            }
        )
    }

    private fun parseRoutePoints(roads: JSONArray): List<RoutePoint> = buildList {
        for (roadIndex in 0 until roads.length()) {
            val road = roads.optJSONObject(roadIndex) ?: continue
            val vertexes = road.optJSONArray("vertexes") ?: JSONArray()
            var index = 0
            while (index + 1 < vertexes.length()) {
                add(
                    RoutePoint(
                        longitude = vertexes.optDouble(index),
                        latitude = vertexes.optDouble(index + 1)
                    )
                )
                index += 2
            }
        }
    }

    private fun parseGuideInstructions(guides: JSONArray): List<RouteInstruction> = buildList {
        for (guideIndex in 0 until guides.length()) {
            val guide = guides.optJSONObject(guideIndex) ?: continue
            val title = firstNonBlank(
                guide.optString("guidance"),
                guide.optString("description"),
                guide.optString("name"),
                guide.optString("road_name")
            ).ifBlank { "\uacbd\ub85c \uc548\ub0b4" }
            add(
                RouteInstruction(
                    title = title,
                    distanceMeters = firstPositiveInt(guide, "distance", "dist"),
                    durationSeconds = firstPositiveInt(guide, "duration", "time"),
                    cue = inferCue(title, guide.optString("type"))
                )
            )
        }
    }

    private fun parseRoadSegments(roads: JSONArray): List<RouteSegment> = buildList {
        for (roadIndex in 0 until roads.length()) {
            val road = roads.optJSONObject(roadIndex) ?: continue
            val distance = road.optInt("distance", 0)
            val duration = road.optInt("duration", 0)
            if (distance > 0 || duration > 0) {
                add(
                    RouteSegment(
                        distanceMeters = distance,
                        durationSeconds = duration,
                        roadName = firstNonBlank(
                            road.optString("road_name"),
                            road.optString("name")
                        ).ifBlank { null },
                        points = parseRoadPoints(road.optJSONArray("vertexes") ?: JSONArray())
                    )
                )
            }
        }
    }

    private fun parseRoadPoints(vertexes: JSONArray): List<RoutePoint> = buildList {
        var index = 0
        while (index + 1 < vertexes.length()) {
            add(
                RoutePoint(
                    longitude = vertexes.optDouble(index),
                    latitude = vertexes.optDouble(index + 1)
                )
            )
            index += 2
        }
    }

    private fun buildTurnInstructions(segments: List<RouteSegment>): List<RouteInstruction> {
        if (segments.isEmpty()) return emptyList()

        val grouped = mutableListOf<RouteInstructionDraft>()
        var currentCue = DirectionCue.STRAIGHT
        var currentDistance = 0
        var currentDuration = 0
        var currentRoadName: String? = null

        fun flush() {
            if (currentDistance <= 0 && currentDuration <= 0) return
            grouped += RouteInstructionDraft(
                cue = currentCue,
                distanceMeters = currentDistance,
                durationSeconds = currentDuration,
                roadName = currentRoadName
            )
            currentDistance = 0
            currentDuration = 0
            currentRoadName = null
        }

        segments.forEachIndexed { index, segment ->
            val cue = when {
                index == 0 -> DirectionCue.STRAIGHT
                else -> inferCueFromBearing(
                    previous = segments[index - 1],
                    current = segment
                )
            }
            val shouldStartNewInstruction =
                cue != DirectionCue.STRAIGHT &&
                    (currentDistance >= MIN_INSTRUCTION_DISTANCE_METERS || currentCue != DirectionCue.STRAIGHT)

            if (shouldStartNewInstruction) {
                flush()
                currentCue = cue
            } else if (currentDistance == 0) {
                currentCue = cue
            }

            currentDistance += segment.distanceMeters
            currentDuration += segment.durationSeconds
            if (currentRoadName.isNullOrBlank() && !segment.roadName.isNullOrBlank()) {
                currentRoadName = segment.roadName
            }

            if (currentDistance >= MAX_INSTRUCTION_DISTANCE_METERS) {
                flush()
                currentCue = DirectionCue.STRAIGHT
            }
        }
        flush()

        return grouped.mapIndexed { index, draft ->
            RouteInstruction(
                title = draft.toInstructionTitle(isFirst = index == 0),
                distanceMeters = draft.distanceMeters,
                durationSeconds = draft.durationSeconds,
                cue = draft.cue
            )
        }
    }

    private fun inferCueFromBearing(
        previous: RouteSegment,
        current: RouteSegment
    ): DirectionCue {
        val previousBearing = previous.bearing() ?: return DirectionCue.STRAIGHT
        val currentBearing = current.bearing() ?: return DirectionCue.STRAIGHT
        val delta = normalizeBearingDelta(currentBearing - previousBearing)
        return when {
            abs(delta) < STRAIGHT_THRESHOLD_DEGREES -> DirectionCue.STRAIGHT
            delta > 0 -> DirectionCue.RIGHT
            else -> DirectionCue.LEFT
        }
    }
}

private const val MIN_INSTRUCTION_DISTANCE_METERS = 25
private const val MAX_INSTRUCTION_DISTANCE_METERS = 90
private const val STRAIGHT_THRESHOLD_DEGREES = 32.0

private data class RouteSegment(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val roadName: String?,
    val points: List<RoutePoint>
)

private data class RouteInstructionDraft(
    val cue: DirectionCue,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val roadName: String?
)

private fun RouteInstructionDraft.toInstructionTitle(isFirst: Boolean): String {
    val routeName = roadName?.takeIf { it.isNotBlank() } ?: "\ubcf4\ud589\uc790\ub3c4\ub85c"
    val distance = formatDistance(distanceMeters)
    return when (cue) {
        DirectionCue.LEFT -> "\uc88c\ud68c\uc804 \ud6c4 ${routeName}\ub97c \ub530\ub77c ${distance} \uc774\ub3d9"
        DirectionCue.RIGHT -> "\uc6b0\ud68c\uc804 \ud6c4 ${routeName}\ub97c \ub530\ub77c ${distance} \uc774\ub3d9"
        DirectionCue.START,
        DirectionCue.STRAIGHT -> "\uc9c1\uc9c4 \ud6c4 ${routeName}\ub97c \ub530\ub77c ${distance} \uc774\ub3d9"
        DirectionCue.DESTINATION -> "\ub3c4\ucc29"
    }
}

private fun RouteSegment.bearing(): Double? {
    val start = points.firstOrNull() ?: return null
    val end = points.lastOrNull() ?: return null
    if (start == end) return null
    val startLat = Math.toRadians(start.latitude)
    val endLat = Math.toRadians(end.latitude)
    val deltaLng = Math.toRadians(end.longitude - start.longitude)
    val y = sin(deltaLng) * cos(endLat)
    val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun normalizeBearingDelta(delta: Double): Double =
    ((delta + 540.0) % 360.0) - 180.0

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return "${minutes}\ubd84"
}

fun formatDistance(meters: Int): String {
    if (meters <= 0) return ""
    if (meters < 1000) return "${meters}m"
    val km = meters / 1000.0
    val text = String.format(java.util.Locale.US, "%.1f", km).removeSuffix(".0")
    return "${text}km"
}

private fun firstNonBlank(vararg values: String): String =
    values.firstOrNull { it.isNotBlank() }.orEmpty()

private fun firstPositiveInt(json: JSONObject, vararg names: String): Int =
    names.firstNotNullOfOrNull { name ->
        json.optInt(name, 0).takeIf { it > 0 }
    } ?: 0

private fun inferCue(title: String, type: String): DirectionCue {
    val combined = "$title $type".lowercase()
    return when {
        combined.contains("start") || combined.contains("\ucd9c\ubc1c") -> DirectionCue.START
        combined.contains("destination") ||
            combined.contains("\ub3c4\ucc29") ||
            combined.contains("\ubaa9\uc801\uc9c0") -> DirectionCue.DESTINATION
        combined.contains("left") ||
            combined.contains("\uc88c") ||
            combined.contains("\uc67c") -> DirectionCue.LEFT
        combined.contains("right") ||
            combined.contains("\uc6b0") ||
            combined.contains("\uc624\ub978") -> DirectionCue.RIGHT
        else -> DirectionCue.STRAIGHT
    }
}
