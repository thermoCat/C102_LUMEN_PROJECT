package com.ssafy.smartcane.network

import com.ssafy.smartcane.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class JusoAddress(
    val roadAddress: String,
    val jibunAddress: String,
    val zipCode: String
)

class JusoAddressService(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun search(keyword: String, currentPage: Int = 1, countPerPage: Int = 20): List<JusoAddress> =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.JUSO_API_KEY.trim()
            if (apiKey.isEmpty()) return@withContext emptyList()

            val url = "https://business.juso.go.kr/addrlink/addrLinkApi.do"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("confmKey", apiKey)
                .addQueryParameter("currentPage", currentPage.toString())
                .addQueryParameter("countPerPage", countPerPage.toString())
                .addQueryParameter("keyword", keyword)
                .addQueryParameter("resultType", "json")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()

                parseAddresses(body)
            }
        }

    private fun parseAddresses(body: String): List<JusoAddress> {
        val root = JSONObject(body)
        val results = root.optJSONObject("results") ?: return emptyList()
        val items = results.optJSONArray("juso") ?: JSONArray()

        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    JusoAddress(
                        roadAddress = item.optString("roadAddrPart1").ifBlank {
                            item.optString("roadAddr")
                        },
                        jibunAddress = item.optString("jibunAddr"),
                        zipCode = item.optString("zipNo")
                    )
                )
            }
        }
    }
}
