package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.theme.NavBarBg
import com.ssafy.smartcane.ui.theme.NavYellow
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset

@Composable
fun BottomNav(active: NavTab, onTab: (NavTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavBarBg)
            .border(width = 1.dp, color = Color(0xFF2A2A2A), shape = RectangleShape)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                NavTab.Search to "위치 검색",
                NavTab.Route  to "경로 탐색",
                NavTab.Safety to "안전 보행",
                NavTab.Fav    to "즐겨찾기",
            ).forEach { (t, label) ->
                val isActive = active == t
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics {
                            contentDescription = if (isActive) "메뉴바 $label 선택됨" else "메뉴바 $label"
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TabIcon(tab = t, active = isActive)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) NavYellow else Color(0xFF666666)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: NavTab, active: Boolean) {
    val color = if (active) NavYellow else Color(0xFF666666)
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        when (tab) {
            NavTab.Search -> {
                drawCircle(color = color, radius = w * 0.3f, center = Offset(w * 0.42f, h * 0.42f), style = Stroke(width = 2.2f))
                drawLine(color, Offset(w * 0.65f, h * 0.65f), Offset(w * 0.88f, h * 0.88f), 2.2f)
            }
            NavTab.Route -> {
                drawLine(color, Offset(w / 2, h * 0.12f), Offset(w / 2, h * 0.88f), 2.2f)
                drawLine(color, Offset(w * 0.2f, h * 0.42f), Offset(w * 0.8f, h * 0.42f), 2.2f)
                drawLine(color, Offset(w / 2, h * 0.12f), Offset(w * 0.75f, h * 0.42f), 2.2f)
                drawLine(color, Offset(w / 2, h * 0.12f), Offset(w * 0.25f, h * 0.42f), 2.2f)
            }
            NavTab.Safety -> {
                drawCircle(color = color, radius = w * 0.375f, center = Offset(w / 2, h / 2), style = Stroke(width = 2.2f))
                drawCircle(color = color, radius = w * 0.17f,  center = Offset(w / 2, h / 2), style = Stroke(width = 2f))
                drawCircle(color = color, radius = w * 0.06f,  center = Offset(w / 2, h / 2))
            }
            NavTab.Fav -> {
                val path = Path().apply {
                    moveTo(w / 2, h * 0.08f)
                    lineTo(w * 0.63f, h * 0.35f); lineTo(w * 0.92f, h * 0.39f)
                    lineTo(w * 0.71f, h * 0.59f); lineTo(w * 0.76f, h * 0.87f)
                    lineTo(w / 2,    h * 0.74f);  lineTo(w * 0.24f, h * 0.87f)
                    lineTo(w * 0.29f, h * 0.59f); lineTo(w * 0.08f, h * 0.39f)
                    lineTo(w * 0.37f, h * 0.35f); close()
                }
                if (active) drawPath(path, color = color)
                else        drawPath(path, color = color, style = Stroke(width = 2f))
            }
        }
    }
}
