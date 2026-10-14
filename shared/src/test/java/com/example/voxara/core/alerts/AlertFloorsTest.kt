package com.example.voxara.core.alerts

import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.SettingsMerge
import com.example.voxara.settings.VoxaraSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The safety floors: whatever the settings (or a synced phone) say, LIMIT alerts are never
 * snoozed, silenced or held; warnings and limits always vibrate; no snooze exceeds 2 h.
 */
class AlertFloorsTest {

    private val now = 1_750_000_000_000L
    private val minute = 23 * 60 // inside the default 22:00-07:00 quiet hours

    private fun inputs(daily: Double = 0.0, weekly: Double = 0.0, hp: Double = 0.0, ambient: Double? = null) =
        AlertInputs(now, localDay = 1L, localMinuteOfDay = minute, sustainedAmbientDba = ambient,
            dailyDosePercent = daily, weeklyAmbientPercent = weekly, weeklyHeadphonePercent = hp)

    private val quiet = AlertSettings(quietHoursEnabled = true)

    @Test
    fun `limit alerts are never held by quiet hours`() {
        val (alerts, _) = AlertEngine.evaluate(inputs(daily = 100.0, weekly = 100.0, hp = 100.0), quiet, AlertMemory(day = 1L))
        assertEquals(
            setOf(AlertKind.DAILY_100, AlertKind.WEEK_100, AlertKind.HEADPHONE_WEEK_100),
            alerts.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `quiet hours still hold advisories and warnings`() {
        val (alerts, _) = AlertEngine.evaluate(inputs(daily = 85.0, ambient = 92.0), quiet, AlertMemory(day = 1L))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `limit alerts cannot be snoozed, even for the longest snooze`() {
        for (kind in AlertKind.entries.filter { it.tier == Tier.LIMIT }) {
            assertFalse(kind.snoozable)
            val m = AlertEngine.snooze(AlertMemory(day = 1L), kind, now, durationMs = AlertPolicy.SNOOZE_MAX_MS)
            assertTrue(m.snoozedUntil.isEmpty())
        }
    }

    @Test
    fun `a forged snooze entry does not stop a limit alert`() {
        val forged = AlertMemory(day = 1L, snoozedUntil = mapOf(AlertKind.DAILY_100 to now + 3_600_000L))
        val (alerts, _) = AlertEngine.evaluate(inputs(daily = 100.0), AlertSettings(), forged)
        assertEquals(listOf(AlertKind.DAILY_100), alerts.map { it.kind })
    }

    @Test
    fun `a snooze lasts at most 2 hours and at least 15 minutes`() {
        val long = AlertEngine.snooze(AlertMemory(), AlertKind.DAILY_80, now, durationMs = 8 * 3_600_000L)
        assertEquals(now + 2 * 3_600_000L, long.snoozedUntil[AlertKind.DAILY_80])
        val short = AlertEngine.snooze(AlertMemory(), AlertKind.DAILY_80, now, durationMs = 1_000L)
        assertEquals(now + 15 * 60_000L, short.snoozedUntil[AlertKind.DAILY_80])
        val default = AlertEngine.snooze(AlertMemory(), AlertKind.AMBIENT_LOUD, now)
        assertEquals(now + 3_600_000L, default.snoozedUntil[AlertKind.AMBIENT_LOUD])
        assertEquals(AlertPolicy.SNOOZE_MAX_MS, AlertPolicy.snoozeMs(10_000))
    }

    @Test
    fun `warnings and limits always vibrate, only advisories can be silenced`() {
        val silentAdvisories = HapticPrefs(advisoryHaptics = false)
        for (kind in AlertKind.entries) {
            val expected = kind.tier != Tier.ADVISORY
            assertEquals(kind.name, expected, AlertPolicy.vibrates(kind, silentAdvisories))
            assertTrue(kind.name, AlertPolicy.repeats(kind, silentAdvisories) >= if (expected) 1 else 0)
            assertTrue(AlertPolicy.vibrates(kind, HapticPrefs()))
        }
        assertEquals(2, AlertPolicy.repeats(AlertKind.DAILY_100, HapticPrefs(HapticStrength.STRONG, advisoryHaptics = false)))
        assertEquals(1, AlertPolicy.repeats(AlertKind.WEEK_80, HapticPrefs(HapticStrength.STANDARD, advisoryHaptics = false)))
    }

    @Test
    fun `settings synced from a phone cannot break a floor`() {
        // A tampered phone document: snooze 8 h, threshold 150 dBA.
        val phone = SettingsDoc.fromJson(
            """{"v":1,"fields":{"snooze_minutes":{"value":"480","at":$now},"alert_threshold_dba":{"value":"150","at":$now}}}"""
        )
        val applied = VoxaraSettings.from(SettingsMerge.watchReceives(SettingsDoc(), phone, now).merged)
        assertEquals(120, applied.snoozeMinutes)
        assertEquals(95, applied.alertThresholdDba)
        assertEquals(SettingField.entries.size, SettingField.entries.map { it.key }.toSet().size)
    }
}
