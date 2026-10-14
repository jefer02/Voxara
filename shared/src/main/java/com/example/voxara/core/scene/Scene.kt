package com.example.voxara.core.scene

/**
 * The six scenes AudioSet's 521 labels collapse into — the only ones we can actually act on.
 * The enum carries no display text: labels are resolved from resources by the UI layer.
 */
enum class Scene {
    TRAFFIC,
    TRANSIT,
    LIVE_MUSIC,
    MACHINERY,
    CONVERSATION,
    QUIET_INDOOR,
    UNKNOWN,
}

/**
 * HYSTERESIS GATE — a label must win [consecutiveWindows] consecutive windows at
 * >= [minConfidence] before it replaces [current]. A low-confidence window breaks the streak but
 * never clears [current]: an unsure moment keeps what was already shown. Used for scenes
 * ([SceneHysteresis]) and for automatic situation modes (AutoModeDetector).
 */
open class ConfidenceHysteresis<T>(
    private val unknown: T,
    private val minConfidence: Float,
    private val consecutiveWindows: Int,
) {
    var current: T = unknown
        private set

    private var candidate: T = unknown
    private var streak = 0

    fun offer(label: T, confidence: Float): T {
        if (confidence < minConfidence || label == unknown) {
            streak = 0
            candidate = unknown
            return current
        }
        if (label == candidate) streak++ else { candidate = label; streak = 1 }
        if (streak >= consecutiveWindows) current = candidate
        return current
    }

    fun reset() {
        current = unknown
        candidate = unknown
        streak = 0
    }
}

/**
 * Scene gate: a scene must win three consecutive windows at >= 0.60 confidence before it
 * changes the UI. Below that the app says "unknown" and falls back to level-only advice —
 * an unlabelled reading is better than a wrong label.
 */
class SceneHysteresis(
    minConfidence: Float = 0.60f,
    consecutiveWindows: Int = 3,
) : ConfidenceHysteresis<Scene>(Scene.UNKNOWN, minConfidence, consecutiveWindows)

/** Level-only fallback band — never presented as a classifier result. */
enum class EnvBand {
    QUIET_ROOM,
    OFFICE,
    CONVERSATION,
    CITY_TRAFFIC,
    LIVE_MUSIC,
    NIGHTCLUB,
    POWER_TOOL,
}

fun environmentBand(dba: Double): EnvBand = when {
    dba < 45 -> EnvBand.QUIET_ROOM
    dba < 62 -> EnvBand.OFFICE
    dba < 78 -> EnvBand.CONVERSATION
    dba < 90 -> EnvBand.CITY_TRAFFIC
    dba < 101 -> EnvBand.LIVE_MUSIC
    dba < 111 -> EnvBand.NIGHTCLUB
    else -> EnvBand.POWER_TOOL
}
