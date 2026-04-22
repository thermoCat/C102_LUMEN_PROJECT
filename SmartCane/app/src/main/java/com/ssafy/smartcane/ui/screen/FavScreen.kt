package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BackBtn
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavGray

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
        FavSub.List -> FavListView(
            favorites = favorites,
            onOpen = {
                selected = it
                sub = FavSub.Detail
            },
            onTabChange = onTabChange
        )

        FavSub.Detail -> selected?.let { item ->
            FavDetailView(
                item = item,
                onSetDest = { onTabChange(NavTab.Route) },
                onDelete = {
                    onFavChange(favorites.filter { it.id != item.id })
                    selected = null
                    sub = FavSub.List
                },
                onRename = {
                    editName = item.name
                    sub = FavSub.Rename
                },
                onBack = { sub = FavSub.List }
            )
        }

        FavSub.Rename -> selected?.let { item ->
            FavRenameView(
                item = item,
                editName = editName,
                onNameChange = { editName = it },
                onSave = {
                    onFavChange(favorites.map { if (it.id == item.id) it.copy(name = editName) else it })
                    selected = item.copy(name = editName)
                    sub = FavSub.Detail
                },
                onBack = { sub = FavSub.Detail }
            )
        }
    }
}

@Composable
private fun FavListView(
    favorites: List<FavItem>,
    onOpen: (FavItem) -> Unit,
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
                .fillMaxWidth()
                .padding(top = 64.dp)
        ) {
            Text(
                text = "즐겨찾기 목록",
                color = AppWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "즐겨찾기한 장소가 없습니다.",
                        color = AppWhite,
                        fontSize = 18.sp
                    )
                }
            } else {
                Spacer(Modifier.height(26.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    favorites.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = item.addr,
                                color = NavGray,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = item.name,
                                color = AppWhite,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        HorizontalDivider(
                            color = NavDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        BackBtn(onClick = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(84.dp))
            Text(
                text = item.name,
                color = AppWhite,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.addr,
                color = NavGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(84.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavBtn("목적지로 설정", filled = true, big = true, onClick = onSetDest)
                NavBtn("즐겨찾기 삭제", outlined = true, big = true, onClick = onDelete)
                NavBtn("장소 이름 변경", outlined = true, big = true, onClick = onRename)
            }
            Spacer(Modifier.weight(1f))
        }
        BottomNav(active = NavTab.Fav, onTab = {})
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        BackBtn(onClick = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(88.dp))
            BasicTextField(
                value = editName,
                onValueChange = onNameChange,
                textStyle = TextStyle(
                    color = AppWhite,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        innerTextField()
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.addr,
                color = NavGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(84.dp))
            NavBtn("변경 완료", filled = true, big = true, onClick = onSave)
            Spacer(Modifier.weight(1f))
        }
        BottomNav(active = NavTab.Fav, onTab = {})
    }
}
