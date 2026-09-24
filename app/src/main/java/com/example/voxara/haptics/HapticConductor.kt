package com.example.voxara.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.os.VibratorManager

/**
 * HAPTIC TAXONOMY - composed from VibrationEffect.startComposition() primitives, never canned
 * system effects. The watch assumes you cannot hear it: haptic first, screen second.
 */
enum class Pattern {
    THRESHOLD, DOSE_FULL, IMPULSE, CALM,
    /** Advisory: one soft tick. */
    SOFT_TICK,
    /** 80% tiers: two soft ticks. */
    TWO_SOFT,
    /** Weekly 100%: long-short-long. */
    LONG_SHORT_LONG,
    /** Very loud listening right now: quick triple. */
    QUICK_TRIPLE,
}

class HapticConductor(private val vibrator: Vibrator) {

    constructor(context: Context) : this(resolve(context))

    private var lastCalmAt = 0L

    private val supportsPrimitives: Boolean = runCatching {
        vibrator.areAllPrimitivesSupported(
            PRIMITIVE_QUICK_RISE, PRIMITIVE_THUD, PRIMITIVE_LOW_TICK, PRIMITIVE_TICK, PRIMITIVE_CLICK, PRIMITIVE_SPIN,
        )
    }.getOrDefault(false)

    fun fire(pattern: Pattern) {
        if (!vibrator.hasVibrator()) return
        if (pattern == Pattern.CALM) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastCalmAt < 600_000) return // relief, not another alert
            lastCalmAt = now
        }
        runCatching {
            vibrator.vibrate(if (supportsPrimitives) composed(pattern) else fallback(pattern))
        }
    }

    private fun composed(pattern: Pattern): VibrationEffect = when (pattern) {
        // Two rising taps 130 ms apart, second one stronger: "you are on the clock".
        Pattern.THRESHOLD -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_QUICK_RISE, 0.55f, 0)
            .addPrimitive(PRIMITIVE_QUICK_RISE, 1.0f, 130)
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

        Pattern.TWO_SOFT -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_TICK, 0.7f, 0)
            .addPrimitive(PRIMITIVE_TICK, 0.7f, 160)
            .compose()

        Pattern.LONG_SHORT_LONG -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_THUD, 1.0f, 0)
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 220)
            .addPrimitive(PRIMITIVE_THUD, 1.0f, 180)
            .compose()

        Pattern.QUICK_TRIPLE -> VibrationEffect.startComposition()
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 0)
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 70)
            .addPrimitive(PRIMITIVE_CLICK, 1.0f, 70)
            .compose()
    }

    /** Older or cheaper actuators: hand-rolled envelopes with the same rhythm. */
    private fun fallback(pattern: Pattern): VibrationEffect = when (pattern) {
        Pattern.THRESHOLD -> VibrationEffect.createWaveform(
            longArrayOf(0, 45, 85, 70), intArrayOf(0, 140, 0, 255), -1
        )
        Pattern.DOSE_FULL -> VibrationEffect.createWaveform(
            longArrayOf(0, 30, 120, 30, 120, 30, 140, 240),
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 180), -1
        )
        Pattern.IMPULSE -> VibrationEffect.createOneShot(400, 255)
        Pattern.CALM -> VibrationEffect.createOneShot(160, 90)
        Pattern.SOFT_TICK -> VibrationEffect.createOneShot(40, 140)
        Pattern.TWO_SOFT -> VibrationEffect.createWaveform(
            longArrayOf(0, 40, 140, 40), intArrayOf(0, 150, 0, 150), -1
        )
        Pattern.LONG_SHORT_LONG -> VibrationEffect.createWaveform(
            longArrayOf(0, 350, 150, 90, 150, 350), intArrayOf(0, 255, 0, 255, 0, 255), -1
        )
        Pattern.QUICK_TRIPLE -> VibrationEffect.createWaveform(
            longArrayOf(0, 45, 60, 45, 60, 45), intArrayOf(0, 255, 0, 255, 0, 255), -1
        )
    }

    companion object {
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
