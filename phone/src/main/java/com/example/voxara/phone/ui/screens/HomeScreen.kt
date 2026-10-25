package com.example.voxara.phone.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.link.LinkState
import com.example.voxara.phone.R
import com.example.voxara.phone.model.Freshness
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneRoutes
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.ContentMaxWidth
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FitFigure
import com.example.voxara.phone.ui.components.HourColumns
import com.example.voxara.phone.ui.components.IconTile
import com.example.voxara.phone.ui.components.LinkButton
import com.example.voxara.phone.ui.components.OrbitRing
import com.example.voxara.phone.ui.components.ShareTile
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.TouchTarget
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.WatchModePill
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.pressFeedback
import com.example.voxara.phone.ui.components.sharedAcrossScreens
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.isLevel
import com.example.voxara.phone.ui.relativeTimeText
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.wordsStyle
import com.example.voxara.phone.ui.titleRes
import com.example.voxara.phone.ui.labelRes
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes
import kotlin.math.roundToInt

/**
 * HOME — the one question: how is my hearing today? A ring (the watch's, enlarged) holds today's share of the allowance
 * as a figure, its caption and the zone as its own label; Tono's sentence sits under it and opens Tono. Then the last 7
 * days and the headphones as two quiet tiles, the day hour by hour, and the last alert. The ring opens Today.
 */
@Composable
fun HomeScreen(s: PhoneUiState, actions: PhoneActions, navigate: (String) -> Unit) {
    BoxWithConstraints {
        // Unfolded and on tablets: the ring and its sentence on the left, the rest on the right (two panes).
        val wide = maxWidth >= 840.dp
        VoxScreen(
            title = stringResource(R.string.nav_home), largeTitle = false, refreshing = s.syncing, onRefresh = actions.onRefresh,
        ) {
            item { HomeHeader(s, actions, navigate, Modifier.widthIn(max = if (wide) 1100.dp else ContentMaxWidth)) }
            item { BatteryGuide(s, actions, Modifier.contentWidth()) }

            if (!s.loaded) {
                items(2) { SkeletonCard(Modifier.contentWidth()) }
                return@VoxScreen
            }
            if (s.noData) {
                item { NoDataCard(s, actions, Modifier.contentWidth()) }
                return@VoxScreen
            }

            val head = s.head
            val figures = s.figures
            if (Freshness.of(head.asOfMs, s.nowMs) == Freshness.STALE) {
                item {
                    Banner(
                        VoxIcons.Clock, stringResource(R.string.fresh_stale_hint), freshnessTextPlain(s),
                        tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                        action = stringResource(R.string.action_sync_now), onAction = actions.onRefresh,
                    )
                }
            }
            val toToday = { navigate(PhoneRoutes.TODAY) }
            if (wide) {
                item {
                    Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f)) { RingHero(s, figures.todayPercent, toToday, { navigate(PhoneRoutes.TONO) }, actions) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Tiles(s, figures.surroundingsWeekPercent, figures.headphonesWeekPercent, navigate)
                            HoursSection(s, Modifier.fillMaxWidth(), toToday)
                            HeadphoneLiveCards(s, actions, onlyWhenActive = true)
                            LastAlertRow(s, Modifier.fillMaxWidth()) { navigate(PhoneRoutes.ALERTS) }
                        }
                    }
                }
            } else {
                item { RingHero(s, figures.todayPercent, toToday, { navigate(PhoneRoutes.TONO) }, actions) }
                item { Tiles(s, figures.surroundingsWeekPercent, figures.headphonesWeekPercent, navigate) }
                item { HoursSection(s, Modifier.contentWidth(), toToday) }
                headphoneLiveItems(s, actions, onlyWhenActive = true)
                item { LastAlertRow(s, Modifier.contentWidth()) { navigate(PhoneRoutes.ALERTS) } }
            }
        }
    }
}

@Composable
private fun freshnessTextPlain(s: PhoneUiState): String =
    s.lastSyncMs?.let { stringResource(R.string.last_synced, dateTimeText(it, s.nowMs)) } ?: stringResource(R.string.fresh_never)

// ------------------------------------------------------------------------------------ header

/** The link in words, with the zone whose icon goes with it (null while checking). */
@Composable
private fun linkText(s: PhoneUiState): Pair<String, RiskZone?> = when (val l = s.link) {
    LinkState.Checking -> stringResource(R.string.link_checking) to null
    LinkState.WearApiMissing -> stringResource(R.string.link_api_missing) to RiskZone.LOUD
    LinkState.NoWatch -> stringResource(R.string.link_no_watch) to RiskZone.MODERATE
    is LinkState.NotInstalled -> stringResource(R.string.link_not_installed) to RiskZone.MODERATE
    is LinkState.Linked -> l.watch.name to RiskZone.OK
    is LinkState.Problem -> stringResource(R.string.link_problem) to RiskZone.LOUD
}

/**
 * One status line for the watch: its name and when it last reported; a problem replaces it with the state in words
 * (tap: the Watch tab). Beside it the sync button, then the watch's situation with its headphones layer.
 */
@Composable
private fun HomeHeader(s: PhoneUiState, actions: PhoneActions, navigate: (String) -> Unit, modifier: Modifier) {
    val c = Vox.colors
    val (state, zone) = linkText(s)
    val sub = if (s.syncing) stringResource(R.string.syncing)
    else linkHealthText(s.linkHealth, s.nowMs)
        ?: freshnessText(s.lastSyncMs, s.nowMs)
    val line = if (s.link is LinkState.Linked) stringResource(R.string.home_link_line, state, sub) else state
    val style = MaterialTheme.typography.labelLarge
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val reduce = LocalReduceMotion.current
    val spin = if (s.syncing && !reduce) {
        val t = rememberInfiniteTransition(label = "sync")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle").value
    } else 0f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Row(
                Modifier.weight(1f).heightIn(min = TouchTarget).clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_watch_tab)) { navigate(PhoneRoutes.WATCH) },
                verticalAlignment = Alignment.Top,
            ) {
                // the icon sits on the first line of text, whatever the font scale
                Box(Modifier.padding(top = (TouchTarget - lineHeight) / 2).size(lineHeight), contentAlignment = Alignment.Center) {
                    Icon(
                        if (zone == null) VoxIcons.Watch else VoxIcons.zone(zone), contentDescription = null,
                        tint = if (zone == null) c.text2 else c.zoneInk(zone), modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(vertical = (TouchTarget - lineHeight) / 2)) {
                    Text(line, style = style, color = c.text)
                    if (s.link !is LinkState.Linked && s.link != LinkState.Checking) {
                        Text(sub, style = MaterialTheme.typography.labelMedium, color = c.text2)
                    }
                }
            }
            IconButton(onClick = actions.onRefresh, enabled = !s.syncing, modifier = Modifier.size(TouchTarget)) {
                Icon(VoxIcons.Refresh, contentDescription = stringResource(R.string.action_sync_now), tint = c.text2, modifier = Modifier.size(22.dp).rotate(spin))
            }
        }
        WatchModePill(s)
    }
}

// ------------------------------------------------------------------------------------ the ring

@Composable
private fun RingHero(s: PhoneUiState, percent: Double, onToday: () -> Unit, onTono: () -> Unit, actions: PhoneActions) {
    val c = Vox.colors
    val zone = RiskZone.ofPercent(percent)
    val value = percent.roundToInt()
    val zoneText = stringResource(zone.wordRes())
    val caption = stringResource(R.string.home_of_allowance)
    val cd = stringResource(R.string.home_ring_cd, value, zoneText)
    val source = remember { MutableInteractionSource() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(Modifier.contentWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val ring = minOf(maxWidth * 0.82f, 340.dp)
            // the room inside the 250° arc, less its stroke and margin
            val inner = ring - 56.dp
            val captionPx = measurer.measure(caption, wordsStyle(14), maxLines = 1, softWrap = false, density = density).size.width
            val zonePx = measurer.measure(zoneText, wordsStyle(17, FontWeight.Bold), maxLines = 1, softWrap = false, density = density).size.width
            val limit = with(density) { (inner * 0.86f).toPx() }
            val inside = captionPx <= limit && zonePx + with(density) { 28.dp.toPx() } <= limit
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OrbitRing(
                    percent,
                    Modifier
                        .width(ring)
                        .sharedAcrossScreens("ring")
                        .pressFeedback(source, 0.98f)
                        .semantics(mergeDescendants = true) { contentDescription = cd }
                        .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.action_open_today), onClick = onToday),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        FitFigure(value, maxWidth = inner * 0.9f, color = c.text, spoken = "", appear = true)
                        if (inside) {
                            Text(caption, style = wordsStyle(14), color = c.text2, textAlign = TextAlign.Center)
                            Spacer(Modifier.size(12.dp))
                            ZoneWord(zone, sizeSp = 17)
                        }
                    }
                }
                if (!inside) {
                    Spacer(Modifier.size(8.dp))
                    ZoneWord(zone, sizeSp = 17)
                    Text(caption, style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center)
                }
            }
        }
        TonoSentence(s, onTono)
        UncalibratedLine(s, actions)
    }
}

/** Tono's sentence in the gap under the ring; a tap opens Tono. With Tono off, the plain line the watch uses. */
@Composable
private fun TonoSentence(s: PhoneUiState, onTono: () -> Unit) {
    val c = Vox.colors
    val tono = s.settings.tonoEnabled
    val text = s.coach.reply?.status?.takeIf { tono } ?: stringResource(R.string.home_status_fallback)
    val body = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = wordsStyle(20, FontWeight.ExtraBold, 1.25f), color = c.text, textAlign = TextAlign.Center)
            if (tono) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (s.coach.source == ReplySource.CLOUD_AI) R.string.home_ask_tono_ai else R.string.home_ask_tono),
                        style = MaterialTheme.typography.labelMedium, color = c.brand,
                    )
                    Icon(VoxIcons.Forward, null, tint = c.brand, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
    if (tono) {
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = TouchTarget).pressFeedback(source, 0.98f)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.action_open_tono), onClick = onTono),
            contentAlignment = Alignment.Center,
        ) { body() }
    } else {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { body() }
    }
}

/** The standing note while the watch is uncalibrated: never hidden, one compact line with its action. */
@Composable
private fun UncalibratedLine(s: PhoneUiState, actions: PhoneActions) {
    if (s.status?.calibrated != false) return
    val c = Vox.colors
    val stacked = LocalDensity.current.fontScale > 1.15f
    val note = @Composable { m: Modifier ->
        Text(stringResource(R.string.uncalibrated_compact), style = MaterialTheme.typography.bodySmall, color = c.text2, modifier = m)
    }
    if (stacked) {
        // large text: the note across the full width, its action under it
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(VoxIcons.Tune, null, tint = c.caution, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                note(Modifier.weight(1f))
            }
            LinkButton(stringResource(R.string.action_calibrate), actions.onOpenCalibration)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(VoxIcons.Tune, null, tint = c.caution, modifier = Modifier.size(18.dp))
            note(Modifier.weight(1f))
            LinkButton(stringResource(R.string.action_calibrate), actions.onOpenCalibration)
        }
    }
}

// ------------------------------------------------------------------------------------ tiles

/** The last 7 days and the headphones: side by side while there is room for the figures, one under the other otherwise. */
@Composable
private fun Tiles(s: PhoneUiState, week: Double, headphones: Double, navigate: (String) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.contentWidth()) {
        val sideBySide = density.fontScale <= 1.15f && (maxWidth - 12.dp) / 2 >= 140.dp
        val weekTile = @Composable { m: Modifier ->
            ShareTile(
                stringResource(R.string.tile_week), stringResource(R.string.tile_week_caption), week,
                VoxIcons.Chart, Vox.colors.brand,
                stringResource(R.string.tile_week_cd, week.roundToInt(), stringResource(RiskZone.ofPercent(week).wordRes())),
                m, onClick = { navigate(PhoneRoutes.TRENDS) },
            )
        }
        val hpTile = @Composable { m: Modifier ->
            ShareTile(
                stringResource(R.string.tile_headphones), stringResource(R.string.tile_headphones_caption), headphones,
                VoxIcons.Headphones, Vox.colors.secondary,
                stringResource(R.string.tile_headphones_cd, headphones.roundToInt(), stringResource(RiskZone.ofPercent(headphones).wordRes())),
                m, onClick = { navigate(PhoneRoutes.HEADPHONES) },
            )
        }
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                weekTile(Modifier.weight(1f)); hpTile(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                weekTile(Modifier.fillMaxWidth()); hpTile(Modifier.fillMaxWidth())
            }
        }
    }
}

// ------------------------------------------------------------------------------------ the day

@Composable
private fun HoursSection(s: PhoneUiState, modifier: Modifier, onToday: () -> Unit) {
    val c = Vox.colors
    val today = s.history.ambient.firstOrNull()
    val hourly = today?.hourly ?: List(24) { 0f }
    val loudest = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0f }
    val nowHour = (((s.nowMs / 60_000L) - s.history.todayStartMinute) / 60).toInt().takeIf { it in 0..23 }
    val cd = if (loudest != null) stringResource(R.string.chart_hourly_cd, loudest.value.roundToInt(), "%02d:00".format(loudest.index))
    else stringResource(R.string.chart_hourly_empty_cd)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_today), onClick = onToday),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_hours), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
                Text(
                    if (loudest != null) stringResource(R.string.chart_loudest, loudest.value.roundToInt(), "%02d:00".format(loudest.index))
                    else stringResource(R.string.coverage_none),
                    style = MaterialTheme.typography.bodySmall, color = c.text2,
                )
            }
            Icon(VoxIcons.Forward, null, tint = c.text3, modifier = Modifier.size(18.dp))
        }
        HourColumns(hourly, nowHour, cd)
    }
}

// ------------------------------------------------------------------------------------ last alert

@Composable
private fun LastAlertRow(s: PhoneUiState, modifier: Modifier, onClick: () -> Unit) {
    val c = Vox.colors
    val last = s.history.alerts.firstOrNull()
    if (last == null) {
        Text(stringResource(R.string.last_alert_none), style = MaterialTheme.typography.bodyMedium, color = c.text2, modifier = modifier.padding(vertical = 8.dp))
        return
    }
    val zone = last.tier.zone()
    val title = stringResource(last.kind.titleRes())
    val tier = stringResource(last.tier.labelRes())
    val value = if (last.kind.isLevel) stringResource(R.string.value_dba, last.value.roundToInt()) else stringResource(R.string.value_percent, last.value.roundToInt())
    val whenText = relativeTimeText(last.atMs, s.nowMs)
    val cd = stringResource(R.string.last_alert_cd, title, tier, whenText)
    Row(
        modifier.heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.see_alerts), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(VoxIcons.zone(zone), c.zoneInk(zone))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.last_alert_title), style = MaterialTheme.typography.labelMedium, color = c.text2)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            // a level alert says how loud; a percent alert already has its number in the title
            Text(if (last.kind.isLevel) "$tier · $value" else tier, style = MaterialTheme.typography.bodySmall, color = c.text2)
            Text(whenText, style = MaterialTheme.typography.bodySmall, color = c.text2)
        }
        Icon(VoxIcons.Forward, null, tint = c.text3, modifier = Modifier.size(18.dp))
    }
}

/** Nothing synced yet: say why, with the one action that helps. */
@Composable
private fun NoDataCard(s: PhoneUiState, actions: PhoneActions, modifier: Modifier) {
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
