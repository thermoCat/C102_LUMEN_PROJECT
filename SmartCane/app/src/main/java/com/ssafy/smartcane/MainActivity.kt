package com.ssafy.smartcane

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ssafy.smartcane.ble.BleNusManager
import com.ssafy.smartcane.detection.HazardDetectionScreen
import com.ssafy.smartcane.ui.NavigationScreen
import com.ssafy.smartcane.ui.theme.SmartCaneTheme
import com.ssafy.smartcane.viewmodel.NavigationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var bleNusManager: BleNusManager
    private var tts: TextToSpeech? = null

    private val blePermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) bleNusManager.connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val navigationViewModel = ViewModelProvider(this)[NavigationViewModel::class.java]
        bleNusManager = (application as SmartCaneApplication).bleNusManager

        // TTS 초기화
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
            }
        }

        // 연결 상태 감시: 연결되면 TTS 안내, 끊기면 자동 재연결
        lifecycleScope.launch {
            bleNusManager.connectionState.collect { state ->
                when (state) {
                    BleNusManager.ConnectionState.CONNECTED -> {
                        tts?.speak("기기가 연결되었습니다", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                    BleNusManager.ConnectionState.DISCONNECTED -> {
                        if (bleNusManager.autoConnectEnabled.value) {
                            delay(3_000)
                            // 딜레이 후에도 여전히 끊겨 있고 자동연결 모드이면 재시도
                            if (bleNusManager.connectionState.value == BleNusManager.ConnectionState.DISCONNECTED
                                && bleNusManager.autoConnectEnabled.value) {
                                bleNusManager.connect()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        // 앱 시작 시 자동 연결
        if (blePermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            bleNusManager.connect()
        } else {
            permissionLauncher.launch(blePermissions)
        }

        setContent {
            SmartCaneTheme {
                var showBleTest by remember { mutableStateOf(false) }
                // "test:model_1.tflite" 형식이면 testMode=true
                var hazardCamModel by remember { mutableStateOf<String?>(null) }
                val isTestMode = hazardCamModel?.startsWith("test:") == true
                val actualModelName = hazardCamModel?.removePrefix("test:") ?: ""

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        hazardCamModel != null -> HazardDetectionScreen(
                            onClose = { hazardCamModel = null },
                            modelFileName = actualModelName,
                            testMode = isTestMode
                        )
                        showBleTest -> BleTestScreen(
                            bleManager = bleNusManager,
                            onOpenHazardCam = { model -> hazardCamModel = model }
                        )
                        else -> NavigationScreen(viewModel = navigationViewModel)
                    }

                    FloatingActionButton(
                        onClick = { showBleTest = !showBleTest },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 96.dp),
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.Transparent,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Text(text = " ", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
