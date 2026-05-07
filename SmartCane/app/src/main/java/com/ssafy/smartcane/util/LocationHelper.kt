package com.ssafy.smartcane.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

object LocationHelper {

    /**
     * SafetyWalkService 등 백그라운드 서비스가 실시간으로 주입하는 위치.
     * null 이면 시스템 캐시 위치로 폴백.
     */
    @Volatile private var liveLocation: Pair<Double, Double>? = null
    @Volatile private var liveLocationTime: Long = 0L

    /** 서비스에서 GPS 업데이트마다 호출 */
    fun updateLiveLocation(lat: Double, lng: Double) {
        liveLocation = Pair(lat, lng)
        liveLocationTime = System.currentTimeMillis()
    }

    /** 서비스 종료 시 호출 */
    fun clearLiveLocation() {
        liveLocation = null
        liveLocationTime = 0L
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        // 30초 이내 라이브 위치가 있으면 우선 사용
        val live = liveLocation
        if (live != null && System.currentTimeMillis() - liveLocationTime < 30_000L) {
            return live
        }

        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val location = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .minByOrNull { it.accuracy }
        return location?.let { Pair(it.latitude, it.longitude) }
    }
}
