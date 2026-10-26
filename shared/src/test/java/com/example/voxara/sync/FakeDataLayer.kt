package com.example.voxara.sync

import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore

/**
 * A FAKE WEARABLE DATA LAYER for JVM tests: the behaviours of the real one that the sync logic has to survive,
 * and nothing else.
 *
 *  - A DataItem is one value per path. Putting the same bytes again is NOT a change and notifies nobody (the real
 *    Data Layer does the same), putting different bytes replaces it.
 *  - While the link is down, changes wait; when it comes back the listener receives them, in an order the test
 *    chooses ([Order]) and optionally more than once ([redeliver]): the real service can be restarted mid-batch.
 *  - A consumer that deletes an item makes room for the same path to be put (and notified) again.
 *  - Messages are lost while the link is down (they are not queued).
 */
class FakeDataLayer {

    enum class Order { AS_SENT, REVERSED, SHUFFLED }

    data class Item(val path: String, val bytes: ByteArray) {
        override fun equals(other: Any?) = other is Item && other.path == path && other.bytes.contentEquals(bytes)
        override fun hashCode() = path.hashCode() * 31 + bytes.contentHashCode()
    }

    var connected = true
        private set

    private val items = LinkedHashMap<String, ByteArray>()
    private val waiting = ArrayList<Item>()
    private var listener: ((Item) -> Unit)? = null

    /** Messages that reached the other side. */
    val messages = ArrayList<Pair<String, ByteArray>>()
    var lostMessages = 0
        private set

    /** The receiving side: called once per delivered change. */
    fun onItem(handler: (Item) -> Unit) { listener = handler }

    /** The sending side. Returns whether it changed anything. */
    fun put(path: String, bytes: ByteArray): Boolean {
        val old = items[path]
        if (old != null && old.contentEquals(bytes)) return false
        items[path] = bytes
        val item = Item(path, bytes)
        if (connected) listener?.invoke(item) else waiting.removeAll { it.path == path }.also { waiting += item }
        return true
    }

    fun get(path: String): ByteArray? = items[path]

    /** The receiving side acknowledges an item by deleting it. */
    fun delete(path: String) { items.remove(path) }

    fun paths(): List<String> = items.keys.toList()

    fun disconnect() { connected = false }

    /**
     * The link is back. Everything that changed meanwhile is delivered, in [order] (with [seed] for SHUFFLED),
     * and the first [redeliver] items are delivered a second time right after (a restarted listener).
     */
    fun reconnect(order: Order = Order.AS_SENT, seed: Long = 1L, redeliver: Int = 0) {
        connected = true
        val batch = when (order) {
            Order.AS_SENT -> waiting.toList()
            Order.REVERSED -> waiting.reversed()
            Order.SHUFFLED -> waiting.shuffled(java.util.Random(seed))
        }
        waiting.clear()
        batch.forEach { listener?.invoke(it) }
        batch.take(redeliver).forEach { listener?.invoke(it) }
    }

    fun message(path: String, bytes: ByteArray): Boolean {
        if (!connected) { lostMessages++; return false }
        messages += path to bytes
        return true
    }
}

/** An in-memory minute ledger with the same key as the databases: (minute, kind, origin), the last put wins. */
class InMemoryMinuteStore : MinuteStore {
    val rows = LinkedHashMap<Triple<Long, ExposureKind, Int>, MinuteRecord>()
    override fun put(r: MinuteRecord) { rows[Triple(r.minute, r.kind, r.origin)] = r }
    override fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind) =
        rows.values.filter { it.kind == kind && it.minute in fromMinute until toMinute }.sortedWith(compareBy({ it.minute }, { it.origin }))
    override fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind) {
        rows.keys.removeAll { it.second == kind && it.first in fromMinute until toMinute }
    }
    override fun prune(nowMinute: Long) = Unit

    fun all(kind: ExposureKind) = range(Long.MIN_VALUE, Long.MAX_VALUE, kind)
}
