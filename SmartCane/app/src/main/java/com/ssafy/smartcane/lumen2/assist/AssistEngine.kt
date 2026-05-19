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
        
        val frontEvidence = semanticStabilizer.stabilize(
            evidence = AssistSemanticAnalyzer.analyzeCorridor(frame, 0f),
            nowMillis = nowMillis
        )
        val curbBoundary = curbBoundaryStabilizer.stabilize(
            evidence = AssistCurbBoundaryAnalyzer.analyze(frame),
            nowMillis = nowMillis
        )
        val stableTrafficEvidence = trafficStabilizer.stabilize(trafficEvidence, nowMillis)
        
        val awareness = buildAwareness(confidence, frame, frontEvidence, curbBoundary, stableTrafficEvidence)
        
        val rawCommand = commandFromAwareness(awareness, confidence)
        val command = stabilize(rawCommand, nowMillis)
        val state = stateFor(command, confidence)
        val changed = state != currentState || command != currentCommand

        if (changed) {
            currentState = state
            currentCommand = command
            stateEnteredAt = nowMillis
        }
        
        val visualization = AssistVisualizationBuilder.build(
            frame = frame,
            semanticEvidence = frontEvidence,
            curbBoundary = curbBoundary,
            trafficEvidence = stableTrafficEvidence
        )

        // 음성 안내: 연석 > 카메라 각도 (횡단보도/신호는 CrosswalkPipeline에서 단일 처리)
        val curbSpeech = if (curbBoundary.status == CurbBoundaryStatus.DETECTED && nowMillis - lastSpokenAt > 8000L) {
            "연석이 있습니다. 주의하세요."
        } else null

        val commandSpeechText = commandSpeech(command)

        val speech = curbSpeech ?: if (command == AssistCommand.CAMERA_ADJUST) commandSpeechText else null

        val shouldSpeak = speech != null && (
            curbSpeech != null || (command == AssistCommand.CAMERA_ADJUST && shouldSpeak(state, changed, nowMillis))
        )
        if (shouldSpeak) lastSpokenAt = nowMillis

        // 장애물 관련(정지, 옆공간 등)은 진동으로만 알림
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
        frontEvidence: SemanticCorridorEvidence?,
        curbBoundary: CurbBoundaryEvidence,
        trafficEvidence: TrafficSceneEvidence
    ): AwarenessSnapshot {
        if (confidence.confidence < 0.30f) {
            return AwarenessSnapshot(
                frontStatus = FrontStatus.UNKNOWN,
                curbBoundary = CurbBoundaryStatus.UNKNOWN,
                trafficScene = TrafficSceneStatus.UNKNOWN,
                depthAnomaly = DepthAnomalyStatus.UNKNOWN,
                frontReason = confidence.unstableReason ?: "low confidence",
                depthReason = null
            )
        }

        val guideRange = guideRangeVisibility(frame)
        if (!guideRange.farVisible) {
            return AwarenessSnapshot(
                frontStatus = FrontStatus.UNKNOWN,
                curbBoundary = curbBoundary.status,
                trafficScene = trafficEvidence.status,
                depthAnomaly = DepthAnomalyStatus.UNKNOWN,
                frontReason = guideRange.reason,
                depthReason = null
            )
        }

        val corridor = assessSemanticCorridor(frontEvidence, guideRange)
        val frontStatus = corridor.status ?: FrontStatus.UNKNOWN

        return AwarenessSnapshot(
            frontStatus = frontStatus,
            curbBoundary = curbBoundary.status,
            trafficScene = trafficEvidence.status,
            depthAnomaly = DepthAnomalyStatus.CLEAR,
            frontReason = corridor.reason ?: "semantic corridor unknown",
            depthReason = null
        )
    }

    private fun commandFromAwareness(awareness: AwarenessSnapshot, confidence: SensorConfidence): AssistCommand {
        if (awareness.frontReason.startsWith("guide range")) return AssistCommand.CAMERA_ADJUST
        if (confidence.confidence < 0.30f || awareness.frontStatus == FrontStatus.UNKNOWN) return AssistCommand.SYSTEM_UNSTABLE
        if (awareness.frontStatus == FrontStatus.CRITICAL) return AssistCommand.STOP
        // 횡단보도 감지 시 전방을 walkable로 처리
        val crosswalkDetected = awareness.trafficScene == TrafficSceneStatus.CROSSWALK ||
                                awareness.trafficScene == TrafficSceneStatus.GREEN_LIGHT ||
                                awareness.trafficScene == TrafficSceneStatus.RED_LIGHT
        if (crosswalkDetected) return AssistCommand.KEEP
        if (awareness.frontStatus == FrontStatus.BLOCKED) return AssistCommand.FRONT_LIMIT
        if (awareness.frontStatus == FrontStatus.CAUTION) return AssistCommand.FRONT_CAUTION
        return AssistCommand.KEEP
    }

    private fun stabilize(raw: AssistCommand, now: Long): AssistCommand {
        if (raw == AssistCommand.STOP || raw == AssistCommand.SYSTEM_UNSTABLE) {
            candidateCommand = null
            return raw
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

        val corridorVisible = guideRange.farVisible && semanticEvidence.immediate.visible

        return when {
            corridorVisible && semanticEvidence.immediate.blocked -> ForwardCorridorAssessment(
                FrontStatus.BLOCKED,
                "2m corridor non-walkable"
            )
            !corridorVisible -> ForwardCorridorAssessment(
                FrontStatus.UNKNOWN,
                "2m corridor not visible"
            )
            else -> ForwardCorridorAssessment(
                FrontStatus.CLEAR,
                "2m corridor clear"
            )
        }
    }

    private fun guideRangeVisibility(frame: ArFrameData, centerLateralMm: Float = 0f): GuideRangeVisibility {
        val visible = distanceArcVisible(frame, centerLateralMm, AssistConfig.PLAN_DISTANCE_MM)
        return GuideRangeVisibility(
            immediateVisible = visible,
            nearVisible = visible,
            farVisible = visible,
            reason = if (visible) "guide range visible" else "guide range ${AssistConfig.PLAN_LABEL} not visible"
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
            AssistCommand.KEEP -> if (currentState in listOf(AssistState.CAUTION, AssistState.CRITICAL_STOP)) AssistState.RECOVERY else AssistState.NORMAL
            AssistCommand.FRONT_CAUTION,
            AssistCommand.FRONT_LIMIT,
            AssistCommand.DEPTH_CAUTION -> if (confidence.unstableReason != null && confidence.confidence < 0.38f) AssistState.SYSTEM_UNSTABLE else AssistState.CAUTION
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
            else -> 2600L
        }
        return (changed || state == AssistState.CRITICAL_STOP) && now - lastSpokenAt > cooldown
    }

    private fun shouldVibrate(state: AssistState, changed: Boolean, now: Long): Boolean {
        return when (state) {
            AssistState.CRITICAL_STOP,
            AssistState.CAUTION -> now - lastVibratedAt >= 2000L
            else -> false
        }
    }

    private fun decisionReason(awareness: AwarenessSnapshot, confidence: SensorConfidence): String {
        return confidence.unstableReason ?: listOfNotNull(
            awareness.frontReason,
            awareness.depthReason
        ).joinToString(" / ")
    }

    private fun commandSpeech(command: AssistCommand): String? {
        return when (command) {
            AssistCommand.STOP -> "정지"
            AssistCommand.FRONT_LIMIT -> "전방 제한"
            AssistCommand.FRONT_CAUTION -> "정면 주의"
            AssistCommand.DEPTH_CAUTION -> "거리 이상 감지"
            AssistCommand.CAMERA_ADJUST -> "카메라를 조금 아래로 내려주세요."
            AssistCommand.SYSTEM_UNSTABLE -> "인식이 불안정합니다."
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
