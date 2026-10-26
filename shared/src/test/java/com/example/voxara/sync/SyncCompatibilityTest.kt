package com.example.voxara.sync

import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertLog
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.mergeByMinute
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.link.LinkHealth
import com.example.voxara.link.LinkMonitor
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.VoxaraSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What the two apps promise each other when they are NOT the same version (one updated, the other not), when the
 * payload is the worst the contract allows, and when the history changes under a sync in progress.
 */
class SyncCompatibilityTest {

    private val now = 1_750_000_000_000L
    private val todayStart = Math.floorDiv(now, 60_000L) / 1440 * 1440

    // ------------------------------------------------------------------ payload versions

    @Test
    fun `an OLD watch's status (fewer fields) is read with safe defaults`() {
        val old = """{"v":1,"at":$now,"app_version":"0.8","monitoring":"RUNNING","mic":true,"notifications":true,
            "calibrated":false,"dose_pct":10.5,"weekly_pct":20.0,"last_dba":70.0,"last_reading_at":$now,
            "sensitive":false,"power_save":false,"battery_exempt":true}""".trimIndent()
        val s = WatchStatus.fromJson(old)!!
        assertEquals(10.5, s.dosePercent, 0.0)
        assertEquals(0.0, s.headphoneWeeklyPercent, 0.0)
        assertTrue("an older watch had Tono", s.tonoEnabled)
        assertEquals(35, s.retentionDays)
        assertNull(s.situationMode)
        assertFalse(s.headphonesMode)
        assertEquals(LayerSource.NONE, s.headphonesSource)
        assertNull(s.lastAlertKind)
    }

    @Test
    fun `a NEWER watch's status (extra fields, values this phone has never heard of) is still read`() {
        val future = """{"v":2,"at":$now,"app_version":"2.0","monitoring":"HIBERNATING","mic":true,"notifications":true,
            "calibrated":true,"calibrated_at":$now,"dose_pct":1.0,"weekly_pct":2.0,"hp_weekly_pct":3.0,"last_dba":null,
            "sensitive":false,"power_save":false,"battery_exempt":false,"tono":false,"retention_days":60,
            "last_alert_kind":"SOMETHING_NEW","situation_mode":"SPACE","hp_mode":true,"hp_source":"TELEPATHY",
            "brand_new_field":{"nested":[1,2,3]}}"""
        val s = WatchStatus.fromJson(future)!!
        assertEquals(MonitoringStatus.OFF, s.monitoring)               // unknown state: the safe reading
        assertNull(s.lastAlertKind)
        assertNull(s.situationMode)
        assertEquals(LayerSource.NONE, s.headphonesSource)
        assertEquals(60, s.retentionDays)
        assertFalse(s.tonoEnabled)
        assertTrue(s.headphonesMode)
        assertNull(s.lastDba)
    }

    @Test
    fun `a status that is not a status is refused, not guessed`() {
        assertNull(WatchStatus.fromJson(null))
        assertNull(WatchStatus.fromJson(""))
        assertNull(WatchStatus.fromJson("[1,2]"))
        assertNull(WatchStatus.fromJson("""{"v":1,"dose_pct":3}"""))      // no time: could be shown as live
    }

    @Test
    fun `an OLD phone's headphone state (no fixed volume, no level time) is read, and a newer one with extras too`() {
        val old = """{"at":$now,"seq":5,"connected":true,"playing":true,"output":"BLUETOOTH_A2DP","volume_db":-20.0,"index":9,"max":15}"""
        val s = PhoneListeningState.decode(old.toByteArray())!!
        assertFalse(s.fixedVolume)
        assertFalse(s.possibleSpeaker)
        assertEquals("the level is taken to have begun when the state was sent", now, s.levelSinceMs)
        assertNotNull(s.estimate)

        val future = """{"at":$now,"seq":6,"connected":true,"playing":false,"output":"HOLOGRAM","index":0,"max":15,"extra":true}"""
        val f = PhoneListeningState.decode(future.toByteArray())!!
        assertEquals(OutputKind.OTHER, f.output)
        assertNull(f.estimate)
        assertNull(PhoneListeningState.decode("""{"at":1}""".toByteArray()))
    }

    @Test
    fun `settings documents tolerate fields from the other version in both directions`() {
        val newer = """{"v":2,"fields":{"alert_threshold_dba":{"value":"90","at":5},"hologram_mode":{"value":"on","at":5},
            "tono_enabled":{"value":"false","at":7,"device":"x"}},"extra":1}"""
        val d = SettingsDoc.fromJson(newer)
        assertEquals("90", d[SettingField.ALERT_THRESHOLD]?.value)
        assertEquals("false", d[SettingField.TONO_ENABLED]?.value)
        assertEquals(2, d.fields.size)
        // An older document that lacks the newer fields falls back to the defaults.
        val older = SettingsDoc().with(SettingField.ALERT_THRESHOLD, "90", 5)
        assertEquals(VoxaraSettings.DEFAULTS.copy(alertThresholdDba = 90), VoxaraSettings.from(older))
    }

    @Test
    fun `a ledger part in a format this version does not know fails loudly, so the phone says incompatible`() {
        val bytes = LedgerCodec.encode(DayBatch(1, ExposureKind.AMBIENT, 0, 1, emptyList()))
        // Re-encode with another format byte: gunzip, change the first byte, gzip again.
        val raw = java.util.zip.GZIPInputStream(bytes.inputStream()).readBytes()
        raw[0] = 9
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(raw) }
        try { LedgerCodec.decode(out.toByteArray()); fail() } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("format"))
        }
        // Truncated in transit.
        try { LedgerCodec.decode(bytes.copyOf(bytes.size / 2)); fail() } catch (_: Exception) {}
    }

    @Test
    fun `a digest request from an older phone that sends nothing means everything is stale, not a crash`() {
        val store = InMemoryMinuteStore()
        store.put(MinuteRecord(todayStart + 5, ExposureKind.AMBIENT, 60.0, 0.01, 0.2))
        val responder = WatchSyncResponder(store, NoAlerts, { now }, { null }, {})
        val stale = (SyncJson.obj(responder.handle(WearContract.RPC_DIGEST, """{"v":0}""".toByteArray())!!)["stale"] as List<*>)
        assertEquals(1, stale.size)
        // And an unknown request is an error to the caller, never silence.
        try { responder.handle(WearContract.RPC_DAY, "not json".toByteArray()); fail() } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ size and window

    private object NoAlerts : AlertLog {
        override fun add(kind: AlertKind, value: Double, level: Int, atMs: Long) = 0L
        override fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean) = Unit
        override fun page(fromMs: Long, afterId: Long, limit: Int) = emptyList<AlertRecord>()
        override fun latest(): AlertRecord? = null
        override fun prune(beforeMs: Long) = Unit
        override fun clear() = Unit
    }

    private class RecordingPhone : PhoneHistoryStore {
        val days = HashMap<Pair<Long, ExposureKind>, List<MinuteRecord>>()
        override fun digests(fromDay: Long, toDay: Long) =
            days.filterKeys { it.first in fromDay..toDay }.mapValues { LedgerCodec.digest(it.value) }
        override fun replaceDay(day: Long, kind: ExposureKind, records: List<MinuteRecord>) {
            if (records.isEmpty()) days.remove(day to kind) else days[day to kind] = records
        }
        override fun replaceAlerts(fromMs: Long, alerts: List<AlertRecord>) = Unit
    }

    @Test
    fun `the worst case - a full window of full days from both origins - fits in messages and syncs in one go`() = runBlocking {
        val watch = InMemoryMinuteStore()
        for (d in 0 until WearContract.SYNC_WINDOW_DAYS) for (m in 0 until 1440) {
            val minute = todayStart - d * 1440L + m
            watch.put(MinuteRecord(minute, ExposureKind.AMBIENT, 60.0, 0.01 + m * 1e-6, 0.1))
            if (m % 2 == 0) {
                watch.put(MinuteRecord(minute, ExposureKind.HEADPHONE, 30.0, 0.02, 0.1, category = 1, confidence = 2, origin = 0))
                watch.put(MinuteRecord(minute, ExposureKind.HEADPHONE, 30.0, 0.03, 0.1, category = 1, confidence = 2, origin = 1))
            }
        }
        val responder = WatchSyncResponder(watch, NoAlerts, { now }, { null }, {})
        var biggest = 0
        val phone = RecordingPhone()
        val report = HistorySyncEngine({ path, body ->
            responder.handle(path, body)!!.also { biggest = maxOf(biggest, it.size) }
        }, phone, nowMs = { now }).run()
        assertTrue("largest answer $biggest bytes", biggest <= WearContract.MAX_PAYLOAD_BYTES)
        assertEquals(WearContract.SYNC_WINDOW_DAYS * 2, report.daysUpdated)
        assertEquals(watch.all(ExposureKind.AMBIENT).size, phone.days.filterKeys { it.second == ExposureKind.AMBIENT }.values.sumOf { it.size })
        assertEquals(watch.all(ExposureKind.HEADPHONE).size, phone.days.filterKeys { it.second == ExposureKind.HEADPHONE }.values.sumOf { it.size })
        // Hours later nothing changed: one digest exchange and one alerts page, no day fetched.
        val again = HistorySyncEngine({ path, body -> responder.handle(path, body)!! }, phone, nowMs = { now }).run()
        assertEquals(0, again.daysUpdated)
    }

    @Test
    fun `a phone that keeps 30 days never fetches the 31st, whatever the watch reports`() = runBlocking {
        val watch = InMemoryMinuteStore()
        for (d in 0 until WearContract.SYNC_WINDOW_DAYS) watch.put(MinuteRecord(todayStart - d * 1440L, ExposureKind.AMBIENT, 60.0, 0.01, 0.1))
        val responder = WatchSyncResponder(watch, NoAlerts, { now }, { null }, {})
        val fetchedDays = ArrayList<Long>()
        val phone = RecordingPhone()
        HistorySyncEngine({ path, body ->
            if (path == WearContract.RPC_DAY) fetchedDays += (SyncJson.obj(body)["day"] as Number).toLong()
            responder.handle(path, body)!!
        }, phone, windowDays = { 30 }, nowMs = { now }).run()
        assertEquals(30, fetchedDays.size)
        assertTrue(fetchedDays.all { it > LedgerCodec.utcDay(todayStart) - 30 })
        assertEquals(30, phone.days.size)
    }

    // ------------------------------------------------------------------ the history changes during a sync

    @Test
    fun `a minute that lands in the day while its parts are being fetched is never duplicated, and the next sync has it`() = runBlocking {
        val watch = InMemoryMinuteStore()
        val day = todayStart - 1440L
        for (m in 0 until 1200) watch.put(MinuteRecord(day + m, ExposureKind.AMBIENT, 60.0, 0.01, 0.1))     // two parts
        val responder = WatchSyncResponder(watch, NoAlerts, { now }, { null }, {})
        var dayCalls = 0
        val phone = RecordingPhone()
        val engine = HistorySyncEngine({ path, body ->
            if (path == WearContract.RPC_DAY && ++dayCalls == 2) {
                // Something lands on the watch between the phone's first and second request: a row early in the day
                // (everything after it shifts by one, so the part boundary moves) and one at its end.
                watch.put(MinuteRecord(day + 1300, ExposureKind.AMBIENT, 60.0, 0.5, 0.1))
                watch.put(MinuteRecord(day + 1, ExposureKind.AMBIENT, 59.0, 0.5, 0.1, origin = 1))
            }
            responder.handle(path, body)!!
        }, phone, nowMs = { now })
        engine.run()
        val got = phone.days.getValue(LedgerCodec.utcDay(day) to ExposureKind.AMBIENT)
        assertEquals("no minute twice", got.size, got.distinctBy { it.minute to it.origin }.size)
        // The next sync notices the day differs from the watch's and fixes it: self-healing, nothing is stuck.
        engine.run()
        val fixed = phone.days.getValue(LedgerCodec.utcDay(day) to ExposureKind.AMBIENT)
        assertEquals(watch.range(day, day + 1440, ExposureKind.AMBIENT), fixed.sortedBy { it.minute })
    }

    @Test
    fun `two origins reporting the same minute count once, the louder, whatever order the rows come in`() {
        val watchRow = MinuteRecord(10, ExposureKind.HEADPHONE, 60.0, 5.0e-3, 0.2, origin = 0)
        val phoneRow = MinuteRecord(10, ExposureKind.HEADPHONE, 60.0, 9.0e-3, 0.3, origin = 1)
        assertEquals(listOf(phoneRow), mergeByMinute(listOf(watchRow, phoneRow)))
        assertEquals(listOf(phoneRow), mergeByMinute(listOf(phoneRow, watchRow)))
        // Exactly equal: the watch's own measurement, in either order.
        val twin = phoneRow.copy(origin = 0, energyPa2s = phoneRow.energyPa2s)
        assertEquals(0, mergeByMinute(listOf(phoneRow, twin)).single().origin)
        assertEquals(0, mergeByMinute(listOf(twin, phoneRow)).single().origin)
    }

    @Test
    fun `an unreachable watch is an exception to the caller, never a half-written day`() = runBlocking {
        val watch = InMemoryMinuteStore()
        for (d in 0 until 3) for (m in 0 until 50) watch.put(MinuteRecord(todayStart - d * 1440L + m, ExposureKind.AMBIENT, 60.0, 0.01, 0.1))
        val responder = WatchSyncResponder(watch, NoAlerts, { now }, { null }, {})
        val phone = RecordingPhone()
        var calls = 0
        try {
            HistorySyncEngine({ p, b -> if (++calls == 3) throw IOException("link lost") else responder.handle(p, b)!! }, phone, nowMs = { now }).run()
            fail()
        } catch (_: IOException) {}
        // Whatever was replaced is a whole day of the watch's.
        for ((key, records) in phone.days) assertEquals(watch.range(key.first * 1440, key.first * 1440 + 1440, key.second), records.sortedBy { it.minute })
    }

    // ------------------------------------------------------------------ link state from many threads

    @Test
    fun `the link monitor can be fed from several threads at once and ends in a consistent state`() {
        val monitor = LinkMonitor()
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) { t ->
            pool.execute { repeat(5_000) { i -> monitor.observe(1_000L + i, (i + t) % 3 != 0); monitor.health(2_000L + i) } }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        val final = monitor.observe(10_000_000L, true)
        assertTrue(final is LinkHealth.Connected)
    }

    @Test
    fun `an outbox counts only what is still worth sending`() {
        val box = com.example.voxara.link.Outbox(maxItems = 10, maxAgeMs = 1_000)
        box.enqueue("old", "{}", 4_500)
        box.enqueue("new", "{}", 5_000)
        val decoded = com.example.voxara.link.Outbox.decode(box.encode(), maxItems = 10, maxAgeMs = 1_000)
        assertEquals("decoding keeps both (a later enqueue or flush drops the old one)", 2, decoded.size)
        assertEquals(1, decoded.liveSize(5_800))
    }
}
