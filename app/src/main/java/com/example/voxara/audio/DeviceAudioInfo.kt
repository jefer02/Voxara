package com.example.voxara.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager

/**
 * What this watch reports about itself, read at runtime — nothing about its microphone is
 * assumed. Used by the calibration record, the debug readout and the source choice.
 */
data class DeviceAudioInfo(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
    /** The platform's UNPROCESSED-support property; null when the device does not report it. */
    val unprocessedSupported: Boolean?,
) {
    /** The key a calibration is bound to. */
    val deviceKey: String get() = "$manufacturer $model"

    companion object {
        fun read(context: Context): DeviceAudioInfo {
            val am = context.getSystemService(AudioManager::class.java)
            val prop = runCatching {
                am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            }.getOrNull()
            return DeviceAudioInfo(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE.orEmpty(),
                unprocessedSupported = prop?.let { it == "true" },
            )
        }
    }
}

/**
 * Battery policies that can stop monitoring. OEM battery managers (e.g. Samsung One UI Watch)
 * may be stricter than stock Wear OS; the app reports what it can detect and never assumes.
 */
data class PowerPolicy(
    val powerSaveMode: Boolean,
    val ignoringBatteryOptimizations: Boolean,
) {
    companion object {
        fun read(context: Context): PowerPolicy {
            val pm = context.getSystemService(PowerManager::class.java)
            return PowerPolicy(
                powerSaveMode = runCatching { pm?.isPowerSaveMode }.getOrNull() == true,
                ignoringBatteryOptimizations = runCatching {
                    pm?.isIgnoringBatteryOptimizations(context.packageName)
                }.getOrNull() == true,
            )
        }
    }
}
