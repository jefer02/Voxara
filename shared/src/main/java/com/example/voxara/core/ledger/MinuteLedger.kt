package com.example.voxara.core.ledger

import com.example.voxara.core.dose.DOSE_THRESHOLD_DBA
import com.example.voxara.core.dose.permittedSeconds
import kotlin.math.log10
import kotlin.math.pow

/**
 * MINUTE LEDGER — pure Kotlin. Every measured second is folded into one record per minute and
 * exposure kind. Everything the app shows (hourly profile, daily dose, rolling 7-day budget) is
 * derived from these records, so there is exactly one source of truth.
 *
 * Energy is kept as sum(p^2 * t) in Pa^2*s — the quantity WHO-ITU H.870 budgets — and the NIOSH
 * dose contribution is accumulated per burst (not per minute Leq), so the 80 dBA threshold is
 * applied exactly as the engine applies it.
 */

/** (20 uPa)^2 */
const val P0_SQUARED_PA2 = 4.0e-10

/** Two ledgers, never mixed: see the attribution rule in the headphone engine. */
enum class ExposureKind(val code: Int) {
    AMBIENT(0),
    HEADPHONE(1);

    companion object {
        fun of(code: Int) = entries.first { it.code == code }
    }
}

data class MinuteRecord(
    /** Minutes since the Unix epoch (UTC). */
    val minute: Long,
    val kind: ExposureKind,
    /** Seconds of this minute actually represented by measurements (0..60). */
    val measuredSeconds: Double,
    /** sum(p^2 * t), Pa^2*s. */
    val energyPa2s: Double,
    /** NIOSH daily-dose percentage contributed by this minute. */
    val nioshDosePercent: Double,
    /** Headphone ledger only: category, confidence and origin codes. 0 for ambient. */
    val category: Int = 0,
    val confidence: Int = 0,
    val origin: Int = 0,
) {
    /** Energy-average level of what was measured in this minute. */
    val laeq: Double? get() = levelOf(energyPa2s, measuredSeconds)
}

fun energyOf(dba: Double, seconds: Double): Double = P0_SQUARED_PA2 * 10.0.pow(dba / 10.0) * seconds

/** Energy-average level from summed energy and duration, or null when nothing was measured. */
fun levelOf(energyPa2s: Double, seconds: Double): Double? =
    if (seconds <= 0.0 || energyPa2s <= 0.0) null
    else 10.0 * log10(energyPa2s / seconds / P0_SQUARED_PA2)

fun nioshDoseOf(dba: Double, seconds: Double): Double =
    if (dba < DOSE_THRESHOLD_DBA) 0.0 else 100.0 * seconds / permittedSeconds(dba)

fun minuteOf(ms: Long): Long = Math.floorDiv(ms, 60_000L)

/**
 * Builds the record for the minute in progress. A burst that spans a minute boundary is split
 * by time, so nothing is attributed to the wrong minute (or the wrong day).
 */
class MinuteAccumulator(private val kind: ExposureKind) {

    private var minute: Long = Long.MIN_VALUE
    private var seconds = 0.0
    private var energy = 0.0
    private var dose = 0.0
    var category = 0
    var confidence = 0
    var origin = 0

    /**
     * Folds a measured span ending at [endMs]. Returns the records of any minutes that closed.
     */
    fun add(dba: Double, spanSeconds: Double, endMs: Long): List<MinuteRecord> {
        if (spanSeconds <= 0.0) return emptyList()
        val closed = ArrayList<MinuteRecord>()
        // Split [end - span, end] forward at minute boundaries; the last slice takes the exact
        // remainder so the seconds always add up to the span.
        val slices = ArrayList<Pair<Long, Double>>()
        var startMs = endMs - Math.round(spanSeconds * 1000)
        var used = 0.0
        while (true) {
            val m = minuteOf(startMs)
            val boundary = (m + 1) * 60_000L
            if (boundary >= endMs) {
                slices += m to (spanSeconds - used)
                break
            }
            val s = (boundary - startMs) / 1000.0
            slices += m to s
            used += s
            startMs = boundary
        }
        for ((m, s) in slices) {
            if (m != minute) {
                flush()?.let { closed += it }
                minute = m
            }
            seconds += s
            energy += energyOf(dba, s)
            dose += nioshDoseOf(dba, s)
        }
        return closed
    }

    /** The minute in progress as a record (not reset). */
    fun current(): MinuteRecord? =
        if (minute == Long.MIN_VALUE || seconds <= 0.0) null
        else MinuteRecord(minute, kind, seconds.coerceAtMost(60.0), energy, dose, category, confidence, origin)

    /** Closes the minute in progress. */
    fun flush(): MinuteRecord? {
        val r = current()
        seconds = 0.0; energy = 0.0; dose = 0.0
        return r
    }
}

/** Rolling window length: WHO-ITU H.870 budgets a week; here it rolls rather than resetting. */
const val ROLLING_WINDOW_MINUTES = 7L * 24 * 60

/**
 * The rolling 7-day energy budget. Holds per-minute energy for the last 7 days (at most 10 080
 * entries per ledger) and answers "how much of the weekly allowance is spent right now".
 */
class RollingWeek {

    private val byMinute = java.util.TreeMap<Long, Double>()
    private var total = 0.0

    fun load(records: List<MinuteRecord>) {
        byMinute.clear(); total = 0.0
        records.forEach { set(it.minute, it.energyPa2s) }
    }

    /** Sets (replaces) a minute's energy — the in-progress minute is updated repeatedly. */
    fun set(minute: Long, energyPa2s: Double) {
        val old = byMinute.put(minute, energyPa2s) ?: 0.0
        total += energyPa2s - old
    }

    /** Pa^2*h spent in the 7 days up to and including [nowMinute]. */
    fun totalPa2h(nowMinute: Long): Double {
        val cutoff = nowMinute - ROLLING_WINDOW_MINUTES
        while (byMinute.isNotEmpty() && byMinute.firstKey() <= cutoff) {
            total -= byMinute.pollFirstEntry()!!.value
        }
        return (total / 3600.0).coerceAtLeast(0.0)
    }
}

/** Hourly energy-average profile (24 values, 0 = nothing measured) for one local day. */
fun hourlyProfile(records: List<MinuteRecord>, dayStartMinute: Long): List<Float> {
    val energy = DoubleArray(24)
    val secs = DoubleArray(24)
    for (r in records) {
        val h = ((r.minute - dayStartMinute) / 60).toInt()
        if (h !in 0..23) continue
        energy[h] += r.energyPa2s
        secs[h] += r.measuredSeconds
    }
    return List(24) { h -> (levelOf(energy[h], secs[h]) ?: 0.0).toFloat() }
}

/** NIOSH daily dose for one local day. */
fun dailyDose(records: List<MinuteRecord>, dayStartMinute: Long): Double =
    records.filter { it.minute in dayStartMinute until dayStartMinute + 1440 }.sumOf { it.nioshDosePercent }

/** Minutes actually measured in one local day (coverage). */
fun measuredMinutes(records: List<MinuteRecord>, dayStartMinute: Long): Double =
    records.filter { it.minute in dayStartMinute until dayStartMinute + 1440 }.sumOf { it.measuredSeconds } / 60.0

/**
 * Sliding energy-average over the last [windowMs] — the "sustained" level alerts use, so one
 * loud burst never triggers an alert on its own.
 */
class SlidingLeq(private val windowMs: Long) {

    private data class Span(val endMs: Long, val seconds: Double, val energy: Double)

    private val spans = ArrayDeque<Span>()

    fun add(dba: Double, seconds: Double, endMs: Long) {
        if (seconds <= 0.0) return
        spans.addLast(Span(endMs, seconds, energyOf(dba, seconds)))
        prune(endMs)
    }

    fun clear() = spans.clear()

    private fun prune(nowMs: Long) {
        while (spans.isNotEmpty() && spans.first().endMs <= nowMs - windowMs) spans.removeFirst()
    }

    /**
     * The window's Leq, or null until measurements cover at least [minCoverage] of it — so a
     * fresh start (or a gap) cannot produce a "sustained" level from a few seconds of data.
     */
    fun value(nowMs: Long, minCoverage: Double = 0.8): Double? {
        prune(nowMs)
        val secs = spans.sumOf { it.seconds }
        if (secs < windowMs / 1000.0 * minCoverage) return null
        return levelOf(spans.sumOf { it.energy }, secs)
    }
}
