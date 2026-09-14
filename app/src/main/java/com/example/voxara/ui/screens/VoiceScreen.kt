package com.example.voxara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voxara.ui.gauge.VoiceWave
import com.example.voxara.ui.theme.Vox
import com.example.voxara.voice.VoiceTurn

/**
 * VOICE / AI — watch microphone only. No phone, no companion app, no cloud round-trip for the
 * common questions. Answers are one sentence, spoken and shown, and always end in an action.
 */
@Composable
fun VoiceScreen(
    turn: VoiceTurn.Turn,
    onArm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listening = turn.phase == VoiceTurn.Phase.LISTENING
    val thinking = turn.phase == VoiceTurn.Phase.THINKING

    VoxScreen(
        modifier = modifier.semantics {
            contentDescription = when {
                listening -> "Listening"
                turn.answer.isNotBlank() -> turn.answer
                else -> "Voice mode. Tap to ask."
            }
        },
        background = Color.Black,
        behind = {
            // A true-black field with the faintest green wash — lit pixels only.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF041008), Color.Black),
                        )
                    )
            )
        },
    ) {
        ColumnCenter {
            Meta(
                text = when (turn.phase) {
                    VoiceTurn.Phase.LISTENING -> "LISTENING"
                    VoiceTurn.Phase.THINKING -> "THINKING"
                    VoiceTurn.Phase.UNAVAILABLE -> "NO ON-DEVICE ASR"
                    else -> "ASK VOXARA"
                },
                color = if (listening) Vox.Safe else Vox.Ink3,
                small = true,
            )

            Spacer(Modifier.height(8.dp))

            VoiceWave(
                amplitude = if (listening) turn.amplitude else if (thinking) 0.25f else 0.08f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )

            Spacer(Modifier.height(8.dp))

            if (turn.heard.isNotBlank()) {
                TitleLine("“${turn.heard}”", color = Color(0xFFE6F6EC))
            } else if (!listening) {
                TitleLine("“Is it safe in here?”", color = Color(0xFFE6F6EC))
            }

            if (turn.answer.isNotBlank()) {
                Box(Modifier.padding(top = 6.dp)) {
                    Body(turn.answer, color = Color(0xFF7E93A8), maxLines = 3)
                }
            }

            Box(Modifier.padding(top = 12.dp)) {
                Capsule(
                    text = if (listening) "STOP" else "ASK",
                    tint = if (listening) Vox.Signal else Vox.Safe,
                    onClick = onArm,
                )
            }
        }
    }
}
