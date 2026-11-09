package com.example.voxara.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.voxara.core.situation.AutoStatus
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.phone.ui.PhoneFixtures.state
import com.example.voxara.phone.ui.PhoneFixtures.status
import com.example.voxara.phone.ui.components.WatchModePill
import com.example.voxara.phone.ui.theme.Vox
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
 * The Home screen's watch-mode pill in each of Auto's states, the same words as the watch's chip: detected, Detecting…
 * (first minutes), unsure past them, no scene model, Concert, an older watch that never says (the older label) and
 * headphones mode on top. EN and ES, font 1.0 and 2.0, light and dark. Opt-in like the render matrix:
 *
 *     VOXARA_SHOTS_DIR=build/pill ./gradlew :phone:testDebugUnitTest --tests '*ModePillRenderTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class ModePillRenderTest {

    @Before fun onlyWhenAsked() = assumeTrue(!System.getenv("VOXARA_SHOTS_DIR").isNullOrBlank())

    private val outDir by lazy {
        val dir = System.getenv("VOXARA_SHOTS_DIR")!!
        (File(dir).let { if (it.isAbsolute) it else File(System.getProperty("user.dir"), "../$dir") }).apply { mkdirs() }
    }

    private val street = status.copy(situationMode = SituationMode.STREET, headphonesMode = false)

    private val cases = listOf(
        "a_detected" to street.copy(autoStatus = AutoStatus.DETECTED),
        "b_detecting" to street.copy(autoStatus = AutoStatus.DETECTING),
        "c_unsure" to street.copy(autoStatus = AutoStatus.UNSURE),
        "d_no_model" to street.copy(autoStatus = AutoStatus.NO_MODEL),
        "e_concert" to street.copy(situationMode = SituationMode.CONCERT, autoStatus = AutoStatus.DETECTED),
        "f_older_watch" to street.copy(autoStatus = null),
        "g_detecting_headphones" to street.copy(autoStatus = AutoStatus.DETECTING, headphonesMode = true),
    )

    @Test fun pillStates() {
        for (lang in listOf("en", "es")) for (scale in listOf(1.0f, 2.0f)) for (dark in listOf(false, true)) for ((name, st) in cases) {
            RuntimeEnvironment.setQualifiers("$lang-w360dp-h120dp-${if (dark) "night" else "notnight"}-xxhdpi")
            RuntimeEnvironment.setFontScale(scale)
            val file = File(outDir, "pill_${name}__${lang}_x$scale${if (dark) "_dark" else "_light"}.png")
            captureRoboImage(file.path) {
                VoxaraPhoneTheme(darkTheme = dark, reduceMotion = true) {
                    Box(Modifier.background(Vox.colors.background).padding(16.dp)) { WatchModePill(state.copy(status = st)) }
                }
            }
        }
    }
}
