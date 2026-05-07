package com.ssafy.smartcane.lumen2.assist

import com.ssafy.smartcane.lumen2.ar.ArFrameData

class AssistEngine {
    private companion object {
        private const val MIN_SEMANTIC_ZONE_SAMPLES = 6
        private const val MIN_DISTANCE_ARC_VISIBLE_RATIO = 0.25f
    }

    private var currentState = AssistState.SYSTEM_UNSTABLE
    private var currentCommand = AssistCommand.SYSTEM_UNSTABLE
    private var stateEnteredAt = 0L
    private var candidateCommand: AssistCommand? = null
    private var candidateSince = 0L
    private var lastSpokenAt = 0L
    private var lastVibratedAt = 0L
    private val semanticStabilizer = AssistSemanticStabilizer()
    private val curbBoundaryStabilizer = AssistCurbBoundaryStabilizer()
    private val trafficStabilizer = AssistTrafficStabilizer()

    fun update(
        frame: ArFrameData,
        trafficEvidence: TrafficSceneEvidence = TrafficSceneEvidence(TrafficSceneStatus.UNKNOWN, emptyList()),
        nowMillis: Long = System.currentTimeMillis()
    ): AssistDecision {
        if (stateEnteredAt == 0L) stateEnteredAt = nowMillis
        val confidence = sensorConfidence(frame)
        val depthLayer = AssistDepthLayer.analyze(frame)
        val frontEvidence = semanticStabilizer.stabilize(
            evidence = AssistSemanticAnalyzer.analyzeCorridor(frame, 0f),
            nowMillis = nowMillis
        )
        val curbBoundary = curbBoundaryStabilizer.stabilize(
            evidence = AssistCurbBoundaryAnalyzer.analyze(frame),
            nowMillis = nowMillis
        )
        val stableTrafficEvidence = trafficStabilizer.stabilize(trafficEvidence, nowMillis)
        val awareness = buildAwareness(confidence, frame, depthLayer, frontEvidence, curbBoundary, stableTrafficEvidence)
        val rawCommand = commandFromAwareness(awareness, confidence)
        val command = stabilize(rawCommand, nowMillis)
        val state = stateFor(command, confidence)
        val changed = state != currentState || command != currentCommand

        if (changed) {
            currentState = state
            currentCommand = command
            stateEnteredAt = nowMillis
        }
        val visualization = AssistVisualizationBuilder.build(frame, depthLayer, frontEvidence, curbBoundary, stableTrafficEvidence)

        val speech = commandSpeech(command)
        val shouldSpeak = speech != null && shouldSpeak(state, changed, nowMillis)
        if (shouldSpeak) lastSpokenAt = nowMillis

        val shouldVibrate = state != AssistState.NORMAL && shouldVibrate(state, changed, nowMillis)
        if (shouldVibrate) lastVibratedAt = nowMillis

        return AssistDecision(
            state = state,
            command = command,
            shouldSpeak = shouldSpeak,
            speech = if (shouldSpeak) speech else null,
            shouldVibrate = shouldVibrate,
            confidence = confidence,
            awareness = awareness,
            reason = decisionReason(awareness, confidence),
            visualization = visualization
        )
    }

    private fun buildAwareness(
        confidence: SensorConfidence,
        frame: ArFrameData,
        depthLayer: DepthLayerResult,
        frontEvidence: SemanticCorridorEvidence?,
        curbBoundary: CurbBoundaryEvidence,
        trafficEvidence: TrafficSceneEvidence
    ): AwarenessSnapshot {
        if (confidence.confidence < 0.30f) {
            return AwarenessSnapshot(
                frontStatus = FrontStatus.UNKNOWN,
                leftSpace = SideSpaceStatus.UNKNOWN,
                rightSpace = SideSpaceStatus.UNKNOWN,
                curbBoundary = CurbBoundaryStatus.UNKNOWN,
                trafficScene = TrafficSceneStatus.UNKNOWN,
                depthAnomaly = depthLayer.status,
                frontReason = confidence.unstableReason ?: "low confidence",
                sideHintReason = null,
                depthReason = depthLayer.reason
            )
        }

        val guideRange = guideRangeVisibility(frame)
        if (!guideRange.immediateVisible && !guideRange.nearVisible && !guideRange.farVisible) {
            return AwarenessSnapshot(
                frontStatus = FrontStatus.UNKNOWN,
                leftSpace = SideSpaceStatus.UNKNOWN,
                rightSpace = SideSpaceStatus.UNKNOWN,
                curbBoundary = curbBoundary.status,
                trafficScene = trafficEvidence.status,
                depthAnomaly = depthLayer.status,
                frontReason = guideRange.reason,
                sideHintReason = null,
                depthReason = depthLayer.reason
            )
        }

        val corridor = assessSemanticCorridor(frontEvidence, guideRange)
        val frontStatus = corridor.status ?: FrontStatus.UNKNOWN
        val shouldCheckSideSpace = frontStatus == FrontStatus.BLOCKED
        val leftSpace = if (shouldCheckSideSpace) {
            assessSideNearSemanticFan(frame, left = true)
        } else {
            SideSpaceStatus.UNKNOWN
        }
        val rightSpace = if (shouldCheckSideSpace) {
            assessSideNearSemanticFan(frame, left = false)
        } else {
            SideSpaceStatus.UNKNOWN
        }
        val sideHint = sideHintReason(frontStatus, leftSpace, rightSpace)

        return AwarenessSnapshot(
            frontStatus = frontStatus,
            leftSpace = leftSpace,
            rightSpace = rightSpace,
            curbBoundary = curbBoundary.status,
            trafficScene = trafficEvidence.status,
            depthAnomaly = depthLayer.status,
            frontReason = corridor.reason ?: "semantic corridor unknown",
            sideHintReason = sideHint,
            depthReason = depthLayer.reason
        )
    }

    private fun commandFromAwareness(awareness: AwarenessSnapshot, confidence: SensorConfidence): AssistCommand {
        if (awareness.frontReason.startsWith("guide range") ||
            awareness.frontReason == "near corridor not visible"
        ) return AssistCommand.CAMERA_ADJUST
        if (confidence.confidence < 0.30f || awareness.frontStatus == FrontStatus.UNKNOWN) return AssistCommand.SYSTEM_UNSTABLE
        if (awareness.frontStatus == FrontStatus.CRITICAL) return AssistCommand.STOP
        if (awareness.frontStatus == FrontStatus.BLOCKED) {
            return when {
                awareness.rightSpace == SideSpaceStatus.AVAILABLE && awareness.leftSpace != SideSpaceStatus.AVAILABLE -> AssistCommand.RIGHT_SPACE
                awareness.leftSpace == SideSpaceStatus.AVAILABLE && awareness.rightSpace != SideSpaceStatus.AVAILABLE -> AssistCommand.LEFT_SPACE
                awareness.rightSpace == SideSpaceStatus.AVAILABLE && awareness.leftSpace == SideSpaceStatus.AVAILABLE -> AssistCommand.BOTH_SIDE_SPACE
                else -> AssistCommand.FRONT_LIMIT
            }
        }
        if (awareness.frontStatus == FrontStatus.CAUTION) {
            return AssistCommand.FRONT_CAUTION
        }
        if (awareness.depthAnomaly != DepthAnomalyStatus.CLEAR && awareness.depthAnomaly != DepthAnomalyStatus.UNKNOWN) {
            return AssistCommand.DEPTH_CAUTION
        }
        return AssistCommand.KEEP
    }

    private fun stabilize(raw: AssistCommand, now: Long): AssistCommand {
        if (raw == AssistCommand.STOP || raw == AssistCommand.SYSTEM_UNSTABLE) {
            candidateCommand = null
            return raw
        }
        val currentIsSide = currentCommand == AssistCommand.LEFT_SPACE || currentCommand == AssistCommand.RIGHT_SPACE
        val rawIsOppositeSide = (currentCommand == AssistCommand.LEFT_SPACE && raw == AssistCommand.RIGHT_SPACE) ||
            (currentCommand == AssistCommand.RIGHT_SPACE && raw == AssistCommand.LEFT_SPACE)
        if (currentIsSide && rawIsOppositeSide && now - stateEnteredAt < 2200L) {
            candidateCommand = null
            return currentCommand
        }
        if (raw == currentCommand) {
            candidateCommand = null
            return currentCommand
        }
        val requiredMillis = when (raw) {
            AssistCommand.KEEP -> 1800L
            AssistCommand.FRONT_CAUTION,
            AssistCommand.FRONT_LIMIT,
            AssistCommand.DEPTH_CAUTION -> 0L
            AssistCommand.LEFT_SPACE, AssistCommand.RIGHT_SPACE, AssistCommand.BOTH_SIDE_SPACE -> 600L
            AssistCommand.CAMERA_ADJUST -> 800L
            else -> 900L
        }
        if (candidateCommand != raw) {
            candidateCommand = raw
            candidateSince = now
            return currentCommand
        }
        if (now - candidateSince < requiredMillis) return currentCommand
        candidateCommand = null
        return raw
    }

    private fun assessSemanticCorridor(
        semanticEvidence: SemanticCorridorEvidence?,
        guideRange: GuideRangeVisibility
    ): ForwardCorridorAssessment {
        if (semanticEvidence == null) {
            return ForwardCorridorAssessment(null, null)
        }

        if (semanticEvidence.totalSamples < MIN_SEMANTIC_ZONE_SAMPLES) {
            return ForwardCorridorAssessment(FrontStatus.UNKNOWN, "semantic corridor low evidence")
        }

        val immediateNonWalkable = semanticEvidence.immediate.visible &&
            guideRange.immediateVisible &&
            semanticEvidence.immediate.blocked
        val nearNonWalkable = semanticEvidence.near.visible &&
            guideRange.nearVisible &&
            semanticEvidence.near.blocked
        val planNonWalkable = semanticEvidence.plan.visible &&
            guideRange.farVisible &&
            semanticEvidence.plan.blocked
        val anyVisibleZone = (guideRange.immediateVisible && semanticEvidence.immediate.visible) ||
            (guideRange.nearVisible && semanticEvidence.near.visible) ||
            (guideRange.farVisible && semanticEvidence.plan.visible)

        return when {
            immediateNonWalkable -> ForwardCorridorAssessment(
                FrontStatus.CRITICAL,
                "corridor immediate non-walkable"
            )
            nearNonWalkable -> ForwardCorridorAssessment(
                FrontStatus.BLOCKED,
                "corridor near non-walkable"
            )
            planNonWalkable -> ForwardCorridorAssessment(
                FrontStatus.CAUTION,
                "corridor plan non-walkable"
            )
            guideRange.nearVisible && semanticEvidence.near.visible -> ForwardCorridorAssessment(
                FrontStatus.CLEAR,
                "corridor clear"
            )
            anyVisibleZone -> ForwardCorridorAssessment(
                FrontStatus.CLEAR,
                "visible corridor clear"
            )
            !guideRange.nearVisible || !semanticEvidence.near.visible -> ForwardCorridorAssessment(
                FrontStatus.UNKNOWN,
                "near corridor not visible"
            )
            else -> ForwardCorridorAssessment(
                FrontStatus.CLEAR,
                "visible corridor clear"
            )
        }
    }

    private fun assessSideNearSemanticFan(frame: ArFrameData, left: Boolean): SideSpaceStatus {
        val evidence = AssistSemanticAnalyzer.analyzeSideFan(frame, left)
            ?: return SideSpaceStatus.UNKNOWN
        val immediateVisible = evidence.immediate.visible
        val nearVisible = evidence.near.visible
        if (!immediateVisible && !nearVisible) return SideSpaceStatus.UNKNOWN
        return if ((immediateVisible && evidence.immediate.blocked) ||
            (nearVisible && evidence.near.blocked)
        ) {
            SideSpaceStatus.BLOCKED
        } else {
            SideSpaceStatus.AVAILABLE
        }
    }

    private fun guideRangeVisibility(frame: ArFrameData, centerLateralMm: Float = 0f): GuideRangeVisibility {
        val immediate = distanceArcVisible(frame, centerLateralMm, AssistConfig.IMMEDIATE_ZONE_MM)
        val near = distanceArcVisible(frame, centerLateralMm, AssistConfig.NEAR_ZONE_MM)
        val far = distanceArcVisible(frame, centerLateralMm, AssistConfig.PLAN_DISTANCE_MM)
        return GuideRangeVisibility(
            immediateVisible = immediate,
            nearVisible = near,
            farVisible = far,
            reason = when {
                immediate && near && far -> "guide range visible"
                !immediate && !near && !far -> "guide range ${AssistConfig.IMMEDIATE_LABEL}, ${AssistConfig.NEAR_LABEL} and ${AssistConfig.PLAN_LABEL} not visible"
                !immediate && !near -> "guide range ${AssistConfig.IMMEDIATE_LABEL} and ${AssistConfig.NEAR_LABEL} not visible"
                !immediate && !far -> "guide range ${AssistConfig.IMMEDIATE_LABEL} and ${AssistConfig.PLAN_LABEL} not visible"
                !near && !far -> "guide range ${AssistConfig.NEAR_LABEL} and ${AssistConfig.PLAN_LABEL} not visible"
                !immediate -> "guide range ${AssistConfig.IMMEDIATE_LABEL} not visible"
                !near -> "guide range ${AssistConfig.NEAR_LABEL} not visible"
                else -> "guide range ${AssistConfig.PLAN_LABEL} not visible"
            }
        )
    }

    private fun distanceArcVisible(
        frame: ArFrameData,
        centerLateralMm: Float,
        distanceMm: Float
    ): Boolean {
        var total = 0
        var visible = 0
        var heading = -35f
        while (heading <= 35.001f) {
            total++
            val radians = Math.toRadians(heading.toDouble())
            val lateralMm = centerLateralMm + (kotlin.math.sin(radians) * distanceMm).toFloat()
            val forwardMm = (kotlin.math.cos(radians) * distanceMm).toFloat()
            val point = AssistGeometry.guidePointToView(frame, lateralMm, forwardMm, false)
            if (point != null && AssistGeometry.insideView(frame, point)) visible++
            heading += 10f
        }
        val centerVisible = AssistGeometry.guidePointToView(frame, centerLateralMm, distanceMm, false)
            ?.let { AssistGeometry.insideView(frame, it) } ?: false
        return centerVisible && total > 0 &&
            visible.toFloat() / total.toFloat() >= MIN_DISTANCE_ARC_VISIBLE_RATIO
    }

    private fun sideHintReason(front: FrontStatus, left: SideSpaceStatus, right: SideSpaceStatus): String? {
        if (front != FrontStatus.BLOCKED) return null
        return when {
            left == SideSpaceStatus.AVAILABLE && right != SideSpaceStatus.AVAILABLE -> "left has clearer space"
            right == SideSpaceStatus.AVAILABLE && left != SideSpaceStatus.AVAILABLE -> "right has clearer space"
            left == SideSpaceStatus.AVAILABLE && right == SideSpaceStatus.AVAILABLE -> "both sides have space"
            else -> null
        }
    }

    private fun sensorConfidence(frame: ArFrameData): SensorConfidence {
        val semanticKnown = semanticCoverage(frame)
        val pitch = AssistGeometry.projectedPitchDeg(frame)
        val pitchReliable = pitch in 4f..76f
        val confidence = (
            (if (frame.tracking) 0.25f else 0.0f) +
                (if (frame.semanticLabels != null) 0.45f + semanticKnown * 0.15f else 0.0f) +
                if (pitchReliable) 0.15f else 0f
            ).coerceIn(0f, 1f)
        val reason = when {
            !frame.tracking -> "tracking lost"
            pitch > 72f -> "camera too low"
            pitch < 6f -> "camera too high"
            frame.semanticLabels == null -> "semantics missing"
            semanticKnown < 0.42f -> "low semantic evidence"
            else -> null
        }
        return SensorConfidence(
            semanticCoverage = semanticKnown,
            confidence = confidence,
            unstableReason = reason
        )
    }

    private fun semanticCoverage(frame: ArFrameData): Float {
        val labels = frame.semanticLabels ?: return 0f
        if (labels.isEmpty()) return 0f
        val unknown = labels.count { (it.toInt() and 0xff) == 0 }
        return (1f - unknown.toFloat() / labels.size.toFloat()).coerceIn(0f, 1f)
    }

    private fun stateFor(command: AssistCommand, confidence: SensorConfidence): AssistState {
        return when (command) {
            AssistCommand.KEEP -> if (currentState in listOf(AssistState.CAUTION, AssistState.SIDE_SPACE_LEFT, AssistState.SIDE_SPACE_RIGHT, AssistState.CRITICAL_STOP)) AssistState.RECOVERY else AssistState.NORMAL
            AssistCommand.FRONT_CAUTION,
            AssistCommand.FRONT_LIMIT,
            AssistCommand.DEPTH_CAUTION -> if (confidence.unstableReason != null && confidence.confidence < 0.38f) AssistState.SYSTEM_UNSTABLE else AssistState.CAUTION
            AssistCommand.LEFT_SPACE -> AssistState.SIDE_SPACE_LEFT
            AssistCommand.RIGHT_SPACE -> AssistState.SIDE_SPACE_RIGHT
            AssistCommand.BOTH_SIDE_SPACE -> AssistState.SIDE_SPACE_BOTH
            AssistCommand.STOP -> AssistState.CRITICAL_STOP
            AssistCommand.CAMERA_ADJUST -> AssistState.CAMERA_ADJUST
            AssistCommand.SYSTEM_UNSTABLE -> AssistState.SYSTEM_UNSTABLE
        }
    }

    private fun shouldSpeak(state: AssistState, changed: Boolean, now: Long): Boolean {
        val cooldown = when (state) {
            AssistState.CRITICAL_STOP -> 900L
            AssistState.SYSTEM_UNSTABLE -> 5000L
            AssistState.CAMERA_ADJUST -> 4500L
            AssistState.SIDE_SPACE_LEFT, AssistState.SIDE_SPACE_RIGHT, AssistState.SIDE_SPACE_BOTH -> 3200L
            else -> 2600L
        }
        return (changed || state == AssistState.CRITICAL_STOP) && now - lastSpokenAt > cooldown
    }

    private fun shouldVibrate(state: AssistState, changed: Boolean, now: Long): Boolean {
        // CRITICAL_STOP 만 반복 허용, 나머지는 상태 변화 시 1회만.
        // "가까울수록 자주, 멀수록 한 번" 원칙.
        val cooldown = when (state) {
            AssistState.CRITICAL_STOP  -> 1000L   // 1초마다 반복 (너무 빠르면 패닉 유발)
            AssistState.CAUTION        -> 2000L   // 상태 변화 시 1회 (1.5m → 사용자 이미 인지 중)
            AssistState.CAMERA_ADJUST,
            AssistState.SYSTEM_UNSTABLE -> return false  // 진동 없음 (노이즈)
            else                       -> 99_999L  // SIDE_SPACE, DEPTH_CAUTION → 사실상 changed 시 1회만
        }
        return (changed || state == AssistState.CRITICAL_STOP) && now - lastVibratedAt > cooldown
    }

    private fun decisionReason(awareness: AwarenessSnapshot, confidence: SensorConfidence): String {
        return confidence.unstableReason ?: listOfNotNull(
            awareness.frontReason,
            awareness.sideHintReason,
            awareness.depthReason
        ).joinToString(" / ")
    }

    private fun commandSpeech(command: AssistCommand): String? {
        return when (command) {
            AssistCommand.STOP -> "정지"
            AssistCommand.FRONT_LIMIT -> "정면 제한"
            AssistCommand.FRONT_CAUTION -> "정면 주의"
            AssistCommand.DEPTH_CAUTION -> "거리 이상 감지"
            AssistCommand.LEFT_SPACE -> null
            AssistCommand.RIGHT_SPACE -> null
            AssistCommand.BOTH_SIDE_SPACE -> null
            AssistCommand.CAMERA_ADJUST -> "카메라를 조금 아래로"
            AssistCommand.SYSTEM_UNSTABLE -> "인식 불안정"
            AssistCommand.KEEP -> null
        }
    }

    private data class ForwardCorridorAssessment(
        val status: FrontStatus?,
        val reason: String?
    )

    private data class GuideRangeVisibility(
        val immediateVisible: Boolean,
        val nearVisible: Boolean,
        val farVisible: Boolean,
        val reason: String
    )

}
