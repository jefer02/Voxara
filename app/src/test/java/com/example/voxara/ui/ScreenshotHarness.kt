package com.example.voxara.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.example.voxara.ui.design.ClockChrome
import com.example.voxara.ui.theme.VoxaraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Layout checks for the watches Voxara targets, rendered on the JVM (Robolectric + Roborazzi).
 *
 *  - 192 dp: Wear OS small-round minimum
 *  - 203 dp: the Galaxy Watch 7 (SM-L300) this is tested on (432 px at 340 dpi)
 *  - 216 dp: Galaxy Watch 7 40 mm (432 px, assumed xhdpi)
 *  - 227 dp: the Wear OS large round emulator class
 *  - 240 dp: Galaxy Watch 7 44 mm / the XL round emulator (480 px, xhdpi)
 *
 * Each at font scale 1.0 and 1.3 (large text). Images land in app/build/screenshots/ for review;
 * they are not golden files and nothing is compared automatically.
 */
object ScreenshotHarness {

    val sizesDp = listOf(192, 203, 216, 227, 240)
    val fontScales = listOf(1.0f, 1.3f, 2.0f)

    private val outDir: File by lazy {
        File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    }

    fun capture(
        name: String,
        sizeDp: Int,
        fontScale: Float,
        ambient: Boolean = false,
        locale: String = "",
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("${if (locale.isEmpty()) "" else "$locale-"}w${sizeDp}dp-h${sizeDp}dp-round-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        val file = File(outDir, "${name}_${sizeDp}dp_x${fontScale}.png")
        captureRoboImage(file.path) {
            // Reduced motion: no free-running clocks, so the frame is deterministic and idle.
            VoxaraTheme(ambient = ambient, reduceMotion = true) {
                // The same scaffold as the app: the system clock steps aside on the glance pages.
                AppScaffold(timeText = { if (!ClockChrome.hidden) TimeText() }) { content() }
            }
        }
    }

    /** Every target size and font scale. */
    fun captureAll(name: String, ambient: Boolean = false, locale: String = "", content: @Composable () -> Unit) {
        for (size in sizesDp) for (scale in fontScales) capture(name, size, scale, ambient, locale, content)
    }
}
