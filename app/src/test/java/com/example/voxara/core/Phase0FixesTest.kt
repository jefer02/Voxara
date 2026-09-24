package com.example.voxara.core

import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.REFERENCE_DB
import com.example.voxara.core.calibration.cddDerivedOffset
import com.example.voxara.core.calibration.dbfsOfRms
import com.example.voxara.core.calibration.offsetFromReference
import com.example.voxara.core.dose.NoiseDoseEngine
import com.example.voxara.core.dose.representedSeconds
import com.example.voxara.core.ledger.LedgerSnapshot
import com.example.voxara.core.ledger.acceptsWrite
import com.example.voxara.core.ledger.isoWeekOf
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.ledger.rollover
import com.example.voxara.core.risk.RiskStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Regression tests for the Phase 0 correctness and safety fixes. */
class Phase0FixesTest {

    // ------------------------------------------------------------------ A1 calibration

    @Test
    fun `CDD starting offsets put the reference tones on their SPL`() {
        // UNPROCESSED: 94 dB SPL -> RMS 520 / 32768.
        val unprocessed = dbfsOfRms(520.0 / 32768.0) + REFERENCE_DB + cddDerivedOffset(MicSource.UNPROCESSED)
        assertEquals(94.0, unprocessed, 1e-9)
        // VOICE_RECOGNITION: 90 dB SPL -> RMS 2500 / 32768.
        val voice = dbfsOfRms(2500.0 / 32768.0) + REFERENCE_DB + cddDerivedOffset(MicSource.VOICE_RECOGNITION)
        assertEquals(90.0, voice, 1e-9)
    }

    @Test
    fun `the old zero offset under-read by roughly 36 and 18 dB`() {
        assertEquals(35.99, cddDerivedOffset(MicSource.UNPROCESSED), 0.01)
        assertEquals(18.35, cddDerivedOffset(MicSource.VOICE_RECOGNITION), 0.01)
    }

    @Test
    fun `an offset measured against a reference meter reproduces the reference`() {
        val measuredDbfsA = -41.3
        val offset = offsetFromReference(referenceDba = 85.0, measuredDbfsA = measuredDbfsA)
        assertEquals(85.0, measuredDbfsA + REFERENCE_DB + offset, 1e-9)
    }

    // A3 (the 100% alert is never swallowed by the cap) moved with the alerts to
    // AlertEngineTest in Phase 1.

    // ------------------------------------------------------------------ A4 time accounting

    @Test
    fun `continuous bursts count the time that actually passed`() {
        // A 250 ms nominal cycle that really took 370 ms (warm-up + setup) counts 0.37 s.
        assertEquals(0.37, representedSeconds(elapsedMs = 370, nominalPeriodMs = 250), 1e-9)
    }

    @Test
    fun `a frozen loop is not extrapolated`() {
        // 10 minutes of doze on a 10 s cadence counts at most two periods.
        assertEquals(20.0, representedSeconds(elapsedMs = 600_000, nominalPeriodMs = 10_000), 1e-9)
        assertEquals(0.0, representedSeconds(elapsedMs = -5, nominalPeriodMs = 10_000), 1e-9)
    }

    @Test
    fun `hazard dose is no longer undercounted`() {
        // 1 h at 94 dBA is a 100% NIOSH dose. Fold it the way the continuous loop does: cycles
        // that each really take 370 ms.
        val e = NoiseDoseEngine()
        var t = 0L
        while (t < 3_600_000L) {
            e.accumulate(94.0, representedSeconds(370, 250))
            t += 370
        }
        assertEquals(100.0, e.dosePercent, 0.5)
    }

    // ------------------------------------------------------------------ #7 day ledger
    // (Phase 1: the weekly budget became a rolling 7-day window and the history moved to the
    // minute ledger, so the rollover only closes the daily counters. See MinuteLedgerTest.)

    private val monday = 20_717L      // 2026-09-21, a Monday
    private val day = LedgerSnapshot(dayEpoch = monday, dosePercent = 42.0, peakDbc = 120.0)

    @Test
    fun `ISO weeks start on Monday`() {
        assertEquals(isoWeekOf(monday), isoWeekOf(monday + 6))          // Mon..Sun
        assertEquals(isoWeekOf(monday) + 1, isoWeekOf(monday + 7))      // next Monday
        assertEquals(isoWeekOf(monday) - 1, isoWeekOf(monday - 1))      // previous Sunday
    }

    @Test
    fun `local epoch day follows the zone`() {
        val utcMidnight = monday * 86_400_000L
        val bogota = TimeZone.getTimeZone("America/Bogota") // UTC-5
        assertEquals(monday, localEpochDay(utcMidnight, TimeZone.getTimeZone("UTC")))
        assertEquals(monday - 1, localEpochDay(utcMidnight, bogota))
    }

    @Test
    fun `rollover closes the day exactly once`() {
        val rolled = rollover(day, monday + 1)
        assertNotNull(rolled)
        rolled!!
        assertEquals(monday + 1, rolled.dayEpoch)
        assertEquals(0.0, rolled.dosePercent, 0.0)
        assertEquals(0.0, rolled.peakDbc, 0.0)
        // The worker and the service both calling it must not close it twice.
        assertNull(rollover(rolled, monday + 1))
    }

    @Test
    fun `rollover never goes backwards`() {
        assertNull(rollover(day, monday))
        assertNull(rollover(day, monday - 1))
    }

    @Test
    fun `a multi-day gap closes the day once`() {
        val rolled = rollover(day, monday + 3)!!
        assertEquals(monday + 3, rolled.dayEpoch)
        assertEquals(0.0, rolled.dosePercent, 0.0)
    }

    @Test
    fun `a first run just stamps the day`() {
        val fresh = day.copy(dayEpoch = 0L)
        val stamped = rollover(fresh, monday)!!
        assertEquals(monday, stamped.dayEpoch)
        assertEquals(42.0, stamped.dosePercent, 0.0)
    }

    @Test
    fun `a stale writer cannot overwrite a newer day`() {
        assertFalse(acceptsWrite(storedDay = monday + 1, writerDay = monday))
        assertTrue(acceptsWrite(storedDay = monday, writerDay = monday))
        assertTrue(acceptsWrite(storedDay = 0L, writerDay = monday))
    }
}
