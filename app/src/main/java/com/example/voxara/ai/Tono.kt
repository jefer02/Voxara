package com.example.voxara.ai

import android.content.Context
import com.example.voxara.BuildConfig
import com.example.voxara.R
import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.CoachBudget
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.SymptomGuard
import com.example.voxara.core.ai.cacheKey
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.data.ExposureState
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a reply came from — always shown to the wearer. */
enum class ReplySource { CLOUD_AI, OFFLINE, SAFETY }

data class CoachUi(
    val loading: Boolean = false,
    val reply: CoachReply? = null,
    val source: ReplySource? = null,
    /** Set when the cloud was tried and failed; the offline reply is shown instead. */
    val cloudFailed: Boolean = false,
)

/**
 * The deterministic templates Tono falls back to (and uses whenever cloud AI is off, offline,
 * over budget or invalid). Every figure comes from the snapshot.
 */
class OfflineCoach(private val context: Context) {

    fun reply(req: CoachRequest): CoachReply {
        val res = LocaleStore.localized(context)
        val s = req.snapshot
        val week = maxOf(s.weeklyAmbientPercent, s.weeklyHeadphonePercent)
        val status = when {
            s.dailyDosePercent >= 100 -> res.getString(R.string.tono_status_limit)
            (s.currentDba ?: 0) >= 85 -> res.getString(R.string.tono_status_loud)
            week >= 80 -> res.getString(R.string.tono_status_week)
            s.dailyDosePercent >= 50 -> res.getString(R.string.tono_status_busy)
            s.listeningMinutesToday > 0 && s.headphoneCategory != null -> res.getString(R.string.tono_status_listening)
            else -> res.getString(R.string.tono_status_calm)
        }
        val insight = when (req.task) {
            CoachTask.WEEKLY_SUMMARY -> res.getString(R.string.tono_insight_week, week, s.streakDays)
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
    fun symptomReply(): CoachReply {
        val res = LocaleStore.localized(context)
        return CoachReply(
            status = res.getString(R.string.tono_symptom_status),
            insight = res.getString(R.string.tono_symptom_insight),
            why = "",
            suggestion = res.getString(R.string.tono_symptom_suggestion),
            chips = emptyList(),
        )
    }
}

/**
 * TONO — the coach, as the app sees it. Decides nothing: it phrases what the deterministic engine
 * computed. Cloud AI only with explicit consent, a debug build key, budget left and a valid reply;
 * otherwise the offline templates answer, so the app always works.
 */
object Tono {

    private val _ui = MutableStateFlow(CoachUi())
    val ui: StateFlow<CoachUi> = _ui.asStateFlow()

    private val cache = object : LinkedHashMap<String, CoachReply>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoachReply>?) = size > 24
    }
    @Volatile private var budget = CoachBudget()

    /** Cloud client, or null when this build has no key (release builds never do). */
    private val cloud: CoachClient? by lazy {
        BuildConfig.AI_API_KEY.takeIf { it.isNotBlank() }?.let {
            OpenAiCompatibleCoachClient(BuildConfig.AI_BASE_URL, BuildConfig.AI_MODEL, it)
        }
    }

    /** True when this build could talk to the cloud at all (debug with a key). */
    val cloudAvailable: Boolean get() = cloud != null

    suspend fun ask(
        context: Context,
        state: ExposureState,
        task: CoachTask,
        question: String? = null,
        alertKind: String? = null,
        automatic: Boolean = false,
        client: CoachClient? = cloud,
    ): CoachUi {
        val offline = OfflineCoach(context.applicationContext)
        val locale = LocaleStore.resolve(context).language
        val snapshot: AggregateSnapshot = CoachSnapshots.build(state, locale)
        val request = CoachRequest(task, snapshot, question?.take(200), alertKind)

        // Symptoms: a fixed, safe answer. The question is never sent anywhere.
        if (task == CoachTask.ASK && SymptomGuard.detect(question)) {
            return publish(CoachUi(reply = offline.symptomReply(), source = ReplySource.SAFETY))
        }

        val consent = VoxaraStore(context).read().aiConsent
        val now = System.currentTimeMillis()
        val today = localEpochDay(now)
        if (consent && client != null && budget.canCall(today, now, automatic)) {
            val key = cacheKey(request, today)
            synchronized(cache) { cache[key] }?.let {
                return publish(CoachUi(reply = it, source = ReplySource.CLOUD_AI))
            }
            _ui.value = _ui.value.copy(loading = true)
            budget = budget.spend(today, now, automatic)
            when (val r = client.reply(request)) {
                is CoachResult.Success -> {
                    synchronized(cache) { cache[key] = r.reply }
                    return publish(CoachUi(reply = r.reply, source = ReplySource.CLOUD_AI))
                }
                is CoachResult.Failure -> return publish(
                    CoachUi(reply = offline.reply(request), source = ReplySource.OFFLINE, cloudFailed = true)
                )
            }
        }
        return publish(CoachUi(reply = offline.reply(request), source = ReplySource.OFFLINE))
    }

    private fun publish(ui: CoachUi): CoachUi {
        _ui.value = ui
        return ui
    }
}
