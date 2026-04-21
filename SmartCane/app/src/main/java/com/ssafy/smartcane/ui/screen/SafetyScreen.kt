package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.theme.NavBlueBg
import com.ssafy.smartcane.ui.theme.NavGray

@Composable
fun SafetyScreen(onTabChange: (NavTab) -> Unit) {
    var on by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(if (on) NavBlueBg else Color(0xFF2C2C2C)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (on) "안전 보행 모드가 가동 중 입니다" else "안전 보행 모드가 비활성 상태입니다",
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, lineHeight = 34.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (on) "버튼을 누르면 안전 보행이 종료 됩니다." else "버튼을 눌러 안전 보행을 활성화 하세요",
                fontSize = 15.sp, color = NavGray, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Color(0xFF1A4A8A) else Color(0xFF3A3A3A))
                    .then(if (on) Modifier.border(2.dp, Color(0xFF3A7ACC), RoundedCornerShape(18.dp)) else Modifier)
                    .clickable { on = !on }
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (on) "안전 보행 끄기" else "안전 보행 켜기", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(56.dp, 30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (on) Color(0xFF2A72DC) else Color(0xFF555555))
                    .clickable { on = !on },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .padding(start = if (on) 29.dp else 3.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        BottomNav(active = NavTab.Safety, onTab = onTabChange)
    }
}
