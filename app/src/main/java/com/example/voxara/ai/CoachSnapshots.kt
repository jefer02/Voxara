package com.example.voxara.ai

import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.DayPart
import com.example.voxara.core.ai.HearingCareScore
import com.example.voxara.core.ai.daysToWeeklyLimit
import com.example.voxara.core.ai.streakDays
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.scene.environmentBand
import com.example.voxara.data.ExposureState
import kotlin.math.roundToInt

/**
 * THE PRIVACY GATE — the only place an [AggregateSnapshot] is built. It reads the app state and
 * keeps aggregates only: rounded percentages and levels, durations, coarse day parts and
 * categories. Pure (no Android calls), so it is unit-tested.
 */
object CoachSnapshots {

    fun build(state: ExposureState, locale: String): AggregateSnapshot {
        val budget = if (state.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
        val today = state.days.firstOrNull()
        val hpToday = state.headphoneDays.firstOrNull()
        val weeklyAmbient = state.weeklyFraction * 100
        val weeklyHeadphone = state.headphoneWeeklyFraction * 100

        // Daily shares of the weekly allowance, both ledgers, for the projection.
        val shares = state.days.take(7).mapIndexed { i, d ->
            100.0 * (d.energyPa2h + (state.headphoneDays.getOrNull(i)?.energyPa2h ?: 0.0)) / budget
        }
        val worstWeekly = maxOf(weeklyAmbient, weeklyHeadphone)
        val daysOver = state.days.take(7).count { it.nioshDosePercent >= 100.0 }
        val completedDays = state.days.drop(1).take(7).map { it.nioshDosePercent < 100.0 }

        val hourly = today?.hourly.orEmpty()
        val levels = hourly.filter { it > 0f }
        return AggregateSnapshot(
            locale = if (locale == "es") "es" else "en",
            dailyDosePercent = state.dosePercent.roundToInt(),
            weeklyAmbientPercent = weeklyAmbient.roundToInt(),
            weeklyHeadphonePercent = weeklyHeadphone.roundToInt(),
            currentDba = state.dba.takeIf { it > 0.0 && !state.simulated }?.roundToInt(),
            averageDbaToday = today?.let { d -> levels.takeIf { it.isNotEmpty() }?.let { avgLevel(d.energyPa2h, d.measuredMinutes) } },
            loudestHourDbaToday = levels.maxOrNull()?.roundToInt(),
            measuredMinutesToday = (today?.measuredMinutes ?: 0.0).roundToInt(),
            listeningMinutesToday = (hpToday?.measuredMinutes ?: 0.0).roundToInt(),
            loudHoursByDayPart = DayPart.entries.associateWith { part ->
                hourly.withIndex().count { (h, v) -> v >= 80f && partOf(h) == part }
            },
            headphoneCategory = state.headphoneCategory?.name?.lowercase(),
            ambientCategory = state.dba.takeIf { it > 0.0 }?.let { environmentBand(it).name.lowercase() },
            calibrated = state.calibrated,
            hearingCareScore = HearingCareScore.of(weeklyAmbient, weeklyHeadphone, daysOver),
            streakDays = streakDays(completedDays),
            daysToWeeklyLimit = daysToWeeklyLimit(worstWeekly, shares.drop(1).ifEmpty { shares }),
        )
    }

    fun partOf(hour: Int): DayPart = when (hour) {
        in 6..11 -> DayPart.MORNING
        in 12..17 -> DayPart.AFTERNOON
        in 18..22 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }

    /** Energy-average level of a day, from its Pa^2*h and measured minutes. */
    private fun avgLevel(energyPa2h: Double, minutes: Double): Int? {
        if (minutes <= 0.0 || energyPa2h <= 0.0) return null
        val pa2 = energyPa2h / (minutes / 60.0)
        return (10.0 * kotlin.math.log10(pa2 / 4.0e-10)).roundToInt()
    }
}
