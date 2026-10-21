package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.link.LinkState
import com.example.voxara.phone.R
import com.example.voxara.phone.model.Freshness
import com.example.voxara.phone.model.zoneSegments
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneRoutes
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.CapsuleMeter
import com.example.voxara.phone.ui.components.HeroCard
import com.example.voxara.phone.ui.components.IconTile
import com.example.voxara.phone.ui.components.ScaleMark
import com.example.voxara.phone.ui.components.ZoneBadge
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupRow
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.StatCard
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.WatchModePill
import com.example.voxara.phone.ui.components.ZoneStrip
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.zoneWordText
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.isLevel
import com.example.voxara.phone.ui.theme.EyebrowStyle
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.titleRes
import com.example.voxara.ui.design.VoxIcons
import kotlin.math.roundToInt

@Composable
fun HomeScreen(s: PhoneUiState, actions: PhoneActions, navigate: (String) -> Unit) {
    // The editorial label over the title says how the watch link is (the template's "Wear OS Sync").
    VoxScreen(
        title = stringResource(R.string.home_title), eyebrow = linkText(s).first,
        refreshing = s.syncing, onRefresh = actions.onRefresh,
    ) {
        item { LinkChip(s, onClick = { navigate(PhoneRoutes.WATCH) }) }

        if (!s.loaded) {
            items(3) { SkeletonCard(Modifier.contentWidth()) }
            return@VoxScreen
        }
        if (s.noData) {
            item { NoDataCard(s, actions, navigate, Modifier.contentWidth()) }
            return@VoxScreen
        }

        val head = s.head
        item { HomeHero(s, Modifier.contentWidth()) }
        if (s.status?.calibrated == false) {
            item {
                Banner(
                    VoxIcons.Tune, stringResource(R.string.uncalibrated_title), stringResource(R.string.uncalibrated_body),
                    tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                    action = stringResource(R.string.action_open_calibration), onAction = actions.onOpenCalibration,
                )
            }
        }
        if (Freshness.of(head.asOfMs, s.nowMs) == Freshness.STALE) {
            item {
                Banner(
                    VoxIcons.Clock, stringResource(R.string.fresh_stale_hint), freshnessTextPlain(s),
                    tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                    action = stringResource(R.string.action_sync_now), onAction = actions.onRefresh,
                )
            }
        }
        item {
            StatRow(Modifier.contentWidth()) { m ->
                StatCard(
                    VoxIcons.Waves, stringResource(R.string.stat_today), head.dosePercent.roundToInt(),
                    stringResource(R.string.stat_today_sub), head.asOfMs, s.nowMs, m, onClick = { navigate(PhoneRoutes.TODAY) },
                )
                StatCard(
                    VoxIcons.Chart, stringResource(R.string.stat_week), head.weeklyPercent.roundToInt(),
                    stringResource(R.string.stat_week_sub), head.asOfMs, s.nowMs, m, onClick = { navigate(PhoneRoutes.TRENDS) },
                )
                StatCard(
                    VoxIcons.Headphones, stringResource(R.string.stat_headphones), head.headphoneWeeklyPercent.roundToInt(),
                    stringResource(R.string.stat_headphones_sub), head.asOfMs, s.nowMs, m, onClick = { navigate(PhoneRoutes.HEADPHONES) },
                )
            }
        }
        item { TodayStripCard(s, Modifier.contentWidth(), onClick = { navigate(PhoneRoutes.TODAY) }) }
        item { LastAlertCard(s, Modifier.contentWidth(), onClick = { navigate(PhoneRoutes.ALERTS) }) }
        item {
            Group(Modifier.contentWidth()) {
                GroupRow(stringResource(R.string.see_today), icon = VoxIcons.Calendar, onClick = { navigate(PhoneRoutes.TODAY) })
                GroupDivider()
                GroupRow(stringResource(R.string.see_alerts), icon = VoxIcons.Bell, onClick = { navigate(PhoneRoutes.ALERTS) })
                GroupDivider()
                GroupRow(stringResource(R.string.see_headphones), icon = VoxIcons.Headphones, onClick = { navigate(PhoneRoutes.HEADPHONES) })
            }
        }
    }
}

@Composable
private fun freshnessTextPlain(s: PhoneUiState): String =
    s.lastSyncMs?.let { stringResource(R.string.last_synced, dateTimeText(it, s.nowMs)) } ?: stringResource(R.string.fresh_never)

/** Three stats side by side when there is room (tablet, unfolded), stacked otherwise. */
@Composable
private fun StatRow(modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.weight(1f)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.fillMaxWidth()) }
        }
    }
}

/** The link in words, with the zone whose icon goes with it (null while checking). */
@Composable
private fun linkText(s: PhoneUiState): Pair<String, RiskZone?> = when (val l = s.link) {
    LinkState.Checking -> stringResource(R.string.link_checking) to null
    LinkState.WearApiMissing -> stringResource(R.string.link_api_missing) to RiskZone.LOUD
    LinkState.NoWatch -> stringResource(R.string.link_no_watch) to RiskZone.MODERATE
    is LinkState.NotInstalled -> stringResource(R.string.link_not_installed) to RiskZone.MODERATE
    is LinkState.Linked -> stringResource(R.string.link_linked, l.watch.name) to RiskZone.OK
    is LinkState.Problem -> stringResource(R.string.link_problem) to RiskZone.LOUD
}

/** The watch connection at a glance; tapping it opens the Watch tab. */
@Composable
fun LinkChip(s: PhoneUiState, onClick: () -> Unit) {
    val c = Vox.colors
    val (text, zone) = linkText(s)
    val sub = if (s.syncing) stringResource(R.string.syncing)
    else s.lastSyncMs?.let { stringResource(R.string.last_synced, dateTimeText(it, s.nowMs)) } ?: stringResource(R.string.fresh_never)
    Surface(
        onClick = onClick, shape = CircleShape, color = c.surface, contentColor = c.text,
        border = BorderStroke(1.dp, c.outline), modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (zone == null) VoxIcons.Watch else VoxIcons.zone(zone), contentDescription = null,
                tint = if (zone == null) c.text2 else c.zoneInk(zone), modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = c.text2, maxLines = 1)
            }
        }
    }
}

/**
 * THE HERO — the template's hero card on Voxara's data: Tono's status line, the Hearing Care Score
 * in Mono, today's dose as the status badge and the 26-capsule meter (0–120 %, Voxara's percent
 * zones), wave lines behind whose height follows today's dose.
 */
@Composable
private fun HomeHero(s: PhoneUiState, modifier: Modifier) {
    val c = Vox.colors
    val tono = s.settings.tonoEnabled
    val status = s.coach.reply?.status?.takeIf { tono } ?: stringResource(R.string.home_status_fallback)
    val scoreCd = stringResource(R.string.home_score_cd, s.score)
    val dose = s.head.dosePercent
    val doseZone = RiskZone.ofPercent(dose)
    HeroCard(intensity = (dose / HERO_DOSE_SCALE).toFloat(), zone = doseZone, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            // The brand is always Voxara; Tono is only the voice of the status line under it.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_title).uppercase(), style = EyebrowStyle, color = c.brand)
                if (tono) {
                    Text(
                        stringResource(R.string.tono_says, stringResource(R.string.tono_title)).uppercase(),
                        style = EyebrowStyle, color = c.text3,
                    )
                }
            }
            ZoneBadge(doseZone)
        }
        Text(status, style = MaterialTheme.typography.headlineMedium, color = c.text)
        if (tono && s.coach.source == ReplySource.CLOUD_AI) {
            Text(stringResource(R.string.tono_source_ai), style = MaterialTheme.typography.labelSmall, color = c.text3)
        }
        // The watch's situation with its headphones layer ("Auto · Street + Headphones").
        WatchModePill(s)
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = scoreCd },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${s.score}", style = MaterialTheme.typography.displayMedium, color = c.text)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_score).uppercase(), style = EyebrowStyle, color = c.text3)
                Text(stringResource(R.string.home_score_sub), style = MaterialTheme.typography.bodySmall, color = c.text2)
            }
        }
        CapsuleMeter(
            fraction = (dose / HERO_DOSE_SCALE).toFloat(),
            zoneAt = { RiskZone.ofPercent(it * HERO_DOSE_SCALE) },
            // Most important first: the limit, then the start, then the zone boundaries.
            marks = listOf(100, 0, 50, 80).map { ScaleMark((it / HERO_DOSE_SCALE).toFloat(), stringResource(R.string.value_percent, it)) },
            description = stringResource(R.string.hero_dose_cd, dose.roundToInt(), zoneWordText(doseZone)),
        )
    }
}

/** The capsule meter's full scale: 0–120 % of the daily dose, so 100 % sits clearly inside it. */
private const val HERO_DOSE_SCALE = 120.0

@Composable
private fun TodayStripCard(s: PhoneUiState, modifier: Modifier, onClick: () -> Unit) {
    val c = Vox.colors
    val h = s.history
    val nowMinute = ((s.nowMs / 60_000L) - h.todayStartMinute).toInt().coerceIn(0, 1440)
    val segments = zoneSegments(h.todayMinutes, h.todayStartMinute, untilMinute = maxOf(1, nowMinute))
    val measured = h.ambient.firstOrNull()?.measuredMinutes ?: 0.0
    val parts = segments.filter { it.zone != null }.takeLast(4).map {
        stringResource(R.string.zone_strip_part, zoneWordText(it.zone), clock(it.fromMinute), clock(it.toMinute))
    }
    val cd = stringResource(
        R.string.zone_strip_cd,
        if (parts.isEmpty()) stringResource(R.string.zone_strip_none) else parts.joinToString("; "),
    )
    VoxCard(modifier, onClick = onClick) {
        Text(stringResource(R.string.zone_strip_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        ZoneStrip(segments, nowMinute, cd)
        Text(
            if (measured > 0) stringResource(R.string.coverage, formatClock(measured)) else stringResource(R.string.coverage_none),
            style = MaterialTheme.typography.bodyMedium, color = c.text2,
        )
        FreshnessLine(s.lastSyncMs, s.nowMs)
    }
}

private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)

@Composable
private fun LastAlertCard(s: PhoneUiState, modifier: Modifier, onClick: () -> Unit) {
    val c = Vox.colors
    val last = s.history.alerts.firstOrNull()
    VoxCard(modifier, onClick = onClick) {
        Text(stringResource(R.string.last_alert_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        if (last == null) {
            Text(stringResource(R.string.last_alert_none), style = MaterialTheme.typography.bodyMedium, color = c.text2)
        } else {
            val zone = when (last.tier) {
                com.example.voxara.core.alerts.Tier.LIMIT -> RiskZone.DANGEROUS
                com.example.voxara.core.alerts.Tier.WARNING -> RiskZone.LOUD
                com.example.voxara.core.alerts.Tier.ADVISORY -> RiskZone.MODERATE
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(VoxIcons.zone(zone), c.zoneInk(zone))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(last.kind.titleRes()), style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Text(
                        dateTimeText(last.atMs, s.nowMs) + " · " +
                            if (last.kind.isLevel) stringResource(R.string.value_dba, last.value.roundToInt())
                            else stringResource(R.string.value_percent, last.value.roundToInt()),
                        style = MaterialTheme.typography.bodySmall, color = c.text2,
                    )
                }
            }
        }
    }
}

/** Nothing synced yet: say why, with the one action that helps. */
@Composable
private fun NoDataCard(s: PhoneUiState, actions: PhoneActions, navigate: (String) -> Unit, modifier: Modifier) {
    when (val l = s.link) {
        LinkState.NoWatch, LinkState.WearApiMissing -> EmptyState(
            VoxIcons.Watch, stringResource(R.string.link_no_watch),
            stringResource(if (l == LinkState.NoWatch) R.string.link_no_watch_body else R.string.link_problem_no_api) +
                "\n\n" + stringResource(R.string.link_explain),
            modifier, stringResource(R.string.action_open_wearable), actions.onOpenWearableApp,
        )
        is LinkState.NotInstalled -> EmptyState(
            VoxIcons.Watch, stringResource(R.string.link_not_installed),
            stringResource(R.string.link_not_installed_body, l.watches.first().name),
            modifier, stringResource(R.string.action_install_on_watch), { actions.onInstallOnWatch(l.watches.first().id) },
        )
        else -> EmptyState(
            VoxIcons.Waves, stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body),
            modifier, stringResource(R.string.action_sync_now), actions.onRefresh,
        )
    }
}
