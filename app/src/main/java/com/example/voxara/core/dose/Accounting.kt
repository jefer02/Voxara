package com.example.voxara.core.dose

/**
 * How much wall-clock time a burst stands for.
 *
 * The burst is folded in for the time that actually passed since the previous one — not the
 * nominal period, which undercounts whenever mic setup, warm-up and scheduling stretch the cycle
 * (about 35-45% in the old 250 ms continuous loop, exactly where the noise is loudest).
 *
 * A gap far beyond the nominal period means the loop was frozen (doze, process pause) rather than
 * listening, so it is capped: one burst is never extrapolated over time nobody measured.
 *
 * @param elapsedMs wall time since the previous burst was folded in, or null for the first burst
 * @param nominalPeriodMs the cadence the loop was running at
 */
fun representedSeconds(elapsedMs: Long?, nominalPeriodMs: Long): Double {
    val ms = when {
        elapsedMs == null -> nominalPeriodMs
        elapsedMs <= 0L -> 0L
        else -> elapsedMs.coerceAtMost(nominalPeriodMs * MAX_GAP_FACTOR)
    }
    return ms / 1000.0
}

/** A gap longer than this many nominal periods is treated as unmeasured. */
const val MAX_GAP_FACTOR = 2L
