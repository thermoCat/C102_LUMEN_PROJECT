package com.ssafy.smartcane.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.route.RouteLineLayer
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.kakao.vectormap.route.RouteLineStylesSet
import com.ssafy.smartcane.BuildConfig
import com.ssafy.smartcane.R
import com.ssafy.smartcane.data.model.RouteDestination
import com.ssafy.smartcane.navigation.LowPassLocationSmoother
import com.ssafy.smartcane.navigation.RouteDeviationStatus
import com.ssafy.smartcane.navigation.RouteMatchResult
import com.ssafy.smartcane.navigation.RouteNavigationMatcher
import com.ssafy.smartcane.network.DirectionCue
import com.ssafy.smartcane.network.KakaoLocalSearchService
import com.ssafy.smartcane.network.RouteInstruction
import com.ssafy.smartcane.network.RoutePoint
import com.ssafy.smartcane.network.WalkingDirectionsService
import com.ssafy.smartcane.network.WalkingRoutePlan
import com.ssafy.smartcane.network.formatDistance
import com.ssafy.smartcane.network.formatDuration
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.LocalNavButtonCornerRadius
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavGray
import com.ssafy.smartcane.ui.theme.NavGreen
import com.ssafy.smartcane.ui.theme.NavLightGray
import com.ssafy.smartcane.ui.theme.NavRed
import com.ssafy.smartcane.ui.theme.NavYellow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.ssafy.smartcane.lumen2.SafetyWalkService
import com.ssafy.smartcane.util.AppLogger

private enum class RouteSub { Main, Simple, Navigation }
private const val USE_DUMMY_ROUTE_MAP = false

private const val ROUTE_TAG = "RouteScreen"
private const val MAX_LAST_KNOWN_AGE_MS = 5 * 60 * 1000L
private const val REROUTE_COOLDOWN_MS = 30_000L

@Composable
fun RouteScreen(
    destination: RouteDestination?,
    originName: String,
    onTabChange: (NavTab) -> Unit
) {
    val context = LocalContext.current
    val directionsService = remember { WalkingDirectionsService() }
    val localSearchService = remember { KakaoLocalSearchService() }
    val locationSmoother = remember(destination?.longitude, destination?.latitude) { LowPassLocationSmoother() }
    val routeScope = rememberCoroutineScope()
    var sub by remember { mutableStateOf(RouteSub.Main) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var resolvedOriginName by remember { mutableStateOf(originName) }
    var routePlan by remember { mutableStateOf<WalkingRoutePlan?>(null) }
    var isRouteLoading by remember { mutableStateOf(false) }
    var routeMessage by remember { mutableStateOf("") }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var requestedRouteKey by remember(destination?.longitude, destination?.latitude) { mutableStateOf("") }
    var routeRequestToken by remember(destination?.longitude, destination?.latitude) { mutableStateOf(0) }
    var handledRouteRequestToken by remember(destination?.longitude, destination?.latitude) { mutableStateOf(-1) }
    var lastRerouteAt by remember(destination?.longitude, destination?.latitude) { mutableStateOf(0L) }
    var routeJob by remember(destination?.longitude, destination?.latitude) { mutableStateOf<Job?>(null) }
    val hasRouteOrigin = currentLocation != null
    val routeMatch = remember(currentLocation, routePlan) {
        val location = currentLocation
        val plan = routePlan
        if (location == null || plan == null) {
            null
        } else {
            RouteNavigationMatcher.match(location, plan.points)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    CurrentLocationEffect(
        enabled = hasLocationPermission,
        onLocation = { location ->
            Log.d(
                ROUTE_TAG,
                "Location update lat=${location.latitude}, lng=${location.longitude}, accuracy=${if (location.hasAccuracy()) location.accuracy else null}, time=${location.time}"
            )
            if (location.isUsableRouteOrigin()) {
                currentLocation = locationSmoother.smooth(location)
            } else {
                Log.d(
                    ROUTE_TAG,
                    "Ignored non-Korea route origin lat=${location.latitude}, lng=${location.longitude}, accuracy=${if (location.hasAccuracy()) location.accuracy else null}"
                )
            }
        }
    )

    fun requestWalkingRoute() {
        val origin = currentLocation ?: return
        val destLongitude = destination?.longitude ?: return
        val destLatitude = destination.latitude ?: return
        val routeKey = origin.routeOriginKey(destLongitude, destLatitude)
        if (requestedRouteKey.isNotBlank() && handledRouteRequestToken == routeRequestToken) return
        requestedRouteKey = routeKey
        handledRouteRequestToken = routeRequestToken

        routeJob?.cancel()
        routeJob = routeScope.launch {
            Log.d(
                ROUTE_TAG,
                "Route request ready origin=${origin.longitude},${origin.latitude} destination=$destLongitude,$destLatitude token=$routeRequestToken"
            )
            isRouteLoading = true
            routeMessage = ""
            routePlan = null
            Log.d(ROUTE_TAG, "Calling walking directions")
            try {
                val fetchedRoutePlan = withTimeoutOrNull(12_000L) {
                    directionsService.getWalkingRoute(
                        originLongitude = origin.longitude,
                        originLatitude = origin.latitude,
                        destinationLongitude = destLongitude,
                        destinationLatitude = destLatitude
                    )
                }
                fetchedRoutePlan?.let { plan ->
                    Log.d(
                        ROUTE_TAG,
                        "Walking route ready key=$routeKey distance=${plan.distanceMeters}m duration=${plan.durationSeconds}s points=${plan.points.size} first=${plan.points.firstOrNull()} last=${plan.points.lastOrNull()}"
                    )
                }
                routePlan = fetchedRoutePlan
                if (fetchedRoutePlan == null) {
                    Log.e(ROUTE_TAG, "Walking route plan is null or timed out")
                    routeMessage = "\uacbd\ub85c\ub97c \uac00\uc838\uc624\uc9c0 \ubabb\ud588\uc2b5\ub2c8\ub2e4. API \ud0a4, \uc81c\ud734 \uad8c\ud55c, \ucd9c\ubc1c/\ubaa9\uc801\uc9c0 \uc88c\ud45c\ub97c \ud655\uc778\ud574\uc8fc\uc138\uc694."
                }
                if (resolvedOriginName.isBlank()) {
                    resolvedOriginName = "\ud604\uc7ac \uc704\uce58"
                }
                Log.d(
                    ROUTE_TAG,
                    "Route UI state updated estimated=${fetchedRoutePlan?.durationSeconds}, instructions=${fetchedRoutePlan?.instructions?.size}"
                )
            } catch (cancelled: CancellationException) {
                Log.d(ROUTE_TAG, "Route request coroutine cancelled")
                throw cancelled
            } finally {
                isRouteLoading = false
            }
        }
    }

    LaunchedEffect(hasRouteOrigin, destination?.longitude, destination?.latitude, routeRequestToken) {
        requestWalkingRoute()
    }

    LaunchedEffect(sub, routeMatch?.status, routeMatch?.distanceToRouteMeters) {
        val match = routeMatch ?: return@LaunchedEffect
        Log.d(
            ROUTE_TAG,
            "Route match status=${match.status} distance=${match.distanceToRouteMeters}m traveled=${match.traveledDistanceMeters}m remaining=${match.remainingDistanceMeters}m"
        )
        if (sub != RouteSub.Navigation || match.status != RouteDeviationStatus.OFF_ROUTE) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastRerouteAt < REROUTE_COOLDOWN_MS) return@LaunchedEffect
        lastRerouteAt = now
        routeRequestToken++
    }

    val destName = destination?.name.orEmpty()
    val estimatedTime = routePlan?.let { formatDuration(it.durationSeconds) }.orEmpty()

    when (sub) {
        RouteSub.Main -> RouteMainView(
            originName = resolvedOriginName,
            destName = destName,
            estimatedTime = estimatedTime,
            routeMessage = when {
                destination == null -> "목적지를 먼저 선택해주세요."
                destination.longitude == null || destination.latitude == null -> "목적지 좌표가 없어 경로를 만들 수 없습니다."
                !hasLocationPermission -> "현 위치 권한이 필요합니다."
                isRouteLoading -> "도보 경로를 불러오는 중입니다."
                routeMessage.isNotBlank() -> routeMessage
                else -> ""
            },
            onNavigation = {
                sub = RouteSub.Navigation
                SafetyWalkService.start(context)   // 길 안내 시작 → 보행 어시스턴트 + TFLite 활성
            },
            onSimple = { sub = RouteSub.Simple },
            onFav = { onTabChange(NavTab.Fav) },
            onTabChange = onTabChange
        )

        RouteSub.Simple -> ExampleRouteGuideView(
            originName = resolvedOriginName,
            destName = destName,
            routePlan = routePlan,
            onDone = { sub = RouteSub.Main },
            onTabChange = { nextTab ->
                sub = RouteSub.Main
                if (nextTab != NavTab.Route) onTabChange(nextTab)
            }
        )

        RouteSub.Navigation -> MapNavView(
            originName = resolvedOriginName,
            destName = destName,
            currentLocation = currentLocation,
            destination = destination,
            routePlan = routePlan,
            routeMatch = routeMatch,
            routeMessage = routeMessage,
            onStop = {
                sub = RouteSub.Main
                // 길 안내 종료 → SafetyScreen 토글이 OFF면 서비스도 종료
                if (!SafetyWalkService.userEnabled) SafetyWalkService.stop(context)
            },
            onTabChange = { nextTab ->
                sub = RouteSub.Main
                if (nextTab != NavTab.Route) onTabChange(nextTab)
            }
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Location.isInKorea(): Boolean =
    latitude in 33.0..39.5 && longitude in 124.0..132.0

private fun Location.isUsableRouteOrigin(): Boolean =
    isInKorea()

private fun Location.isRecentEnough(): Boolean =
    time <= 0L || System.currentTimeMillis() - time <= MAX_LAST_KNOWN_AGE_MS

private fun Location.routeOriginKey(destinationLongitude: Double, destinationLatitude: Double): String =
    "${latitude.roundForRoute()},${longitude.roundForRoute()}:${destinationLatitude.roundForRoute()},${destinationLongitude.roundForRoute()}"

private fun Double.roundForRoute(): String = String.format(Locale.US, "%.6f", this)

@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
    return providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .filter { it.isRecentEnough() && it.isUsableRouteOrigin() }
        .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
}

@Composable
private fun RouteMainView(
    originName: String,
    destName: String,
    estimatedTime: String,
    routeMessage: String,
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
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, top = 56.dp, end = 20.dp, bottom = 22.dp)
        ) {
            Text(
                text = "경로 탐색",
                color = AppWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NavDivider, RoundedCornerShape(12.dp))
                ) {
                    RouteInfoRow("출발지", originName)
                    HorizontalDivider(color = NavDivider, thickness = 1.dp)
                    RouteInfoRow("도착지", destName)
                    HorizontalDivider(color = NavDivider, thickness = 1.dp)
                    RouteInfoRow("예상시간", estimatedTime)
                }
                Spacer(Modifier.height(20.dp))
                CompositionLocalProvider(LocalNavButtonCornerRadius provides 30.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavBtn("길 안내 및 안전 보행 시작", filled = true, big = true, onClick = onNavigation)
                    NavBtn("간편 경로 안내", outlined = true, big = true, onClick = onSimple)
                    NavBtn("즐겨찾기에서 선택", outlined = true, big = true, onClick = onFav)
                    }
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = label,
            color = NavGray,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(86.dp)
        )
        Text(
            text = value,
            color = AppWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExampleRouteGuideView(
    originName: String,
    destName: String,
    routePlan: WalkingRoutePlan?,
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
                    text = "\uacbd\ub85c \uc548\ub0b4",
                    color = AppWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "\uc644\ub8cc",
                color = AppWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDone)
            )
        }
        HorizontalDivider(color = Color(0xFF2B2B2B), thickness = 1.dp)
        if (routePlan == null) {
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                item {
                    StartRouteRow(originName = originName)
                }
                items(routePlan.instructions) { item ->
                    ExampleRouteGuideRow(item = item)
                }
                item {
                    ExampleDestinationRow(destName = destName)
                }
            }
        }
    }
}

@Composable
private fun StartRouteRow(originName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_route_start),
            contentDescription = null,
            tint = NavRed,
            modifier = Modifier.size(width = 55.dp, height = 65.dp)
        )
        Spacer(Modifier.width(28.dp))
        RouteGuideTextColumn(
            title = originName.ifBlank { "\ucd9c\ubc1c\uc9c0" },
            distanceText = null,
            reserveDistanceSlot = false,
            maxTitleLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(
        color = NavYellow,
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun ExampleRouteGuideRow(item: RouteInstruction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.cue.routeIconRes()),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(width = 59.dp, height = 69.dp)
        )
        Spacer(Modifier.width(28.dp))
        RouteGuideTextColumn(
            title = item.title,
            distanceText = formatDistance(item.distanceMeters),
            maxTitleLines = 3,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(
        color = NavYellow,
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun ExampleDestinationRow(destName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_route_destination),
            contentDescription = null,
            tint = NavGreen,
            modifier = Modifier.size(width = 55.dp, height = 65.dp)
        )
        Spacer(Modifier.width(28.dp))
        RouteGuideTextColumn(
            title = destName.ifBlank { "\ub3c4\ucc29\uc9c0" },
            distanceText = null,
            reserveDistanceSlot = false,
            maxTitleLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(
        color = NavYellow,
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun RouteGuideTextColumn(
    title: String,
    distanceText: String?,
    reserveDistanceSlot: Boolean = distanceText != null,
    maxTitleLines: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (reserveDistanceSlot) {
            Text(
                text = distanceText ?: "0m",
                color = if (distanceText == null) Color.Transparent else AppWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = title,
            color = AppWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 30.sp,
            maxLines = maxTitleLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SimpleRouteView(
    destName: String,
    routePlan: WalkingRoutePlan?,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destName,
                    color = AppWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                routePlan?.let {
                    Text(
                        text = "${formatDistance(it.distanceMeters)} · ${formatDuration(it.durationSeconds)}",
                        color = NavGray,
                        fontSize = 16.sp
                    )
                }
            }
        }
        HorizontalDivider(color = NavDivider, thickness = 1.dp)
        if (routePlan == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "경로 정보가 없습니다.", color = AppWhite, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = {
                    items(routePlan.instructions) { item ->
                        RouteGuideRow(item = item)
                        if (false) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(item.cue.routeIconRes()),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp, 54.dp)
                            )
                            Spacer(Modifier.width(26.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = AppWhite,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatDistance(item.distanceMeters)} · ${formatDuration(item.durationSeconds)}",
                                    color = NavLightGray,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        HorizontalDivider(color = NavDivider, thickness = 0.5.dp)
                        }
                    }
                    item {
                        DestinationGuideRow(destName = destName)
                    }
                }
            )
        }
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}

@Composable
private fun RouteGuideRow(item: RouteInstruction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .height(226.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.cue.routeIconRes()),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(width = 96.dp, height = 116.dp)
        )
        Spacer(Modifier.width(34.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDistance(item.distanceMeters),
                color = AppWhite,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = item.title,
                color = AppWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 43.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(
        color = NavYellow,
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun DestinationGuideRow(destName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .height(172.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_route_destination),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(width = 96.dp, height = 116.dp)
        )
        Spacer(Modifier.width(34.dp))
        Text(
            text = destName.ifBlank { "\ub3c4\ucc29\uc9c0" },
            color = AppWhite,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(
        color = NavYellow,
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun MapNavView(
    originName: String,
    destName: String,
    currentLocation: Location?,
    destination: RouteDestination?,
    routePlan: WalkingRoutePlan?,
    routeMatch: RouteMatchResult?,
    routeMessage: String,
    onStop: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    var currentFocusRequest by remember { mutableStateOf(0) }
    var currentStepIndex by remember(routePlan) { mutableStateOf(0) }
    val routeSteps = remember(routePlan, originName, destName) {
        routePlan.toNavigationSteps(originName, destName)
    }

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
            RouteMapView(
                currentLocation = currentLocation,
                destination = destination,
                routePoints = routePlan?.points.orEmpty(),
                routeMatch = routeMatch,
                currentFocusRequest = currentFocusRequest,
                modifier = Modifier.fillMaxSize()
            )
            NavigationRouteHeader(
                destName = destName,
                steps = routeSteps,
                currentStepIndex = currentStepIndex,
                onStepChange = { currentStepIndex = it },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .background(NavBg)
                .padding(start = 42.dp, top = 13.dp, end = 40.dp, bottom = 40.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { currentFocusRequest++ }
                        .background(NavYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_searching),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "현 위치 확인",
                    color = AppWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(51.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(NavYellow)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "종료",
                    color = Color(0xFF121212),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NavigationRouteHeader(
    destName: String,
    steps: List<NavigationStep>,
    currentStepIndex: Int,
    onStepChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeSteps = steps.ifEmpty {
        listOf(NavigationStep(title = destName, subtitle = "\uacbd\ub85c \ubd88\ub7ec\uc624\ub294 \uc911"))
    }
    val safeStepIndex = currentStepIndex.coerceIn(0, safeSteps.lastIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(185.dp)
            .background(NavBg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .pointerInput(safeSteps, currentStepIndex) {
                var dragAmount = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { _, amount -> dragAmount += amount },
                    onDragEnd = {
                        when {
                            dragAmount < -48f && currentStepIndex < safeSteps.lastIndex ->
                                onStepChange(currentStepIndex + 1)
                            dragAmount > 48f && currentStepIndex > 0 ->
                                onStepChange(currentStepIndex - 1)
                        }
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = safeStepIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally(animationSpec = tween(durationMillis = 260)) { width ->
                        direction * width
                    } + fadeIn(animationSpec = tween(durationMillis = 160))
                    ).togetherWith(
                    slideOutHorizontally(animationSpec = tween(durationMillis = 260)) { width ->
                        -direction * width
                    } + fadeOut(animationSpec = tween(durationMillis = 160))
                ).using(SizeTransform(clip = false))
            },
            label = "NavigationStepSlide"
        ) { stepIndex ->
            NavigationStepCard(step = safeSteps[stepIndex])
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            safeSteps.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStepIndex) 8.dp else 7.dp)
                        .background(
                            color = if (index == currentStepIndex) AppWhite else Color(0xFF5E5E5E),
                            shape = CircleShape
                        )
                )
                if (index != safeSteps.lastIndex) {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
private fun NavigationStepCard(step: NavigationStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(step.cue.routeIconRes()),
            contentDescription = null,
            tint = when (step.cue) {
                DirectionCue.START -> NavRed
                DirectionCue.DESTINATION -> NavGreen
                else -> Color.Unspecified
            },
            modifier = Modifier.size(
                width = if (step.cue == DirectionCue.START || step.cue == DirectionCue.DESTINATION) 55.dp else 59.dp,
                height = if (step.cue == DirectionCue.START || step.cue == DirectionCue.DESTINATION) 65.dp else 69.dp
            )
        )
        Spacer(Modifier.width(28.dp))
        RouteGuideTextColumn(
            title = step.title,
            distanceText = if (step.cue == DirectionCue.START || step.cue == DirectionCue.DESTINATION) {
                null
            } else {
                formatDistance(step.distanceMeters)
            },
            reserveDistanceSlot = step.cue != DirectionCue.START && step.cue != DirectionCue.DESTINATION,
            maxTitleLines = if (step.cue == DirectionCue.START || step.cue == DirectionCue.DESTINATION) 2 else 3,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class NavigationStep(
    val title: String,
    val subtitle: String = "",
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    val cue: DirectionCue = DirectionCue.STRAIGHT
)

private class MapViewHolder {
    var view: MapView? = null
}

private fun WalkingRoutePlan?.toNavigationSteps(originName: String, destName: String): List<NavigationStep> {
    if (this == null) return emptyList()
    return buildList {
        add(
            NavigationStep(
                title = originName.ifBlank { "\ucd9c\ubc1c\uc9c0" },
                subtitle = "\ucd1d ${formatDistance(distanceMeters)} / ${formatDuration(durationSeconds)}",
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                cue = DirectionCue.START
            )
        )
        instructions.forEach { instruction ->
            add(
                NavigationStep(
                    title = instruction.title,
                    subtitle = "${formatDistance(instruction.distanceMeters)} / ${formatDuration(instruction.durationSeconds)}",
                    distanceMeters = instruction.distanceMeters,
                    durationSeconds = instruction.durationSeconds,
                    cue = instruction.cue
                )
            )
        }
        add(
            NavigationStep(
                title = destName.ifBlank { "\ub3c4\ucc29\uc9c0" },
                subtitle = "\ub3c4\ucc29",
                cue = DirectionCue.DESTINATION
            )
        )
    }
}

@Composable
private fun RouteMapView(
    currentLocation: Location?,
    destination: RouteDestination?,
    routePoints: List<RoutePoint>,
    routeMatch: RouteMatchResult?,
    currentFocusRequest: Int,
    modifier: Modifier = Modifier
) {
    if (USE_DUMMY_ROUTE_MAP) {
        DummyRouteMap(
            routePoints = routePoints,
            currentLocation = currentLocation,
            destination = destination,
            modifier = modifier
        )
        return
    }

    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        DummyRouteMap(
            routePoints = routePoints,
            currentLocation = currentLocation,
            destination = destination,
            modifier = modifier
        )
        return
    }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var routeLineLayer by remember { mutableStateOf<RouteLineLayer?>(null) }
    var originLabel by remember { mutableStateOf<Label?>(null) }
    var currentLabel by remember { mutableStateOf<Label?>(null) }
    var destinationLabel by remember { mutableStateOf<Label?>(null) }
    val mapViewHolder = remember { MapViewHolder() }
    var mapError by remember { mutableStateOf("") }
    var lastFocusedRouteKey by remember { mutableStateOf("") }
    var lastDrawnRouteKey by remember { mutableStateOf("") }
    var lastHandledFocusRequest by remember { mutableStateOf(currentFocusRequest) }
    val latestRoutePoints by rememberUpdatedState(routePoints)
    val latestLocation by rememberUpdatedState(currentLocation)
    val latestDestination by rememberUpdatedState(destination)
    val latestRouteMatch by rememberUpdatedState(routeMatch)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = mapViewHolder.view ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { view.resume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { view.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewHolder.view?.let { view -> runCatching { view.pause() } }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    mapViewHolder.view = this
                    start(
                        object : MapLifeCycleCallback() {
                            override fun onMapDestroy() = Unit
                            override fun onMapError(error: Exception) {
                                val msg = error.message ?: error.javaClass.simpleName
                                mapError = msg
                                AppLogger.error("KakaoMap", "onMapError: $msg")
                            }
                        },
                        object : KakaoMapReadyCallback() {
                            override fun onMapReady(map: KakaoMap) {
                                kakaoMap = map
                                routeLineLayer = map.routeLineManager?.layer
                                routeLineLayer?.drawRoute(latestRoutePoints)
                                lastDrawnRouteKey = latestRoutePoints.routeKey()
                                latestRoutePoints.firstOrNull()?.toLatLng()?.let { position ->
                                    originLabel = map.addOrMoveLabel(
                                        label = null,
                                        id = "route-origin",
                                        position = position,
                                        iconRes = R.drawable.ic_route_start
                                    )
                                }
                                val displayPosition = latestRouteMatch?.snappedPoint?.toLatLng()
                                    ?: latestLocation?.let { LatLng.from(it.latitude, it.longitude) }
                                displayPosition?.let { position ->
                                    currentLabel = map.addOrMoveLabel(
                                        label = null,
                                        id = "route-current",
                                        position = position,
                                        iconRes = R.drawable.ic_location_target
                                    )
                                }
                                latestDestination?.toLatLngOrNull()?.let { position ->
                                    destinationLabel = map.addOrMoveLabel(
                                        label = null,
                                        id = "route-destination",
                                        position = position,
                                        iconRes = R.drawable.ic_route_destination
                                    )
                                }
                                map.focusOnRoute(latestRouteMatch?.snappedPoint, latestLocation, latestDestination, latestRoutePoints)
                                lastFocusedRouteKey = latestRoutePoints.routeKey()
                            }

                            override fun getPosition(): LatLng =
                                latestLocation?.let { LatLng.from(it.latitude, it.longitude) }
                                    ?: latestRoutePoints.firstOrNull()?.let { LatLng.from(it.latitude, it.longitude) }
                                    ?: LatLng.from(35.1595, 126.8526)
                        }
                    )
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        post { runCatching { resume() } }
                    }
                }
            },
            update = {
                val routeKey = routePoints.routeKey()
                if (routeKey != lastDrawnRouteKey) {
                    routeLineLayer?.drawRoute(routePoints)
                    lastDrawnRouteKey = routeKey
                }
                val map = kakaoMap
                if (map != null) {
                    routePoints.firstOrNull()?.toLatLng()?.let { position ->
                        originLabel = map.addOrMoveLabel(
                            label = originLabel,
                            id = "route-origin",
                            position = position,
                            iconRes = R.drawable.ic_route_start
                        )
                    }
                    val displayPosition = routeMatch?.snappedPoint?.toLatLng()
                        ?: currentLocation?.let { LatLng.from(it.latitude, it.longitude) }
                    displayPosition?.let { position ->
                        currentLabel = map.addOrMoveLabel(
                            label = currentLabel,
                            id = "route-current",
                            position = position,
                            iconRes = R.drawable.ic_location_target
                        )
                    }
                    destination?.toLatLngOrNull()?.let { position ->
                        destinationLabel = map.addOrMoveLabel(
                            label = destinationLabel,
                            id = "route-destination",
                            position = position,
                            iconRes = R.drawable.ic_route_destination
                        )
                    }
                    if (routeKey.isNotBlank() && routeKey != lastFocusedRouteKey) {
                        map.focusOnRoute(routeMatch?.snappedPoint, currentLocation, destination, routePoints)
                        lastFocusedRouteKey = routeKey
                    }
                    if (currentFocusRequest != lastHandledFocusRequest) {
                        routeMatch?.snappedPoint?.let { map.focusOnCurrentLocation(it) }
                            ?: currentLocation?.let { map.focusOnCurrentLocation(it) }
                        lastHandledFocusRequest = currentFocusRequest
                    }
                }
            }
        )

        if (mapError.isNotBlank()) {
            DummyRouteMap(
                routePoints = routePoints,
                currentLocation = currentLocation,
                destination = destination,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DummyRouteMap(
    routePoints: List<RoutePoint>,
    currentLocation: Location?,
    destination: RouteDestination?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(Color.White)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val roadColor = Color(0xFFE8E8E8)
            val routeColor = Color(0xFF2D67E3)

            repeat(7) { index ->
                val x = size.width * (index + 1) / 8f
                drawLine(
                    color = roadColor,
                    start = Offset(x, 0f),
                    end = Offset(x - size.width * 0.32f, size.height),
                    strokeWidth = 2f
                )
            }
            repeat(5) { index ->
                val y = size.height * (index + 1) / 6f
                drawLine(
                    color = roadColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y + size.height * 0.08f),
                    strokeWidth = 2f
                )
            }

            val start = Offset(size.width * 0.25f, size.height * 0.72f)
            val corner = Offset(size.width * 0.34f, size.height * 0.58f)
            val end = Offset(size.width * 0.88f, size.height * 0.08f)
            drawLine(routeColor, start, corner, strokeWidth = 10f, cap = StrokeCap.Round)
            drawLine(routeColor, corner, end, strokeWidth = 10f, cap = StrokeCap.Round)
            drawLine(Color.White, start, corner, strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(Color.White, corner, end, strokeWidth = 2f, cap = StrokeCap.Round)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(10.dp)
                .background(Color(0xFF259865), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 30.dp)
                .size(10.dp)
                .background(Color(0xFF2D67E3), CircleShape)
        )
    }
}

private fun KakaoMap.addOrMoveLabel(
    label: Label?,
    id: String,
    position: LatLng,
    iconRes: Int
): Label? {
    if (label != null) {
        label.moveTo(position)
        return label
    }

    val style = LabelStyle
        .from(iconRes)
        .setAnchorPoint(0.5f, 0.5f)
    val options = LabelOptions
        .from(id, position)
        .setStyles(style)
    return labelManager?.layer?.addLabel(options)
}

private fun KakaoMap.focusOnRoute(
    snappedLocation: RoutePoint?,
    currentLocation: Location?,
    destination: RouteDestination?,
    points: List<RoutePoint>
) {
    val latLngs = buildList {
        snappedLocation?.let { add(it.toLatLng()) }
            ?: currentLocation?.let { add(LatLng.from(it.latitude, it.longitude)) }
        addAll(points.map { LatLng.from(it.latitude, it.longitude) })
        destination?.toLatLngOrNull()?.let { add(it) }
    }.distinct()

    when (latLngs.size) {
        0 -> Unit
        1 -> moveCamera(CameraUpdateFactory.newCenterPosition(latLngs.first(), 16))
        else -> moveCamera(
            CameraUpdateFactory.fitMapPoints(latLngs.toTypedArray(), 90),
            CameraAnimation.from(350)
        )
    }
}

private fun KakaoMap.focusOnCurrentLocation(location: Location) {
    moveCamera(
        CameraUpdateFactory.newCenterPosition(LatLng.from(location.latitude, location.longitude), 17),
        CameraAnimation.from(250)
    )
}

private fun KakaoMap.focusOnCurrentLocation(point: RoutePoint) {
    moveCamera(
        CameraUpdateFactory.newCenterPosition(point.toLatLng(), 17),
        CameraAnimation.from(250)
    )
}

private fun RouteDestination.toLatLngOrNull(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return LatLng.from(lat, lng)
}

private fun RoutePoint.toLatLng(): LatLng = LatLng.from(latitude, longitude)

private fun List<RoutePoint>.routeKey(): String {
    if (isEmpty()) return ""
    val first = first()
    val middle = this[size / 2]
    val last = last()
    return "$size:${first.latitude},${first.longitude}:${middle.latitude},${middle.longitude}:${last.latitude},${last.longitude}"
}

private fun RouteLineLayer.drawRoute(points: List<RoutePoint>) {
    removeAll()
    val distinctPoints = points.withoutConsecutiveDuplicates()
    if (distinctPoints.size < 2) return
    val latLngs = distinctPoints.map { LatLng.from(it.latitude, it.longitude) }
    val style = RouteLineStyle.from(16f, android.graphics.Color.rgb(45, 103, 227))
    val styles = RouteLineStyles.from(style)
    val stylesSet = RouteLineStylesSet.from(styles)
    val segment = RouteLineSegment.from(latLngs).setStyles(styles)
    val options = RouteLineOptions.from(segment).setStylesSet(stylesSet)
    addRouteLine(options)
}

private fun List<RoutePoint>.withoutConsecutiveDuplicates(): List<RoutePoint> =
    filterIndexed { index, point ->
        index == 0 || point != this[index - 1]
    }

private fun DirectionCue.routeIconRes(): Int =
    when (this) {
        DirectionCue.START -> R.drawable.ic_route_start
        DirectionCue.LEFT -> R.drawable.ic_route_left
        DirectionCue.RIGHT -> R.drawable.ic_route_right
        DirectionCue.DESTINATION -> R.drawable.ic_route_destination
        DirectionCue.STRAIGHT -> R.drawable.ic_route_straight
    }

@Composable
@SuppressLint("MissingPermission")
private fun CurrentLocationEffect(
    enabled: Boolean,
    onLocation: (Location) -> Unit
) {
    val context = LocalContext.current
    val latestOnLocation by rememberUpdatedState(onLocation)

    DisposableEffect(enabled, context) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose { }

        lastKnownLocation(context)?.let(latestOnLocation)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                latestOnLocation(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(provider, 1_500L, 2f, listener)
            }
        }

        onDispose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }
}

