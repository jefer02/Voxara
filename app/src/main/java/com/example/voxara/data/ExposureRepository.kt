package com.example.voxara.data

import android.content.Context
import com.example.voxara.core.advice.Advice
import com.example.voxara.core.dose.DOSE_THRESHOLD_DBA
import com.example.voxara.core.dose.NoiseDoseEngine
import com.example.voxara.core.dose.SampleState
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.scene.Scene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** THREE MODES, ONE ENGINE. Titles and blurbs are resolved from resources by the UI layer. */
enum class AppMode {
    CONCERT,
    URBAN,
    VOICE,
}

/** The scenario chips of the dossier's live gauge, kept as a bench driver for mic-less devices. */
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
    val todayProfile: List<Float> = List(24) { 0f },
    val weekProfiles: List<List<Float>> = emptyList(),
    val lastAdvice: Advice? = null,
) {
    val headroomMinutes: Double get() = headroomSeconds / 60.0
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

    fun init(context: Context) {
        if (initialised) return
        store = VoxaraStore(context.applicationContext)
        initialised = true
    }

    fun isInitialised() = initialised

    fun hydrate(p: VoxaraStore.Persisted) {
        engine.restore(p.dosePercent, p.weeklyPa2h, p.peakDbc)
        _state.update {
            it.copy(
                // Seed the gauge from the last persisted reading so a cold start never shows a
                // zero the microphone has not actually measured yet.
                dba = if (it.dba > 0.0) it.dba else p.lastDba,
                dosePercent = p.dosePercent,
                weeklyFraction = engine.weeklyFraction(p.sensitiveListener),
                peakDbc = p.peakDbc,
                twaDba = engine.twaDba(),
                calibrationOffsetDb = p.calibrationOffsetDb,
                todayProfile = p.todayProfile,
                weekProfiles = p.weekProfiles,
                mode = runCatching { AppMode.valueOf(p.mode) }.getOrDefault(AppMode.URBAN),
            )
        }
    }

    fun update(transform: (ExposureState) -> ExposureState) = _state.update(transform)

    fun setMode(mode: AppMode) = _state.update { it.copy(mode = mode) }
}
