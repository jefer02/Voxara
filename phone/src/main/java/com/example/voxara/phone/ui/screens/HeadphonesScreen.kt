package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.voxara.phone.ui.components.ChoiceRow
import com.example.voxara.phone.ui.components.FreshnessLine
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.StatCard
import com.example.voxara.phone.ui.components.SwitchRow
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.ZoneBadge
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.settings.HeadphoneType
import com.example.voxara.settings.SettingField
import com.example.voxara.ui.design.VoxIcons
import kotlin.math.roundToInt

@Composable
fun HeadphonesScreen(s: PhoneUiState, actions: PhoneActions, onBack: () -> Unit) {
    VoxScreen(stringResource(R.string.hp_title), onBack = onBack, refreshing = s.syncing, onRefresh = actions.onRefresh) {
        item {
            Group(Modifier.contentWidth()) {
                SwitchRow(
                    title = stringResource(R.string.hp_sampling),
                    subtitle = if (!s.phoneSampling) stringResource(R.string.phone_status_off)
                    else if (s.lastListeningSentMs <= 0L) stringResource(R.string.phone_status_waiting)
                    else stringResource(R.string.phone_status_synced, dateTimeText(s.lastListeningSentMs, s.nowMs)),
                    checked = s.phoneSampling,
                    onChange = actions.onPhoneSampling,
                    icon = VoxIcons.Phone,
                )
            }
        }
        item { GroupNote(stringResource(R.string.phone_what) + "\n\n" + stringResource(R.string.phone_sends) + "\n\n" + stringResource(R.string.phone_never), Modifier.contentWidth()) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.value_dba, level.roundToInt()), style = MaterialTheme.typography.displaySmall, color = Vox.colors.text)
                            Spacer(Modifier.width(12.dp))
                            ZoneBadge(RiskZone.of(level))
                        }
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
                StatCard(
                    VoxIcons.Headphones, stringResource(R.string.hp_weekly), s.head.headphoneWeeklyPercent.roundToInt(),
                    stringResource(R.string.stat_headphones_sub), s.head.asOfMs, s.nowMs, Modifier.contentWidth(),
                )
            }
        }

        item { GroupHeader(stringResource(R.string.hp_type), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                ChoiceRow(
                    title = stringResource(R.string.hp_type),
                    options = listOf(
                        HeadphoneType.EARBUDS to stringResource(R.string.hp_type_earbuds),
                        HeadphoneType.OVER_EAR to stringResource(R.string.hp_type_over),
                        HeadphoneType.NONE to stringResource(R.string.hp_type_none),
                    ),
                    selected = s.settings.headphoneType,
                    onSelect = { actions.onSetting(SettingField.HEADPHONE_TYPE, it.name) },
                    pending = SettingField.HEADPHONE_TYPE in s.pending,
                )
                GroupDivider()
                GroupNote(stringResource(R.string.hp_type_note), Modifier.padding(vertical = 12.dp))
            }
        }
        item { GroupNote(stringResource(R.string.phone_limits), Modifier.contentWidth()) }
    }
}
