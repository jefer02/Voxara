package com.example.voxara.core.alerts

/**
 * THE FLOORS — what no setting (on the watch or synced from the phone) can weaken:
 *  - WARNING and LIMIT alerts always vibrate; only ADVISORY haptics can be turned off.
 *  - A snooze lasts 15 min to 2 h, and LIMIT alerts can never be snoozed ([AlertKind.snoozable]).
 *  - Quiet hours never hold a LIMIT alert (enforced in [AlertEngine.evaluate]).
 */
enum class HapticStrength { STANDARD, STRONG }

data class HapticPrefs(
    val strength: HapticStrength = HapticStrength.STANDARD,
    /** Vibrate for advisories (level over threshold, 50% of the day). Warnings and limits always do. */
    val advisoryHaptics: Boolean = true,
)

object AlertPolicy {
    const val SNOOZE_MIN_MINUTES = 15
    const val SNOOZE_MAX_MINUTES = 120
    const val SNOOZE_DEFAULT_MINUTES = 60
    val SNOOZE_CHOICES = listOf(15, 30, 60, 120)
    const val SNOOZE_MIN_MS = SNOOZE_MIN_MINUTES * 60_000L
    const val SNOOZE_MAX_MS = SNOOZE_MAX_MINUTES * 60_000L

    fun snoozeMs(minutes: Int): Long = minutes.coerceIn(SNOOZE_MIN_MINUTES, SNOOZE_MAX_MINUTES) * 60_000L

    /** Whether [kind] vibrates. Only advisories honour the "off" preference. */
    fun vibrates(kind: AlertKind, prefs: HapticPrefs): Boolean =
        kind.tier != Tier.ADVISORY || prefs.advisoryHaptics

    /** How many times the alert's pattern plays: STRONG repeats it, STANDARD is today's single play. */
    fun repeats(kind: AlertKind, prefs: HapticPrefs): Int = when {
        !vibrates(kind, prefs) -> 0
        prefs.strength == HapticStrength.STRONG -> 2
        else -> 1
    }
}
