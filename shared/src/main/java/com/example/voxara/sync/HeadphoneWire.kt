package com.example.voxara.sync

import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.estimateListening

/**
 * HEADPHONES OVER THE DATA LAYER — what the phone tells the watch the moment something changes, and the
 * one command that goes back ("lower to safe volume"). Numbers and an output TYPE only: no device names,
 * addresses, titles or app names.
 *
 *  - [PATH_LIVE] MessageClient, phone → watch, sent on every change (connected, playing, volume): real
 *    time while both are connected, lost when they are not (that is what the state item is for).
 *  - [PATH_STATE] DataClient, phone → watch, ONE item overwritten on every change: durable, so a watch
 *    that was off or out of range reads the latest state when it comes back (`sequence` and `atMs` say
 *    how old it is; a state older than [FRESH_MS] is shown as "last known", never as live).
 *  - [PATH_CMD_LOWER] MessageClient, watch → phone, with an id; [PATH_CMD_LOWERED] is the phone's answer
 *    with the same id. A command that was not answered is never assumed to have worked.
 *  - [PATH_CMD_NOT_HEADPHONES] MessageClient, watch → phone: the wearer said the phone's output is not headphones
 *    (leave it out until it disconnects, or remember it as a speaker). No answer: the phone's next state says the
 *    headphones are gone, and the watch ignores the phone's output until then whatever happens to the message.
 *
 * The older `/voxara/listening/…` spans (PhoneSyncContract) keep working: an older watch still receives them.
 */
object HeadphoneWire {
    const val PATH_LIVE = "/voxara/hp/live"
    const val PATH_STATE = "/voxara/hp/state"
    const val PATH_CMD_LOWER = "/voxara/hp/cmd/lower"
    const val PATH_CMD_LOWERED = "/voxara/hp/cmd/lowered"
    const val PATH_CMD_NOT_HEADPHONES = "/voxara/hp/cmd/not_headphones"
    const val KEY_PAYLOAD = "payload"

    /** A phone state older than this is "last known": the phone reports on every change, so silence means nothing is playing or the link was down. */
    const val FRESH_MS = 3 * 60_000L

    /** How far apart the two devices' clocks may be before a timestamp from the other one is doubted. */
    const val CLOCK_TOLERANCE_MS = 5 * 60_000L

    /** A level over the recommended one is acted on only after it has held this long (a slider sweep is not an alert). */
    const val OVER_SAFE_DWELL_MS = 8_000L

    /** While headphones are connected the phone repeats its state this often, so "fresh" keeps meaning "still true". */
    const val KEEPALIVE_MS = 60_000L

    /** How long the watch waits for the phone's answer to "lower" before saying it could not. */
    const val LOWER_TIMEOUT_MS = 6_000L
}

/** What the phone's headphones are doing, as the watch needs to know it. */
data class PhoneListeningState(
    val atMs: Long,
    /** Increases with every send from the phone; a receiver drops anything not newer than what it has. */
    val sequence: Long,
    val connected: Boolean,
    val playing: Boolean,
    val output: OutputKind,
    /** Bluetooth output whose class the phone could not read: headphones, but maybe a speaker. */
    val possibleSpeaker: Boolean,
    /** getStreamVolumeDb on the phone for this output, or null when it cannot say. */
    val volumeDb: Double?,
    val volumeIndex: Int,
    val volumeMax: Int,
    /** The platform fixes the volume (cannot be lowered from here). */
    val fixedVolume: Boolean = false,
    /**
     * When the level in force began (the last connect, play, pause or volume change on the phone). A receiver
     * applies the same "has stayed over for a few seconds" wait the phone does, from the sender's clock.
     */
    val levelSinceMs: Long = atMs,
    /**
     * When THIS device received the state (its own clock; 0 = unknown, e.g. read back from the durable item). Not on
     * the wire. Freshness is judged from it when known, so a phone whose clock is wrong cannot make a state that
     * stopped coming look live for longer.
     */
    val receivedAtMs: Long = 0L,
) {
    /** The estimated listening level, only while connected AND playing. */
    val estimate: ListeningEstimate?
        get() = if (connected && playing) estimateListening(volumeDb, volumeIndex, volumeMax) else null

    /**
     * Live, not "last known". From the arrival time when it is known (both times are this device's); otherwise from
     * the phone's stamp, where a clock a few minutes apart must not make every state "from the future".
     */
    fun isFresh(nowMs: Long): Boolean =
        if (receivedAtMs > 0L) nowMs - receivedAtMs in -HeadphoneWire.CLOCK_TOLERANCE_MS..HeadphoneWire.FRESH_MS
        else nowMs - atMs in -HeadphoneWire.CLOCK_TOLERANCE_MS..HeadphoneWire.FRESH_MS

    fun encode(): ByteArray = MiniJson.write(
        linkedMapOf(
            "at" to atMs, "seq" to sequence, "connected" to connected, "playing" to playing,
            "output" to output.name, "maybe_speaker" to possibleSpeaker,
            "volume_db" to volumeDb?.takeIf { it.isFinite() }, "index" to volumeIndex, "max" to volumeMax,
            "fixed" to fixedVolume, "since" to levelSinceMs,
        )
    ).toByteArray(Charsets.UTF_8)

    companion object {
        /** A state that says nothing is connected (the phone's app stopped watching, or the earbuds went). */
        fun none(atMs: Long, sequence: Long) = PhoneListeningState(
            atMs, sequence, connected = false, playing = false, output = OutputKind.OTHER,
            possibleSpeaker = false, volumeDb = null, volumeIndex = 0, volumeMax = 0,
        )

        fun decode(bytes: ByteArray): PhoneListeningState? = runCatching {
            val m = MiniJson.parse(bytes.toString(Charsets.UTF_8)) as Map<*, *>
            PhoneListeningState(
                atMs = (m["at"] as Number).toLong(),
                sequence = (m["seq"] as Number).toLong(),
                connected = m["connected"] as Boolean,
                playing = m["playing"] as Boolean,
                output = runCatching { OutputKind.valueOf(m["output"] as String) }.getOrDefault(OutputKind.OTHER),
                possibleSpeaker = m["maybe_speaker"] as? Boolean ?: false,
                volumeDb = (m["volume_db"] as? Number)?.toDouble(),
                volumeIndex = (m["index"] as Number).toInt(),
                volumeMax = (m["max"] as Number).toInt(),
                fixedVolume = m["fixed"] as? Boolean ?: false,
                levelSinceMs = (m["since"] as? Number)?.toLong() ?: (m["at"] as Number).toLong(),
            )
        }.getOrNull()
    }
}

/** The watch asks the phone to bring its music volume down to [targetDba] (estimated). */
data class LowerVolumeRequest(val id: String, val atMs: Long, val targetDba: Double) {
    fun encode(): ByteArray = MiniJson.write(linkedMapOf("id" to id, "at" to atMs, "target" to targetDba))
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): LowerVolumeRequest? = runCatching {
            val m = MiniJson.parse(bytes.toString(Charsets.UTF_8)) as Map<*, *>
            LowerVolumeRequest(m["id"] as String, (m["at"] as Number).toLong(), (m["target"] as Number).toDouble())
        }.getOrNull()
    }
}

/** The watch tells the phone its current output is not headphones; [speaker]: remember it as a speaker. */
data class NotHeadphonesRequest(val id: String, val atMs: Long, val speaker: Boolean) {
    fun encode(): ByteArray = MiniJson.write(linkedMapOf("id" to id, "at" to atMs, "speaker" to speaker))
        .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): NotHeadphonesRequest? = runCatching {
            val m = MiniJson.parse(bytes.toString(Charsets.UTF_8)) as Map<*, *>
            NotHeadphonesRequest(m["id"] as String, (m["at"] as Number).toLong(), m["speaker"] == true)
        }.getOrNull()
    }
}

/** What happened to a lower-volume command. Anything but [LOWERED] and [ALREADY_SAFE] is told to the wearer. */
enum class LowerOutcome { LOWERED, ALREADY_SAFE, FIXED_VOLUME, NOT_PLAYING, FAILED, UNREACHABLE }

/** The phone's answer to a [LowerVolumeRequest] (same [id]), with what the volume is now. */
data class LowerVolumeResult(
    val id: String,
    val outcome: LowerOutcome,
    val volumeIndex: Int,
    val estimatedDba: Double?,
) {
    fun encode(): ByteArray = MiniJson.write(
        linkedMapOf("id" to id, "outcome" to outcome.name, "index" to volumeIndex, "dba" to estimatedDba?.takeIf { it.isFinite() })
    ).toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): LowerVolumeResult? = runCatching {
            val m = MiniJson.parse(bytes.toString(Charsets.UTF_8)) as Map<*, *>
            LowerVolumeResult(
                m["id"] as String,
                LowerOutcome.valueOf(m["outcome"] as String),
                (m["index"] as Number).toInt(),
                (m["dba"] as? Number)?.toDouble(),
            )
        }.getOrNull()
    }
}
