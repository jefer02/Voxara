package com.example.voxara.audio

import android.content.Context
import android.util.Log
import com.example.voxara.core.headphones.HeadphoneProfile
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.NotHeadphonesOutcome
import com.example.voxara.data.ExposureRepository
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.sync.HeadphoneWire
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.sync.LowerVolumeRequest
import com.example.voxara.sync.LowerVolumeResult
import com.example.voxara.sync.NotHeadphonesRequest
import com.example.voxara.sync.PhoneLink
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * "LOWER TO SAFE VOLUME" — brings the music volume of the headphones that are over the recommended level down
 * to [HeadphoneProfile.SAFE_DBA] (estimated). One entry point for the screen and for the notification action.
 *
 *  - Headphones on THIS watch: the watch lowers its own music stream.
 *  - Headphones on the PHONE: a command goes to the phone (MessageClient, with an id) and the phone answers
 *    with the same id once it has read the volume back. No answer in time is reported as "could not reach the
 *    phone": it is never assumed to have worked, and it is never queued to run later (a volume that drops
 *    minutes after the wearer asked would be a surprise).
 *
 * The outcome is always told: lowered, already safe, fixed system volume, nothing playing, failed, unreachable.
 */
object HeadphoneControl {

    private const val TAG = "VoxaraHeadphones"
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<LowerVolumeResult>>()

    /** The phone's answer arrived (from the Data Layer listener). */
    fun onResult(result: LowerVolumeResult) { waiting.remove(result.id)?.complete(result) }

    suspend fun lowerToSafe(context: Context, source: LayerSource): LowerVolumeResult {
        ExposureRepository.setLowerVolume(LowerUi.Working)
        val result = if (source == LayerSource.PHONE) lowerOnPhone(context.applicationContext) else lowerOnWatch(context.applicationContext)
        // The panel stays up to say what happened, then closes itself on success; the engine re-alerts by itself
        // if the level climbs again.
        ExposureRepository.setLowerVolume(LowerUi.Done(result.outcome, result.estimatedDba))
        return result
    }

    /**
     * "NOT HEADPHONES" — the wearer says the output headphones mode is using is not headphones. Headphones mode
     * leaves at once and stops counting that output until it disconnects (a new connection is detected again);
     * with [rememberSpeaker], a Bluetooth device is remembered as a speaker for later connections too.
     *
     *  - This watch's output: the shared list leaves it out ([HeadphoneWatcher.notHeadphones]).
     *  - The phone's output: the watch ignores the phone's reports until the phone says its headphones are gone,
     *    and asks the phone to leave the output out itself (and remember it) — fire and forget: if the message is
     *    lost, the watch still ignores it until it disconnects, and the wearer can say it again from the phone.
     *
     * The room is still measured, saved and alerted on; headphone alerts stop only for that output. The choice is
     * logged (the output's type only).
     */
    suspend fun notHeadphones(context: Context, source: LayerSource, rememberSpeaker: Boolean): NotHeadphonesOutcome {
        val app = context.applicationContext
        val outcome = if (source == LayerSource.PHONE) {
            ExposureRepository.phoneOutputIgnored = true
            val request = NotHeadphonesRequest(UUID.randomUUID().toString(), System.currentTimeMillis(), rememberSpeaker).encode()
            val sent = PhoneLink.phoneNodes(app).count { n ->
                runCatching { Wearable.getMessageClient(app).sendMessage(n.id, HeadphoneWire.PATH_CMD_NOT_HEADPHONES, request).await() }
                    .onFailure { Log.w(TAG, "not-headphones command not sent: ${it.javaClass.simpleName}") }
                    .isSuccess
            }
            PhoneLink.observeSend(sent > 0)
            NotHeadphonesOutcome.IGNORED
        } else {
            SpeakerMemory.ensureLoaded()
            withContext(Dispatchers.Main) { HeadphoneWatcher(app).notHeadphones(rememberSpeaker) }
        }
        Log.i(TAG, "not headphones: $source, $outcome")
        // The in-view alert belongs to the output that was just left out.
        ExposureRepository.dismissHeadphoneAlert()
        ExposureRepository.layerDropRequested.set(true)
        ExposureRepository.layerWake.trySend(Unit)
        ExposureRepository.listeningWake.trySend(Unit)
        return outcome
    }

    private suspend fun lowerOnWatch(app: Context): LowerVolumeResult = withContext(Dispatchers.Main) {
        val r = HeadphoneWatcher(app).lowerToSafe()
        LowerVolumeResult(UUID.randomUUID().toString(), r.outcome, r.index, r.estimatedDba)
    }

    private suspend fun lowerOnPhone(app: Context): LowerVolumeResult {
        val id = UUID.randomUUID().toString()
        fun failed(o: LowerOutcome) = LowerVolumeResult(id, o, 0, null)
        val nodes = PhoneLink.phoneNodes(app)
        if (nodes.isEmpty()) return failed(LowerOutcome.UNREACHABLE)
        val answer = CompletableDeferred<LowerVolumeResult>()
        waiting[id] = answer
        try {
            val request = LowerVolumeRequest(id, System.currentTimeMillis(), HeadphoneProfile.SAFE_DBA).encode()
            val sent = nodes.count { n ->
                runCatching { Wearable.getMessageClient(app).sendMessage(n.id, HeadphoneWire.PATH_CMD_LOWER, request).await() }
                    .onFailure { Log.w(TAG, "lower command not sent: ${it.javaClass.simpleName}") }
                    .isSuccess
            }
            PhoneLink.observeSend(sent > 0)
            if (sent == 0) return failed(LowerOutcome.UNREACHABLE)
            return withTimeoutOrNull(HeadphoneWire.LOWER_TIMEOUT_MS) { answer.await() } ?: failed(LowerOutcome.UNREACHABLE)
        } finally {
            waiting.remove(id)
        }
    }
}
