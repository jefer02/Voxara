package com.example.voxara.debug

import com.example.voxara.core.situation.SituationMode

/**
 * ONE AUTO-MODE CLASSIFICATION, as the debug detection log (DetectionLog, src/debug) records it.
 * Numbers and labels only — never audio, never the feature vector. Release builds build none
 * (their DetectionLog is a no-op and the service skips the work).
 */
data class DetectionTrace(
    val atMs: Long,
    /** Burst level (calibrated or provisional dBA). */
    val dba: Double,
    /** Feature extraction time for this vote's vectors, total (ms), and how many vectors. */
    val featureMs: Double,
    val vectors: Int,
    /** Scene model time for the vote (ms); 0 when the licence gate keeps the model off. */
    val inferenceMs: Double,
    /** The three likeliest scene classes with their probabilities; empty without the model. */
    val topClasses: List<Pair<String, Double>>,
    /** The vote over QUIET, STREET, TRANSPORT, WORK after the level vetoes and priors. */
    val vote: DoubleArray?,
    val musicLike: Boolean,
    val tonalityDb: Double,
    /** AutoModeDetector.Veto names that applied to this vote. */
    val vetoes: List<String>,
    /** The detector's decision (null = not sure) and the mode in force after it. */
    val detected: SituationMode?,
    val mode: SituationMode,
    val confidence: Double,
    val headphonesPlaying: Boolean,
    val sceneModelEnabled: Boolean,
)
