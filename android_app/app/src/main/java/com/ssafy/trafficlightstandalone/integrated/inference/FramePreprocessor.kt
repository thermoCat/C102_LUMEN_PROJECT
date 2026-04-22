package com.ssafy.trafficlightstandalone.integrated.inference

import androidx.camera.core.ImageProxy
import com.ssafy.trafficlightstandalone.integrated.model.CropRegion
import com.ssafy.trafficlightstandalone.integrated.model.LetterboxInfo
import com.ssafy.trafficlightstandalone.integrated.model.PreparedInput
import kotlin.math.min
import kotlin.math.roundToInt

class FramePreprocessor(
    private val inputSize: Int,
    private val letterboxFill: Float = 114f / 255f,
) {
    /**
     * Full-frame preprocessing with optional digital zoom.
     * digitalZoom > 1.0 crops the center (1/digitalZoom) of the frame, simulating zoom.
     */
    fun preprocess(image: ImageProxy, digitalZoom: Float = 1.0f): PreparedInput {
        val rotation = normalizeRotation(image)
        val (sourceWidth, sourceHeight) = rotatedSourceSize(image, rotation)
        val cropRegion = if (digitalZoom > 1.0f) {
            centerCropRegion(sourceWidth, sourceHeight, digitalZoom)
        } else {
            CropRegion(left = 0, top = 0, width = sourceWidth, height = sourceHeight)
        }
        return preprocessCrop(image, rotation, sourceWidth, sourceHeight, cropRegion)
    }

    /**
     * Top-crop preprocessing for 2-pass pipeline.
     * Crops the top [topRatio] of the digitally zoomed frame.
     */
    fun preprocessTopCrop(image: ImageProxy, topRatio: Float, digitalZoom: Float = 1.0f): PreparedInput {
        val rotation = normalizeRotation(image)
        val (sourceWidth, sourceHeight) = rotatedSourceSize(image, rotation)
        val baseCrop = if (digitalZoom > 1.0f) {
            centerCropRegion(sourceWidth, sourceHeight, digitalZoom)
        } else {
            CropRegion(left = 0, top = 0, width = sourceWidth, height = sourceHeight)
        }
        val cropHeight = (baseCrop.height * topRatio).toInt().coerceIn(1, baseCrop.height)
        val cropRegion = CropRegion(
            left = baseCrop.left,
            top = baseCrop.top,
            width = baseCrop.width,
            height = cropHeight,
        )
        return preprocessCrop(image, rotation, sourceWidth, sourceHeight, cropRegion)
    }

    private fun centerCropRegion(sourceWidth: Int, sourceHeight: Int, digitalZoom: Float): CropRegion {
        val cropWidth = (sourceWidth / digitalZoom).roundToInt().coerceIn(1, sourceWidth)
        val cropHeight = (sourceHeight / digitalZoom).roundToInt().coerceIn(1, sourceHeight)
        val cropLeft = (sourceWidth - cropWidth) / 2
        val cropTop = (sourceHeight - cropHeight) / 2
        return CropRegion(left = cropLeft, top = cropTop, width = cropWidth, height = cropHeight)
    }

    private fun preprocessCrop(
        image: ImageProxy,
        rotation: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        cropRegion: CropRegion,
    ): PreparedInput {
        val scale = min(
            inputSize.toFloat() / cropRegion.width.toFloat(),
            inputSize.toFloat() / cropRegion.height.toFloat(),
        )
        val scaledWidth = cropRegion.width * scale
        val scaledHeight = cropRegion.height * scale
        val padX = (inputSize - scaledWidth) / 2f
        val padY = (inputSize - scaledHeight) / 2f

        val output = FloatArray(inputSize * inputSize * 3) { letterboxFill }
        val plane = image.planes[0]
        val rgbaBuffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val cropX = ((x + 0.5f - padX) / scale).toInt()
                val cropY = ((y + 0.5f - padY) / scale).toInt()
                if (cropX !in 0 until cropRegion.width || cropY !in 0 until cropRegion.height) {
                    continue
                }
                val rotatedX = cropRegion.left + cropX
                val rotatedY = cropRegion.top + cropY

                val (sourceX, sourceY) = mapRotatedToSource(
                    rotatedX = rotatedX,
                    rotatedY = rotatedY,
                    rotationDegrees = rotation,
                    imageWidth = image.width,
                    imageHeight = image.height,
                )
                if (sourceX !in 0 until image.width || sourceY !in 0 until image.height) {
                    continue
                }

                val offset = sourceY * rowStride + sourceX * pixelStride
                val dstIndex = (y * inputSize + x) * 3
                output[dstIndex] = (rgbaBuffer.get(offset).toInt() and 0xFF) / 255f
                output[dstIndex + 1] = (rgbaBuffer.get(offset + 1).toInt() and 0xFF) / 255f
                output[dstIndex + 2] = (rgbaBuffer.get(offset + 2).toInt() and 0xFF) / 255f
            }
        }

        return PreparedInput(
            input = output,
            letterbox = LetterboxInfo(
                inputSize = inputSize,
                scale = scale,
                padX = padX,
                padY = padY,
                originalWidth = cropRegion.width,
                originalHeight = cropRegion.height,
            ),
            cropRegion = cropRegion,
        )
    }

    private fun normalizeRotation(image: ImageProxy): Int =
        ((image.imageInfo.rotationDegrees % 360) + 360) % 360

    private fun rotatedSourceSize(image: ImageProxy, rotation: Int): Pair<Int, Int> {
        val w = if (rotation == 90 || rotation == 270) image.height else image.width
        val h = if (rotation == 90 || rotation == 270) image.width else image.height
        return w to h
    }

    private fun mapRotatedToSource(
        rotatedX: Int,
        rotatedY: Int,
        rotationDegrees: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): Pair<Int, Int> = when (rotationDegrees) {
        90 -> rotatedY to (imageHeight - 1 - rotatedX)
        180 -> (imageWidth - 1 - rotatedX) to (imageHeight - 1 - rotatedY)
        270 -> (imageWidth - 1 - rotatedY) to rotatedX
        else -> rotatedX to rotatedY
    }
}
