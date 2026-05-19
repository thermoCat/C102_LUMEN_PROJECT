package com.ssafy.smartcane.segformer.pose

import android.os.SystemClock
import com.ssafy.smartcane.segformer.model.GroundProjection

/**
 * Phone-only pseudo world pose for devices without ARCore or Jetson pose input.
 *
 * Rotation comes from TYPE_ROTATION_VECTOR through [OrientationProvider].
 * Forward translation is estimated from the frame-to-frame decrease of the
 * nearest projected walkable class distance. This is intentionally conservative:
 * it is a drift-prone odometer, not SLAM.
 */
class ImuSegWorldPoseProvider {
    private var worldX = 0f
    private var worldY = 0f
    private var worldZ = 0f
    private var lastWalkableMinForwardM: Float? = null
    private var lastTimestampNs = 0L

    @Synchronized
    fun snapshot(gravity: GravitySnapshot): WorldCameraPose? {
        if (!gravity.hasReading || gravity.cameraToWorldRotation.size != ROTATION_SIZE) return null
        lastTimestampNs = gravity.timestampNs
        return WorldCameraPose(
            matrix = matrixFromRotationAndTranslation(
                rotation = gravity.cameraToWorldRotation,
                tx = worldX,
                ty = worldY,
                tz = worldZ,
            ),
            timestampNs = gravity.timestampNs,
            receivedAtMs = SystemClock.elapsedRealtime(),
        )
    }

    @Synchronized
    fun updateFromProjection(
        projection: GroundProjection?,
        gravity: GravitySnapshot,
    ) {
        if (!gravity.hasReading || gravity.cameraToWorldRotation.size != ROTATION_SIZE) return
        val currentMinForward = projection
            ?.classSummaries
            ?.asSequence()
            ?.filter { it.classIndex in WALKABLE_CLASS_INDICES }
            ?.map { it.minForwardM }
            ?.filter { it in MIN_TRACKED_FORWARD_M..MAX_TRACKED_FORWARD_M }
            ?.minOrNull()

        if (currentMinForward == null) {
            lastWalkableMinForwardM = null
            return
        }

        val previousMinForward = lastWalkableMinForwardM
        if (previousMinForward != null) {
            val forwardDelta = previousMinForward - currentMinForward
            if (forwardDelta in MIN_FORWARD_DELTA_M..MAX_FORWARD_DELTA_M) {
                integrateForwardDelta(gravity.cameraToWorldRotation, forwardDelta)
            }
        }
        lastWalkableMinForwardM = currentMinForward
        lastTimestampNs = gravity.timestampNs
    }

    @Synchronized
    fun reset() {
        worldX = 0f
        worldY = 0f
        worldZ = 0f
        lastWalkableMinForwardM = null
        lastTimestampNs = 0L
    }

    private fun integrateForwardDelta(rotation: FloatArray, forwardDeltaM: Float) {
        worldX += rotation[2] * forwardDeltaM
        worldY += rotation[5] * forwardDeltaM
        worldZ += rotation[8] * forwardDeltaM
    }

    private fun matrixFromRotationAndTranslation(
        rotation: FloatArray,
        tx: Float,
        ty: Float,
        tz: Float,
    ): FloatArray {
        return floatArrayOf(
            rotation[0], rotation[1], rotation[2], tx,
            rotation[3], rotation[4], rotation[5], ty,
            rotation[6], rotation[7], rotation[8], tz,
            0f, 0f, 0f, 1f,
        )
    }

    companion object {
        private const val ROTATION_SIZE = 9
        private val WALKABLE_CLASS_INDICES = setOf(3, 6)
        private const val MIN_TRACKED_FORWARD_M = 0.20f
        private const val MAX_TRACKED_FORWARD_M = 6.00f
        private const val MIN_FORWARD_DELTA_M = 0.02f
        private const val MAX_FORWARD_DELTA_M = 0.80f
    }
}
