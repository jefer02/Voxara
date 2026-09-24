package com.example.voxara.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertMemoryCodec
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.ReferenceType
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.ledger.LedgerSnapshot
import com.example.voxara.core.ledger.acceptsWrite
import com.example.voxara.core.ledger.rollover
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("voxara")

/**
 * DataStore holds settings, calibration, the running daily counters and the alert engine's
 * memory. Exposure history lives in the minute ledger ([ExposureDb]); the rolling weekly total is
 * also cached here so the tile and complication can draw without opening the database.
 */
class VoxaraStore(context: Context) {

    private val ds = context.applicationContext.dataStore

    private object Keys {
        val calibration = doublePreferencesKey("calibration_offset_db")
        val dose = doublePreferencesKey("dose_percent")
        val weekly = doublePreferencesKey("weekly_pa2h")
        val headphoneWeekly = doublePreferencesKey("headphone_weekly_pa2h")
        val peak = doublePreferencesKey("peak_dbc")
        val dayEpoch = longPreferencesKey("day_epoch")
        val mode = stringPreferencesKey("mode")
        val sensitive = booleanPreferencesKey("sensitive_listener")
        val alertCap = intPreferencesKey("daily_alert_cap")
        val monitoring = booleanPreferencesKey("monitoring_enabled")
        val lastDba = doublePreferencesKey("last_dba")
        val heartbeat = longPreferencesKey("monitor_heartbeat_ms")
        val alertThreshold = intPreferencesKey("alert_threshold_dba")
        val headphoneLoud = intPreferencesKey("headphone_loud_dba")
        val quietEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietStart = intPreferencesKey("quiet_start_min")
        val quietEnd = intPreferencesKey("quiet_end_min")
        val alertMemory = stringPreferencesKey("alert_memory")
        val usualHeadphones = intPreferencesKey("usual_headphones")
        val aiConsent = booleanPreferencesKey("ai_cloud_consent")
        val onboarded = booleanPreferencesKey("onboarding_done")
        private fun cal(source: MicSource, field: String) = "cal_${source.name.lowercase()}_$field"
        fun calOffset(s: MicSource) = doublePreferencesKey(cal(s, "offset"))
        fun calAt(s: MicSource) = longPreferencesKey(cal(s, "at"))
        fun calModel(s: MicSource) = stringPreferencesKey(cal(s, "model"))
        fun calRef(s: MicSource) = stringPreferencesKey(cal(s, "ref"))
        fun calSecond(s: MicSource) = doublePreferencesKey(cal(s, "second"))
    }

    data class Persisted(
        val calibrationOffsetDb: Double = 0.0,
        val dosePercent: Double = 0.0,
        /** Rolling 7-day ambient energy, Pa^2*h (cache of the minute ledger). */
        val weeklyPa2h: Double = 0.0,
        /** Rolling 7-day headphone energy, Pa^2*h (cache of the minute ledger). */
        val headphoneWeeklyPa2h: Double = 0.0,
        val peakDbc: Double = 0.0,
        val dayEpoch: Long = 0L,
        val mode: String = "URBAN",
        val sensitiveListener: Boolean = false,
        val monitoringEnabled: Boolean = true,
        val lastDba: Double = 0.0,
        /** Guided calibrations per capture path. Absent = provisional (uncalibrated) offset. */
        val calibrations: Map<MicSource, CalibrationRecord> = emptyMap(),
        /** Last time the dosimeter saved its ledger; 0 = never. */
        val heartbeatMs: Long = 0L,
        val alertSettings: AlertSettings = AlertSettings(),
        val alertMemory: AlertMemory = AlertMemory(),
        /** "My headphones are usually…"; null = not set. */
        val usualHeadphones: HeadphoneCategory? = null,
        /** Explicit opt-in to cloud AI (Tono). OFF by default. */
        val aiConsent: Boolean = false,
        val onboarded: Boolean = false,
    )

    val flow: Flow<Persisted> = ds.data.map { p ->
        Persisted(
            calibrationOffsetDb = p[Keys.calibration] ?: 0.0,
            dosePercent = p[Keys.dose] ?: 0.0,
            weeklyPa2h = p[Keys.weekly] ?: 0.0,
            headphoneWeeklyPa2h = p[Keys.headphoneWeekly] ?: 0.0,
            peakDbc = p[Keys.peak] ?: 0.0,
            dayEpoch = p[Keys.dayEpoch] ?: 0L,
            mode = p[Keys.mode] ?: "URBAN",
            sensitiveListener = p[Keys.sensitive] ?: false,
            monitoringEnabled = p[Keys.monitoring] ?: true,
            lastDba = p[Keys.lastDba] ?: 0.0,
            calibrations = MicSource.entries.mapNotNull { s ->
                val offset = p[Keys.calOffset(s)] ?: return@mapNotNull null
                s to CalibrationRecord(
                    source = s,
                    offsetDb = offset,
                    calibratedAtMs = p[Keys.calAt(s)] ?: 0L,
                    deviceModel = p[Keys.calModel(s)].orEmpty(),
                    referenceType = runCatching { ReferenceType.valueOf(p[Keys.calRef(s)].orEmpty()) }
                        .getOrDefault(ReferenceType.PHONE_APP),
                    secondOffsetDb = p[Keys.calSecond(s)],
                )
            }.toMap(),
            heartbeatMs = p[Keys.heartbeat] ?: 0L,
            alertSettings = AlertSettings(
                thresholdDba = (p[Keys.alertThreshold] ?: AlertDefaults.THRESHOLD_DBA)
                    .coerceIn(AlertDefaults.THRESHOLD_MIN_DBA, AlertDefaults.THRESHOLD_MAX_DBA),
                headphoneLoudDba = (p[Keys.headphoneLoud] ?: AlertDefaults.HEADPHONE_LOUD_DBA)
                    .coerceIn(AlertDefaults.HEADPHONE_LOUD_MIN_DBA, AlertDefaults.HEADPHONE_LOUD_MAX_DBA),
                quietHoursEnabled = p[Keys.quietEnabled] ?: false,
                quietStartMin = p[Keys.quietStart] ?: (22 * 60),
                quietEndMin = p[Keys.quietEnd] ?: (7 * 60),
                dailyCap = p[Keys.alertCap] ?: AlertDefaults.DAILY_CAP,
            ),
            alertMemory = AlertMemoryCodec.decode(p[Keys.alertMemory]),
            usualHeadphones = p[Keys.usualHeadphones]?.let { HeadphoneCategory.of(it) }
                ?.takeIf { it == HeadphoneCategory.EARBUDS || it == HeadphoneCategory.OVER_EAR },
            aiConsent = p[Keys.aiConsent] ?: false,
            onboarded = p[Keys.onboarded] ?: false,
        )
    }

    suspend fun read(): Persisted = flow.first()

    /**
     * Writes the running daily counters for [dayEpoch]. Refused when the store has already moved
     * on to a newer day (the midnight worker rolled first): writing would resurrect yesterday's dose.
     */
    suspend fun saveLedger(
        dosePercent: Double,
        weeklyPa2h: Double,
        headphoneWeeklyPa2h: Double,
        peakDbc: Double,
        dayEpoch: Long,
        lastDba: Double,
    ) = ds.edit {
        if (!acceptsWrite(it[Keys.dayEpoch] ?: 0L, dayEpoch)) return@edit
        it[Keys.dose] = dosePercent
        it[Keys.weekly] = weeklyPa2h
        it[Keys.headphoneWeekly] = headphoneWeeklyPa2h
        it[Keys.peak] = peakDbc
        it[Keys.dayEpoch] = dayEpoch
        it[Keys.lastDba] = lastDba
        it[Keys.heartbeat] = System.currentTimeMillis()
    }

    suspend fun heartbeat() = ds.edit { it[Keys.heartbeat] = System.currentTimeMillis() }

    /**
     * Closes the stored day if [today] has moved past it. Atomic and idempotent: the service and
     * the midnight worker may both call it; the day is closed once.
     *
     * @return true when a rollover actually happened.
     */
    suspend fun rollOverIfNeeded(today: Long): Boolean {
        var rolled = false
        ds.edit { p ->
            val stored = LedgerSnapshot(
                dayEpoch = p[Keys.dayEpoch] ?: 0L,
                dosePercent = p[Keys.dose] ?: 0.0,
                peakDbc = p[Keys.peak] ?: 0.0,
            )
            val next = rollover(stored, today) ?: return@edit
            rolled = stored.dayEpoch != 0L
            p[Keys.dayEpoch] = next.dayEpoch
            p[Keys.dose] = next.dosePercent
            p[Keys.peak] = next.peakDbc
        }
        return rolled
    }

    suspend fun setCalibration(offsetDb: Double) = ds.edit { it[Keys.calibration] = offsetDb }
    suspend fun setMode(mode: String) = ds.edit { it[Keys.mode] = mode }
    suspend fun setSensitive(v: Boolean) = ds.edit { it[Keys.sensitive] = v }
    suspend fun setMonitoring(v: Boolean) = ds.edit { it[Keys.monitoring] = v }
    suspend fun setAiConsent(v: Boolean) = ds.edit { it[Keys.aiConsent] = v }
    suspend fun setOnboarded(v: Boolean) = ds.edit { it[Keys.onboarded] = v }
    suspend fun setUsualHeadphones(c: HeadphoneCategory?) = ds.edit {
        if (c == null) it.remove(Keys.usualHeadphones) else it[Keys.usualHeadphones] = c.code
    }

    suspend fun saveAlertSettings(s: AlertSettings) = ds.edit {
        it[Keys.alertThreshold] = s.thresholdDba
        it[Keys.headphoneLoud] = s.headphoneLoudDba
        it[Keys.quietEnabled] = s.quietHoursEnabled
        it[Keys.quietStart] = s.quietStartMin
        it[Keys.quietEnd] = s.quietEndMin
        it[Keys.alertCap] = s.dailyCap
    }

    suspend fun saveAlertMemory(m: AlertMemory) = ds.edit { it[Keys.alertMemory] = AlertMemoryCodec.encode(m) }

    /** Read-modify-write, so a notification action cannot race the service's own write. */
    suspend fun updateAlertMemory(transform: (AlertMemory) -> AlertMemory) = ds.edit {
        it[Keys.alertMemory] = AlertMemoryCodec.encode(transform(AlertMemoryCodec.decode(it[Keys.alertMemory])))
    }

    suspend fun saveCalibration(r: CalibrationRecord) = ds.edit {
        it[Keys.calOffset(r.source)] = r.offsetDb
        it[Keys.calAt(r.source)] = r.calibratedAtMs
        it[Keys.calModel(r.source)] = r.deviceModel
        it[Keys.calRef(r.source)] = r.referenceType.name
        val second = r.secondOffsetDb
        if (second != null) it[Keys.calSecond(r.source)] = second else it.remove(Keys.calSecond(r.source))
    }

    suspend fun clearCalibration(source: MicSource) = ds.edit {
        it.remove(Keys.calOffset(source)); it.remove(Keys.calAt(source))
        it.remove(Keys.calModel(source)); it.remove(Keys.calRef(source))
        it.remove(Keys.calSecond(source))
    }

    suspend fun resetDose() = ds.edit {
        it[Keys.dose] = 0.0
        it[Keys.peak] = 0.0
    }
}
