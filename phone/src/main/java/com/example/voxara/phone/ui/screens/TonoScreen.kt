package com.example.voxara.phone.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.voxara.phone.ui.components.EdgeFade
import com.example.voxara.phone.ui.components.EmptyState
import com.example.voxara.phone.ui.components.GroupNote
import com.example.voxara.phone.ui.components.PillButton
import com.example.voxara.phone.ui.components.VoxScreen
import com.example.voxara.phone.ui.components.contentWidth
import com.example.voxara.phone.ui.theme.Vox
import com.example.voxara.phone.ui.theme.VoxSpace
import com.example.voxara.settings.SettingField
import com.example.voxara.ui.design.VoxIcons

/**
 * TONO — a calm chat. One short notice that is always there (who words the answers, not medical advice, symptom
 * questions are never sent), two suggestions, the conversation as bubbles (the question on the right in the accent, each
 * reply grouped on one surface with where it came from), and one input at the bottom.
 */
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
            item(key = "off") {
                EmptyState(
                    VoxIcons.Ask, stringResource(R.string.tono_off_title), stringResource(R.string.tono_off_body),
                    Modifier.contentWidth(), stringResource(R.string.tono_turn_on),
                    { actions.onSetting(SettingField.TONO_ENABLED, "true") },
                )
            }
            return@VoxScreen
        }
        item(key = "notice") { Notice(s.settings.cloudAi, Modifier.contentWidth()) }
        if (!s.settings.cloudAi && s.cloudAvailable) {
            item(key = "optin") {
                Banner(
                    VoxIcons.Sparkle, stringResource(R.string.tono_optin_title), stringResource(R.string.tono_optin_body),
                    tint = Vox.colors.brand, modifier = Modifier.contentWidth(),
                    action = stringResource(R.string.tono_optin_action),
                    onAction = { actions.onSetting(SettingField.CLOUD_AI, "true") },
                )
            }
        }
        item(key = "suggestions") { Suggestions(actions, Modifier.contentWidth()) }
        if (s.chat.isEmpty() && !s.coach.loading) {
            item(key = "empty") { GroupNote(stringResource(R.string.tono_empty), Modifier.contentWidth()) }
        }
        s.chat.forEachIndexed { i, turn -> item(key = "turn-$i") { ChatBubbles(turn, actions) } }
        if (s.coach.loading) {
            item(key = "thinking") {
                Text(
                    stringResource(R.string.tono_thinking), style = MaterialTheme.typography.bodyMedium, color = Vox.colors.text2,
                    modifier = Modifier.contentWidth().padding(horizontal = VoxSpace.m).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** The standing notice, one short line: AI-assisted (or built-in) · not medical advice · symptom questions never sent. */
@Composable
private fun Notice(cloud: Boolean, modifier: Modifier) {
    val c = Vox.colors
    Row(modifier.padding(horizontal = VoxSpace.xxs), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
        Icon(if (cloud) VoxIcons.Sparkle else VoxIcons.Shield, null, tint = c.text2, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(
            stringResource(if (cloud) R.string.tono_notice_ai else R.string.tono_notice_offline),
            style = MaterialTheme.typography.bodySmall, color = c.text2,
        )
    }
}

/** The two things Tono can always do, as tonal pills (side by side when they fit, else one under the other). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(actions: PhoneActions, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(VoxSpace.xs), verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
        PillButton(stringResource(R.string.tono_today_button), { actions.onAsk(CoachTask.DAILY_INSIGHT, null) }, icon = VoxIcons.Calendar, tonal = true)
        PillButton(stringResource(R.string.tono_weekly_button), { actions.onAsk(CoachTask.WEEKLY_SUMMARY, null) }, icon = VoxIcons.Sparkle, tonal = true)
    }
}

private val QuestionShape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
private val ReplyShape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubbles(turn: ChatTurn, actions: PhoneActions) {
    val c = Vox.colors
    Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
        if (turn.question != null) {
            Box(Modifier.fillMaxWidth().padding(start = VoxSpace.xl), contentAlignment = Alignment.CenterEnd) {
                Surface(shape = QuestionShape, color = c.accent.fill, contentColor = c.accent.onFill) {
                    Text(turn.question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 520.dp))
                }
            }
        }
        val r = turn.reply
        // the reply: one grouped bubble, its parts in order of weight, where it came from at its foot
        Surface(
            shape = ReplyShape, color = c.surface, contentColor = c.text,
            modifier = Modifier.padding(end = VoxSpace.l).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(Modifier.padding(VoxSpace.m), verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
                Text(r.status, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(r.insight, style = MaterialTheme.typography.bodyMedium, color = c.text)
                if (r.why.isNotBlank()) Text(r.why, style = MaterialTheme.typography.bodyMedium, color = c.text2)
                if (r.suggestion.isNotBlank()) Text(r.suggestion, style = MaterialTheme.typography.bodyMedium, color = c.brand)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = VoxSpace.xxs)) {
                    val (icon, label, tint) = when (turn.source) {
                        ReplySource.CLOUD_AI -> Triple(VoxIcons.Sparkle, R.string.tono_source_ai, c.text2)
                        ReplySource.SAFETY -> Triple(VoxIcons.Shield, R.string.tono_source_safety, c.caution)
                        else -> Triple(VoxIcons.Check, R.string.tono_source_offline, c.text2)
                    }
                    Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint)
                }
                if (turn.cloudFailed) Text(stringResource(R.string.tono_cloud_failed), style = MaterialTheme.typography.bodySmall, color = c.text2)
            }
        }
        if (r.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(VoxSpace.xs), verticalArrangement = Arrangement.spacedBy(VoxSpace.xs)) {
                r.chips.forEach { chip -> PillButton(chip, { actions.onAsk(CoachTask.ASK, chip) }, tonal = true) }
            }
        }
    }
}

/**
 * The one input: a capsule (the field and its send button inside it) on the page, the conversation dissolving behind it.
 * It sits above the tab bar and rises with the keyboard.
 */
@Composable
private fun AskBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val c = Vox.colors
    Column(Modifier.fillMaxWidth()) {
        EdgeFade(top = false, height = 24.dp)
        Box(
            Modifier.fillMaxWidth().background(c.background.copy(alpha = 0.92f)).navigationBarsPadding().imePadding()
                .padding(horizontal = VoxSpace.m, vertical = VoxSpace.xs),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape, color = c.surface, contentColor = c.text, border = BorderStroke(1.dp, c.outline),
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = VoxSpace.xxs, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = value,
                        onValueChange = { onChange(it.take(200)) },
                        placeholder = { Text(stringResource(R.string.tono_placeholder), maxLines = 2) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        singleLine = false, maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = c.brand, focusedPlaceholderColor = c.text2, unfocusedPlaceholderColor = c.text2,
                        ),
                    )
                    FilledIconButton(
                        onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = c.accent.fill, contentColor = c.accent.onFill,
                            disabledContainerColor = c.surface2, disabledContentColor = c.text3,
                        ),
                    ) { Icon(VoxIcons.Forward, contentDescription = stringResource(R.string.tono_send)) }
                }
            }
        }
    }
}
