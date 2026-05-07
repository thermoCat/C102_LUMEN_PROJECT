package com.ssafy.smartcane.network

import com.ssafy.smartcane.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class KakaoPlace(
    val placeName: String,
    val roadAddressName: String,
    val addressName: String,
    val distanceMeters: Int = 0,
    val longitude: Double? = null,
    val latitude: Double? = null
)

class KakaoLocalSearchService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(
        keyword: String,
        page: Int = 1,
        size: Int = 15,
        longitude: Double? = null,
        latitude: Double? = null
    ): List<KakaoPlace> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = BuildConfig.KAKAO_REST_API_KEY.trim()
                android.util.Log.d("KakaoSearch", "key=${apiKey.take(6)}… keyword=$keyword")
                if (apiKey.isEmpty()) {
                    android.util.Log.e("KakaoSearch", "API 키 비어있음 — BuildConfig 확인 필요")
                    return@withContext emptyList()
                }

                val urlBuilder = "https://dapi.kakao.com/v2/local/search/keyword.json"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", keyword.trim())
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("size", size.toString())

                if (longitude != null && latitude != null) {
                    urlBuilder
                        .addQueryParameter("x", longitude.toString())
                        .addQueryParameter("y", latitude.toString())
                        .addQueryParameter("sort", "distance")
                }

                val url = urlBuilder.build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "KakaoAK $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    android.util.Log.d("KakaoSearch", "HTTP ${response.code}")
                    if (!response.isSuccessful) {
                        android.util.Log.e("KakaoSearch", "실패 ${response.code}: ${response.body?.string()}")
                        return@withContext emptyList()
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()
                    parsePlaces(body)
                }
            }.getOrElse { e ->
                android.util.Log.e("KakaoSearch", "예외 발생", e)
                emptyList()
            }
        }

    suspend fun searchNearbyAttractions(
        longitude: Double,
        latitude: Double,
        radiusMeters: Int = 2000,
        size: Int = 15
    ): List<KakaoPlace> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = BuildConfig.KAKAO_REST_API_KEY.trim()
                if (apiKey.isEmpty()) return@withContext emptyList()

                val categoryGroups = listOf("SW8", "CS2", "FD6", "CE7", "BK9", "PM9", "HP8", "AT4")
                categoryGroups
                    .flatMap { categoryGroup ->
                        searchNearbyCategory(
                            apiKey = apiKey,
                            categoryGroup = categoryGroup,
                            longitude = longitude,
                            latitude = latitude,
                            radiusMeters = radiusMeters,
                            size = 5
                        )
                    }
                    .distinctBy { it.placeName to it.addressName }
                    .sortedBy { it.distanceMeters }
                    .take(size)
            }.getOrElse {
                emptyList()
            }
        }

    private fun searchNearbyCategory(
        apiKey: String,
        categoryGroup: String,
        longitude: Double,
        latitude: Double,
        radiusMeters: Int,
        size: Int
    ): List<KakaoPlace> {
        val url = "https://dapi.kakao.com/v2/local/search/category.json"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("category_group_code", categoryGroup)
            .addQueryParameter("x", longitude.toString())
            .addQueryParameter("y", latitude.toString())
            .addQueryParameter("radius", radiusMeters.toString())
            .addQueryParameter("sort", "distance")
            .addQueryParameter("size", size.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "KakaoAK $apiKey")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            parsePlaces(body)
        }
    }

    suspend fun getAddressName(longitude: Double, latitude: Double): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = BuildConfig.KAKAO_REST_API_KEY.trim()
                if (apiKey.isEmpty()) return@withContext ""

                val url = "https://dapi.kakao.com/v2/local/geo/coord2address.json"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("x", longitude.toString())
                    .addQueryParameter("y", latitude.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "KakaoAK $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext ""
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext ""
                    parseAddressName(body)
                }
            }.getOrDefault("")
        }

    private fun parseAddressName(body: String): String {
        val root = JSONObject(body)
        val document = root.optJSONArray("documents")?.optJSONObject(0) ?: return ""
        val roadAddress = document.optJSONObject("road_address")
        val address = document.optJSONObject("address")
        return roadAddress?.optString("address_name").orEmpty()
            .ifBlank { address?.optString("address_name").orEmpty() }
    }

    private fun parsePlaces(body: String): List<KakaoPlace> {
        val root = JSONObject(body)
        val documents = root.optJSONArray("documents") ?: JSONArray()

        return buildList {
            for (index in 0 until documents.length()) {
                val item = documents.optJSONObject(index) ?: continue
                add(
                    KakaoPlace(
                        placeName = item.optString("place_name"),
                        roadAddressName = item.optString("road_address_name"),
                        addressName = item.optString("address_name"),
                        distanceMeters = item.optString("distance").toIntOrNull() ?: 0,
                        longitude = item.optString("x").toDoubleOrNull(),
                        latitude = item.optString("y").toDoubleOrNull()
                    )
                )
            }
        }
    }
}
