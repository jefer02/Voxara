package com.example.voxara.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.voxara.complication.DoseComplicationService
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.VoxaraStore
import com.example.voxara.tile.VoxaraTileService
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ROLLUP — WorkManager writes the daily ledger at local midnight. The dosimeter service catches
 * the case where it happens to be running when midnight passes; this catches every other case,
 * including a watch that was off the wrist all night.
 */
class MidnightRollupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = VoxaraStore(applicationContext)
        val p = store.read()
        val today = todayEpoch()
        if (p.dayEpoch != 0L && p.dayEpoch != today) {
            store.rollOverDay(p.todayProfile)
            if (ExposureRepository.isInitialised()) {
                ExposureRepository.engine.resetDay()
                ExposureRepository.hydrate(store.read())
            }
        }
        VoxaraTileService.requestRefresh(applicationContext)
        DoseComplicationService.requestRefresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "voxara-midnight-rollup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MidnightRollupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilMidnight(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun millisUntilMidnight(): Long {
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
            return (next.timeInMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
        }

        private fun todayEpoch(): Long {
            val cal = Calendar.getInstance()
            val offset = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)).toLong()
            return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() + offset)
        }
    }
}
