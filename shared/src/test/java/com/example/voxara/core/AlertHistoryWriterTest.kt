package com.example.voxara.core

import com.example.voxara.core.alerts.Alert
import com.example.voxara.core.alerts.AlertHistoryWriter
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertLog
import com.example.voxara.core.alerts.AlertRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The alert history is written after the haptic and off the delivering thread; nothing queued survives a "delete all". */
class AlertHistoryWriterTest {

    private class Recording : AlertLog {
        val rows = java.util.concurrent.CopyOnWriteArrayList<Triple<AlertKind, Double, Long>>()
        val threads = java.util.concurrent.CopyOnWriteArrayList<Thread>()
        /** The rows as the database would hold them, so a resolve finds the latest unresolved row of its kind. */
        val records = java.util.concurrent.CopyOnWriteArrayList<AlertRecord>()
        override fun add(kind: AlertKind, value: Double, level: Int, atMs: Long): Long {
            threads += Thread.currentThread(); rows += Triple(kind, value, atMs)
            records += AlertRecord(rows.size.toLong(), atMs, kind, value, level, acknowledged = false, snoozed = false)
            return rows.size.toLong()
        }
        override fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean) {
            val i = records.indexOfLast { it.kind == kind && !it.acknowledged && !it.snoozed }
            if (i >= 0) records[i] = records[i].copy(acknowledged = acknowledged, snoozed = snoozed)
        }
        override fun page(fromMs: Long, afterId: Long, limit: Int): List<AlertRecord> = emptyList()
        override fun latest(): AlertRecord? = null
        override fun prune(beforeMs: Long) = Unit
        override fun clear() { rows.clear(); records.clear() }
    }

    private val alerts = listOf(Alert(AlertKind.DAILY_100, 100.0, 100), Alert(AlertKind.DAILY_80, 80.0))

    @Test
    fun `record returns before the write and the write happens on another thread, in order`() {
        val log = Recording()
        val pool = Executors.newSingleThreadExecutor()
        val gate = CountDownLatch(1)
        val done = CountDownLatch(1)
        // Hold the writer thread: if record() wrote on the caller's thread it would already have a row.
        pool.execute { gate.await(5, TimeUnit.SECONDS) }
        val writer = AlertHistoryWriter({ log }, executor = pool)
        writer.record(alerts, atMs = 1_000)
        assertTrue("nothing was written on the caller's thread", log.rows.isEmpty())
        pool.execute { done.countDown() }
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(AlertKind.DAILY_100, AlertKind.DAILY_80), log.rows.map { it.first })
        assertTrue(log.threads.none { it === Thread.currentThread() })
        pool.shutdown()
    }

    @Test
    fun `the default writer uses its own daemon thread`() {
        val log = Recording()
        val done = CountDownLatch(1)
        val writer = AlertHistoryWriter({ log })
        writer.record(alerts, atMs = 2_000)
        // The shared thread runs tasks in order: this marker runs after the two rows.
        val marker = AlertHistoryWriter({ done.countDown(); log })
        marker.record(listOf(Alert(AlertKind.WEEK_80, 80.0)), atMs = 3_000)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(2, log.rows.count { it.third == 2_000L })
        assertNotEquals(Thread.currentThread(), log.threads.first())
        assertTrue(log.threads.first().isDaemon)
    }

    @Test
    fun `an alert delivered before the delete is not written after it`() {
        val log = Recording()
        val direct = Executor { it.run() }
        var wipedAt = 0L
        val writer = AlertHistoryWriter({ log }, wipedAtMs = { wipedAt }, executor = direct)

        // Queued at t=1000 but only run after "delete all" at t=5000 (the executor is held, then released).
        val held = ArrayList<Runnable>()
        val delayed = AlertHistoryWriter({ log }, wipedAtMs = { wipedAt }, executor = { held += it })
        delayed.record(alerts, atMs = 1_000)
        wipedAt = 5_000
        held.forEach { it.run() }
        assertTrue("the write that was still queued is dropped", log.rows.isEmpty())

        // An alert after the delete is new data.
        writer.record(alerts, atMs = 6_000)
        assertEquals(2, log.rows.size)
    }

    @Test
    fun `the delete fences first and clears second, as one step against the queued writes`() {
        val order = ArrayList<String>()
        AlertHistoryWriter.wipe(fence = { order += "fence" }, clear = { order += "clear" })
        assertEquals(listOf("fence", "clear"), order)
    }

    @Test
    fun `a failing log never throws into the caller`() {
        var failures = 0
        val boom = object : AlertLog by Recording() {
            override fun add(kind: AlertKind, value: Double, level: Int, atMs: Long): Long = error("disk full")
        }
        val writer = AlertHistoryWriter({ boom }, onFailure = { failures++ }, executor = { it.run() })
        writer.record(alerts, atMs = 1)           // must not throw
        assertEquals("one report per row that failed", 2, failures)
        val broken = AlertHistoryWriter({ error("no database") }, onFailure = { failures++ }, executor = { it.run() })
        broken.record(alerts, atMs = 1)
        assertEquals(3, failures)
    }

    @Test
    fun `a Got it that lands before the row is written is not lost`() {
        val log = Recording()
        // The writer thread is busy: the alert's row is queued, then the tap arrives before it has been written.
        val held = ArrayList<Runnable>()
        val writer = AlertHistoryWriter({ log }, executor = { held += it })
        writer.record(listOf(Alert(AlertKind.DAILY_80, 80.0)), atMs = 1_000)
        writer.resolve(AlertKind.DAILY_80, acknowledged = true, snoozed = false)
        assertTrue("nothing has run yet", log.records.isEmpty())
        held.forEach { it.run() }
        assertTrue("the row is written first, then acknowledged", log.records.single().acknowledged)
    }

    @Test
    fun `acknowledging twice changes nothing the second time and a newer alert stays open`() {
        val log = Recording()
        val writer = AlertHistoryWriter({ log }, executor = { it.run() })
        writer.record(listOf(Alert(AlertKind.DAILY_80, 80.0)), atMs = 1_000)
        writer.resolve(AlertKind.DAILY_80, acknowledged = true, snoozed = false)
        writer.resolve(AlertKind.DAILY_80, acknowledged = true, snoozed = false)   // a double tap
        assertTrue(log.records.single().acknowledged)
        // A later alert of the same kind is a new row: the earlier taps do not close it.
        writer.record(listOf(Alert(AlertKind.DAILY_80, 82.0)), atMs = 2_000)
        assertTrue(log.records.last().let { !it.acknowledged && !it.snoozed })
    }

    @Test
    fun `resolve reports completion even when the log fails`() {
        var done = 0
        var failures = 0
        val boom = object : AlertLog by Recording() {
            override fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean) = error("locked")
        }
        AlertHistoryWriter({ boom }, onFailure = { failures++ }, executor = { it.run() })
            .resolve(AlertKind.DAILY_80, acknowledged = true, snoozed = false, onDone = { done++ })
        assertEquals(1, failures)
        assertEquals("the broadcast still finishes", 1, done)
    }
}
