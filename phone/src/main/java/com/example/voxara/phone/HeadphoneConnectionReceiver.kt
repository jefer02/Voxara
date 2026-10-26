package com.example.voxara.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voxara.phone.listening.PhoneListeningService

/**
 * Bluetooth connection edges (the four broadcasts Android still lets an app register in its manifest). They
 * wake the app so it can start watching: the foreground service is the detector, and this only makes sure it
 * is running. Android does not let a Bluetooth broadcast START a foreground service in every situation (see
 * [PhoneListeningService]), so when it is refused one sample is taken instead and nothing is lost at the edge.
 * The device itself is never read.
 */
class HeadphoneConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PhoneListeningService.ensureRunning(context)
    }
}
