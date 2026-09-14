package com.example.voxara.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.core.dose.DOSE_THRESHOLD_DBA
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.core.scene.Scene
import com.example.voxara.core.scene.environmentLabel
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.gauge.NeonReactiveGauge
import com.example.voxara.ui.gauge.levelColor
import com.example.voxara.ui.theme.LocalVoxTypography
import com.example.voxara.ui.theme.Vox
import kotlin.math.roundToInt

/**
 * THE LIVE GAUGE. One number owns the centre, the ring carries state in the periphery.
 * Colour never carries information alone — level, label and haptic say it too.
 */
@Composable
fun LiveScreen(
    state: ExposureState,
    ambient: Boolean,
    modifier: Modifier = Modifier,
) {
    val type = LocalVoxTypography.current
    val dba = state.dba.toFloat()
    val tint = levelColor(dba)
    val scene = if (state.scene == Scene.UNKNOWN) environmentLabel(state.dba) else state.scene.label

    val spoken = "${state.dba.roundToInt()} decibels, ${state.risk.label.lowercase()}, " +
        "${state.dosePercent.roundToInt()} percent of today's dose used"

    VoxScreen(
        modifier = modifier.semantics { contentDescription = spoken },
        behind = {
            NeonReactiveGauge(
                dba = dba,
                dosePercent = state.dosePercent.toFloat(),
                ambient = ambient,
                modifier = Modifier.fillMaxSize(),
            )
        },
    ) {
        ColumnCenter {
            Meta(scene, color = if (ambient) Vox.Ink3 else Vox.Ink2, small = true)

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.dba.roundToInt().toString(),
                    color = if (ambient) Vox.Ink1 else tint,
                    style = type.hero,
                )
                Box(Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                    Text("dBA", color = Vox.Ink3, style = type.metaSmall)
                }
            }

            Hairline()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("DOSE", color = Vox.Ink2, style = type.metaSmall)
                Text(
                    text = "${state.dosePercent.roundToInt()}%",
                    color = if (ambient) Vox.Ink1 else tint,
                    style = type.numeral,
                )
            }

            Meta(
                text = if (state.dba < DOSE_THRESHOLD_DBA) "NO DOSE ACCRUING"
                else formatHeadroom(state.headroomMinutes) + " LEFT",
                color = Vox.Ink3,
                small = true,
                modifier = Modifier.padding(top = 2.dp),
            )

            AnimatedVisibility(
                visible = state.simulated && !ambient,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Meta("BENCH SOURCE", color = Vox.Signal, small = true)
            }
        }
    }
}

/**
 * BREACH ALERT — fires on dose crossing 100%. Haptic first, screen second.
 */
@Composable
fun BreachScreen(
    state: ExposureState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalVoxTypography.current
    VoxScreen(
        modifier = modifier.semantics {
            contentDescription = "Daily limit reached. Protect your ears or leave."
        },
        behind = {
            NeonReactiveGauge(
                dba = state.dba.toFloat().coerceAtLeast(106f),
                dosePercent = 100f,
                modifier = Modifier.fillMaxSize(),
            )
        },
    ) {
        ColumnCenter {
            Meta("DAILY LIMIT REACHED", color = Vox.Critical, small = true)
            Text(
                text = state.dba.roundToInt().toString(),
                color = Vox.Critical,
                style = type.hero,
            )
            Meta(
                "dBA · ${state.dosePercent.roundToInt()}% DOSE",
                color = Vox.Ink1,
                small = true,
            )
            Box(Modifier.padding(top = 10.dp)) {
                Capsule(
                    text = "PROTECT OR LEAVE",
                    tint = Vox.Critical,
                    onClick = onDismiss,
                )
            }
        }
    }
}
