package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import com.example.voxara.R
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.ChoiceList
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.Panel
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList

private enum class OnbStep { WELCOME, PRIVACY, ESTIMATES, PERMISSIONS, ALERT_LEVEL, TONO, KEEP_RUNNING }

/**
 * ONBOARDING — first run only. Short steps: what Voxara does, that it never records, that it is
 * not a medical device and measures estimates, the permissions (each asked in context with its
 * reason), the alert level, the cloud-AI choice, and how to keep monitoring running.
 */
@Composable
fun OnboardingScreen(env: UiEnv, actions: VoxaraActions) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val steps = OnbStep.entries.filter { it != OnbStep.TONO || env.cloudAvailable }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val name = stringResource(R.string.ai_name)
    val next: () -> Unit = { if (index < steps.lastIndex) index++ else actions.onFinishOnboarding() }
    val a = env.state.alertSettings

    VoxList {
        item { SectionLabel(stringResource(R.string.onb_progress, index + 1, steps.size)) }
        when (step) {
            OnbStep.WELCOME -> {
                item { Icon(VoxIcons.Waves, contentDescription = null, tint = VColor.BrandStart, modifier = Modifier.size(32.dp)) }
                item { HeroStatus(stringResource(R.string.onb_welcome_title), modifier = Modifier.semantics { heading() }) }
                item { BodyText(stringResource(R.string.onb_welcome_body, name), color = VColor.Text) }
            }
            OnbStep.PRIVACY -> {
                item { Icon(VoxIcons.Shield, contentDescription = null, tint = VColor.BrandStart, modifier = Modifier.size(32.dp)) }
                item { HeroStatus(stringResource(R.string.onb_privacy_title), modifier = Modifier.semantics { heading() }) }
                item { BodyText(stringResource(R.string.onb_privacy_body), color = VColor.Text) }
            }
            OnbStep.ESTIMATES -> {
                item { Icon(VoxIcons.Info, contentDescription = null, tint = VColor.BrandStart, modifier = Modifier.size(32.dp)) }
                item { HeroStatus(stringResource(R.string.onb_estimates_title), modifier = Modifier.semantics { heading() }) }
                item { BodyText(stringResource(R.string.onb_estimates_body), color = VColor.Text) }
            }
            OnbStep.PERMISSIONS -> {
                item { HeroStatus(stringResource(R.string.onb_permissions_title), modifier = Modifier.semantics { heading() }) }
                item {
                    Panel {
                        BodyText(stringResource(R.string.onb_mic_reason), color = VColor.Text)
                        PillButton(
                            stringResource(if (env.micGranted) R.string.onb_granted else R.string.onb_allow_mic),
                            onClick = actions.onRequestMic, enabled = !env.micGranted,
                            icon = if (env.micGranted) VoxIcons.Check else VoxIcons.Mic,
                        )
                    }
                }
                item {
                    Panel {
                        BodyText(stringResource(R.string.onb_notif_reason), color = VColor.Text)
                        PillButton(
                            stringResource(if (env.notificationsGranted) R.string.onb_granted else R.string.onb_allow_notifications),
                            onClick = actions.onRequestNotifications, enabled = !env.notificationsGranted,
                            icon = if (env.notificationsGranted) VoxIcons.Check else null,
                            style = PillStyle.TONAL,
                        )
                    }
                }
                if (env.bluetoothNeeded) {
                    item {
                        Panel {
                            BodyText(stringResource(R.string.onb_bt_reason), color = VColor.Text)
                            PillButton(stringResource(R.string.hp_allow_bluetooth), onClick = actions.onRequestBluetooth, style = PillStyle.TONAL)
                        }
                    }
                }
            }
            OnbStep.ALERT_LEVEL -> {
                item { HeroStatus(stringResource(R.string.onb_alert_title), modifier = Modifier.semantics { heading() }) }
                item { BodyText(stringResource(R.string.onb_alert_body, AlertDefaults.SUSTAINED_WINDOW_MIN), color = VColor.Text) }
                item {
                    ChoiceList(
                        listOf(80, 85, 90, 95).map {
                            it to stringResource(if (it == AlertDefaults.THRESHOLD_DBA) R.string.onb_alert_option_default else R.string.onb_alert_option, it)
                        },
                        selected = a.thresholdDba,
                        onSelect = { actions.onAlertSettings(a.copy(thresholdDba = it)) },
                    )
                }
            }
            OnbStep.TONO -> {
                item { Icon(VoxIcons.Sparkle, contentDescription = null, tint = VColor.BrandStart, modifier = Modifier.size(32.dp)) }
                item { HeroStatus(stringResource(R.string.onb_tono_title, name), modifier = Modifier.semantics { heading() }) }
                item { BodyText(stringResource(R.string.tono_consent_body, name), color = VColor.Text) }
                item {
                    ChoiceList(
                        listOf(true to stringResource(R.string.onb_tono_yes), false to stringResource(R.string.onb_tono_no)),
                        selected = env.state.aiConsent,
                        onSelect = actions.onAiConsent,
                    )
                }
            }
            OnbStep.KEEP_RUNNING -> {
                item { HeroStatus(stringResource(R.string.keep_running_title_plain), modifier = Modifier.semantics { heading() }) }
                item { BodyText(stringResource(R.string.keep_running_body), color = VColor.Text) }
                item { BodyText(stringResource(R.string.onb_calibrate_later), color = VColor.TextSecondary) }
            }
        }
        item {
            PillButton(
                label = stringResource(if (index == steps.lastIndex) R.string.onb_start else R.string.onb_next),
                onClick = next,
                // The microphone is the one thing Voxara cannot work without.
                enabled = step != OnbStep.PERMISSIONS || env.micGranted,
            )
        }
        if (step == OnbStep.PERMISSIONS && !env.micGranted) {
            item { BodyText(stringResource(R.string.onb_mic_required), color = VColor.TextSecondary) }
        }
        if (index > 0) {
            item { PillButton(stringResource(R.string.onb_back), onClick = { index-- }, style = PillStyle.OUTLINED) }
        }
    }
}
