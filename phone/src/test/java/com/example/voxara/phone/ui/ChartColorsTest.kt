package com.example.voxara.phone.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.ui.theme.VoxColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * THE CHARTS' COLOURS, every accent in both themes. The thresholds are the dataviz and WCAG ones and are fixed; when a
 * check fails the colour changes, never the number.
 *  - A mark that carries meaning is at least 3:1 against the surface it sits on (WCAG 1.4.11).
 *  - Text on a chart or a calendar cell is at least 4.5:1 on what is behind it (WCAG 1.4.3).
 *  - The accent, amber and crimson are three clearly different colours (OKLab distance, normal vision), and still
 *    apart for the three kinds of colour-blindness; the zones always also carry an icon and a word.
 */
class ChartColorsTest {

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private fun contrast(fg: Color, bg: Color) = Palette.contrast(fg.argb(), bg.argb())

    private fun over(fg: Color, alpha: Float, bg: Color): Color = Color(Palette.over(fg.argb(), alpha, bg.argb()).toInt())

    private val combos = Accent.entries.flatMap { a -> listOf(false, true).map { dark -> a to dark } }

    private fun name(a: Accent, dark: Boolean) = "${a.name} ${if (dark) "dark" else "light"}"

    @Test
    fun `every zone mark is at least 3 to 1 against the page and the card`() {
        for ((a, dark) in combos) {
            val c = VoxColors.of(dark, a)
            for (z in RiskZone.entries) for (bg in listOf(c.background, c.surface)) {
                val r = contrast(c.zoneFill(z), bg)
                assertTrue("${name(a, dark)} $z fill on its surface = %.2f".format(r), r >= 3.0)
            }
            // the accent outline of a calendar day and the guide hairline
            assertTrue("${name(a, dark)} accent outline", contrast(c.accent.fill, c.surface) >= 3.0)
            assertTrue("${name(a, dark)} 100% guide", contrast(c.text3, c.surface) >= 3.0)
        }
    }

    @Test
    fun `text on a calendar cell and on a column label is at least 4_5 to 1`() {
        for ((a, dark) in combos) {
            val c = VoxColors.of(dark, a)
            // the soft OK cell: the page text over the accent at 20 % (light) or 28 % (dark) on the card
            val soft = over(c.zoneFill(RiskZone.OK), if (dark) 0.28f else 0.20f, c.surface)
            assertTrue("${name(a, dark)} text on the OK cell", contrast(c.text, soft) >= 4.5)
            // the solid cells: each tier's own text colour on its fill
            for (z in RiskZone.entries) {
                val t = c.level(z)
                assertTrue("${name(a, dark)} $z cell text", contrast(t.onFill, t.fill) >= 4.5)
            }
            // axis labels, values and the gutter labels
            for (bg in listOf(c.background, c.surface)) {
                assertTrue("${name(a, dark)} axis labels", contrast(c.text2, bg) >= 4.5)
                assertTrue("${name(a, dark)} values", contrast(c.text, bg) >= 4.5)
            }
            // the zone words next to the charts
            for (z in RiskZone.entries) assertTrue("${name(a, dark)} $z word", contrast(c.zoneInk(z), c.background) >= 4.5)
        }
    }

    @Test
    fun `the accent, amber and crimson are three different colours, also for colour-blind readers`() {
        for ((a, dark) in combos) {
            val c = VoxColors.of(dark, a)
            val trio = listOf("accent" to c.zoneFill(RiskZone.OK), "amber" to c.zoneFill(RiskZone.LOUD), "crimson" to c.zoneFill(RiskZone.DANGEROUS))
            for (i in trio.indices) for (j in i + 1 until trio.size) {
                val (n1, c1) = trio[i]; val (n2, c2) = trio[j]
                val normal = distance(c1, c2, null)
                assertTrue("${name(a, dark)} $n1 vs $n2 normal vision = %.1f".format(normal), normal >= 15.0)
                for ((kind, m) in CVD) {
                    val d = distance(c1, c2, m)
                    // 6 is the floor that is allowed when a second encoding (icon, word, height) is always present
                    assertTrue("${name(a, dark)} $n1 vs $n2 as $kind = %.1f".format(d), d >= 6.0)
                }
            }
        }
    }

    // ------------------------------------------------------------------ OKLab and colour-blindness simulation

    private val CVD = listOf(
        "protanopia" to arrayOf(doubleArrayOf(0.152286, 1.052583, -0.204868), doubleArrayOf(0.114503, 0.786281, 0.099216), doubleArrayOf(-0.003882, -0.048116, 1.051998)),
        "deuteranopia" to arrayOf(doubleArrayOf(0.367322, 0.860646, -0.227968), doubleArrayOf(0.280085, 0.672501, 0.047413), doubleArrayOf(-0.011820, 0.042940, 0.968881)),
        "tritanopia" to arrayOf(doubleArrayOf(1.255528, -0.076749, -0.178779), doubleArrayOf(-0.078411, 0.930809, 0.147602), doubleArrayOf(0.004733, 0.691367, 0.303900)),
    )

    private fun lin(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun oklab(c: Color, m: Array<DoubleArray>?): DoubleArray {
        var rgb = doubleArrayOf(lin(c.red), lin(c.green), lin(c.blue))
        if (m != null) rgb = DoubleArray(3) { i -> (m[i][0] * rgb[0] + m[i][1] * rgb[1] + m[i][2] * rgb[2]).coerceIn(0.0, 1.0) }
        val l = cbrt(0.4122214708 * rgb[0] + 0.5363325363 * rgb[1] + 0.0514459929 * rgb[2])
        val mm = cbrt(0.2119034982 * rgb[0] + 0.6806995451 * rgb[1] + 0.1073969566 * rgb[2])
        val s = cbrt(0.0883024619 * rgb[0] + 0.2817188376 * rgb[1] + 0.6299787005 * rgb[2])
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * mm - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * mm + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * mm - 0.8086757660 * s,
        )
    }

    /** OKLab distance times 100 (the dataviz scale). */
    private fun distance(a: Color, b: Color, m: Array<DoubleArray>?): Double {
        val x = oklab(a, m); val y = oklab(b, m)
        return 100.0 * sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
    }
}
