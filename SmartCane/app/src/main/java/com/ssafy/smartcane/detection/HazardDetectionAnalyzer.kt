package com.ssafy.smartcane.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ssafy.smartcane.network.HazardApiService
import com.ssafy.smartcane.util.HazardType
import com.ssafy.smartcane.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * TFLite 추론 결과를 받아 위험구간 신고 API로 전송.
 * - confidence가 [THRESHOLD] 이상일 때만 신고.
 * - 같은 타입 연속 트리거 시 [COOLDOWN_MS] 동안 재신고 차단.
 * - TFLite 라벨은 [HazardType.fromTfliteLabel]로 백엔드 enum 문자열로 변환.
 *
 * detect 람다는 외부에서 주입 (예: TFLiteRunner).
 */
class HazardDetectionAnalyzer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val detect: (Bitmap) -> DetectionResult?
) {

    data class DetectionResult(val tfliteLabel: String, val confidence: Float)

    companion object {
        private const val TAG = "HazardDetect"
        const val THRESHOLD = 0.10f
        const val COOLDOWN_MS = 5_000L
    }

    private val lastReportedAt = mutableMapOf<String, Long>()

    /** 카메라 프레임 비트맵 1장 처리. */
    fun analyzeBitmap(bitmap: Bitmap) {
        val result = detect(bitmap)
        if (result == null) {
            com.ssafy.smartcane.util.AppLogger.log(TAG, "감지 없음 (threshold 미달 or 모델 null)")
            return
        }
        com.ssafy.smartcane.util.AppLogger.log(TAG, "감지: ${result.tfliteLabel} ${(result.confidence*100).toInt()}%")

        val backendType = HazardType.fromTfliteLabel(result.tfliteLabel)
        if (backendType == null) {
            com.ssafy.smartcane.util.AppLogger.log(TAG, "HazardType 미매핑: ${result.tfliteLabel} → 신고 제외")
            return
        }
        if (result.confidence < THRESHOLD) {
            com.ssafy.smartcane.util.AppLogger.log(TAG, "confidence 부족: ${result.confidence} < $THRESHOLD")
            return
        }
        if (!canReport(backendType)) {
            com.ssafy.smartcane.util.AppLogger.log(TAG, "쿨다운 중: $backendType")
            return
        }

        val loc = LocationHelper.getLastKnownLocation(context)
        if (loc == null) {
            com.ssafy.smartcane.util.AppLogger.log(TAG, "위치 없음 → 신고 불가")
            return
        }
        lastReportedAt[backendType] = System.currentTimeMillis()
        com.ssafy.smartcane.util.AppLogger.log(TAG, "신고 시작: $backendType lat=${loc.first} lng=${loc.second}")

        scope.launch {
            val res = HazardApiService.reportWithImage(
                lat = loc.first,
                lng = loc.second,
                tfliteLabel = result.tfliteLabel,
                confidence = result.confidence,
                bitmap = bitmap
            )
            if (res.success) {
                com.ssafy.smartcane.util.AppLogger.log(TAG, "✅ 신고 성공: ${res.message}")
            } else {
                com.ssafy.smartcane.util.AppLogger.error(TAG, "신고 실패: ${res.message}")
            }
            Log.e(TAG, "신고 실패: ${res.message}")
        }
    }

    private fun canReport(type: String): Boolean {
        val last = lastReportedAt[type] ?: return true
        return System.currentTimeMillis() - last > COOLDOWN_MS
    }
}
