package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.DayFigure
import com.example.voxara.phone.model.HearingFigures
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FitFigure
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.DayColumns
import com.example.voxara.phone.ui.components.HeatCalendar
import com.example.voxara.phone.ui.components.HourColumns
import com.example.voxara.phone.ui.components.RollingLine
import com.example.voxara.phone.ui.components.SegmentedChoice
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.ZoneLegend
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.dayText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.wordsStyle
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TrendRange(val days: Int) { DAY(1), WEEK(7), MONTH(30) }

/**
 * TRENDS — one range switch (a filter, so a Material 3 segmented button), one hero, one chart per range.
 *  - Day: today's share and the day hour by hour.
 *  - Week: all sound in the last 7 days (surroundings + headphones) against the 7 days before, the seven daily columns.
 *  - Month: how many days went over the daily limit, the calendar of the last 30 days.
 * Under them, the rolling week as a line, how much came from headphones, the Hearing Care Score as a supporting line,
 * and a chip that asks Tono. Every number comes from [HearingFigures], the same the Home ring and Today show.
 */
@Composable
fun TrendsScreen(s: PhoneUiState, onRefresh: () -> Unit, onAskTono: () -> Unit = {}, initialRange: TrendRange = TrendRange.WEEK) {
    var range by rememberSaveable { mutableStateOf(initialRange) }
    BoxWithConstraints {
    val wide = maxWidth >= 840.dp
    VoxScreen(stringResource(R.string.trends_title), refreshing = s.syncing, onRefresh = onRefresh) {
        item {
            SegmentedChoice(
                listOf(
                    TrendRange.DAY to stringResource(R.string.range_day),
                    TrendRange.WEEK to stringResource(R.string.range_week),
                    TrendRange.MONTH to stringResource(R.string.range_month),
                ),
                range, { range = it }, Modifier.contentWidth(),
            )
        }
        if (!s.loaded) {
            items(2) { SkeletonCard(Modifier.contentWidth(), lines = 4) }
            return@VoxScreen
        }
        item { FreshnessLine(s.lastSyncMs, s.nowMs, Modifier.contentWidth()) }
        if (s.history.isEmpty) {
            item { EmptyState(VoxIcons.Chart, stringResource(R.string.trends_title), stringResource(R.string.trends_empty), Modifier.contentWidth()) }
            return@VoxScreen
        }

        val f = s.figures
        val askTono = @Composable {
            if (s.settings.tonoEnabled) {
                AssistChip(
                    onClick = onAskTono,
                    label = { Text(stringResource(R.string.trends_ask_tono), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(VoxIcons.Ask, null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        val chart = @Composable {
            when (range) {
                TrendRange.DAY -> DayChart(s)
                TrendRange.WEEK -> WeekChart(f)
                TrendRange.MONTH -> MonthChart(f)
            }
        }
        if (wide) {
            // two panes: what it says on the left, the charts on the right
            item {
                Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Hero(range, f, s)
                        askTono()
                        if (range == TrendRange.WEEK) HeadphoneShare(f)
                        ScoreLine(s)
                    }
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        chart()
                        RollingSection(f, if (range == TrendRange.MONTH) 30 else 7)
                    }
                }
            }
        } else {
            item { Hero(range, f, s) }
            item { Column(Modifier.contentWidth()) { askTono() } }
            item { Column(Modifier.contentWidth()) { chart() } }
            item { RollingSection(f, if (range == TrendRange.MONTH) 30 else 7) }
            if (range == TrendRange.WEEK) item { HeadphoneShare(f) }
            item { ScoreLine(s) }
        }
    }
    }
}

// ------------------------------------------------------------------------------------ hero

@Composable
private fun Hero(range: TrendRange, f: HearingFigures, s: PhoneUiState) {
    val c = Vox.colors
    val (value, suffix, zone, caption) = when (range) {
        TrendRange.DAY -> HeroParts(f.todayPercent.roundToInt(), "%", RiskZone.ofPercent(f.todayPercent), stringResource(R.string.home_of_allowance))
        TrendRange.WEEK -> HeroParts(f.allSoundWeekPercent.roundToInt(), "%", RiskZone.ofPercent(f.allSoundWeekPercent), stringResource(R.string.trends_week_caption))
        TrendRange.MONTH -> {
            val n = f.daysOverLimit(30)
            HeroParts(n, "", null, pluralStringResource(R.plurals.trends_month_caption, n))
        }
    }
    val sentence = when (range) {
        TrendRange.DAY -> {
            val loudest = s.history.ambient.firstOrNull()?.hourly?.withIndex()?.maxByOrNull { it.value }?.takeIf { it.value > 0f }
            loudest?.let { stringResource(R.string.chart_loudest, it.value.roundToInt(), "%02d:00".format(it.index)) } ?: stringResource(R.string.coverage_none)
        }
        TrendRange.WEEK -> changeSentence(f)
        TrendRange.MONTH -> {
            val busiest = f.busiest(30)
            val avg = f.averageDay(30)
            listOfNotNull(
                busiest?.let { stringResource(R.string.trends_month_busiest, dayText(it.dayEpoch, "d MMM"), it.percent.roundToInt()) },
                avg?.let { stringResource(R.string.trends_month_average, it.roundToInt()) },
            ).joinToString(" ")
        }
    }
    val spoken = buildString {
        append(value).append(if (suffix.isEmpty()) " " else " $suffix ").append(caption)
        if (zone != null) append(", ").append(stringResource(zone.wordRes()))
        append(". ").append(sentence)
    }
    Column(Modifier.contentWidth().semantics(mergeDescendants = true) { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints {
            val room = this.maxWidth
            FitFigure(value, maxWidth = room * 0.6f, color = c.text, suffix = suffix, sizes = listOf(72, 64, 56, 48), spoken = "")
        }
        // the zone is its own label, the caption stays with the figure
        if (zone != null) ZoneWord(zone, sizeSp = 16)
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = c.text2)
        if (range == TrendRange.WEEK) {
            Text(
                stringResource(
                    R.string.trends_week_breakdown, f.surroundingsWeekPercent.roundToInt(), f.headphonesWeekPercent.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium, color = c.text2,
            )
        }
        if (sentence.isNotBlank()) Text(sentence, style = wordsStyle(17, FontWeight.ExtraBold, 1.25f), color = c.text)
    }
}

private data class HeroParts(val value: Int, val suffix: String, val zone: RiskZone?, val caption: String)

/** "12 points more than the 7 days before (80 %)": the difference is always the two numbers the screen shows. */
@Composable
private fun changeSentence(f: HearingFigures): String {
    val diff = f.weekChange.roundToInt()
    val previous = f.previousWeekPercent.roundToInt()
    return when {
        abs(f.weekChange) < 3 -> stringResource(R.string.trends_change_same, previous)
        diff > 0 -> pluralStringResource(R.plurals.trends_change_up, diff, diff, previous)
        else -> pluralStringResource(R.plurals.trends_change_down, -diff, -diff, previous)
    }
}

// ------------------------------------------------------------------------------------ charts

@Composable
private fun Block(title: String, note: String?, content: @Composable () -> Unit) {
    Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Vox.colors.text, modifier = Modifier.semantics { heading() })
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
        content()
    }
}

@Composable
private fun DayChart(s: PhoneUiState) {
    val today = s.history.ambient.firstOrNull()
    val hourly = today?.hourly ?: List(24) { 0f }
    val loudest = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0f }
    val nowHour = (((s.nowMs / 60_000L) - s.history.todayStartMinute) / 60).toInt().takeIf { it in 0..23 }
    Block(stringResource(R.string.today_hourly), stringResource(R.string.chart_85_note)) {
        HourColumns(
            hourly, nowHour,
            if (loudest != null) stringResource(R.string.chart_hourly_cd, loudest.value.roundToInt(), "%02d:00".format(loudest.index))
            else stringResource(R.string.chart_hourly_empty_cd),
        )
        ZoneLegend(hourly.filter { it > 0f }.map { RiskZone.of(it.toDouble()) }.toSet())
    }
}

@Composable
private fun WeekChart(f: HearingFigures) {
    val days = f.lastDays(7)
    val today = stringResource(R.string.stat_today)
    val labels = days.mapIndexed { i, d -> if (i == days.lastIndex) today else dayText(d.dayEpoch, "EEE") }
    val table = days.mapIndexed { i, d ->
        stringResource(R.string.chart_day_cd, if (i == days.lastIndex) today else dayText(d.dayEpoch, "EEEE"), if (d.measured) d.percent.roundToInt().toString() else stringResource(R.string.value_none))
    }.joinToString("; ")
    Block(stringResource(R.string.trends_daily_dose), stringResource(R.string.trends_daily_note)) {
        DayColumns(days, labels, stringResource(R.string.chart_bars_cd, 7, table))
        ZoneLegend(days.filter { it.measured }.map { RiskZone.ofPercent(it.percent) }.toSet())
    }
}

@Composable
private fun MonthChart(f: HearingFigures) {
    val days = f.lastDays(30)
    val words = zoneWords()
    Block(stringResource(R.string.trends_month_title), stringResource(R.string.trends_month_note)) {
        HeatCalendar(days, zoneWordFor = words, noData = stringResource(R.string.zone_not_measured), todayLabel = stringResource(R.string.stat_today))
        ZoneLegend(RiskZone.entries.toSet())
    }
}

@Composable
private fun zoneWords(): (RiskZone) -> String {
    val words = RiskZone.entries.associateWith { stringResource(it.wordRes()) }
    return { z -> words.getValue(z) }
}

@Composable
private fun RollingSection(f: HearingFigures, count: Int) {
    val values = f.rolling.takeLast(count)
    Block(stringResource(R.string.trends_budget), stringResource(R.string.trends_budget_note)) {
        RollingLine(values, stringResource(R.string.chart_line_cd, (values.lastOrNull() ?: 0.0).roundToInt()))
    }
}

@Composable
private fun HeadphoneShare(f: HearingFigures) {
    val c = Vox.colors
    Row(Modifier.contentWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(VoxIcons.Headphones, null, tint = c.secondary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Text(
            stringResource(R.string.trends_headphone_share, (f.headphoneShare * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium, color = c.text2,
        )
    }
}

/** The Hearing Care Score, a supporting line: it was the Home hero before, and it is not a hearing test. */
@Composable
private fun ScoreLine(s: PhoneUiState) {
    Text(
        stringResource(R.string.trends_score_line, s.score),
        style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2, modifier = Modifier.contentWidth().padding(top = 4.dp),
    )
}
