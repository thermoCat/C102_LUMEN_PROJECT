package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.R
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.theme.NavBarBg
import com.ssafy.smartcane.ui.theme.NavLightGray
import com.ssafy.smartcane.ui.theme.NavYellow

@Composable
fun BottomNav(active: NavTab, onTab: (NavTab) -> Unit) {
    val items = listOf(
        NavItem(NavTab.Search, "위치 검색", R.drawable.ic_nav_search),
        NavItem(NavTab.Route, "경로 탐색", R.drawable.ic_nav_route),
        NavItem(NavTab.Safety, "안전 보행", R.drawable.ic_nav_safety),
        NavItem(NavTab.Fav, "즐겨찾기", R.drawable.ic_nav_fav)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavBarBg)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isActive = active == item.tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics {
                            contentDescription = if (isActive) "메뉴바 ${item.label} 선택됨" else "메뉴바 ${item.label}"
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onTab(item.tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = if (isActive) NavYellow else NavLightGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) NavYellow else NavLightGray
                    )
                }
            }
        }
    }
}

private data class NavItem(
    val tab: NavTab,
    val label: String,
    val iconRes: Int
)
