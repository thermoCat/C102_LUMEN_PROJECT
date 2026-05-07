package com.ssafy.smartcane.lumen2.assist

internal class AssistSemanticStabilizer {
    private companion object {
        private const val HOLD_MILLIS = 550L
        private const val CHANGE_CONFIRM_MILLIS = 220L
    }

    private var lastEvidence: SemanticCorridorEvidence? = null
    private var lastUpdatedAt = 0L
    private var lastSignature: SemanticSignature? = null
    private var pendingSignature: SemanticSignature? = null
    private var pendingSince = 0L

    fun stabilize(evidence: SemanticCorridorEvidence?, nowMillis: Long): SemanticCorridorEvidence? {
        if (evidence == null || evidence.totalSamples <= 0) {
            pendingSignature = null
            val held = lastEvidence ?: return null
            return if (nowMillis - lastUpdatedAt <= HOLD_MILLIS) held else null
        }

        val signature = evidence.signature()
        val currentSignature = lastSignature
        if (lastEvidence == null || currentSignature == null || signature == currentSignature) {
            accept(evidence, signature, nowMillis)
            return evidence
        }

        if (pendingSignature != signature) {
            pendingSignature = signature
            pendingSince = nowMillis
            return lastEvidence
        }

        if (nowMillis - pendingSince >= CHANGE_CONFIRM_MILLIS) {
            lastEvidence = evidence
            lastSignature = signature
            lastUpdatedAt = nowMillis
            pendingSignature = null
            return evidence
        }
        return lastEvidence
    }

    private fun accept(
        evidence: SemanticCorridorEvidence,
        signature: SemanticSignature,
        nowMillis: Long
    ) {
        lastEvidence = evidence
        lastSignature = signature
        lastUpdatedAt = nowMillis
        pendingSignature = null
    }

    private fun SemanticCorridorEvidence.signature(): SemanticSignature {
        return SemanticSignature(
            immediate = immediate.zoneSignature(),
            near = near.zoneSignature(),
            plan = plan.zoneSignature()
        )
    }

    private fun SemanticZoneEvidence.zoneSignature(): ZoneSignature {
        return ZoneSignature(
            visible = visible,
            blocked = blocked,
            componentKinds = components.flatMap { component ->
                component.polygons.map { it.kind }
            }.toSet()
        )
    }

    private data class SemanticSignature(
        val immediate: ZoneSignature,
        val near: ZoneSignature,
        val plan: ZoneSignature
    )

    private data class ZoneSignature(
        val visible: Boolean,
        val blocked: Boolean,
        val componentKinds: Set<AssistObstacleKind>
    )
}
