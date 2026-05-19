package com.ssafy.smartcane.segformer.pose

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import kotlin.math.max

/**
 * Pinhole intrinsics on the post-rotation source frame (the frame that
 * [com.ssafy.smartcane.segformer.inference.FramePreprocessor] letterboxes
 * into the model input). cx/cy are the centre of the source image.
 *
 * Square pixels are assumed (universal on modern phone cameras), so fx == fy.
 */
data class CameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

/**
 * Resolves per-frame [CameraIntrinsics] from the camera's reported physical
 * sensor size and focal length. We derive focal-length-in-pixels at the source
 * resolution as `focalMm * sourceLong / sensorPhysicalLongMm`, which holds for
 * square pixels regardless of how the frame was scaled/cropped.
 */
class CameraIntrinsicsResolver private constructor(
    private val focalMm: Float,
    private val sensorPhysicalLongMm: Float,
) {
    fun intrinsicsForSource(sourceWidth: Int, sourceHeight: Int): CameraIntrinsics {
        val sourceLong = max(sourceWidth, sourceHeight).toFloat()
        val f = focalMm * sourceLong / sensorPhysicalLongMm
        return CameraIntrinsics(
            fx = f,
            fy = f,
            cx = sourceWidth / 2f,
            cy = sourceHeight / 2f,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
    }

    companion object {
        fun fromCameraInfo(cameraInfo: CameraInfo): CameraIntrinsicsResolver? {
            val info = runCatching { Camera2CameraInfo.from(cameraInfo) }.getOrNull()
                ?: return null
            val focalLengths = info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
            )
            val physicalSize = info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
            )
            if (focalLengths == null || focalLengths.isEmpty() || physicalSize == null) return null
            val focalMm = focalLengths[0]
            val physLong = max(physicalSize.width, physicalSize.height)
            if (focalMm <= 0f || physLong <= 0f) return null
            return CameraIntrinsicsResolver(focalMm, physLong)
        }
    }
}
