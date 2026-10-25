package com.example.voxara.ui

import androidx.compose.runtime.CompositionLocalProvider
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.data.ExposureState
import com.example.voxara.link.LinkHealth
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.ui.design.HideClock
import com.example.voxara.ui.design.LocalInitialItem
import com.example.voxara.ui.screens.HeadphoneAlertPanel
import com.example.voxara.ui.screens.ModeScreen
import com.example.voxara.ui.screens.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * THE HEADPHONES VIEWS, at the four sizes that matter (192 dp small round, 203 dp Galaxy Watch 7, 216 dp 40 mm,
 * 240 dp 44 mm) and font scale 1.0 and 1.3 (the alert panel also at 2.0), in English and Spanish. Rendered for
 * review into app/build/screenshots/ (not golden files). Every view is also checked to have produced an image.
 *
 *  - the headphone-volume alert panel: above the recommended level (watch, phone), lowering, and each result;
 *  - Now in headphones mode: playing, a possible speaker, the alert over it;
 *  - what the page says below the fold (listening minutes today);
 *  - the Mode screen (automatic: nothing to choose), the More page with the phone link in each state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HeadphoneScreensTest {

    private val sizes = listOf(192, 203, 216, 240)
    private val scales = listOf(1.0f, 1.3f)
    private val actions = VoxaraActions()
    private val now = System.currentTimeMillis()

    private val layerOn = ListeningLayerState(active = true, source = LayerSource.WATCH, detected = true, detectedBy = LayerSource.WATCH)
    private val base = ExposureState(
        dba = 64.0, dosePercent = 46.0, weeklyFraction = 0.38, monitoringStatus = MonitoringStatus.RUNNING, monitoring = true,
        headphoneCategory = HeadphoneCategory.EARBUDS, headphoneListening = true, listening = layerOn,
        headphoneSource = LayerSource.WATCH,
        headphoneEstimate = ListeningEstimate(92.0, Confidence.LOW), headphoneWeeklyPa2h = 0.9, headphoneWeeklyFraction = 0.56,
        listeningSecondsToday = 2_400.0,
        days = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { 0f }, 46.0 - i * 5, 310.0, 0.1) },
        headphoneDays = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { 0f }, 0.0, if (i == 0) 12.0 else 40.0, 0.05 * (i + 1)) },
    )
    private val coach = CoachUi(
        reply = CoachReply("Loud café. Your week is on track.", "You are at 46% today.", "", "Lower the volume a little.", emptyList()),
        source = ReplySource.OFFLINE,
    )
    private fun env(s: ExposureState) = UiEnv(state = s, coach = coach, cloudAvailable = true)

    private fun alert(tier: Int, dba: Double, source: LayerSource = LayerSource.WATCH) =
        base.copy(
            headphoneEstimate = ListeningEstimate(dba, Confidence.LOW), headphoneSource = source,
            headphoneAlert = HeadphoneAlert(tier, dba, now, source),
            listening = if (source == LayerSource.PHONE) layerOn.copy(detectedBy = LayerSource.PHONE, source = LayerSource.PHONE) else layerOn,
        )

    private val captured = ArrayList<String>()

    /** English and Spanish, four sizes, 1.0 and 1.3 (and 2.0 when [large]). */
    private fun both(name: String, large: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        for (locale in listOf("" to name, "es" to "es_$name")) {
            for (size in sizes) for (scale in scales + if (large) listOf(2.0f) else emptyList()) {
                ScreenshotHarness.capture(locale.second, size, scale, locale = locale.first, content = content)
                captured += "${locale.second}_${size}dp_x$scale.png"
            }
        }
    }

    // ------------------------------------------------------------------ the alert panel

    @Test fun panelAboveRecommended() = both("hp_alert_t1", large = true) { HideClock(true); HeadphoneAlertPanel(env(alert(1, 84.0)), actions) }
    @Test fun panelDangerousOnPhone() = both("hp_alert_t3_phone", large = true) { HideClock(true); HeadphoneAlertPanel(env(alert(3, 101.0, LayerSource.PHONE)), actions) }
    @Test fun panelLowering() = both("hp_alert_working") { HideClock(true); HeadphoneAlertPanel(env(alert(2, 92.0).copy(lowerVolume = LowerUi.Working)), actions) }
    @Test fun panelLowered() = both("hp_alert_lowered") {
        HideClock(true)
        HeadphoneAlertPanel(env(alert(2, 92.0).copy(lowerVolume = LowerUi.Done(LowerOutcome.LOWERED, 79.0))), actions)
    }
    @Test fun panelFixedVolume() = both("hp_alert_fixed", large = true) {
        HideClock(true)
        HeadphoneAlertPanel(env(alert(2, 92.0).copy(lowerVolume = LowerUi.Done(LowerOutcome.FIXED_VOLUME, null))), actions)
    }
    @Test fun panelPhoneUnreachable() = both("hp_alert_unreachable", large = true) {
        HideClock(true)
        HeadphoneAlertPanel(env(alert(2, 92.0, LayerSource.PHONE).copy(lowerVolume = LowerUi.Done(LowerOutcome.UNREACHABLE, null))), actions)
    }
    /** Below the fold of the panel: the Dismiss action, the explanation and whose headphones. */
    @Test fun panelDismissBelowTheFold() = both("hp_alert_dismiss") {
        HideClock(true)
        CompositionLocalProvider(LocalInitialItem provides 1) { HeadphoneAlertPanel(env(alert(1, 84.0)), actions) }
    }

    // ------------------------------------------------------------------ Now in headphones mode

    @Test fun nowPlaying() = both("hp_now_playing") { HomeScreen(env(base.copy(headphoneEstimate = ListeningEstimate(76.0, Confidence.LOW))), actions) {} }
    @Test fun nowPossibleSpeaker() = both("hp_now_speaker") {
        HomeScreen(env(base.copy(headphoneEstimate = ListeningEstimate(76.0, Confidence.LOW), headphonePossibleSpeaker = true)), actions) {}
    }
    @Test fun nowFromPhone() = both("hp_now_phone") {
        HomeScreen(env(base.copy(headphoneSource = LayerSource.PHONE, listening = layerOn.copy(source = LayerSource.PHONE, detectedBy = LayerSource.PHONE))), actions) {}
    }
    @Test fun nowWithTheAlertOverIt() = both("hp_now_alert") { HomeScreen(env(alert(2, 92.0)), actions) {} }
    @Test fun nowDetailsListeningMinutes() = both("hp_now_details") {
        CompositionLocalProvider(LocalInitialItem provides 1) {
            HomeScreen(env(base.copy(headphoneEstimate = ListeningEstimate(76.0, Confidence.LOW), headphonePossibleSpeaker = true)), actions) {}
        }
    }

    // ------------------------------------------------------------------ Mode and More

    @Test fun modeIsAutomatic() = both("hp_mode") { ModeScreen(env(base), actions) }
    @Test fun modeHeadphonesSection() = both("hp_mode_section") {
        CompositionLocalProvider(LocalInitialItem provides 5) { ModeScreen(env(base.copy(headphonePossibleSpeaker = true)), actions) }
    }
    @Test fun moreConnected() = both("link_connected") {
        CompositionLocalProvider(LocalInitialItem provides 3) { HomeScreen(env(base.copy(phoneLink = LinkHealth.Connected(now - 60_000))), actions, Routes.MENU) {} }
    }
    @Test fun moreReconnecting() = both("link_reconnecting") {
        CompositionLocalProvider(LocalInitialItem provides 3) { HomeScreen(env(base.copy(phoneLink = LinkHealth.Reconnecting(now - 20_000))), actions, Routes.MENU) {} }
    }
    @Test fun moreDisconnected() = both("link_disconnected") {
        CompositionLocalProvider(LocalInitialItem provides 3) { HomeScreen(env(base.copy(phoneLink = LinkHealth.Disconnected(now - 12 * 60_000))), actions, Routes.MENU) {} }
    }

    /** Every view above produced its images (a layout that throws would leave none). */
    @Test fun everyImageWasWritten() {
        panelAboveRecommended()
        val dir = File(System.getProperty("user.dir"), "build/screenshots")
        assertNotNull(dir.listFiles())
        val names = dir.listFiles()!!.map { it.name }.toSet()
        assertTrue(captured.isNotEmpty())
        for (n in captured) assertTrue("missing $n", n in names)
        assertEquals(2 * sizes.size * 3, captured.size)
    }
}
