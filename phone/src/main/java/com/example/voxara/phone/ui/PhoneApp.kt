package com.example.voxara.phone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.screens.AlertsScreen
import com.example.voxara.phone.ui.screens.HeadphonesScreen
import com.example.voxara.phone.ui.screens.HomeScreen
import com.example.voxara.phone.ui.screens.HowItWorksScreen
import com.example.voxara.phone.ui.screens.SettingsScreen
import com.example.voxara.phone.ui.screens.SetupScreen
import com.example.voxara.phone.ui.screens.TodayScreen
import com.example.voxara.phone.ui.screens.TonoScreen
import com.example.voxara.phone.ui.screens.TrendsScreen
import com.example.voxara.phone.ui.screens.WatchScreen
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(PhoneRoutes.HOME, R.string.nav_home, VoxIcons.Home),
    Tab(PhoneRoutes.TRENDS, R.string.nav_trends, VoxIcons.Chart),
    Tab(PhoneRoutes.TONO, R.string.nav_tono, VoxIcons.Ask),
    Tab(PhoneRoutes.WATCH, R.string.nav_watch, VoxIcons.Watch),
    Tab(PhoneRoutes.SETTINGS, R.string.nav_settings, VoxIcons.Settings),
)

/**
 * THE PHONE APP. First launch: the setup. Then five tabs — a bottom bar on phones, a navigation
 * rail on tablets and unfolded foldables (window size classes, via the navigation suite).
 */
@Composable
fun PhoneApp(state: PhoneUiState, actions: PhoneActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    if (!state.setupDone) {
        SetupScreen(state, actions)
        return
    }
    // Destinations read the state through State, so they always show the latest (the nav graph
    // keeps the lambdas it was built with).
    val s by rememberUpdatedState(state)
    val a by rememberUpdatedState(actions)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val go: (String) -> Unit = { nav.navigate(it) }
    val back: () -> Unit = { nav.popBackStack() }
    val c = Vox.colors
    // The template's bottom nav: muted items, the selected one with an accent-soft pill and accent icon.
    val pill = c.accent.soft.compositeOver(c.surface)
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = c.brand, selectedTextColor = c.text, indicatorColor = pill,
            unselectedIconColor = c.text3, unselectedTextColor = c.text3,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = c.brand, selectedTextColor = c.text, indicatorColor = pill,
            unselectedIconColor = c.text3, unselectedTextColor = c.text3,
        ),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { tab ->
                item(
                    selected = current == tab.route,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                    colors = itemColors,
                )
            }
        },
        layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = c.surface,
            navigationRailContainerColor = c.surface,
        ),
        containerColor = c.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = PhoneRoutes.HOME) {
                composable(PhoneRoutes.HOME) { HomeScreen(s, a, go) }
                composable(PhoneRoutes.TRENDS) { TrendsScreen(s, a.onRefresh) }
                composable(PhoneRoutes.TONO) { TonoScreen(s, a) }
                composable(PhoneRoutes.WATCH) { WatchScreen(s, a) }
                composable(PhoneRoutes.SETTINGS) { SettingsScreen(s, a, go) }
                composable(PhoneRoutes.TODAY) { TodayScreen(s, back, a.onRefresh) }
                composable(PhoneRoutes.ALERTS) { AlertsScreen(s, back, a.onRefresh) }
                composable(PhoneRoutes.HEADPHONES) { HeadphonesScreen(s, a, back) }
                composable(PhoneRoutes.HOW) { HowItWorksScreen(back) }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
