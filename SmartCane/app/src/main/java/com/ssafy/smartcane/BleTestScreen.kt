package com.ssafy.smartcane

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ssafy.smartcane.ble.BleNusManager
import com.ssafy.smartcane.detection.TFLiteRunner
import com.ssafy.smartcane.network.HazardApiService
import com.ssafy.smartcane.network.LocationApiService
import com.ssafy.smartcane.ui.theme.AppWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BleTestScreen(bleManager: BleNusManager, onOpenHazardCam: (String) -> Unit = {}) {
    val context   = LocalContext.current
    val connState by bleManager.connectionState.collectAsState()
    val connName  by bleManager.connectedName.collectAsState()
    val latestLog by bleManager.log.collectAsState()
    val scope     = rememberCoroutineScope()

    var isReporting      by remember { mutableStateOf(false) }
    var isTracking       by remember { mutableStateOf(false) }
    var trackingJob      by remember { mutableStateOf<Job?>(null) }
    var locationListener by remember { mutableStateOf<LocationListener?>(null) }
    var lastLocation     by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val deviceId         = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    val logHistory  = remember { mutableStateListOf<String>() }
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            trackingJob?.cancel()
            locationListener?.let {
                runCatching {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    lm?.removeUpdates(it)
                }
            }
            locationListener = null
        }
    }

    androidx.compose.runtime.LaunchedEffect(latestLog) {
        if (latestLog.isNotBlank()) {
            logHistory.add(latestLog)
            if (logHistory.size > 30) logHistory.removeAt(0)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }.toTypedArray()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) bleManager.connect()
    }

    fun connectWithPermCheck() {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) bleManager.connect() else permLauncher.launch(permissions)
    }

    val isConnected = connState == BleNusManager.ConnectionState.CONNECTED

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A2E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 제목 ──────────────────────────────────────────
            Text(
                text = "앱 테스트 대쉬보드",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppWhite
            )

            Spacer(Modifier.height(12.dp))

            // ── 연결 상태 배지 ────────────────────────────────
            val (stateText, stateColor) = when (connState) {
                BleNusManager.ConnectionState.CONNECTED    -> "연결됨: $connName" to Color(0xFF4CAF50)
                BleNusManager.ConnectionState.CONNECTING   -> "연결 중..." to Color(0xFFFFB300)
                BleNusManager.ConnectionState.SCANNING     -> "스캔 중..." to Color(0xFF29B6F6)
                BleNusManager.ConnectionState.DISCONNECTED -> "미연결" to Color(0xFFEF5350)
            }
            Box(
                modifier = Modifier
                    .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(text = stateText, color = stateColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(16.dp))

            // ── 탭 ───────────────────────────────────────────
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D0D1A),
                contentColor = AppWhite,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab, matchContentSize = false),
                        color = Color(0xFF29B6F6)
                    )
                }
            ) {
                listOf("제어", "로그").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color(0xFF29B6F6) else Color(0xFF90A4AE)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 탭 콘텐츠 ─────────────────────────────────────
            when (selectedTab) {
                0 -> ControlTab(
                    isConnected = isConnected,
                    connState = connState,
                    context = context,
                    bleManager = bleManager,
                    isTracking = isTracking,
                    isReporting = isReporting,
                    logHistory = logHistory,
                    scope = scope,
                    deviceId = deviceId,
                    lastLocation = lastLocation,
                    onConnect = { connectWithPermCheck() },
                    onDisconnect = { bleManager.disconnect() },
                    onTrackingToggle = { starting ->
                        if (!starting) {
                            trackingJob?.cancel(); trackingJob = null; isTracking = false
                            locationListener?.let { listener ->
                                runCatching {
                                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                    lm?.removeUpdates(listener)
                                }
                            }
                            locationListener = null; lastLocation = null
                            logHistory.add("위치 추적 중지")
                            scope.launch {
                                val result = LocationApiService.sendStop(deviceId)
                                logHistory.add(if (result.isSuccess) "중지 전송 완료 (deviceId=$deviceId)" else "중지 전송 실패: ${result.exceptionOrNull()?.message}")
                            }
                        } else {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!fineGranted && !coarseGranted) {
                                logHistory.add("위치 권한 필요 - 설정에서 허용 후 다시 시도")
                                return@ControlTab
                            }
                            isTracking = true
                            logHistory.add("위치 추적 시작 (실시간 GPS, 3초 간격)")
                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                            if (lm == null) {
                                logHistory.add("LocationManager 사용 불가"); isTracking = false; return@ControlTab
                            }
                            val listener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    lastLocation = Pair(location.latitude, location.longitude)
                                }
                                override fun onProviderEnabled(provider: String) {}
                                override fun onProviderDisabled(provider: String) {}
                                @Suppress("DEPRECATION")
                                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            }
                            locationListener = listener
                            val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
                            if (providers.isEmpty()) {
                                logHistory.add("활성화된 위치 provider 없음 - 위치 서비스 ON 확인")
                                isTracking = false; locationListener = null; return@ControlTab
                            }
                            runCatching {
                                providers.forEach { provider ->
                                    @SuppressLint("MissingPermission")
                                    lm.requestLocationUpdates(provider, 1000L, 1f, listener, Looper.getMainLooper())
                                }
                            }.onFailure { logHistory.add("GPS listener 등록 실패: ${it.message}") }
                            lastLocation = getLastKnownLocation(context)
                            trackingJob = scope.launch {
                                while (true) {
                                    val loc = lastLocation
                                    if (loc != null) {
                                        val result = LocationApiService.sendLocation(deviceId, loc.first, loc.second)
                                        logHistory.add(if (result.isSuccess) "위치 전송 lat=${loc.first}, lng=${loc.second}" else "전송 실패: ${result.exceptionOrNull()?.message}")
                                    } else {
                                        logHistory.add("GPS null - 위치 수신 대기 중")
                                    }
                                    if (logHistory.size > 30) logHistory.removeAt(0)
                                    delay(3000)
                                }
                            }
                        }
                    },
                    onReport = {
                        scope.launch {
                            isReporting = true
                            val loc = getLastKnownLocation(context)
                            if (loc == null) {
                                logHistory.add("GPS 위치를 가져올 수 없습니다. 위치 권한을 확인하세요.")
                            } else {
                                logHistory.add("신고 중... lat=${loc.first}, lng=${loc.second}")
                                val result = HazardApiService.reportHazard(lat = loc.first, lng = loc.second)
                                logHistory.add(if (result.success) "[성공] ${result.message}" else "[실패] ${result.message}")
                            }
                            if (logHistory.size > 30) logHistory.removeAt(0)
                            isReporting = false
                        }
                    },
                    onOpenModel = onOpenHazardCam
                )
                1 -> LogTab(logHistory = logHistory, scrollState = scrollState)
            }
        }
    }
}

@Composable
private fun ControlTab(
    isConnected: Boolean,
    connState: BleNusManager.ConnectionState,
    context: Context,
    bleManager: BleNusManager,
    isTracking: Boolean,
    isReporting: Boolean,
    logHistory: MutableList<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    deviceId: String,
    lastLocation: Pair<Double, Double>?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTrackingToggle: (Boolean) -> Unit,
    onReport: () -> Unit,
    onOpenModel: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 연결 / 해제 ────────────────────────────────────
        SectionLabel("연결")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                enabled = connState == BleNusManager.ConnectionState.DISCONNECTED,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) { Text("자동 연결") }
            Button(
                onClick = onDisconnect,
                modifier = Modifier.weight(1f),
                enabled = connState != BleNusManager.ConnectionState.DISCONNECTED,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) { Text("연결 해제") }
        }

        Spacer(Modifier.height(20.dp))

        // ── 진동 명령 ──────────────────────────────────────
        SectionLabel("진동 명령")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CmdButton("LEFT\n(L)",  Color(0xFFFF6B6B), Modifier.weight(1f), isConnected) { bleManager.sendCommand("L") }
            CmdButton("RIGHT\n(R)", Color(0xFF6BAAFF), Modifier.weight(1f), isConnected) { bleManager.sendCommand("R") }
            CmdButton("BOTH\n(B)",  Color(0xFFFFD700), Modifier.weight(1f), isConnected) { bleManager.sendCommand("B") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CmdButton("OFF\n(O)",  Color(0xFF90A4AE), Modifier.weight(1f), isConnected) { bleManager.sendCommand("O") }
            CmdButton("PING\n(P)", Color(0xFF80CBC4), Modifier.weight(1f), isConnected) { bleManager.sendCommand("P") }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // ── 임시 테스트 ────────────────────────────────────
        SectionLabel("임시 테스트")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { bleManager.sendCommand("G") },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF43A047),
                    disabledContainerColor = Color(0xFF43A047).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("진동 ON\n(G)", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 16.sp)
            }
            Button(
                onClick = { bleManager.sendCommand("O") },
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    disabledContainerColor = Color(0xFFE53935).copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("진동 OFF\n(O)", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 실시간 위치 추적 ──────────────────────────────
        SectionLabel("실시간 위치 추적")
        Button(
            onClick = { onTrackingToggle(!isTracking) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color(0xFF1565C0) else Color(0xFF0D47A1),
                disabledContainerColor = Color(0xFF0D47A1).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isTracking) "위치 추적 중... (탭하여 중지)" else "위치 추적 시작",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── 위험구간 신고 ──────────────────────────────────
        SectionLabel("위험구간 신고")
        Button(
            onClick = onReport,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isReporting,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE65100),
                disabledContainerColor = Color(0xFFE65100).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isReporting) "신고 중..." else "현재 위치 위험구간 신고",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── AI 위험 감지 (기존 모델 — 자동 신고) ─────────────
        Button(
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        context,
                        com.ssafy.smartcane.segformer.SegFormerActivity::class.java
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("SegFormer + Detection", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
        }

        Spacer(Modifier.height(8.dp))

        // ── AI 위험 감지 (동일 모델 테스트 — 신고 없음) ──────
        SectionLabel("모델 테스트 (신고 없음 · bbox만)")
        Button(
            onClick = { onOpenModel("test:${TFLiteRunner.DEFAULT_MODEL_FILE_NAME}") },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("yolo11n_fine_tune 테스트", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppWhite)
        }

        Spacer(Modifier.height(12.dp))

        // ── 안전보행 테스트 (Lumen2) ──────────────────────
        Button(
            onClick = {
                val intent = android.content.Intent(
                    context,
                    com.ssafy.smartcane.lumen2.SafetyWalkActivity::class.java
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("안전보행 테스트", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
        }

        Spacer(Modifier.height(12.dp))

        // ── 앱 로그 확인 ──────────────────────────────────
        Button(
            onClick = {
                val logs = com.ssafy.smartcane.util.AppLogger.entries
                logHistory.clear()
                if (logs.isEmpty()) {
                    logHistory.add("── 앱 로그 없음 ──")
                } else {
                    logHistory.add("── 앱 로그 ${logs.size}개 ──")
                    logs.takeLast(50).forEach { logHistory.add(it) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("앱 로그 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppWhite)
        }

        Spacer(Modifier.height(4.dp))

        // ── 앱 로그 초기화 ────────────────────────────────
        Button(
            onClick = {
                com.ssafy.smartcane.util.AppLogger.clear()
                logHistory.clear()
                logHistory.add("── 앱 로그 초기화 완료 ──")
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E342E)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("앱 로그 초기화", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppWhite)
        }

        Spacer(Modifier.height(8.dp))

        // ── 카카오 검색 진단 ──────────────────────────────
        var kakaoTesting by remember { mutableStateOf(false) }
        Button(
            onClick = {
                scope.launch {
                    kakaoTesting = true
                    logHistory.add("── 카카오 검색 진단 시작 ──")
                    val key = com.ssafy.smartcane.BuildConfig.KAKAO_REST_API_KEY.trim()
                    logHistory.add("BuildConfig KEY: ${if (key.isEmpty()) "비어있음 ❌" else "${key.take(6)}… (${key.length}자) ✅"}")
                    if (key.isNotEmpty()) {
                        logHistory.add("HTTP 직접 요청 중...")
                        val rawResult = runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val client = okhttp3.OkHttpClient()
                                val request = okhttp3.Request.Builder()
                                    .url("https://dapi.kakao.com/v2/local/search/keyword.json?query=스타벅스&size=1")
                                    .addHeader("Authorization", "KakaoAK $key")
                                    .build()
                                client.newCall(request).execute().use { resp ->
                                    val code = resp.code
                                    val body = resp.body?.string().orEmpty().take(200)
                                    "$code | $body"
                                }
                            }
                        }
                        rawResult.onSuccess { msg -> logHistory.add("응답: $msg") }
                        rawResult.onFailure { e -> logHistory.add("예외: ${e.javaClass.simpleName}: ${e.message}") }
                    }
                    logHistory.add("── 진단 완료 ──")
                    kakaoTesting = false
                    while (logHistory.size > 30) logHistory.removeAt(0)
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !kakaoTesting,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                if (kakaoTesting) "진단 중..." else "카카오맵 검색 진단",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppWhite
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── 저장된 기기 삭제 ───────────────────────────────
        OutlinedButton(
            onClick = { bleManager.clearSavedDeviceId() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF78909C))
        ) { Text("저장된 기기 삭제") }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LogTab(
    logHistory: List<String>,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${logHistory.size}개의 로그.",
                fontSize = 12.sp,
                color = Color(0xFF90A4AE)
            )
            Text(
                text = "최대 30줄",
                fontSize = 12.sp,
                color = Color(0xFF546E7A)
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D1A), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            if (logHistory.isEmpty()) {
                Text(
                    text = "로그가 없습니다.",
                    color = Color(0xFF546E7A),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    logHistory.forEach { line ->
                        Text(
                            text = "> $line",
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = Color(0xFF90A4AE),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
    val location = providers
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .minByOrNull { it.accuracy }
    return location?.let { Pair(it.latitude, it.longitude) }
}

@Composable
private fun CmdButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.85f),
            disabledContainerColor = color.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            color = if (enabled) AppWhite else AppWhite.copy(alpha = 0.4f)
        )
    }
}
