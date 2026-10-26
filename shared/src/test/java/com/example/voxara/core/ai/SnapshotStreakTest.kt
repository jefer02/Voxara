package com.example.voxara.core.ai

import com.example.voxara.core.ledger.DaySummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The streak Tono may quote ("N días seguidos dentro de tu límite diario") as the snapshot builds it from the days:
 * a day with no (or too little) measurement never counts, and a slip is never forgiven.
 */
class SnapshotStreakTest {

    private fun day(back: Int, minutes: Double, dose: Double) = DaySummary(20_000L - back, List(24) { 0f }, dose, minutes, 0.0)

    private fun streakOf(vararg pastDays: Pair<Double, Double>): Int {
        // Index 0 is today (still running, never counted); then yesterday, the day before, ...
        val days = listOf(day(0, 30.0, 5.0)) + pastDays.mapIndexed { i, (minutes, dose) -> day(i + 1, minutes, dose) }
        val inputs = SnapshotInputs(
            dosePercent = 5.0, weeklyFraction = 0.1, headphoneWeeklyFraction = 0.0, dba = 50.0, simulated = false,
            sensitiveListener = false, days = days, headphoneDays = emptyList(), headphoneCategory = null, calibrated = false,
        )
        return SnapshotBuilder.build(inputs, "es").streakDays
    }

    @Test fun daysWithoutMeasurementsNeverCount() {
        // Six empty days (the watch off) were "6 días seguidos" on the real watch: now they are none.
        assertEquals(0, streakOf(*Array(6) { 0.0 to 0.0 }))
        assertEquals(0, streakOf(30.0 to 10.0, 300.0 to 10.0, 300.0 to 10.0))
    }

    @Test fun measuredDaysWithinTheLimitInARowCount() {
        assertEquals(3, streakOf(120.0 to 20.0, 300.0 to 60.0, 90.0 to 99.0, 0.0 to 0.0, 300.0 to 10.0))
    }

    @Test fun aSlipIsNeverForgiven() {
        assertEquals(2, streakOf(300.0 to 20.0, 300.0 to 30.0, 300.0 to 140.0, 300.0 to 10.0, 300.0 to 10.0))
        assertEquals(0, streakOf(300.0 to 100.0, 300.0 to 10.0, 300.0 to 10.0))
    }
}
