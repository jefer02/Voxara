package com.example.voxara.core.headphones

import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.MinuteAccumulator
import com.example.voxara.core.ledger.MinuteRecord

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
)

/**
 * The phone's span as headphone minutes (origin = phone), or empty when it is not headphone
 * listening (speaker output, muted, nonsense). The span is capped so one sample never claims
 * more than the sampling interval.
 */
fun phoneSampleToMinutes(s: PhoneListeningSample, usual: HeadphoneCategory?): List<MinuteRecord> {
    val category = categoryOf(s.output, BtClassHint.UNKNOWN, usual) ?: return emptyList()
    val estimate = estimateListening(s.volumeDb, s.index, s.max) ?: return emptyList()
    val seconds = s.seconds.coerceIn(0.0, PhoneSyncContract.MAX_SPAN_SECONDS)
    if (seconds <= 0.0) return emptyList()
    val acc = MinuteAccumulator(ExposureKind.HEADPHONE).apply {
        this.category = category.code
        confidence = Confidence.VERY_LOW.code          // sampled every 15 min: coarser than the watch
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
