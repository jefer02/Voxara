package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.averageDba
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.GroupRow
import com.example.voxara.phone.ui.components.ShareTile
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.ZoneWord
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.figureStyle
import com.example.voxara.ui.design.RollingDigits
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes
import kotlin.math.roundToInt

/**
 * HEADPHONES — what the phone's headphones are doing now, today's estimate and the week's share. Sharing the listening
 * with the watch and the headphone type are settings: they live in Settings, one row away (the first row here).
 * How the sharing works is explained in How it works.
 */
@Composable
fun HeadphonesScreen(s: PhoneUiState, actions: PhoneActions, onBack: () -> Unit, onOpenSettings: () -> Unit = {}) {
    VoxScreen(stringResource(R.string.hp_title), onBack = onBack, refreshing = s.syncing, onRefresh = actions.onRefresh) {
        item {
            Group(Modifier.contentWidth()) {
                GroupRow(
                    title = stringResource(R.string.hp_sampling),
                    subtitle = if (!s.phoneSampling) stringResource(R.string.phone_status_off)
                    else if (s.lastListeningSentMs <= 0L) stringResource(R.string.phone_status_waiting)
                    else stringResource(R.string.phone_status_synced, dateTimeText(s.lastListeningSentMs, s.nowMs)),
                    icon = VoxIcons.Phone,
                    onClick = onOpenSettings,
                )
            }
        }
        // What is happening now comes first.
        headphoneLiveItems(s, actions)
        item { BatteryGuide(s, actions, Modifier.contentWidth()) }
        if (s.phoneSampling && !s.bluetoothGranted) {
            item {
                Banner(
                    VoxIcons.Headphones, stringResource(R.string.hp_sampling), stringResource(R.string.hp_bt_needed),
                    tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                    action = stringResource(R.string.action_allow), onAction = actions.onRequestBluetooth,
                )
            }
        }

        if (!s.loaded) {
            item { SkeletonCard(Modifier.contentWidth()) }
        } else {
            val today = s.history.headphone.firstOrNull()
            item {
                VoxCard(Modifier.contentWidth()) {
                    Text(stringResource(R.string.hp_estimate_title), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                    val level = today?.takeIf { it.measuredMinutes > 0 }?.averageDba()
                    if (level == null) {
                        Text(stringResource(R.string.hp_estimate_none), style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RollingDigits(level.roundToInt(), figureStyle(36), Vox.colors.text, prefix = "≈", suffix = "dBA", still = true, cellScale = 0.94f)
                        }
                        ZoneWord(RiskZone.of(level), sizeSp = 15)
                        Text(stringResource(R.string.hp_estimate_label), style = MaterialTheme.typography.labelMedium, color = Vox.colors.text2)
                        Text(
                            stringResource(R.string.hp_today) + " · " + formatClock(today.measuredMinutes),
                            style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2,
                        )
                    }
                    FreshnessLine(s.lastSyncMs, s.nowMs)
                }
            }
            item {
                val week = s.figures.headphonesWeekPercent
                ShareTile(
                    stringResource(R.string.hp_weekly), stringResource(R.string.tile_headphones_caption), week,
                    VoxIcons.Headphones, Vox.colors.secondary,
                    stringResource(R.string.tile_headphones_cd, week.roundToInt(), stringResource(RiskZone.ofPercent(week).wordRes())),
                    Modifier.contentWidth(),
                )
            }
        }
        item { GroupNote(stringResource(R.string.phone_limits), Modifier.contentWidth().padding(top = 4.dp)) }
    }
}
