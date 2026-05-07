package com.ssafy.smartcane.lumen2.assist

internal class AssistCurbBoundaryStabilizer {
    private companion object {
        private const val HOLD_MILLIS = 650L
        private const val CLEAR_CONFIRM_MILLIS = 350L
    }

    private var lastDetected: CurbBoundaryEvidence? = null
    private var lastDetectedAt = 0L
    private var clearSince = 0L

    fun stabilize(evidence: CurbBoundaryEvidence, nowMillis: Long): CurbBoundaryEvidence {
        if (evidence.status == CurbBoundaryStatus.DETECTED && evidence.polygons.isNotEmpty()) {
            lastDetected = evidence
            lastDetectedAt = nowMillis
            clearSince = 0L
            return evidence
        }

        val held = lastDetected
        if (held != null && nowMillis - lastDetectedAt <= HOLD_MILLIS) {
            if (clearSince == 0L) clearSince = nowMillis
            if (nowMillis - clearSince < CLEAR_CONFIRM_MILLIS) return held
        }

        lastDetected = null
        clearSince = 0L
        return evidence
    }
}
