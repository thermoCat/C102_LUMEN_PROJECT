package com.ssafy.smartcane.lumen2.assist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

class AssistOverlayRenderer {
    private var reusableBitmap: Bitmap? = null
    private val semanticObstaclePath = Path()
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(188, 7, 16, 24)
    }
    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = 2f
    }
    private val pathFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pathStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
    }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 210, 238, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(245, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val obstacleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 255, 45, 45)
        style = Paint.Style.FILL
    }
    private val walkableFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 35, 220, 95)
        style = Paint.Style.FILL
    }
    private val roadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 255, 210, 40)
        style = Paint.Style.FILL
    }
    private val depthAnomalyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 80, 210, 255)
        style = Paint.Style.FILL
    }
    private val curbBoundaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 255, 140, 35)
        style = Paint.Style.FILL
    }
    private val depthSectionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 80, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 56f
        isFakeBoldText = true
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 244, 247, 250)
        textSize = 31f
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        isFakeBoldText = true
    }
    private val arcLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 230, 244, 255)
        textSize = 22f
        isFakeBoldText = true
    }
    private val trafficBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val ocrCropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(210, 255, 255, 255)
    }
    private val trafficLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 8, 15, 22)
    }
    private val trafficLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f
        isFakeBoldText = true
    }

    fun render(width: Int, height: Int, decision: AssistDecision): Bitmap {
        val bitmap = reusableBitmap
            ?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        drawGuidePathVisualization(canvas, decision)
        val panelBottom = drawGuidancePanel(canvas, width, decision)
        drawSituationAlerts(canvas, width, panelBottom, decision)
        return bitmap
    }

    private fun drawGuidePathVisualization(canvas: Canvas, decision: AssistDecision) {
        val visualization = decision.visualization ?: return
        val accent = colorForState(decision.state)
        pathFillPaint.color = withAlpha(accent, 52)
        pathStrokePaint.color = withAlpha(accent, 180)
        centerLinePaint.color = accent

        drawPolygon(canvas, visualization.selectedSafetyPolygon, pathFillPaint)
        drawPolygon(canvas, visualization.selectedSafetyPolygon, pathStrokePaint)
        drawPolygon(canvas, visualization.selectedBodyPolygon, pathStrokePaint)
        drawPolyline(canvas, visualization.selectedCenterLine, centerLinePaint)

        drawDepthSectionLines(canvas, visualization)
        drawObstacleMarks(canvas, visualization)
        visualization.distanceCurves.forEach { curve ->
            drawPolyline(canvas, curve.points, arcPaint)
            drawCurveLabel(canvas, curve)
        }
        drawPolygon(canvas, visualization.anchorBarPolygon, pathFillPaint)
        if (visualization.anchorBarPolygon.size >= 4) {
            drawPolyline(
                canvas,
                listOf(
                    visualization.anchorBarPolygon[0],
                    visualization.anchorBarPolygon[1],
                    visualization.anchorBarPolygon[2],
                    visualization.anchorBarPolygon[3],
                    visualization.anchorBarPolygon[0]
                ),
                anchorPaint
            )
        }
        drawTrafficDetections(canvas, visualization)
    }

    private fun drawGuidancePanel(canvas: Canvas, width: Int, decision: AssistDecision): Float {
        val left = 24f
        val top = 24f
        val right = minOf(width * 0.9f, left + 900f)
        val panelWidth = right - left
        val panelBottom = top + 280f
        val accentColor = colorForState(decision.state)
        panelBorderPaint.color = accentColor
        canvas.drawRoundRect(left, top, right, panelBottom, 30f, 30f, panelPaint)
        canvas.drawRoundRect(left, top, right, panelBottom, 30f, 30f, panelBorderPaint)

        val badgeText = stateLabel(decision.state)
        val badgeLeft = left + 24f
        val badgeTop = top + 22f
        val badgeWidth = badgeTextPaint.measureText(badgeText) + 36f
        val badgeBottom = badgeTop + 42f
        badgePaint.color = accentColor
        canvas.drawRoundRect(
            RectF(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeBottom),
            18f,
            18f,
            badgePaint
        )
        canvas.drawText(badgeText, badgeLeft + 18f, badgeBottom - 11f, badgeTextPaint)

        var contentY = badgeBottom + 34f
        titlePaint.color = accentColor
        contentY = drawWrappedText(canvas, primaryGuidance(decision), left + 24f, contentY, panelWidth - 48f, titlePaint, 14f, 2)
        contentY += 8f
        canvas.drawLine(left + 24f, contentY, right - 24f, contentY, dividerPaint)
        contentY += 28f
        val secondary = supportSummary(decision)
        drawWrappedText(canvas, secondary, left + 24f, contentY, panelWidth - 48f, bodyPaint, 10f, 3)
        return panelBottom
    }

    private fun supportSummary(decision: AssistDecision): String {
        return listOfNotNull(
            evidenceLine(decision),
            sideEvidenceLine(decision),
            sensorLine(decision)
        ).joinToString("\n")
    }

    private fun drawSituationAlerts(canvas: Canvas, width: Int, panelBottom: Float, decision: AssistDecision) {
        val alerts = listOfNotNull(
            curbAlert(decision),
            trafficAlert(decision)
        )
        if (alerts.isEmpty()) return
        val left = 24f
        val right = minOf(width * 0.9f, left + 900f)
        var top = panelBottom + 12f
        alerts.forEach { alert ->
            val textWidth = bodyPaint.measureText(alert.text)
            val boxWidth = (textWidth + 42f).coerceAtLeast(260f).coerceAtMost(right - left)
            val bottom = top + 58f
            badgePaint.color = alert.color
            canvas.drawRoundRect(left, top, left + boxWidth, bottom, 18f, 18f, trafficLabelBgPaint)
            canvas.drawRoundRect(left, top, left + boxWidth, bottom, 18f, 18f, badgePaint)
            bodyPaint.color = Color.WHITE
            canvas.drawText(alert.text, left + 18f, bottom - 18f, bodyPaint)
            top = bottom + 10f
        }
        bodyPaint.color = Color.argb(235, 244, 247, 250)
    }

    private fun curbAlert(decision: AssistDecision): SituationAlert? {
        return if (decision.awareness.curbBoundary == CurbBoundaryStatus.DETECTED) {
            SituationAlert("인도-차도 경계 | 단차 주의", Color.rgb(255, 150, 50))
        } else {
            null
        }
    }

    private fun trafficAlert(decision: AssistDecision): SituationAlert? {
        val detections = decision.visualization?.trafficDetections.orEmpty()
        detections.bestTrafficDetection(TrafficDetectionLabel.RED_LIGHT)?.let { detection ->
            return SituationAlert("정지 신호 감지${trafficSeconds(detection)}", Color.rgb(255, 90, 90))
        }
        detections.bestTrafficDetection(TrafficDetectionLabel.GREEN_LIGHT)?.let { detection ->
            return SituationAlert("초록 신호 감지${trafficSeconds(detection)}", Color.rgb(70, 230, 120))
        }
        detections.bestTrafficDetection(TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT)?.let { _ ->
            return SituationAlert("보행자 신호등 감지", Color.rgb(255, 220, 90))
        }
        val hasCrosswalkDetection = detections.any { it.label == TrafficDetectionLabel.CROSSWALK }
        return if (hasCrosswalkDetection || decision.awareness.trafficScene == TrafficSceneStatus.CROSSWALK) {
            SituationAlert("횡단보도 - 신호 확인", Color.rgb(90, 210, 255))
        } else {
            null
        }
    }

    private fun List<TrafficDetection>.bestTrafficDetection(label: TrafficDetectionLabel): TrafficDetection? {
        return filter { it.label == label }.maxByOrNull { it.confidence }
    }

    private fun trafficSeconds(detection: TrafficDetection): String {
        return detection.remainingSeconds?.let { " | ${it}초" }.orEmpty()
    }

    private fun primaryGuidance(decision: AssistDecision): String {
        return when (decision.command) {
            AssistCommand.KEEP -> "정면 관찰"
            AssistCommand.STOP -> "정지"
            AssistCommand.FRONT_CAUTION -> "정면 주의"
            AssistCommand.FRONT_LIMIT -> "정면 제한"
            AssistCommand.DEPTH_CAUTION -> "거리 이상 감지"
            AssistCommand.LEFT_SPACE -> "정면 제한 - 왼쪽 여유"
            AssistCommand.RIGHT_SPACE -> "정면 제한 - 오른쪽 여유"
            AssistCommand.BOTH_SIDE_SPACE -> "정면 제한 - 양쪽 여유"
            AssistCommand.CAMERA_ADJUST -> "카메라 각도 조절"
            AssistCommand.SYSTEM_UNSTABLE -> "인식 불안정"
        }
    }

    private fun evidenceLine(decision: AssistDecision): String {
        return when (decision.command) {
            AssistCommand.STOP -> "${AssistConfig.IMMEDIATE_LABEL} 이내 정면 non-walkable"
            AssistCommand.FRONT_LIMIT,
            AssistCommand.LEFT_SPACE,
            AssistCommand.RIGHT_SPACE,
            AssistCommand.BOTH_SIDE_SPACE -> "${AssistConfig.IMMEDIATE_LABEL}~${AssistConfig.NEAR_LABEL} 정면 non-walkable"
            AssistCommand.FRONT_CAUTION -> "${AssistConfig.NEAR_LABEL}~${AssistConfig.PLAN_LABEL} 정면 non-walkable"
            AssistCommand.DEPTH_CAUTION -> "화면 3분할 depth anomaly ${depthLabel(decision.awareness.depthAnomaly)}"
            AssistCommand.CAMERA_ADJUST -> cameraAdjustEvidence(decision.awareness.frontReason)
            AssistCommand.SYSTEM_UNSTABLE -> decision.confidence.unstableReason ?: "센서 신뢰 낮음"
            AssistCommand.KEEP -> "${AssistConfig.PLAN_LABEL}까지 정면 관찰 중"
        }
    }

    private fun sideEvidenceLine(decision: AssistDecision): String? {
        return when (decision.command) {
            AssistCommand.LEFT_SPACE -> "좌측 ${AssistConfig.NEAR_LABEL} 여유 / 우측 ${sideShort(decision.awareness.rightSpace)}"
            AssistCommand.RIGHT_SPACE -> "우측 ${AssistConfig.NEAR_LABEL} 여유 / 좌측 ${sideShort(decision.awareness.leftSpace)}"
            AssistCommand.BOTH_SIDE_SPACE -> "좌우 ${AssistConfig.NEAR_LABEL} 여유"
            AssistCommand.FRONT_LIMIT -> "좌측 ${sideShort(decision.awareness.leftSpace)} / 우측 ${sideShort(decision.awareness.rightSpace)}"
            else -> null
        }
    }

    private fun cameraAdjustEvidence(reason: String): String {
        return when (reason) {
            "guide range ${AssistConfig.IMMEDIATE_LABEL}, ${AssistConfig.NEAR_LABEL} and ${AssistConfig.PLAN_LABEL} not visible" -> "${AssistConfig.IMMEDIATE_LABEL}선, ${AssistConfig.NEAR_LABEL}선, ${AssistConfig.PLAN_LABEL}선이 화면 밖입니다"
            "guide range ${AssistConfig.IMMEDIATE_LABEL} and ${AssistConfig.NEAR_LABEL} not visible" -> "${AssistConfig.IMMEDIATE_LABEL}선과 ${AssistConfig.NEAR_LABEL}선이 화면 밖입니다"
            "guide range ${AssistConfig.IMMEDIATE_LABEL} and ${AssistConfig.PLAN_LABEL} not visible" -> "${AssistConfig.IMMEDIATE_LABEL}선과 ${AssistConfig.PLAN_LABEL}선이 화면 밖입니다"
            "guide range ${AssistConfig.NEAR_LABEL} and ${AssistConfig.PLAN_LABEL} not visible" -> "${AssistConfig.NEAR_LABEL}선과 ${AssistConfig.PLAN_LABEL}선이 화면 밖입니다"
            "guide range ${AssistConfig.IMMEDIATE_LABEL} not visible" -> "${AssistConfig.IMMEDIATE_LABEL}선이 화면 밖입니다"
            "guide range ${AssistConfig.NEAR_LABEL} not visible" -> "${AssistConfig.NEAR_LABEL}선이 화면 밖입니다"
            "guide range ${AssistConfig.PLAN_LABEL} not visible" -> "${AssistConfig.PLAN_LABEL}선이 화면 밖입니다"
            "near corridor not visible" -> "${AssistConfig.IMMEDIATE_LABEL}~${AssistConfig.NEAR_LABEL} 정면 영역이 화면 밖입니다"
            else -> "기준선이 화면 밖입니다"
        }
    }

    private fun sensorLine(decision: AssistDecision): String {
        return "근거 ${decision.reason}  신뢰 ${decision.confidence.confidence.fmt()}  " +
            "SEM ${decision.confidence.semanticCoverage.fmt()}"
    }

    private fun depthLabel(status: DepthAnomalyStatus): String {
        return when (status) {
            DepthAnomalyStatus.LEFT -> "L"
            DepthAnomalyStatus.CENTER -> "C"
            DepthAnomalyStatus.RIGHT -> "R"
            DepthAnomalyStatus.MULTIPLE -> "multi"
            DepthAnomalyStatus.CLEAR,
            DepthAnomalyStatus.UNKNOWN -> "-"
        }
    }

    private fun sideShort(status: SideSpaceStatus): String {
        return when (status) {
            SideSpaceStatus.AVAILABLE -> "여유"
            SideSpaceStatus.BLOCKED -> "제한"
            SideSpaceStatus.UNKNOWN -> "불확실"
        }
    }

    private fun drawObstacleMarks(canvas: Canvas, visualization: AssistVisualization) {
        val corridorClip = screenPath(visualization.selectedSafetyPolygon) ?: return
        val roadPath = Path()
        val depthPath = Path()
        val curbPath = Path()
        val walkablePath = Path()
        semanticObstaclePath.reset()
        var hasWalkable = false
        var hasRoad = false
        var hasDepth = false
        var hasCurb = false
        var hasNonWalkable = false
        visualization.obstaclePolygons.forEach { obstacle ->
            when (obstacle.kind) {
                AssistObstacleKind.WALKABLE -> {
                    appendPolygon(walkablePath, obstacle.displayPolygon)
                    hasWalkable = true
                }
                AssistObstacleKind.ROAD -> {
                    appendPolygon(roadPath, obstacle.displayPolygon)
                    hasRoad = true
                }
                AssistObstacleKind.NON_WALKABLE -> {
                    appendPolygon(semanticObstaclePath, obstacle.displayPolygon)
                    hasNonWalkable = true
                }
                AssistObstacleKind.CURB_BOUNDARY -> {
                    appendPolygon(curbPath, obstacle.displayPolygon)
                    hasCurb = true
                }
                AssistObstacleKind.DEPTH_ANOMALY -> {
                    appendPolygon(depthPath, obstacle.displayPolygon)
                    hasDepth = true
                }
            }
        }
        if (hasDepth) canvas.drawPath(depthPath, depthAnomalyFillPaint)
        if (hasWalkable || hasRoad || hasCurb || hasNonWalkable) {
            canvas.save()
            canvas.clipPath(corridorClip)
            if (hasWalkable) canvas.drawPath(walkablePath, walkableFillPaint)
            if (hasRoad) canvas.drawPath(roadPath, roadFillPaint)
            if (hasCurb) canvas.drawPath(curbPath, curbBoundaryFillPaint)
            if (hasNonWalkable) canvas.drawPath(semanticObstaclePath, obstacleFillPaint)
            canvas.restore()
        }
    }

    private fun drawDepthSectionLines(canvas: Canvas, visualization: AssistVisualization) {
        visualization.depthSectionLines.forEach { line ->
            if (line.size >= 2) drawPolyline(canvas, line, depthSectionLinePaint)
        }
    }

    private fun drawTrafficDetections(canvas: Canvas, visualization: AssistVisualization) {
        visualization.trafficDetections.forEach { detection ->
            drawOcrCrop(canvas, detection)
            trafficBoxPaint.color = trafficColor(detection.label)
            val rect = RectF(detection.left, detection.top, detection.right, detection.bottom)
            canvas.drawRoundRect(rect, 8f, 8f, trafficBoxPaint)
            val label = trafficLabel(detection)
            val labelWidth = trafficLabelPaint.measureText(label) + 18f
            val labelLeft = rect.left
            val labelTop = (rect.bottom + 5f).coerceAtMost(canvas.height - trafficLabelPaint.textSize - 8f)
            val labelBottom = labelTop + trafficLabelPaint.textSize + 12f
            canvas.drawRoundRect(
                labelLeft,
                labelTop,
                (labelLeft + labelWidth).coerceAtMost(canvas.width.toFloat() - 4f),
                labelBottom,
                8f,
                8f,
                trafficLabelBgPaint
            )
            canvas.drawText(label, labelLeft + 9f, labelBottom - 9f, trafficLabelPaint)
        }
    }

    private fun drawOcrCrop(canvas: Canvas, detection: TrafficDetection) {
        val left = detection.ocrLeft ?: return
        val top = detection.ocrTop ?: return
        val right = detection.ocrRight ?: return
        val bottom = detection.ocrBottom ?: return
        if (right <= left || bottom <= top) return
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 10f, 10f, ocrCropPaint)
        val label = "OCR"
        val labelWidth = trafficLabelPaint.measureText(label) + 14f
        val labelBottom = (rect.top - 5f).coerceAtLeast(trafficLabelPaint.textSize + 8f)
        val labelTop = labelBottom - trafficLabelPaint.textSize - 10f
        canvas.drawRoundRect(rect.left, labelTop, rect.left + labelWidth, labelBottom, 7f, 7f, trafficLabelBgPaint)
        canvas.drawText(label, rect.left + 7f, labelBottom - 8f, trafficLabelPaint)
    }

    private fun trafficLabel(detection: TrafficDetection): String {
        val base = when (detection.label) {
            TrafficDetectionLabel.CROSSWALK -> "횡단보도"
            TrafficDetectionLabel.GREEN_LIGHT -> "초록신호"
            TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT -> "보행신호등"
            TrafficDetectionLabel.RED_LIGHT -> "빨간신호"
        }
        val timer = detection.remainingSeconds?.let { " | ${it}초" }.orEmpty()
        return "$base$timer ${(detection.confidence * 100f).toInt()}%"
    }

    private fun trafficColor(label: TrafficDetectionLabel): Int {
        return when (label) {
            TrafficDetectionLabel.CROSSWALK -> Color.rgb(90, 210, 255)
            TrafficDetectionLabel.GREEN_LIGHT -> Color.rgb(70, 230, 120)
            TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT -> Color.rgb(255, 220, 90)
            TrafficDetectionLabel.RED_LIGHT -> Color.rgb(255, 90, 90)
        }
    }

    private fun appendPolygon(path: Path, polygon: List<PointF>) {
        if (polygon.size < 3) return
        path.moveTo(polygon.first().x, polygon.first().y)
        for (index in 1 until polygon.size) path.lineTo(polygon[index].x, polygon[index].y)
        path.close()
    }

    private fun screenPath(polygon: List<PointF>): Path? {
        if (polygon.size < 3) return null
        return Path().apply {
            moveTo(polygon.first().x, polygon.first().y)
            for (index in 1 until polygon.size) lineTo(polygon[index].x, polygon[index].y)
            close()
        }
    }

    private fun drawPolygon(canvas: Canvas, polygon: List<PointF>, paint: Paint) {
        if (polygon.size < 3) return
        val path = Path().apply {
            moveTo(polygon.first().x, polygon.first().y)
            for (index in 1 until polygon.size) lineTo(polygon[index].x, polygon[index].y)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPolyline(canvas: Canvas, points: List<PointF>, paint: Paint) {
        if (points.size < 2) return
        val path = Path().apply { moveTo(points.first().x, points.first().y) }
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
        } else {
            for (index in 1 until points.lastIndex) {
                val current = points[index]
                val next = points[index + 1]
                path.quadTo(current.x, current.y, (current.x + next.x) * 0.5f, (current.y + next.y) * 0.5f)
            }
            path.lineTo(points.last().x, points.last().y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawCurveLabel(canvas: Canvas, curve: AssistDistanceCurve) {
        val center = curve.points.getOrNull(curve.points.size / 2) ?: return
        val textWidth = arcLabelPaint.measureText(curve.label)
        canvas.drawText(curve.label, center.x - textWidth * 0.5f, center.y - 12f, arcLabelPaint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        startX: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineSpacing: Float,
        maxLines: Int
    ): Float {
        if (text.isBlank()) return startY
        var y = startY
        var lines = 0
        text.split('\n').forEach { paragraph ->
            var remaining = paragraph
            while (remaining.isNotEmpty() && lines < maxLines) {
                val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                val line = remaining.take(count).trimEnd()
                canvas.drawText(line, startX, y, paint)
                y += paint.textSize + lineSpacing
                lines++
                remaining = remaining.drop(count).trimStart()
            }
        }
        return y
    }

    private fun colorForState(state: AssistState): Int {
        return when (state) {
            AssistState.CRITICAL_STOP -> Color.rgb(255, 80, 80)
            AssistState.SYSTEM_UNSTABLE -> Color.rgb(210, 210, 210)
            AssistState.SIDE_SPACE_LEFT,
            AssistState.SIDE_SPACE_RIGHT,
            AssistState.SIDE_SPACE_BOTH,
            AssistState.CAUTION,
            AssistState.CAMERA_ADJUST -> Color.rgb(255, 220, 80)
            AssistState.RECOVERY,
            AssistState.NORMAL -> Color.rgb(0, 184, 148)
        }
    }

    private fun stateLabel(state: AssistState): String {
        return when (state) {
            AssistState.NORMAL -> "관찰"
            AssistState.CAUTION -> "주의"
            AssistState.SIDE_SPACE_LEFT, AssistState.SIDE_SPACE_RIGHT, AssistState.SIDE_SPACE_BOTH -> "여유"
            AssistState.CRITICAL_STOP -> "정지"
            AssistState.CAMERA_ADJUST -> "각도"
            AssistState.SYSTEM_UNSTABLE -> "불안정"
            AssistState.RECOVERY -> "회복"
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun Float.fmt(): String = "%.2f".format(this)

    private data class SituationAlert(
        val text: String,
        val color: Int
    )
}
