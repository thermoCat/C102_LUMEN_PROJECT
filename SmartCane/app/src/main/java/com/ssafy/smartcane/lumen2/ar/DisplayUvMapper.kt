package com.ssafy.smartcane.lumen2.ar

import android.graphics.Matrix

object DisplayUvMapper {
    fun imageToViewMatrix(
        displayUvCoords: FloatArray,
        imageWidth: Float,
        imageHeight: Float,
        viewWidth: Float,
        viewHeight: Float
    ): Matrix {
        return Matrix().apply {
            setPolyToPoly(
                sourceQuad(displayUvCoords, imageWidth, imageHeight),
                0,
                viewQuad(viewWidth, viewHeight),
                0,
                4
            )
        }
    }

    fun sourceQuad(displayUvCoords: FloatArray, imageWidth: Float, imageHeight: Float): FloatArray {
        return if (displayUvCoords.size >= 8) {
            floatArrayOf(
                displayUvCoords[0] * imageWidth, displayUvCoords[1] * imageHeight,
                displayUvCoords[2] * imageWidth, displayUvCoords[3] * imageHeight,
                displayUvCoords[4] * imageWidth, displayUvCoords[5] * imageHeight,
                displayUvCoords[6] * imageWidth, displayUvCoords[7] * imageHeight
            )
        } else {
            viewQuad(imageWidth, imageHeight)
        }
    }

    fun viewQuad(viewWidth: Float, viewHeight: Float): FloatArray {
        return floatArrayOf(
            0f, viewHeight,
            viewWidth, viewHeight,
            0f, 0f,
            viewWidth, 0f
        )
    }
}
