package com.example.voxara.core.advice

import com.example.voxara.core.format.formatHeadroom
import com.example.voxara.core.risk.RiskState
import com.example.voxara.core.scene.Scene
import kotlin.math.roundToInt

/**
 * TIER 1 — always on the watch. A deterministic template table keyed by
 * (scene x risk state x headroom). Zero latency, zero network, no invented numbers:
 * every figure in a reply is injected from the dose engine.
 *
 * Rules the generator is held to:
 *   state the fact, then one action; never more than one instruction;
 *   no medical claims; no guilt; silence is a valid answer when dose is under 25%.
 */
/**
 * Which sentence the grammar chose. The core picks the outcome; the words for it are resolved
 * from resources by the UI layer, so the same decision reads correctly in either language.
 */
enum class AdviceKind {
    FINE,
    COMFORTABLE,
    CRITICAL_MUSIC,
    CRITICAL_MACHINERY,
    CRITICAL_GENERIC,
    HAZARD_MUSIC,
    HAZARD_MACHINERY,
    HAZARD_TRANSIT,
    HAZARD_TRAFFIC,
    HAZARD_GENERIC,
    ACCRUING_TRAFFIC,
    ACCRUING_TRANSIT,
    ACCRUING_MUSIC,
    ACCRUING_MACHINERY,
    ACCRUING_GENERIC,
    RECOVER,
    CALM,
}

/**
 * A chosen sentence plus the figures to inject into it. `args` are positional and match the
 * template's placeholders; every one of them comes from the dose engine.
 */
data class Advice(val kind: AdviceKind, val args: List<Any> = emptyList())

object AdviceGrammar {

    data class State(
        val dba: Double,
        val dosePercent: Double,
        val headroomMinutes: Double,
        val scene: Scene,
        val risk: RiskState,
    )

    /** The one-sentence answer. <= 12 words, always ends in an action. */
    fun advise(s: State): Advice {
        if (s.dosePercent < 25.0 && s.risk == RiskState.CALM) {
            return when (s.scene) {
                Scene.QUIET_INDOOR, Scene.CONVERSATION, Scene.UNKNOWN -> Advice(AdviceKind.FINE)
                else -> Advice(AdviceKind.COMFORTABLE)
            }
        }
        val left = formatHeadroom(s.headroomMinutes)
        return when (s.risk) {
            RiskState.CRITICAL -> when (s.scene) {
                Scene.LIVE_MUSIC -> Advice(AdviceKind.CRITICAL_MUSIC)
                Scene.MACHINERY -> Advice(AdviceKind.CRITICAL_MACHINERY)
                else -> Advice(AdviceKind.CRITICAL_GENERIC)
            }
            RiskState.HAZARD -> when (s.scene) {
                Scene.LIVE_MUSIC -> Advice(AdviceKind.HAZARD_MUSIC, listOf(left))
                Scene.MACHINERY -> Advice(AdviceKind.HAZARD_MACHINERY, listOf(left))
                Scene.TRANSIT -> Advice(AdviceKind.HAZARD_TRANSIT, listOf(left))
                Scene.TRAFFIC -> Advice(AdviceKind.HAZARD_TRAFFIC, listOf(left))
                else -> Advice(AdviceKind.HAZARD_GENERIC, listOf(left))
            }
            RiskState.ACCRUING -> when (s.scene) {
                Scene.TRAFFIC -> Advice(AdviceKind.ACCRUING_TRAFFIC)
                Scene.TRANSIT -> Advice(AdviceKind.ACCRUING_TRANSIT)
                Scene.LIVE_MUSIC ->
                    Advice(AdviceKind.ACCRUING_MUSIC, listOf(s.dosePercent.roundToInt()))
                Scene.MACHINERY -> Advice(AdviceKind.ACCRUING_MACHINERY)
                else -> Advice(AdviceKind.ACCRUING_GENERIC, listOf(s.dba.roundToInt()))
            }
            RiskState.RECOVER -> Advice(AdviceKind.RECOVER)
            RiskState.CALM -> Advice(AdviceKind.CALM, listOf(s.dosePercent.roundToInt()))
        }
    }
}

/** The six on-device voice intents. Anything unmatched falls through to the advice path. */
enum class VoiceIntent {
    LEVEL,       // "How loud is it?"
    HEADROOM,    // "How long can I stay?"
    HISTORY,     // "How was yesterday?"
    MODE,        // "Start concert mode"
    ADVICE,      // "Is it safe in here?"
    CALIBRATE,   // "Calibrate"
    UNKNOWN,
}

object IntentRouter {

    // Both languages are matched at once, in every language setting: the wearer may well speak
    // to the watch in the other one, and a recognised question beats a correct language.
    private val level = listOf(
        "how loud", "decibel", "db", "level", "noise now",
        "cuanto ruido", "cuánto ruido", "decibel", "decibelio", "nivel", "ruido ahora",
    )
    private val headroom = listOf(
        "how long", "how much time", "time left", "headroom", "minutes left",
        "cuanto tiempo", "cuánto tiempo", "cuanto puedo", "cuánto puedo",
        "tiempo restante", "margen", "minutos",
    )
    private val history = listOf(
        "yesterday", "this week", "history", "last week", "today's total",
        "ayer", "esta semana", "historial", "semana pasada", "total de hoy", "hoy en total",
    )
    private val mode = listOf(
        "concert", "start", "stop", "urban", "focus mode", "mode",
        "concierto", "empieza", "empezar", "inicia", "para", "detén", "deten",
        "urbano", "modo enfoque", "modo",
    )
    private val calibrate = listOf(
        "calibrat", "offset", "accurate",
        "calibra", "ajuste", "preciso", "precisión", "precision",
    )
    private val advice = listOf(
        "is it safe", "should i", "what do i do", "safe in here", "advice",
        "es seguro", "estoy seguro", "debo", "qué hago", "que hago", "consejo",
    )

    fun route(utterance: String): VoiceIntent {
        val u = utterance.lowercase()
        fun hit(keys: List<String>) = keys.any { u.contains(it) }
        return when {
            hit(advice) -> VoiceIntent.ADVICE
            hit(headroom) -> VoiceIntent.HEADROOM
            hit(history) -> VoiceIntent.HISTORY
            hit(calibrate) -> VoiceIntent.CALIBRATE
            hit(mode) -> VoiceIntent.MODE
            hit(level) -> VoiceIntent.LEVEL
            else -> VoiceIntent.UNKNOWN
        }
    }
}
