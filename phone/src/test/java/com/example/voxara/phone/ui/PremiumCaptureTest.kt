package com.example.voxara.phone.ui

import androidx.compose.runtime.Composable
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.phone.ui.PhoneFixtures.liveOver
import com.example.voxara.phone.ui.PhoneFixtures.reply
import com.example.voxara.phone.ui.PhoneFixtures.state
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
 * THE PREMIUM PASS'S RENDERS (docs/design/PHONE_PREMIUM.md, docs/design/phone-premium/ROLLOUT.md): every screen the pass
 * changed, in Spanish, on one phone (360 dp), inside the real navigation: light and dark at font 1.0, dark at font 2.0.
 * Opt-in: it writes into `$VOXARA_PREMIUM_DIR` only, so the same test makes the "before" and the "after" sets:
 *
 *     VOXARA_PREMIUM_DIR=docs/design/phone-premium/after ./gradlew :phone:testDebugUnitTest --tests '*PremiumCaptureTest*'
 *
 * Fake data only ([PhoneFixtures]); Tono's words are the Spanish ones the AI would write, with the fixture's numbers.
 * Reduced motion is on so every frame is final.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PremiumCaptureTest {

    @Before fun onlyWhenAsked() = assumeTrue(!System.getenv("VOXARA_PREMIUM_DIR").isNullOrBlank())

    private val outDir by lazy {
        val dir = File(System.getenv("VOXARA_PREMIUM_DIR")!!)
        (if (dir.isAbsolute) dir else File(System.getProperty("user.dir"), "../$dir")).apply { mkdirs() }
    }

    private val actions = PhoneActions()
    private val esReply = reply.copy(
        status = "Una tarde intensa para tus oídos",
        insight = "Vas en el 83 % del límite de hoy. El tramo más fuerte fue cerca de las 14:00.",
        why = "Casi todo vino de veinte minutos cerca de las 14:00.",
        suggestion = "Una noche más tranquila mantiene la semana en buen camino.",
        chips = listOf("¿Mi semana?", "¿Por qué estimado?", "Consejos"),
    )
    private val es = state.copy(
        coach = CoachUi(reply = esReply, source = ReplySource.CLOUD_AI),
        chat = listOf(ChatTurn("¿Qué tan fuerte fue el almuerzo?", esReply, ReplySource.CLOUD_AI, cloudFailed = false)),
    )

    private fun capture(name: String, tab: String?, scale: Float, dark: Boolean, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("es-w360dp-h800dp-${if (dark) "night" else "notnight"}-xxhdpi")
        RuntimeEnvironment.setFontScale(scale)
        val file = File(outDir, "${name}_x$scale${if (dark) "_dark" else "_light"}.png")
        captureRoboImage(file.path) {
            VoxaraPhoneTheme(darkTheme = dark, reduceMotion = true) {
                if (tab == null) content() else VoxNavigationSuite(selectedTab = tab, onTab = {}) { content() }
            }
        }
    }

    /** Light and dark at font 1.0, dark at font 2.0. */
    private fun screen(name: String, tab: String?, content: @Composable () -> Unit) {
        capture(name, tab, 1.0f, dark = false, content)
        capture(name, tab, 1.0f, dark = true, content)
        capture(name, tab, 2.0f, dark = true, content)
    }

    @Test fun home() = screen("home", PhoneRoutes.HOME) { HomeScreen(es, actions) {} }
    @Test fun today() = screen("today", PhoneRoutes.HOME) { TodayScreen(es, {}, {}) }
    @Test fun headphones() = screen("headphones", PhoneRoutes.HOME) { HeadphonesScreen(es.copy(phoneSampling = true, phoneLive = liveOver), actions, {}) }
    @Test fun trends() = screen("trends", PhoneRoutes.TRENDS) { TrendsScreen(es, {}) }
    @Test fun tono() = screen("tono", PhoneRoutes.HOME) { TonoScreen(es, actions, onBack = {}) }
    @Test fun watch() = screen("watch", PhoneRoutes.WATCH) { WatchScreen(es, actions) }
    @Test fun settings() = screen("settings", PhoneRoutes.SETTINGS) { SettingsScreen(es, actions) {} }
    @Test fun alerts() = screen("alerts", PhoneRoutes.HOME) { AlertsScreen(es, {}, {}) }
    @Test fun how() = screen("how", PhoneRoutes.SETTINGS) { HowItWorksScreen {} }
    @Test fun setup() = screen("setup", null) { SetupScreen(es.copy(setupDone = false), actions, initialStep = 0) }
}
