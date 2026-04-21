package com.ssafy.smartcane.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.data.model.SearchResult
import com.ssafy.smartcane.data.model.defaultSearchResults
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.MicIconDraw
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.component.SearchIconDraw
import com.ssafy.smartcane.ui.component.StarIconDraw
import com.ssafy.smartcane.ui.theme.*
import kotlinx.coroutines.delay

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
            SearchSub.VoiceReady -> { delay(1200); sub = SearchSub.VoiceListening }
            SearchSub.VoiceListening -> { delay(2200); query = "서울특별시"; sub = SearchSub.Results }
            else -> {}
        }
    }

    fun toggleStar(id: Int) {
        val item = results.find { it.id == id } ?: return
        results = results.map { if (it.id == id) it.copy(starred = !it.starred) else it }
        if (!item.starred) onFavChange(favorites + FavItem(item.id, item.name, item.addr))
        else onFavChange(favorites.filter { it.id != id })
    }

    when (sub) {
        SearchSub.VoiceReady, SearchSub.VoiceListening -> VoiceOverlay(
            listening = sub == SearchSub.VoiceListening,
            onCancel  = { sub = SearchSub.Main },
            onTabChange = { t -> sub = SearchSub.Main; if (t != NavTab.Search) onTabChange(t) }
        )
        SearchSub.Results -> SearchResultsView(
            query         = query,
            results       = results,
            onQueryChange = { query = it },
            onCancel      = { sub = SearchSub.Main; query = "" },
            onSelect      = { onTabChange(NavTab.Route) },
            onToggleStar  = { toggleStar(it) },
            onTabChange   = { t -> sub = SearchSub.Main; query = ""; if (t != NavTab.Search) onTabChange(t) }
        )
        SearchSub.Main -> SearchMainView(
            onOpenSearch = { sub = SearchSub.Results },
            onVoice      = { sub = SearchSub.VoiceReady },
            onTabChange  = { t -> if (t != NavTab.Search) onTabChange(t) }
        )
    }
}

@Composable
private fun SearchMainView(
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    Column(Modifier.fillMaxSize().background(NavBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Text("위치 검색", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavBg2)
                    .border(1.dp, NavBg3, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchIconDraw(active = false)
                Text("도로명 주소로 검색해주세요", color = NavGray, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            NavBtn(label = "음성 검색", filled = true, big = true, onClick = onVoice)
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
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val ring1Scale by infinite.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "r1s")
    val ring1Alpha by infinite.animateFloat(0.6f, 0f,   infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "r1a")
    val ring2Scale by infinite.animateFloat(1f, 2.2f,   infiniteRepeatable(tween(1200, delayMillis = 400, easing = LinearEasing)), label = "r2s")
    val ring2Alpha by infinite.animateFloat(0.6f, 0f,   infiniteRepeatable(tween(1200, delayMillis = 400, easing = LinearEasing)), label = "r2a")

    Column(Modifier.fillMaxSize().background(Color(0xFF2C2C2C)), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (listening) "듣는 중.." else "준비 중..", fontSize = 22.sp, color = Color.White)
            Spacer(Modifier.height(32.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                if (listening) {
                    androidx.compose.foundation.Canvas(Modifier.size(120.dp)) {
                        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                        val baseR = size.width / 2 * 0.75f
                        drawCircle(NavRed.copy(alpha = ring1Alpha), radius = baseR * ring1Scale, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                        drawCircle(NavRed.copy(alpha = ring2Alpha), radius = baseR * ring2Scale, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(if (listening) scale else 1f)
                        .clip(CircleShape)
                        .background(if (listening) NavRed else Color.Transparent)
                        .then(if (!listening) Modifier.border(2.dp, Color(0xFFAAAAAA), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) { MicIconDraw(color = if (listening) Color.White else Color(0xFFAAAAAA)) }
            }
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF666666), RoundedCornerShape(10.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) { Text("취소", color = Color.White, fontSize = 16.sp) }
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
    Column(Modifier.fillMaxSize().background(NavBg)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavBg2)
                    .border(1.5.dp, NavYellow, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchIconDraw(active = false)
                BasicTextField(
                    value = query, onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("도로명 주소 입력", color = NavGray, fontSize = 17.sp)
                        inner()
                    }
                )
            }
            Text("검색 취소", color = Color.White, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            results.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavBg2)
                        .border(1.5.dp, NavBg3, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(r) }
                    ) {
                        Text(r.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(r.addr, fontSize = 13.sp, color = NavGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(44.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onToggleStar(r.id) },
                        contentAlignment = Alignment.Center
                    ) { StarIconDraw(filled = r.starred, sizeDp = 28f) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}
