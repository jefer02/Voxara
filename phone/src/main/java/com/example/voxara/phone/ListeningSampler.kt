package com.example.voxara.phone

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.voxara.audio.HeadphoneWatcher
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.phone.listening.PhoneListeningService
import com.example.voxara.phone.listening.WatchBridge
import com.example.voxara.sync.PhoneListeningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * THE FALLBACK. The detector is [PhoneListeningService] (events: devices, playback, volume). This worker is
 * only the safety net for when Android does not let that service run (it was started from the background and
 * refused, or the system stopped it): every 15 minutes (WorkManager's minimum) and at Bluetooth edges it takes
 * ONE sample. While the service is running it does nothing at all, so a minute is never reported twice.
 *
 * When media is playing to headphones it sends ONE span to the watch (end time, seconds, volume and the
 * output TYPE; nothing else) and tells the watch the live state.
 *
 * Honest limits: one sample cannot see pauses or volume changes in between, so this path is coarser. The
 * span can only be as long as the time since the previous sample (at most 15 minutes).
 */
class ListeningSampler(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!PhonePrefs.enabled(ctx)) return Result.success()
        // The service is the detector: while it runs, this stays out of the way.
        if (PhoneListeningService.running) return Result.success()
        // From the background this is usually refused on Android 12+; when it is allowed the service takes over.
        if (PhoneListeningService.tryStart(ctx)) return Result.success()
        val now = System.currentTimeMillis()
        val last = PhonePrefs.lastSampleMs(ctx)
        PhonePrefs.setLastSampleMs(ctx, now)

        // One look at the outputs, through the same classifier the service uses.
        val snap = withContext(Dispatchers.Main) { HeadphoneWatcher(ctx).refresh() }
        val route = snap.route
        // The watch always hears the state, so its "headphones on the phone" is right at the edges too.
        runCatching {
            WatchBridge.sendState(
                ctx,
                if (route == null) PhoneListeningState.none(now, PhonePrefs.nextSequence(ctx, now))
                else PhoneListeningState(
                    now, PhonePrefs.nextSequence(ctx, now), true, snap.playing, route.kind, route.possibleSpeaker,
                    snap.volumeDb, snap.volumeIndex, snap.volumeMax, snap.fixedVolume, now,
                ),
            )
        }
        val seconds = spanSeconds(last, now)
        if (route == null || !snap.playing || seconds <= 0.0) return Result.success()

        WatchBridge.enqueueSpan(
            ctx,
            PhoneListeningSample(
                endMs = now, seconds = seconds, volumeDb = snap.volumeDb,
                index = snap.volumeIndex, max = snap.volumeMax, output = route.kind,
            ),
        )
        // What the Data Layer does not take stays in the outbox; the next flush (a reconnect, the next sample) sends it once.
        WatchBridge.flushSpans(ctx, now)
        return Result.success()
    }

    companion object {
        private const val TAG = "VoxaraPhone"
        private const val PERIODIC = "voxara-phone-listening"
        private const val EDGE = "voxara-phone-listening-edge"

        // Shared contract with the watch (app/core/headphones/PhoneListening.kt).
        const val PATH_PREFIX = "/voxara/listening"
        const val KEY_END_MS = "end_ms"
        const val KEY_SECONDS = "seconds"
        const val KEY_VOLUME_DB = "volume_db"
        const val KEY_INDEX = "index"
        const val KEY_MAX = "max"
        const val KEY_OUTPUT = "output"
        const val MAX_SPAN_SECONDS = 15 * 60.0

        /** Seconds since the previous sample, capped at the sampling interval; 0 on the first. */
        fun spanSeconds(lastMs: Long, nowMs: Long): Double =
            if (lastMs <= 0L || nowMs <= lastMs) 0.0 else ((nowMs - lastMs) / 1000.0).coerceAtMost(MAX_SPAN_SECONDS)

        /** AudioDeviceInfo type -> the watch's OutputKind name, or null when not headphones. */
        fun outputKind(type: Int): String? = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADPHONES"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
            else -> null
        }

        /**
         * The headphone [type] as one of the OUTPUT device constants that
         * [AudioManager.getStreamVolumeDb] accepts. [AudioDeviceInfo.getType] is typed as "any device
         * type" (inputs too), so lint cannot see that [outputKind] already narrowed it to these
         * six. For every type [outputKind] accepts, the value returned is the number passed in
         * (the `else` branch is TYPE_WIRED_HEADPHONES, the sixth); nothing else reaches this call.
         */
        // InlinedApi: TYPE_BLE_HEADSET (API 31) and TYPE_BLE_BROADCAST (API 33) are compile-time ints
        // copied into the code; their branches can only match on devices that report those types.
        @SuppressLint("InlinedApi")
        fun outputDeviceType(type: Int): Int = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceInfo.TYPE_USB_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceInfo.TYPE_BLE_HEADSET
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioDeviceInfo.TYPE_BLE_BROADCAST
            else -> AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ListeningSampler>(15, TimeUnit.MINUTES).build(),
            )
            sampleNow(context)
        }

        fun sampleNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                EDGE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ListeningSampler>().build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
