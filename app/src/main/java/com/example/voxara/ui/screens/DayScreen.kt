package com.example.voxara.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.dose.energyAverage
import com.example.voxara.core.format.formatTwa
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.gauge.DayPolarClock
import com.example.voxara.ui.gauge.levelColor
import com.example.voxara.ui.theme.LocalVoxTypography
import com.example.voxara.ui.theme.Vox
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * DAY LEDGER — 24 hourly spikes on a polar clock. Loud hours glow, quiet hours stay hairline.
 * The rotary crown scrubs back through the week.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DayScreen(
    state: ExposureState,
    ambient: Boolean,
    modifier: Modifier = Modifier,
) {
    val type = LocalVoxTypography.current
    val history = remember(state.weekProfiles, state.todayProfile) {
        listOf(state.todayProfile) + state.weekProfiles.reversed()
    }
    var dayBack by remember { mutableIntStateOf(0) }
    var accumulated by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }

    val index = dayBack.coerceIn(0, history.lastIndex)
    val profile = history.getOrElse(index) { List(24) { 0f } }

    // The day's dose is re-derived from the hourly ledger, never guessed.
    val doseToday = remember(profile) { dosePercentOf(profile) }
    val twa = remember(doseToday) {
        if (doseToday <= 0.0) null else 10.0 * log10(doseToday / 100.0) + 85.0
    }
    val dayLabel = when (index) {
        0 -> stringResource(R.string.day_today)
        1 -> stringResource(R.string.day_yesterday)
        else -> stringResource(R.string.day_n_ago, index)
    }
    val spoken = stringResource(
        R.string.cd_day, dayLabel, twa?.roundToInt() ?: 0, doseToday.roundToInt(),
    )

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    VoxScreen(
        modifier = modifier
            .semantics {
                contentDescription = spoken
            }
            .onRotaryScrollEvent { event ->
                accumulated += event.verticalScrollPixels
                while (abs(accumulated) >= 60f) {
                    val step = if (accumulated > 0) 1 else -1
                    dayBack = (dayBack + step).coerceIn(0, history.lastIndex)
                    accumulated -= 60f * step
                }
                true
            }
            .focusRequester(focus)
            .focusable(),
        behind = {
            DayPolarClock(
                profile = profile,
                ambient = ambient,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        },
    ) {
        ColumnCenter {
            Meta(dayLabel, color = Vox.Ink3, small = true)
            Meta(stringResource(R.string.twa_8h), color = Vox.Ink3, small = true)
            if (twa == null) {
                // Nothing accrued: no number to show, and a placeholder glyph would read as one.
                Meta(stringResource(R.string.no_dose_logged), color = Vox.Ink2)
            } else {
                Text(
                    text = twa.roundToInt().toString(),
                    color = if (ambient) Vox.Ink1 else levelColor(twa.toFloat()),
                    style = type.display,
                )
            }
            Meta(
                stringResource(R.string.dose_percent, doseToday.roundToInt()),
                color = Vox.Ink2,
                small = true,
            )
            if (index == 0) {
                Box(Modifier.padding(top = 6.dp)) {
                    Meta(
                        stringResource(
                            R.string.niosh_twa,
                            formatTwa(state.twaDba) ?: stringResource(R.string.value_none),
                        ),
                        color = Vox.Ink3,
                        small = true,
                    )
                }
            }
        }
    }
}

/**
 * Dose from an hourly ledger: each hour is one C/T term at that hour's energy-average level.
 * Below 80 dBA nothing accrues.
 */
private fun dosePercentOf(profile: List<Float>): Double {
    var d = 0.0
    for (v in profile) {
        val l = v.toDouble()
        if (l >= 80.0) d += 100.0 * 3600.0 / (8.0 * 3600.0 / 2.0.pow((l - 85.0) / 3.0))
    }
    return d
}

/** Energy-average of a day, used by the week scrub summary. */
internal fun dayLeq(profile: List<Float>): Double =
    energyAverage(profile.filter { it > 0f }.map { it.toDouble() }.toDoubleArray())
