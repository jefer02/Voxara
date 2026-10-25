package com.example.voxara.link

/**
 * HONEST CONNECTION STATE between the watch and the phone, for the screens of both: connected,
 * reconnecting (the other device was reachable a moment ago, so it is probably coming back) or
 * disconnected since a time. Pure and clock-free.
 *
 * Feed it every observation of "is the other device reachable" (capability and node callbacks, the
 * result of a sync or a message). It never guesses a state it has not seen: before the first
 * observation it says [Unknown].
 */
sealed interface LinkHealth {
    data object Unknown : LinkHealth
    data class Connected(val sinceMs: Long) : LinkHealth

    /** Lost [sinceMs]; still inside the window in which a Bluetooth link usually comes back by itself. */
    data class Reconnecting(val sinceMs: Long) : LinkHealth

    /** Lost [sinceMs] and not back after [LinkMonitor.RECONNECT_WINDOW_MS]: say how long, do not pretend. */
    data class Disconnected(val sinceMs: Long) : LinkHealth
}

class LinkMonitor(
    private val reconnectWindowMs: Long = RECONNECT_WINDOW_MS,
) {
    private var seen = false
    private var reachable = false
    private var sinceMs = 0L

    /**
     * Records an observation made at [nowMs] and returns the state it gives. Synchronized: the Data Layer
     * listener, the sync, the dosimeter loop and the UI all report here from different threads.
     */
    @Synchronized
    fun observe(nowMs: Long, isReachable: Boolean): LinkHealth {
        if (!seen || isReachable != reachable) {
            seen = true
            reachable = isReachable
            sinceMs = nowMs
        }
        return health(nowMs)
    }

    /** The state at [nowMs] with no new observation (so "reconnecting" turns into "disconnected" with time). */
    @Synchronized
    fun health(nowMs: Long): LinkHealth = when {
        !seen -> LinkHealth.Unknown
        reachable -> LinkHealth.Connected(sinceMs)
        nowMs - sinceMs < reconnectWindowMs -> LinkHealth.Reconnecting(sinceMs)
        else -> LinkHealth.Disconnected(sinceMs)
    }

    companion object {
        const val RECONNECT_WINDOW_MS = 60_000L
    }
}

/**
 * Things that could not be sent, kept until they can be, WITHOUT DUPLICATES: an item is identified by
 * its key, so enqueuing the same thing twice (a retry, a re-delivery) leaves one copy, and a successful
 * send removes it. Failed items stay, oldest first, up to [maxItems] (the oldest are dropped past that)
 * and [maxAgeMs] (a stale item is no longer worth sending). Payloads are opaque strings (JSON).
 *
 * Persistence is the caller's: [encode] / [decode] round-trip the queue as text.
 */
class Outbox(
    private val maxItems: Int = 200,
    private val maxAgeMs: Long = 24L * 60 * 60_000,
) {
    data class Item(val key: String, val payload: String, val createdMs: Long, val attempts: Int = 0)

    private val items = LinkedHashMap<String, Item>()

    val size: Int get() = items.size
    /** Items still worth sending at [nowMs]: [size] also counts those a later [enqueue] or [flush] is about to drop for age. */
    fun liveSize(nowMs: Long): Int = items.values.count { nowMs - it.createdMs <= maxAgeMs }
    fun pending(): List<Item> = items.values.sortedBy { it.createdMs }
    fun contains(key: String) = items.containsKey(key)
    fun remove(key: String) { items.remove(key) }

    /** Adds [payload] under [key]; the same key replaces the earlier copy (latest wins), never adds a second. */
    fun enqueue(key: String, payload: String, nowMs: Long) {
        val previous = items[key]
        items[key] = Item(key, payload, previous?.createdMs ?: nowMs, previous?.attempts ?: 0)
        trim(nowMs)
    }

    /**
     * Sends everything pending, oldest first. [send] returns true when the item went out (it is then
     * removed); false or an exception keeps it for the next flush. Returns how many were sent.
     */
    suspend fun flush(nowMs: Long, send: suspend (Item) -> Boolean): Int {
        trim(nowMs)
        var sent = 0
        for (item in pending()) {
            val ok = try { send(item) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { false }
            if (ok) { items.remove(item.key); sent++ }
            else items[item.key] = item.copy(attempts = item.attempts + 1)
        }
        return sent
    }

    private fun trim(nowMs: Long) {
        items.values.removeAll { nowMs - it.createdMs > maxAgeMs }
        while (items.size > maxItems) items.remove(pending().first().key)
    }

    /** One line per item: `createdMs TAB attempts TAB key TAB payload` (tabs and newlines in them are escaped). */
    fun encode(): String = pending().joinToString("\n") { i ->
        listOf(i.createdMs.toString(), i.attempts.toString(), esc(i.key), esc(i.payload)).joinToString("\t")
    }

    companion object {
        fun decode(text: String?, maxItems: Int = 200, maxAgeMs: Long = 24L * 60 * 60_000): Outbox {
            val box = Outbox(maxItems, maxAgeMs)
            text.orEmpty().lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val p = line.split('\t')
                if (p.size == 4) {
                    val created = p[0].toLongOrNull() ?: return@forEach
                    val attempts = p[1].toIntOrNull() ?: 0
                    box.items[unesc(p[2])] = Item(unesc(p[2]), unesc(p[3]), created, attempts)
                }
            }
            return box
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        private fun unesc(s: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); else -> sb.append(s[i + 1]) }
                    i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}

/**
 * Keys of things already applied, so a delivery that arrives twice (a Data Layer item re-put after the
 * receiver had already consumed and deleted it, a listener restarted mid-batch) is applied once. Bounded
 * by count and age; [encode] / [decode] round-trip it as text for the caller to persist.
 *
 * The receiver applies a thing only when [firstTime] returns true. It records the key BEFORE applying, so a
 * crash in between loses at most that one thing instead of counting it twice (a headphone span is a few
 * seconds of listening; double counting would be a number the wearer cannot check).
 */
class SeenKeys(
    private val maxItems: Int = 1_000,
    private val maxAgeMs: Long = 7L * 24 * 60 * 60_000,
) {
    private val seen = LinkedHashMap<String, Long>()

    val size: Int get() = seen.size

    /** True the first time [key] is offered (and remembers it), false for every repeat. */
    fun firstTime(key: String, nowMs: Long): Boolean {
        trim(nowMs)
        if (seen.containsKey(key)) return false
        seen[key] = nowMs
        trim(nowMs)
        return true
    }

    fun contains(key: String) = seen.containsKey(key)

    private fun trim(nowMs: Long) {
        seen.values.removeAll { nowMs - it > maxAgeMs }
        while (seen.size > maxItems) seen.remove(seen.keys.first())
    }

    /** One line per key: `seenMs TAB key` (tabs and newlines in a key are replaced by spaces). */
    fun encode(): String = seen.entries.joinToString("\n") { (k, t) -> "$t\t${k.replace('\t', ' ').replace('\n', ' ')}" }

    companion object {
        fun decode(text: String?, maxItems: Int = 1_000, maxAgeMs: Long = 7L * 24 * 60 * 60_000): SeenKeys {
            val keys = SeenKeys(maxItems, maxAgeMs)
            text.orEmpty().lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) line.substring(0, tab).toLongOrNull()?.let { keys.seen[line.substring(tab + 1)] = it }
            }
            return keys
        }
    }
}
