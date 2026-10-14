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

/** The card the watch's Home screen puts first. */
enum class FeaturedCard { NOW, HEADPHONES, TODAY, WEEK }

object Situation {

    /** Concert ends on its own after this long (battery: it captures continuously)... */
    const val CONCERT_CAP_MS = 6L * 60 * 60 * 1000
    /** ...or after this long below [CONCERT_EXIT_BELOW_DBA]. Same values as before modes existed. */
    const val CONCERT_EXIT_QUIET_MS = 20L * 60 * 1000
    const val CONCERT_EXIT_BELOW_DBA = 80.0

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
