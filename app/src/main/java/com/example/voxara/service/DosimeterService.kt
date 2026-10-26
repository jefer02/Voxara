package com.example.voxara.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.voxara.BuildConfig
import com.example.voxara.R
import com.example.voxara.audio.AudioBurstSampler
import com.example.voxara.audio.DeviceAudioInfo
import com.example.voxara.audio.HeadphoneWatcher
import com.example.voxara.audio.SpeakerMemory
import com.example.voxara.audio.PowerPolicy
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.BtClassHint
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.headphones.categoryOf
import com.example.voxara.sync.PhoneListeningState
import com.example.voxara.core.headphones.EffectiveListening
import com.example.voxara.core.headphones.ListeningTracker
import com.example.voxara.core.headphones.WatchListening
import com.example.voxara.core.headphones.attribute
import com.example.voxara.core.headphones.Attribution
import com.example.voxara.core.headphones.effectiveListening
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.sync.PhoneLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.voxara.core.headphones.ListeningLayer
import com.example.voxara.core.headphones.ListeningLayerPolicy
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.audio.HeuristicSceneClassifier
import com.example.voxara.complication.DoseComplicationService
import com.example.voxara.core.advice.AdviceGrammar
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.headphones.HeadphoneProfile
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.inQuietHours
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.ledger.ExposureKind
import com.example.voxara.core.ledger.LedgerBook
import com.example.voxara.core.ledger.SlidingLeq
import com.example.voxara.core.ledger.localMinuteOfDay
import com.example.voxara.core.dose.PEAK_CEILING_DBC
import com.example.voxara.core.dose.SampleState
import com.example.voxara.core.dose.representedSeconds
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.risk.RiskStateMachine
import com.example.voxara.core.scene.SceneHysteresis
import com.example.voxara.core.situation.AutoModeDetector
import com.example.voxara.core.situation.ModeChangeHapticGate
import com.example.voxara.core.situation.Situation
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.data.ExposureDb
import com.example.voxara.data.ExposureState
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
import com.example.voxara.debug.DetectionLog
import com.example.voxara.debug.DetectionTrace
import com.example.voxara.core.scene.SceneModel
import com.example.voxara.sync.CompanionLink
import com.example.voxara.haptics.HapticConductor
import com.example.voxara.haptics.Pattern
import com.example.voxara.presentation.MainActivity
import com.example.voxara.complication.ListeningComplicationService
import com.example.voxara.tile.ListeningTileService
import com.example.voxara.tile.VoxaraTileService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * SAMPLING WITHOUT KILLING THE BATTERY.
 *
 *   IDLE      < 65      1.0 s / 60 s    1.7%   existence check only
 *   AMBIENT   65-79     1.0 s / 30 s    3.3%   Leq logged per minute
 *   ACCRUING  80-94     2.0 s / 10 s    20%    dose integrator active
 *   HAZARD    >= 95     continuous      100%   one open capture session
 *   CONCERT   user      continuous      100%   time-boxed to 6 h
 *
 * Concert means the wearer CHOSE Concert; an Auto-detected Concert keeps the level ladder above
 * (it adds Concert's alert behaviour, not microphone time).
 *
 * AUTO MODE: while the choice is Auto, each burst also carries scene features (computed inside
 * the sampler from the same PCM) and feeds [AutoModeDetector]. No extra capture: it classifies at
 * the ladder's own pace — once a minute when idle, twice a minute in AMBIENT, every 10 s when
 * accruing, and at most every 5 s while continuous. Slow on purpose: a situation changes over
 * minutes, and the detector needs several agreeing votes anyway.
 *
 * Each burst stands for the wall-clock time since the previous measured burst (capped, see
 * [representedSeconds]). A burst that could not be measured stands for nothing: no simulated or
 * silenced reading ever reaches the ledger.
 *
 * Lifecycle: started only from a visible surface (activity, tile tap -> activity) with the mic
 * permission granted, because Android 14+ refuses a microphone foreground service started from
 * the background. Not sticky: after a kill or a reboot the wearer resumes it (see BootReceiver).
 */
class DosimeterService : LifecycleService() {

    companion object {
        private const val TAG = "VoxaraDosimeter"
        const val ACTION_START = "com.example.voxara.START"
        const val ACTION_STOP = "com.example.voxara.STOP"
        private const val PERSIST_EVERY_MS = 30_000L
        /** The ongoing notification is rebuilt on a risk change, otherwise at most once a minute. */
        private const val NOTIFY_EVERY_MS = 60_000L
        private const val DAYS_REFRESH_MS = 60_000L
        private const val CONTINUOUS_BURST_MS = 250L
        private const val HEARTBEAT_EVERY_MS = 30_000L
        private const val ERROR_BACKOFF_MS = 2_000L
        /** Headphones mode looks this often while nothing is changing (a device event wakes it sooner)... */
        private const val LAYER_POLL_MS = 5_000L
        /** ...and this often while a change is waiting out its hold time. */
        private const val LAYER_PENDING_POLL_MS = 1_000L
        /** The phone link is looked at this often while monitoring (a cheap local Data Layer query). */
        private const val LINK_LOOK_MS = 30_000L
        /** While something plays to headphones the listening loop ticks this often (the alert's wait, the ledger hand-over)... */
        private const val LISTEN_TICK_MS = 5_000L
        /** ...and when nothing plays it waits for an event, or this long. */
        private const val LISTEN_IDLE_MS = 60_000L
        /** Finished listening spans reach the ledger at least this often while playing. */
        private const val DRAIN_EVERY_MS = 30_000L
        /** The level must stay above the recommended one this long before the alert fires. */
        private const val OVER_SAFE_DWELL_MS = 8_000L
        /** A room level older than this does not decide who owns a listening span. */
        private const val ROOM_FRESH_MS = 2L * 60_000

        fun hasMic(context: Context) = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context) = send(context, ACTION_START)

        fun stop(context: Context) {
            context.startService(
                Intent(context, DosimeterService::class.java).setAction(ACTION_STOP)
            )
        }

        /**
         * Without the mic permission there is nothing to measure, and a microphone-type
         * foreground service would be refused — so the service is not started at all.
         */
        fun send(context: Context, action: String) {
            if (!hasMic(context)) return
            runCatching {
                context.startForegroundService(
                    Intent(context, DosimeterService::class.java).setAction(action)
                )
            }.onFailure { Log.w(TAG, "could not start dosimeter", it) }
        }
    }

    private lateinit var sampler: AudioBurstSampler
    private val classifier = HeuristicSceneClassifier()
    private val hysteresis = SceneHysteresis()
    private val riskMachine = RiskStateMachine()
    private lateinit var haptics: HapticConductor

    private val ready = CompletableDeferred<Unit>()
    private var running = false
    private var burstIndex = 0
    private var lastPersistAt = 0L
    private var lastNotifyAt = 0L
    private var lastRisk: RiskState? = null
    private var quietSinceMs = 0L
    private var leavingConcert = false
    /** When this dosimeter run started: no mode-change haptic in its first minutes. */
    private var startedAtMs = 0L
    private val modeHaptics = ModeChangeHapticGate()
    /** Headphones mode has its own polite budget: a headphone switch must not use up the mode-change one. */
    private val layerHaptics = ModeChangeHapticGate()
    /** Headphones mode: detection with hysteresis (3 s in, 10 s out). Reads only; it never gates sampling or alerts. */
    private val layer = ListeningLayer()
    /**
     * Automatic situation detection (Auto only). Probabilities and levels in memory, nothing else.
     * Licence gate: without SCENE_MODEL_ENABLED (release default) only the Concert rule runs.
     */
    private val autoModes = AutoModeDetector(AutoModeDetector.Config(sceneModel = BuildConfig.SCENE_MODEL_ENABLED))
    private var currentDayEpoch = 0L
    private val sensitive: Boolean get() = ExposureRepository.state.value.sensitiveListener
    private val weeklyBudgetPa2h: Double
        get() = if (sensitive) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT

    /** The ambient minute ledger (rolling 7-day window, day history). */
    private lateinit var ambient: LedgerBook
    /** Sustained level for the ambient alert: a sliding window, never a single burst. */
    private val sustained = SlidingLeq(AlertDefaults.SUSTAINED_WINDOW_MIN * 60_000L)
    /** The headphone minute ledger — separate from ambient, never double-counted. */
    private lateinit var listening: LedgerBook
    private lateinit var headphones: HeadphoneWatcher
    /** What the watch's own ears got, in spans cut at every event (connect, play, pause, volume). */
    private val watchTracker = ListeningTracker()
    /** The room's last measured level, to decide which ledger owns a listening span (the double-count rule). */
    private var lastRoomDba: Double? = null
    private var lastRoomAtMs = 0L
    private var lastListenDrainAt = 0L
    /** When the estimate first went above the recommended level in this stretch (the alert waits a few seconds). */
    private var overSinceMs = 0L
    private var listeningDay = 0L
    private var listeningSeconds = 0.0
    private val alertMutex = Mutex()
    /** Sustained listening level for the "very loud listening" alert. */
    private val hpSustained = SlidingLeq(AlertDefaults.HEADPHONE_WINDOW_MIN * 60_000L)
    private lateinit var notifier: AlertNotifier
    private var alertMemory = AlertMemory()
    private var lastDaysAt = 0L
    private var seenResetGeneration = 0
    private var seenLedgerGeneration = 0
    private var seenWipeGeneration = 0
    private var lastHeartbeatAt = 0L
    private var userStopped = false

    override fun onCreate() {
        super.onCreate()
        ExposureRepository.init(this)
        sampler = AudioBurstSampler(this)
        val device = DeviceAudioInfo.read(this)
        ExposureRepository.update { it.copy(device = device) }
        Log.i(TAG, "device ${device.deviceKey} API ${device.sdkInt} unprocessed=${device.unprocessedSupported}")
        haptics = HapticConductor(this)
        notifier = AlertNotifier(this, haptics)
        ambient = LedgerBook(ExposureKind.AMBIENT, ExposureDb.get(this))
        listening = LedgerBook(ExposureKind.HEADPHONE, ExposureDb.get(this))
        ExposureRepository.liveLedgers.addAll(listOf(ambient, listening))
        seenWipeGeneration = ExposureRepository.wipeGeneration.get()
        headphones = HeadphoneWatcher(this)
        lifecycleScope.launch { SpeakerMemory.ensureLoaded() }
        Notifications.ensureChannel(this)
        seenResetGeneration = ExposureRepository.resetGeneration.get()
        lifecycleScope.launch {
            val store = ExposureRepository.store
            store.migrateSituation()
            // Close any day that ended while nothing was running, before the ledger is loaded.
            store.rollOverIfNeeded(localEpochDay(System.currentTimeMillis()))
            val p = store.read()
            ExposureRepository.hydrate(p)
            alertMemory = p.alertMemory
            currentDayEpoch = p.dayEpoch
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                ambient.prune(now)
                ambient.load(now)
                listening.load(now)
            }
            refreshDays(System.currentTimeMillis(), force = true)
            ready.complete(Unit)
        }
        // Settings and notification-action snoozes written elsewhere apply live.
        lifecycleScope.launch {
            ExposureRepository.store.flow.collect { p ->
                alertMemory = alertMemory.copy(snoozedUntil = p.alertMemory.snoozedUntil)
                headphones.usualCategory = p.usualHeadphones
                ExposureRepository.update {
                    it.copy(
                        alertSettings = p.alertSettings,
                        sensitiveListener = p.sensitiveListener,
                        usualHeadphones = p.usualHeadphones,
                        hapticPrefs = p.hapticPrefs,
                        snoozeMinutes = p.snoozeMinutes,
                        tonoEnabled = p.tonoEnabled,
                        aiConsent = p.aiConsent,
                        headphoneMode = p.headphoneMode,
                        listening = it.listening.withChoice(p.headphoneMode),
                        situationChoice = p.situationChoice,
                        detectedMode = if (p.situationChoice == SituationChoice.AUTO) it.detectedMode else null,
                        mode = Situation.resolve(p.situationChoice, it.detectedMode),
                        concertStartedAtMs = p.concertStartedAtMs,
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Must be called promptly on every startForegroundService, before any early return.
        if (!startInForeground()) {
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                running = false
                userStopped = true
                ExposureRepository.update {
                    it.copy(monitoring = false, monitoringStatus = MonitoringStatus.OFF)
                }
                lifecycleScope.launch {
                    withContext(NonCancellable) { persist() }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }

        }

        if (!running) {
            running = true
            userStopped = false
            lifecycleScope.launch { loop() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        ExposureRepository.serviceActive = false
        ExposureRepository.liveLedgers.clear()
        super.onDestroy()
    }

    // ------------------------------------------------------------- the adaptive ladder

    private suspend fun loop() {
        ready.await()
        startedAtMs = System.currentTimeMillis()
        ExposureRepository.serviceActive = true
        ExposureRepository.update {
            it.copy(monitoring = true, monitoringStatus = MonitoringStatus.RUNNING)
        }

        // Event-driven: a device appearing, playback starting or stopping and the volume moving all land in
        // onListeningEvent at once. The only timer runs while something is actually playing.
        headphones.onChanged = {
            ExposureRepository.layerWake.trySend(Unit)
            ExposureRepository.listeningWake.trySend(Unit)
        }
        headphones.start()
        layer.reset()
        watchTracker.reset()
        val layerJob = lifecycleScope.launch { layerLoop() }
        val listenJob = lifecycleScope.launch { listeningLoop() }
        // The phone link, looked at while monitoring: reconnecting turns into connected (and the catch-up runs)
        // by itself, or into "not connected for...", honestly.
        val linkJob = lifecycleScope.launch {
            while (running && lifecycleScope.isActive) {
                runCatching { PhoneLink.refresh(this@DosimeterService) }
                delay(LINK_LOOK_MS)
            }
        }
        var state = SampleState.IDLE
        var session: AudioBurstSampler.Session? = null
        var lastFoldAt: Long? = null
        var previousPeriodMs = state.periodMs

        try {
            while (running && lifecycleScope.isActive) {
                try {
                    syncWipes()
                    syncResets()
                    syncLedgerChanges()
                    beatIfDue()
                    val repo = ExposureRepository.state.value
                    val probe = ExposureRepository.calibrationProbe.value
                    // Only a CHOSEN Concert captures continuously (battery); see the class doc.
                    val manualConcert = repo.situationChoice == SituationChoice.CONCERT
                    val auto = repo.situationChoice == SituationChoice.AUTO && probe == null
                    if (!auto) forgetDetection()
                    val continuous =
                        probe != null || manualConcert || state == SampleState.HAZARD
                    val burstMs = if (continuous) CONTINUOUS_BURST_MS else state.burstMs
                    val periodMs = if (continuous) CONTINUOUS_BURST_MS else state.periodMs

                    // ---- capture: one open session while continuous, self-contained bursts otherwise
                    val burst: AudioBurstSampler.Burst? = if (continuous && hasMic(this)) {
                        if (session != null && probe != null && session.source != probe) {
                            session.close(); session = null
                        }
                        val s = session ?: withContext(Dispatchers.IO) { sampler.open(probe) }
                        session = s
                        val b = s?.let { withContext(Dispatchers.IO) { it.read(burstMs, offsetFor(it), sceneFeatures = auto) } }
                        if (b == null) { session?.close(); session = null }
                        b
                    } else {
                        session?.close(); session = null
                        capture(burstMs, sceneFeatures = auto)
                    }

                    // ---- debug calibration probe: readout only, never the ledger or the alerts
                    if (probe != null) {
                        lastFoldAt = null
                        if (burst != null) {
                            ExposureRepository.emitProbe(
                                ExposureRepository.ProbeReading(burst.source, burst.dbfsA, System.currentTimeMillis())
                            )
                        }
                        ExposureRepository.update {
                            it.copy(
                                dba = burst?.leqDba ?: it.dba,
                                micSource = burst?.source ?: it.micSource,
                                rawDbfsA = burst?.dbfsA,
                                rawDbfsZ = burst?.dbfsZ,
                                micGranted = burst != null,
                            )
                        }
                        if (burst == null) delay(500)
                        continue
                    }

                    // ---- unmeasured: mic busy, silenced or gone. Nothing AMBIENT is folded in, but
                    // headphone listening needs no microphone and is still counted.
                    if (burst == null) {
                        lastFoldAt = null
                        // Headphone listening needs no microphone: its own loop keeps counting it.
                        val nowMs = System.currentTimeMillis()
                        if (auto) applyDetection(autoModes.tick(nowMs), nowMs)
                        ExposureRepository.update { it.copy(micGranted = false, monitoring = true) }
                        delay(periodMs)
                        continue
                    }

                    val now = System.currentTimeMillis()
                    rollDayIfNeeded(now)

                    val nowElapsed = SystemClock.elapsedRealtime()
                    val represents = lastFoldAt?.let {
                        representedSeconds(nowElapsed - it, maxOf(periodMs, previousPeriodMs))
                    } ?: (burstMs / 1000.0)
                    lastFoldAt = nowElapsed
                    previousPeriodMs = periodMs

                    val leq = burst.leqDba
                    val engine = ExposureRepository.engine
                    val hp = headphones.current()
                    lastRoomDba = leq
                    lastRoomAtMs = now
                    // THE DOUBLE-COUNT RULE: while listening, this span belongs to exactly one ledger.
                    // Headphones mode is passed in only to show that it changes nothing here: the room
                    // is always measured and always reaches the alert window. The headphone side of the
                    // same rule is applied by the listening loop (foldListeningSpan) with the same inputs.
                    val plan = ListeningLayerPolicy.planBurst(
                        repo.listening, hp.listeningDba, leq, hp.category ?: HeadphoneCategory.UNKNOWN,
                    )
                    if (plan.foldAmbient) {
                        engine.accumulate(leq, represents)
                        withContext(Dispatchers.IO) { ambient.add(leq, represents, now) }
                    }
                    if (plan.feedSustained) sustained.add(leq, represents, now)
                    val impulse = engine.notePeak(burst.peakDbc)
                    val weeklyPercent = 100.0 * ambient.weeklyPa2h(now) / weeklyBudgetPa2h

                    // Automatic situation: this burst's features, no extra microphone time.
                    if (auto) detectSituation(burst, now, hp)

                    // Classifier: every burst in ACCRUING and above, every 5th burst in AMBIENT.
                    burstIndex++
                    val everyNth = if (manualConcert) 1 else state.classifierEveryNthBurst
                    if (everyNth > 0 && burstIndex % everyNth == 0) {
                        val (scene, confidence) = classifier.classify(burst)
                        hysteresis.offer(scene, confidence)
                    }

                    val dose = engine.dosePercent
                    val headroom = engine.headroomSeconds(leq)
                    val risk = RiskState.of(leq, dose)
                    val sustainedDba = sustained.value(now)

                    ExposureRepository.update {
                        it.copy(
                            dba = leq,
                            lmaxDba = burst.lmaxDba,
                            peakDbc = engine.peakDbc,
                            dosePercent = dose,
                            weeklyFraction = weeklyPercent / 100.0,
                            sustainedDba = sustainedDba,
                            twaDba = engine.twaDba(),
                            headroomSeconds = headroom,
                            risk = risk,
                            scene = hysteresis.current,
                            sampleState = state,
                            monitoring = true,
                            micGranted = true,
                            simulated = false,
                            micSource = burst.source,
                            rawDbfsA = burst.dbfsA,
                            rawDbfsZ = burst.dbfsZ,
                            lastAdvice = AdviceGrammar.advise(
                                AdviceGrammar.State(leq, dose, headroom / 60.0, hysteresis.current, risk)
                            ),
                        )
                    }

                    // Haptic first, screen second — the watch assumes you cannot hear it.
                    if (impulse && engine.peakDbc >= PEAK_CEILING_DBC) haptics.fire(Pattern.IMPULSE)
                    // The live state: an Auto-detected Concert applies to this very evaluation.
                    evaluateAlerts(now, sustainedDba, dose, weeklyPercent, ExposureRepository.state.value)
                    when (riskMachine.update(leq, dose, now)) {
                        // Relief is advisory-class: it honours "vibrate for advisories".
                        RiskStateMachine.Event.BackToSafe ->
                            if (repo.hapticPrefs.advisoryHaptics) haptics.fire(Pattern.CALM)
                        RiskStateMachine.Event.ImpulsePeak -> haptics.fire(Pattern.IMPULSE)
                        null -> Unit
                    }
                    refreshDays(now)

                    maybeNotify(risk, now)
                    concertHousekeeping(leq, now)

                    if (now - lastPersistAt > PERSIST_EVERY_MS) {
                        lastPersistAt = now
                        persist()
                        VoxaraTileService.requestRefresh(this)
                        DoseComplicationService.requestRefresh(this)
                        ListeningTileService.requestRefresh(this)
                        ListeningComplicationService.requestRefresh(this)
                    }

                    state = if (manualConcert) SampleState.HAZARD else SampleState.of(leq)
                    val sleep = (periodMs - burstMs).coerceAtLeast(0L)
                    if (sleep > 0) delay(sleep)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    // One bad iteration must never take monitoring down with it.
                    Log.e(TAG, "dosimeter iteration failed", t)
                    session?.close(); session = null
                    lastFoldAt = null
                    delay(ERROR_BACKOFF_MS)
                }
            }
        } finally {
            layerJob.cancel()
            listenJob.cancel()
            linkJob.cancel()
            headphones.onChanged = null
            headphones.stop()
            watchTracker.reset()
            session?.close()
            // Nothing is watching for headphones any more: Auto falls back to off, a manual choice stays.
            layer.reset()
            ExposureRepository.update { it.copy(listening = ListeningLayerState.OFF.withChoice(it.headphoneMode)) }
            ExposureRepository.serviceActive = false
            // Ended without the wearer asking: that is a pause, and the UI offers a resume.
            ExposureRepository.update {
                it.copy(
                    monitoring = false,
                    monitoringStatus = if (userStopped) MonitoringStatus.OFF else MonitoringStatus.PAUSED,
                )
            }
            withContext(NonCancellable) { persist() }
        }
    }

    private suspend fun capture(burstMs: Long, sceneFeatures: Boolean): AudioBurstSampler.Burst? {
        if (!hasMic(this)) return null
        return withContext(Dispatchers.IO) {
            sampler.capture(
                burstMs,
                offsetFor = { ExposureRepository.state.value.offsetFor(it) },
                sceneFeatures = sceneFeatures,
            )
        }
    }

    // ------------------------------------------------------------- headphones mode

    /**
     * HEADPHONES MODE. Looks at the watch's outputs and at the phone's last report, applies the
     * hysteresis and publishes the layer. Its own loop, because the burst ladder can be a minute
     * apart when it is quiet and putting headphones on should be noticed within seconds. It writes
     * one state field and plays one soft haptic; the measuring, the ledgers and the alerts never read
     * what it concludes ([ListeningLayerPolicy]).
     */
    private suspend fun layerLoop() {
        while (running && lifecycleScope.isActive) {
            val now = System.currentTimeMillis()
            // Fully automatic: the choice is always Auto (a manual On/Off from an older app is ignored).
            // "Not headphones": re-read the outputs now and leave without the exit hold (the wearer asked).
            if (ExposureRepository.layerDropRequested.getAndSet(false)) {
                headphones.refresh()
                layer.reset()
            }
            val phone = ExposureRepository.phoneListeningForLayer
            val phoneLive = phone != null && phone.connected && phone.isFresh(now)
            // An older phone app only reports spans (it cannot say when listening stopped): 16 minutes, as before.
            val phoneLegacy = phone == null && !ExposureRepository.phoneOutputIgnored &&
                ListeningLayer.phoneReports(now, ExposureRepository.phoneHeadphonesAtMs)
            val next = layer.update(
                now, HeadphoneModeChoice.AUTO,
                watchHeadphones = headphones.current().connected,
                phoneHeadphones = phoneLive || phoneLegacy,
            )
            publishLayer(next, now)
            val wait = if (layer.pending) LAYER_PENDING_POLL_MS else LAYER_POLL_MS
            withTimeoutOrNull(wait) { ExposureRepository.layerWake.receive() }
        }
    }

    /**
     * Publishes a changed layer. When it turned on or off by detection, the wrist gets one soft
     * double tick (HEADPHONES), inside ModeChangeHapticGate's limits and never near an alert. A manual
     * switch already gave its own tap under the wearer's finger and is not repeated here.
     */
    private fun publishLayer(next: ListeningLayerState, now: Long) {
        val before = ExposureRepository.state.value.listening
        if (next == before) return
        ExposureRepository.update { it.copy(listening = next) }
        if (next.active == before.active) return
        Log.i(TAG, "headphones mode ${if (next.active) "on" else "off"} (${next.source})")
        refreshNotification(now)
        VoxaraTileService.requestRefresh(this)
        ListeningTileService.requestRefresh(this)
        DoseComplicationService.requestRefresh(this)
        if (next.source == LayerSource.MANUAL || before.source == LayerSource.MANUAL) return
        val s = ExposureRepository.state.value
        val allowed = layerHaptics.tryPlay(
            ModeChangeHapticGate.Context(
                nowMs = now,
                localDay = localEpochDay(now),
                serviceStartedAtMs = startedAtMs,
                lastAlertHapticAtMs = haptics.lastAlertAtMs.takeIf { it > 0L },
                quietHours = inQuietHours(s.alertSettings, localMinuteOfDay(now)),
                powerSave = PowerPolicy.read(this).powerSaveMode,
                advisoryHaptics = s.hapticPrefs.advisoryHaptics,
            )
        )
        if (allowed) haptics.fire(Pattern.HEADPHONES)
    }

    // ------------------------------------------------------------- automatic situation

    /**
     * One vote from this burst's scene features (if it completed any and the detector wants one),
     * otherwise just the staleness check. The feature vectors are dropped right here.
     */
    private fun detectSituation(burst: AudioBurstSampler.Burst, now: Long, hp: HeadphoneWatcher.Snapshot) {
        val t0 = System.nanoTime()
        val vote = if (burst.sceneFeatures.isNotEmpty() && autoModes.wantsVote(now)) {
            autoModes.voteOf(now, burst.sceneFeatures, burst.leqDba)
        } else null
        val inferenceNs = System.nanoTime() - t0
        val decision = if (vote != null) {
            autoModes.offer(
                vote,
                AutoModeDetector.Context(headphonesPlaying = hp.playing, localMinuteOfDay = localMinuteOfDay(now)),
            )
        } else {
            autoModes.tick(now)
        }
        applyDetection(decision, now)
        if (vote != null && DetectionLog.available) logDetection(burst, vote, decision, inferenceNs, hp.playing)
    }

    /** Debug builds: one numbers-only row per classification (DetectionLog, src/debug). */
    private fun logDetection(
        burst: AudioBurstSampler.Burst,
        vote: AutoModeDetector.Vote,
        decision: AutoModeDetector.Decision,
        inferenceNs: Long,
        headphones: Boolean,
    ) {
        val classes = SceneModel.classes
        val top = vote.classProbabilities?.let { p ->
            p.indices.sortedByDescending { p[it] }.take(3).map { classes[it] to p[it] }
        } ?: emptyList()
        DetectionLog.record(
            this,
            DetectionTrace(
                atMs = vote.atMs,
                dba = burst.leqDba,
                featureMs = burst.sceneFeatureNanos / 1e6,
                vectors = burst.sceneFeatures.size,
                inferenceMs = inferenceNs / 1e6,
                topClasses = top,
                vote = decision.vote,
                musicLike = vote.musicLike,
                tonalityDb = vote.tonalityDb,
                vetoes = decision.vetoes.map { it.name },
                detected = decision.detected,
                mode = ExposureRepository.state.value.mode,
                confidence = decision.confidence,
                headphonesPlaying = headphones,
                sceneModelEnabled = BuildConfig.SCENE_MODEL_ENABLED,
            ),
        )
    }

    /**
     * Publishes a changed detection. When it changes the mode in force, the wearer may get the
     * polite MODE_CHANGE tap, only within ModeChangeHapticGate's limits.
     */
    private fun applyDetection(decision: AutoModeDetector.Decision, now: Long) {
        if (!decision.changed) return
        val before = ExposureRepository.state.value.mode
        ExposureRepository.update {
            if (it.situationChoice != SituationChoice.AUTO) it
            else it.copy(detectedMode = decision.detected, mode = Situation.resolve(it.situationChoice, decision.detected))
        }
        if (ExposureRepository.state.value.mode != before) playModeChange(now)
    }

    /** A fixed choice (or the calibration probe) needs no detection: forget it, silently. */
    private fun forgetDetection() {
        if (autoModes.detected == null && ExposureRepository.state.value.detectedMode == null) return
        autoModes.reset()
        ExposureRepository.update {
            it.copy(detectedMode = null, mode = Situation.resolve(it.situationChoice, null))
        }
    }

    /** Read live, so a calibration change in the UI applies to the very next burst. */
    private fun offsetFor(session: AudioBurstSampler.Session): Double =
        ExposureRepository.state.value.offsetFor(session.source)

    // ------------------------------------------------------------- ledger housekeeping

    /** Proof of life for the paused-state check, even while nothing is being folded in. */
    private suspend fun beatIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < HEARTBEAT_EVERY_MS) return
        lastHeartbeatAt = now
        runCatching { ExposureRepository.store.heartbeat() }
    }

    /**
     * "Delete all data" asked by the phone: the ledgers were wiped by [CompanionLink.deleteAll] (it cannot reach what
     * only this loop owns), so here go the alert windows, the open listening span, today's listening seconds and the
     * alert memory. Runs on the main thread like both loops, so it cannot interleave with them.
     */
    private fun syncWipes() {
        val g = ExposureRepository.wipeGeneration.get()
        if (g == seenWipeGeneration) return
        seenWipeGeneration = g
        sustained.clear()
        hpSustained.clear()
        watchTracker.reset()
        listeningSeconds = 0.0
        overSinceMs = 0L
        hpNowDba = null
        alertMemory = AlertMemory()
        lastDaysAt = 0L
    }

    /**
     * A manual reset from the UI ("start today over") also clears today's ambient minutes, so the
     * history, the daily dose and the rolling budget agree, and re-arms the daily alerts.
     */
    private suspend fun syncResets() {
        val g = ExposureRepository.resetGeneration.get()
        if (g != seenResetGeneration) {
            seenResetGeneration = g
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) { ambient.clearDay(currentDayEpoch, now) }
            sustained.clear()
            riskMachine.resetDay()
            alertMemory = alertMemory.copy(day = 0L)
            refreshDays(now, force = true)
        }
    }

    /**
     * Minutes arrived from the phone companion (and may have replaced ambient minutes under the
     * double-count rule): reload both ledgers and take today's dose from the ambient ledger.
     */
    private suspend fun syncLedgerChanges() {
        val g = ExposureRepository.ledgerGeneration.get()
        if (g == seenLedgerGeneration) return
        seenLedgerGeneration = g
        val now = System.currentTimeMillis()
        val todayDose = withContext(Dispatchers.IO) {
            ambient.load(now)
            listening.load(now)
            ambient.days(currentDayEpoch, now, days = 1).firstOrNull()?.nioshDosePercent
        }
        val e = ExposureRepository.engine
        if (todayDose != null) e.restore(todayDose, e.weeklyPa2h, e.peakDbc)
        refreshDays(now, force = true)
    }

    /**
     * The deterministic alert engine decides; the notifier delivers. Never depends on the AI.
     * The situation mode can only ADD here: Concert lifts the advisory cap and plays alerts
     * "Strong" ([Situation]); every threshold, cooldown and quiet-hours rule is the wearer's.
     */
    private suspend fun evaluateAlerts(
        now: Long,
        sustainedDba: Double?,
        dose: Double,
        weeklyPercent: Double,
        repo: ExposureState,
    ) = alertMutex.withLock {
        val settings: AlertSettings = Situation.alertSettings(repo.mode, repo.alertSettings)
        val (alerts, memory) = AlertEngine.evaluate(
            AlertInputs(
                nowMs = now,
                localDay = currentDayEpoch,
                localMinuteOfDay = localMinuteOfDay(now),
                sustainedAmbientDba = sustainedDba,
                dailyDosePercent = dose,
                weeklyAmbientPercent = weeklyPercent,
                weeklyHeadphonePercent = 100.0 * listening.weeklyPa2h(now) / weeklyBudgetPa2h,
                sustainedHeadphoneDba = hpSustained.value(now),
                headphoneNowDba = hpNowDba,
            ),
            settings,
            alertMemory,
        )
        // "Delete all" ran while this iteration was in flight: what it computed came from windows that are gone.
        // Nothing is delivered or remembered from them; the next iteration starts from clean ones (syncWipes).
        if (ExposureRepository.wipeGeneration.get() != seenWipeGeneration) return@withLock
        if (memory != alertMemory) {
            alertMemory = memory
            // Snoozes are owned by the notification actions: keep whatever is stored.
            ExposureRepository.store.updateAlertMemory { stored -> memory.copy(snoozedUntil = stored.snoozedUntil) }
        }
        // The in-view panel for "above the recommended level" (its haptic and notification come from the notifier).
        alerts.firstOrNull { it.kind == AlertKind.HEADPHONE_OVER_SAFE }?.let { a ->
            ExposureRepository.update {
                it.copy(headphoneAlert = HeadphoneAlert(a.level, a.value, now, it.headphoneSource), lowerVolume = LowerUi.Idle)
            }
        }
        notifier.deliver(alerts, Situation.hapticPrefs(repo.mode, repo.hapticPrefs))
    }

    /** Day history from both minute ledgers, refreshed about once a minute. */
    private suspend fun refreshDays(now: Long, force: Boolean = false) {
        if (!force && now - lastDaysAt < DAYS_REFRESH_MS) return
        lastDaysAt = now
        val today = if (currentDayEpoch != 0L) currentDayEpoch else localEpochDay(now)
        val (days, hpDays) = withContext(Dispatchers.IO) {
            ambient.days(today, now) to listening.days(today, now)
        }
        ExposureRepository.update { it.copy(days = days, headphoneDays = hpDays) }
    }

    // ------------------------------------------------------------- headphone listening (event-driven)

    /**
     * THE LISTENING LOOP. Not tied to the microphone's ladder: it looks when something CHANGES (headphones
     * connected or gone, playback started or stopped, the volume moved, the phone reported) and, while
     * something plays, every [LISTEN_TICK_MS] to carry the alert's short wait and hand finished spans to
     * the ledger. When nothing plays it sleeps until an event (or a minute, to refresh the weekly figure).
     */
    private suspend fun listeningLoop() {
        while (running && lifecycleScope.isActive) {
            try {
                onListening(System.currentTimeMillis())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "listening iteration failed", t)
            }
            val wait = if (ExposureRepository.state.value.headphoneListening) LISTEN_TICK_MS else LISTEN_IDLE_MS
            withTimeoutOrNull(wait) { ExposureRepository.listeningWake.receive() }
        }
    }

    private var lastListenCategory: HeadphoneCategory? = null

    /** The estimate the alert engine sees right now (null while nothing plays or the level has not yet stayed over). */
    private var hpNowDba: Double? = null

    private suspend fun onListening(now: Long) {
        syncWipes()
        val snap = headphones.refresh()
        val phone = ExposureRepository.phoneListeningForLayer
        val eff = effectiveListening(
            WatchListening(snap.connected, snap.playing, snap.listeningDba, snap.possibleSpeaker, snap.category),
            phone, now,
        )
        if (snap.category != null) lastListenCategory = snap.category

        // Today's listening seconds at the ears (any ledger), reset at midnight.
        val day = localEpochDay(now)
        if (day != listeningDay) { listeningDay = day; listeningSeconds = 0.0 }

        // The watch's own ears: cut the running span at this event, then hand finished spans to the ledger
        // every 30 s while playing, at once when playback stops, and whenever the level changes.
        val levelBefore = watchTracker.currentDba
        watchTracker.update(now, snap.listeningDba)
        val due = now - lastListenDrainAt >= DRAIN_EVERY_MS || snap.listeningDba == null || snap.listeningDba != levelBefore
        if (due) {
            lastListenDrainAt = now
            for (span in watchTracker.drain(now)) {
                listeningSeconds += span.seconds
                foldListeningSpan(
                    span,
                    snap.category ?: lastListenCategory ?: HeadphoneCategory.UNKNOWN,
                    snap.estimate?.confidence?.code ?: 0,
                    now,
                )
            }
        }
        publishHeadphones(snap, eff, phone, now)

        // The over-the-recommended-level alert: only after the level has stayed over for a few seconds (a slider sweep is not an alert).
        val level = eff.dba
        hpNowDba = when {
            level == null -> { overSinceMs = 0L; null }
            level > HeadphoneProfile.SAFE_DBA -> {
                if (overSinceMs == 0L) overSinceMs = now
                if (now - overSinceMs >= OVER_SAFE_DWELL_MS) level else null
            }
            else -> { overSinceMs = 0L; level }
        }
        if (hpNowDba != null) {
            val s = ExposureRepository.state.value
            evaluateAlerts(now, s.sustainedDba, s.dosePercent, s.weeklyFraction * 100, s)
        }
    }

    /**
     * One finished span of the watch's listening into the headphone ledger, IF the headphones own it: the
     * same double-count rule the burst loop applies to the room. A span the room owns is not lost: it is
     * already in the ambient ledger, and still counted in today's listening seconds.
     */
    private suspend fun foldListeningSpan(span: ListeningTracker.Span, category: HeadphoneCategory, confidence: Int, now: Long) {
        val room = lastRoomDba?.takeIf { now - lastRoomAtMs < ROOM_FRESH_MS }
        if (attribute(span.dba, room, category) != Attribution.HEADPHONE) return
        withContext(Dispatchers.IO) {
            listening.add(span.dba, span.seconds, span.endMs, category = category.code, confidence = confidence)
        }
        hpSustained.add(span.dba, span.seconds, span.endMs)
    }

    /** Headphone status for every surface. Category only — never a device name. */
    private fun publishHeadphones(
        snap: HeadphoneWatcher.Snapshot,
        eff: EffectiveListening,
        phone: PhoneListeningState?,
        now: Long,
    ) {
        if (!eff.playing) hpSustained.clear()
        val used = listening.weeklyPa2h(now)
        val estimate = when (eff.source) {
            LayerSource.WATCH -> snap.estimate
            LayerSource.PHONE -> phone?.estimate
            else -> null
        }
        val category = when (eff.source) {
            LayerSource.PHONE -> phone?.let { categoryOf(it.output, BtClassHint.UNKNOWN, ExposureRepository.state.value.usualHeadphones) }
            else -> snap.category
        }
        val over = estimate != null && estimate.dba > HeadphoneProfile.SAFE_DBA
        ExposureRepository.update {
            it.copy(
                headphoneCategory = category,
                headphoneListening = eff.playing,
                headphoneEstimate = estimate,
                headphonePossibleSpeaker = eff.possibleSpeaker,
                headphoneCanRememberSpeaker = eff.source == LayerSource.WATCH && snap.canRememberSpeaker,
                headphoneSource = eff.source,
                listeningSecondsToday = listeningSeconds,
                headphoneWeeklyPa2h = used,
                headphoneWeeklyFraction = used / weeklyBudgetPa2h,
                // Back under the recommended level (or nothing playing): the in-view alert has nothing left to say.
                headphoneAlert = if (over || it.lowerVolume !is LowerUi.Idle) it.headphoneAlert else null,
                lowerVolume = if (over || it.lowerVolume !is LowerUi.Idle) it.lowerVolume else LowerUi.Idle,
            )
        }
    }

    /**
     * Closes the day this service has been accruing into. The running ledger is persisted under
     * the OLD day first, then the store rolls atomically — a no-op when the midnight worker got
     * there first — and the engine is reloaded from the store, weekly budget included.
     */
    private suspend fun rollDayIfNeeded(nowMs: Long) {
        val today = localEpochDay(nowMs)
        if (currentDayEpoch == 0L || today <= currentDayEpoch) return
        val store = ExposureRepository.store
        persist()
        store.rollOverIfNeeded(today)
        riskMachine.resetDay()
        hysteresis.reset()
        val p = store.read()
        ExposureRepository.engine.restore(p.dosePercent, ExposureRepository.engine.weeklyPa2h, p.peakDbc)
        currentDayEpoch = today
        ExposureRepository.update {
            it.copy(
                dosePercent = p.dosePercent,
                twaDba = ExposureRepository.engine.twaDba(),
                peakDbc = p.peakDbc,
            )
        }
        withContext(Dispatchers.IO) { ambient.prune(nowMs) }
        refreshDays(nowMs, force = true)
    }

    private fun concertHousekeeping(leq: Double, now: Long) {
        val s = ExposureRepository.state.value
        // The 6 h cap and the quiet exit belong to a CHOSEN Concert; an Auto one exits by itself.
        if (s.situationChoice != SituationChoice.CONCERT || leavingConcert) {
            quietSinceMs = 0L
            return
        }
        val started = s.concertStartedAtMs
        if (started != null && now - started >= Situation.CONCERT_CAP_MS) {
            leaveConcert()
            return
        }
        if (leq < Situation.CONCERT_EXIT_BELOW_DBA) {
            if (quietSinceMs == 0L) quietSinceMs = now
            if (now - quietSinceMs >= Situation.CONCERT_EXIT_QUIET_MS) leaveConcert()
        } else {
            quietSinceMs = 0L
        }
    }

    /**
     * Concert ends on its own after 20 min below 80 dBA, or at the 6 h cap, and returns to what was
     * chosen before it. An automatic change: it may vibrate MODE_CHANGE, within the gate's limits.
     */
    private fun leaveConcert() {
        quietSinceMs = 0L
        leavingConcert = true
        lifecycleScope.launch {
            try {
                val next = ExposureRepository.store.leaveConcert() ?: return@launch
                ExposureRepository.update {
                    it.copy(situationChoice = next, mode = Situation.resolve(next, it.detectedMode), concertStartedAtMs = null)
                }
                playModeChange(System.currentTimeMillis())
                // The phone follows even when no watch screen is open to publish it.
                CompanionLink.publishSettings(this@DosimeterService, ExposureRepository.store.read())
            } finally {
                leavingConcert = false
            }
        }
    }

    private fun playModeChange(now: Long) {
        val s = ExposureRepository.state.value
        val allowed = modeHaptics.tryPlay(
            ModeChangeHapticGate.Context(
                nowMs = now,
                localDay = localEpochDay(now),
                serviceStartedAtMs = startedAtMs,
                lastAlertHapticAtMs = haptics.lastAlertAtMs.takeIf { it > 0L },
                quietHours = inQuietHours(s.alertSettings, localMinuteOfDay(now)),
                powerSave = PowerPolicy.read(this).powerSaveMode,
                advisoryHaptics = s.hapticPrefs.advisoryHaptics,
            )
        )
        if (allowed) haptics.fire(Pattern.MODE_CHANGE)
    }

    /**
     * Stamped with the day this service has been accruing into — never "today" — so a write
     * racing midnight is refused by the store instead of landing on the new day.
     */
    private suspend fun persist() {
        if (!ExposureRepository.isInitialised() || currentDayEpoch == 0L) return
        syncResets()
        val e = ExposureRepository.engine
        val now = System.currentTimeMillis()
        val (weekly, hpWeekly) = withContext(Dispatchers.IO) {
            ambient.flush()
            listening.flush()
            ambient.weeklyPa2h(now) to listening.weeklyPa2h(now)
        }
        ExposureRepository.store.saveLedger(
            dosePercent = e.dosePercent,
            weeklyPa2h = weekly,
            headphoneWeeklyPa2h = hpWeekly,
            peakDbc = e.peakDbc,
            dayEpoch = currentDayEpoch,
            lastDba = ExposureRepository.state.value.dba,
        )
    }

    // ------------------------------------------------------------- ongoing activity

    /** @return false when the platform refuses the microphone foreground service. */
    private fun startInForeground(): Boolean {
        if (!hasMic(this)) return false
        return try {
            startForeground(
                Notifications.DOSIMETER_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            true
        } catch (t: RuntimeException) {
            // Android 14+: refused when started while the app is not visible.
            Log.w(TAG, "microphone foreground service refused", t)
            false
        }
    }

    /** The wearer's chosen language, re-read per use: the setting can change under us. */
    private fun strings() = LocaleStore.localized(this)

    private fun buildNotification(): Notification {
        val s = ExposureRepository.state.value
        val res = strings()
        // Headphones mode says so, and that the room is still being measured.
        val content = if (s.listening.active) res.getString(
            R.string.notification_content_headphones, s.dba.roundToInt(), s.dosePercent.roundToInt(),
        ) else res.getString(
            R.string.notification_content, s.dba.roundToInt(), s.dosePercent.roundToInt(),
        )
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voxara_status)
            .setContentTitle(res.getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending)

        // Ongoing activity for as long as the mic is being sampled: the wearer can always see
        // that monitoring is on and get back to it from the watch face.
        OngoingActivity.Builder(applicationContext, Notifications.DOSIMETER_ID, builder)
            .setStaticIcon(R.drawable.ic_voxara_status)
            .setTouchIntent(pending)
            .setStatus(Status.Builder().addTemplate(content).build())
            .build()
            .apply(applicationContext)
        return builder.build()
    }

    private fun maybeNotify(risk: RiskState, now: Long) {
        if (risk == lastRisk && now - lastNotifyAt < NOTIFY_EVERY_MS) return
        lastRisk = risk
        refreshNotification(now)
    }

    private fun refreshNotification(now: Long) {
        lastNotifyAt = now
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.DOSIMETER_ID, buildNotification())
    }
}
