package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.R
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavGreen
import com.ssafy.smartcane.ui.theme.NavYellow

private enum class RouteSub { Main, Simple, Navigation }

@Composable
fun RouteScreen(onTabChange: (NavTab) -> Unit) {
    var sub by remember { mutableStateOf(RouteSub.Main) }
    val destination = "기아 챔피언스필드"

    when (sub) {
        RouteSub.Main -> RouteMainView(
            destName = destination,
            onNavigation = { sub = RouteSub.Navigation },
            onSimple = { sub = RouteSub.Simple },
            onFav = { onTabChange(NavTab.Fav) },
            onTabChange = onTabChange
        )

        RouteSub.Simple -> SimpleRouteView(
            destName = "멀티캠퍼스",
            onDone = { sub = RouteSub.Main },
            onTabChange = { nextTab ->
                sub = RouteSub.Main
                if (nextTab != NavTab.Route) onTabChange(nextTab)
            }
        )

        RouteSub.Navigation -> MapNavView(
            destName = "멀티캠퍼스",
            onStop = { sub = RouteSub.Main },
            onTabChange = { nextTab ->
                sub = RouteSub.Main
                if (nextTab != NavTab.Route) onTabChange(nextTab)
            }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 56.dp, end = 20.dp, bottom = 22.dp)
        ) {
            Text(
                text = "경로 탐색",
                color = AppWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            Spacer(Modifier.height(40.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavDivider, RoundedCornerShape(14.dp))
            ) {
                RouteInfoRow("출발지", "국민혁 집")
                HorizontalDivider(color = NavDivider, thickness = 1.dp)
                RouteInfoRow("도착지", destName)
                HorizontalDivider(color = NavDivider, thickness = 1.dp)
                RouteInfoRow("예상시간", "5분")
            }
            Spacer(Modifier.height(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NavBtn("길 안내 및 안전 보행 시작", filled = true, big = true, onClick = onNavigation)
                NavBtn("간편 경로 안내", outlined = true, big = true, onClick = onSimple)
                NavBtn("즐겨찾기에서 선택", outlined = true, big = true, onClick = onFav)
            }
            }
        }
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}

@Composable
private fun RouteInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = label,
            color = NavYellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(68.dp)
        )
        Text(
            text = value,
            color = AppWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SimpleRouteView(
    destName: String,
    onDone: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(44.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "경로 안내",
                    color = AppWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "완료",
                color = AppWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDone)
            )
        }
        HorizontalDivider(color = NavDivider, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_route_destination),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(38.dp, 52.dp)
            )
            Spacer(Modifier.width(22.dp))
            Text(
                text = destName,
                color = AppWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        HorizontalDivider(color = NavDivider, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_route_straight),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(40.dp, 54.dp)
            )
            Spacer(Modifier.width(26.dp))
            Text(
                text = "보행자도로를 따라\n154m 이동",
                color = AppWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 31.sp
            )
        }
        Spacer(Modifier.weight(1f))
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}

@Composable
private fun MapNavView(
    destName: String,
    onStop: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_route_destination),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(38.dp, 52.dp)
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = destName,
                color = AppWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(10) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (index == 0) 9.dp else 7.dp)
                        .background(
                            color = if (index == 0) AppWhite else Color(0xFF5D5D5D),
                            shape = CircleShape
                        )
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFF2F0EC))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawRect(Color(0xFFF2F0EC))
                listOf(
                    floatArrayOf(0.02f, 0.04f, 0.34f, 0.20f),
                    floatArrayOf(0.42f, 0.02f, 0.18f, 0.18f),
                    floatArrayOf(0.67f, 0.12f, 0.28f, 0.20f),
                    floatArrayOf(0.08f, 0.42f, 0.18f, 0.13f),
                    floatArrayOf(0.33f, 0.36f, 0.19f, 0.21f),
                    floatArrayOf(0.58f, 0.37f, 0.32f, 0.22f),
                    floatArrayOf(0.18f, 0.66f, 0.18f, 0.16f),
                    floatArrayOf(0.45f, 0.68f, 0.30f, 0.14f)
                ).forEach { (x, y, bw, bh) ->
                    drawRect(Color(0xFFE9E5DE), topLeft = Offset(w * x, h * y), size = Size(w * bw, h * bh))
                    drawRect(Color(0xFFD5D1CA), topLeft = Offset(w * x, h * y), size = Size(w * bw, 1.5f))
                }

                drawRect(Color(0xFFCECBC6), topLeft = Offset(0f, h * 0.28f), size = Size(w, h * 0.035f))
                drawRect(Color(0xFFCECBC6), topLeft = Offset(0f, h * 0.70f), size = Size(w, h * 0.03f))
                drawRect(Color(0xFFCECBC6), topLeft = Offset(w * 0.72f, 0f), size = Size(w * 0.028f, h))

                val route = Path().apply {
                    moveTo(w * 0.95f, h * 0.08f)
                    lineTo(w * 0.68f, h * 0.34f)
                    lineTo(w * 0.52f, h * 0.50f)
                    lineTo(w * 0.39f, h * 0.64f)
                    lineTo(w * 0.23f, h * 0.74f)
                }
                drawPath(
                    path = route,
                    color = Color(0xFF2D67E3),
                    style = Stroke(
                        width = 10f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f))
                    )
                )
                drawCircle(AppWhite, radius = 10f, center = Offset(w * 0.95f, h * 0.08f))
                drawCircle(Color(0xFF2D67E3), radius = 6f, center = Offset(w * 0.95f, h * 0.08f))
                drawCircle(NavGreen, radius = 14f, center = Offset(w * 0.23f, h * 0.74f))
                drawCircle(AppWhite, radius = 7f, center = Offset(w * 0.23f, h * 0.74f))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(NavYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_target),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "현 위치 확인",
                    color = AppWhite,
                    fontSize = 12.sp
                )
            }
            NavBtn(
                label = "종료",
                filled = true,
                big = true,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
        }
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}
