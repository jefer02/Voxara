package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.voxara.R
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.DoseArc
import com.example.voxara.ui.design.IconTile
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ScreenTitle
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.row

/**
 * WHAT THE RING SHOWS — the Dose ring explained in two short sentences (a tap on its number opens it): it fills
 * from 12 o'clock as the limit is used, and a full ring is 100 %. A small ring at a third, without a number, shows
 * the shape; nothing on it is data.
 */
@Composable
fun DoseHelpScreen(onDone: () -> Unit) {
    VoxList {
        row {
            Box(Modifier.size(64.dp).clearAndSetSemantics { }) {
                val tone = VColor.tone(RiskZone.OK)
                DoseArc(today = 1f / 3f, week = 1f / 3f, todayTone = tone, weekTone = tone, mix = { 0f })
            }
        }
        row { ScreenTitle(stringResource(R.string.dose_help_title)) }
        row { BodyText(stringResource(R.string.dose_help_body), color = VColor.Text) }
        row { PillButton(stringResource(R.string.alert_action_ack), onClick = onDone) }
    }
}

/**
 * NOT HEADPHONES? — the way out of headphones mode, opened from its chip on Now or the row under it. Detection
 * stays automatic; this only tells Voxara that the output in use is not headphones:
 *  - "Ignore for now": headphones mode leaves and ignores this output until it disconnects (a new connection is
 *    detected again);
 *  - "It's a speaker" (a Bluetooth device it can recognise): also remembered for later connections; Mode lists
 *    how many are remembered and forgets them.
 * The room is still measured and its alerts are never affected; headphone alerts stop only for this output.
 */
@Composable
fun HeadphonesExitScreen(env: UiEnv, actions: VoxaraActions, onDone: () -> Unit) {
    val s = env.state
    VoxList {
        row { IconTile(VoxIcons.Headphones, VColor.Secondary) }
        row { ScreenTitle(stringResource(R.string.hp_exit_title)) }
        row { BodyText(stringResource(R.string.hp_exit_body), color = VColor.Text) }
        row {
            PillButton(
                stringResource(R.string.hp_exit_ignore),
                onClick = { actions.onNotHeadphones(false); onDone() },
            )
        }
        if (s.headphoneCanRememberSpeaker) {
            row {
                PillButton(
                    stringResource(R.string.hp_exit_speaker),
                    onClick = { actions.onNotHeadphones(true); onDone() },
                    style = PillStyle.TONAL,
                )
            }
        }
        row { BodyText(stringResource(R.string.hp_exit_room_note)) }
    }
}
