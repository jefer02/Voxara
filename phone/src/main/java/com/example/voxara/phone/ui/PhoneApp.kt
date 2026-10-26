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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.voxara.phone.ui.components.TabIcons
import com.example.voxara.phone.ui.components.rememberVoxHaptics
import com.example.voxara.phone.ui.theme.VoxMotion
import com.example.voxara.phone.ui.theme.VoxSpace
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voxara.phone.ui.components.EdgeFade
import com.example.voxara.phone.ui.components.LocalBottomBarInset
import com.example.voxara.phone.ui.components.glassBar
import com.example.voxara.phone.ui.components.glassSource
import com.example.voxara.phone.ui.theme.LocalGlass
import dev.chrisbanes.haze.HazeState
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

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val filled: ImageVector)

/** The four tabs. Everything else (Today, Alerts, Headphones, Tono, How it works) opens from one of them, at most two taps from Home. */
private val tabs = listOf(
    Tab(PhoneRoutes.HOME, R.string.nav_home, VoxIcons.Home, TabIcons.Home),
    Tab(PhoneRoutes.TRENDS, R.string.nav_trends, VoxIcons.Chart, TabIcons.Chart),
    Tab(PhoneRoutes.WATCH, R.string.nav_watch, VoxIcons.Watch, TabIcons.Watch),
    Tab(PhoneRoutes.SETTINGS, R.string.nav_settings, VoxIcons.Settings, TabIcons.Settings),
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
                    // Home's dose card grows into Today and its headphones card into Headphones (shared elements, SharedKeys).
                    composable(PhoneRoutes.TRENDS) { TrendsScreen(s, a.onRefresh, onAskTono = { go(PhoneRoutes.TONO) }) }
                    composable(PhoneRoutes.TONO) { TonoScreen(s, a, onBack = back) }
                    composable(PhoneRoutes.WATCH) { WatchScreen(s, a) }
                    composable(PhoneRoutes.SETTINGS) { SettingsScreen(s, a, go) }
                    composable(PhoneRoutes.TODAY) {
                        CompositionLocalProvider(LocalSharedTransition provides shared, LocalDestinationScope provides this) { TodayScreen(s, back, a.onRefresh) }
                    }
                    composable(PhoneRoutes.ALERTS) { AlertsScreen(s, back, a.onRefresh) }
                    composable(PhoneRoutes.HEADPHONES) {
                        CompositionLocalProvider(LocalSharedTransition provides shared, LocalDestinationScope provides this) {
                            HeadphonesScreen(s, a, back, onOpenSettings = { goTab(PhoneRoutes.SETTINGS) })
                        }
                    }
                    composable(PhoneRoutes.HOW) { HowItWorksScreen(back) }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomBarInset.current))
        }
    }
}

/**
 * The navigation around every screen. On phones a floating capsule of glass ([CapsuleTabBar]) over the content, which runs
 * under it and ends above it ([LocalBottomBarInset]). On tablets and unfolded foldables a rail (window size classes, via the
 * navigation suite). The renders and the app use this one, so a screenshot shows the tabs as they are.
 */
@Composable
fun VoxNavigationSuite(selectedTab: String, onTab: (String) -> Unit, content: @Composable () -> Unit) {
    val layout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    if (layout == NavigationSuiteType.NavigationBar) CapsuleLayout(selectedTab, onTab, content)
    else RailLayout(selectedTab, onTab, layout, content)
}

/** The tab label: held at 1.3 times the font scale, so a label never breaks a word. */
@Composable
private fun TabLabel(tab: Tab, color: Color, modifier: Modifier = Modifier) {
    CappedFontScale(1.3f) {
        Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false, modifier = modifier)
    }
}

/** The capsule's height (at least: large text may make it taller) and its distance from the screen's edges. */
private val CapsuleHeight = 64.dp
private val CapsuleMargin = 16.dp
private val CapsuleGap = 8.dp

@Composable
private fun CapsuleLayout(selectedTab: String, onTab: (String) -> Unit, content: @Composable () -> Unit) {
    val c = Vox.colors
    val glass = LocalGlass.current
    val haze = remember(glass) { if (glass) HazeState() else null }
    val density = LocalDensity.current
    // The room the capsule takes from the bottom (gap, capsule, gesture area) once measured; until then its minimum.
    val estimate = CapsuleHeight + CapsuleGap + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var measured by remember { mutableStateOf<Dp?>(null) }
    Box(Modifier.fillMaxSize().background(c.background)) {
        CompositionLocalProvider(LocalBottomBarInset provides (measured ?: estimate)) {
            Box(Modifier.fillMaxSize().glassSource(haze)) { content() }
        }
        // the content dissolves over 48 dp before it reaches the capsule (drawing only: the list still scrolls under it)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            EdgeFade(top = false)
            Box(Modifier.fillMaxWidth().height(measured ?: estimate).background(c.background.copy(alpha = 0.92f)))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { measured = with(density) { it.height.toDp() } }
                .navigationBarsPadding()
                .padding(start = CapsuleMargin, end = CapsuleMargin, bottom = CapsuleGap),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CapsuleTabBar(selectedTab, onTab, haze)
        }
    }
}

/**
 * THE CAPSULE TAB BAR — a fully rounded pill of glass (blurred content under a tint of the surface; the solid surface where
 * there is no blur) with a hairline border. No indicator: the selected tab has its icon filled, in the accent, with its
 * label; the others are outlined in the secondary colour. The change springs (standard motion) and ticks once. At large
 * font sizes only the selected tab shows its label (every tab keeps its name for TalkBack).
 */
@Composable
private fun CapsuleTabBar(selectedTab: String, onTab: (String) -> Unit, haze: HazeState?) {
    val c = Vox.colors
    val haptics = rememberVoxHaptics()
    val compact = LocalDensity.current.fontScale > 1.15f
    Row(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = CapsuleHeight)
            .clip(CircleShape)
            .glassBar(haze, c.surface)
            .border(1.dp, c.outline, CircleShape)
            .selectableGroup()
            .padding(horizontal = VoxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab.route
            CapsuleTab(
                tab, selected, showLabel = !compact || selected,
                onClick = {
                    if (!selected) haptics.segment()
                    onTab(tab.route)
                },
                modifier = Modifier.weight(if (compact && selected) 1.6f else 1f),
            )
        }
    }
}

@Composable
private fun CapsuleTab(tab: Tab, selected: Boolean, showLabel: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Vox.colors
    val reduce = LocalReduceMotion.current
    val name = stringResource(tab.label)
    // 0 = unselected, 1 = selected; springs both ways
    val t by animateFloatAsState(if (selected) 1f else 0f, VoxMotion.spec(reduce, VoxMotion.standard()), label = "tab")
    val ink = lerp(c.text2, c.brand, t)
    Column(
        modifier
            .heightIn(min = CapsuleHeight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag("tab_${tab.route}")
            .semantics(mergeDescendants = true) { contentDescription = name }
            .padding(vertical = VoxSpace.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(24.dp).graphicsLayer {
                // the selected icon rises a little and settles with the spring
                translationY = -2.dp.toPx() * t
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, null, tint = c.text2, modifier = Modifier.graphicsLayer { alpha = 1f - t })
            Icon(tab.filled, null, tint = c.brand, modifier = Modifier.graphicsLayer { alpha = t })
        }
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(VoxMotion.spec(reduce, VoxMotion.effects())) + expandVertically(VoxMotion.spec(reduce, VoxMotion.standard())),
            exit = fadeOut(VoxMotion.spec(reduce, VoxMotion.effects())) + shrinkVertically(VoxMotion.spec(reduce, VoxMotion.standard())),
        ) {
            TabLabel(tab, ink, Modifier.padding(top = 2.dp).clearAndSetSemantics { })
        }
    }
}

@Composable
private fun RailLayout(selectedTab: String, onTab: (String) -> Unit, layout: NavigationSuiteType, content: @Composable () -> Unit) {
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
                    label = { TabLabel(tab, if (selectedTab == tab.route) c.text else c.text2) },
                    colors = itemColors,
                )
            }
        },
        layoutType = layout,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = c.surface,
            navigationRailContainerColor = c.surface,
        ),
        containerColor = c.background,
        content = content,
    )
}
