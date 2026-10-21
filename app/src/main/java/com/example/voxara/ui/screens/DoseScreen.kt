package com.example.voxara.ui.screens

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.HearingCareScore
import com.example.voxara.core.ai.daysToWeeklyLimit
import com.example.voxara.core.ai.streakDays
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.format.formatTwa
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.DoseMorph
import com.example.voxara.ui.design.EmptyState
import com.example.voxara.ui.design.HeroList
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PageName
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.WeekBars
import com.example.voxara.ui.design.ZoneTag
import com.example.voxara.ui.design.animationsActive
import com.example.voxara.ui.design.heroScaled
import com.example.voxara.ui.design.row
import com.example.voxara.ui.design.zoneWord
import com.example.voxara.ui.theme.LocalAmbient
import com.example.voxara.ui.theme.LocalReduceMotion
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/** How long the Today to Week morph takes (ms); with reduced motion the two pictures cross-fade in [FADE_MS]. */
private const val MORPH_MS = 520
private const val FADE_MS = 200

/** Crown movement (pixels) that flips the view, and the quiet (ms) after a flip and between two strokes. */
private const val ROTARY_FLIP_PX = 24f
private const val ROTARY_HOLD_MS = 380L
private const val ROTARY_IDLE_MS = 250L

/**
 * DOSE — how much of your allowance you have used, today or over the week, on ONE ring that morphs between
 * the two pictures.
 *
 * TODAY (the default): 24 hourly bars from the ledger around the ring (midnight bottom-left, noon at the top),
 * each as long and as coloured as that hour was loud, with a marker on this hour; in the middle the day's dose
 * as a rolling percentage, its zone and "of your allowance". WEEK: the rolling 7 days as seven segments, oldest
 * first and today last, each filled to that day's share of the weekly allowance; in the middle the worse of the
 * room and the headphones as a rolling percentage, its zone and "of your weekly limit".
 *
 * THE SWITCH. [view] says which is showing and [onView] changes it, two ways: a small "Today | Week" switch in
 * the ring's gap, where the current view is both the label and the selected segment, or the crown. The crown
 * flips the view while the page rests at its top: clockwise goes to Week, counter-clockwise back to Today;
 * once the page is scrolled (or, on Week, once the crown keeps turning clockwise) the crown scrolls the details
 * as on any list, so it never fights the pager (that is a swipe) nor the scrolling. On the smallest displays and
 * at large fonts the switch is the first detail row and the gap carries only the view's name.
 *
 * Below the fold, per view. Today: how the day adds up (equivalent level), the hourly capsules, coverage and how
 * the dose works. Week: where the share comes from (around you and headphones), the daily bars, the pace, the
 * streak, the score, Tono's summary and the headphones group (listening history and its estimate's details).
 */
@Composable
internal fun DoseScreen(env: UiEnv, actions: VoxaraActions, view: DoseView, onView: (DoseView) -> Unit) {
    val s = env.state
    val week = view == DoseView.WEEK
    val compact = isCompactFont()
    val locale = LocalConfiguration.current.locales[0]
    val still = LocalReduceMotion.current
    val canAnimate = animationsActive(!LocalAmbient.current)

    // ------------------------------------------------------------------ today
    val today = s.days.firstOrNull()
    val dayZone = RiskZone.ofPercent(s.dosePercent)
    val hourly = today?.hourly ?: List(24) { 0f }
    val todaySpoken = stringResource(R.string.cd_today, s.dosePercent.roundToInt(), zoneWord(dayZone)) + " " +
        stringResource(R.string.cd_hours, hourly.count { it >= 85f })
    val twaLine = formatTwa(s.twaDba, locale)?.let { stringResource(R.string.today_twa, it) } ?: stringResource(R.string.no_dose_logged)
    val nowHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    // ------------------------------------------------------------------ week
    val budget = if (s.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
    val ambient = s.weeklyFraction * 100
    val listening = s.headphoneWeeklyFraction * 100
    val worst = maxOf(ambient, listening)
    val days = s.days.take(7)
    val shares = days.mapIndexed { i, d -> 100.0 * (d.energyPa2h + (s.headphoneDays.getOrNull(i)?.energyPa2h ?: 0.0)) / budget }
    val weekZone = RiskZone.ofPercent(worst)
    val dayNames = weekdayNames(days.size, locale)
    val dayShares = shares.reversed()
    val res = LocalContext.current.resources
    val todayLabel = stringResource(R.string.cd_week_today)
    // Each day is spoken: "Monday: 3%. Tuesday: 0%. ... Friday (today): 12%".
    val perDay = dayNames.indices.joinToString(". ") { i ->
        val dayName = if (i == dayNames.lastIndex) "${dayNames[i]} ($todayLabel)" else dayNames[i]
        res.getString(R.string.cd_week_day, dayName, dayShares.getOrElse(i) { 0.0 }.roundToInt())
    }
    val weekSpoken = stringResource(R.string.cd_week, ambient.roundToInt(), listening.roundToInt()) + ". " +
        stringResource(R.string.cd_week_days, perDay)
    val breakdown = stringResource(R.string.week_breakdown, ambient.roundToInt(), listening.roundToInt())
    // One segment per day, oldest first and today last; a day fills with its share of the weekly allowance and takes the
    // colour of its own dose zone. A day counts as measured when either ledger has minutes for it (dotted: no data).
    val dayValues = dayShares.map { weekDayFill(it) }
    val dayTones = days.reversed().map { VColor.tone(RiskZone.ofPercent(it.nioshDosePercent)) }
    val dayMeasured = days.indices.reversed().map { i ->
        days[i].measuredMinutes > 0.0 || (s.headphoneDays.getOrNull(i)?.measuredMinutes ?: 0.0) > 0.0
    }

    // ------------------------------------------------------------------ the morph
    val mix = remember { Animatable(if (week) 1f else 0f) }
    LaunchedEffect(week, canAnimate, still) {
        val goal = if (week) 1f else 0f
        when {
            !canAnimate -> mix.snapTo(goal)
            still -> mix.animateTo(goal, tween(FADE_MS))
            else -> mix.animateTo(goal, tween(MORPH_MS, easing = FastOutSlowInEasing))
        }
    }
    val tone = zoneTone(if (week) weekZone else dayZone)
    val identity = pageIdentity(GlancePage.DOSE)
    val spoken = (if (week) weekSpoken else todaySpoken)
    val todayName = stringResource(R.string.page_today)
    val weekName = stringResource(R.string.page_week)

    // ------------------------------------------------------------------ the crown
    val hapticView = LocalView.current
    val brush = remember { RotaryStroke() }
    val currentView = androidx.compose.runtime.rememberUpdatedState(view)
    val onRotary: (Float) -> Boolean = { px ->
        val now = SystemClock.uptimeMillis()
        if (now - brush.lastFlip < ROTARY_HOLD_MS) {
            brush.lastEvent = now
            true
        } else {
            // A new stroke (a pause since the last event) or a turn the other way starts the sum again.
            if (now - brush.lastEvent > ROTARY_IDLE_MS || (px > 0f) != (brush.sum > 0f)) brush.sum = 0f
            brush.lastEvent = now
            brush.sum += px
            val goal = if (brush.sum > 0f) DoseView.WEEK else DoseView.TODAY
            when {
                abs(brush.sum) < ROTARY_FLIP_PX -> true
                goal != currentView.value -> {
                    brush.sum = 0f
                    brush.lastFlip = now
                    hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onView(goal)
                    true
                }
                // Already there: on Week the crown goes on to scroll the details, on Today there is nothing before it.
                else -> false
            }
        }
    }

    HeroList(
        onRotary = onRotary,
        hero = { m ->
            PageStage(
                identity = identity, wave = waveOf(s.dba), waveTone = zoneTone(RiskZone.of(s.dba)), description = spoken,
                modifier = m,
                // The gap's one control: the switch (its selected half is the view's name). Where there is no room for a
                // 48 dp switch the gap carries the name and the switch is the first detail.
                nameSlot = {
                    if (compact) {
                        PageName(if (week) weekName else todayName, if (week) VoxIcons.Chart else VoxIcons.Calendar, VColor.AccentInk)
                    } else DoseSwitch(view, onView, todayName, weekName)
                },
                picture = { DoseMorph(hourly, nowHour, dayValues, dayTones, dayMeasured, week = { mix.value }) },
            ) { isCompact ->
                HeroFigure(if (week) worst.roundToInt() else s.dosePercent.roundToInt(), tone.fill, isCompact, suffix = "%")
                if (!isCompact) {
                    AnimatedContent(
                        targetState = week,
                        transitionSpec = {
                            if (still || !canAnimate) fadeIn(snap()) togetherWith fadeOut(snap())
                            else fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                        },
                        label = "doseCaption",
                    ) { w -> FigureCaption(stringResource(if (w) R.string.week_caption else R.string.allowance_caption)) }
                }
                ZoneTag(if (week) weekZone else dayZone)
            }
        },
        details = {
            if (compact) row { DoseSwitch(view, onView, todayName, weekName, fill = true) }
            if (!week) todayRows(today?.let { formatClock(it.measuredMinutes) }, today?.measuredMinutes ?: 0.0, hourly, twaLine)
            else weekRows(env, actions, shares, days.size, locale, breakdown, ambient, listening, worst, days.drop(1).map { it.nioshDosePercent < 100.0 }, dayValues)
        },
    )
}

/** The crown's running state: movement summed over the current stroke, and when the last event and the last flip were. */
private class RotaryStroke { var sum = 0f; var lastEvent = Long.MIN_VALUE / 2; var lastFlip = Long.MIN_VALUE / 2 }

/**
 * THE SWITCH — "Today | Week": two segments in one 48 dp pill, the selected one filled and bold, so the view is
 * named and its state shown by shape, not by colour alone. Each segment is a tab for TalkBack ("Today, tab,
 * selected"). [fill] stretches it across a list row (the large-text arrangement).
 */
@Composable
private fun DoseSwitch(view: DoseView, onView: (DoseView) -> Unit, todayName: String, weekName: String, fill: Boolean = false) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(48.dp)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f), shape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((v, label) in listOf(DoseView.TODAY to todayName, DoseView.WEEK to weekName)) {
            val selected = view == v
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .height(42.dp)
                    .clip(shape)
                    .then(if (selected) Modifier.background(VColor.accent.fill.copy(alpha = 0.30f), shape) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onView(v) })
                    .semantics { contentDescription = label }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = VType.Label.heroScaled(), maxLines = 1,
                    color = if (selected) VColor.AccentInk else VColor.TextSecondary,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Below Today's hero: the equivalent level, the hourly capsules, what was measured and how the dose works. */
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.todayRows(
    measured: String?, measuredMinutes: Double, hourly: List<Float>, twaLine: String,
) {
    row { FootLine(twaLine, color = VColor.Text) }
    row {
        if (measured == null || measuredMinutes <= 0.0) EmptyState(VoxIcons.Waves, stringResource(R.string.today_empty))
        else HourBars(hourly)
    }
    measured?.let { row { SectionLabel(stringResource(R.string.day_measured_plain, it)) } }
    row { BodyText(stringResource(R.string.today_note), color = VColor.TextSecondary) }
}

/**
 * Below Week's hero: where the share comes from, the daily bars with their weekday letters, the pace
 * projection, the streak, the score and Tono's weekly summary (the numbers are deterministic; Tono only words
 * them), then the headphones group.
 */
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.weekRows(
    env: UiEnv, actions: VoxaraActions, shares: List<Double>, dayCount: Int, locale: java.util.Locale, breakdown: String,
    ambient: Double, listening: Double, worst: Double, withinLimit: List<Boolean>, dayValues: List<Float>,
) {
    val streak = streakDays(withinLimit)
    val pace = daysToWeeklyLimit(worst, shares.drop(1).ifEmpty { shares })
    val days = env.state.days.take(7)
    val score = HearingCareScore.of(ambient, listening, days.count { it.nioshDosePercent >= 100.0 })
    row { FootLine(breakdown, color = VColor.Text) }
    if (shares.isNotEmpty()) {
        row { WeekBars(dayValues, weekdayLabels(dayCount, locale), stringResource(R.string.cd_week_bars, shares.first().roundToInt())) }
    }
    row {
        BodyText(pace?.let { stringResource(R.string.week_pace, it) } ?: stringResource(R.string.week_pace_none), color = VColor.Text)
    }
    row { BodyText(stringResource(R.string.week_streak, streak), color = VColor.TextSecondary) }
    row { MetricCard(VoxIcons.Shield, stringResource(R.string.score_title), "$score / 100", detail = stringResource(R.string.score_sub)) }
    row { BodyText(stringResource(R.string.score_explain), color = VColor.TextSecondary) }
    if (env.state.tonoEnabled) {
        row {
            PillButton(
                stringResource(R.string.week_ask_summary, stringResource(R.string.ai_name)),
                onClick = { actions.onAsk(CoachTask.WEEKLY_SUMMARY, null) },
                icon = VoxIcons.Sparkle,
                style = PillStyle.TONAL,
            )
        }
        env.coach.reply?.let { r ->
            row { HeroStatus(r.insight, maxLines = 4) }
            row { BodyText(r.suggestion, color = VColor.AccentInk) }
        }
    }
    headphonesWeekRows(env, env.state.listening.active)
}

/**
 * 24 rounded capsule bars, the template's history bars: height by level (40-110 dBA), colour by
 * the level's tier, unmeasured hours as low grey capsules.
 */
@Composable
private fun HourBars(hourly: List<Float>) {
    val loud = hourly.count { it >= 85f }
    val cd = stringResource(R.string.cd_hours, loud)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val n = 24
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            for (h in 0 until n) {
                val v = hourly.getOrElse(h) { 0f }
                val f = if (v <= 0f) 0.08f else ((v - 40f) / 70f).coerceIn(0.12f, 1f)
                val bh = size.height * f
                drawRoundRect(
                    color = if (v <= 0f) VColor.Track else VColor.zone(RiskZone.of(v.toDouble())),
                    topLeft = Offset(h * (bw + gap), size.height - bh),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bw / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, style = VType.Value, color = VColor.TextTertiary) }
        }
    }
}

/**
 * How full a day's segment is for its [share] of the weekly allowance (percent). An even week spends
 * about 14 % a day, so a day at twice that fills its segment: ordinary days read as about half, not as nothing.
 */
internal fun weekDayFill(share: Double): Float = (share / 100.0 * 3.5).toFloat().coerceIn(0f, 1f)

/** Single-letter weekday labels in the app's language, oldest to today. */
private fun weekdayLabels(n: Int, locale: java.util.Locale): List<String> =
    weekdays(n, DateFormatSymbols.getInstance(locale).shortWeekdays).map { it.take(1).uppercase(locale) }

/** Full weekday names in the app's language, oldest to today (for the spoken description). */
private fun weekdayNames(n: Int, locale: java.util.Locale): List<String> =
    weekdays(n, DateFormatSymbols.getInstance(locale).weekdays)

private fun weekdays(n: Int, symbols: Array<String>): List<String> {
    val cal = Calendar.getInstance()
    return (n - 1 downTo 0).map { back ->
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -back)
        symbols[c.get(Calendar.DAY_OF_WEEK)]
    }
}
