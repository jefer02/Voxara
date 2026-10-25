package com.example.voxara.phone.model

import com.example.voxara.core.ledger.DaySummary

/** One local day on a chart: its share of the daily allowance, and whether anything was measured. */
data class DayFigure(val dayEpoch: Long, val percent: Double, val measured: Boolean)

/**
 * THE NUMBERS THE PHONE SHOWS, derived once from the state so Home, Today and Trends cannot disagree.
 *
 * Four figures with four names, never mixed:
 *  - **today**: the share of today's allowance (the watch's own number when its status is from today, else the ledger's).
 *  - **surroundings, last 7 days** and **headphones, last 7 days**: each a share of the weekly allowance, the two
 *    budgets the watch alerts on.
 *  - **all sound, last 7 days**: those two added, compared with the 7 days before. Trends leads with it.
 * The seven days are always the rolling last seven (today and the six before), never a calendar week.
 *
 * Today's column in every day-by-day view is [todayPercent], so the chart and the headline are the same number.
 */
data class HearingFigures(
    val todayPercent: Double,
    val surroundingsWeekPercent: Double,
    val headphonesWeekPercent: Double,
    /** The 7 days before the last 7, all sound, from the ledger. */
    val previousWeekPercent: Double,
    /** Newest first; index 0 is today. */
    val days: List<DayFigure>,
    /** All sound, last 7 days, as of the end of each day, oldest first; the last entry is [allSoundWeekPercent]. */
    val rolling: List<Double>,
    val asOfMs: Long?,
    val fromWatchStatus: Boolean,
) {
    val allSoundWeekPercent: Double get() = surroundingsWeekPercent + headphonesWeekPercent

    /** Points gained (positive) or lost against the 7 days before; always [allSoundWeekPercent] minus [previousWeekPercent]. */
    val weekChange: Double get() = allSoundWeekPercent - previousWeekPercent

    /** The headphones' share of all sound in the last 7 days, 0..1. */
    val headphoneShare: Double get() = if (allSoundWeekPercent > 0.0) headphonesWeekPercent / allSoundWeekPercent else 0.0

    /** The last [count] days, oldest first (what a chart draws). */
    fun lastDays(count: Int): List<DayFigure> = days.take(count).reversed()

    /** Days among the last [count] that reached the daily limit. */
    fun daysOverLimit(count: Int): Int = days.take(count).count { it.percent >= 100.0 }

    /** The day with the largest share among the last [count], or null when none was measured. */
    fun busiest(count: Int): DayFigure? = days.take(count).filter { it.measured }.maxByOrNull { it.percent }

    /** The mean share of the measured days among the last [count], or null when none was measured. */
    fun averageDay(count: Int): Double? = days.take(count).filter { it.measured }.map { it.percent }.average().takeIf { !it.isNaN() }

    companion object {
        val EMPTY = HearingFigures(0.0, 0.0, 0.0, 0.0, emptyList(), emptyList(), null, false)
    }
}

/** Builds the figures from the headline (the watch's numbers or the ledger's) and the synced history. */
fun figuresOf(head: Headline, history: History, sensitive: Boolean, rollingCount: Int = 30): HearingFigures {
    val amb = history.ambient
    val hp = history.headphone
    val budget = Budget.weeklyPa2h(sensitive)
    fun window(from: List<DaySummary>, skip: Int) = from.drop(skip).take(7).sumOf { it.energyPa2h }
    val days = amb.mapIndexed { i, d -> DayFigure(d.dayEpoch, if (i == 0) head.dosePercent else d.nioshDosePercent, d.measuredMinutes > 0.0 || (i == 0 && head.dosePercent > 0.0)) }
    val all = head.weeklyPercent + head.headphoneWeeklyPercent
    val count = minOf(rollingCount, maxOf(1, amb.size - 6))
    val rolling = (count - 1 downTo 0).map { i ->
        if (i == 0) all else 100.0 * (window(amb, i) + window(hp, i)) / budget
    }
    return HearingFigures(
        todayPercent = head.dosePercent,
        surroundingsWeekPercent = head.weeklyPercent,
        headphonesWeekPercent = head.headphoneWeeklyPercent,
        previousWeekPercent = 100.0 * (window(amb, 7) + window(hp, 7)) / budget,
        days = days,
        rolling = rolling,
        asOfMs = head.asOfMs,
        fromWatchStatus = head.fromWatchStatus,
    )
}

/**
 * How today's allowance filled up: the running total of the day's minutes (one entry every [step] minutes up to
 * [nowMinute], starting at 0), scaled so the last entry is exactly [totalPercent] — the headline figure the screens
 * show. Without any measured minute the curve is a flat 0 and the screens keep the figure only.
 */
fun cumulativeShare(
    minutes: List<com.example.voxara.core.ledger.MinuteRecord>,
    dayStartMinute: Long,
    nowMinute: Int,
    totalPercent: Double,
    step: Int = 6,
): List<Double> {
    val end = nowMinute.coerceIn(1, 1440)
    val byMinute = minutes.groupBy { (it.minute - dayStartMinute).toInt() }
    val raw = DoubleArray(end + 1)
    var run = 0.0
    for (m in 0..end) {
        run += byMinute[m]?.sumOf { it.nioshDosePercent } ?: 0.0
        raw[m] = run
    }
    val scale = if (run > 0.0) totalPercent / run else 0.0
    val points = (0..end step step).map { raw[it] * scale }.toMutableList()
    if ((end % step) != 0) points += raw[end] * scale
    // the last point is the headline, exactly
    if (run > 0.0) points[points.lastIndex] = totalPercent
    return points
}
