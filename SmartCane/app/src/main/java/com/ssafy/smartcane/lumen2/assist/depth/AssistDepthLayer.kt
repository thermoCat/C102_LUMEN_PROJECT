package com.ssafy.smartcane.lumen2.assist

import android.graphics.PointF
import com.ssafy.smartcane.lumen2.ar.ArFrameData

internal object AssistDepthLayer {
    private const val DEPTH_STRIDE = 4
    private const val SCREEN_CELL_PX = 12f
    private const val MIN_VALID_DEPTH_MM = 150
    private const val MAX_VALID_DEPTH_MM = 6000
    private const val CLOSE_OBJECT_MAX_MM = 950
    private const val RELATIVE_OBJECT_MAX_MM = 2200
    private const val RELATIVE_DROP_MM = 500
    private const val MIN_ROW_VALUES = 4
    private const val MIN_COMPONENT_CANDIDATES = 3

    fun analyze(frame: ArFrameData): DepthLayerResult {
        val sectionLines = buildSectionLines(frame)
        val depths = frame.depthMillimeters ?: return DepthLayerResult(
            status = DepthAnomalyStatus.UNKNOWN,
            reason = null,
            sectionLines = sectionLines,
            obstaclePolygons = emptyList()
        )
        if (frame.depthWidth <= 0 || frame.depthHeight <= 0 || depths.isEmpty()) {
            return DepthLayerResult(
                status = DepthAnomalyStatus.UNKNOWN,
                reason = null,
                sectionLines = sectionLines,
                obstaclePolygons = emptyList()
            )
        }

        val depthToView = AssistGeometry.imageToViewMatrix(
            displayUvCoords = frame.displayUvCoords,
            imageWidth = frame.depthWidth.toFloat(),
            imageHeight = frame.depthHeight.toFloat(),
            viewWidth = frame.viewWidth.toFloat(),
            viewHeight = frame.viewHeight.toFloat()
        )
        val candidates = mutableListOf<DepthCandidate>()

        var y = 0
        while (y < frame.depthHeight) {
            val rowValues = ArrayList<Int>(frame.depthWidth / DEPTH_STRIDE + 1)
            var x = 0
            while (x < frame.depthWidth) {
                val mm = depths[y * frame.depthWidth + x].toInt() and 0xffff
                if (mm in MIN_VALID_DEPTH_MM..MAX_VALID_DEPTH_MM) rowValues += mm
                x += DEPTH_STRIDE
            }
            if (rowValues.size >= MIN_ROW_VALUES) {
                rowValues.sort()
                val rowMedian = rowValues[rowValues.size / 2]
                x = 0
                while (x < frame.depthWidth) {
                    val mm = depths[y * frame.depthWidth + x].toInt() and 0xffff
                    val closeObject = mm in MIN_VALID_DEPTH_MM..CLOSE_OBJECT_MAX_MM
                    val unusuallyClose = mm in MIN_VALID_DEPTH_MM..RELATIVE_OBJECT_MAX_MM &&
                        rowMedian - mm > RELATIVE_DROP_MM
                    if (closeObject || unusuallyClose) {
                        val polygon = AssistGeometry.rectToView(
                            matrix = depthToView,
                            left = x.toFloat(),
                            top = y.toFloat(),
                            right = (x + DEPTH_STRIDE).coerceAtMost(frame.depthWidth).toFloat(),
                            bottom = (y + DEPTH_STRIDE).coerceAtMost(frame.depthHeight).toFloat()
                        )
                        val center = AssistGeometry.centerOf(polygon)
                        if (AssistGeometry.insideView(frame, center)) {
                            candidates += DepthCandidate(
                                cellKey = AssistGeometry.cellKey(center, SCREEN_CELL_PX),
                                section = sectionFor(frame, center),
                                polygon = polygon
                            )
                        }
                    }
                    x += DEPTH_STRIDE
                }
            }
            y += DEPTH_STRIDE
        }

        val accepted = acceptedComponents(candidates)
        val sections = accepted.map { it.section }.toSet()
        val status = when {
            accepted.isEmpty() -> DepthAnomalyStatus.CLEAR
            sections.size > 1 -> DepthAnomalyStatus.MULTIPLE
            0 in sections -> DepthAnomalyStatus.LEFT
            1 in sections -> DepthAnomalyStatus.CENTER
            else -> DepthAnomalyStatus.RIGHT
        }
        return DepthLayerResult(
            status = status,
            reason = reasonFor(status),
            sectionLines = sectionLines,
            obstaclePolygons = accepted.map {
                AssistObstaclePolygon(
                    displayPolygon = it.polygon,
                    kind = AssistObstacleKind.DEPTH_ANOMALY
                )
            }
        )
    }

    private fun acceptedComponents(candidates: List<DepthCandidate>): List<DepthCandidate> {
        if (candidates.size < MIN_COMPONENT_CANDIDATES) return emptyList()
        val byCell = candidates.associateBy { it.cellKey }
        val remaining = byCell.keys.toMutableSet()
        val accepted = mutableListOf<DepthCandidate>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.first()
            val queue = ArrayDeque<Long>()
            val component = mutableListOf<DepthCandidate>()
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            queue.add(seed)
            remaining.remove(seed)
            while (queue.isNotEmpty()) {
                val key = queue.removeFirst()
                val candidate = byCell[key] ?: continue
                component += candidate
                val cx = AssistGeometry.unpackX(key)
                val cy = AssistGeometry.unpackY(key)
                minX = minOf(minX, cx)
                maxX = maxOf(maxX, cx)
                minY = minOf(minY, cy)
                maxY = maxOf(maxY, cy)
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                            val neighbor = AssistGeometry.packCell(cx + dx, cy + dy)
                        if (remaining.remove(neighbor)) queue.add(neighbor)
                    }
                }
            }
            val spanX = maxX - minX + 1
            val spanY = maxY - minY + 1
            val groundBandLike = spanX > spanY * 3 && spanX > 10
            if (component.size in MIN_COMPONENT_CANDIDATES..160 &&
                spanX in 1..22 &&
                spanY in 1..28 &&
                !groundBandLike
            ) {
                accepted += component
            }
        }
        return accepted
    }

    private fun buildSectionLines(frame: ArFrameData): List<List<PointF>> {
        val height = frame.viewHeight.toFloat()
        val width = frame.viewWidth.toFloat()
        return listOf(width / 3f, width * 2f / 3f).map { x ->
            listOf(PointF(x, 0f), PointF(x, height))
        }
    }

    private fun sectionFor(frame: ArFrameData, point: PointF): Int {
        return (point.x * 3f / frame.viewWidth.toFloat().coerceAtLeast(1f)).toInt().coerceIn(0, 2)
    }

    private fun reasonFor(status: DepthAnomalyStatus): String? {
        return when (status) {
            DepthAnomalyStatus.LEFT -> "depth anomaly left"
            DepthAnomalyStatus.CENTER -> "depth anomaly center"
            DepthAnomalyStatus.RIGHT -> "depth anomaly right"
            DepthAnomalyStatus.MULTIPLE -> "depth anomaly multiple"
            DepthAnomalyStatus.CLEAR,
            DepthAnomalyStatus.UNKNOWN -> null
        }
    }

    private data class DepthCandidate(
        val cellKey: Long,
        val section: Int,
        val polygon: List<PointF>
    )
}

internal data class DepthLayerResult(
    val status: DepthAnomalyStatus,
    val reason: String?,
    val sectionLines: List<List<PointF>>,
    val obstaclePolygons: List<AssistObstaclePolygon>
)
