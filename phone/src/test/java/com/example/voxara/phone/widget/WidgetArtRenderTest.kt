package com.example.voxara.phone.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.test.core.app.ApplicationProvider
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * The widget's masks, composited the way Glance tints them (a preview of what the home screen shows), light and dark, with
 * a value in each zone and the empty state. Opt-in: it writes into `$VOXARA_SHOTS_DIR` like the other render tests.
 * Without the env var it still checks that the masks are drawn (not empty).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetArtRenderTest {

    private val px = 360

    private fun opaquePixels(b: Bitmap): Int {
        var n = 0
        for (y in 0 until b.height step 4) for (x in 0 until b.width step 4) if ((b.getPixel(x, y) ushr 24) > 0) n++
        return n
    }

    @Test
    fun `the ring and the figure masks are drawn`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(opaquePixels(WidgetArt.track(px)) > 100)
        assertTrue(opaquePixels(WidgetArt.arc(px, 0.83f)) > 100)
        assertTrue(opaquePixels(WidgetArt.arc(px, 0f)) == 0)
        assertTrue(opaquePixels(WidgetArt.figure(context, px, 83)) > 100)
        assertTrue(opaquePixels(WidgetArt.figure(context, px, null)) > 10)
    }

    @Test
    fun `preview of the widget in both themes`() {
        val dir = System.getenv("VOXARA_SHOTS_DIR")?.takeIf { it.isNotBlank() }
        assumeTrue(dir != null)
        val out = File(dir!!).let { if (it.isAbsolute) it else File(System.getProperty("user.dir"), "../$dir") }.apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val cases = listOf(34 to RiskZone.OK, 66 to RiskZone.MODERATE, 83 to RiskZone.LOUD, 112 to RiskZone.DANGEROUS, null to null)
        val cell = 420
        val sheet = Bitmap.createBitmap(cell * cases.size, cell * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        for ((row, dark) in listOf(false, true).withIndex()) {
            cases.forEachIndexed { col, (value, zone) ->
                val bg = if (dark) Palette.DARK_CARD else Palette.LIGHT_CARD
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = (if (dark) Palette.DARK_BG else Palette.LIGHT_BG).toInt()
                canvas.drawRect((col * cell).toFloat(), (row * cell).toFloat(), ((col + 1) * cell).toFloat(), ((row + 1) * cell).toFloat(), paint)
                paint.color = bg.toInt()
                canvas.drawRoundRect((col * cell + 30).toFloat(), (row * cell + 30).toFloat(), ((col + 1) * cell - 30).toFloat(), ((row + 1) * cell - 30).toFloat(), 60f, 60f, paint)
                fun tinted(mask: Bitmap, color: Int) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
                    canvas.drawBitmap(Bitmap.createScaledBitmap(mask, 300, 300, true), (col * cell + 60).toFloat(), (row * cell + 60).toFloat(), p)
                }
                tinted(WidgetArt.track(px), (if (dark) Palette.DARK_TRACK else Palette.LIGHT_TRACK))
                if (value != null && zone != null) {
                    tinted(WidgetArt.arc(px, value / 100f), Palette.level(zone, Accent.PURPLE, dark).fill.toInt())
                }
                tinted(WidgetArt.figure(context, px, value), (if (dark) Palette.DARK_TEXT else Palette.LIGHT_TEXT).toInt())
            }
        }
        FileOutputStream(File(out, "widget_preview.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
