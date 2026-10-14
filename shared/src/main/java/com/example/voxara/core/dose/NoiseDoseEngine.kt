package com.example.voxara.core.dose

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * DOMAIN CORE — pure Kotlin, zero Android imports, so the dose engine can be unit-tested
 * against the published NIOSH duration tables without an emulator.
 *
 * The three equations of the dossier:
 *   T(L) = 8 / 2^((L - 85) / 3)   hours
 *   D    = 100 x SUM (Ci / Ti)    percent
 *   TWA  = 10 * log10(D / 100) + 85
 */

const val NIOSH_REL_DBA = 85.0
const val EXCHANGE_RATE_DB = 3.0
const val DOSE_THRESHOLD_DBA = 80.0
const val PEAK_CEILING_DBC = 140.0

/** ITU-T H.870: 1.6 Pa^2h per week for adults, 0.51 Pa^2h (75 dB) for sensitive users. */
const val WHO_WEEKLY_PA2H_ADULT = 1.6
const val WHO_WEEKLY_PA2H_SENSITIVE = 0.51

private const val P0_SQUARED = 4.0e-10 // (20 uPa)^2

/** NIOSH permitted duration at a steady level, in seconds. */
fun permittedSeconds(dba: Double): Double =
    8.0 * 3600.0 / 2.0.pow((dba - NIOSH_REL_DBA) / EXCHANGE_RATE_DB)

/** NIOSH permitted duration at a steady level, in hours. */
fun permittedHours(dba: Double): Double = permittedSeconds(dba) / 3600.0

/** Immutable snapshot of the ledger, safe to hand to the UI layer. */
data class DoseSnapshot(
    val dosePercent: Double,
    val weeklyPa2h: Double,
    val peakDbc: Double,
    val twaDba: Double?,
)

class NoiseDoseEngine(
    initialDosePercent: Double = 0.0,
    initialWeeklyPa2h: Double = 0.0,
) {
    var dosePercent = initialDosePercent
        private set
    var weeklyPa2h = initialWeeklyPa2h
        private set
    var peakDbc = 0.0
        private set

    /**
     * Fold one burst into the ledger.
     *
     * @param leq the burst's A-weighted equivalent level
     * @param representsSeconds wall-clock span this burst stands in for (duty-cycle corrected)
     */
    fun accumulate(leq: Double, representsSeconds: Double) {
        if (representsSeconds <= 0.0) return
        if (leq >= DOSE_THRESHOLD_DBA) {
            dosePercent += 100.0 * representsSeconds / permittedSeconds(leq)
        }
        weeklyPa2h += P0_SQUARED * 10.0.pow(leq / 10.0) * (representsSeconds / 3600.0)
    }

    /** The peak-hold path. Returns true the first time the 140 dBC ceiling is breached. */
    fun notePeak(dbc: Double): Boolean {
        val wasUnder = peakDbc < PEAK_CEILING_DBC
        if (dbc > peakDbc) peakDbc = dbc
        return wasUnder && peakDbc >= PEAK_CEILING_DBC
    }

    /** 8-hour TWA equivalent of the dose so far, or null while nothing has accrued. */
    fun twaDba(): Double? =
        if (dosePercent <= 0.0) null
        else 10.0 * log10(dosePercent / 100.0) + NIOSH_REL_DBA

    /** Seconds of headroom left at the level currently being measured. */
    fun headroomSeconds(currentDba: Double): Double =
        if (currentDba < DOSE_THRESHOLD_DBA) Double.POSITIVE_INFINITY
        else (permittedSeconds(currentDba) * (1.0 - dosePercent / 100.0)).coerceAtLeast(0.0)

    /** Fraction of the WHO weekly allowance spent, 0..1+. */
    fun weeklyFraction(sensitive: Boolean = false): Double =
        weeklyPa2h / (if (sensitive) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT)

    fun snapshot() = DoseSnapshot(dosePercent, weeklyPa2h, peakDbc, twaDba())

    /** Rehydrate from persisted state after a process death. */
    fun restore(dosePercent: Double, weeklyPa2h: Double, peakDbc: Double) {
        this.dosePercent = dosePercent
        this.weeklyPa2h = weeklyPa2h
        this.peakDbc = peakDbc
    }

    fun resetDay() {
        dosePercent = 0.0
        peakDbc = 0.0
    }

    fun resetWeek() {
        weeklyPa2h = 0.0
    }
}

/**
 * Adaptive cadence — spend power where the risk is.
 * Duty cycles: 1.7% / 3.3% / 20% / 100%.
 */
enum class SampleState(val burstMs: Long, val periodMs: Long, val label: String) {
    IDLE(1_000, 60_000, "IDLE"),
    AMBIENT(1_000, 30_000, "AMBIENT"),
    ACCRUING(2_000, 10_000, "ACCRUING"),
    HAZARD(4_000, 4_000, "HAZARD");

    val dutyCycle: Double get() = burstMs.toDouble() / periodMs.toDouble()

    /** Run the scene classifier on every burst above AMBIENT, every 5th burst in AMBIENT. */
    val classifierEveryNthBurst: Int
        get() = when (this) {
            IDLE -> 0
            AMBIENT -> 5
            else -> 1
        }

    companion object {
        fun of(leq: Double): SampleState = when {
            leq >= 95.0 -> HAZARD
            leq >= DOSE_THRESHOLD_DBA -> ACCRUING
            leq >= 65.0 -> AMBIENT
            else -> IDLE
        }
    }
}

/**
 * Samples arrive already A-weighted by the biquad cascade; 94 dB is the full-scale reference
 * (pink noise at 94 dB / 1 kHz is the calibration tone of the Phase 0 rig).
 */
fun FloatArray.frameDba(calibrationOffsetDb: Double): Double {
    if (isEmpty()) return 0.0
    var sum = 0.0
    for (s in this) sum += (s * s).toDouble()
    val rms = sqrt(sum / size).coerceAtLeast(1e-7)
    return 20.0 * log10(rms) + 94.0 + calibrationOffsetDb
}

/** Energy-average a set of frame levels into one Leq. */
fun energyAverage(levels: DoubleArray): Double {
    if (levels.isEmpty()) return 0.0
    var acc = 0.0
    for (l in levels) acc += 10.0.pow(l / 10.0)
    return 10.0 * log10(acc / levels.size)
}
