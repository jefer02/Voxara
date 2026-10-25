package com.example.voxara.ui

import com.example.voxara.ui.design.wholeSentencesToFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tono's line on a glance is a whole sentence or nothing: never an ellipsis, never a piece of a sentence ("Un día
 * tranquilo para" was seen on the real watch and read as a broken message). What does not fit goes under the hero.
 */
class StatusFitTest {

    /** A pretend space: at most [n] characters. */
    private fun fitsIn(n: Int): (String) -> Boolean = { it.length <= n }

    @Test fun aSentenceThatFitsIsKeptAsItIs() {
        assertEquals("Calm day for your ears", wholeSentencesToFit("Calm day for your ears", fitsIn(40)))
    }

    @Test fun whenItDoesNotFitItKeepsWholeSentencesFromTheStart() {
        val t = "All good today. Your week is calm. Keep the volume where it is."
        assertEquals("All good today. Your week is calm.", wholeSentencesToFit(t, fitsIn(40)))
        assertEquals("All good today.", wholeSentencesToFit(t, fitsIn(20)))
    }

    @Test fun aSentenceThatDoesNotFitIsNotCutIntoClausesOrWords() {
        // The real watch showed "Un día tranquilo para": a clause or word cut is never offered.
        assertNull(wholeSentencesToFit("Un día tranquilo para tus oídos", fitsIn(22)))
        assertNull(wholeSentencesToFit("Moderate right now, take a break soon.", fitsIn(30)))
    }

    @Test fun whateverItReturnsIsAPrefixEndingASentence() {
        val t = "Fuerte ahora. Baja el volumen. Descansa un rato; tus oídos lo agradecerán."
        for (n in 1..t.length) {
            val out = wholeSentencesToFit(t, fitsIn(n)) ?: continue
            assertTrue("'$out' is not a prefix", t.startsWith(out))
            assertTrue("'$out' does not end a sentence", out == t || out.last() in ".!?…")
            assertTrue("'$out' has an ellipsis", !out.endsWith("...") && (out == t || !out.endsWith("…")))
        }
    }

    @Test fun emptyIsNothing() {
        assertNull(wholeSentencesToFit("   ", fitsIn(5)))
    }
}
