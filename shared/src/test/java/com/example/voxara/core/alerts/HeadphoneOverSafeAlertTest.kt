package com.example.voxara.core.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "ABOVE THE RECOMMENDED LEVEL" — the alert that fires when the estimated listening level passes 80 dBA, and
 * what Dismiss does (nothing permanent: it can never switch the alert off).
 */
class HeadphoneOverSafeAlertTest {

    private val t0 = 1_750_000_000_000L
    private val min = 60_000L
    private val settings = AlertSettings()

    private fun at(t: Long, hp: Double?, memory: AlertMemory, day: Long = 20_000L, minuteOfDay: Int = 12 * 60) =
        AlertEngine.evaluate(
            AlertInputs(
                nowMs = t, localDay = day, localMinuteOfDay = minuteOfDay,
                sustainedAmbientDba = null, dailyDosePercent = 0.0, weeklyAmbientPercent = 0.0,
                headphoneNowDba = hp,
            ),
            settings, memory,
        )

    private fun List<Alert>.over() = filter { it.kind == AlertKind.HEADPHONE_OVER_SAFE }

    @Test
    fun `at or below 80 dBA nothing fires, just above it does, with tier 1`() {
        var m = AlertMemory()
        assertTrue(at(t0, 80.0, m).first.over().isEmpty())
        val (alerts, next) = at(t0 + 1, 82.0, m)
        assertEquals(1, alerts.over().size)
        assertEquals(1, alerts.over()[0].level)
        assertEquals(Tier.WARNING, alerts.over()[0].kind.tier)
        m = next
        assertEquals(1, m.hpSafeTier)
    }

    @Test
    fun `the same tier does not repeat, then repeats after the cooldown`() {
        var m = at(t0, 85.0, AlertMemory()).second
        assertTrue(at(t0 + 5 * min, 86.0, m).first.over().isEmpty())
        assertTrue(at(t0 + 29 * min, 86.0, m).first.over().isEmpty())
        val again = at(t0 + AlertDefaults.COOLDOWN_MS, 86.0, m)
        assertEquals(1, again.first.over().size)
    }

    @Test
    fun `a higher tier alerts again at once whatever the cooldown says`() {
        var m = at(t0, 85.0, AlertMemory()).second                    // tier 1
        val up = at(t0 + 10_000, 93.0, m)                              // tier 2, 10 s later
        assertEquals(listOf(2), up.first.over().map { it.level })
        m = up.second
        val top = at(t0 + 20_000, 101.0, m)                            // tier 3
        assertEquals(listOf(3), top.first.over().map { it.level })
    }

    @Test
    fun `dismissing is not muting - the engine keeps no mute, so a new tier or the cooldown alerts again`() {
        // Dismiss is an acknowledge: it writes nothing into the alert memory.
        val fired = at(t0, 85.0, AlertMemory()).second
        val afterDismiss = fired                                        // what ACK leaves behind
        assertEquals(fired, afterDismiss)
        assertEquals(listOf(2), at(t0 + 30_000, 91.0, afterDismiss).first.over().map { it.level })
        assertEquals(1, at(t0 + 31 * min, 85.0, afterDismiss).first.over().size)
    }

    @Test
    fun `dropping to 75 or below starts a new episode that alerts at once`() {
        var m = at(t0, 85.0, AlertMemory()).second
        m = at(t0 + 2 * min, 70.0, m).second                           // lowered: episode over
        assertEquals(0, m.hpSafeTier)
        assertEquals(1, at(t0 + 3 * min, 84.0, m).first.over().size)   // up again, no 30 min wait
    }

    @Test
    fun `lowering a little but staying above 80 does not re-alert`() {
        var m = at(t0, 95.0, AlertMemory()).second                     // tier 2
        assertTrue(at(t0 + min, 84.0, m).first.over().isEmpty())
    }

    @Test
    fun `not playing is not an alert`() {
        assertTrue(at(t0, null, AlertMemory()).first.over().isEmpty())
    }

    @Test
    fun `quiet hours hold it and a snooze holds it, but a limit is never held`() {
        val quiet = AlertSettings(quietHoursEnabled = true, quietStartMin = 22 * 60, quietEndMin = 7 * 60)
        val held = AlertEngine.evaluate(
            AlertInputs(t0, 20_000L, 23 * 60, null, 0.0, 0.0, headphoneNowDba = 90.0), quiet, AlertMemory(),
        )
        assertTrue(held.first.over().isEmpty())
        val snoozed = AlertEngine.snooze(AlertMemory(), AlertKind.HEADPHONE_OVER_SAFE, t0)
        assertTrue(at(t0 + 1, 90.0, snoozed).first.over().isEmpty())
    }

    @Test
    fun `the memory survives its codec`() {
        val m = at(t0, 92.0, AlertMemory()).second
        assertEquals(m, AlertMemoryCodec.decode(AlertMemoryCodec.encode(m)))
        // An older memory without the new fields decodes with defaults.
        assertEquals(0, AlertMemoryCodec.decode("day=5;adv=1").hpSafeTier)
    }

    @Test
    fun `it always vibrates - warnings cannot be turned off`() {
        val off = HapticPrefs(advisoryHaptics = false)
        assertTrue(AlertPolicy.vibrates(AlertKind.HEADPHONE_OVER_SAFE, off))
        assertEquals(1, AlertPolicy.repeats(AlertKind.HEADPHONE_OVER_SAFE, off))
        assertEquals(2, AlertPolicy.repeats(AlertKind.HEADPHONE_OVER_SAFE, HapticPrefs(strength = HapticStrength.STRONG)))
    }
}
