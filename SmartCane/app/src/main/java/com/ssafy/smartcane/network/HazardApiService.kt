package com.ssafy.smartcane.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HazardApiService {

    private const val BASE_URL = "https://k14c102.p.ssafy.io"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class HazardResult(val success: Boolean, val message: String)

    suspend fun reportHazard(
        lat: Double,
        lng: Double,
        type: String = "OBSTACLE",
        confidence: Double = 1.0,
        imageBase64: String = "",
        description: String = "BLE 테스트 신고"
    ): HazardResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("type", type)
                put("confidence", confidence)
                put("imageBase64", imageBase64)
                put("description", description)
            }.toString()

            val request = Request.Builder()
                .url("$BASE_URL/api/hazards")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            response.close()

            if (code in 200..299) {
                HazardResult(true, "신고 완료 (HTTP $code)")
            } else {
                HazardResult(false, "서버 오류 (HTTP $code)")
            }
        }.getOrElse { e ->
            HazardResult(false, "네트워크 오류: ${e.message}")
        }
    }
}
