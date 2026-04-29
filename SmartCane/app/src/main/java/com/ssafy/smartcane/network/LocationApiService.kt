package com.ssafy.smartcane.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LocationApiService {

    private const val BASE_URL = "http://k14c102.p.ssafy.io"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun sendLocation(deviceId: String, lat: Double, lng: Double) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("lat", lat)
                    put("lng", lng)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/api/location")  // Traefik이 /api 붙여서 라우팅
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            }
        }
}
