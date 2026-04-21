package com.ssafy.smartcane.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav

private val SafetyAccent = Color(0xFF005387)
private val SafetyBackground = Color(0xFF001B2B)

@Composable
fun SafetyScreen(onTabChange: (NavTab) -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    val background = if (enabled) SafetyBackground else Color(0xFF3A3A3A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            SafetyStatusText(enabled = enabled)
            Spacer(Modifier.height(34.dp))
            SafetyActionButton(enabled = enabled, onClick = { enabled = !enabled })
            Spacer(Modifier.height(40.dp))
            SafetySwitch(enabled = enabled, onToggle = { enabled = !enabled })
            Spacer(Modifier.weight(1f))
        }
        BottomNav(active = NavTab.Safety, onTab = onTabChange)
    }
}

@Composable
private fun SafetyStatusText(enabled: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (enabled) "안전 보행 모드가 가동 중입니다." else "안전 보행 모드가 비활성 상태입니다.",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (enabled) "버튼을 누르면 안전 보행이 종료됩니다." else "버튼을 눌러 안전 보행을 활성화하세요.",
            color = Color(0xFFD2D2D2),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SafetyActionButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(162.dp),
        contentAlignment = Alignment.Center
    ) {
        listOf(
            Triple(0.dp, 0.dp, 0.10f) to Pair(72.dp, 160),
            Triple(12.dp, 12.dp, 0.20f) to Pair(62.dp, 80),
            Triple(24.dp, 24.dp, 0.30f) to Pair(52.dp, 0)
        ).forEach { (layer, style) ->
            val (horizontalInset, verticalInset, targetAlpha) = layer
            val (cornerRadius, delayMillis) = style
            val animatedAlpha by animateFloatAsState(
                targetValue = if (enabled) targetAlpha else 0f,
                animationSpec = tween(durationMillis = 220, delayMillis = delayMillis),
                label = "safetyHoverAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalInset, vertical = verticalInset)
                    .height(162.dp - (verticalInset * 2))
                    .background(SafetyAccent.copy(alpha = animatedAlpha), RoundedCornerShape(cornerRadius))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 36.dp)
                .height(90.dp)
                .background(if (enabled) SafetyAccent else Color(0xFF222222), RoundedCornerShape(42.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (enabled) "안전 보행 끄기" else "안전 보행 켜기",
                color = if (enabled) Color.White else Color(0xFFA7A7A7),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun SafetySwitch(enabled: Boolean, onToggle: () -> Unit) {
    val thumbOffsetX by animateDpAsState(
        targetValue = if (enabled) 34.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "safetySwitchThumb"
    )

    Box(
        modifier = Modifier
            .size(width = 68.dp, height = 34.dp)
            .background(if (enabled) SafetyAccent else Color(0xFF222222), RoundedCornerShape(17.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX)
                .size(24.dp)
                .background(Color(0xFFD9E2EA), CircleShape)
        )
    }
}
