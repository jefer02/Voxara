package com.example.voxara.sync

import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertLog
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.MinuteStore

/**
 * HISTORY SYNC — the phone pulls, the watch answers. Incremental and idempotent:
 *
 *  1. The phone sends a digest of every (UTC day, ledger) it holds in the sync window.
 *  2. The watch compares with its own and returns only the days that differ (with their part
 *     count). An unchanged day costs nothing.
 *  3. The phone fetches each stale day part by part and REPLACES that day in one transaction, so a
 *     retry, a repeat or an interrupted sync can never count a minute twice.
 *  4. Alerts in the window are fetched in pages and replaced the same way (acknowledgements
 *     change after the fact, so they are not append-only).
 *
 * The watch's ledger is the single source of truth: phone listening spans reach the phone through
 * this sync too, after the watch has applied the no-double-counting rule.
 */

/** One request/response exchange with a connected watch (MessageClient.sendRequest in the app). */
fun interface WatchRpc {
    /** Throws when the watch is unreachable or does not answer. */
    suspend fun call(path: String, body: ByteArray): ByteArray
}

/** The phone's copy of the history. */
interface PhoneHistoryStore {
    /** Digest of every (day, ledger) the phone holds between the two UTC days (inclusive). */
    fun digests(fromDay: Long, toDay: Long): Map<Pair<Long, ExposureKind>, String>
    /** Replaces everything the phone has for that day and ledger (atomically). */
    fun replaceDay(day: Long, kind: ExposureKind, records: List<MinuteRecord>)
    /** Replaces every alert at or after [fromMs] with [alerts] (atomically). */
    fun replaceAlerts(fromMs: Long, alerts: List<AlertRecord>)
}

internal object SyncJson {
    fun key(day: Long, kind: ExposureKind) = "$day:${kind.code}"

    fun parseKey(k: String): Pair<Long, ExposureKind>? {
        val (d, c) = k.split(':').takeIf { it.size == 2 } ?: return null
        val kind = runCatching { ExposureKind.of(c.toInt()) }.getOrNull() ?: return null
        return (d.toLongOrNull() ?: return null) to kind
    }

    fun obj(bytes: ByteArray): Map<*, *> =
        MiniJson.parse(bytes.toString(Charsets.UTF_8)) as? Map<*, *> ?: error("not a JSON object")

    fun bytes(value: Any?): ByteArray = MiniJson.write(value).toByteArray(Charsets.UTF_8)

    fun alertToMap(a: AlertRecord) = linkedMapOf(
        "id" to a.id, "at" to a.atMs, "kind" to a.kind.name, "value" to a.value,
        "level" to a.level, "ack" to a.acknowledged, "snoozed" to a.snoozed,
    )

    fun alertFromMap(m: Map<*, *>): AlertRecord? = runCatching {
        AlertRecord(
            id = (m["id"] as Number).toLong(),
            atMs = (m["at"] as Number).toLong(),
            kind = AlertKind.valueOf(m["kind"] as String),
            value = (m["value"] as Number).toDouble(),
            level = (m["level"] as Number).toInt(),
            acknowledged = m["ack"] as Boolean,
            snoozed = m["snoozed"] as? Boolean ?: false,
        )
    }.getOrNull()
}

/** The UTC days the sync compares, oldest first, ending with today's. */
fun syncWindow(nowMs: Long): LongRange {
    val today = LedgerCodec.utcDay(Math.floorDiv(nowMs, 60_000L))
    return (today - WearContract.SYNC_WINDOW_DAYS + 1)..today
}

/**
 * WATCH SIDE: answers the phone's requests from the watch's own minute ledger and alert log.
 * Pure, so the whole exchange is unit-tested against [HistorySyncEngine] with no Data Layer.
 */
class WatchSyncResponder(
    private val minutes: MinuteStore,
    private val alerts: AlertLog,
    private val nowMs: () -> Long,
    private val status: () -> WatchStatus?,
    private val deleteAll: () -> Unit,
) {
    /** The response bytes for [path], or null when the path is not ours. Throws on a bad request. */
    fun handle(path: String, body: ByteArray): ByteArray? = when (path) {
        WearContract.RPC_STATUS -> status()?.toJson()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        WearContract.RPC_DIGEST -> digest(body)
        WearContract.RPC_DAY -> day(body)
        WearContract.RPC_ALERTS -> alertsPage(body)
        WearContract.RPC_DELETE -> { deleteAll(); SyncJson.bytes(mapOf("ok" to true)) }
        else -> null
    }

    private fun recordsOf(day: Long, kind: ExposureKind): List<MinuteRecord> =
        minutes.range(LedgerCodec.dayStartMinute(day), LedgerCodec.dayStartMinute(day + 1), kind)

    private fun digest(body: ByteArray): ByteArray {
        val theirs = (SyncJson.obj(body)["digests"] as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> SyncJson.parseKey(k.toString())?.let { it to v.toString() } }.toMap()
        val stale = ArrayList<Map<String, Any>>()
        for (day in syncWindow(nowMs())) for (kind in ExposureKind.entries) {
            val mine = recordsOf(day, kind)
            val phone = theirs[day to kind] ?: LedgerCodec.EMPTY_DIGEST
            if (LedgerCodec.digest(mine) != phone) {
                stale += linkedMapOf("day" to day, "kind" to kind.code, "parts" to LedgerCodec.parts(mine.size))
            }
        }
        return SyncJson.bytes(linkedMapOf("stale" to stale))
    }

    private fun day(body: ByteArray): ByteArray {
        val req = SyncJson.obj(body)
        val day = (req["day"] as Number).toLong()
        val kind = ExposureKind.of((req["kind"] as Number).toInt())
        val part = (req["part"] as Number).toInt()
        val batches = LedgerCodec.split(day, kind, recordsOf(day, kind))
        // The day may have shrunk since the digest: an out-of-range part is simply empty.
        val batch = batches.getOrNull(part) ?: DayBatch(day, kind, part, batches.size, emptyList())
        return LedgerCodec.encode(batch)
    }

    private fun alertsPage(body: ByteArray): ByteArray {
        val req = SyncJson.obj(body)
        val from = (req["from"] as Number).toLong()
        val after = (req["after"] as Number).toLong()
        val limit = (req["limit"] as Number).toInt().coerceIn(1, ALERT_PAGE)
        val page = alerts.page(from, after, limit + 1)
        return SyncJson.bytes(
            linkedMapOf(
                "alerts" to page.take(limit).map(SyncJson::alertToMap),
                "more" to (page.size > limit),
            )
        )
    }

    companion object {
        /** ~100 bytes of JSON per alert: a page stays far below the message limit. */
        const val ALERT_PAGE = 300
    }
}

data class SyncReport(val daysChecked: Int, val daysUpdated: Int, val alerts: Int, val atMs: Long)

/**
 * PHONE SIDE: brings the phone's copy of the history in line with the watch's.
 * Any exception (watch gone mid-sync, malformed payload) propagates; what was already replaced
 * stays valid, and the next run fetches whatever is still stale.
 */
class HistorySyncEngine(
    private val rpc: WatchRpc,
    private val store: PhoneHistoryStore,
    private val nowMs: () -> Long,
) {
    suspend fun run(): SyncReport {
        val window = syncWindow(nowMs())
        val mine = store.digests(window.first, window.last)
        val digestReq = linkedMapOf(
            "v" to WearContract.PROTOCOL,
            "digests" to window.flatMap { day ->
                ExposureKind.entries.map { kind -> SyncJson.key(day, kind) to (mine[day to kind] ?: LedgerCodec.EMPTY_DIGEST) }
            }.toMap(),
        )
        val stale = (SyncJson.obj(rpc.call(WearContract.RPC_DIGEST, SyncJson.bytes(digestReq)))["stale"] as? List<*>).orEmpty()

        var updated = 0
        for (entry in stale) {
            val m = entry as? Map<*, *> ?: continue
            val day = (m["day"] as Number).toLong()
            val kind = ExposureKind.of((m["kind"] as Number).toInt())
            var parts = (m["parts"] as Number).toInt().coerceAtLeast(1)
            val records = ArrayList<MinuteRecord>()
            var part = 0
            while (part < parts) {
                val body = SyncJson.bytes(linkedMapOf("day" to day, "kind" to kind.code, "part" to part))
                val batch = LedgerCodec.decode(rpc.call(WearContract.RPC_DAY, body))
                require(batch.day == day && batch.kind == kind) { "answer for the wrong day" }
                records += batch.records
                parts = maxOf(1, batch.parts)
                part++
            }
            // Parts are re-cut on every request; a minute on a boundary may arrive twice.
            store.replaceDay(day, kind, records.distinctBy { it.minute to it.origin })
            updated++
        }

        val fromMs = LedgerCodec.dayStartMinute(window.first) * 60_000L
        val alerts = ArrayList<AlertRecord>()
        var after = 0L
        while (true) {
            val body = SyncJson.bytes(linkedMapOf("from" to fromMs, "after" to after, "limit" to WatchSyncResponder.ALERT_PAGE))
            val res = SyncJson.obj(rpc.call(WearContract.RPC_ALERTS, body))
            val page = (res["alerts"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(SyncJson::alertFromMap) }
            alerts += page
            if (res["more"] != true || page.isEmpty()) break
            after = page.last().id
        }
        store.replaceAlerts(fromMs, alerts)
        return SyncReport(window.count() * ExposureKind.entries.size, updated, alerts.size, nowMs())
    }
}
