package com.example.voxara.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import com.example.voxara.BuildConfig
import com.example.voxara.R
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.core.scene.SceneModel
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.designsystem.R as DsR
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.ScreenTitle
import com.example.voxara.ui.design.choiceRows
import com.example.voxara.ui.design.GroupLabel
import com.example.voxara.ui.Routes
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.row
import com.example.voxara.ui.design.blurbRes
import com.example.voxara.ui.design.labelRes

/**
 * MODE — Auto (the default) or one fixed situation mode. Opened from the chip on Now and from
 * Settings. Picking one gives a minimal confirmation tap; the choice syncs to the phone. Under
 * Auto, one line says what automatic detection concluded (or that it is still listening).
 *
 * Below it, what HEADPHONES MODE found. It is fully automatic (a layer over whichever situation is
 * chosen, not a sixth situation): there is nothing to switch, only what it detected and the rule it follows.
 */
@Composable
fun ModeScreen(env: UiEnv, actions: VoxaraActions, navigate: (String) -> Unit = {}) {
    val s = env.state
    VoxList {
        row { ScreenTitle(stringResource(R.string.mode_title)) }
        choiceRows(SituationChoice.entries, s.situationChoice, actions.onSituationChoice) { stringResource(it.labelRes()) }
        row { BodyText(stringResource(s.situationChoice.blurbRes()), color = VColor.Text) }
        // Auto says what it concluded, or that it is still listening (Street meanwhile).
        if (s.situationChoice == SituationChoice.AUTO) {
            row {
                val detected = s.detectedMode
                BodyText(
                    when {
                        detected != null -> stringResource(R.string.mode_auto_detected, stringResource(detected.labelRes()))
                        // Licence gate: no scene model in this build — Street, plus the Concert rule.
                        !BuildConfig.SCENE_MODEL_ENABLED || !SceneModel.available -> stringResource(R.string.mode_auto_unavailable)
                        else -> stringResource(R.string.mode_auto_listening, stringResource(s.mode.labelRes()))
                    },
                    color = VColor.TextSecondary,
                )
            }
        }
        row { BodyText(stringResource(DsR.string.situation_rule)) }

        // ------------------------------------------------------------------ headphones mode
        // Fully automatic: there is nothing to choose, only what it found and the rule it follows.
        row { GroupLabel(stringResource(R.string.hp_mode_title)) }
        row {
            BodyText(
                stringResource(
                    when {
                        !s.listening.detected -> R.string.hp_mode_status_none
                        s.listening.detectedBy == LayerSource.PHONE -> R.string.hp_mode_status_phone
                        else -> R.string.hp_mode_status_watch
                    }
                ),
                color = VColor.Text,
            )
        }
        if (s.headphonePossibleSpeaker) row { BodyText(stringResource(R.string.hp_possible_speaker_note), color = VColor.TextSecondary) }
        // The way out while it is on, and the speakers the wearer asked to remember (undoable here).
        if (s.listening.active) {
            row { PillButton(stringResource(R.string.hp_not_headphones), onClick = { navigate(Routes.HP_EXIT) }, icon = VoxIcons.Headphones, style = PillStyle.TONAL) }
        }
        if (s.rememberedSpeakers > 0) {
            row { BodyText(stringResource(R.string.hp_speakers_count, s.rememberedSpeakers), color = VColor.TextSecondary) }
            row { PillButton(stringResource(R.string.hp_forget_speakers), onClick = actions.onForgetSpeakers, style = PillStyle.OUTLINED) }
        }
        row { BodyText(stringResource(R.string.hp_mode_auto_note), color = VColor.TextSecondary) }
        row { BodyText(stringResource(R.string.hp_mode_rule)) }
        // The optional Bluetooth permission tells a speaker or car kit from headphones; it used to sit on the Headphones page.
        if (env.bluetoothNeeded && (s.headphoneCategory != null || s.listening.detected)) {
            row { PillButton(stringResource(R.string.hp_allow_bluetooth), onClick = actions.onRequestBluetooth, style = PillStyle.TONAL) }
            row { BodyText(stringResource(R.string.hp_bluetooth_note)) }
        }
    }
}

/** "Street", or "Auto · Street" when Auto chose it; "+ Headphones" is appended while headphones mode is on ("Transport + Headphones"). */
@Composable
fun modeValue(choice: SituationChoice, mode: SituationMode, layer: ListeningLayerState = ListeningLayerState.OFF): String {
    val situation = stringResource(mode.labelRes())
    val withLayer = if (layer.active) stringResource(R.string.mode_plus_headphones, situation) else situation
    return if (choice == SituationChoice.AUTO) stringResource(R.string.mode_auto_value, withLayer) else withLayer
}
