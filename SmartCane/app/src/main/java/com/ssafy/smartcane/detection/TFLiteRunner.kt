package com.ssafy.smartcane.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

/**
 * 분류 모델 가정.
 *   입력: [1, H, W, 3] (uint8 또는 float32)
 *   출력: [1, N_classes]
 *
 * float32 입력은 0~1 정규화(mean=0, std=255)로 자동 처리.
 * 다른 형태(세그멘테이션/디텍션)면 classify() 부분만 모델에 맞게 교체.
 */
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
    private val processor: ImageProcessor

    init {
        val model = FileUtil.loadMappedFile(context, modelFileName)
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })

        labels = FileUtil.loadLabels(context, labelsFileName)

        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape()
        inputH = inShape[1]
        inputW = inShape[2]
        inputDType = inputTensor.dataType()

        val outputTensor = interpreter.getOutputTensor(0)
        outputShape = outputTensor.shape()
        outputDType = outputTensor.dataType()

        processor = ImageProcessor.Builder()
            .add(ResizeOp(inputH, inputW, ResizeOp.ResizeMethod.BILINEAR))
            .apply {
                if (inputDType == DataType.FLOAT32) add(NormalizeOp(0f, 255f))
            }
            .build()
    }

    fun classify(bitmap: Bitmap): Result? {
        val image = TensorImage(inputDType).apply { load(bitmap) }
        val processed = processor.process(image)

        val output = TensorBuffer.createFixedSize(outputShape, outputDType)
        interpreter.run(processed.buffer, output.buffer.rewind())

        val probs = output.floatArray
        val idx = probs.indices.maxByOrNull { probs[it] } ?: return null
        val label = labels.getOrNull(idx) ?: return null
        return Result(label, probs[idx])
    }

    override fun close() {
        interpreter.close()
    }

    data class Result(val label: String, val confidence: Float)
}
