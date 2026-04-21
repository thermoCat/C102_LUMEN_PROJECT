package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.LocationIconDraw
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.*

private enum class RouteSub { Main, Simple, Navigation }

@Composable
fun RouteScreen(onTabChange: (NavTab) -> Unit) {
    var sub by remember { mutableStateOf(RouteSub.Main) }
    val destName = "기아 챔피언스필드"

    when (sub) {
        RouteSub.Simple     -> SimpleRouteView(destName, onDone = { sub = RouteSub.Main })
        RouteSub.Navigation -> MapNavView(destName, onStop = { sub = RouteSub.Main })
        RouteSub.Main       -> RouteMainView(
            destName    = destName,
            onNavigation = { sub = RouteSub.Navigation },
            onSimple     = { sub = RouteSub.Simple },
            onFav        = { onTabChange(NavTab.Fav) },
            onTabChange  = onTabChange
        )
    }
}

@Composable
private fun RouteMainView(
    destName: String,
    onNavigation: () -> Unit,
    onSimple: () -> Unit,
    onFav: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    Column(Modifier.fillMaxSize().background(NavBg)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("경로 탐색", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(NavBg2).border(1.dp, NavBg3, RoundedCornerShape(16.dp))
            ) {
                listOf("출발지" to "국민혁 집", "도착지" to destName, "예상 소요 시간" to "5분")
                    .forEachIndexed { i, (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(label, fontSize = 16.sp, color = NavGray, modifier = Modifier.width(100.dp))
                            Text(value, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        if (i < 2) HorizontalDivider(color = NavBg3, thickness = 1.dp)
                    }
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NavBtn("길 안내 및 안전 보행 시작", filled = true, big = true, onClick = onNavigation)
                NavBtn("간편 경로 안내", outlined = true, big = true, onClick = onSimple)
                NavBtn("즐겨찾기에서 선택", outlined = true, big = true, onClick = onFav)
            }
        }
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}

@Composable
private fun SimpleRouteView(destName: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NavBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("경로 안내", fontSize = 17.sp, color = Color.White)
            Text("완료", color = Color.White, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onDone).padding(4.dp))
        }
        HorizontalDivider(color = NavBg3)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Canvas(Modifier.size(30.dp, 38.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    cubicTo(0f, 0f, 0f, size.height * 0.5f, size.width / 2, size.height * 0.6f)
                    cubicTo(size.width, size.height * 0.5f, size.width, 0f, size.width / 2, 0f)
                }
                drawPath(path, NavGreen)
                drawCircle(Color.White, size.width * 0.17f, Offset(size.width / 2, size.height * 0.37f))
            }
            Text("목적지: $destName", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        HorizontalDivider(color = NavBg3)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Canvas(Modifier.size(30.dp)) {
                drawLine(Color.White, Offset(size.width / 2, size.height * 0.13f), Offset(size.width / 2, size.height * 0.87f), 2.5f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(size.width / 2, size.height * 0.13f), Offset(size.width * 0.23f, size.height * 0.4f), 2.5f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(size.width / 2, size.height * 0.13f), Offset(size.width * 0.77f, size.height * 0.4f), 2.5f, cap = StrokeCap.Round)
            }
            Text("보행자도로를 따라\n154m 이동", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun MapNavView(destName: String, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Canvas(Modifier.size(32.dp, 40.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    cubicTo(0f, 0f, 0f, size.height * 0.5f, size.width / 2, size.height * 0.65f)
                    cubicTo(size.width, size.height * 0.5f, size.width, 0f, size.width / 2, 0f)
                }
                drawPath(path, NavGreen)
                drawCircle(Color.White, size.width * 0.17f, Offset(size.width / 2, size.height * 0.35f))
            }
            Text(destName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        HorizontalDivider(color = NavBg3)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(9) { i ->
                val sz = if (i == 0) 10.dp else 7.dp
                Box(Modifier.padding(horizontal = 3.5.dp).size(sz).clip(CircleShape).background(if (i == 0) Color.White else Color(0xFF555555)))
            }
        }
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val w = size.width; val h = size.height
            drawRect(Color(0xFF2D3740))
            listOf(
                floatArrayOf(0.05f,0.06f,0.31f,0.22f), floatArrayOf(0.41f,0.06f,0.26f,0.22f),
                floatArrayOf(0.72f,0.06f,0.23f,0.22f), floatArrayOf(0.05f,0.33f,0.21f,0.28f),
                floatArrayOf(0.31f,0.33f,0.33f,0.28f), floatArrayOf(0.69f,0.33f,0.26f,0.28f),
                floatArrayOf(0.05f,0.67f,0.23f,0.28f), floatArrayOf(0.33f,0.67f,0.28f,0.28f),
                floatArrayOf(0.67f,0.67f,0.31f,0.28f),
            ).forEach { (x,y,bw,bh) -> drawRect(Color(0xFF3A444E), Offset(x*w,y*h), Size(bw*w,bh*h)) }
            drawRect(Color(0xFF4A5260), Offset(0f, h*0.29f), Size(w, h*0.028f))
            drawRect(Color(0xFF4A5260), Offset(w*0.385f, 0f), Size(w*0.026f, h))
            drawRect(Color(0xFF4A5260), Offset(0f, h*0.625f), Size(w, h*0.028f))
            drawRect(Color(0xFF4A5260), Offset(w*0.655f, 0f), Size(w*0.026f, h))
            val routePath = Path().apply {
                moveTo(w*0.154f, h*0.94f); lineTo(w*0.154f, h*0.32f)
                lineTo(w*0.41f, h*0.32f); lineTo(w*0.41f, h*0.17f)
            }
            drawPath(routePath, Color(0xFF4488FF), style = Stroke(5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f,5f))))
            drawCircle(Color(0xFF4488FF), 8f, Offset(w*0.154f, h*0.94f))
            drawCircle(Color.White, 5f, Offset(w*0.154f, h*0.94f), style = Stroke(2f))
            drawCircle(NavGreen, 10f, Offset(w*0.41f, h*0.14f))
            drawCircle(Color.White, 7f, Offset(w*0.41f, h*0.14f), style = Stroke(2f))
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(horizontal = 20.dp, vertical = 14.dp).padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(70.dp).clip(CircleShape).background(NavYellow).clickable { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LocationIconDraw()
                    Text("현 위치 확인", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            NavBtn("종료", filled = true, big = true, onClick = onStop, modifier = Modifier.weight(1f))
        }
    }
}
