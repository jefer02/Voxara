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

/** The transition detector. Owns the rules about which haptic fires and how often. */
class RiskStateMachine(private val calmCooldownMs: Long = 600_000L) {

    var state: RiskState = RiskState.CALM
        private set

    private var breachedDose = false
    private var crossedThreshold = false
    private var lastCalmAt = 0L
    private var alertsToday = 0

    /** Hard cap in FOCUS/URBAN mode: four interruptions a day, no more. */
    var dailyAlertCap = 4

    sealed interface Event {
        /** 85 dBA crossed upwards: "you're now on the clock." */
        data object ThresholdCrossed : Event
        /** Daily dose hit 100%: get out. */
        data object DoseFull : Event
        /** 140 dBC impulse peak. */
        data object ImpulsePeak : Event
        /** Back under 80 dBA after a loud stretch. Relief, not another alert. */
        data object BackToSafe : Event
    }

    fun resetDay() {
        breachedDose = false
        crossedThreshold = false
        alertsToday = 0
        state = RiskState.CALM
    }

    /**
     * @return the event worth interrupting the wearer for, or null.
     */
    fun update(dba: Double, dosePercent: Double, nowMs: Long, respectCap: Boolean = true): Event? {
        val next = RiskState.of(dba, dosePercent)
        val previous = state
        state = if (next.ordinal < previous.ordinal && previous != RiskState.CALM) {
            if (next == RiskState.CALM) RiskState.RECOVER else next
        } else next

        if (dosePercent >= 100.0 && !breachedDose) {
            breachedDose = true
            return spend(Event.DoseFull, respectCap)
        }
        if (dba >= 85.0 && !crossedThreshold) {
            crossedThreshold = true
            return spend(Event.ThresholdCrossed, respectCap)
        }
        if (dba < 80.0 && crossedThreshold) {
            crossedThreshold = false
            if (nowMs - lastCalmAt >= calmCooldownMs) {
                lastCalmAt = nowMs
                return Event.BackToSafe // relief is never rate-capped against the alert budget
            }
        }
        return null
    }

    fun impulse(): Event = Event.ImpulsePeak

    private fun spend(e: Event, respectCap: Boolean): Event? {
        if (respectCap && alertsToday >= dailyAlertCap) return null
        alertsToday++
        return e
    }
}
