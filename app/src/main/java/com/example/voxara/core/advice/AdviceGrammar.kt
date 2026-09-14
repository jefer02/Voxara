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
object AdviceGrammar {

    data class State(
        val dba: Double,
        val dosePercent: Double,
        val headroomMinutes: Double,
        val scene: Scene,
        val risk: RiskState,
    )

    /** The one-sentence answer. <= 12 words, always ends in an action. */
    fun advise(s: State): String {
        if (s.dosePercent < 25.0 && s.risk == RiskState.CALM) {
            return when (s.scene) {
                Scene.QUIET_INDOOR, Scene.CONVERSATION, Scene.UNKNOWN -> "You're fine. Nothing to do today."
                else -> "Comfortable level. Nothing to do."
            }
        }
        val left = formatHeadroom(s.headroomMinutes)
        return when (s.risk) {
            RiskState.CRITICAL -> when (s.scene) {
                Scene.LIVE_MUSIC -> "Daily limit reached. Put in plugs or step outside."
                Scene.MACHINERY -> "Daily limit reached. Put on protection now."
                else -> "Daily limit reached. Leave or protect your ears."
            }
            RiskState.HAZARD -> when (s.scene) {
                Scene.LIVE_MUSIC -> "$left left. Step back from the speakers."
                Scene.MACHINERY -> "$left left. Move away or wear protection."
                Scene.TRANSIT -> "$left left. Move down the platform."
                Scene.TRAFFIC -> "$left left. Cross to the quieter side."
                else -> "$left left at this level. Find somewhere quieter."
            }
            RiskState.ACCRUING -> when (s.scene) {
                Scene.TRAFFIC -> "Loud commute. Switch to the quieter platform end."
                Scene.TRANSIT -> "Loud carriage. Move a car forward."
                Scene.LIVE_MUSIC -> "${s.dosePercent.roundToInt()}% used. Take a break between sets."
                Scene.MACHINERY -> "Dose is running. Fit protection before the next hour."
                else -> "${s.dba.roundToInt()} dBA, dose running. Take a quiet break soon."
            }
            RiskState.RECOVER -> "Back under the line. Nothing more to do."
            RiskState.CALM -> "${s.dosePercent.roundToInt()}% used today. Keep going."
        }
    }

    /** The short line under the number on the breach screen. */
    fun breachHeadline(): String = "PROTECT OR LEAVE"
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
    private val level = listOf("how loud", "decibel", "db", "level", "noise now")
    private val headroom = listOf("how long", "how much time", "time left", "headroom", "minutes left")
    private val history = listOf("yesterday", "this week", "history", "last week", "today's total")
    private val mode = listOf("concert", "start", "stop", "urban", "focus mode", "mode")
    private val calibrate = listOf("calibrat", "offset", "accurate")
    private val advice = listOf("is it safe", "should i", "what do i do", "safe in here", "advice")

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
