package com.example.voxara.core.scene

import com.example.voxara.core.situation.SituationMode
import kotlin.math.exp

/**
 * SCENE MODEL — one feature vector in, how likely each situation mode is out.
 *
 * A tiny dense model (weights in the generated [SceneModelWeights]) over the 53 [SceneFeatures]:
 * standardise, dense layers with ReLU between them, softmax over the training dataset's scene
 * classes, then the class probabilities are summed into the four modes the model can tell
 * apart. Concert is never a model output: it is a deterministic rule (see AutoModeDetector).
 * No dependency, no file to load, a few thousand multiply-adds per call.
 */
object SceneModel {

    /** The modes the model can tell apart, in output order (SceneModelWeights.CLASS_MODE). */
    val MODES: List<SituationMode> =
        listOf(SituationMode.QUIET, SituationMode.STREET, SituationMode.TRANSPORT, SituationMode.WORK)

    /** The scene labels, in [classProbabilities] order (debug log). */
    val classes: List<String> get() = SceneModelWeights.CLASSES.asList()

    /** Where the weights come from, for the About screen and the release checklist. */
    val trainingData: String get() = SceneModelWeights.TRAINING_DATA
    val commercialUseAllowed: Boolean get() = SceneModelWeights.COMMERCIAL_USE_ALLOWED

    /** Softmax over [SceneModelWeights.CLASSES]. */
    fun classProbabilities(features: DoubleArray): DoubleArray {
        require(features.size == SceneFeatures.COUNT) { "expected ${SceneFeatures.COUNT} features, got ${features.size}" }
        val w = SceneModelWeights
        var z = DoubleArray(features.size) { (features[it] - w.MEAN[it]) / w.SCALE[it] }
        val layers = w.WEIGHTS.size
        for (layer in 0 until layers) {
            val inputs = w.SIZES[layer]
            val outputs = w.SIZES[layer + 1]
            val weights = w.WEIGHTS[layer]
            val bias = w.BIASES[layer]
            val next = DoubleArray(outputs) { bias[it].toDouble() }
            for (i in 0 until inputs) {
                val zi = z[i]
                if (zi == 0.0) continue
                val row = i * outputs
                for (o in 0 until outputs) next[o] += zi * weights[row + o]
            }
            if (layer < layers - 1) for (o in next.indices) if (next[o] < 0.0) next[o] = 0.0
            z = next
        }
        val max = z.max()
        var sum = 0.0
        for (i in z.indices) { z[i] = exp(z[i] - max); sum += z[i] }
        for (i in z.indices) z[i] /= sum
        return z
    }

    /** Probabilities over [MODES] (they sum to 1). */
    fun modeProbabilities(features: DoubleArray): DoubleArray = modesOf(classProbabilities(features))

    /** Class probabilities (in [SceneModelWeights.CLASSES] order) summed into [MODES]. */
    fun modesOf(p: DoubleArray): DoubleArray {
        val m = DoubleArray(MODES.size)
        for (c in p.indices) m[SceneModelWeights.CLASS_MODE[c]] += p[c]
        var sum = 0.0
        for (i in m.indices) { m[i] *= SceneModelWeights.MODE_WEIGHT[i]; sum += m[i] }
        for (i in m.indices) m[i] /= sum
        return m
    }

    /** Multiply-adds per call, for the cost estimate. */
    val multiplyAdds: Int
        get() = (0 until SceneModelWeights.WEIGHTS.size).sumOf { SceneModelWeights.SIZES[it] * SceneModelWeights.SIZES[it + 1] }
}
