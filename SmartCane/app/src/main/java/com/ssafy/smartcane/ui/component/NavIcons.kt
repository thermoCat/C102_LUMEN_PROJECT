package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ssafy.smartcane.ui.theme.NavYellow

@Composable
fun SearchIconDraw(active: Boolean) {
    val color = if (active) NavYellow else Color(0xFF666666)
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color, radius = size.width * 0.3f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(2.2f))
        drawLine(color, Offset(size.width * 0.65f, size.height * 0.65f), Offset(size.width * 0.88f, size.height * 0.88f), 2.2f, cap = StrokeCap.Round)
    }
}

@Composable
fun MicIconDraw(color: Color) {
    Canvas(modifier = Modifier.size(34.dp, 38.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color = color, topLeft = Offset(w * 0.29f, h * 0.05f), size = Size(w * 0.41f, h * 0.58f), cornerRadius = CornerRadius(w * 0.2f), style = Stroke(2.4f))
        drawLine(color, Offset(w * 0.12f, h * 0.47f), Offset(w * 0.12f, h * 0.55f), 2.4f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.88f, h * 0.47f), Offset(w * 0.88f, h * 0.55f), 2.4f, cap = StrokeCap.Round)
        drawArc(color, startAngle = 0f, sweepAngle = -180f, useCenter = false, topLeft = Offset(w * 0.12f, h * 0.34f), size = Size(w * 0.76f, h * 0.42f), style = Stroke(2.4f, cap = StrokeCap.Round))
        drawLine(color, Offset(w / 2, h * 0.82f), Offset(w / 2, h * 0.97f), 2.4f, cap = StrokeCap.Round)
    }
}

@Composable
fun StarIconDraw(filled: Boolean, sizeDp: Float = 24f) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(w / 2, h * 0.08f)
            lineTo(w * 0.63f, h * 0.35f); lineTo(w * 0.92f, h * 0.39f)
            lineTo(w * 0.71f, h * 0.59f); lineTo(w * 0.76f, h * 0.87f)
            lineTo(w / 2,    h * 0.74f);  lineTo(w * 0.24f, h * 0.87f)
            lineTo(w * 0.29f, h * 0.59f); lineTo(w * 0.08f, h * 0.39f)
            lineTo(w * 0.37f, h * 0.35f); close()
        }
        if (filled) drawPath(path, NavYellow)
        drawPath(path, NavYellow, style = Stroke(2f))
    }
}

@Composable
fun LocationIconDraw() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black, size.width * 0.18f, c)
        drawCircle(Color.Black, size.width * 0.39f, c, style = Stroke(2f))
        drawLine(Color.Black, Offset(c.x, 0f),                  Offset(c.x, size.height * 0.18f), 2f, cap = StrokeCap.Round)
        drawLine(Color.Black, Offset(c.x, size.height * 0.82f), Offset(c.x, size.height),         2f, cap = StrokeCap.Round)
        drawLine(Color.Black, Offset(0f, c.y),                  Offset(size.width * 0.18f, c.y),  2f, cap = StrokeCap.Round)
        drawLine(Color.Black, Offset(size.width * 0.82f, c.y),  Offset(size.width, c.y),          2f, cap = StrokeCap.Round)
    }
}
