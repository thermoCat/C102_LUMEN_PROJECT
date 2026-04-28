package com.ssafy.smartcane.map

import android.os.Build

object KakaoMapSupport {
    fun isSupportedDevice(): Boolean =
        Build.SUPPORTED_ABIS.any { abi ->
            abi == "arm64-v8a" || abi == "armeabi-v7a"
        }
}
