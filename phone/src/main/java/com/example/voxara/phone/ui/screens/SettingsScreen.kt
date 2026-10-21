package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.design.Accent
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.HapticStrength
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.designsystem.R as DsR
import com.example.voxara.phone.R
import com.example.voxara.phone.data.PhoneStoreState
import com.example.voxara.phone.data.ThemeMode
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneRoutes
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.AccentSwatches
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.ChoiceRow
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.GroupDivider
import com.example.voxara.phone.ui.components.GroupHeader
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.GroupRow
import com.example.voxara.phone.ui.components.StepperRow
import com.example.voxara.phone.ui.components.SwitchRow
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.components.headphonesSourceText
import com.example.voxara.phone.ui.components.watchModeName
import com.example.voxara.phone.ui.freshnessText
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingLimits
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.blurbRes
import com.example.voxara.ui.design.labelRes

@Composable
fun SettingsScreen(s: PhoneUiState, actions: PhoneActions, navigate: (String) -> Unit) {
    val set = s.settings
    val p = s.pending
    fun change(f: SettingField, v: Any) = actions.onSetting(f, v.toString())
    fun clock(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    VoxScreen(stringResource(R.string.settings_title)) {
        if (p.isNotEmpty()) {
            item {
                Banner(
                    VoxIcons.Refresh, stringResource(R.string.pending_sync), stringResource(R.string.settings_pending_note),
                    tint = Vox.colors.caution, modifier = Modifier.contentWidth(),
                )
            }
        }

        // ------------------------------------------------------------------ mode
        item { GroupHeader(stringResource(R.string.sec_mode), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                SituationChoice.entries.forEachIndexed { i, choice ->
                    if (i > 0) GroupDivider()
                    val selected = choice == set.situationChoice
                    GroupRow(
                        stringResource(choice.labelRes()),
                        subtitle = stringResource(choice.blurbRes()),
                        pending = selected && SettingField.SITUATION_MODE in p,
                        onClick = { change(SettingField.SITUATION_MODE, choice.name) },
                        trailing = {
                            if (selected) Icon(VoxIcons.Check, stringResource(R.string.mode_selected_cd), tint = Vox.colors.brand)
                        },
                    )
                }
            }
        }
        item { GroupNote(stringResource(DsR.string.situation_rule), Modifier.contentWidth()) }

        // ------------------------------------------------------------------ headphones mode
        item { GroupHeader(stringResource(R.string.sec_headphone_mode), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                // What the watch is doing now (its last status), so the choice below has its effect in view.
                val live = s.status
                if (live != null) {
                    GroupRow(
                        stringResource(if (live.headphonesMode) R.string.hp_state_on else R.string.hp_state_off),
                        subtitle = listOfNotNull(
                            watchModeName(s), headphonesSourceText(live.headphonesSource), freshnessText(live.atMs, s.nowMs),
                        ).joinToString(" · "),
                        icon = VoxIcons.Headphones, iconTint = if (live.headphonesMode) Vox.colors.secondary else Vox.colors.text3,
                    )
                    GroupDivider()
                }
                ChoiceRow(
                    stringResource(R.string.hp_mode_how),
                    HeadphoneModeChoice.entries.map { it to stringResource(it.labelRes()) },
                    set.headphoneMode, { change(SettingField.HEADPHONE_MODE, it.name) },
                    subtitle = stringResource(set.headphoneMode.blurbRes()), pending = SettingField.HEADPHONE_MODE in p,
                )
            }
        }
        item { GroupNote(stringResource(R.string.hp_mode_rule), Modifier.contentWidth()) }

        // ------------------------------------------------------------------ alerts
        item { GroupHeader(stringResource(R.string.sec_alerts), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                StepperRow(
                    stringResource(R.string.set_threshold), stringResource(R.string.value_dba, set.alertThresholdDba),
                    onMinus = { change(SettingField.ALERT_THRESHOLD, set.alertThresholdDba - 1) },
                    onPlus = { change(SettingField.ALERT_THRESHOLD, set.alertThresholdDba + 1) },
                    canMinus = set.alertThresholdDba > SettingLimits.THRESHOLD.first,
                    canPlus = set.alertThresholdDba < SettingLimits.THRESHOLD.last,
                    subtitle = stringResource(R.string.set_threshold_note, AlertDefaults.SUSTAINED_WINDOW_MIN),
                    pending = SettingField.ALERT_THRESHOLD in p,
                )
                GroupDivider()
                SwitchRow(
                    stringResource(R.string.set_sensitive), set.sensitiveListener, { change(SettingField.SENSITIVE, it) },
                    subtitle = stringResource(R.string.set_sensitive_note), pending = SettingField.SENSITIVE in p,
                )
                GroupDivider()
                SwitchRow(
                    stringResource(R.string.set_quiet), set.quietHoursEnabled, { change(SettingField.QUIET_ENABLED, it) },
                    icon = VoxIcons.Moon, pending = SettingField.QUIET_ENABLED in p,
                )
                if (set.quietHoursEnabled) {
                    GroupDivider()
                    StepperRow(
                        stringResource(R.string.set_quiet_from), clock(set.quietStartMin),
                        onMinus = { change(SettingField.QUIET_START, Math.floorMod(set.quietStartMin - 30, 1440)) },
                        onPlus = { change(SettingField.QUIET_START, Math.floorMod(set.quietStartMin + 30, 1440)) },
                        canMinus = true, canPlus = true, pending = SettingField.QUIET_START in p,
                    )
                    GroupDivider()
                    StepperRow(
                        stringResource(R.string.set_quiet_to), clock(set.quietEndMin),
                        onMinus = { change(SettingField.QUIET_END, Math.floorMod(set.quietEndMin - 30, 1440)) },
                        onPlus = { change(SettingField.QUIET_END, Math.floorMod(set.quietEndMin + 30, 1440)) },
                        canMinus = true, canPlus = true, pending = SettingField.QUIET_END in p,
                    )
                }
                GroupDivider()
                StepperRow(
                    stringResource(R.string.set_cap), set.dailyCap.toString(),
                    onMinus = { change(SettingField.DAILY_CAP, set.dailyCap - 1) },
                    onPlus = { change(SettingField.DAILY_CAP, set.dailyCap + 1) },
                    canMinus = set.dailyCap > SettingLimits.DAILY_CAP.first, canPlus = set.dailyCap < SettingLimits.DAILY_CAP.last,
                    subtitle = stringResource(R.string.set_cap_note), pending = SettingField.DAILY_CAP in p,
                )
            }
        }
        item { GroupNote(stringResource(R.string.set_quiet_note), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                ChoiceRow(
                    stringResource(R.string.set_haptics),
                    listOf(HapticStrength.STANDARD to stringResource(R.string.haptic_standard), HapticStrength.STRONG to stringResource(R.string.haptic_strong)),
                    set.hapticStrength, { change(SettingField.HAPTIC_STRENGTH, it.name) },
                    pending = SettingField.HAPTIC_STRENGTH in p,
                )
                GroupDivider()
                SwitchRow(
                    stringResource(R.string.set_advisory_haptics), set.advisoryHaptics, { change(SettingField.ADVISORY_HAPTICS, it) },
                    icon = VoxIcons.Vibrate, subtitle = stringResource(R.string.set_haptics_note), pending = SettingField.ADVISORY_HAPTICS in p,
                )
                GroupDivider()
                ChoiceRow(
                    stringResource(R.string.set_snooze),
                    AlertPolicy.SNOOZE_CHOICES.map { it to stringResource(R.string.snooze_value, it) },
                    set.snoozeMinutes.takeIf { it in AlertPolicy.SNOOZE_CHOICES } ?: AlertPolicy.SNOOZE_DEFAULT_MINUTES,
                    { change(SettingField.SNOOZE_MINUTES, it) },
                    subtitle = stringResource(R.string.set_snooze_note), pending = SettingField.SNOOZE_MINUTES in p,
                )
            }
        }

        // ------------------------------------------------------------------ headphones
        item { GroupHeader(stringResource(R.string.sec_headphones), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                SwitchRow(stringResource(R.string.hp_sampling), s.phoneSampling, actions.onPhoneSampling, icon = VoxIcons.Phone,
                    subtitle = stringResource(R.string.setup_off_default))
                GroupDivider()
                GroupRow(stringResource(R.string.hp_title), icon = VoxIcons.Headphones, onClick = { navigate(PhoneRoutes.HEADPHONES) })
                GroupDivider()
                StepperRow(
                    stringResource(R.string.set_hp_loud), stringResource(R.string.value_dba, set.headphoneLoudDba),
                    onMinus = { change(SettingField.HEADPHONE_LOUD, set.headphoneLoudDba - 1) },
                    onPlus = { change(SettingField.HEADPHONE_LOUD, set.headphoneLoudDba + 1) },
                    canMinus = set.headphoneLoudDba > SettingLimits.HEADPHONE_LOUD.first,
                    canPlus = set.headphoneLoudDba < SettingLimits.HEADPHONE_LOUD.last,
                    subtitle = stringResource(R.string.set_hp_loud_note), pending = SettingField.HEADPHONE_LOUD in p,
                )
            }
        }

        // ------------------------------------------------------------------ Tono and privacy
        item { GroupHeader(stringResource(R.string.sec_tono), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                SwitchRow(stringResource(R.string.set_tono), set.tonoEnabled, { change(SettingField.TONO_ENABLED, it) },
                    icon = VoxIcons.Ask, subtitle = stringResource(R.string.set_tono_note), pending = SettingField.TONO_ENABLED in p)
                GroupDivider()
                SwitchRow(
                    stringResource(R.string.set_cloud), set.cloudAi, { change(SettingField.CLOUD_AI, it) },
                    icon = VoxIcons.Sparkle,
                    subtitle = stringResource(if (s.cloudAvailable) R.string.set_cloud_note else R.string.tono_unavailable),
                    enabled = s.cloudAvailable && set.tonoEnabled, pending = SettingField.CLOUD_AI in p,
                )
            }
        }
        item { GroupNote(stringResource(R.string.privacy_numbers) + "\n\n" + stringResource(R.string.privacy_backup), Modifier.contentWidth()) }

        // ------------------------------------------------------------------ appearance (phone only)
        item { GroupHeader(stringResource(R.string.sec_appearance), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.set_accent), style = MaterialTheme.typography.titleSmall, color = Vox.colors.text)
                    Text(stringResource(R.string.set_accent_note), style = MaterialTheme.typography.bodySmall, color = Vox.colors.text2)
                    AccentSwatches(
                        selected = s.accent,
                        onSelect = actions.onAccent,
                        names = mapOf(
                            Accent.PURPLE to stringResource(R.string.accent_purple),
                            Accent.TURQUOISE to stringResource(R.string.accent_turquoise),
                            Accent.BLUE to stringResource(R.string.accent_blue),
                            Accent.EMERALD to stringResource(R.string.accent_emerald),
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                GroupDivider()
                ChoiceRow(
                    stringResource(R.string.set_theme),
                    listOf(
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ),
                    s.themeMode, actions.onThemeMode,
                )
            }
        }
        item { GroupNote(stringResource(R.string.set_appearance_note), Modifier.contentWidth()) }

        // ------------------------------------------------------------------ language
        item { GroupHeader(stringResource(R.string.sec_language), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                ChoiceRow(
                    stringResource(R.string.sec_language),
                    listOf(
                        LanguageChoice.SYSTEM to stringResource(R.string.lang_system),
                        LanguageChoice.ENGLISH to stringResource(R.string.lang_en),
                        LanguageChoice.SPANISH to stringResource(R.string.lang_es),
                    ),
                    set.language, { change(SettingField.LANGUAGE, it.name) },
                    subtitle = stringResource(R.string.set_language_note), pending = SettingField.LANGUAGE in p,
                )
            }
        }

        // ------------------------------------------------------------------ data
        item { GroupHeader(stringResource(R.string.sec_data), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                ChoiceRow(
                    stringResource(R.string.set_retention),
                    PhoneStoreState.RETENTION_CHOICES.map { it to stringResource(R.string.retention_days, it) },
                    s.retentionDays, actions.onRetention,
                )
                GroupDivider()
                SwitchRow(stringResource(R.string.set_mirror), s.mirrorAlerts, actions.onMirrorAlerts, icon = VoxIcons.Bell,
                    subtitle = stringResource(R.string.set_mirror_note))
                GroupDivider()
                GroupRow(stringResource(R.string.set_export), stringResource(R.string.set_export_note), icon = VoxIcons.Share, onClick = actions.onExport)
                GroupDivider()
                GroupRow(
                    stringResource(R.string.set_delete),
                    if (s.deletePending) stringResource(R.string.delete_pending_note) else stringResource(R.string.set_delete_note),
                    icon = VoxIcons.Trash, iconTint = Vox.colors.danger,
                    onClick = { confirmDelete = true },
                )
            }
        }

        // ------------------------------------------------------------------ about
        item { GroupHeader(stringResource(R.string.sec_about), Modifier.contentWidth()) }
        item {
            Group(Modifier.contentWidth()) {
                GroupRow(stringResource(R.string.how_it_works), icon = VoxIcons.Info, onClick = { navigate(PhoneRoutes.HOW) })
                GroupDivider()
                GroupRow(stringResource(R.string.setup_again), icon = VoxIcons.Refresh, onClick = actions.onRunSetupAgain)
                GroupDivider()
                GroupRow(stringResource(R.string.version, s.appVersion), stringResource(R.string.licences), icon = VoxIcons.Shield)
            }
        }
        item { GroupNote(stringResource(R.string.how_medical_body), Modifier.contentWidth().padding(bottom = 8.dp)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDeleteAll() }) {
                    Text(stringResource(R.string.action_delete), color = Vox.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun HeadphoneModeChoice.labelRes(): Int = when (this) {
    HeadphoneModeChoice.AUTO -> R.string.hp_mode_choice_auto
    HeadphoneModeChoice.ON -> R.string.hp_mode_choice_on
    HeadphoneModeChoice.OFF -> R.string.hp_mode_choice_off
}

private fun HeadphoneModeChoice.blurbRes(): Int = when (this) {
    HeadphoneModeChoice.AUTO -> R.string.hp_mode_blurb_auto
    HeadphoneModeChoice.ON -> R.string.hp_mode_blurb_on
    HeadphoneModeChoice.OFF -> R.string.hp_mode_blurb_off
}
