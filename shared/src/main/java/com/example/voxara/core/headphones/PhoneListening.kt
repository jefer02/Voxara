package com.example.voxara.core.headphones

import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteAccumulator
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.secondsAfter

/**
 * PHONE LISTENING — what the phone companion sends through the Wearable Data Layer, and how the
 * watch turns it into headphone-ledger minutes. The phone sends raw aggregates only (no titles,
 * no app names, no device names or addresses); the SAME estimate and attribution rules as on the
 * watch are applied here, so both origins are treated identically.
 */

/** Origin code of records that came from the phone companion (0 = this watch). */
const val ORIGIN_PHONE = 1

/** Data Layer path prefix and keys — shared contract with the :phone module. */
object PhoneSyncContract {
    const val PATH_PREFIX = "/voxara/listening"
    const val KEY_END_MS = "end_ms"
    const val KEY_SECONDS = "seconds"
    const val KEY_VOLUME_DB = "volume_db"
    const val KEY_INDEX = "index"
    const val KEY_MAX = "max"
    const val KEY_OUTPUT = "output"
    /** True when the span stands for a coarse sample (a 15-minute check); absent = true, so older phones keep their confidence. */
    const val KEY_COARSE = "coarse"
    /** Largest span one sample may stand for (the phone samples every 15 min). */
    const val MAX_SPAN_SECONDS = 15 * 60.0
}

/** One span of listening reported by the phone. */
data class PhoneListeningSample(
    val endMs: Long,
    val seconds: Double,
    /** getStreamVolumeDb on the phone, or null/NaN when unavailable. */
    val volumeDb: Double?,
    val index: Int,
    val max: Int,
    val output: OutputKind,
    /**
     * The span was cut from a sample taken now and then (the fallback worker, or an older phone app) rather
     * than at every change of state: it is filed with VERY LOW confidence. Default true.
     */
    val coarse: Boolean = true,
)

/** One span as text (for the phone's outbox): the same numbers the Data Layer item carries, nothing else. */
fun PhoneListeningSample.toJson(): String = com.example.voxara.core.ai.MiniJson.write(
    linkedMapOf(
        "end" to endMs, "sec" to seconds, "db" to volumeDb?.takeIf { it.isFinite() },
        "idx" to index, "max" to max, "out" to output.name, "coarse" to coarse,
    )
)

fun phoneSampleFromJson(text: String): PhoneListeningSample? = runCatching {
    val m = com.example.voxara.core.ai.MiniJson.parse(text) as Map<*, *>
    PhoneListeningSample(
        endMs = (m["end"] as Number).toLong(),
        seconds = (m["sec"] as Number).toDouble(),
        volumeDb = (m["db"] as? Number)?.toDouble(),
        index = (m["idx"] as Number).toInt(),
        max = (m["max"] as Number).toInt(),
        output = OutputKind.valueOf(m["out"] as String),
        coarse = m["coarse"] as? Boolean ?: true,
    )
}.getOrNull()

/**
 * The phone's span as headphone minutes (origin = phone), or empty when it is not headphone
 * listening (speaker output, muted, nonsense). The span is capped so one sample never claims
 * more than the sampling interval.
 */
fun phoneSampleToMinutes(s: PhoneListeningSample, usual: HeadphoneCategory?): List<MinuteRecord> {
    val category = categoryOf(s.output, BtClassHint.UNKNOWN, usual) ?: return emptyList()
    val estimate = estimateListening(s.volumeDb, s.index, s.max) ?: return emptyList()
    if (!s.seconds.isFinite()) return emptyList()      // NaN would poison the minute row and the rolling total for a week
    val seconds = s.seconds.coerceIn(0.0, PhoneSyncContract.MAX_SPAN_SECONDS)
    if (seconds <= 0.0) return emptyList()
    val acc = MinuteAccumulator(ExposureKind.HEADPHONE).apply {
        this.category = category.code
        // Cut at every change of state: the same confidence as on the watch. A coarse sample is VERY LOW whatever the curve.
        confidence = (if (s.coarse) Confidence.VERY_LOW else estimate.confidence).code
        origin = ORIGIN_PHONE
    }
    return acc.add(estimate.dba, seconds, s.endMs) + listOfNotNull(acc.flush())
}

/** What to do with one phone minute given the ambient minute the watch recorded for it. */
data class PhoneMinuteDecision(val keepHeadphone: Boolean, val dropAmbient: Boolean)

/**
 * The double-count rule applied after the fact: the minute belongs to the louder source at the
 * ear. If the headphones win, the ambient minute is removed; if the room wins, the phone minute
 * is dropped. Never both.
 */
fun resolvePhoneMinute(phone: MinuteRecord, ambient: MinuteRecord?): PhoneMinuteDecision {
    val hp = phone.laeq ?: return PhoneMinuteDecision(keepHeadphone = false, dropAmbient = false)
    val room = ambient?.laeq
    return when (attribute(hp, room, HeadphoneCategory.of(phone.category))) {
        Attribution.HEADPHONE -> PhoneMinuteDecision(keepHeadphone = true, dropAmbient = ambient != null)
        Attribution.AMBIENT -> PhoneMinuteDecision(keepHeadphone = false, dropAmbient = false)
    }
}

/**
 * A span as the watch may trust it: a span ending in the future (the phone's clock is ahead) is
 * credited up to now, never to minutes that have not happened; one older than the ledger keeps is
 * dropped (it would be written and pruned at once). Null when there is nothing to credit.
 */
fun sanitizePhoneSample(s: PhoneListeningSample, nowMs: Long, retentionMs: Long = 35L * 24 * 3_600_000): PhoneListeningSample? {
    if (!s.seconds.isFinite() || s.seconds <= 0.0) return null
    if (s.endMs < nowMs - retentionMs) return null
    return if (s.endMs > nowMs + FUTURE_TOLERANCE_MS) s.copy(endMs = nowMs) else s
}

/**
 * The part of a span that happened after [floorMs] (the moment of the last "delete all"), or null when none did. The
 * phone applies it to every span it queues, so the span that was running while the data was deleted cannot carry
 * the time before the delete to the watch.
 */
fun clipToFloor(s: PhoneListeningSample, floorMs: Long): PhoneListeningSample? {
    val kept = secondsAfter(floorMs, s.endMs, s.seconds)
    return when {
        kept <= 0.0 -> null
        kept == s.seconds -> s
        else -> s.copy(seconds = kept)
    }
}

/** Clock difference between the devices that is simply tolerated (a span "ending" a little ahead of the watch's clock). */
const val FUTURE_TOLERANCE_MS = 2 * 60_000L

/**
 * Adds a phone span's minute to what the watch already holds for that minute from the phone.
 *
 * The phone cuts a span at every play, pause and volume change, so one minute can arrive as several
 * spans. Their seconds, energy and dose ADD (the ledger key is minute + kind + origin, so storing each
 * span as its own row would let the last one erase the others). Whoever calls this must apply each
 * span once ([com.example.voxara.link.SeenKeys]); the sum is not idempotent by itself.
 *
 * A minute never holds more than 60 measured seconds: overlapping spans are scaled down together.
 * The category of the louder part wins and the confidence is the weaker of the two.
 */
fun mergePhoneMinute(existing: MinuteRecord?, incoming: MinuteRecord): MinuteRecord {
    if (existing == null) return incoming
    val seconds = existing.measuredSeconds + incoming.measuredSeconds
    val scale = if (seconds > 60.0) 60.0 / seconds else 1.0
    val louder = if ((incoming.laeq ?: Double.NEGATIVE_INFINITY) >= (existing.laeq ?: Double.NEGATIVE_INFINITY)) incoming else existing
    return MinuteRecord(
        minute = incoming.minute,
        kind = incoming.kind,
        measuredSeconds = seconds * scale,
        energyPa2s = (existing.energyPa2s + incoming.energyPa2s) * scale,
        nioshDosePercent = (existing.nioshDosePercent + incoming.nioshDosePercent) * scale,
        category = louder.category,
        confidence = maxOf(existing.confidence, incoming.confidence),
        origin = incoming.origin,
    )
}

/**
 * THE WATCH TAKING ONE PHONE SPAN into its ledgers: sanitised, split into minutes, each minute added to
 * what the phone already reported for it, and the room-versus-headphones rule applied on the total.
 */
fun applyPhoneSample(
    store: com.example.voxara.core.ledger.MinuteStore,
    sample: PhoneListeningSample,
    usual: HeadphoneCategory?,
    nowMs: Long,
): PhoneSpanResult {
    val clean = sanitizePhoneSample(sample, nowMs) ?: return PhoneSpanResult(heard = false, changed = false)
    var changed = false
    val minutes = phoneSampleToMinutes(clean, usual)
    for (minute in minutes) {
        val previous = store.range(minute.minute, minute.minute + 1, ExposureKind.HEADPHONE).firstOrNull { it.origin == ORIGIN_PHONE }
        val merged = mergePhoneMinute(previous, minute)
        val ambient = store.range(minute.minute, minute.minute + 1, ExposureKind.AMBIENT).firstOrNull()
        val decision = resolvePhoneMinute(merged, ambient)
        if (decision.dropAmbient) store.clear(minute.minute, minute.minute + 1, ExposureKind.AMBIENT)
        if (decision.keepHeadphone) store.put(merged)
        changed = changed || decision.keepHeadphone || decision.dropAmbient
    }
    return PhoneSpanResult(heard = minutes.isNotEmpty(), changed = changed)
}

/** [heard]: the phone was playing to headphones (whichever ledger the minutes ended up in). [changed]: the watch's ledgers changed. */
data class PhoneSpanResult(val heard: Boolean, val changed: Boolean)

/**
 * The watch's receiving end of the phone's spans: each one is applied ONCE, however many times the Data Layer
 * delivers it, and in whatever order (addition does not care). [key] identifies the span (its item path, which
 * carries the span's end time).
 */
class PhoneSpanReceiver(
    private val store: com.example.voxara.core.ledger.MinuteStore,
    private val seen: com.example.voxara.link.SeenKeys,
) {
    /**
     * @param persistSeen called after the key is recorded and BEFORE the ledger is touched, so a crash in between
     *   costs one span instead of counting it twice.
     * @return null for a repeat, otherwise what the span did.
     */
    fun receive(
        key: String,
        sample: PhoneListeningSample,
        usual: HeadphoneCategory?,
        nowMs: Long,
        persistSeen: () -> Unit = {},
    ): PhoneSpanResult? {
        if (!seen.firstTime(key, nowMs)) return null
        persistSeen()
        return applyPhoneSample(store, sample, usual, nowMs)
    }
}
