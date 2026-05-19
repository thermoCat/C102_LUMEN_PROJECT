package com.ssafy.smartcane.segformer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.ssafy.smartcane.segformer.model.BrailleTilePattern
import com.ssafy.smartcane.segformer.model.LetterboxInfo
import com.ssafy.smartcane.segformer.model.SourcePoint
import com.ssafy.smartcane.segformer.model.VirtualBrailleGuide
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SegmentationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var maskBitmap: Bitmap? = null
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0
    private var letterbox: LetterboxInfo? = null
    private var virtualBrailleGuide: VirtualBrailleGuide? = null

    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guidePath = Path()
    private val guideFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 209, 167, 39)
        style = Paint.Style.FILL
    }
    private val guideStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 95, 73, 18)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val raisedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 232, 193, 68)
        style = Paint.Style.FILL
    }
    private val raisedShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 111, 82, 18)
        style = Paint.Style.FILL
    }
    private val raisedHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 236, 137)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun setResult(
        mask: IntArray,
        maskWidth: Int,
        maskHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        letterbox: LetterboxInfo,
        virtualBrailleGuide: VirtualBrailleGuide?,
    ) {
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        this.letterbox = letterbox
        this.virtualBrailleGuide = virtualBrailleGuide
        this.maskBitmap?.recycle()
        this.maskBitmap = createMaskBitmap(mask, maskWidth, maskHeight)
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        maskBitmap?.recycle()
        maskBitmap = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = maskBitmap ?: return
        val currentLetterbox = letterbox ?: return
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return
        }

        val previewScale = min(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val offsetX = (width - sourceWidth * previewScale) / 2f
        val offsetY = (height - sourceHeight * previewScale) / 2f

        val contentLeft = currentLetterbox.padX.roundToInt().coerceIn(0, bitmap.width - 1)
        val contentTop = currentLetterbox.padY.roundToInt().coerceIn(0, bitmap.height - 1)
        val contentRight = (currentLetterbox.padX + sourceWidth * currentLetterbox.scale)
            .roundToInt()
            .coerceIn(contentLeft + 1, bitmap.width)
        val contentBottom = (currentLetterbox.padY + sourceHeight * currentLetterbox.scale)
            .roundToInt()
            .coerceIn(contentTop + 1, bitmap.height)

        sourceRect.set(contentLeft, contentTop, contentRight, contentBottom)
        destinationRect.set(
            offsetX,
            offsetY,
            offsetX + sourceWidth * previewScale,
            offsetY + sourceHeight * previewScale,
        )
        canvas.drawBitmap(bitmap, sourceRect, destinationRect, overlayPaint)
        drawVirtualBrailleGuide(canvas, virtualBrailleGuide, previewScale, offsetX, offsetY)
    }

    private fun drawVirtualBrailleGuide(
        canvas: Canvas,
        guide: VirtualBrailleGuide?,
        previewScale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        if (guide == null) return
        for (block in guide.blocks) {
            if (block.corners.size < 4) continue
            val corners = block.corners.map {
                SourcePoint(
                    x = offsetX + it.x * previewScale,
                    y = offsetY + it.y * previewScale,
                )
            }
            drawTile(canvas, corners, guideFillPaint)
            when (block.pattern) {
                BrailleTilePattern.DIRECTIONAL -> drawDirectionalBars(canvas, corners)
                BrailleTilePattern.WARNING -> drawWarningDots(canvas, corners)
            }
            drawTile(canvas, corners, guideStrokePaint)
        }
    }

    private fun drawTile(canvas: Canvas, corners: List<SourcePoint>, paint: Paint) {
        guidePath.reset()
        guidePath.moveTo(corners[0].x, corners[0].y)
        for (index in 1 until corners.size) {
            guidePath.lineTo(corners[index].x, corners[index].y)
        }
        guidePath.close()
        canvas.drawPath(guidePath, paint)
    }

    private fun drawDirectionalBars(canvas: Canvas, corners: List<SourcePoint>) {
        val barCenters = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f)
        for (center in barCenters) {
            drawRaisedPatch(
                canvas = canvas,
                corners = corners,
                startS = 0.12f,
                endS = 0.88f,
                startL = center - 0.045f,
                endL = center + 0.045f,
            )
        }
    }

    private fun drawWarningDots(canvas: Canvas, corners: List<SourcePoint>) {
        val radius = max(2.0f, min(tileWidthPx(corners), tileLengthPx(corners)) * 0.055f)
        val positions = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f)
        for (s in positions) {
            for (l in positions) {
                val center = pointInTile(corners, s, l)
                canvas.drawCircle(center.x + radius * 0.25f, center.y + radius * 0.35f, radius, raisedShadowPaint)
                canvas.drawCircle(center.x, center.y, radius, raisedPaint)
                canvas.drawCircle(center.x - radius * 0.20f, center.y - radius * 0.20f, radius * 0.55f, raisedHighlightPaint)
            }
        }
    }

    private fun drawRaisedPatch(
        canvas: Canvas,
        corners: List<SourcePoint>,
        startS: Float,
        endS: Float,
        startL: Float,
        endL: Float,
    ) {
        val patch = listOf(
            pointInTile(corners, startS, startL),
            pointInTile(corners, endS, startL),
            pointInTile(corners, endS, endL),
            pointInTile(corners, startS, endL),
        )
        val shadow = patch.map { SourcePoint(it.x + 2f, it.y + 2f) }
        drawTile(canvas, shadow, raisedShadowPaint)
        drawTile(canvas, patch, raisedPaint)
        guidePath.reset()
        guidePath.moveTo(patch[0].x, patch[0].y)
        guidePath.lineTo(patch[1].x, patch[1].y)
        canvas.drawPath(guidePath, raisedHighlightPaint)
    }

    private fun pointInTile(corners: List<SourcePoint>, s: Float, l: Float): SourcePoint {
        val left = lerp(corners[0], corners[1], s)
        val right = lerp(corners[3], corners[2], s)
        return lerp(left, right, l)
    }

    private fun lerp(start: SourcePoint, end: SourcePoint, t: Float): SourcePoint {
        return SourcePoint(
            x = start.x + (end.x - start.x) * t,
            y = start.y + (end.y - start.y) * t,
        )
    }

    private fun tileWidthPx(corners: List<SourcePoint>): Float {
        val nearWidth = distance(corners[0], corners[3])
        val farWidth = distance(corners[1], corners[2])
        return (nearWidth + farWidth) / 2f
    }

    private fun tileLengthPx(corners: List<SourcePoint>): Float {
        val leftLength = distance(corners[0], corners[1])
        val rightLength = distance(corners[3], corners[2])
        return (leftLength + rightLength) / 2f
    }

    private fun distance(a: SourcePoint, b: SourcePoint): Float {
        return hypot(a.x - b.x, a.y - b.y)
    }

    private fun createMaskBitmap(mask: IntArray, maskWidth: Int, maskHeight: Int): Bitmap {
        val pixels = IntArray(mask.size)
        for (index in mask.indices) {
            pixels[index] = colorForClass(mask[index])
        }
        return Bitmap.createBitmap(pixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
    }

    private fun colorForClass(classIndex: Int): Int {
        return if (classIndex in vocPalette.indices) {
            vocPalette[classIndex]
        } else {
            fallbackPalette[classIndex % fallbackPalette.size]
        }
    }

    companion object {
        private val vocPalette = intArrayOf(
            Color.TRANSPARENT,
            Color.argb(120, 128, 0, 0),
            Color.argb(120, 0, 128, 0),
            Color.argb(120, 128, 128, 0),
            Color.argb(120, 0, 0, 128),
            Color.argb(120, 128, 0, 128),
            Color.argb(120, 0, 128, 128),
            Color.argb(120, 128, 128, 128),
            Color.argb(120, 64, 0, 0),
            Color.argb(120, 192, 0, 0),
            Color.argb(120, 64, 128, 0),
            Color.argb(120, 192, 128, 0),
            Color.argb(120, 64, 0, 128),
            Color.argb(120, 192, 0, 128),
            Color.argb(120, 64, 128, 128),
            Color.argb(120, 192, 128, 128),
            Color.argb(120, 0, 64, 0),
            Color.argb(120, 128, 64, 0),
            Color.argb(120, 0, 192, 0),
            Color.argb(120, 128, 192, 0),
            Color.argb(120, 0, 64, 128),
        )

        private val fallbackPalette = intArrayOf(
            Color.argb(120, 245, 158, 11),
            Color.argb(120, 168, 85, 247),
            Color.argb(120, 6, 182, 212),
            Color.argb(120, 236, 72, 153),
        )
    }
}
