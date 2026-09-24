package com.example.voxara.core

import com.example.voxara.core.alerts.Alert
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertMemoryCodec
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.inQuietHours
import com.example.voxara.core.alerts.limitLevel
import com.example.voxara.haptics.Pattern
import com.example.voxara.haptics.pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deterministic alert engine: tiers, caps, cooldowns, snooze, quiet hours, persistence. */
class AlertEngineTest {

    private val min = 60_000L
    private var memory = AlertMemory()
    private var settings = AlertSettings()

    private fun run(
        t: Long,
        ambient: Double? = null,
        daily: Double = 0.0,
        weekly: Double = 0.0,
        hpWeekly: Double = 0.0,
        hpLoud: Double? = null,
        day: Long = 1L,
        minuteOfDay: Int = 12 * 60,
    ): List<Alert> {
        val (alerts, m) = AlertEngine.evaluate(
            AlertInputs(t, day, minuteOfDay, ambient, daily, weekly, hpWeekly, hpLoud),
            settings,
            memory,
        )
        memory = m
        return alerts
    }

    private fun List<Alert>.kinds() = map { it.kind }

    // ---------------------------------------------------------------- sustained ambient level

    @Test
    fun `the ambient alert needs a sustained level, not a burst`() {
        assertTrue("no sustained value yet", run(0, ambient = null).isEmpty())
        assertTrue(run(min, ambient = 84.9).isEmpty())
        assertEquals(listOf(AlertKind.AMBIENT_LOUD), run(2 * min, ambient = 85.0).kinds())
    }

    @Test
    fun `the ambient alert re-arms only after dropping 5 dB and a 30 minute cooldown`() {
        run(0, ambient = 90.0)
        assertTrue("still loud: no repeat", run(40 * min, ambient = 90.0).isEmpty())
        run(41 * min, ambient = 82.0)                      // not 5 dB below: still disarmed
        assertTrue(run(42 * min, ambient = 90.0).isEmpty())
        run(43 * min, ambient = 79.0)                      // re-armed
        assertEquals(listOf(AlertKind.AMBIENT_LOUD), run(44 * min, ambient = 90.0).kinds())
        run(45 * min, ambient = 70.0)
        assertTrue("cooldown", run(50 * min, ambient = 90.0).isEmpty())
    }

    @Test
    fun `the threshold is configurable`() {
        settings = settings.copy(thresholdDba = 90)
        assertTrue(run(0, ambient = 88.0).isEmpty())
        assertEquals(listOf(AlertKind.AMBIENT_LOUD), run(min, ambient = 90.5).kinds())
    }

    // ---------------------------------------------------------------- daily cap vs limits

    @Test
    fun `the daily cap limits advisories but never the 100 percent alert`() {
        settings = settings.copy(dailyCap = 1)
        assertEquals(listOf(AlertKind.AMBIENT_LOUD), run(0, ambient = 90.0).kinds())
        run(min, ambient = 70.0)
        assertTrue("cap reached", run(40 * min, ambient = 90.0, daily = 55.0).isEmpty())
        val limit = run(41 * min, ambient = 90.0, daily = 100.0)
        assertEquals(AlertKind.DAILY_100, limit.first().kind)
    }

    @Test
    fun `daily tiers fire once each and reset the next day`() {
        assertEquals(listOf(AlertKind.DAILY_50), run(0, daily = 51.0).kinds())
        assertTrue(run(min, daily = 60.0).isEmpty())
        assertEquals(listOf(AlertKind.DAILY_80), run(2 * min, daily = 81.0).kinds())
        assertEquals(listOf(AlertKind.DAILY_100), run(3 * min, daily = 101.0).kinds())
        assertTrue(run(4 * min, daily = 150.0).isEmpty())
        assertEquals("next +100%", listOf(AlertKind.DAILY_100), run(5 * min, daily = 200.0).kinds())
        assertEquals(200, memory.dailyLimitLevel)
        assertEquals(listOf(AlertKind.DAILY_50), run(6 * min, daily = 50.0, day = 2L).kinds())
    }

    @Test
    fun `jumping straight past several tiers fires only the most severe`() {
        assertEquals(listOf(AlertKind.DAILY_100), run(0, daily = 130.0).kinds())
    }

    // ---------------------------------------------------------------- weekly (rolling)

    @Test
    fun `weekly 80 re-arms below 70 and weekly 100 repeats every further 100`() {
        assertEquals(listOf(AlertKind.WEEK_80), run(0, weekly = 82.0).kinds())
        assertTrue(run(min, weekly = 85.0).isEmpty())
        run(2 * min, weekly = 65.0)
        assertEquals(listOf(AlertKind.WEEK_80), run(3 * min, weekly = 81.0).kinds())
        assertEquals(listOf(AlertKind.WEEK_100), run(4 * min, weekly = 100.0).kinds())
        assertTrue(run(5 * min, weekly = 180.0).isEmpty())
        assertEquals(listOf(AlertKind.WEEK_100), run(6 * min, weekly = 205.0).kinds())
        run(7 * min, weekly = 90.0)                        // the window let go
        assertEquals("crossing again alerts again", listOf(AlertKind.WEEK_100), run(8 * min, weekly = 101.0).kinds())
    }

    @Test
    fun `headphone tiers work the same way, with their own ledger`() {
        assertEquals(listOf(AlertKind.HEADPHONE_WEEK_80), run(0, hpWeekly = 80.0).kinds())
        assertEquals(listOf(AlertKind.HEADPHONE_WEEK_100), run(min, hpWeekly = 120.0).kinds())
        assertEquals(listOf(AlertKind.HEADPHONE_WEEK_100), run(2 * min, hpWeekly = 300.0).kinds())
        assertEquals(300, memory.hpWeekLimitLevel)
    }

    @Test
    fun `very loud listening alerts with a cooldown`() {
        assertEquals(listOf(AlertKind.HEADPHONE_LOUD_NOW), run(min, hpLoud = 101.0).kinds())
        run(2 * min, hpLoud = 90.0)
        assertTrue("cooldown", run(10 * min, hpLoud = 101.0).isEmpty())
        assertEquals(listOf(AlertKind.HEADPHONE_LOUD_NOW), run(32 * min, hpLoud = 101.0).kinds())
    }

    // ---------------------------------------------------------------- snooze and quiet hours

    @Test
    fun `snooze holds a warning for an hour but cannot touch a limit`() {
        memory = AlertEngine.snooze(memory, AlertKind.WEEK_80, 0L)
        memory = AlertEngine.snooze(memory, AlertKind.WEEK_100, 0L)
        assertFalse(memory.snoozedUntil.containsKey(AlertKind.WEEK_100))
        assertTrue(run(10 * min, weekly = 85.0).isEmpty())
        assertEquals(listOf(AlertKind.WEEK_80), run(61 * min, weekly = 85.0).kinds())
        assertEquals(listOf(AlertKind.WEEK_100), run(62 * min, weekly = 100.0).kinds())
    }

    @Test
    fun `quiet hours hold minor alerts but not limits`() {
        settings = settings.copy(quietHoursEnabled = true, quietStartMin = 22 * 60, quietEndMin = 7 * 60)
        assertTrue(inQuietHours(settings, 23 * 60))
        assertTrue(inQuietHours(settings, 6 * 60 + 59))
        assertFalse(inQuietHours(settings, 7 * 60))
        assertTrue(run(0, ambient = 90.0, daily = 85.0, minuteOfDay = 23 * 60).isEmpty())
        assertEquals(listOf(AlertKind.DAILY_100), run(min, daily = 100.0, minuteOfDay = 23 * 60 + 1).kinds())
        // After quiet hours the held weekly warning comes through.
        assertEquals(listOf(AlertKind.WEEK_80), run(2 * min, weekly = 85.0, daily = 100.0, minuteOfDay = 8 * 60).kinds())
    }

    // ---------------------------------------------------------------- misc

    @Test
    fun `limit levels are multiples of one hundred`() {
        assertEquals(0, limitLevel(99.9))
        assertEquals(100, limitLevel(100.0))
        assertEquals(200, limitLevel(299.0))
    }

    @Test
    fun `memory survives a round trip through persistence`() {
        run(0, ambient = 90.0, daily = 81.0, weekly = 120.0)
        memory = AlertEngine.snooze(memory, AlertKind.AMBIENT_LOUD, 5L)
        assertEquals(memory, AlertMemoryCodec.decode(AlertMemoryCodec.encode(memory)))
        assertEquals(AlertMemory(), AlertMemoryCodec.decode(null))
        assertEquals(AlertMemory(), AlertMemoryCodec.decode("garbage"))
    }

    @Test
    fun `each alert tier has its own haptic`() {
        assertEquals(Pattern.TWO_SOFT, AlertKind.HEADPHONE_WEEK_80.pattern())
        assertEquals(Pattern.LONG_SHORT_LONG, AlertKind.HEADPHONE_WEEK_100.pattern())
        assertEquals(Pattern.QUICK_TRIPLE, AlertKind.HEADPHONE_LOUD_NOW.pattern())
        assertEquals(Pattern.DOSE_FULL, AlertKind.DAILY_100.pattern())
    }
}
