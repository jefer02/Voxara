package com.example.voxara.ai

import android.content.Context
import android.util.Log
import com.example.voxara.BuildConfig
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.data.ExposureState
import com.example.voxara.data.LocaleStore
import com.example.voxara.data.VoxaraStore
import kotlinx.coroutines.flow.StateFlow

/**
 * TONO — the coach, as the watch sees it. Decides nothing: it phrases what the deterministic
 * engine computed. The decisions (symptom guard, consent, cache, rate limits, fallback) live in
 * the shared [TonoCore], the same one the phone companion uses; this object only supplies the
 * watch's state, language, consent and cloud client.
 */
object Tono {

    private const val TAG = "Tono"

    // The reason is a fixed code (never the key or the question).
    private val core = TonoCore(onCloudFailure = { Log.w(TAG, "cloud reply failed: $it") })

    val ui: StateFlow<CoachUi> = core.ui

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
        val locale = LocaleStore.resolve(context).language
        return core.ask(
            task = task,
            snapshot = CoachSnapshots.build(state, locale),
            question = question,
            alertKind = alertKind,
            builtIn = OfflineCoach(LocaleStore.localized(context.applicationContext).resources),
            consent = VoxaraStore(context).read().aiConsent,
            client = client,
            automatic = automatic,
        )
    }
}
