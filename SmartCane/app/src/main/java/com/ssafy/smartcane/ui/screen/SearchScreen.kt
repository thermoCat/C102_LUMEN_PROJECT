package com.ssafy.smartcane.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import com.ssafy.smartcane.R
import com.ssafy.smartcane.data.model.FavItem
import com.ssafy.smartcane.data.model.RouteDestination
import com.ssafy.smartcane.data.model.SearchResult
import com.ssafy.smartcane.network.KakaoPlace
import com.ssafy.smartcane.network.KakaoLocalSearchService
import com.ssafy.smartcane.network.formatDistance
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

private enum class SearchSub { Main, VoiceReady, VoiceListening, Results }
private const val NEARBY_SEARCH_TAG = "NearbySearch"

private fun voiceSearchIntent(context: android.content.Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "음성으로 검색")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    }

@Composable
private fun SpeechRecognizerEffect(
    active: Boolean,
    startToken: Int,
    onReady: () -> Unit,
    onResult: (String) -> Unit,
    onError: (Int) -> Unit,
    onRmsChanged: (Float) -> Unit
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
    val onRmsChangedState = rememberUpdatedState(onRmsChanged)

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
            override fun onRmsChanged(rmsdB: Float) {
                onRmsChangedState.value(rmsdB)
            }
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
        val intent = voiceSearchIntent(context)
        runCatching { recognizer.startListening(intent) }
            .onFailure { onErrorState.value(SpeechRecognizer.ERROR_CLIENT) }
    }
}

@Composable
fun SearchScreen(
    favorites: List<FavItem>,
    onFavChange: (List<FavItem>) -> Unit,
    onDestinationSelected: (RouteDestination) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val context = LocalContext.current
    val kakaoLocalSearchService = remember { KakaoLocalSearchService() }
    var sub by remember { mutableStateOf(SearchSub.Main) }
    var query by remember { mutableStateOf("") }
    var queryFromVoice by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    var nearbyPlaces by remember { mutableStateOf(emptyList<KakaoPlace>()) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var voiceStartToken by remember { mutableStateOf(0) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var voiceRmsDb by remember { mutableStateOf(0f) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
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
    val externalVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spokenText.isNullOrEmpty()) {
            query = spokenText
            queryFromVoice = true
            sub = SearchSub.Results
        } else {
            sub = SearchSub.Main
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    fun toggleStar(id: Int) {
        val target = results.find { it.id == id } ?: return
        results = results.map { if (it.id == id) it.copy(starred = !it.starred) else it }

        if (target.starred) {
            onFavChange(favorites.filter { it.id != id })
        } else {
            onFavChange(
                favorites + FavItem(
                    id = target.id,
                    name = target.name,
                    addr = target.addr,
                    longitude = target.longitude,
                    latitude = target.latitude,
                estimatedMinutes = null
                )
            )
        }
    }

    fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { externalVoiceLauncher.launch(voiceSearchIntent(context)) }
                .onFailure { sub = SearchSub.Main }
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

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    NearbyLocationEffect(
        enabled = hasLocationPermission,
        onLocation = { currentLocation = it }
    )

    // 탭 진입할 때마다 주변 장소 바로 호출
    LaunchedEffect(Unit) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager

        // 1) 캐시된 마지막 위치 즉시 시도
        if (hasLocationPermission && lm != null) {
            val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            val cached = providers.firstNotNullOfOrNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }
            if (cached != null) currentLocation = cached
        }

        // 2) 캐시 없으면 GPS 업데이트 대기 (타임아웃 없음)
        // network provider 기준 보통 1~3초, 화면 이탈 시 코루틴 자동 취소
        if (currentLocation == null) {
            androidx.compose.runtime.snapshotFlow { currentLocation }
                .first { it != null }
        }

        val loc = currentLocation ?: return@LaunchedEffect
        Log.d(NEARBY_SEARCH_TAG, "nearby search lat=${loc.latitude} lng=${loc.longitude}")
        nearbyPlaces = kakaoLocalSearchService.searchNearbyAttractions(
            longitude = loc.longitude,
            latitude = loc.latitude
        )
        Log.d(NEARBY_SEARCH_TAG, "nearby result count=${nearbyPlaces.size}")
    }

    LaunchedEffect(sub, query) {
        if (sub != SearchSub.Results) return@LaunchedEffect

        val keyword = query.trim()
        if (keyword.length < 2 && !queryFromVoice) {
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
                },
                longitude = place.longitude,
                latitude = place.latitude,
                estimatedMinutes = null
            )
        }
    }

    SpeechRecognizerEffect(
        active = sub == SearchSub.VoiceReady || sub == SearchSub.VoiceListening,
        startToken = voiceStartToken,
        onReady = { sub = SearchSub.VoiceListening },
        onResult = { spokenText ->
            query = spokenText
            queryFromVoice = true
            sub = SearchSub.Results
        },
        onError = {
            voiceRmsDb = 0f
            sub = SearchSub.Main
        },
        onRmsChanged = { rmsDb -> voiceRmsDb = rmsDb }
    )

    when (sub) {
        SearchSub.VoiceReady,
        SearchSub.VoiceListening -> VoiceOverlay(
            listening = sub == SearchSub.VoiceListening,
            rmsDb = voiceRmsDb,
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
            nearbyPlaces = nearbyPlaces,
            onOpenSearch = { sub = SearchSub.Results },
            onVoice = ::startVoiceRecognition,
            onQueryChange = {
                queryFromVoice = false
                query = it
            },
            onCancel = {
                sub = SearchSub.Main
                query = ""
                queryFromVoice = false
            },
            onSelect = { result ->
                onDestinationSelected(result.toRouteDestination())
                onTabChange(NavTab.Route)
            },
            onNearbySelect = { place ->
                onDestinationSelected(place.toRouteDestination())
                onTabChange(NavTab.Route)
            },
            onToggleStar = ::toggleStar,
            onTabChange = {
                sub = SearchSub.Main
                query = ""
                queryFromVoice = false
                if (it != NavTab.Search) onTabChange(it)
            }
        )
    }
}

@Composable
@SuppressLint("MissingPermission")
private fun NearbyLocationEffect(
    enabled: Boolean,
    onLocation: (Location) -> Unit
) {
    val context = LocalContext.current
    val latestOnLocation by rememberUpdatedState(onLocation)

    DisposableEffect(enabled, context) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose { }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(
                    NEARBY_SEARCH_TAG,
                    "location update lat=${location.latitude}, lng=${location.longitude}, provider=${location.provider}"
                )
                latestOnLocation(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(provider, 1_500L, 5f, listener)
            }
        }

        onDispose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }
}

private fun SearchResult.toRouteDestination(): RouteDestination =
    RouteDestination(
        name = name,
        addr = addr,
        longitude = longitude,
        latitude = latitude,
        estimatedMinutes = estimatedMinutes
    )

private fun KakaoPlace.toRouteDestination(): RouteDestination =
    RouteDestination(
        name = placeName,
        addr = roadAddressName.ifBlank { addressName },
        longitude = longitude,
        latitude = latitude,
        estimatedMinutes = null
    )

@Composable
private fun SearchBrowseView(
    inResults: Boolean,
    query: String,
    results: List<SearchResult>,
    nearbyPlaces: List<KakaoPlace>,
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    onNearbySelect: (KakaoPlace) -> Unit,
    onToggleStar: (Int) -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val topPadding by animateDpAsState(
        targetValue = if (inResults) 12.dp else 56.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
    )
    val cancelSlotWidth by animateDpAsState(
        targetValue = if (inResults) 72.dp else 0.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
    )
    val searchBarTrailingSpace by animateDpAsState(
        targetValue = if (inResults) 82.dp else 0.dp,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
    )

    LaunchedEffect(inResults) {
        if (inResults) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (!inResults) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavBg)
        ) {
            SearchMainScrollContent(
                nearbyPlaces = nearbyPlaces,
                onOpenSearch = onOpenSearch,
                onVoice = onVoice,
                onPlaceClick = onNearbySelect,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            )
            BottomNav(active = NavTab.Search, onTab = onTabChange)
        }
        return
    }

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
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 0.dp)
            ) {
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
                        .padding(horizontal = 6.dp, vertical = 4.dp),
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
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            cursorBrush = SolidColor(AppWhite),
                            textStyle = TextStyle(
                                color = AppWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-0.6).sp
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
                            text = "장소 이름을 입력하세요",
                            color = NavGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light
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
            AnimatedVisibility(
                visible = false,
                enter = fadeIn(tween(320, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(240, easing = FastOutSlowInEasing))
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "주변 위치 검색 결과",
                        color = AppWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = if (inResults) 0.dp else 10.dp)) {
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = inResults,
                    enter = fadeIn(tween(360, delayMillis = 240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    HorizontalDivider(color = NavDivider, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(if (inResults) 20.dp else 0.dp))
            }

            if (inResults) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 0.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    results.forEachIndexed { index, result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onSelect(result) }
                                .padding(horizontal = 30.dp)
                                .padding(top = if (index == 0) 7.dp else 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                Text(
                                    text = result.addr,
                                    color = NavGray,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = result.name.take(12),
                                    color = AppWhite,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(14.dp))
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
                        HorizontalDivider(
                            color = NavDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 30.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .weight(1f)
                ) {
                    NearbyRecommendationSection(
                        places = nearbyPlaces,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            }
        }

        BottomNav(active = NavTab.Search, onTab = onTabChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchMainScrollContent(
    nearbyPlaces: List<KakaoPlace>,
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit,
    onPlaceClick: (KakaoPlace) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var searchFieldRevealed by remember { mutableStateOf(false) }
    var searchFieldClosing by remember { mutableStateOf(false) }
    val compactTitleAlphaTarget by remember {
        derivedStateOf {
            when {
                searchFieldRevealed || searchFieldClosing -> 0f
                listState.firstVisibleItemIndex > 1 -> 1f
                else -> {
                    val revealStart = with(density) { 96.dp.toPx() }
                    val revealRange = with(density) { 32.dp.toPx() }
                    ((listState.firstVisibleItemScrollOffset - revealStart) / revealRange).coerceIn(0f, 1f)
                }
            }
        }
    }
    val compactTitleAlpha by animateFloatAsState(
        targetValue = compactTitleAlphaTarget,
        animationSpec = tween(durationMillis = 320),
    )
    LaunchedEffect(searchFieldClosing) {
        if (searchFieldClosing) {
            kotlinx.coroutines.delay(220L)
            searchFieldClosing = false
        }
    }
    val revealSearchFieldConnection = remember(searchFieldRevealed, searchFieldClosing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isAtTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (isAtTop && available.y > 8f) {
                    searchFieldRevealed = true
                }
                if (searchFieldClosing && available.y < 0f) {
                    return Offset(0f, available.y)
                }
                if (searchFieldRevealed && available.y < -8f) {
                    searchFieldRevealed = false
                    searchFieldClosing = true
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }
    val stickyHeaderHeight = 56.dp
    val visiblePlaces = nearbyPlaces.take(15)

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = stickyHeaderHeight)
                .nestedScroll(revealSearchFieldConnection),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SearchTitleRow(onVoice = onVoice)
                Spacer(Modifier.height(10.dp))
            }

            item {
                AnimatedVisibility(
                    visible = searchFieldRevealed,
                    enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                        expandVertically(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top
                        ),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        shrinkVertically(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        )
                ) {
                    MainSearchField(onOpenSearch = onOpenSearch)
                }
            }

            stickyHeader {
                NearbySectionTitleBlock()
            }

            visiblePlaces.forEachIndexed { index, place ->
                item {
                    NearbyRecommendationRow(
                        place = place,
                        isFirst = index == 0,
                        onClick = { onPlaceClick(place) }
                    )
                    if (index != visiblePlaces.lastIndex) {
                        HorizontalDivider(
                            color = NavDivider,
                            thickness = 0.4.dp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stickyHeaderHeight)
                .background(NavBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\uc704\uce58 \uac80\uc0c9",
                color = AppWhite.copy(alpha = compactTitleAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(NavYellow.copy(alpha = compactTitleAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onVoice() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "음성 검색",
                    color = Color(0xFF121212).copy(alpha = compactTitleAlpha),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            HorizontalDivider(
                color = NavGray.copy(alpha = compactTitleAlpha),
                thickness = 0.5.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SearchTitleRow(onVoice: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "위치 검색",
            color = AppWhite,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NavYellow)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onVoice() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "음성 검색",
                color = Color(0xFF121212),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NearbySectionTitleBlock(
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NavBg)
            .padding(horizontal = 10.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "\uc8fc\ubcc0 \uc704\uce58 \uac80\uc0c9 \uacb0\uacfc",
            color = AppWhite.copy(alpha = alpha),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MainSearchField(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NavBg2)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpenSearch
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search_field),
            contentDescription = null,
            tint = NavGray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "도로명 주소로 직접 입력",
            color = NavGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun NearbyRecommendationSection(
    places: List<KakaoPlace>,
    modifier: Modifier = Modifier
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
    )
    val stickyHeaderHeight = 48.dp
    val visiblePlaces = places.take(15)

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = stickyHeaderHeight, bottom = 16.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        text = "주변 위치 검색 결과",
                        color = AppWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            visiblePlaces.forEachIndexed { index, place ->
                item {
                    NearbyRecommendationRow(
                        place = place,
                        isFirst = index == 0,
                        onClick = { }
                    )
                    if (index != visiblePlaces.lastIndex) {
                        HorizontalDivider(
                            color = NavDivider,
                            thickness = 0.4.dp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stickyHeaderHeight)
                .background(NavBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "주변 위치 검색 결과",
                color = AppWhite.copy(alpha = compactTitleAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            HorizontalDivider(
                color = NavGray.copy(alpha = compactTitleAlpha),
                thickness = 0.5.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun NearbyRecommendationRow(
    place: KakaoPlace,
    isFirst: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 10.dp)
            .padding(top = if (isFirst) 7.dp else 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(138.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Color(0xFF555555)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatDistance(place.distanceMeters),
                color = NavYellow,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.addressName.ifBlank { place.roadAddressName },
                color = NavGray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = place.placeName,
                color = AppWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VoiceOverlay(
    listening: Boolean,
    rmsDb: Float,
    onCancel: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    val audioLevel by animateFloatAsState(
        targetValue = if (listening) ((rmsDb + 2f) / 12f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "voiceAudioLevel"
    )
    val wave1Alpha by animateFloatAsState(
        targetValue = if (!listening || audioLevel < 0.2f) 0f else 0.3f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
    )
    val wave2Alpha by animateFloatAsState(
        targetValue = if (!listening || audioLevel < 0.45f) 0f else 0.2f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
    )
    val wave3Alpha by animateFloatAsState(
        targetValue = if (!listening || audioLevel < 0.6f) 0f else 0.1f,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
    )

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

