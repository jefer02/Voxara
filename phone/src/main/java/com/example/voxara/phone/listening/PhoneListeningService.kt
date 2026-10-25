package com.example.voxara.phone.listening

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.voxara.audio.HeadphoneWatcher
import com.example.voxara.core.headphones.NotHeadphonesOutcome
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneProfile
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.ListeningTracker
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.headphones.PhoneListeningSample
import com.example.voxara.phone.ListeningSampler
import com.example.voxara.phone.MainActivity
import com.example.voxara.phone.PhoneGraph
import com.example.voxara.phone.PhoneLocale
import com.example.voxara.phone.PhonePrefs
import com.example.voxara.phone.R
import com.example.voxara.settings.HeadphoneType
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.sync.HeadphoneWire
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.sync.LowerVolumeResult
import com.example.voxara.sync.PhoneListeningState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * WHAT THE SCREENS OF THE PHONE APP SEE of its headphones, live: the detector's snapshot, the alert in force
 * and where "lower the volume" is. In memory only.
 */
object PhoneListeningRuntime {
    data class Live(
        val running: Boolean = false,
        val snapshot: HeadphoneWatcher.Snapshot = HeadphoneWatcher.Snapshot.NONE,
        val alert: HeadphoneAlert? = null,
        val lower: LowerUi = LowerUi.Idle,
    )

    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live.asStateFlow()

    fun update(transform: (Live) -> Live) = _live.update(transform)
    fun dismissAlert() = _live.update { it.copy(alert = it.alert?.copy(dismissed = true), lower = if (it.lower is LowerUi.Working) it.lower else LowerUi.Idle) }
}

/**
 * HEADPHONES ON THE PHONE, DETECTED BY EVENTS AND KEPT AS LONG AS THE PHONE IS ON — the phone's half of
 * automatic headphones mode.
 *
 * A foreground service of type `connectedDevice` (it talks to Bluetooth, USB and wired audio devices; Android
 * asks for `FOREGROUND_SERVICE_CONNECTED_DEVICE`, plus one of a short list of permissions that
 * the manifest declares). It hosts the same [HeadphoneWatcher] as the watch: device callback, playback
 * callback, volume events, and a 2 s guard that runs only while something plays. On every change it:
 *   - closes the listening span that was running ([ListeningTracker]) and queues it for the watch's ledger;
 *   - tells the watch at once (message) and keeps one durable state item current;
 *   - runs the headphone-volume alert (the same engine and cooldown as the watch) and notifies the phone.
 * While headphones are connected it repeats its state every minute so "fresh" keeps meaning "still true".
 *
 * WHEN IT CAN START. Android 12 and later only lets an app start a foreground service from the background
 * in a short list of cases (an activity in front, boot, the user's notification or widget action, battery
 * optimisation turned off, ...). A Bluetooth broadcast is NOT one of them. So the service is started from the
 * app when it is in front, at boot and after an update, and from the Bluetooth broadcast as a best effort;
 * when the system refuses, a one-shot sample keeps working (see [ensureRunning]). The user can also
 * exclude the app from battery optimisation, which lifts the restriction (the app guides them to it).
 *
 * It exists only while the wearer has opted in to sharing headphone listening with the watch.
 */
class PhoneListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var watcher: HeadphoneWatcher
    private val tracker = ListeningTracker()
    private var previous: HeadphoneWatcher.Snapshot = HeadphoneWatcher.Snapshot.NONE
    private var levelSinceMs = 0L
    private var overSinceMs = 0L
    private var keepalive: Job? = null
    private var dwell: Job? = null
    private val send = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PhoneGraph.init(this)
        PhonePrefs.loadSpeakerMemory(this)
        watcher = HeadphoneWatcher(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promptly, on every start, before anything else.
        if (!enterForeground()) { stopSelf(); return START_NOT_STICKY }
        if (!PhonePrefs.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        if (!started) {
            started = true
            running = true
            PhoneListeningRuntime.update { it.copy(running = true) }
            scope.launch { applyUsualCategory() }
            watcher.onChanged = { onSnapshot(it) }
            watcher.start()
            // The state may already be "headphones playing": take it as the first span's start.
            onSnapshot(watcher.refresh(), force = true)
            scope.launch { sender() }
            scope.launch(Dispatchers.IO) { WatchBridge.flushSpans(this@PhoneListeningService) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        watcher.onChanged = null
        watcher.stop()
        runCatching {
            // The running span is not lost: it is closed and queued before the process lets go.
            val now = System.currentTimeMillis()
            tracker.update(now, null)
            queueSpans(previous, tracker.drain(now))
            // The scope is cancelled below, so this one outlives it: the watch must hear that nothing is connected now.
            val app = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                WatchBridge.sendState(app, PhoneListeningState.none(now, PhonePrefs.nextSequence(app, now)))
                WatchBridge.flushSpans(app)
            }
        }
        PhoneListeningRuntime.update { it.copy(running = false, snapshot = HeadphoneWatcher.Snapshot.NONE) }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun applyUsualCategory() {
        val type = VoxaraSettings.from(PhoneGraph.store.read().phoneSettings).headphoneType
        watcher.usualCategory = when (type) {
            HeadphoneType.EARBUDS -> HeadphoneCategory.EARBUDS
            HeadphoneType.OVER_EAR -> HeadphoneCategory.OVER_EAR
            HeadphoneType.NONE -> null
        }
    }

    // ------------------------------------------------------------------ one change of state

    private fun onSnapshot(snap: HeadphoneWatcher.Snapshot, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val levelBefore = previous.listeningDba
        val level = snap.listeningDba
        if (force || level != levelBefore || snap.connected != previous.connected) levelSinceMs = now

        // Close the span at the level that was in force, queue it for the watch's ledger.
        tracker.update(now, level)
        queueSpans(previous, tracker.drain(now))
        previous = snap
        PhoneListeningRuntime.update { it.copy(snapshot = snap) }

        // Tell the watch now (coalesced), and keep the minute-by-minute repeat only while headphones are connected.
        send.trySend(Unit)
        if (snap.connected) {
            if (keepalive?.isActive != true) keepalive = scope.launch {
                while (true) {
                    delay(HeadphoneWire.KEEPALIVE_MS)
                    val s = watcher.refresh()
                    val t = System.currentTimeMillis()
                    // A steady listening level also hands its finished stretch to the watch's ledger.
                    tracker.update(t, s.listeningDba)
                    queueSpans(s, tracker.drain(t))
                    send.trySend(Unit)
                }
            }
        } else {
            keepalive?.cancel(); keepalive = null
        }

        // The over-the-recommended-level alert: a level above 80 dBA must hold a few seconds first.
        dwell?.cancel()
        when {
            level == null -> { overSinceMs = 0L; scope.launch { evaluate(null) } }
            level > HeadphoneProfile.SAFE_DBA -> {
                if (overSinceMs == 0L) overSinceMs = now
                val wait = (HeadphoneWire.OVER_SAFE_DWELL_MS - (now - overSinceMs)).coerceAtLeast(0L)
                dwell = scope.launch {
                    delay(wait)
                    evaluate(watcher.refresh().listeningDba)
                    // The watch applies the same wait from the state's own time: one more state, now that it has held.
                    send.trySend(Unit)
                }
            }
            else -> { overSinceMs = 0L; scope.launch { evaluate(level) } }
        }
    }

    private suspend fun evaluate(level: Double?) {
        val alert = withContext(Dispatchers.IO) { PhoneAlerts.evaluate(this@PhoneListeningService, level) }
        if (alert != null) {
            PhoneListeningRuntime.update {
                it.copy(alert = HeadphoneAlert(alert.level, alert.value, System.currentTimeMillis(), LayerSource.PHONE), lower = LowerUi.Idle)
            }
        } else if (level == null || level <= HeadphoneProfile.SAFE_DBA) {
            // Back under the recommended level: the panel has nothing left to say (unless it is showing a result).
            PhoneListeningRuntime.update { if (it.lower is LowerUi.Idle) it.copy(alert = null) else it }
        }
    }

    /** Sends the current state to the watch, at most about twice a second however fast the volume moves. */
    private suspend fun sender() {
        for (u in send) {
            val snap = watcher.current()
            val now = System.currentTimeMillis()
            val state = stateOf(snap, now)
            val reached = withContext(Dispatchers.IO) { WatchBridge.sendState(this@PhoneListeningService, state) }
            PhoneGraph.sync.observeReachable(reached)
            if (reached) withContext(Dispatchers.IO) { WatchBridge.flushSpans(this@PhoneListeningService) }
            delay(MIN_SEND_GAP_MS)
        }
    }

    private fun stateOf(snap: HeadphoneWatcher.Snapshot, now: Long): PhoneListeningState {
        val route = snap.route ?: return PhoneListeningState.none(now, PhonePrefs.nextSequence(this, now))
        return PhoneListeningState(
            atMs = now, sequence = PhonePrefs.nextSequence(this, now),
            connected = true, playing = snap.playing, output = route.kind, possibleSpeaker = route.possibleSpeaker,
            volumeDb = snap.volumeDb, volumeIndex = snap.volumeIndex, volumeMax = snap.volumeMax,
            fixedVolume = snap.fixedVolume, levelSinceMs = levelSinceMs.takeIf { it > 0L } ?: now,
        )
    }

    private fun queueSpans(snap: HeadphoneWatcher.Snapshot, spans: List<ListeningTracker.Span>) {
        val route = snap.route ?: return
        for (s in spans) {
            if (s.seconds < 1.0) continue
            WatchBridge.enqueueSpan(
                this,
                PhoneListeningSample(
                    endMs = s.endMs, seconds = s.seconds, volumeDb = snap.volumeDb,
                    index = snap.volumeIndex, max = snap.volumeMax, output = route.kind,
                    coarse = false,       // cut at the change itself, not guessed from a sample
                ),
            )
        }
    }

    // ------------------------------------------------------------------ foreground

    private fun enterForeground(): Boolean {
        ensureChannel(this)
        val notification = notification(this)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else startForeground(NOTIFICATION_ID, notification)
            true
        } catch (t: RuntimeException) {
            // Refused by the system (started from the background): the caller falls back to a one-shot sample.
            Log.w(TAG, "foreground service refused: ${t.javaClass.simpleName}")
            false
        }
    }

    companion object {
        private const val TAG = "VoxaraPhoneHp"
        private const val CHANNEL_ID = "voxara_phone_listening"
        private const val NOTIFICATION_ID = 7_200
        private const val MIN_SEND_GAP_MS = 400L

        @Volatile var running = false
            private set
        @Volatile private var instance: PhoneListeningService? = null

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, PhoneLocale.localized(context).resources.getString(R.string.hp_service_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }

        private fun notification(context: Context): Notification {
            val res = PhoneLocale.localized(context).resources
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_voxara_status)
                .setContentTitle(res.getString(R.string.hp_service_title))
                .setContentText(res.getString(R.string.hp_service_text))
                .setOngoing(true).setSilent(true).setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(open)
                .build()
        }

        /**
         * Makes sure the headphones are being watched. Starts the foreground service when the wearer has opted in
         * and the system lets this moment start one; otherwise takes ONE sample now, so a connect or a
         * disconnect is not lost (the periodic worker is the safety net). Never throws.
         */
        fun ensureRunning(context: Context) {
            val app = context.applicationContext
            if (!PhonePrefs.enabled(app)) return
            if (running) return
            if (!tryStart(app)) ListeningSampler.sampleNow(app)
        }

        /** Starts the service if the system lets this moment start one. False (not an error) when it does not. */
        fun tryStart(context: Context): Boolean {
            val app = context.applicationContext
            if (!PhonePrefs.enabled(app)) return false
            if (running) return true
            return runCatching {
                ContextCompat.startForegroundService(app, Intent(app, PhoneListeningService::class.java))
                true
            }.getOrElse {
                // ForegroundServiceStartNotAllowedException (Android 12+), or a refused type: not an error, a limit.
                Log.i(TAG, "service not started from here: ${it.javaClass.simpleName}")
                false
            }
        }

        /** The watch (re)appeared: say where the headphones are right now. */
        fun resendState() { instance?.send?.trySend(Unit) }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context.applicationContext, PhoneListeningService::class.java))
        }

        /** Whether the phone could be asked for the Bluetooth class (the permission is held, or not needed). */
        fun bluetoothDetails(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        /**
         * "Not headphones" for the phone's output, asked from the watch: left out until it disconnects (the next
         * connection is detected again), and with [speaker] a Bluetooth device is remembered as a speaker. The next
         * state the phone sends says the headphones are gone.
         */
        suspend fun notHeadphones(context: Context, speaker: Boolean): NotHeadphonesOutcome = withContext(Dispatchers.Main) {
            val app = context.applicationContext
            PhonePrefs.loadSpeakerMemory(app)
            val w = instance?.watcher ?: HeadphoneWatcher(app)
            w.notHeadphones(speaker).also { Log.i(TAG, "not headphones (from the watch): $it") }
        }

        /**
         * "Lower to safe volume" for the phone's headphones, from the service when it runs and from a one-off
         * detector when it does not (the watch's command may arrive with the app closed). Always says what happened.
         */
        suspend fun lower(context: Context): LowerVolumeResult = withContext(Dispatchers.Main) {
            val app = context.applicationContext
            PhoneListeningRuntime.update { it.copy(lower = LowerUi.Working) }
            val w = instance?.watcher ?: HeadphoneWatcher(app)
            val r = w.lowerToSafe()
            PhoneListeningRuntime.update { it.copy(lower = LowerUi.Done(r.outcome, r.estimatedDba)) }
            LowerVolumeResult(UUID.randomUUID().toString(), r.outcome, r.index, r.estimatedDba)
        }
    }
}
