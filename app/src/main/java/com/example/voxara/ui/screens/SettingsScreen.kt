package com.example.voxara.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.Scenario
import com.example.voxara.debug.DebugTools
import com.example.voxara.debug.DetectionLog
import com.example.voxara.text.labelRes
import com.example.voxara.ui.Routes
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.GroupLabel
import com.example.voxara.ui.design.ScreenTitle
import com.example.voxara.ui.design.ChoiceList
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.Panel
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.ValueStepper
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.row
import java.text.DateFormat
import java.util.Date

/** SETTINGS — every option in one scrolling list, grouped, with plain-language notes. */
@Composable
fun SettingsScreen(env: UiEnv, actions: VoxaraActions, navigate: (String) -> Unit) {
    val s = env.state
    val a = s.alertSettings
    val name = stringResource(R.string.ai_name)
    var confirmReset by remember { mutableStateOf(false) }
    var devOpen by rememberSaveable { mutableStateOf(false) }
    val dec = stringResource(R.string.decrease_cd)
    val inc = stringResource(R.string.increase_cd)

    VoxList {
        row { ScreenTitle(stringResource(R.string.card_settings)) }

        // ------------------------------------------------------------------ monitoring
        row {
            val paused = s.monitoringStatus == MonitoringStatus.PAUSED
            MetricCard(
                icon = if (s.monitoring) VoxIcons.Waves else VoxIcons.Pause,
                label = stringResource(R.string.monitoring),
                value = stringResource(
                    when {
                        paused -> R.string.paused_label
                        s.monitoring -> R.string.on
                        else -> R.string.off
                    }
                ),
                accent = if (paused) VColor.Caution else VColor.AccentInk,
            )
        }
        row {
            val paused = s.monitoringStatus == MonitoringStatus.PAUSED
            PillButton(
                label = stringResource(if (paused) R.string.action_resume else if (s.monitoring) R.string.set_stop_monitoring else R.string.set_start_monitoring),
                onClick = { if (paused) actions.onResume() else actions.onMonitoring(!s.monitoring) },
                style = if (s.monitoring && !paused) PillStyle.TONAL else PillStyle.PRIMARY,
            )
        }
        if (s.power?.powerSaveMode == true) row { BodyText(stringResource(R.string.power_save_on), color = VColor.Caution) }
        row { BodyText(stringResource(R.string.keep_running_body)) }
        if (s.batterySettingsAvailable && s.power?.ignoringBatteryOptimizations == false) {
            row { PillButton(stringResource(R.string.battery_settings), onClick = actions.onOpenBatterySettings, style = PillStyle.OUTLINED) }
        }

        // ------------------------------------------------------------------ mode
        row { GroupLabel(stringResource(R.string.mode_title)) }
        row {
            MetricCard(
                icon = VoxIcons.Tune,
                label = stringResource(R.string.mode_title),
                value = modeValue(s.situationChoice, s.mode, s.listening),
                onClick = { navigate(Routes.MODE) },
            )
        }

        // ------------------------------------------------------------------ alerts
        row { GroupLabel(stringResource(R.string.alerts_title)) }
        row {
            ValueStepper(
                label = stringResource(R.string.set_alert_level),
                value = stringResource(R.string.alerts_threshold_value, a.thresholdDba),
                onDecrease = { actions.onAlertSettings(a.copy(thresholdDba = (a.thresholdDba - 1).coerceAtLeast(AlertDefaults.THRESHOLD_MIN_DBA))) },
                onIncrease = { actions.onAlertSettings(a.copy(thresholdDba = (a.thresholdDba + 1).coerceAtMost(AlertDefaults.THRESHOLD_MAX_DBA))) },
                decreaseDescription = dec, increaseDescription = inc,
            )
        }
        row { BodyText(stringResource(R.string.alerts_threshold_note, AlertDefaults.SUSTAINED_WINDOW_MIN)) }
        row {
            PillButton(
                label = stringResource(if (a.quietHoursEnabled) R.string.quiet_on else R.string.quiet_off, clock(a.quietStartMin), clock(a.quietEndMin)),
                onClick = { actions.onAlertSettings(a.copy(quietHoursEnabled = !a.quietHoursEnabled)) },
                style = if (a.quietHoursEnabled) PillStyle.PRIMARY else PillStyle.TONAL,
            )
        }
        if (a.quietHoursEnabled) {
            row {
                ValueStepper(
                    stringResource(R.string.quiet_start), clock(a.quietStartMin),
                    { actions.onAlertSettings(a.copy(quietStartMin = Math.floorMod(a.quietStartMin - 30, 1440))) },
                    { actions.onAlertSettings(a.copy(quietStartMin = Math.floorMod(a.quietStartMin + 30, 1440))) },
                    dec, inc,
                )
            }
            row {
                ValueStepper(
                    stringResource(R.string.quiet_end), clock(a.quietEndMin),
                    { actions.onAlertSettings(a.copy(quietEndMin = Math.floorMod(a.quietEndMin - 30, 1440))) },
                    { actions.onAlertSettings(a.copy(quietEndMin = Math.floorMod(a.quietEndMin + 30, 1440))) },
                    dec, inc,
                )
            }
        }
        row { BodyText(stringResource(R.string.quiet_note)) }
        row {
            PillButton(
                label = stringResource(if (s.sensitiveListener) R.string.sensitive_on else R.string.sensitive_off),
                onClick = { actions.onSensitive(!s.sensitiveListener) },
                style = if (s.sensitiveListener) PillStyle.PRIMARY else PillStyle.TONAL,
            )
        }
        row { BodyText(stringResource(R.string.sensitive_note)) }

        // ------------------------------------------------------------------ headphones
        row { GroupLabel(stringResource(R.string.hp_settings_title)) }
        row {
            ChoiceList(
                listOf<Pair<HeadphoneCategory?, String>>(
                    HeadphoneCategory.EARBUDS to stringResource(R.string.hp_cat_earbuds_title),
                    HeadphoneCategory.OVER_EAR to stringResource(R.string.hp_cat_over_ear_title),
                    null to stringResource(R.string.hp_usual_not_set),
                ),
                selected = s.usualHeadphones,
                onSelect = actions.onUsualHeadphones,
            )
        }
        row {
            ValueStepper(
                label = stringResource(R.string.set_very_loud),
                value = stringResource(R.string.alerts_threshold_value, a.headphoneLoudDba),
                onDecrease = { actions.onAlertSettings(a.copy(headphoneLoudDba = (a.headphoneLoudDba - 1).coerceAtLeast(AlertDefaults.HEADPHONE_LOUD_MIN_DBA))) },
                onIncrease = { actions.onAlertSettings(a.copy(headphoneLoudDba = (a.headphoneLoudDba + 1).coerceAtMost(AlertDefaults.HEADPHONE_LOUD_MAX_DBA))) },
                decreaseDescription = dec, increaseDescription = inc,
            )
        }
        row { BodyText(stringResource(R.string.hp_loud_note, AlertDefaults.HEADPHONE_WINDOW_MIN)) }

        // ------------------------------------------------------------------ Tono
        row { GroupLabel(stringResource(R.string.tono_settings_title)) }
        row {
            PillButton(
                label = stringResource(if (s.tonoEnabled) R.string.tono_enabled_on else R.string.tono_enabled_off),
                onClick = { actions.onTonoEnabled(!s.tonoEnabled) },
                icon = VoxIcons.Ask,
                style = if (s.tonoEnabled) PillStyle.PRIMARY else PillStyle.TONAL,
            )
        }
        row { BodyText(stringResource(R.string.tono_enabled_note)) }
        if (env.cloudAvailable && s.tonoEnabled) {
            row {
                PillButton(
                    label = stringResource(if (s.aiConsent) R.string.tono_consent_on else R.string.tono_consent_off),
                    onClick = { actions.onAiConsent(!s.aiConsent) },
                    icon = VoxIcons.Sparkle,
                    style = if (s.aiConsent) PillStyle.PRIMARY else PillStyle.TONAL,
                )
            }
        }
        row { BodyText(stringResource(if (env.cloudAvailable) R.string.tono_consent_body else R.string.tono_consent_unavailable, name)) }

        // ------------------------------------------------------------------ calibration
        row { GroupLabel(stringResource(R.string.cal_title)) }
        row {
            val record = s.calibrationFor(s.activeSource)
            MetricCard(
                icon = VoxIcons.Tune,
                label = stringResource(R.string.cal_title),
                value = if (record != null) stringResource(
                    R.string.cal_status_calibrated,
                    DateFormat.getDateInstance(DateFormat.MEDIUM, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(record.calibratedAtMs)),
                ) else stringResource(R.string.cal_status_uncalibrated),
                accent = if (record != null) VColor.AccentInk else VColor.TextSecondary,
                onClick = { navigate(Routes.CALIBRATION) },
            )
        }
        row {
            ValueStepper(
                label = stringResource(R.string.set_fine_trim),
                value = stringResource(R.string.calibration_value, s.calibrationOffsetDb),
                onDecrease = { actions.onCalibrationTrim(s.calibrationOffsetDb - 0.5) },
                onIncrease = { actions.onCalibrationTrim(s.calibrationOffsetDb + 0.5) },
                decreaseDescription = dec, increaseDescription = inc,
            )
        }
        row { BodyText(stringResource(R.string.calibration_note)) }

        // ------------------------------------------------------------------ language
        row { GroupLabel(stringResource(R.string.language)) }
        row {
            ChoiceList(
                AppLanguage.entries.map { it to stringResource(it.labelRes()) },
                selected = env.language,
                onSelect = actions.onLanguage,
            )
        }

        // ------------------------------------------------------------------ privacy & about
        row { GroupLabel(stringResource(R.string.set_privacy)) }
        row { Panel { BodyText(stringResource(R.string.set_privacy_body), color = VColor.Text) } }
        row { Panel { BodyText(stringResource(R.string.disclaimer), color = VColor.Text) } }
        row { BodyText(stringResource(R.string.set_licenses), color = VColor.TextTertiary) }

        // ------------------------------------------------------------------ advanced
        row { GroupLabel(stringResource(R.string.set_advanced)) }
        row { PillButton(stringResource(R.string.reset_dose_title), onClick = { confirmReset = true }, style = PillStyle.OUTLINED) }

        // Developer tools: one collapsed section at the very end, for testing on a watch. Debug builds only.
        if (DebugTools.available) DebugTools.settingsSection(this, env, actions, navigate, devOpen) { devOpen = !devOpen }
    }

    AlertDialog(
        visible = confirmReset,
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.reset_dose_title)) },
        text = { Text(stringResource(R.string.reset_dose_body)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = { confirmReset = false; actions.onResetDose() })
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirmReset = false }) },
    )
}

/** "22:00" for minutes after midnight. */
fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
