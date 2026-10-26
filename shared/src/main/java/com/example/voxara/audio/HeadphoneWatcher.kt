package com.example.voxara.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.voxara.core.headphones.BtClassHint
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneProfile
import com.example.voxara.core.headphones.HeadphoneRoute
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.headphones.NotHeadphonesOutcome
import com.example.voxara.core.headphones.OutputId
import com.example.voxara.core.headphones.OutputIgnoreList
import com.example.voxara.core.headphones.OutputKind
import com.example.voxara.core.headphones.RouteCandidate
import com.example.voxara.core.headphones.chooseRoute
import com.example.voxara.core.headphones.estimateListening
import com.example.voxara.core.headphones.safeVolumeIndex
import com.example.voxara.sync.LowerOutcome

/**
 * AUTOMATIC, EVENT-DRIVEN HEADPHONE DETECTION — the same class on the watch and on the phone.
 *
 * What it listens to (no polling to find headphones):
 *  - [AudioDeviceCallback]: a wired, USB, BLE or A2DP output appears or goes;
 *  - [AudioManager.AudioPlaybackCallback]: something starts or stops playing (used only as a trigger: the
 *    answer is [AudioManager.isMusicActive], which is documented and global, whatever the callback
 *    list shows an app that is not privileged);
 *  - the music-volume change broadcast and the settings provider (best effort: neither is a documented
 *    public API, so neither is trusted);
 *  - a GUARD TICK every [GUARD_MS] that runs ONLY while headphones are playing, so a missed volume event
 *    costs at most that long and the watch's "within 3 s" holds without depending on an undocumented event.
 *    When nothing plays there is no tick at all.
 *
 * Which output counts: [chooseRoute]. A Bluetooth speaker or car kit is told from headphones by its
 * Bluetooth class, which needs BLUETOOTH_CONNECT (API 31+); without it A2DP counts as headphones and the
 * snapshot says [Snapshot.possibleSpeaker]. Only the output TYPE and the category are kept: never a name,
 * an address (it is read to look the class up and to recognise an output the wearer marked, and not kept), a title
 * or an app.
 *
 * "Not headphones" ([notHeadphones]): the wearer can leave the current output out of headphones mode until it
 * disconnects, or, for a Bluetooth device, remember it as a speaker. The list is one per process ([ignored]), so
 * every watcher in the app agrees; the app persists the remembered speakers ([salt], [onSpeakersChanged]).
 *
 * Callbacks run on the main thread. Safe to start and stop repeatedly.
 */
class HeadphoneWatcher(context: Context) {

    data class Snapshot(
        val route: HeadphoneRoute?,
        /** Something is playing (music stream) — only meaningful with a [route]. */
        val playing: Boolean,
        val volumeDb: Double?,
        val volumeIndex: Int,
        val volumeMax: Int,
        /** The platform fixes the volume: it cannot be lowered from here. */
        val fixedVolume: Boolean,
        /** BLUETOOTH_CONNECT is granted (or not needed on this API level). */
        val btDetailsAvailable: Boolean,
        /** Some output is being left out until it disconnects ("Not headphones"). */
        val ignoringOutput: Boolean = false,
    ) {
        val connected: Boolean get() = route != null
        val category: HeadphoneCategory? get() = route?.category
        val possibleSpeaker: Boolean get() = route?.possibleSpeaker == true

        /** The current output can be remembered as a speaker (a Bluetooth device with a full address). */
        val canRememberSpeaker: Boolean get() = route?.id?.rememberable == true

        /** The estimated listening level (an estimate, never a measurement): only while connected AND playing. */
        val estimate: ListeningEstimate?
            get() = if (route != null && playing) estimateListening(volumeDb, volumeIndex, volumeMax) else null

        val listeningDba: Double? get() = estimate?.dba

        companion object {
            val NONE = Snapshot(null, false, null, 0, 0, false, false)
        }
    }

    private val app = context.applicationContext
    private val am: AudioManager? = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    /** The wearer's "my headphones are usually…" setting (earbuds / over-ear), or null. */
    @Volatile var usualCategory: HeadphoneCategory? = null

    /** Called on the main thread whenever the snapshot changes. */
    @Volatile var onChanged: ((Snapshot) -> Unit)? = null

    @Volatile private var last: Snapshot = Snapshot.NONE
    private var started = false

    /** Bluetooth class hints by address for the devices that are connected now (the address itself is not kept past this). */
    private val hintCache = HashMap<String, BtClassHint>()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { hintCache.clear(); refresh() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { hintCache.clear(); refresh() }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) { refresh() }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    private val guard = object : Runnable {
        override fun run() {
            refresh()
            // Only while there is something to watch: no playback, no tick.
            if (last.connected && last.playing) handler.postDelayed(this, GUARD_MS)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start() {
        if (started) return
        started = true
        runCatching { am?.registerAudioDeviceCallback(deviceCallback, handler) }
        runCatching { am?.registerAudioPlaybackCallback(playbackCallback, handler) }
        runCatching {
            val filter = IntentFilter(VOLUME_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Not exported: only the platform's own broadcast reaches it.
                app.registerReceiver(volumeReceiver, filter, null, handler, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(volumeReceiver, filter, null, handler)
            }
        }
        runCatching { app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver) }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(guard)
        runCatching { am?.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { am?.unregisterAudioPlaybackCallback(playbackCallback) }
        runCatching { app.unregisterReceiver(volumeReceiver) }
        runCatching { app.contentResolver.unregisterContentObserver(settingsObserver) }
        hintCache.clear()
        last = Snapshot.NONE
    }

    /** The state right now (a few cheap binder calls), also published to [onChanged] if it changed. */
    fun refresh(): Snapshot {
        val now = read()
        val changed = now != last
        val wasTicking = last.connected && last.playing
        last = now
        if (now.connected && now.playing && !wasTicking && started) {
            handler.removeCallbacks(guard)
            handler.postDelayed(guard, GUARD_MS)
        }
        if (changed) onChanged?.invoke(now)
        return now
    }

    fun current(): Snapshot = last

    /** Tests replace what is read (the platform has no headphones in a unit test); production leaves it null. */
    internal var reader: (() -> Snapshot)? = null

    private fun read(): Snapshot {
        reader?.let { return it() }
        val manager = am ?: return Snapshot.NONE
        val outputs = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrDefault(emptyArray())
        val candidates = outputs.map { RouteCandidate(kindOf(it.type), btHint(it), it.type, OutputId(kindOf(it.type), it.type, it.address)) }
        // An output left out "until it disconnects" is forgotten as soon as it is no longer there.
        ignored.prune(candidates.mapNotNull { it.id })
        val route = chooseRoute(candidates, usualCategory) { c -> c.id?.let { ignored.isIgnored(it, salt) } == true }
        val index = runCatching { manager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val max = runCatching { manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val fixed = runCatching { manager.isVolumeFixed }.getOrDefault(false)
        val playing = route != null && runCatching { manager.isMusicActive }.getOrDefault(false)
        val db = route?.let { volumeDbAt(manager, index, it.androidType) }
        return Snapshot(route, playing, db, index, max, fixed, canReadBluetoothClass(), ignored.ignoringNow)
    }

    private fun volumeDbAt(manager: AudioManager, index: Int, deviceType: Int): Double? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching { manager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, deviceType).toDouble() }
            .getOrNull()?.takeIf { it.isFinite() }
    }

    // ------------------------------------------------------------------ Bluetooth class

    private fun canReadBluetoothClass(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun btHint(d: AudioDeviceInfo): BtClassHint {
        val kind = kindOf(d.type)
        if (kind != OutputKind.BLUETOOTH_A2DP && kind != OutputKind.BLE_HEADSET && kind != OutputKind.BLE_BROADCAST) {
            return BtClassHint.UNKNOWN
        }
        if (!canReadBluetoothClass()) return BtClassHint.UNKNOWN
        val address = d.address
        if (address.isNullOrEmpty()) return BtClassHint.UNKNOWN
        return hintCache.getOrPut(address) {
            runCatching {
                val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
                hintOf(adapter?.getRemoteDevice(address)?.bluetoothClass)
            }.getOrDefault(BtClassHint.UNKNOWN)
        }
    }

    // ------------------------------------------------------------------ not headphones

    /**
     * "NOT HEADPHONES": leaves the output headphones mode is using out of it until it disconnects, and with
     * [rememberSpeaker] (a Bluetooth device with a full address) remembers it as a speaker for later connections
     * too. The room's measurement and alerts are untouched; headphone alerts stop only for this output.
     */
    fun notHeadphones(rememberSpeaker: Boolean): NotHeadphonesOutcome {
        val id = refresh().route?.id ?: return NotHeadphonesOutcome.NOTHING
        val remembered = if (rememberSpeaker) ignored.rememberSpeaker(id, salt) else { ignored.ignoreUntilDisconnect(id); false }
        if (remembered) onSpeakersChanged?.invoke(ignored.speakers)
        refresh()
        return if (remembered) NotHeadphonesOutcome.REMEMBERED else NotHeadphonesOutcome.IGNORED
    }

    // ------------------------------------------------------------------ lower to a safe volume

    /** The result of [lowerToSafe]. [index] is the music volume step after the attempt. */
    data class LowerResult(val outcome: LowerOutcome, val index: Int, val estimatedDba: Double?)

    /**
     * Brings the music volume down to the highest step whose estimated level is at or below [targetDba]
     * ([HeadphoneProfile.SAFE_DBA] by default). Never raises it. Says what happened: a fixed system volume
     * or a refusal is reported, never hidden, and success is only claimed after reading the volume back.
     */
    fun lowerToSafe(targetDba: Double = HeadphoneProfile.SAFE_DBA): LowerResult {
        val manager = am ?: return LowerResult(LowerOutcome.FAILED, 0, null)
        val snap = refresh()
        val route = snap.route ?: return LowerResult(LowerOutcome.NOT_PLAYING, snap.volumeIndex, null)
        if (snap.fixedVolume) return LowerResult(LowerOutcome.FIXED_VOLUME, snap.volumeIndex, snap.listeningDba)
        val dbAt: (Int) -> Double? = { i -> volumeDbAt(manager, i, route.androidType) }
        val target = safeVolumeIndex(snap.volumeIndex, snap.volumeMax, dbAt, targetDba)
        if (target >= snap.volumeIndex) {
            return LowerResult(LowerOutcome.ALREADY_SAFE, snap.volumeIndex, estimateListening(snap.volumeDb, snap.volumeIndex, snap.volumeMax)?.dba)
        }
        val set = runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        val after = refresh()
        return when {
            set.isFailure -> LowerResult(LowerOutcome.FAILED, after.volumeIndex, after.listeningDba)
            after.volumeIndex <= target -> LowerResult(LowerOutcome.LOWERED, after.volumeIndex, estimateListening(after.volumeDb, after.volumeIndex, after.volumeMax)?.dba)
            // The platform (or the headphones) did not take it.
            else -> LowerResult(LowerOutcome.FAILED, after.volumeIndex, after.listeningDba)
        }
    }

    companion object {
        /** The outputs the wearer said are not headphones: one list per process, so every watcher agrees. */
        val ignored = OutputIgnoreList()

        /** This install's salt for remembered speakers' digests (the app sets it from its storage before starting). */
        @Volatile var salt: String = ""

        /** Called with the remembered speakers whenever they change, so the app can persist them. */
        @Volatile var onSpeakersChanged: ((Set<String>) -> Unit)? = null

        /** Forgets every remembered speaker and persists that. */
        fun forgetSpeakers() {
            ignored.forgetSpeakers()
            onSpeakersChanged?.invoke(ignored.speakers)
        }

        /** While headphones play, the state is read at least this often (a missed volume event costs at most this). */
        const val GUARD_MS = 2_000L

        /** The music-volume broadcast: undocumented, used only as a hint on top of the guard tick. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        /** AudioDeviceInfo type -> output kind. Hearing aids and speakers are deliberately not headphones. */
        @SuppressLint("InlinedApi")
        fun kindOf(type: Int): OutputKind = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.USB_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_BLE_HEADSET -> OutputKind.BLE_HEADSET                   // API 31
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> OutputKind.BLE_BROADCAST               // API 33
            else -> OutputKind.OTHER
        }

        /** The Bluetooth device class as far as headphones vs speaker matters. */
        fun hintOf(cls: BluetoothClass?): BtClassHint = when (cls?.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BtClassHint.HEADPHONES_OR_HEADSET
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> BtClassHint.SPEAKER_OR_CAR
            else -> BtClassHint.UNKNOWN
        }
    }
}
