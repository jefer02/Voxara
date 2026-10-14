package com.example.voxara.core.ai

import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.scene.environmentBand
import com.example.voxara.core.situation.Situation
import com.example.voxara.core.situation.SituationMode
import kotlin.math.roundToInt

/**
 * What the privacy gate may read. The watch fills it from its live state, the phone from the
 * last synced status and minute ledger; both go through [SnapshotBuilder], so the same data gives
 * the same snapshot on either device.
 */
data class SnapshotInputs(
    val dosePercent: Double,
    /** Rolling 7-day ambient budget as a fraction (1.0 = 100%). */
    val weeklyFraction: Double,
    val headphoneWeeklyFraction: Double,
    /** Last level in dBA, 0 when there is no reading worth quoting. */
    val dba: Double,
    /** Display-only bench values never reach the model as a "current" level. */
    val simulated: Boolean,
    val sensitiveListener: Boolean,
    /** Local days, index 0 = today. */
    val days: List<DaySummary>,
    val headphoneDays: List<DaySummary>,
    /** Connected headphone category name, or null. */
    val headphoneCategory: String?,
    val calibrated: Boolean,
    /** The situation mode in force, or null when unknown (Tono then uses its neutral voice). */
    val situation: SituationMode? = null,
)

/**
 * THE PRIVACY GATE — the only place an [AggregateSnapshot] is built. Aggregates only: rounded
 * percentages and levels, durations, coarse day parts and categories.
 */
object SnapshotBuilder {

    fun build(i: SnapshotInputs, locale: String): AggregateSnapshot {
        val budget = if (i.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
        val today = i.days.firstOrNull()
        val hpToday = i.headphoneDays.firstOrNull()
        val weeklyAmbient = i.weeklyFraction * 100
        val weeklyHeadphone = i.headphoneWeeklyFraction * 100

        // Daily shares of the weekly allowance, both ledgers, for the projection.
        val shares = i.days.take(7).mapIndexed { idx, d ->
            100.0 * (d.energyPa2h + (i.headphoneDays.getOrNull(idx)?.energyPa2h ?: 0.0)) / budget
        }
        val worstWeekly = maxOf(weeklyAmbient, weeklyHeadphone)
        val daysOver = i.days.take(7).count { it.nioshDosePercent >= 100.0 }
        val completedDays = i.days.drop(1).take(7).map { it.nioshDosePercent < 100.0 }

        val hourly = today?.hourly.orEmpty()
        val levels = hourly.filter { it > 0f }
        return AggregateSnapshot(
            locale = if (locale == "es") "es" else "en",
            dailyDosePercent = i.dosePercent.roundToInt(),
            weeklyAmbientPercent = weeklyAmbient.roundToInt(),
            weeklyHeadphonePercent = weeklyHeadphone.roundToInt(),
            currentDba = i.dba.takeIf { it > 0.0 && !i.simulated }?.roundToInt(),
            averageDbaToday = today?.let { d -> levels.takeIf { it.isNotEmpty() }?.let { avgLevel(d.energyPa2h, d.measuredMinutes) } },
            loudestHourDbaToday = levels.maxOrNull()?.roundToInt(),
            measuredMinutesToday = (today?.measuredMinutes ?: 0.0).roundToInt(),
            listeningMinutesToday = (hpToday?.measuredMinutes ?: 0.0).roundToInt(),
            loudHoursByDayPart = DayPart.entries.associateWith { part ->
                hourly.withIndex().count { (h, v) -> v >= 80f && partOf(h) == part }
            },
            headphoneCategory = i.headphoneCategory?.lowercase(),
            ambientCategory = i.dba.takeIf { it > 0.0 }?.let { environmentBand(it).name.lowercase() },
            calibrated = i.calibrated,
            hearingCareScore = HearingCareScore.of(weeklyAmbient, weeklyHeadphone, daysOver),
            streakDays = streakDays(completedDays),
            daysToWeeklyLimit = daysToWeeklyLimit(worstWeekly, shares.drop(1).ifEmpty { shares }),
            situationMode = i.situation?.let(Situation::wireValue),
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
