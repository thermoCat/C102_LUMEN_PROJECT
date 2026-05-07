package com.ssafy.smartcane.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteRunner(
    context: Context,
    modelFileName: String = "model.tflite",
    labelsFileName: String = "labels.txt"
) : AutoCloseable {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputH: Int
    private val inputW: Int
    private val inputDType: DataType
    private val outputShape: IntArray

    init {
        val afd = context.assets.openFd(modelFileName)
        val model = afd.createInputStream().channel.map(
            FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
        )
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })

        labels = context.assets.open(labelsFileName).use { stream ->
            BufferedReader(InputStreamReader(stream)).readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape()
        inputH = inShape[1]
        inputW = inShape[2]
        inputDType = inputTensor.dataType()

        val outputTensor = interpreter.getOutputTensor(0)
        outputShape = outputTensor.shape()
    }

    /**
     * 가장 높은 confidence 탐지 1개 반환 (HazardDetectionAnalyzer 호환).
     * [minConfidence] 기본값은 CONFIDENCE_THRESHOLD(0.5).
     * 서비스에서 진단 목적으로 0.3 등 낮은 값을 지정할 수 있음.
     */
    fun classify(bitmap: Bitmap, minConfidence: Float = CONFIDENCE_THRESHOLD): Result? =
        detectAll(bitmap, minConfidence).maxByOrNull { it.confidence }

    /**
     * confidence ≥ [minConfidence] 인 모든 탐지 반환.
     * 출력 형식 [1, N, 6]: 각 행 = [x1, y1, x2, y2, confidence, class_id]
     * (포맷이 다르면 AppLogger 로그 보고 인덱스 조정)
     */
    fun detectAll(bitmap: Bitmap, minConfidence: Float = CONFIDENCE_THRESHOLD): List<Result> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val flat = FloatArray(outputSize) { outputBuffer.float }

        val numDetections = if (outputShape.size >= 2) outputShape[outputShape.size - 2] else return emptyList()
        val stride        = if (outputShape.size >= 1) outputShape[outputShape.size - 1] else return emptyList()
        if (stride < 6) return emptyList()

        // 포맷 확인용 로그 (처음 3개)
        for (i in 0 until minOf(3, numDetections)) {
            val base = i * stride
            val vals = (0 until stride).map { "%.3f".format(flat[base + it]) }
            com.ssafy.smartcane.util.AppLogger.log("TFLite", "det[$i]: $vals")
        }

        val results = mutableListOf<Result>()
        for (i in 0 until numDetections) {
            val base    = i * stride
            val a0      = flat[base + 0]
            val a1      = flat[base + 1]
            val a2      = flat[base + 2]
            val a3      = flat[base + 3]
            val conf    = flat[base + 4]
            val classId = flat[base + 5].toInt()

            if (conf < minConfidence) continue
            val label = labels.getOrNull(classId) ?: continue

            // 포맷 자동 감지:
            // [cx,cy,w,h]: a3(h) < a1(cy) 불가능 → a1-a3/2 < 0 이 되므로 y2>y1 이 됨
            // [x1,y1,x2,y2]: y2 >= y1 이 보장됨
            val isCxCyWH = a2 < a0 || a3 < a1
            val x1: Float; val y1: Float; val x2: Float; val y2: Float
            if (isCxCyWH) {
                // [cx, cy, w, h] → [x1, y1, x2, y2]
                x1 = (a0 - a2 / 2f).coerceIn(0f, 1f)
                y1 = (a1 - a3 / 2f).coerceIn(0f, 1f)
                x2 = (a0 + a2 / 2f).coerceIn(0f, 1f)
                y2 = (a1 + a3 / 2f).coerceIn(0f, 1f)
            } else {
                x1 = a0; y1 = a1; x2 = a2; y2 = a3
            }

            results += Result(label, conf, x1, y1, x2, y2)
        }
        return results
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val isFloat = inputDType == DataType.FLOAT32
        val bytesPerPixel = if (isFloat) 4 else 1
        val buffer = ByteBuffer.allocateDirect(inputH * inputW * 3 * bytesPerPixel).apply {
            order(ByteOrder.nativeOrder())
        }
        val pixels = IntArray(inputH * inputW)
        bitmap.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (isFloat) {
                buffer.putFloat(r / 255f)
                buffer.putFloat(g / 255f)
                buffer.putFloat(b / 255f)
            } else {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }
        buffer.rewind()
        return buffer
    }

    override fun close() { interpreter.close() }

    data class Result(
        val label: String,
        val confidence: Float,
        /** 정규화 좌표 0~1 (모델 입력 해상도 기준) */
        val x1: Float = 0f, val y1: Float = 0f,
        val x2: Float = 0f, val y2: Float = 0f
    )

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.5f
    }
}
