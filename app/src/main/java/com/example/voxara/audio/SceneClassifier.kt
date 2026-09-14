package com.example.voxara.audio

import com.example.voxara.core.scene.Scene
import kotlin.math.sqrt

/**
 * The seam the Phase-3 TFLite head plugs into.
 *
 * The dossier's target is a YAMNet-class int8 embedding model (~4 MB) run on the 0.96 s burst
 * window, with a 2-layer head collapsing AudioSet's 521 labels into six actionable scenes and a
 * <= 25 ms per-window budget. That model is not bundled here; what ships is the interface, the
 * hysteresis gate, and the statistics-only fallback below so every caller downstream is already
 * written against the real contract.
 */
interface SceneClassifier {
    /** @return scene plus confidence 0..1. Confidence below 0.60 is gated out upstream. */
    fun classify(burst: AudioBurstSampler.Burst): Pair<Scene, Float>
}

/**
 * Statistics-only stand-in: level band, temporal variance and crest factor. Deliberately
 * conservative — it returns low confidence whenever the shape is ambiguous, so the hysteresis
 * gate reports UNKNOWN and the app falls back to level-only advice.
 */
class HeuristicSceneClassifier : SceneClassifier {

    override fun classify(burst: AudioBurstSampler.Burst): Pair<Scene, Float> {
        val f = burst.frames
        if (f.size < 3) return Scene.UNKNOWN to 0f
        val mean = f.average()
        val variance = f.sumOf { (it - mean) * (it - mean) } / f.size
        val sd = sqrt(variance)
        val crest = burst.lmaxDba - burst.leqDba

        return when {
            mean < 45 -> Scene.QUIET_INDOOR to 0.75f
            mean < 62 && sd < 4 -> Scene.QUIET_INDOOR to 0.62f
            mean < 78 && sd in 2.0..8.0 -> Scene.CONVERSATION to 0.68f
            mean in 78.0..95.0 && sd < 3.5 -> Scene.TRAFFIC to 0.66f
            mean in 78.0..95.0 && crest > 9 -> Scene.TRANSIT to 0.61f
            mean >= 95 && sd < 3.0 -> Scene.LIVE_MUSIC to 0.70f
            mean >= 95 && crest > 10 -> Scene.MACHINERY to 0.64f
            else -> Scene.UNKNOWN to 0.30f
        }
    }
}
