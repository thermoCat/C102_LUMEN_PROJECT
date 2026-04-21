package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BackBtn
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.*

private enum class FavSub { List, Detail, Rename }

@Composable
fun FavScreen(
    favorites: List<FavItem>,
    onFavChange: (List<FavItem>) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    var sub by remember { mutableStateOf(FavSub.List) }
    var selected by remember { mutableStateOf<FavItem?>(null) }
    var editName by remember { mutableStateOf("") }

    when (sub) {
        FavSub.Rename -> if (selected != null) FavRenameView(
            item         = selected!!,
            editName     = editName,
            onNameChange = { editName = it },
            onSave       = {
                onFavChange(favorites.map { if (it.id == selected!!.id) it.copy(name = editName) else it })
                selected = selected!!.copy(name = editName)
                sub = FavSub.Detail
            },
            onBack = { sub = FavSub.Detail }
        )
        FavSub.Detail -> if (selected != null) FavDetailView(
            item     = selected!!,
            onSetDest = { onTabChange(NavTab.Route) },
            onDelete  = { onFavChange(favorites.filter { it.id != selected!!.id }); sub = FavSub.List; selected = null },
            onRename  = { editName = selected!!.name; sub = FavSub.Rename },
            onBack    = { sub = FavSub.List }
        )
        FavSub.List -> FavListView(
            favorites = favorites,
            onOpen    = { item -> selected = item; sub = FavSub.Detail },
            onTabChange = onTabChange
        )
    }
}

@Composable
private fun FavListView(
    favorites: List<FavItem>,
    onOpen: (FavItem) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    Column(Modifier.fillMaxSize().background(NavBg)) {
        Text(
            "즐겨찾기 목록",
            fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        if (favorites.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("즐겨찾기한 장소가 없습니다.", fontSize = 17.sp, color = NavGray)
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                favorites.forEach { item ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpen(item) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(item.addr, fontSize = 13.sp, color = NavGray)
                        Spacer(Modifier.height(3.dp))
                        Text(item.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    HorizontalDivider(color = NavYellow, thickness = 1.5.dp)
                }
            }
        }
        BottomNav(active = NavTab.Fav, onTab = onTabChange)
    }
}

@Composable
private fun FavDetailView(
    item: FavItem,
    onSetDest: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(NavBg)) {
        BackBtn(onClick = onBack)
        Column(
            Modifier.weight(1f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(item.name, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(item.addr, fontSize = 16.sp, color = NavGray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NavBtn("목적지로 설정", filled = true, big = true, onClick = onSetDest)
                NavBtn("즐겨찾기 삭제", outlined = true, big = true, onClick = onDelete)
                NavBtn("장소 이름 변경", outlined = true, big = true, onClick = onRename)
            }
        }
    }
}

@Composable
private fun FavRenameView(
    item: FavItem,
    editName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(NavBg)) {
        BackBtn(onClick = onBack)
        Column(
            Modifier.weight(1f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BasicTextField(
                value = editName, onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth().border(2.dp, NavYellow, RectangleShape).padding(bottom = 4.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center
                ),
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } }
            )
            Spacer(Modifier.height(8.dp))
            Text(item.addr, fontSize = 16.sp, color = NavGray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            NavBtn("변경 완료", filled = true, big = true, onClick = onSave)
        }
    }
}
