package com.example.voxara.ui

import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.data.ExposureState
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every redesigned screen at 192 / 216 / 240 dp (Wear minimum, Galaxy Watch 7 40 mm and 44 mm)
 * and font scale 1.0 / 1.3, rendered for visual review into app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenLayoutTest {

    private val hourly = List(24) { h ->
        when (h) { 8 -> 62f; 9 -> 71f; 12 -> 78f; 13 -> 86f; 18 -> 92f; 19 -> 88f; else -> 0f }
    }

    private val state = ExposureState(
        dba = 88.0, dosePercent = 46.0, headroomSeconds = 5400.0, weeklyFraction = 0.38,
        monitoringStatus = MonitoringStatus.RUNNING, monitoring = true, twaDba = 81.6,
        headphoneCategory = HeadphoneCategory.EARBUDS, headphoneListening = true,
        headphoneEstimate = ListeningEstimate(82.0, Confidence.LOW),
        headphoneWeeklyPa2h = 0.9, headphoneWeeklyFraction = 0.56,
        days = (0 until 7).map { i -> DaySummary(20_717L - i, hourly, 46.0 - i * 5, 310.0, 0.1 * (i % 3 + 1)) },
        headphoneDays = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { 0f }, 0.0, 40.0, 0.05 * (i + 1)) },
    )

    private val coach = CoachUi(
        reply = CoachReply(
            status = "Loud café. Your week is on track.",
            insight = "You are at 46% of today's sound allowance and 56% of this week's.",
            why = "At this pace you would reach the weekly limit in about 3 days.",
            suggestion = "Lowering the volume a little buys a lot of listening time.",
            chips = listOf("My week?", "Why estimated?", "Tips"),
        ),
        source = ReplySource.OFFLINE,
    )

    private val env = UiEnv(state = state, coach = coach, cloudAvailable = true)
    private val actions = VoxaraActions()

    @Test fun home() = ScreenshotHarness.captureAll("home") { HomeScreen(env, actions) {} }
    @Test fun homePaused() = ScreenshotHarness.captureAll("home_paused") {
        HomeScreen(env.copy(state = state.copy(monitoringStatus = MonitoringStatus.PAUSED)), actions) {}
    }
    @Test fun now() = ScreenshotHarness.captureAll("now") { NowScreen(env, actions) }
    @Test fun headphones() = ScreenshotHarness.captureAll("headphones") { HeadphonesScreen(env, actions) }
    @Test fun today() = ScreenshotHarness.captureAll("today") { TodayScreen(env) }
    @Test fun week() = ScreenshotHarness.captureAll("week") { WeekScreen(env, actions) }
    @Test fun ask() = ScreenshotHarness.captureAll("ask") { AskScreen(env, actions) }
    @Test fun settings() = ScreenshotHarness.captureAll("settings") { SettingsScreen(env, actions) {} }
    @Test fun onboarding() = ScreenshotHarness.captureAll("onboarding") {
        OnboardingScreen(env.copy(onboarded = false, micGranted = false, notificationsGranted = false), actions)
    }
    @Test fun limitAlert() = ScreenshotHarness.captureAll("limit_alert") {
        LimitAlertScreen(env.copy(state = state.copy(dosePercent = 104.0)), LimitKind.DAILY, actions) {}
    }
    @Test fun ambient() = ScreenshotHarness.captureAll("ambient", ambient = true) { AmbientScreen(env) }
    @Test fun calibration() = ScreenshotHarness.captureAll("calibration") {
        CalibrationScreen(state, onEnsureMonitoring = {}, onProbe = {}, onSave = {}, onClose = {})
    }
}
