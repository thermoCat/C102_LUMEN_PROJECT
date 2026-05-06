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
    private val outputDType: DataType

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
        outputDType = outputTensor.dataType()
    }

    fun classify(bitmap: Bitmap): Result? {
        val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
        val bytesPerElement = if (outputDType == DataType.FLOAT32) 4 else 1
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * bytesPerElement).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val probs = if (outputDType == DataType.FLOAT32) {
            FloatArray(outputSize) { outputBuffer.float }
        } else {
            FloatArray(outputSize) { (outputBuffer.get().toInt() and 0xFF) / 255f }
        }

        val idx = probs.indices.maxByOrNull { probs[it] } ?: return null
        val label = labels.getOrNull(idx) ?: return null
        return Result(label, probs[idx])
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

    override fun close() {
        interpreter.close()
    }

    data class Result(val label: String, val confidence: Float)
}
