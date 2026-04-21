package com.ssafy.smartcane.data.model

data class NavigationState(
    val state: String = "CENTER",
    val path: List<List<Float>> = emptyList(),
    val mask: List<List<Float>> = emptyList()
)
