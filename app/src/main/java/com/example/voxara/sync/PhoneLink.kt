package com.example.voxara.sync

import android.content.Context
import android.util.Log
import com.example.voxara.data.ExposureRepository
import com.example.voxara.link.LinkHealth
import com.example.voxara.link.LinkMonitor
import com.example.voxara.link.Outbox
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * THE WATCH'S VIEW OF THE PHONE LINK — is the Voxara phone app reachable, and what to do when it comes
 * back. It never blocks anything: the watch measures, saves and alerts on its own with or without a phone.
 *
 *  - [refresh] asks the Data Layer who is reachable (CapabilityClient, the phone app's capability) and
 *    feeds [LinkMonitor], so the screens can say connected / reconnecting / disconnected since X;
 *  - on becoming reachable it republishes what the phone may have missed (settings and status items are
 *    idempotent: the same key replaces) and flushes the [Outbox] of things that could not be sent;
 *  - it is called from the capability listener (the app may be closed), the dosimeter service and the
 *    activity, so a reconnect needs no user action.
 */
object PhoneLink {

    private const val TAG = "VoxaraLink"
    private val monitor = LinkMonitor()
    @Volatile private var wasReachable = false

    /** Things that could not be sent (a mirrored limit alert), kept until the phone is back. Persisted by [PhoneOutbox]. */
    private val outbox get() = PhoneOutbox

    /** The reachable phone nodes with the Voxara app, or empty. Never throws. */
    suspend fun phoneNodes(context: Context): List<Node> = try {
        Wearable.getCapabilityClient(context.applicationContext)
            .getCapability(WearContract.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.toList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "phone lookup failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    /** Looks, records the observation and, when the phone just came back, catches it up. Returns the state. */
    suspend fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()): LinkHealth {
        val app = context.applicationContext
        val nodes = phoneNodes(app)
        val reachable = nodes.isNotEmpty()
        val health = monitor.observe(nowMs, reachable)
        publish(health)
        // Decided and recorded together, so two callers racing here (service, listener, activity) catch up once.
        val justBack = synchronized(this) { (reachable && !wasReachable).also { wasReachable = reachable } }
        if (justBack) {
            try {
                catchUp(app, nowMs, nodes.map { it.id })
            } catch (t: Throwable) {
                // Cancelled or failed half way (the service stopped): the next look must run it again.
                synchronized(this) { wasReachable = false }
                throw t
            }
        }
        return health
    }

    /** Re-reads the state with no new lookup (reconnecting turns into disconnected with time). */
    fun tick(nowMs: Long = System.currentTimeMillis()) = publish(monitor.health(nowMs))

    /**
     * A send went through or failed: that is an observation too. A success does NOT mark the phone as already
     * caught up: the next [refresh] still sees "back" and runs the catch-up (settings, status, outbox), once. A
     * failure while the phone still looks reachable clears the mark, so that same catch-up runs again and retries
     * what is queued, instead of waiting for a real drop and return.
     */
    fun observeSend(ok: Boolean, nowMs: Long = System.currentTimeMillis()) {
        if (!ok) synchronized(this) { wasReachable = false }
        publish(monitor.observe(nowMs, ok))
    }

    private fun publish(health: LinkHealth) {
        if (ExposureRepository.state.value.phoneLink != health) {
            ExposureRepository.update { it.copy(phoneLink = health) }
        }
    }

    /** The phone is reachable again: settings and status are re-sent (same item = no duplicate), then the outbox. */
    private suspend fun catchUp(app: Context, nowMs: Long, phoneNodeIds: List<String>) {
        // What the phone changed while this app could not hear it (the Data Layer will not say it again).
        CompanionLink.pullPhoneSettings(app, phoneNodeIds)
        runCatching {
            CompanionLink.publishSettings(app, com.example.voxara.data.VoxaraStore(app).read(), force = true)
            CompanionLink.publishStatus(app, CompanionLink.status(app))
        }.onFailure { Log.w(TAG, "catch-up publish failed", it) }
        runCatching { outbox.flush(app, nowMs) }.onFailure { Log.w(TAG, "outbox flush failed", it) }
    }
}

/**
 * The watch's pending queue: today only the optional mirrored 100% alert (a courtesy copy for the phone;
 * the watch has already alerted). Identified by kind and level, so a resend never duplicates, and a copy older
 * than ten minutes is dropped (the phone refuses stale ones anyway).
 */
object PhoneOutbox {
    private const val FILE = "voxara_outbox"
    private const val KEY = "items"
    private val lock = Any()
    /** One flush at a time: two (an alert, a reconnect) would send the same item twice and one could save a stale queue. */
    private val flushing = Mutex()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun load(c: Context) = Outbox.decode(prefs(c).getString(KEY, null), maxItems = 20, maxAgeMs = MirroredAlert.MAX_AGE_MS)
    private fun save(c: Context, box: Outbox) = prefs(c).edit().putString(KEY, box.encode()).apply()

    fun enqueueMirror(context: Context, alert: MirroredAlert, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        val box = load(context)
        box.enqueue(alert.notificationKey + ":" + alert.atMs, String(alert.encode(), Charsets.UTF_8), nowMs)
        save(context, box)
    }

    suspend fun flush(context: Context, nowMs: Long = System.currentTimeMillis()) = flushing.withLock {
        val box = synchronized(lock) { load(context) }
        if (box.size == 0) return@withLock
        val before = box.pending().map { it.key }.toSet()
        val nodes = PhoneLink.phoneNodes(context)
        box.flush(nowMs) { item ->
            if (nodes.isEmpty()) return@flush false
            nodes.all { node ->
                runCatching {
                    Wearable.getMessageClient(context).sendMessage(node.id, WearContract.MSG_LIMIT_ALERT, item.payload.toByteArray(Charsets.UTF_8)).await()
                }.isSuccess
            }
        }
        synchronized(lock) {
            // What is queued NOW is the truth (an alert added while this flush ran survives it); only what this
            // flush sent is taken off it.
            val current = load(context)
            val unsent = box.pending().map { it.key }.toSet()
            for (key in before - unsent) current.remove(key)
            save(context, current)
        }
    }

    fun size(context: Context): Int = synchronized(lock) { load(context).size }
}
