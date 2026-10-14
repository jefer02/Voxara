package com.example.voxara.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material3.Text
import com.example.voxara.BuildConfig
import com.example.voxara.R
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import com.example.voxara.designsystem.R as DsR
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.ChoiceList
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.blurbRes
import com.example.voxara.ui.design.labelRes

/**
 * MODE — Auto (the default) or one fixed situation mode. Opened from the chip on Now and from
 * Settings. Picking one gives a minimal confirmation tap; the choice syncs to the phone. Under
 * Auto, one line says what automatic detection concluded (or that it is still listening).
 */
@Composable
fun ModeScreen(env: UiEnv, actions: VoxaraActions) {
    val s = env.state
    VoxList {
        item { Text(stringResource(R.string.mode_title), style = VType.Title, color = VColor.Text, modifier = Modifier.semantics { heading() }) }
        item {
            ChoiceList(
                SituationChoice.entries.map { it to stringResource(it.labelRes()) },
                selected = s.situationChoice,
                onSelect = actions.onSituationChoice,
            )
        }
        item { BodyText(stringResource(s.situationChoice.blurbRes()), color = VColor.Text) }
        // Auto says what it concluded, or that it is still listening (Street meanwhile).
        if (s.situationChoice == SituationChoice.AUTO) {
            item {
                val detected = s.detectedMode
                BodyText(
                    when {
                        detected != null -> stringResource(R.string.mode_auto_detected, stringResource(detected.labelRes()))
                        // Licence gate: no scene model in this build — Street, plus the Concert rule.
                        !BuildConfig.SCENE_MODEL_ENABLED -> stringResource(R.string.mode_auto_unavailable)
                        else -> stringResource(R.string.mode_auto_listening, stringResource(s.mode.labelRes()))
                    },
                    color = VColor.TextSecondary,
                )
            }
        }
        item { BodyText(stringResource(DsR.string.situation_rule)) }
    }
}

/** "Street", or "Auto · Street" when Auto chose it. */
@Composable
fun modeValue(choice: SituationChoice, mode: SituationMode): String =
    if (choice == SituationChoice.AUTO) stringResource(R.string.mode_auto_value, stringResource(mode.labelRes()))
    else stringResource(mode.labelRes())
