package com.ssafy.smartcane.lumen2.assist

import kotlin.math.max
import kotlin.math.min

internal class AssistTrafficStabilizer {
    private companion object {
        private const val HOLD_MILLIS = 900L
        private const val SMOOTHING_ALPHA = 0.45f
    }

    private var lastEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
    private var lastUpdatedAt = 0L

    fun stabilize(evidence: TrafficSceneEvidence, nowMillis: Long): TrafficSceneEvidence {
        if (evidence.detections.isEmpty()) {
            return if (lastEvidence.detections.isNotEmpty() && nowMillis - lastUpdatedAt <= HOLD_MILLIS) {
                lastEvidence
            } else {
                lastEvidence = evidence
                evidence
            }
        }

        val smoothed = evidence.detections.map { detection ->
            val previous = lastEvidence.detections
                .filter { it.label == detection.label }
                .maxByOrNull { iou(it, detection) }
            if (previous != null && iou(previous, detection) > 0.20f) {
                detection.blendWith(previous)
            } else {
                detection
            }
        }
        val stabilized = evidence.copy(
            status = sceneStatus(smoothed),
            detections = smoothed
        )
        lastEvidence = stabilized
        lastUpdatedAt = nowMillis
        return stabilized
    }

    private fun TrafficDetection.blendWith(previous: TrafficDetection): TrafficDetection {
        return copy(
            left = blend(previous.left, left),
            top = blend(previous.top, top),
            right = blend(previous.right, right),
            bottom = blend(previous.bottom, bottom),
            confidence = max(previous.confidence * 0.85f, confidence)
        )
    }

    private fun blend(previous: Float, current: Float): Float {
        return previous * (1f - SMOOTHING_ALPHA) + current * SMOOTHING_ALPHA
    }

    private fun sceneStatus(detections: List<TrafficDetection>): TrafficSceneStatus {
        val hasCrosswalk = detections.any { it.label == TrafficDetectionLabel.CROSSWALK }
        val hasGreen = detections.any { it.label == TrafficDetectionLabel.GREEN_LIGHT }
        val hasRed = detections.any { it.label == TrafficDetectionLabel.RED_LIGHT }
        return when {
            hasCrosswalk && hasRed -> TrafficSceneStatus.RED_LIGHT
            hasCrosswalk && hasGreen -> TrafficSceneStatus.GREEN_LIGHT
            hasCrosswalk -> TrafficSceneStatus.CROSSWALK
            else -> TrafficSceneStatus.CLEAR
        }
    }

    private fun iou(a: TrafficDetection, b: TrafficDetection): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val areaA = max(0f, a.right - a.left) * max(0f, a.bottom - a.top)
        val areaB = max(0f, b.right - b.left) * max(0f, b.bottom - b.top)
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
