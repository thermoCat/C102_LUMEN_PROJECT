package com.ssafy.smartcane.lumen2.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.ssafy.smartcane.lumen2.ar.ArFrameData
import com.ssafy.smartcane.lumen2.ar.DisplayUvMapper
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.text.Text
import kotlin.math.max
import kotlin.math.min

class OverlayRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.YELLOW
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    fun semantics(frame: ArFrameData, width: Int, height: Int, rotatePortrait: Boolean): Bitmap {
        val labels = frame.semanticLabels ?: return blank(
            width,
            height,
            if (frame.semanticsSupported) "Semantics supported, waiting/no image" else "Semantics unsupported on this device"
        )
        val raw = Bitmap.createBitmap(frame.semanticWidth, frame.semanticHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(frame.semanticWidth * frame.semanticHeight)
        for (i in pixels.indices) pixels[i] = semanticColor(labels[i].toInt() and 0xff)
        raw.setPixels(pixels, 0, frame.semanticWidth, 0, 0, frame.semanticWidth, frame.semanticHeight)
        val overlay = raw.toDisplayOverlay(width, height, rotatePortrait, frame.displayUvCoords)
        drawSemanticLegend(Canvas(overlay), width, height)
        return overlay
    }

    fun depth(frame: ArFrameData, width: Int, height: Int, rotatePortrait: Boolean): Bitmap {
        val depths = frame.depthMillimeters ?: return blank(
            width,
            height,
            if (frame.depthSupported) "Depth supported, waiting/no image" else "Depth unsupported on this device"
        )
        val raw = Bitmap.createBitmap(frame.depthWidth, frame.depthHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(frame.depthWidth * frame.depthHeight)
        for (i in pixels.indices) {
            val mm = depths[i].toInt() and 0xffff
            pixels[i] = if (mm in 1..7999) depthHeatColor(mm / 1000f) else Color.TRANSPARENT
        }
        raw.setPixels(pixels, 0, frame.depthWidth, 0, 0, frame.depthWidth, frame.depthHeight)
        val heatmap = raw.toDisplayOverlay(width, height, rotatePortrait, frame.displayUvCoords)
        val canvas = Canvas(heatmap)
        fillPaint.color = Color.argb(120, 0, 0, 0)
        canvas.drawRect(24f, height - 120f, width - 24f, height - 72f, fillPaint)
        canvas.drawText("DEPTH HEATMAP  near=red/yellow  far=blue  invalid=transparent", 40f, height - 86f, textPaint)
        return heatmap
    }

    fun poseIntrinsics(frame: ArFrameData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fillPaint.color = Color.argb(135, 0, 0, 0)
        val panelTop = (height - 280f).coerceAtLeast(24f)
        canvas.drawRect(24f, panelTop, width - 24f, height - 72f, fillPaint)
        val t = frame.poseTranslation
        val q = frame.poseQuaternion
        val intr = frame.intrinsics
        var y = panelTop + 46f
        canvas.drawText("tracking=${frame.tracking}", 44f, y, textPaint)
        y += 42f
        canvas.drawText("pose t: x=${t[0].fmt()} y=${t[1].fmt()} z=${t[2].fmt()}", 44f, y, textPaint)
        y += 42f
        canvas.drawText("pose q: ${q[0].fmt()}, ${q[1].fmt()}, ${q[2].fmt()}, ${q[3].fmt()}", 44f, y, textPaint)
        y += 42f
        canvas.drawText("intrinsics ${intr.imageWidth}x${intr.imageHeight}", 44f, y, textPaint)
        y += 42f
        canvas.drawText("fx=${intr.fx.fmt()} fy=${intr.fy.fmt()} cx=${intr.cx.fmt()} cy=${intr.cy.fmt()}", 44f, y, textPaint)
        return bitmap
    }

    fun text(frame: ArFrameData, width: Int, height: Int, result: Text?, analysisWidth: Int, analysisHeight: Int, rotatePortrait: Boolean): Bitmap {
        val cameraBitmap = frame.cameraBitmap ?: return blank(width, height, "Text mode waiting for camera image")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        strokePaint.color = Color.rgb(80, 220, 255)
        result?.textBlocks?.forEach { block ->
            drawMappedRect(canvas, block.boundingBox, analysisWidth, analysisHeight, width, height, rotatePortrait)
            block.lines.firstOrNull()?.text?.let {
                val p = mapPoint(block.boundingBox?.left ?: 0, block.boundingBox?.top ?: 0, analysisWidth, analysisHeight, width, height, rotatePortrait)
                canvas.drawText(it.take(24), p.first, max(34f, p.second - 8f), textPaint)
            }
        }
        return bitmap
    }

    fun objects(frame: ArFrameData, width: Int, height: Int, objects: List<DetectedObject>, analysisWidth: Int, analysisHeight: Int, status: String, rotatePortrait: Boolean): Bitmap {
        frame.cameraBitmap ?: return blank(width, height, "Object mode waiting for camera image")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        strokePaint.color = Color.rgb(255, 210, 70)
        drawBottomStatus(canvas, width, height, status)
        objects.forEach { obj ->
            drawMappedRect(canvas, obj.boundingBox, analysisWidth, analysisHeight, width, height, rotatePortrait)
            val label = obj.labels.firstOrNull()?.text ?: "id=${obj.trackingId ?: "-"}"
            val p = mapPoint(obj.boundingBox.left, obj.boundingBox.top, analysisWidth, analysisHeight, width, height, rotatePortrait)
            canvas.drawText(label, p.first, max(34f, p.second - 8f), textPaint)
        }
        return bitmap
    }

    private fun drawMappedRect(canvas: Canvas, rect: Rect?, srcW: Int, srcH: Int, dstW: Int, dstH: Int, rotate: Boolean) {
        rect ?: return
        val p1 = mapPoint(rect.left, rect.top, srcW, srcH, dstW, dstH, rotate)
        val p2 = mapPoint(rect.right, rect.bottom, srcW, srcH, dstW, dstH, rotate)
        canvas.drawRect(
            RectF(min(p1.first, p2.first), min(p1.second, p2.second), max(p1.first, p2.first), max(p1.second, p2.second)),
            strokePaint
        )
    }

    private fun mapPoint(x: Int, y: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int, rotate: Boolean): Pair<Float, Float> {
        val nx = x.toFloat() / srcW.coerceAtLeast(1)
        val ny = y.toFloat() / srcH.coerceAtLeast(1)
        return if (rotate) Pair((1f - ny) * dstW, nx * dstH) else Pair(nx * dstW, ny * dstH)
    }

    private fun Bitmap.toDisplayOverlay(width: Int, height: Int, rotate: Boolean, displayUvCoords: FloatArray? = null): Bitmap {
        if (displayUvCoords != null && displayUvCoords.size >= 8) {
            val overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(overlay)
            val matrix = DisplayUvMapper.imageToViewMatrix(
                displayUvCoords = displayUvCoords,
                imageWidth = this.width.toFloat(),
                imageHeight = this.height.toFloat(),
                viewWidth = width.toFloat(),
                viewHeight = height.toFloat()
            )
            canvas.drawBitmap(this, matrix, null)
            return overlay
        }
        val transformed = if (rotate && this.width > this.height) {
            val matrix = Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
        } else this
        return Bitmap.createScaledBitmap(transformed, width, height, false)
    }

    private fun blank(width: Int, height: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fillPaint.color = Color.argb(145, 80, 80, 80)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        canvas.drawText(label, 32f, 64f, textPaint)
        return bitmap
    }

    private fun drawBottomStatus(canvas: Canvas, width: Int, height: Int, label: String) {
        fillPaint.color = Color.argb(145, 0, 0, 0)
        canvas.drawRect(24f, height - 120f, width - 24f, height - 72f, fillPaint)
        canvas.drawText(label, 40f, height - 86f, textPaint)
    }

    private fun drawSemanticLegend(canvas: Canvas, width: Int, height: Int) {
        val labels = listOf(
            0 to "UNLABELED",
            1 to "SKY",
            2 to "BUILDING",
            3 to "TREE",
            4 to "ROAD",
            5 to "SIDEWALK",
            6 to "TERRAIN",
            7 to "STRUCTURE",
            8 to "OBJECT",
            9 to "VEHICLE",
            10 to "PERSON",
            11 to "WATER"
        )
        val rowHeight = 34f
        val rows = 4
        val panelHeight = rowHeight * rows + 28f
        val top = height - panelHeight - 66f
        fillPaint.color = Color.argb(145, 0, 0, 0)
        canvas.drawRect(24f, top, width - 24f, top + panelHeight, fillPaint)
        val colWidth = (width - 80f) / 3f
        labels.forEachIndexed { index, (id, label) ->
            val col = index % 3
            val row = index / 3
            val x = 44f + col * colWidth
            val y = top + 34f + row * rowHeight
            fillPaint.color = semanticColor(id)
            canvas.drawRect(x, y - 20f, x + 24f, y + 4f, fillPaint)
            canvas.drawText(label, x + 34f, y, legendPaint)
        }
    }

    private fun semanticColor(label: Int): Int {
        return when (label) {
            0 -> Color.argb(150, 120, 120, 120)
            1 -> Color.argb(160, 80, 150, 255)
            2 -> Color.argb(160, 130, 100, 70)
            3 -> Color.argb(160, 40, 150, 80)
            4 -> Color.argb(165, 90, 90, 95)
            5 -> Color.argb(165, 40, 220, 100)
            6 -> Color.argb(165, 180, 210, 70)
            7 -> Color.argb(165, 220, 210, 70)
            8 -> Color.argb(165, 240, 160, 50)
            9 -> Color.argb(165, 240, 70, 170)
            10 -> Color.argb(165, 80, 200, 240)
            11 -> Color.argb(165, 40, 120, 240)
            else -> Color.argb(150, 150, 80, 220)
        }
    }

    private fun depthHeatColor(meters: Float): Int {
        val t = (meters / 6f).coerceIn(0f, 1f)
        val r = (255f * (1f - (t - 0.20f).coerceIn(0f, 0.80f) / 0.80f)).toInt()
        val g = (255f * (1f - kotlin.math.abs(t - 0.35f) / 0.35f).coerceIn(0f, 1f)).toInt()
        val b = (255f * ((t - 0.35f).coerceIn(0f, 0.65f) / 0.65f)).toInt()
        return Color.argb(210, r, g, b)
    }

    private fun Float.fmt(): String = "%.2f".format(this)
}
