package com.example.voxara.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.Scenario
import com.example.voxara.text.labelRes
import com.example.voxara.ui.Routes
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.ChoiceList
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.Panel
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.ValueStepper
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import java.text.DateFormat
import java.util.Date

/** SETTINGS — every option in one scrolling list, grouped, with plain-language notes. */
@Composable
fun SettingsScreen(env: UiEnv, actions: VoxaraActions, navigate: (String) -> Unit) {
    val s = env.state
    val a = s.alertSettings
    val name = stringResource(R.string.ai_name)
    var confirmReset by remember { mutableStateOf(false) }
    val dec = stringResource(R.string.decrease_cd)
    val inc = stringResource(R.string.increase_cd)

    VoxList {
        item { Text(stringResource(R.string.card_settings), style = VType.Title, color = VColor.Text, modifier = Modifier.semantics { heading() }) }

        // ------------------------------------------------------------------ monitoring
        item { SectionLabel(stringResource(R.string.set_monitoring)) }
        item {
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
                accent = if (paused) VColor.zone(RiskZone.MODERATE) else VColor.BrandStart,
            )
        }
        item {
            val paused = s.monitoringStatus == MonitoringStatus.PAUSED
            PillButton(
                label = stringResource(if (paused) R.string.action_resume else if (s.monitoring) R.string.set_stop_monitoring else R.string.set_start_monitoring),
                onClick = { if (paused) actions.onResume() else actions.onMonitoring(!s.monitoring) },
                style = if (s.monitoring && !paused) PillStyle.TONAL else PillStyle.PRIMARY,
            )
        }
        if (s.power?.powerSaveMode == true) item { BodyText(stringResource(R.string.power_save_on), color = VColor.zone(RiskZone.MODERATE)) }
        item { BodyText(stringResource(R.string.keep_running_body)) }
        if (s.batterySettingsAvailable && s.power?.ignoringBatteryOptimizations == false) {
            item { PillButton(stringResource(R.string.battery_settings), onClick = actions.onOpenBatterySettings, style = PillStyle.OUTLINED) }
        }

        // ------------------------------------------------------------------ alerts
        item { SectionLabel(stringResource(R.string.alerts_title)) }
        item {
            ValueStepper(
                label = stringResource(R.string.set_alert_level),
                value = stringResource(R.string.alerts_threshold_value, a.thresholdDba),
                onDecrease = { actions.onAlertSettings(a.copy(thresholdDba = (a.thresholdDba - 1).coerceAtLeast(AlertDefaults.THRESHOLD_MIN_DBA))) },
                onIncrease = { actions.onAlertSettings(a.copy(thresholdDba = (a.thresholdDba + 1).coerceAtMost(AlertDefaults.THRESHOLD_MAX_DBA))) },
                decreaseDescription = dec, increaseDescription = inc,
            )
        }
        item { BodyText(stringResource(R.string.alerts_threshold_note, AlertDefaults.SUSTAINED_WINDOW_MIN)) }
        item {
            PillButton(
                label = stringResource(if (a.quietHoursEnabled) R.string.quiet_on else R.string.quiet_off, clock(a.quietStartMin), clock(a.quietEndMin)),
                onClick = { actions.onAlertSettings(a.copy(quietHoursEnabled = !a.quietHoursEnabled)) },
                style = if (a.quietHoursEnabled) PillStyle.PRIMARY else PillStyle.TONAL,
            )
        }
        if (a.quietHoursEnabled) {
            item {
                ValueStepper(
                    stringResource(R.string.quiet_start), clock(a.quietStartMin),
                    { actions.onAlertSettings(a.copy(quietStartMin = Math.floorMod(a.quietStartMin - 30, 1440))) },
                    { actions.onAlertSettings(a.copy(quietStartMin = Math.floorMod(a.quietStartMin + 30, 1440))) },
                    dec, inc,
                )
            }
            item {
                ValueStepper(
                    stringResource(R.string.quiet_end), clock(a.quietEndMin),
                    { actions.onAlertSettings(a.copy(quietEndMin = Math.floorMod(a.quietEndMin - 30, 1440))) },
                    { actions.onAlertSettings(a.copy(quietEndMin = Math.floorMod(a.quietEndMin + 30, 1440))) },
                    dec, inc,
                )
            }
        }
        item { BodyText(stringResource(R.string.quiet_note)) }
        item {
            PillButton(
                label = stringResource(if (s.sensitiveListener) R.string.sensitive_on else R.string.sensitive_off),
                onClick = { actions.onSensitive(!s.sensitiveListener) },
                style = if (s.sensitiveListener) PillStyle.PRIMARY else PillStyle.TONAL,
            )
        }
        item { BodyText(stringResource(R.string.sensitive_note)) }

        // ------------------------------------------------------------------ headphones
        item { SectionLabel(stringResource(R.string.hp_settings_title)) }
        item {
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
        item {
            ValueStepper(
                label = stringResource(R.string.set_very_loud),
                value = stringResource(R.string.alerts_threshold_value, a.headphoneLoudDba),
                onDecrease = { actions.onAlertSettings(a.copy(headphoneLoudDba = (a.headphoneLoudDba - 1).coerceAtLeast(AlertDefaults.HEADPHONE_LOUD_MIN_DBA))) },
                onIncrease = { actions.onAlertSettings(a.copy(headphoneLoudDba = (a.headphoneLoudDba + 1).coerceAtMost(AlertDefaults.HEADPHONE_LOUD_MAX_DBA))) },
                decreaseDescription = dec, increaseDescription = inc,
            )
        }
        item { BodyText(stringResource(R.string.hp_loud_note, AlertDefaults.HEADPHONE_WINDOW_MIN)) }

        // ------------------------------------------------------------------ Tono
        item { SectionLabel(stringResource(R.string.tono_settings_title)) }
        if (env.cloudAvailable) {
            item {
                PillButton(
                    label = stringResource(if (s.aiConsent) R.string.tono_consent_on else R.string.tono_consent_off),
                    onClick = { actions.onAiConsent(!s.aiConsent) },
                    icon = VoxIcons.Sparkle,
                    style = if (s.aiConsent) PillStyle.PRIMARY else PillStyle.TONAL,
                )
            }
        }
        item { BodyText(stringResource(if (env.cloudAvailable) R.string.tono_consent_body else R.string.tono_consent_unavailable, name)) }

        // ------------------------------------------------------------------ calibration
        item { SectionLabel(stringResource(R.string.cal_title)) }
        item {
            val record = s.calibrationFor(s.activeSource)
            MetricCard(
                icon = VoxIcons.Tune,
                label = stringResource(R.string.cal_title),
                value = if (record != null) stringResource(
                    R.string.cal_status_calibrated,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.calibratedAtMs)),
                ) else stringResource(R.string.cal_status_uncalibrated),
                accent = if (record != null) VColor.zone(RiskZone.OK) else VColor.zone(RiskZone.MODERATE),
                onClick = { navigate(Routes.CALIBRATION) },
            )
        }
        item {
            ValueStepper(
                label = stringResource(R.string.set_fine_trim),
                value = stringResource(R.string.calibration_value, s.calibrationOffsetDb),
                onDecrease = { actions.onCalibrationTrim(s.calibrationOffsetDb - 0.5) },
                onIncrease = { actions.onCalibrationTrim(s.calibrationOffsetDb + 0.5) },
                decreaseDescription = dec, increaseDescription = inc,
            )
        }
        item { BodyText(stringResource(R.string.calibration_note)) }

        // ------------------------------------------------------------------ language
        item { SectionLabel(stringResource(R.string.language)) }
        item {
            ChoiceList(
                AppLanguage.entries.map { it to stringResource(it.labelRes()) },
                selected = env.language,
                onSelect = actions.onLanguage,
            )
        }

        // ------------------------------------------------------------------ privacy & about
        item { SectionLabel(stringResource(R.string.set_privacy)) }
        item { Panel { BodyText(stringResource(R.string.set_privacy_body), color = VColor.Text) } }
        item { Panel { BodyText(stringResource(R.string.disclaimer), color = VColor.Text) } }
        item { BodyText(stringResource(R.string.set_licenses), color = VColor.TextTertiary) }

        // ------------------------------------------------------------------ advanced
        item { SectionLabel(stringResource(R.string.set_advanced)) }
        item { PillButton(stringResource(R.string.reset_dose_title), onClick = { confirmReset = true }, style = PillStyle.OUTLINED) }

        if (env.debug) {
            item { SectionLabel(stringResource(R.string.set_debug), color = VColor.BrandStart) }
            item {
                Panel { CalibrationProbePanel(s, actions.onProbe, actions.onSaveSourceOffset) }
            }
            if (s.simulated) {
                item { BodyText(stringResource(R.string.bench_note)) }
                item {
                    ChoiceList(
                        Scenario.entries.map { it to stringResource(R.string.scenario_chip, stringResource(it.labelRes()), it.dba.toInt()) },
                        selected = null as Scenario?,
                        onSelect = { it?.let(actions.onScenario) },
                    )
                }
            }
        }
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
