package com.example.voxara.bench

import android.content.Context
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
 */
object Bench {

    /** Set by the scenario chips in the debug panel. */
    @Volatile var target = 68.0

    suspend fun run(context: Context) {
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
