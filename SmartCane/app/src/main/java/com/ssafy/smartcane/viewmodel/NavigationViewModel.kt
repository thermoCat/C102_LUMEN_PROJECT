package com.ssafy.smartcane.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.smartcane.ble.BleManager
import com.ssafy.smartcane.data.MockDataSource
import com.ssafy.smartcane.data.model.NavigationState
import com.ssafy.smartcane.network.WebSocketManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val _navState = MutableStateFlow(NavigationState())
    val navState: StateFlow<NavigationState> = _navState.asStateFlow()

    private val lastServerUpdate = AtomicLong(0L)

    val wsManager: WebSocketManager = WebSocketManager { state ->
        lastServerUpdate.set(System.currentTimeMillis())
        _navState.value = state
    }

    val bleManager: BleManager = BleManager(application)

    init {
        wsManager.connect()

        viewModelScope.launch {
            while (true) {
                delay(150)
                if (System.currentTimeMillis() - lastServerUpdate.get() > 800L) {
                    _navState.value = MockDataSource.getAnimatedState()
                }
            }
        }

        viewModelScope.launch {
            var lastSentState = ""
            navState.collect { nav ->
                val s = nav.state
                if (s.isEmpty()) return@collect
                val isDeviation = s == "LEFT" || s == "RIGHT" || s == "STOP"
                if (s != lastSentState && (isDeviation || lastSentState != "CENTER")) {
                    lastSentState = s
                    bleManager.sendState(s)
                }
            }
        }
    }

    override fun onCleared() {
        wsManager.disconnect()
        bleManager.disconnect()
        super.onCleared()
    }
}
