package com.example.voxara.core.headphones

import com.example.voxara.core.ledger.P0_SQUARED_PA2
import kotlin.math.pow

/**
 * HEADPHONE SAFETY — pure rules. Nothing here ever sees the microphone: the listening level is
 * ESTIMATED from the output route and the volume setting only. No device names, addresses,
 * media titles or app names exist anywhere in this model.
 */

/** What the wearer listens through. Only the category is ever stored. */
enum class HeadphoneCategory(val code: Int) {
    EARBUDS(1),
    OVER_EAR(2),
    WIRED(3),
    UNKNOWN(0);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Platform output kinds, mapped from AudioDeviceInfo types by the Android layer. */
enum class OutputKind {
    WIRED_HEADPHONES,
    USB_HEADSET,
    BLUETOOTH_A2DP,
    BLE_HEADSET,
    BLE_BROADCAST,
    /** Speakers, car kits, hearing aids, HDMI, the watch speaker: never headphone listening. */
    OTHER,
}

/**
 * Bluetooth device class as far as it matters: a speaker or car kit is A2DP too, but it is not
 * headphone listening. Read only with BLUETOOTH_CONNECT; UNKNOWN without it.
 */
enum class BtClassHint { HEADPHONES_OR_HEADSET, SPEAKER_OR_CAR, UNKNOWN }

/**
 * Category of a connected output, or null when it is not headphones at all.
 * Earbuds vs over-ear is not exposed by any public API, so a Bluetooth device takes the
 * wearer's own setting ("my headphones are usually…"), or UNKNOWN.
 */
fun categoryOf(kind: OutputKind, btHint: BtClassHint, usual: HeadphoneCategory?): HeadphoneCategory? =
    when (kind) {
        OutputKind.WIRED_HEADPHONES, OutputKind.USB_HEADSET -> HeadphoneCategory.WIRED
        OutputKind.BLUETOOTH_A2DP, OutputKind.BLE_HEADSET, OutputKind.BLE_BROADCAST ->
            if (btHint == BtClassHint.SPEAKER_OR_CAR) null
            else usual?.takeIf { it == HeadphoneCategory.EARBUDS || it == HeadphoneCategory.OVER_EAR }
                ?: HeadphoneCategory.UNKNOWN
        OutputKind.OTHER -> null
    }

/**
 * ASSUMED LISTENING PROFILE — the one place these assumptions live.
 *
 * EN 50332 limits portable players sold in the EU to about 100 dBA at maximum volume with a
 * music-like test signal, so full volume is taken as ~100 dBA and the platform's own
 * volume curve (getStreamVolumeDb) supplies the attenuation. Real headphones and real music vary
 * by +-10 dB or more: every number built on this is labelled "estimated" with LOW confidence.
 */
object HeadphoneProfile {
    const val MAX_OUTPUT_DBA = 100.0
    /** Used only when the platform cannot convert a volume index to dB. */
    const val FALLBACK_RANGE_DB = 60.0
    const val UNCERTAINTY_DB = 10.0

    /**
     * The recommended listening level: 80 dBA for 40 h a week is exactly the WHO-ITU H.870 weekly
     * reference of 1.6 Pa²h (4e-10 Pa² · 10^8 · 40 h). Above it a headphone alert fires and "Lower to
     * safe volume" aims at it. Not a measurement: the level itself is an estimate (see above).
     */
    const val SAFE_DBA = 80.0

    /** Escalation steps of the "above the recommended level" alert (estimated dBA, inclusive). */
    const val TIER2_DBA = 90.0
    const val TIER3_DBA = 100.0
}

/**
 * Where the estimate sits against the recommended level: 0 = at or below [HeadphoneProfile.SAFE_DBA],
 * 1 = above it, 2 = from [HeadphoneProfile.TIER2_DBA], 3 = from [HeadphoneProfile.TIER3_DBA].
 * A higher tier is a reason to alert again whatever the cooldown says.
 */
fun headphoneTierOf(dba: Double): Int = when {
    dba.isNaN() || dba <= HeadphoneProfile.SAFE_DBA -> 0
    dba >= HeadphoneProfile.TIER3_DBA -> 3
    dba >= HeadphoneProfile.TIER2_DBA -> 2
    else -> 1
}

/** One audio output as the platform reports it, before any decision (the Android layer fills it in). */
data class RouteCandidate(
    val kind: OutputKind,
    val btHint: BtClassHint,
    /** `AudioDeviceInfo.getType()`; only used to ask the platform for this output's volume curve. */
    val androidType: Int,
    /** Which physical output this is, for "Not headphones" ([OutputIgnoreList]); null when unknown. */
    val id: OutputId? = null,
)

/**
 * The headphone route in force, decided from the connected outputs.
 *
 * @property possibleSpeaker a Bluetooth output whose class could not be read (no BLUETOOTH_CONNECT, or
 * the device does not say): counted as headphones on purpose (warning too much beats warning too
 * little), but the UI says "possible speaker" so the wearer can tell why
 */
data class HeadphoneRoute(
    val category: HeadphoneCategory,
    val kind: OutputKind,
    val androidType: Int,
    val possibleSpeaker: Boolean,
    /** The output itself, so "Not headphones" can leave exactly this one out. */
    val id: OutputId? = null,
)

/** A Bluetooth output whose class we could not read might be a speaker or a car kit. */
fun possibleSpeaker(kind: OutputKind, btHint: BtClassHint): Boolean =
    btHint == BtClassHint.UNKNOWN &&
        (kind == OutputKind.BLUETOOTH_A2DP || kind == OutputKind.BLE_BROADCAST)

/**
 * The route media would take: wired and USB first (the platform prefers them), then a BLE headset,
 * then A2DP. Outputs that are not headphones (speaker, car kit, hearing aid) never win; null when
 * there is none. [usual] is the wearer's "my headphones are usually…" setting. An output the wearer said is not
 * headphones ([ignored], see [OutputIgnoreList]) never wins either.
 */
fun chooseRoute(
    candidates: List<RouteCandidate>,
    usual: HeadphoneCategory?,
    ignored: (RouteCandidate) -> Boolean = { false },
): HeadphoneRoute? {
    fun rank(k: OutputKind) = when (k) {
        OutputKind.WIRED_HEADPHONES, OutputKind.USB_HEADSET -> 0
        OutputKind.BLE_HEADSET -> 1
        OutputKind.BLUETOOTH_A2DP -> 2
        OutputKind.BLE_BROADCAST -> 3
        OutputKind.OTHER -> 9
    }
    return candidates
        .filterNot(ignored)
        .mapNotNull { c -> categoryOf(c.kind, c.btHint, usual)?.let { c to it } }
        .minByOrNull { rank(it.first.kind) }
        ?.let { (c, cat) -> HeadphoneRoute(cat, c.kind, c.androidType, possibleSpeaker(c.kind, c.btHint), c.id) }
}

enum class Confidence(val code: Int) {
    /** Volume curve from the platform, output level assumed. */
    LOW(1),
    /** Volume index mapped linearly: even rougher. */
    VERY_LOW(2),
}

data class ListeningEstimate(val dba: Double, val confidence: Confidence) {
    val lowDba get() = dba - HeadphoneProfile.UNCERTAINTY_DB
    val highDba get() = dba + HeadphoneProfile.UNCERTAINTY_DB
}

/**
 * Estimated listening level.
 *
 * @param volumeDb the platform's attenuation for the current volume (<= 0), or null when unknown
 * @param index current volume index, max its maximum — the fallback when [volumeDb] is null
 */
fun estimateListening(volumeDb: Double?, index: Int, max: Int): ListeningEstimate? {
    if (index <= 0 || max <= 0) return null                     // muted: nothing reaches the ear
    return if (volumeDb != null && !volumeDb.isNaN() && volumeDb <= 0.0) {
        ListeningEstimate(HeadphoneProfile.MAX_OUTPUT_DBA + volumeDb, Confidence.LOW)
    } else {
        val fraction = index.toDouble() / max
        ListeningEstimate(
            HeadphoneProfile.MAX_OUTPUT_DBA - HeadphoneProfile.FALLBACK_RANGE_DB * (1 - fraction),
            Confidence.VERY_LOW,
        )
    }
}

/**
 * Passive attenuation of room noise by the headphones, used ONLY to decide which ledger a minute
 * belongs to. Deliberately conservative (small), so loud surroundings are not waved away.
 */
fun passiveAttenuationDb(c: HeadphoneCategory): Double = when (c) {
    HeadphoneCategory.EARBUDS -> 10.0
    HeadphoneCategory.OVER_EAR -> 15.0
    HeadphoneCategory.WIRED, HeadphoneCategory.UNKNOWN -> 0.0
}

enum class Attribution { AMBIENT, HEADPHONE }

/**
 * THE DOUBLE-COUNT RULE. While headphone playback is active, each span belongs to exactly ONE
 * ledger: whichever is louder at the ear — the headphone estimate, or the room minus the
 * headphones' passive attenuation. The other ledger gets nothing for that span.
 *
 * @param headphoneDba estimate while playing to headphones, null when not listening
 * @param ambientDba the measured room level, null when the mic could not measure
 */
fun attribute(headphoneDba: Double?, ambientDba: Double?, category: HeadphoneCategory): Attribution = when {
    headphoneDba == null -> Attribution.AMBIENT
    ambientDba == null -> Attribution.HEADPHONE
    headphoneDba >= ambientDba - passiveAttenuationDb(category) -> Attribution.HEADPHONE
    else -> Attribution.AMBIENT
}

/**
 * "About 2 h left this week at this volume": remaining weekly energy divided by the energy rate
 * at the current level. Null when the level is not known.
 */
fun weeklyTimeLeftHours(usedPa2h: Double, budgetPa2h: Double, dba: Double?): Double? {
    if (dba == null) return null
    val rate = P0_SQUARED_PA2 * 10.0.pow(dba / 10.0)            // Pa^2 per hour of listening
    return ((budgetPa2h - usedPa2h) / rate).coerceAtLeast(0.0)
}

/**
 * Connection debouncing: Bluetooth links flap while pairing or switching. A change only counts
 * once it has held for [holdMs].
 */
class Debouncer<T>(initial: T, private val holdMs: Long = 3_000L) {
    var stable: T = initial
        private set
    private var pending: T = initial
    private var pendingSince = 0L

    /** Offers the raw value observed at [nowMs]; returns the debounced value. */
    fun offer(value: T, nowMs: Long): T {
        if (value == stable) {
            pending = value
            return stable
        }
        if (value != pending) {
            pending = value
            pendingSince = nowMs
        } else if (nowMs - pendingSince >= holdMs) {
            stable = value
        }
        return stable
    }
}
