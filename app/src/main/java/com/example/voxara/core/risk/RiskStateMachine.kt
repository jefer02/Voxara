package com.example.voxara.core.risk

/**
 * RING STATE MACHINE. Five states, each with its own motion spec (see ui/gauge).
 * Colour never carries information alone: every state also has a localized label
 * (see ui/text/VoxStrings) and a haptic.
 */
enum class RiskState {
    /** < 80 dBA — aura breathes at 0.16 Hz, ticks 12% lit. Feels asleep. */
    CALM,

    /** 80-94 — hue crossfades green to amber over 600 ms, breath rate doubles. */
    ACCRUING,

    /** 95-105 — incandescent amber, glow +60%, dose arc gains a travelling head. */
    HAZARD,

    /** > 105 dBA or dose >= 100% — electric magenta, 1.1 Hz pulse locked to the haptics. */
    CRITICAL,

    /** Decay back down: colour returns over 1.4 s, always slower than the rise. */
    RECOVER;

    companion object {
        fun of(dba: Double, dosePercent: Double): RiskState = when {
            dosePercent >= 100.0 || dba >= 106.0 -> CRITICAL
            dba >= 95.0 || dosePercent >= 60.0 -> HAZARD
            dba >= 80.0 || dosePercent >= 25.0 -> ACCRUING
            else -> CALM
        }
    }
}

/**
 * The ring's state tracker plus the one gentle event it owns: relief when the level falls back
 * after a loud stretch. Every INTERRUPTING alert (sustained level, dose tiers, weekly budget) is
 * decided by `core.alerts.AlertEngine`, which is deterministic and never rate-limits a 100% alert.
 */
class RiskStateMachine(private val calmCooldownMs: Long = 600_000L) {

    var state: RiskState = RiskState.CALM
        private set

    private var wasLoud = false
    private var lastCalmAt = 0L

    sealed interface Event {
        /** 140 dBC impulse peak. */
        data object ImpulsePeak : Event
        /** Back under 80 dBA after a loud (>= 85 dBA) stretch. Relief, not another alert. */
        data object BackToSafe : Event
    }

    fun resetDay() {
        wasLoud = false
        state = RiskState.CALM
    }

    /** @return the relief event, or null. */
    fun update(dba: Double, dosePercent: Double, nowMs: Long): Event? {
        val next = RiskState.of(dba, dosePercent)
        val previous = state
        state = if (next.ordinal < previous.ordinal && previous != RiskState.CALM) {
            if (next == RiskState.CALM) RiskState.RECOVER else next
        } else next

        if (dba >= 85.0) wasLoud = true
        if (dba < 80.0 && wasLoud) {
            wasLoud = false
            if (lastCalmAt == 0L || nowMs - lastCalmAt >= calmCooldownMs) {
                lastCalmAt = nowMs
                return Event.BackToSafe
            }
        }
        return null
    }

    fun impulse(): Event = Event.ImpulsePeak
}
