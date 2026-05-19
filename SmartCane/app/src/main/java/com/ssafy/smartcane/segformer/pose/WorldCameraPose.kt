package com.ssafy.smartcane.segformer.pose

data class WorldCameraPose(
    val matrix: FloatArray,
    val timestampNs: Long,
    val receivedAtMs: Long,
) {
    init {
        require(matrix.size == MATRIX_SIZE) {
            "T_world_camera must contain 16 row-major values"
        }
    }

    fun cameraToWorld(point: CameraPoint): WorldPoint {
        val x = matrix[0] * point.x + matrix[1] * point.y + matrix[2] * point.z + matrix[3]
        val y = matrix[4] * point.x + matrix[5] * point.y + matrix[6] * point.z + matrix[7]
        val z = matrix[8] * point.x + matrix[9] * point.y + matrix[10] * point.z + matrix[11]
        return WorldPoint(x, y, z)
    }

    fun worldToCamera(point: WorldPoint): CameraPoint {
        val dx = point.x - matrix[3]
        val dy = point.y - matrix[7]
        val dz = point.z - matrix[11]
        val x = matrix[0] * dx + matrix[4] * dy + matrix[8] * dz
        val y = matrix[1] * dx + matrix[5] * dy + matrix[9] * dz
        val z = matrix[2] * dx + matrix[6] * dy + matrix[10] * dz
        return CameraPoint(x, y, z)
    }

    companion object {
        private const val MATRIX_SIZE = 16
    }
}

data class CameraPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class WorldPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)
