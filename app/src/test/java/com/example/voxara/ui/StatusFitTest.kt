package com.example.voxara.ui

import com.example.voxara.ui.design.shortenToFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tono's line on the hero never ends in an ellipsis: it is kept whole, or cut at a clause or a word, never mid-word. */
class StatusFitTest {

    /** A pretend space: at most [n] characters. */
    private fun fitsIn(n: Int): (String) -> Boolean = { it.length <= n }

    @Test fun aSentenceThatFitsIsKeptAsItIs() {
        assertEquals("Looking after your ears", shortenToFit("Looking after your ears", fitsIn(40)))
    }

    @Test fun whenItDoesNotFitItKeepsWholeClauses() {
        val t = "Moderate right now, take a break soon, keep the volume low."
        assertEquals("Moderate right now", shortenToFit(t, fitsIn(30)))
        assertEquals("Moderate right now, take a break soon", shortenToFit(t, fitsIn(40)))
    }

    @Test fun aSentenceEndKeepsItsPunctuation() {
        val t = "All good today. You are well inside your allowance and the week looks calm."
        assertEquals("All good today.", shortenToFit(t, fitsIn(30)))
    }

    @Test fun withNoClauseThatFitsItCutsAtAWholeWord() {
        val t = "Surroundings are moderate at the moment"
        val out = shortenToFit(t, fitsIn(22))
        assertEquals("Surroundings are", out)
        assertTrue(t.startsWith(out))
    }

    @Test fun neverEndsInACutWordOrADanglingMark() {
        val t = "Fuerte ahora, baja el volumen — descansa un rato; tus oídos lo agradecerán."
        for (n in 8..t.length) {
            val out = shortenToFit(t, fitsIn(n))
            assertFalse("'$out' ends in a mark", out.last() in ",;:—– ")
            assertFalse("'$out' has an ellipsis", out.contains("…") || out.endsWith("..."))
            // A prefix of the text that stops at a word boundary.
            assertTrue("'$out' is not a whole-word prefix", t.startsWith(out) && (out.length == t.length || !t[out.length].isLetterOrDigit()))
        }
    }

    @Test fun aSingleLongWordIsReturnedWhole() {
        assertEquals("Supercalifragilistic", shortenToFit("Supercalifragilistic and more", fitsIn(5)))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", shortenToFit("   ", fitsIn(5)))
    }
}
