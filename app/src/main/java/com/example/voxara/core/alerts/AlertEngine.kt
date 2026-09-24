package com.example.voxara.core.alerts

import kotlin.math.floor

/**
 * ALERT ENGINE — deterministic, offline, independent of any AI. Pure Kotlin, fully unit-tested.
 *
 * Three tiers:
 *  - LIMIT     daily dose 100%, weekly (rolling 7-day) 100% for either ledger. NEVER rate-limited:
 *              no daily cap, no snooze, no quiet hours, no cooldown. Re-alerts at every further
 *              +100% (200%, 300%, ...).
 *  - WARNING   80% tiers and "very loud listening right now". Not counted against the daily cap;
 *              snoozable; held (not dropped) during quiet hours.
 *  - ADVISORY  sustained ambient level over the wearer's threshold, daily dose 50%. Counted
 *              against the daily cap; snoozable; held during quiet hours.
 *
 * "Held" means the alert is simply not fired while quiet hours last; if the condition still holds
 * afterwards it fires then.
 */
enum class Tier { ADVISORY, WARNING, LIMIT }

enum class AlertKind(val tier: Tier) {
    AMBIENT_LOUD(Tier.ADVISORY),
    DAILY_50(Tier.ADVISORY),
    DAILY_80(Tier.WARNING),
    DAILY_100(Tier.LIMIT),
    WEEK_80(Tier.WARNING),
    WEEK_100(Tier.LIMIT),
    HEADPHONE_WEEK_80(Tier.WARNING),
    HEADPHONE_WEEK_100(Tier.LIMIT),
    HEADPHONE_LOUD_NOW(Tier.WARNING);

    val snoozable: Boolean get() = tier != Tier.LIMIT
}

object AlertDefaults {
    const val THRESHOLD_DBA = 85
    const val THRESHOLD_MIN_DBA = 80
    const val THRESHOLD_MAX_DBA = 95
    /** Sustained window for the ambient level alert. */
    const val SUSTAINED_WINDOW_MIN = 3
    /** Sustained window for "very loud listening". */
    const val HEADPHONE_WINDOW_MIN = 5
    const val HEADPHONE_LOUD_DBA = 100
    const val HEADPHONE_LOUD_MIN_DBA = 95
    const val HEADPHONE_LOUD_MAX_DBA = 105
    const val COOLDOWN_MS = 30L * 60 * 1000
    const val SNOOZE_MS = 60L * 60 * 1000
    /** A level alert re-arms only after dropping this far below its threshold. */
    const val REARM_HYSTERESIS_DB = 5.0
    /** An 80% weekly warning re-arms only after the rolling dose falls below this. */
    const val WEEK_REARM_PERCENT = 70.0
    const val DAILY_CAP = 4
}

data class AlertSettings(
    val thresholdDba: Int = AlertDefaults.THRESHOLD_DBA,
    val headphoneLoudDba: Int = AlertDefaults.HEADPHONE_LOUD_DBA,
    val quietHoursEnabled: Boolean = false,
    /** Minutes after local midnight. */
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val dailyCap: Int = AlertDefaults.DAILY_CAP,
)

data class AlertInputs(
    val nowMs: Long,
    val localDay: Long,
    /** Minutes after local midnight. */
    val localMinuteOfDay: Int,
    /** Sliding ambient Leq over the sustained window, or null while there is not enough data. */
    val sustainedAmbientDba: Double?,
    val dailyDosePercent: Double,
    val weeklyAmbientPercent: Double,
    val weeklyHeadphonePercent: Double = 0.0,
    val sustainedHeadphoneDba: Double? = null,
)

/** One alert to deliver. [level] is the dose multiple (100, 200, ...) for LIMIT alerts. */
data class Alert(val kind: AlertKind, val value: Double, val level: Int = 0)

/** Everything the engine must remember between evaluations (persisted). */
data class AlertMemory(
    val day: Long = 0L,
    val advisoriesToday: Int = 0,
    val daily50Fired: Boolean = false,
    val daily80Fired: Boolean = false,
    val dailyLimitLevel: Int = 0,
    val week80Armed: Boolean = true,
    val weekLimitLevel: Int = 0,
    val hpWeek80Armed: Boolean = true,
    val hpWeekLimitLevel: Int = 0,
    val ambientLoudArmed: Boolean = true,
    val lastAmbientLoudMs: Long = 0L,
    val hpLoudArmed: Boolean = true,
    val lastHpLoudMs: Long = 0L,
    val snoozedUntil: Map<AlertKind, Long> = emptyMap(),
)

fun inQuietHours(s: AlertSettings, minuteOfDay: Int): Boolean {
    if (!s.quietHoursEnabled || s.quietStartMin == s.quietEndMin) return false
    return if (s.quietStartMin < s.quietEndMin) minuteOfDay in s.quietStartMin until s.quietEndMin
    else minuteOfDay >= s.quietStartMin || minuteOfDay < s.quietEndMin
}

/** 0 means "never fired". */
private fun cooledDown(lastMs: Long, nowMs: Long) = lastMs == 0L || nowMs - lastMs >= AlertDefaults.COOLDOWN_MS

/** Largest multiple of 100 at or below [percent] (0 below 100). */
fun limitLevel(percent: Double): Int = if (percent < 100.0) 0 else (floor(percent / 100.0) * 100).toInt()

object AlertEngine {

    /**
     * @return the alerts to deliver now (most severe first) and the updated memory.
     */
    fun evaluate(
        inputs: AlertInputs,
        settings: AlertSettings,
        memory: AlertMemory,
    ): Pair<List<Alert>, AlertMemory> {
        var m = if (memory.day != inputs.localDay) {
            // New local day: daily tiers and the daily cap start over; weekly state carries on.
            memory.copy(
                day = inputs.localDay,
                advisoriesToday = 0,
                daily50Fired = false,
                daily80Fired = false,
                dailyLimitLevel = 0,
            )
        } else memory
        val out = ArrayList<Alert>()
        val now = inputs.nowMs
        val quiet = inQuietHours(settings, inputs.localMinuteOfDay)
        fun snoozed(k: AlertKind) = (m.snoozedUntil[k] ?: 0L) > now
        fun holdable(k: AlertKind) = !quiet && !snoozed(k)

        // ---------------------------------------------------------------- LIMIT (never limited)
        val dailyLevel = limitLevel(inputs.dailyDosePercent)
        if (dailyLevel > m.dailyLimitLevel) {
            out += Alert(AlertKind.DAILY_100, inputs.dailyDosePercent, dailyLevel)
            m = m.copy(dailyLimitLevel = dailyLevel, daily80Fired = true, daily50Fired = true)
        }
        val weekLevel = limitLevel(inputs.weeklyAmbientPercent)
        if (weekLevel > m.weekLimitLevel) {
            out += Alert(AlertKind.WEEK_100, inputs.weeklyAmbientPercent, weekLevel)
            m = m.copy(weekLimitLevel = weekLevel, week80Armed = false)
        } else if (weekLevel < m.weekLimitLevel) {
            // The rolling window let go of old exposure: crossing again alerts again.
            m = m.copy(weekLimitLevel = weekLevel)
        }
        val hpLevel = limitLevel(inputs.weeklyHeadphonePercent)
        if (hpLevel > m.hpWeekLimitLevel) {
            out += Alert(AlertKind.HEADPHONE_WEEK_100, inputs.weeklyHeadphonePercent, hpLevel)
            m = m.copy(hpWeekLimitLevel = hpLevel, hpWeek80Armed = false)
        } else if (hpLevel < m.hpWeekLimitLevel) {
            m = m.copy(hpWeekLimitLevel = hpLevel)
        }

        // ---------------------------------------------------------------- WARNING
        if (inputs.dailyDosePercent >= 80.0 && !m.daily80Fired && holdable(AlertKind.DAILY_80)) {
            out += Alert(AlertKind.DAILY_80, inputs.dailyDosePercent)
            m = m.copy(daily80Fired = true, daily50Fired = true)
        }
        if (inputs.weeklyAmbientPercent < AlertDefaults.WEEK_REARM_PERCENT) m = m.copy(week80Armed = true)
        if (inputs.weeklyAmbientPercent in 80.0..<100.0 && m.week80Armed && holdable(AlertKind.WEEK_80)) {
            out += Alert(AlertKind.WEEK_80, inputs.weeklyAmbientPercent)
            m = m.copy(week80Armed = false)
        }
        if (inputs.weeklyHeadphonePercent < AlertDefaults.WEEK_REARM_PERCENT) m = m.copy(hpWeek80Armed = true)
        if (inputs.weeklyHeadphonePercent in 80.0..<100.0 && m.hpWeek80Armed &&
            holdable(AlertKind.HEADPHONE_WEEK_80)
        ) {
            out += Alert(AlertKind.HEADPHONE_WEEK_80, inputs.weeklyHeadphonePercent)
            m = m.copy(hpWeek80Armed = false)
        }
        inputs.sustainedHeadphoneDba?.let { hp ->
            if (hp < settings.headphoneLoudDba - AlertDefaults.REARM_HYSTERESIS_DB) m = m.copy(hpLoudArmed = true)
            if (hp >= settings.headphoneLoudDba && m.hpLoudArmed &&
                cooledDown(m.lastHpLoudMs, now) && holdable(AlertKind.HEADPHONE_LOUD_NOW)
            ) {
                out += Alert(AlertKind.HEADPHONE_LOUD_NOW, hp)
                m = m.copy(hpLoudArmed = false, lastHpLoudMs = now)
            }
        }

        // ---------------------------------------------------------------- ADVISORY (capped)
        fun capLeft() = m.advisoriesToday < settings.dailyCap
        inputs.sustainedAmbientDba?.let { amb ->
            if (amb < settings.thresholdDba - AlertDefaults.REARM_HYSTERESIS_DB) m = m.copy(ambientLoudArmed = true)
            if (amb >= settings.thresholdDba && m.ambientLoudArmed &&
                cooledDown(m.lastAmbientLoudMs, now) &&
                holdable(AlertKind.AMBIENT_LOUD) && capLeft()
            ) {
                out += Alert(AlertKind.AMBIENT_LOUD, amb)
                m = m.copy(
                    ambientLoudArmed = false,
                    lastAmbientLoudMs = now,
                    advisoriesToday = m.advisoriesToday + 1,
                )
            }
        }
        if (inputs.dailyDosePercent >= 50.0 && !m.daily50Fired && holdable(AlertKind.DAILY_50) && capLeft()) {
            out += Alert(AlertKind.DAILY_50, inputs.dailyDosePercent)
            m = m.copy(daily50Fired = true, advisoriesToday = m.advisoriesToday + 1)
        }

        // Expired snoozes are dropped so the memory does not grow.
        m = m.copy(snoozedUntil = m.snoozedUntil.filterValues { it > now })
        return out.sortedByDescending { it.kind.tier.ordinal } to m
    }

    /** "Snooze 1 h" from a notification. LIMIT alerts cannot be snoozed. */
    fun snooze(memory: AlertMemory, kind: AlertKind, nowMs: Long): AlertMemory =
        if (!kind.snoozable) memory
        else memory.copy(snoozedUntil = memory.snoozedUntil + (kind to nowMs + AlertDefaults.SNOOZE_MS))
}

/** Compact persistence for [AlertMemory] (DataStore string). */
object AlertMemoryCodec {

    fun encode(m: AlertMemory): String = buildString {
        append("day=").append(m.day)
        append(";adv=").append(m.advisoriesToday)
        append(";d50=").append(m.daily50Fired)
        append(";d80=").append(m.daily80Fired)
        append(";dlim=").append(m.dailyLimitLevel)
        append(";w80=").append(m.week80Armed)
        append(";wlim=").append(m.weekLimitLevel)
        append(";h80=").append(m.hpWeek80Armed)
        append(";hlim=").append(m.hpWeekLimitLevel)
        append(";aarm=").append(m.ambientLoudArmed)
        append(";alast=").append(m.lastAmbientLoudMs)
        append(";harm=").append(m.hpLoudArmed)
        append(";hlast=").append(m.lastHpLoudMs)
        append(";snz=").append(m.snoozedUntil.entries.joinToString(",") { "${it.key.name}:${it.value}" })
    }

    fun decode(s: String?): AlertMemory {
        if (s.isNullOrBlank()) return AlertMemory()
        val kv = s.split(';').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
        }.toMap()
        val d = AlertMemory()
        fun l(k: String, def: Long) = kv[k]?.toLongOrNull() ?: def
        fun i(k: String, def: Int) = kv[k]?.toIntOrNull() ?: def
        fun b(k: String, def: Boolean) = kv[k]?.toBooleanStrictOrNull() ?: def
        val snz = kv["snz"].orEmpty().split(',').mapNotNull { e ->
            val (k, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val kind = runCatching { AlertKind.valueOf(k) }.getOrNull() ?: return@mapNotNull null
            v.toLongOrNull()?.let { kind to it }
        }.toMap()
        return AlertMemory(
            day = l("day", d.day),
            advisoriesToday = i("adv", d.advisoriesToday),
            daily50Fired = b("d50", d.daily50Fired),
            daily80Fired = b("d80", d.daily80Fired),
            dailyLimitLevel = i("dlim", d.dailyLimitLevel),
            week80Armed = b("w80", d.week80Armed),
            weekLimitLevel = i("wlim", d.weekLimitLevel),
            hpWeek80Armed = b("h80", d.hpWeek80Armed),
            hpWeekLimitLevel = i("hlim", d.hpWeekLimitLevel),
            ambientLoudArmed = b("aarm", d.ambientLoudArmed),
            lastAmbientLoudMs = l("alast", d.lastAmbientLoudMs),
            hpLoudArmed = b("harm", d.hpLoudArmed),
            lastHpLoudMs = l("hlast", d.lastHpLoudMs),
            snoozedUntil = snz,
        )
    }
}
