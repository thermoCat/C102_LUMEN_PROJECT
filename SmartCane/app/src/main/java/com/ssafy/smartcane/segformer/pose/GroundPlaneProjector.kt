package com.ssafy.smartcane.segformer.pose

import com.ssafy.smartcane.segformer.model.ClassGroundSummary
import com.ssafy.smartcane.segformer.model.GroundProjection
import com.ssafy.smartcane.segformer.model.LetterboxInfo
import com.ssafy.smartcane.segformer.model.SourcePoint
import com.ssafy.smartcane.segformer.model.BrailleTilePattern
import com.ssafy.smartcane.segformer.model.VirtualBrailleAnchorMode
import com.ssafy.smartcane.segformer.model.VirtualBrailleBlock
import com.ssafy.smartcane.segformer.model.VirtualBrailleGuide
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Back-projects the segmentation mask onto a gravity-aligned ground plane and
 * summarises, per class, the closest forward distance and the centroid in the
 * camera-local (forward, lateral) frame.
 *
 * This is the per-frame "coordinate-system fixing" stage: the gravity vector
 * from [OrientationProvider] removes pitch/roll from the raw camera frame so
 * the same physical point gives the same metric reading whether the user is
 * looking straight ahead or tilts the phone.
 */
class GroundPlaneProjector(
    private val cameraHeightM: Float = WorldFrame.CAMERA_HEIGHT_M,
    private val pixelStride: Int = WorldFrame.PROJECTION_PIXEL_STRIDE,
) {
    private val worldAnchors = mutableListOf<WorldBrailleGuideAnchor>()

    fun project(
        mask: IntArray,
        maskWidth: Int,
        maskHeight: Int,
        letterbox: LetterboxInfo,
        intrinsics: CameraIntrinsics,
        gravityCam: FloatArray,
        worldPose: WorldCameraPose?,
        classCount: Int,
    ): GroundProjection? {
        val gNorm = sqrt(
            gravityCam[0] * gravityCam[0] +
                gravityCam[1] * gravityCam[1] +
                gravityCam[2] * gravityCam[2],
        )
        if (gNorm < 1e-3f) return null
        val gxu = gravityCam[0] / gNorm
        val gyu = gravityCam[1] / gNorm
        val gzu = gravityCam[2] / gNorm

        // Forward axis: optical z (0,0,1) projected onto the ground plane (orthogonal to gravity).
        val zg = gzu
        val fwdX0 = -zg * gxu
        val fwdY0 = -zg * gyu
        val fwdZ0 = 1f - zg * gzu
        val fwdNorm = sqrt(fwdX0 * fwdX0 + fwdY0 * fwdY0 + fwdZ0 * fwdZ0)
        if (fwdNorm < 1e-3f) return null
        val fx = fwdX0 / fwdNorm
        val fy = fwdY0 / fwdNorm
        val fz = fwdZ0 / fwdNorm

        // Lateral axis: gravity 횞 forward (right-handed; positive lateral = user's right).
        val lxRaw = gyu * fz - gzu * fy
        val lyRaw = gzu * fx - gxu * fz
        val lzRaw = gxu * fy - gyu * fx
        val lNorm = sqrt(lxRaw * lxRaw + lyRaw * lyRaw + lzRaw * lzRaw).coerceAtLeast(1e-6f)
        val lx = lxRaw / lNorm
        val ly = lyRaw / lNorm
        val lz = lzRaw / lNorm
        val groundBasis = GroundBasis(
            gx = gxu,
            gy = gyu,
            gz = gzu,
            forwardX = fx,
            forwardY = fy,
            forwardZ = fz,
            lateralX = lx,
            lateralY = ly,
            lateralZ = lz,
        )

        val fxK = intrinsics.fx
        val fyK = intrinsics.fy
        val cxK = intrinsics.cx
        val cyK = intrinsics.cy

        val pixelCounts = IntArray(classCount)
        val minForward = FloatArray(classCount) { Float.POSITIVE_INFINITY }
        val sumForward = FloatArray(classCount)
        val sumLateral = FloatArray(classCount)
        val guideBandStartForwardM = VIRTUAL_GUIDE_START_FORWARD_M
        val guideBandEndForwardM = VIRTUAL_GUIDE_START_FORWARD_M + virtualGuideTotalLengthM()
        var walkableGuideBandPixels = 0
        var walkableGuideBandSumForward = 0f
        var walkableGuideBandSumLateral = 0f

        val invScale = 1f / letterbox.scale
        val sourceWidth = letterbox.originalWidth.toFloat()
        val sourceHeight = letterbox.originalHeight.toFloat()

        var v = 0
        while (v < maskHeight) {
            val rowBase = v * maskWidth
            val vSrc = (v - letterbox.padY) * invScale
            if (vSrc >= 0f && vSrc < sourceHeight) {
                val yn = (vSrc - cyK) / fyK
                var u = 0
                while (u < maskWidth) {
                    val cls = mask[rowBase + u]
                    if (cls > 0 && cls < classCount) {
                        val uSrc = (u - letterbox.padX) * invScale
                        if (uSrc >= 0f && uSrc < sourceWidth) {
                            val xn = (uSrc - cxK) / fxK
                            val dotRayG = xn * gxu + yn * gyu + gzu
                            if (dotRayG > WorldFrame.MIN_RAY_DOT_GRAVITY) {
                                val t = cameraHeightM / dotRayG
                                val px = t * xn
                                val py = t * yn
                                val pz = t
                                val forward = px * fx + py * fy + pz * fz
                                if (forward > 0f) {
                                    val lateral = px * lx + py * ly + pz * lz
                                    pixelCounts[cls] += 1
                                    if (forward < minForward[cls]) minForward[cls] = forward
                                    sumForward[cls] += forward
                                    sumLateral[cls] += lateral
                                    if (
                                        isWalkableClass(cls) &&
                                        forward in guideBandStartForwardM..guideBandEndForwardM
                                    ) {
                                        walkableGuideBandPixels += 1
                                        walkableGuideBandSumForward += forward
                                        walkableGuideBandSumLateral += lateral
                                    }
                                }
                            }
                        }
                    }
                    u += pixelStride
                }
            }
            v += pixelStride
        }

        val summaries = mutableListOf<ClassGroundSummary>()
        for (idx in 0 until classCount) {
            val n = pixelCounts[idx]
            if (n < WorldFrame.MIN_CLASS_PIXELS_FOR_PROJECTION) continue
            summaries += ClassGroundSummary(
                classIndex = idx,
                pixelCount = n,
                minForwardM = minForward[idx],
                centroidForwardM = sumForward[idx] / n,
                centroidLateralM = sumLateral[idx] / n,
            )
        }
        val walkableCandidate = findWalkableGroundCandidate(
            pixelCounts = pixelCounts,
            minForward = minForward,
            sumForward = sumForward,
            sumLateral = sumLateral,
            classCount = classCount,
            guideBandPixels = walkableGuideBandPixels,
            guideBandSumForward = walkableGuideBandSumForward,
            guideBandSumLateral = walkableGuideBandSumLateral,
        )
        val physicalBrailleOverlapsCandidate = physicalBrailleOverlapsVirtualGuideCandidate(
            pixelCounts = pixelCounts,
            sumForward = sumForward,
            sumLateral = sumLateral,
            classCount = classCount,
            walkableCandidate = walkableCandidate,
        )
        val virtualBrailleGuide = if (physicalBrailleOverlapsCandidate) {
            null
        } else {
            buildVirtualBrailleGuide(groundBasis, intrinsics, worldPose, walkableCandidate)
        }

        if (summaries.isEmpty() && virtualBrailleGuide == null) return null
        return GroundProjection(
            cameraHeightM = cameraHeightM,
            classSummaries = summaries,
            virtualBrailleGuide = virtualBrailleGuide,
        )
    }

    private fun buildVirtualBrailleGuide(
        basis: GroundBasis,
        intrinsics: CameraIntrinsics,
        worldPose: WorldCameraPose?,
        walkableCandidate: WalkableGroundCandidate?,
    ): VirtualBrailleGuide? {
        if (worldPose != null) {
            if (worldAnchors.isEmpty() && walkableCandidate != null) {
                addInitialWorldAnchor(
                    basis = basis,
                    worldPose = worldPose,
                    guideLateralM = walkableCandidate.centerLateralM,
                )
            }
            val blocks = worldAnchors.flatMap { anchor ->
                projectWorldAnchor(anchor, basis, worldPose, intrinsics)
            }
            if (blocks.isEmpty()) return null
            val minForward = blocks.minOf { it.centerForwardM } - VIRTUAL_GUIDE_BLOCK_LENGTH_M / 2f
            val maxForward = blocks.maxOf { it.centerForwardM } + VIRTUAL_GUIDE_BLOCK_LENGTH_M / 2f
            return VirtualBrailleGuide(
                blocks = blocks,
                startForwardM = minForward,
                endForwardM = maxForward,
                centerLateralM = blocks.map { it.centerLateralM }.average().toFloat(),
                anchorMode = VirtualBrailleAnchorMode.WORLD_FIXED,
                anchorCount = worldAnchors.size,
            )
        }

        return null
    }

    private fun addInitialWorldAnchor(
        basis: GroundBasis,
        worldPose: WorldCameraPose,
        guideLateralM: Float,
    ) {
        val clampedLateral = guideLateralM.coerceIn(
            -MAX_VIRTUAL_GUIDE_LATERAL_OFFSET_M,
            MAX_VIRTUAL_GUIDE_LATERAL_OFFSET_M,
        )
        val proposedCenter = worldPose.cameraToWorld(
            groundPointCamera(
                forwardM = VIRTUAL_GUIDE_START_FORWARD_M + virtualGuideTotalLengthM() / 2f,
                lateralM = clampedLateral,
                basis = basis,
            ),
        )

        val blocks = mutableListOf<WorldBrailleBlockAnchor>()
        val halfWidth = VIRTUAL_GUIDE_WIDTH_M / 2f
        var startForward = VIRTUAL_GUIDE_START_FORWARD_M
        repeat(VIRTUAL_GUIDE_BLOCK_COUNT) { blockIndex ->
            val endForward = startForward + VIRTUAL_GUIDE_BLOCK_LENGTH_M
            val corners = listOf(
                groundPointCamera(startForward, clampedLateral - halfWidth, basis),
                groundPointCamera(endForward, clampedLateral - halfWidth, basis),
                groundPointCamera(endForward, clampedLateral + halfWidth, basis),
                groundPointCamera(startForward, clampedLateral + halfWidth, basis),
            ).map(worldPose::cameraToWorld)
            val centerWorld = worldPose.cameraToWorld(
                groundPointCamera(
                    forwardM = (startForward + endForward) / 2f,
                    lateralM = clampedLateral,
                    basis = basis,
                ),
            )
            blocks += WorldBrailleBlockAnchor(
                cornersWorld = corners,
                centerWorld = centerWorld,
                pattern = patternForBlock(blockIndex),
            )
            startForward = endForward + VIRTUAL_GUIDE_BLOCK_GAP_M
        }

        worldAnchors += WorldBrailleGuideAnchor(
            centerWorld = proposedCenter,
            blocks = blocks,
        )
    }

    private fun findWalkableGroundCandidate(
        pixelCounts: IntArray,
        minForward: FloatArray,
        sumForward: FloatArray,
        sumLateral: FloatArray,
        classCount: Int,
        guideBandPixels: Int,
        guideBandSumForward: Float,
        guideBandSumLateral: Float,
    ): WalkableGroundCandidate? {
        var walkablePixels = 0
        var weightedForward = 0f
        var weightedLateral = 0f
        var nearestForward = Float.POSITIVE_INFINITY
        for (classIndex in WALKABLE_CLASS_INDICES) {
            if (classIndex !in 0 until classCount) continue
            val count = pixelCounts[classIndex]
            if (count <= 0) continue
            walkablePixels += count
            weightedForward += sumForward[classIndex]
            weightedLateral += sumLateral[classIndex]
            if (minForward[classIndex] < nearestForward) nearestForward = minForward[classIndex]
        }
        if (walkablePixels < MIN_WALKABLE_PIXELS_FOR_VIRTUAL_GUIDE) return null
        if (nearestForward == Float.POSITIVE_INFINITY || nearestForward > MAX_WALKABLE_NEAREST_FORWARD_M) {
            return null
        }
        val useGuideBand = guideBandPixels >= MIN_GUIDE_BAND_PIXELS_FOR_VIRTUAL_GUIDE
        val centerForward = if (useGuideBand) {
            guideBandSumForward / guideBandPixels
        } else {
            (weightedForward / walkablePixels).coerceIn(
                MIN_WALKABLE_CENTER_FORWARD_M,
                MAX_WALKABLE_CENTER_FORWARD_M,
            )
        }
        val centerLateral = if (useGuideBand) {
            guideBandSumLateral / guideBandPixels
        } else {
            weightedLateral / walkablePixels
        }
        return WalkableGroundCandidate(
            pixelCount = walkablePixels,
            centerForwardM = centerForward,
            centerLateralM = centerLateral,
        )
    }

    private fun physicalBrailleOverlapsVirtualGuideCandidate(
        pixelCounts: IntArray,
        sumForward: FloatArray,
        sumLateral: FloatArray,
        classCount: Int,
        walkableCandidate: WalkableGroundCandidate?,
    ): Boolean {
        if (walkableCandidate == null) return false
        if (BRAILLE_GUIDE_BLOCKS_CLASS_INDEX !in 0 until classCount) return false
        val braillePixels = pixelCounts[BRAILLE_GUIDE_BLOCKS_CLASS_INDEX]
        if (braillePixels < WorldFrame.MIN_CLASS_PIXELS_FOR_PROJECTION) return false

        val brailleCenterForwardM = sumForward[BRAILLE_GUIDE_BLOCKS_CLASS_INDEX] / braillePixels
        val brailleCenterLateralM = sumLateral[BRAILLE_GUIDE_BLOCKS_CLASS_INDEX] / braillePixels
        val candidateLateralM = walkableCandidate.centerLateralM.coerceIn(
            -MAX_VIRTUAL_GUIDE_LATERAL_OFFSET_M,
            MAX_VIRTUAL_GUIDE_LATERAL_OFFSET_M,
        )

        val guideStartForwardM = VIRTUAL_GUIDE_START_FORWARD_M - PHYSICAL_BRAILLE_FORWARD_MARGIN_M
        val guideEndForwardM =
            VIRTUAL_GUIDE_START_FORWARD_M + virtualGuideTotalLengthM() + PHYSICAL_BRAILLE_FORWARD_MARGIN_M
        val lateralToleranceM = VIRTUAL_GUIDE_WIDTH_M / 2f + PHYSICAL_BRAILLE_LATERAL_MARGIN_M

        return brailleCenterForwardM in guideStartForwardM..guideEndForwardM &&
            abs(brailleCenterLateralM - candidateLateralM) <= lateralToleranceM
    }

    private fun projectWorldAnchor(
        anchor: WorldBrailleGuideAnchor,
        basis: GroundBasis,
        worldPose: WorldCameraPose,
        intrinsics: CameraIntrinsics,
    ): List<VirtualBrailleBlock> {
        return anchor.blocks.mapNotNull { block ->
            val cameraCorners = block.cornersWorld.map(worldPose::worldToCamera)
            val sourceCorners = cameraCorners.mapNotNull { projectCameraPoint(it, intrinsics) }
            if (sourceCorners.size != 4 || !isVisibleEnough(sourceCorners, intrinsics)) {
                return@mapNotNull null
            }
            val centerCamera = worldPose.worldToCamera(block.centerWorld)
            VirtualBrailleBlock(
                corners = sourceCorners,
                centerForwardM = dotForward(centerCamera, basis),
                centerLateralM = dotLateral(centerCamera, basis),
                pattern = block.pattern,
            )
        }
    }

    private fun groundPointCamera(
        forwardM: Float,
        lateralM: Float,
        basis: GroundBasis,
    ): CameraPoint {
        return CameraPoint(
            x = cameraHeightM * basis.gx +
                forwardM * basis.forwardX +
                lateralM * basis.lateralX,
            y = cameraHeightM * basis.gy +
                forwardM * basis.forwardY +
                lateralM * basis.lateralY,
            z = cameraHeightM * basis.gz +
                forwardM * basis.forwardZ +
                lateralM * basis.lateralZ,
        )
    }

    private fun projectCameraPoint(
        point: CameraPoint,
        intrinsics: CameraIntrinsics,
    ): SourcePoint? {
        if (point.z <= MIN_PROJECTED_Z_M) return null
        val sourceX = intrinsics.fx * (point.x / point.z) + intrinsics.cx
        val sourceY = intrinsics.fy * (point.y / point.z) + intrinsics.cy
        if (!sourceX.isFinite() || !sourceY.isFinite()) return null
        return SourcePoint(sourceX, sourceY)
    }

    private fun dotForward(point: CameraPoint, basis: GroundBasis): Float {
        return point.x * basis.forwardX + point.y * basis.forwardY + point.z * basis.forwardZ
    }

    private fun dotLateral(point: CameraPoint, basis: GroundBasis): Float {
        return point.x * basis.lateralX + point.y * basis.lateralY + point.z * basis.lateralZ
    }

    private fun virtualGuideTotalLengthM(): Float {
        return VIRTUAL_GUIDE_BLOCK_COUNT * VIRTUAL_GUIDE_BLOCK_LENGTH_M +
            (VIRTUAL_GUIDE_BLOCK_COUNT - 1) * VIRTUAL_GUIDE_BLOCK_GAP_M
    }

    private fun patternForBlock(index: Int): BrailleTilePattern {
        return if (index == 0 || index == VIRTUAL_GUIDE_BLOCK_COUNT - 1) {
            BrailleTilePattern.WARNING
        } else {
            BrailleTilePattern.DIRECTIONAL
        }
    }

    private fun isVisibleEnough(corners: List<SourcePoint>, intrinsics: CameraIntrinsics): Boolean {
        val marginX = intrinsics.sourceWidth * PROJECTION_VISIBILITY_MARGIN_RATIO
        val marginY = intrinsics.sourceHeight * PROJECTION_VISIBILITY_MARGIN_RATIO
        return corners.any {
            it.x >= -marginX &&
                it.x <= intrinsics.sourceWidth + marginX &&
                it.y >= -marginY &&
                it.y <= intrinsics.sourceHeight + marginY
        }
    }

    private fun isWalkableClass(classIndex: Int): Boolean {
        return classIndex == ALLEY_CLASS_INDEX || classIndex == SIDEWALK_CLASS_INDEX
    }

    private data class WorldBrailleGuideAnchor(
        val centerWorld: WorldPoint,
        val blocks: List<WorldBrailleBlockAnchor>,
    )

    private data class WorldBrailleBlockAnchor(
        val cornersWorld: List<WorldPoint>,
        val centerWorld: WorldPoint,
        val pattern: BrailleTilePattern,
    )

    private data class WalkableGroundCandidate(
        val pixelCount: Int,
        val centerForwardM: Float,
        val centerLateralM: Float,
    )

    private data class GroundBasis(
        val gx: Float,
        val gy: Float,
        val gz: Float,
        val forwardX: Float,
        val forwardY: Float,
        val forwardZ: Float,
        val lateralX: Float,
        val lateralY: Float,
        val lateralZ: Float,
    )

    companion object {
        private const val BRAILLE_GUIDE_BLOCKS_CLASS_INDEX = 5
        private const val ALLEY_CLASS_INDEX = 3
        private const val SIDEWALK_CLASS_INDEX = 6
        private val WALKABLE_CLASS_INDICES = intArrayOf(ALLEY_CLASS_INDEX, SIDEWALK_CLASS_INDEX)
        private const val MIN_WALKABLE_PIXELS_FOR_VIRTUAL_GUIDE = 96
        private const val MIN_GUIDE_BAND_PIXELS_FOR_VIRTUAL_GUIDE = 48
        private const val MIN_WALKABLE_CENTER_FORWARD_M = 0.30f
        private const val MAX_WALKABLE_CENTER_FORWARD_M = 6.00f
        private const val MAX_WALKABLE_NEAREST_FORWARD_M = 6.00f
        private const val MAX_VIRTUAL_GUIDE_LATERAL_OFFSET_M = 0.80f
        private const val VIRTUAL_GUIDE_START_FORWARD_M = 0.80f
        private const val VIRTUAL_GUIDE_BLOCK_LENGTH_M = 0.40f
        private const val VIRTUAL_GUIDE_BLOCK_GAP_M = 0.06f
        private const val VIRTUAL_GUIDE_WIDTH_M = 0.32f
        private const val VIRTUAL_GUIDE_BLOCK_COUNT = 8
        private const val PHYSICAL_BRAILLE_FORWARD_MARGIN_M = 0.40f
        private const val PHYSICAL_BRAILLE_LATERAL_MARGIN_M = 0.30f
        private const val MIN_PROJECTED_Z_M = 0.05f
        private const val PROJECTION_VISIBILITY_MARGIN_RATIO = 0.25f
    }
}
