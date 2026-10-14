package com.example.voxara.phone.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.widget.DoseWidget
import java.util.concurrent.TimeUnit

/**
 * Hourly background sync while a watch is linked. It only reads from the watch; the watch keeps
 * measuring and alerting on its own whether or not this runs.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        PhoneGraph.init(applicationContext)
        PhoneGraph.sync.sync()
        runCatching { DoseWidget.refresh(applicationContext) }
        // A failed sync is recorded and shown; retrying sooner than the next hour gains nothing.
        return Result.success()
    }

    companion object {
        private const val NAME = "voxara-phone-sync"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
