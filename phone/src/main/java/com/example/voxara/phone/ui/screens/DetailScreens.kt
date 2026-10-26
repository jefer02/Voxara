package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.alerts.Tier
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.averageDba
import com.example.voxara.phone.model.cumulativeShare
import com.example.voxara.phone.model.loudestMinuteDba
import com.example.voxara.phone.model.zoneSegments
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.CumulativeLine
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FitFigure
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.HourColumns
import com.example.voxara.phone.ui.components.IconTile
import com.example.voxara.phone.ui.components.OrbitRing
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.ZoneStrip
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.sharedAcrossScreens
import com.example.voxara.phone.ui.components.zoneWordText
import com.example.voxara.phone.ui.dateText
import com.example.voxara.phone.ui.isLevel
import com.example.voxara.phone.ui.labelRes
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxNum
import com.example.voxara.phone.ui.timeText
import com.example.voxara.phone.ui.titleRes
import com.example.voxara.ui.design.VoxIcons
import java.util.Calendar
import kotlin.math.roundToInt

fun Tier.zone(): RiskZone = when (this) {
    Tier.ADVISORY -> RiskZone.MODERATE
    Tier.WARNING -> RiskZone.LOUD
    Tier.LIMIT -> RiskZone.DANGEROUS
}

// ------------------------------------------------------------------------------------ today

/**
 * TODAY — opened from Home's ring (the ring is the same element, so it grows into this screen). The figure, then how
 * the allowance filled up through the day, the day hour by hour and minute by minute, the numbers behind them and
 * today's alerts: everything Home only summarises.
 */
@Composable
fun TodayScreen(s: PhoneUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    val today = s.history.ambient.firstOrNull()
    VoxScreen(stringResource(R.string.today_title), onBack = onBack, refreshing = s.syncing, onRefresh = onRefresh) {
        if (!s.loaded) {
            items(2) { SkeletonCard(Modifier.contentWidth(), lines = 4) }
            return@VoxScreen
        }
        item { FreshnessLine(s.lastSyncMs, s.nowMs, Modifier.contentWidth()) }
        if (today == null || today.measuredMinutes <= 0.0) {
            item { EmptyState(VoxIcons.Waves, stringResource(R.string.today_title), stringResource(R.string.today_empty), Modifier.contentWidth()) }
        } else {
            val figures = s.figures
            val total = figures.todayPercent
            val zone = RiskZone.ofPercent(total)
            item { TodayRing(total, zone) }

            val h = s.history
            val nowMinute = ((s.nowMs / 60_000L) - h.todayStartMinute).toInt().coerceIn(1, 1440)
            val cumulative = cumulativeShare(h.todayMinutes, h.todayStartMinute, nowMinute, total)
            item {
                Section(stringResource(R.string.today_where), stringResource(R.string.today_where_note)) {
                    CumulativeLine(
                        cumulative, nowMinute / 1440f,
                        stringResource(R.string.chart_cumulative_cd, total.roundToInt(), clock(nowMinute)),
                    )
                }
            }

            val hourly = today.hourly
            val loudestHour = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0f }
            val nowHour = (nowMinute / 60).takeIf { it in 0..23 }
            item {
                Section(
                    stringResource(R.string.today_hourly),
                    loudestHour?.let { stringResource(R.string.chart_loudest, it.value.roundToInt(), "%02d:00".format(it.index)) },
                ) {
                    HourColumns(
                        hourly, nowHour,
                        if (loudestHour != null) stringResource(R.string.chart_hourly_cd, loudestHour.value.roundToInt(), "%02d:00".format(loudestHour.index))
                        else stringResource(R.string.chart_hourly_empty_cd),
                    )
                    Text(stringResource(R.string.chart_85_note), style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
                }
            }

            val segments = zoneSegments(h.todayMinutes, h.todayStartMinute, untilMinute = nowMinute)
            item {
                val parts = segments.filter { it.zone != null }.takeLast(4).map {
                    stringResource(R.string.zone_strip_part, zoneWordText(it.zone), clock(it.fromMinute), clock(it.toMinute))
                }
                Section(stringResource(R.string.zone_strip_title), null) {
                    ZoneStrip(
                        segments, nowMinute,
                        stringResource(R.string.zone_strip_cd, if (parts.isEmpty()) stringResource(R.string.zone_strip_none) else parts.joinToString("; ")),
                    )
                }
            }

            item {
                Group(Modifier.contentWidth()) {
                    MetricRow(stringResource(R.string.today_avg), today.averageDba()?.let { stringResource(R.string.value_dba, it.roundToInt()) } ?: stringResource(R.string.value_none))
                    GroupDivider()
                    MetricRow(stringResource(R.string.today_loudest), h.todayMinutes.loudestMinuteDba()?.let { stringResource(R.string.value_dba, it.roundToInt()) } ?: stringResource(R.string.value_none))
                    GroupDivider()
                    MetricRow(stringResource(R.string.today_measured), formatClock(today.measuredMinutes))
                }
            }
        }
        val todays = s.history.alerts.filter { it.atMs >= s.history.todayStartMinute * 60_000L }
        item { GroupHeader(stringResource(R.string.today_alerts), Modifier.contentWidth()) }
        if (todays.isEmpty()) {
            item { Text(stringResource(R.string.today_no_alerts), style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2, modifier = Modifier.contentWidth().padding(horizontal = 16.dp)) }
        } else {
            item { Group(Modifier.contentWidth()) { todays.forEachIndexed { i, a -> if (i > 0) GroupDivider(); AlertRow(a) } } }
        }
    }
}

/** The ring of Home, smaller: the same element (it grows out of Home's ring), the figure, the zone as its own label under it. */
@Composable
private fun TodayRing(total: Double, zone: RiskZone) {
    val c = Vox.colors
    val cd = stringResource(R.string.home_ring_cd, total.roundToInt(), zoneWordText(zone))
    Column(Modifier.contentWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OrbitRing(
            total,
            Modifier.width(220.dp).sharedAcrossScreens("ring").semantics(mergeDescendants = true) { contentDescription = cd },
            strokeWidth = 14.dp,
        ) {
            FitFigure(total.roundToInt(), maxWidth = 150.dp, color = c.text, sizes = listOf(64, 54, 46, 40), spoken = "")
        }
        ZoneWord(zone, sizeSp = 17)
        Text(stringResource(R.string.home_of_allowance), style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center)
    }
}

/** A titled block on the page itself (no card): heading, an optional line under it, then the chart. */
@Composable
private fun Section(title: String, note: String?, content: @Composable () -> Unit) {
    Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Vox.colors.text, modifier = Modifier.semantics { heading() })
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
        content()
    }
}

private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = Vox.colors.text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = VoxNum.medium, color = Vox.colors.text, modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.End)
    }
}

// ------------------------------------------------------------------------------------ alerts

@Composable
fun AlertRow(a: AlertRecord) {
    val c = Vox.colors
    val zone = a.tier.zone()
    val title = stringResource(a.kind.titleRes())
    val tier = stringResource(a.tier.labelRes())
    val value = if (a.kind.isLevel) stringResource(R.string.value_dba, a.value.roundToInt())
    else stringResource(R.string.value_percent, a.value.roundToInt())
    val state = stringResource(
        when {
            a.acknowledged -> R.string.alert_ack
            a.snoozed -> R.string.alert_snoozed
            else -> R.string.alert_open
        }
    )
    val time = timeText(a.atMs)
    val cd = stringResource(R.string.alert_row_cd, tier, title, time, value, state)
    // large text: the value joins the lines under the title instead of squeezing a column beside them
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clearAndSetSemantics { contentDescription = cd },
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
    ) {
        IconTile(VoxIcons.zone(zone), c.zoneInk(zone))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text("$tier · $time · $state", style = MaterialTheme.typography.bodySmall, color = c.text2)
            if (stacked) {
                Text(if (a.level > 0) "$value · " + stringResource(R.string.alert_level, a.level) else value, style = VoxNum.medium, color = c.text)
            }
        }
        if (!stacked) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(value, style = VoxNum.medium, color = c.text)
                if (a.level > 0) Text(stringResource(R.string.alert_level, a.level), style = MaterialTheme.typography.labelSmall, color = c.text2)
            }
        }
    }
}

@Composable
fun AlertsScreen(s: PhoneUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    VoxScreen(stringResource(R.string.alerts_title), onBack = onBack, refreshing = s.syncing, onRefresh = onRefresh) {
        if (!s.loaded) {
            items(2) { SkeletonCard(Modifier.contentWidth()) }
            return@VoxScreen
        }
        item { FreshnessLine(s.lastSyncMs, s.nowMs, Modifier.contentWidth()) }
        if (s.history.alerts.isEmpty()) {
            item { EmptyState(VoxIcons.Bell, stringResource(R.string.alerts_title), stringResource(R.string.alerts_empty), Modifier.contentWidth()) }
            return@VoxScreen
        }
        val byDay = s.history.alerts.groupBy { dayKey(it.atMs) }
        for ((_, alerts) in byDay) {
            item { GroupHeader(dateText(alerts.first().atMs), Modifier.contentWidth()) }
            item { Group(Modifier.contentWidth()) { alerts.forEachIndexed { i, a -> if (i > 0) GroupDivider(); AlertRow(a) } } }
        }
    }
}

private fun dayKey(ms: Long): Int = Calendar.getInstance().run {
    timeInMillis = ms
    get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
}
