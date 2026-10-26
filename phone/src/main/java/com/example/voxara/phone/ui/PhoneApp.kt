package com.example.voxara.phone.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.components.LocalDestinationScope
import com.example.voxara.phone.ui.components.LocalSharedTransition
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
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.CappedFontScale
import com.example.voxara.ui.design.VoxIcons

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

/** The four tabs. Everything else (Today, Alerts, Headphones, Tono, How it works) opens from one of them, at most two taps from Home. */
private val tabs = listOf(
    Tab(PhoneRoutes.HOME, R.string.nav_home, VoxIcons.Home),
    Tab(PhoneRoutes.TRENDS, R.string.nav_trends, VoxIcons.Chart),
    Tab(PhoneRoutes.WATCH, R.string.nav_watch, VoxIcons.Watch),
    Tab(PhoneRoutes.SETTINGS, R.string.nav_settings, VoxIcons.Settings),
)
private val tabRoutes = tabs.map { it.route }.toSet()

/**
 * THE PHONE APP. First launch: the setup. Then four tabs — a bottom bar on phones, a navigation rail on tablets and
 * unfolded foldables (window size classes, via the navigation suite). A screen opened from a tab keeps that tab selected.
 * Tabs cross-fade; detail screens slide in a little; the Home ring grows into Today (a shared element). With reduced motion
 * everything is a plain fade.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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
    // The tab a screen belongs to: the last tab you were in (Tono opened from Trends keeps Trends selected).
    var lastTab by rememberSaveable { mutableStateOf(PhoneRoutes.HOME) }
    if (current in tabRoutes && current != null) lastTab = current
    val selectedTab = if (current in tabRoutes && current != null) current else lastTab
    val go: (String) -> Unit = { nav.navigate(it) }
    val goTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val reduce = LocalReduceMotion.current

    fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(pop: Boolean): EnterTransition {
        val from = initialState.destination.route
        val to = targetState.destination.route
        return when {
            reduce -> fadeIn(tween(120))
            from in tabRoutes && to in tabRoutes -> fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.96f)
            pop -> fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 10 }
            else -> fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 }
        }
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(pop: Boolean): ExitTransition {
        val from = initialState.destination.route
        val to = targetState.destination.route
        return when {
            reduce -> fadeOut(tween(90))
            from in tabRoutes && to in tabRoutes -> fadeOut(tween(90))
            pop -> fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 10 }
            else -> fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 10 }
        }
    }

    VoxNavigationSuite(
        selectedTab = selectedTab,
        onTab = { route ->
            if (selectedTab == route && current != route) {
                // Tapping the tab you are in takes you back to its first screen.
                nav.popBackStack(route, inclusive = false)
            } else {
                goTab(route)
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            SharedTransitionLayout {
                val shared = this
                NavHost(
                    nav, startDestination = PhoneRoutes.HOME,
                    enterTransition = { enter(pop = false) }, exitTransition = { exit(pop = false) },
                    popEnterTransition = { enter(pop = true) }, popExitTransition = { exit(pop = true) },
                ) {
                    composable(PhoneRoutes.HOME) {
                        CompositionLocalProvider(LocalSharedTransition provides shared, LocalDestinationScope provides this) { HomeScreen(s, a, go) }
                    }
                    composable(PhoneRoutes.TRENDS) { TrendsScreen(s, a.onRefresh, onAskTono = { go(PhoneRoutes.TONO) }) }
                    composable(PhoneRoutes.TONO) { TonoScreen(s, a, onBack = back) }
                    composable(PhoneRoutes.WATCH) { WatchScreen(s, a) }
                    composable(PhoneRoutes.SETTINGS) { SettingsScreen(s, a, go) }
                    composable(PhoneRoutes.TODAY) {
                        CompositionLocalProvider(LocalSharedTransition provides shared, LocalDestinationScope provides this) { TodayScreen(s, back, a.onRefresh) }
                    }
                    composable(PhoneRoutes.ALERTS) { AlertsScreen(s, back, a.onRefresh) }
                    composable(PhoneRoutes.HEADPHONES) { HeadphonesScreen(s, a, back, onOpenSettings = { goTab(PhoneRoutes.SETTINGS) }) }
                    composable(PhoneRoutes.HOW) { HowItWorksScreen(back) }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The navigation around every screen: a bar at the bottom on phones, a rail on tablets and unfolded foldables (window size
 * classes). The renders and the app use this one, so a screenshot shows the four tabs as they are.
 */
@Composable
fun VoxNavigationSuite(selectedTab: String, onTab: (String) -> Unit, content: @Composable () -> Unit) {
    val c = Vox.colors
    // The selected item: an accent-soft pill and accent icon; the rest muted.
    val pill = c.accent.soft.compositeOver(c.surface)
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = c.brand, selectedTextColor = c.text, indicatorColor = pill,
            unselectedIconColor = c.text2, unselectedTextColor = c.text2,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = c.brand, selectedTextColor = c.text, indicatorColor = pill,
            unselectedIconColor = c.text2, unselectedTextColor = c.text2,
        ),
    )
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { tab ->
                item(
                    selected = selectedTab == tab.route,
                    onClick = { onTab(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    // the tab names hold at 1.3 times the font scale: a bar of four labels never breaks a word
                    label = { CappedFontScale(1.3f) { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) } },
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
        content = content,
    )
}
