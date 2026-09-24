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
import com.example.voxara.R
import com.example.voxara.audio.AudioBurstSampler
import com.example.voxara.audio.DeviceAudioInfo
import com.example.voxara.audio.HeadphoneMonitor
import com.example.voxara.core.headphones.Attribution
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.attribute
import com.example.voxara.audio.HeuristicSceneClassifier
import com.example.voxara.complication.DoseComplicationService
import com.example.voxara.core.advice.AdviceGrammar
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertSettings
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
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureDb
import com.example.voxara.data.ExposureState
import com.example.voxara.data.ExposureRepository
import com.example.voxara.data.LocaleStore
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
        const val ACTION_CONCERT = "com.example.voxara.CONCERT"
        const val ACTION_URBAN = "com.example.voxara.URBAN"

        private const val CONCERT_CAP_MS = 6L * 60 * 60 * 1000
        private const val CONCERT_EXIT_QUIET_MS = 20L * 60 * 1000
        private const val PERSIST_EVERY_MS = 30_000L
        /** The ongoing notification is rebuilt on a risk change, otherwise at most once a minute. */
        private const val NOTIFY_EVERY_MS = 60_000L
        private const val DAYS_REFRESH_MS = 60_000L
        private const val CONTINUOUS_BURST_MS = 250L
        private const val HEARTBEAT_EVERY_MS = 30_000L
        private const val ERROR_BACKOFF_MS = 2_000L

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
    private var concertStartedAt = 0L
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
    private lateinit var headphones: HeadphoneMonitor
    /** Sustained listening level for the "very loud listening" alert. */
    private val hpSustained = SlidingLeq(AlertDefaults.HEADPHONE_WINDOW_MIN * 60_000L)
    private lateinit var notifier: AlertNotifier
    private var alertMemory = AlertMemory()
    private var lastDaysAt = 0L
    private var seenResetGeneration = 0
    private var seenLedgerGeneration = 0
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
        headphones = HeadphoneMonitor(this)
        Notifications.ensureChannel(this)
        seenResetGeneration = ExposureRepository.resetGeneration.get()
        lifecycleScope.launch {
            val store = ExposureRepository.store
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

            ACTION_CONCERT -> {
                concertStartedAt = System.currentTimeMillis()
                ExposureRepository.update {
                    it.copy(
                        mode = AppMode.CONCERT,
                        concertEndsAtMs = concertStartedAt + CONCERT_CAP_MS,
                    )
                }
                lifecycleScope.launch { ExposureRepository.store.setMode(AppMode.CONCERT.name) }
            }

            ACTION_URBAN -> dropToUrban()
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
        super.onDestroy()
    }

    // ------------------------------------------------------------- the adaptive ladder

    private suspend fun loop() {
        ready.await()
        ExposureRepository.serviceActive = true
        ExposureRepository.update {
            it.copy(monitoring = true, monitoringStatus = MonitoringStatus.RUNNING)
        }

        headphones.start()
        var state = SampleState.IDLE
        var session: AudioBurstSampler.Session? = null
        var lastFoldAt: Long? = null
        var previousPeriodMs = state.periodMs

        try {
            while (running && lifecycleScope.isActive) {
                try {
                    syncResets()
                    syncLedgerChanges()
                    beatIfDue()
                    val repo = ExposureRepository.state.value
                    val probe = ExposureRepository.calibrationProbe.value
                    val continuous =
                        probe != null || repo.mode == AppMode.CONCERT || state == SampleState.HAZARD
                    val burstMs = if (continuous) CONTINUOUS_BURST_MS else state.burstMs
                    val periodMs = if (continuous) CONTINUOUS_BURST_MS else state.periodMs

                    // ---- capture: one open session while continuous, self-contained bursts otherwise
                    val burst: AudioBurstSampler.Burst? = if (continuous && hasMic(this)) {
                        if (session != null && probe != null && session.source != probe) {
                            session.close(); session = null
                        }
                        val s = session ?: withContext(Dispatchers.IO) { sampler.open(probe) }
                        session = s
                        val b = s?.let { withContext(Dispatchers.IO) { it.read(burstMs, offsetFor(it)) } }
                        if (b == null) { session?.close(); session = null }
                        b
                    } else {
                        session?.close(); session = null
                        capture(burstMs)
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
                        val nowMs = System.currentTimeMillis()
                        val hp = headphones.snapshot(nowMs)
                        hp.listeningDba?.let { foldListening(it, periodMs / 1000.0, nowMs, hp) }
                        publishHeadphones(hp, nowMs)
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
                    val hp = headphones.snapshot(now)
                    // THE DOUBLE-COUNT RULE: while listening, this span belongs to exactly one ledger.
                    when (attribute(hp.listeningDba, leq, hp.category ?: HeadphoneCategory.UNKNOWN)) {
                        Attribution.AMBIENT -> {
                            engine.accumulate(leq, represents)
                            withContext(Dispatchers.IO) { ambient.add(leq, represents, now) }
                            sustained.add(leq, represents, now)
                        }
                        Attribution.HEADPHONE -> foldListening(hp.listeningDba!!, represents, now, hp)
                    }
                    val impulse = engine.notePeak(burst.peakDbc)
                    publishHeadphones(hp, now)
                    val weeklyPercent = 100.0 * ambient.weeklyPa2h(now) / weeklyBudgetPa2h

                    // Classifier: every burst in ACCRUING and above, every 5th burst in AMBIENT.
                    burstIndex++
                    val everyNth = if (repo.mode == AppMode.CONCERT) 1 else state.classifierEveryNthBurst
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
                    evaluateAlerts(now, sustainedDba, dose, weeklyPercent, repo)
                    when (riskMachine.update(leq, dose, now)) {
                        RiskStateMachine.Event.BackToSafe -> haptics.fire(Pattern.CALM)
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

                    state = if (repo.mode == AppMode.CONCERT) SampleState.HAZARD else SampleState.of(leq)
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
            headphones.stop()
            session?.close()
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

    private suspend fun capture(burstMs: Long): AudioBurstSampler.Burst? {
        if (!hasMic(this)) return null
        return withContext(Dispatchers.IO) {
            sampler.capture(burstMs, offsetFor = { ExposureRepository.state.value.offsetFor(it) })
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
     * In Concert mode the wearer expects interruptions, so the advisory cap is lifted.
     */
    private suspend fun evaluateAlerts(
        now: Long,
        sustainedDba: Double?,
        dose: Double,
        weeklyPercent: Double,
        repo: ExposureState,
    ) {
        val settings: AlertSettings = repo.alertSettings.let {
            if (repo.mode == AppMode.CONCERT) it.copy(dailyCap = Int.MAX_VALUE) else it
        }
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
            ),
            settings,
            alertMemory,
        )
        if (memory != alertMemory) {
            alertMemory = memory
            // Snoozes are owned by the notification actions: keep whatever is stored.
            ExposureRepository.store.updateAlertMemory { stored -> memory.copy(snoozedUntil = stored.snoozedUntil) }
        }
        notifier.deliver(alerts)
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

    /** Folds an estimated listening span into the headphone ledger. */
    private suspend fun foldListening(dba: Double, seconds: Double, now: Long, hp: HeadphoneMonitor.Snapshot) {
        withContext(Dispatchers.IO) {
            listening.add(
                dba, seconds, now,
                category = hp.category?.code ?: 0,
                confidence = hp.estimate?.confidence?.code ?: 0,
            )
        }
        hpSustained.add(dba, seconds, now)
    }

    /** Headphone status for every surface. Category only — never a device name. */
    private fun publishHeadphones(hp: HeadphoneMonitor.Snapshot, now: Long) {
        if (!hp.playing) hpSustained.clear()
        val used = listening.weeklyPa2h(now)
        ExposureRepository.update {
            it.copy(
                headphoneCategory = hp.category,
                headphoneListening = hp.playing,
                headphoneEstimate = hp.estimate,
                headphoneWeeklyPa2h = used,
                headphoneWeeklyFraction = used / weeklyBudgetPa2h,
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
        if (s.mode != AppMode.CONCERT) {
            quietSinceMs = 0L
            return
        }
        if (concertStartedAt != 0L && now - concertStartedAt >= CONCERT_CAP_MS) {
            dropToUrban()
            return
        }
        if (leq < 80.0) {
            if (quietSinceMs == 0L) quietSinceMs = now
            if (now - quietSinceMs >= CONCERT_EXIT_QUIET_MS) dropToUrban()
        } else {
            quietSinceMs = 0L
        }
    }

    /** Concert mode auto-drops to Urban after 20 min below 80 dBA, or at the 6 h cap. */
    private fun dropToUrban() {
        quietSinceMs = 0L
        concertStartedAt = 0L
        ExposureRepository.update { it.copy(mode = AppMode.URBAN, concertEndsAtMs = 0L) }
        lifecycleScope.launch { ExposureRepository.store.setMode(AppMode.URBAN.name) }
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
        val content = res.getString(
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
        lastNotifyAt = now
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.DOSIMETER_ID, buildNotification())
    }
}
