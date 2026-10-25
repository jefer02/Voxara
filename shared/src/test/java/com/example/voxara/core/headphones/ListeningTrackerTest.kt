package com.example.voxara.core.headphones

import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.LedgerBook
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * LISTENING ACCUMULATION — event-accurate spans, the ledger they end up in, the rule that nothing is added
 * without playback, and "lower to safe volume".
 */
class ListeningTrackerTest {

    private val t0 = 1_750_000_000_000L
    private val sec = 1_000L

    @Test
    fun `an event closes the span at the level that was in force`() {
        val t = ListeningTracker()
        t.update(t0, 70.0)                       // playing at 70
        t.update(t0 + 20 * sec, 85.0)            // volume up at +20 s
        t.update(t0 + 50 * sec, null)            // paused at +50 s
        val spans = t.drain(t0 + 90 * sec)
        assertEquals(listOf(70.0 to 20.0, 85.0 to 30.0), spans.map { it.dba to it.seconds })
        assertEquals(t0 + 50 * sec, spans.last().endMs)
    }

    @Test
    fun `no playback adds nothing even with headphones connected`() {
        val t = ListeningTracker()
        t.update(t0, null)                       // connected but not playing: the caller passes null
        assertTrue(t.drain(t0 + 10 * 60 * sec).isEmpty())
    }

    @Test
    fun `a guard tick merges into one span and a missed event cannot credit hours`() {
        val t = ListeningTracker()
        t.update(t0, 75.0)
        for (i in 1..5) t.update(t0 + i * 2 * sec, 75.0)      // 2 s guard ticks, same level
        val one = t.drain(t0 + 10 * sec)
        assertEquals(1, one.size)
        assertEquals(10.0, one[0].seconds, 1e-9)
        // The process was frozen for an hour with nothing watching: only the cap is credited.
        t.update(t0 + 10 * sec, 75.0)
        val frozen = t.drain(t0 + 10 * sec + 3_600 * sec)
        assertEquals(ListeningTracker.MAX_SPAN_SECONDS, frozen.sumOf { it.seconds }, 1e-9)
    }

    @Test
    fun `drain keeps the running span going`() {
        val t = ListeningTracker()
        t.update(t0, 80.0)
        assertEquals(30.0, t.drain(t0 + 30 * sec).sumOf { it.seconds }, 1e-9)
        assertEquals(30.0, t.drain(t0 + 60 * sec).sumOf { it.seconds }, 1e-9)
        assertEquals(80.0, t.currentDba!!, 0.0)
    }

    // ------------------------------------------------------------------ the ledger

    private class MapStore : MinuteStore {
        val rows = HashMap<Triple<Long, ExposureKind, Int>, MinuteRecord>()
        override fun put(r: MinuteRecord) { rows[Triple(r.minute, r.kind, r.origin)] = r }
        override fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind) =
            rows.values.filter { it.minute in fromMinute until toMinute && it.kind == kind }.sortedBy { it.minute }
        override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
            rows.keys.removeAll { it.first in fromMinute until toMinute && it.second == kind }
        }
        override fun prune(nowMinute: Long) = Unit
    }

    @Test
    fun `listening spans reach the headphone ledger and the weekly dose, with their origin`() {
        val store = MapStore()
        val watch = LedgerBook(ExposureKind.HEADPHONE, store, TimeZone.getTimeZone("UTC"), origin = 0)
        val phone = LedgerBook(ExposureKind.HEADPHONE, store, TimeZone.getTimeZone("UTC"), origin = ORIGIN_PHONE)
        val t = ListeningTracker()
        t.update(t0, 85.0)
        // The service drains every 30 s while something plays (and on every event).
        val spans = ArrayList<ListeningTracker.Span>()
        for (i in 1..20) spans += t.drain(t0 + i * 30 * sec)
        t.update(t0 + 600 * sec, null)
        for (s in spans) watch.add(s.dba, s.seconds, s.endMs, category = HeadphoneCategory.EARBUDS.code)
        watch.flush()
        assertEquals(600.0, store.rows.values.sumOf { it.measuredSeconds }, 1e-6)
        assertTrue(store.rows.values.all { it.kind == ExposureKind.HEADPHONE && it.origin == 0 })
        // 10 minutes at 85 dBA: 4e-10 * 10^8.5 Pa2 * 600 s / 3600 s/h
        val expected = 4.0e-10 * Math.pow(10.0, 8.5) * 600 / 3600
        assertEquals(expected, watch.weeklyPa2h(t0 + 600 * sec), expected * 1e-6)

        // The phone's own minutes carry their own origin and count toward the same weekly dose.
        val t2 = ListeningTracker()
        t2.update(t0 + 1_200 * sec, 70.0)
        for (s in t2.drain(t0 + 1_260 * sec)) phone.add(s.dba, s.seconds, s.endMs, category = HeadphoneCategory.UNKNOWN.code)
        phone.flush()
        assertTrue(store.rows.keys.any { it.third == ORIGIN_PHONE })
    }

    @Test
    fun `a minute reported by two devices counts once, the louder one`() {
        val store = MapStore()
        val watch = LedgerBook(ExposureKind.HEADPHONE, store, TimeZone.getTimeZone("UTC"), origin = 0)
        val phone = LedgerBook(ExposureKind.HEADPHONE, store, TimeZone.getTimeZone("UTC"), origin = ORIGIN_PHONE)
        val end = (t0 / 60_000 + 1) * 60_000                      // a minute boundary
        watch.add(70.0, 60.0, end)
        phone.add(78.0, 60.0, end)
        // The next minute starts (the first one closes on both sides), then the ledgers are re-read.
        watch.add(70.0, 1.0, end + 30_000)
        phone.add(78.0, 1.0, end + 30_000)
        watch.flush(); phone.flush()
        watch.load(end + 31_000)
        // Minute one counts once, as the louder device (78 dBA). (The minute still in progress counts on its
        // own until it closes and is merged: that is the ledger's rule, so it is this device's 70 dBA second.)
        val louder = (4.0e-10 * Math.pow(10.0, 7.8) * 60 + 4.0e-10 * Math.pow(10.0, 7.0) * 1) / 3600
        assertEquals(louder, watch.weeklyPa2h(end + 31_000), louder * 1e-6)
    }

    // ------------------------------------------------------------------ lower to safe volume

    /** A made-up but typical curve: 15 steps, 3 dB per step below full scale. */
    private fun curve(max: Int): (Int) -> Double? = { i -> -3.0 * (max - i) }

    @Test
    fun `lower to safe volume picks the highest step at or below 80 dBA and never raises`() {
        val dbAt = curve(15)
        // 100 dBA at step 15 and 3 dB per step: 80 dBA needs -20 dB. Step 9 is -18 dB (82 dBA, too loud),
        // step 8 is -21 dB (79 dBA): the answer is 8.
        assertEquals(8, safeVolumeIndex(current = 15, max = 15, dbAt = dbAt))
        assertEquals(8, safeVolumeIndex(current = 12, max = 15, dbAt = dbAt))
        // Already safe: unchanged. Never raised.
        assertEquals(5, safeVolumeIndex(current = 5, max = 15, dbAt = dbAt))
        assertEquals(0, safeVolumeIndex(current = 0, max = 15, dbAt = dbAt))
        val chosen = safeVolumeIndex(current = 15, max = 15, dbAt = dbAt)
        assertTrue(levelAtIndex(chosen, 15, dbAt)!! <= HeadphoneProfile.SAFE_DBA)
        assertTrue(levelAtIndex(chosen + 1, 15, dbAt)!! > HeadphoneProfile.SAFE_DBA)
    }

    @Test
    fun `without a dB curve the linear fallback is used and is still at or below the target`() {
        val none: (Int) -> Double? = { null }
        val i = safeVolumeIndex(current = 15, max = 15, dbAt = none)
        assertTrue(levelAtIndex(i, 15, none)!! <= HeadphoneProfile.SAFE_DBA)
        assertEquals(10, i)                                   // 100 - 60 * (1 - i/15) <= 80  ->  i <= 10
    }

    @Test
    fun `when even the quietest step is above the target the only safe step is mute`() {
        assertEquals(0, safeVolumeIndex(current = 5, max = 15, dbAt = { 0.0 }))
    }
}
