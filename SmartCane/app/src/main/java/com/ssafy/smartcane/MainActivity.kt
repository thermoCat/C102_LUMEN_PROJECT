package com.ssafy.smartcane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.ssafy.smartcane.ble.BleNusManager
import com.ssafy.smartcane.ui.NavigationScreen
import com.ssafy.smartcane.ui.theme.SmartCaneTheme
import com.ssafy.smartcane.viewmodel.NavigationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val navigationViewModel = ViewModelProvider(this)[NavigationViewModel::class.java]
        val bleNusManager = BleNusManager(applicationContext)

        setContent {
            SmartCaneTheme {
                var showBleTest by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showBleTest) {
                        BleTestScreen(bleManager = bleNusManager)
                    } else {
                        NavigationScreen(viewModel = navigationViewModel)
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
}
