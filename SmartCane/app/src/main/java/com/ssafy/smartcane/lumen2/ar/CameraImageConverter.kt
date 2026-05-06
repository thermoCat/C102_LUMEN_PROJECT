package com.ssafy.smartcane.lumen2.ar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object CameraImageConverter {
    fun toBitmap(image: Image, maxSide: Int? = null): Bitmap {
        if (maxSide != null) return toLumaBitmap(image, maxSide)
        val nv21 = yuv420ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun toLumaBitmap(image: Image, maxSide: Int): Bitmap {
        val srcWidth = image.width
        val srcHeight = image.height
        val longest = maxOf(srcWidth, srcHeight)
        val scale = (maxSide.toFloat() / longest.toFloat()).coerceAtMost(1f)
        val dstWidth = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val dstHeight = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val pixels = IntArray(dstWidth * dstHeight)
        var offset = 0
        for (y in 0 until dstHeight) {
            val srcY = (y * srcHeight / dstHeight).coerceIn(0, srcHeight - 1)
            val rowStart = srcY * rowStride
            for (x in 0 until dstWidth) {
                val srcX = (x * srcWidth / dstWidth).coerceIn(0, srcWidth - 1)
                val luma = yBuffer.get(rowStart + srcX * pixelStride).toInt() and 0xff
                pixels[offset++] = Color.rgb(luma, luma, luma)
            }
        }
        return Bitmap.createBitmap(pixels, dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height * 3 / 2)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        var offset = 0

        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (col in 0 until width) {
                output[offset++] = yPlane.buffer.get(rowStart + col * yPlane.pixelStride)
            }
        }

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                output[offset++] = vPlane.buffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                output[offset++] = uPlane.buffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }
        return output
    }
}
