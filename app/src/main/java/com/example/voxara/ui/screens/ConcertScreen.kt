package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.core.format.formatCountdown
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.gauge.ConcertSpectrum
import com.example.voxara.ui.gauge.levelColor
import com.example.voxara.ui.theme.LocalVoxTypography
import com.example.voxara.ui.theme.Vox
import kotlin.math.roundToInt

/**
 * CONCERT MODE — true-black field, lit pixels only. The user trades battery for real-time
 * truth and expects to be interrupted. Countdown of minutes left, not dose percent.
 */
@Composable
fun ConcertScreen(
    state: ExposureState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalVoxTypography.current
    val active = state.mode == AppMode.CONCERT
    val dba = state.dba.toFloat()
    val tint = levelColor(dba)

    VoxScreen(
        modifier = modifier.semantics {
            contentDescription = if (active)
                "Concert mode. ${state.dba.roundToInt()} decibels, " +
                    "${formatCountdown(state.headroomSeconds)} left."
            else "Concert mode off"
        },
        background = Color.Black,
        behind = {
            if (active) {
                ConcertSpectrum(
                    dba = dba,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        },
    ) {
        ColumnCenter {
            if (active) {
                Text(
                    text = state.dba.roundToInt().toString(),
                    color = tint,
                    style = type.display,
                )
                Meta(
                    // Concert mode counts down in minutes, not dose percent — and below the
                    // 80 dBA threshold there is nothing to count down.
                    text = if (state.headroomSeconds.isInfinite()) "NO DOSE ACCRUING"
                    else "${formatCountdown(state.headroomSeconds)} LEFT",
                    color = tint,
                    small = true,
                )
                Box(Modifier.padding(top = 12.dp)) {
                    Capsule("STOP", Vox.Ink3, onClick = onToggle)
                }
            } else {
                Meta("CONCERT / CLUB", color = Vox.Incandescent, small = true)
                Body(
                    "Continuous sampling, capped at 6 hours. Auto-drops to Urban after 20 quiet minutes.",
                    color = Vox.Ink2,
                    maxLines = 4,
                )
                Box(Modifier.padding(top = 12.dp)) {
                    Capsule("START", Vox.Incandescent, onClick = onToggle)
                }
            }
        }
    }
}
