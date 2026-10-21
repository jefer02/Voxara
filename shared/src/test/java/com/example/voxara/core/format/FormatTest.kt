package com.example.voxara.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Figures with a decimal follow the language the app shows, never the JVM's default. */
class FormatTest {
    private val en = Locale.forLanguageTag("en")
    private val es = Locale.forLanguageTag("es")

    @Test fun `a decimal uses the point in English and the comma in Spanish`() {
        assertEquals("54.0", formatDecimal(54.0, 1, en))
        assertEquals("54,0", formatDecimal(54.0, 1, es))
        assertEquals("81,6", formatDecimal(81.64, 1, es))
    }

    @Test fun `the 8-hour equivalent keeps its unit and one decimal in both languages`() {
        assertEquals("54.0 dBA", formatTwa(54.0, en))
        assertEquals("54,0 dBA", formatTwa(54.0, es))
        assertEquals("82,0 dBA", formatTwa(81.96, es))
        assertNull(formatTwa(null, es))
    }

    @Test fun `clock text has no language in it`() {
        assertEquals("1h 30m", formatClock(90.0))
        assertEquals("28s", formatClock(28.0 / 60))
    }
}
