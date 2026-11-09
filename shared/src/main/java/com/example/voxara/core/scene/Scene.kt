package com.example.voxara.core.scene

/**
 * HYSTERESIS GATE — a label must win [consecutiveWindows] consecutive windows at
 * >= [minConfidence] before it replaces [current]. A low-confidence window breaks the streak but
 * never clears [current]: an unsure moment keeps what was already shown. Used for automatic
 * situation modes (AutoModeDetector).
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
