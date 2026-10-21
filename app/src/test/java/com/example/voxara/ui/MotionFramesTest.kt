package com.example.voxara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.ListeningLayerState
import androidx.compose.runtime.mutableStateOf
import com.example.voxara.core.ledger.DaySummary
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.example.voxara.ui.design.ClockChrome
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.screens.HomeScreen
import com.example.voxara.ui.screens.LimitAlertScreen
import com.example.voxara.ui.screens.LimitKind
import com.example.voxara.ui.screens.NowScreen
import com.example.voxara.ui.theme.VoxaraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Frame strips of the main animations, captured with a manual frame clock (time advances only when
 * the test says so), at 216 dp x1.0. Review images in app/build/screenshots/motion_*; the numbers in
 * the names are milliseconds since the trigger. Not golden files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class MotionFramesTest {

    @get:Rule val rule = createComposeRule()

    private val out = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun base(dba: Double) = ExposureState(
        dba = dba, dosePercent = 46.0, headroomSeconds = 5400.0, weeklyFraction = 0.38,
        monitoringStatus = MonitoringStatus.RUNNING, monitoring = true, twaDba = 81.6,
    )

    private fun frames(name: String, at: List<Int>, trigger: () -> Unit = {}) {
        var now = 0
        for (t in at) {
            if (t == 0) trigger()
            if (t > now) { rule.mainClock.advanceTimeBy((t - now).toLong()); now = t }
            rule.onRoot().captureRoboImage(File(out, "motion_${name}_%03d.png".format(t)).path)
        }
    }

    private fun setup() {
        RuntimeEnvironment.setQualifiers("w216dp-h216dp-round-xhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        rule.mainClock.autoAdvance = false
    }

    /** The bezel sweeps from empty to the live level and the zone colour settles. */
    @Test fun arcSweepsIn() {
        setup()
        rule.setContent { VoxaraTheme { AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { NowScreen(UiEnv(state = base(88.0)), VoxaraActions()) } } }
        frames("arc", listOf(0, 120, 240, 400, 650))
    }

    /** The level number rolls digit by digit instead of jumping, and the rings and tone follow. */
    @Test fun numberRolls() {
        setup()
        var dba by mutableDoubleStateOf(79.0)
        rule.setContent { VoxaraTheme { AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { NowScreen(UiEnv(state = base(dba)), VoxaraActions()) } } }
        rule.mainClock.advanceTimeBy(900)
        frames("roll", listOf(0, 60, 120, 200, 420)) { dba = 88.0 }
    }

    /**
     * Changing page is a change, not only a slide: the new page's picture draws itself in while the old
     * one retracts, and both pictures turn with the swipe. Drag from Now towards Today, hold, release.
     */
    @Test fun pageTransition() {
        setup()
        val env = UiEnv(
            state = base(88.0).copy(
                days = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { h -> if (h in 8..19) 60f + h * 2f else 0f }, 46.0 - i * 5, 310.0, 0.1 * (i % 3 + 1)) },
            ),
        )
        rule.setContent { VoxaraTheme { AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { HomeScreen(env, VoxaraActions(), Routes.NOW) {} } } }
        rule.mainClock.advanceTimeBy(1_000)
        val root = rule.onRoot()
        var shot = 0
        fun snap(label: String) = root.captureRoboImage(File(out, "motion_page_%d_%s.png".format(shot++, label)).path)
        snap("rest")
        root.performTouchInput { down(Offset(width * 0.75f, height * 0.5f)) }
        root.performTouchInput { moveBy(Offset(-width * 0.18f, 0f)) }
        rule.mainClock.advanceTimeBy(32)
        snap("drag25")
        root.performTouchInput { moveBy(Offset(-width * 0.22f, 0f)) }
        rule.mainClock.advanceTimeBy(32)
        snap("drag50")
        root.performTouchInput { up() }
        for ((i, ms) in listOf(120L, 130L, 150L, 250L, 450L).withIndex()) {
            rule.mainClock.advanceTimeBy(ms)
            snap("settle$i")
        }
    }

    /**
     * HEADPHONES MODE — the signature transition on the Overview hero, on a manual clock: the ring splits
     * into two arcs and turns indigo, the waves go stereo, the figure rolls from the outside level to the
     * estimated listening level while the outside level flies out to the moon, and the label settles into the chip.
     */
    @Test fun headphonesModeEnters() = headphonesTransition(enter = true)

    /** ...and the reverse: the arcs rejoin, the moon returns to the centre and is the figure again. */
    @Test fun headphonesModeLeaves() = headphonesTransition(enter = false)

    private fun headphonesTransition(enter: Boolean) {
        setup()
        val on = ListeningLayerState(active = true, source = LayerSource.MANUAL, detected = true, detectedBy = LayerSource.WATCH)
        val state = base(68.0).copy(
            headphoneCategory = HeadphoneCategory.EARBUDS, headphoneListening = true,
            headphoneEstimate = ListeningEstimate(84.0, Confidence.LOW), headphoneWeeklyFraction = 0.2,
            headphoneMode = HeadphoneModeChoice.ON,
            listening = if (enter) ListeningLayerState.OFF else on,
        )
        val env = mutableStateOf(UiEnv(state = state))
        rule.setContent { VoxaraTheme { AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { HomeScreen(env.value, VoxaraActions()) {} } } }
        rule.mainClock.advanceTimeBy(1_200)
        val name = if (enter) "hp_enter" else "hp_leave"
        frames(name, listOf(0, 90, 180, 270, 360, 450, 540, 630, 720, 900)) {
            env.value = env.value.copy(state = env.value.state.copy(listening = if (enter) on else ListeningLayerState.OFF))
        }
    }

    /**
     * DOSE — the Today to Week morph on a manual clock: the 24 bars retract into the rim one after another round the
     * ring while the seven day segments fill in behind them, the figure rolls from the day's share to the week's and
     * the caption cross-fades. Reduced motion: the two pictures cross-fade.
     */
    @Test fun doseMorphsToWeek() = doseMorph(reduce = false)
    @Test fun doseCrossfadesToWeek() = doseMorph(reduce = true)

    private fun doseMorph(reduce: Boolean) {
        setup()
        val state = base(70.0).copy(
            dosePercent = 46.0, weeklyFraction = 0.56,
            days = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { h -> if (h in 8..19) 60f + h * 2f else 0f }, 46.0 - i * 5, 310.0, 0.1 * (i % 3 + 1)) },
        )
        rule.setContent {
            VoxaraTheme(reduceMotion = reduce) { AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { HomeScreen(UiEnv(state = state), VoxaraActions(), Routes.TODAY) {} } }
        }
        rule.mainClock.advanceTimeBy(1_200)
        val name = if (reduce) "dose_fade" else "dose_morph"
        frames(name, listOf(0, 60, 120, 180, 240, 300, 380, 460, 600)) {
            rule.onNodeWithContentDescription("Week").performTouchInput { click() }
        }
    }

    /** The limit alert pulses on each beat of its haptic (daily: three clicks and a thud at 0/150/300/470 ms). */
    @Test fun limitPulse() {
        setup()
        rule.setContent {
            VoxaraTheme { AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { LimitAlertScreen(UiEnv(state = base(70.0).copy(dosePercent = 104.0)), LimitKind.DAILY, VoxaraActions()) {} } }
        }
        frames("limit", listOf(0, 40, 150, 190, 470, 520, 700))
    }
}
