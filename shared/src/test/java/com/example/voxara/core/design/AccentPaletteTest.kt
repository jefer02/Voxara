package com.example.voxara.core.design

import com.example.voxara.core.risk.RiskZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG AA for every accent × theme (docs/design/STYLE.md §1): inks ≥ 4.5:1 on every surface and on
 * the soft tint over it, fills ≥ 3:1 as graphics, text on fills ≥ 4.5:1. Same for the caution and
 * danger tiers and the neutral text tokens. The thresholds are fixed; colours change, not these.
 */
class AccentPaletteTest {

    private fun assertContrast(fg: Long, bg: Long, min: Double, what: String) {
        val c = Palette.contrast(fg, bg)
        assertTrue("$what: %08X on %08X = %.2f < $min".format(fg, bg, c), c >= min)
    }

    private fun tones(dark: Boolean) =
        Accent.entries.map { it.name to Palette.accent(it, dark) } +
            listOf("CAUTION" to Palette.caution(dark), "DANGER" to Palette.danger(dark))

    @Test
    fun `every accent and tier passes AA in both themes`() {
        for (dark in listOf(true, false)) {
            val theme = if (dark) "dark" else "light"
            val surfaces = Palette.surfaces(dark)
            for ((name, t) in tones(dark)) {
                for (bg in surfaces) {
                    assertContrast(t.ink, bg, 4.5, "$theme $name ink")
                    assertContrast(t.ink, Palette.over(t.fill, Palette.SOFT_ALPHA, bg), 4.5, "$theme $name ink on soft")
                }
                for (bg in surfaces.take(2)) assertContrast(t.fill, bg, 3.0, "$theme $name fill")
                assertContrast(t.onFill, t.fill, 4.5, "$theme $name on fill")
            }
        }
    }

    @Test
    fun `the watch purple works on black and on the dark card`() {
        val t = Palette.accent(Accent.PURPLE, dark = true)
        for (bg in listOf(Palette.BLACK, Palette.DARK_CARD)) {
            assertContrast(t.ink, bg, 4.5, "watch ink")
            assertContrast(t.fill, bg, 3.0, "watch fill")
        }
        for (tier in listOf(Palette.caution(true), Palette.danger(true))) {
            assertContrast(tier.ink, Palette.BLACK, 4.5, "watch tier ink")
            assertContrast(tier.fill, Palette.BLACK, 3.0, "watch tier fill")
        }
    }

    @Test
    fun `neutral text passes AA on every surface of its theme`() {
        for (bg in Palette.surfaces(true) + Palette.BLACK) {
            assertContrast(Palette.DARK_TEXT, bg, 4.5, "dark text")
            assertContrast(Palette.DARK_TEXT_2, bg, 4.5, "dark text 2")
            assertContrast(Palette.DARK_MUTED, bg, 4.5, "dark muted")
            assertContrast(Palette.SECONDARY_INK_DARK, bg, 4.5, "dark secondary ink")
        }
        for (bg in Palette.surfaces(false)) {
            assertContrast(Palette.LIGHT_TEXT, bg, 4.5, "light text")
            assertContrast(Palette.LIGHT_TEXT_2, bg, 4.5, "light text 2")
            assertContrast(Palette.LIGHT_MUTED, bg, 4.5, "light muted")
            assertContrast(Palette.SECONDARY_INK_LIGHT, bg, 4.5, "light secondary ink")
        }
    }

    @Test
    fun `the level accent follows Voxara's zones`() {
        for (a in Accent.entries) for (dark in listOf(true, false)) {
            assertEquals(Palette.accent(a, dark), Palette.level(RiskZone.OK, a, dark))
            assertEquals(Palette.accent(a, dark), Palette.level(RiskZone.MODERATE, a, dark))
            assertEquals(Palette.caution(dark), Palette.level(RiskZone.LOUD, a, dark))
            assertEquals(Palette.danger(dark), Palette.level(RiskZone.DANGEROUS, a, dark))
        }
        assertEquals(RiskZone.MODERATE, RiskZone.of(84.9))
        assertEquals(RiskZone.LOUD, RiskZone.of(85.0))
    }

    @Test
    fun `accent defaults to purple and parses safely`() {
        assertEquals(Accent.PURPLE, Accent.DEFAULT)
        assertEquals(Accent.BLUE, Accent.parse("BLUE"))
        assertEquals(Accent.PURPLE, Accent.parse(null))
        assertEquals(Accent.PURPLE, Accent.parse("PINK"))
    }

    @Test
    fun `over blends straight alpha`() {
        assertEquals(0xFF000000, Palette.over(0xFFFFFFFF, 0f, 0xFF000000))
        assertEquals(0xFFFFFFFF, Palette.over(0xFFFFFFFF, 1f, 0xFF000000))
    }
}
