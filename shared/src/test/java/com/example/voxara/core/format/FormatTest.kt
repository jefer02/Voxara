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

    @Test
    fun `levels and long estimates claim no more precision than they have`() {
        assertEquals("82 dBA", formatLevel(81.6))
        assertEquals("54 dBA", formatLevel(54.0))
        assertEquals("100h", formatApproxDuration(100 * 60 + 29.0))
        assertEquals("3h 20m", formatApproxDuration(3 * 60 + 17.0))
        assertEquals("35m", formatApproxDuration(33.0))
        assertEquals("5m", formatApproxDuration(1.0))
    }

    @Test
    fun `quiet hours follow the clock format and the language`() {
        // The JDK's CLDR data puts a narrow no-break space before the day period; compare with plain spaces.
        fun plain(t: String) = t.replace(' ', ' ').replace(' ', ' ')
        val es = Locale.forLanguageTag("es")
        assertEquals("22:00", formatTimeOfDay(22 * 60, is24Hour = true, locale = es))
        assertEquals("07:30", formatTimeOfDay(7 * 60 + 30, is24Hour = true, locale = Locale.ENGLISH))
        assertEquals("10:00 PM", plain(formatTimeOfDay(22 * 60, is24Hour = false, locale = Locale.ENGLISH)))
        assertEquals("10:00 p. m.", plain(formatTimeOfDay(22 * 60, is24Hour = false, locale = es)))
        assertEquals("12:00 AM", plain(formatTimeOfDay(1440, is24Hour = false, locale = Locale.ENGLISH)))
    }
}
