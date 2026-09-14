package com.example.voxara.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A-weighting as an IIR biquad cascade (IEC 61672-1), derived by bilinear transform of the
 * analog prototype at the running sample rate — so it stays correct if the DSP path is
 * decimated to 16 kHz to save power.
 *
 *   A(s) = K * s^4 / ((s+w1)^2 (s+w2) (s+w3) (s+w4)^2)
 *   f1 = 20.598997, f2 = 107.65265, f3 = 737.86223, f4 = 12194.217 Hz
 *
 * Pure math, no Android imports: unit-testable against published 1/3-octave tolerances.
 */
class AWeightingFilter(sampleRate: Int) {

    private class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
    ) {
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }

        /** |H(e^jw)| for gain normalisation. */
        fun magnitude(w: Double): Double {
            val cw = cos(w); val sw = sin(w)
            val c2w = cos(2 * w); val s2w = sin(2 * w)
            val nr = b0 + b1 * cw + b2 * c2w
            val ni = -(b1 * sw + b2 * s2w)
            val dr = 1.0 + a1 * cw + a2 * c2w
            val di = -(a1 * sw + a2 * s2w)
            return hypot(nr, ni) / hypot(dr, di)
        }
    }

    private val sections: List<Biquad>
    private val gain: Double

    init {
        val c = 2.0 * sampleRate
        val w1 = 2 * PI * 20.598997
        val w2 = 2 * PI * 107.65265
        val w3 = 2 * PI * 737.86223
        val w4 = 2 * PI * 12194.217

        // s^2 / (s + w1)^2
        val s1 = bilinear(1.0, 0.0, 0.0, 1.0, 2 * w1, w1 * w1, c)
        // s^2 / ((s + w2)(s + w3))
        val s2 = bilinear(1.0, 0.0, 0.0, 1.0, w2 + w3, w2 * w3, c)
        // 1 / (s + w4)^2  — carries the remaining two poles, no zeros
        val s3 = bilinear(0.0, 0.0, 1.0, 1.0, 2 * w4, w4 * w4, c)
        sections = listOf(s1, s2, s3)

        // Normalise so the filter is exactly 0 dB at 1 kHz (A1000 = 1.9997 in the analog form).
        val w1k = 2 * PI * 1000.0 / sampleRate
        var m = 1.0
        for (s in sections) m *= s.magnitude(w1k)
        gain = if (m > 0.0) 1.0 / m else 1.0
    }

    /** Analog (b2 s^2 + b1 s + b0) / (a2 s^2 + a1 s + a0) -> digital biquad. */
    private fun bilinear(
        b2: Double, b1: Double, b0: Double,
        a2: Double, a1: Double, a0: Double,
        c: Double,
    ): Biquad {
        val cc = c * c
        val nb0 = b2 * cc + b1 * c + b0
        val nb1 = 2 * b0 - 2 * b2 * cc
        val nb2 = b2 * cc - b1 * c + b0
        val na0 = a2 * cc + a1 * c + a0
        val na1 = 2 * a0 - 2 * a2 * cc
        val na2 = a2 * cc - a1 * c + a0
        return Biquad(nb0 / na0, nb1 / na0, nb2 / na0, na1 / na0, na2 / na0)
    }

    fun reset() = sections.forEach { it.reset() }

    /** Filters in place and returns the same array, A-weighted. */
    fun process(samples: FloatArray): FloatArray {
        for (i in samples.indices) {
            var v = samples[i].toDouble()
            for (s in sections) v = s.process(v)
            samples[i] = (v * gain).toFloat()
        }
        return samples
    }
}
