package com.example.voxara.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import com.example.voxara.BuildConfig
import com.example.voxara.R
import com.example.voxara.core.headphones.HeadphoneModeChoice
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
import com.example.voxara.ui.design.ChoiceList
import com.example.voxara.ui.design.GroupLabel
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
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
 * Below it, HEADPHONES MODE: Auto (follow the detection), On or Off. It is a layer over whichever
 * situation is chosen, not a sixth situation, so both are set here and both stay in the chip.
 */
@Composable
fun ModeScreen(env: UiEnv, actions: VoxaraActions) {
    val s = env.state
    VoxList {
        row { ScreenTitle(stringResource(R.string.mode_title)) }
        row {
            ChoiceList(
                SituationChoice.entries.map { it to stringResource(it.labelRes()) },
                selected = s.situationChoice,
                onSelect = actions.onSituationChoice,
            )
        }
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
        row { GroupLabel(stringResource(R.string.hp_mode_title)) }
        row {
            ChoiceList(
                HeadphoneModeChoice.entries.map { it to stringResource(it.labelRes()) },
                selected = s.headphoneMode,
                onSelect = actions.onHeadphoneMode,
            )
        }
        row { BodyText(stringResource(s.headphoneMode.blurbRes()), color = VColor.Text) }
        if (s.headphoneMode == HeadphoneModeChoice.AUTO) {
            row {
                BodyText(
                    stringResource(
                        when {
                            !s.listening.detected -> R.string.hp_mode_status_none
                            s.listening.detectedBy == LayerSource.PHONE -> R.string.hp_mode_status_phone
                            else -> R.string.hp_mode_status_watch
                        }
                    ),
                    color = VColor.TextSecondary,
                )
            }
        }
        row { BodyText(stringResource(R.string.hp_mode_rule)) }
        // The optional Bluetooth permission tells a speaker or car kit from headphones; it used to sit on the Headphones page.
        if (env.bluetoothNeeded && (s.headphoneCategory != null || s.listening.detected)) {
            row { PillButton(stringResource(R.string.hp_allow_bluetooth), onClick = actions.onRequestBluetooth, style = PillStyle.TONAL) }
            row { BodyText(stringResource(R.string.hp_bluetooth_note)) }
        }
    }
}

@StringRes
private fun HeadphoneModeChoice.labelRes(): Int = when (this) {
    HeadphoneModeChoice.AUTO -> R.string.hp_mode_choice_auto
    HeadphoneModeChoice.ON -> R.string.hp_mode_choice_on
    HeadphoneModeChoice.OFF -> R.string.hp_mode_choice_off
}

@StringRes
private fun HeadphoneModeChoice.blurbRes(): Int = when (this) {
    HeadphoneModeChoice.AUTO -> R.string.hp_mode_blurb_auto
    HeadphoneModeChoice.ON -> R.string.hp_mode_blurb_on
    HeadphoneModeChoice.OFF -> R.string.hp_mode_blurb_off
}

/** "Street", or "Auto · Street" when Auto chose it; "+ Headphones" is appended while headphones mode is on ("Transport + Headphones"). */
@Composable
fun modeValue(choice: SituationChoice, mode: SituationMode, layer: ListeningLayerState = ListeningLayerState.OFF): String {
    val situation = stringResource(mode.labelRes())
    val withLayer = if (layer.active) stringResource(R.string.mode_plus_headphones, situation) else situation
    return if (choice == SituationChoice.AUTO) stringResource(R.string.mode_auto_value, withLayer) else withLayer
}
