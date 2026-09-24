package com.example.voxara.core

import com.example.voxara.audio.AWeightingFilter
import com.example.voxara.audio.rmsDbfs
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.CalibrationRules
import com.example.voxara.core.calibration.CaptureCheck
import com.example.voxara.core.calibration.Consistency
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.ProvisionalMicOffsets
import com.example.voxara.core.calibration.REFERENCE_DB
import com.example.voxara.core.calibration.ReferenceCheck
import com.example.voxara.core.calibration.ReferenceType
import com.example.voxara.core.calibration.appliesTo
import com.example.voxara.core.calibration.calibrationOffset
import com.example.voxara.core.calibration.cddDerivedOffset
import com.example.voxara.core.calibration.checkCapture
import com.example.voxara.core.calibration.checkReference
import com.example.voxara.core.calibration.compareOffsets
import com.example.voxara.core.calibration.snapReference
import com.example.voxara.core.monitoring.HEARTBEAT_STALE_MS
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.monitoring.monitoringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Guided calibration math, driven by synthetic signals rather than a real microphone. */
class GuidedCalibrationTest {

    private val rate = 48_000

    /** A 1 kHz sine: A-weighting is 0 dB there, so the weighted level equals the raw RMS. */
    private fun sine(amplitude: Double, seconds: Double, hz: Double = 1000.0) =
        FloatArray((rate * seconds).toInt()) { i -> (amplitude * sin(2 * PI * hz * i / rate)).toFloat() }

    /** Per-100 ms A-weighted dBFS levels, the way the sampler produces them. */
    private fun frameLevels(signal: FloatArray): List<Double> {
        val filter = AWeightingFilter(rate)
        val frame = rate / 10
        val out = ArrayList<Double>()
        var i = 0
        while (i + frame <= signal.size) {
            val f = signal.copyOfRange(i, i + frame)
            val level = filter.process(f).rmsDbfs()
            if (i >= frame * 2) out += level      // skip the filter's start-up transient
            i += frame
        }
        return out
    }

    @Test
    fun `provisional offsets stay within half a dB of the CDD derivation`() {
        assertEquals(cddDerivedOffset(MicSource.UNPROCESSED), ProvisionalMicOffsets.UNPROCESSED_DB, 0.5)
        assertEquals(cddDerivedOffset(MicSource.VOICE_RECOGNITION), ProvisionalMicOffsets.VOICE_RECOGNITION_DB, 0.5)
        assertEquals(ProvisionalMicOffsets.UNPROCESSED_DB, MicSource.UNPROCESSED.provisionalOffsetDb, 0.0)
    }

    @Test
    fun `A-weighted level of a 1 kHz sine equals its RMS in dBFS`() {
        val amplitude = 0.02
        val expected = 20 * log10(amplitude / sqrt(2.0))
        frameLevels(sine(amplitude, 1.0)).forEach { assertEquals(expected, it, 0.1) }
    }

    @Test
    fun `offset from a synthetic capture reproduces the reference`() {
        // A steady tone the "reference meter" reads as 72.5 dBA.
        val levels = frameLevels(sine(0.01, 10.0))
        val capture = checkCapture(levels) as CaptureCheck.Steady
        val offset = calibrationOffset(72.5, capture.leqDbfsA)!!
        assertEquals(72.5, capture.leqDbfsA + REFERENCE_DB + offset, 1e-9)
        // -39 dBFS at 72.5 dBA -> offset ~ +17.5 dB.
        assertEquals(72.5 - (20 * log10(0.01 / sqrt(2.0)) + REFERENCE_DB), offset, 0.1)
    }

    @Test
    fun `two synthetic levels 10 dB apart give consistent offsets`() {
        val quiet = checkCapture(frameLevels(sine(0.01, 10.0))) as CaptureCheck.Steady
        val loud = checkCapture(frameLevels(sine(0.0316, 10.0))) as CaptureCheck.Steady
        val o1 = calibrationOffset(65.0, quiet.leqDbfsA)!!
        val o2 = calibrationOffset(75.0, loud.leqDbfsA)!!
        val c = compareOffsets(o1, o2)
        assertTrue(c is Consistency.Consistent)
        assertEquals(0.0, (c as Consistency.Consistent).differenceDb, 0.2)
    }

    @Test
    fun `a fluctuating sound is rejected as unsteady`() {
        // Alternate loud and quiet seconds: 20 dB swings.
        val signal = FloatArray(rate * 10) { i ->
            val loud = (i / rate) % 2 == 0
            ((if (loud) 0.1 else 0.01) * sin(2 * PI * 1000.0 * i / rate)).toFloat()
        }
        assertTrue(checkCapture(frameLevels(signal)) is CaptureCheck.Unsteady)
    }

    @Test
    fun `steady noise with small jitter passes`() {
        val rnd = Random(7)
        val levels = List(40) { -40.0 + (rnd.nextDouble() - 0.5) }
        assertTrue(checkCapture(levels) is CaptureCheck.Steady)
    }

    @Test
    fun `too few readings means the mic dropped out`() {
        assertTrue(checkCapture(List(5) { -40.0 }) is CaptureCheck.NotEnoughData)
    }

    @Test
    fun `reference bounds protect meaning and ears`() {
        assertEquals(ReferenceCheck.TooQuiet, checkReference(49.5))
        assertEquals(ReferenceCheck.Ok, checkReference(50.0))
        assertEquals(ReferenceCheck.Ok, checkReference(85.0))
        assertEquals(ReferenceCheck.LouderThanAdvised, checkReference(87.0))
        assertEquals(ReferenceCheck.TooLoud, checkReference(90.5))
    }

    @Test
    fun `offsets beyond a real microphone are refused, real ones fit`() {
        assertNull(calibrationOffset(70.0, -200.0))
        // A +40 dB offset (a very insensitive path) must be storable.
        assertEquals(40.0, calibrationOffset(70.0, 70.0 - REFERENCE_DB - 40.0)!!, 1e-9)
        assertEquals(-40.0, calibrationOffset(70.0, 70.0 - REFERENCE_DB + 40.0)!!, 1e-9)
    }

    @Test
    fun `offsets more than 3 dB apart are flagged`() {
        val c = compareOffsets(18.0, 21.5)
        assertTrue(c is Consistency.Inconsistent)
        assertEquals(19.75, (c as Consistency.Inconsistent).averageDb, 1e-9)
        assertTrue(compareOffsets(18.0, 21.0) is Consistency.Consistent)
    }

    @Test
    fun `the crown moves in half-dB steps`() {
        assertEquals(72.5, snapReference(72.4), 1e-9)
        assertEquals(72.0, snapReference(72.2), 1e-9)
        assertEquals(0.5, CalibrationRules.REFERENCE_STEP_DB, 0.0)
    }

    @Test
    fun `a calibration only applies to the watch it was made on`() {
        val r = CalibrationRecord(
            MicSource.UNPROCESSED, 33.0, 1L, "samsung SM-L310", ReferenceType.SOUND_LEVEL_METER,
        )
        assertTrue(r.appliesTo("samsung SM-L310"))
        assertFalse(r.appliesTo("Google Pixel Watch 3"))
        assertFalse((null as CalibrationRecord?).appliesTo("samsung SM-L310"))
    }

    // ------------------------------------------------------------------ paused state

    @Test
    fun `monitoring status distinguishes off, running and paused`() {
        val now = 10_000_000L
        assertEquals(MonitoringStatus.OFF, monitoringStatus(false, false, 0L, now))
        assertEquals(MonitoringStatus.RUNNING, monitoringStatus(true, true, 0L, now))
        assertEquals(MonitoringStatus.RUNNING, monitoringStatus(true, false, now - 60_000, now))
        assertEquals(MonitoringStatus.PAUSED, monitoringStatus(true, false, now - HEARTBEAT_STALE_MS - 1, now))
        assertEquals(MonitoringStatus.PAUSED, monitoringStatus(true, false, 0L, now))
    }
}
