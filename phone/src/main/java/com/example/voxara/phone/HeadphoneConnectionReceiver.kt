package com.example.voxara.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Bluetooth connection edges (manifest-exempt broadcasts). Samples immediately, so a session's
 * start and end are not up to 15 minutes late. The device itself is never read.
 */
class HeadphoneConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (PhonePrefs.enabled(context)) ListeningSampler.sampleNow(context)
    }
}
