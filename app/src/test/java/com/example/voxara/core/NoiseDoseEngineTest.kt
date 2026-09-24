package com.example.voxara.core

import com.example.voxara.core.dose.NoiseDoseEngine
import com.example.voxara.core.dose.SampleState
import com.example.voxara.core.dose.energyAverage
import com.example.voxara.core.dose.permittedHours
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.risk.RiskStateMachine
import com.example.voxara.core.scene.Scene
import com.example.voxara.core.scene.SceneHysteresis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The domain core is pure Kotlin, so it is tested against the published NIOSH duration table
 * without an emulator.
 */
class NoiseDoseEngineTest {

    /** NIOSH Pub. 98-126, Table 1-1. */
    @Test
    fun `permitted durations match the NIOSH table`() {
        assertEquals(8.0, permittedHours(85.0), 1e-9)
        assertEquals(4.0, permittedHours(88.0), 1e-9)
        assertEquals(2.0, permittedHours(91.0), 1e-9)
        assertEquals(1.0, permittedHours(94.0), 1e-9)
        assertEquals(0.5, permittedHours(97.0), 1e-9)     // 30 min
        assertEquals(0.125, permittedHours(103.0), 1e-9)  // 7 min 30 s
        assertEquals(28.125, permittedHours(115.0) * 3600.0, 1e-6) // 28 s
    }

    @Test
    fun `eight hours at the REL is exactly one hundred percent`() {
        val e = NoiseDoseEngine()
        e.accumulate(85.0, 8.0 * 3600.0)
        assertEquals(100.0, e.dosePercent, 1e-6)
        assertEquals(85.0, e.twaDba()!!, 1e-6)
    }

    @Test
    fun `three dB more halves the permitted time`() {
        val e = NoiseDoseEngine()
        e.accumulate(88.0, 4.0 * 3600.0)
        assertEquals(100.0, e.dosePercent, 1e-6)
    }

    @Test
    fun `nothing accrues below the eighty dBA threshold`() {
        val e = NoiseDoseEngine()
        e.accumulate(79.9, 24.0 * 3600.0)
        assertEquals(0.0, e.dosePercent, 1e-9)
        assertNull(e.twaDba())
        assertTrue("the WHO ledger still counts quiet hours", e.weeklyPa2h > 0.0)
    }

    @Test
    fun `TWA is the inverse of the dose equation`() {
        val e = NoiseDoseEngine()
        e.accumulate(85.0, 4.0 * 3600.0) // half a day's allowance
        assertEquals(50.0, e.dosePercent, 1e-6)
        assertEquals(81.99, e.twaDba()!!, 0.01) // 10*log10(0.5) + 85
    }

    @Test
    fun `headroom shrinks as the dose is spent`() {
        val e = NoiseDoseEngine()
        e.accumulate(85.0, 4.0 * 3600.0)
        assertEquals(4.0 * 3600.0, e.headroomSeconds(85.0), 1e-6)
        assertTrue(e.headroomSeconds(79.0).isInfinite())
    }

    @Test
    fun `the WHO weekly budget is spent by forty hours at eighty dB`() {
        val e = NoiseDoseEngine()
        e.accumulate(80.0, 40.0 * 3600.0)
        assertEquals(1.0, e.weeklyFraction(), 0.01)
    }

    @Test
    fun `the impulse ceiling latches once`() {
        val e = NoiseDoseEngine()
        assertTrue(e.notePeak(141.0))
        assertTrue(!e.notePeak(150.0))
    }

    @Test
    fun `energy averaging is not arithmetic averaging`() {
        // 80 and 90 dBA average to 87.4, not 85.
        assertEquals(87.4, energyAverage(doubleArrayOf(80.0, 90.0)), 0.05)
    }

    @Test
    fun `the sampling ladder escalates on level`() {
        assertEquals(SampleState.IDLE, SampleState.of(60.0))
        assertEquals(SampleState.AMBIENT, SampleState.of(70.0))
        assertEquals(SampleState.ACCRUING, SampleState.of(85.0))
        assertEquals(SampleState.HAZARD, SampleState.of(96.0))
        assertEquals(0.20, SampleState.ACCRUING.dutyCycle, 1e-9)
    }

    @Test
    fun `risk states follow the ring spec`() {
        assertEquals(RiskState.CALM, RiskState.of(60.0, 0.0))
        assertEquals(RiskState.ACCRUING, RiskState.of(82.0, 0.0))
        assertEquals(RiskState.HAZARD, RiskState.of(96.0, 0.0))
        assertEquals(RiskState.CRITICAL, RiskState.of(70.0, 100.0))
    }

    // The daily alert cap moved to AlertEngine (Phase 1): see AlertEngineTest.

    @Test
    fun `relief follows a loud stretch, with a cooldown`() {
        val m = RiskStateMachine(calmCooldownMs = 10_000L)
        assertNull(m.update(90.0, 0.0, 0L))
        assertEquals(RiskStateMachine.Event.BackToSafe, m.update(70.0, 0.0, 1_000L))
        assertNull(m.update(90.0, 0.0, 2_000L))
        assertNull("within the cooldown", m.update(70.0, 0.0, 3_000L))
    }

    @Test
    fun `a scene must win three windows before it changes the UI`() {
        val h = SceneHysteresis()
        assertEquals(Scene.UNKNOWN, h.offer(Scene.LIVE_MUSIC, 0.9f))
        assertEquals(Scene.UNKNOWN, h.offer(Scene.LIVE_MUSIC, 0.9f))
        assertEquals(Scene.LIVE_MUSIC, h.offer(Scene.LIVE_MUSIC, 0.9f))
    }

    @Test
    fun `low confidence never changes the label`() {
        val h = SceneHysteresis()
        repeat(10) { h.offer(Scene.MACHINERY, 0.4f) }
        assertEquals(Scene.UNKNOWN, h.current)
    }
}
