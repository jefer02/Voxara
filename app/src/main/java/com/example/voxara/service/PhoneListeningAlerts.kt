package com.example.voxara.service

import android.content.Context
import com.example.voxara.core.alerts.AlertEngine
import com.example.voxara.core.alerts.AlertInputs
import com.example.voxara.core.alerts.AlertKind
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_ADULT
import com.example.voxara.core.dose.WHO_WEEKLY_PA2H_SENSITIVE
import com.example.voxara.core.headphones.LayerSource
import com.example.voxara.core.ledger.localEpochDay
import com.example.voxara.core.ledger.localMinuteOfDay
import com.example.voxara.data.ExposureRepository
import com.example.voxara.core.headphones.HeadphoneAlert
import com.example.voxara.data.VoxaraStore
import com.example.voxara.haptics.HapticConductor
import com.example.voxara.sync.HeadphoneWire
import com.example.voxara.sync.PhoneListeningState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "The volume on your PHONE's headphones is above the recommended level", while the watch's dosimeter is not
 * running (so nothing else is looking): the same AlertEngine, the same notifier and haptics, with the dose
 * figures taken from what the watch last saved. Only this one alert is delivered here; everything else the
 * engine would say belongs to the dosimeter, which has the live numbers. With the dosimeter running its own
 * listening loop does this (and has the live ambient figures too).
 */
object PhoneListeningAlerts {

    private val mutex = Mutex()

    suspend fun evaluate(context: Context, state: PhoneListeningState, nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        val app = context.applicationContext
        ExposureRepository.init(app)
        val level = state.estimate?.dba?.let { dba ->
            // The same short wait as everywhere: a level only counts once it has held for a few seconds.
            if (dba > com.example.voxara.core.headphones.HeadphoneProfile.SAFE_DBA &&
                nowMs - state.levelSinceMs < HeadphoneWire.OVER_SAFE_DWELL_MS
            ) null else dba
        }
        val store = ExposureRepository.store
        val p = store.read()
        val budget = if (p.sensitiveListener) WHO_WEEKLY_PA2H_SENSITIVE else WHO_WEEKLY_PA2H_ADULT
        val today = localEpochDay(nowMs)
        val (alerts, memory) = AlertEngine.evaluate(
            AlertInputs(
                nowMs = nowMs,
                localDay = today,
                localMinuteOfDay = localMinuteOfDay(nowMs),
                sustainedAmbientDba = null,
                dailyDosePercent = if (p.dayEpoch == today) p.dosePercent else 0.0,
                weeklyAmbientPercent = 100.0 * p.weeklyPa2h / budget,
                weeklyHeadphonePercent = 100.0 * p.headphoneWeeklyPa2h / budget,
                headphoneNowDba = level,
            ),
            p.alertSettings,
            p.alertMemory,
        )
        // Only this alert's own memory is taken over: the dosimeter owns the rest.
        if (memory.hpSafeTier != p.alertMemory.hpSafeTier || memory.lastHpSafeMs != p.alertMemory.lastHpSafeMs) {
            store.updateAlertMemory { stored -> stored.copy(hpSafeTier = memory.hpSafeTier, lastHpSafeMs = memory.lastHpSafeMs) }
        }
        val mine = alerts.filter { it.kind == AlertKind.HEADPHONE_OVER_SAFE }
        if (mine.isEmpty()) return@withLock
        val a = mine.first()
        ExposureRepository.update {
            it.copy(headphoneSource = LayerSource.PHONE, headphoneAlert = HeadphoneAlert(a.level, a.value, nowMs, LayerSource.PHONE))
        }
        AlertNotifier(app, HapticConductor(app)).deliver(mine, p.hapticPrefs)
    }
}
