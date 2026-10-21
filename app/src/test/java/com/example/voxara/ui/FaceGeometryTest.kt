package com.example.voxara.ui

import com.example.voxara.ui.design.Face
import com.example.voxara.ui.design.figureScaleFor
import com.example.voxara.ui.design.textScaleFor
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The face is proportions of the display, so it holds at every size the watches come in. */
class FaceGeometryTest {

    private val sizes = listOf(192, 216, 227, 240)

    @Test fun theRingHugsTheEdgeWithASmallConstantInsetAndItsGlowStaysOnTheDisplay() {
        for (d in sizes) {
            val inset = d * Face.RING_INSET
            assertTrue("inset $inset dp at $d", inset in 6.0..11.0)
            // The ring's widest paint is its glow (a 3x wide stroke under a 1.1x one, centred 0.55 stroke inside the rim).
            val w = d * Face.BEZEL
            val glowOuter = d * (0.5 - Face.RING_INSET) - 0.55 * w + 1.5 * w
            assertTrue("glow outer $glowOuter inside ${d / 2.0} at $d", glowOuter < d / 2.0)
        }
    }

    @Test fun theRingIsTheSameFractionOfTheDisplayOnEverySize() {
        val fractions = sizes.map { d -> (d * (0.5 - Face.RING_INSET)) / d }
        assertTrue(fractions.all { it == fractions.first() })
        assertEquals(0.46, fractions.first(), 1e-9)
    }

    @Test fun theFootLinesFitBetweenTheRingEnds() {
        // The ring's ends sit at +-(gap/2) from straight down. The foot is at most FOOT_WIDTH wide, so even
        // at the height of the ends its widest line stays clear of their round caps.
        val half = Math.toRadians((360.0 - Face.SWEEP) / 2)
        for (d in sizes) {
            val r = d * (0.5 - Face.RING_INSET)
            val endNearestX = r * Math.sin(half) - d * Face.BEZEL * 1.1 / 2
            assertTrue("ends at $endNearestX vs foot half ${d * Face.FOOT_WIDTH / 2} at $d", endNearestX > d * Face.FOOT_WIDTH / 2)
        }
        assertEquals(250f, Face.SWEEP, 0f)
    }

    @Test fun figuresAndWordsFollowTheDisplayWithinLimits() {
        assertEquals(1f, textScaleFor(192.dp), 0f)          // small displays keep the 12 sp floor
        assertEquals(1f, textScaleFor(216.dp), 0f)
        assertEquals(1.12f, textScaleFor(240.dp), 0.01f)
        assertEquals(0.9f, figureScaleFor(192.dp), 0.01f)
        assertEquals(1f, figureScaleFor(216.dp), 0f)
        assertEquals(1.11f, figureScaleFor(240.dp), 0.01f)
        assertTrue(figureScaleFor(300.dp) <= 1.15f)
    }
}
