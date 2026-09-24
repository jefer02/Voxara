package com.example.voxara.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.voxara.core.headphones.BtClassHint
import com.example.voxara.core.headphones.Debouncer
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.categoryOf
import com.example.voxara.core.headphones.estimateListening

/**
 * Watches the WATCH's own audio outputs for headphones and estimates the listening level.
 *
 * Public APIs only: AudioManager device callbacks, isMusicActive, the music stream volume and
 * its dB curve. The microphone is never used as a proxy. Only the headphone CATEGORY leaves this
 * class — never a product name or a Bluetooth address (the address is read transiently, with
 * BLUETOOTH_CONNECT, only to tell a speaker or car kit from headphones, and is not kept).
 */
class HeadphoneMonitor(context: Context) {

    data class Snapshot(
        /** Headphones connected (debounced), and their category. */
        val category: HeadphoneCategory?,
        /** Media is playing while headphones are connected. */
        val playing: Boolean,
        val estimate: ListeningEstimate?,
        val volumeIndex: Int,
        val volumeMax: Int,
    ) {
        val connected: Boolean get() = category != null
        /** The estimate that counts: only while actually playing to headphones. */
        val listeningDba: Double? get() = if (playing) estimate?.dba else null
    }

    private val app = context.applicationContext
    private val am = app.getSystemService(AudioManager::class.java)
    private val debouncer = Debouncer<HeadphoneCategory?>(null)
    private val handler = Handler(Looper.getMainLooper())

    /** The wearer's "my headphones are usually…" setting. */
    @Volatile var usualCategory: HeadphoneCategory? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = Unit
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = Unit
    }

    fun start() {
        // The callback keeps the platform's device list warm; the state is polled per burst.
        runCatching { am?.registerAudioDeviceCallback(callback, handler) }
    }

    fun stop() {
        runCatching { am?.unregisterAudioDeviceCallback(callback) }
    }

    /** Reads the current state. Cheap: called once per dosimeter burst. */
    fun snapshot(nowMs: Long): Snapshot {
        val manager = am ?: return Snapshot(null, false, null, 0, 0)
        val raw = connectedCategory(manager)
        val category = debouncer.offer(raw, nowMs)
        val index = runCatching { manager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val max = runCatching { manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val deviceType = headphoneDeviceType(manager)
        val volumeDb = if (deviceType != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                manager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, deviceType).toDouble()
            }.getOrNull()?.takeIf { it.isFinite() }
        } else null
        val playing = category != null && runCatching { manager.isMusicActive }.getOrDefault(false)
        return Snapshot(
            category = category,
            playing = playing,
            estimate = if (category != null) estimateListening(volumeDb, index, max) else null,
            volumeIndex = index,
            volumeMax = max,
        )
    }

    private fun outputs(manager: AudioManager): Array<AudioDeviceInfo> =
        runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrDefault(emptyArray())

    /** The most specific headphone category among connected outputs, or null. */
    private fun connectedCategory(manager: AudioManager): HeadphoneCategory? =
        outputs(manager).mapNotNull { d ->
            categoryOf(kindOf(d.type), btHint(d), usualCategory)
        }.minByOrNull { if (it == HeadphoneCategory.UNKNOWN) 1 else 0 }

    private fun headphoneDeviceType(manager: AudioManager): Int? =
        outputs(manager).firstOrNull { categoryOf(kindOf(it.type), btHint(it), usualCategory) != null }?.type

    /** Bluetooth class hint, only with BLUETOOTH_CONNECT; the address is not kept. */
    @SuppressLint("MissingPermission")
    private fun btHint(d: AudioDeviceInfo): BtClassHint {
        val kind = kindOf(d.type)
        if (kind != OutputKind.BLUETOOTH_A2DP && kind != OutputKind.BLE_HEADSET) return BtClassHint.UNKNOWN
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return BtClassHint.UNKNOWN
        return runCatching {
            val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
            val cls = adapter?.getRemoteDevice(d.address)?.bluetoothClass ?: return BtClassHint.UNKNOWN
            when (cls.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BtClassHint.HEADPHONES_OR_HEADSET
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> BtClassHint.SPEAKER_OR_CAR
                else -> BtClassHint.UNKNOWN
            }
        }.getOrDefault(BtClassHint.UNKNOWN)
    }

    companion object {
        /** AudioDeviceInfo type -> output kind. Hearing aids are deliberately not headphones. */
        fun kindOf(type: Int): OutputKind = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.USB_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_BLE_HEADSET -> OutputKind.BLE_HEADSET                   // API 31
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> OutputKind.BLE_BROADCAST               // API 33
            else -> OutputKind.OTHER
        }
    }
}
