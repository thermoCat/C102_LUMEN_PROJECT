package com.ssafy.smartcane.lumen2.assist

import android.graphics.PointF

enum class AssistState {
    NORMAL,
    CAUTION,
    CRITICAL_STOP,
    CAMERA_ADJUST,
    SYSTEM_UNSTABLE,
    RECOVERY
}

enum class AssistCommand {
    KEEP,
    FRONT_CAUTION,
    FRONT_LIMIT,
    DEPTH_CAUTION,
    STOP,
    CAMERA_ADJUST,
    SYSTEM_UNSTABLE
}

enum class FrontStatus {
    CLEAR,
    CAUTION,
    BLOCKED,
    CRITICAL,
    UNKNOWN
}

data class SensorConfidence(
    val semanticCoverage: Float,
    val confidence: Float,
    val unstableReason: String?
)

data class AwarenessSnapshot(
    val frontStatus: FrontStatus,
    val curbBoundary: CurbBoundaryStatus,
    val trafficScene: TrafficSceneStatus,
    val depthAnomaly: DepthAnomalyStatus,
    val frontReason: String,
    val depthReason: String?
)

data class AssistDistanceCurve(
    val label: String,
    val points: List<PointF>
)

enum class AssistObstacleKind {
    WALKABLE,
    ROAD,
    NON_WALKABLE,
    CURB_BOUNDARY,
    DEPTH_ANOMALY
}

enum class AssistTactileTileKind {
    WALKABLE,
    NON_WALKABLE,
    UNKNOWN
}

enum class CurbBoundaryStatus {
    CLEAR,
    DETECTED,
    UNKNOWN
}

enum class TrafficSceneStatus {
    CLEAR,
    CROSSWALK,
    GREEN_LIGHT,
    RED_LIGHT,
    UNKNOWN
}

enum class DepthAnomalyStatus {
    CLEAR,
    LEFT,
    CENTER,
    RIGHT,
    MULTIPLE,
    UNKNOWN
}

data class AssistObstaclePolygon(
    val displayPolygon: List<PointF>,
    val kind: AssistObstacleKind
)

data class AssistTactileTile(
    val displayPolygon: List<PointF>,
    val kind: AssistTactileTileKind
)

data class CurbBoundaryEvidence(
    val status: CurbBoundaryStatus,
    val polygons: List<AssistObstaclePolygon>
)

data class TrafficDetection(
    val label: TrafficDetectionLabel,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

enum class TrafficDetectionLabel {
    CROSSWALK,
    GREEN_LIGHT,
    PEDESTRIAN_TRAFFIC_LIGHT,
    RED_LIGHT
}

enum class IntersectionContext {
    NONE,             // 교차로 아님 (일반 횡단보도)
    T_JUNCTION,       // 삼거리
    INTERSECTION      // 사거리/교차로
}

data class TrafficSceneEvidence(
    val status: TrafficSceneStatus,
    val detections: List<TrafficDetection>,
    val intersectionContext: IntersectionContext = IntersectionContext.NONE
)

enum class SemanticDistanceZone {
    IMMEDIATE,
    NEAR,
    PLAN
}

data class SemanticComponent(
    val cellCount: Int,
    val spanPx: Float,
    val polygons: List<AssistObstaclePolygon>
)

data class SemanticZoneEvidence(
    val zone: SemanticDistanceZone,
    val totalSamples: Int,
    val walkablePolygons: List<AssistObstaclePolygon>,
    val components: List<SemanticComponent>
) {
    val visible: Boolean
        get() = totalSamples >= 6

    val blocked: Boolean
        get() = components.isNotEmpty()
}

data class SemanticCorridorEvidence(
    val immediate: SemanticZoneEvidence,
    val near: SemanticZoneEvidence,
    val plan: SemanticZoneEvidence
) {
    val totalSamples: Int
        get() = immediate.totalSamples + near.totalSamples + plan.totalSamples

    fun obstaclePolygons(): List<AssistObstaclePolygon> {
        return listOf(immediate, near, plan).flatMap { zone ->
            zone.walkablePolygons + zone.components.flatMap { component -> component.polygons }
        }
    }
}

data class AssistVisualization(
    val anchorBarPolygon: List<PointF>,
    val distanceCurves: List<AssistDistanceCurve>,
    val depthSectionLines: List<List<PointF>>,
    val selectedSafetyPolygon: List<PointF>,
    val selectedBodyPolygon: List<PointF>,
    val selectedCenterLine: List<PointF>,
    val obstaclePolygons: List<AssistObstaclePolygon>,
    val tactileTiles: List<AssistTactileTile>,
    val trafficDetections: List<TrafficDetection>
)

data class AssistDecision(
    val state: AssistState,
    val command: AssistCommand,
    val shouldSpeak: Boolean,
    val speech: String?,
    val shouldVibrate: Boolean,
    val confidence: SensorConfidence,
    val awareness: AwarenessSnapshot,
    val reason: String,
    val visualization: AssistVisualization?
)
