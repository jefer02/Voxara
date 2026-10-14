package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.link.LinkState
import com.example.voxara.link.SyncProblem
import com.example.voxara.link.WatchNode
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.GroupRow
import com.example.voxara.phone.ui.components.LinkButton
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.dateText
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.labelRes

@Composable
fun WatchScreen(s: PhoneUiState, actions: PhoneActions) {
    VoxScreen(stringResource(R.string.watch_title), refreshing = s.syncing, onRefresh = actions.onRefresh) {
        item { DeviceCard(s, actions, Modifier.contentWidth()) }
        item { GroupNote(stringResource(R.string.link_explain), Modifier.contentWidth()) }

        val watch = s.watch
        if (watch != null) {
            item { GroupHeader(stringResource(R.string.watch_checklist), Modifier.contentWidth()) }
            item { Checklist(s, actions, Modifier.contentWidth()) }
            item {
                Group(Modifier.contentWidth()) {
                    GroupRow(stringResource(R.string.battery_tips_title), icon = VoxIcons.Battery)
                    GroupNote("• " + stringResource(R.string.battery_tip_1) + "\n• " + stringResource(R.string.battery_tip_2) + "\n• " + stringResource(R.string.battery_tip_3), Modifier.padding(bottom = 14.dp))
                }
            }
            item {
                Group(Modifier.contentWidth()) {
                    GroupRow(stringResource(R.string.cal_how_title), icon = VoxIcons.Tune)
                    GroupNote(stringResource(R.string.cal_how_body), Modifier.padding(bottom = 14.dp))
                }
            }
            val others = when (val l = s.link) {
                is LinkState.Linked -> l.others
                is LinkState.Problem -> l.others
                else -> emptyList()
            }
            if (others.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.link_other_watches), Modifier.contentWidth()) }
                item {
                    Group(Modifier.contentWidth()) {
                        others.forEachIndexed { i, w ->
                            if (i > 0) GroupDivider()
                            GroupRow(w.name, icon = VoxIcons.Watch, trailing = { LinkButton(stringResource(R.string.link_use_this), { actions.onChooseWatch(w.id) }) })
                        }
                    }
                }
            }
        }

        item { GroupHeader(stringResource(R.string.troubleshoot_title), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                listOf(R.string.ts_1, R.string.ts_2, R.string.ts_3, R.string.ts_4, R.string.ts_5).forEachIndexed { i, res ->
                    if (i > 0) GroupDivider()
                    GroupRow(stringResource(res))
                }
            }
        }
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
            tint = c.zoneInk(RiskZone.MODERATE), modifier = modifier,
            action = stringResource(R.string.action_open_wearable), onAction = actions.onOpenWearableApp,
        )
        is LinkState.NotInstalled -> VoxCard(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(VoxIcons.Info, null, tint = c.zoneInk(RiskZone.MODERATE), modifier = Modifier.size(24.dp))
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

@Composable
private fun LinkedCard(s: PhoneUiState, watch: WatchNode, problem: SyncProblem?, actions: PhoneActions, modifier: Modifier) {
    val c = Vox.colors
    VoxCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(VoxIcons.Watch, null, tint = c.brand, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column {
                Text(watch.name, style = MaterialTheme.typography.titleLarge, color = c.text)
                val zone = if (problem == null) RiskZone.OK else RiskZone.LOUD
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(VoxIcons.zone(zone), null, tint = c.zoneInk(zone), modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(if (problem == null) R.string.watch_connected else R.string.link_problem),
                        style = MaterialTheme.typography.labelLarge, color = c.zoneInk(zone),
                    )
                }
            }
        }
        Text(
            s.lastSyncMs?.let { stringResource(R.string.last_synced, dateTimeText(it, s.nowMs)) } ?: stringResource(R.string.fresh_never),
            style = MaterialTheme.typography.bodyMedium, color = c.text2,
        )
        Text(
            s.status?.appVersion?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.watch_version, it) }
                ?: stringResource(R.string.watch_version_unknown),
            style = MaterialTheme.typography.bodyMedium, color = c.text2,
        )
        // The mode the watch is in (as of its last status); Auto shows what it resolved to.
        s.status?.situationMode?.let { mode ->
            val name = stringResource(mode.labelRes())
            Text(
                stringResource(
                    R.string.watch_mode,
                    if (s.settings.situationChoice == SituationChoice.AUTO)
                        stringResource(R.string.watch_mode_auto, name) else name,
                ),
                style = MaterialTheme.typography.bodyMedium, color = c.text2,
            )
        }
        if (problem != null) {
            Text(
                stringResource(
                    when (problem) {
                        SyncProblem.UNREACHABLE -> R.string.link_problem_unreachable
                        SyncProblem.INCOMPATIBLE -> R.string.link_problem_incompatible
                        SyncProblem.NO_WEAR_API -> R.string.link_problem_no_api
                    }
                ),
                style = MaterialTheme.typography.bodyMedium, color = c.text,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.action_sync_now), actions.onRefresh, icon = VoxIcons.Refresh, enabled = !s.syncing)
        }
        PillButton(stringResource(R.string.action_open_on_watch), actions.onOpenOnWatch, icon = VoxIcons.Watch, tonal = true)
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
        row(stringResource(R.string.check_cal), st?.calibrated,
            when {
                st == null -> unknown
                st.calibrated -> stringResource(R.string.check_cal_ok, st.calibratedAtMs?.let { dateText(it) } ?: "")
                else -> stringResource(R.string.check_cal_missing)
            },
            actions.onOpenCalibration)
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
        row(stringResource(R.string.check_battery), st?.let { !it.powerSaveMode },
            when {
                st == null -> unknown
                st.powerSaveMode -> stringResource(R.string.battery_power_save)
                !st.ignoringBatteryOptimizations -> stringResource(R.string.battery_optimized)
                else -> stringResource(R.string.battery_ok)
            })
        if (st != null) {
            GroupDivider()
            GroupNote(stringResource(R.string.check_as_of, freshnessText(st.atMs, s.nowMs)), Modifier.padding(vertical = 12.dp))
        }
    }
}
