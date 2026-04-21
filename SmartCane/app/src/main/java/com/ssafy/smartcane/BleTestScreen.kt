package com.ssafy.smartcane

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ssafy.smartcane.ble.BleNusManager

@Composable
fun BleTestScreen(bleManager: BleNusManager) {
    val context      = LocalContext.current
    val connState    by bleManager.connectionState.collectAsState()
    val connName     by bleManager.connectedName.collectAsState()
    val latestLog    by bleManager.log.collectAsState()

    // 로그 히스토리 (최대 30줄)
    val logHistory = remember { mutableStateListOf<String>() }
    val scrollState = rememberScrollState()

    // latestLog 변화 감지 → 히스토리에 추가
    androidx.compose.runtime.LaunchedEffect(latestLog) {
        if (latestLog.isNotBlank()) {
            logHistory.add(latestLog)
            if (logHistory.size > 30) logHistory.removeAt(0)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // ── 권한 요청 ──────────────────────────────────────────────
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
        if (allGranted) bleManager.connect()
        else permLauncher.launch(permissions)
    }

    // ── UI ─────────────────────────────────────────────────────
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A2E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 제목
            Text(
                text = "ESP32 BLE 진동 테스트",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(12.dp))

            // 연결 상태 배지
            val (stateText, stateColor) = when (connState) {
                BleNusManager.ConnectionState.CONNECTED    ->
                    ("연결됨: $connName" to Color(0xFF4CAF50))
                BleNusManager.ConnectionState.CONNECTING  ->
                    ("연결 중..." to Color(0xFFFFB300))
                BleNusManager.ConnectionState.SCANNING    ->
                    ("스캔 중..." to Color(0xFF29B6F6))
                BleNusManager.ConnectionState.DISCONNECTED ->
                    ("미연결" to Color(0xFFEF5350))
            }

            Box(
                modifier = Modifier
                    .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(text = stateText, color = stateColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))

            // ── 연결 / 해제 버튼 ─────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { connectWithPermCheck() },
                    modifier = Modifier.weight(1f),
                    enabled = connState == BleNusManager.ConnectionState.DISCONNECTED,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("자동 연결")
                }
                Button(
                    onClick = { bleManager.disconnect() },
                    modifier = Modifier.weight(1f),
                    enabled = connState != BleNusManager.ConnectionState.DISCONNECTED,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Text("연결 해제")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 진동 명령 버튼 ────────────────────────────────
            Text(
                text = "진동 명령",
                fontSize = 13.sp,
                color = Color(0xFF90A4AE),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            val isConnected = connState == BleNusManager.ConnectionState.CONNECTED

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CmdButton("LEFT\n(L)", Color(0xFFFF6B6B), Modifier.weight(1f), isConnected) {
                    bleManager.sendCommand("L")
                }
                CmdButton("RIGHT\n(R)", Color(0xFF6BAAFF), Modifier.weight(1f), isConnected) {
                    bleManager.sendCommand("R")
                }
                CmdButton("BOTH\n(B)", Color(0xFFFFD700), Modifier.weight(1f), isConnected) {
                    bleManager.sendCommand("B")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CmdButton("OFF\n(O)", Color(0xFF90A4AE), Modifier.weight(1f), isConnected) {
                    bleManager.sendCommand("O")
                }
                CmdButton("PING\n(P)", Color(0xFF80CBC4), Modifier.weight(1f), isConnected) {
                    bleManager.sendCommand("P")
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            // ── 임시 테스트 버튼 (G=진동ON / O=진동OFF) ───────
            Text(
                text = "임시 테스트",
                fontSize = 13.sp,
                color = Color(0xFF90A4AE),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

            // ── 로그 영역 ────────────────────────────────────
            Text(
                text = "로그",
                fontSize = 13.sp,
                color = Color(0xFF90A4AE),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D0D1A), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(scrollState)
                ) {
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

            Spacer(Modifier.height(12.dp))

            // 저장된 기기 삭제
            OutlinedButton(
                onClick = { bleManager.clearSavedDeviceId() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF78909C))
            ) {
                Text("저장된 기기 삭제")
            }
        }
    }
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
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
        )
    }
}
