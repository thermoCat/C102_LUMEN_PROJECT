package com.ssafy.smartcane.segformer.inference

import androidx.camera.core.ImageProxy
import com.ssafy.smartcane.segformer.model.LetterboxInfo
import com.ssafy.smartcane.segformer.model.PreparedInput
import java.nio.ByteBuffer
import kotlin.math.min

class FramePreprocessor(
    private val inputSize: Int,
    private val letterboxFill: Float = 114f / 255f,
) {
    fun preprocess(image: ImageProxy): PreparedInput {
        val plane = image.planes[0]
        return preprocess(
            rgba = plane.buffer,
            imageWidth = image.width,
            imageHeight = image.height,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride,
            rotationDegrees = image.imageInfo.rotationDegrees,
        )
    }

    /**
     * Preprocess a packed RGBA/RGBX buffer. Used by the UVC (external-camera) path
     * which delivers frames as a flat `ByteBuffer` with no Camera2 metadata.
     *
     * - `imageWidth`/`imageHeight`: pixel dimensions of the buffer as-laid-out.
     * - `rowStride`: bytes per row (often `imageWidth * pixelStride`).
     * - `pixelStride`: bytes per pixel (4 for RGBA8888 / RGBX).
     * - `rotationDegrees`: how many degrees to rotate the buffer so that the
     *   resulting frame is upright (mirrors `ImageProxy.imageInfo.rotationDegrees`).
     *   Pass 0 for an Osmo Action 4 mounted in its natural landscape orientation.
     */
    fun preprocess(
        rgba: ByteBuffer,
        imageWidth: Int,
        imageHeight: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int,
    ): PreparedInput {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val sourceWidth = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
        val sourceHeight = if (rotation == 90 || rotation == 270) imageWidth else imageHeight

        val scale = min(
            inputSize.toFloat() / sourceWidth.toFloat(),
            inputSize.toFloat() / sourceHeight.toFloat(),
        )
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val padX = (inputSize - scaledWidth) / 2f
        val padY = (inputSize - scaledHeight) / 2f

        val output = FloatArray(inputSize * inputSize * 3) { letterboxFill }
        val rgbaBuffer = rgba.duplicate()

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val rotatedX = ((x + 0.5f - padX) / scale).toInt()
                val rotatedY = ((y + 0.5f - padY) / scale).toInt()
                if (rotatedX !in 0 until sourceWidth || rotatedY !in 0 until sourceHeight) {
                    continue
                }

                val (sourceX, sourceY) = mapRotatedToSource(
                    rotatedX = rotatedX,
                    rotatedY = rotatedY,
                    rotationDegrees = rotation,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                )
                if (sourceX !in 0 until imageWidth || sourceY !in 0 until imageHeight) {
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
                originalWidth = sourceWidth,
                originalHeight = sourceHeight,
            ),
        )
    }

    private fun mapRotatedToSource(
        rotatedX: Int,
        rotatedY: Int,
        rotationDegrees: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): Pair<Int, Int> {
        return when (rotationDegrees) {
            90 -> rotatedY to (imageHeight - 1 - rotatedX)
            180 -> (imageWidth - 1 - rotatedX) to (imageHeight - 1 - rotatedY)
            270 -> (imageWidth - 1 - rotatedY) to rotatedX
            else -> rotatedX to rotatedY
        }
    }
}
