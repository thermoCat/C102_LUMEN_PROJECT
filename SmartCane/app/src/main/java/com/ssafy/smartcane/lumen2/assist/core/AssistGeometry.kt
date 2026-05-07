package com.ssafy.smartcane.lumen2.assist

import android.graphics.Matrix
import android.graphics.PointF
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import com.ssafy.smartcane.lumen2.ar.DisplayUvMapper
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object AssistGeometry {
    fun mappingFrom(frame: ArFrameData): AssistFrameMapping? {
        val imageWidth = frame.intrinsics.imageWidth.takeIf { it > 0 } ?: return null
        val imageHeight = frame.intrinsics.imageHeight.takeIf { it > 0 } ?: return null
        val matrix = DisplayUvMapper.imageToViewMatrix(
            displayUvCoords = frame.displayUvCoords,
            imageWidth = imageWidth.toFloat(),
            imageHeight = imageHeight.toFloat(),
            viewWidth = frame.viewWidth.toFloat(),
            viewHeight = frame.viewHeight.toFloat()
        )
        return AssistFrameMapping(matrix)
    }

    fun imageToViewMatrix(
        displayUvCoords: FloatArray,
        imageWidth: Float,
        imageHeight: Float,
        viewWidth: Float,
        viewHeight: Float
    ): Matrix {
        return DisplayUvMapper.imageToViewMatrix(displayUvCoords, imageWidth, imageHeight, viewWidth, viewHeight)
    }

    fun rectToView(matrix: Matrix, left: Float, top: Float, right: Float, bottom: Float): List<PointF> {
        return listOf(
            PointF(left, top),
            PointF(right, top),
            PointF(right, bottom),
            PointF(left, bottom)
        ).map { point ->
            val mapped = floatArrayOf(point.x, point.y)
            matrix.mapPoints(mapped)
            PointF(mapped[0], mapped[1])
        }
    }

    private fun guideProjectionToView(frame: ArFrameData, point: PointF): PointF {
        val width = frame.viewWidth.toFloat().coerceAtLeast(1f)
        val height = frame.viewHeight.toFloat().coerceAtLeast(1f)
        if (width >= height) return point
        val nx = point.x / width
        val ny = point.y / height
        return PointF(ny * width, (1f - nx) * height)
    }

    fun insideView(frame: ArFrameData, point: PointF): Boolean {
        return point.x in 0f..frame.viewWidth.toFloat() &&
            point.y in 0f..frame.viewHeight.toFloat()
    }

    fun centerOf(points: List<PointF>): PointF {
        if (points.isEmpty()) return PointF()
        return PointF(
            points.sumOf { it.x.toDouble() }.toFloat() / points.size.toFloat(),
            points.sumOf { it.y.toDouble() }.toFloat() / points.size.toFloat()
        )
    }

    fun guidePointToView(
        frame: ArFrameData,
        lateralMm: Float,
        forwardMm: Float,
        contentOnly: Boolean
    ): PointF? {
        val imagePoint = bodyPointToImage(frame, lateralMm, scaledGuideForwardMm(forwardMm)) ?: return null
        val displayPoint = mappingFrom(frame)
            ?.sourcePointToDisplayView(PointF(imagePoint.first, imagePoint.second))
            ?: return null
        val point = guideProjectionToView(frame, displayPoint)
        if (contentOnly && !insideView(frame, point)) return null
        return point
    }

    private fun scaledGuideForwardMm(forwardMm: Float): Float {
        val anchor = AssistConfig.ANCHOR_FORWARD_MM
        return anchor + (forwardMm - anchor) * AssistConfig.GUIDE_FORWARD_SCALE
    }

    fun guideCorridorBand(
        frame: ArFrameData,
        centerLateralMm: Float,
        nearForwardMm: Float,
        farForwardMm: Float,
        contentOnly: Boolean = true
    ): List<PointF> {
        return listOfNotNull(
            guidePointToView(frame, centerLateralMm - AssistConfig.USER_HALF_WIDTH_MM, nearForwardMm, contentOnly),
            guidePointToView(frame, centerLateralMm + AssistConfig.USER_HALF_WIDTH_MM, nearForwardMm, contentOnly),
            guidePointToView(frame, centerLateralMm + AssistConfig.USER_HALF_WIDTH_MM, farForwardMm, contentOnly),
            guidePointToView(frame, centerLateralMm - AssistConfig.USER_HALF_WIDTH_MM, farForwardMm, contentOnly)
        )
    }

    fun containsPoint(polygon: List<PointF>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val denominator = previous.y - current.y
            val safeDenominator = when {
                abs(denominator) >= 1e-4f -> denominator
                denominator < 0f -> -1e-4f
                else -> 1e-4f
            }
            val intersects = ((current.y > y) != (previous.y > y)) &&
                (x < (previous.x - current.x) * (y - current.y) / safeDenominator + current.x)
            if (intersects) inside = !inside
            previous = current
        }
        return inside
    }

    fun cellKey(point: PointF, cellSizePx: Float): Long {
        return packCell((point.x / cellSizePx).toInt(), (point.y / cellSizePx).toInt())
    }

    fun packCell(x: Int, y: Int): Long {
        return (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    }

    fun unpackX(key: Long): Int = (key shr 32).toInt()

    fun unpackY(key: Long): Int = key.toInt()

    fun projectedPitchDeg(frame: ArFrameData): Float {
        return cameraDownPitchDegrees(frame.poseQuaternion).coerceIn(1f, 82f)
    }

    private fun cameraDownPitchDegrees(q: FloatArray): Float {
        if (q.size < 4) return AssistConfig.DEFAULT_MOUNT_PITCH_DEG
        val x = q[0]
        val y = q[1]
        val z = q[2]
        val w = q[3]

        val m02 = 2f * (x * z + w * y)
        val m12 = 2f * (y * z - w * x)
        val m22 = 1f - 2f * (x * x + y * y)

        val forwardX = -m02
        val forwardY = -m12
        val forwardZ = -m22
        val horizontal = sqrt(forwardX * forwardX + forwardZ * forwardZ).coerceAtLeast(1e-4f)
        val downPitch = Math.toDegrees(atan2((-forwardY).toDouble(), horizontal.toDouble())).toFloat()
        return downPitch + AssistConfig.DEFAULT_MOUNT_PITCH_DEG
    }

    private fun bodyPointToImage(frame: ArFrameData, lateralMm: Float, forwardMm: Float): Pair<Float, Float>? {
        if (forwardMm <= 0f) return null
        val pitch = Math.toRadians(projectedPitchDeg(frame).toDouble()).toFloat()
        val downMm = AssistConfig.CAMERA_HEIGHT_MM
        val depthMm = forwardMm * cos(pitch) + downMm * sin(pitch)
        val cameraY = downMm * cos(pitch) - forwardMm * sin(pitch)
        if (depthMm <= 1f) return null
        val intrinsics = frame.intrinsics
        val imageX = intrinsics.cx + (lateralMm / depthMm) * intrinsics.fx
        val imageY = intrinsics.cy + (cameraY / depthMm) * intrinsics.fy
        return imageX to imageY
    }

}

internal class AssistFrameMapping(
    private val sourceToView: Matrix
) {
    fun sourcePointToDisplayView(point: PointF): PointF? {
        val mapped = floatArrayOf(point.x, point.y)
        sourceToView.mapPoints(mapped)
        if (!mapped[0].isFinite() || !mapped[1].isFinite()) return null
        return PointF(mapped[0], mapped[1])
    }

    fun semanticToViewMatrix(frame: ArFrameData): Matrix {
        return AssistGeometry.imageToViewMatrix(
            displayUvCoords = frame.displayUvCoords,
            imageWidth = frame.semanticWidth.toFloat().coerceAtLeast(1f),
            imageHeight = frame.semanticHeight.toFloat().coerceAtLeast(1f),
            viewWidth = frame.viewWidth.toFloat(),
            viewHeight = frame.viewHeight.toFloat()
        )
    }

    fun semanticPixelCenterToView(matrix: Matrix, x: Int, y: Int): PointF {
        val mapped = floatArrayOf(x + 0.5f, y + 0.5f)
        matrix.mapPoints(mapped)
        return PointF(mapped[0], mapped[1])
    }

    fun semanticRectToDisplayView(matrix: Matrix, left: Float, top: Float, right: Float, bottom: Float): List<PointF> {
        return AssistGeometry.rectToView(matrix, left, top, right, bottom)
    }
}
