package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackBtn(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp, 18.dp)) {
            drawLine(Color.White, Offset(size.width * 0.9f, 1.dp.toPx()), Offset(1.dp.toPx(), size.height / 2), 2.2f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(1.dp.toPx(), size.height / 2), Offset(size.width * 0.9f, size.height - 1.dp.toPx()), 2.2f, cap = StrokeCap.Round)
        }
        Text("돌아가기", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
