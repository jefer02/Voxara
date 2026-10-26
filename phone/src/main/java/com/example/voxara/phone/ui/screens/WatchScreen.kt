package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.voxara.phone.ui.theme.VoxSpace
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.link.LinkState
import com.example.voxara.link.SyncProblem
import com.example.voxara.link.WatchNode
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.ExpandableRow
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.GroupRow
import com.example.voxara.phone.ui.components.HeroCard
import com.example.voxara.phone.ui.components.IconTile
import com.example.voxara.phone.ui.components.LinkButton
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.headphonesSourceText
import com.example.voxara.phone.ui.components.watchModeName
import com.example.voxara.phone.ui.dateText
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons

@Composable
fun WatchScreen(s: PhoneUiState, actions: PhoneActions) {
    VoxScreen(stringResource(R.string.watch_title), refreshing = s.syncing, onRefresh = actions.onRefresh) {
        // the connection is the hero: connected, reconnecting or disconnected since when, in words with its icon
        item(key = "device") { DeviceCard(s, actions, Modifier.contentWidth()) }
        if (s.pendingSpans > 0) item(key = "pending") { GroupNote(stringResource(R.string.link_pending_listening, s.pendingSpans), Modifier.contentWidth()) }
        item(key = "battery") { BatteryGuide(s, actions, Modifier.contentWidth()) }

        val watch = s.watch
        if (watch != null) {
            item(key = "checklist-header") { GroupHeader(stringResource(R.string.watch_checklist), Modifier.contentWidth()) }
            item(key = "checklist") { Checklist(s, actions, Modifier.contentWidth()) }
            val others = when (val l = s.link) {
                is LinkState.Linked -> l.others
                is LinkState.Problem -> l.others
                else -> emptyList()
            }
            if (others.isNotEmpty()) {
                item(key = "others-header") { GroupHeader(stringResource(R.string.link_other_watches), Modifier.contentWidth()) }
                item(key = "others") {
                    Group(Modifier.contentWidth()) {
                        others.forEachIndexed { i, w ->
                            if (i > 0) GroupDivider()
                            GroupRow(w.name, icon = VoxIcons.Watch, trailing = { LinkButton(stringResource(R.string.link_use_this), { actions.onChooseWatch(w.id) }) })
                        }
                    }
                }
            }
        }

        item(key = "help") {
            Group(Modifier.contentWidth()) {
                ExpandableRow(stringResource(R.string.troubleshoot_title), icon = VoxIcons.Info) {
                    listOf(R.string.ts_1, R.string.ts_2, R.string.ts_3, R.string.ts_4, R.string.ts_5).forEach {
                        Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2)
                    }
                }
            }
        }
        item(key = "explain") { GroupNote(stringResource(R.string.link_explain), Modifier.contentWidth()) }
    }
}

@Composable
private fun DeviceCard(s: PhoneUiState, actions: PhoneActions, modifier: Modifier) {
    val c = Vox.colors
    when (val l = s.link) {
        LinkState.Checking -> VoxCard(modifier) {
            Text(stringResource(R.string.link_checking), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        LinkState.WearApiMissing -> Banner(
            VoxIcons.Warning, stringResource(R.string.link_api_missing), stringResource(R.string.link_problem_no_api),
            tint = c.zoneInk(RiskZone.LOUD), modifier = modifier,
        )
        LinkState.NoWatch -> Banner(
            VoxIcons.Watch, stringResource(R.string.link_no_watch), stringResource(R.string.link_no_watch_body),
            tint = c.caution, modifier = modifier,
            action = stringResource(R.string.action_open_wearable), onAction = actions.onOpenWearableApp,
        )
        is LinkState.NotInstalled -> VoxCard(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(VoxIcons.Info, c.caution)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.link_not_installed), style = MaterialTheme.typography.titleMedium, color = c.text)
            }
            l.watches.forEach { w ->
                Text(stringResource(R.string.link_not_installed_body, w.name), style = MaterialTheme.typography.bodyMedium, color = c.text2)
                PillButton(stringResource(R.string.action_install_on_watch), { actions.onInstallOnWatch(w.id) }, icon = VoxIcons.Watch)
            }
            if (s.debugBuild) Text(stringResource(R.string.link_debug_install), style = MaterialTheme.typography.bodySmall, color = c.text2)
        }
        is LinkState.Linked -> LinkedCard(s, l.watch, null, actions, modifier)
        is LinkState.Problem -> LinkedCard(s, l.watch, l.problem, actions, modifier)
    }
}

/**
 * THE WATCH HERO — the watch and the link as it really is, said plainly and large: connected; problem; reconnecting; or
 * disconnected since when (each with its icon and its word, never colour alone), what that means while it lasts, then
 * when it last reported, its version and mode, and the two things to do.
 */
@Composable
private fun LinkedCard(s: PhoneUiState, watch: WatchNode, problem: SyncProblem?, actions: PhoneActions, modifier: Modifier) {
    val c = Vox.colors
    val health = linkHealthText(s.linkHealth, s.nowMs)
    val ok = problem == null && health == null
    val zone = if (ok) RiskZone.OK else RiskZone.LOUD
    val state = when {
        problem != null -> stringResource(R.string.link_problem)
        health != null -> health
        else -> stringResource(R.string.watch_connected)
    }
    HeroCard(modifier) {
        IconTile(VoxIcons.Watch, c.brand, size = 56.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VoxSpace.xxs)) {
            Text(watch.name, style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (ok) VoxIcons.Check else VoxIcons.zone(zone), null, tint = c.zoneInk(zone), modifier = Modifier.size(18.dp))
                Text(state, style = MaterialTheme.typography.titleSmall, color = c.zoneInk(zone), textAlign = TextAlign.Center)
            }
        }
        val why = when (problem) {
            SyncProblem.UNREACHABLE -> R.string.link_problem_unreachable
            SyncProblem.INCOMPATIBLE -> R.string.link_problem_incompatible
            SyncProblem.NO_WEAR_API -> R.string.link_problem_no_api
            null -> if (health != null) R.string.link_health_body else null
        }
        if (why != null) Text(stringResource(why), style = MaterialTheme.typography.bodyMedium, color = c.text2, textAlign = TextAlign.Center)
        val details = listOfNotNull(
            s.lastSyncMs?.let { stringResource(R.string.last_synced, dateTimeText(it, s.nowMs)) } ?: stringResource(R.string.fresh_never),
            s.status?.appVersion?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.watch_version, it) }
                ?: stringResource(R.string.watch_version_unknown),
            // the mode the watch is in (as of its last status), with its headphones layer and why it is on
            watchModeName(s)?.let { stringResource(R.string.watch_mode, it) },
        )
        Text(details.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = c.text2, textAlign = TextAlign.Center)
        s.status?.takeIf { it.headphonesMode }?.let { st ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(VoxIcons.Headphones, null, tint = c.secondary, modifier = Modifier.size(16.dp))
                val on = stringResource(R.string.hp_state_on)
                val source = headphonesSourceText(st.headphonesSource)
                Text(if (source != null) stringResource(R.string.hp_state_detail, on, source) else on, style = MaterialTheme.typography.labelLarge, color = c.secondary)
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
            PillButton(stringResource(R.string.action_sync_now), actions.onRefresh, icon = VoxIcons.Refresh, enabled = !s.syncing, modifier = Modifier.fillMaxWidth())
            PillButton(stringResource(R.string.action_open_on_watch), actions.onOpenOnWatch, icon = VoxIcons.Watch, tonal = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Checklist(s: PhoneUiState, actions: PhoneActions, modifier: Modifier) {
    val st = s.status
    val c = Vox.colors
    @Composable
    fun row(title: String, ok: Boolean?, detail: String, onClick: (() -> Unit)? = null) {
        val zone = when (ok) { true -> RiskZone.OK; false -> RiskZone.LOUD; null -> null }
        GroupRow(
            title = title, subtitle = detail,
            icon = if (zone == null) VoxIcons.Info else VoxIcons.zone(zone),
            iconTint = if (zone == null) c.text3 else c.zoneInk(zone),
            onClick = onClick,
        )
    }
    val unknown = stringResource(R.string.check_unknown)
    Group(modifier) {
        row(stringResource(R.string.check_mic), st?.micGranted,
            if (st == null) unknown else stringResource(if (st.micGranted) R.string.check_mic_ok else R.string.check_mic_missing),
            if (st?.micGranted == false) actions.onOpenOnWatch else null)
        GroupDivider()
        ExpandableRow(
            stringResource(R.string.check_cal),
            subtitle = when {
                st == null -> unknown
                st.calibrated -> stringResource(R.string.check_cal_ok, st.calibratedAtMs?.let { dateText(it) } ?: "")
                else -> stringResource(R.string.check_cal_missing)
            },
            icon = if (st?.calibrated == null) VoxIcons.Info else VoxIcons.zone(if (st.calibrated) RiskZone.OK else RiskZone.LOUD),
            iconTint = when (st?.calibrated) { null -> c.text3; true -> c.zoneInk(RiskZone.OK); false -> c.zoneInk(RiskZone.LOUD) },
        ) {
            Text(stringResource(R.string.cal_how_body), style = MaterialTheme.typography.bodyMedium, color = c.text2)
            LinkButton(stringResource(R.string.action_open_calibration), actions.onOpenCalibration)
        }
        GroupDivider()
        row(stringResource(R.string.check_monitoring), st?.let { it.monitoring == MonitoringStatus.RUNNING },
            when (st?.monitoring) {
                null -> unknown
                MonitoringStatus.RUNNING -> stringResource(R.string.mon_running)
                MonitoringStatus.PAUSED -> stringResource(R.string.mon_paused)
                MonitoringStatus.OFF -> stringResource(R.string.mon_off)
            },
            if (st != null && st.monitoring != MonitoringStatus.RUNNING) actions.onOpenOnWatch else null)
        GroupDivider()
        row(stringResource(R.string.check_notifications), st?.notificationsGranted,
            if (st == null) unknown else stringResource(if (st.notificationsGranted) R.string.check_mic_ok else R.string.check_notifications_off))
        GroupDivider()
        ExpandableRow(
            stringResource(R.string.check_battery),
            subtitle = when {
                st == null -> unknown
                st.powerSaveMode -> stringResource(R.string.battery_power_save)
                !st.ignoringBatteryOptimizations -> stringResource(R.string.battery_optimized)
                else -> stringResource(R.string.battery_ok)
            },
            icon = if (st == null) VoxIcons.Info else VoxIcons.zone(if (!st.powerSaveMode) RiskZone.OK else RiskZone.LOUD),
            iconTint = if (st == null) c.text3 else c.zoneInk(if (!st.powerSaveMode) RiskZone.OK else RiskZone.LOUD),
        ) {
            Text(stringResource(R.string.battery_tips_title), style = MaterialTheme.typography.titleSmall, color = c.text)
            listOf(R.string.battery_tip_1, R.string.battery_tip_2, R.string.battery_tip_3).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium, color = c.text2)
            }
        }
        if (st != null) {
            GroupDivider()
            GroupNote(freshnessText(st.atMs, s.nowMs), Modifier.padding(vertical = 12.dp))
        }
    }
}
