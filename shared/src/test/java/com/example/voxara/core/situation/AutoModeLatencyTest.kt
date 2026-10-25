package com.example.voxara.core.situation

import com.example.voxara.core.scene.SceneModel
import com.example.voxara.core.situation.AutoModeDetector.Vote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * HOW LONG AUTO TAKES TO NOTICE A CHANGE OF SITUATION, measured on the real detector.
 *
 * A vote arrives once per burst, so the cadence is the sampling ladder's: 60 s when the room is under
 * 65 dBA (IDLE), 30 s at 65-79 (AMBIENT), 10 s at 80-94 (ACCRUING), at most every 5 s above that.
 * Each scenario settles on one mode for ten minutes, then the evidence turns to another (a clear
 * 0.8 lean, which is what the model gives for an unambiguous scene). The reported time is from the
 * first vote of the new scene to [AutoModeDetector.detected] changing. Flicker is checked too: a
 * short blip of the other scene must never change the mode.
 */
class AutoModeLatencyTest {

    private val t0 = 1_750_000_000_000L

    private fun leaning(mode: SituationMode, strength: Double = 0.8): DoubleArray {
        val rest = (1.0 - strength) / 3
        return DoubleArray(4) { if (SceneModel.MODES[it] == mode) strength else rest }
    }

    /** Cadence by ladder step, with a level that belongs to it. */
    private data class Step(val name: String, val everyMs: Long, val dba: Double)

    private val steps = listOf(
        Step("IDLE (60 s)", 60_000L, 58.0),
        Step("AMBIENT (30 s)", 30_000L, 68.0),
        Step("ACCRUING (10 s)", 10_000L, 82.0),
    )

    /** Seconds from the scene change to the new mode, or null if it never came within [capMs]. */
    private fun secondsToSwitch(step: Step, from: SituationMode, to: SituationMode, capMs: Long = 30L * 60_000): Double? {
        val d = AutoModeDetector()
        var t = t0
        val settle = 10L * 60_000
        while (t < t0 + settle) { d.offer(Vote(t, leaning(from), step.dba, false)); t += step.everyMs }
        check(d.detected == from) { "${step.name}: did not settle on $from (got ${d.detected})" }
        val change = t
        while (t < change + capMs) {
            if (d.offer(Vote(t, leaning(to), step.dba, false)).detected == to) return (t - change) / 1000.0
            t += step.everyMs
        }
        return null
    }

    /** As [secondsToSwitch], but the loop takes the detector's fast votes (the service does exactly this). */
    private fun secondsToSwitchFast(step: Step, from: SituationMode, to: SituationMode, capMs: Long = 30L * 60_000): Double? {
        val d = AutoModeDetector()
        var t = t0
        fun next(): Long = minOf(step.everyMs, d.fastVotePeriodMs(t) ?: step.everyMs)
        while (t < t0 + 10L * 60_000) { d.offer(Vote(t, leaning(from), step.dba, false)); t += next() }
        check(d.detected == from) { "${step.name}: did not settle on $from (got ${d.detected})" }
        val change = t
        while (t < change + capMs) {
            if (d.offer(Vote(t, leaning(to), step.dba, false)).detected == to) return (t - change) / 1000.0
            t += next()
        }
        return null
    }

    private fun secondsToFirstFast(step: Step, mode: SituationMode, capMs: Long = 30L * 60_000): Double? {
        val d = AutoModeDetector()
        var t = t0
        while (t < t0 + capMs) {
            if (d.offer(Vote(t, leaning(mode), step.dba, false)).detected == mode) return (t - t0) / 1000.0
            t += minOf(step.everyMs, d.fastVotePeriodMs(t) ?: step.everyMs)
        }
        return null
    }

    /** Seconds from the first vote on a cold start to a first detection. */
    private fun secondsToFirst(step: Step, mode: SituationMode, capMs: Long = 30L * 60_000): Double? {
        val d = AutoModeDetector()
        var t = t0
        while (t < t0 + capMs) {
            if (d.offer(Vote(t, leaning(mode), step.dba, false)).detected == mode) return (t - t0) / 1000.0
            t += step.everyMs
        }
        return null
    }

    @Test
    fun `report how long a change takes at each cadence`() {
        val lines = ArrayList<String>()
        for (s in steps) {
            val first = secondsToFirst(s, SituationMode.TRANSPORT)
            val change = secondsToSwitch(s, SituationMode.STREET, SituationMode.TRANSPORT)
            lines += "LATENCY ${s.name}: cold start -> mode ${first ?: "never"} s; Street -> Transport ${change ?: "never"} s"
            lines += "LATENCY ${s.name} WITH FAST VOTES: cold start -> ${secondsToFirstFast(s, SituationMode.TRANSPORT) ?: "never"} s; " +
                "Street -> Transport ${secondsToSwitchFast(s, SituationMode.STREET, SituationMode.TRANSPORT) ?: "never"} s"
        }
        lines.forEach(::println)
        // Auto must always get there, whatever the cadence.
        steps.forEach {
            assertNotNull(secondsToFirst(it, SituationMode.TRANSPORT))
            assertNotNull(secondsToSwitch(it, SituationMode.STREET, SituationMode.TRANSPORT))
        }
    }

    @Test
    fun `a short blip of another scene never changes the mode`() {
        for (s in steps) {
            val d = AutoModeDetector()
            var t = t0
            while (t < t0 + 10L * 60_000) { d.offer(Vote(t, leaning(SituationMode.STREET), s.dba, false)); t += s.everyMs }
            assertEquals(SituationMode.STREET, d.detected)
            // 45 s of something else, then back.
            val blipEnd = t + 45_000L
            while (t < blipEnd) { d.offer(Vote(t, leaning(SituationMode.TRANSPORT), s.dba, false)); t += s.everyMs }
            val back = t + 10L * 60_000
            while (t < back) {
                val after = d.offer(Vote(t, leaning(SituationMode.STREET), s.dba, false))
                assertEquals("${s.name}: flickered at ${(t - t0) / 1000} s", SituationMode.STREET, after.detected)
                t += s.everyMs
            }
        }
    }

    @Test
    fun `fast votes do not add flicker - a blip or alternating evidence still changes nothing`() {
        for (s in steps) {
            val d = AutoModeDetector()
            var t = t0
            fun next(): Long = minOf(s.everyMs, d.fastVotePeriodMs(t) ?: s.everyMs)
            while (t < t0 + 10L * 60_000) { d.offer(Vote(t, leaning(SituationMode.STREET), s.dba, false)); t += next() }
            assertEquals(SituationMode.STREET, d.detected)
            val blipEnd = t + 45_000L
            while (t < blipEnd) { d.offer(Vote(t, leaning(SituationMode.TRANSPORT), s.dba, false)); t += next() }
            val back = t + 10L * 60_000
            while (t < back) {
                val after = d.offer(Vote(t, leaning(SituationMode.STREET), s.dba, false))
                assertEquals("${s.name}: flickered at ${(t - t0) / 1000} s", SituationMode.STREET, after.detected)
                t += next()
            }
            // Alternating evidence over 20 minutes: at most the first settle, never a back-and-forth.
            val alt = AutoModeDetector()
            var changes = 0
            var flip = false
            t = t0
            while (t < t0 + 20L * 60_000) {
                if (alt.offer(Vote(t, leaning(if (flip) SituationMode.STREET else SituationMode.TRANSPORT, 0.6), s.dba, false)).changed) changes++
                flip = !flip
                t += minOf(s.everyMs, alt.fastVotePeriodMs(t) ?: s.everyMs)
            }
            assert(changes <= 2) { "${s.name}: changed $changes times with fast votes" }
        }
    }

    @Test
    fun `the fast votes are a small budget, not a mode`() {
        val d = AutoModeDetector()
        var t = t0
        var fast = 0
        // A scene that never settles asks for fast votes, but only from the budget, then falls back to the ladder.
        repeat(200) {
            d.offer(Vote(t, leaning(if (it % 2 == 0) SituationMode.STREET else SituationMode.TRANSPORT, 0.55), 66.0, false))
            val f = d.fastVotePeriodMs(t)
            if (f != null) fast++
            t += f ?: 60_000L
        }
        assert(fast < 60) { "asked for $fast fast votes in 200 steps" }
    }

    @Test
    fun `alternating evidence never settles on a mode`() {
        for (s in steps) {
            val d = AutoModeDetector()
            var changes = 0
            var t = t0
            var flip = false
            while (t < t0 + 20L * 60_000) {
                if (d.offer(Vote(t, leaning(if (flip) SituationMode.STREET else SituationMode.TRANSPORT, 0.6), s.dba, false)).changed) changes++
                flip = !flip
                t += s.everyMs
            }
            assert(changes <= 2) { "${s.name}: changed $changes times on alternating evidence" }
        }
        assertNull(AutoModeDetector().detected)
    }
}
