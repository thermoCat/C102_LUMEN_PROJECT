package com.ssafy.smartcane.segformer.inference

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.ssafy.smartcane.segformer.model.CameraPose
import com.ssafy.smartcane.segformer.model.PreparedInput
import com.ssafy.smartcane.segformer.model.SegmentationResult
import com.ssafy.smartcane.segformer.pose.CameraIntrinsics
import com.ssafy.smartcane.segformer.pose.CameraIntrinsicsResolver
import com.ssafy.smartcane.segformer.pose.GroundPlaneProjector
import com.ssafy.smartcane.segformer.pose.ImuSegWorldPoseProvider
import com.ssafy.smartcane.segformer.pose.OrientationProvider
import com.ssafy.smartcane.segformer.pose.WorldPoseProvider
import java.nio.ByteBuffer
import org.json.JSONObject
import kotlin.system.measureTimeMillis

class LiteRtSegFormerSegmenter(
    context: Context,
    private val inputSize: Int = 512,
) {
    private val classNames = loadClassNames(context, CLASS_MAP_ASSET)
    private val preprocessor = FramePreprocessor(
        inputSize = inputSize,
        letterboxFill = 0f,
    )

    private val compiledModel = createCompiledModel(context)
    private val inputBuffers = compiledModel.createInputBuffers()
    private val outputBuffers = compiledModel.createOutputBuffers()

    private val groundPlaneProjector = GroundPlaneProjector()
    private val imuSegWorldPoseProvider = ImuSegWorldPoseProvider()

    @Volatile var orientationProvider: OrientationProvider? = null
    @Volatile var intrinsicsResolver: CameraIntrinsicsResolver? = null

    /**
     * Pre-computed intrinsics for cameras that do not expose Camera2 metadata
     * (e.g. external UVC cameras like the DJI Osmo Action 4). When set, this
     * takes precedence over [intrinsicsResolver].
     */
    @Volatile var intrinsicsOverride: CameraIntrinsics? = null
    @Volatile var worldPoseProvider: WorldPoseProvider? = null

    fun segment(image: ImageProxy): SegmentationResult {
        return segmentPrepared(preprocessor.preprocess(image))
    }

    /**
     * Inference entry point for the UVC / external-camera path. The frame must
     * be a packed RGBA8888 (or RGBX8888) buffer of [imageWidth] x [imageHeight]
     * pixels. Pass `rotationDegrees = 0` for an Osmo Action 4 mounted in its
     * natural landscape orientation; pass 90 for portrait clip-mount.
     */
    fun segment(
        rgba: ByteBuffer,
        imageWidth: Int,
        imageHeight: Int,
        rowStride: Int = imageWidth * 4,
        pixelStride: Int = 4,
        rotationDegrees: Int = 0,
    ): SegmentationResult {
        val prepared = preprocessor.preprocess(
            rgba = rgba,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rowStride = rowStride,
            pixelStride = pixelStride,
            rotationDegrees = rotationDegrees,
        )
        return segmentPrepared(prepared)
    }

    private fun segmentPrepared(prepared: PreparedInput): SegmentationResult {
        val inferenceMs = measureTimeMillis {
            inputBuffers[0].writeFloat(prepared.input)
            compiledModel.run(inputBuffers, outputBuffers)
        }

        val rawMask = outputBuffers[0].readInt()
        val expectedMaskSize = inputSize * inputSize
        require(rawMask.size == expectedMaskSize) {
            "Unexpected SegFormer mask size ${rawMask.size}; expected $expectedMaskSize"
        }

        val mask = IntArray(rawMask.size)
        val classPixelCounts = IntArray(classNames.size)
        for (index in rawMask.indices) {
            val classIndex = rawMask[index]
            mask[index] = classIndex
            if (classIndex in classPixelCounts.indices) {
                classPixelCounts[classIndex] += 1
            }
        }

        val gravity = orientationProvider?.snapshot()?.takeIf { it.hasReading }
        val cameraPose = gravity?.let {
            CameraPose(gravityCam = it.gravityCam, timestampNs = it.timestampNs)
        }
        val intrinsics = intrinsicsOverride
            ?: intrinsicsResolver?.intrinsicsForSource(
                prepared.letterbox.originalWidth,
                prepared.letterbox.originalHeight,
            )
        val externalWorldPose = worldPoseProvider?.snapshot()
        val internalWorldPose = if (externalWorldPose == null && gravity != null) {
            imuSegWorldPoseProvider.snapshot(gravity)
        } else {
            null
        }
        val worldPose = externalWorldPose ?: internalWorldPose
        val groundProjection = if (gravity != null && intrinsics != null) {
            groundPlaneProjector.project(
                mask = mask,
                maskWidth = inputSize,
                maskHeight = inputSize,
                letterbox = prepared.letterbox,
                intrinsics = intrinsics,
                gravityCam = gravity.gravityCam,
                worldPose = worldPose,
                classCount = classNames.size,
            )
        } else null
        if (externalWorldPose == null && gravity != null) {
            imuSegWorldPoseProvider.updateFromProjection(groundProjection, gravity)
        } else if (externalWorldPose != null) {
            imuSegWorldPoseProvider.reset()
        }

        return SegmentationResult(
            mask = mask,
            maskWidth = inputSize,
            maskHeight = inputSize,
            classNames = classNames,
            classPixelCounts = classPixelCounts,
            inferenceTimeMs = inferenceMs,
            pipelineTimeMs = inferenceMs,
            sourceWidth = prepared.letterbox.originalWidth,
            sourceHeight = prepared.letterbox.originalHeight,
            letterbox = prepared.letterbox,
            dominantClassIndex = dominantForegroundClass(classPixelCounts),
            pose = cameraPose,
            groundProjection = groundProjection,
        )
    }

    fun close() {
        runCatching {
            compiledModel::class.java.methods
                .firstOrNull { it.name == "close" && it.parameterCount == 0 }
                ?.invoke(compiledModel)
        }
    }

    private fun dominantForegroundClass(classPixelCounts: IntArray): Int {
        val foreground = classPixelCounts.indices
            .filter { it != BACKGROUND_CLASS_INDEX && classPixelCounts[it] > 0 }
            .maxByOrNull { classPixelCounts[it] }
        return foreground ?: classPixelCounts.indices.maxByOrNull { classPixelCounts[it] } ?: 0
    }

    private fun loadClassNames(context: Context, assetName: String): List<String> {
        val jsonText = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val classes = JSONObject(jsonText).getJSONArray("classes")
        val names = (0 until classes.length())
            .map { classes.getJSONObject(it) }
            .sortedBy { it.getInt("id") }
            .map { it.getString("name") }

        require(names.isNotEmpty()) {
            "No segmentation classes found in $assetName"
        }
        return names
    }

    /**
     * Try each accelerator in order and return the first model whose input AND
     * output tensor buffers can be allocated ??on some SoCs (e.g. Adreno 710)
     * GPU compile passes but the subsequent createInputBuffers/createOutputBuffers
     * fails inside litert_tensor_buffer.cc. The previous "compile then
     * unconditionally allocate" code would crash in that case; here we
     * smoke-test buffer allocation per accelerator before committing.
     */
    private fun createCompiledModel(context: Context): CompiledModel {
        val attempts = listOf(Accelerator.GPU, Accelerator.CPU)
        val failures = mutableListOf<String>()
        for (accelerator in attempts) {
            val attempt = runCatching {
                val model = CompiledModel.create(
                    context.assets,
                    MODEL_ASSET,
                    CompiledModel.Options(accelerator),
                )
                try {
                    // Throwaway smoke test ??proves both buffer types allocate.
                    model.createInputBuffers()
                    model.createOutputBuffers()
                    model
                } catch (t: Throwable) {
                    runCatching {
                        model::class.java.methods
                            .firstOrNull { it.name == "close" && it.parameterCount == 0 }
                            ?.invoke(model)
                    }
                    throw t
                }
            }
            attempt.onSuccess { model ->
                android.util.Log.i(
                    "SegFormer",
                    "Loaded model with $accelerator" +
                        (if (failures.isEmpty()) "" else " (after ${failures.joinToString("; ")})"),
                )
                return model
            }
            attempt.onFailure { t ->
                failures += "$accelerator: ${t.javaClass.simpleName}: ${t.message?.take(120)}"
            }
        }
        throw IllegalStateException(
            "SegFormer LiteRT init failed on every accelerator. Tried: " +
                failures.joinToString(" | "),
        )
    }

    companion object {
        const val MODEL_ASSET = "segformer_train_test.tflite"
        const val CLASS_MAP_ASSET = "class_map.json"
        private const val BACKGROUND_CLASS_INDEX = 0
    }
}
