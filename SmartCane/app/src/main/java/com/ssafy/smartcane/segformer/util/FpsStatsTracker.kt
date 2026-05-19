package com.ssafy.smartcane.segformer.util

data class FpsSnapshot(
    val avgInferenceMs: Double,
    val avgPipelineMs: Double,
    val avgCallbackIntervalMs: Double?,
) {
    val inferenceFps: Double
        get() = if (avgInferenceMs <= 0.0) 0.0 else 1000.0 / avgInferenceMs

    val pipelineFps: Double
        get() = if (avgPipelineMs <= 0.0) 0.0 else 1000.0 / avgPipelineMs

    val callbackFps: Double?
        get() = avgCallbackIntervalMs?.takeIf { it > 0.0 }?.let { 1000.0 / it }
}

class FpsStatsTracker(
    private val windowSize: Int = 10,
) {
    private val inferenceTimes = ArrayDeque<Long>()
    private val pipelineTimes = ArrayDeque<Long>()
    private val callbackIntervals = ArrayDeque<Long>()
    private var lastCallbackTimestampMs: Long? = null

    fun record(
        inferenceTimeMs: Long,
        pipelineTimeMs: Long,
        callbackTimestampMs: Long,
    ): FpsSnapshot {
        push(inferenceTimes, inferenceTimeMs)
        push(pipelineTimes, pipelineTimeMs)

        lastCallbackTimestampMs?.let { previous ->
            push(callbackIntervals, callbackTimestampMs - previous)
        }
        lastCallbackTimestampMs = callbackTimestampMs

        return FpsSnapshot(
            avgInferenceMs = inferenceTimes.average(),
            avgPipelineMs = pipelineTimes.average(),
            avgCallbackIntervalMs = callbackIntervals.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    private fun push(buffer: ArrayDeque<Long>, value: Long) {
        buffer.addLast(value)
        while (buffer.size > windowSize) {
            buffer.removeFirst()
        }
    }
}
