package com.example.voxara.haptics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.os.VibratorManager

/**
 * HAPTIC TAXONOMY - composed from VibrationEffect.startComposition() primitives, never canned
 * system effects. The watch assumes you cannot hear it: haptic first, screen second.
 *
 * Strength grows with severity, so the wrist can tell them apart before the screen:
 *   advisory  soft   (<= 0.6)  THRESHOLD, SOFT_TICK
 *   warning   firm   (0.8)     TWO_FIRM, QUICK_TRIPLE
 *   limit     full   (1.0)     DOSE_FULL, LONG_SHORT_LONG (and IMPULSE, the 140 dBC peak)
 * Not alerts: CALM (back to a safe level), MODE_CHANGE (automatic mode change), CONFIRM (a tap
 * the wearer just made). Each one has its own primitive and rhythm.
 */
enum class Pattern(val isAlert: Boolean) {
    /** Advisory: sustained level over the threshold. Two soft rising pulses. */
    THRESHOLD(true),
    /** Daily 100%: three hard clicks then a thud. */
    DOSE_FULL(true),
    /** 140 dBC impulse peak. */
    IMPULSE(true),
    /** Relief: back under 80 dBA after a loud stretch. Not an alert. */
    CALM(false),
    /** Advisory: one soft tick. */
    SOFT_TICK(true),
    /** 80% warnings: two firm clicks. */
    TWO_FIRM(true),
    /** Weekly 100%: long-short-long. */
    LONG_SHORT_LONG(true),
    /** Very loud listening right now (warning): quick triple. */
    QUICK_TRIPLE(true),
    /** An automatic mode change: one slow, soft swell. Rate-limited by ModeChangeHapticGate. */
    MODE_CHANGE(false),
    /** The wearer picked a mode: the smallest possible tap, right under their finger. */
    CONFIRM(false),
}

class HapticConductor(private val vibrator: Vibrator) {

    constructor(context: Context) : this(resolve(context))

    private var lastCalmAt = 0L

    /** Wall-clock time of the last alert pattern played (any tier), 0 = none yet. */
    @Volatile var lastAlertAtMs = 0L
        private set

    /** Per pattern: does this actuator support every primitive it is composed of? */
    private val composable: Map<Pattern, Boolean> = Pattern.entries.associateWith { p ->
        runCatching { vibrator.areAllPrimitivesSupported(*primitivesOf(p)) }.getOrDefault(false)
    }

    /** Whether [pattern] plays as composed primitives here (false = the waveform fallback). */
    fun isComposed(pattern: Pattern): Boolean = composable[pattern] == true

    /**
     * Plays [pattern] [times] times (the STRONG preference plays it twice, [REPEAT_GAP_MS] apart).
     * 0 plays nothing; callers get the count from AlertPolicy, which never returns 0 for a
     * warning or a limit.
     */
    fun fire(pattern: Pattern, times: Int) {
        if (times <= 0) return
        fire(pattern)
        if (times > 1) Handler(Looper.getMainLooper()).postDelayed({ fire(pattern) }, REPEAT_GAP_MS)
    }

    fun fire(pattern: Pattern) {
        if (!vibrator.hasVibrator()) return
        if (pattern == Pattern.CALM) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastCalmAt < 600_000) return // relief, not another alert
            lastCalmAt = now
        }
        if (pattern.isAlert) lastAlertAtMs = System.currentTimeMillis()
        runCatching {
            vibrator.vibrate(if (composable[pattern] == true) composed(pattern) else fallback(pattern))
        }
    }

    private fun composed(pattern: Pattern): VibrationEffect = when (pattern) {
        // Two soft rising pulses 130 ms apart, the second a little stronger: "you are on the clock".
        Pattern.THRESHOLD -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_QUICK_RISE, 0.4f, 0)
            .addPrimitive(PRIMITIVE_QUICK_RISE, 0.6f, 130)
            .compose()

        // Three hard clicks then a decaying thud. Unlike anything else on the platform.
        Pattern.DOSE_FULL -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 0)
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 150)
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 150)
            .addPrimitive(PRIMITIVE_THUD, 1.0f, 170)
            .compose()

        // 140 dBC peak: no ramp, no politeness.
        Pattern.IMPULSE -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_SPIN, 1.0f, 0)
            .addPrimitive(PRIMITIVE_THUD, 1.0f, 60)
            .compose()

        Pattern.CALM -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_LOW_TICK, 0.35f, 0)
            .compose()

        Pattern.SOFT_TICK -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_TICK, 0.6f, 0)
            .compose()

        Pattern.TWO_FIRM -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_CLICK, 0.8f, 0)
            .addPrimitive(PRIMITIVE_CLICK, 0.8f, 160)
            .compose()

        Pattern.LONG_SHORT_LONG -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_THUD, 1.0f, 0)
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 220)
            .addPrimitive(PRIMITIVE_THUD, 1.0f, 180)
            .compose()

        Pattern.QUICK_TRIPLE -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_CLICK, 0.8f, 0)
            .addPrimitive(PRIMITIVE_CLICK, 0.8f, 70)
            .addPrimitive(PRIMITIVE_CLICK, 0.8f, 70)
            .compose()

        Pattern.MODE_CHANGE -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_SLOW_RISE, 0.3f, 0)
            .compose()

        Pattern.CONFIRM -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_TICK, 0.3f, 0)
            .compose()
    }

    /** Older or cheaper actuators: hand-rolled envelopes with the same rhythm and strength order. */
    private fun fallback(pattern: Pattern): VibrationEffect = when (pattern) {
        Pattern.THRESHOLD -> VibrationEffect.createWaveform(
            longArrayOf(0, 45, 85, 70), intArrayOf(0, 100, 0, 150), -1
        )
        Pattern.DOSE_FULL -> VibrationEffect.createWaveform(
            longArrayOf(0, 30, 120, 30, 120, 30, 140, 240),
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 180), -1
        )
        Pattern.IMPULSE -> VibrationEffect.createOneShot(400, 255)
        Pattern.CALM -> VibrationEffect.createOneShot(160, 90)
        Pattern.SOFT_TICK -> VibrationEffect.createOneShot(40, 140)
        Pattern.TWO_FIRM -> VibrationEffect.createWaveform(
            longArrayOf(0, 40, 140, 40), intArrayOf(0, 204, 0, 204), -1
        )
        Pattern.LONG_SHORT_LONG -> VibrationEffect.createWaveform(
            longArrayOf(0, 350, 150, 90, 150, 350), intArrayOf(0, 255, 0, 255, 0, 255), -1
        )
        Pattern.QUICK_TRIPLE -> VibrationEffect.createWaveform(
            longArrayOf(0, 45, 60, 45, 60, 45), intArrayOf(0, 204, 0, 204, 0, 204), -1
        )
        // A slow, quiet ramp: nothing else on the watch swells like this.
        Pattern.MODE_CHANGE -> VibrationEffect.createWaveform(
            longArrayOf(0, 60, 60, 60, 60), intArrayOf(0, 25, 45, 65, 0), -1
        )
        Pattern.CONFIRM -> VibrationEffect.createOneShot(15, 60)
    }

    companion object {
        /** Longest pattern (~1.1 s) plus a clear pause, so a repeat reads as a second alert beat. */
        const val REPEAT_GAP_MS = 1_600L

        private fun primitivesOf(pattern: Pattern): IntArray = when (pattern) {
            Pattern.THRESHOLD -> intArrayOf(PRIMITIVE_QUICK_RISE)
            Pattern.DOSE_FULL -> intArrayOf(PRIMITIVE_CLICK, PRIMITIVE_THUD)
            Pattern.IMPULSE -> intArrayOf(PRIMITIVE_SPIN, PRIMITIVE_THUD)
            Pattern.CALM -> intArrayOf(PRIMITIVE_LOW_TICK)
            Pattern.SOFT_TICK, Pattern.CONFIRM -> intArrayOf(PRIMITIVE_TICK)
            Pattern.TWO_FIRM, Pattern.QUICK_TRIPLE -> intArrayOf(PRIMITIVE_CLICK)
            Pattern.LONG_SHORT_LONG -> intArrayOf(PRIMITIVE_THUD, PRIMITIVE_CLICK)
            Pattern.MODE_CHANGE -> intArrayOf(PRIMITIVE_SLOW_RISE)
        }

        private fun resolve(context: Context): Vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
    }
}
