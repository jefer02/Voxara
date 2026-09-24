package com.example.voxara.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.REFERENCE_DB
import com.example.voxara.core.calibration.dbfsOfRms
import com.example.voxara.core.dose.energyAverage
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * BURST PIPELINE - 48 kHz MONO / FLOAT
 *
 *  01  AudioRecord opens with UNPROCESSED when the device reports support for it, otherwise
 *      VOICE_RECOGNITION; AGC, noise suppression and echo cancellation are switched off where the
 *      device exposes them.
 *  02  Drop the first 120 ms after opening (mic warm-up transient), then window into 100 ms frames.
 *  03  Per frame: A-weighting IIR biquad cascade -> RMS -> dBFS -> dBA via the source's offset.
 *  04  Energy-average frames into one Leq per burst; keep the max frame as Lmax and the raw
 *      peak for the impulse check.
 *  05  Release the mic (or keep the session open in continuous mode). PCM never leaves the
 *      process, never touches disk, never leaves the watch.
 */
class AudioBurstSampler(context: Context) {

    companion object {
        private const val TAG = "VoxaraSampler"
        const val SAMPLE_RATE = 48_000
        private const val WARMUP_MS = 120
        private const val FRAME_MS = 100

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
        val source: MicSource,
        /** A-weighted level before any offset — what the calibration readout compares. */
        val dbfsA: Double,
        /** Unweighted level before any offset. */
        val dbfsZ: Double,
    )

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** UNPROCESSED only when the device says it implements it; the CDD makes it optional. */
    fun preferredSource(): MicSource {
        val supported = runCatching {
            audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        }.getOrNull()
        return if (supported == "true") MicSource.UNPROCESSED else MicSource.VOICE_RECOGNITION
    }

    /** One self-contained burst: open, warm up, measure, release. */
    fun capture(burstMs: Long, offsetFor: (MicSource) -> Double, source: MicSource? = null): Burst? =
        open(source)?.use { it.read(burstMs, offsetFor(it.source)) }

    /**
     * Opens a session on [requested] (or the preferred source), falling back to
     * VOICE_RECOGNITION when UNPROCESSED cannot be built. Null when the mic is unavailable.
     */
    fun open(requested: MicSource? = null): Session? {
        val first = requested ?: preferredSource()
        return build(first)?.let { Session(it, first) }
            ?: if (first != MicSource.VOICE_RECOGNITION && requested == null) {
                build(MicSource.VOICE_RECOGNITION)?.let { Session(it, MicSource.VOICE_RECOGNITION) }
            } else null
    }

    @SuppressLint("MissingPermission")
    private fun build(source: MicSource): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuf <= 0) return null
        val platformSource = when (source) {
            MicSource.UNPROCESSED -> MediaRecorder.AudioSource.UNPROCESSED
            MicSource.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(platformSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_RATE * 4 / 2))
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "AudioRecord unavailable for $source", t)
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    /**
     * An open capture. In continuous mode the loop keeps one of these alive instead of paying
     * setup and warm-up four times a second.
     */
    inner class Session internal constructor(
        private val record: AudioRecord,
        val source: MicSource,
    ) : Closeable {

        private val filter = AWeightingFilter(SAMPLE_RATE)
        private val effects: List<AudioEffect> = disableProcessing(record.audioSessionId)
        private var warmedUp = false
        private var started = false

        /** Reads [burstMs] of audio. Null when nothing usable arrived or the client was silenced. */
        fun read(burstMs: Long, offsetDb: Double): Burst? {
            val frameSamples = SAMPLE_RATE * FRAME_MS / 1000
            val warmupSamples = if (warmedUp) 0 else SAMPLE_RATE * WARMUP_MS / 1000
            val totalSamples = (SAMPLE_RATE * burstMs / 1000).toInt() + warmupSamples
            val buf = FloatArray(frameSamples)
            val frameDbfs = ArrayList<Double>()
            var rawPeak = 0f
            var rawSumSq = 0.0
            var rawCount = 0L
            var consumed = 0

            try {
                if (!started) {
                    filter.reset()
                    record.startRecording()
                    started = true
                }
                while (consumed < totalSamples) {
                    val read = record.read(buf, 0, frameSamples, AudioRecord.READ_BLOCKING)
                    if (read <= 0) break
                    consumed += read

                    // 02 - discard the warm-up transient entirely.
                    if (consumed <= warmupSamples) continue

                    val frame = if (read == frameSamples) buf.copyOf() else buf.copyOf(read)

                    // The impulse check and the Z readout run on the UNWEIGHTED signal.
                    for (s in frame) {
                        val a = abs(s)
                        if (a > rawPeak) rawPeak = a
                        rawSumSq += (s * s).toDouble()
                    }
                    rawCount += frame.size

                    // 03 - A-weight, then RMS -> dBFS.
                    frameDbfs += filter.process(frame).rmsDbfs()
                }
                warmedUp = true
            } catch (t: Throwable) {
                Log.w(TAG, "burst failed", t)
                return null
            }

            // Another client holds the mic (e.g. a voice turn): Android hands us zeros. Report
            // "unmeasured" rather than a silent room.
            if (runCatching { record.activeRecordingConfiguration?.isClientSilenced }
                    .getOrNull() == true
            ) return null
            if (frameDbfs.isEmpty() || rawCount == 0L) return null

            val dbfsA = energyAverage(frameDbfs.toDoubleArray())
            val dbfsZ = dbfsOfRms(sqrt(rawSumSq / rawCount))
            val frames = DoubleArray(frameDbfs.size) {
                (frameDbfs[it] + REFERENCE_DB + offsetDb).coerceAtLeast(NOISE_FLOOR_DBA)
            }
            val peak = dbfsOfRms(rawPeak.toDouble()) + REFERENCE_DB + offsetDb
            return Burst(
                leqDba = (dbfsA + REFERENCE_DB + offsetDb).coerceAtLeast(NOISE_FLOOR_DBA),
                lmaxDba = frames.max(),
                peakDbc = peak.coerceAtLeast(NOISE_FLOOR_DBA),
                frames = frames,
                source = source,
                dbfsA = dbfsA,
                dbfsZ = dbfsZ,
            )
        }

        override fun close() {
            // 05 - release the mic immediately; the buffers go out of scope with the call.
            runCatching { if (started) record.stop() }
            record.release()
            effects.forEach { runCatching { it.release() } }
        }
    }

    /** The level has to track the room, not a gain stage trying to normalise it. */
    private fun disableProcessing(sessionId: Int): List<AudioEffect> = buildList {
        if (AutomaticGainControl.isAvailable()) {
            runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()?.let { add(it) }
        }
        if (NoiseSuppressor.isAvailable()) {
            runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()?.let { add(it) }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()?.let { add(it) }
        }
    }.onEach { runCatching { it.enabled = false } }
}

/** RMS of a frame in dBFS (1.0 = full scale). */
internal fun FloatArray.rmsDbfs(): Double {
    if (isEmpty()) return dbfsOfRms(0.0)
    var sum = 0.0
    for (s in this) sum += (s * s).toDouble()
    return dbfsOfRms(sqrt(sum / size))
}
