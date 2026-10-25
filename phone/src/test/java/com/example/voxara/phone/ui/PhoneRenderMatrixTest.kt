package com.example.voxara.phone.ui

import androidx.compose.runtime.Composable
import com.example.voxara.ai.CoachUi
import com.example.voxara.core.design.Accent
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.link.LinkHealth
import com.example.voxara.link.LinkState
import com.example.voxara.link.SyncProblem
import com.example.voxara.phone.listening.PhoneListeningRuntime
import com.example.voxara.phone.ui.PhoneFixtures.calmState
import com.example.voxara.phone.ui.PhoneFixtures.liveOver
import com.example.voxara.phone.ui.PhoneFixtures.now
import com.example.voxara.phone.ui.PhoneFixtures.state
import com.example.voxara.phone.ui.PhoneFixtures.status
import com.example.voxara.phone.ui.PhoneFixtures.watch
import com.example.voxara.phone.ui.screens.AlertsScreen
import com.example.voxara.phone.ui.screens.HeadphonesScreen
import com.example.voxara.phone.ui.screens.HomeScreen
import com.example.voxara.phone.ui.screens.HowItWorksScreen
import com.example.voxara.phone.ui.screens.SettingsScreen
import com.example.voxara.phone.ui.screens.SetupScreen
import com.example.voxara.phone.ui.screens.TodayScreen
import com.example.voxara.phone.ui.screens.TonoScreen
import com.example.voxara.phone.ui.screens.TrendRange
import com.example.voxara.phone.ui.screens.TrendsScreen
import com.example.voxara.phone.ui.screens.WatchScreen
import com.example.voxara.phone.ui.theme.VoxaraPhoneTheme
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
 * THE REDESIGN'S RENDER MATRIX. Every phone screen at phone / foldable / tablet widths, font scale 1.0 / 1.3 / 2.0, light and
 * dark, each inside the real navigation (the four tabs: a bar on the phone, a rail on the foldable and the tablet), the
 * screens that matter most also full length, plus the states of Home, the Watch screen and the headphones. It writes into
 * `$VOXARA_SHOTS_DIR`, so the same test makes the "before" and the "after" sets of `docs/design/phone-redesign/`:
 *
 *     VOXARA_SHOTS_DIR=docs/design/phone-redesign/after ./gradlew :phone:testDebugUnitTest --tests '*PhoneRenderMatrixTest*'
 *
 * Fake data only ([PhoneFixtures]); nothing touches the network or the Data Layer. Reduced motion is on so every frame is final.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PhoneRenderMatrixTest {

    /** Renders are opt-in (hundreds of images): they run only when `VOXARA_SHOTS_DIR` names where to write them. */
    @Before fun onlyWhenAsked() = assumeTrue(!System.getenv("VOXARA_SHOTS_DIR").isNullOrBlank())

    private data class Size(val name: String, val screen: String, val density: String)

    private val phone = Size("phone", "w360dp-h800dp", "xxhdpi")
    private val foldable = Size("foldable", "w673dp-h841dp", "xhdpi")
    private val tablet = Size("tablet", "w1280dp-h800dp-land", "xhdpi")
    private val sizes = listOf(phone, foldable, tablet)
    private val scales = listOf(1.0f, 1.3f, 2.0f)

    private val outDir by lazy {
        val dir = System.getenv("VOXARA_SHOTS_DIR")?.takeIf { it.isNotBlank() }
        (if (dir != null) File(dir).let { if (it.isAbsolute) it else File(System.getProperty("user.dir"), "../$dir") }
        else File(System.getProperty("user.dir"), "build/screenshots/matrix")).apply { mkdirs() }
    }

    private val actions = PhoneActions()

    /** The screen inside the app's navigation, with [tab] selected (no tab: the screen alone, as setup is). */
    @Composable
    private fun Framed(tab: String?, content: @Composable () -> Unit) {
        if (tab == null) content() else VoxNavigationSuite(selectedTab = tab, onTab = {}) { content() }
    }

    private fun capture(
        name: String, size: Size, scale: Float, dark: Boolean, tab: String?, accent: Accent = Accent.DEFAULT, heightDp: Int? = null,
        content: @Composable () -> Unit,
    ) {
        val screen = if (heightDp == null) size.screen else "w360dp-h${heightDp}dp"
        RuntimeEnvironment.setQualifiers("$screen-${if (dark) "night" else "notnight"}-${if (heightDp == null) size.density else "xhdpi"}")
        RuntimeEnvironment.setFontScale(scale)
        val file = File(outDir, "${name}_${if (heightDp == null) size.name else "full"}_x$scale${if (dark) "_dark" else "_light"}.png")
        captureRoboImage(file.path) { VoxaraPhoneTheme(darkTheme = dark, accent = accent, reduceMotion = true) { Framed(tab, content) } }
    }

    /** Every size, every font scale, light and dark: 18 images. */
    private fun matrix(name: String, tab: String?, content: @Composable () -> Unit) {
        for (size in sizes) for (scale in scales) for (dark in listOf(false, true)) capture(name, size, scale, dark, tab, content = content)
    }

    /** One phone, font 1.0 and 2.0, light and dark: a state of a screen. */
    private fun states(name: String, tab: String?, content: @Composable () -> Unit) {
        for (scale in listOf(1.0f, 2.0f)) for (dark in listOf(false, true)) capture(name, phone, scale, dark, tab, content = content)
    }

    /** The whole list in one image (a tall canvas), light and dark at font 1.0. */
    private fun full(name: String, tab: String?, heightDp: Int = 2400, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) capture(name, phone, 1.0f, dark, tab, heightDp = heightDp, content = content)
    }

    @Test fun home() { matrix("home", PhoneRoutes.HOME) { HomeScreen(state, actions) {} }; full("home", PhoneRoutes.HOME) { HomeScreen(state, actions) {} } }
    @Test fun today() { matrix("today", PhoneRoutes.HOME) { TodayScreen(state, {}, {}) }; full("today", PhoneRoutes.HOME, 2600) { TodayScreen(state, {}, {}) } }
    @Test fun alerts() { matrix("alerts", PhoneRoutes.HOME) { AlertsScreen(state, {}, {}) }; full("alerts", PhoneRoutes.HOME) { AlertsScreen(state, {}, {}) } }
    @Test fun trends() {
        matrix("trends", PhoneRoutes.TRENDS) { TrendsScreen(state, {}) }
        for (range in TrendRange.entries) full("trends_${range.name.lowercase()}", PhoneRoutes.TRENDS, 2300) { TrendsScreen(state, {}, initialRange = range) }
    }
    @Test fun tono() { matrix("tono", PhoneRoutes.HOME) { TonoScreen(state, actions) }; full("tono", PhoneRoutes.HOME) { TonoScreen(state, actions) } }
    @Test fun watch() { matrix("watch", PhoneRoutes.WATCH) { WatchScreen(state, actions) }; full("watch", PhoneRoutes.WATCH) { WatchScreen(state, actions) } }
    @Test fun headphones() { matrix("headphones", PhoneRoutes.HOME) { HeadphonesScreen(state, actions, {}) }; full("headphones", PhoneRoutes.HOME) { HeadphonesScreen(state, actions, {}) } }
    @Test fun settings() { matrix("settings", PhoneRoutes.SETTINGS) { SettingsScreen(state, actions) {} }; full("settings", PhoneRoutes.SETTINGS, 4200) { SettingsScreen(state, actions) {} } }
    @Test fun how() = matrix("how", PhoneRoutes.SETTINGS) { HowItWorksScreen {} }
    @Test fun app() = matrix("app", null) { PhoneApp(state, actions) }

    @Test fun homeStates() {
        states("home_loading", PhoneRoutes.HOME) { HomeScreen(state.copy(loaded = false), actions) {} }
        states("home_no_watch", PhoneRoutes.HOME) { HomeScreen(PhoneUiState(link = LinkState.NoWatch, nowMs = now), actions) {} }
        states("home_not_installed", PhoneRoutes.HOME) { HomeScreen(PhoneUiState(link = LinkState.NotInstalled(listOf(watch)), nowMs = now), actions) {} }
        states("home_stale", PhoneRoutes.HOME) { HomeScreen(state.copy(status = status.copy(atMs = now - 26 * 3_600_000L), lastSyncMs = now - 26 * 3_600_000L), actions) {} }
        states("home_calm", PhoneRoutes.HOME) { HomeScreen(calmState.copy(status = PhoneFixtures.calmStatus.copy(calibrated = true)), actions) {} }
        states("home_limit", PhoneRoutes.HOME) { HomeScreen(state.copy(status = status.copy(dosePercent = 112.0, weeklyPercent = 96.0, calibrated = true)), actions) {} }
        states("home_hp", PhoneRoutes.HOME) {
            HomeScreen(
                state.copy(status = status.copy(situationMode = SituationMode.TRANSPORT, headphonesMode = true, headphonesSource = LayerSource.WATCH)),
                actions,
            ) {}
        }
        states("home_hp_alert", PhoneRoutes.HOME) { HomeScreen(state.copy(phoneSampling = true, phoneLive = liveOver), actions) {} }
        val gone = state.copy(linkHealth = LinkHealth.Disconnected(now - 47 * 60_000L), link = LinkState.NoWatch, pendingSpans = 3, batteryRestricted = true)
        states("home_disconnected", PhoneRoutes.HOME) { HomeScreen(gone, actions) {} }
    }

    @Test fun accents() {
        val calm = calmState
        for (accent in Accent.entries) for (dark in listOf(false, true)) {
            capture("home_accent_${accent.name.lowercase()}", phone, 1.0f, dark, PhoneRoutes.HOME, accent) { HomeScreen(calm.copy(accent = accent), actions) {} }
            for (range in listOf(TrendRange.WEEK, TrendRange.MONTH)) {
                capture("trends_${range.name.lowercase()}_accent_${accent.name.lowercase()}", phone, 1.0f, dark, PhoneRoutes.TRENDS, accent, heightDp = 1900) {
                    TrendsScreen(state.copy(accent = accent), {}, initialRange = range)
                }
            }
        }
    }

    @Test fun watchStates() {
        states("watch_problem", PhoneRoutes.WATCH) {
            WatchScreen(state.copy(link = LinkState.Problem(watch, emptyList(), SyncProblem.UNREACHABLE, now - 5 * 3_600_000L)), actions)
        }
        states("watch_not_installed", PhoneRoutes.WATCH) { WatchScreen(state.copy(link = LinkState.NotInstalled(listOf(watch)), status = null, debugBuild = true), actions) }
        states("watch_reconnecting", PhoneRoutes.WATCH) { WatchScreen(state.copy(linkHealth = LinkHealth.Reconnecting(now - 20_000), pendingSpans = 3), actions) }
    }

    @Test fun headphoneStates() {
        val hp = state.copy(phoneSampling = true, bluetoothGranted = false)
        states("headphones_live_over", PhoneRoutes.HOME) { HeadphonesScreen(hp.copy(phoneLive = liveOver), actions, {}) }
        states("headphones_live_calm", PhoneRoutes.HOME) {
            val calm = PhoneFixtures.hpSnapshot.copy(volumeIndex = 6, volumeDb = -30.0)
            HeadphonesScreen(hp.copy(phoneLive = PhoneListeningRuntime.Live(running = true, snapshot = calm)), actions, {})
        }
        states("headphones_empty", PhoneRoutes.HOME) { HeadphonesScreen(hp.copy(phoneLive = PhoneListeningRuntime.Live(running = true)), actions, {}) }
        states("tono_cloud_off", PhoneRoutes.HOME) { TonoScreen(state.copy(chat = emptyList(), coach = CoachUi()), actions) }
    }

    @Test fun setup() {
        for (step in 0..6) for (dark in listOf(false, true)) {
            capture("setup_$step", phone, 1.0f, dark, null) { SetupScreen(state.copy(setupDone = false), actions, initialStep = step) }
        }
        capture("setup_0", phone, 2.0f, false, null) { SetupScreen(state.copy(setupDone = false), actions, initialStep = 0) }
        capture("setup_3", tablet, 1.0f, false, null) { SetupScreen(state.copy(setupDone = false), actions, initialStep = 3) }
    }
}
