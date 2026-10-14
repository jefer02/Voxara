package com.example.voxara.core.calibration

import kotlin.math.log10

/**
 * PROVISIONAL MICROPHONE OFFSETS — the ONE place to change the uncalibrated defaults.
 *
 * These are NOT measured on any real watch. They are derived from the Android CDD sensitivity
 * targets (see [cddDerivedOffset]) and only make an uncalibrated watch read in the right
 * ballpark. Every reading taken with them is an "uncalibrated estimate". The guided calibration
 * (Settings -> Calibration) replaces them per source with a value measured against a reference.
 *
 * Level chain: dBA = dBFS(A) + 94 + offset.
 */
object ProvisionalMicOffsets {
    /** CDD 5.11 target: 94 dB SPL, 1 kHz -> RMS 520/32768 (-35.99 dBFS). */
    const val UNPROCESSED_DB = 36.0

    /** CDD 5.4 target: 90 dB SPL, 1 kHz -> RMS 2500/32768 (-22.35 dBFS). */
    const val VOICE_RECOGNITION_DB = 18.0
}

/**
 * The capture paths Voxara can measure through. Each one has its own sensitivity, so each one
 * carries its own dBFS -> dB SPL offset.
 */
enum class MicSource(val provisionalOffsetDb: Double) {
    UNPROCESSED(ProvisionalMicOffsets.UNPROCESSED_DB),
    VOICE_RECOGNITION(ProvisionalMicOffsets.VOICE_RECOGNITION_DB);
}

/** The exact CDD-derived value the provisional offsets are rounded from. */
fun cddDerivedOffset(source: MicSource): Double = when (source) {
    MicSource.UNPROCESSED -> fullScaleOffset(splDb = 94.0, rms16 = 520.0)
    MicSource.VOICE_RECOGNITION -> fullScaleOffset(splDb = 90.0, rms16 = 2500.0)
}

/** Offset that maps a CDD reference tone onto its SPL under the `dBFS + 94 + offset` chain. */
fun fullScaleOffset(splDb: Double, rms16: Double): Double =
    splDb - (dbfsOfRms(rms16 / 32768.0) + REFERENCE_DB)

/** RMS (1.0 = full scale) to dBFS. */
fun dbfsOfRms(rms: Double): Double = 20.0 * log10(rms.coerceAtLeast(1e-7))

/** The fixed reference term of the level chain. */
const val REFERENCE_DB = 94.0

/**
 * The per-source offset that makes the watch agree with a reference meter.
 *
 * @param referenceDba what the reference reads (A-weighted, same position)
 * @param measuredDbfsA the watch's A-weighted level in dBFS over the same window
 */
fun offsetFromReference(referenceDba: Double, measuredDbfsA: Double): Double =
    referenceDba - (measuredDbfsA + REFERENCE_DB)

/**
 * Stored source offsets must fit here. Wide enough to hold any real MEMS microphone
 * (sensitivities span roughly -20..-45 dBFS at 94 dB), narrow enough to reject a typo.
 */
val SOURCE_OFFSET_RANGE = -60.0..60.0

/** The wearer's fine trim on top of the source offset. */
val TRIM_RANGE = -40.0..40.0
