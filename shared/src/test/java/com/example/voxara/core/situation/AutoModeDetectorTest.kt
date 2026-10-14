package com.example.voxara.core.situation

import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.HapticPrefs
import com.example.voxara.core.scene.SceneModel
import com.example.voxara.core.situation.AutoModeDetector.Context
import com.example.voxara.core.situation.AutoModeDetector.Vote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * AUTO DETECTION — stability (window, hysteresis, dwell), doubt and fallback, the level vetoes,
 * the Concert rule, the haptic gate and THE RULE: automatic detection can never relax an alert.
 * Votes are built by hand (the model has its own test), times come from a fake clock.
 */
class AutoModeDetectorTest {

    private val minute = 60_000L
    private val t0 = 1_750_000_000_000L

    /** Probabilities over SceneModel.MODES (QUIET, STREET, TRANSPORT, WORK). */
    private fun p(quiet: Double = 0.0, street: Double = 0.0, transport: Double = 0.0, work: Double = 0.0) =
        doubleArrayOf(quiet, street, transport, work)

    private fun leaning(mode: SituationMode, strength: Double = 0.8): DoubleArray {
        val rest = (1.0 - strength) / 3
        return DoubleArray(4) { if (SceneModel.MODES[it] == mode) strength else rest }
    }

    private fun vote(atMs: Long, probs: DoubleArray, dba: Double = 65.0, music: Boolean = false) =
        Vote(atMs, probs, dba, music)

    /** Feeds one vote every [everyMs] from [from] for [count] votes; returns the last decision. */
    private fun AutoModeDetector.feed(
        from: Long, count: Int, everyMs: Long, probs: DoubleArray, dba: Double = 65.0,
        music: Boolean = false, context: Context = Context(),
    ): AutoModeDetector.Decision {
        var d: AutoModeDetector.Decision? = null
        for (i in 0 until count) d = offer(vote(from + i * everyMs, probs, dba, music), context)
        return d!!
    }

    @Test
    fun `nothing is decided before the window has enough votes, then the hysteresis holds one more`() {
        val d = AutoModeDetector()
        // Idle cadence: one burst a minute.
        assertNull(d.offer(vote(t0, leaning(SituationMode.TRANSPORT))).detected)
        assertNull(d.offer(vote(t0 + minute, leaning(SituationMode.TRANSPORT))).detected)
        // Third vote: a candidate, but the hysteresis wants two evaluations in a row.
        assertNull(d.offer(vote(t0 + 2 * minute, leaning(SituationMode.TRANSPORT))).detected)
        val fourth = d.offer(vote(t0 + 3 * minute, leaning(SituationMode.TRANSPORT)))
        assertEquals(SituationMode.TRANSPORT, fourth.detected)
        assertTrue(fourth.changed)
        assertTrue(fourth.confidence > 0.5)
    }

    @Test
    fun `a single odd burst never flips the mode`() {
        val d = AutoModeDetector()
        d.feed(t0, 6, minute, leaning(SituationMode.WORK), dba = 58.0)
        assertEquals(SituationMode.WORK, d.detected)
        val odd = d.offer(vote(t0 + 6 * minute, leaning(SituationMode.TRANSPORT, 0.95), dba = 58.0))
        assertEquals(SituationMode.WORK, odd.detected)
        assertFalse(odd.changed)
        assertEquals(SituationMode.WORK, d.feed(t0 + 7 * minute, 3, minute, leaning(SituationMode.WORK), dba = 58.0).detected)
    }

    @Test
    fun `a new mode waits for the minimum dwell of the current one`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        // Quiet detected at the 4th vote (t0 + 3 min).
        d.feed(t0, 4, minute, leaning(SituationMode.QUIET), dba = 45.0)
        assertEquals(SituationMode.QUIET, d.detected)
        val quietSince = t0 + 3 * minute
        // The wearer walks out: strong Street votes every 10 s (the louder ladder step).
        var switchedAt: Long? = null
        var t = quietSince + 10_000
        while (t < quietSince + 15 * minute) {
            val r = d.offer(vote(t, leaning(SituationMode.STREET, 0.9), dba = 72.0))
            if (r.detected == SituationMode.STREET && switchedAt == null) switchedAt = t
            t += 10_000
        }
        assertTrue("Street must win eventually", switchedAt != null)
        assertTrue("left Quiet after ${(switchedAt!! - quietSince) / 1000} s", switchedAt - quietSince >= cfg.minDwellMs)
    }

    @Test
    fun `in doubt the current mode is kept, then Auto falls back to null (Street)`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        d.feed(t0, 6, minute, leaning(SituationMode.WORK), dba = 55.0)
        assertEquals(SituationMode.WORK, d.detected)
        // Ambiguous sound: no mode convincing, Work barely supported.
        val unsure = p(quiet = 0.3, street = 0.35, transport = 0.3, work = 0.05)
        val start = t0 + 6 * minute
        var fellBackAt: Long? = null
        for (i in 0 until 30) {
            val r = d.offer(vote(start + i * minute, unsure, dba = 60.0))
            if (r.detected == null && fellBackAt == null) {
                fellBackAt = start + i * minute
                assertTrue(r.changed)
            }
            if (fellBackAt == null) assertEquals("kept while in doubt", SituationMode.WORK, r.detected)
        }
        assertTrue(fellBackAt != null)
        // Doubt starts once the window has turned against Work; it needs doubtFallbackMs more.
        assertTrue(fellBackAt!! - start >= cfg.doubtFallbackMs)
        assertEquals(SituationMode.STREET, Situation.resolve(SituationChoice.AUTO, d.detected))
    }

    @Test
    fun `no vote for a long time forgets the detection`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        d.feed(t0, 5, minute, leaning(SituationMode.QUIET), dba = 42.0)
        val last = t0 + 4 * minute
        assertEquals(SituationMode.QUIET, d.tick(last + cfg.staleAfterMs).detected)
        val expired = d.tick(last + cfg.staleAfterMs + 1)
        assertNull(expired.detected)
        assertTrue(expired.changed)
    }

    @Test
    fun `the level vetoes what cannot be`() {
        // The spectrum says Quiet but the room is at 78 dBA: never Quiet.
        val loud = AutoModeDetector()
        val r = loud.feed(t0, 8, minute, p(quiet = 0.9, street = 0.06, transport = 0.04), dba = 78.0)
        assertNotEquals(SituationMode.QUIET, r.detected)
        // Work is ruled out above 80 dBA.
        val work = AutoModeDetector()
        assertNotEquals(SituationMode.WORK, work.feed(t0, 8, minute, leaning(SituationMode.WORK, 0.9), dba = 84.0).detected)
        // Below 38 dBA it is Quiet whatever the spectrum says (mostly microphone noise there).
        val hush = AutoModeDetector()
        assertEquals(SituationMode.QUIET, hush.feed(t0, 5, minute, leaning(SituationMode.TRANSPORT, 0.9), dba = 34.0).detected)
        // Transport is ruled out below 50 dBA.
        val calm = AutoModeDetector()
        assertNotEquals(SituationMode.TRANSPORT, calm.feed(t0, 8, minute, p(street = 0.3, transport = 0.7), dba = 46.0).detected)
    }

    @Test
    fun `headphones and the night are mild priors`() {
        val close = p(street = 0.55, transport = 0.45)
        assertEquals(SituationMode.STREET, AutoModeDetector().feed(t0, 5, minute, close, dba = 70.0).detected)
        assertEquals(
            SituationMode.TRANSPORT,
            AutoModeDetector().feed(t0, 5, minute, close, dba = 70.0, context = Context(headphonesPlaying = true)).detected,
        )
        val workish = p(quiet = 0.45, work = 0.55)
        assertEquals(SituationMode.WORK, AutoModeDetector().feed(t0, 5, minute, workish, dba = 50.0, context = Context(localMinuteOfDay = 11 * 60)).detected)
        assertEquals(SituationMode.QUIET, AutoModeDetector().feed(t0, 5, minute, workish, dba = 50.0, context = Context(localMinuteOfDay = 23 * 60)).detected)
        assertEquals(SituationMode.QUIET, AutoModeDetector().feed(t0, 5, minute, workish, dba = 50.0, context = Context(localMinuteOfDay = 3 * 60)).detected)
    }

    @Test
    fun `sustained loud music-like sound is a Concert, entered without waiting for the dwell`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        d.feed(t0, 5, minute, leaning(SituationMode.STREET), dba = 72.0)
        assertEquals(SituationMode.STREET, d.detected)
        // Doors open: 96 dBA, music-like, one vote per 10 s (the ladder's ACCRUING step or faster).
        val start = t0 + 5 * minute
        var enteredAt: Long? = null
        for (i in 0 until 30) {
            val r = d.offer(vote(start + i * 10_000, leaning(SituationMode.TRANSPORT, 0.6), dba = 96.0, music = true))
            if (r.detected == SituationMode.CONCERT && enteredAt == null) enteredAt = start + i * 10_000
        }
        assertTrue(enteredAt != null)
        assertTrue("needs ${cfg.concertMinSpanMs} ms of evidence", enteredAt!! - start >= cfg.concertMinSpanMs)
        assertTrue("but not the full dwell", enteredAt - start < cfg.concertMinSpanMs + cfg.minDwellMs)
    }

    @Test
    fun `loud but not music-like (a metro), or music in the wearer's own headphones, is not a Concert`() {
        val metro = AutoModeDetector()
        metro.feed(t0, 40, 10_000, leaning(SituationMode.TRANSPORT), dba = 92.0, music = false)
        assertEquals(SituationMode.TRANSPORT, metro.detected)
        val phones = AutoModeDetector()
        phones.feed(t0, 40, 10_000, leaning(SituationMode.TRANSPORT), dba = 92.0, music = true, context = Context(headphonesPlaying = true))
        assertNotEquals(SituationMode.CONCERT, phones.detected)
        val quietBand = AutoModeDetector()
        quietBand.feed(t0, 40, 10_000, leaning(SituationMode.STREET), dba = 84.0, music = true)
        assertNotEquals("below 90 dBA it is not a concert", SituationMode.CONCERT, quietBand.detected)
    }

    @Test
    fun `Concert ends only after 20 quiet minutes, like a manual Concert`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        d.feed(t0, 30, 10_000, leaning(SituationMode.STREET), dba = 98.0, music = true)
        assertEquals(SituationMode.CONCERT, d.detected)
        val end = t0 + 30 * 10_000
        // An interval: 15 minutes at 75 dBA, then loud again — still a Concert.
        d.feed(end, 15, minute, leaning(SituationMode.STREET), dba = 75.0)
        assertEquals(SituationMode.CONCERT, d.detected)
        d.feed(end + 15 * minute, 6, 10_000, leaning(SituationMode.STREET), dba = 97.0, music = true)
        assertEquals(SituationMode.CONCERT, d.detected)
        // Then the walk home: 20 minutes below 80 dBA.
        val home = end + 20 * minute
        var leftAt: Long? = null
        for (i in 0 until 25) {
            val r = d.offer(vote(home + i * minute, leaning(SituationMode.STREET), dba = 70.0))
            if (r.detected != SituationMode.CONCERT && leftAt == null) leftAt = home + i * minute
        }
        assertTrue(leftAt != null)
        assertTrue(leftAt!! - home >= cfg.concertExitQuietMs)
    }

    @Test
    fun `votes closer than the minimum gap are ignored (continuous capture)`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        assertTrue(d.wantsVote(t0))
        d.offer(vote(t0, leaning(SituationMode.WORK)))
        assertFalse(d.wantsVote(t0 + 1_000))
        // A burst of votes 1 s apart counts once per 5 s: 3 votes span 10 s, not 3 s.
        for (i in 1..9) d.offer(vote(t0 + i * 1_000, leaning(SituationMode.WORK)))
        assertNull("only two votes were taken", d.detected)
        assertTrue(d.wantsVote(t0 + cfg.minVoteGapMs * 2))
    }

    @Test
    fun `fixed choices ignore detection, and Auto without a detection is Street`() {
        for (choice in SituationChoice.entries - SituationChoice.AUTO) {
            for (detected in SituationMode.entries + listOf(null)) {
                assertEquals(choice.fixed, Situation.resolve(choice, detected))
            }
        }
        assertEquals(SituationMode.STREET, Situation.resolve(SituationChoice.AUTO, null))
        for (m in SituationMode.entries) assertEquals(m, Situation.resolve(SituationChoice.AUTO, m))
    }

    @Test
    fun `THE RULE - no automatic detection ever relaxes an alert below what the wearer set`() {
        val bases = listOf(AlertSettings(), AlertSettings(dailyCap = 1), AlertSettings(thresholdDba = 80, quietHoursEnabled = true))
        val prefs = listOf(HapticPrefs(), HapticPrefs(advisoryHaptics = false))
        val rnd = Random(42)
        val d = AutoModeDetector()
        var t = t0
        repeat(3_000) {
            // Random scenes, levels, music and headphones: whatever the detector concludes...
            val probs = DoubleArray(4) { rnd.nextDouble() }.let { a -> val s = a.sum(); DoubleArray(4) { a[it] / s } }
            val r = d.offer(
                vote(t, probs, dba = 30.0 + rnd.nextDouble() * 80.0, music = rnd.nextBoolean()),
                Context(headphonesPlaying = rnd.nextInt(5) == 0, localMinuteOfDay = rnd.nextInt(1440)),
            )
            t += 5_000L + rnd.nextLong(120_000L)
            for (choice in SituationChoice.entries) {
                val mode = Situation.resolve(choice, r.detected)
                // ...a fixed choice is exactly what the wearer chose...
                if (choice != SituationChoice.AUTO) assertEquals(choice.fixed, mode)
                for (base in bases) {
                    // ...and the settings in force never drop below the wearer's own.
                    val s = Situation.alertSettings(mode, base)
                    assertEquals(base.copy(dailyCap = s.dailyCap), s)
                    assertTrue(s.dailyCap >= base.dailyCap)
                }
                for (hp in prefs) for (kind in AlertKind.entries) {
                    val with = Situation.hapticPrefs(mode, hp)
                    assertTrue(AlertPolicy.repeats(kind, with) >= AlertPolicy.repeats(kind, hp))
                    assertEquals(hp.advisoryHaptics, with.advisoryHaptics)
                }
            }
        }
    }

    @Test
    fun `flapping sound gives few changes, and the haptic gate bounds the vibrations`() {
        val cfg = AutoModeDetector.Config()
        val d = AutoModeDetector(cfg)
        val gate = ModeChangeHapticGate()
        val rnd = Random(7)
        val serviceStart = t0
        var t = t0
        var changes = 0
        var lastChangeAt: Long? = null
        val played = ArrayList<Long>()
        var mode = SituationMode.STREET
        // 12 hours of a scene that flips every 1-3 minutes, one vote every 10 s.
        while (t < t0 + 12 * 60 * minute) {
            if (rnd.nextInt(12) == 0) mode = listOf(SituationMode.QUIET, SituationMode.STREET, SituationMode.TRANSPORT, SituationMode.WORK).random(rnd)
            val r = d.offer(vote(t, leaning(mode, 0.7), dba = 60.0))
            val resolvedBefore = r.changed
            if (resolvedBefore) {
                lastChangeAt?.let { assertTrue("changes ${t - it} ms apart", t - it >= cfg.minDwellMs || r.detected == null) }
                lastChangeAt = t
                changes++
                val ok = gate.tryPlay(
                    ModeChangeHapticGate.Context(
                        nowMs = t, localDay = 1L, serviceStartedAtMs = serviceStart, lastAlertHapticAtMs = null,
                        quietHours = false, powerSave = false, advisoryHaptics = true,
                    )
                )
                if (ok) played += t
            }
            t += 10_000
        }
        assertTrue("dwell bounds the changes: $changes", changes <= (12 * 60 * minute) / cfg.minDwellMs + 1)
        assertTrue("at most 6 mode-change haptics a day, got ${played.size}", played.size <= 6)
        played.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 30 * minute) }
    }

    @Test
    fun `licence gate - without the scene model Auto stays Street but Concert still works`() {
        val d = AutoModeDetector(AutoModeDetector.Config(sceneModel = false))
        // Strong, consistent model votes for an hour: nothing is detected (Auto = Street).
        d.feed(t0, 60, minute, leaning(SituationMode.WORK, 0.95), dba = 55.0)
        assertNull(d.detected)
        assertEquals(SituationMode.STREET, Situation.resolve(SituationChoice.AUTO, d.detected))
        // Even the "too quiet, so Quiet" veto is model-path only.
        val hush = AutoModeDetector(AutoModeDetector.Config(sceneModel = false))
        hush.feed(t0, 10, minute, leaning(SituationMode.QUIET), dba = 33.0)
        assertNull(hush.detected)
        // The Concert rule needs no model.
        val gig = AutoModeDetector(AutoModeDetector.Config(sceneModel = false))
        gig.feed(t0, 30, 10_000, leaning(SituationMode.STREET), dba = 97.0, music = true)
        assertEquals(SituationMode.CONCERT, gig.detected)
    }

    @Test
    fun `licence gate - the vote is built without calling the model`() {
        val gated = AutoModeDetector(AutoModeDetector.Config(sceneModel = false))
        val features = DoubleArray(com.example.voxara.core.scene.SceneFeatures.COUNT)
        features[com.example.voxara.core.scene.SceneFeatures.TONALITY] = -10.0
        val v = gated.voteOf(t0, listOf(features), 95.0)!!
        assertNull(v.classProbabilities)
        assertTrue(v.musicLike)
        assertTrue(v.probabilities.all { it == 0.25 })
        val open = AutoModeDetector().voteOf(t0, listOf(features), 95.0)!!
        assertEquals(SceneModel.classes.size, open.classProbabilities!!.size)
        assertEquals(1.0, open.probabilities.sum(), 1e-9)
    }

    @Test
    fun `each decision reports the vote after vetoes, and which vetoes applied`() {
        val d = AutoModeDetector()
        val r = d.offer(vote(t0, p(quiet = 0.6, street = 0.2, transport = 0.1, work = 0.1), dba = 85.0), Context(localMinuteOfDay = 23 * 60))
        assertEquals(setOf(AutoModeDetector.Veto.NO_QUIET, AutoModeDetector.Veto.NO_WORK, AutoModeDetector.Veto.NIGHT_PRIOR), r.vetoes)
        assertEquals(0.0, r.vote!![0], 0.0)
        assertEquals(1.0, r.vote!!.sum(), 1e-9)
        val hush = d.offer(vote(t0 + minute, p(street = 1.0), dba = 30.0))
        assertEquals(setOf(AutoModeDetector.Veto.ONLY_QUIET), hush.vetoes)
        assertNull("no vote taken by tick", d.tick(t0 + 2 * minute).vote)
    }

    @Test
    fun `reset forgets everything`() {
        val d = AutoModeDetector()
        d.feed(t0, 5, minute, leaning(SituationMode.WORK), dba = 55.0)
        assertEquals(SituationMode.WORK, d.detected)
        d.reset()
        assertNull(d.detected)
        assertTrue(d.wantsVote(t0 + 5 * minute))
        assertNull(d.offer(vote(t0 + 6 * minute, leaning(SituationMode.WORK)), Context()).detected)
    }
}
