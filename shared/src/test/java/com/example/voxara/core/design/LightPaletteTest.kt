package com.example.voxara.core.design

import com.example.voxara.core.risk.RiskZone
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's light theme: every text and zone ink passes WCAG AA (4.5:1) on every light surface. */
class LightPaletteTest {

    private val surfaces = listOf(Palette.LIGHT_BG, Palette.LIGHT_SURFACE_1, Palette.LIGHT_SURFACE_2)

    @Test
    fun `light text and brand inks pass AA on every light surface`() {
        val inks = listOf(
            Palette.LIGHT_TEXT_PRIMARY, Palette.LIGHT_TEXT_SECONDARY, Palette.LIGHT_TEXT_TERTIARY,
            Palette.LIGHT_BRAND_INK, Palette.LIGHT_BRAND_INK_2,
        )
        for (ink in inks) for (bg in surfaces) {
            assertTrue("%08X on %08X".format(ink, bg), Palette.contrast(ink, bg) >= 4.5)
        }
    }

    @Test
    fun `zone inks pass AA on every light surface, zone fills pass 3 to 1 on the dark ones`() {
        for (z in RiskZone.entries) {
            for (bg in surfaces) assertTrue(z.name, Palette.contrast(Palette.zoneInk(z), bg) >= 4.5)
            for (bg in listOf(Palette.BLACK, Palette.SURFACE_1, Palette.SURFACE_2)) {
                assertTrue(z.name, Palette.contrast(Palette.zone(z), bg) >= 3.0)
            }
        }
    }
}
