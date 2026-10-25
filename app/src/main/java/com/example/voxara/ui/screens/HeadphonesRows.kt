package com.example.voxara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import com.example.voxara.R
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.format.formatApproxDuration
import com.example.voxara.core.format.formatClock
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.weeklyTimeLeftHours
import com.example.voxara.ui.UiEnv
import com.example.voxara.ui.design.BodyText
import com.example.voxara.ui.design.EmptyState
import com.example.voxara.ui.design.GroupLabel
import com.example.voxara.ui.design.MetricCard
import com.example.voxara.ui.design.PillButton
import com.example.voxara.ui.design.PillStyle
import com.example.voxara.ui.design.SectionLabel
import com.example.voxara.ui.design.VColor
import com.example.voxara.ui.design.VoxIcons
import com.example.voxara.ui.design.WeekBars
import com.example.voxara.ui.design.row
import kotlin.math.roundToInt

/*
 * What the standalone Headphones page used to show, now below the fold of the two pages that own it:
 * the live estimate's details under Now in headphones mode (and under Dose / Week when the mode is off),
 * the week's listening history under Dose / Week. Every number is an ESTIMATE from the volume setting and
 * an assumed output level, labelled with its confidence; the microphone is never used for it.
 */

/**
 * The live listening rows: what is connected or playing, the estimate's range, the time left before the
 * weekly limit at this level, and why it is only an estimate (a button that opens the explanation).
 */
internal fun TransformingLazyColumnScope.headphonesLiveRows(env: UiEnv) {
    val s = env.state
    val budget = if (s.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
    val estimate = s.headphoneEstimate?.takeIf { s.headphoneListening }
    val left = weeklyTimeLeftHours(s.headphoneWeeklyPa2h, budget, estimate?.dba)
    row {
        val status = when {
            s.headphoneCategory == null -> stringResource(R.string.hp_not_connected)
            estimate != null -> stringResource(R.string.hp_listening, stringResource(categoryRes(s.headphoneCategory)))
            else -> stringResource(R.string.hp_connected, stringResource(categoryRes(s.headphoneCategory)))
        }
        FootLine(status, color = VColor.Text)
    }
    if (s.headphonePossibleSpeaker && s.headphoneCategory != null) row { FootLine(stringResource(R.string.hp_possible_speaker_short), color = VColor.TextSecondary) }
    // Listening minutes at the ears today, whichever ledger owns them (the room can own a loud stretch).
    if (s.listeningSecondsToday >= 60.0) {
        row { FootLine(stringResource(R.string.hp_listening_today, formatClock(s.listeningSecondsToday / 60.0)), color = VColor.Text) }
        val counted = s.headphoneDays.firstOrNull()?.measuredMinutes ?: 0.0
        if (s.listeningSecondsToday / 60.0 - counted > 1.0) row { BodyText(stringResource(R.string.hp_listening_room_note), color = VColor.TextSecondary) }
    }
    if (estimate != null) {
        row {
            SectionLabel(
                stringResource(
                    if (estimate.confidence == Confidence.LOW) R.string.hp_estimated_low else R.string.hp_estimated_very_low,
                    estimate.lowDba.roundToInt(), estimate.highDba.roundToInt(),
                ),
            )
        }
    }
    left?.let {
        row {
            BodyText(
                if (it <= 0.0) stringResource(R.string.hp_time_left_none) else stringResource(R.string.hp_time_left, formatApproxDuration(it * 60.0)),
                color = VColor.Text,
            )
        }
    }
    row {
        var showWhy by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                stringResource(if (showWhy) R.string.hp_why_hide else R.string.hp_why),
                onClick = { showWhy = !showWhy }, icon = VoxIcons.Info, style = PillStyle.OUTLINED,
            )
            if (showWhy) BodyText(stringResource(R.string.hp_why_body), color = VColor.Text)
        }
    }
}

/**
 * The headphones group of the Week view: its heading, the live rows when headphones mode is off (with the
 * mode on they are under Now), the last seven days of listening, the empty state when nothing is connected,
 * and the note that the phone's listening is counted too.
 */
internal fun TransformingLazyColumnScope.headphonesWeekRows(env: UiEnv, layerOn: Boolean) {
    val s = env.state
    val budget = if (s.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
    row { GroupLabel(stringResource(R.string.page_headphones)) }
    if (!layerOn) headphonesLiveRows(env)
    val days = s.headphoneDays.take(7)
    if (days.isNotEmpty()) {
        row {
            WeekBars(
                values = days.reversed().map { (it.energyPa2h / budget * 3).toFloat() },
                labels = List(days.size) { "" },
                description = stringResource(R.string.cd_week_bars, (days.first().energyPa2h / budget * 100).roundToInt()),
                color = VColor.Secondary,
            )
        }
        row { SectionLabel(stringResource(R.string.hp_week_history_note)) }
    }
    if (s.headphoneCategory == null) row { EmptyState(VoxIcons.Headphones, stringResource(R.string.hp_empty)) }
    row { MetricCard(VoxIcons.Info, stringResource(R.string.hp_phone_title), stringResource(R.string.hp_phone_value)) }
}

fun categoryRes(c: HeadphoneCategory?): Int = when (c) {
    HeadphoneCategory.EARBUDS -> R.string.hp_cat_earbuds
    HeadphoneCategory.OVER_EAR -> R.string.hp_cat_over_ear
    HeadphoneCategory.WIRED -> R.string.hp_cat_wired
    HeadphoneCategory.UNKNOWN, null -> R.string.hp_cat_unknown
}
