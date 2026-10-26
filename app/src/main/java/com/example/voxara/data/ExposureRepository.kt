package com.example.voxara.data

import android.content.Context
import com.example.voxara.core.advice.Advice
import com.example.voxara.audio.DeviceAudioInfo
import com.example.voxara.audio.PowerPolicy
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.alerts.HapticPrefs
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.headphones.LowerUi
import com.example.voxara.link.LinkHealth
import com.example.voxara.sync.LowerOutcome
import com.example.voxara.sync.PhoneListeningState
import com.example.voxara.core.headphones.ListeningLayerState
import com.example.voxara.core.headphones.ListeningEstimate
import com.example.voxara.core.ledger.DaySummary
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.appliesTo
import com.example.voxara.core.monitoring.MonitoringStatus
import com.example.voxara.core.dose.DOSE_THRESHOLD_DBA
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.dose.NoiseDoseEngine
import com.example.voxara.core.dose.SampleState
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.scene.Scene
import com.example.voxara.core.situation.Situation
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.core.situation.SituationMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/**
 * The scenario chips of the dossier's live gauge, kept as a bench driver for mic-less devices.
 * Debug builds only, and display only: a scenario never reaches the dose ledger.
 */
enum class Scenario(val dba: Double) {
    OFFICE(52.0),
    CAFE(71.0),
    TRAFFIC(86.0),
    CLUB(104.0),
    JACKHAMMER(113.0),
}

data class ExposureState(
    val dba: Double = 0.0,
    val lmaxDba: Double = 0.0,
    val peakDbc: Double = 0.0,
    val dosePercent: Double = 0.0,
    val weeklyFraction: Double = 0.0,
    val twaDba: Double? = null,
    val headroomSeconds: Double = Double.POSITIVE_INFINITY,
    val risk: RiskState = RiskState.CALM,
    val scene: Scene = Scene.UNKNOWN,
    val sampleState: SampleState = SampleState.IDLE,
    /** The situation mode in force (Auto already resolved). Never changes an alert threshold. */
    val mode: SituationMode = SituationMode.DEFAULT,
    /** The wearer's choice: Auto or a fixed mode (synced with the phone). */
    val situationChoice: SituationChoice = SituationChoice.DEFAULT,
    /**
     * What automatic detection concluded while the choice is Auto; null when it is not sure yet
     * (Auto is then Street) or the choice is fixed. In memory only, never persisted.
     */
    val detectedMode: SituationMode? = null,
    val monitoring: Boolean = false,
    val micGranted: Boolean = false,
    val simulated: Boolean = false,
    /** When Concert was chosen (for its 6 h cap); null when Concert is not the choice. */
    val concertStartedAtMs: Long? = null,
    val calibrationOffsetDb: Double = 0.0,
    /** The last 7 local days from the minute ledger, index 0 = today. */
    val days: List<DaySummary> = emptyList(),
    /** Sliding ambient Leq the level alert uses (null until the window has enough data). */
    val sustainedDba: Double? = null,
    val alertSettings: AlertSettings = AlertSettings(),
    val lastAdvice: Advice? = null,
    val sensitiveListener: Boolean = false,
    /** Capture path of the last real burst, or null before the first one. */
    val micSource: MicSource? = null,
    /** Raw levels of the last burst, before any offset — the calibration readout. */
    val rawDbfsA: Double? = null,
    val rawDbfsZ: Double? = null,
    /** Guided calibrations per capture path (possibly made on another device). */
    val calibrations: Map<MicSource, CalibrationRecord> = emptyMap(),
    /** What the watch reports about itself; null until read. */
    val device: DeviceAudioInfo? = null,
    val power: PowerPolicy? = null,
    val monitoringStatus: MonitoringStatus = MonitoringStatus.OFF,
    /** Headphones connected to THIS watch (debounced), by category only. */
    val headphoneCategory: HeadphoneCategory? = null,
    /** Media playing to those headphones. */
    val headphoneListening: Boolean = false,
    /** Estimated listening level (never measured, never from the microphone). */
    val headphoneEstimate: ListeningEstimate? = null,
    /** Rolling 7-day headphone energy, Pa^2*h, and as a fraction of the weekly allowance. */
    val headphoneWeeklyPa2h: Double = 0.0,
    val headphoneWeeklyFraction: Double = 0.0,
    /** Headphone ledger history, index 0 = today. */
    val headphoneDays: List<DaySummary> = emptyList(),
    /** A Bluetooth output whose class could not be read: counted as headphones, but it may be a speaker. */
    val headphonePossibleSpeaker: Boolean = false,
    /** The output in use can be remembered as a speaker ("Not headphones": a Bluetooth device with a full address). */
    val headphoneCanRememberSpeaker: Boolean = false,
    /** How many outputs the wearer remembered as speakers ("Forget speakers" undoes it). */
    val rememberedSpeakers: Int = 0,
    /** Whose headphones the live estimate comes from: this watch or the phone ([LayerSource.NONE] when none). */
    val headphoneSource: LayerSource = LayerSource.NONE,
    /** Seconds of playback at the ears today (any ledger), so "listening minutes" is honest even when the room owned the dose. */
    val listeningSecondsToday: Double = 0.0,
    /** The over-the-recommended-level alert in force, for its in-view panel; null when there is none. */
    val headphoneAlert: HeadphoneAlert? = null,
    val lowerVolume: LowerUi = LowerUi.Idle,
    /** Honest state of the link to the phone: connected, reconnecting, or disconnected since... */
    val phoneLink: LinkHealth = LinkHealth.Unknown,
    /** "My headphones are usually…" (earbuds / over-ear), or null. */
    val usualHeadphones: HeadphoneCategory? = null,
    /** Headphones mode: the wearer's choice (Auto follows the detection). */
    val headphoneMode: HeadphoneModeChoice = HeadphoneModeChoice.DEFAULT,
    /**
     * The listening layer over the situation mode (headphones mode): on or off, and why. It only changes
     * what is shown: measuring, the ledgers and the alerts do not read it ([com.example.voxara.core.headphones.ListeningLayerPolicy]).
     */
    val listening: ListeningLayerState = ListeningLayerState.OFF,
    /** When the last alert was delivered (0 = none this run): a transition never plays over an alert. */
    val lastAlertAtMs: Long = 0L,
    /** Explicit opt-in to cloud AI (Tono); OFF by default. */
    val aiConsent: Boolean = false,
    /** Whether this watch exposes the system battery-optimisation list. */
    val batterySettingsAvailable: Boolean = false,
    /** Haptic preferences (warnings and limits always vibrate regardless; see AlertPolicy). */
    val hapticPrefs: HapticPrefs = HapticPrefs(),
    /** Snooze length for alert notifications, 15-120 min. */
    val snoozeMinutes: Int = AlertPolicy.SNOOZE_DEFAULT_MINUTES,
    /** Tono shown on the watch; off hides it and keeps voice commands. */
    val tonoEnabled: Boolean = true,
) {
    val headroomMinutes: Double get() = headroomSeconds / 60.0

    /** The capture path in use, or the one the device would pick. */
    val activeSource: MicSource
        get() = micSource ?: if (device?.unprocessedSupported == true) MicSource.UNPROCESSED
        else MicSource.VOICE_RECOGNITION

    /** The calibration that applies on THIS watch for [source], or null (provisional offset). */
    fun calibrationFor(source: MicSource): CalibrationRecord? =
        calibrations[source]?.takeIf { rec -> device == null || rec.appliesTo(device.deviceKey) }

    /** Readings are an "uncalibrated estimate" until the active path has a calibration. */
    val calibrated: Boolean get() = calibrationFor(activeSource) != null

    /** Calibrated (or provisional) source offset plus the wearer's fine trim. */
    fun offsetFor(source: MicSource): Double =
        (calibrationFor(source)?.offsetDb ?: source.provisionalOffsetDb) + calibrationOffsetDb
    val accruing: Boolean get() = dba >= DOSE_THRESHOLD_DBA
}

/**
 * The single source of truth the app, the tile, the complication and the ongoing activity all
 * read. The dose engine itself lives inside — nothing else is allowed to mutate the ledger.
 */
object ExposureRepository {

    private val _state = MutableStateFlow(ExposureState())
    val state: StateFlow<ExposureState> = _state.asStateFlow()

    lateinit var store: VoxaraStore
        private set

    val engine = NoiseDoseEngine()

    @Volatile private var initialised = false

    /**
     * True while the dosimeter loop owns the in-memory ledger. A cold-start hydrate from disk
     * must not overwrite it: the persisted copy can be up to 30 s behind.
     */
    @Volatile var serviceActive = false

    /**
     * When an OLDER phone app last reported a listening span (its end, ms), 0 = never. Only used while no
     * phone has ever sent a live state ([phoneListening]): that older app cannot say when listening stopped,
     * so it counts for 16 minutes.
     */
    @Volatile var phoneHeadphonesAtMs = 0L
        set(value) {
            field = value
            layerWake.trySend(Unit)
        }

    /**
     * The phone's headphones, live (MessageClient) and durable (the state item). Newest sequence wins; an
     * older or repeated one is dropped. Wakes headphones mode's loop at once.
     */
    @Volatile var phoneListening: PhoneListeningState? = null
        private set

    /**
     * "Not headphones" for the PHONE's output: while true its reports count as "no headphones" here, and its spans
     * and alerts are not taken as headphones. Cleared as soon as the phone says its headphones are gone, so the next
     * connection is detected again (the phone also leaves the output out itself when it gets the command).
     */
    @Volatile var phoneOutputIgnored = false
        set(value) {
            field = value
            layerWake.trySend(Unit)
            listeningWake.trySend(Unit)
        }

    /** The phone's live state as headphones mode should see it: null while the wearer said it is not headphones. */
    val phoneListeningForLayer: PhoneListeningState? get() = phoneListening.takeUnless { phoneOutputIgnored }

    /**
     * Set by "Not headphones": the dosimeter's layer loop re-reads the outputs and drops the layer at once instead
     * of waiting out the 10 s exit hysteresis (the wearer asked; a flapping link did not).
     */
    val layerDropRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The latest live state (the sender's [PhoneListeningState.sequence] is the order); false when it was stale news. */
    fun onPhoneListening(state: PhoneListeningState): Boolean {
        val cur = phoneListening
        if (cur != null && state.sequence <= cur.sequence && state.atMs <= cur.atMs) return false
        // The phone says its headphones are gone: an output marked "not headphones" there is forgotten.
        if (!state.connected) phoneOutputIgnored = false
        phoneListening = state.copy(receivedAtMs = System.currentTimeMillis())
        layerWake.trySend(Unit)
        listeningWake.trySend(Unit)
        return true
    }

    /** Wakes the dosimeter's listening loop: a phone state changed, so the live estimate and alerts must look now. */
    val listeningWake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Wakes headphones mode's loop in the dosimeter: a device appeared or went, the phone reported, or the wearer
     * changed the choice. Without it the loop looks every few seconds on its own.
     */
    val layerWake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Bumped when minutes were written from outside the dosimeter (phone companion sync), so it
     * reloads its ledgers and re-derives today's dose from them.
     */
    val ledgerGeneration = AtomicInteger(0)

    /** Bumped by a manual dose reset so the service drops its in-memory hourly profile too. */
    val resetGeneration = AtomicInteger(0)

    /**
     * The running dosimeter's two ledgers (empty while it is stopped). "Delete all" wipes them in place, under their
     * own locks, so the minute in progress and the spans still on their way cannot write the deleted data back.
     */
    val liveLedgers = java.util.concurrent.CopyOnWriteArrayList<com.example.voxara.core.ledger.LedgerBook>()

    /** When "delete all" last ran on this watch (0 = never, this process): queued history writes older than it are dropped. */
    @Volatile var wipedAtMs = 0L

    /** Bumped by "delete all": the service also forgets what it holds in memory (alert windows, listening seconds, open span). */
    val wipeGeneration = AtomicInteger(0)

    /**
     * Debug calibration probe: when set, the service captures continuously through this source,
     * publishes raw dBFS, and keeps the readings OUT of the ledger and the alert engine.
     */
    val calibrationProbe = MutableStateFlow<MicSource?>(null)

    /** One reading per probe burst (not conflated), for the guided calibration's 10 s window. */
    data class ProbeReading(val source: MicSource, val dbfsA: Double, val atMs: Long)

    private val _probeReadings = MutableSharedFlow<ProbeReading>(extraBufferCapacity = 64)
    val probeReadings: SharedFlow<ProbeReading> = _probeReadings.asSharedFlow()

    fun emitProbe(r: ProbeReading) { _probeReadings.tryEmit(r) }

    fun init(context: Context) {
        if (initialised) return
        store = VoxaraStore(context.applicationContext)
        initialised = true
    }

    fun isInitialised() = initialised

    /**
     * Settings written from anywhere (this UI, the phone companion, a notification) applied to the
     * live state without touching the dose figures.
     */
    fun applySettings(p: VoxaraStore.Persisted) = _state.update {
        it.copy(
            alertSettings = p.alertSettings,
            sensitiveListener = p.sensitiveListener,
            usualHeadphones = p.usualHeadphones,
            aiConsent = p.aiConsent,
            hapticPrefs = p.hapticPrefs,
            snoozeMinutes = p.snoozeMinutes,
            tonoEnabled = p.tonoEnabled,
            headphoneMode = p.headphoneMode,
            listening = it.listening.withChoice(p.headphoneMode),
            situationChoice = p.situationChoice,
            detectedMode = if (p.situationChoice == SituationChoice.AUTO) it.detectedMode else null,
            mode = Situation.resolve(p.situationChoice, it.detectedMode),
            concertStartedAtMs = p.concertStartedAtMs,
            weeklyFraction = if (it.sensitiveListener == p.sensitiveListener) it.weeklyFraction
            else it.weeklyFraction * budgetOf(it.sensitiveListener) / budgetOf(p.sensitiveListener),
            headphoneWeeklyFraction = p.headphoneWeeklyPa2h / budgetOf(p.sensitiveListener),
        )
    }

    private fun budgetOf(sensitive: Boolean) = if (sensitive) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT

    fun hydrate(p: VoxaraStore.Persisted) {
        if (!serviceActive) engine.restore(p.dosePercent, p.weeklyPa2h, p.peakDbc)
        _state.update {
            it.copy(
                // Seed the gauge from the last persisted reading so a cold start never shows a
                // zero the microphone has not actually measured yet.
                dba = if (it.dba > 0.0) it.dba else p.lastDba,
                dosePercent = engine.dosePercent,
                weeklyFraction = engine.weeklyFraction(p.sensitiveListener),
                peakDbc = engine.peakDbc,
                twaDba = engine.twaDba(),
                calibrationOffsetDb = p.calibrationOffsetDb,
                alertSettings = p.alertSettings,
                situationChoice = p.situationChoice,
                mode = Situation.resolve(p.situationChoice, it.detectedMode),
                concertStartedAtMs = p.concertStartedAtMs,
                sensitiveListener = p.sensitiveListener,
                calibrations = p.calibrations,
                usualHeadphones = p.usualHeadphones,
                aiConsent = p.aiConsent,
                hapticPrefs = p.hapticPrefs,
                snoozeMinutes = p.snoozeMinutes,
                tonoEnabled = p.tonoEnabled,
                headphoneMode = p.headphoneMode,
                listening = it.listening.withChoice(p.headphoneMode),
                headphoneWeeklyPa2h = p.headphoneWeeklyPa2h,
                headphoneWeeklyFraction = p.headphoneWeeklyPa2h /
                    (if (p.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT),
            )
        }
    }

    fun update(transform: (ExposureState) -> ExposureState) = _state.update(transform)

    /** "Dismiss" on the headphone alert: hides its panel for this tier. The engine's cooldown and tiers decide the next one. */
    fun dismissHeadphoneAlert() = _state.update { st ->
        st.copy(
            headphoneAlert = st.headphoneAlert?.copy(dismissed = true),
            lowerVolume = if (st.lowerVolume is LowerUi.Working) st.lowerVolume else LowerUi.Idle,
        )
    }

    fun setLowerVolume(ui: LowerUi) = _state.update { it.copy(lowerVolume = ui) }

    /** Manual reset of today's dose. The service notices [resetGeneration] and clears its copy. */
    fun resetDay() {
        engine.resetDay()
        resetGeneration.incrementAndGet()
        _state.update {
            it.copy(dosePercent = 0.0, twaDba = null, peakDbc = 0.0)
        }
    }
}
