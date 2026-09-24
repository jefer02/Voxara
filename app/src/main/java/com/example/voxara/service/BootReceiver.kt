package com.example.voxara.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voxara.data.VoxaraStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * After a reboot, monitoring the wearer left on cannot restart by itself (Android 14+ refuses a
 * microphone foreground service from BOOT_COMPLETED). Post a tap-to-resume prompt instead.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (VoxaraStore(app).read().monitoringEnabled) Notifications.postResumePrompt(app)
            } finally {
                pending.finish()
            }
        }
    }
}
