package com.example.voxara.phone.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.link.LinkState
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.ContentMaxWidth
import com.example.voxara.phone.ui.components.Group
import com.example.voxara.phone.ui.components.LinkButton
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.SwitchRow
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.theme.LocalReduceMotion
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.settings.SettingField
import com.example.voxara.ui.design.VoxIcons

// ------------------------------------------------------------------------------------ how it works

@Composable
fun HowItWorksScreen(onBack: () -> Unit) {
    val sections = listOf(
        Triple(VoxIcons.Mic, R.string.how_measure_title, R.string.how_measure_body),
        Triple(VoxIcons.Waves, R.string.how_aweight_title, R.string.how_aweight_body),
        Triple(VoxIcons.Clock, R.string.how_sustained_title, R.string.how_sustained_body),
        Triple(VoxIcons.Calendar, R.string.how_niosh_title, R.string.how_niosh_body),
        Triple(VoxIcons.Chart, R.string.how_who_title, R.string.how_who_body),
        Triple(VoxIcons.Headphones, R.string.how_headphones_title, R.string.how_headphones_body),
        Triple(VoxIcons.Tune, R.string.how_accuracy_title, R.string.how_accuracy_body),
        Triple(VoxIcons.Lock, R.string.how_privacy_title, R.string.how_privacy_body),
        Triple(VoxIcons.Shield, R.string.how_medical_title, R.string.how_medical_body),
    )
    VoxScreen(stringResource(R.string.how_title), onBack = onBack) {
        sections.forEach { (icon, title, body) ->
            item {
                VoxCard(Modifier.contentWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Vox.colors.brand, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text, modifier = Modifier.semantics { heading() })
                    }
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = Vox.colors.text2)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ setup

private const val STEPS = 7

/**
 * First run: what Voxara does, "never records audio", "not a medical device", the watch link,
 * then each permission and opt-in in context (every opt-in starts OFF).
 */
@Composable
fun SetupScreen(s: PhoneUiState, actions: PhoneActions, initialStep: Int = 0) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    val next: () -> Unit = { if (step < STEPS - 1) step++ else actions.onFinishSetup() }
    val c = Vox.colors
    val reduceMotion = LocalReduceMotion.current
    Surface(color = c.background, contentColor = c.text, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().weight(1f)) {
                Text(stringResource(R.string.setup_step, step + 1, STEPS), style = MaterialTheme.typography.labelMedium, color = c.text2)
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth(), color = c.brand, trackColor = c.surface2)
                Spacer(Modifier.size(24.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (reduceMotion) fadeIn(snap()) togetherWith fadeOut(snap())
                        else fadeIn() togetherWith fadeOut()
                    },
                    label = "setup",
                    modifier = Modifier.weight(1f),
                ) { current ->
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StepContent(current, s, actions)
                    }
                }
            }
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PillButton(
                    stringResource(if (step == STEPS - 1) R.string.action_done else R.string.action_continue),
                    next, modifier = Modifier.fillMaxWidth(),
                )
                if (step in 1 until STEPS - 1) {
                    LinkButton(stringResource(R.string.action_back), { step-- }, Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun StepTitle(icon: ImageVector, title: String) {
    Icon(icon, null, tint = Vox.colors.brand, modifier = Modifier.size(40.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge, color = Vox.colors.text, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = Vox.colors.text2)

@Composable
private fun StepContent(step: Int, s: PhoneUiState, actions: PhoneActions) {
    val c = Vox.colors
    when (step) {
        0 -> {
            StepTitle(VoxIcons.Waves, stringResource(R.string.setup_welcome_title))
            Body(stringResource(R.string.setup_welcome_body))
            listOf(
                VoxIcons.Chart to R.string.setup_point_1,
                VoxIcons.Vibrate to R.string.setup_point_2,
                VoxIcons.Ask to R.string.setup_point_3,
            ).forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = c.brand, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
            }
        }
        1 -> {
            StepTitle(VoxIcons.Lock, stringResource(R.string.setup_privacy_title))
            Body(stringResource(R.string.setup_privacy_body))
            VoxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(VoxIcons.Shield, null, tint = c.zoneInk(RiskZone.MODERATE), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.setup_medical), style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
            }
        }
        2 -> {
            StepTitle(VoxIcons.Watch, stringResource(R.string.setup_link_title))
            Body(stringResource(R.string.link_explain))
            VoxCard {
                when (val l = s.link) {
                    LinkState.Checking -> Text(stringResource(R.string.link_checking), color = c.text)
                    LinkState.WearApiMissing -> Text(stringResource(R.string.link_problem_no_api), color = c.text)
                    LinkState.NoWatch -> {
                        Text(stringResource(R.string.link_no_watch), style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(stringResource(R.string.link_no_watch_body), color = c.text2)
                        PillButton(stringResource(R.string.action_open_wearable), actions.onOpenWearableApp, tonal = true)
                    }
                    is LinkState.NotInstalled -> {
                        Text(stringResource(R.string.link_not_installed_body, l.watches.first().name), color = c.text)
                        PillButton(stringResource(R.string.action_install_on_watch), { actions.onInstallOnWatch(l.watches.first().id) }, tonal = true)
                        if (s.debugBuild) Text(stringResource(R.string.link_debug_install), style = MaterialTheme.typography.bodySmall, color = c.text2)
                    }
                    is LinkState.Linked -> Text(stringResource(R.string.link_linked, l.watch.name), style = MaterialTheme.typography.titleMedium, color = c.zoneInk(RiskZone.OK))
                    is LinkState.Problem -> Text(stringResource(R.string.link_problem_unreachable), color = c.text)
                }
                LinkButton(stringResource(R.string.action_retry), actions.onRefresh)
            }
        }
        3 -> {
            StepTitle(VoxIcons.Bell, stringResource(R.string.setup_notify_title))
            Body(stringResource(R.string.setup_notify_body))
            if (s.notificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Text(stringResource(R.string.setup_notify_granted), style = MaterialTheme.typography.bodyLarge, color = c.zoneInk(RiskZone.OK))
            } else {
                PillButton(stringResource(R.string.action_allow), actions.onRequestNotifications, tonal = true)
            }
        }
        4 -> {
            StepTitle(VoxIcons.Headphones, stringResource(R.string.setup_hp_title))
            Body(stringResource(R.string.phone_what))
            Body(stringResource(R.string.phone_never))
            Group {
                SwitchRow(stringResource(R.string.hp_sampling), s.phoneSampling, actions.onPhoneSampling, subtitle = stringResource(R.string.setup_off_default))
            }
            if (s.phoneSampling && !s.bluetoothGranted) {
                Body(stringResource(R.string.hp_bt_needed))
                PillButton(stringResource(R.string.action_allow), actions.onRequestBluetooth, tonal = true)
            }
        }
        5 -> {
            StepTitle(VoxIcons.Sparkle, stringResource(R.string.setup_ai_title))
            Body(stringResource(R.string.setup_ai_body))
            Group {
                SwitchRow(
                    stringResource(R.string.set_cloud), s.settings.cloudAi, { actions.onSetting(SettingField.CLOUD_AI, it.toString()) },
                    subtitle = stringResource(if (s.cloudAvailable) R.string.setup_off_default else R.string.tono_unavailable),
                    enabled = s.cloudAvailable,
                )
            }
        }
        else -> {
            StepTitle(VoxIcons.Check, stringResource(R.string.setup_done_title))
            Body(stringResource(R.string.setup_done_body))
        }
    }
}
