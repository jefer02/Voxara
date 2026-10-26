package com.example.voxara.phone.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.example.voxara.phone.ui.components.DoseRing
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.FitFigure
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.HeroCard
import com.example.voxara.phone.ui.components.HourColumns
import com.example.voxara.phone.ui.components.IconTile
import com.example.voxara.phone.ui.components.LinkButton
import com.example.voxara.phone.ui.components.RowIconSize
import com.example.voxara.phone.ui.components.RowMinHeight
import com.example.voxara.phone.ui.components.ShareTile
import com.example.voxara.phone.ui.components.SharedKeys
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.TouchTarget
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.WatchModePill
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.entrance
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.isLevel
import com.example.voxara.phone.ui.labelRes
import com.example.voxara.phone.ui.relativeTimeText
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxSpace
import com.example.voxara.phone.ui.theme.wordsStyle
import com.example.voxara.phone.ui.titleRes
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes
import kotlin.math.roundToInt

/** The key [entrance] remembers Home's first load by. */
private const val SCREEN = "home"

/**
 * HOME — the one question: how is my hearing today? (docs/design/PHONE_PREMIUM.md) A large title with the watch's line
 * under it, then ONE hero: the dose card, today's share of the limit on the watch's ring (one arc from 12 o'clock,
 * clockwise, full = 100 %); it opens Today and grows into it. Then quieter grouped sections: Tono's sentence, the last 7
 * days (surroundings and headphones, the headphones card growing into Headphones), the day hour by hour, the last alert.
 */
@Composable
fun HomeScreen(s: PhoneUiState, actions: PhoneActions, navigate: (String) -> Unit) {
    BoxWithConstraints {
        // Unfolded and on tablets: the hero and Tono on the left, the rest on the right (two panes).
        val wide = maxWidth >= 840.dp
        VoxScreen(
            title = stringResource(R.string.nav_home), refreshing = s.syncing, onRefresh = actions.onRefresh,
            header = { WatchLine(s, navigate) },
            actions = { SyncButton(s, actions) },
        ) {
            item(key = "battery") { BatteryGuide(s, actions, Modifier.contentWidth()) }

            if (!s.loaded) {
                items(2, key = { "skeleton-$it" }) { SkeletonCard(Modifier.contentWidth()) }
                return@VoxScreen
            }
            if (s.noData) {
                item(key = "no-data") { NoDataCard(s, actions, Modifier.contentWidth()) }
                return@VoxScreen
            }

            val head = s.head
            val figures = s.figures
            if (Freshness.of(head.asOfMs, s.nowMs) == Freshness.STALE) {
                item(key = "stale") {
                    Banner(
                        VoxIcons.Clock, stringResource(R.string.fresh_stale_hint), freshnessTextPlain(s),
                        tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                        action = stringResource(R.string.action_sync_now), onAction = actions.onRefresh,
                    )
                }
            }
            val toToday = { navigate(PhoneRoutes.TODAY) }
            val toTono = { navigate(PhoneRoutes.TONO) }
            if (wide) {
                item(key = "panes") {
                    Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VoxSpace.xl)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VoxSpace.m)) {
                            DoseCard(s, figures.todayPercent, toToday, actions, Modifier.fillMaxWidth().entrance(SCREEN, 0))
                            TonoRow(s, toTono, Modifier.fillMaxWidth().entrance(SCREEN, 1))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VoxSpace.m)) {
                            WeekSection(figures.surroundingsWeekPercent, figures.headphonesWeekPercent, navigate, Modifier.fillMaxWidth().entrance(SCREEN, 2))
                            HoursSection(s, Modifier.fillMaxWidth().entrance(SCREEN, 3), toToday)
                            HeadphoneLiveCards(s, actions, onlyWhenActive = true)
                            LastAlertSection(s, Modifier.fillMaxWidth().entrance(SCREEN, 4)) { navigate(PhoneRoutes.ALERTS) }
                        }
                    }
                }
            } else {
                item(key = "dose", contentType = "hero") { DoseCard(s, figures.todayPercent, toToday, actions, Modifier.contentWidth().entrance(SCREEN, 0)) }
                item(key = "tono") { TonoRow(s, toTono, Modifier.contentWidth().entrance(SCREEN, 1)) }
                item(key = "week") {
                    WeekSection(figures.surroundingsWeekPercent, figures.headphonesWeekPercent, navigate, Modifier.contentWidth().entrance(SCREEN, 2))
                }
                item(key = "hours") { HoursSection(s, Modifier.contentWidth().entrance(SCREEN, 3), toToday) }
                headphoneLiveItems(s, actions, onlyWhenActive = true)
                item(key = "last-alert") { LastAlertSection(s, Modifier.contentWidth().entrance(SCREEN, 4)) { navigate(PhoneRoutes.ALERTS) } }
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
 * The line under the large title: the watch and when it last reported (a problem replaces it with the state in words), then
 * the watch's situation with its headphones layer. A tap opens the Watch tab.
 */
@Composable
private fun WatchLine(s: PhoneUiState, navigate: (String) -> Unit) {
    val c = Vox.colors
    val (state, zone) = linkText(s)
    val sub = if (s.syncing) stringResource(R.string.syncing)
    else linkHealthText(s.linkHealth, s.nowMs)
        ?: freshnessText(s.lastSyncMs, s.nowMs)
    val line = if (s.link is LinkState.Linked) stringResource(R.string.home_link_line, state, sub) else state
    Column(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_watch_tab)) { navigate(PhoneRoutes.WATCH) },
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
            Icon(
                if (zone == null) VoxIcons.Watch else VoxIcons.zone(zone), contentDescription = null,
                tint = if (zone == null) c.text2 else c.zoneInk(zone), modifier = Modifier.padding(top = 2.dp).size(16.dp),
            )
            Column {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = c.text2)
                if (s.link !is LinkState.Linked && s.link != LinkState.Checking) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = c.text2)
                }
            }
        }
        WatchModePill(s)
    }
}

/** Sync now, in the bar; the arrows turn while a sync runs (read in the draw phase; still with reduced motion). */
@Composable
private fun SyncButton(s: PhoneUiState, actions: PhoneActions) {
    val c = Vox.colors
    val spinning = s.syncing && !LocalReduceMotion.current
    val angle = if (spinning) {
        rememberInfiniteTransition(label = "sync").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    } else null
    IconButton(onClick = actions.onRefresh, enabled = !s.syncing, modifier = Modifier.size(TouchTarget)) {
        Icon(
            VoxIcons.Refresh, contentDescription = stringResource(R.string.action_sync_now), tint = c.text2,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = angle?.value ?: 0f },
        )
    }
}

// ------------------------------------------------------------------------------------ the hero

/**
 * THE DOSE CARD — the screen's one hero. The ring with today's share counting up inside it and what it is a share of, the
 * zone as a word with its icon, and the uncalibrated note while it applies (an estimate says so next to its number). The
 * whole card opens Today and grows into it ([SharedKeys.DOSE]).
 */
@Composable
private fun DoseCard(s: PhoneUiState, percent: Double, onToday: () -> Unit, actions: PhoneActions, modifier: Modifier) {
    val c = Vox.colors
    val zone = RiskZone.ofPercent(percent)
    val value = percent.roundToInt()
    val zoneText = stringResource(zone.wordRes())
    val caption = stringResource(R.string.home_of_allowance)
    val cd = stringResource(R.string.home_ring_cd, value, zoneText)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    HeroCard(modifier.testTag("dose_card"), onClick = onToday, onClickLabel = stringResource(R.string.action_open_today), sharedKey = SharedKeys.DOSE) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val ring = minOf(maxWidth * 0.78f, 248.dp)
            // the room inside the ring, less its stroke and a margin
            val inner = ring - 48.dp
            val captionStyle = wordsStyle(15)
            val captionPx = measurer.measure(caption, captionStyle, maxLines = 1, softWrap = false, density = density).size.width
            val inside = captionPx <= with(density) { (inner * 0.86f).toPx() }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DoseRing(percent, Modifier.width(ring).clearAndSetSemantics { contentDescription = cd }) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        FitFigure(value, maxWidth = inner * 0.86f, color = c.text, sizes = listOf(72, 64, 56, 48, 40), spoken = "", count = true)
                        if (inside) Text(caption, style = captionStyle, color = c.text2, textAlign = TextAlign.Center)
                    }
                }
                if (!inside) {
                    Spacer(Modifier.size(VoxSpace.xs))
                    Text(caption, style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics { })
                }
                Spacer(Modifier.size(VoxSpace.s))
                // the ring's description already says the zone: the word is for the eye
                ZoneWord(zone, Modifier.clearAndSetSemantics { }, sizeSp = 17)
                UncalibratedLine(s, actions)
            }
        }
    }
}

/**
 * The standing note while the watch is uncalibrated, right under the zone: "Sin calibrar · ±3–5 dB" and Calibrar on one row.
 * Never hidden and never cut: the words wrap (two lines at most at font 2.0) and break only between words; when even one
 * unbreakable piece of the note would not fit beside the button (large text, a long language), the button goes under the
 * note instead. Neutral, not a warning.
 */
@Composable
private fun UncalibratedLine(s: PhoneUiState, actions: PhoneActions) {
    if (s.status?.calibrated != false) return
    val c = Vox.colors
    val note = stringResource(R.string.uncalibrated_short)
    val action = stringResource(R.string.action_calibrate)
    val noteStyle = MaterialTheme.typography.bodySmall
    val actionStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val widest = note.split(' ').maxOf { measurer.measure(it, noteStyle, maxLines = 1, softWrap = false, density = density).size.width }
        // the text button: its label plus Material's 12 dp padding on each side
        val button = measurer.measure(action, actionStyle, maxLines = 1, softWrap = false, density = density).size.width +
            with(density) { 24.dp.roundToPx() }
        val sideBySide = widest + button <= constraints.maxWidth
        val text = @Composable { m: Modifier -> Text(note, style = noteStyle, color = c.text2, textAlign = TextAlign.Center, modifier = m) }
        if (sideBySide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                text(Modifier.weight(1f, fill = false))
                LinkButton(action, actions.onOpenCalibration)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                text(Modifier.padding(top = VoxSpace.xxs))
                LinkButton(action, actions.onOpenCalibration)
            }
        }
    }
}

// ------------------------------------------------------------------------------------ Tono

/** Tono's sentence as one grouped row; a tap opens Tono. With Tono off, the plain line the watch uses, not tappable. */
@Composable
private fun TonoRow(s: PhoneUiState, onTono: () -> Unit, modifier: Modifier) {
    val c = Vox.colors
    val tono = s.settings.tonoEnabled
    val text = s.coach.reply?.status?.takeIf { tono } ?: stringResource(R.string.home_status_fallback)
    Group(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (tono) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_tono), onClick = onTono) else Modifier)
                .heightIn(min = RowMinHeight).padding(VoxSpace.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoxSpace.s),
        ) {
            IconTile(if (s.coach.source == ReplySource.CLOUD_AI) VoxIcons.Sparkle else VoxIcons.Ask, c.brand, size = RowIconSize)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text, style = wordsStyle(17, FontWeight.Bold, 1.3f), color = c.text)
                if (tono) {
                    Text(
                        stringResource(if (s.coach.source == ReplySource.CLOUD_AI) R.string.home_ask_tono_ai else R.string.home_ask_tono),
                        style = MaterialTheme.typography.labelMedium, color = c.brand,
                    )
                }
            }
            if (tono) Icon(VoxIcons.Forward, null, tint = c.text3, modifier = Modifier.size(18.dp))
        }
    }
}

// ------------------------------------------------------------------------------------ the week

/**
 * The last 7 days as a titled section of two cards, surroundings and headphones: side by side while the figures have room,
 * one under the other otherwise. The headphones card grows into Headphones ([SharedKeys.HEADPHONES]).
 */
@Composable
private fun WeekSection(week: Double, headphones: Double, navigate: (String) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
        GroupHeader(stringResource(R.string.tile_week))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = density.fontScale <= 1.15f && (maxWidth - VoxSpace.s) / 2 >= 140.dp
            val weekTile = @Composable { m: Modifier ->
                ShareTile(
                    stringResource(R.string.home_surroundings), stringResource(R.string.home_week_caption), week,
                    VoxIcons.Waves, Vox.colors.brand,
                    stringResource(R.string.tile_week_cd, week.roundToInt(), stringResource(RiskZone.ofPercent(week).wordRes())),
                    m, onClick = { navigate(PhoneRoutes.TRENDS) }, count = true,
                )
            }
            val hpTile = @Composable { m: Modifier ->
                ShareTile(
                    stringResource(R.string.tile_headphones), stringResource(R.string.home_hp_caption), headphones,
                    VoxIcons.Headphones, Vox.colors.secondary,
                    stringResource(R.string.tile_headphones_cd, headphones.roundToInt(), stringResource(RiskZone.ofPercent(headphones).wordRes())),
                    m, onClick = { navigate(PhoneRoutes.HEADPHONES) }, count = true, sharedKey = SharedKeys.HEADPHONES,
                )
            }
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(VoxSpace.s)) {
                    weekTile(Modifier.weight(1f)); hpTile(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(VoxSpace.s)) {
                    weekTile(Modifier.fillMaxWidth()); hpTile(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ the day

/** The day hour by hour, in its own section: its title and loudest hour open Today; the chart under them. */
@Composable
private fun HoursSection(s: PhoneUiState, modifier: Modifier, onToday: () -> Unit) {
    val c = Vox.colors
    val today = s.history.ambient.firstOrNull()
    val hourly = today?.hourly ?: List(24) { 0f }
    val loudest = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0f }
    val nowHour = (((s.nowMs / 60_000L) - s.history.todayStartMinute) / 60).toInt().takeIf { it in 0..23 }
    val cd = if (loudest != null) stringResource(R.string.chart_hourly_cd, loudest.value.roundToInt(), "%02d:00".format(loudest.index))
    else stringResource(R.string.chart_hourly_empty_cd)
    Group(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowMinHeight)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_open_today), onClick = onToday)
                .padding(horizontal = VoxSpace.m, vertical = VoxSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_hours), style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    if (loudest != null) stringResource(R.string.chart_loudest, loudest.value.roundToInt(), "%02d:00".format(loudest.index))
                    else stringResource(R.string.coverage_none),
                    style = MaterialTheme.typography.bodySmall, color = c.text2,
                )
            }
            Icon(VoxIcons.Forward, null, tint = c.text3, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.padding(start = VoxSpace.m, end = VoxSpace.m, bottom = VoxSpace.m)) { HourColumns(hourly, nowHour, cd) }
    }
}

// ------------------------------------------------------------------------------------ last alert

@Composable
private fun LastAlertSection(s: PhoneUiState, modifier: Modifier, onClick: () -> Unit) {
    val c = Vox.colors
    val last = s.history.alerts.firstOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
        GroupHeader(stringResource(R.string.last_alert_title))
        Group(Modifier.fillMaxWidth()) {
            if (last == null) {
                Text(
                    stringResource(R.string.last_alert_none), style = MaterialTheme.typography.bodyMedium, color = c.text2,
                    modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = VoxSpace.m, vertical = VoxSpace.m),
                )
                return@Group
            }
            val zone = last.tier.zone()
            val title = stringResource(last.kind.titleRes())
            val tier = stringResource(last.tier.labelRes())
            val value = if (last.kind.isLevel) stringResource(R.string.value_dba, last.value.roundToInt()) else stringResource(R.string.value_percent, last.value.roundToInt())
            val whenText = relativeTimeText(last.atMs, s.nowMs)
            val cd = stringResource(R.string.last_alert_cd, title, tier, whenText)
            Row(
                Modifier.fillMaxWidth().heightIn(min = RowMinHeight)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.see_alerts), onClick = onClick)
                    .semantics(mergeDescendants = true) { contentDescription = cd }
                    .padding(horizontal = VoxSpace.m, vertical = VoxSpace.s),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxSpace.s),
            ) {
                IconTile(VoxIcons.zone(zone), c.zoneInk(zone), size = RowIconSize)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
                    // a level alert says how loud; a percent alert already has its number in the title
                    Text(
                        (if (last.kind.isLevel) "$tier · $value" else tier) + " · " + whenText,
                        style = MaterialTheme.typography.bodySmall, color = c.text2,
                    )
                }
                Icon(VoxIcons.Forward, null, tint = c.text3, modifier = Modifier.size(18.dp))
            }
        }
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
