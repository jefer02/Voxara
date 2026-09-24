package com.example.voxara.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.CalibrationRules
import com.example.voxara.core.calibration.CaptureCheck
import com.example.voxara.core.calibration.Consistency
import com.example.voxara.core.calibration.ReferenceCheck
import com.example.voxara.core.calibration.ReferenceType
import com.example.voxara.core.calibration.REFERENCE_DB
import com.example.voxara.core.calibration.calibrationOffset
import com.example.voxara.core.calibration.checkCapture
import com.example.voxara.core.calibration.checkReference
import com.example.voxara.core.calibration.compareOffsets
import com.example.voxara.core.calibration.snapReference
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.ExposureState
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.HeroStatus
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VSpace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** The probe needs a moment after switching path before its levels settle. */
private const val SETTLE_MS = 1_500L

private enum class Step { INTRO, MEASURING, REFERENCE, RESULT, COMPARE, SAVED }

/** One finished measurement. */
private data class Measurement(val leqDbfsA: Double, val spreadDb: Double, val referenceDba: Double, val offsetDb: Double)

/**
 * GUIDED CALIBRATION (Settings -> Calibration).
 *
 * A couple of minutes with any reference reading: the watch measures a steady sound for ~10 s,
 * the wearer dials in what the reference reads (crown, 0.5 dB steps), and the offset for the
 * active capture path is stored with the date, the device model and the reference type.
 * Readings taken during calibration never reach the dose ledger or the alerts.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CalibrationScreen(
    state: ExposureState,
    onEnsureMonitoring: () -> Unit,
    onProbe: (com.example.voxara.core.calibration.MicSource?) -> Unit,
    onSave: (CalibrationRecord) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progressCd = stringResource(R.string.cal_progress_cd)
    val source = state.activeSource
    val existing = state.calibrationFor(source)

    var step by remember { mutableStateOf(Step.INTRO) }
    var referenceType by remember { mutableStateOf(ReferenceType.SOUND_LEVEL_METER) }
    var progress by remember { mutableFloatStateOf(0f) }
    var liveDbfs by remember { mutableStateOf<Double?>(null) }
    var captureProblem by remember { mutableStateOf<Int?>(null) }
    var captured by remember { mutableStateOf<CaptureCheck.Steady?>(null) }
    var reference by remember { mutableDoubleStateOf(70.0) }
    var referenceProblem by remember { mutableStateOf<Int?>(null) }
    var first by remember { mutableStateOf<Measurement?>(null) }
    var second by remember { mutableStateOf<Measurement?>(null) }
    var comparison by remember { mutableStateOf<Consistency?>(null) }
    var savedAt by remember { mutableStateOf(0L) }

    // The probe must never outlive the screen: it holds the ledger off.
    DisposableEffect(Unit) { onDispose { onProbe(null) } }

    fun measure() {
        captureProblem = null
        referenceProblem = null
        step = Step.MEASURING
        progress = 0f
        liveDbfs = null
        onEnsureMonitoring()
        onProbe(source)
        scope.launch {
            val levels = ArrayList<Double>()
            val started = System.currentTimeMillis()
            val totalMs = SETTLE_MS + CalibrationRules.MEASURE_SECONDS * 1000L
            // Collect until the window is full; the timeout covers a mic that never delivers.
            withTimeoutOrNull(totalMs + 3_000L) {
                ExposureRepository.probeReadings.first { r ->
                    val t = r.atMs - started
                    if (r.source == source && t >= SETTLE_MS) {
                        levels += r.dbfsA
                        liveDbfs = r.dbfsA
                    }
                    progress = (t.toFloat() / totalMs).coerceIn(0f, 1f)
                    t >= totalMs
                }
            }
            onProbe(null)
            when (val c = checkCapture(levels)) {
                is CaptureCheck.Steady -> {
                    captured = c
                    // Start the dial near what the watch currently thinks, inside the safe band.
                    reference = snapReference(
                        (c.leqDbfsA + REFERENCE_DB + state.offsetFor(source))
                            .coerceIn(CalibrationRules.MIN_REFERENCE_DBA, CalibrationRules.SAFE_REFERENCE_DBA)
                    )
                    step = Step.REFERENCE
                }
                is CaptureCheck.Unsteady -> {
                    captureProblem = R.string.cal_unsteady; step = Step.INTRO
                }
                is CaptureCheck.NotEnoughData -> {
                    captureProblem = R.string.cal_no_data; step = Step.INTRO
                }
            }
        }
    }

    fun confirmReference() {
        val c = captured ?: return
        when (checkReference(reference)) {
            ReferenceCheck.TooQuiet -> { referenceProblem = R.string.cal_too_quiet; return }
            ReferenceCheck.TooLoud -> { referenceProblem = R.string.cal_too_loud; return }
            else -> Unit
        }
        val offset = calibrationOffset(reference, c.leqDbfsA)
        if (offset == null) { referenceProblem = R.string.cal_out_of_range; return }
        val m = Measurement(c.leqDbfsA, c.spreadDb, reference, offset)
        if (first == null) {
            first = m
            step = Step.RESULT
        } else {
            second = m
            comparison = compareOffsets(first!!.offsetDb, m.offsetDb)
            step = Step.COMPARE
        }
    }

    fun save(offset: Double, secondOffset: Double?) {
        val now = System.currentTimeMillis()
        onSave(
            CalibrationRecord(
                source = source,
                offsetDb = offset,
                calibratedAtMs = now,
                deviceModel = state.device?.deviceKey.orEmpty(),
                referenceType = referenceType,
                secondOffsetDb = secondOffset,
            )
        )
        savedAt = now
        step = Step.SAVED
    }

    val listState = rememberTransformingLazyColumnState()
    val dialFocus = remember { FocusRequester() }
    var crownAccum by remember { mutableFloatStateOf(0f) }

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            // Side insets keep text inside the round display (about 6% of the width).
            modifier = Modifier.fillMaxSize().padding(horizontal = VSpace.roundInset),
        ) {
            item { HeroStatus(stringResource(R.string.cal_title)) }
            item {
                SectionLabel(
                    text = if (existing != null) stringResource(R.string.cal_status_calibrated, formatDate(existing.calibratedAtMs))
                    else stringResource(R.string.cal_status_uncalibrated),
                    color = if (existing != null) VColor.zone(RiskZone.OK) else VColor.zone(RiskZone.MODERATE),
                )
            }

            when (step) {
                Step.INTRO -> {
                    captureProblem?.let { item { BodyText(stringResource(it), color = VColor.zone(RiskZone.MODERATE)) } }
                    item { BodyText(stringResource(R.string.cal_step_1), color = VColor.Text, maxLines = 6) }
                    item { BodyText(stringResource(R.string.cal_step_2), color = VColor.Text, maxLines = 6) }
                    item { BodyText(stringResource(R.string.cal_step_3), color = VColor.Text, maxLines = 6) }
                    item { SectionLabel(stringResource(R.string.cal_reference_type), color = VColor.TextSecondary) }
                    ReferenceType.entries.forEach { t ->
                        item {
                            ChoiceButton(
                                label = stringResource(
                                    if (t == ReferenceType.SOUND_LEVEL_METER) R.string.cal_ref_meter
                                    else R.string.cal_ref_phone
                                ),
                                selected = referenceType == t,
                                onClick = { referenceType = t },
                            )
                        }
                    }
                    item {
                        SectionLabel(
                            stringResource(R.string.cal_source_line, source.name.lowercase().replace('_', ' ')),
                            color = VColor.TextSecondary,
                        )
                    }
                    item {
                        Button(
                            onClick = { measure() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.cal_start)) }
                    }
                    item {
                        FilledTonalButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }

                Step.MEASURING -> {
                    item {
                        Box(
                            Modifier.size(96.dp).semantics {
                                contentDescription = progressCd
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize())
                            Text(
                                text = liveDbfs?.let { "%.1f".format(it) } ?: "…",
                                style = VType.Numeral,
                                color = VColor.Text,
                            )
                        }
                    }
                    item { SectionLabel(stringResource(R.string.cal_measuring), color = VColor.TextSecondary) }
                    item { BodyText(stringResource(R.string.cal_hold_still), color = VColor.TextSecondary) }
                }

                Step.REFERENCE -> {
                    val c = captured
                    item {
                        SectionLabel(
                            stringResource(
                                R.string.cal_measured_line,
                                c?.leqDbfsA ?: 0.0,
                                state.offsetFor(source) - state.calibrationOffsetDb,
                            ),
                            color = VColor.TextSecondary,
                        )
                    }
                    item { SectionLabel(stringResource(R.string.cal_enter_reference), color = VColor.Text) }
                    item {
                        // The crown moves the reference in 0.5 dB steps; -/+ do the same.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onRotaryScrollEvent { e ->
                                    crownAccum += e.verticalScrollPixels
                                    while (abs(crownAccum) >= 24f) {
                                        val dir = if (crownAccum > 0) 1 else -1
                                        reference = snapReference(reference + dir * CalibrationRules.REFERENCE_STEP_DB)
                                        crownAccum -= 24f * dir
                                    }
                                    true
                                }
                                .focusRequester(dialFocus)
                                .focusable(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StepButton("−") { reference = snapReference(reference - CalibrationRules.REFERENCE_STEP_DB) }
                            Text(
                                text = stringResource(R.string.cal_reference_value, reference),
                                style = VType.Display,
                                color = VColor.Text,
                            )
                            StepButton("+") { reference = snapReference(reference + CalibrationRules.REFERENCE_STEP_DB) }
                        }
                        LaunchedEffect(Unit) { runCatching { dialFocus.requestFocus() } }
                    }
                    if (checkReference(reference) == ReferenceCheck.LouderThanAdvised) {
                        item { BodyText(stringResource(R.string.cal_louder_than_advised), color = VColor.zone(RiskZone.MODERATE)) }
                    }
                    referenceProblem?.let { item { BodyText(stringResource(it), color = VColor.zone(RiskZone.DANGEROUS)) } }
                    item {
                        Button(onClick = { confirmReference() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cal_confirm))
                        }
                    }
                    if (referenceProblem == R.string.cal_too_quiet || referenceProblem == R.string.cal_too_loud) {
                        item {
                            FilledTonalButton(onClick = { measure() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cal_measure_again))
                            }
                        }
                    }
                }

                Step.RESULT -> {
                    val m = first!!
                    item {
                        Text(
                            text = stringResource(R.string.cal_offset_value, m.offsetDb),
                            style = VType.Display,
                            color = VColor.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                    item {
                        SectionLabel(
                            stringResource(R.string.cal_offset_was, source.provisionalOffsetDb),
                            color = VColor.TextSecondary,
                        )
                    }
                    item { BodyText(stringResource(R.string.cal_second_hint), color = VColor.TextSecondary, maxLines = 5) }
                    item {
                        Button(onClick = { save(m.offsetDb, null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cal_save))
                        }
                    }
                    item {
                        FilledTonalButton(onClick = { measure() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cal_second))
                        }
                    }
                }

                Step.COMPARE -> {
                    val a = first!!
                    val b = second!!
                    val levelGap = abs(a.referenceDba - b.referenceDba)
                    item {
                        SectionLabel(
                            stringResource(R.string.cal_two_offsets, a.offsetDb, b.offsetDb),
                            color = VColor.Text,
                        )
                    }
                    if (levelGap < 6.0) {
                        item { BodyText(stringResource(R.string.cal_levels_close), color = VColor.TextSecondary) }
                    }
                    when (val cmp = comparison) {
                        is Consistency.Consistent -> {
                            item { BodyText(stringResource(R.string.cal_consistent, cmp.differenceDb), color = VColor.zone(RiskZone.OK)) }
                            item {
                                Button(onClick = { save(cmp.offsetDb, b.offsetDb) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.cal_save))
                                }
                            }
                        }
                        is Consistency.Inconsistent -> {
                            item { BodyText(stringResource(R.string.cal_inconsistent, cmp.differenceDb), color = VColor.zone(RiskZone.DANGEROUS), maxLines = 6) }
                            item {
                                FilledTonalButton(onClick = { save(cmp.averageDb, b.offsetDb) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.cal_save_average))
                                }
                            }
                            item {
                                Button(
                                    onClick = { first = null; second = null; comparison = null; step = Step.INTRO },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.cal_start_over)) }
                            }
                        }
                        null -> Unit
                    }
                }

                Step.SAVED -> {
                    item {
                        BodyText(
                            stringResource(R.string.cal_saved, formatDate(savedAt)),
                            color = VColor.zone(RiskZone.OK),
                            maxLines = 4,
                        )
                    }
                    item { BodyText(stringResource(R.string.cal_saved_note), color = VColor.TextSecondary, maxLines = 6) }
                    item {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 2) }
    } else {
        FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 2) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, style = VType.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatDate(ms: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
