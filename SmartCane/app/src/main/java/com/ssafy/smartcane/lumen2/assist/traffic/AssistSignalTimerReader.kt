package com.ssafy.smartcane.lumen2.assist

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.Text
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class AssistSignalTimerReader : Closeable {
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val timerByKey = ConcurrentHashMap<String, Int>()
    private val latestTimerByLabel = ConcurrentHashMap<TrafficDetectionLabel, Int>()
    private var busy = false
    private var lastRequestedAt = 0L

    fun attachTimers(bitmap: Bitmap?, evidence: TrafficSceneEvidence): TrafficSceneEvidence {
        bitmap ?: return evidence
        requestTimerRead(bitmap, evidence.detections)
        return evidence.copy(
            detections = evidence.detections.map { detection ->
                val cropRect = timerCropRect(bitmap, detection)
                detection.copy(
                    remainingSeconds = timerByKey[detection.timerKey()] ?: latestTimerByLabel[detection.label],
                    ocrLeft = cropRect?.left?.toFloat(),
                    ocrTop = cropRect?.top?.toFloat(),
                    ocrRight = cropRect?.right?.toFloat(),
                    ocrBottom = cropRect?.bottom?.toFloat()
                )
            }
        )
    }

    override fun close() {
        recognizer.close()
    }

    private fun requestTimerRead(bitmap: Bitmap, detections: List<TrafficDetection>) {
        val now = System.currentTimeMillis()
        if (busy || now - lastRequestedAt < REQUEST_INTERVAL_MS) return
        val signal = detections.firstOrNull {
            it.label == TrafficDetectionLabel.GREEN_LIGHT ||
                it.label == TrafficDetectionLabel.RED_LIGHT
        } ?: return
        val cropRect = timerCropRect(bitmap, signal) ?: return
        val crop = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        busy = true
        lastRequestedAt = now
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { text ->
                parseSeconds(text)?.let { seconds ->
                    timerByKey[signal.timerKey()] = seconds
                    latestTimerByLabel[signal.label] = seconds
                }
            }
            .addOnCompleteListener { busy = false }
    }

    private fun timerCropRect(bitmap: Bitmap, detection: TrafficDetection): Rect? {
        if (detection.label != TrafficDetectionLabel.GREEN_LIGHT &&
            detection.label != TrafficDetectionLabel.RED_LIGHT
        ) return null
        val boxWidth = detection.right - detection.left
        val boxHeight = detection.bottom - detection.top
        if (boxWidth <= 1f || boxHeight <= 1f) return null
        val left = (detection.left - boxWidth * 1.5f).toInt().coerceIn(0, bitmap.width - 1)
        val right = (detection.right + boxWidth * 1.5f).toInt().coerceIn(left + 1, bitmap.width)
        val top = (detection.top - boxHeight * 1.0f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (detection.bottom + boxHeight * 3.0f).toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < MIN_CROP_PX || bottom - top < MIN_CROP_PX) return null
        return Rect(left, top, right, bottom)
    }

    private fun parseSeconds(text: Text): Int? {
        val rawCandidates = Regex("\\d{1,2}")
            .findAll(text.text)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..99 }
            .toList()
        rawCandidates.firstOrNull { it >= 10 }?.let { return it }

        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val digits = line.elements
                    .map { element -> element.text.filter(Char::isDigit) }
                    .filter { it.length == 1 }
                if (digits.size >= 2) {
                    digits.joinToString("")
                        .take(2)
                        .toIntOrNull()
                        ?.takeIf { it in 10..99 }
                        ?.let { return it }
                }
            }
        }

        return rawCandidates.firstOrNull()
    }

    private fun TrafficDetection.timerKey(): String {
        val cx = ((left + right) * 0.5f / KEY_BUCKET_PX).toInt()
        val cy = ((top + bottom) * 0.5f / KEY_BUCKET_PX).toInt()
        return "${label.name}:$cx:$cy"
    }

    private companion object {
        private const val REQUEST_INTERVAL_MS = 450L
        private const val KEY_BUCKET_PX = 96f
        private const val MIN_CROP_PX = 12
    }
}
