package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.ui.theme.NavYellow

@Composable
fun NavBtn(
    label: String,
    filled: Boolean = false,
    outlined: Boolean = false,
    big: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor   = if (filled) NavYellow else Color.Transparent
    val textColor = if (filled) Color.Black else Color.White
    val fontSize  = if (big) 22.sp else 18.sp
    val vPad      = if (big) 20.dp else 17.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(if (outlined) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = vPad),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}
