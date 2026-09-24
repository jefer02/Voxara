package com.example.voxara.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.data.VoxaraStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Got it" and "Snooze 1 h" from an alert notification. Snooze never applies to 100% alerts. */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { AlertKind.valueOf(intent.getStringExtra(AlertNotifier.EXTRA_KIND).orEmpty()) }
            .getOrNull() ?: return
        context.getSystemService(NotificationManager::class.java).cancel(AlertNotifier.notificationId(kind))
        if (intent.action != AlertNotifier.ACTION_SNOOZE || !kind.snoozable) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                VoxaraStore(app).updateAlertMemory { AlertEngine.snooze(it, kind, System.currentTimeMillis()) }
            } finally {
                pending.finish()
            }
        }
    }
}
