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

    /** Nothing measured before this instant is ever recorded again: set by [wipe] ("delete all"). */
    private var floorMs = Long.MIN_VALUE

    /** Reloads the rolling window from storage (start-up, after a reset or a phone sync). */
    @Synchronized
    fun load(nowMs: Long) {
        val now = minuteOf(nowMs)
        rolling.load(mergeByMinute(store.range(now - ROLLING_WINDOW_MINUTES + 1, now + 1, kind)))
        acc.current()?.let { rolling.set(it.minute, it.energyPa2s) }
    }

    /**
     * "DELETE ALL DATA" on a running ledger: the minute in progress is dropped unwritten, every stored minute of this
     * kind goes, and from [nowMs] on only what is measured after it is recorded. A span that was already running
     * keeps its part after [nowMs] and loses the rest, so nothing from before the delete can come back through the
     * periodic save, a closing minute or a span still waiting to be handed over.
     */
    @Synchronized
    fun wipe(nowMs: Long) {
        floorMs = nowMs
        acc.discard()
        store.clear(Long.MIN_VALUE, Long.MAX_VALUE, kind)
        rolling.load(emptyList())
    }

    /** Folds a measured span; closed minutes are written immediately. */
    @Synchronized
    fun add(dba: Double, seconds: Double, endMs: Long, category: Int = 0, confidence: Int = 0) {
        val kept = secondsAfter(floorMs, endMs, seconds)
        if (kept <= 0.0) return
        acc.category = category
        acc.confidence = confidence
        acc.add(dba, kept, endMs).forEach { closed ->
            store.put(closed)
            rolling.set(closed.minute, energyAt(closed))
        }
        // The minute in progress counts on its own; other origins are merged when it closes.
        acc.current()?.let { rolling.set(it.minute, it.energyPa2s) }
    }

    /** Writes the minute in progress (called with the periodic save). */
    @Synchronized
    fun flush() {
        acc.current()?.let { store.put(it) }
    }

    @Synchronized
    fun weeklyPa2h(nowMs: Long): Double = rolling.totalPa2h(minuteOf(nowMs))

    /** The last [days] local days, index 0 = [today]. */
    @Synchronized
    fun days(today: Long, nowMs: Long, days: Int = 7): List<DaySummary> {
        flush()
        val from = dayStartMinute(today - (days - 1), zone)
        return summarizeDays(mergeByMinute(store.range(from, minuteOf(nowMs) + 1, kind)), today, days, zone)
    }

    /** Manual "start today over": today's minutes of this ledger are removed. */
    @Synchronized
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
 * Of a span of [seconds] ending at [endMs], the seconds that fall after [floorMs]: all of them when the span began
 * after the floor, none when it ended before it, the later part when it straddles it.
 */
fun secondsAfter(floorMs: Long, endMs: Long, seconds: Double): Double {
    if (floorMs == Long.MIN_VALUE) return seconds
    if (endMs <= floorMs) return 0.0
    return minOf(seconds, (endMs - floorMs) / 1000.0)
}

/**
 * One record per minute across origins. Two devices can report the same minute (the watch and a
 * phone both playing to headphones): the ear hears one or the other, so the LOUDER one is kept —
 * never the sum, so a minute is never counted twice.
 *
 * Deterministic: the answer does not depend on the order the rows came out of the database. Equal
 * levels fall back to the larger energy, then to the lower origin (the watch's own measurement).
 */
fun mergeByMinute(records: List<MinuteRecord>): List<MinuteRecord> =
    records.groupBy { it.minute }.values.map { same ->
        if (same.size == 1) same[0]
        else same.maxWith(compareBy<MinuteRecord>({ it.laeq ?: Double.NEGATIVE_INFINITY }, { it.energyPa2s }, { -it.origin }))
    }.sortedBy { it.minute }
