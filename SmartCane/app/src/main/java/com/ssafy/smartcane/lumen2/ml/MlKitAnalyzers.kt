package com.ssafy.smartcane.lumen2.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.math.roundToInt

class MlKitAnalyzers {
    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )
    private var busyText = false
    private var busyObjects = false
    private var lastTextAt = 0L
    private var lastObjectsAt = 0L
    var latestText: Text? = null
        private set
    var latestObjects: List<DetectedObject> = emptyList()
        private set
    var latestTextInputWidth: Int = 1
        private set
    var latestTextInputHeight: Int = 1
        private set
    var latestObjectInputWidth: Int = 1
        private set
    var latestObjectInputHeight: Int = 1
        private set
    var latestObjectStatus: String = "objects: waiting"
        private set

    fun processText(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (busyText || now - lastTextAt < 350L) return
        val inputBitmap = bitmap.scaledForAnalysis(maxSide = 960)
        latestTextInputWidth = inputBitmap.width
        latestTextInputHeight = inputBitmap.height
        busyText = true
        lastTextAt = now
        textRecognizer.process(InputImage.fromBitmap(inputBitmap, 0))
            .addOnSuccessListener { latestText = it }
            .addOnCompleteListener { busyText = false }
    }

    fun processObjects(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (busyObjects || now - lastObjectsAt < 300L) return
        val inputBitmap = bitmap.scaledForAnalysis(maxSide = 480)
        latestObjectInputWidth = inputBitmap.width
        latestObjectInputHeight = inputBitmap.height
        busyObjects = true
        lastObjectsAt = now
        objectDetector.process(InputImage.fromBitmap(inputBitmap, 0))
            .addOnSuccessListener {
                latestObjects = it
                latestObjectStatus = "objects=${it.size} input=${inputBitmap.width}x${inputBitmap.height}"
            }
            .addOnFailureListener {
                latestObjects = emptyList()
                latestObjectStatus = "object failed: ${it.javaClass.simpleName}"
            }
            .addOnCompleteListener { busyObjects = false }
    }

    fun close() {
        textRecognizer.close()
        objectDetector.close()
    }

    private fun Bitmap.scaledForAnalysis(maxSide: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return this
        val scale = maxSide.toFloat() / longest.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}
