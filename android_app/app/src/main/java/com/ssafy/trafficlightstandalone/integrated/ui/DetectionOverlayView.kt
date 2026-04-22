package com.ssafy.trafficlightstandalone.integrated.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.ssafy.trafficlightstandalone.integrated.model.Detection
import kotlin.math.absoluteValue
import kotlin.math.min

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var detections: List<Detection> = emptyList()
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        style = Paint.Style.FILL
    }

    fun setResult(detections: List<Detection>, sourceWidth: Int, sourceHeight: Int) {
        this.detections = detections
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceWidth <= 0 || sourceHeight <= 0) return

        val scale = min(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f

        detections.forEach { detection ->
            val color = colorForClass(detection.className)
            boxPaint.color = color
            textBackgroundPaint.color = Color.argb(180, Color.red(color), Color.green(color), Color.blue(color))

            val rect = RectF(
                offsetX + detection.left * scale,
                offsetY + detection.top * scale,
                offsetX + detection.right * scale,
                offsetY + detection.bottom * scale,
            )
            canvas.drawRoundRect(rect, 16f, 16f, boxPaint)

            val label = "${formatClassName(detection.className)} ${(detection.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize + 18f
            val textLeft = rect.left
            val textTop = (rect.top - textHeight - 8f).coerceAtLeast(0f)
            val textRect = RectF(textLeft, textTop, textLeft + textWidth + 28f, textTop + textHeight)
            canvas.drawRoundRect(textRect, 12f, 12f, textBackgroundPaint)
            canvas.drawText(label, textRect.left + 14f, textRect.bottom - 14f, textPaint)
        }
    }

    private fun colorForClass(className: String): Int = when (className.trim().lowercase()) {
        "crosswalk" -> Color.rgb(59, 130, 246)
        "green_pedestrian_light" -> Color.rgb(34, 197, 94)
        "red_pedestrian_light" -> Color.rgb(239, 68, 68)
        "pedestrian_traffic_light" -> Color.rgb(245, 158, 11)
        else -> {
            val palette = listOf(
                Color.rgb(245, 158, 11), Color.rgb(168, 85, 247),
                Color.rgb(6, 182, 212), Color.rgb(236, 72, 153),
                Color.rgb(14, 165, 233), Color.rgb(249, 115, 22),
            )
            palette[className.hashCode().absoluteValue % palette.size]
        }
    }

    private fun formatClassName(className: String): String =
        className.replace('_', ' ').split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
}
