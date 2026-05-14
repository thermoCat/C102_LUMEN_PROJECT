package com.ssafy.smartcane.lumen2.assist

import android.graphics.PointF
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import kotlin.math.cos
import kotlin.math.sin

internal object AssistVisualizationBuilder {
    private const val MIN_HEADING_DEG = -35f
    private const val MAX_HEADING_DEG = 35f
    private const val SELECTED_STEP_MM = 100f
    private const val ARC_STEP_DEG = 2f
    private const val MIN_DISTANCE_ARC_VISIBLE_RATIO = 0.25f

    fun build(
        frame: ArFrameData,
        semanticEvidence: SemanticCorridorEvidence?,
        curbBoundary: CurbBoundaryEvidence,
        trafficEvidence: TrafficSceneEvidence
    ): AssistVisualization? {
        val pathForwardMm = AssistConfig.PLAN_DISTANCE_MM
        val bodyForwardMm = minOf(pathForwardMm, AssistConfig.NEAR_ZONE_MM)
        val raw = AssistVisualization(
            anchorBarPolygon = buildAnchorBar(frame),
            distanceCurves = listOfNotNull(
                buildDistanceCurve(frame, AssistConfig.IMMEDIATE_ZONE_MM, AssistConfig.IMMEDIATE_LABEL),
                buildDistanceCurve(frame, AssistConfig.NEAR_ZONE_MM, AssistConfig.NEAR_LABEL),
                buildDistanceCurve(frame, AssistConfig.PLAN_DISTANCE_MM, AssistConfig.PLAN_LABEL)
            ),
            depthSectionLines = emptyList(),
            selectedSafetyPolygon = buildStraightCorridorPolygon(frame, AssistConfig.USER_HALF_WIDTH_MM, pathForwardMm, false),
            selectedBodyPolygon = buildStraightCorridorPolygon(frame, AssistConfig.USER_HALF_WIDTH_MM, bodyForwardMm, false),
            selectedCenterLine = buildStraightCenterLine(frame, pathForwardMm, false),
            obstaclePolygons = emptyList(),
            trafficDetections = trafficEvidence.detections
        )
        return raw.copy(
            obstaclePolygons = semanticEvidence.orEmptyObstaclePolygons() +
                curbBoundary.polygons
        )
    }

    private fun buildAnchorBar(frame: ArFrameData): List<PointF> {
        return buildProjectedCorridorPolygon(
            frame = frame,
            nearCenterOffsetMm = 0f,
            farCenterOffsetMm = 0f,
            nearForwardMm = AssistConfig.ANCHOR_FORWARD_MM,
            farForwardMm = AssistConfig.ANCHOR_FORWARD_MM + 40f,
            halfWidthMm = AssistConfig.USER_HALF_WIDTH_MM,
            contentOnly = false
        )
    }

    private fun buildDistanceCurve(
        frame: ArFrameData,
        radialDistanceMm: Float,
        label: String
    ): AssistDistanceCurve? {
        val points = mutableListOf<PointF>()
        var heading = MIN_HEADING_DEG
        while (heading <= MAX_HEADING_DEG + 0.001f) {
            val radians = Math.toRadians(heading.toDouble())
            val forwardMm = (cos(radians) * radialDistanceMm).toFloat()
            val lateralMm = (sin(radians) * radialDistanceMm).toFloat()
            projectBodyPoint(frame, lateralMm, forwardMm, false)?.let(points::add)
            heading += ARC_STEP_DEG
        }
        val visiblePoints = points.filter { AssistGeometry.insideView(frame, it) }
        val centerVisible = projectBodyPoint(frame, 0f, radialDistanceMm, false)
            ?.let { AssistGeometry.insideView(frame, it) } ?: false
        val visibleRatio = visiblePoints.size.toFloat() / points.size.coerceAtLeast(1).toFloat()
        return if (centerVisible &&
            visiblePoints.size >= 3 &&
            visibleRatio >= MIN_DISTANCE_ARC_VISIBLE_RATIO
        ) {
            AssistDistanceCurve(label, visiblePoints)
        } else {
            null
        }
    }

    private fun buildStraightCorridorPolygon(
        frame: ArFrameData,
        halfWidthMm: Float,
        farForwardMm: Float,
        contentOnly: Boolean
    ): List<PointF> {
        val left = mutableListOf<PointF>()
        val right = mutableListOf<PointF>()
        var forwardMm = AssistConfig.ANCHOR_FORWARD_MM
        while (forwardMm <= farForwardMm + 0.001f) {
            projectBodyPoint(frame, -halfWidthMm, forwardMm, contentOnly)?.let(left::add)
            projectBodyPoint(frame, halfWidthMm, forwardMm, contentOnly)?.let(right::add)
            forwardMm += SELECTED_STEP_MM
        }
        if (left.size < 2 || right.size < 2) return emptyList()
        return left + right.reversed()
    }

    private fun buildStraightCenterLine(
        frame: ArFrameData,
        farForwardMm: Float,
        contentOnly: Boolean
    ): List<PointF> {
        val points = mutableListOf<PointF>()
        var forwardMm = AssistConfig.ANCHOR_FORWARD_MM
        while (forwardMm <= farForwardMm + 0.001f) {
            projectBodyPoint(frame, 0f, forwardMm, contentOnly)?.let(points::add)
            forwardMm += SELECTED_STEP_MM
        }
        return points
    }

    private fun buildProjectedCorridorPolygon(
        frame: ArFrameData,
        nearCenterOffsetMm: Float,
        farCenterOffsetMm: Float,
        nearForwardMm: Float,
        farForwardMm: Float,
        halfWidthMm: Float,
        contentOnly: Boolean
    ): List<PointF> {
        return listOfNotNull(
            projectBodyPoint(frame, nearCenterOffsetMm - halfWidthMm, nearForwardMm, contentOnly),
            projectBodyPoint(frame, nearCenterOffsetMm + halfWidthMm, nearForwardMm, contentOnly),
            projectBodyPoint(frame, farCenterOffsetMm + halfWidthMm, farForwardMm, contentOnly),
            projectBodyPoint(frame, farCenterOffsetMm - halfWidthMm, farForwardMm, contentOnly)
        )
    }

    private fun projectBodyPoint(
        frame: ArFrameData,
        lateralMm: Float,
        forwardMm: Float,
        contentOnly: Boolean
    ): PointF? {
        return AssistGeometry.guidePointToView(frame, lateralMm, forwardMm, contentOnly)
    }

    private fun SemanticCorridorEvidence?.orEmptyObstaclePolygons(): List<AssistObstaclePolygon> {
        return this?.obstaclePolygons().orEmpty()
    }

}
