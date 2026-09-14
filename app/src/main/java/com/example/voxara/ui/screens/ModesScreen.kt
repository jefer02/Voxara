package com.example.voxara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.data.AppLanguage
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureState
import com.example.voxara.data.Scenario
import com.example.voxara.text.blurbRes
import com.example.voxara.text.indexRes
import com.example.voxara.text.labelRes
import com.example.voxara.text.titleRes
import com.example.voxara.ui.theme.LocalVoxTypography
import com.example.voxara.ui.theme.Vox

/**
 * THREE MODES, ONE ENGINE — plus the two controls the dossier's Phase 0 depends on:
 * the per-model calibration offset, and the bench source for devices whose mic is unavailable.
 */
@Composable
fun ModesScreen(
    state: ExposureState,
    onMode: (AppMode) -> Unit,
    onMonitoring: (Boolean) -> Unit,
    onCalibration: (Double) -> Unit,
    onScenario: (Scenario) -> Unit,
    onResetDose: () -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalVoxTypography.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().background(Vox.Void),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 34.dp, bottom = 40.dp,
        ),
    ) {
        item { Meta(stringResource(R.string.modes_title), color = Vox.Ink3) }

        items(AppMode.entries.toList()) { mode ->
            ModeCard(
                mode = mode,
                selected = state.mode == mode,
                onClick = { onMode(mode) },
            )
        }

        item { Panel {
            StatRow(
                stringResource(R.string.monitoring),
                stringResource(if (state.monitoring) R.string.on else R.string.off),
                if (state.monitoring) Vox.Safe else Vox.Ink3,
            )
            Box(Modifier.padding(top = 8.dp)) {
                Capsule(
                    text = stringResource(
                        if (state.monitoring) R.string.action_stop else R.string.action_start,
                    ),
                    tint = if (state.monitoring) Vox.Ink3 else Vox.Safe,
                    onClick = { onMonitoring(!state.monitoring) },
                )
            }
        } }

        item { Panel {
            Meta(stringResource(R.string.calibration_offset), color = Vox.Ink3, small = true)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Capsule(
                    stringResource(R.string.decrease),
                    Vox.Ink3,
                    onClick = { onCalibration(state.calibrationOffsetDb - 0.5) },
                )
                Text(
                    text = stringResource(
                        R.string.calibration_value, state.calibrationOffsetDb,
                    ),
                    color = Vox.Ink1,
                    style = type.numeral,
                )
                Capsule(
                    stringResource(R.string.increase),
                    Vox.Ink3,
                    onClick = { onCalibration(state.calibrationOffsetDb + 0.5) },
                )
            }
            Text(
                text = stringResource(R.string.calibration_note),
                color = Vox.Ink3,
                style = type.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        } }

        if (state.simulated) {
            item { Panel {
                Meta(stringResource(R.string.bench_source), color = Vox.Signal, small = true)
                Text(
                    text = stringResource(R.string.bench_note),
                    color = Vox.Ink2,
                    style = type.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Scenario.entries.forEach { s ->
                        Capsule(
                            text = stringResource(
                                R.string.scenario_chip,
                                stringResource(s.labelRes()),
                                s.dba.toInt(),
                            ),
                            tint = Vox.Ink3,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onScenario(s) },
                        )
                    }
                }
            } }
        }

        item { Panel {
            StatRow(
                stringResource(R.string.daily_dose),
                stringResource(R.string.tile_dose_value, state.dosePercent.toInt()),
                Vox.Ink1,
            )
            StatRow(
                stringResource(R.string.who_week),
                stringResource(R.string.tile_dose_value, (state.weeklyFraction * 100).toInt()),
                Vox.Ink1,
            )
            StatRow(
                stringResource(R.string.peak),
                stringResource(R.string.unit_dbc_value, state.peakDbc.toInt()),
                Vox.Ink1,
            )
            Box(Modifier.padding(top = 8.dp)) {
                Capsule(stringResource(R.string.reset_dose), Vox.Critical, onClick = onResetDose)
            }
        } }

        // LANGUAGE - the one setting that changes every other word on every surface.
        item { Panel {
            Meta(stringResource(R.string.language), color = Vox.Ink3, small = true)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                AppLanguage.entries.forEach { option ->
                    Capsule(
                        text = stringResource(option.labelRes()),
                        tint = if (option == language) Vox.Safe else Vox.Ink3,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLanguage(option) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.language_note),
                color = Vox.Ink3,
                style = type.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } }

        item {
            Text(
                text = stringResource(R.string.disclaimer),
                color = Vox.Ink3,
                style = type.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Vox.Panel)
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(22.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun ModeCard(mode: AppMode, selected: Boolean, onClick: () -> Unit) {
    val type = LocalVoxTypography.current
    val tint = when (mode) {
        AppMode.CONCERT -> Vox.Incandescent
        AppMode.URBAN -> Vox.Safe
        AppMode.VOICE -> Vox.Signal
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) tint.copy(alpha = 0.08f) else Vox.Panel)
            .border(
                1.dp,
                if (selected) tint.copy(alpha = 0.45f) else Color(0x12FFFFFF),
                RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(mode.indexRes()), color = Vox.Ink3, style = type.metaSmall)
        Text(
            stringResource(mode.titleRes()),
            color = if (selected) tint else Vox.Ink1,
            style = type.title,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            stringResource(mode.blurbRes()),
            color = Vox.Ink2,
            style = type.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Box(Modifier.height(2.dp))
    }
}
