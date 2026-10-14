package com.example.voxara.phone.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertLog
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.link.LinkState
import com.example.voxara.link.SyncProblem
import com.example.voxara.link.WatchNode
import com.example.voxara.phone.data.PhoneDb
import com.example.voxara.phone.data.PhoneStore
import com.example.voxara.phone.link.WatchGateway
import com.example.voxara.phone.link.WearApiUnavailable
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.sync.WatchStatus
import com.example.voxara.sync.WatchSyncResponder
import com.example.voxara.sync.WearContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The phone's link and sync, against a fake Data Layer that behaves like the real watch: the
 * shared responder over in-memory stores, and the watch's own settings merge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SyncCoordinatorTest {

    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_750_000_000_000L
    private val watchNode = WatchNode("w1", "Galaxy Watch7", nearby = true)

    // ------------------------------------------------------------------ the fake watch

    private class Minutes : MinuteStore {
        val rows = LinkedHashMap<Triple<Long, ExposureKind, Int>, MinuteRecord>()
        override fun put(r: MinuteRecord) { rows[Triple(r.minute, r.kind, r.origin)] = r }
        override fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind) =
            rows.values.filter { it.kind == kind && it.minute in fromMinute until toMinute }
        override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
            rows.keys.removeAll { it.second == kind && it.first in fromMinute until toMinute }
        }
        override fun prune(nowMinute: Long) = Unit
    }

    private class Alerts : AlertLog {
        val rows = ArrayList<AlertRecord>()
        override fun add(kind: AlertKind, value: Double, level: Int, atMs: Long): Long {
            val id = rows.size + 1L
            rows += AlertRecord(id, atMs, kind, value, level, acknowledged = false, snoozed = false)
            return id
        }
        override fun resolveLatest(kind: AlertKind, acknowledged: Boolean, snoozed: Boolean) = Unit
        override fun page(fromMs: Long, afterId: Long, limit: Int) = rows.filter { it.atMs >= fromMs && it.id > afterId }.take(limit)
        override fun latest() = rows.lastOrNull()
        override fun prune(beforeMs: Long) = Unit
        override fun clear() = rows.clear()
    }

    private val watchMinutes = Minutes()
    private val watchAlerts = Alerts()
    private var watchDoc = VoxaraSettings.DEFAULTS.let { d ->
        SettingField.entries.fold(SettingsDoc()) { doc, f -> doc.with(f, d.valueOf(f), 0L) }
    }
    private var watchDeletes = 0

    private val responder = WatchSyncResponder(
        watchMinutes, watchAlerts, { now },
        status = {
            WatchStatus(
                atMs = now, appVersion = "0.9", monitoring = MonitoringStatus.RUNNING, micGranted = true,
                notificationsGranted = true, calibrated = true, calibratedAtMs = now - 86_400_000L, dosePercent = 42.0,
                weeklyPercent = 31.0, headphoneWeeklyPercent = 12.0, lastDba = 70.0, lastReadingAtMs = now,
                sensitiveListener = false, powerSaveMode = false, ignoringBatteryOptimizations = true,
                tonoEnabled = true, retentionDays = 35,
            )
        },
        deleteAll = { watchDeletes++; watchMinutes.rows.clear(); watchAlerts.clear() },
    )

    private inner class FakeGateway : WatchGateway {
        var connected = listOf(watchNode)
        var withApp = setOf(watchNode.id)
        var reachable = true
        var apiMissing = false
        val opened = ArrayList<String>()

        override suspend fun connectedWatches(): List<WatchNode> = if (apiMissing) throw WearApiUnavailable() else connected
        override suspend fun watchesWithApp() = withApp
        override suspend fun request(nodeId: String, path: String, body: ByteArray): ByteArray {
            if (!reachable) throw IOException("watch unreachable")
            return responder.handle(path, body) ?: error("unhandled $path")
        }
        /**
         * Delivered only while reachable, like a DataItem waiting for the watch to reconnect; the
         * watch then publishes its applied settings, which reach the phone's listener.
         */
        override suspend fun publishSettings(json: String) {
            if (!reachable) return
            watchDoc = SettingsMerge.watchReceives(watchDoc, SettingsDoc.fromJson(json), now).merged
            coordinator.onWatchSettings(watchDoc.toJson())
        }
        override suspend fun latestPayload(nodeId: String, path: String) =
            if (path == WearContract.PATH_SETTINGS_WATCH) watchDoc.toJson() else null
        override suspend fun openOnWatch(nodeId: String, uri: String) { opened += uri }
    }

    // ------------------------------------------------------------------ the phone under test

    private val gateway = FakeGateway()
    private val db = PhoneDb(RuntimeEnvironment.getApplication(), name = null)   // in-memory
    private val store by lazy {
        PhoneStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { tmp.newFile("phone.preferences_pb").also { it.delete() } },
            )
        )
    }
    private val coordinator by lazy { SyncCoordinator(gateway, store, db) { now } }

    private fun addWatchMinutes(count: Int) {
        val start = Math.floorDiv(now, 60_000L) - count
        for (i in 0 until count) watchMinutes.put(MinuteRecord(start + i, ExposureKind.AMBIENT, 60.0, 0.01, 0.2))
    }

    // ------------------------------------------------------------------ link states

    @Test
    fun `link states - none, not installed, linked, problem, no Wear API`() = runBlocking<Unit> {
        gateway.connected = emptyList()
        assertEquals(LinkState.NoWatch, coordinator.refreshLink())

        gateway.connected = listOf(watchNode); gateway.withApp = emptySet()
        assertEquals(LinkState.NotInstalled(listOf(watchNode)), coordinator.refreshLink())

        gateway.withApp = setOf(watchNode.id)
        assertEquals(LinkState.Linked(watchNode, emptyList(), null), coordinator.refreshLink())

        gateway.reachable = false
        assertFalse(coordinator.sync())
        val problem = coordinator.link.value as LinkState.Problem
        assertEquals(SyncProblem.UNREACHABLE, problem.problem)

        gateway.reachable = true
        assertTrue(coordinator.sync())
        assertEquals(LinkState.Linked(watchNode, emptyList(), now), coordinator.link.value)

        gateway.apiMissing = true
        assertEquals(LinkState.WearApiMissing, coordinator.refreshLink())
    }

    @Test
    fun `several watches - the chosen one is used`() = runBlocking<Unit> {
        val other = WatchNode("w2", "Pixel Watch", nearby = true)
        gateway.connected = listOf(watchNode, other); gateway.withApp = setOf("w1", "w2")
        coordinator.chooseWatch("w2")
        assertEquals(other, (coordinator.link.value as LinkState.Linked).watch)
        assertEquals(listOf(watchNode), (coordinator.link.value as LinkState.Linked).others)
    }

    // ------------------------------------------------------------------ sync

    @Test
    fun `a sync stores the watch's status and history`() = runBlocking<Unit> {
        addWatchMinutes(120)
        watchAlerts.add(AlertKind.DAILY_50, 51.0, 0, now - 60_000)
        assertTrue(coordinator.sync())
        val s = store.read()
        assertEquals(42.0, s.status!!.dosePercent, 0.0)
        assertEquals(now, s.sync.lastSuccessMs)
        assertEquals(120, db.minutes(0, Long.MAX_VALUE, ExposureKind.AMBIENT).size)
        assertEquals(1, db.alerts().size)
        // A second sync changes nothing and does not duplicate a minute.
        assertTrue(coordinator.sync())
        assertEquals(120, db.minutes(0, Long.MAX_VALUE, ExposureKind.AMBIENT).size)
    }

    // ------------------------------------------------------------------ settings

    @Test
    fun `the first sync adopts the watch's settings, nothing pending`() = runBlocking<Unit> {
        watchDoc = watchDoc.with(SettingField.ALERT_THRESHOLD, "88", now - 3_600_000)
        coordinator.sync()
        assertEquals(88, VoxaraSettings.from(store.read().phoneSettings).alertThresholdDba)
        assertTrue(coordinator.pendingSettings().isEmpty())
    }

    @Test
    fun `a change made while the watch is away stays pending until it applies`() = runBlocking<Unit> {
        coordinator.sync()
        gateway.reachable = false
        now += 60_000
        coordinator.changeSetting(SettingField.SNOOZE_MINUTES, "30")
        assertEquals(setOf(SettingField.SNOOZE_MINUTES), coordinator.pendingSettings())
        assertEquals("60", watchDoc[SettingField.SNOOZE_MINUTES]!!.value)

        gateway.reachable = true
        now += 60_000
        coordinator.sync()
        assertEquals("30", watchDoc[SettingField.SNOOZE_MINUTES]!!.value)
        assertTrue(coordinator.pendingSettings().isEmpty())
    }

    @Test
    fun `a newer change on the watch wins over an older one from the phone`() = runBlocking<Unit> {
        coordinator.sync()
        gateway.reachable = false
        coordinator.changeSetting(SettingField.ALERT_THRESHOLD, "90")          // phone, at t
        now += 120_000
        watchDoc = watchDoc.with(SettingField.ALERT_THRESHOLD, "82", now)      // watch, 2 min later
        gateway.reachable = true
        coordinator.sync()
        assertEquals("82", watchDoc[SettingField.ALERT_THRESHOLD]!!.value)
        assertEquals(82, VoxaraSettings.from(store.read().phoneSettings).alertThresholdDba)
        assertTrue(coordinator.pendingSettings().isEmpty())
    }

    @Test
    fun `the watch clamps what the phone sends`() = runBlocking<Unit> {
        coordinator.sync()
        now += 60_000
        // A broken or tampered phone document: the watch clamps it (the phone's own parser would too).
        gateway.publishSettings("""{"v":1,"fields":{"snooze_minutes":{"value":"999","at":$now}}}""")
        assertEquals("120", watchDoc[SettingField.SNOOZE_MINUTES]!!.value)
    }

    // ------------------------------------------------------------------ delete all

    @Test
    fun `delete all clears the phone now and the watch as soon as it is reachable`() = runBlocking<Unit> {
        addWatchMinutes(30)
        coordinator.sync()
        assertFalse(db.isEmpty())

        gateway.reachable = false
        assertEquals(SyncCoordinator.DeleteResult.PHONE_ONLY_WATCH_PENDING, coordinator.deleteAll())
        assertTrue(db.isEmpty())
        assertNull(store.read().status)
        assertTrue(store.read().deletePending)
        assertEquals(0, watchDeletes)

        gateway.reachable = true
        coordinator.sync()
        assertEquals(1, watchDeletes)
        assertFalse(store.read().deletePending)
        assertTrue(db.isEmpty())
    }

    @Test
    fun `opening on the watch uses the linked watch`() = runBlocking<Unit> {
        coordinator.refreshLink()
        assertTrue(coordinator.openOnWatch(WearContract.LINK_CALIBRATION))
        assertEquals(listOf(WearContract.LINK_CALIBRATION), gateway.opened)
    }
}
