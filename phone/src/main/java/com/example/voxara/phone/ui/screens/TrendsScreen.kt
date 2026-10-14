package com.example.voxara.phone.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.voxara.phone.R
import com.example.voxara.phone.model.rollingWeeklyPercent
import com.example.voxara.phone.model.weekCompare
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.BudgetLine
import com.example.voxara.phone.ui.components.ChoiceRow
import com.example.voxara.phone.ui.components.DoseBars
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.HourlyChart
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.SplitBar
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TrendRange(val days: Int) { DAY(1), WEEK(7), MONTH(30) }

@Composable
fun TrendsScreen(s: PhoneUiState, onRefresh: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(TrendRange.WEEK) }
    VoxScreen(stringResource(R.string.trends_title), refreshing = s.syncing, onRefresh = onRefresh) {
        item {
            Group(Modifier.contentWidth()) {
                ChoiceRow(
                    title = null,
                    options = listOf(
                        TrendRange.DAY to stringResource(R.string.range_day),
                        TrendRange.WEEK to stringResource(R.string.range_week),
                        TrendRange.MONTH to stringResource(R.string.range_month),
                    ),
                    selected = range,
                    onSelect = { range = it },
                )
            }
        }
        if (!s.loaded) {
            items(3) { SkeletonCard(Modifier.contentWidth(), lines = 4) }
            return@VoxScreen
        }
        item { FreshnessLine(s.lastSyncMs, s.nowMs, Modifier.contentWidth()) }
        val amb = s.history.ambient
        val hp = s.history.headphone
        if (s.history.isEmpty) {
            item { EmptyState(VoxIcons.Chart, stringResource(R.string.trends_title), stringResource(R.string.trends_empty), Modifier.contentWidth()) }
            return@VoxScreen
        }

        item {
            VoxCard(Modifier.contentWidth()) {
                if (range == TrendRange.DAY) {
                    val today = amb.firstOrNull()
                    Text(stringResource(R.string.today_hourly), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                    val loudest = today?.hourly?.withIndex()?.maxByOrNull { it.value }
                    HourlyChart(
                        today?.hourly ?: List(24) { 0f }, null,
                        if (loudest != null && loudest.value > 0f)
                            stringResource(R.string.chart_hourly_cd, loudest.value.roundToInt(), "%02d:00".format(loudest.index))
                        else stringResource(R.string.chart_hourly_empty_cd),
                    )
                } else {
                    val days = amb.take(range.days).reversed()
                    val values = days.map { it.nioshDosePercent }
                    val fmt = SimpleDateFormat(if (range == TrendRange.WEEK) "EEE" else "d", Locale.getDefault())
                    val labels = if (range == TrendRange.WEEK) days.map { fmt.format(Date(it.dayEpoch * 86_400_000L + 43_200_000L)) }
                    else listOf(days.first(), days[days.size / 2], days.last()).map { fmt.format(Date(it.dayEpoch * 86_400_000L + 43_200_000L)) }
                    Text(stringResource(R.string.trends_daily_dose), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                    DoseBars(
                        values, labels,
                        stringResource(R.string.chart_bars_cd, range.days, (values.maxOrNull() ?: 0.0).roundToInt()),
                    )
                }
            }
        }

        val span = if (range == TrendRange.MONTH) 30 else 7
        val budget = rollingWeeklyPercent(amb, s.settings.sensitiveListener, minOf(span, maxOf(1, amb.size - 6)))
        item {
            VoxCard(Modifier.contentWidth()) {
                Text(stringResource(R.string.trends_budget), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                Text(stringResource(R.string.trends_budget_note), style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
                BudgetLine(budget, stringResource(R.string.chart_line_cd, (budget.lastOrNull() ?: 0.0).roundToInt()))
            }
        }

        val cmp = weekCompare(amb, hp, s.settings.sensitiveListener)
        item {
            VoxCard(Modifier.contentWidth()) {
                Text(stringResource(R.string.trends_compare), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                Text(
                    stringResource(R.string.trends_compare_value, cmp.thisWeek.roundToInt(), cmp.previousWeek.roundToInt()),
                    style = MaterialTheme.typography.displaySmall, color = Vox.colors.text,
                )
                val diff = cmp.change.roundToInt()
                Text(
                    when {
                        abs(diff) < 3 -> stringResource(R.string.trends_compare_same)
                        diff > 0 -> stringResource(R.string.trends_compare_up, diff)
                        else -> stringResource(R.string.trends_compare_down, -diff)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2,
                )
            }
        }
        item {
            VoxCard(Modifier.contentWidth()) {
                Text(stringResource(R.string.trends_split), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                SplitBar(cmp.headphoneShare)
            }
        }
    }
}
