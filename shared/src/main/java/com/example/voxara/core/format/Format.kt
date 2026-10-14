package com.example.voxara.core.format

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The domain core stays language-agnostic: it produces numbers and typed outcomes, never
 * sentences. Digits and the h/m/s symbols are identical in every locale Voxara ships, so they
 * are formatted here; every word around them is resolved from resources by the UI layer.
 */

/** What is left at the level currently being measured. */
sealed interface Headroom {
    /** Below 80 dBA — nothing is accruing, so there is nothing to count down. */
    data object NotAccruing : Headroom

    /** The day's allowance is spent. */
    data object LimitReached : Headroom

    /** "8h 00m", "30m", "28s". */
    data class Left(val clock: String) : Headroom
}

fun headroomOf(minutes: Double): Headroom = when {
    minutes.isInfinite() -> Headroom.NotAccruing
    minutes <= 0.0 -> Headroom.LimitReached
    else -> Headroom.Left(formatClock(minutes))
}

/** "8h 00m", "30m", "28s", or "SAFE". */
fun formatHeadroom(minutes: Double): String = when (val h = headroomOf(minutes)) {
    is Headroom.NotAccruing -> "SAFE"
    is Headroom.LimitReached -> "0m"
    is Headroom.Left -> h.clock
}

/** "8h 00m" / "30m" / "28s" — locale-neutral. */
fun formatClock(minutes: Double): String = when {
    minutes < 1.0 -> "${(minutes * 60).roundToInt()}s"
    minutes < 60.0 -> "${minutes.roundToInt()}m"
    else -> "${floor(minutes / 60).toInt()}h ${(minutes % 60).roundToInt().toString().padStart(2, '0')}m"
}

/** Concert mode counts down in minutes and seconds — not in dose percent. */
fun formatCountdown(seconds: Double): String? {
    if (seconds.isInfinite()) return null
    val s = seconds.roundToInt().coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
}

/** "82.0 dBA", or null while nothing has accrued. The unit is the same in every locale. */
fun formatTwa(twa: Double?): String? =
    if (twa == null) null else "${(twa * 10).roundToInt() / 10.0} dBA"
