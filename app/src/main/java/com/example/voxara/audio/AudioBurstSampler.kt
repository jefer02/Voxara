package com.example.voxara.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.voxara.core.dose.energyAverage
import com.example.voxara.core.dose.frameDba
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * BURST PIPELINE - 48 kHz MONO / 16-BIT
 *
 *  01  AudioRecord opens with UNPROCESSED when the device reports a flat-response mic,
 *      otherwise VOICE_RECOGNITION with AGC disabled.
 *  02  Drop the first 120 ms (mic warm-up transient), then window into 100 ms frames.
 *  03  Per frame: RMS -> A-weighting IIR biquad cascade -> dBFS -> dBA via calibration offset.
 *  04  Energy-average frames into one Leq per burst; keep the max frame as Lmax and the raw
 *      peak for the impulse check.
 *  05  Release the mic. PCM never leaves the process, never touches disk, never leaves the watch.
 */
class AudioBurstSampler(
    private val preferUnprocessed: Boolean = true,
) {
    companion object {
        private const val TAG = "VoxaraSampler"
        const val SAMPLE_RATE = 48_000
        private const val WARMUP_MS = 120
        private const val FRAME_MS = 100
        /** dBFS -> dB SPL reference: full scale is treated as 94 dB + per-model offset. */
        private const val FULL_SCALE_SPL = 94.0

        /**
         * A wrist MEMS mic has a self-noise floor around 30 dBA; anything the arithmetic puts
         * below that is the electronics, not the room, so it is reported as the floor rather
         * than as a number the device cannot actually resolve.
         */
        const val NOISE_FLOOR_DBA = 30.0
    }

    /** One burst, already reduced. No PCM escapes this class. */
    data class Burst(
        val leqDba: Double,
        val lmaxDba: Double,
        val peakDbc: Double,
        val frames: DoubleArray,
    )

    private val filter = AWeightingFilter(SAMPLE_RATE)

    @SuppressLint("MissingPermission")
    fun capture(burstMs: Long, calibrationOffsetDb: Double): Burst? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuf <= 0) return null

        val source = if (preferUnprocessed) MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION

        val record = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, SAMPLE_RATE * 4 / 2))
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "AudioRecord unavailable", t)
            return null
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }

        val frameSamples = SAMPLE_RATE * FRAME_MS / 1000
        val warmupSamples = SAMPLE_RATE * WARMUP_MS / 1000
        val totalSamples = (SAMPLE_RATE * burstMs / 1000).toInt() + warmupSamples
        val buf = FloatArray(frameSamples)
        val levels = ArrayList<Double>()
        var rawPeak = 0f
        var consumed = 0

        filter.reset()
        try {
            record.startRecording()
            while (consumed < totalSamples) {
                val read = record.read(buf, 0, frameSamples, AudioRecord.READ_BLOCKING)
                if (read <= 0) break
                consumed += read

                // 02 - discard the warm-up transient entirely.
                if (consumed <= warmupSamples) continue

                val frame = if (read == frameSamples) buf.copyOf() else buf.copyOf(read)

                // The impulse check runs on the UNWEIGHTED peak (peak-hold path, not the Leq path).
                for (s in frame) { val a = abs(s); if (a > rawPeak) rawPeak = a }

                // 03 - A-weight, then RMS -> dBA.
                levels += filter.process(frame).frameDba(calibrationOffsetDb)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "burst failed", t)
        } finally {
            // 05 - release the mic immediately; the buffers go out of scope with the method.
            try { record.stop() } catch (_: Throwable) {}
            record.release()
        }

        if (levels.isEmpty()) return null
        val arr = levels.toDoubleArray()
        val peakDbc = 20.0 * log10(rawPeak.toDouble().coerceAtLeast(1e-7)) +
            FULL_SCALE_SPL + calibrationOffsetDb
        for (i in arr.indices) arr[i] = arr[i].coerceAtLeast(NOISE_FLOOR_DBA)
        return Burst(
            leqDba = energyAverage(arr).coerceAtLeast(NOISE_FLOOR_DBA),
            lmaxDba = arr.max(),
            peakDbc = peakDbc.coerceAtLeast(NOISE_FLOOR_DBA),
            frames = arr,
        )
    }
}
