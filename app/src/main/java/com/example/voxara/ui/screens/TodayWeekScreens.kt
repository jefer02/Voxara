package com.example.voxara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.voxara.ui.design.EmptyState
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ProgressRing
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.WeekBars
import com.example.voxara.ui.design.zoneWord
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlin.math.roundToInt

/** TODAY — the daily dose ring, 24 hourly bars coloured AND sized by level, and coverage. */
@Composable
fun TodayScreen(env: UiEnv) {
    val s = env.state
    val today = s.days.firstOrNull()
    val zone = RiskZone.ofPercent(s.dosePercent)
    VoxList {
        item { SectionLabel(stringResource(R.string.card_today).uppercase()) }
        item {
            val cd = stringResource(R.string.cd_today, s.dosePercent.roundToInt(), zoneWord(zone))
            ProgressRing(
                (s.dosePercent / 100).toFloat(),
                Modifier.size(112.dp).semantics(mergeDescendants = true) { contentDescription = cd },
                color = VColor.zone(zone),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.dosePercent.roundToInt()}%", style = VType.Numeral, color = VColor.Text)
                    Text(stringResource(R.string.today_ring_sub), style = VType.Caption, color = VColor.TextSecondary)
                }
            }
        }
        item {
            BodyText(
                formatTwa(s.twaDba)?.let { stringResource(R.string.today_twa, it) } ?: stringResource(R.string.no_dose_logged),
                color = VColor.Text,
            )
        }
        item {
            if (today == null || today.measuredMinutes <= 0.0) {
                EmptyState(VoxIcons.Waves, stringResource(R.string.today_empty))
            } else {
                HourBars(today.hourly)
            }
        }
        today?.let {
            item { SectionLabel(stringResource(R.string.day_measured_plain, formatClock(it.measuredMinutes))) }
        }
        item { BodyText(stringResource(R.string.today_note), color = VColor.TextSecondary) }
    }
}

/** 24 slim bars: height by level (40-110 dBA), colour by zone, empty hours as dots. */
@Composable
private fun HourBars(hourly: List<Float>) {
    val loud = hourly.count { it >= 85f }
    val cd = stringResource(R.string.cd_hours, loud)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hourly.forEach { v ->
                val f = if (v <= 0f) 0.05f else ((v - 40f) / 70f).coerceIn(0.08f, 1f)
                Box(Modifier.weight(1f).fillMaxHeight(f)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = if (v <= 0f) VColor.Surface3 else VColor.zone(RiskZone.of(v.toDouble())),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, style = VType.Caption, color = VColor.TextTertiary) }
        }
    }
}

/**
 * WEEK — the rolling 7-day budget for both ledgers, seven daily bars, the streak, the pace
 * projection and Tono's weekly summary (the numbers are deterministic; Tono only words them).
 */
@Composable
fun WeekScreen(env: UiEnv, actions: VoxaraActions) {
    val s = env.state
    val budget = if (s.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
    val ambient = s.weeklyFraction * 100
    val listening = s.headphoneWeeklyFraction * 100
    val worst = maxOf(ambient, listening)
    val days = s.days.take(7)
    val shares = days.mapIndexed { i, d ->
        100.0 * (d.energyPa2h + (s.headphoneDays.getOrNull(i)?.energyPa2h ?: 0.0)) / budget
    }
    val streak = streakDays(days.drop(1).map { it.nioshDosePercent < 100.0 })
    val pace = daysToWeeklyLimit(worst, shares.drop(1).ifEmpty { shares })
    val score = HearingCareScore.of(ambient, listening, days.count { it.nioshDosePercent >= 100.0 })
    val zone = RiskZone.ofPercent(worst)
    val name = stringResource(R.string.ai_name)

    VoxList {
        item { SectionLabel(stringResource(R.string.card_week).uppercase()) }
        item {
            val cd = stringResource(R.string.cd_week, ambient.roundToInt(), listening.roundToInt())
            ProgressRing(
                (worst / 100).toFloat(),
                Modifier.size(112.dp).semantics(mergeDescendants = true) { contentDescription = cd },
                color = VColor.zone(zone),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${worst.roundToInt()}%", style = VType.Numeral, color = VColor.Text)
                    Text(stringResource(R.string.week_ring_sub), style = VType.Caption, color = VColor.TextSecondary)
                }
            }
        }
        item {
            MetricCard(VoxIcons.Waves, stringResource(R.string.week_ambient), "${ambient.roundToInt()}%", accent = VColor.zone(RiskZone.ofPercent(ambient)))
        }
        item {
            MetricCard(
                VoxIcons.Headphones, stringResource(R.string.week_listening), "${listening.roundToInt()}%",
                detail = stringResource(R.string.estimated_short), accent = VColor.zone(RiskZone.ofPercent(listening)),
            )
        }
        if (shares.isNotEmpty()) {
            item {
                val labels = weekdayLabels(days.size)
                val values = shares.reversed().map { (it / 100.0 * 3.0).toFloat().coerceIn(0f, 1f) }
                WeekBars(values, labels, stringResource(R.string.cd_week_bars, shares.first().roundToInt()))
            }
        }
        item {
            BodyText(
                pace?.let { stringResource(R.string.week_pace, it) } ?: stringResource(R.string.week_pace_none),
                color = VColor.Text,
            )
        }
        item { BodyText(stringResource(R.string.week_streak, streak), color = VColor.TextSecondary) }
        item {
            MetricCard(VoxIcons.Shield, stringResource(R.string.score_title), "$score / 100", detail = stringResource(R.string.score_sub))
        }
        item { BodyText(stringResource(R.string.score_explain), color = VColor.TextSecondary) }
        item {
            PillButton(
                stringResource(R.string.week_ask_summary, name),
                onClick = { actions.onAsk(CoachTask.WEEKLY_SUMMARY, null) },
                icon = VoxIcons.Sparkle,
                style = PillStyle.TONAL,
            )
        }
        env.coach.reply?.let { r ->
            item { HeroStatus(r.insight, maxLines = 4) }
            item { BodyText(r.suggestion, color = VColor.BrandStart) }
        }
    }
}

/** Single-letter weekday labels, oldest to today. */
private fun weekdayLabels(n: Int): List<String> {
    val symbols = DateFormatSymbols.getInstance().shortWeekdays
    val cal = Calendar.getInstance()
    return (n - 1 downTo 0).map { back ->
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -back)
        symbols[c.get(Calendar.DAY_OF_WEEK)].take(1).uppercase()
    }
}
