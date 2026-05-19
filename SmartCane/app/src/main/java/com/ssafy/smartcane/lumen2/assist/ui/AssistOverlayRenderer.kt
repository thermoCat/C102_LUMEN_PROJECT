package com.ssafy.smartcane.lumen2.assist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

class AssistOverlayRenderer {
    private companion object {
        private const val TACTILE_TEXTURE_SIZE = 256
        private const val PANEL_RIGHT_MARGIN = 24f
        private const val PANEL_WIDTH = 270f
        private const val PANEL_TITLE_TEXT_SIZE = 38f
    }

    private var reusableBitmap: Bitmap? = null
    private var reusableLinearTactileTexture: Bitmap? = null
    private var reusableDotTactileTexture: Bitmap? = null
    private val textureMatrix = Matrix()
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
    private val tactileTileFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tactileTileStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
    }
    private val tactileTexturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 205
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
    private val trafficLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 8, 15, 22)
    }
    private val trafficLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f
        isFakeBoldText = true
    }

    fun render(width: Int, height: Int, decision: AssistDecision, topInsetPx: Float = 0f): Bitmap {
        val bitmap = reusableBitmap
            ?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        drawGuidePathVisualization(canvas, decision)
        val panelBottom = drawGuidancePanel(canvas, width, decision, topInsetPx)
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
        drawTactileTiles(canvas, visualization)
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

    private fun drawGuidancePanel(canvas: Canvas, width: Int, decision: AssistDecision, topInsetPx: Float = 0f): Float {
        val right = width - PANEL_RIGHT_MARGIN
        val left = right - PANEL_WIDTH
        val top = topInsetPx
        val accentColor = colorForState(decision.state)

        val badgeText = stateLabel(decision.state)
        val badgePadH = 16f
        val badgeH = badgeTextPaint.textSize + 20f
        val badgeW = badgeTextPaint.measureText(badgeText) + badgePadH * 2

        val panelPad = 14f
        val panelH = panelPad + badgeH + 10f + PANEL_TITLE_TEXT_SIZE + panelPad
        val panelBottom = top + panelH

        panelBorderPaint.color = accentColor
        canvas.drawRoundRect(left, top, right, panelBottom, 20f, 20f, panelPaint)
        canvas.drawRoundRect(left, top, right, panelBottom, 20f, 20f, panelBorderPaint)

        val badgeLeft = left + panelPad
        val badgeTop = top + panelPad
        val badgeBottom = badgeTop + badgeH
        badgePaint.color = accentColor
        canvas.drawRoundRect(RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeBottom), 12f, 12f, badgePaint)
        canvas.drawText(badgeText, badgeLeft + badgePadH, badgeBottom - 7f, badgeTextPaint)

        val savedSize = titlePaint.textSize
        titlePaint.textSize = PANEL_TITLE_TEXT_SIZE
        titlePaint.color = accentColor
        canvas.drawText(primaryGuidance(decision), left + panelPad, badgeBottom + 10f + PANEL_TITLE_TEXT_SIZE, titlePaint)
        titlePaint.textSize = savedSize

        return panelBottom
    }

    private fun drawSituationAlerts(canvas: Canvas, width: Int, panelBottom: Float, decision: AssistDecision) {
        val alerts = listOfNotNull(
            curbAlert(decision),
            trafficAlert(decision)
        )
        if (alerts.isEmpty()) return
        val right = width - PANEL_RIGHT_MARGIN
        var top = panelBottom + 10f
        alerts.forEach { alert ->
            val textWidth = bodyPaint.measureText(alert.text)
            val boxWidth = (textWidth + 36f).coerceAtLeast(160f).coerceAtMost(PANEL_WIDTH)
            val left = right - boxWidth
            val bottom = top + 50f
            badgePaint.color = alert.color
            canvas.drawRoundRect(left, top, right, bottom, 14f, 14f, trafficLabelBgPaint)
            canvas.drawRoundRect(left, top, right, bottom, 14f, 14f, badgePaint)
            bodyPaint.color = Color.WHITE
            canvas.drawText(alert.text, left + 14f, bottom - 14f, bodyPaint)
            top = bottom + 8f
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
        detections.bestTrafficDetection(TrafficDetectionLabel.RED_LIGHT)?.let { _ ->
            return SituationAlert("정지 신호 감지", Color.rgb(255, 90, 90))
        }
        detections.bestTrafficDetection(TrafficDetectionLabel.GREEN_LIGHT)?.let { _ ->
            return SituationAlert("초록 신호 감지", Color.rgb(70, 230, 120))
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

    private fun primaryGuidance(decision: AssistDecision): String {
        return when (decision.command) {
            AssistCommand.KEEP -> "전방 관찰"
            AssistCommand.STOP -> "정지"
            AssistCommand.FRONT_CAUTION -> "전방 주의"
            AssistCommand.FRONT_LIMIT -> "전방 제한"
            AssistCommand.DEPTH_CAUTION -> "거리 이상 감지"
            AssistCommand.CAMERA_ADJUST -> "카메라 각도 조절"
            AssistCommand.SYSTEM_UNSTABLE -> "인식 불안정"
        }
    }

    private fun evidenceLine(decision: AssistDecision): String {
        return when (decision.command) {
            AssistCommand.STOP -> "${AssistConfig.PLAN_LABEL} 이내 전방 non-walkable"
            AssistCommand.FRONT_LIMIT -> "${AssistConfig.PLAN_LABEL} 이내 전방 non-walkable"
            AssistCommand.FRONT_CAUTION -> "${AssistConfig.PLAN_LABEL} 이내 전방 주의"
            AssistCommand.DEPTH_CAUTION -> "화면 3분할 depth anomaly ${depthLabel(decision.awareness.depthAnomaly)}"
            AssistCommand.CAMERA_ADJUST -> cameraAdjustEvidence(decision.awareness.frontReason)
            AssistCommand.SYSTEM_UNSTABLE -> decision.confidence.unstableReason ?: "센서 신뢰 낮음"
            AssistCommand.KEEP -> "${AssistConfig.PLAN_LABEL}까지 전방 관찰 중"
        }
    }

    private fun cameraAdjustEvidence(reason: String): String {
        return when (reason) {
            "guide range ${AssistConfig.PLAN_LABEL} not visible" -> "${AssistConfig.PLAN_LABEL}선이 화면 밖입니다"
            "2m corridor not visible" -> "${AssistConfig.PLAN_LABEL} 전방 영역이 화면 밖입니다"
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

    private fun drawObstacleMarks(canvas: Canvas, visualization: AssistVisualization) {
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
        
        // 클리핑을 제거하여 정면뿐만 아니라 좌우 분석 영역의 색상(인도, 차도 등)도 모두 보이도록 수정
        if (hasWalkable) canvas.drawPath(walkablePath, walkableFillPaint)
        if (hasRoad) canvas.drawPath(roadPath, roadFillPaint)
        if (hasCurb) canvas.drawPath(curbPath, curbBoundaryFillPaint)
        if (hasNonWalkable) canvas.drawPath(semanticObstaclePath, obstacleFillPaint)
    }

    private fun drawTactileTiles(canvas: Canvas, visualization: AssistVisualization) {
        visualization.tactileTiles.forEach { tile ->
            if (tile.displayPolygon.size != 4) return@forEach
            tactileTileFillPaint.color = when (tile.kind) {
                AssistTactileTileKind.WALKABLE -> Color.argb(28, 28, 170, 88)
                AssistTactileTileKind.NON_WALKABLE -> Color.argb(32, 255, 170, 35)
                AssistTactileTileKind.UNKNOWN -> Color.argb(28, 210, 210, 210)
            }
            drawPolygon(canvas, tile.displayPolygon, tactileTileFillPaint)
            when (tile.kind) {
                AssistTactileTileKind.WALKABLE -> drawTactileTextureTile(
                    canvas = canvas,
                    polygon = tile.displayPolygon,
                    texture = linearTactileTexture()
                )
                AssistTactileTileKind.NON_WALKABLE -> drawTactileTextureTile(
                    canvas = canvas,
                    polygon = tile.displayPolygon,
                    texture = dotTactileTexture()
                )
                AssistTactileTileKind.UNKNOWN -> Unit
            }
            drawPolygon(canvas, tile.displayPolygon, tactileTileStrokePaint)
        }
    }

    private fun drawTactileTextureTile(canvas: Canvas, polygon: List<PointF>, texture: Bitmap) {
        val src = floatArrayOf(
            0f, 0f,
            texture.width.toFloat(), 0f,
            texture.width.toFloat(), texture.height.toFloat(),
            0f, texture.height.toFloat()
        )
        val dst = floatArrayOf(
            polygon[0].x, polygon[0].y,
            polygon[1].x, polygon[1].y,
            polygon[2].x, polygon[2].y,
            polygon[3].x, polygon[3].y
        )
        textureMatrix.reset()
        textureMatrix.setPolyToPoly(src, 0, dst, 0, 4)
        canvas.drawBitmap(texture, textureMatrix, tactileTexturePaint)
    }

    private fun linearTactileTexture(): Bitmap {
        reusableLinearTactileTexture?.let { return it }
        return createLinearTactileTexture().also { reusableLinearTactileTexture = it }
    }

    private fun dotTactileTexture(): Bitmap {
        reusableDotTactileTexture?.let { return it }
        return createDotTactileTexture().also { reusableDotTactileTexture = it }
    }

    private fun createLinearTactileTexture(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            TACTILE_TEXTURE_SIZE,
            TACTILE_TEXTURE_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val size = TACTILE_TEXTURE_SIZE.toFloat()
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(116, 245, 209, 44)
            style = Paint.Style.FILL
        }
        val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, 255, 252, 170)
            style = Paint.Style.STROKE
            strokeWidth = size * 0.018f
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(88, 70, 52, 0)
            style = Paint.Style.FILL
        }
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 248, 150)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(10f, 10f, size - 10f, size - 10f, 20f, 20f, base)
        canvas.drawRoundRect(13f, 13f, size - 13f, size - 13f, 17f, 17f, bevel)

        val barWidth = size * 0.13f
        val barHeight = size * 0.74f
        val top = (size - barHeight) * 0.5f
        val bottom = top + barHeight
        listOf(0.29f, 0.5f, 0.71f).forEach { xRatio ->
            val left = size * xRatio - barWidth * 0.5f
            val right = left + barWidth
            canvas.drawRoundRect(left + 5f, top + 7f, right + 5f, bottom + 7f, barWidth, barWidth, shadow)
            canvas.drawRoundRect(left, top, right, bottom, barWidth, barWidth, highlight)
        }
        return bitmap
    }

    private fun createDotTactileTexture(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            TACTILE_TEXTURE_SIZE,
            TACTILE_TEXTURE_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val size = TACTILE_TEXTURE_SIZE.toFloat()
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(126, 247, 200, 38)
            style = Paint.Style.FILL
        }
        val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(92, 255, 245, 145)
            style = Paint.Style.STROKE
            strokeWidth = size * 0.018f
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, 70, 50, 0)
            style = Paint.Style.FILL
        }
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 238, 112)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(10f, 10f, size - 10f, size - 10f, 20f, 20f, base)
        canvas.drawRoundRect(13f, 13f, size - 13f, size - 13f, 17f, 17f, bevel)

        val radius = size * 0.085f
        listOf(0.28f, 0.5f, 0.72f).forEach { xRatio ->
            listOf(0.28f, 0.5f, 0.72f).forEach { yRatio ->
                val x = size * xRatio
                val y = size * yRatio
                canvas.drawCircle(x + 5f, y + 6f, radius, shadow)
                canvas.drawCircle(x, y, radius, highlight)
            }
        }
        return bitmap
    }

    private fun drawDepthSectionLines(canvas: Canvas, visualization: AssistVisualization) {
        visualization.depthSectionLines.forEach { line ->
            if (line.size >= 2) drawPolyline(canvas, line, depthSectionLinePaint)
        }
    }

    private fun drawTrafficDetections(canvas: Canvas, visualization: AssistVisualization) {
        visualization.trafficDetections.forEach { detection ->
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

    private fun trafficLabel(detection: TrafficDetection): String {
        val base = when (detection.label) {
            TrafficDetectionLabel.CROSSWALK -> "횡단보도"
            TrafficDetectionLabel.GREEN_LIGHT -> "초록신호"
            TrafficDetectionLabel.PEDESTRIAN_TRAFFIC_LIGHT -> "보행신호등"
            TrafficDetectionLabel.RED_LIGHT -> "빨간신호"
        }
        return "$base ${(detection.confidence * 100f).toInt()}%"
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
