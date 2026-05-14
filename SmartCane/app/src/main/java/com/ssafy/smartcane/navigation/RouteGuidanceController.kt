package com.ssafy.smartcane.navigation

import com.ssafy.smartcane.network.DirectionCue
import com.ssafy.smartcane.network.WalkingRoutePlan
import com.ssafy.smartcane.network.formatDistance
import kotlin.math.abs

enum class RouteGuidanceEventType {
    START,
    PRE_TURN,
    OFF_ROUTE,
    ARRIVAL
}

data class RouteGuidanceEvent(
    val type: RouteGuidanceEventType,
    val cue: DirectionCue,
    val stepsRemaining: Int,
    val distanceMeters: Float,
    val message: String
)

class RouteGuidanceController {
    private var activeRouteKey = ""
    private var startAnnounced = false
    private var offRouteAnnounced = false
    private var arrivalAnnounced = false
    private var lastInstructionIndex = -1

    fun update(
        routePlan: WalkingRoutePlan?,
        routeMatch: RouteMatchResult?
    ): RouteGuidanceEvent? {
        val plan = routePlan ?: return null
        val match = routeMatch ?: return null
        val routeKey = plan.routeKey()
        if (routeKey != activeRouteKey) {
            activeRouteKey = routeKey
            startAnnounced = false
            offRouteAnnounced = false
            arrivalAnnounced = false
            lastInstructionIndex = -1
        }

        if (match.status == RouteDeviationStatus.OFF_ROUTE) {
            if (offRouteAnnounced) return null
            offRouteAnnounced = true
            return RouteGuidanceEvent(
                type = RouteGuidanceEventType.OFF_ROUTE,
                cue = DirectionCue.STRAIGHT,
                stepsRemaining = plan.instructions.size,
                distanceMeters = match.distanceToRouteMeters,
                message = "경로를 벗어났습니다. 새 경로를 확인합니다."
            )
        }
        offRouteAnnounced = false

        if (match.remainingDistanceMeters <= ARRIVAL_DISTANCE_METERS) {
            if (arrivalAnnounced) return null
            arrivalAnnounced = true
            return RouteGuidanceEvent(
                type = RouteGuidanceEventType.ARRIVAL,
                cue = DirectionCue.DESTINATION,
                stepsRemaining = 0,
                distanceMeters = match.remainingDistanceMeters,
                message = "목적지에 거의 도착했습니다."
            )
        }

        if (!startAnnounced) {
            startAnnounced = true
            return RouteGuidanceEvent(
                type = RouteGuidanceEventType.START,
                cue = plan.instructions.firstOrNull()?.cue ?: DirectionCue.STRAIGHT,
                stepsRemaining = plan.instructions.size,
                distanceMeters = match.remainingDistanceMeters,
                message = "경로 안내를 시작합니다."
            )
        }

        val instructionIndex = plan.currentInstructionIndex(match.traveledDistanceMeters)
        if (instructionIndex == lastInstructionIndex) return null
        lastInstructionIndex = instructionIndex

        val instruction = plan.instructions.getOrNull(instructionIndex) ?: return null
        val instructionStart = plan.instructionStartDistance(instructionIndex)
        val distanceToInstruction = (instructionStart - match.traveledDistanceMeters)
            .coerceAtLeast(0f)
        if (distanceToInstruction > PRE_TURN_DISTANCE_METERS) return null

        return RouteGuidanceEvent(
            type = RouteGuidanceEventType.PRE_TURN,
            cue = instruction.cue,
            stepsRemaining = (plan.instructions.size - instructionIndex).coerceAtLeast(0),
            distanceMeters = distanceToInstruction,
            message = buildInstructionMessage(instruction.title, distanceToInstruction)
        )
    }

    private fun WalkingRoutePlan.currentInstructionIndex(traveledDistanceMeters: Float): Int {
        if (instructions.isEmpty()) return -1
        var cumulative = 0f
        instructions.forEachIndexed { index, instruction ->
            cumulative += instruction.distanceMeters.toFloat().coerceAtLeast(0f)
            if (traveledDistanceMeters <= cumulative + PRE_TURN_DISTANCE_METERS) {
                return index
            }
        }
        return instructions.lastIndex
    }

    private fun WalkingRoutePlan.instructionStartDistance(index: Int): Float {
        if (index <= 0) return 0f
        return instructions
            .take(index)
            .sumOf { it.distanceMeters.coerceAtLeast(0) }
            .toFloat()
    }

    private fun WalkingRoutePlan.routeKey(): String =
        "${distanceMeters}:${durationSeconds}:${points.size}:${instructions.size}"

    private fun buildInstructionMessage(title: String, distanceMeters: Float): String {
        val distance = distanceMeters.toInt()
        return if (abs(distanceMeters) <= 1f) {
            title
        } else {
            "${formatDistance(distance)} 앞, $title"
        }
    }

    private companion object {
        private const val PRE_TURN_DISTANCE_METERS = 25f
        private const val ARRIVAL_DISTANCE_METERS = 7f
    }
}
