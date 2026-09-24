package com.example.voxara.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.monitoring.monitoringStatus
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.VoxaraStore
import com.example.voxara.tile.VoxaraTileService
import java.util.concurrent.TimeUnit

/**
 * WATCHDOG — if the wearer left monitoring on but the dosimeter's heartbeat has gone stale (the
 * system or an OEM battery manager stopped it), post one tap-to-resume prompt. It cannot restart
 * the microphone service itself: Android 14+ only allows that from a visible surface.
 */
class MonitoringWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val p = VoxaraStore(applicationContext).read()
        val now = System.currentTimeMillis()
        val status = monitoringStatus(
            enabled = p.monitoringEnabled,
            serviceActive = ExposureRepository.serviceActive,
            lastHeartbeatMs = p.heartbeatMs,
            nowMs = now,
        )
        if (status == MonitoringStatus.PAUSED) {
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // One prompt per stale episode: only when nothing was posted since the last beat.
            if (prefs.getLong(KEY_PROMPTED_FOR, -1L) != p.heartbeatMs) {
                Notifications.postResumePrompt(applicationContext, afterReboot = false)
                prefs.edit().putLong(KEY_PROMPTED_FOR, p.heartbeatMs).apply()
            }
            VoxaraTileService.requestRefresh(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "voxara-monitoring-watchdog"
        private const val PREFS = "voxara_watchdog"
        private const val KEY_PROMPTED_FOR = "prompted_for_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitoringWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
