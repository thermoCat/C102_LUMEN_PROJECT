package com.ssafy.smartcane

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.ssafy.smartcane.map.KakaoMapSupport

class SmartCaneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val nativeKey = BuildConfig.KAKAO_NATIVE_APP_KEY.trim()
        if (nativeKey.isNotEmpty() && KakaoMapSupport.isSupportedDevice()) {
            runCatching { KakaoMapSdk.init(this, nativeKey) }
        }
    }
}
