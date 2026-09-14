package com.example.voxara.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.voxara.R
import com.example.voxara.audio.AudioBurstSampler
import com.example.voxara.audio.HeuristicSceneClassifier
import com.example.voxara.complication.DoseComplicationService
import com.example.voxara.core.advice.AdviceGrammar
import com.example.voxara.core.dose.PEAK_CEILING_DBC
import com.example.voxara.core.dose.SampleState
import com.example.voxara.core.dose.energyAverage
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.risk.RiskStateMachine
import com.example.voxara.core.scene.SceneHysteresis
import com.example.voxara.data.AppMode
import com.example.voxara.data.ExposureRepository
import com.example.voxara.haptics.HapticConductor
import com.example.voxara.haptics.Pattern
import com.example.voxara.presentation.MainActivity
import com.example.voxara.tile.VoxaraTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * SAMPLING WITHOUT KILLING THE BATTERY.
 *
 *   IDLE      < 65      1.0 s / 60 s    1.7%   existence check only
 *   AMBIENT   65-79     1.0 s / 30 s    3.3%   Leq logged per minute
 *   ACCRUING  80-94     2.0 s / 10 s    20%    dose integrator active
 *   HAZARD    >= 95     continuous      100%   foreground + ongoing activity
 *   CONCERT   user      continuous      100%   time-boxed to 6 h
 *
 * The burst is treated as a statistically representative window of the gap around it, so a
 * burst is folded into the ledger as if it had lasted the whole period.
 */
class DosimeterService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.example.voxara.START"
        const val ACTION_STOP = "com.example.voxara.STOP"
        const val ACTION_CONCERT = "com.example.voxara.CONCERT"
        const val ACTION_URBAN = "com.example.voxara.URBAN"
        const val ACTION_SET_SIM = "com.example.voxara.SET_SIM"
        const val EXTRA_SIM_DBA = "sim_dba"

        private const val CHANNEL_ID = "voxara_dosimeter"
        private const val NOTIFICATION_ID = 4181
        private const val CONCERT_CAP_MS = 6L * 60 * 60 * 1000
        private const val CONCERT_EXIT_QUIET_MS = 20L * 60 * 1000
        private const val PERSIST_EVERY_MS = 30_000L
        private const val NOTIFY_EVERY_MS = 5_000L

        fun start(context: Context) = send(context, ACTION_START)

        fun stop(context: Context) {
            context.startService(
                Intent(context, DosimeterService::class.java).setAction(ACTION_STOP)
            )
        }

        fun send(context: Context, action: String, simDba: Double? = null) {
            val i = Intent(context, DosimeterService::class.java).setAction(action)
            simDba?.let { i.putExtra(EXTRA_SIM_DBA, it) }
            context.startForegroundService(i)
        }
    }

    private val sampler = AudioBurstSampler()
    private val classifier = HeuristicSceneClassifier()
    private val hysteresis = SceneHysteresis()
    private val riskMachine = RiskStateMachine()
    private lateinit var haptics: HapticConductor

    private var running = false
    private var burstIndex = 0
    private var lastPersistAt = 0L
    private var lastNotifyAt = 0L
    private var lastRisk: RiskState? = null
    private var quietSinceMs = 0L
    private var concertStartedAt = 0L
    private var currentDayEpoch = 0L
    private var hourly = FloatArray(24)

    /** Bench driver used when the mic is unavailable. Never presented as a measurement. */
    private var simTarget = 68.0
    private var simLevel = 68.0

    override fun onCreate() {
        super.onCreate()
        ExposureRepository.init(this)
        haptics = HapticConductor(this)
        createChannel()
        lifecycleScope.launch {
            val p = ExposureRepository.store.read()
            ExposureRepository.hydrate(p)
            riskMachine.dailyAlertCap = p.dailyAlertCap
            currentDayEpoch = todayEpoch()
            hourly = p.todayProfile.toFloatArray()
            if (p.dayEpoch != 0L && p.dayEpoch != currentDayEpoch) rollOverDay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                running = false
                ExposureRepository.update { it.copy(monitoring = false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
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

            ACTION_SET_SIM -> simTarget = intent.getDoubleExtra(EXTRA_SIM_DBA, simTarget)
        }

        startInForeground()
        if (!running) {
            running = true
            lifecycleScope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    // ------------------------------------------------------------- the adaptive ladder

    private suspend fun loop() {
        ExposureRepository.update { it.copy(monitoring = true) }
        var state = SampleState.IDLE
        while (running && lifecycleScope.isActive) {
            val repo = ExposureRepository.state.value
            val continuous = repo.mode == AppMode.CONCERT || state == SampleState.HAZARD
            val burstMs = if (continuous) 250L else state.burstMs
            val periodMs = if (continuous) 250L else state.periodMs

            val burst = capture(burstMs, repo.calibrationOffsetDb)
            val leq = burst?.leqDba ?: simulate(burstMs)
            val lmax = burst?.lmaxDba ?: leq
            val peak = burst?.peakDbc ?: (leq + 12.0)

            val engine = ExposureRepository.engine
            engine.accumulate(leq, periodMs / 1000.0)
            val impulse = engine.notePeak(peak)

            // Classifier: every burst in ACCRUING and above, every 5th burst in AMBIENT.
            burstIndex++
            val everyNth = if (repo.mode == AppMode.CONCERT) 1 else state.classifierEveryNthBurst
            if (burst != null && everyNth > 0 && burstIndex % everyNth == 0) {
                val (scene, confidence) = classifier.classify(burst)
                hysteresis.offer(scene, confidence)
            }

            val dose = engine.dosePercent
            val now = System.currentTimeMillis()
            val headroom = engine.headroomSeconds(leq)
            val risk = RiskState.of(leq, dose)

            logHour(leq)
            if (currentDayEpoch != 0L && todayEpoch() != currentDayEpoch) rollOverDay()
            currentDayEpoch = todayEpoch()

            ExposureRepository.update {
                it.copy(
                    dba = leq,
                    lmaxDba = lmax,
                    peakDbc = engine.peakDbc,
                    dosePercent = dose,
                    weeklyFraction = engine.weeklyFraction(),
                    twaDba = engine.twaDba(),
                    headroomSeconds = headroom,
                    risk = risk,
                    scene = hysteresis.current,
                    sampleState = state,
                    monitoring = true,
                    micGranted = burst != null,
                    simulated = burst == null,
                    todayProfile = hourly.toList(),
                    lastAdvice = AdviceGrammar.advise(
                        AdviceGrammar.State(leq, dose, headroom / 60.0, hysteresis.current, risk)
                    ),
                )
            }

            // Haptic first, screen second — the watch assumes you cannot hear it.
            if (impulse && engine.peakDbc >= PEAK_CEILING_DBC) haptics.fire(Pattern.IMPULSE)
            val event = riskMachine.update(
                leq, dose, now,
                respectCap = repo.mode != AppMode.CONCERT,
            )
            when (event) {
                RiskStateMachine.Event.DoseFull -> haptics.fire(Pattern.DOSE_FULL)
                RiskStateMachine.Event.ThresholdCrossed -> haptics.fire(Pattern.THRESHOLD)
                RiskStateMachine.Event.BackToSafe -> haptics.fire(Pattern.CALM)
                RiskStateMachine.Event.ImpulsePeak -> haptics.fire(Pattern.IMPULSE)
                null -> Unit
            }

            maybeNotify(risk, now)
            concertHousekeeping(leq, now)

            if (now - lastPersistAt > PERSIST_EVERY_MS) {
                lastPersistAt = now
                persist()
                VoxaraTileService.requestRefresh(this)
                DoseComplicationService.requestRefresh(this)
            }

            state = if (repo.mode == AppMode.CONCERT) SampleState.HAZARD else SampleState.of(leq)
            val sleep = (periodMs - burstMs).coerceAtLeast(0L)
            if (sleep > 0) delay(sleep)
        }
        ExposureRepository.update { it.copy(monitoring = false) }
        persist()
    }

    private suspend fun capture(burstMs: Long, offset: Double): AudioBurstSampler.Burst? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        return withContext(Dispatchers.IO) { sampler.capture(burstMs, offset) }
    }

    /** Smoothed bench level with the jitter character of a real room. */
    private suspend fun simulate(burstMs: Long): Double {
        delay(burstMs)
        simLevel += (simTarget - simLevel) * 0.35
        val jitter = (Random.nextDouble() - 0.5) * (0.8 + simLevel / 90.0)
        return (simLevel + jitter).coerceAtLeast(26.0)
    }

    // ------------------------------------------------------------- ledger housekeeping

    private fun logHour(leq: Double) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val prev = hourly[hour]
        hourly[hour] = if (prev <= 0f) leq.toFloat()
        else energyAverage(doubleArrayOf(prev.toDouble(), leq)).toFloat()
    }

    /** The daily ledger is written at local midnight. */
    private suspend fun rollOverDay() {
        ExposureRepository.store.rollOverDay(hourly.toList())
        hourly = FloatArray(24)
        ExposureRepository.engine.resetDay()
        riskMachine.resetDay()
        hysteresis.reset()
        val refreshed = ExposureRepository.store.read()
        ExposureRepository.update {
            it.copy(
                dosePercent = 0.0,
                twaDba = null,
                peakDbc = 0.0,
                todayProfile = List(24) { 0f },
                weekProfiles = refreshed.weekProfiles,
            )
        }
        currentDayEpoch = todayEpoch()
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

    private suspend fun persist() {
        if (!ExposureRepository.isInitialised()) return
        val e = ExposureRepository.engine
        ExposureRepository.store.saveLedger(
            dosePercent = e.dosePercent,
            weeklyPa2h = e.weeklyPa2h,
            peakDbc = e.peakDbc,
            dayEpoch = todayEpoch(),
            weekEpoch = todayEpoch() / 7,
            todayProfile = hourly.toList(),
            lastDba = ExposureRepository.state.value.dba,
        )
    }

    private fun todayEpoch(): Long {
        val cal = Calendar.getInstance()
        val offset = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)).toLong()
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() + offset)
    }

    // ------------------------------------------------------------- ongoing activity

    private fun startInForeground() {
        val notification = buildNotification(ExposureRepository.state.value.risk)
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        // Android 14 split the types: without the mic permission the service must not claim
        // the microphone type, so it runs under specialUse until the grant arrives.
        val type = if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        startForeground(NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_dosimeter),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun buildNotification(risk: RiskState): Notification {
        val s = ExposureRepository.state.value
        val content = "${s.dba.roundToInt()} dBA · ${s.dosePercent.roundToInt()}% dose"
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voxara_status)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending)

        // Ongoing activity: hazard state only — the wearer should not carry a chip all day.
        if (risk == RiskState.HAZARD || risk == RiskState.CRITICAL) {
            OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_voxara_status)
                .setTouchIntent(pending)
                .setStatus(Status.Builder().addTemplate(content).build())
                .build()
                .apply(applicationContext)
        }
        return builder.build()
    }

    private fun maybeNotify(risk: RiskState, now: Long) {
        if (risk == lastRisk && now - lastNotifyAt < NOTIFY_EVERY_MS) return
        lastRisk = risk
        lastNotifyAt = now
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(risk))
    }
}
