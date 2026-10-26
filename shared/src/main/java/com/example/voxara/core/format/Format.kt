package com.example.voxara.core.format

import java.text.NumberFormat
import java.util.Locale
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

/**
 * A time of day ([minuteOfDay], 0..1439, as quiet hours are stored) as the device's clock shows it: 24-hour
 * ("22:00", "07:00") when [is24Hour], otherwise 12-hour with the [locale]'s day-period words ("10:00 PM",
 * "10:00 p. m."). Only the display changes; callers pass `DateFormat.is24HourFormat(context)`.
 */
fun formatTimeOfDay(minuteOfDay: Int, is24Hour: Boolean, locale: Locale): String {
    val m = Math.floorMod(minuteOfDay, 1440)
    val time = java.time.LocalTime.of(m / 60, m % 60)
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return time.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}

/** A level in whole decibels ("82 dBA"): the precision an uncalibrated estimate can honestly claim. */
fun formatLevel(dba: Double): String = "${dba.roundToInt()} dBA"

/**
 * A long, estimated duration rounded to what it can mean: whole hours from 10 h ("100h"), ten minutes below
 * ("3h 20m"), five minutes under an hour ("35m"). For estimates built on a ±10 dB assumption (headphone time left),
 * where "100h 29m" would claim a precision nobody has.
 */
fun formatApproxDuration(minutes: Double): String = when {
    minutes >= 600.0 -> "${(minutes / 60.0).roundToInt()}h"
    minutes >= 60.0 -> {
        val m = ((minutes / 10.0).roundToInt() * 10)
        "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
    }
    else -> "${maxOf(5, (minutes / 5.0).roundToInt() * 5)}m"
}

/** Concert mode counts down in minutes and seconds — not in dose percent. */
fun formatCountdown(seconds: Double): String? {
    if (seconds.isInfinite()) return null
    val s = seconds.roundToInt().coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
}

/**
 * [value] with [digits] decimals in [locale]: "54.0" in English, "54,0" in Spanish. Every figure with a
 * decimal that a person reads goes through here; digits-only text (clock, percentages) needs no locale.
 */
fun formatDecimal(value: Double, digits: Int = 1, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)

/** "82.0 dBA" / "82,0 dBA" in [locale], or null while nothing has accrued. The unit is the same in every locale. */
fun formatTwa(twa: Double?, locale: Locale = Locale.getDefault()): String? =
    if (twa == null) null else "${formatDecimal(twa, 1, locale)} dBA"
