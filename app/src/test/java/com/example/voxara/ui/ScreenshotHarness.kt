package com.example.voxara.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import com.example.voxara.ui.theme.VoxaraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Layout checks for the watches Voxara targets, rendered on the JVM (Robolectric + Roborazzi).
 *
 *  - 192 dp: Wear OS small-round minimum
 *  - 216 dp: Galaxy Watch 7 40 mm (432 px, assumed xhdpi)
 *  - 240 dp: Galaxy Watch 7 44 mm (480 px, assumed xhdpi)
 *
 * Each at font scale 1.0 and 1.3 (large text). Images land in app/build/screenshots/ for review;
 * they are not golden files and nothing is compared automatically.
 */
object ScreenshotHarness {

    val sizesDp = listOf(192, 216, 240)
    val fontScales = listOf(1.0f, 1.3f)

    private val outDir: File by lazy {
        File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    }

    fun capture(
        name: String,
        sizeDp: Int,
        fontScale: Float,
        ambient: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("w${sizeDp}dp-h${sizeDp}dp-round-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        val file = File(outDir, "${name}_${sizeDp}dp_x${fontScale}.png")
        captureRoboImage(file.path) {
            // Reduced motion: no free-running clocks, so the frame is deterministic and idle.
            VoxaraTheme(ambient = ambient, reduceMotion = true) {
                AppScaffold { content() }
            }
        }
    }

    /** Every target size and font scale. */
    fun captureAll(name: String, ambient: Boolean = false, content: @Composable () -> Unit) {
        for (size in sizesDp) for (scale in fontScales) capture(name, size, scale, ambient, content)
    }
}
