package com.example.voxara.core.situation

import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.HapticPrefs
import com.example.voxara.core.alerts.HapticStrength

/**
 * SITUATION MODES — what the wearer is doing (home, street, transport, a concert, work).
 *
 * THE RULE: a mode can only ADD protection, never take it away. It never touches a threshold, a
 * cooldown, quiet hours, a snooze or any input of the alert engine. What a mode may change:
 *  - which card the watch puts first;
 *  - the tone Tono uses (the mode is one more coarse field in the data Tono sees);
 *  - Concert only: the advisory daily cap is lifted and alert haptics play "Strong" (both add).
 *
 * The enums carry no display text: labels are resolved from resources by each app.
 */
enum class SituationMode {
    QUIET,
    STREET,
    TRANSPORT,
    CONCERT,
    WORK;

    companion object {
        /** What "Auto" resolves to until automatic detection is switched on. */
        val DEFAULT = STREET
    }
}

/** The wearer's choice: Auto (the default) or one fixed mode. This is what syncs between devices. */
enum class SituationChoice(val fixed: SituationMode?) {
    AUTO(null),
    QUIET(SituationMode.QUIET),
    STREET(SituationMode.STREET),
    TRANSPORT(SituationMode.TRANSPORT),
    CONCERT(SituationMode.CONCERT),
    WORK(SituationMode.WORK);

    companion object {
        val DEFAULT = AUTO

        fun of(mode: SituationMode): SituationChoice = entries.first { it.fixed == mode }

        /** Null when [raw] is not a choice name. */
        fun parse(raw: String?): SituationChoice? = entries.firstOrNull { it.name == raw?.trim() }
    }
}

/**
 * How sure Auto is of the mode in force. Display only: whatever it says, the mode applied is the one
 * [Situation.resolve] gives (Street while unsure).
 *  - [DETECTED]: detection concluded the mode (Concert included).
 *  - [DETECTING]: the scene model is on, not sure yet, and still inside its first settling window
 *    ([Situation.AUTO_SETTLE_MS]); Street is a fallback, not a finding.
 *  - [UNSURE]: the settling window is over and Auto is still not sure. Said as plain "Auto", like [NO_MODEL]:
 *    "Detecting…" for hours would be a promise it does not keep.
 *  - [NO_MODEL]: this build has no scene model (licence gate), so only Concert can ever be detected.
 *
 * Synced to the phone by name (WatchStatus "auto_status"): add values, never rename them.
 */
enum class AutoStatus { DETECTED, DETECTING, UNSURE, NO_MODEL }

/** The card the watch's Home screen puts first. */
enum class FeaturedCard { NOW, HEADPHONES, TODAY, WEEK }

object Situation {

    /** Concert ends on its own after this long (battery: it captures continuously)... */
    const val CONCERT_CAP_MS = 6L * 60 * 60 * 1000
    /** ...or after this long below [CONCERT_EXIT_BELOW_DBA]. Same values as before modes existed. */
    const val CONCERT_EXIT_QUIET_MS = 20L * 60 * 1000
    const val CONCERT_EXIT_BELOW_DBA = 80.0

    /**
     * How long Auto may say "Detecting…" after the dosimeter starts or the wearer switches to Auto. AutoModeDetector
     * needs minVotes (2) plus consecutive (2) votes before a first detection: about a minute at its fast-vote pace
     * (15 s), a few minutes when the sampling ladder sets a slower pace. Past this window it is just "Auto".
     */
    const val AUTO_SETTLE_MS = 3L * 60_000

    /**
     * The mode the watch stored before situation modes existed ("mode": URBAN / CONCERT / VOICE).
     * Null when nothing was stored (a fresh install keeps the Auto default).
     */
    fun migrateLegacyMode(legacy: String?): SituationChoice? = when (legacy?.trim()) {
        null, "" -> null
        "CONCERT" -> SituationChoice.CONCERT
        // URBAN was the default and VOICE was only ever a label: both are Street.
        else -> SituationChoice.STREET
    }

    /**
     * The mode in force. A fixed choice is that mode; Auto uses [detected] when automatic switching
     * is on (it is not yet: callers pass null) and otherwise the default.
     */
    fun resolve(choice: SituationChoice, detected: SituationMode? = null): SituationMode =
        choice.fixed ?: detected ?: SituationMode.DEFAULT

    /**
     * The mode the wearer actually is in, as far as anyone knows: the fixed choice, or what Auto
     * detected. Null while Auto has only its Street fallback, so advice and Tono do not claim a street.
     */
    fun known(choice: SituationChoice, detected: SituationMode?): SituationMode? = choice.fixed ?: detected

    /** How sure Auto is ([AutoStatus]); null when the choice is fixed. [settling]: inside [AutoSettleWindow]. */
    fun autoStatus(choice: SituationChoice, detected: SituationMode?, sceneModel: Boolean, settling: Boolean): AutoStatus? = when {
        choice != SituationChoice.AUTO -> null
        detected != null -> AutoStatus.DETECTED
        !sceneModel -> AutoStatus.NO_MODEL
        settling -> AutoStatus.DETECTING
        else -> AutoStatus.UNSURE
    }

    /**
     * The alert settings the engine runs with in [mode]. Only Concert differs, and only by lifting
     * the advisory daily cap: the wearer expects interruptions there, so it can only ADD alerts.
     */
    fun alertSettings(mode: SituationMode, base: AlertSettings): AlertSettings =
        if (mode == SituationMode.CONCERT) base.copy(dailyCap = Int.MAX_VALUE) else base

    /** Haptic preferences in [mode]. Concert plays alerts "Strong" (twice); nothing else changes. */
    fun hapticPrefs(mode: SituationMode, base: HapticPrefs): HapticPrefs =
        if (mode == SituationMode.CONCERT) base.copy(strength = HapticStrength.STRONG) else base

    fun featuredCard(mode: SituationMode): FeaturedCard = when (mode) {
        SituationMode.QUIET -> FeaturedCard.WEEK
        SituationMode.STREET -> FeaturedCard.NOW
        // People turn the volume up over transport noise: headphones first.
        SituationMode.TRANSPORT -> FeaturedCard.HEADPHONES
        SituationMode.CONCERT -> FeaturedCard.NOW
        SituationMode.WORK -> FeaturedCard.TODAY
    }

    /** The coarse value Tono sees (one of five words; never a place, never a time). */
    fun wireValue(mode: SituationMode): String = mode.name.lowercase()
}

/**
 * THE FIRST SETTLING WINDOW of Auto: [settling] is true for [windowMs] after Auto starts being in force (the
 * dosimeter started, or the wearer switched to Auto) and false after it. Leaving Auto (a fixed choice, the
 * calibration probe) and coming back starts a new window. Pure and clock-free: times are passed in.
 */
class AutoSettleWindow(private val windowMs: Long = Situation.AUTO_SETTLE_MS) {
    private var startedAtMs: Long? = null

    /** Call with whether Auto is in force now; true while inside the window. */
    fun settling(auto: Boolean, nowMs: Long): Boolean {
        if (!auto) {
            startedAtMs = null
            return false
        }
        val start = startedAtMs ?: nowMs.also { startedAtMs = it }
        return nowMs - start < windowMs
    }
}

/**
 * WHEN AN AUTOMATIC MODE CHANGE MAY VIBRATE. A mode change is information, not an alert, so it
 * is rare and polite:
 *  - at most one every [minGapMs] and [maxPerDay] a local day;
 *  - never in the first [startupQuietMs] after the dosimeter starts (or restarts);
 *  - never within [alertGuardMs] of an alert haptic;
 *  - never during quiet hours, in power-save mode, or when advisory haptics are off.
 * Manual changes do not go through here: the wearer gets a minimal confirmation tap instead.
 * Pure and clock-free (times are passed in), so every rule is unit-tested with a fake clock.
 */
class ModeChangeHapticGate(
    private val minGapMs: Long = 30L * 60_000,
    private val maxPerDay: Int = 6,
    private val startupQuietMs: Long = 5L * 60_000,
    private val alertGuardMs: Long = 60_000L,
) {
    data class Context(
        val nowMs: Long,
        val localDay: Long,
        /** When the dosimeter (re)started, on the same clock as [nowMs]. */
        val serviceStartedAtMs: Long,
        /** Last alert haptic (any tier, impulse included), or null when none yet. */
        val lastAlertHapticAtMs: Long?,
        val quietHours: Boolean,
        val powerSave: Boolean,
        val advisoryHaptics: Boolean,
    )

    private var lastAtMs: Long? = null
    private var day = Long.MIN_VALUE
    private var countToday = 0

    /** True when the mode-change haptic may play now; a true answer counts as played. */
    fun tryPlay(c: Context): Boolean {
        if (c.quietHours || c.powerSave || !c.advisoryHaptics) return false
        if (c.nowMs - c.serviceStartedAtMs < startupQuietMs) return false
        val alertAt = c.lastAlertHapticAtMs
        if (alertAt != null && kotlin.math.abs(c.nowMs - alertAt) < alertGuardMs) return false
        if (c.localDay != day) { day = c.localDay; countToday = 0 }
        if (countToday >= maxPerDay) return false
        val last = lastAtMs
        if (last != null && c.nowMs - last < minGapMs) return false
        lastAtMs = c.nowMs
        countToday++
        return true
    }
}
