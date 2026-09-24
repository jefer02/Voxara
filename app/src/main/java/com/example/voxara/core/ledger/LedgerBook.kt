package com.example.voxara.core.ledger

import java.util.TimeZone

/** Where minute records live. The app uses SQLite; tests use a map. */
interface MinuteStore {
    /** Inserts or replaces the row keyed by (minute, kind, origin). */
    fun put(r: MinuteRecord)
    fun range(fromMinute: Long, toMinute: Long, kind: ExposureKind): List<MinuteRecord>
    fun clear(fromMinute: Long, toMinute: Long, kind: ExposureKind)
    fun prune(nowMinute: Long)
}

/**
 * One exposure ledger (ambient or headphone): folds measured spans into minute records, keeps
 * the rolling 7-day total, and answers the history questions. Pure Kotlin over [MinuteStore].
 */
class LedgerBook(
    val kind: ExposureKind,
    private val store: MinuteStore,
    private val zone: TimeZone = TimeZone.getDefault(),
    /** Which device produced these records (0 = this watch). */
    private val origin: Int = 0,
) {
    private val acc = MinuteAccumulator(kind).also { it.origin = origin }
    private val rolling = RollingWeek()

    /** Reloads the rolling window from storage (start-up, after a reset or a phone sync). */
    fun load(nowMs: Long) {
        val now = minuteOf(nowMs)
        rolling.load(mergeByMinute(store.range(now - ROLLING_WINDOW_MINUTES + 1, now + 1, kind)))
        acc.current()?.let { rolling.set(it.minute, it.energyPa2s) }
    }

    /** Folds a measured span; closed minutes are written immediately. */
    fun add(dba: Double, seconds: Double, endMs: Long, category: Int = 0, confidence: Int = 0) {
        acc.category = category
        acc.confidence = confidence
        acc.add(dba, seconds, endMs).forEach { closed ->
            store.put(closed)
            rolling.set(closed.minute, energyAt(closed))
        }
        // The minute in progress counts on its own; other origins are merged when it closes.
        acc.current()?.let { rolling.set(it.minute, it.energyPa2s) }
    }

    /** Writes the minute in progress (called with the periodic save). */
    fun flush() {
        acc.current()?.let { store.put(it) }
    }

    fun weeklyPa2h(nowMs: Long): Double = rolling.totalPa2h(minuteOf(nowMs))

    /** The last [days] local days, index 0 = [today]. */
    fun days(today: Long, nowMs: Long, days: Int = 7): List<DaySummary> {
        flush()
        val from = dayStartMinute(today - (days - 1), zone)
        return summarizeDays(mergeByMinute(store.range(from, minuteOf(nowMs) + 1, kind)), today, days, zone)
    }

    /** Manual "start today over": today's minutes of this ledger are removed. */
    fun clearDay(day: Long, nowMs: Long) {
        acc.flush()
        store.clear(dayStartMinute(day, zone), dayStartMinute(day + 1, zone), kind)
        load(nowMs)
    }

    fun prune(nowMs: Long) = store.prune(minuteOf(nowMs))

    /** Energy of a record as it counts in the rolling window (all origins of a minute are merged). */
    private fun energyAt(r: MinuteRecord): Double {
        val others = store.range(r.minute, r.minute + 1, kind).filter { it.origin != r.origin }
        return mergeByMinute(others + r).firstOrNull()?.energyPa2s ?: r.energyPa2s
    }
}

/**
 * One record per minute across origins. Two devices can report the same minute (the watch and a
 * phone both playing to headphones): the ear hears one or the other, so the LOUDER one is kept —
 * never the sum, so a minute is never counted twice.
 */
fun mergeByMinute(records: List<MinuteRecord>): List<MinuteRecord> =
    records.groupBy { it.minute }.values.map { same ->
        if (same.size == 1) same[0] else same.maxBy { it.laeq ?: Double.NEGATIVE_INFINITY }
    }.sortedBy { it.minute }
