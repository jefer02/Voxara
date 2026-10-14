package com.example.voxara.phone.model

import com.example.voxara.core.ai.HearingCareScore
import com.example.voxara.core.ai.SnapshotInputs
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.ledger.MinuteRecord
import com.example.voxara.core.ledger.dayStartMinute
import com.example.voxara.core.ledger.levelOf
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.ledger.mergeByMinute
import com.example.voxara.core.ledger.summarizeDays
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.sync.WatchStatus
import java.util.TimeZone

/**
 * What the phone shows, derived from the synced minute ledger with the SAME shared functions the
 * watch uses (mergeByMinute, summarizeDays, HearingCareScore), regrouped into the phone's local
 * days. Pure, so it is unit-tested.
 */
data class History(
    /** Local days, index 0 = today. */
    val ambient: List<DaySummary>,
    val headphone: List<DaySummary>,
    /** Today's ambient minutes (one per minute, louder origin kept). */
    val todayMinutes: List<MinuteRecord>,
    val todayStartMinute: Long,
    /** Newest first. */
    val alerts: List<AlertRecord>,
) {
    val isEmpty: Boolean get() = ambient.all { it.measuredMinutes <= 0.0 } && headphone.all { it.measuredMinutes <= 0.0 }

    companion object {
        val EMPTY = History(emptyList(), emptyList(), emptyList(), 0L, emptyList())
        const val DAYS = 35

        fun build(
            ambientRecords: List<MinuteRecord>,
            headphoneRecords: List<MinuteRecord>,
            alerts: List<AlertRecord>,
            nowMs: Long,
            zone: TimeZone = TimeZone.getDefault(),
            days: Int = DAYS,
        ): History {
            val today = localEpochDay(nowMs, zone)
            val start = dayStartMinute(today, zone)
            val amb = mergeByMinute(ambientRecords)
            val hp = mergeByMinute(headphoneRecords)
            return History(
                ambient = summarizeDays(amb, today, days, zone),
                headphone = summarizeDays(hp, today, days, zone),
                todayMinutes = amb.filter { it.minute >= start && it.minute < start + 1440 },
                todayStartMinute = start,
                alerts = alerts,
            )
        }
    }
}

object Budget {
    fun weeklyPa2h(sensitive: Boolean) = if (sensitive) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
}

/** The energy-average level of a day, or null when nothing was measured. */
fun DaySummary.averageDba(): Double? = levelOf(energyPa2h * 3600.0, measuredMinutes * 60.0)

/** Loudest single minute (LAeq over 1 min) — the phone never claims a peak it did not receive. */
fun List<MinuteRecord>.loudestMinuteDba(): Double? = mapNotNull { it.laeq }.maxOrNull()

/**
 * Rolling 7-day budget at the end of each day, oldest first, for [count] days:
 * energy of that day and the six before it, as a percent of the weekly allowance.
 */
fun rollingWeeklyPercent(ambient: List<DaySummary>, sensitive: Boolean, count: Int): List<Double> {
    val budget = Budget.weeklyPa2h(sensitive)
    return (count - 1 downTo 0).map { i ->
        100.0 * ambient.drop(i).take(7).sumOf { it.energyPa2h } / budget
    }
}

/** This week (last 7 days) against the 7 before, as percent of the weekly allowance. */
data class WeekCompare(val thisWeek: Double, val previousWeek: Double, val headphoneShare: Double) {
    val change: Double get() = thisWeek - previousWeek
}

fun weekCompare(ambient: List<DaySummary>, headphone: List<DaySummary>, sensitive: Boolean): WeekCompare {
    val budget = Budget.weeklyPa2h(sensitive)
    val thisAmb = ambient.take(7).sumOf { it.energyPa2h }
    val thisHp = headphone.take(7).sumOf { it.energyPa2h }
    val prev = ambient.drop(7).take(7).sumOf { it.energyPa2h } + headphone.drop(7).take(7).sumOf { it.energyPa2h }
    val total = thisAmb + thisHp
    return WeekCompare(
        thisWeek = 100.0 * total / budget,
        previousWeek = 100.0 * prev / budget,
        headphoneShare = if (total > 0) thisHp / total else 0.0,
    )
}

/** A run of consecutive minutes in one zone (null zone = not measured). */
data class ZoneSegment(val fromMinute: Int, val toMinute: Int, val zone: RiskZone?)

/** Today's minutes as zone runs over 0..1440 (minutes after local midnight), gaps included. */
fun zoneSegments(minutes: List<MinuteRecord>, dayStartMinute: Long, untilMinute: Int = 1440): List<ZoneSegment> {
    val byMinute = minutes.associateBy { (it.minute - dayStartMinute).toInt() }
    val out = ArrayList<ZoneSegment>()
    var runStart = 0
    var runZone: RiskZone? = byMinute[0]?.laeq?.let(RiskZone::of)
    for (m in 1..untilMinute) {
        val z = if (m == untilMinute) null else byMinute[m]?.laeq?.let(RiskZone::of)
        if (m == untilMinute || z != runZone) {
            out += ZoneSegment(runStart, m, runZone)
            runStart = m
            runZone = z
        }
    }
    return out
}

/** Hearing Care Score from the watch's weekly figures and the synced days (same formula as the watch). */
fun careScore(weeklyPercent: Double, headphoneWeeklyPercent: Double, ambient: List<DaySummary>): Int =
    HearingCareScore.of(weeklyPercent, headphoneWeeklyPercent, ambient.take(7).count { it.nioshDosePercent >= 100.0 })

/**
 * The figures the phone shows as "now": the watch's own numbers when its status is from today,
 * otherwise what the synced ledger says — each with the time it is valid for.
 */
data class Headline(
    val dosePercent: Double,
    val weeklyPercent: Double,
    val headphoneWeeklyPercent: Double,
    /** When these figures were true (the watch's status time, or the last sync). */
    val asOfMs: Long?,
    val fromWatchStatus: Boolean,
)

fun headline(
    status: WatchStatus?,
    history: History,
    lastSyncMs: Long?,
    sensitive: Boolean,
    nowMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Headline {
    if (status != null && localEpochDay(status.atMs, zone) == localEpochDay(nowMs, zone)) {
        return Headline(status.dosePercent, status.weeklyPercent, status.headphoneWeeklyPercent, status.atMs, true)
    }
    val budget = Budget.weeklyPa2h(sensitive)
    return Headline(
        dosePercent = history.ambient.firstOrNull()?.nioshDosePercent ?: 0.0,
        weeklyPercent = 100.0 * history.ambient.take(7).sumOf { it.energyPa2h } / budget,
        headphoneWeeklyPercent = 100.0 * history.headphone.take(7).sumOf { it.energyPa2h } / budget,
        asOfMs = lastSyncMs,
        fromWatchStatus = false,
    )
}

/**
 * Tono's privacy-gate inputs on the phone. A level is only quoted as "current" when the watch
 * measured it in the last [LIVE_WINDOW_MS]; otherwise the model sees no current level at all.
 */
fun phoneSnapshotInputs(status: WatchStatus?, history: History, head: Headline, sensitive: Boolean, nowMs: Long): SnapshotInputs {
    val lastAt = status?.lastReadingAtMs
    val recent = status?.lastDba?.takeIf { lastAt != null && nowMs - lastAt in 0..LIVE_WINDOW_MS }
    return SnapshotInputs(
        dosePercent = head.dosePercent,
        weeklyFraction = head.weeklyPercent / 100.0,
        headphoneWeeklyFraction = head.headphoneWeeklyPercent / 100.0,
        dba = recent ?: 0.0,
        simulated = false,
        sensitiveListener = sensitive,
        days = history.ambient,
        headphoneDays = history.headphone,
        headphoneCategory = null,
        calibrated = status?.calibrated ?: false,
        situation = status?.situationMode,
    )
}

const val LIVE_WINDOW_MS = 5 * 60_000L
