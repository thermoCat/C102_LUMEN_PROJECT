package com.ssafy.smartcane.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import com.ssafy.smartcane.network.KakaoLocalSearchService
import com.ssafy.smartcane.network.RoutePoint
import com.ssafy.smartcane.network.WalkingDirectionsService
import com.ssafy.smartcane.network.WalkingRoutePlan
import com.ssafy.smartcane.network.formatDistance
import com.ssafy.smartcane.network.formatDuration
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.component.NavBtn
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavDivider
import com.ssafy.smartcane.ui.theme.NavGray
import com.ssafy.smartcane.ui.theme.NavLightGray
import com.ssafy.smartcane.ui.theme.NavYellow

private enum class RouteSub { Main, Simple, Navigation }
private const val USE_DUMMY_ROUTE_MAP = true

@Composable
fun RouteScreen(
    destination: RouteDestination?,
    originName: String,
    onTabChange: (NavTab) -> Unit
) {
    val context = LocalContext.current
    val directionsService = remember { WalkingDirectionsService() }
    val localSearchService = remember { KakaoLocalSearchService() }
    var sub by remember { mutableStateOf(RouteSub.Main) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var resolvedOriginName by remember { mutableStateOf(originName) }
    var routePlan by remember { mutableStateOf<WalkingRoutePlan?>(null) }
    var routeOriginLocation by remember { mutableStateOf<Location?>(null) }
    var routeDestinationKey by remember { mutableStateOf<String?>(null) }
    var isRouteLoading by remember { mutableStateOf(false) }
    var routeMessage by remember { mutableStateOf("") }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }

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
        onLocation = { currentLocation = it }
    )

    LaunchedEffect(currentLocation, destination?.longitude, destination?.latitude) {
        val origin = currentLocation ?: return@LaunchedEffect
        val destLongitude = destination?.longitude ?: return@LaunchedEffect
        val destLatitude = destination.latitude ?: return@LaunchedEffect
        val destinationKey = "$destLongitude,$destLatitude"
        val previousOrigin = routeOriginLocation
        if (
            routePlan != null &&
            previousOrigin != null &&
            routeDestinationKey == destinationKey &&
            previousOrigin.distanceTo(origin) < 25f
        ) {
            return@LaunchedEffect
        }

        if (resolvedOriginName.isBlank()) {
            resolvedOriginName = localSearchService.getAddressName(origin.longitude, origin.latitude)
        }

        isRouteLoading = true
        routeMessage = ""
        routePlan = null
        routePlan = directionsService.getWalkingRoute(
            originLongitude = origin.longitude,
            originLatitude = origin.latitude,
            destinationLongitude = destLongitude,
            destinationLatitude = destLatitude
        )
        if (routePlan == null) {
            routeMessage = "도보 경로를 불러올 수 없습니다."
        } else {
            routeOriginLocation = origin
            routeDestinationKey = destinationKey
        }
        isRouteLoading = false
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
            onNavigation = { sub = RouteSub.Navigation },
            onSimple = { sub = RouteSub.Simple },
            onFav = { onTabChange(NavTab.Fav) },
            onTabChange = onTabChange
        )

        RouteSub.Simple -> SimpleRouteView(
            destName = destName,
            routePlan = routePlan,
            onDone = { sub = RouteSub.Main },
            onTabChange = { nextTab ->
                sub = RouteSub.Main
                if (nextTab != NavTab.Route) onTabChange(nextTab)
            }
        )

        RouteSub.Navigation -> MapNavView(
            destName = destName,
            currentLocation = currentLocation,
            destination = destination,
            routePlan = routePlan,
            routeMessage = routeMessage,
            onStop = { sub = RouteSub.Main },
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
        .maxByOrNull { it.time }
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
                        .border(1.dp, NavDivider, RoundedCornerShape(14.dp))
                ) {
                    RouteInfoRow("출발지", originName)
                    HorizontalDivider(color = NavDivider, thickness = 1.dp)
                    RouteInfoRow("도착지", destName)
                    HorizontalDivider(color = NavDivider, thickness = 1.dp)
                    RouteInfoRow("예상시간", estimatedTime)
                }
                if (routeMessage.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = routeMessage,
                        color = NavLightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavBtn("길 안내 및 안전 보행 시작", filled = true, big = true, onClick = onNavigation)
                    NavBtn("간편 경로 안내", outlined = true, big = true, onClick = onSimple)
                    NavBtn("즐겨찾기에서 선택", outlined = true, big = true, onClick = onFav)
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
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(86.dp)
        )
        Text(
            text = value,
            color = NavGray,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_route_straight),
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
            )
        }
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}

@Composable
private fun MapNavView(
    destName: String,
    currentLocation: Location?,
    destination: RouteDestination?,
    routePlan: WalkingRoutePlan?,
    routeMessage: String,
    onStop: () -> Unit,
    onTabChange: (NavTab) -> Unit
) {
    var currentFocusRequest by remember { mutableStateOf(0) }
    val stepCount = routePlan?.instructions?.size?.coerceIn(1, 10) ?: 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
    ) {
        NavigationRouteHeader(destName = destName, stepCount = stepCount)
        RouteMapView(
            currentLocation = currentLocation,
            destination = destination,
            routePoints = routePlan?.points.orEmpty(),
            currentFocusRequest = currentFocusRequest,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        if (routeMessage.isNotBlank()) {
            Text(
                text = routeMessage,
                color = NavLightGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
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
    stepCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(161.dp)
            .background(NavBg)
            .padding(start = 42.dp, top = 54.dp, end = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_route_destination),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(width = 36.dp, height = 50.dp)
            )
            Spacer(Modifier.width(42.dp))
            Text(
                text = destName,
                color = AppWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.padding(start = 43.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == 0) 8.dp else 7.dp)
                        .background(
                            color = if (index == 0) AppWhite else Color(0xFF5E5E5E),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun RouteMapView(
    currentLocation: Location?,
    destination: RouteDestination?,
    routePoints: List<RoutePoint>,
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
    var destinationLabel by remember { mutableStateOf<Label?>(null) }
    var mapError by remember { mutableStateOf("") }
    var lastFocusedRouteKey by remember { mutableStateOf("") }
    var lastHandledFocusRequest by remember { mutableStateOf(currentFocusRequest) }
    val latestRoutePoints by rememberUpdatedState(routePoints)
    val latestLocation by rememberUpdatedState(currentLocation)
    val latestDestination by rememberUpdatedState(destination)

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    start(
                        object : MapLifeCycleCallback() {
                            override fun onMapDestroy() = Unit
                            override fun onMapError(error: Exception) {
                                mapError = error.message ?: error.javaClass.simpleName
                            }
                        },
                        object : KakaoMapReadyCallback() {
                            override fun onMapReady(map: KakaoMap) {
                                kakaoMap = map
                                routeLineLayer = map.routeLineManager?.layer
                                routeLineLayer?.drawRoute(latestRoutePoints)
                                latestLocation?.let { location ->
                                    originLabel = map.addOrMoveLabel(
                                        label = null,
                                        id = "route-origin",
                                        position = LatLng.from(location.latitude, location.longitude),
                                        iconRes = R.drawable.ic_route_start
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
                                map.focusOnRoute(latestLocation, latestDestination, latestRoutePoints)
                                lastFocusedRouteKey = latestRoutePoints.routeKey()
                            }

                            override fun getPosition(): LatLng =
                                latestLocation?.let { LatLng.from(it.latitude, it.longitude) }
                                    ?: latestRoutePoints.firstOrNull()?.let { LatLng.from(it.latitude, it.longitude) }
                                    ?: LatLng.from(35.1595, 126.8526)
                        }
                    )
                }
            },
            update = {
                routeLineLayer?.drawRoute(routePoints)
                val map = kakaoMap
                if (map != null) {
                    currentLocation?.let { location ->
                        originLabel = map.addOrMoveLabel(
                            label = originLabel,
                            id = "route-origin",
                            position = LatLng.from(location.latitude, location.longitude),
                            iconRes = R.drawable.ic_route_start
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
                    val routeKey = routePoints.routeKey()
                    if (routeKey.isNotBlank() && routeKey != lastFocusedRouteKey) {
                        map.focusOnRoute(currentLocation, destination, routePoints)
                        lastFocusedRouteKey = routeKey
                    }
                    if (currentFocusRequest != lastHandledFocusRequest) {
                        currentLocation?.let { map.focusOnCurrentLocation(it) }
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
    currentLocation: Location?,
    destination: RouteDestination?,
    points: List<RoutePoint>
) {
    val latLngs = buildList {
        currentLocation?.let { add(LatLng.from(it.latitude, it.longitude)) }
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

private fun RouteDestination.toLatLngOrNull(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return LatLng.from(lat, lng)
}

private fun List<RoutePoint>.routeKey(): String {
    if (isEmpty()) return ""
    val first = first()
    val last = last()
    return "$size:${first.latitude},${first.longitude}:${last.latitude},${last.longitude}"
}

private fun RouteLineLayer.drawRoute(points: List<RoutePoint>) {
    removeAll()
    if (points.size < 2) return
    val latLngs = points.map { LatLng.from(it.latitude, it.longitude) }
    val style = RouteLineStyle.from(16f, android.graphics.Color.rgb(45, 103, 227))
    val styles = RouteLineStyles.from(style)
    val stylesSet = RouteLineStylesSet.from(styles)
    val segment = RouteLineSegment.from(latLngs).setStyles(styles)
    val options = RouteLineOptions.from(segment).setStylesSet(stylesSet)
    addRouteLine(options)
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

