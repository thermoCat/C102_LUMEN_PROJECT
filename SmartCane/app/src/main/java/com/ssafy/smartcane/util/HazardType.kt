package com.ssafy.smartcane.util

object HazardType {
    const val POLE = "POLE"
    const val BOLLARD = "BOLLARD"
    const val BARRICADE = "BARRICADE"
    const val MOVABLE_SIGNAGE = "MOVABLE_SIGNAGE"
    const val SIDEWALK_DAMAGED = "SIDEWALK_DAMAGED"
    const val BRAILLE_GUIDE_BLOCKS_DAMAGED = "BRAILLE_GUIDE_BLOCKS_DAMAGED"
    const val ALLEY_DAMAGED = "ALLEY_DAMAGED"
    const val CAUTION_ZONE_STAIRS = "CAUTION_ZONE_STAIRS"
    const val CAUTION_ZONE_MANHOLE = "CAUTION_ZONE_MANHOLE"
    const val CAUTION_ZONE_GRATING = "CAUTION_ZONE_GRATING"
    const val CAUTION_ZONE_REPAIR_ZONE = "CAUTION_ZONE_REPAIR_ZONE"

    val LABEL_KO = mapOf(
        POLE to "기둥",
        BOLLARD to "볼라드",
        BARRICADE to "바리케이드",
        MOVABLE_SIGNAGE to "이동식 표지판",
        SIDEWALK_DAMAGED to "손상된 보도",
        BRAILLE_GUIDE_BLOCKS_DAMAGED to "손상된 점자블록",
        ALLEY_DAMAGED to "손상된 골목길",
        CAUTION_ZONE_STAIRS to "계단 주의구역",
        CAUTION_ZONE_MANHOLE to "맨홀 주의구역",
        CAUTION_ZONE_GRATING to "배수구 주의구역",
        CAUTION_ZONE_REPAIR_ZONE to "공사구역 주의구역"
    )

    /**
     * TFLite 모델이 내놓는 raw 라벨을 백엔드 enum 문자열로 변환.
     * 예: "caution_zone:stairs" → "CAUTION_ZONE_STAIRS"
     * 매핑 불가 라벨은 null.
     */
    fun fromTfliteLabel(label: String): String? {
        val normalized = label.trim().uppercase().replace(":", "_")
        return if (LABEL_KO.containsKey(normalized)) normalized else null
    }

    /** 타입 + 신뢰도 기반 한글 설명 생성. 예: "기둥 감지 (신뢰도 92%)" */
    fun makeDescription(type: String, confidence: Float): String {
        val label = LABEL_KO[type] ?: type
        val pct = (confidence * 100).toInt()
        return "$label 감지 (신뢰도 ${pct}%)"
    }
}
