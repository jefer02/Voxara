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
