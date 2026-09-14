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
 * Hysteresis gate: a scene must win three consecutive windows at >= 0.60 confidence before it
 * changes the UI. Below that the app says "unknown" and falls back to level-only advice —
 * an unlabelled reading is better than a wrong label.
 */
class SceneHysteresis(
    private val minConfidence: Float = 0.60f,
    private val consecutiveWindows: Int = 3,
) {
    var current: Scene = Scene.UNKNOWN
        private set

    private var candidate: Scene = Scene.UNKNOWN
    private var streak = 0

    fun offer(scene: Scene, confidence: Float): Scene {
        if (confidence < minConfidence) {
            streak = 0
            candidate = Scene.UNKNOWN
            return current
        }
        if (scene == candidate) streak++ else { candidate = scene; streak = 1 }
        if (streak >= consecutiveWindows) current = candidate
        return current
    }

    fun reset() {
        current = Scene.UNKNOWN
        candidate = Scene.UNKNOWN
        streak = 0
    }
}

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
