package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.phone.R
import com.example.voxara.phone.model.averageDba
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.GroupRow
import com.example.voxara.phone.ui.components.ShareTile
import com.example.voxara.phone.ui.components.SharedKeys
import com.example.voxara.phone.ui.components.SkeletonCard
import com.example.voxara.phone.ui.components.ValueRow
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.dateTimeText
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxSpace
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.wordRes
import kotlin.math.roundToInt

/**
 * HEADPHONES — one hero: what this phone's headphones are doing now, the live estimate as the figure (Home's "Audífonos"
 * card grows into it, [SharedKeys.HEADPHONES]). Above the recommended level the alert with its three actions sits right
 * under it. Then today's estimate and the week's share as a grouped section, and the setting that turns the sharing on,
 * one row away in Settings. How the sharing works is explained in How it works.
 */
@Composable
fun HeadphonesScreen(s: PhoneUiState, actions: PhoneActions, onBack: () -> Unit, onOpenSettings: () -> Unit = {}) {
    VoxScreen(stringResource(R.string.hp_title), onBack = onBack, refreshing = s.syncing, onRefresh = actions.onRefresh) {
        item(key = "hero") { HeadphoneHero(s, Modifier.contentWidth(), sharedKey = SharedKeys.HEADPHONES) }
        val alert = s.phoneLive.alert?.takeIf { !it.dismissed }
        if (s.phoneSampling && alert != null) item(key = "alert") { HeadphoneAlertCard(s, actions, Modifier.contentWidth()) }
        if (s.phoneSampling && !s.bluetoothGranted) {
            item(key = "bluetooth") {
                Banner(
                    VoxIcons.Headphones, stringResource(R.string.hp_sampling), stringResource(R.string.hp_bt_needed),
                    tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                    action = stringResource(R.string.action_allow), onAction = actions.onRequestBluetooth,
                )
            }
        }
        item(key = "battery") { BatteryGuide(s, actions, Modifier.contentWidth()) }

        if (!s.loaded) {
            item(key = "skeleton") { SkeletonCard(Modifier.contentWidth()) }
        } else {
            val today = s.history.headphone.firstOrNull()?.takeIf { it.measuredMinutes > 0 }
            item(key = "today-header") { GroupHeader(stringResource(R.string.hp_today), Modifier.contentWidth()) }
            item(key = "today") {
                Group(Modifier.contentWidth()) {
                    val level = today?.averageDba()
                    if (today == null || level == null) {
                        GroupNote(stringResource(R.string.hp_estimate_none), Modifier.padding(vertical = VoxSpace.m))
                    } else {
                        // an estimate says so: "≈" and its label
                        ValueRow(
                            stringResource(R.string.hp_estimate_title), "≈ " + stringResource(R.string.value_dba, level.roundToInt()),
                            subtitle = stringResource(R.string.hp_estimate_label) + " · " + stringResource(RiskZone.of(level).wordRes()),
                        )
                        GroupDivider()
                        ValueRow(stringResource(R.string.today_measured), formatClock(today.measuredMinutes))
                    }
                    GroupDivider()
                    GroupNote(freshnessText(s.lastSyncMs, s.nowMs), Modifier.padding(vertical = VoxSpace.s))
                }
            }
            item(key = "week") {
                val week = s.figures.headphonesWeekPercent
                ShareTile(
                    stringResource(R.string.hp_weekly), stringResource(R.string.tile_headphones_caption), week,
                    VoxIcons.Headphones, Vox.colors.secondary,
                    stringResource(R.string.tile_headphones_cd, week.roundToInt(), stringResource(RiskZone.ofPercent(week).wordRes())),
                    Modifier.contentWidth(),
                )
            }
        }
        item(key = "sharing") {
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
        item(key = "limits") { GroupNote(stringResource(R.string.phone_limits), Modifier.contentWidth()) }
    }
}
