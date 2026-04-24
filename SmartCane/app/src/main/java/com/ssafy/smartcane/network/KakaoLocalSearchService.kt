package com.ssafy.smartcane.network

import com.ssafy.smartcane.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class KakaoPlace(
    val placeName: String,
    val roadAddressName: String,
    val addressName: String,
    val distanceMeters: Int = 0
)

class KakaoLocalSearchService(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun search(keyword: String, page: Int = 1, size: Int = 15): List<KakaoPlace> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = BuildConfig.KAKAO_REST_API_KEY.trim()
                if (apiKey.isEmpty()) return@withContext emptyList()

                val url = "https://dapi.kakao.com/v2/local/search/keyword.json"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", keyword.trim())
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("size", size.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "KakaoAK $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()

                    parsePlaces(body)
                }
            }.getOrElse {
                emptyList()
            }
        }

    suspend fun searchNearbyAttractions(
        longitude: Double,
        latitude: Double,
        radiusMeters: Int = 20000,
        size: Int = 15
    ): List<KakaoPlace> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = BuildConfig.KAKAO_REST_API_KEY.trim()
                if (apiKey.isEmpty()) return@withContext emptyList()

                val url = "https://dapi.kakao.com/v2/local/search/keyword.json"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", "\uad00\uad11\uba85\uc18c")
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

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()

                    parsePlaces(body)
                }
            }.getOrElse {
                emptyList()
            }
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
                        distanceMeters = item.optString("distance").toIntOrNull() ?: 0
                    )
                )
            }
        }
    }
}
