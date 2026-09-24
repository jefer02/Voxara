package com.example.voxara.data

import android.content.Context
import com.example.voxara.core.advice.Advice
import com.example.voxara.audio.DeviceAudioInfo
import com.example.voxara.audio.PowerPolicy
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.headphones.HeadphoneCategory
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/** THREE MODES, ONE ENGINE. Titles and blurbs are resolved from resources by the UI layer. */
enum class AppMode {
    CONCERT,
    URBAN,
    VOICE,
}

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
    val mode: AppMode = AppMode.URBAN,
    val monitoring: Boolean = false,
    val micGranted: Boolean = false,
    val simulated: Boolean = false,
    val concertEndsAtMs: Long = 0L,
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
    /** "My headphones are usually…" (earbuds / over-ear), or null. */
    val usualHeadphones: HeadphoneCategory? = null,
    /** Explicit opt-in to cloud AI (Tono); OFF by default. */
    val aiConsent: Boolean = false,
    /** Whether this watch exposes the system battery-optimisation list. */
    val batterySettingsAvailable: Boolean = false,
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
     * Bumped when minutes were written from outside the dosimeter (phone companion sync), so it
     * reloads its ledgers and re-derives today's dose from them.
     */
    val ledgerGeneration = AtomicInteger(0)

    /** Bumped by a manual dose reset so the service drops its in-memory hourly profile too. */
    val resetGeneration = AtomicInteger(0)

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
                mode = runCatching { AppMode.valueOf(p.mode) }.getOrDefault(AppMode.URBAN),
                sensitiveListener = p.sensitiveListener,
                calibrations = p.calibrations,
                usualHeadphones = p.usualHeadphones,
                aiConsent = p.aiConsent,
                headphoneWeeklyPa2h = p.headphoneWeeklyPa2h,
                headphoneWeeklyFraction = p.headphoneWeeklyPa2h /
                    (if (p.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT),
            )
        }
    }

    fun update(transform: (ExposureState) -> ExposureState) = _state.update(transform)

    fun setMode(mode: AppMode) = _state.update { it.copy(mode = mode) }

    /** Manual reset of today's dose. The service notices [resetGeneration] and clears its copy. */
    fun resetDay() {
        engine.resetDay()
        resetGeneration.incrementAndGet()
        _state.update {
            it.copy(dosePercent = 0.0, twaDba = null, peakDbc = 0.0)
        }
    }
}
