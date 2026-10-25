package com.example.voxara.phone.sync

import android.util.Log
import com.example.voxara.link.LinkHealth
import com.example.voxara.link.LinkMonitor
import com.example.voxara.link.LinkResolver
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
import com.example.voxara.sync.HistorySyncEngine
import com.example.voxara.sync.WatchStatus
import com.example.voxara.sync.WearContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * THE PHONE'S SIDE OF THE LINK. Pull-based and safe to call at any time (app open, pull to
 * refresh, the hourly worker, a Data Layer event): one sync runs at a time, a failure is recorded
 * and shown with help, and nothing here can affect the watch's alerts — the watch only ever
 * receives validated settings and read-only requests (plus an explicit delete request).
 */
class SyncCoordinator(
    private val gateway: WatchGateway,
    private val store: PhoneStore,
    private val db: PhoneDb,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _link = MutableStateFlow<LinkState>(LinkState.Checking)
    val link: StateFlow<LinkState> = _link.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val mutex = Mutex()

    // The honest state of the link, for the screens: connected, reconnecting, or disconnected since...
    private val monitor = LinkMonitor()
    private val _health = MutableStateFlow<LinkHealth>(LinkHealth.Unknown)
    val health: StateFlow<LinkHealth> = _health.asStateFlow()

    /** The watch answered (or did not): an observation of the link, from a sync, a message or a callback. */
    fun observeReachable(reachable: Boolean) {
        _health.value = monitor.observe(nowMs(), reachable)
    }

    /** Lets "reconnecting" turn into "disconnected" with time, with no new observation. */
    fun tickHealth() {
        _health.value = monitor.health(nowMs())
    }

    /**
     * Something changed about the watch (its capability appeared or went): look, and when it has just come
     * back run what it missed: a sync (settings and history both ways) and [afterReconnect] (queued listening,
     * the current headphone state). No user action. Never throws.
     */
    suspend fun onWatchEvent(afterReconnect: suspend () -> Unit = {}) {
        val reachable = linkedWatch(refreshLink()) != null
        if (!reachable) return
        // Not "was the link shown as connected": a message that got through (live headphone state, a lowered volume)
        // marks the link connected before this event is handled, and the catch-up would be skipped for good.
        if (caughtUp) return
        caughtUp = true
        if (!sync()) caughtUp = false         // the watch went again mid-sync: the next event tries again
        runCatching { afterReconnect() }.onFailure { if (it is CancellationException) throw it }
    }

    /** The watch has been caught up since it was last seen to go (settings, history and queued listening re-sent). */
    @Volatile private var caughtUp = false

    /** Re-reads which watches are connected and which have the app. Never throws. */
    suspend fun refreshLink(): LinkState {
        val state = try {
            val s = store.read()
            LinkResolver.resolve(gateway.connectedWatches(), gateway.watchesWithApp(), s.preferredWatchId, s.sync)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WearApiUnavailable) {
            LinkState.WearApiMissing
        } catch (e: Exception) {
            Log.w(TAG, "link check failed", e)
            LinkState.NoWatch
        }
        _link.value = state
        val reachable = linkedWatch(state) != null
        if (!reachable) caughtUp = false
        observeReachable(reachable)
        return state
    }

    private fun linkedWatch(state: LinkState): WatchNode? = when (state) {
        is LinkState.Linked -> state.watch
        is LinkState.Problem -> state.watch
        else -> null
    }

    /**
     * A full sync: status, settings both ways, history, retention, and a pending delete.
     * @return true when it completed.
     */
    suspend fun sync(): Boolean = mutex.withLock {
        _syncing.value = true
        try {
            val watch = linkedWatch(refreshLink()) ?: return@withLock false
            val attemptAt = nowMs()
            try {
                if (store.read().deletePending) deleteOnWatch(watch.id)
                // Status: exactly what the watch computed, with its own time.
                val statusJson = gateway.request(watch.id, WearContract.RPC_STATUS, ByteArray(0)).toString(Charsets.UTF_8)
                val status = WatchStatus.fromJson(statusJson) ?: throw IncompatibleWatch("status")
                store.setStatus(status)
                // Settings: take the watch's document, then send ours if it still has changes.
                gateway.latestPayload(watch.id, WearContract.PATH_SETTINGS_WATCH)?.let { onWatchSettings(it) }
                if (pendingSettings().isNotEmpty()) pushSettings()
                // History: no further back than the phone keeps (a 30-day retention must not fetch day 31 to prune it).
                val retention = store.read().retentionDays
                HistorySyncEngine(
                    rpc = { path, body -> gateway.request(watch.id, path, body) },
                    store = db,
                    nowMs = nowMs,
                    windowDays = { retention },
                ).run()
                db.prune(retention, nowMs())
                store.syncSucceeded(nowMs())
                true
            } catch (e: TimeoutCancellationException) {
                // A watch that did not answer in time is a sync problem to show, not a cancelled coroutine to rethrow
                // (a timeout IS a CancellationException: rethrown, nothing would be recorded and the screen would
                // keep saying "linked" while every sync failed).
                Log.w(TAG, "sync failed: watch did not answer in time", e)
                store.syncFailed(attemptAt, SyncProblem.UNREACHABLE)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = when (e) {
                    is WearApiUnavailable -> SyncProblem.NO_WEAR_API
                    is IncompatibleWatch, is IllegalArgumentException, is IllegalStateException, is ClassCastException,
                    is NullPointerException -> SyncProblem.INCOMPATIBLE
                    else -> SyncProblem.UNREACHABLE
                }
                Log.w(TAG, "sync failed: $problem", e)
                store.syncFailed(attemptAt, problem)
                false
            }
        } finally {
            refreshLink()
            _syncing.value = false
        }
    }

    // ------------------------------------------------------------------ settings

    /** The watch published its settings: the watch wins ties, and we keep its document. */
    suspend fun onWatchSettings(json: String) {
        val watchDoc = SettingsDoc.fromJson(json)
        if (watchDoc.fields.isEmpty()) return
        store.setWatchSettings(watchDoc)
        store.updatePhoneSettings { SettingsMerge.phoneReceives(it, watchDoc, nowMs()).merged }
    }

    suspend fun onStatus(json: String) {
        WatchStatus.fromJson(json)?.let { store.setStatus(it) }
    }

    /** Changes the phone made that the watch has not applied yet. */
    suspend fun pendingSettings(): Set<SettingField> {
        val s = store.read()
        return SettingsMerge.pending(s.phoneSettings, s.watchSettings)
    }

    /** A settings change on the phone: stored at once (so it shows as pending) and published. */
    suspend fun changeSetting(field: SettingField, value: String) {
        store.updatePhoneSettings { it.with(field, value, nowMs()) }
        pushSettings()
    }

    /** Publishes the phone's document; the Data Layer delivers it when the watch is reachable. */
    suspend fun pushSettings(): Boolean = try {
        gateway.publishSettings(store.read().phoneSettings.toJson())
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not publish settings", e)
        false
    }

    // ------------------------------------------------------------------ the watch

    suspend fun chooseWatch(id: String) {
        store.setPreferredWatch(id)
        refreshLink()
    }

    /** Opens [uri] (open / calibration / store page) on the linked watch. */
    suspend fun openOnWatch(uri: String, nodeId: String? = null): Boolean {
        val id = nodeId ?: linkedWatch(_link.value)?.id ?: return false
        return try {
            gateway.openOnWatch(id, uri)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not open $uri on the watch", e)
            false
        }
    }

    // ------------------------------------------------------------------ delete all

    enum class DeleteResult { DELETED_EVERYWHERE, PHONE_ONLY_WATCH_PENDING }

    /**
     * Clears the phone now, and asks the watch to clear its history too. When the watch cannot be
     * reached the request is kept and sent on the next successful connection.
     */
    suspend fun deleteAll(): DeleteResult = mutex.withLock {
        db.clearAll()
        store.clearData()
        store.setDeletePending(true)
        val watch = linkedWatch(refreshLink())
        val done = watch != null && runCatching { deleteOnWatch(watch.id) }.isSuccess
        if (done) DeleteResult.DELETED_EVERYWHERE else DeleteResult.PHONE_ONLY_WATCH_PENDING
    }

    private suspend fun deleteOnWatch(nodeId: String) {
        gateway.request(nodeId, WearContract.RPC_DELETE, ByteArray(0))
        store.setDeletePending(false)
        // Anything that arrived between the phone's clear and the watch's is gone as well.
        db.clearAll()
    }

    private class IncompatibleWatch(what: String) : Exception("unreadable $what from the watch")

    private companion object {
        const val TAG = "VoxaraPhoneSync"
    }
}
