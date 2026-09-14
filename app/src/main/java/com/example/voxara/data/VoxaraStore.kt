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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("voxara")

/**
 * DataStore holds the calibration offset, preferences and the rolled-up ledger.
 * Minute-level Leq lives here as a compact CSV of 24 hourly energy-averages per day —
 * enough to redraw the polar clock and the week scrub without a database on a 300 mAh watch.
 */
class VoxaraStore(context: Context) {

    private val ds = context.applicationContext.dataStore

    private object Keys {
        val calibration = doublePreferencesKey("calibration_offset_db")
        val dose = doublePreferencesKey("dose_percent")
        val weekly = doublePreferencesKey("weekly_pa2h")
        val peak = doublePreferencesKey("peak_dbc")
        val dayEpoch = longPreferencesKey("day_epoch")
        val weekEpoch = longPreferencesKey("week_epoch")
        val today = stringPreferencesKey("today_profile")
        val week = stringPreferencesKey("week_profiles")
        val mode = stringPreferencesKey("mode")
        val sensitive = booleanPreferencesKey("sensitive_listener")
        val alertCap = intPreferencesKey("daily_alert_cap")
        val monitoring = booleanPreferencesKey("monitoring_enabled")
        val lastDba = doublePreferencesKey("last_dba")
    }

    data class Persisted(
        val calibrationOffsetDb: Double = 0.0,
        val dosePercent: Double = 0.0,
        val weeklyPa2h: Double = 0.0,
        val peakDbc: Double = 0.0,
        val dayEpoch: Long = 0L,
        val weekEpoch: Long = 0L,
        val todayProfile: List<Float> = List(24) { 0f },
        val weekProfiles: List<List<Float>> = emptyList(),
        val mode: String = "URBAN",
        val sensitiveListener: Boolean = false,
        val dailyAlertCap: Int = 4,
        val monitoringEnabled: Boolean = true,
        val lastDba: Double = 0.0,
    )

    val flow: Flow<Persisted> = ds.data.map { p ->
        Persisted(
            calibrationOffsetDb = p[Keys.calibration] ?: 0.0,
            dosePercent = p[Keys.dose] ?: 0.0,
            weeklyPa2h = p[Keys.weekly] ?: 0.0,
            peakDbc = p[Keys.peak] ?: 0.0,
            dayEpoch = p[Keys.dayEpoch] ?: 0L,
            weekEpoch = p[Keys.weekEpoch] ?: 0L,
            todayProfile = decodeDay(p[Keys.today]),
            weekProfiles = decodeWeek(p[Keys.week]),
            mode = p[Keys.mode] ?: "URBAN",
            sensitiveListener = p[Keys.sensitive] ?: false,
            dailyAlertCap = p[Keys.alertCap] ?: 4,
            monitoringEnabled = p[Keys.monitoring] ?: true,
            lastDba = p[Keys.lastDba] ?: 0.0,
        )
    }

    suspend fun read(): Persisted = flow.first()

    suspend fun saveLedger(
        dosePercent: Double,
        weeklyPa2h: Double,
        peakDbc: Double,
        dayEpoch: Long,
        weekEpoch: Long,
        todayProfile: List<Float>,
        lastDba: Double,
    ) = ds.edit {
        it[Keys.dose] = dosePercent
        it[Keys.weekly] = weeklyPa2h
        it[Keys.peak] = peakDbc
        it[Keys.dayEpoch] = dayEpoch
        it[Keys.weekEpoch] = weekEpoch
        it[Keys.today] = encodeDay(todayProfile)
        it[Keys.lastDba] = lastDba
    }

    suspend fun rollOverDay(finishedDay: List<Float>) = ds.edit { p ->
        val week = (decodeWeek(p[Keys.week]) + listOf(finishedDay)).takeLast(7)
        p[Keys.week] = encodeWeek(week)
        p[Keys.today] = encodeDay(List(24) { 0f })
        p[Keys.dose] = 0.0
        p[Keys.peak] = 0.0
    }

    suspend fun setCalibration(offsetDb: Double) = ds.edit { it[Keys.calibration] = offsetDb }
    suspend fun setMode(mode: String) = ds.edit { it[Keys.mode] = mode }
    suspend fun setSensitive(v: Boolean) = ds.edit { it[Keys.sensitive] = v }
    suspend fun setAlertCap(v: Int) = ds.edit { it[Keys.alertCap] = v }
    suspend fun setMonitoring(v: Boolean) = ds.edit { it[Keys.monitoring] = v }

    suspend fun resetDose() = ds.edit {
        it[Keys.dose] = 0.0
        it[Keys.peak] = 0.0
        it[Keys.today] = encodeDay(List(24) { 0f })
    }

    private fun encodeDay(p: List<Float>) = p.joinToString(",") { it.toString() }

    private fun decodeDay(s: String?): List<Float> {
        val parts = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
        return if (parts.size == 24) parts else List(24) { 0f }
    }

    private fun encodeWeek(w: List<List<Float>>) = w.joinToString(";") { encodeDay(it) }

    private fun decodeWeek(s: String?): List<List<Float>> =
        s?.split(";")?.filter { it.isNotBlank() }?.map { decodeDay(it) } ?: emptyList()
}
