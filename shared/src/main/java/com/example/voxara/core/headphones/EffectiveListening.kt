package com.example.voxara.core.headphones

import com.example.voxara.sync.PhoneListeningState

/**
 * WHOSE HEADPHONES — the watch's own outputs, or the phone's (reported live over the Data Layer). There is
 * one pair of ears: when both devices say something is playing the LOUDER estimate is the one that counts
 * for the live level and its alerts (the cautious choice). The ledgers are not touched here: each device
 * writes its own origin and a minute reported twice counts once ([mergeByMinute]).
 */
data class WatchListening(
    val connected: Boolean,
    val playing: Boolean,
    val dba: Double?,
    val possibleSpeaker: Boolean,
    val category: HeadphoneCategory?,
)

data class EffectiveListening(
    /** [LayerSource.WATCH], [LayerSource.PHONE] or [LayerSource.NONE]. Never MANUAL (there is no manual mode). */
    val source: LayerSource,
    val connected: Boolean,
    val playing: Boolean,
    /** The estimated level to show and alert on; null unless playing. */
    val dba: Double?,
    val possibleSpeaker: Boolean,
) {
    companion object { val NONE = EffectiveListening(LayerSource.NONE, false, false, null, false) }
}

fun effectiveListening(watch: WatchListening, phone: PhoneListeningState?, nowMs: Long): EffectiveListening {
    val phoneLive = phone?.takeIf { it.connected && it.isFresh(nowMs) }
    val phoneDba = phoneLive?.estimate?.dba
    val watchDba = watch.dba.takeIf { watch.connected && watch.playing }
    return when {
        watchDba != null && phoneDba != null ->
            if (phoneDba > watchDba) EffectiveListening(LayerSource.PHONE, true, true, phoneDba, phoneLive!!.possibleSpeaker)
            else EffectiveListening(LayerSource.WATCH, true, true, watchDba, watch.possibleSpeaker)
        watchDba != null -> EffectiveListening(LayerSource.WATCH, true, true, watchDba, watch.possibleSpeaker)
        phoneDba != null -> EffectiveListening(LayerSource.PHONE, true, true, phoneDba, phoneLive!!.possibleSpeaker)
        watch.connected -> EffectiveListening(LayerSource.WATCH, true, false, null, watch.possibleSpeaker)
        phoneLive != null -> EffectiveListening(LayerSource.PHONE, true, false, null, phoneLive.possibleSpeaker)
        else -> EffectiveListening.NONE
    }
}
