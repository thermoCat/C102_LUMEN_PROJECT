package com.ssafy.smartcane.lumen2.assist

import android.graphics.PointF
import com.ssafy.smartcane.lumen2.ar.ArFrameData

internal object AssistSemanticAnalyzer {
    private const val SAMPLE_STRIDE = 2
    private const val SCREEN_GRID_PX = 10f
    private const val MIN_ZONE_SAMPLES = 6
    private const val MIN_COMPONENT_CELLS = 1
    private const val MIN_COMPONENT_SPAN_PX = 0f
    private const val SIDE_FAN_INNER_DEG = 16f
    private const val SIDE_FAN_OUTER_DEG = 58f
    private const val SIDE_FAN_STEP_DEG = 6f

    fun analyzeCorridor(frame: ArFrameData, centerLateralMm: Float): SemanticCorridorEvidence? {
        return analyzeWithZones(frame, buildCorridorZones(frame, centerLateralMm))
    }

    fun analyzeSideFan(frame: ArFrameData, left: Boolean): SemanticCorridorEvidence? {
        return analyzeWithZones(frame, buildSideFanZones(frame, left))
    }

    private fun analyzeWithZones(
        frame: ArFrameData,
        zones: Map<SemanticDistanceZone, MutableZoneEvidence>
    ): SemanticCorridorEvidence? {
        val labels = frame.semanticLabels ?: return null
        if (frame.semanticWidth <= 0 || frame.semanticHeight <= 0) return null
        val mapping = AssistGeometry.mappingFrom(frame) ?: return null
        if (zones.values.none { it.polygon.size >= 3 }) return null

        val semanticToView = mapping.semanticToViewMatrix(frame)
        var y = 0
        while (y < frame.semanticHeight) {
            var x = 0
            while (x < frame.semanticWidth) {
                val assistCenter = mapping.semanticPixelCenterToView(semanticToView, x, y)
                val zone = zones.values.firstOrNull {
                    it.polygon.size >= 3 &&
                        AssistGeometry.containsPoint(it.polygon, assistCenter.x, assistCenter.y)
                }
                if (zone != null) {
                    val label = labels[y * frame.semanticWidth + x].toInt() and 0xff
                    zone.totalSamples++
                    val displayRect = mapping.semanticRectToDisplayView(
                        matrix = semanticToView,
                        left = x.toFloat(),
                        top = y.toFloat(),
                        right = (x + SAMPLE_STRIDE).coerceAtMost(frame.semanticWidth).toFloat(),
                        bottom = (y + SAMPLE_STRIDE).coerceAtMost(frame.semanticHeight).toFloat()
                    )
                    if (AssistSemanticLayer.isWalkable(label)) {
                        zone.walkableSamples += AssistObstaclePolygon(
                            displayPolygon = displayRect,
                            kind = AssistObstacleKind.WALKABLE
                        )
                    } else {
                        zone.blockedSamples += SemanticBlockedSample(
                            cellKey = AssistGeometry.cellKey(assistCenter, SCREEN_GRID_PX),
                            displayPolygon = displayRect,
                            kind = AssistSemanticLayer.obstacleKind(label)
                        )
                    }
                }
                x += SAMPLE_STRIDE
            }
            y += SAMPLE_STRIDE
        }

        return SemanticCorridorEvidence(
            immediate = zones.getValue(SemanticDistanceZone.IMMEDIATE).toEvidence(),
            near = zones.getValue(SemanticDistanceZone.NEAR).toEvidence(),
            plan = zones.getValue(SemanticDistanceZone.PLAN).toEvidence()
        )
    }

    private fun buildCorridorZones(
        frame: ArFrameData,
        centerLateralMm: Float
    ): Map<SemanticDistanceZone, MutableZoneEvidence> {
        return mapOf(
            SemanticDistanceZone.IMMEDIATE to MutableZoneEvidence(
                zone = SemanticDistanceZone.IMMEDIATE,
                polygon = buildScreenCorridorBand(
                    frame = frame,
                    centerLateralMm = centerLateralMm,
                    nearForwardMm = AssistConfig.ANCHOR_FORWARD_MM,
                    farForwardMm = AssistConfig.IMMEDIATE_ZONE_MM
                )
            ),
            SemanticDistanceZone.NEAR to MutableZoneEvidence(
                zone = SemanticDistanceZone.NEAR,
                polygon = buildScreenCorridorBand(
                    frame = frame,
                    centerLateralMm = centerLateralMm,
                    nearForwardMm = AssistConfig.IMMEDIATE_ZONE_MM,
                    farForwardMm = AssistConfig.NEAR_ZONE_MM
                )
            ),
            SemanticDistanceZone.PLAN to MutableZoneEvidence(
                zone = SemanticDistanceZone.PLAN,
                polygon = buildScreenCorridorBand(
                    frame = frame,
                    centerLateralMm = centerLateralMm,
                    nearForwardMm = AssistConfig.NEAR_ZONE_MM,
                    farForwardMm = AssistConfig.PLAN_DISTANCE_MM
                )
            )
        )
    }

    private fun buildSideFanZones(
        frame: ArFrameData,
        left: Boolean
    ): Map<SemanticDistanceZone, MutableZoneEvidence> {
        return mapOf(
            SemanticDistanceZone.IMMEDIATE to MutableZoneEvidence(
                zone = SemanticDistanceZone.IMMEDIATE,
                polygon = buildSideFanBand(
                    frame = frame,
                    left = left,
                    nearForwardMm = AssistConfig.ANCHOR_FORWARD_MM,
                    farForwardMm = AssistConfig.IMMEDIATE_ZONE_MM
                )
            ),
            SemanticDistanceZone.NEAR to MutableZoneEvidence(
                zone = SemanticDistanceZone.NEAR,
                polygon = buildSideFanBand(
                    frame = frame,
                    left = left,
                    nearForwardMm = AssistConfig.IMMEDIATE_ZONE_MM,
                    farForwardMm = AssistConfig.NEAR_ZONE_MM
                )
            ),
            SemanticDistanceZone.PLAN to MutableZoneEvidence(
                zone = SemanticDistanceZone.PLAN,
                polygon = emptyList()
            )
        )
    }

    private fun buildScreenCorridorBand(
        frame: ArFrameData,
        centerLateralMm: Float,
        nearForwardMm: Float,
        farForwardMm: Float
    ): List<PointF> {
        return AssistGeometry.guideCorridorBand(
            frame = frame,
            centerLateralMm = centerLateralMm,
            nearForwardMm = nearForwardMm,
            farForwardMm = farForwardMm,
            contentOnly = false
        )
    }

    private fun buildSideFanBand(
        frame: ArFrameData,
        left: Boolean,
        nearForwardMm: Float,
        farForwardMm: Float
    ): List<PointF> {
        val sign = if (left) -1f else 1f
        val near = if (nearForwardMm <= AssistConfig.ANCHOR_FORWARD_MM + 0.001f) {
            anchorStartEdge(frame, left)
        } else {
            fanArc(frame, sign, nearForwardMm).reversed()
        }
        val far = fanArc(frame, sign, farForwardMm)
        return near + far
    }

    private fun anchorStartEdge(frame: ArFrameData, left: Boolean): List<PointF> {
        val start = AssistConfig.ANCHOR_FORWARD_MM
        return if (left) {
            listOfNotNull(
                AssistGeometry.guidePointToView(frame, AssistConfig.USER_HALF_WIDTH_MM, start, contentOnly = false),
                AssistGeometry.guidePointToView(frame, -AssistConfig.USER_HALF_WIDTH_MM, start, contentOnly = false)
            )
        } else {
            listOfNotNull(
                AssistGeometry.guidePointToView(frame, -AssistConfig.USER_HALF_WIDTH_MM, start, contentOnly = false),
                AssistGeometry.guidePointToView(frame, AssistConfig.USER_HALF_WIDTH_MM, start, contentOnly = false)
            )
        }
    }

    private fun fanArc(frame: ArFrameData, sign: Float, radialMm: Float): List<PointF> {
        val points = mutableListOf<PointF>()
        var angle = SIDE_FAN_OUTER_DEG
        while (angle >= SIDE_FAN_INNER_DEG - 0.001f) {
            fanPoint(frame, sign * angle, radialMm)?.let(points::add)
            angle -= SIDE_FAN_STEP_DEG
        }
        return points
    }

    private fun fanPoint(frame: ArFrameData, headingDeg: Float, radialMm: Float): PointF? {
        val radians = Math.toRadians(headingDeg.toDouble())
        val lateralMm = (kotlin.math.sin(radians) * radialMm).toFloat()
        val forwardMm = (kotlin.math.cos(radians) * radialMm).toFloat()
        return AssistGeometry.guidePointToView(frame, lateralMm, forwardMm, contentOnly = false)
    }

    private fun MutableZoneEvidence.toEvidence(): SemanticZoneEvidence {
        val components = acceptedComponents(blockedSamples)
        return SemanticZoneEvidence(
            zone = zone,
            totalSamples = totalSamples,
            walkablePolygons = walkableSamples,
            components = components
        )
    }

    private fun acceptedComponents(samples: List<SemanticBlockedSample>): List<SemanticComponent> {
        if (samples.size < MIN_COMPONENT_CELLS) return emptyList()
        val byCell = samples.groupBy { it.cellKey }
        val remaining = byCell.keys.toMutableSet()
        val components = mutableListOf<SemanticComponent>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.first()
            val queue = ArrayDeque<Long>()
            val cells = mutableListOf<Long>()
            val componentSamples = mutableListOf<SemanticBlockedSample>()
            queue.add(seed)
            remaining.remove(seed)
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                cells += cell
                componentSamples += byCell[cell].orEmpty()
                val cx = AssistGeometry.unpackX(cell)
                val cy = AssistGeometry.unpackY(cell)
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val neighbor = AssistGeometry.packCell(cx + dx, cy + dy)
                        if (remaining.remove(neighbor)) queue.add(neighbor)
                    }
                }
            }
            val xs = cells.map(AssistGeometry::unpackX)
            val ys = cells.map(AssistGeometry::unpackY)
            val spanX = (xs.maxOrNull()!! - xs.minOrNull()!! + 1) * SCREEN_GRID_PX
            val spanY = (ys.maxOrNull()!! - ys.minOrNull()!! + 1) * SCREEN_GRID_PX
            if (cells.size >= MIN_COMPONENT_CELLS && maxOf(spanX, spanY) >= MIN_COMPONENT_SPAN_PX) {
                components += SemanticComponent(
                    cellCount = cells.size,
                    spanPx = maxOf(spanX, spanY),
                    polygons = componentSamples.map {
                        AssistObstaclePolygon(
                            displayPolygon = it.displayPolygon,
                            kind = it.kind
                        )
                    }
                )
            }
        }
        return components
    }

    private data class MutableZoneEvidence(
        val zone: SemanticDistanceZone,
        val polygon: List<PointF>,
        var totalSamples: Int = 0,
        val walkableSamples: MutableList<AssistObstaclePolygon> = mutableListOf(),
        val blockedSamples: MutableList<SemanticBlockedSample> = mutableListOf()
    )

    private data class SemanticBlockedSample(
        val cellKey: Long,
        val displayPolygon: List<PointF>,
        val kind: AssistObstacleKind
    )
}
