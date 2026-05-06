package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavYellow

val LocalNavButtonCornerRadius = compositionLocalOf { 30.dp }

@Composable
fun NavBtn(
    label: String,
    filled: Boolean = false,
    outlined: Boolean = false,
    big: Boolean = false,
    cornerRadius: Dp? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius ?: LocalNavButtonCornerRadius.current)
    val bgColor = if (filled) NavYellow else Color.Transparent
    val textColor = if (filled) Color(0xFF151515) else NavYellow
    val fontSize = if (big) 24.sp else 17.sp
    val verticalPadding = if (big) 18.dp else 15.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(if (outlined) Modifier.border(1.5.dp, NavDivider, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}
