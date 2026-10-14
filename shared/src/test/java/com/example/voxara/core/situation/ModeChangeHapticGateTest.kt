package com.example.voxara.core.situation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The automatic mode-change haptic, against a fake clock: rare, polite and never near an alert. */
class ModeChangeHapticGateTest {

    private val minute = 60_000L
    private val start = 1_750_000_000_000L
    private val day = 20_000L

    /** A context 10 minutes after the dosimeter started, with nothing in the way. */
    private fun ctx(
        now: Long = start + 10 * minute,
        localDay: Long = day,
        lastAlert: Long? = null,
        quiet: Boolean = false,
        powerSave: Boolean = false,
        advisory: Boolean = true,
    ) = ModeChangeHapticGate.Context(now, localDay, start, lastAlert, quiet, powerSave, advisory)

    @Test
    fun `plays when nothing is in the way`() {
        assertTrue(ModeChangeHapticGate().tryPlay(ctx()))
    }

    @Test
    fun `never in the first 5 minutes after the dosimeter starts or restarts`() {
        val gate = ModeChangeHapticGate()
        assertFalse(gate.tryPlay(ctx(now = start)))
        assertFalse(gate.tryPlay(ctx(now = start + 5 * minute - 1)))
        assertTrue(gate.tryPlay(ctx(now = start + 5 * minute)))
    }

    @Test
    fun `at most one every 30 minutes`() {
        val gate = ModeChangeHapticGate()
        val t = start + 10 * minute
        assertTrue(gate.tryPlay(ctx(now = t)))
        assertFalse(gate.tryPlay(ctx(now = t + 29 * minute)))
        assertTrue(gate.tryPlay(ctx(now = t + 30 * minute)))
    }

    @Test
    fun `at most 6 a day, and the count starts again the next day`() {
        val gate = ModeChangeHapticGate()
        val t = start + 10 * minute
        repeat(6) { i -> assertTrue("change $i", gate.tryPlay(ctx(now = t + i * 31 * minute))) }
        assertFalse(gate.tryPlay(ctx(now = t + 6 * 31 * minute)))
        assertTrue(gate.tryPlay(ctx(now = t + 7 * 31 * minute, localDay = day + 1)))
    }

    @Test
    fun `never within 60 seconds of an alert haptic, on either side`() {
        val t = start + 10 * minute
        assertFalse(ModeChangeHapticGate().tryPlay(ctx(now = t, lastAlert = t - 59_000)))
        assertFalse(ModeChangeHapticGate().tryPlay(ctx(now = t, lastAlert = t + 30_000)))
        assertTrue(ModeChangeHapticGate().tryPlay(ctx(now = t, lastAlert = t - 60_000)))
    }

    @Test
    fun `quiet hours, power saving and advisory haptics off all silence it`() {
        assertFalse(ModeChangeHapticGate().tryPlay(ctx(quiet = true)))
        assertFalse(ModeChangeHapticGate().tryPlay(ctx(powerSave = true)))
        assertFalse(ModeChangeHapticGate().tryPlay(ctx(advisory = false)))
    }

    @Test
    fun `a refused change does not use up the allowance`() {
        val gate = ModeChangeHapticGate()
        val t = start + 10 * minute
        repeat(10) { assertFalse(gate.tryPlay(ctx(now = t + it * minute, quiet = true))) }
        assertTrue(gate.tryPlay(ctx(now = t + 10 * minute)))
        var played = 1
        for (i in 1..20) if (gate.tryPlay(ctx(now = t + 10 * minute + i * 31 * minute))) played++
        assertEquals(6, played)
    }
}
