package com.example.voxara.phone.listening

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot or an update of the app: watch for headphones again (if the wearer opted in). Both are on
 * Android's list of moments a foreground service may start from the background, and `connectedDevice` is
 * not one of the types restricted at boot.
 */
class PhoneBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            PhoneListeningService.ensureRunning(context)
        }
    }
}
