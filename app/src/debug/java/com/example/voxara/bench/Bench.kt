package com.example.voxara.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.voxara.core.headphones.Confidence
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneModeChoice
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
 *   adb shell am broadcast -a com.example.voxara.DEBUG --es hp ON|OFF|AUTO
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
        i.getStringExtra("hp")?.let { raw ->
            val choice = HeadphoneModeChoice.parse(raw) ?: return@let
            ExposureRepository.setHeadphoneMode(choice)
            CoroutineScope(Dispatchers.IO).launch { ExposureRepository.store.setHeadphoneMode(choice) }
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
