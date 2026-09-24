package com.example.voxara.phone

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
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/**
 * Samples phone listening every 15 minutes (WorkManager's minimum) and at Bluetooth connect /
 * disconnect edges. When media is playing to headphones it sends ONE span to the watch:
 * end time, seconds, volume (dB and index) and the output TYPE. Nothing else.
 *
 * Honest limits: a 15-minute sample cannot see pauses or volume changes in between, so the watch
 * files these minutes as very-low-confidence estimates.
 */
class ListeningSampler(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!PhonePrefs.enabled(ctx)) return Result.success()
        val am = ctx.getSystemService(AudioManager::class.java) ?: return Result.success()
        val now = System.currentTimeMillis()
        val last = PhonePrefs.lastSampleMs(ctx)
        PhonePrefs.setLastSampleMs(ctx, now)

        val headphones = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { outputKind(it.type) != null }
        val kind = headphones?.let { outputKind(it.type) }
        val playing = kind != null && am.isMusicActive
        val seconds = spanSeconds(last, now)
        if (!playing || headphones == null || kind == null || seconds <= 0.0) return Result.success()

        val index = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeDb = runCatching {
            am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, headphones.type).toDouble()
        }.getOrDefault(Double.NaN)

        val request = PutDataMapRequest.create("$PATH_PREFIX/$now").apply {
            dataMap.putLong(KEY_END_MS, now)
            dataMap.putDouble(KEY_SECONDS, seconds)
            dataMap.putDouble(KEY_VOLUME_DB, volumeDb)
            dataMap.putInt(KEY_INDEX, index)
            dataMap.putInt(KEY_MAX, max)
            dataMap.putString(KEY_OUTPUT, kind)
        }.asPutDataRequest().setUrgent()

        return runCatching {
            Tasks.await(Wearable.getDataClient(ctx).putDataItem(request), 30, TimeUnit.SECONDS)
            PhonePrefs.setLastSyncMs(ctx, now)
            Result.success()
        }.getOrElse {
            Log.w(TAG, "sync failed; will retry", it)
            Result.retry()
        }
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
