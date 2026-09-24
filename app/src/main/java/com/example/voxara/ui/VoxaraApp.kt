package com.example.voxara.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.voxara.core.alerts.limitLevel
import com.example.voxara.ui.screens.AmbientScreen
import com.example.voxara.ui.screens.AskScreen
import com.example.voxara.ui.screens.CalibrationScreen
import com.example.voxara.ui.screens.HeadphonesScreen
import com.example.voxara.ui.screens.HomeScreen
import com.example.voxara.ui.screens.LimitAlertScreen
import com.example.voxara.ui.screens.LimitKind
import com.example.voxara.ui.screens.NowScreen
import com.example.voxara.ui.screens.OnboardingScreen
import com.example.voxara.ui.screens.SettingsScreen
import com.example.voxara.ui.screens.TodayScreen
import com.example.voxara.ui.screens.WeekScreen

/**
 * THE APP. First run: onboarding. Then Home (Tono's status line + cards) is the root, every area
 * one tap away, swipe right to go back. Ambient mode shows one calm always-on view. A 100% limit
 * shows full screen while the app is open (the haptic and the notification already fired).
 */
@Composable
fun VoxaraApp(env: UiEnv, actions: VoxaraActions, ambient: Boolean) {
    if (ambient) {
        AmbientScreen(env)
        return
    }
    if (!env.onboarded) {
        OnboardingScreen(env, actions)
        return
    }

    // Limit screens: shown once per new 100% level while the app is open.
    var dailySeen by remember { mutableIntStateOf(0) }
    var weekSeen by remember { mutableIntStateOf(0) }
    var hpSeen by remember { mutableIntStateOf(0) }
    val s = env.state
    val daily = limitLevel(s.dosePercent)
    val week = limitLevel(s.weeklyFraction * 100)
    val hp = limitLevel(s.headphoneWeeklyFraction * 100)
    when {
        daily > dailySeen -> { LimitAlertScreen(env, LimitKind.DAILY, actions) { dailySeen = daily }; return }
        week > weekSeen -> { LimitAlertScreen(env, LimitKind.WEEK_AMBIENT, actions) { weekSeen = week }; return }
        hp > hpSeen -> { LimitAlertScreen(env, LimitKind.WEEK_HEADPHONE, actions) { hpSeen = hp }; return }
    }

    // Destinations read env through State, not the builder's closure: the nav graph keeps the
    // destination lambdas it was first built with, so a captured env would freeze each screen.
    val currentEnv by rememberUpdatedState(env)
    val currentActions by rememberUpdatedState(actions)
    val nav = rememberSwipeDismissableNavController()
    val go: (String) -> Unit = { nav.navigate(it) }
    SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(currentEnv, currentActions, go) }
        composable(Routes.NOW) { NowScreen(currentEnv, currentActions) }
        composable(Routes.HEADPHONES) { HeadphonesScreen(currentEnv, currentActions) }
        composable(Routes.TODAY) { TodayScreen(currentEnv) }
        composable(Routes.WEEK) { WeekScreen(currentEnv, currentActions) }
        composable(Routes.ASK) { AskScreen(currentEnv, currentActions) }
        composable(Routes.SETTINGS) { SettingsScreen(currentEnv, currentActions, go) }
        composable(Routes.CALIBRATION) {
            CalibrationScreen(
                state = currentEnv.state,
                onEnsureMonitoring = currentActions.onEnsureMonitoring,
                onProbe = currentActions.onProbe,
                onSave = currentActions.onSaveCalibration,
                onClose = { nav.popBackStack() },
            )
        }
    }
}
