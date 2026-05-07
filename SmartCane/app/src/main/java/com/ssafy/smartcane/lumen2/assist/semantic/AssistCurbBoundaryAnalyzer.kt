package com.ssafy.smartcane.lumen2.assist

import android.graphics.PointF
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import kotlin.math.abs
import kotlin.math.hypot

internal object AssistCurbBoundaryAnalyzer {
    private const val SAMPLE_STRIDE = 2
    private const val SCREEN_GRID_PX = 18f
    private const val MIN_ROAD_SAMPLES = 6
    private const val MIN_SIDEWALK_SAMPLES = 6
    private const val MIN_BOUNDARY_CELLS = 2
    private const val BOUNDARY_DISTANCE_PX = 42f
    private const val NEARBY_BOUNDARY_CELL_RADIUS = 3

    fun analyze(frame: ArFrameData): CurbBoundaryEvidence {
        val labels = frame.semanticLabels ?: return CurbBoundaryEvidence(CurbBoundaryStatus.UNKNOWN, emptyList())
        if (frame.semanticWidth <= 0 || frame.semanticHeight <= 0) {
            return CurbBoundaryEvidence(CurbBoundaryStatus.UNKNOWN, emptyList())
        }
        val mapping = AssistGeometry.mappingFrom(frame)
            ?: return CurbBoundaryEvidence(CurbBoundaryStatus.UNKNOWN, emptyList())
        val corridor = AssistGeometry.guideCorridorBand(
            frame = frame,
            centerLateralMm = 0f,
            nearForwardMm = AssistConfig.ANCHOR_FORWARD_MM,
            farForwardMm = AssistConfig.PLAN_DISTANCE_MM,
            contentOnly = false
        )
        if (corridor.size < 3) return CurbBoundaryEvidence(CurbBoundaryStatus.UNKNOWN, emptyList())

        val semanticToView = mapping.semanticToViewMatrix(frame)
        val roadSamplesByCell = mutableMapOf<Long, MutableList<SemanticSample>>()
        val sidewalkSamplesByCell = mutableMapOf<Long, MutableList<PointF>>()
        var roadSamples = 0
        var sidewalkSamples = 0

        var y = 0
        while (y < frame.semanticHeight) {
            var x = 0
            while (x < frame.semanticWidth) {
                val center = mapping.semanticPixelCenterToView(semanticToView, x, y)
                if (AssistGeometry.containsPoint(corridor, center.x, center.y)) {
                    val label = labels[y * frame.semanticWidth + x].toInt() and 0xff
                    val key = AssistGeometry.cellKey(center, SCREEN_GRID_PX)
                    when (label) {
                        ROAD_LABEL -> {
                            roadSamples++
                            roadSamplesByCell.getOrPut(key) { mutableListOf() }.add(
                                SemanticSample(
                                    center = center,
                                    polygon = mapping.semanticRectToDisplayView(
                                        matrix = semanticToView,
                                        left = x.toFloat(),
                                        top = y.toFloat(),
                                        right = (x + SAMPLE_STRIDE).coerceAtMost(frame.semanticWidth).toFloat(),
                                        bottom = (y + SAMPLE_STRIDE).coerceAtMost(frame.semanticHeight).toFloat()
                                    )
                                )
                            )
                        }
                        SIDEWALK_LABEL -> {
                            sidewalkSamples++
                            sidewalkSamplesByCell.getOrPut(key) { mutableListOf() }.add(center)
                        }
                    }
                }
                x += SAMPLE_STRIDE
            }
            y += SAMPLE_STRIDE
        }

        if (roadSamples < MIN_ROAD_SAMPLES || sidewalkSamples < MIN_SIDEWALK_SAMPLES) {
            return CurbBoundaryEvidence(CurbBoundaryStatus.CLEAR, emptyList())
        }

        val boundaryPolygons = roadSamplesByCell.flatMap { (key, samples) ->
            samples.mapNotNull { sample ->
                if (touchesSidewalk(sample.center, key, sidewalkSamplesByCell)) {
                    AssistObstaclePolygon(
                        displayPolygon = sample.polygon,
                        kind = AssistObstacleKind.CURB_BOUNDARY
                    )
                } else {
                    null
                }
            }
        }

        val acceptedPolygons = if (boundaryPolygons.size >= MIN_BOUNDARY_CELLS) {
            boundaryPolygons
        } else if (roadSamples >= MIN_ROAD_SAMPLES && sidewalkSamples >= MIN_SIDEWALK_SAMPLES) {
            nearMixedBoundaryFallback(roadSamplesByCell, sidewalkSamplesByCell)
        } else {
            emptyList()
        }

        val status = if (acceptedPolygons.size >= MIN_BOUNDARY_CELLS) {
            CurbBoundaryStatus.DETECTED
        } else {
            CurbBoundaryStatus.CLEAR
        }
        return CurbBoundaryEvidence(status, acceptedPolygons)
    }

    private fun nearMixedBoundaryFallback(
        roadSamplesByCell: Map<Long, List<SemanticSample>>,
        sidewalkSamplesByCell: Map<Long, List<PointF>>
    ): List<AssistObstaclePolygon> {
        val roadKeys = roadSamplesByCell.keys
        val sidewalkKeys = sidewalkSamplesByCell.keys
        val hasNearbyCell = roadKeys.any { roadKey ->
            sidewalkKeys.any { sidewalkKey ->
                abs(AssistGeometry.unpackX(roadKey) - AssistGeometry.unpackX(sidewalkKey)) <= NEARBY_BOUNDARY_CELL_RADIUS &&
                    abs(AssistGeometry.unpackY(roadKey) - AssistGeometry.unpackY(sidewalkKey)) <= NEARBY_BOUNDARY_CELL_RADIUS
            }
        }
        if (!hasNearbyCell) return emptyList()
        return roadSamplesByCell.values
            .flatten()
            .take(MIN_BOUNDARY_CELLS)
            .map {
                AssistObstaclePolygon(
                    displayPolygon = it.polygon,
                    kind = AssistObstacleKind.CURB_BOUNDARY
                )
            }
    }

    private fun touchesSidewalk(
        roadCenter: PointF,
        key: Long,
        sidewalkSamplesByCell: Map<Long, List<PointF>>
    ): Boolean {
        val x = AssistGeometry.unpackX(key)
        val y = AssistGeometry.unpackY(key)
        for (dy in -NEARBY_BOUNDARY_CELL_RADIUS..NEARBY_BOUNDARY_CELL_RADIUS) {
            for (dx in -NEARBY_BOUNDARY_CELL_RADIUS..NEARBY_BOUNDARY_CELL_RADIUS) {
                if (abs(dx) + abs(dy) == 0) continue
                val nearby = sidewalkSamplesByCell[AssistGeometry.packCell(x + dx, y + dy)].orEmpty()
                if (nearby.any { point -> distance(roadCenter, point) <= BOUNDARY_DISTANCE_PX }) return true
            }
        }
        return false
    }

    private fun distance(a: PointF, b: PointF): Float {
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
    }

    private const val ROAD_LABEL = 4
    private const val SIDEWALK_LABEL = 5

    private data class SemanticSample(
        val center: PointF,
        val polygon: List<PointF>
    )
}
