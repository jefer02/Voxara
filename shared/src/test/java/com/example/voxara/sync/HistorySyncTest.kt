package com.example.voxara.sync

import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertLog
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore
import com.example.voxara.core.monitoring.MonitoringStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Batch sync end to end, with in-memory fakes of both devices and of the Data Layer. */
class HistorySyncTest {

    private var now = 1_750_000_000_000L
    private val todayStart = Math.floorDiv(now, 60_000L) / 1440 * 1440

    // ------------------------------------------------------------------ fakes

    private class MapStore : MinuteStore {
        val rows = LinkedHashMap<Triple<Long, ExposureKind, Int>, MinuteRecord>()
        override fun put(r: MinuteRecord) { rows[Triple(r.minute, r.kind, r.origin)] = r }
        override fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind) =
            rows.values.filter { it.kind == kind && it.minute in fromMinute until toMinute }.sortedBy { it.minute }
        override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
            rows.keys.removeAll { it.second == kind && it.first in fromMinute until toMinute }
        }
        override fun prune(nowMinute: Long) = Unit
    }

    private class ListLog : AlertLog {
        val rows = ArrayList<AlertRecord>()
        override fun add(kind: AlertKind, value: Double, level: Int, atMs: Long): Long {
            val id = (rows.maxOfOrNull { it.id } ?: 0L) + 1
            rows += AlertRecord(id, atMs, kind, value, level, acknowledged = false, snoozed = false)
            return id
        }
        override fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean) {
            val i = rows.indexOfLast { it.kind == kind && !it.acknowledged && !it.snoozed }
            if (i >= 0) rows[i] = rows[i].copy(acknowledged = acknowledged, snoozed = snoozed)
        }
        override fun page(fromMs: Long, afterId: Long, limit: Int) =
            rows.filter { it.atMs >= fromMs && it.id > afterId }.sortedBy { it.id }.take(limit)
        override fun latest() = rows.maxByOrNull { it.atMs }
        override fun prune(beforeMs: Long) { rows.removeAll { it.atMs < beforeMs } }
        override fun clear() = rows.clear()
    }

    /** The phone's store: rows per (day, kind), replaced whole. */
    private class PhoneStore : PhoneHistoryStore {
        val days = HashMap<Pair<Long, ExposureKind>, List<MinuteRecord>>()
        val alerts = ArrayList<AlertRecord>()
        var replaces = 0
        override fun digests(fromDay: Long, toDay: Long) =
            days.filterKeys { it.first in fromDay..toDay }.mapValues { LedgerCodec.digest(it.value) }
        override fun replaceDay(day: Long, kind: ExposureKind, records: List<MinuteRecord>) {
            replaces++
            if (records.isEmpty()) days.remove(day to kind) else days[day to kind] = records
        }
        override fun replaceAlerts(fromMs: Long, alerts: List<AlertRecord>) {
            this.alerts.removeAll { it.atMs >= fromMs }
            this.alerts += alerts
        }
        fun all(kind: ExposureKind) = days.filterKeys { it.second == kind }.values.flatten().sortedWith(compareBy({ it.minute }, { it.origin }))
    }

    private val watchMinutes = MapStore()
    private val watchAlerts = ListLog()
    private var deleted = 0
    private val responder = WatchSyncResponder(watchMinutes, watchAlerts, { now }, { status() }, {
        deleted++; watchMinutes.rows.clear(); watchAlerts.clear()
    })

    /** Loopback "Data Layer": every response is checked against the 100 KB message limit. */
    private var calls = 0
    private var failAfter = Int.MAX_VALUE
    private val rpc = WatchRpc { path, body ->
        if (++calls > failAfter) throw IOException("watch went away")
        val out = responder.handle(path, body) ?: error("unhandled $path")
        assertTrue("$path answered ${out.size} bytes", out.size <= WearContract.MAX_PAYLOAD_BYTES)
        out
    }

    private val phone = PhoneStore()
    private val engine = HistorySyncEngine(rpc, phone) { now }

    private fun status() = WatchStatus(
        atMs = now, appVersion = "0.9", monitoring = MonitoringStatus.RUNNING, micGranted = true,
        notificationsGranted = true, calibrated = false, calibratedAtMs = null, dosePercent = 12.5,
        weeklyPercent = 30.0, headphoneWeeklyPercent = 4.0, lastDba = 71.0, lastReadingAtMs = now - 30_000,
        sensitiveListener = false, powerSaveMode = false, ignoringBatteryOptimizations = true,
        tonoEnabled = true, retentionDays = 35, lastAlertKind = AlertKind.DAILY_50, lastAlertAtMs = now - 60_000,
        lastAlertValue = 51.0,
    )

    private fun rec(minute: Long, kind: ExposureKind = ExposureKind.AMBIENT, origin: Int = 0, energy: Double = 0.0123) =
        MinuteRecord(minute, kind, 60.0, energy * (1 + minute % 7), 0.2 + (minute % 3) * 0.1, origin = origin)

    private fun fill(days: Int, perDay: Int, kind: ExposureKind = ExposureKind.AMBIENT, origin: Int = 0) {
        for (d in 0 until days) for (m in 0 until perDay) watchMinutes.put(rec(todayStart - d * 1440L + m, kind, origin))
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun `a first sync copies the watch's window exactly`() = runBlocking {
        fill(days = 5, perDay = 300)
        fill(days = 2, perDay = 40, kind = ExposureKind.HEADPHONE, origin = 1)
        val r = engine.run()
        assertEquals(7, r.daysUpdated)
        assertEquals(watchMinutes.range(0, Long.MAX_VALUE, ExposureKind.AMBIENT), phone.all(ExposureKind.AMBIENT))
        assertEquals(watchMinutes.range(0, Long.MAX_VALUE, ExposureKind.HEADPHONE), phone.all(ExposureKind.HEADPHONE))
    }

    @Test
    fun `a repeated sync changes nothing and fetches no days`() = runBlocking {
        fill(days = 3, perDay = 100)
        engine.run()
        val before = phone.days.toMap()
        val replaces = phone.replaces
        val r = engine.run()
        assertEquals(0, r.daysUpdated)
        assertEquals(replaces, phone.replaces)
        assertEquals(before, phone.days)
    }

    @Test
    fun `only the changed day is fetched again`() = runBlocking {
        fill(days = 4, perDay = 60)
        engine.run()
        watchMinutes.put(rec(todayStart + 61))
        val r = engine.run()
        assertEquals(1, r.daysUpdated)
        assertEquals(61, phone.all(ExposureKind.AMBIENT).count { it.minute >= todayStart })
    }

    @Test
    fun `minutes removed on the watch are removed on the phone`() = runBlocking {
        fill(days = 2, perDay = 50)
        engine.run()
        watchMinutes.clear(todayStart, todayStart + 1440, ExposureKind.AMBIENT)   // "start today over"
        engine.run()
        assertTrue(phone.all(ExposureKind.AMBIENT).none { it.minute >= todayStart })
        assertEquals(50, phone.all(ExposureKind.AMBIENT).size)
    }

    @Test
    fun `a full day from two origins is split into parts under the size limit`() = runBlocking {
        fill(days = 1, perDay = 1440, kind = ExposureKind.HEADPHONE, origin = 0)
        fill(days = 1, perDay = 1440, kind = ExposureKind.HEADPHONE, origin = 1)
        engine.run()
        assertEquals(2880, phone.all(ExposureKind.HEADPHONE).size)
        assertEquals(3, LedgerCodec.split(0, ExposureKind.HEADPHONE, phone.all(ExposureKind.HEADPHONE)).size)
    }

    @Test
    fun `an interrupted sync leaves valid data and the retry completes it`() = runBlocking {
        fill(days = 6, perDay = 200)
        failAfter = 4                  // digest + 3 days, then the watch disappears
        try { engine.run(); fail("expected the sync to fail") } catch (_: IOException) {}
        assertEquals(3, phone.days.size)
        failAfter = Int.MAX_VALUE
        engine.run()
        assertEquals(watchMinutes.range(0, Long.MAX_VALUE, ExposureKind.AMBIENT), phone.all(ExposureKind.AMBIENT))
    }

    @Test
    fun `days outside the watch's retention window are never touched`() = runBlocking {
        val old = todayStart - 40 * 1440L
        phone.days[LedgerCodec.utcDay(old) to ExposureKind.AMBIENT] = listOf(rec(old))
        engine.run()
        assertEquals(listOf(rec(old)), phone.days[LedgerCodec.utcDay(old) to ExposureKind.AMBIENT])
    }

    @Test
    fun `alerts sync in pages and acknowledgements update`() = runBlocking {
        repeat(700) { i -> watchAlerts.add(AlertKind.AMBIENT_LOUD, 90.0, 0, now - 1_000_000L + i) }
        watchAlerts.add(AlertKind.DAILY_100, 101.0, 100, now)
        engine.run()
        assertEquals(701, phone.alerts.size)
        assertTrue(phone.alerts.none { it.acknowledged })
        watchAlerts.resolveLatest(AlertKind.DAILY_100, acknowledged = true, snoozed = false)
        engine.run()
        assertEquals(701, phone.alerts.size)
        assertTrue(phone.alerts.single { it.kind == AlertKind.DAILY_100 }.acknowledged)
        assertEquals(100, phone.alerts.single { it.kind == AlertKind.DAILY_100 }.level)
    }

    @Test
    fun `delete asks the watch to clear everything`() = runBlocking {
        fill(days = 1, perDay = 10)
        watchAlerts.add(AlertKind.DAILY_50, 50.0, 0, now)
        rpc.call(WearContract.RPC_DELETE, ByteArray(0))
        assertEquals(1, deleted)
        assertTrue(watchMinutes.rows.isEmpty())
        engine.run()
        assertTrue(phone.days.isEmpty() && phone.alerts.isEmpty())
    }

    @Test
    fun `status and settings travel as json and come back equal`() = runBlocking {
        val s = WatchStatus.fromJson(rpc.call(WearContract.RPC_STATUS, ByteArray(0)).toString(Charsets.UTF_8))
        assertEquals(status(), s)
        assertEquals(null, WatchStatus.fromJson("{}"))
    }

    @Test
    fun `the ledger codec is bit-exact and rejects garbage`() {
        val records = (0 until 1000).map { rec(todayStart + it, energy = 1e-7 * (it + 1)) }
        val batch = DayBatch(LedgerCodec.utcDay(todayStart), ExposureKind.AMBIENT, 0, 1, records)
        val back = LedgerCodec.decode(LedgerCodec.encode(batch))
        assertEquals(batch, back)
        assertEquals(LedgerCodec.digest(records), LedgerCodec.digest(records.reversed()))
        assertArrayEquals(LedgerCodec.encode(batch), LedgerCodec.encode(back))
        try { LedgerCodec.decode(byteArrayOf(1, 2, 3)); fail() } catch (_: Exception) {}
        // A record count above the part size is refused (it could only come from a broken sender).
        try { LedgerCodec.encode(batch.copy(records = records + rec(todayStart + 1001))); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `a responder never answers a path it does not own`() {
        assertEquals(null, responder.handle("/voxara/listening/1", ByteArray(0)))
    }
}
