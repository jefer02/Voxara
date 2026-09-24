package com.example.voxara.core

import com.example.voxara.core.dose.NoiseDoseEngine
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.LedgerBook
import com.example.voxara.core.ledger.MinuteAccumulator
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore
import com.example.voxara.core.ledger.ROLLING_WINDOW_MINUTES
import com.example.voxara.core.ledger.RollingWeek
import com.example.voxara.core.ledger.SlidingLeq
import com.example.voxara.core.ledger.dayStartMinute
import com.example.voxara.core.ledger.energyOf
import com.example.voxara.core.ledger.hourlyProfile
import com.example.voxara.core.ledger.levelOf
import com.example.voxara.core.ledger.mergeByMinute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Minute ledger, rolling 7-day window and sustained level — the Phase 1 data foundation. */
class MinuteLedgerTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** In-memory stand-in for SQLite. */
    private class FakeStore : MinuteStore {
        val rows = HashMap<Triple<Long, ExposureKind, Int>, MinuteRecord>()
        override fun put(r: MinuteRecord) { rows[Triple(r.minute, r.kind, r.origin)] = r }
        override fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind) =
            rows.values.filter { it.kind == kind && it.minute in fromMinute until toMinute }.sortedBy { it.minute }
        override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
            rows.keys.removeAll { it.second == kind && it.first in fromMinute until toMinute }
        }
        override fun prune(nowMinute: Long) = Unit
    }

    @Test
    fun `a burst spanning a minute boundary is split by time`() {
        val acc = MinuteAccumulator(ExposureKind.AMBIENT)
        // 10 s ending 4 s into minute 101: 6 s belong to minute 100, 4 s to 101.
        val closed = acc.add(90.0, 10.0, 101 * 60_000L + 4_000L)
        assertEquals(1, closed.size)
        assertEquals(100L, closed[0].minute)
        assertEquals(6.0, closed[0].measuredSeconds, 1e-6)
        assertEquals(4.0, acc.current()!!.measuredSeconds, 1e-6)
        assertEquals(90.0, closed[0].laeq!!, 1e-9)
    }

    @Test
    fun `minute records carry the exact NIOSH dose`() {
        val acc = MinuteAccumulator(ExposureKind.AMBIENT)
        val engine = NoiseDoseEngine()
        repeat(6) { i ->
            acc.add(94.0, 10.0, 60_000L + (i + 1) * 10_000L)
            engine.accumulate(94.0, 10.0)
        }
        assertEquals(engine.dosePercent, acc.current()!!.nioshDosePercent, 1e-9)
    }

    @Test
    fun `the hourly profile is a true energy average (bug 9)`() {
        // 59 quiet minutes at 60 dBA and one loud minute at 100 dBA in the same hour.
        val records = (0 until 60).map { m ->
            val l = if (m == 59) 100.0 else 60.0
            MinuteRecord(m.toLong(), ExposureKind.AMBIENT, 60.0, energyOf(l, 60.0), 0.0)
        }
        val expected = levelOf(records.sumOf { it.energyPa2s }, 3600.0)!!
        val hour0 = hourlyProfile(records, dayStartMinute = 0L)[0].toDouble()
        assertEquals(expected, hour0, 0.01)
        // The old pairwise running average would have reported ~97 dBA here.
        assertTrue(hour0 < 83.0)
    }

    @Test
    fun `the weekly window rolls instead of resetting`() {
        val w = RollingWeek()
        w.set(0L, 3600.0)                                   // 1 Pa2h at minute 0
        w.set(3 * 1440L, 1800.0)                            // 0.5 Pa2h three days later
        assertEquals(1.5, w.totalPa2h(3 * 1440L), 1e-9)
        assertEquals("still inside 7 days", 1.5, w.totalPa2h(ROLLING_WINDOW_MINUTES - 1), 1e-9)
        assertEquals("the first minute has left the window", 0.5, w.totalPa2h(ROLLING_WINDOW_MINUTES), 1e-9)
        assertEquals(0.0, w.totalPa2h(3 * 1440L + ROLLING_WINDOW_MINUTES), 1e-9)
    }

    @Test
    fun `forty hours at 80 dBA spread over a rolling week is the WHO allowance`() {
        val w = RollingWeek()
        // 8 h a day for 5 days at 80 dBA.
        var minute = 0L
        repeat(5) { d ->
            repeat(8 * 60) { m -> w.set(d * 1440L + m, energyOf(80.0, 60.0)) }
            minute = d * 1440L + 8 * 60
        }
        assertEquals(1.6, w.totalPa2h(minute), 1e-6)
    }

    @Test
    fun `sustained level needs the window covered`() {
        val s = SlidingLeq(3 * 60_000L)
        s.add(95.0, 10.0, 10_000L)
        assertNull("10 s is not sustained", s.value(10_000L))
        var t = 10_000L
        while (t < 180_000L) { t += 10_000L; s.add(95.0, 10.0, t) }
        assertEquals(95.0, s.value(t)!!, 1e-9)
        // A single loud burst inside a quiet window barely moves it.
        val q = SlidingLeq(3 * 60_000L)
        var u = 0L
        while (u < 180_000L) { u += 10_000L; q.add(if (u == 90_000L) 100.0 else 60.0, 10.0, u) }
        assertTrue(q.value(u)!! < 90.0)
    }

    @Test
    fun `the ledger book keeps history, rolling total and resets consistent`() {
        val store = FakeStore()
        val book = LedgerBook(ExposureKind.AMBIENT, store, utc)
        val day = 20_717L
        val start = dayStartMinute(day, utc) * 60_000L
        // One hour at 85 dBA from 09:00, in 10 s bursts.
        var t = start + 9 * 3_600_000L
        repeat(360) { t += 10_000L; book.add(85.0, 10.0, t) }
        val days = book.days(day, t)
        assertEquals(day, days[0].dayEpoch)
        assertEquals(85.0, days[0].hourly[9].toDouble(), 0.01)
        assertEquals(12.5, days[0].nioshDosePercent, 1e-6)           // 1 h of 8 h at 85
        assertEquals(60.0, days[0].measuredMinutes, 1e-6)
        assertEquals(energyOf(85.0, 3600.0) / 3600.0, book.weeklyPa2h(t), 1e-9)

        book.clearDay(day, t)
        assertEquals(0.0, book.weeklyPa2h(t), 1e-12)
        assertEquals(0.0, book.days(day, t)[0].nioshDosePercent, 0.0)
    }

    @Test
    fun `a minute reported by two devices counts once, the louder one`() {
        val a = MinuteRecord(10L, ExposureKind.HEADPHONE, 60.0, energyOf(80.0, 60.0), 0.0, origin = 0)
        val b = MinuteRecord(10L, ExposureKind.HEADPHONE, 60.0, energyOf(90.0, 60.0), 0.0, origin = 1)
        val merged = mergeByMinute(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals(90.0, merged[0].laeq!!, 1e-9)
    }

    @Test
    fun `day boundaries follow the local zone`() {
        val bogota = TimeZone.getTimeZone("America/Bogota")
        // Local midnight in Bogota (UTC-5) is 05:00 UTC.
        assertEquals(20_717L * 1440 + 5 * 60, dayStartMinute(20_717L, bogota))
    }
}
