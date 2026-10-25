package com.example.voxara.settings

import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.alerts.AlertDefaults
import com.example.voxara.core.alerts.AlertPolicy
import com.example.voxara.core.alerts.HapticStrength
import com.example.voxara.core.headphones.HeadphoneModeChoice
import com.example.voxara.core.situation.SituationChoice
import kotlin.math.roundToInt

/**
 * SETTINGS SHARED BY THE WATCH AND THE PHONE — versioned per field.
 *
 * Every field carries its value and the time it was last changed ([Stamped]). Either device can
 * change any field; [SettingsMerge] decides which change wins. The watch validates and clamps
 * every value it receives ([normalize]) and keeps working with the last settings it applied when
 * the phone is gone. Calibration is NOT here: it belongs to the watch's own microphone.
 */
enum class SettingField(val key: String) {
    ALERT_THRESHOLD("alert_threshold_dba"),
    HEADPHONE_LOUD("headphone_loud_dba"),
    SENSITIVE("sensitive_listener"),
    QUIET_ENABLED("quiet_enabled"),
    QUIET_START("quiet_start_min"),
    QUIET_END("quiet_end_min"),
    DAILY_CAP("daily_advisory_cap"),
    HAPTIC_STRENGTH("haptic_strength"),
    ADVISORY_HAPTICS("advisory_haptics"),
    SNOOZE_MINUTES("snooze_minutes"),
    HEADPHONE_TYPE("headphone_type"),
    LANGUAGE("language"),
    TONO_ENABLED("tono_enabled"),
    CLOUD_AI("cloud_ai_consent"),
    /** Auto or a fixed situation mode. The mode the watch is IN travels in WatchStatus, never here. */
    SITUATION_MODE("situation_mode"),
    /** Headphones mode: Auto (follow the detection), On or Off. A listening layer over the situation, never a situation. */
    HEADPHONE_MODE("headphone_mode");

    companion object {
        fun ofKey(key: String): SettingField? = entries.firstOrNull { it.key == key }
    }
}

/** "My headphones are usually…" as the settings store it. */
enum class HeadphoneType { EARBUDS, OVER_EAR, NONE }

/** App language as the settings store it (the watch's AppLanguage uses the same names). */
enum class LanguageChoice { SYSTEM, ENGLISH, SPANISH }

object SettingLimits {
    val THRESHOLD = AlertDefaults.THRESHOLD_MIN_DBA..AlertDefaults.THRESHOLD_MAX_DBA
    val HEADPHONE_LOUD = AlertDefaults.HEADPHONE_LOUD_MIN_DBA..AlertDefaults.HEADPHONE_LOUD_MAX_DBA
    val MINUTE_OF_DAY = 0..1439
    val DAILY_CAP = 1..10
    val SNOOZE = AlertPolicy.SNOOZE_MIN_MINUTES..AlertPolicy.SNOOZE_MAX_MINUTES
}

/** Canonical (validated, clamped) string for [raw], or null when it cannot be read at all. */
fun normalize(field: SettingField, raw: String?): String? {
    if (raw == null) return null
    fun int(range: IntRange): String? =
        raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(range)?.toString()
    fun bool(): String? = raw.trim().lowercase().toBooleanStrictOrNull()?.toString()
    fun <E : Enum<E>> enum(values: Array<E>): String? = values.firstOrNull { it.name == raw.trim() }?.name
    return when (field) {
        SettingField.ALERT_THRESHOLD -> int(SettingLimits.THRESHOLD)
        SettingField.HEADPHONE_LOUD -> int(SettingLimits.HEADPHONE_LOUD)
        SettingField.QUIET_START, SettingField.QUIET_END -> int(SettingLimits.MINUTE_OF_DAY)
        SettingField.DAILY_CAP -> int(SettingLimits.DAILY_CAP)
        SettingField.SNOOZE_MINUTES -> int(SettingLimits.SNOOZE)
        SettingField.SENSITIVE, SettingField.QUIET_ENABLED, SettingField.ADVISORY_HAPTICS,
        SettingField.TONO_ENABLED, SettingField.CLOUD_AI -> bool()
        SettingField.HAPTIC_STRENGTH -> enum(HapticStrength.entries.toTypedArray())
        SettingField.HEADPHONE_TYPE -> enum(HeadphoneType.entries.toTypedArray())
        SettingField.LANGUAGE -> enum(LanguageChoice.entries.toTypedArray())
        SettingField.SITUATION_MODE -> enum(SituationChoice.entries.toTypedArray())
        SettingField.HEADPHONE_MODE -> enum(HeadphoneModeChoice.entries.toTypedArray())
    }
}

/** One field's value and when it was last changed (ms since epoch, the changing device's clock). */
data class Stamped(val value: String, val atMs: Long)

/** A device's view of every field it has a value for. Missing fields mean "never changed". */
data class SettingsDoc(val fields: Map<SettingField, Stamped> = emptyMap()) {

    operator fun get(field: SettingField): Stamped? = fields[field]

    fun with(field: SettingField, value: String, atMs: Long): SettingsDoc {
        val v = normalize(field, value) ?: return this
        return copy(fields = fields + (field to Stamped(v, atMs)))
    }

    fun toJson(): String = MiniJson.write(
        linkedMapOf(
            "v" to PROTOCOL,
            "fields" to fields.entries.sortedBy { it.key.ordinal }.associate { (f, s) ->
                f.key to linkedMapOf("value" to s.value, "at" to s.atMs)
            },
        )
    )

    companion object {
        const val PROTOCOL = 1

        /** Unknown fields and unreadable values are dropped, never guessed. */
        fun fromJson(json: String?): SettingsDoc {
            if (json.isNullOrBlank()) return SettingsDoc()
            val root = runCatching { MiniJson.parse(json) as? Map<*, *> }.getOrNull() ?: return SettingsDoc()
            val raw = root["fields"] as? Map<*, *> ?: return SettingsDoc()
            val out = LinkedHashMap<SettingField, Stamped>()
            for ((k, v) in raw) {
                val field = SettingField.ofKey(k.toString()) ?: continue
                val entry = v as? Map<*, *> ?: continue
                val value = normalize(field, entry["value"]?.toString()) ?: continue
                val at = (entry["at"] as? Number)?.toLong() ?: continue
                out[field] = Stamped(value, at)
            }
            return SettingsDoc(out)
        }
    }
}

/** Typed view with defaults, for the UIs and the watch's stores. */
data class VoxaraSettings(
    val alertThresholdDba: Int = AlertDefaults.THRESHOLD_DBA,
    val headphoneLoudDba: Int = AlertDefaults.HEADPHONE_LOUD_DBA,
    val sensitiveListener: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val dailyCap: Int = AlertDefaults.DAILY_CAP,
    val hapticStrength: HapticStrength = HapticStrength.STANDARD,
    val advisoryHaptics: Boolean = true,
    val snoozeMinutes: Int = AlertPolicy.SNOOZE_DEFAULT_MINUTES,
    val headphoneType: HeadphoneType = HeadphoneType.NONE,
    val language: LanguageChoice = LanguageChoice.SYSTEM,
    val tonoEnabled: Boolean = true,
    val cloudAi: Boolean = false,
    val situationChoice: SituationChoice = SituationChoice.DEFAULT,
    val headphoneMode: HeadphoneModeChoice = HeadphoneModeChoice.DEFAULT,
) {
    fun valueOf(field: SettingField): String = when (field) {
        SettingField.ALERT_THRESHOLD -> alertThresholdDba.toString()
        SettingField.HEADPHONE_LOUD -> headphoneLoudDba.toString()
        SettingField.SENSITIVE -> sensitiveListener.toString()
        SettingField.QUIET_ENABLED -> quietHoursEnabled.toString()
        SettingField.QUIET_START -> quietStartMin.toString()
        SettingField.QUIET_END -> quietEndMin.toString()
        SettingField.DAILY_CAP -> dailyCap.toString()
        SettingField.HAPTIC_STRENGTH -> hapticStrength.name
        SettingField.ADVISORY_HAPTICS -> advisoryHaptics.toString()
        SettingField.SNOOZE_MINUTES -> snoozeMinutes.toString()
        SettingField.HEADPHONE_TYPE -> headphoneType.name
        SettingField.LANGUAGE -> language.name
        SettingField.TONO_ENABLED -> tonoEnabled.toString()
        SettingField.CLOUD_AI -> cloudAi.toString()
        SettingField.SITUATION_MODE -> situationChoice.name
        SettingField.HEADPHONE_MODE -> headphoneMode.name
    }

    companion object {
        val DEFAULTS = VoxaraSettings()

        /** Values from [doc], defaults for the fields it lacks. Everything is already normalized. */
        fun from(doc: SettingsDoc): VoxaraSettings {
            val d = DEFAULTS
            fun s(f: SettingField) = doc[f]?.value
            return VoxaraSettings(
                alertThresholdDba = s(SettingField.ALERT_THRESHOLD)?.toInt() ?: d.alertThresholdDba,
                headphoneLoudDba = s(SettingField.HEADPHONE_LOUD)?.toInt() ?: d.headphoneLoudDba,
                sensitiveListener = s(SettingField.SENSITIVE)?.toBoolean() ?: d.sensitiveListener,
                quietHoursEnabled = s(SettingField.QUIET_ENABLED)?.toBoolean() ?: d.quietHoursEnabled,
                quietStartMin = s(SettingField.QUIET_START)?.toInt() ?: d.quietStartMin,
                quietEndMin = s(SettingField.QUIET_END)?.toInt() ?: d.quietEndMin,
                dailyCap = s(SettingField.DAILY_CAP)?.toInt() ?: d.dailyCap,
                hapticStrength = s(SettingField.HAPTIC_STRENGTH)?.let { HapticStrength.valueOf(it) } ?: d.hapticStrength,
                advisoryHaptics = s(SettingField.ADVISORY_HAPTICS)?.toBoolean() ?: d.advisoryHaptics,
                snoozeMinutes = s(SettingField.SNOOZE_MINUTES)?.toInt() ?: d.snoozeMinutes,
                headphoneType = s(SettingField.HEADPHONE_TYPE)?.let { HeadphoneType.valueOf(it) } ?: d.headphoneType,
                language = s(SettingField.LANGUAGE)?.let { LanguageChoice.valueOf(it) } ?: d.language,
                tonoEnabled = s(SettingField.TONO_ENABLED)?.toBoolean() ?: d.tonoEnabled,
                cloudAi = s(SettingField.CLOUD_AI)?.toBoolean() ?: d.cloudAi,
                situationChoice = SituationChoice.parse(s(SettingField.SITUATION_MODE)) ?: d.situationChoice,
                headphoneMode = HeadphoneModeChoice.parse(s(SettingField.HEADPHONE_MODE)) ?: d.headphoneMode,
            )
        }
    }
}

/**
 * THE CONFLICT RULE — per field, the newest change wins; ties go to the watch.
 *
 * Two clocks are involved, so changes less than [SKEW_MS] apart count as a tie. A phone timestamp more
 * than [SKEW_MS] in the watch's future is NOT refused (refusing it lost the wearer's change for good: the
 * Data Layer delivers an unchanged item only once): it is accepted with the watch's own "now" as its time,
 * so a phone whose clock runs ahead (or is simply wrong) can neither lose a change nor win every later
 * comparison. The same future-dated phone value is accepted ONCE: the watch remembers the phone's own stamp
 * ([Result.futureSeen]) and a redelivery of it (the pull on every reconnect) is not a new change, so it cannot
 * undo something the wearer set on the watch since.
 */
object SettingsMerge {
    const val SKEW_MS = 5_000L

    data class Result(
        val merged: SettingsDoc,
        /** Fields whose value came from the other device. */
        val accepted: Set<SettingField>,
        /** Fields accepted with their timestamp pulled back to the receiver's now (it was in the receiver's future). */
        val clampedFuture: Set<SettingField>,
        /** The phone's own (future) stamp of each field accepted that way; the caller keeps it and passes it back next time. */
        val futureSeen: Map<SettingField, Long> = emptyMap(),
    )

    /**
     * The watch applying the phone's settings: the phone must be newer by more than [SKEW_MS],
     * future timestamps are pulled back to the watch's now, every value is normalized (clamped) first.
     */
    fun watchReceives(
        watch: SettingsDoc, phone: SettingsDoc, watchNowMs: Long,
        /** Future-dated phone stamps already accepted once ([Result.futureSeen] of the earlier calls). */
        futureSeen: Map<SettingField, Long> = emptyMap(),
    ): Result = merge(own = watch, incoming = phone, incomingIsWatch = false, receiverNowMs = watchNowMs, seen = futureSeen)

    /** The phone taking the watch's settings: the watch wins ties. */
    fun phoneReceives(phone: SettingsDoc, watch: SettingsDoc, phoneNowMs: Long): Result =
        merge(own = phone, incoming = watch, incomingIsWatch = true, receiverNowMs = phoneNowMs)

    private fun merge(
        own: SettingsDoc, incoming: SettingsDoc, incomingIsWatch: Boolean, receiverNowMs: Long,
        seen: Map<SettingField, Long> = emptyMap(),
    ): Result {
        val out = LinkedHashMap(own.fields)
        val accepted = LinkedHashSet<SettingField>()
        val clamped = LinkedHashSet<SettingField>()
        val clampedStamps = LinkedHashMap<SettingField, Long>()
        for ((field, stamped) in incoming.fields) {
            val value = normalize(field, stamped.value) ?: continue
            var theirs = stamped
            if (!incomingIsWatch && theirs.atMs > receiverNowMs + SKEW_MS) {
                if (seen[field] == stamped.atMs) continue      // this very change was accepted before: a redelivery
                clamped += field
                clampedStamps[field] = stamped.atMs
                theirs = theirs.copy(atMs = receiverNowMs)
            }
            val mine = own[field]
            val wins = when {
                mine == null -> true
                incomingIsWatch -> theirs.atMs >= mine.atMs - SKEW_MS   // ties go to the watch
                else -> theirs.atMs > mine.atMs + SKEW_MS               // the phone must be clearly newer
            }
            if (wins && (mine == null || mine.value != value || mine.atMs != theirs.atMs)) {
                out[field] = Stamped(value, theirs.atMs)
                if (mine?.value != value) accepted += field
            }
        }
        return Result(SettingsDoc(out), accepted, clamped, clampedStamps)
    }

    /**
     * Phone changes the watch has not applied yet (shown as "Pending sync"): newer on the phone AND a
     * different value. A watch that already holds the same value has nothing to apply, whatever the clocks say
     * (it stamps an accepted value with its own time when the phone's was ahead).
     */
    fun pending(phone: SettingsDoc, watch: SettingsDoc?): Set<SettingField> =
        phone.fields.filter { (field, mine) ->
            val theirs = watch?.get(field)
            theirs == null || (mine.atMs > theirs.atMs + SKEW_MS && mine.value != theirs.value)
        }.keys
}
