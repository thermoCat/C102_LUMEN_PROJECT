package com.ssafy.smartcane.lumen2.ar

import android.graphics.Bitmap

data class ArFrameData(
    val cameraBitmap: Bitmap?,
    val timestampNanos: Long,
    val viewWidth: Int,
    val viewHeight: Int,
    val displayUvCoords: FloatArray,
    val depthWidth: Int,
    val depthHeight: Int,
    val depthMillimeters: ShortArray?,
    val semanticWidth: Int,
    val semanticHeight: Int,
    val semanticLabels: ByteArray?,
    val semanticFractions: FloatArray?,
    val poseTranslation: FloatArray,
    val poseQuaternion: FloatArray,
    val intrinsics: CameraIntrinsicsData,
    val tracking: Boolean,
    val depthSupported: Boolean,
    val semanticsSupported: Boolean
)

data class CameraIntrinsicsData(
    val imageWidth: Int,
    val imageHeight: Int,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float
)
