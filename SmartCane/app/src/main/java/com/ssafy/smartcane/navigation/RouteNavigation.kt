package com.ssafy.smartcane.navigation

import android.location.Location
import com.ssafy.smartcane.network.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class RouteDeviationStatus {
    ON_ROUTE,
    NEAR_ROUTE,
    POSSIBLE_DEVIATION,
    OFF_ROUTE
}

data class RouteMatchResult(
    val snappedPoint: RoutePoint,
    val distanceToRouteMeters: Float,
    val traveledDistanceMeters: Float,
    val remainingDistanceMeters: Float,
    val status: RouteDeviationStatus
)

class LowPassLocationSmoother(
    private val alpha: Double = 0.25
) {
    private var previous: Location? = null

    fun smooth(location: Location): Location {
        val before = previous
        if (before == null) {
            previous = Location(location)
            return location
        }

        val smoothed = Location(location).apply {
            latitude = before.latitude + alpha * (location.latitude - before.latitude)
            longitude = before.longitude + alpha * (location.longitude - before.longitude)
            if (before.hasAccuracy() && location.hasAccuracy()) {
                accuracy = min(before.accuracy, location.accuracy)
            }
        }
        previous = smoothed
        return smoothed
    }

    fun reset() {
        previous = null
    }
}

object RouteNavigationMatcher {
    fun match(location: Location, routePoints: List<RoutePoint>): RouteMatchResult? {
        return match(
            location = RoutePoint(
                longitude = location.longitude,
                latitude = location.latitude
            ),
            routePoints = routePoints
        )
    }

    fun match(location: RoutePoint, routePoints: List<RoutePoint>): RouteMatchResult? {
        val points = routePoints.withoutConsecutiveDuplicates()
        if (points.size < 2) return null

        val cumulativeDistances = points.cumulativeDistances()
        var best: ProjectionResult? = null

        for (index in 0 until points.lastIndex) {
            val projection = projectToSegment(
                point = location,
                start = points[index],
                end = points[index + 1],
                segmentStartDistance = cumulativeDistances[index]
            )
            if (best == null || projection.distanceMeters < best.distanceMeters) {
                best = projection
            }
        }

        val projection = best ?: return null
        val totalDistance = cumulativeDistances.last()
        return RouteMatchResult(
            snappedPoint = projection.point,
            distanceToRouteMeters = projection.distanceMeters,
            traveledDistanceMeters = projection.distanceAlongRouteMeters,
            remainingDistanceMeters = max(0f, totalDistance - projection.distanceAlongRouteMeters),
            status = projection.distanceMeters.toDeviationStatus()
        )
    }
}

private data class ProjectionResult(
    val point: RoutePoint,
    val distanceMeters: Float,
    val distanceAlongRouteMeters: Float
)

private fun projectToSegment(
    point: RoutePoint,
    start: RoutePoint,
    end: RoutePoint,
    segmentStartDistance: Float
): ProjectionResult {
    val originLat = start.latitude
    val originLng = start.longitude
    val pointXY = point.toMeters(originLat, originLng)
    val startXY = start.toMeters(originLat, originLng)
    val endXY = end.toMeters(originLat, originLng)
    val dx = endXY.x - startXY.x
    val dy = endXY.y - startXY.y
    val segmentLengthSquared = dx * dx + dy * dy
    val t = if (segmentLengthSquared == 0.0) {
        0.0
    } else {
        (((pointXY.x - startXY.x) * dx + (pointXY.y - startXY.y) * dy) / segmentLengthSquared)
            .coerceIn(0.0, 1.0)
    }

    val projectedX = startXY.x + t * dx
    val projectedY = startXY.y + t * dy
    val projectedPoint = metersToRoutePoint(projectedX, projectedY, originLat, originLng)
    val distance = distanceMeters(point, projectedPoint)
    val segmentDistance = distanceMeters(start, end)

    return ProjectionResult(
        point = projectedPoint,
        distanceMeters = distance,
        distanceAlongRouteMeters = segmentStartDistance + segmentDistance * t.toFloat()
    )
}

private data class XY(val x: Double, val y: Double)

private fun RoutePoint.toMeters(originLat: Double, originLng: Double): XY {
    val earthRadius = 6_371_000.0
    val latRad = Math.toRadians(latitude)
    val originLatRad = Math.toRadians(originLat)
    val x = Math.toRadians(longitude - originLng) * earthRadius * cos(originLatRad)
    val y = (latRad - originLatRad) * earthRadius
    return XY(x, y)
}

private fun metersToRoutePoint(x: Double, y: Double, originLat: Double, originLng: Double): RoutePoint {
    val earthRadius = 6_371_000.0
    val originLatRad = Math.toRadians(originLat)
    return RoutePoint(
        longitude = originLng + Math.toDegrees(x / (earthRadius * cos(originLatRad))),
        latitude = originLat + Math.toDegrees(y / earthRadius)
    )
}

private fun List<RoutePoint>.cumulativeDistances(): List<Float> {
    val distances = mutableListOf(0f)
    var total = 0f
    for (index in 0 until lastIndex) {
        total += distanceMeters(this[index], this[index + 1])
        distances += total
    }
    return distances
}

private fun List<RoutePoint>.withoutConsecutiveDuplicates(): List<RoutePoint> =
    filterIndexed { index, point ->
        index == 0 || point != this[index - 1]
    }

private fun Float.toDeviationStatus(): RouteDeviationStatus =
    when {
        this <= 8f -> RouteDeviationStatus.ON_ROUTE
        this <= 20f -> RouteDeviationStatus.NEAR_ROUTE
        this <= 30f -> RouteDeviationStatus.POSSIBLE_DEVIATION
        else -> RouteDeviationStatus.OFF_ROUTE
    }

private fun distanceMeters(start: RoutePoint, end: RoutePoint): Float {
    val earthRadius = 6_371_000.0
    val startLat = Math.toRadians(start.latitude)
    val endLat = Math.toRadians(end.latitude)
    val deltaLat = Math.toRadians(end.latitude - start.latitude)
    val deltaLng = Math.toRadians(end.longitude - start.longitude)
    val a = sin(deltaLat / 2).pow(2.0) +
        cos(startLat) * cos(endLat) * sin(deltaLng / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toFloat()
}
