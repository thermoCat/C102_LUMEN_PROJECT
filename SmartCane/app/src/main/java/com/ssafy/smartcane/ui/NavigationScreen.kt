package com.ssafy.smartcane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ssafy.smartcane.data.model.defaultFavorites
import com.ssafy.smartcane.ui.screen.FavScreen
import com.ssafy.smartcane.ui.screen.RouteScreen
import com.ssafy.smartcane.ui.screen.SafetyScreen
import com.ssafy.smartcane.ui.screen.SearchScreen
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.viewmodel.NavigationViewModel

@Composable
fun NavigationScreen(viewModel: NavigationViewModel) {
    var tab by remember { mutableStateOf(NavTab.Search) }
    var favorites by remember { mutableStateOf(defaultFavorites) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavBg)
            .statusBarsPadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                NavTab.Search -> SearchScreen(
                    favorites   = favorites,
                    onFavChange = { favorites = it },
                    onTabChange = { tab = it }
                )
                NavTab.Route  -> RouteScreen(onTabChange = { tab = it })
                NavTab.Safety -> SafetyScreen(onTabChange = { tab = it })
                NavTab.Fav    -> FavScreen(
                    favorites   = favorites,
                    onFavChange = { favorites = it },
                    onTabChange = { tab = it }
                )
            }
        }
    }
}
