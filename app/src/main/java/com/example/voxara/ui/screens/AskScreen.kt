package com.example.voxara.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voxara.R
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ReplyChip
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VSpace
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.theme.LocalReduceMotion
import com.example.voxara.voice.VoiceTurn

/**
 * ASK — talk to Tono. Speak (on-device recognition), or tap a quick reply. Symptoms get a fixed
 * safe answer; cloud AI is used only with consent; otherwise the on-device answers reply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(env: UiEnv, actions: VoxaraActions) {
    val name = stringResource(R.string.ai_name)
    val turn = env.turn
    val listening = turn.phase == VoiceTurn.Phase.LISTENING
    val coach = env.coach
    val showCoach = turn.handedOff || turn.phase == VoiceTurn.Phase.IDLE
    val reply = coach.reply

    VoxList {
        item { SectionLabel(name.uppercase(), color = VColor.BrandStart) }
        if (turn.heard.isNotBlank()) {
            item { BodyText(stringResource(R.string.voice_quoted, turn.heard), color = VColor.TextSecondary, maxLines = 3) }
        }
        item {
            val text = when {
                listening -> stringResource(R.string.voice_listening)
                turn.phase == VoiceTurn.Phase.THINKING || coach.loading -> stringResource(R.string.tono_loading, name)
                !showCoach && turn.answer.isNotBlank() -> turn.answer
                reply != null -> reply.insight
                else -> stringResource(R.string.ask_hint, name)
            }
            HeroStatus(
                text, maxLines = 5,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (listening) item { ListeningWave(turn.amplitude) }
        if (showCoach && reply != null && !coach.loading && !listening) {
            if (reply.why.isNotBlank()) item { BodyText(reply.why, color = VColor.TextSecondary) }
            item { BodyText(reply.suggestion, color = VColor.BrandStart) }
            item {
                SectionLabel(
                    stringResource(
                        when (coach.source) {
                            ReplySource.CLOUD_AI -> R.string.tono_source_ai
                            ReplySource.SAFETY -> R.string.tono_source_safety
                            else -> R.string.tono_source_offline
                        }
                    ),
                    color = VColor.TextTertiary,
                )
            }
            if (coach.cloudFailed) item { BodyText(stringResource(R.string.tono_cloud_failed), color = VColor.TextSecondary) }
            if (reply.chips.isNotEmpty()) {
                item {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VSpace.xs, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(VSpace.xs),
                    ) {
                        reply.chips.forEach { chip -> ReplyChip(chip, onClick = { actions.onAsk(CoachTask.ASK, chip) }) }
                    }
                }
            }
        }
        item {
            PillButton(
                label = stringResource(if (listening) R.string.action_stop else R.string.ask_speak),
                onClick = actions.onVoiceArm,
                icon = if (listening) VoxIcons.Pause else VoxIcons.Mic,
                style = if (listening) PillStyle.TONAL else PillStyle.PRIMARY,
            )
        }
        item {
            PillButton(
                label = stringResource(R.string.ask_today),
                onClick = { actions.onAsk(CoachTask.DAILY_INSIGHT, null) },
                icon = VoxIcons.Sparkle,
                style = PillStyle.TONAL,
            )
        }
        item { BodyText(stringResource(R.string.tono_not_medical), color = VColor.TextTertiary) }
    }
}

/** Your voice as a line of bars; still when motion is reduced. */
@Composable
private fun ListeningWave(amplitude: Float) {
    val a by animateFloatAsState(if (LocalReduceMotion.current) 0.4f else amplitude, label = "wave")
    Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        val n = 9
        val gap = size.width / (n + 1)
        for (i in 1..n) {
            val shape = 1f - kotlin.math.abs(i - (n + 1) / 2f) / n
            val h = size.height * (0.15f + 0.85f * a * shape)
            val x = gap * i
            drawLine(
                VColor.BrandStart, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
}
