package com.example.voxara.sync

import com.example.voxara.core.headphones.ListeningTracker
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.clipToFloor
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.LedgerBook
import com.example.voxara.core.ledger.energyOf
import com.example.voxara.core.ledger.minuteOf
import com.example.voxara.core.ledger.secondsAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * "Delete all data" is a privacy promise: once it ran, nothing measured before it comes back. The dangerous moment is
 * the one where the dosimeter is RUNNING: it holds the minute in progress in memory, the listening tracker holds the
 * span that is open, and either would write the deleted time back through the periodic save, a closing minute or a
 * late hand-over. The ledger is fenced at the instant of the delete.
 */
class DeleteAllPrivacyTest {

    private val t0 = 1_750_000_000_000L - (1_750_000_000_000L % 60_000L)      // a minute boundary
    private val store = InMemoryMinuteStore()
    private val ambient = LedgerBook(ExposureKind.AMBIENT, store, TimeZone.getTimeZone("UTC"))
    private val listening = LedgerBook(ExposureKind.HEADPHONE, store, TimeZone.getTimeZone("UTC"))

    private fun seconds(kind: ExposureKind) = store.all(kind).sumOf { it.measuredSeconds }

    @Test
    fun `the minute in progress is not written back by the periodic save or by closing`() {
        // 40 s of the room at 82 dBA are in the accumulator (nothing written yet), a periodic save writes them.
        ambient.add(82.0, 40.0, t0 + 40_000)
        ambient.flush()
        assertEquals(40.0, seconds(ExposureKind.AMBIENT), 1e-9)
        ambient.add(82.0, 5.0, t0 + 45_000)               // five more, still in memory

        val deleteAt = t0 + 45_000
        ambient.wipe(deleteAt)
        assertTrue("the database is empty at once", store.all(ExposureKind.AMBIENT).isEmpty())
        assertEquals(0.0, ambient.weeklyPa2h(deleteAt), 0.0)

        // The save that runs every few seconds, and the minute closing: neither may bring anything back.
        ambient.flush()
        ambient.add(60.0, 20.0, t0 + 65_000)             // began exactly at the delete: all 20 s are new
        ambient.flush()
        val rows = store.all(ExposureKind.AMBIENT)
        assertEquals("only what was measured after the delete", 20.0, rows.sumOf { it.measuredSeconds }, 1e-9)
        assertEquals(energyOf(60.0, 20.0), rows.sumOf { it.energyPa2s }, 1e-15)
        assertEquals(energyOf(60.0, 20.0) / 3600.0, ambient.weeklyPa2h(t0 + 65_000), 1e-15)
    }

    @Test
    fun `a span that was running keeps only its part after the delete`() {
        // The listening span began at t0 + 0 and was handed over at t0 + 90 s; the delete happened at t0 + 60 s.
        listening.wipe(t0 + 60_000)
        listening.add(80.0, 90.0, t0 + 90_000, category = 1)
        listening.flush()
        assertEquals("30 s of the 90 happened after the delete", 30.0, seconds(ExposureKind.HEADPHONE), 1e-9)
        assertTrue(store.all(ExposureKind.HEADPHONE).all { it.minute >= minuteOf(t0 + 60_000) })
    }

    @Test
    fun `a span that ended before the delete is dropped whole, however late it is handed over`() {
        listening.add(85.0, 60.0, t0 + 60_000)
        listening.wipe(t0 + 120_000)
        // The tracker's drain, running after the delete: it ends at t0 + 100 s, before the instant of the delete.
        listening.add(85.0, 30.0, t0 + 100_000)
        listening.flush()
        assertTrue(store.all(ExposureKind.HEADPHONE).isEmpty())
        assertEquals(0.0, listening.weeklyPa2h(t0 + 130_000), 0.0)
    }

    @Test
    fun `the tracker's open span cannot carry the time before the delete into the ledger`() {
        val tracker = ListeningTracker()
        tracker.update(t0, 80.0)                                         // headphones start playing at t0
        val deleteAt = t0 + 50_000
        listening.wipe(deleteAt)                                         // ... the wearer deletes everything at +50 s
        for (span in tracker.drain(t0 + 80_000)) listening.add(span.dba, span.seconds, span.endMs)
        listening.flush()
        assertEquals("30 s of the 80 s are after the delete", 30.0, seconds(ExposureKind.HEADPHONE), 1e-9)
    }

    @Test
    fun `wiping one ledger does not touch the other kind, and a second delete is as clean as the first`() {
        ambient.add(70.0, 60.0, t0 + 60_000)
        ambient.flush()
        listening.add(75.0, 60.0, t0 + 120_000)
        listening.wipe(t0 + 130_000)
        assertEquals(60.0, seconds(ExposureKind.AMBIENT), 1e-9)
        assertTrue(store.all(ExposureKind.HEADPHONE).isEmpty())
        ambient.wipe(t0 + 140_000)
        ambient.wipe(t0 + 141_000)
        assertTrue(store.all(ExposureKind.AMBIENT).isEmpty())
    }

    @Test
    fun `a delete with nothing measured yet, and measuring that goes on, work`() {
        ambient.wipe(t0)
        ambient.add(65.0, 30.0, t0 + 30_000)
        ambient.add(65.0, 30.0, t0 + 60_000)
        ambient.flush()
        assertEquals(60.0, seconds(ExposureKind.AMBIENT), 1e-9)
    }

    @Test
    fun `the part of a span after a floor`() {
        assertEquals(10.0, secondsAfter(Long.MIN_VALUE, 1_000L, 10.0), 0.0)        // no floor
        assertEquals(10.0, secondsAfter(0L, 20_000L, 10.0), 1e-12)                 // began after the floor
        assertEquals(4.0, secondsAfter(16_000L, 20_000L, 10.0), 1e-12)             // straddles it
        assertEquals(0.0, secondsAfter(20_000L, 20_000L, 10.0), 0.0)               // ended exactly at it
        assertEquals(0.0, secondsAfter(30_000L, 20_000L, 10.0), 0.0)               // ended before it
    }

    @Test
    fun `the phone clips every span it queues to the moment of the delete`() {
        fun span(end: Long, seconds: Double) = PhoneListeningSample(end, seconds, -21.0, 8, 15, OutputKind.BLUETOOTH_A2DP, coarse = false)
        assertNull(clipToFloor(span(t0, 120.0), t0 + 1))
        val straddling = clipToFloor(span(t0 + 100_000, 120.0), t0 + 70_000)!!
        assertEquals(30.0, straddling.seconds, 1e-9)
        assertEquals(t0 + 100_000, straddling.endMs)
        val untouched = span(t0 + 500_000, 60.0)
        assertEquals(untouched, clipToFloor(untouched, t0))
        assertEquals("no delete ever: the floor is 0", untouched, clipToFloor(untouched, 0L))
    }
}
