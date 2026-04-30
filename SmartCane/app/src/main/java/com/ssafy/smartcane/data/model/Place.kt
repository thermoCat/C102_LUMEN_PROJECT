package com.ssafy.smartcane.data.model

data class FavItem(
    val id: Int,
    val name: String,
    val addr: String,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val estimatedMinutes: Int? = null
)

data class SearchResult(
    val id: Int,
    val name: String,
    val addr: String,
    val starred: Boolean,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val estimatedMinutes: Int? = null
)

data class RouteDestination(
    val name: String,
    val addr: String,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val estimatedMinutes: Int? = null
)
