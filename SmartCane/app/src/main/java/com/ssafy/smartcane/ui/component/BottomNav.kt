package com.ssafy.smartcane.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.R
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBarBg
import com.ssafy.smartcane.ui.theme.NavYellow

@Composable
fun BottomNav(
    active: NavTab,
    onTab: (NavTab) -> Unit,
    backgroundColor: Color = NavBarBg
) {
    val items = listOf(
        NavItem(NavTab.Search, "위치 검색", R.drawable.ic_nav_search),
        NavItem(NavTab.Route, "경로 안내", R.drawable.ic_nav_route),
        NavItem(NavTab.Safety, "안전 보행", R.drawable.ic_nav_safety),
        NavItem(NavTab.Fav, "즐겨찾기", R.drawable.ic_nav_fav)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            items.forEach { item ->
                val isActive = active == item.tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp, bottom = 24.dp)
                        .clearAndSetSemantics {
                            contentDescription = if (isActive) {
                                "하단 메뉴 ${item.label}, 선택됨"
                            } else {
                                "하단 메뉴 ${item.label}"
                            }
                            role = Role.Tab
                            selected = isActive
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onTab(item.tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = if (isActive) NavYellow else AppWhite
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isActive) NavYellow else AppWhite
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
