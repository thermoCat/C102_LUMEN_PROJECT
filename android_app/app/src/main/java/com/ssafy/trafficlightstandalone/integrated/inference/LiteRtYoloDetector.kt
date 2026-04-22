package com.ssafy.trafficlightstandalone.integrated.inference

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.ssafy.trafficlightstandalone.integrated.model.CropRegion
import com.ssafy.trafficlightstandalone.integrated.model.Detection
import com.ssafy.trafficlightstandalone.integrated.model.InferenceResult
import com.ssafy.trafficlightstandalone.integrated.model.ModelConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.measureTimeMillis

class LiteRtYoloDetector(
    context: Context,
    modelConfig: ModelConfig = ModelConfig.YOLOV8N,
    private val confidenceThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
) {
    private val classNames = loadLabels(context, modelConfig.labelsAsset)
    private val preprocessor = FramePreprocessor(modelConfig.inputSize)
    private val postProcessor = YoloPostProcessor()

    private val compiledModel = CompiledModel.create(
        context.assets,
        modelConfig.modelAsset,
        CompiledModel.Options(Accelerator.CPU),
    )
    private val inputBuffers = compiledModel.createInputBuffers()
    private val outputBuffers = compiledModel.createOutputBuffers()

    /** Settings adjustable at runtime */
    @Volatile var twoPasEnabled: Boolean = true
    @Volatile var topCropRatio: Float = 0.6f
    @Volatile var digitalZoom: Float = 1.0f

    fun detect(image: ImageProxy): InferenceResult {
        val dz = digitalZoom.coerceAtLeast(1.0f)
        val fullPrepared = preprocessor.preprocess(image, dz)
        val fullPass = runPass(fullPrepared)
        val (frameWidth, frameHeight) = rotatedSourceSize(image)

        val topCropPass = if (twoPasEnabled && topCropRatio in 0f..0.99f) {
            runPass(preprocessor.preprocessTopCrop(image, topCropRatio, dz))
        } else null

        val mergedDetections = if (topCropPass != null) {
            val remappedCrop = topCropPass.detections.map { detection ->
                remapCropDetection(
                    detection = detection,
                    cropRegion = topCropPass.cropRegion,
                    frameWidth = fullPrepared.cropRegion.width,
                    frameHeight = fullPrepared.cropRegion.height,
                )
            }
            postProcessor.mergeDetections(
                detections = fullPass.detections + remappedCrop,
                iouThreshold = iouThreshold,
            )
        } else {
            fullPass.detections
        }
        val detectionsInOriginalFrame = mergedDetections.map { detection ->
            remapToOriginalFrame(
                detection = detection,
                cropRegion = fullPrepared.cropRegion,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
        }

        return InferenceResult(
            detections = detectionsInOriginalFrame,
            inferenceTimeMs = fullPass.inferenceTimeMs + (topCropPass?.inferenceTimeMs ?: 0L),
            pipelineTimeMs = fullPass.inferenceTimeMs + (topCropPass?.inferenceTimeMs ?: 0L),
            sourceWidth = frameWidth,
            sourceHeight = frameHeight,
            peakScore = maxOf(fullPass.peakScore, topCropPass?.peakScore ?: 0f),
        )
    }

    fun close() {
        runCatching {
            compiledModel::class.java.methods
                .firstOrNull { it.name == "close" && it.parameterCount == 0 }
                ?.invoke(compiledModel)
        }
    }

    private fun loadLabels(context: Context, assetName: String): List<String> {
        context.assets.open(assetName).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                return lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        }
    }

    private fun runPass(prepared: com.ssafy.trafficlightstandalone.integrated.model.PreparedInput): PassResult {
        var rawOutput = FloatArray(0)
        val inferenceMs = measureTimeMillis {
            inputBuffers[0].writeFloat(prepared.input)
            compiledModel.run(inputBuffers, outputBuffers)
            rawOutput = outputBuffers[0].readFloat()
        }

        val detections = postProcessor.decode(
            output = rawOutput,
            classNames = classNames,
            letterbox = prepared.letterbox,
            confidenceThreshold = confidenceThreshold,
            iouThreshold = iouThreshold,
        )

        val numAnchors = rawOutput.size / (classNames.size + 4)
        val scoreOffset = numAnchors * 4
        var peakScore = 0f
        for (index in scoreOffset until rawOutput.size) {
            peakScore = maxOf(peakScore, rawOutput[index])
        }

        return PassResult(
            detections = detections,
            inferenceTimeMs = inferenceMs,
            peakScore = peakScore,
            cropRegion = prepared.cropRegion,
        )
    }

    private fun remapCropDetection(
        detection: Detection,
        cropRegion: CropRegion,
        frameWidth: Int,
        frameHeight: Int,
    ): Detection = detection.copy(
        left = (detection.left + cropRegion.left).coerceIn(0f, frameWidth.toFloat()),
        top = (detection.top + cropRegion.top).coerceIn(0f, frameHeight.toFloat()),
        right = (detection.right + cropRegion.left).coerceIn(0f, frameWidth.toFloat()),
        bottom = (detection.bottom + cropRegion.top).coerceIn(0f, frameHeight.toFloat()),
    )

    private fun remapToOriginalFrame(
        detection: Detection,
        cropRegion: CropRegion,
        frameWidth: Int,
        frameHeight: Int,
    ): Detection = detection.copy(
        left = (detection.left + cropRegion.left).coerceIn(0f, frameWidth.toFloat()),
        top = (detection.top + cropRegion.top).coerceIn(0f, frameHeight.toFloat()),
        right = (detection.right + cropRegion.left).coerceIn(0f, frameWidth.toFloat()),
        bottom = (detection.bottom + cropRegion.top).coerceIn(0f, frameHeight.toFloat()),
    )

    private fun rotatedSourceSize(image: ImageProxy): Pair<Int, Int> {
        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
        val width = if (rotation == 90 || rotation == 270) image.height else image.width
        val height = if (rotation == 90 || rotation == 270) image.width else image.height
        return width to height
    }

    private data class PassResult(
        val detections: List<Detection>,
        val inferenceTimeMs: Long,
        val peakScore: Float,
        val cropRegion: CropRegion,
    )
}
