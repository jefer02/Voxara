package com.example.voxara.core.scene

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SCENE FEATURES — 54 numbers per second of sound, the only thing that leaves a burst for the
 * automatic mode detector. The Python twin is `voxfeat.py` in the training kit
 * (C:\dataset_voxara\training); both MUST compute the same numbers, which
 * SceneFeaturesParityTest checks against reference vectors produced by that script. Change one,
 * change both, bump [VERSION], regenerate the reference and retrain.
 *
 *  01  Mono float PCM at 48 kHz, unweighted (the samples AudioBurstSampler reads), cut into
 *      non-overlapping frames of [FFT_SIZE] samples (21.3 ms) with a periodic Hann window.
 *  02  Power spectrum, [MEL_BANDS] triangular mel bands 50 Hz-12 kHz in dB, broadband frame
 *      level, spectral centroid and spectral flatness over the same bins.
 *  03  Every [FRAMES_PER_VECTOR] frames (0.98 s), one vector:
 *        [0..23]  spectral shape: mean band level minus the mean over bands (gain-invariant)
 *        [24..47] per-band temporal standard deviation (dB)
 *        [48] mean centroid (kHz) · [49] mean flatness (dB) · [50] frame-level std (dB)
 *        [51] mean |frame-level step| (dB) · [52] mean positive spectral flux (dB)
 *        [53] mean local tonality: flatness inside 12 blocks of 8 bins (234 Hz-4.7 kHz),
 *             tilt-free, low when the sound is harmonic (music) — the Concert rule reads it
 *
 * No absolute level is in the vector (the training recordings are not calibrated); the detector
 * takes the level from the calibrated dBA chain. A one-second average spectrum cannot be turned
 * back into speech, and the vectors are never written to disk.
 */
object SceneFeatures {
    const val VERSION = 1
    const val SAMPLE_RATE = 48_000
    const val FFT_SIZE = 1024
    const val FRAMES_PER_VECTOR = 46
    const val MEL_BANDS = 24
    const val FMIN_HZ = 50.0
    const val FMAX_HZ = 12_000.0
    const val EPS = 1e-12
    const val COUNT = 2 * MEL_BANDS + 6

    /** Local tonality blocks: [TONAL_BLOCKS] runs of [TONAL_BLOCK] bins from bin [TONAL_K0]. */
    const val TONAL_K0 = 5
    const val TONAL_BLOCK = 8
    const val TONAL_BLOCKS = 12

    /** Index of the mean spectral flatness (dB) in a vector — the Concert rule reads it. */
    const val FLATNESS = 2 * MEL_BANDS + 1

    /** Index of the mean local tonality (dB) — music-like sound is low. */
    const val TONALITY = 2 * MEL_BANDS + 5

    /** Samples one vector needs: 46 x 1024 = 47 104 (0.98 s at 48 kHz). */
    const val SAMPLES_PER_VECTOR = FFT_SIZE * FRAMES_PER_VECTOR

    internal val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / FFT_SIZE) }

    internal val kMin = ceil(FMIN_HZ * FFT_SIZE / SAMPLE_RATE).toInt()
    internal val kMax = floor(FMAX_HZ * FFT_SIZE / SAMPLE_RATE).toInt()

    internal fun binHz(k: Int): Double = k.toDouble() * SAMPLE_RATE / FFT_SIZE

    private fun hzToMel(f: Double) = 2595.0 * log10(1.0 + f / 700.0)
    private fun melToHz(m: Double) = 700.0 * (10.0.pow(m / 2595.0) - 1.0)

    /** Dense triangular weights [band][bin]; zero outside each triangle. */
    internal val mel: Array<DoubleArray> = run {
        val lo = hzToMel(FMIN_HZ)
        val hi = hzToMel(FMAX_HZ)
        // Same as numpy.linspace(lo, hi, MEL_BANDS + 2).
        val step = (hi - lo) / (MEL_BANDS + 1)
        val edges = DoubleArray(MEL_BANDS + 2) { i -> melToHz(if (i == MEL_BANDS + 1) hi else lo + i * step) }
        Array(MEL_BANDS) { b ->
            val l = edges[b]; val c = edges[b + 1]; val h = edges[b + 2]
            DoubleArray(FFT_SIZE / 2 + 1) { k ->
                val f = binHz(k)
                max(0.0, min((f - l) / (c - l), (h - f) / (h - c)))
            }
        }
    }

    /** First and last non-zero bin of each band, so the band sums skip the zeros. */
    internal val melFirst = IntArray(MEL_BANDS) { b -> mel[b].indexOfFirst { it > 0.0 }.coerceAtLeast(0) }
    internal val melLast = IntArray(MEL_BANDS) { b -> mel[b].indexOfLast { it > 0.0 } }
}

/**
 * Streaming extractor: feed it PCM in any chunk sizes (a 100 ms read, a 250 ms burst) and it
 * emits exactly the vectors [SceneFeatures] defines for the concatenated signal. One instance per
 * capture session; not thread-safe. Allocation-free per frame.
 */
class SceneFeatureExtractor {
    private val n = SceneFeatures.FFT_SIZE
    private val bands = SceneFeatures.MEL_BANDS
    private val frames = SceneFeatures.FRAMES_PER_VECTOR

    private val frame = DoubleArray(n)
    private var fill = 0
    private val fft = RealFft(n)
    private val power = DoubleArray(n / 2 + 1)

    private val levels = Array(frames) { DoubleArray(bands) }
    private val frameLevel = DoubleArray(frames)
    private val centroid = DoubleArray(frames)
    private val flatness = DoubleArray(frames)
    private val tonality = DoubleArray(frames)
    private var t = 0

    private val ready = ArrayList<DoubleArray>(2)

    /** Feeds [samples] in [from, to). Completed vectors wait in [takeVectors]. */
    fun push(samples: FloatArray, from: Int = 0, to: Int = samples.size) {
        for (i in from until to) {
            frame[fill++] = samples[i].toDouble()
            if (fill == n) {
                fill = 0
                analyseFrame()
                if (t == frames) {
                    ready += vector()
                    t = 0
                }
            }
        }
    }

    /** The vectors completed so far (oldest first); the extractor forgets them. */
    fun takeVectors(): List<DoubleArray> {
        if (ready.isEmpty()) return emptyList()
        val out = ArrayList(ready)
        ready.clear()
        return out
    }

    /** Drops any partial frame and vector (e.g. when a capture session restarts). */
    fun reset() {
        fill = 0
        t = 0
        ready.clear()
    }

    private fun analyseFrame() {
        val w = SceneFeatures.window
        for (i in 0 until n) frame[i] *= w[i]
        fft.powerSpectrum(frame, power)

        val l = levels[t]
        for (b in 0 until bands) {
            val weights = SceneFeatures.mel[b]
            var e = 0.0
            for (k in SceneFeatures.melFirst[b]..SceneFeatures.melLast[b]) e += weights[k] * power[k]
            l[b] = 10.0 * log10(e + SceneFeatures.EPS)
        }

        var sum = 0.0
        var weighted = 0.0
        var logSum = 0.0
        val k0 = SceneFeatures.kMin
        val k1 = SceneFeatures.kMax
        for (k in k0..k1) {
            val p = power[k]
            sum += p
            weighted += p * SceneFeatures.binHz(k)
            logSum += ln(p + SceneFeatures.EPS)
        }
        val count = (k1 - k0 + 1).toDouble()
        frameLevel[t] = 10.0 * log10(sum + SceneFeatures.EPS)
        centroid[t] = weighted / (sum + SceneFeatures.EPS)
        flatness[t] = 10.0 * log10(exp(logSum / count) / (sum / count + SceneFeatures.EPS))

        var tonal = 0.0
        for (block in 0 until SceneFeatures.TONAL_BLOCKS) {
            val from = SceneFeatures.TONAL_K0 + block * SceneFeatures.TONAL_BLOCK
            var bSum = 0.0
            var bLog = 0.0
            for (k in from until from + SceneFeatures.TONAL_BLOCK) {
                bSum += power[k]
                bLog += ln(power[k] + SceneFeatures.EPS)
            }
            val blockLen = SceneFeatures.TONAL_BLOCK.toDouble()
            tonal += 10.0 * log10(exp(bLog / blockLen) / (bSum / blockLen + SceneFeatures.EPS))
        }
        tonality[t] = tonal / SceneFeatures.TONAL_BLOCKS
        t++
    }

    private fun vector(): DoubleArray {
        val out = DoubleArray(SceneFeatures.COUNT)
        val mu = DoubleArray(bands)
        for (b in 0 until bands) {
            var s = 0.0
            for (f in 0 until frames) s += levels[f][b]
            mu[b] = s / frames
        }
        val grand = mu.average()
        for (b in 0 until bands) {
            out[b] = mu[b] - grand
            var v = 0.0
            for (f in 0 until frames) { val d = levels[f][b] - mu[b]; v += d * d }
            out[bands + b] = sqrt(v / frames)
        }
        out[2 * bands] = centroid.average() / 1000.0
        out[2 * bands + 1] = flatness.average()
        val eMean = frameLevel.average()
        var ev = 0.0
        for (f in 0 until frames) { val d = frameLevel[f] - eMean; ev += d * d }
        out[2 * bands + 2] = sqrt(ev / frames)
        var jump = 0.0
        var flux = 0.0
        for (f in 1 until frames) {
            jump += abs(frameLevel[f] - frameLevel[f - 1])
            var up = 0.0
            for (b in 0 until bands) up += max(0.0, levels[f][b] - levels[f - 1][b])
            flux += up / bands
        }
        out[2 * bands + 3] = jump / (frames - 1)
        out[2 * bands + 4] = flux / (frames - 1)
        out[2 * bands + 5] = tonality.average()
        return out
    }
}

/**
 * Iterative radix-2 FFT of a real frame (imaginary part zero), power spectrum only. Tables are
 * built once per instance; the transform itself allocates nothing.
 */
internal class RealFft(private val n: Int) {
    init { require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" } }

    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val cosT = DoubleArray(n / 2) { cos(2.0 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { -sin(2.0 * PI * it / n) }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** [out] gets |X_k|^2 for k = 0..n/2. */
    fun powerSpectrum(x: DoubleArray, out: DoubleArray) {
        for (i in 0 until n) { re[rev[i]] = x[i]; im[rev[i]] = 0.0 }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosT[k]; val wi = sinT[k]
                    val l = j + half
                    val tr = wr * re[l] - wi * im[l]
                    val ti = wr * im[l] + wi * re[l]
                    re[l] = re[j] - tr; im[l] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
        for (k in 0..n / 2) out[k] = re[k] * re[k] + im[k] * im[k]
    }
}
