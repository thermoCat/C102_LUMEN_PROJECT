package com.ssafy.trafficlightstandalone.integrated.inference

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.ssafy.trafficlightstandalone.integrated.model.CropRegion
import com.ssafy.trafficlightstandalone.integrated.model.Detection
import com.ssafy.trafficlightstandalone.integrated.model.InferenceResult
import com.ssafy.trafficlightstandalone.integrated.model.ModelConfig
import com.ssafy.trafficlightstandalone.integrated.model.TrafficLightRoi
import com.ssafy.trafficlightstandalone.integrated.model.TrafficLightRoiSource
import com.ssafy.trafficlightstandalone.integrated.model.TrafficLightState
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.measureTimeMillis

class LiteRtYoloDetector(
    context: Context,
    modelConfig: ModelConfig = ModelConfig.MODEL_0000_YOLOV8N_260408,
    private val confidenceThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
) {
    companion object {
        private const val MAX_TRACKED_ROI_FRAMES = 10
        private const val LIGHT_ROI_EXPAND_RATIO = 1.5f
        private const val LIGHT_ROI_SECONDARY_PADDING_RATIO = 0.15f
        private const val LIGHT_ROI_SIDE_PADDING_RATIO = 0.15f

        private const val MIN_EXPANDED_ROI_CONFIDENCE = 0.40f
        private const val MIN_SIGNAL_CONFIDENCE_PTL = 0.35f
        private const val MIN_SIGNAL_CONFIDENCE_EXPANDED = 0.50f
        private const val MIN_SIGNAL_CONFIDENCE_TRACKED = 0.55f
        private const val MIN_PROTECTED_RED_CONFIDENCE = 0.72f

        private const val COLOR_AMBIGUITY_MARGIN = 0.08f
        private const val RED_REGION_MAX_RATIO = 0.62f
        private const val GREEN_REGION_MIN_RATIO = 0.38f
        private const val MIN_ROI_OVERLAP_RATIO = 0.15f

        private const val GREEN_PROTECTION_FRAMES = 4
        private const val REQUIRED_COLOR_FROM_NONE_FRAMES = 2
        private const val REQUIRED_COLOR_SWITCH_FRAMES = 2
        private const val REQUIRED_RED_AFTER_GREEN_FRAMES = 3
        private const val REQUIRED_NONE_AFTER_GREEN_FRAMES = 1
        private const val REQUIRED_NONE_AFTER_OTHER_FRAMES = 2
    }

    private val tag = "LiteRtYoloDetector"
    private val classNames = loadLabels(context, modelConfig.labelsAsset)
    private val preprocessor = FramePreprocessor(modelConfig.inputSize)
    private val postProcessor = YoloPostProcessor()

    private val compiledModel = createCompiledModel(context, modelConfig)
    private val inputBuffers = compiledModel.createInputBuffers()
    private val outputBuffers = compiledModel.createOutputBuffers()

    /** Settings adjustable at runtime */
    @Volatile var twoPasEnabled: Boolean = true
    @Volatile var topCropRatio: Float = 0.6f
    @Volatile var digitalZoom: Float = 1.0f

    private var trackedTrafficLightRoi: TrafficLightRoi? = null
    private var trackedRoiFrames: Int = 0

    private var stableTrafficLightState: TrafficLightState = TrafficLightState.NONE
    private var pendingTrafficLightState: TrafficLightState = TrafficLightState.NONE
    private var pendingStateFrames: Int = 0
    private var missingSignalFrames: Int = 0
    private var greenProtectionFramesRemaining: Int = 0

    fun detect(image: ImageProxy): InferenceResult {
        val dz = digitalZoom.coerceAtLeast(1.0f)
        val fullPrepared = preprocessor.preprocess(image, dz)
        val fullPass = runPass(fullPrepared)
        val (frameWidth, frameHeight) = rotatedSourceSize(image)

        val topCropPass = if (twoPasEnabled && topCropRatio in 0f..0.99f) {
            runPass(preprocessor.preprocessTopCrop(image, topCropRatio, dz))
        } else {
            null
        }

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

        val roiSelection = resolveTrafficLightRoi(
            detections = detectionsInOriginalFrame,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        val frameSignal = resolveFrameSignal(
            detections = detectionsInOriginalFrame,
            roiSelection = roiSelection,
        )
        val trafficLightState = stabilizeTrafficLightState(frameSignal)

        return InferenceResult(
            detections = detectionsInOriginalFrame,
            inferenceTimeMs = fullPass.inferenceTimeMs + (topCropPass?.inferenceTimeMs ?: 0L),
            pipelineTimeMs = fullPass.inferenceTimeMs + (topCropPass?.inferenceTimeMs ?: 0L),
            sourceWidth = frameWidth,
            sourceHeight = frameHeight,
            peakScore = maxOf(fullPass.peakScore, topCropPass?.peakScore ?: 0f),
            trafficLightRoi = roiSelection.roi,
            trafficLightRoiSource = roiSelection.source,
            trafficLightState = trafficLightState,
        )
    }

    fun close() {
        runCatching {
            compiledModel::class.java.methods
                .firstOrNull { it.name == "close" && it.parameterCount == 0 }
                ?.invoke(compiledModel)
        }
    }

    private fun createCompiledModel(context: Context, modelConfig: ModelConfig): CompiledModel {
        return runCatching {
            CompiledModel.create(
                context.assets,
                modelConfig.modelAsset,
                CompiledModel.Options(Accelerator.GPU),
            ).also {
                Log.i(tag, "LiteRT accelerator selected: GPU")
            }
        }.getOrElse { gpuError ->
            Log.w(tag, "LiteRT GPU unavailable, falling back to CPU", gpuError)
            CompiledModel.create(
                context.assets,
                modelConfig.modelAsset,
                CompiledModel.Options(Accelerator.CPU),
            ).also {
                Log.i(tag, "LiteRT accelerator selected: CPU")
            }
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

    private fun resolveTrafficLightRoi(
        detections: List<Detection>,
        frameWidth: Int,
        frameHeight: Int,
    ): RoiSelection {
        val previousRoi = trackedTrafficLightRoi
        val housingDetections = detections.filter { isTrafficLightHousing(it.className) }
        if (housingDetections.isNotEmpty()) {
            val bestHousing = housingDetections.maxByOrNull { it.confidence }!!
            return updateTrackedRoi(
                roi = TrafficLightRoi(
                    left = bestHousing.left,
                    top = bestHousing.top,
                    right = bestHousing.right,
                    bottom = bestHousing.bottom,
                    confidence = bestHousing.confidence,
                ),
                source = TrafficLightRoiSource.PTL,
            )
        }

        val lightDetections = detections
            .filter { isSignalLight(it.className) }
            .filter { it.confidence >= MIN_EXPANDED_ROI_CONFIDENCE }

        if (previousRoi != null) {
            val associatedLights = lightDetections.filter { detection ->
                isCenterInsideRoi(detection, previousRoi) || overlapsRoi(detection, previousRoi)
            }
            if (associatedLights.isNotEmpty()) {
                val bestLight = associatedLights.maxByOrNull { it.confidence }!!
                return updateTrackedRoi(
                    roi = expandLightToTrafficLightRoi(
                        detection = bestLight,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                    ),
                    source = TrafficLightRoiSource.EXPANDED,
                )
            }
            if (trackedRoiFrames < MAX_TRACKED_ROI_FRAMES) {
                trackedRoiFrames += 1
                return RoiSelection(previousRoi, TrafficLightRoiSource.TRACKED)
            }
        } else if (lightDetections.isNotEmpty()) {
            val bestLight = lightDetections.maxByOrNull { it.confidence }!!
            return updateTrackedRoi(
                roi = expandLightToTrafficLightRoi(
                    detection = bestLight,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                ),
                source = TrafficLightRoiSource.EXPANDED,
            )
        }

        trackedTrafficLightRoi = null
        trackedRoiFrames = 0
        return RoiSelection(null, TrafficLightRoiSource.NONE)
    }

    private fun resolveFrameSignal(
        detections: List<Detection>,
        roiSelection: RoiSelection,
    ): FrameSignal {
        val roi = roiSelection.roi ?: return FrameSignal.none(roiSelection.source)
        val minSignalConfidence = minSignalConfidence(roiSelection.source)

        val greenCandidate = detections
            .asSequence()
            .filter { isGreenLight(it.className) }
            .filter { it.confidence >= minSignalConfidence }
            .filter { isCenterInsideRoi(it, roi) }
            .filter { passesSignalPositionGate(it, roi) }
            .maxByOrNull { it.confidence }

        val redCandidate = detections
            .asSequence()
            .filter { isRedLight(it.className) }
            .filter { it.confidence >= minSignalConfidence }
            .filter { isCenterInsideRoi(it, roi) }
            .filter { passesSignalPositionGate(it, roi) }
            .maxByOrNull { it.confidence }

        if (greenCandidate == null && redCandidate == null) {
            return FrameSignal.none(roiSelection.source)
        }
        if (greenCandidate != null && redCandidate != null) {
            val confidenceGap = abs(greenCandidate.confidence - redCandidate.confidence)
            if (confidenceGap < COLOR_AMBIGUITY_MARGIN) {
                return FrameSignal.none(roiSelection.source)
            }
        }

        val selected = listOfNotNull(greenCandidate, redCandidate).maxByOrNull { it.confidence }
            ?: return FrameSignal.none(roiSelection.source)
        return FrameSignal(
            state = if (isGreenLight(selected.className)) TrafficLightState.GREEN else TrafficLightState.RED,
            confidence = selected.confidence,
            source = roiSelection.source,
        )
    }

    private fun stabilizeTrafficLightState(frameSignal: FrameSignal): TrafficLightState {
        val protectedSignal = applyRecentGreenProtection(frameSignal)

        if (protectedSignal.state == TrafficLightState.GREEN) {
            greenProtectionFramesRemaining = GREEN_PROTECTION_FRAMES
        } else if (greenProtectionFramesRemaining > 0) {
            greenProtectionFramesRemaining -= 1
        }

        if (protectedSignal.state == TrafficLightState.NONE) {
            pendingTrafficLightState = TrafficLightState.NONE
            pendingStateFrames = 0
            missingSignalFrames += 1

            val requiredNoneFrames = when (stableTrafficLightState) {
                TrafficLightState.GREEN -> REQUIRED_NONE_AFTER_GREEN_FRAMES
                TrafficLightState.RED -> REQUIRED_NONE_AFTER_OTHER_FRAMES
                TrafficLightState.NONE -> 1
            }
            if (missingSignalFrames >= requiredNoneFrames) {
                stableTrafficLightState = TrafficLightState.NONE
            }
            return stableTrafficLightState
        }

        missingSignalFrames = 0

        if (protectedSignal.state == stableTrafficLightState) {
            pendingTrafficLightState = TrafficLightState.NONE
            pendingStateFrames = 0
            return stableTrafficLightState
        }

        if (pendingTrafficLightState != protectedSignal.state) {
            pendingTrafficLightState = protectedSignal.state
            pendingStateFrames = 1
        } else {
            pendingStateFrames += 1
        }

        val requiredFrames = requiredTransitionFrames(
            fromState = stableTrafficLightState,
            toState = protectedSignal.state,
        )
        if (pendingStateFrames >= requiredFrames) {
            stableTrafficLightState = protectedSignal.state
            pendingTrafficLightState = TrafficLightState.NONE
            pendingStateFrames = 0
        }

        return stableTrafficLightState
    }

    private fun applyRecentGreenProtection(frameSignal: FrameSignal): FrameSignal {
        if (frameSignal.state != TrafficLightState.RED) return frameSignal
        if (greenProtectionFramesRemaining <= 0) return frameSignal
        if (frameSignal.confidence >= MIN_PROTECTED_RED_CONFIDENCE) return frameSignal
        return FrameSignal.none(frameSignal.source)
    }

    private fun requiredTransitionFrames(
        fromState: TrafficLightState,
        toState: TrafficLightState,
    ): Int = when {
        fromState == TrafficLightState.GREEN && toState == TrafficLightState.RED -> {
            REQUIRED_RED_AFTER_GREEN_FRAMES
        }
        fromState == TrafficLightState.NONE -> REQUIRED_COLOR_FROM_NONE_FRAMES
        else -> REQUIRED_COLOR_SWITCH_FRAMES
    }

    private fun minSignalConfidence(source: TrafficLightRoiSource): Float = when (source) {
        TrafficLightRoiSource.PTL -> MIN_SIGNAL_CONFIDENCE_PTL
        TrafficLightRoiSource.EXPANDED -> MIN_SIGNAL_CONFIDENCE_EXPANDED
        TrafficLightRoiSource.TRACKED -> MIN_SIGNAL_CONFIDENCE_TRACKED
        TrafficLightRoiSource.NONE -> 1f
    }

    private fun updateTrackedRoi(
        roi: TrafficLightRoi,
        source: TrafficLightRoiSource,
    ): RoiSelection {
        trackedTrafficLightRoi = roi
        trackedRoiFrames = 0
        return RoiSelection(roi, source)
    }

    private fun expandLightToTrafficLightRoi(
        detection: Detection,
        frameWidth: Int,
        frameHeight: Int,
    ): TrafficLightRoi {
        val boxWidth = max(1f, detection.right - detection.left)
        val boxHeight = max(1f, detection.bottom - detection.top)
        val primaryPadding = boxHeight * LIGHT_ROI_EXPAND_RATIO
        val secondaryPadding = boxHeight * LIGHT_ROI_SECONDARY_PADDING_RATIO
        val sidePadding = boxWidth * LIGHT_ROI_SIDE_PADDING_RATIO
        val isGreen = isGreenLight(detection.className)

        val left = (detection.left - sidePadding).coerceIn(0f, frameWidth.toFloat())
        val top = if (isGreen) {
            (detection.top - primaryPadding).coerceIn(0f, frameHeight.toFloat())
        } else {
            (detection.top - secondaryPadding).coerceIn(0f, frameHeight.toFloat())
        }
        val right = (detection.right + sidePadding).coerceIn(0f, frameWidth.toFloat())
        val bottom = if (isGreen) {
            (detection.bottom + secondaryPadding).coerceIn(0f, frameHeight.toFloat())
        } else {
            (detection.bottom + primaryPadding).coerceIn(0f, frameHeight.toFloat())
        }

        return TrafficLightRoi(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            confidence = detection.confidence,
        )
    }

    private fun passesSignalPositionGate(
        detection: Detection,
        roi: TrafficLightRoi,
    ): Boolean {
        val roiHeight = max(1f, roi.bottom - roi.top)
        val normalizedCenterY = ((detection.top + detection.bottom) / 2f - roi.top) / roiHeight
        return when {
            isRedLight(detection.className) -> normalizedCenterY <= RED_REGION_MAX_RATIO
            isGreenLight(detection.className) -> normalizedCenterY >= GREEN_REGION_MIN_RATIO
            else -> false
        }
    }

    private fun overlapsRoi(
        detection: Detection,
        roi: TrafficLightRoi,
    ): Boolean {
        val overlapLeft = max(detection.left, roi.left)
        val overlapTop = max(detection.top, roi.top)
        val overlapRight = minOf(detection.right, roi.right)
        val overlapBottom = minOf(detection.bottom, roi.bottom)
        val overlapWidth = max(0f, overlapRight - overlapLeft)
        val overlapHeight = max(0f, overlapBottom - overlapTop)
        val overlapArea = overlapWidth * overlapHeight
        if (overlapArea <= 0f) return false

        val detectionArea = max(1f, detection.right - detection.left) * max(1f, detection.bottom - detection.top)
        return overlapArea / detectionArea >= MIN_ROI_OVERLAP_RATIO
    }

    private fun isCenterInsideRoi(detection: Detection, roi: TrafficLightRoi): Boolean {
        val centerX = (detection.left + detection.right) / 2f
        val centerY = (detection.top + detection.bottom) / 2f
        return centerX in roi.left..roi.right && centerY in roi.top..roi.bottom
    }

    private fun isTrafficLightHousing(className: String): Boolean =
        normalizeClassName(className) == "pedestrian_traffic_light"

    private fun isGreenLight(className: String): Boolean =
        normalizeClassName(className) in setOf("green", "green_light", "green_pedestrian_light")

    private fun isRedLight(className: String): Boolean =
        normalizeClassName(className) in setOf("red", "red_light", "red_pedestrian_light")

    private fun isSignalLight(className: String): Boolean =
        isGreenLight(className) || isRedLight(className)

    private fun normalizeClassName(className: String): String =
        className.trim().lowercase().replace(' ', '_')

    private data class PassResult(
        val detections: List<Detection>,
        val inferenceTimeMs: Long,
        val peakScore: Float,
        val cropRegion: CropRegion,
    )

    private data class RoiSelection(
        val roi: TrafficLightRoi?,
        val source: TrafficLightRoiSource,
    )

    private data class FrameSignal(
        val state: TrafficLightState,
        val confidence: Float,
        val source: TrafficLightRoiSource,
    ) {
        companion object {
            fun none(source: TrafficLightRoiSource): FrameSignal =
                FrameSignal(
                    state = TrafficLightState.NONE,
                    confidence = 0f,
                    source = source,
                )
        }
    }
}
