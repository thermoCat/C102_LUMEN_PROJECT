package com.ssafy.smartcane.segformer.model

data class SegmentationResult(
    val mask: IntArray,
    val maskWidth: Int,
    val maskHeight: Int,
    val classNames: List<String>,
    val classPixelCounts: IntArray,
    val inferenceTimeMs: Long,
    val pipelineTimeMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val letterbox: LetterboxInfo,
    val dominantClassIndex: Int,
    val pose: CameraPose? = null,
    val groundProjection: GroundProjection? = null,
)

data class LetterboxInfo(
    val inputSize: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val originalWidth: Int,
    val originalHeight: Int,
)

data class PreparedInput(
    val input: FloatArray,
    val letterbox: LetterboxInfo,
)

data class CameraPose(
    val gravityCam: FloatArray,
    val timestampNs: Long,
)

data class GroundProjection(
    val cameraHeightM: Float,
    val classSummaries: List<ClassGroundSummary>,
    val virtualBrailleGuide: VirtualBrailleGuide? = null,
)

data class ClassGroundSummary(
    val classIndex: Int,
    val pixelCount: Int,
    val minForwardM: Float,
    val centroidForwardM: Float,
    val centroidLateralM: Float,
)

data class VirtualBrailleGuide(
    val blocks: List<VirtualBrailleBlock>,
    val startForwardM: Float,
    val endForwardM: Float,
    val centerLateralM: Float,
    val anchorMode: VirtualBrailleAnchorMode,
    val anchorCount: Int,
)

data class VirtualBrailleBlock(
    val corners: List<SourcePoint>,
    val centerForwardM: Float,
    val centerLateralM: Float,
    val pattern: BrailleTilePattern,
)

enum class VirtualBrailleAnchorMode {
    WORLD_FIXED,
}

enum class BrailleTilePattern {
    DIRECTIONAL,
    WARNING,
}

data class SourcePoint(
    val x: Float,
    val y: Float,
)
