package com.ssafy.smartcane

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.ssafy.smartcane.ble.BleNusManager

class SmartCaneApplication : Application() {

    lateinit var bleNusManager: BleNusManager
        private set

    override fun onCreate() {
        super.onCreate()
        bleNusManager = BleNusManager(applicationContext)
        val nativeKey = BuildConfig.KAKAO_NATIVE_APP_KEY.trim()
        if (nativeKey.isNotEmpty()) {
            runCatching { KakaoMapSdk.init(this, nativeKey) }
        }
    }
}
