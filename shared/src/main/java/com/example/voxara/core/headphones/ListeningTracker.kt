package com.example.voxara.core.headphones

/**
 * EVENT-ACCURATE LISTENING — pure and clock-free (times are passed in).
 *
 * The old accrual credited one volume reading to a whole sampling period (up to a minute on the watch,
 * fifteen on the phone). Here every EVENT (headphones connected or gone, playback started or stopped,
 * the volume moved) closes the span that was running at the level that was in force, and opens the next
 * one. Seconds therefore follow what the ears actually got, and a sampling loop is only needed to hand
 * the finished spans to the ledger ([drain]) and as a guard for a missed event.
 *
 * Nothing here knows about Android, the ledger or the alert engine: it turns "estimate at time t" into
 * spans of (level, seconds). The caller decides which ledger owns a span (the double-count rule) and
 * which origin it carries.
 */
class ListeningTracker(
    /** A span longer than this was not watched all the way (process frozen): only this much is credited. */
    private val maxSpanSeconds: Double = MAX_SPAN_SECONDS,
) {

    /** One finished span: [dba] held for [seconds], ending at [endMs]. */
    data class Span(val dba: Double, val seconds: Double, val endMs: Long)

    private var sinceMs = 0L
    private var level: Double? = null
    private val done = ArrayList<Span>()

    /** The estimate in force, or null while nothing is being listened to. */
    val currentDba: Double? get() = level

    /**
     * What the ears get from [nowMs] on: [dba] while headphones are connected AND playing, otherwise null.
     * The span that was running is closed first, so a change is accounted at the time it happened.
     */
    fun update(nowMs: Long, dba: Double?) {
        close(nowMs)
        level = dba
        sinceMs = nowMs
    }

    /**
     * Finished spans up to [nowMs] (the running one is closed and a new one starts at [nowMs] at the same
     * level), oldest first. Returns an empty list when nothing was playing.
     */
    fun drain(nowMs: Long): List<Span> {
        close(nowMs)
        sinceMs = nowMs
        if (done.isEmpty()) return emptyList()
        return done.toList().also { done.clear() }
    }

    /** Forgets the running span and anything undelivered (the dosimeter stopped). */
    fun reset() {
        level = null
        sinceMs = 0L
        done.clear()
    }

    private fun close(nowMs: Long) {
        val l = level ?: return
        if (sinceMs <= 0L || nowMs <= sinceMs) return
        val seconds = ((nowMs - sinceMs) / 1000.0).coerceAtMost(maxSpanSeconds)
        // Merge with the previous span when the level did not change (a guard tick, not an event).
        val last = done.lastOrNull()
        if (last != null && last.dba == l && last.endMs == sinceMs) {
            done[done.lastIndex] = last.copy(seconds = last.seconds + seconds, endMs = nowMs)
        } else {
            done += Span(l, seconds, nowMs)
        }
    }

    companion object {
        /** Longest span credited without an event or a tick in between. */
        const val MAX_SPAN_SECONDS = 5 * 60.0
    }
}

/**
 * The estimated level at a volume [index] of [max], with [dbAt] the platform's curve (getStreamVolumeDb
 * for that index and output, or null when it cannot say). Same rules as [estimateListening].
 */
fun levelAtIndex(index: Int, max: Int, dbAt: (Int) -> Double?): Double? =
    estimateListening(dbAt(index), index, max)?.dba

/**
 * "LOWER TO SAFE VOLUME": the highest volume index whose estimated level is at or below [safeDba]
 * (default [HeadphoneProfile.SAFE_DBA]). Never raises: the result is at most [current]. Returns 0 when
 * even the quietest step is above the target (mute is the only safe step), and [current] when
 * [current] is already safe.
 */
fun safeVolumeIndex(
    current: Int,
    max: Int,
    dbAt: (Int) -> Double?,
    safeDba: Double = HeadphoneProfile.SAFE_DBA,
): Int {
    if (max <= 0 || current <= 0) return current.coerceAtLeast(0)
    for (i in current.coerceAtMost(max) downTo 1) {
        val level = levelAtIndex(i, max, dbAt) ?: continue
        if (level <= safeDba) return i
    }
    return 0
}
