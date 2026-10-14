package com.example.voxara.core.situation

import com.example.voxara.core.alerts.Alert
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.HapticPrefs
import com.example.voxara.core.alerts.HapticStrength
import com.example.voxara.core.alerts.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE GUARANTEE: a situation mode never lowers or delays a hearing alert. The same day of inputs is
 * run through the real AlertEngine under all five modes. Every mode but Concert gives exactly the
 * same alerts, at the same moments, as Street. Concert may only ADD advisories (its lifted cap);
 * everything Street fires, Concert fires at the same step.
 */
class ModesNeverWeakenAlertsTest {

    private val dayStart = 1_750_000_000_000L
    private val step = 5 * 60_000L

    /** A loud day, one step every 5 minutes from 06:00: levels swing, doses climb past several limits. */
    private val day: List<AlertInputs> = (0 until 18 * 12).map { i ->
        val minuteOfDay = 6 * 60 + i * 5
        val ambient = when ((i / 6) % 4) {
            0 -> 70.0
            1 -> 88.0 + (i % 3)
            2 -> 95.0
            else -> 82.0
        }
        AlertInputs(
            nowMs = dayStart + i * step,
            localDay = 1L,
            localMinuteOfDay = minuteOfDay % 1440,
            sustainedAmbientDba = if (i % 7 == 0) null else ambient,
            dailyDosePercent = i * 1.6,
            weeklyAmbientPercent = 40.0 + i * 0.5,
            weeklyHeadphonePercent = 20.0 + i * 0.6,
            sustainedHeadphoneDba = if ((i / 10) % 3 == 1) 101.0 else 85.0,
        )
    }

    private val settingsVariants = listOf(
        AlertSettings(),
        AlertSettings(dailyCap = 1),
        AlertSettings(thresholdDba = 80, quietHoursEnabled = true, quietStartMin = 20 * 60, quietEndMin = 7 * 60),
    )

    private fun run(mode: SituationMode, base: AlertSettings): List<List<Alert>> {
        var memory = AlertMemory()
        return day.map { inputs ->
            val (alerts, next) = AlertEngine.evaluate(inputs, Situation.alertSettings(mode, base), memory)
            memory = next
            alerts
        }
    }

    @Test
    fun `every mode but Concert fires exactly what Street fires, step by step`() {
        for (base in settingsVariants) {
            val street = run(SituationMode.STREET, base)
            assertTrue("the day must actually fire alerts", street.flatten().isNotEmpty())
            for (mode in SituationMode.entries - SituationMode.CONCERT) {
                assertEquals("$mode with $base", street, run(mode, base))
            }
        }
    }

    @Test
    fun `Concert fires everything Street fires, at the same step, and only adds advisories`() {
        for (base in settingsVariants) {
            val street = run(SituationMode.STREET, base)
            val concert = run(SituationMode.CONCERT, base)
            street.zip(concert).forEachIndexed { i, (s, c) ->
                assertTrue("step $i with $base: $s not in $c", c.containsAll(s))
                (c - s.toSet()).forEach { extra -> assertEquals("step $i extra $extra", Tier.ADVISORY, extra.kind.tier) }
            }
        }
        // With a cap of 1 the lifted cap must actually show up as extra advisories.
        val capped = AlertSettings(dailyCap = 1)
        assertTrue(run(SituationMode.CONCERT, capped).flatten().size > run(SituationMode.STREET, capped).flatten().size)
    }

    @Test
    fun `a mode changes nothing in the settings but Concert's cap, and only upwards`() {
        for (base in settingsVariants) for (mode in SituationMode.entries) {
            val s = Situation.alertSettings(mode, base)
            assertEquals(base.copy(dailyCap = s.dailyCap), s)
            assertTrue(s.dailyCap >= base.dailyCap)
            if (mode != SituationMode.CONCERT) assertEquals(base, s)
        }
    }

    @Test
    fun `a mode never silences or shortens an alert haptic`() {
        val prefs = listOf(HapticPrefs(), HapticPrefs(advisoryHaptics = false), HapticPrefs(strength = HapticStrength.STRONG))
        for (p in prefs) for (mode in SituationMode.entries) for (kind in AlertKind.entries) {
            val withMode = Situation.hapticPrefs(mode, p)
            assertTrue("$mode $kind $p", AlertPolicy.repeats(kind, withMode) >= AlertPolicy.repeats(kind, p))
            assertEquals(p.advisoryHaptics, withMode.advisoryHaptics)
        }
        assertEquals(HapticStrength.STRONG, Situation.hapticPrefs(SituationMode.CONCERT, HapticPrefs()).strength)
    }
}
