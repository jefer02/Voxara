package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.example.voxara.R
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.weeklyTimeLeftHours
import com.example.voxara.core.risk.RiskZone
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.VoxaraActions
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.EmptyState
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.ProgressRing
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VType
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.VoxList
import com.example.voxara.ui.design.WeekBars
import com.example.voxara.ui.design.ZoneBadge
import kotlin.math.roundToInt

/**
 * HEADPHONES — listening on THIS watch (and the phone, when the companion syncs). Every number
 * is an ESTIMATE from the volume setting and an assumed output level, labelled with its
 * confidence; the microphone is never used for it.
 */
@Composable
fun HeadphonesScreen(env: UiEnv, actions: VoxaraActions) {
    val s = env.state
    var showWhy by remember { mutableStateOf(false) }
    val budget = if (s.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
    val estimate = s.headphoneEstimate?.takeIf { s.headphoneListening }
    val weeklyPercent = (s.headphoneWeeklyFraction * 100).roundToInt()
    val left = weeklyTimeLeftHours(s.headphoneWeeklyPa2h, budget, estimate?.dba)
    val weekZone = RiskZone.ofPercent(weeklyPercent.toDouble())

    VoxList {
        item { SectionLabel(stringResource(R.string.hp_title)) }
        item {
            val status = when {
                s.headphoneCategory == null -> stringResource(R.string.hp_not_connected)
                estimate != null -> stringResource(R.string.hp_listening, stringResource(categoryRes(s.headphoneCategory)))
                else -> stringResource(R.string.hp_connected, stringResource(categoryRes(s.headphoneCategory)))
            }
            BodyText(status, color = VColor.Text, maxLines = 3)
        }
        if (estimate != null) {
            item {
                val zone = RiskZone.of(estimate.dba)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("≈ ${estimate.dba.roundToInt()}", style = VType.Display, color = VColor.Text)
                    Text(stringResource(R.string.unit_dba), style = VType.Caption, color = VColor.TextSecondary)
                    ZoneBadge(zone)
                }
            }
            item {
                SectionLabel(
                    stringResource(
                        if (estimate.confidence == Confidence.LOW) R.string.hp_estimated_low else R.string.hp_estimated_very_low,
                        estimate.lowDba.roundToInt(), estimate.highDba.roundToInt(),
                    ),
                )
            }
        } else if (s.headphoneCategory == null) {
            item { EmptyState(VoxIcons.Headphones, stringResource(R.string.hp_empty)) }
        }
        item {
            val cd = stringResource(R.string.cd_headphones_week, weeklyPercent)
            ProgressRing(
                s.headphoneWeeklyFraction.toFloat(),
                Modifier.size(104.dp).semantics(mergeDescendants = true) { contentDescription = cd },
                color = VColor.zone(weekZone),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$weeklyPercent%", style = VType.Numeral, color = VColor.Text)
                    Text(stringResource(R.string.hp_week_label), style = VType.Caption, color = VColor.TextSecondary)
                }
            }
        }
        left?.let {
            item {
                BodyText(
                    if (it <= 0.0) stringResource(R.string.hp_time_left_none)
                    else stringResource(R.string.hp_time_left, formatClock(it * 60.0)),
                    color = VColor.Text,
                )
            }
        }
        val days = s.headphoneDays.take(7)
        if (days.isNotEmpty()) {
            item {
                WeekBars(
                    values = days.reversed().map { (it.energyPa2h / budget * 3).toFloat() },
                    labels = List(days.size) { "" },
                    description = stringResource(R.string.cd_week_bars, (days.first().energyPa2h / budget * 100).roundToInt()),
                )
            }
            item { SectionLabel(stringResource(R.string.hp_week_history_note)) }
        }
        if (env.bluetoothNeeded && s.headphoneCategory != null) {
            item { PillButton(stringResource(R.string.hp_allow_bluetooth), onClick = actions.onRequestBluetooth, style = PillStyle.TONAL) }
            item { BodyText(stringResource(R.string.hp_bluetooth_note)) }
        }
        item {
            PillButton(
                stringResource(if (showWhy) R.string.hp_why_hide else R.string.hp_why),
                onClick = { showWhy = !showWhy }, icon = VoxIcons.Info, style = PillStyle.OUTLINED,
            )
        }
        if (showWhy) item { BodyText(stringResource(R.string.hp_why_body), color = VColor.Text, maxLines = 14) }
        item { MetricCard(VoxIcons.Info, stringResource(R.string.hp_phone_title), stringResource(R.string.hp_phone_value)) }
    }
}

fun categoryRes(c: HeadphoneCategory?): Int = when (c) {
    HeadphoneCategory.EARBUDS -> R.string.hp_cat_earbuds
    HeadphoneCategory.OVER_EAR -> R.string.hp_cat_over_ear
    HeadphoneCategory.WIRED -> R.string.hp_cat_wired
    HeadphoneCategory.UNKNOWN, null -> R.string.hp_cat_unknown
}
