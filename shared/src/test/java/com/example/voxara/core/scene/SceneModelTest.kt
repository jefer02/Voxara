package com.example.voxara.core.scene

import com.example.voxara.core.situation.SituationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * THE EXPORTED MODEL — the generated weights give, in Kotlin, the mode probabilities train.py
 * computed in Python for the same fixed vectors (the reference signal and one real evaluation
 * window per scene; see [SceneModelReference]). Plus the shape of the export and its licence flag.
 */
class SceneModelTest {

    @Test
    fun `kotlin predictions equal the python ones on fixed vectors`() {
        assertTrue(SceneModelReference.CASES.size >= 2)
        for ((label, features, expected) in SceneModelReference.CASES) {
            val got = SceneModel.modeProbabilities(features)
            for (i in expected.indices) {
                assertTrue("$label mode $i: python ${expected[i]} kotlin ${got[i]}", abs(expected[i] - got[i]) < 1e-6)
            }
        }
    }

    @Test
    fun `probabilities are a distribution over the four model modes`() {
        for ((_, features, _) in SceneModelReference.CASES) {
            val m = SceneModel.modeProbabilities(features)
            assertEquals(4, m.size)
            assertEquals(1.0, m.sum(), 1e-9)
            assertTrue(m.all { it in 0.0..1.0 })
            val c = SceneModel.classProbabilities(features)
            assertEquals(SceneModelWeights.CLASSES.size, c.size)
            assertEquals(1.0, c.sum(), 1e-9)
        }
    }

    @Test
    fun `the export is consistent and Concert is never a model output`() {
        assertEquals(listOf(SituationMode.QUIET, SituationMode.STREET, SituationMode.TRANSPORT, SituationMode.WORK), SceneModel.MODES)
        assertFalse(SituationMode.CONCERT in SceneModel.MODES)
        assertEquals(SceneModelWeights.CLASSES.size, SceneModelWeights.CLASS_MODE.size)
        assertTrue(SceneModelWeights.CLASS_MODE.all { it in SceneModel.MODES.indices })
        assertEquals(SceneFeatures.COUNT, SceneModelWeights.SIZES.first())
        assertEquals(SceneModelWeights.CLASSES.size, SceneModelWeights.SIZES.last())
        assertEquals(SceneFeatures.COUNT, SceneModelWeights.MEAN.size)
        assertTrue(SceneModelWeights.SCALE.all { it > 0f })
        for (l in SceneModelWeights.WEIGHTS.indices) {
            assertEquals(SceneModelWeights.SIZES[l] * SceneModelWeights.SIZES[l + 1], SceneModelWeights.WEIGHTS[l].size)
            assertEquals(SceneModelWeights.SIZES[l + 1], SceneModelWeights.BIASES[l].size)
        }
    }

    @Test
    fun `the training data does not allow commercial use - the release checklist must see this`() {
        assertFalse(SceneModel.commercialUseAllowed)
        assertTrue(SceneModel.trainingData.contains("TUT Acoustic Scenes 2017"))
    }

    @Test
    fun `wrong-sized input is refused`() {
        try {
            SceneModel.modeProbabilities(DoubleArray(SceneFeatures.COUNT - 1))
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    /**
     * Cost probe, not a pass/fail check: one vote = 46 FFT frames + one model call. Printed to the
     * test report (stdout); the PROGRESS.md cost estimate scales it to the watch's cores.
     */
    @Test
    fun `cost probe - features and inference per vote on this JVM`() {
        val x = FloatArray(SceneFeatures.SAMPLES_PER_VECTOR) { ((it * 7919) % 2000 - 1000) / 4000f }
        val ex = SceneFeatureExtractor()
        repeat(30) { ex.push(x); ex.takeVectors().forEach { SceneModel.modeProbabilities(it) } } // warm-up
        val n = 200
        var t0 = System.nanoTime()
        var last = DoubleArray(0)
        repeat(n) { ex.push(x); last = ex.takeVectors().single() }
        val featNs = (System.nanoTime() - t0) / n
        t0 = System.nanoTime()
        repeat(n * 10) { SceneModel.modeProbabilities(last) }
        val modelNs = (System.nanoTime() - t0) / (n * 10)
        println("COST features ${featNs / 1000} us per vote (0.98 s audio), model ${modelNs / 1000.0} us, ${SceneModel.multiplyAdds} multiply-adds")
    }
}
