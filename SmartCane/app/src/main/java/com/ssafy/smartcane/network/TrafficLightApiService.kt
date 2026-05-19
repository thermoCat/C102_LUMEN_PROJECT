package com.ssafy.smartcane.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NearestTrafficLight(
    val id: Long,
    val managementNumber: String?,
    val roadNameAddress: String?,
    val roadRouteName: String?,
    val trafficLightType: Int?,
    val facingDirection: Double?,
    val lat: Double,
    val lng: Double
)

object TrafficLightApiService {

    private const val TAG = "TrafficLightApiService"
    private const val BASE_URL = "https://k14c102.p.ssafy.io"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchNearest(lat: Double, lng: Double): List<NearestTrafficLight> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/api/traffic-lights/nearest?lat=$lat&lng=$lng"
                val response = client.newCall(Request.Builder().url(url).get().build()).execute()
                val body = response.body?.string() ?: return@runCatching emptyList()
                response.close()

                val root = JSONObject(body)
                val features = root.getJSONArray("features")
                val result = mutableListOf<NearestTrafficLight>()

                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                    val props = feature.getJSONObject("properties")

                    result.add(
                        NearestTrafficLight(
                            id = props.optLong("id"),
                            managementNumber = props.optString("managementNumber").takeIf { it.isNotEmpty() },
                            roadNameAddress = props.optString("roadNameAddress").takeIf { it.isNotEmpty() },
                            roadRouteName = props.optString("roadRouteName").takeIf { it.isNotEmpty() },
                            trafficLightType = props.optInt("trafficLightType").takeIf { it != 0 },
                            facingDirection = if (props.isNull("facingDirection")) null
                                             else props.getDouble("facingDirection"),
                            lat = coords.getDouble(1),
                            lng = coords.getDouble(0)
                        )
                    )
                }
                result
            }.onFailure {
                Log.e(TAG, "신호등 조회 실패", it)
            }.getOrDefault(emptyList())
        }
}
