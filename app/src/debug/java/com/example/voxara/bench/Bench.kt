package com.example.voxara.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.ListeningEstimate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.voxara.core.risk.RiskState
import com.example.voxara.data.ExposureRepository
import com.example.voxara.service.DosimeterService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * DEBUG BUILD ONLY (src/debug). With no microphone (emulator, denied permission) the gauge is
 * driven by a scripted level so the UI can be exercised. It writes the DISPLAY state and nothing
 * else: no dose, no ledger minutes, no alerts, no persistence. Release builds get a no-op
 * (src/release), so none of this code ships.
 *
 * It also takes display-only commands over adb, to look at headphones mode without headphones:
 *   adb shell am broadcast -a com.example.voxara.DEBUG --es hpalert 2:93.0[:phone]   (show the headphone-volume alert panel)
 *   adb shell am broadcast -a com.example.voxara.DEBUG --ei listen 85      (estimated dBA; -1 = nothing playing)
 *   adb shell am broadcast -a com.example.voxara.DEBUG --ed dba 72.0       (the room level)
 *   adb shell am broadcast -a com.example.voxara.DEBUG --ez alert true     (an alert was delivered just now)
 *   adb shell am broadcast -a com.example.voxara.DEBUG --ez phone true     (the phone just reported headphones; false = it did not)
 *   adb shell am broadcast -a com.example.voxara.DEBUG --ez monitoring false (stop the dosimeter and the saved "monitoring" choice, as the Settings button does)
 *   adb shell am broadcast -a com.example.voxara.DEBUG --ef crown 3        (a turn of the crown: the real rotary event, + = clockwise; the emulator has no way to send one over adb)
 */
object Bench {

    /** Set by the scenario chips in the debug panel. */
    @Volatile var target = 68.0

    private const val ACTION = "com.example.voxara.DEBUG"

    private fun command(context: Context, i: Intent) {
        // Headphones mode is fully automatic now: there is no `hp` switch any more (an old script sending it is ignored).
        // `hpalert <tier>:<dba>[:phone]` shows the headphone-volume alert panel without hardware (display only).
        i.getStringExtra("hpalert")?.let { raw ->
            val p = raw.split(':')
            val tier = p.getOrNull(0)?.toIntOrNull() ?: return@let
            val dba = p.getOrNull(1)?.toDoubleOrNull() ?: return@let
            val src = if (p.getOrNull(2) == "phone") com.example.voxara.core.headphones.LayerSource.PHONE else com.example.voxara.core.headphones.LayerSource.WATCH
            ExposureRepository.update {
                it.copy(
                    headphoneAlert = com.example.voxara.core.headphones.HeadphoneAlert(tier, dba, System.currentTimeMillis(), src),
                    lowerVolume = com.example.voxara.core.headphones.LowerUi.Idle,
                )
            }
        }
        // `hplower <outcome>` shows how the panel reads after "Lower to safe volume" (LOWERED, FIXED_VOLUME, UNREACHABLE...).
        i.getStringExtra("hplower")?.let { raw ->
            val outcome = runCatching { com.example.voxara.sync.LowerOutcome.valueOf(raw) }.getOrNull() ?: return@let
            ExposureRepository.setLowerVolume(com.example.voxara.core.headphones.LowerUi.Done(outcome, 79.0))
        }
        if (i.hasExtra("listen")) {
            val dba = i.getIntExtra("listen", -1)
            ExposureRepository.update {
                if (dba < 0) it.copy(headphoneListening = false, headphoneEstimate = null)
                else it.copy(
                    headphoneCategory = HeadphoneCategory.EARBUDS, headphoneListening = true,
                    headphoneEstimate = ListeningEstimate(dba.toDouble(), Confidence.LOW),
                )
            }
        }
        if (i.hasExtra("phone")) ExposureRepository.phoneHeadphonesAtMs = if (i.getBooleanExtra("phone", false)) System.currentTimeMillis() else 0L
        if (i.hasExtra("monitoring")) {
            val on = i.getBooleanExtra("monitoring", true)
            if (on) DosimeterService.start(context) else DosimeterService.stop(context)
            CoroutineScope(Dispatchers.IO).launch { ExposureRepository.store.setMonitoring(on) }
        }
        if (i.hasExtra("crown")) crown(context, i.getFloatExtra("crown", 0f))
        if (i.hasExtra("dba")) target = i.getDoubleExtra("dba", target)
        if (i.getBooleanExtra("alert", false)) ExposureRepository.update { it.copy(lastAlertAtMs = System.currentTimeMillis()) }
    }

    /** The activity the commands play on (for the crown). */
    @Volatile private var activity: android.app.Activity? = null

    /** Delivers a rotary scroll event ([clicks] > 0 clockwise) to the window, the way the hardware crown does. */
    private fun crown(context: Context, clicks: Float) {
        val view = activity?.window?.decorView ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val props = arrayOf(android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_UNKNOWN })
        val coords = arrayOf(android.view.MotionEvent.PointerCoords().apply { setAxisValue(android.view.MotionEvent.AXIS_SCROLL, -clicks) })
        val event = android.view.MotionEvent.obtain(
            now, now, android.view.MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_ROTARY_ENCODER, 0,
        )
        view.post { view.dispatchGenericMotionEvent(event); event.recycle() }
    }

    suspend fun run(context: Context) {
        activity = context as? android.app.Activity
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = command(c, intent)
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        try { loop(context) } finally { activity = null; runCatching { context.unregisterReceiver(receiver) } }
    }

    private suspend fun loop(context: Context) {
        var level = target
        while (currentCoroutineContext().isActive) {
            delay(1_000)
            if (DosimeterService.hasMic(context)) {
                if (ExposureRepository.state.value.simulated) {
                    ExposureRepository.update { it.copy(simulated = false) }
                }
                continue
            }
            level += (target - level) * 0.35
            val shown = (level + (Random.nextDouble() - 0.5) * (0.8 + level / 90.0)).coerceAtLeast(26.0)
            ExposureRepository.update {
                it.copy(dba = shown, simulated = true, micGranted = false, risk = RiskState.of(shown, it.dosePercent))
            }
        }
    }
}
