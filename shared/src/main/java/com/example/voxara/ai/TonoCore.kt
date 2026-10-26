package com.example.voxara.ai

import com.example.voxara.core.ai.AggregateSnapshot
import com.example.voxara.core.ai.CoachBudget
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.SymptomGuard
import com.example.voxara.core.ai.cacheKey
import com.example.voxara.core.ledger.localEpochDay
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
    /** Why it failed, as a fixed code (an exception class, an HTTP status, a validator reason): never the key or the question. */
    val failure: String? = null,
)

/** The built-in answers ([OfflineCoach] in the apps, a fake in tests). */
interface BuiltInCoach {
    fun reply(req: CoachRequest): CoachReply
    fun symptomReply(): CoachReply
}

/**
 * TONO'S DECISIONS, shared by the watch and the phone so both behave the same:
 *  - a symptom question gets the fixed safe answer and is never sent anywhere;
 *  - the cloud is used only with explicit consent, a client (debug key) and budget left;
 *  - a cached cloud reply is served even when the rate limit says no (it costs nothing);
 *  - a failed or invalid cloud reply falls back to the built-in answer, flagged as such;
 *  - a rate-limited automatic refresh keeps the last cloud reply instead of replacing it.
 *
 * Budget and cache live in memory, per device.
 */
class TonoCore(
    /** Called with a fixed failure code (never the key or the question) when the cloud fails. */
    private val onCloudFailure: (String) -> Unit = {},
) {

    private val _ui = MutableStateFlow(CoachUi())
    val ui: StateFlow<CoachUi> = _ui.asStateFlow()

    private val cache = object : LinkedHashMap<String, CoachReply>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoachReply>?) = size > 24
    }
    @Volatile private var budget = CoachBudget()

    suspend fun ask(
        task: CoachTask,
        snapshot: AggregateSnapshot,
        question: String?,
        alertKind: String?,
        builtIn: BuiltInCoach,
        consent: Boolean,
        client: CoachClient?,
        automatic: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        today: Long = localEpochDay(nowMs),
    ): CoachUi {
        val request = CoachRequest(task, snapshot, question?.take(200), alertKind)

        // Symptoms: a fixed, safe answer. The question is never sent anywhere.
        if (task == CoachTask.ASK && SymptomGuard.detect(question)) {
            return publish(CoachUi(reply = builtIn.symptomReply(), source = ReplySource.SAFETY))
        }

        if (consent && client != null) {
            val key = cacheKey(request, today)
            synchronized(cache) { cache[key] }?.let {
                return publish(CoachUi(reply = it, source = ReplySource.CLOUD_AI))
            }
            if (budget.canCall(today, nowMs, automatic)) {
                _ui.value = _ui.value.copy(loading = true)
                budget = budget.spend(today, nowMs, automatic)
                when (val r = client.reply(request)) {
                    is CoachResult.Success -> {
                        synchronized(cache) { cache[key] = r.reply }
                        return publish(CoachUi(reply = r.reply, source = ReplySource.CLOUD_AI))
                    }
                    is CoachResult.Failure -> {
                        onCloudFailure(r.reason)
                        return publish(
                            CoachUi(reply = builtIn.reply(request), source = ReplySource.OFFLINE, cloudFailed = true, failure = r.reason)
                        )
                    }
                }
            }
            val current = _ui.value
            if (automatic && current.source == ReplySource.CLOUD_AI && current.reply != null) return current
        }
        return publish(CoachUi(reply = builtIn.reply(request), source = ReplySource.OFFLINE))
    }

    private fun publish(ui: CoachUi): CoachUi {
        _ui.value = ui
        return ui
    }
}
