package com.example.voxara.core.ledger

import java.util.TimeZone

/**
 * DAY BOOKKEEPING — pure Kotlin so the rollover rules are unit-tested.
 *
 * Days are local epoch days. The daily NIOSH dose resets at local midnight; the weekly budget is
 * a ROLLING 7-day window derived from the minute ledger (see [RollingWeek]) and never resets.
 */

/** Local epoch day for a wall-clock instant. */
fun localEpochDay(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Math.floorDiv(nowMs + zone.getOffset(nowMs), 86_400_000L)

/** ISO week index (Monday start) of an epoch day. Kept for display grouping only. */
fun isoWeekOf(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

/** Epoch minute of local midnight starting [epochDay]. */
fun dayStartMinute(epochDay: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val utcMidnight = epochDay * 86_400_000L
    // The offset in force at that local midnight (DST-safe to within the transition hour).
    val offset = zone.getOffset(utcMidnight - zone.rawOffset)
    return Math.floorDiv(utcMidnight - offset, 60_000L)
}

/** Minutes after local midnight. */
fun localMinuteOfDay(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Int =
    (Math.floorMod(nowMs + zone.getOffset(nowMs), 86_400_000L) / 60_000L).toInt()

/** The persisted daily counters, as far as the rollover rules care. */
data class LedgerSnapshot(
    val dayEpoch: Long,
    val dosePercent: Double,
    val peakDbc: Double,
)

/**
 * Close the stored day if [today] has moved past it: the daily dose and peak start over.
 *
 * Idempotent by construction: it keys on the stored day, so the service and the midnight worker
 * can both call it and the day is closed exactly once.
 *
 * @return the new snapshot, or null when there is nothing to roll.
 */
fun rollover(stored: LedgerSnapshot, today: Long): LedgerSnapshot? {
    // Fresh install: nothing to close, just stamp the day.
    if (stored.dayEpoch == 0L) return stored.copy(dayEpoch = today)
    // Same day, or a clock that went backwards: never roll twice, never roll into the past.
    if (today <= stored.dayEpoch) return null
    return LedgerSnapshot(dayEpoch = today, dosePercent = 0.0, peakDbc = 0.0)
}

/**
 * A ledger write stamped with [writerDay] must never land on top of a newer day: that would put
 * yesterday's dose back after the rollover.
 */
fun acceptsWrite(storedDay: Long, writerDay: Long): Boolean = writerDay >= storedDay

/** One day of the history screen, derived from the minute ledger. */
data class DaySummary(
    val dayEpoch: Long,
    /** 24 hourly energy-average levels (0 = nothing measured). */
    val hourly: List<Float>,
    val nioshDosePercent: Double,
    val measuredMinutes: Double,
    /** Energy spent that day, Pa^2*h. */
    val energyPa2h: Double,
) {
    /** 8-hour TWA equivalent of the day's dose, or null when nothing accrued. */
    val twaDba: Double?
        get() = if (nioshDosePercent <= 0.0) null else 10.0 * kotlin.math.log10(nioshDosePercent / 100.0) + 85.0
}

/** The last [days] local days (index 0 = today) for one ledger. */
fun summarizeDays(
    records: List<MinuteRecord>,
    today: Long,
    days: Int = 7,
    zone: TimeZone = TimeZone.getDefault(),
): List<DaySummary> = (0 until days).map { back ->
    val day = today - back
    val start = dayStartMinute(day, zone)
    val end = dayStartMinute(day + 1, zone)
    val inDay = records.filter { it.minute in start until end }
    DaySummary(
        dayEpoch = day,
        hourly = hourlyProfile(inDay, start),
        nioshDosePercent = inDay.sumOf { it.nioshDosePercent },
        measuredMinutes = inDay.sumOf { it.measuredSeconds } / 60.0,
        energyPa2h = inDay.sumOf { it.energyPa2s } / 3600.0,
    )
}
