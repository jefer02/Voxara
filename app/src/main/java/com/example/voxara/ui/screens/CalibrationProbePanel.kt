package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.voxara.R
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.dose.energyAverage
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.ChoiceList
import com.example.voxara.ui.design.InfoRow
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.ValueStepper

/** Readings averaged before an offset is saved: about 2 s of the probe's 250 ms bursts. */
private const val AVERAGE_WINDOW = 8

/**
 * DEBUG CALIBRATION READOUT (debug builds only, in Settings).
 *
 * Raw A-weighted and unweighted dBFS per capture path, plus what the watch reports about itself
 * (manufacturer, model, API level, UNPROCESSED support, active source). The probe keeps the service
 * capturing continuously through that path and holds every reading OUT of the ledger and the
 * alerts; it switches off as soon as this panel leaves the screen.
 */
@Composable
fun CalibrationProbePanel(
    state: ExposureState,
    onProbe: (MicSource?) -> Unit,
    onSave: (source: MicSource, referenceDba: Double, dbfsA: Double) -> Boolean,
) {
    var probing by remember { mutableStateOf<MicSource?>(null) }
    var reference by remember { mutableDoubleStateOf(94.0) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val recent = remember { mutableStateListOf<Double>() }

    DisposableEffect(Unit) { onDispose { onProbe(null) } }

    // Rolling window of the probed source's raw A-weighted dBFS.
    LaunchedEffect(state.rawDbfsA, state.micSource, probing) {
        val raw = state.rawDbfsA
        if (probing == null || raw == null || state.micSource != probing) return@LaunchedEffect
        recent += raw
        while (recent.size > AVERAGE_WINDOW) recent.removeAt(0)
    }
    val averageDbfsA = if (recent.isEmpty()) null else energyAverage(recent.toDoubleArray())

    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.probe_title), color = VColor.BrandStart)
        ChoiceList(
            listOf(
                MicSource.UNPROCESSED to stringResource(R.string.probe_unprocessed),
                MicSource.VOICE_RECOGNITION to stringResource(R.string.probe_voice_recognition),
                null to stringResource(R.string.probe_off),
            ),
            selected = probing,
            onSelect = { option ->
                probing = option
                recent.clear()
                result = null
                onProbe(option)
            },
        )
        if (!state.monitoring) BodyText(stringResource(R.string.probe_needs_monitoring), color = VColor.zone(RiskZone.MODERATE))

        state.device?.let { d ->
            InfoRow(stringResource(R.string.probe_device), "${d.manufacturer} ${d.model}")
            InfoRow(stringResource(R.string.probe_api), "${d.sdkInt} (${d.release})")
            InfoRow(
                stringResource(R.string.probe_unprocessed_support),
                when (d.unprocessedSupported) { true -> "yes"; false -> "no"; null -> "?" },
            )
        }
        InfoRow(stringResource(R.string.probe_source), state.micSource?.name?.lowercase()?.replace('_', ' ') ?: "—")
        InfoRow(stringResource(R.string.probe_dbfs_a), fmt(state.rawDbfsA))
        InfoRow(stringResource(R.string.probe_dbfs_z), fmt(state.rawDbfsZ))
        InfoRow(stringResource(R.string.probe_avg_dbfs_a), fmt(averageDbfsA))
        state.micSource?.let { InfoRow(stringResource(R.string.probe_offset), fmt(state.offsetFor(it))) }
        InfoRow(stringResource(R.string.probe_reads), fmt(state.dba))

        ValueStepper(
            label = stringResource(R.string.probe_reference),
            value = fmt(reference),
            onDecrease = { reference -= 1.0 },
            onIncrease = { reference += 1.0 },
            decreaseDescription = stringResource(R.string.decrease_cd),
            increaseDescription = stringResource(R.string.increase_cd),
        )
        val source = probing
        PillButton(
            label = stringResource(R.string.probe_save),
            onClick = { if (source != null && averageDbfsA != null) result = onSave(source, reference, averageDbfsA) },
            enabled = source != null && averageDbfsA != null,
        )
        result?.let { ok ->
            BodyText(
                stringResource(if (ok) R.string.probe_saved else R.string.probe_out_of_range),
                color = if (ok) VColor.zone(RiskZone.OK) else VColor.zone(RiskZone.DANGEROUS),
            )
        }
    }
}

private fun fmt(v: Double?): String = if (v == null) "—" else "%.1f".format(v)
