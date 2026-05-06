package com.ssafy.smartcane.util

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ImageEncoder {
    fun bitmapToBase64(bitmap: Bitmap, maxSize: Int = 400, quality: Int = 70): String {
        val ratio = min(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height
        )
        val resized = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)

        return "data:image/jpeg;base64," +
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
