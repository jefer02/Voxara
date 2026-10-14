package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.voxara.core.alerts.AlertRecord
import com.example.voxara.core.alerts.Tier
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.averageDba
import com.example.voxara.phone.model.loudestMinuteDba
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.HourlyChart
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.dateText
import com.example.voxara.phone.ui.isLevel
import com.example.voxara.phone.ui.labelRes
import com.example.voxara.phone.ui.theme.Vox
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
            val hourly = today.hourly
            val loudestHour = hourly.withIndex().maxByOrNull { it.value }
            val nowHour = (((s.nowMs / 60_000L) - s.history.todayStartMinute) / 60).toInt().takeIf { it in 0..23 }
            item {
                val cd = if (loudestHour != null && loudestHour.value > 0f)
                    stringResource(R.string.chart_hourly_cd, loudestHour.value.roundToInt(), "%02d:00".format(loudestHour.index))
                else stringResource(R.string.chart_hourly_empty_cd)
                VoxCard(Modifier.contentWidth()) {
                    Text(stringResource(R.string.today_hourly), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                    HourlyChart(hourly, nowHour, cd)
                }
            }
            item {
                Group(Modifier.contentWidth()) {
                    Metric(stringResource(R.string.today_dose), stringResource(R.string.value_percent, s.head.dosePercent.roundToInt()))
                    GroupDivider()
                    Metric(stringResource(R.string.today_avg), today.averageDba()?.let { stringResource(R.string.value_dba, it.roundToInt()) } ?: stringResource(R.string.value_none))
                    GroupDivider()
                    Metric(stringResource(R.string.today_loudest), s.history.todayMinutes.loudestMinuteDba()?.let { stringResource(R.string.value_dba, it.roundToInt()) } ?: stringResource(R.string.value_none))
                    GroupDivider()
                    Metric(stringResource(R.string.today_measured), formatClock(today.measuredMinutes))
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

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Vox.colors.text, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clearAndSetSemantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VoxIcons.zone(zone), contentDescription = null, tint = c.zoneInk(zone), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text("$tier · $time · $state", style = MaterialTheme.typography.bodySmall, color = c.text2)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (a.level > 0) Text(stringResource(R.string.alert_level, a.level), style = MaterialTheme.typography.labelSmall, color = c.text2)
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
