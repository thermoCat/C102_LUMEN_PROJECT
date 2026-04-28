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
import androidx.compose.ui.graphics.Color
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
import com.ssafy.smartcane.map.KakaoMapSupport
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
        val previousOrigin = routeOriginLocation
        if (routePlan != null && previousOrigin != null && previousOrigin.distanceTo(origin) < 25f) {
            return@LaunchedEffect
        }

        if (resolvedOriginName.isBlank()) {
            resolvedOriginName = localSearchService.getAddressName(origin.longitude, origin.latitude)
        }

        isRouteLoading = true
        routeMessage = ""
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 56.dp, end = 20.dp, bottom = 22.dp)
        ) {
            Text(
                text = "경로 탐색",
                color = AppWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                Spacer(Modifier.height(40.dp))
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
                Spacer(Modifier.height(40.dp))
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = label,
            color = NavYellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(68.dp)
        )
        Text(
            text = value,
            color = AppWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
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
    routePlan: WalkingRoutePlan?,
    routeMessage: String,
    onStop: () -> Unit,
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
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_route_destination),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(38.dp, 52.dp)
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = destName,
                color = AppWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        RouteMapView(
            currentLocation = currentLocation,
            routePoints = routePlan?.points.orEmpty(),
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
                .background(NavBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(NavYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_target),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "현 위치 확인",
                    color = AppWhite,
                    fontSize = 12.sp
                )
            }
            NavBtn(
                label = "종료",
                filled = true,
                big = true,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
        }
        BottomNav(active = NavTab.Route, onTab = onTabChange)
    }
}

@Composable
private fun RouteMapView(
    currentLocation: Location?,
    routePoints: List<RoutePoint>,
    modifier: Modifier = Modifier
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(0.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            Text("KAKAO_NATIVE_APP_KEY가 필요합니다.", color = AppWhite, fontSize = 16.sp)
        }
        return
    }

    if (!KakaoMapSupport.isSupportedDevice()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(0.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            Text("이 에뮬레이터 ABI에서는 카카오맵 SDK를 실행할 수 없습니다.", color = AppWhite, fontSize = 16.sp)
        }
        return
    }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var routeLineLayer by remember { mutableStateOf<RouteLineLayer?>(null) }
    var currentLabel by remember { mutableStateOf<Label?>(null) }
    var mapError by remember { mutableStateOf("") }
    val latestRoutePoints by rememberUpdatedState(routePoints)
    val latestLocation by rememberUpdatedState(currentLocation)

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
                                    currentLabel = map.addOrMoveCurrentLabel(
                                        label = null,
                                        location = location
                                    )
                                }
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
                val location = currentLocation
                if (map != null && location != null) {
                    currentLabel = map.addOrMoveCurrentLabel(currentLabel, location)
                }
            }
        )

        if (mapError.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC262626)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "카카오맵을 불러올 수 없습니다.\n$mapError",
                    color = AppWhite,
                    fontSize = 16.sp
                )
            }
        }
    }
}

private fun KakaoMap.addOrMoveCurrentLabel(label: Label?, location: Location): Label {
    val position = LatLng.from(location.latitude, location.longitude)
    if (label != null) {
        label.moveTo(position)
        return label
    }

    val style = LabelStyle
        .from(R.drawable.ic_location_target)
        .setAnchorPoint(0.5f, 0.5f)
    val options = LabelOptions
        .from("current-location", position)
        .setStyles(style)
    return requireNotNull(labelManager?.layer?.addLabel(options))
}

private fun RouteLineLayer.drawRoute(points: List<RoutePoint>) {
    if (points.size < 2) return
    removeAll()
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
