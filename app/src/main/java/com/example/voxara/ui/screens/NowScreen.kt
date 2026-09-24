package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.dose.DOSE_THRESHOLD_DBA
import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.data.AppMode
import com.example.voxara.text.sceneOrBandLabelRes
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.ZoneBadge
import com.example.voxara.ui.design.ZoneGauge
import com.example.voxara.ui.design.zoneWord
import kotlin.math.roundToInt

/**
 * NOW — the room around you. One hero element: the zone gauge with the level inside it. The zone
 * is also said in words with its own icon, so it never depends on colour.
 */
@Composable
fun NowScreen(env: UiEnv, actions: VoxaraActions) {
    val s = env.state
    val zone = RiskZone.of(s.dba)
    val word = zoneWord(zone)
    val spoken = stringResource(R.string.cd_now, s.dba.roundToInt(), word, s.dosePercent.roundToInt())
    val concert = s.mode == AppMode.CONCERT

    VoxList {
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
                val side = maxWidth * 0.92f
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                        ZoneGauge(dba = s.dba.toFloat().takeIf { s.dba > 0.0 }, modifier = Modifier.fillMaxSize())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (s.dba > 0.0) s.dba.roundToInt().toString() else "—",
                                style = VType.Hero,
                                color = VColor.Text,
                            )
                            Text(stringResource(R.string.unit_dba), style = VType.Caption, color = VColor.TextSecondary)
                        }
                    }
                }
            }
        }
        item { ZoneBadge(zone, label = word) }
        item {
            SectionLabel(stringResource(sceneOrBandLabelRes(s.scene, s.dba)))
        }
        item {
            BodyText(
                if (s.dba < DOSE_THRESHOLD_DBA) stringResource(R.string.now_not_accruing, s.dosePercent.roundToInt())
                else stringResource(R.string.now_accruing, formatHeadroom(s.headroomMinutes), s.dosePercent.roundToInt()),
                color = VColor.Text,
            )
        }
        if (s.simulated) item { SectionLabel(stringResource(R.string.bench_source), color = VColor.BrandStart) }
        if (!s.calibrated && !s.simulated) {
            item { SectionLabel(stringResource(R.string.uncalibrated_badge), color = VColor.TextSecondary) }
        }
        item {
            PillButton(
                label = stringResource(if (concert) R.string.concert_stop else R.string.concert_start),
                onClick = actions.onConcertToggle,
                icon = if (concert) VoxIcons.Pause else VoxIcons.Play,
                style = if (concert) PillStyle.PRIMARY else PillStyle.TONAL,
            )
        }
        item {
            BodyText(stringResource(if (concert) R.string.concert_on_note else R.string.concert_off_note), color = VColor.TextSecondary)
        }
    }
}
