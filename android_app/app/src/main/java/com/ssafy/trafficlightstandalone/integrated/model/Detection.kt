package com.ssafy.trafficlightstandalone.integrated.model

data class Detection(
    val classIndex: Int,
    val className: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

enum class TrafficLightRoiSource {
    PTL,
    EXPANDED,
    TRACKED,
    NONE,
}

enum class TrafficLightState {
    GREEN,
    RED,
    NONE,
}

data class TrafficLightRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
)

data class InferenceResult(
    val detections: List<Detection>,
    val inferenceTimeMs: Long,
    val pipelineTimeMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val peakScore: Float,
    val trafficLightRoi: TrafficLightRoi? = null,
    val trafficLightRoiSource: TrafficLightRoiSource = TrafficLightRoiSource.NONE,
    val trafficLightState: TrafficLightState = TrafficLightState.NONE,
)

data class LetterboxInfo(
    val inputSize: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val originalWidth: Int,
    val originalHeight: Int,
)

data class CropRegion(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

data class PreparedInput(
    val input: FloatArray,
    val letterbox: LetterboxInfo,
    val cropRegion: CropRegion,
)
