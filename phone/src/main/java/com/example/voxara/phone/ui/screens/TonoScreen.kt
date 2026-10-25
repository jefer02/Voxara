package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.phone.R
import com.example.voxara.phone.ui.ChatTurn
import com.example.voxara.phone.ui.PhoneActions
import com.example.voxara.phone.ui.PhoneUiState
import com.example.voxara.phone.ui.components.Banner
import com.example.voxara.phone.ui.components.ContentMaxWidth
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.VoxCard
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.settings.SettingField
import com.example.voxara.ui.design.VoxIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TonoScreen(s: PhoneUiState, actions: PhoneActions, onBack: (() -> Unit)? = null) {
    val tono = s.settings.tonoEnabled
    var question by rememberSaveable { mutableStateOf("") }
    val send = {
        val q = question.trim()
        if (q.isNotEmpty()) { actions.onAsk(CoachTask.ASK, q); question = "" }
    }
    VoxScreen(
        title = stringResource(R.string.tono_title),
        onBack = onBack,
        bottomBar = { if (tono) AskBar(question, { question = it }, send) },
    ) {
        if (!tono) {
            item {
                EmptyState(
                    VoxIcons.Ask, stringResource(R.string.tono_off_title), stringResource(R.string.tono_off_body),
                    Modifier.contentWidth(), stringResource(R.string.tono_turn_on),
                    { actions.onSetting(SettingField.TONO_ENABLED, "true") },
                )
            }
            return@VoxScreen
        }
        if (!s.settings.cloudAi) {
            item {
                if (s.cloudAvailable) {
                    Banner(
                        VoxIcons.Sparkle, stringResource(R.string.tono_optin_title), stringResource(R.string.tono_optin_body),
                        tint = Vox.colors.brand, modifier = Modifier.contentWidth(),
                        action = stringResource(R.string.tono_optin_action),
                        onAction = { actions.onSetting(SettingField.CLOUD_AI, "true") },
                    )
                } else {
                    Banner(
                        VoxIcons.Info, stringResource(R.string.tono_optin_title), stringResource(R.string.tono_unavailable),
                        tint = Vox.colors.text2, modifier = Modifier.contentWidth(),
                    )
                }
            }
        }
        item {
            VoxCard(Modifier.contentWidth()) {
                Text(stringResource(R.string.tono_weekly_title), style = MaterialTheme.typography.titleMedium, color = Vox.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.tono_weekly_button), { actions.onAsk(CoachTask.WEEKLY_SUMMARY, null) }, icon = VoxIcons.Sparkle, tonal = true)
                }
                PillButton(stringResource(R.string.tono_today_button), { actions.onAsk(CoachTask.DAILY_INSIGHT, null) }, icon = VoxIcons.Calendar, tonal = true)
            }
        }
        if (s.chat.isEmpty() && !s.coach.loading) {
            item { GroupNote(stringResource(R.string.tono_empty), Modifier.contentWidth()) }
        }
        s.chat.forEach { turn -> item { ChatBubbles(turn, actions) } }
        if (s.coach.loading) {
            item {
                Text(
                    stringResource(R.string.tono_thinking), style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2,
                    modifier = Modifier.contentWidth().padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        item { GroupNote(stringResource(R.string.tono_not_medical), Modifier.contentWidth()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubbles(turn: ChatTurn, actions: PhoneActions) {
    val c = Vox.colors
    Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (turn.question != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), color = c.accent.fill, contentColor = c.accent.onFill) {
                    Text(turn.question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 520.dp))
                }
            }
        }
        val r = turn.reply
        VoxCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, padding = 16.dp) {
            Text(r.status, style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(r.insight, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (r.why.isNotBlank()) Text(r.why, style = MaterialTheme.typography.bodyMedium, color = c.text2)
            Text(r.suggestion, style = MaterialTheme.typography.bodyLarge, color = c.brand)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, label, tint) = when (turn.source) {
                    ReplySource.CLOUD_AI -> Triple(VoxIcons.Sparkle, R.string.tono_source_ai, c.text3)
                    ReplySource.SAFETY -> Triple(VoxIcons.Shield, R.string.tono_source_safety, c.caution)
                    else -> Triple(VoxIcons.Check, R.string.tono_source_offline, c.text3)
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint)
            }
            if (turn.cloudFailed) Text(stringResource(R.string.tono_cloud_failed), style = MaterialTheme.typography.bodySmall, color = c.text2)
            if (r.chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.chips.forEach { chip ->
                        SuggestionChip(onClick = { actions.onAsk(CoachTask.ASK, chip) }, label = { Text(chip) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AskBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val c = Vox.colors
    Surface(color = c.surface, contentColor = c.text) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.take(200)) },
                placeholder = { Text(stringResource(R.string.tono_placeholder), maxLines = 2) },
                modifier = Modifier.weight(1f, fill = true).widthIn(max = ContentMaxWidth),
                shape = CircleShape,
                singleLine = false, maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent.fill, unfocusedBorderColor = c.outline,
                    focusedContainerColor = c.surface2, unfocusedContainerColor = c.surface2, cursorColor = c.brand,
                ),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent.fill, contentColor = c.accent.onFill),
            ) { Icon(VoxIcons.Forward, contentDescription = stringResource(R.string.tono_send)) }
        }
    }
}
