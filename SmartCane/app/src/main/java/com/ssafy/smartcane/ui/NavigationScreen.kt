package com.ssafy.smartcane.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ssafy.smartcane.data.model.defaultFavorites
import com.ssafy.smartcane.data.model.RouteDestination
import com.ssafy.smartcane.ui.screen.FavScreen
import com.ssafy.smartcane.ui.screen.RouteScreen
import com.ssafy.smartcane.ui.screen.SafetyScreen
import com.ssafy.smartcane.ui.screen.SearchScreen
import com.ssafy.smartcane.ui.theme.NavBg
import com.ssafy.smartcane.ui.theme.NavBarBg
import com.ssafy.smartcane.viewmodel.NavigationViewModel

@Composable
fun NavigationScreen(viewModel: NavigationViewModel) {
    var tab by remember { mutableStateOf(NavTab.Search) }
    var favorites by remember { mutableStateOf(defaultFavorites) }
    var safetyEnabled by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<RouteDestination?>(null) }
    val screenBackground = when {
        tab == NavTab.Safety && safetyEnabled -> Color(0xFF001B2B)
        tab == NavTab.Safety -> NavBarBg
        else -> NavBg
    }
    val view = LocalView.current

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = screenBackground.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    LaunchedEffect(tab) {
        if (tab != NavTab.Safety) safetyEnabled = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    NavTab.Search -> SearchScreen(
                        favorites = favorites,
                        onFavChange = { favorites = it },
                        onDestinationSelected = { destination = it },
                        onTabChange = { tab = it }
                    )
                    NavTab.Route -> RouteScreen(
                        destination = destination,
                        originName = "",
                        onTabChange = { tab = it }
                    )
                    NavTab.Safety -> SafetyScreen(
                        onTabChange = { tab = it },
                        onEnabledChange = { safetyEnabled = it }
                    )
                    NavTab.Fav -> FavScreen(
                        favorites = favorites,
                        onFavChange = { favorites = it },
                        onDestinationSelected = { destination = it },
                        onTabChange = { tab = it }
                    )
                }
            }
        }
    }
}
