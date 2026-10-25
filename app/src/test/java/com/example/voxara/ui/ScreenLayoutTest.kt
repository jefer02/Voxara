package com.example.voxara.ui

import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.ReferenceType
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.screens.AmbientScreen
import com.example.voxara.ui.screens.AskScreen
import com.example.voxara.ui.screens.CalibrationScreen
import com.example.voxara.ui.screens.HomeScreen
import com.example.voxara.ui.screens.LimitAlertScreen
import com.example.voxara.ui.screens.LimitKind
import com.example.voxara.ui.screens.ModeScreen
import com.example.voxara.ui.screens.OnboardingScreen
import com.example.voxara.ui.screens.SettingsScreen
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
            insight = "You are at 46% of today's limit and 56% of this week's.",
            why = "At this pace you would reach the weekly limit in about 3 days.",
            suggestion = "Lowering the volume a little buys a lot of listening time.",
            chips = listOf("My week?", "Why estimated?", "Tips"),
        ),
        source = ReplySource.OFFLINE,
    )

    private val env = UiEnv(state = state, coach = coach, cloudAvailable = true)
    private val actions = VoxaraActions()

    /** Headphones mode on: playing (estimated level), nothing playing, and loud listening. */
    private val layerOn = ListeningLayerState(active = true, source = LayerSource.WATCH, detected = true, detectedBy = LayerSource.WATCH)
    private val hpEnv = env.copy(state = state.copy(headphoneMode = HeadphoneModeChoice.AUTO, listening = layerOn, dba = 68.0))
    private val hpIdleEnv = env.copy(state = hpEnv.state.copy(headphoneListening = false, headphoneEstimate = null))
    private val hpLoudEnv = env.copy(
        state = hpEnv.state.copy(headphoneEstimate = ListeningEstimate(98.0, Confidence.LOW), listening = layerOn.copy(source = LayerSource.WATCH)),
    )

    @Test fun nowHeadphonesMode() = ScreenshotHarness.captureAll("now_hp") { HomeScreen(hpEnv, actions) {} }
    @Test fun nowHeadphonesIdle() = ScreenshotHarness.captureAll("now_hp_idle") { HomeScreen(hpIdleEnv, actions) {} }
    @Test fun nowHeadphonesLoud() = ScreenshotHarness.captureAll("now_hp_loud") { HomeScreen(hpLoudEnv, actions) {} }
    @Test fun nowHeadphonesModeSpanish() = ScreenshotHarness.captureAll("es_now_hp", locale = "es") { HomeScreen(hpEnv, actions) {} }
    @Test fun nowHeadphonesDetails() = details("now_hp") { HomeScreen(hpEnv, actions) {} }
    @Test fun modeHeadphones() = ScreenshotHarness.captureAll("mode_hp") { ModeScreen(hpEnv, actions) }
    /** The Mode screen scrolled to its Headphones mode section. */
    @Test fun modeHeadphonesSection() = ScreenshotHarness.captureAll("mode_hp_section") {
        androidx.compose.runtime.CompositionLocalProvider(com.example.voxara.ui.design.LocalInitialItem provides 5) {
            ModeScreen(hpEnv, actions)
        }
    }
    @Test fun ambientHeadphones() = ScreenshotHarness.captureAll("ambient_hp", ambient = true) { AmbientScreen(hpEnv) }

    /** Tono's tag on the hero, long, in both languages: it must fit without an ellipsis (or be the first detail). */
    private fun longStatus(text: String) = env.copy(coach = coach.copy(reply = coach.reply!!.copy(status = text)))
    @Test fun nowLongStatus() = ScreenshotHarness.captureAll("now_long") {
        HomeScreen(longStatus("Moderate right now, but your ears are fine today. Keep the volume low and take a quiet break soon."), actions) {}
    }
    @Test fun nowLongStatusSpanish() = ScreenshotHarness.captureAll("es_now_long", locale = "es") {
        HomeScreen(longStatus("Moderado ahora mismo, pero tus oídos están bien hoy. Mantén el volumen bajo y descansa un rato pronto."), actions) {}
    }

    @Test fun now() = ScreenshotHarness.captureAll("now") { HomeScreen(env, actions) {} }
    /** Calibrated: the note under the zone is gone, so the foot has more room. */
    @Test fun nowCalibrated() = ScreenshotHarness.captureAll("now_calibrated") { HomeScreen(env.copy(state = state.copy(calibrations = mapOf(MicSource.VOICE_RECOGNITION to CalibrationRecord(MicSource.VOICE_RECOGNITION, 3.0, 0L, "test", ReferenceType.SOUND_LEVEL_METER)))), actions) {} }
    @Test fun nowPaused() = ScreenshotHarness.captureAll("now_paused") {
        HomeScreen(env.copy(state = state.copy(monitoringStatus = MonitoringStatus.PAUSED)), actions) {}
    }
    /** The pages below their heroes (first detail at the top), where the foot and the lists live. */
    private fun details(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        ScreenshotHarness.captureAll("${name}_details") {
            androidx.compose.runtime.CompositionLocalProvider(com.example.voxara.ui.design.LocalInitialItem provides 1, content = content)
        }
    @Test fun nowDetails() = details("now") { HomeScreen(env, actions) {} }
    @Test fun doseDetails() = details("dose") { HomeScreen(env, actions, Routes.TODAY) {} }
    @Test fun doseWeekDetails() = details("dose_week") { HomeScreen(env, actions, Routes.WEEK) {} }
    @Test fun menu() = ScreenshotHarness.captureAll("menu") { HomeScreen(env, actions, Routes.MENU) {} }
    /** The last page scrolled to its end: every item must rest above the page indicator. */
    @Test fun menuEnd() = ScreenshotHarness.captureAll("menu_end") {
        androidx.compose.runtime.CompositionLocalProvider(com.example.voxara.ui.design.LocalInitialItem provides 4) {
            HomeScreen(env, actions, Routes.MENU) {}
        }
    }
    /** Spanish: the longest words (Auriculares, Peligroso, decimal comma) at every size. */
    @Test fun spanish() {
        for ((name, route) in listOf("now" to Routes.NOW, "dose" to Routes.TODAY, "dose_week" to Routes.WEEK, "menu" to Routes.MENU)) {
            ScreenshotHarness.captureAll("es_$name", locale = "es") { HomeScreen(env, actions, route) {} }
        }
        ScreenshotHarness.captureAll("es_settings", locale = "es") { SettingsScreen(env, actions) {} }
        ScreenshotHarness.captureAll("es_limit_alert", locale = "es") {
            LimitAlertScreen(env.copy(state = state.copy(dosePercent = 104.0)), LimitKind.DAILY, actions) {}
        }
    }
    @Test fun mode() = ScreenshotHarness.captureAll("mode") { ModeScreen(env, actions) }
    @Test fun dose() = ScreenshotHarness.captureAll("dose") { HomeScreen(env, actions, Routes.TODAY) {} }
    @Test fun doseWeek() = ScreenshotHarness.captureAll("dose_week") { HomeScreen(env, actions, Routes.WEEK) {} }
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
