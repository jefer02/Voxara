package com.example.voxara.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.OfflineCoach
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.SnapshotBuilder
import com.example.voxara.core.ai.SnapshotInputs
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.data.ExposureState
import com.example.voxara.link.LinkHealth
import com.example.voxara.ui.design.ClockChrome
import com.example.voxara.ui.design.HideClock
import com.example.voxara.ui.design.LocalInitialItem
import com.example.voxara.ui.screens.AmbientScreen
import com.example.voxara.ui.screens.AskScreen
import com.example.voxara.ui.screens.CalibrationScreen
import com.example.voxara.ui.screens.DoseHelpScreen
import com.example.voxara.ui.screens.HeadphonesExitScreen
import com.example.voxara.ui.screens.HeadphoneAlertPanel
import com.example.voxara.ui.screens.HomeScreen
import com.example.voxara.ui.screens.LimitAlertScreen
import com.example.voxara.ui.screens.LimitKind
import com.example.voxara.ui.screens.ModeScreen
import com.example.voxara.ui.screens.OnboardingScreen
import com.example.voxara.ui.screens.SettingsScreen
import com.example.voxara.ui.theme.VoxaraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * THE COPY AND UX AUDIT SET: every watch screen and state the wearer can meet, in English and Spanish, at
 * 192, 216 and 240 dp and font scale 1.0 and 1.3, with the long lists rendered at several scroll positions
 * (`_sNN` = the list item anchored on screen). Tono's text is the real built-in answer for the state shown
 * ([OfflineCoach] on a snapshot built by [SnapshotBuilder]), not a fixture; one cloud reply reproduces the one
 * seen on the real watch.
 *
 * Opt-in (about a thousand images): runs only when `VOXARA_AUDIT_DIR` names where to write them, e.g.
 *     VOXARA_AUDIT_DIR=$PWD/build/audit ./gradlew :app:testDebugUnitTest --tests '*CopyAuditCaptureTest*'
 * `VOXARA_AUDIT_ONLY` (a regex on the screen name) renders a subset; `VOXARA_AUDIT_VARIANT` (e.g. `es_192_1.3`) one variant.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CopyAuditCaptureTest {

    private val sizes = listOf(192, 216, 240)
    private val scales = listOf(1.0f, 1.3f)
    private val locales = listOf("en", "es")
    private val actions = VoxaraActions()
    private val now = System.currentTimeMillis()

    @Before fun onlyWhenAsked() = assumeTrue(!System.getenv("VOXARA_AUDIT_DIR").isNullOrBlank())

    private val outDir: File by lazy { File(System.getenv("VOXARA_AUDIT_DIR")!!).apply { mkdirs() } }
    private val only: Regex? by lazy { System.getenv("VOXARA_AUDIT_ONLY")?.takeIf { it.isNotBlank() }?.toRegex() }
    /** One language, size and scale only, e.g. `es_192_1.3` (`VOXARA_AUDIT_VARIANT`); all twelve when unset. */
    private val variant: String? by lazy { System.getenv("VOXARA_AUDIT_VARIANT")?.takeIf { it.isNotBlank() } }

    // ------------------------------------------------------------------ states

    /** A quiet office day with nothing measured on the past days: what the real watch showed (0 %, Auto · Quiet). */
    private val emptyDays = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { 0f }, 0.0, 0.0, 0.0) }
    private val quiet = ExposureState(
        dba = 52.0, dosePercent = 0.0, weeklyFraction = 0.0,
        monitoringStatus = MonitoringStatus.RUNNING, monitoring = true,
        situationChoice = SituationChoice.AUTO, mode = SituationMode.QUIET, detectedMode = SituationMode.QUIET,
        days = emptyDays, headphoneDays = emptyDays, phoneLink = LinkHealth.Connected(now - 60_000),
    )

    private val hourly = List(24) { h -> when (h) { 8 -> 62f; 9 -> 71f; 12 -> 78f; 13 -> 86f; 18 -> 92f; 19 -> 88f; else -> 0f } }

    /** A loud afternoon: 88 dBA, 46 % of today, a week with data. */
    private val loud = quiet.copy(
        dba = 88.0, dosePercent = 46.0, headroomSeconds = 5400.0, weeklyFraction = 0.38, twaDba = 81.6,
        mode = SituationMode.STREET, detectedMode = SituationMode.STREET,
        days = (0 until 7).map { i -> DaySummary(20_717L - i, hourly, 46.0 - i * 5, 310.0, 0.1 * (i % 3 + 1)) },
        headphoneDays = (0 until 7).map { i -> DaySummary(20_717L - i, List(24) { 0f }, 0.0, 40.0, 0.05 * (i + 1)) },
        headphoneWeeklyFraction = 0.56, headphoneWeeklyPa2h = 0.9,
    )

    private val layerOn = ListeningLayerState(active = true, source = LayerSource.WATCH, detected = true, detectedBy = LayerSource.WATCH)
    private val hpPlaying = quiet.copy(
        dba = 58.0, headphoneCategory = HeadphoneCategory.EARBUDS, headphoneListening = true, listening = layerOn,
        headphoneSource = LayerSource.WATCH, headphoneEstimate = ListeningEstimate(76.0, Confidence.LOW),
        headphoneWeeklyFraction = 0.12, listeningSecondsToday = 2_400.0,
    )
    private val hpSpeaker = hpPlaying.copy(headphonePossibleSpeaker = true)
    private val hpAlert = hpPlaying.copy(
        headphoneEstimate = ListeningEstimate(88.0, Confidence.LOW),
        headphoneAlert = HeadphoneAlert(2, 88.0, now, LayerSource.WATCH),
    )

    // ------------------------------------------------------------------ Tono, as the app words it

    private fun snapshotOf(s: ExposureState, lang: String) = SnapshotBuilder.build(
        SnapshotInputs(
            dosePercent = s.dosePercent, weeklyFraction = s.weeklyFraction, headphoneWeeklyFraction = s.headphoneWeeklyFraction,
            dba = s.dba, simulated = false, sensitiveListener = false, days = s.days, headphoneDays = s.headphoneDays,
            headphoneCategory = s.headphoneCategory?.name, calibrated = s.calibrated, situation = s.mode,
        ),
        lang,
    )

    /** The real built-in answer for [s], in the language being rendered. */
    @Composable
    private fun builtIn(s: ExposureState, task: CoachTask = CoachTask.STATUS): CoachUi {
        val res = LocalContext.current.resources
        val lang = LocalConfiguration.current.locales[0].language
        return remember(s, task, lang) {
            CoachUi(reply = OfflineCoach(res).reply(CoachRequest(task, snapshotOf(s, lang))), source = ReplySource.OFFLINE)
        }
    }

    /**
     * A long cloud reply as the new prompt asks for it (the streak said only when true, the ears not "the sound",
     * "límite"): long on purpose, to show the hero drops what does not fit whole and the Tono screen never cuts it.
     * The reply seen on the real watch was "Llevas 6 días seguidos cuidando tu sonido…" (see build/audit/before).
     */
    @Composable
    private fun cloudSeenOnWatch(): CoachUi {
        val es = LocalConfiguration.current.locales[0].language == "es"
        return CoachUi(
            reply = if (es) CoachReply(
                status = "Llevas 6 días seguidos dentro de tu límite diario.",
                insight = "Hoy vas al 0% de tu límite diario. Si mantienes el volumen así, tus oídos siguen con margen toda la semana.",
                why = "", suggestion = "Sigue así.", chips = listOf("¿Mi semana?", "Consejos"),
            ) else CoachReply(
                status = "6 days in a row within your daily limit.",
                insight = "Today you are at 0% of your daily limit. Keep the volume where it is and your ears keep room all week.",
                why = "", suggestion = "Keep it up.", chips = listOf("My week?", "Tips"),
            ),
            source = ReplySource.CLOUD_AI,
        )
    }

    @Composable
    private fun env(s: ExposureState, task: CoachTask = CoachTask.STATUS, coach: CoachUi? = null) =
        UiEnv(state = s, coach = coach ?: builtIn(s, task), cloudAvailable = true)

    // ------------------------------------------------------------------ capture

    private fun capture(name: String, scroll: Int = 0, ambient: Boolean = false, content: @Composable () -> Unit) {
        if (only?.containsMatchIn(name) == false) return
        for (lang in locales) for (size in sizes) for (scale in scales) {
            if (variant != null && variant != "${lang}_${size}_$scale") continue
            RuntimeEnvironment.setQualifiers("$lang-w${size}dp-h${size}dp-round-xhdpi")
            RuntimeEnvironment.setFontScale(scale)
            val suffix = if (scroll > 0) "_s%02d".format(scroll) else ""
            val file = File(outDir, "${name}${suffix}__${lang}_${size}dp_x$scale.png")
            captureRoboImage(file.path) {
                VoxaraTheme(ambient = ambient, reduceMotion = true) {
                    AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) {
                        CompositionLocalProvider(LocalInitialItem provides scroll, content = content)
                    }
                }
            }
        }
    }

    /** A list rendered at rest and then every [step] items down to [last]. */
    private fun list(name: String, last: Int, step: Int = 2, content: @Composable () -> Unit) {
        for (i in 0..last step step) capture(name, i, content = content)
    }

    // ------------------------------------------------------------------ the pager

    @Test fun nowQuiet() = list("01_now_quiet", 6) { HomeScreen(env(quiet), actions, Routes.NOW) {} }
    @Test fun nowLoud() = capture("02_now_loud") { HomeScreen(env(loud), actions, Routes.NOW) {} }
    @Test fun nowCloudReply() = capture("03_now_cloud") { HomeScreen(env(quiet, coach = cloudSeenOnWatch()), actions, Routes.NOW) {} }
    @Test fun doseTodayQuiet() = list("04_dose_today_quiet", 6) { HomeScreen(env(quiet), actions, Routes.TODAY) {} }
    @Test fun doseTodayLoud() = list("05_dose_today_loud", 6) { HomeScreen(env(loud), actions, Routes.TODAY) {} }
    @Test fun doseWeekQuiet() = capture("06_dose_week_quiet") { HomeScreen(env(quiet), actions, Routes.WEEK) {} }
    @Test fun doseWeekLoud() = list("07_dose_week_loud", 8) { HomeScreen(env(loud), actions, Routes.WEEK) {} }
    @Test fun more() = list("08_more", 6) { HomeScreen(env(quiet), actions, Routes.MENU) {} }

    // ------------------------------------------------------------------ headphones

    @Test fun nowHeadphones() = list("09_now_hp", 8) { HomeScreen(env(hpPlaying), actions, Routes.NOW) {} }
    @Test fun nowHeadphonesSpeaker() = list("10_now_hp_speaker", 4) { HomeScreen(env(hpSpeaker), actions, Routes.NOW) {} }
    @Test fun headphoneAlert() = list("11_hp_alert", 2, step = 1) { HideClock(true); HeadphoneAlertPanel(env(hpAlert), actions) }

    // ------------------------------------------------------------------ overlays and lists

    @Test fun limitAlert() = list("12_limit_alert", 2, step = 1) {
        LimitAlertScreen(env(loud.copy(dosePercent = 104.0)), LimitKind.DAILY, actions) {}
    }
    @Test fun askBuiltIn() = list("13_ask", 10) { AskScreen(env(quiet, CoachTask.DAILY_INSIGHT), actions) }
    @Test fun askCloud() = list("14_ask_cloud", 4) { AskScreen(env(quiet, coach = cloudSeenOnWatch()), actions) }
    @Test fun mode() = list("15_mode", 14) { ModeScreen(env(quiet), actions) }
    @Test fun modeHeadphones() = list("16_mode_hp", 14) { ModeScreen(env(hpPlaying), actions) }
    @Test fun settings() = list("17_settings", 40) { SettingsScreen(env(quiet), actions) {} }
    @Test fun calibration() = list("18_calibration", 14) {
        CalibrationScreen(quiet, onEnsureMonitoring = {}, onProbe = {}, onSave = {}, onClose = {})
    }
    @Test fun onboarding() {
        for (step in 0..6) list("19_onboarding_$step", 6) {
            OnboardingScreen(env(quiet).copy(onboarded = false, micGranted = false, notificationsGranted = false), actions, initialStep = step)
        }
    }
    @Test fun ambient() = capture("20_ambient", ambient = true) { AmbientScreen(env(quiet)) }
    @Test fun ambientHeadphones() = capture("21_ambient_hp", ambient = true) { AmbientScreen(env(hpPlaying)) }

    // ------------------------------------------------------------------ added by the UX/copy pass

    /** "Not headphones?": a Bluetooth device it can remember (two choices) and a wired one (ignore only). */
    @Test fun headphonesExit() = list("22_hp_exit", 4) { HeadphonesExitScreen(env(hpPlaying.copy(headphoneCanRememberSpeaker = true)), actions) {} }
    @Test fun headphonesExitWired() = capture("23_hp_exit_wired") { HeadphonesExitScreen(env(hpPlaying), actions) {} }
    /** What the Dose ring shows (a tap on its number). */
    @Test fun doseHelp() = list("24_dose_help", 2) { DoseHelpScreen {} }
    /** Mode with speakers remembered: the count and "Forget speakers". */
    @Test fun modeRememberedSpeakers() = list("25_mode_speakers", 14) { ModeScreen(env(hpPlaying.copy(rememberedSpeakers = 2)), actions) }
    /** Quiet hours on, with the system clock set to 12-hour: the times follow it ("10:00 p. m."). */
    @Test fun settingsTwelveHour() {
        android.provider.Settings.System.putString(
            RuntimeEnvironment.getApplication().contentResolver, android.provider.Settings.System.TIME_12_24, "12",
        )
        val on = quiet.copy(alertSettings = quiet.alertSettings.copy(quietHoursEnabled = true))
        for (i in listOf(8, 10, 12)) capture("27_settings_12h", i) { SettingsScreen(env(on), actions) {} }
    }
    /** The Dose ring in its other states: over the limit, and the week. */
    @Test fun doseOverLimit() = capture("26_dose_over") { HomeScreen(env(loud.copy(dosePercent = 112.0)), actions, Routes.TODAY) {} }
}
