package com.example.voxara.ai

import android.content.res.Resources
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.STREAK_WORTH_SAYING
import com.example.voxara.shared.R

/**
 * The deterministic templates Tono falls back to (and uses whenever cloud AI is off, offline,
 * over budget or invalid). Every figure comes from the snapshot.
 *
 * Shared by the watch and the phone, so both give the same built-in answer to the same data.
 * [res] must already be localized to the language the wearer chose.
 */
class OfflineCoach(private val res: Resources) : BuiltInCoach {

    override fun reply(req: CoachRequest): CoachReply {
        val s = req.snapshot
        val week = maxOf(s.weeklyAmbientPercent, s.weeklyHeadphonePercent)
        val status = when {
            s.dailyDosePercent >= 100 -> res.getString(R.string.tono_status_limit)
            (s.currentDba ?: 0) >= 85 -> res.getString(R.string.tono_status_loud)
            week >= 80 -> res.getString(R.string.tono_status_week)
            s.dailyDosePercent >= 50 -> res.getString(R.string.tono_status_busy)
            s.listeningMinutesToday > 0 && s.headphoneCategory != null -> res.getString(R.string.tono_status_listening)
            // Moderate around you (70–84 dBA): not a "calm day", whatever the dose says.
            (s.currentDba ?: 0) >= 70 -> res.getString(R.string.tono_status_moderate)
            else -> res.getString(R.string.tono_status_calm)
        }
        val insight = when (req.task) {
            // The streak only when it is true and worth saying: measured days in a row within the limit, three or more.
            CoachTask.WEEKLY_SUMMARY ->
                if (s.streakDays >= STREAK_WORTH_SAYING) res.getString(R.string.tono_insight_week, week, s.streakDays)
                else res.getString(R.string.tono_insight_week_plain, week)
            else -> res.getString(R.string.tono_insight_today, s.dailyDosePercent, week)
        }
        val why = when (req.task) {
            CoachTask.ALERT_WHY -> res.getString(R.string.tono_why_alert)
            else -> s.daysToWeeklyLimit?.let { res.getString(R.string.tono_why_projection, it) } ?: ""
        }
        val suggestion = when {
            (s.currentDba ?: 0) >= 85 -> res.getString(R.string.tono_suggest_quieter)
            s.headphoneCategory != null && s.weeklyHeadphonePercent >= 50 -> res.getString(R.string.tono_suggest_volume)
            week >= 80 || s.dailyDosePercent >= 80 -> res.getString(R.string.tono_suggest_rest)
            else -> res.getString(R.string.tono_suggest_keep)
        }
        return CoachReply(
            status = status,
            insight = insight,
            why = why,
            suggestion = suggestion,
            chips = listOf(
                res.getString(R.string.tono_chip_week),
                res.getString(R.string.tono_chip_why_estimated),
                res.getString(R.string.tono_chip_tips),
            ),
        )
    }

    /** The fixed answer to anything that sounds like a symptom. Never sent to a model. */
    override fun symptomReply(): CoachReply = CoachReply(
        status = res.getString(R.string.tono_symptom_status),
        insight = res.getString(R.string.tono_symptom_insight),
        why = "",
        suggestion = res.getString(R.string.tono_symptom_suggestion),
        chips = emptyList(),
    )
}
