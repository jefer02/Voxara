package com.example.voxara.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.HapticPrefs
import com.example.voxara.core.alerts.HapticStrength
import com.example.voxara.core.alerts.AlertMemory
import com.example.voxara.core.alerts.AlertMemoryCodec
import com.example.voxara.core.alerts.AlertSettings
import com.example.voxara.core.calibration.CalibrationRecord
import com.example.voxara.core.calibration.MicSource
import com.example.voxara.core.calibration.ReferenceType
import com.example.voxara.core.headphones.HeadphoneCategory
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.ledger.LedgerSnapshot
import com.example.voxara.core.ledger.acceptsWrite
import com.example.voxara.core.ledger.rollover
import com.example.voxara.core.situation.Situation
import com.example.voxara.core.situation.SituationChoice
import com.example.voxara.settings.HeadphoneType
import com.example.voxara.settings.LanguageChoice
import com.example.voxara.settings.SettingField
import com.example.voxara.settings.SettingsDoc
import com.example.voxara.settings.Stamped
import com.example.voxara.settings.VoxaraSettings
import com.example.voxara.settings.normalize
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
        /** Before situation modes: URBAN / CONCERT / VOICE. Read once to migrate, then removed. */
        val legacyMode = stringPreferencesKey("mode")
        val situationChoice = stringPreferencesKey("situation_choice")
        /** Set while Concert is chosen: when it started (for the 6 h cap) and what to go back to. */
        val concertStartedAt = longPreferencesKey("concert_started_at")
        val choiceBeforeConcert = stringPreferencesKey("situation_before_concert")
        val headphoneMode = stringPreferencesKey("headphone_mode")
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
        val hapticStrength = stringPreferencesKey("haptic_strength")
        val advisoryHaptics = booleanPreferencesKey("advisory_haptics")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val tonoEnabled = booleanPreferencesKey("tono_enabled")
        /** When each synced setting last changed (either device's change time), for the conflict rule. */
        fun stamp(f: SettingField) = longPreferencesKey("settings_at_${f.key}")
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
        /** Auto or a fixed situation mode (synced with the phone). */
        val situationChoice: SituationChoice = SituationChoice.DEFAULT,
        /** When Concert was chosen; null when Concert is not the choice. */
        val concertStartedAtMs: Long? = null,
        /** What leaving Concert returns to; null = Auto. */
        val choiceBeforeConcert: SituationChoice? = null,
        /** Headphones mode: Auto (follow the detection), On or Off (synced with the phone). */
        val headphoneMode: HeadphoneModeChoice = HeadphoneModeChoice.DEFAULT,
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
        /** Haptic preferences; the floors in AlertPolicy still apply (warnings and limits always vibrate). */
        val hapticPrefs: HapticPrefs = HapticPrefs(),
        /** Snooze length, 15-120 min; LIMIT alerts are never snoozed. */
        val snoozeMinutes: Int = AlertPolicy.SNOOZE_DEFAULT_MINUTES,
        /** Tono shown on the watch. Off hides it; voice commands keep working. */
        val tonoEnabled: Boolean = true,
        /** Last change time per synced setting; absent = never changed (the default). */
        val settingStamps: Map<SettingField, Long> = emptyMap(),
    )

    val flow: Flow<Persisted> = ds.data.map { p ->
        Persisted(
            calibrationOffsetDb = p[Keys.calibration] ?: 0.0,
            dosePercent = p[Keys.dose] ?: 0.0,
            weeklyPa2h = p[Keys.weekly] ?: 0.0,
            headphoneWeeklyPa2h = p[Keys.headphoneWeekly] ?: 0.0,
            peakDbc = p[Keys.peak] ?: 0.0,
            dayEpoch = p[Keys.dayEpoch] ?: 0L,
            // Until migrate() has run, the pre-modes value is read through the same mapping.
            situationChoice = SituationChoice.parse(p[Keys.situationChoice])
                ?: Situation.migrateLegacyMode(p[Keys.legacyMode]) ?: SituationChoice.DEFAULT,
            concertStartedAtMs = p[Keys.concertStartedAt],
            choiceBeforeConcert = SituationChoice.parse(p[Keys.choiceBeforeConcert]),
            headphoneMode = HeadphoneModeChoice.parse(p[Keys.headphoneMode]) ?: HeadphoneModeChoice.DEFAULT,
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
            hapticPrefs = HapticPrefs(
                strength = runCatching { HapticStrength.valueOf(p[Keys.hapticStrength].orEmpty()) }
                    .getOrDefault(HapticStrength.STANDARD),
                advisoryHaptics = p[Keys.advisoryHaptics] ?: true,
            ),
            snoozeMinutes = (p[Keys.snoozeMinutes] ?: AlertPolicy.SNOOZE_DEFAULT_MINUTES)
                .coerceIn(AlertPolicy.SNOOZE_MIN_MINUTES, AlertPolicy.SNOOZE_MAX_MINUTES),
            tonoEnabled = p[Keys.tonoEnabled] ?: true,
            settingStamps = SettingField.entries.mapNotNull { f -> p[Keys.stamp(f)]?.let { f to it } }.toMap(),
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
    /**
     * One-time move from the pre-modes "mode" key: URBAN and VOICE become Street, CONCERT stays
     * Concert (its 6 h cap counts from now: the old start time was never stored). Not stamped as a
     * change, so a choice made on the phone still wins. Idempotent.
     */
    suspend fun migrateSituation() = ds.edit { p ->
        val legacy = p[Keys.legacyMode] ?: return@edit
        if (p[Keys.situationChoice] == null) {
            Situation.migrateLegacyMode(legacy)?.let { choice ->
                p[Keys.situationChoice] = choice.name
                if (choice == SituationChoice.CONCERT) p[Keys.concertStartedAt] = now()
            }
        }
        p.remove(Keys.legacyMode)
    }

    /** Tests only: the store as an install from before situation modes left it ([legacy] null = never set). */
    @VisibleForTesting
    internal suspend fun seedLegacyMode(legacy: String?) = ds.edit {
        if (legacy == null) it.remove(Keys.legacyMode) else it[Keys.legacyMode] = legacy
        it.remove(Keys.situationChoice)
        it.remove(Keys.concertStartedAt)
        it.remove(Keys.choiceBeforeConcert)
        it.remove(Keys.stamp(SettingField.SITUATION_MODE))
    }

    /** The wearer chose [choice] (on the watch). Stamped, so it syncs to the phone. */
    suspend fun setSituationChoice(choice: SituationChoice) = ds.edit {
        applyChoice(it, choice, now())
        it[Keys.stamp(SettingField.SITUATION_MODE)] = now()
    }

    /**
     * Leaves Concert (the 6 h cap, 20 quiet minutes, or the wearer) for what was chosen before it.
     * Stamped, so the phone follows. @return the new choice, or null when Concert was not chosen.
     */
    suspend fun leaveConcert(): SituationChoice? {
        var next: SituationChoice? = null
        ds.edit { p ->
            if (SituationChoice.parse(p[Keys.situationChoice]) != SituationChoice.CONCERT) return@edit
            val back = SituationChoice.parse(p[Keys.choiceBeforeConcert])
                ?.takeIf { it != SituationChoice.CONCERT } ?: SituationChoice.DEFAULT
            applyChoice(p, back, now())
            p[Keys.stamp(SettingField.SITUATION_MODE)] = now()
            next = back
        }
        return next
    }

    /** Entering Concert remembers when and what came before; leaving it clears both. */
    private fun applyChoice(p: MutablePreferences, next: SituationChoice, nowMs: Long) {
        val previous = SituationChoice.parse(p[Keys.situationChoice]) ?: SituationChoice.DEFAULT
        if (next == SituationChoice.CONCERT) {
            if (previous != SituationChoice.CONCERT) {
                p[Keys.concertStartedAt] = nowMs
                p[Keys.choiceBeforeConcert] = previous.name
            }
        } else {
            p.remove(Keys.concertStartedAt)
            p.remove(Keys.choiceBeforeConcert)
        }
        p[Keys.situationChoice] = next.name
    }
    /** The wearer chose [choice] for headphones mode (on the watch). Stamped, so it syncs to the phone. */
    suspend fun setHeadphoneMode(choice: HeadphoneModeChoice) = ds.edit {
        it[Keys.headphoneMode] = choice.name
        it[Keys.stamp(SettingField.HEADPHONE_MODE)] = now()
    }

    suspend fun setSensitive(v: Boolean) = ds.edit { it[Keys.sensitive] = v; it[Keys.stamp(SettingField.SENSITIVE)] = now() }
    suspend fun setMonitoring(v: Boolean) = ds.edit { it[Keys.monitoring] = v }
    suspend fun setAiConsent(v: Boolean) = ds.edit { it[Keys.aiConsent] = v; it[Keys.stamp(SettingField.CLOUD_AI)] = now() }
    suspend fun setOnboarded(v: Boolean) = ds.edit { it[Keys.onboarded] = v }
    suspend fun setUsualHeadphones(c: HeadphoneCategory?) = ds.edit {
        if (c == null) it.remove(Keys.usualHeadphones) else it[Keys.usualHeadphones] = c.code
        it[Keys.stamp(SettingField.HEADPHONE_TYPE)] = now()
    }
    suspend fun setTonoEnabled(v: Boolean) = ds.edit { it[Keys.tonoEnabled] = v; it[Keys.stamp(SettingField.TONO_ENABLED)] = now() }
    suspend fun setSnoozeMinutes(v: Int) = ds.edit {
        it[Keys.snoozeMinutes] = v.coerceIn(AlertPolicy.SNOOZE_MIN_MINUTES, AlertPolicy.SNOOZE_MAX_MINUTES)
        it[Keys.stamp(SettingField.SNOOZE_MINUTES)] = now()
    }
    suspend fun setHapticPrefs(h: HapticPrefs) = ds.edit {
        val t = now()
        if (it[Keys.hapticStrength] != h.strength.name) it[Keys.stamp(SettingField.HAPTIC_STRENGTH)] = t
        if ((it[Keys.advisoryHaptics] ?: true) != h.advisoryHaptics) it[Keys.stamp(SettingField.ADVISORY_HAPTICS)] = t
        it[Keys.hapticStrength] = h.strength.name
        it[Keys.advisoryHaptics] = h.advisoryHaptics
    }

    /** The language lives in LocaleStore; its change time lives here with the other settings. */
    suspend fun stampLanguage() = ds.edit { it[Keys.stamp(SettingField.LANGUAGE)] = now() }

    suspend fun saveAlertSettings(s: AlertSettings) = ds.edit {
        // Stamp only what changed, so an unrelated edit never wins a conflict for these fields.
        val t = now()
        fun stampIf(changed: Boolean, f: SettingField) { if (changed) it[Keys.stamp(f)] = t }
        val d = AlertSettings()
        stampIf((it[Keys.alertThreshold] ?: d.thresholdDba) != s.thresholdDba, SettingField.ALERT_THRESHOLD)
        stampIf((it[Keys.headphoneLoud] ?: d.headphoneLoudDba) != s.headphoneLoudDba, SettingField.HEADPHONE_LOUD)
        stampIf((it[Keys.quietEnabled] ?: d.quietHoursEnabled) != s.quietHoursEnabled, SettingField.QUIET_ENABLED)
        stampIf((it[Keys.quietStart] ?: d.quietStartMin) != s.quietStartMin, SettingField.QUIET_START)
        stampIf((it[Keys.quietEnd] ?: d.quietEndMin) != s.quietEndMin, SettingField.QUIET_END)
        stampIf((it[Keys.alertCap] ?: d.dailyCap) != s.dailyCap, SettingField.DAILY_CAP)
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

    /**
     * "Delete all data" from the phone: the running counters and the alert memory go with the
     * minute ledger. Settings, calibration and onboarding stay.
     */
    suspend fun clearExposure() = ds.edit {
        it[Keys.dose] = 0.0
        it[Keys.weekly] = 0.0
        it[Keys.headphoneWeekly] = 0.0
        it[Keys.peak] = 0.0
        it[Keys.lastDba] = 0.0
        it[Keys.alertMemory] = AlertMemoryCodec.encode(AlertMemory())
    }

    /**
     * Writes settings accepted from the phone, each with the phone's change time, so the watch's
     * published document echoes it and the phone sees the change as applied. Every value is
     * normalized (validated and clamped) again here. [SettingField.LANGUAGE] is applied by the
     * caller (LocaleStore); only its time is stored here.
     */
    suspend fun applySynced(fields: Map<SettingField, Stamped>) = ds.edit { p ->
        for ((f, stamped) in fields) {
            val v = normalize(f, stamped.value) ?: continue
            when (f) {
                SettingField.ALERT_THRESHOLD -> p[Keys.alertThreshold] = v.toInt()
                SettingField.HEADPHONE_LOUD -> p[Keys.headphoneLoud] = v.toInt()
                SettingField.SENSITIVE -> p[Keys.sensitive] = v.toBoolean()
                SettingField.QUIET_ENABLED -> p[Keys.quietEnabled] = v.toBoolean()
                SettingField.QUIET_START -> p[Keys.quietStart] = v.toInt()
                SettingField.QUIET_END -> p[Keys.quietEnd] = v.toInt()
                SettingField.DAILY_CAP -> p[Keys.alertCap] = v.toInt()
                SettingField.HAPTIC_STRENGTH -> p[Keys.hapticStrength] = v
                SettingField.ADVISORY_HAPTICS -> p[Keys.advisoryHaptics] = v.toBoolean()
                SettingField.SNOOZE_MINUTES -> p[Keys.snoozeMinutes] = v.toInt()
                SettingField.HEADPHONE_TYPE -> when (HeadphoneType.valueOf(v)) {
                    HeadphoneType.EARBUDS -> p[Keys.usualHeadphones] = HeadphoneCategory.EARBUDS.code
                    HeadphoneType.OVER_EAR -> p[Keys.usualHeadphones] = HeadphoneCategory.OVER_EAR.code
                    HeadphoneType.NONE -> p.remove(Keys.usualHeadphones)
                }
                SettingField.LANGUAGE -> Unit
                SettingField.TONO_ENABLED -> p[Keys.tonoEnabled] = v.toBoolean()
                SettingField.CLOUD_AI -> p[Keys.aiConsent] = v.toBoolean()
                SettingField.SITUATION_MODE -> applyChoice(p, SituationChoice.valueOf(v), now())
                SettingField.HEADPHONE_MODE -> p[Keys.headphoneMode] = v
            }
            p[Keys.stamp(f)] = stamped.atMs
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        /**
         * The watch's settings as the phone sees them: every field, with its last change time
         * (0 = never changed, so any real change on the phone is newer).
         */
        fun settingsDoc(p: Persisted, language: LanguageChoice): SettingsDoc {
            val a = p.alertSettings
            val typed = VoxaraSettings(
                alertThresholdDba = a.thresholdDba,
                headphoneLoudDba = a.headphoneLoudDba,
                sensitiveListener = p.sensitiveListener,
                quietHoursEnabled = a.quietHoursEnabled,
                quietStartMin = a.quietStartMin,
                quietEndMin = a.quietEndMin,
                dailyCap = a.dailyCap,
                hapticStrength = p.hapticPrefs.strength,
                advisoryHaptics = p.hapticPrefs.advisoryHaptics,
                snoozeMinutes = p.snoozeMinutes,
                headphoneType = when (p.usualHeadphones) {
                    HeadphoneCategory.EARBUDS -> HeadphoneType.EARBUDS
                    HeadphoneCategory.OVER_EAR -> HeadphoneType.OVER_EAR
                    else -> HeadphoneType.NONE
                },
                language = language,
                tonoEnabled = p.tonoEnabled,
                cloudAi = p.aiConsent,
                situationChoice = p.situationChoice,
                headphoneMode = p.headphoneMode,
            )
            return SettingField.entries.fold(SettingsDoc()) { doc, f ->
                doc.with(f, typed.valueOf(f), p.settingStamps[f] ?: 0L)
            }
        }
    }
}
