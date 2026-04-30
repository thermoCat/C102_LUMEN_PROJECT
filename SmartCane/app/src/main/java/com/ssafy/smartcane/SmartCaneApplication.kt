package com.ssafy.smartcane

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class SmartCaneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val nativeKey = BuildConfig.KAKAO_NATIVE_APP_KEY.trim()
        if (nativeKey.isNotEmpty()) {
            runCatching { KakaoMapSdk.init(this, nativeKey) }
        }
    }
}
