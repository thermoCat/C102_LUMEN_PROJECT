package com.ssafy.smartcane.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ssafy.smartcane.R
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.data.model.SearchResult
import com.ssafy.smartcane.network.KakaoLocalSearchService
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavBg2
import com.ssafy.smartcane.ui.theme.NavBarBg
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavGray
import com.ssafy.smartcane.ui.theme.NavLightGray
import com.ssafy.smartcane.ui.theme.NavYellow
import kotlin.random.Random

private enum class SearchSub { Main, VoiceReady, VoiceListening, Results }

@Composable
private fun SpeechRecognizerEffect(
    active: Boolean,
    startToken: Int,
    onReady: () -> Unit,
    onResult: (String) -> Unit,
    onError: (Int) -> Unit
) {
    val context = LocalContext.current
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }
    val onReadyState = rememberUpdatedState(onReady)
    val onResultState = rememberUpdatedState(onResult)
    val onErrorState = rememberUpdatedState(onError)

    DisposableEffect(recognizer) {
        if (recognizer == null) {
            return@DisposableEffect onDispose { }
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onReadyState.value()
            }

            override fun onResults(results: Bundle?) {
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                if (!spokenText.isNullOrEmpty()) {
                    onResultState.value(spokenText)
                } else {
                    onErrorState.value(SpeechRecognizer.ERROR_NO_MATCH)
                }
            }

            override fun onError(error: Int) {
                onErrorState.value(error)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
        }

        recognizer.setRecognitionListener(listener)
        onDispose {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    LaunchedEffect(active, startToken, recognizer) {
        if (recognizer == null) return@LaunchedEffect
        if (!active) {
            runCatching { recognizer.cancel() }
            return@LaunchedEffect
        }

        runCatching { recognizer.cancel() }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "음성으로 검색")
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure { onErrorState.value(SpeechRecognizer.ERROR_CLIENT) }
    }
}

@Composable
fun SearchScreen(
    favorites: List<FavItem>,
    onFavChange: (List<FavItem>) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val context = LocalContext.current
    val kakaoLocalSearchService = remember { KakaoLocalSearchService() }
    var sub by remember { mutableStateOf(SearchSub.Main) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    var voiceStartToken by remember { mutableStateOf(0) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sub = SearchSub.VoiceReady
            pendingVoiceStart = true
        } else {
            sub = SearchSub.Main
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

    fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            sub = SearchSub.Main
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            sub = SearchSub.VoiceReady
            pendingVoiceStart = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(sub, pendingVoiceStart) {
        if (pendingVoiceStart && sub == SearchSub.VoiceReady) {
            voiceStartToken += 1
            pendingVoiceStart = false
        }
    }

    LaunchedEffect(sub, query) {
        if (sub != SearchSub.Results) return@LaunchedEffect

        val keyword = query.trim()
        if (keyword.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(300)

        val places = kakaoLocalSearchService.search(keyword = keyword)
        results = places.mapIndexed { index, place ->
            SearchResult(
                id = (place.placeName + place.roadAddressName + place.addressName).hashCode() + index,
                name = place.placeName,
                addr = place.roadAddressName.ifBlank {
                    place.addressName
                },
                starred = favorites.any { fav ->
                    fav.name == place.placeName || fav.addr == place.roadAddressName || fav.addr == place.addressName
                }
            )
        }
    }

    SpeechRecognizerEffect(
        active = sub == SearchSub.VoiceReady || sub == SearchSub.VoiceListening,
        startToken = voiceStartToken,
        onReady = { sub = SearchSub.VoiceListening },
        onResult = { spokenText ->
            query = spokenText
            sub = SearchSub.Results
        },
        onError = { error ->
            sub = when (error) {
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SearchSub.Results

                else -> SearchSub.Main
            }
        }
    )

    when (sub) {
        SearchSub.VoiceReady,
        SearchSub.VoiceListening -> VoiceOverlay(
            listening = sub == SearchSub.VoiceListening,
            onCancel = { sub = SearchSub.Main },
            onTabChange = {
                sub = SearchSub.Main
                if (it != NavTab.Search) onTabChange(it)
            }
        )

        SearchSub.Main,
        SearchSub.Results -> SearchBrowseView(
            inResults = sub == SearchSub.Results,
            query = query,
            results = results,
            onOpenSearch = { sub = SearchSub.Results },
            onVoice = ::startVoiceRecognition,
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
private fun SearchBrowseView(
    inResults: Boolean,
    query: String,
    results: List<SearchResult>,
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    onToggleStar: (Int) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val topPadding by animateDpAsState(
        targetValue = if (inResults) 12.dp else 64.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "searchTopPadding"
    )
    val cancelSlotWidth by animateDpAsState(
        targetValue = if (inResults) 72.dp else 0.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "cancelSlotWidth"
    )
    val searchBarTrailingSpace by animateDpAsState(
        targetValue = if (inResults) 82.dp else 0.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "searchBarTrailingSpace"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = topPadding)
        ) {
            AnimatedVisibility(
                visible = !inResults,
                enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) + expandVertically(tween(320, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(240, easing = FastOutSlowInEasing)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing))
            ) {
                Column {
                    Text(
                        text = "위치 검색",
                        color = AppWhite,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "직접 입력하거나 음성으로 검색하세요",
                        color = NavGray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            Column(modifier = Modifier.padding(horizontal = if (inResults) 0.dp else 10.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = searchBarTrailingSpace)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavBg2)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (!inResults) onOpenSearch()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_field),
                        contentDescription = null,
                        tint = if (inResults) NavLightGray else NavGray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))

                    if (inResults) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            cursorBrush = SolidColor(AppWhite),
                            textStyle = TextStyle(
                                color = AppWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "도로명 주소로 직접 입력하기",
                            color = NavGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(cancelSlotWidth),
                    contentAlignment = Alignment.CenterStart
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = cancelSlotWidth >= 72.dp,
                        enter = fadeIn(tween(100, delayMillis = 0, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(100, easing = FastOutSlowInEasing))
                    ) {
                        Text(
                            text = "검색 취소",
                            color = AppWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(onClick = onCancel)
                        )
                    }
                }
            }

            Column {
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = inResults,
                    enter = fadeIn(tween(360, delayMillis = 240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    HorizontalDivider(color = NavDivider, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(20.dp))
            }

            if (inResults) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    results.forEach { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, NavDivider, RoundedCornerShape(12.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onSelect(result) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.name,
                                    color = AppWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                                        tint = NavYellow,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = NavDivider, thickness = 0.5.dp)
                            Text(
                                text = result.addr,
                                color = NavLightGray,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    NavBtn(
                        label = "음성 검색",
                        filled = true,
                        big = true,
                        onClick = onVoice
                    )
                }
            }
            }
        }

        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}

/*
@Composable
private fun SearchHeaderContent() {
    Column {
        Text(
            text = "위치 검색",
            color = AppWhite,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "직접 입력하거나 음성으로 검색하세요",
            color = AppWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SearchBar(
    inResults: Boolean,
    query: String,
    cancelSlotWidth: androidx.compose.ui.unit.Dp,
    searchBarTrailingSpace: androidx.compose.ui.unit.Dp,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = searchBarTrailingSpace)
                .clip(RoundedCornerShape(10.dp))
                .background(NavBg2)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!inResults) onOpenSearch()
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search_field),
                contentDescription = null,
                tint = if (inResults) NavLightGray else NavGray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))

            if (inResults) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    cursorBrush = SolidColor(AppWhite),
                    textStyle = TextStyle(
                        color = AppWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            } else {
                Text(
                    text = "도로명 주소로 검색해주세요",
                    color = AppWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(cancelSlotWidth),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedVisibility(
                visible = cancelSlotWidth >= 72.dp,
                enter = fadeIn(tween(100, delayMillis = 0, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(100, easing = FastOutSlowInEasing))
            ) {
                Text(
                    text = "검색 취소",
                    color = AppWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onCancel)
                )
            }
        }
    }
}

@Composable
private fun StickySearchBrowseView(
    inResults: Boolean,
    query: String,
    results: List<SearchResult>,
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    onToggleStar: (Int) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val topPadding by animateDpAsState(
        targetValue = if (inResults) 12.dp else 22.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "searchTopPadding"
    )
    val cancelSlotWidth by animateDpAsState(
        targetValue = if (inResults) 72.dp else 0.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "cancelSlotWidth"
    )
    val searchBarTrailingSpace by animateDpAsState(
        targetValue = if (inResults) 82.dp else 0.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "searchBarTrailingSpace"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        if (inResults) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = topPadding)
            ) {
                SearchBar(
                    inResults = true,
                    query = query,
                    cancelSlotWidth = cancelSlotWidth,
                    searchBarTrailingSpace = searchBarTrailingSpace,
                    onOpenSearch = onOpenSearch,
                    onQueryChange = onQueryChange,
                    onCancel = onCancel
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = NavDivider, thickness = 0.5.dp)
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
                                .border(0.5.dp, NavDivider, RoundedCornerShape(12.dp))
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
                                    color = AppWhite,
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
                                        tint = NavYellow,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = NavDivider, thickness = 0.5.dp)
                            Text(
                                text = result.addr,
                                color = AppWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SearchHeaderContent()
                    }
                }
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavBg)
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        SearchBar(
                            inResults = false,
                            query = query,
                            cancelSlotWidth = 0.dp,
                            searchBarTrailingSpace = 0.dp,
                            onOpenSearch = onOpenSearch,
                            onQueryChange = onQueryChange,
                            onCancel = onCancel
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NavBtn(
                            label = "음성 검색",
                            filled = true,
                            big = true,
                            onClick = onVoice
                        )
                    }
                }
            }
        }

        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}

*/
@Composable
private fun VoiceOverlay(
    listening: Boolean,
    onCancel: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    var mockAudioLevel by remember { mutableStateOf(0f) }
    val wave1Alpha by animateFloatAsState(
        targetValue = if (!listening || mockAudioLevel < 0.2f) 0f else 0.3f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "wave1Alpha"
    )
    val wave2Alpha by animateFloatAsState(
        targetValue = if (!listening || mockAudioLevel < 0.45f) 0f else 0.2f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "wave2Alpha"
    )
    val wave3Alpha by animateFloatAsState(
        targetValue = if (!listening || mockAudioLevel < 0.6f) 0f else 0.1f,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "wave3Alpha"
    )

    LaunchedEffect(listening) {
        if (!listening) {
            mockAudioLevel = 0f
            return@LaunchedEffect
        }

        while (true) {
            mockAudioLevel = Random.nextFloat()
            kotlinx.coroutines.delay(Random.nextLong(140L, 320L))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBarBg)
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
                    color = AppWhite,
                    fontSize = 32.sp,
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
                            val center = Offset(size.width / 2, size.height / 2)
                            val waveColor = Color(0xFFC02C26)
                            drawCircle(
                                color = waveColor.copy(alpha = wave3Alpha),
                                radius = 89.dp.toPx(),
                                center = center
                            )
                            drawCircle(
                                color = waveColor.copy(alpha = wave2Alpha),
                                radius = 79.dp.toPx(),
                                center = center
                            )
                            drawCircle(
                                color = waveColor.copy(alpha = wave1Alpha),
                                radius = 69.dp.toPx(),
                                center = center
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(114.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                if (listening) R.drawable.ic_voice_active else R.drawable.ic_voice_idle
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        }
        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}
