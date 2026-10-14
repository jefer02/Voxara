package com.example.voxara.core.calibration

import com.example.voxara.core.dose.energyAverage
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * GUIDED CALIBRATION — pure rules, so every accept / reject decision is unit-tested.
 *
 * Flow: capture ~10 s of steady sound -> check it is steady -> the wearer enters the reference
 * reading -> check the level is meaningful -> offset = reference - measured -> optionally repeat
 * at a different level and compare.
 */
object CalibrationRules {
    const val MEASURE_SECONDS = 10
    /** At 4 readings per second a 10 s capture yields ~40; fewer means the mic dropped out. */
    const val MIN_READINGS = 24
    /** Standard deviation of the short-term levels. A fan or steady noise sits well under 1 dB. */
    const val MAX_SPREAD_DB = 2.0
    /** Below this a wrist mic is close to its own noise floor: not a meaningful reference. */
    const val MIN_REFERENCE_DBA = 50.0
    /** The wearer is asked to stay at or under this. */
    const val SAFE_REFERENCE_DBA = 85.0
    /** Above this the step is refused outright, for the wearer's ears. */
    const val MAX_REFERENCE_DBA = 90.0
    /** Two measurements further apart than this disagree. */
    const val CONSISTENCY_DB = 3.0
    /** Crown / button step for the reference value. */
    const val REFERENCE_STEP_DB = 0.5
}

/** What the reference reading came from — it bounds how much to trust the result. */
enum class ReferenceType {
    /** A real sound level meter (Class 1/2). */
    SOUND_LEVEL_METER,
    /** A phone sound-meter app: rough, roughly +-3..5 dB. */
    PHONE_APP,
}

/** A stored calibration for one capture path. */
data class CalibrationRecord(
    val source: MicSource,
    val offsetDb: Double,
    val calibratedAtMs: Long,
    val deviceModel: String,
    val referenceType: ReferenceType,
    /** The second measurement's offset, when one was taken. */
    val secondOffsetDb: Double? = null,
)

/** Result of a capture window. */
sealed interface CaptureCheck {
    data class Steady(val leqDbfsA: Double, val spreadDb: Double, val readings: Int) : CaptureCheck
    data class Unsteady(val spreadDb: Double) : CaptureCheck
    data class NotEnoughData(val readings: Int) : CaptureCheck
}

/** Checks the short-term A-weighted dBFS levels captured during the window. */
fun checkCapture(levelsDbfsA: List<Double>): CaptureCheck {
    if (levelsDbfsA.size < CalibrationRules.MIN_READINGS) {
        return CaptureCheck.NotEnoughData(levelsDbfsA.size)
    }
    val spread = standardDeviation(levelsDbfsA)
    if (spread > CalibrationRules.MAX_SPREAD_DB) return CaptureCheck.Unsteady(spread)
    return CaptureCheck.Steady(energyAverage(levelsDbfsA.toDoubleArray()), spread, levelsDbfsA.size)
}

/** Checks the reference value the wearer entered. */
sealed interface ReferenceCheck {
    data object Ok : ReferenceCheck
    /** Accepted, but louder than the guidance asks for. */
    data object LouderThanAdvised : ReferenceCheck
    data object TooQuiet : ReferenceCheck
    data object TooLoud : ReferenceCheck
}

fun checkReference(referenceDba: Double): ReferenceCheck = when {
    referenceDba < CalibrationRules.MIN_REFERENCE_DBA -> ReferenceCheck.TooQuiet
    referenceDba > CalibrationRules.MAX_REFERENCE_DBA -> ReferenceCheck.TooLoud
    referenceDba > CalibrationRules.SAFE_REFERENCE_DBA -> ReferenceCheck.LouderThanAdvised
    else -> ReferenceCheck.Ok
}

/** The offset one measurement implies, or null when it cannot be a real microphone. */
fun calibrationOffset(referenceDba: Double, leqDbfsA: Double): Double? =
    offsetFromReference(referenceDba, leqDbfsA).takeIf { it in SOURCE_OFFSET_RANGE }

/** Two measurements taken at different levels. */
sealed interface Consistency {
    data class Consistent(val offsetDb: Double, val differenceDb: Double) : Consistency
    data class Inconsistent(val averageDb: Double, val differenceDb: Double) : Consistency
}

fun compareOffsets(first: Double, second: Double): Consistency {
    val diff = abs(first - second)
    val avg = (first + second) / 2.0
    return if (diff <= CalibrationRules.CONSISTENCY_DB) Consistency.Consistent(avg, diff)
    else Consistency.Inconsistent(avg, diff)
}

/** Snap a reference value to the 0.5 dB grid the crown moves on. */
fun snapReference(value: Double): Double =
    Math.round(value / CalibrationRules.REFERENCE_STEP_DB) * CalibrationRules.REFERENCE_STEP_DB

/**
 * A stored calibration only applies to the watch it was made on: a backup restored onto a
 * different model is an uncalibrated estimate again.
 */
fun CalibrationRecord?.appliesTo(deviceModel: String): Boolean =
    this != null && this.deviceModel == deviceModel

private fun standardDeviation(v: List<Double>): Double {
    val mean = v.average()
    return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
}
