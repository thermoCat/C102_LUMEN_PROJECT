package com.ssafy.smartcane.intersection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class ItsNode(val lat: Double, val lng: Double)

object IntersectionDetector {

    private const val TAG = "IntersectionDetector"
    private const val BASE_URL = "https://k14c102.p.ssafy.io"

    // 반경 300m fetch, 150m 이상 이동 시 재요청
    private const val FETCH_RADIUS_DEG = 0.003       // ~300m
    private const val REFETCH_THRESHOLD_M = 150.0
    private const val INTERSECTION_RADIUS_M = 25.0
    private const val INTERSECTION_NODE_COUNT = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val cachedNodes = mutableListOf<ItsNode>()
    private var lastFetchLat: Double? = null
    private var lastFetchLng: Double? = null

    // 최초 1회 또는 150m 이탈 시 노드 fetch
    suspend fun updateIfNeeded(lat: Double, lng: Double) {
        val last = lastFetchLat
        if (last == null ||
            haversine(lat, lng, last, lastFetchLng!!) > REFETCH_THRESHOLD_M
        ) {
            fetchNodes(lat, lng)
        }
    }

    // 현재 위치가 교차로(사거리) 근처인지 판단
    fun isNearIntersection(lat: Double, lng: Double): Boolean =
        nearbyNodeCount(lat, lng) >= INTERSECTION_NODE_COUNT

    // 근처 노드 수 반환 (삼거리/사거리 구분용)
    fun nearbyNodeCount(lat: Double, lng: Double): Int {
        return synchronized(cachedNodes) {
            cachedNodes.count { node ->
                haversine(lat, lng, node.lat, node.lng) <= INTERSECTION_RADIUS_M
            }
        }
    }

    private suspend fun fetchNodes(lat: Double, lng: Double) =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/api/road/nodes" +
                    "?minLng=${lng - FETCH_RADIUS_DEG}" +
                    "&minLat=${lat - FETCH_RADIUS_DEG}" +
                    "&maxLng=${lng + FETCH_RADIUS_DEG}" +
                    "&maxLat=${lat + FETCH_RADIUS_DEG}" +
                    "&nodeType=101" +
                    "&limit=500"

                val response = client.newCall(Request.Builder().url(url).get().build())
                    .execute()

                response.body?.string()?.let { body ->
                    val features = JSONObject(body).getJSONArray("features")
                    val nodes = mutableListOf<ItsNode>()
                    for (i in 0 until features.length()) {
                        val coords = features.getJSONObject(i)
                            .getJSONObject("geometry")
                            .getJSONArray("coordinates")
                        nodes.add(ItsNode(lat = coords.getDouble(1), lng = coords.getDouble(0)))
                    }
                    synchronized(cachedNodes) {
                        cachedNodes.clear()
                        cachedNodes.addAll(nodes)
                    }
                    lastFetchLat = lat
                    lastFetchLng = lng
                    Log.d(TAG, "노드 ${nodes.size}개 캐시 완료 (중심: $lat, $lng)")
                }
            }.onFailure {
                Log.e(TAG, "노드 fetch 실패", it)
            }
        }

    // Haversine 거리 계산 (미터)
    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
