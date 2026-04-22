package com.ssafy.smartcane.data.model

data class FavItem(val id: Int, val name: String, val addr: String)

data class SearchResult(val id: Int, val name: String, val addr: String, val starred: Boolean)

val defaultFavorites = listOf(
    FavItem(1, "집", "광주 광산구 풍영로 522-1"),
    FavItem(2, "학교", "광주 광산구 장덕동 327"),
    FavItem(3, "상무 시민공원", "광주 서구 상무공원로 212"),
    FavItem(4, "집 근처", "광주 광산구 풍영로 522-1")
)
