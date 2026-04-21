package com.ssafy.smartcane.data.model

data class FavItem(val id: Int, val name: String, val addr: String)

data class SearchResult(val id: Int, val name: String, val addr: String, val starred: Boolean)

val defaultFavorites = listOf(
    FavItem(1, "집",           "서울특별시 강남구 테헤란로 1길 10"),
    FavItem(2, "회사",         "서울특별시 서초구 강남대로 327"),
    FavItem(3, "SSAFY 캠퍼스", "서울특별시 강남구 역삼로 180"),
    FavItem(4, "세곡천공원",   "서울특별시 강남구 세곡동 522-1"),
    FavItem(5, "스타벅스 역삼점", "서울특별시 강남구 역삼동 823-3"),
    FavItem(6, "서울성모병원", "서울특별시 서초구 반포대로 222"),
)

val defaultSearchResults = listOf(
    SearchResult(10, "서울역",    "서울특별시 용산구 한강대로 405",             starred = true),
    SearchResult(11, "강남역",    "서울특별시 강남구 강남대로 396",             starred = true),
    SearchResult(12, "바이크 하우스",   "광주광역시 서구 하남대로111번길 6",          starred = false),
)
