package com.ssafy.smartcane.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.data.model.SearchResult
import com.ssafy.smartcane.data.model.defaultSearchResults
import com.ssafy.smartcane.R
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavBg2
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavGray
import com.ssafy.smartcane.ui.theme.NavLightGray
import com.ssafy.smartcane.ui.theme.NavRed

private enum class SearchSub { Main, VoiceReady, VoiceListening, Results }

@Composable
fun SearchScreen(
    favorites: List<FavItem>,
    onFavChange: (List<FavItem>) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    var sub by remember { mutableStateOf(SearchSub.Main) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(defaultSearchResults) }

    LaunchedEffect(sub) {
        when (sub) {
            SearchSub.VoiceReady -> {
                kotlinx.coroutines.delay(1200)
                sub = SearchSub.VoiceListening
            }

            SearchSub.VoiceListening -> {
                kotlinx.coroutines.delay(2200)
                query = "서울분식"
                sub = SearchSub.Results
            }

            else -> Unit
        }
    }

    fun toggleStar(id: Int) {
        val target = results.find { it.id == id } ?: return
        results = results.map { if (it.id == id) it.copy(starred = !it.starred) else it }

        if (target.starred) {
            onFavChange(favorites.filter { it.id != id })
        } else {
            onFavChange(favorites + FavItem(target.id, target.name, target.addr))
        }
    }

    when (sub) {
        SearchSub.Main -> SearchMainView(
            onOpenSearch = { sub = SearchSub.Results },
            onVoice = { sub = SearchSub.VoiceReady },
            onTabChange = { if (it != NavTab.Search) onTabChange(it) }
        )

        SearchSub.VoiceReady,
        SearchSub.VoiceListening -> VoiceOverlay(
            listening = sub == SearchSub.VoiceListening,
            onCancel = { sub = SearchSub.Main },
            onTabChange = {
                sub = SearchSub.Main
                if (it != NavTab.Search) onTabChange(it)
            }
        )

        SearchSub.Results -> SearchResultsView(
            query = query,
            results = results,
            onQueryChange = { query = it },
            onCancel = {
                sub = SearchSub.Main
                query = ""
            },
            onSelect = { onTabChange(NavTab.Route) },
            onToggleStar = ::toggleStar,
            onTabChange = {
                sub = SearchSub.Main
                query = ""
                if (it != NavTab.Search) onTabChange(it)
            }
        )
    }
}

@Composable
private fun SearchMainView(
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Text(
                text = "위치 검색",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(NavBg2)
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_field),
                    contentDescription = null,
                    tint = NavLightGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "도로명 주소로 검색해주세요",
                    color = NavGray,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            NavBtn(
                label = "음성 검색",
                filled = true,
                big = true,
                onClick = onVoice
            )
            Spacer(Modifier.height(18.dp))
        }
        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}

@Composable
private fun VoiceOverlay(
    listening: Boolean,
    onCancel: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "voice")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )
    val ringScale1 by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "ringScale1"
    )
    val ringScale2 by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(tween(1400, delayMillis = 240, easing = LinearEasing)),
        label = "ringScale2"
    )
    val ringAlpha1 by infinite.animateFloat(
        initialValue = 0.22f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "ringAlpha1"
    )
    val ringAlpha2 by infinite.animateFloat(
        initialValue = 0.14f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400, delayMillis = 240, easing = LinearEasing)),
        label = "ringAlpha2"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A3A3A))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (listening) "듣는 중.." else "준비 중..",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(38.dp))
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    if (listening) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                            drawCircle(NavRed.copy(alpha = ringAlpha2), radius = size.minDimension * 0.23f * ringScale2, center = center)
                            drawCircle(NavRed.copy(alpha = ringAlpha1), radius = size.minDimension * 0.23f * ringScale1, center = center)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(if (listening) 104.dp else 96.dp)
                            .scale(if (listening) scale else 1f)
                            .clip(CircleShape)
                            .background(if (listening) NavRed else Color.Transparent)
                            .then(
                                if (listening) Modifier else Modifier.border(
                                    2.dp,
                                    Color(0xFFD9D9D9),
                                    CircleShape
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic_outline),
                            contentDescription = null,
                            tint = Color(0xFFF3F3F3),
                            modifier = Modifier.size(34.dp, 38.dp)
                        )
                    }
                }
            }
        }
        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}

@Composable
private fun SearchResultsView(
    query: String,
    results: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    onToggleStar: (Int) -> Unit,
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(NavBg2)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_field),
                        contentDescription = null,
                        tint = NavLightGray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text("서울분식", color = NavGray, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "검색 취소",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onCancel)
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = NavDivider, thickness = 1.dp)
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                results.forEach { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, NavDivider, RoundedCornerShape(12.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onSelect(result) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result.name,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onToggleStar(result.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (result.starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                                    ),
                                    contentDescription = null,
                                    tint = com.ssafy.smartcane.ui.theme.NavYellow,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = NavDivider, thickness = 1.dp)
                        Text(
                            text = result.addr,
                            color = NavLightGray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}
