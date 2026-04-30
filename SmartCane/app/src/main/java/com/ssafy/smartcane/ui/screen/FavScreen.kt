package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.data.model.RouteDestination
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
    onDestinationSelected: (RouteDestination) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    var sub by remember { mutableStateOf(FavSub.List) }
    var selected by remember { mutableStateOf<FavItem?>(null) }
    var editName by remember { mutableStateOf("") }

    fun saveEditedFavorite(current: FavItem) {
        val sanitizedName = editName.take(12)
        onFavChange(favorites.map { if (it.id == current.id) it.copy(name = sanitizedName) else it })
        selected = current.copy(name = sanitizedName)
        editName = sanitizedName
    }

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
                onSetDest = {
                    onDestinationSelected(item.toRouteDestination())
                    onTabChange(NavTab.Route)
                },
                onDelete = {
                    onFavChange(favorites.filter { it.id != item.id })
                    selected = null
                    sub = FavSub.List
                },
                onRename = {
                    editName = item.name
                    sub = FavSub.Rename
                },
                onBack = { sub = FavSub.List },
                onTabChange = { tab ->
                    sub = FavSub.List
                    if (tab != NavTab.Fav) onTabChange(tab)
                }
            )
        }

        FavSub.Rename -> selected?.let { item ->
            FavRenameView(
                item = item,
                editName = editName,
                onNameChange = { editName = it },
                onSave = {
                    saveEditedFavorite(item)
                    sub = FavSub.Detail
                },
                onTabChange = { tab ->
                    saveEditedFavorite(item)
                    sub = FavSub.List
                    if (tab != NavTab.Fav) onTabChange(tab)
                }
            )
        }
    }
}

private fun FavItem.toRouteDestination(): RouteDestination =
    RouteDestination(
        name = name,
        addr = addr,
        longitude = longitude,
        latitude = latitude,
        estimatedMinutes = estimatedMinutes
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavListView(
    favorites: List<FavItem>,
    onOpen: (FavItem) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val compactTitleAlphaTarget by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val revealStart = with(density) { 34.dp.toPx() }
                    val revealRange = with(density) { 18.dp.toPx() }
                    ((listState.firstVisibleItemScrollOffset - revealStart) / revealRange).coerceIn(0f, 1f)
                }
            }
        }
    }
    val compactTitleAlpha by animateFloatAsState(
        targetValue = compactTitleAlphaTarget,
        animationSpec = tween(durationMillis = 320),
        label = "favCompactTitleAlpha"
    )
    val stickyHeaderHeight = 56.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp)
                ) {
                    Text(
                        text = "\uc990\uaca8\ucc3e\uae30",
                        color = AppWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\uc990\uaca8\ucc3e\uae30\ud55c \uc7a5\uc18c\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.",
                            color = AppWhite,
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = stickyHeaderHeight),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 0.dp)
                        ) {
                            Text(
                                text = "\uc990\uaca8\ucc3e\uae30",
                                color = AppWhite,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    items(favorites, key = { it.id }) { item ->
                        val index = favorites.indexOfFirst { it.id == item.id }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item) }
                                .padding(horizontal = 40.dp)
                                .padding(top = if (index == 0) 7.dp else 14.dp, bottom = 14.dp)
                        ) {
                            Text(
                                text = item.addr,
                                color = NavGray,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.name.take(12),
                                color = AppWhite,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        HorizontalDivider(
                            color = NavDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 30.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(stickyHeaderHeight)
                        .background(NavBg)
                ) {
                    Text(
                        text = "\uc990\uaca8\ucc3e\uae30",
                        color = AppWhite.copy(alpha = compactTitleAlpha),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                    HorizontalDivider(
                        color = NavGray.copy(alpha = compactTitleAlpha),
                        thickness = 0.5.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
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
    onBack: () -> Unit,
    onTabChange: (NavTab) -> Unit
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
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
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
        BottomNav(active = NavTab.Fav, onTab = onTabChange)
    }
}

@Composable
private fun FavRenameView(
    item: FavItem,
    editName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(88.dp))
            BasicTextField(
                value = editName,
                onValueChange = { onNameChange(it.take(12)) },
                textStyle = TextStyle(
                    color = AppWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.6).sp
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
        BottomNav(active = NavTab.Fav, onTab = onTabChange)
    }
}
