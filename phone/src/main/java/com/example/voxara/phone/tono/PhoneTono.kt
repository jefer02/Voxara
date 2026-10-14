package com.example.voxara.phone.tono

import android.content.res.Resources
import android.util.Log
import com.example.voxara.ai.CoachClient
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.OfflineCoach
import com.example.voxara.ai.OpenAiCompatibleCoachClient
import com.example.voxara.ai.TonoCore
import com.example.voxara.core.ai.CoachTask
import com.example.voxara.core.ai.SnapshotBuilder
import com.example.voxara.core.ai.SnapshotInputs
import com.example.voxara.phone.BuildConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Tono on the phone: the SAME shared [TonoCore] (symptom guard, consent, cache, rate limits,
 * fallback) and the same privacy gate ([SnapshotBuilder]) as the watch. The cloud client exists
 * only in debug builds with a key in local.properties, behind [CoachClient] so a server-side proxy
 * can replace it without touching anything else.
 */
class PhoneTono(private val cloud: CoachClient? = defaultCloud()) {

    private val core = TonoCore(onCloudFailure = { Log.w(TAG, "cloud reply failed: $it") })

    val ui: StateFlow<CoachUi> = core.ui

    val cloudAvailable: Boolean get() = cloud != null

    /**
     * @param res resources already localized to the wearer's language
     * @param consent the cloud AI opt-in (off by default)
     */
    suspend fun ask(
        res: Resources,
        locale: String,
        task: CoachTask,
        question: String?,
        inputs: SnapshotInputs,
        consent: Boolean,
        automatic: Boolean = false,
    ): CoachUi = core.ask(
        task = task,
        snapshot = SnapshotBuilder.build(inputs, locale),
        question = question,
        alertKind = null,
        builtIn = OfflineCoach(res),
        consent = consent,
        client = cloud,
        automatic = automatic,
    )

    companion object {
        private const val TAG = "Tono"

        fun defaultCloud(): CoachClient? = BuildConfig.AI_API_KEY.takeIf { it.isNotBlank() }?.let {
            OpenAiCompatibleCoachClient(BuildConfig.AI_BASE_URL, BuildConfig.AI_MODEL, it)
        }
    }
}
