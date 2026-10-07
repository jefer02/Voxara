package com.example.voxara.ai

import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.DayPart
import com.example.voxara.core.ai.SnapshotBuilder
import com.example.voxara.core.ai.SnapshotInputs
import com.example.voxara.data.ExposureState

/**
 * The watch's side of the privacy gate: its live state mapped onto the shared
 * [SnapshotBuilder], the same builder the phone companion uses.
 */
object CoachSnapshots {

    fun build(state: ExposureState, locale: String): AggregateSnapshot = SnapshotBuilder.build(
        SnapshotInputs(
            dosePercent = state.dosePercent,
            weeklyFraction = state.weeklyFraction,
            headphoneWeeklyFraction = state.headphoneWeeklyFraction,
            dba = state.dba,
            simulated = state.simulated,
            sensitiveListener = state.sensitiveListener,
            days = state.days,
            headphoneDays = state.headphoneDays,
            headphoneCategory = state.headphoneCategory?.name,
            calibrated = state.calibrated,
            situation = state.mode,
        ),
        locale,
    )

    fun partOf(hour: Int): DayPart = SnapshotBuilder.partOf(hour)
}
