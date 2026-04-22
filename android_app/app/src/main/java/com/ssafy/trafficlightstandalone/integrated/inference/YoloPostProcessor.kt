package com.ssafy.trafficlightstandalone.integrated.inference

import com.ssafy.trafficlightstandalone.integrated.model.Detection
import com.ssafy.trafficlightstandalone.integrated.model.LetterboxInfo
import kotlin.math.max
import kotlin.math.min

class YoloPostProcessor {
    private val boxChannels = 4

    fun decode(
        output: FloatArray,
        classNames: List<String>,
        letterbox: LetterboxInfo,
        confidenceThreshold: Float,
        iouThreshold: Float,
    ): List<Detection> {
        val channelCount = boxChannels + classNames.size
        require(output.isNotEmpty() && output.size % channelCount == 0) {
            "Unexpected output size ${output.size} for class count ${classNames.size}"
        }

        val numAnchors = output.size / channelCount
        val candidates = ArrayList<Detection>(numAnchors)

        for (anchorIndex in 0 until numAnchors) {
            val bestClassIndex = bestClassIndex(output, anchorIndex, numAnchors, classNames.size)
            val confidence = output[(boxChannels + bestClassIndex) * numAnchors + anchorIndex]
            if (confidence < confidenceThreshold) continue

            val centerX = output[anchorIndex] * letterbox.inputSize
            val centerY = output[numAnchors + anchorIndex] * letterbox.inputSize
            val width = output[(numAnchors * 2) + anchorIndex] * letterbox.inputSize
            val height = output[(numAnchors * 3) + anchorIndex] * letterbox.inputSize

            val mapped = mapToOriginal(
                left = centerX - width / 2f,
                top = centerY - height / 2f,
                right = centerX + width / 2f,
                bottom = centerY + height / 2f,
                letterbox = letterbox,
            )
            if (mapped.right <= mapped.left || mapped.bottom <= mapped.top) continue

            candidates += Detection(
                classIndex = bestClassIndex,
                className = classNames[bestClassIndex],
                confidence = confidence,
                left = mapped.left,
                top = mapped.top,
                right = mapped.right,
                bottom = mapped.bottom,
            )
        }

        return mergeDetections(candidates, iouThreshold)
    }

    fun mergeDetections(
        detections: List<Detection>,
        iouThreshold: Float,
    ): List<Detection> = nonMaximumSuppression(detections, iouThreshold)

    private fun bestClassIndex(
        output: FloatArray,
        anchorIndex: Int,
        numAnchors: Int,
        numClasses: Int,
    ): Int {
        var bestIndex = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (classIndex in 0 until numClasses) {
            val score = output[(boxChannels + classIndex) * numAnchors + anchorIndex]
            if (score > bestScore) {
                bestScore = score
                bestIndex = classIndex
            }
        }
        return bestIndex
    }

    private fun mapToOriginal(
        left: Float, top: Float, right: Float, bottom: Float,
        letterbox: LetterboxInfo,
    ): Detection {
        return Detection(
            classIndex = -1, className = "", confidence = 0f,
            left = ((left - letterbox.padX) / letterbox.scale).coerceIn(0f, letterbox.originalWidth.toFloat()),
            top = ((top - letterbox.padY) / letterbox.scale).coerceIn(0f, letterbox.originalHeight.toFloat()),
            right = ((right - letterbox.padX) / letterbox.scale).coerceIn(0f, letterbox.originalWidth.toFloat()),
            bottom = ((bottom - letterbox.padY) / letterbox.scale).coerceIn(0f, letterbox.originalHeight.toFloat()),
        )
    }

    private fun nonMaximumSuppression(
        detections: List<Detection>,
        iouThreshold: Float,
    ): List<Detection> {
        val selected = ArrayList<Detection>()
        val remaining = detections.sortedByDescending { it.confidence }.toMutableList()
        while (remaining.isNotEmpty()) {
            val current = remaining.removeAt(0)
            selected += current
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (other.classIndex != current.classIndex) continue
                if (iou(current, other) > iouThreshold) iterator.remove()
            }
        }
        return selected
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val intersection = interWidth * interHeight
        if (intersection <= 0f) return 0f
        val areaA = max(0f, a.right - a.left) * max(0f, a.bottom - a.top)
        val areaB = max(0f, b.right - b.left) * max(0f, b.bottom - b.top)
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
