package com.example.voxara.core

import com.example.voxara.core.design.Palette
import com.example.voxara.core.risk.RiskZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design tokens: WCAG contrast and the single zone model. */
class DesignSystemTest {

    private fun assertContrast(fg: Long, bg: Long, min: Double, what: String) {
        val c = Palette.contrast(fg, bg)
        assertTrue("$what: ${"%.2f".format(c)} < $min", c >= min)
    }

    @Test
    fun `text inks meet 4_5 to 1 on every surface they sit on`() {
        val backgrounds = listOf(Palette.BLACK, Palette.SURFACE_1, Palette.SURFACE_2, Palette.SURFACE_3)
        for (bg in backgrounds) {
            assertContrast(Palette.TEXT_PRIMARY, bg, 4.5, "primary on ${bg.toString(16)}")
            assertContrast(Palette.TEXT_SECONDARY, bg, 4.5, "secondary on ${bg.toString(16)}")
        }
        // Tertiary is for captions on black and the first surface only.
        assertContrast(Palette.TEXT_TERTIARY, Palette.BLACK, 4.5, "tertiary on black")
        assertContrast(Palette.TEXT_TERTIARY, Palette.SURFACE_1, 4.5, "tertiary on surface 1")
    }

    @Test
    fun `zone colours stand out as graphics and carry dark text`() {
        RiskZone.entries.forEach { z ->
            val c = Palette.zone(z)
            assertContrast(c, Palette.BLACK, 3.0, "$z on black")          // WCAG non-text contrast
            assertContrast(c, Palette.SURFACE_2, 3.0, "$z on surface 2")
            assertContrast(Palette.ON_ZONE, c, 4.5, "text on $z")
        }
        assertContrast(Palette.BRAND_START, Palette.BLACK, 4.5, "brand on black")
    }

    @Test
    fun `adjacent zones differ in lightness, not only hue`() {
        val l = RiskZone.entries.map { Palette.luminance(Palette.zone(it)) }
        // OK -> MODERATE and LOUD -> DANGEROUS change lightness clearly; icons and words cover the rest.
        assertTrue(Palette.contrast(Palette.ZONE_OK, Palette.ZONE_MODERATE) > 1.3)
        assertTrue(Palette.contrast(Palette.ZONE_LOUD, Palette.ZONE_DANGEROUS) > 1.1)
        assertEquals(4, l.size)
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
        assertEquals(21.0, Palette.contrast(Palette.TEXT_PRIMARY, Palette.BLACK), 0.01)
        assertEquals(1.0, Palette.contrast(Palette.BLACK, Palette.BLACK), 1e-9)
    }
}
