package com.example.voxara.core

import com.example.voxara.core.design.Accent
import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Watch design tokens: WCAG contrast on black and dark cards, the tiers, and the single zone model. */
class DesignSystemTest {

    private fun assertContrast(fg: Long, bg: Long, min: Double, what: String) {
        val c = Palette.contrast(fg, bg)
        assertTrue("$what: ${"%.2f".format(c)} < $min", c >= min)
    }

    private val watchSurfaces = listOf(Palette.BLACK, Palette.DARK_CARD, Palette.DARK_CARD_SUBTLE)

    @Test
    fun `watch text tokens meet 4_5 to 1 on black and on every card`() {
        for (bg in watchSurfaces) {
            assertContrast(Palette.DARK_TEXT, bg, 4.5, "text on ${bg.toString(16)}")
            assertContrast(Palette.DARK_TEXT_2, bg, 4.5, "secondary on ${bg.toString(16)}")
            assertContrast(Palette.DARK_MUTED, bg, 4.5, "muted on ${bg.toString(16)}")
        }
    }

    @Test
    fun `every zone tier on the watch stands out as a graphic and as text, and carries dark text`() {
        RiskZone.entries.forEach { z ->
            val t = Palette.level(z, Accent.PURPLE, dark = true)
            for (bg in watchSurfaces) {
                assertContrast(t.ink, bg, 4.5, "$z ink on ${bg.toString(16)}")
                assertContrast(t.fill, bg, 3.0, "$z fill on ${bg.toString(16)}")   // WCAG non-text contrast
            }
            assertContrast(t.onFill, t.fill, 4.5, "text on $z")
        }
    }

    @Test
    fun `the headphones indigo and the page names read on black and on the cards`() {
        for (bg in watchSurfaces) assertContrast(Palette.SECONDARY_INK_DARK, bg, 4.5, "indigo ink on ${bg.toString(16)}")
    }

    @Test
    fun `the indigo fill is a visible graphic on black and on the cards`() {
        // The split ring, its arcs and the chip border are drawn in the indigo fill (WCAG 1.4.11, non-text).
        for (bg in watchSurfaces) assertContrast(Palette.SECONDARY, bg, 3.0, "indigo fill on ${bg.toString(16)}")
    }

    @Test
    fun `the headphones chip text reads on its own indigo tint`() {
        // The chip: indigo at 18 % over black; its title is the indigo ink, its second line the secondary text.
        val tint = Palette.over(Palette.SECONDARY, 0.18f, Palette.BLACK)
        assertContrast(Palette.SECONDARY_INK_DARK, tint, 4.5, "chip title")
        assertContrast(Palette.DARK_TEXT_2, tint, 4.5, "chip second line")
    }

    @Test
    fun `the moon figure reads on its disc in every zone`() {
        // The moon is black under a 22 % tier tint, with the figure in the main text colour.
        RiskZone.entries.forEach { z ->
            val t = Palette.level(z, Accent.PURPLE, dark = true)
            val disc = Palette.over(t.fill, 0.22f, Palette.BLACK)
            assertContrast(Palette.DARK_TEXT, disc, 4.5, "moon figure in $z")
            assertContrast(t.fill, Palette.BLACK, 3.0, "moon ring in $z")
        }
    }

    /**
     * The zone tag sits over the wave rings: worst case, the brightest ring colour (the tier fill at
     * the rings' top opacity, 0.52) under the tag's dark scrim (black at 62 %) and its tier tint (15 %).
     */
    @Test
    fun `the zone tag text stays readable over the brightest part of the waves`() {
        RiskZone.entries.forEach { z ->
            val t = Palette.level(z, Accent.PURPLE, dark = true)
            val waves = Palette.over(t.fill, 0.52f, Palette.BLACK)
            val scrimmed = Palette.over(Palette.BLACK, 0.62f, waves)
            val tag = Palette.over(t.fill, Palette.SOFT_ALPHA, scrimmed)
            assertContrast(t.ink, tag, 4.5, "$z tag text over waves")
        }
    }

    @Test
    fun `the three tiers differ in hue and lightness, so caution and danger never look like the accent`() {
        val a = Palette.accent(Accent.PURPLE, true).fill
        val c = Palette.caution(true).fill
        val d = Palette.danger(true).fill
        assertTrue(Palette.contrast(a, c) > 1.3)
        assertTrue(Palette.contrast(c, d) > 1.3)
        assertEquals(3, setOf(a, c, d).size)
    }

    @Test
    fun `one zone model for levels and for percentages`() {
        assertEquals(RiskZone.OK, RiskZone.of(69.9))
        assertEquals(RiskZone.MODERATE, RiskZone.of(70.0))
        assertEquals(RiskZone.LOUD, RiskZone.of(85.0))
        assertEquals(RiskZone.DANGEROUS, RiskZone.of(95.0))
        assertEquals(RiskZone.OK, RiskZone.ofPercent(49.0))
        assertEquals(RiskZone.MODERATE, RiskZone.ofPercent(50.0))
        assertEquals(RiskZone.LOUD, RiskZone.ofPercent(80.0))
        assertEquals(RiskZone.DANGEROUS, RiskZone.ofPercent(100.0))
    }

    @Test
    fun `WCAG contrast math is right`() {
        assertEquals(21.0, Palette.contrast(0xFFFFFFFF, Palette.BLACK), 0.01)
        assertEquals(1.0, Palette.contrast(Palette.BLACK, Palette.BLACK), 1e-9)
    }
}
