package com.ssafy.smartcane.lumen2.assist

import android.content.Context
import android.graphics.Bitmap
import com.ssafy.smartcane.detection.TFLiteRunner
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class AssistTrafficDetector(context: Context) : Closeable {
    private val labels = loadLabels(context)
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().apply {
        setNumThreads(2)
    })
    private val inputShape = interpreter.getInputTensor(0).shape()
    private val inputHeight = inputShape.getOrNull(1) ?: 640
    private val inputWidth = inputShape.getOrNull(2) ?: 640
    private val inputChannels = inputShape.getOrNull(3) ?: 3
    private val inputBuffer = ByteBuffer
        .allocateDirect(inputWidth * inputHeight * inputChannels * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())

    fun analyze(bitmap: Bitmap?): TrafficSceneEvidence {
        bitmap ?: return TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList())
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        inputBuffer.rewind()
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        // 시연 안정성: scaled 비트맵 즉시 recycle (프레임 루프 OOM 방지)
        if (resized !== bitmap && !resized.isRecycled) resized.recycle()
        pixels.forEach { pixel ->
            inputBuffer.putFloat(((pixel shr 16) and 0xff) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xff) / 255f)
            inputBuffer.putFloat((pixel and 0xff) / 255f)
        }

        val outputShape = interpreter.getOutputTensor(0).shape()
        val output = Array(outputShape[0]) {
            Array(outputShape[1]) {
                FloatArray(outputShape[2])
            }
        }
        interpreter.run(inputBuffer, output)
        val detections = parseYoloOutput(output[0], bitmap.width.toFloat(), bitmap.height.toFloat())
        return TrafficSceneEvidence(
            status = sceneStatus(detections),
            detections = detections
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun parseYoloOutput(output: Array<FloatArray>, sourceWidth: Float, sourceHeight: Float): List<TrafficDetection> {
        val rowWidth = output.firstOrNull()?.size ?: return emptyList()
        val expectedChannelCount = BBOX_VALUE_COUNT + labels.size
        val transposed = output.size == expectedChannelCount || output.size < rowWidth
        val candidateCount = if (transposed) rowWidth else output.size
        val detections = mutableListOf<TrafficDetection>()
        for (index in 0 until candidateCount) {
            val values = if (transposed) {
                FloatArray(output.size) { row -> output[row][index] }
            } else {
                output[index]
            }
            if (values.size <= BBOX_VALUE_COUNT) continue

            val classCount = min(labels.size, values.size - BBOX_VALUE_COUNT)
            var classIndex = -1
            var confidence = 0f
            for (candidateClassIndex in 0 until classCount) {
                val score = values[BBOX_VALUE_COUNT + candidateClassIndex]
                if (score > confidence) {
                    confidence = score
                    classIndex = candidateClassIndex
                }
            }
            if (classIndex < 0) continue
            val label = labelFor(classIndex) ?: continue
            if (confidence < thresholdFor(label)) continue

            val cx = values[0]
            val cy = values[1]
            val w = values[2]
            val h = values[3]
            val normalized = max(max(cx, cy), max(w, h)) <= 2f
            val scaleX = if (normalized) sourceWidth else sourceWidth / inputWidth.toFloat()
            val scaleY = if (normalized) sourceHeight else sourceHeight / inputHeight.toFloat()
            val left = (cx - w * 0.5f) * scaleX
            val top = (cy - h * 0.5f) * scaleY
            val right = (cx + w * 0.5f) * scaleX
            val bottom = (cy + h * 0.5f) * scaleY
            detections += TrafficDetection(
                label = label,
                confidence = confidence,
                left = left.coerceIn(0f, sourceWidth),
                top = top.coerceIn(0f, sourceHeight),
                right = right.coerceIn(0f, sourceWidth),
                bottom = bottom.coerceIn(0f, sourceHeight)
            )
        }
        return nonMaxSuppression(detections)
    }

    private fun nonMaxSuppression(detections: List<TrafficDetection>): List<TrafficDetection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val kept = mutableListOf<TrafficDetection>()
        sorted.forEach { candidate ->
            val overlaps = kept.any { it.label == candidate.label && iou(it, candidate) > IOU_THRESHOLD }
            if (!overlaps) kept += candidate
        }
        return kept.take(MAX_DETECTIONS)
    }

    private fun iou(a: TrafficDetection, b: TrafficDetection): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val areaA = max(0f, a.right - a.left) * max(0f, a.bottom - a.top)
        val areaB = max(0f, b.right - b.left) * max(0f, b.bottom - b.top)
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun sceneStatus(detections: List<TrafficDetection>): TrafficSceneStatus {
        val hasCrosswalk = detections.any { it.label == TrafficDetectionLabel.CROSSWALK }
        val hasGreen = detections.any { it.label == TrafficDetectionLabel.GREEN_LIGHT }
        val hasRed = detections.any { it.label == TrafficDetectionLabel.RED_LIGHT }
        return when {
            hasCrosswalk && hasRed -> TrafficSceneStatus.RED_LIGHT
            hasCrosswalk && hasGreen -> TrafficSceneStatus.GREEN_LIGHT
            hasCrosswalk -> TrafficSceneStatus.CROSSWALK
            else -> TrafficSceneStatus.CLEAR
        }
    }

    private fun labelFor(index: Int): TrafficDetectionLabel? {
        return when (labels.getOrNull(index)) {
            "crosswalk" -> TrafficDetectionLabel.CROSSWALK
            "green_pedestrian_light" -> TrafficDetectionLabel.GREEN_LIGHT
            "pedestrian_traffic_light" -> TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT
            "red_pedestrian_light" -> TrafficDetectionLabel.RED_LIGHT
            else -> null
        }
    }

    private fun thresholdFor(label: TrafficDetectionLabel): Float {
        return when (label) {
            TrafficDetectionLabel.CROSSWALK -> CROSSWALK_CONFIDENCE_THRESHOLD
            TrafficDetectionLabel.GREEN_LIGHT,
            TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT,
            TrafficDetectionLabel.RED_LIGHT -> TRAFFIC_LIGHT_CONFIDENCE_THRESHOLD
        }
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        val descriptor = context.assets.openFd(MODEL_NAME)
        return FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
    }

    private fun loadLabels(context: Context): List<String> {
        return context.assets.open(TFLiteRunner.DEFAULT_LABELS_FILE_NAME).use { stream ->
            BufferedReader(InputStreamReader(stream)).readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    private companion object {
        private const val MODEL_NAME = TFLiteRunner.DEFAULT_MODEL_FILE_NAME
        private const val FLOAT_BYTES = 4
        private const val BBOX_VALUE_COUNT = 4
        private const val CROSSWALK_CONFIDENCE_THRESHOLD = 0.10f
        private const val TRAFFIC_LIGHT_CONFIDENCE_THRESHOLD = 0.30f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 12
    }
}
