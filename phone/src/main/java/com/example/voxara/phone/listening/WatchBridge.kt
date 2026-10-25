package com.example.voxara.phone.listening

import android.content.Context
import android.util.Log
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.core.headphones.PhoneSyncContract
import com.example.voxara.core.headphones.clipToFloor
import com.example.voxara.core.headphones.phoneSampleFromJson
import com.example.voxara.core.headphones.toJson
import com.example.voxara.link.Outbox
import com.example.voxara.phone.PhonePrefs
import com.example.voxara.sync.HeadphoneWire
import com.example.voxara.sync.LowerVolumeResult
import com.example.voxara.sync.PhoneListeningState
import com.example.voxara.sync.WearContract
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * WHAT THE PHONE SENDS THE WATCH ABOUT HEADPHONES. Numbers and an output type only.
 *
 *  - [sendState]: the live state as a message (real time while both are connected) AND as one overwritten
 *    data item (durable, so a watch that was off reads the latest when it is back);
 *  - [enqueueSpan] / [flushSpans]: finished listening spans for the watch's ledger. They wait in a persisted,
 *    keyed [Outbox] until the Data Layer takes them, so a span is sent once however many times a flush is
 *    retried (the item path carries the span's end time: the same span is the same item);
 *  - [reply]: the answer to the watch's "lower to safe volume".
 *
 * Nothing here ever blocks the phone's own alert: if the watch is away, the phone still vibrates and notifies.
 */
object WatchBridge {

    private const val TAG = "VoxaraBridge"
    private val lock = Any()

    /** The reachable watches that have the Voxara app, or empty. Never throws. */
    suspend fun watchNodes(context: Context): List<Node> = try {
        Wearable.getCapabilityClient(context.applicationContext)
            .getCapability(WearContract.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.toList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "watch lookup failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    /** True when at least one watch took the live message. The durable item is written either way. */
    suspend fun sendState(context: Context, state: PhoneListeningState, durable: Boolean = true): Boolean {
        val app = context.applicationContext
        val bytes = state.encode()
        if (durable) {
            runCatching {
                val request = PutDataMapRequest.create(HeadphoneWire.PATH_STATE).apply {
                    dataMap.putByteArray(HeadphoneWire.KEY_PAYLOAD, bytes)
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(app).putDataItem(request).await()
            }.onFailure { if (it is CancellationException) throw it else Log.w(TAG, "state item not written: ${it.javaClass.simpleName}") }
        }
        val nodes = watchNodes(app)
        var any = false
        for (n in nodes) {
            runCatching { Wearable.getMessageClient(app).sendMessage(n.id, HeadphoneWire.PATH_LIVE, bytes).await() }
                .onSuccess { any = true }
                .onFailure { if (it is CancellationException) throw it }
        }
        return any
    }

    suspend fun reply(context: Context, nodeId: String, result: LowerVolumeResult) {
        runCatching {
            Wearable.getMessageClient(context.applicationContext).sendMessage(nodeId, HeadphoneWire.PATH_CMD_LOWERED, result.encode()).await()
        }.onFailure { if (it is CancellationException) throw it else Log.w(TAG, "answer not sent: ${it.javaClass.simpleName}") }
    }

    // ------------------------------------------------------------------ spans (idempotent, persisted)

    private fun load(c: Context) = Outbox.decode(PhonePrefs.outbox(c), maxItems = 500, maxAgeMs = 3L * 24 * 60 * 60_000)

    /** Queues a finished span. The same end time is the same span: queuing it twice leaves one. */
    fun enqueueSpan(context: Context, sample: PhoneListeningSample, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        // The span that was running when the wearer deleted everything keeps only its time after the delete.
        val kept = clipToFloor(sample, PhonePrefs.wipedAtMs(context)) ?: return@synchronized
        val box = load(context)
        box.enqueue("span/${kept.endMs}", kept.toJson(), nowMs)
        PhonePrefs.setOutbox(context, box.encode())
    }

    /** Spans still waiting to go (one older than the queue keeps is about to be dropped, so it is not counted). */
    fun pendingSpans(context: Context, nowMs: Long = System.currentTimeMillis()): Int = synchronized(lock) { load(context).liveSize(nowMs) }

    /** Empties the queue (text form) and returns how many spans it held. */
    fun clearQueued(context: Context): Int = synchronized(lock) {
        val n = load(context).size
        PhonePrefs.setOutbox(context, Outbox().encode())
        n
    }

    /**
     * "DELETE ALL DATA": listening that was still waiting here, or sitting in the Data Layer for a watch that has not
     * taken it yet, would come back on the watch after it was deleted (and then sync back to this phone). Both are dropped.
     * Never throws: deleting the data items is best effort, the queue is emptied first.
     */
    suspend fun discardPending(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        // Fence first: from now on nothing that happened before this instant is queued or sent.
        synchronized(lock) {
            PhonePrefs.setWipedAtMs(app, nowMs)
            clearQueued(app)
        }
        try {
            val local = Wearable.getNodeClient(app).localNode.await().id
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(local).path(PhoneSyncContract.PATH_PREFIX).build()
            Wearable.getDataClient(app).deleteDataItems(uri, DataClient.FILTER_PREFIX).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "queued listening not removed from the Data Layer: ${e.javaClass.simpleName}")
        }
    }

    /** Hands every queued span to the Data Layer; the ones it takes leave the queue. Returns how many went out. */
    suspend fun flushSpans(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val app = context.applicationContext
        val box = synchronized(lock) { load(app) }
        if (box.size == 0) return 0
        val before = box.pending().map { it.key }.toSet()
        val sent = box.flush(nowMs) { item ->
            val read = phoneSampleFromJson(item.payload) ?: return@flush true   // unreadable: nothing to retry
            // Looked at again here: a "delete all" can run while this flush is on its way.
            val s = clipToFloor(read, PhonePrefs.wipedAtMs(app)) ?: return@flush true
            runCatching {
                val request = PutDataMapRequest.create("${PhoneSyncContract.PATH_PREFIX}/${s.endMs}").apply {
                    dataMap.putLong(PhoneSyncContract.KEY_END_MS, s.endMs)
                    dataMap.putDouble(PhoneSyncContract.KEY_SECONDS, s.seconds)
                    dataMap.putDouble(PhoneSyncContract.KEY_VOLUME_DB, s.volumeDb ?: Double.NaN)
                    dataMap.putInt(PhoneSyncContract.KEY_INDEX, s.index)
                    dataMap.putInt(PhoneSyncContract.KEY_MAX, s.max)
                    dataMap.putString(PhoneSyncContract.KEY_OUTPUT, s.output.name)
                    dataMap.putBoolean(PhoneSyncContract.KEY_COARSE, s.coarse)
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(app).putDataItem(request).await()
            }.isSuccess
        }
        synchronized(lock) {
            // What is queued NOW is the truth (items added while this flush ran are kept, and a "delete all" that
            // emptied the queue meanwhile stays empty); only what this flush sent is taken off it.
            val current = load(app)
            val unsent = box.pending().map { it.key }.toSet()
            for (key in before - unsent) current.remove(key)
            PhonePrefs.setOutbox(app, current.encode())
        }
        if (sent > 0) PhonePrefs.setLastSyncMs(app, nowMs)
        return sent
    }
}
