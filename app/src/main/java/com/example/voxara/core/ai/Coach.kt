package com.example.voxara.core.ai

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * TONO — the coach. The deterministic engine DECIDES (alerts, doses, score, projections); Tono
 * only EXPLAINS, COACHES and CONVERSES. This file is pure Kotlin: the privacy gate, the
 * deterministic score / projection / streak, the symptom guardrail, the reply validator and the
 * rate limits are all unit-tested without a network.
 */

// ============================================================================ privacy gate

/** Coarse times of day: enough for habits, never a timeline. */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * EVERYTHING the cloud model may ever see. Aggregates only: percentages, rounded levels,
 * durations, coarse buckets and categories. No audio, no titles, no app or device names, no
 * location, no identifiers, no timestamps finer than a day part.
 */
data class AggregateSnapshot(
    val locale: String,
    val dailyDosePercent: Int,
    val weeklyAmbientPercent: Int,
    val weeklyHeadphonePercent: Int,
    val currentDba: Int?,
    val averageDbaToday: Int?,
    val loudestHourDbaToday: Int?,
    val measuredMinutesToday: Int,
    val listeningMinutesToday: Int,
    val loudHoursByDayPart: Map<DayPart, Int>,
    val headphoneCategory: String?,
    val ambientCategory: String?,
    val calibrated: Boolean,
    val hearingCareScore: Int,
    val streakDays: Int,
    val daysToWeeklyLimit: Int?,
) {
    /** The ONLY serialisation that leaves the watch. Keys are a fixed whitelist. */
    fun toWire(): Map<String, Any?> = linkedMapOf(
        "locale" to locale,
        "daily_dose_pct" to dailyDosePercent,
        "weekly_ambient_pct" to weeklyAmbientPercent,
        "weekly_headphone_pct" to weeklyHeadphonePercent,
        "current_dba" to currentDba,
        "avg_dba_today" to averageDbaToday,
        "loudest_hour_dba_today" to loudestHourDbaToday,
        "measured_min_today" to measuredMinutesToday,
        "listening_min_today" to listeningMinutesToday,
        "loud_hours_by_day_part" to DayPart.entries.associate { it.name.lowercase() to (loudHoursByDayPart[it] ?: 0) },
        "headphone_category" to headphoneCategory,
        "ambient_category" to ambientCategory,
        "calibrated" to calibrated,
        "hearing_care_score" to hearingCareScore,
        "streak_days" to streakDays,
        "days_to_weekly_limit" to daysToWeeklyLimit,
    )

    /** Every number the reply is allowed to mention (the hallucination guard). */
    fun allowedNumbers(): Set<Int> = buildSet {
        listOfNotNull(
            dailyDosePercent, weeklyAmbientPercent, weeklyHeadphonePercent, currentDba, averageDbaToday,
            loudestHourDbaToday, measuredMinutesToday, listeningMinutesToday, hearingCareScore, streakDays,
            daysToWeeklyLimit,
        ).forEach { add(it) }
        loudHoursByDayPart.values.forEach { add(it) }
        // Derived figures the coach may state: hours from minutes, the thresholds it explains.
        add(measuredMinutesToday / 60); add(listeningMinutesToday / 60)
        addAll(listOf(1, 2, 3, 5, 7, 8, 10, 15, 20, 24, 30, 40, 50, 60, 70, 75, 80, 85, 90, 95, 100))
    }

    companion object {
        /** Whitelisted wire keys; a test asserts nothing else is ever serialised. */
        val WIRE_KEYS = setOf(
            "locale", "daily_dose_pct", "weekly_ambient_pct", "weekly_headphone_pct", "current_dba",
            "avg_dba_today", "loudest_hour_dba_today", "measured_min_today", "listening_min_today",
            "loud_hours_by_day_part", "headphone_category", "ambient_category", "calibrated",
            "hearing_care_score", "streak_days", "days_to_weekly_limit",
        )
    }
}

// ============================================================================ score / projection / streak

/**
 * HEARING CARE SCORE (0-100) — a LISTENING-HABITS score, deterministic and explainable. Not a
 * hearing test, not a diagnosis.
 *   - Weekly load (worst of ambient and headphone rolling %): no penalty up to 50%, then linearly
 *     up to -60 at 150%.
 *   - Each of the last 7 days over the daily (NIOSH) limit: -8.
 *   - Uncalibrated measurements are not penalised: the score is about habits, not accuracy.
 */
object HearingCareScore {
    fun of(weeklyAmbientPercent: Double, weeklyHeadphonePercent: Double, daysOverDailyLimit: Int): Int {
        val load = maxOf(weeklyAmbientPercent, weeklyHeadphonePercent)
        val loadPenalty = ((load - 50.0) / 100.0 * 60.0).coerceIn(0.0, 60.0)
        val dayPenalty = 8.0 * daysOverDailyLimit.coerceIn(0, 7)
        return (100.0 - loadPenalty - dayPenalty).roundToInt().coerceIn(0, 100)
    }
}

/**
 * "At this pace you'll reach the weekly limit in N days": remaining percent divided by the
 * recent daily average. Null when nothing is accruing or the limit is already reached.
 */
fun daysToWeeklyLimit(weeklyPercent: Double, dailySharesLast7: List<Double>): Int? {
    if (weeklyPercent >= 100.0) return null
    val avg = dailySharesLast7.takeIf { it.isNotEmpty() }?.average() ?: return null
    if (avg <= 0.5) return null
    return ceil((100.0 - weeklyPercent) / avg).toInt().takeIf { it <= 14 }
}

/**
 * Gentle streak: consecutive days (newest first, today excluded while it is still running) under
 * the daily limit. One slip per 7 days is forgiven, so a single loud evening does not reset weeks
 * of good habits.
 */
fun streakDays(daysUnderLimitNewestFirst: List<Boolean>): Int {
    var streak = 0
    var forgivenAt = -100
    for ((i, ok) in daysUnderLimitNewestFirst.withIndex()) {
        if (ok) { streak++; continue }
        if (i - forgivenAt >= 7 && streak > 0) { forgivenAt = i; continue }
        break
    }
    return streak
}

// ============================================================================ guardrails

/**
 * Symptoms are never sent to the model: a fixed, safe answer recommends a hearing professional.
 * Matched in English and Spanish, like the voice intents.
 */
object SymptomGuard {
    private val keys = listOf(
        "tinnitus", "ringing", "buzzing", "muffled", "ear pain", "ears hurt", "earache", "can't hear",
        "cannot hear", "hearing loss", "deaf", "dizzy", "vertigo", "blocked ear", "fullness",
        "zumbido", "pitido", "acúfeno", "acufeno", "oído tapado", "oido tapado", "dolor de oído", "dolor de oido",
        "me duele el oído", "me duele el oido", "no oigo", "sordo", "sorda", "pérdida auditiva", "perdida auditiva", "mareo",
    )

    fun detect(text: String?): Boolean {
        val t = text?.lowercase() ?: return false
        return keys.any { t.contains(it) }
    }
}

/** A validated, screen-sized reply. */
data class CoachReply(
    val status: String,
    val insight: String,
    val why: String,
    val suggestion: String,
    val chips: List<String>,
)

object ReplyLimits {
    const val STATUS = 60
    const val INSIGHT = 140
    const val WHY = 120
    const val SUGGESTION = 80
    const val CHIP = 20
    const val CHIPS = 3
}

sealed interface Validation {
    data class Valid(val reply: CoachReply) : Validation
    data class Invalid(val reason: String) : Validation
}

object ReplyValidator {

    /** Diagnosis, treatment and alarm vocabulary. The coach is non-medical and non-alarmist. */
    private val banned = listOf(
        "diagnos", "you have hearing loss", "you are deaf", "prescri", "cure", "treatment", "medical advice",
        "permanent damage", "you will go deaf", "emergency",
        "diagnóstic", "diagnostic", "tienes pérdida", "tienes perdida", "eres sordo", "receta", "cura",
        "tratamiento", "consejo médico", "daño permanente", "te quedarás sordo", "emergencia",
    )

    /** Word-start boundary, so "cure" matches "cure" but not "secure". */
    private val bannedPatterns = banned.map { Regex("""(?<!\p{L})""" + Regex.escape(it.trim())) }

    private val numberRegex = Regex("""\d+(?:[.,]\d+)?""")

    fun validate(raw: String?, snapshot: AggregateSnapshot): Validation {
        if (raw.isNullOrBlank()) return Validation.Invalid("empty")
        val obj = runCatching { MiniJson.parse(raw) }.getOrNull() as? Map<*, *>
            ?: return Validation.Invalid("not a JSON object")
        fun field(name: String, max: Int, required: Boolean = true): String? {
            val v = (obj[name] as? String)?.trim()
            if (v.isNullOrEmpty()) return if (required) null else ""
            return v.takeIf { it.length <= max }
        }
        val status = field("status", ReplyLimits.STATUS) ?: return Validation.Invalid("status")
        val insight = field("insight", ReplyLimits.INSIGHT) ?: return Validation.Invalid("insight")
        val why = field("why", ReplyLimits.WHY, required = false) ?: return Validation.Invalid("why")
        val suggestion = field("suggestion", ReplyLimits.SUGGESTION) ?: return Validation.Invalid("suggestion")
        val chips = (obj["chips"] as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.trim()?.takeIf { c -> c.isNotEmpty() && c.length <= ReplyLimits.CHIP } }
            .take(ReplyLimits.CHIPS)

        val all = listOf(status, insight, why, suggestion) + chips
        val text = all.joinToString(" ").lowercase()
        bannedPatterns.firstOrNull { it.containsMatchIn(text) }?.let { return Validation.Invalid("banned: ${it.pattern}") }

        // Every number in the reply must come from the data (rounded, +-1).
        val allowed = snapshot.allowedNumbers()
        for (m in numberRegex.findAll(all.joinToString(" "))) {
            val n = m.value.replace(',', '.').toDoubleOrNull()?.roundToInt() ?: continue
            if (allowed.none { abs(it - n) <= 1 }) return Validation.Invalid("unsupported number ${m.value}")
        }
        return Validation.Valid(CoachReply(status, insight, why, suggestion, chips))
    }
}

// ============================================================================ requests, prompt, limits

enum class CoachTask { STATUS, DAILY_INSIGHT, WEEKLY_SUMMARY, ALERT_WHY, ASK }

data class CoachRequest(
    val task: CoachTask,
    val snapshot: AggregateSnapshot,
    /** The wearer's own question (ASK only). Checked by [SymptomGuard] before anything is sent. */
    val question: String? = null,
    /** The alert being explained (ALERT_WHY only), by kind name. */
    val alertKind: String? = null,
)

object CoachPrompt {
    const val MAX_TOKENS = 250

    /** The system prompt: voice, guardrails and the exact JSON contract (with the word "json"). */
    fun system(language: String): String = """
        You are Tono, the calm coach inside Voxara, a hearing-care app on a smartwatch.
        Voice: calm, clear, warm, never alarmist, never judgmental. Second person. Plain words.
        Say one fact, then at most one gentle suggestion.
        You are NOT a medical device and you never diagnose, treat or make medical claims.
        If the user mentions symptoms, say you cannot assess them and suggest a hearing professional.
        Use ONLY numbers that appear in the data provided. Never invent figures.
        All sound levels from the watch are estimates; headphone levels are rough estimates.
        Reply in ${if (language == "es") "Spanish" else "English"}.
        Output ONLY a json object with exactly these keys and limits:
        {"status": "<=60 chars, live status line", "insight": "<=140 chars", "why": "<=120 chars, may be empty",
         "suggestion": "<=80 chars", "chips": ["<=20 chars", "up to 3 follow-up questions"]}
        Example json: {"status":"Quiet morning so far","insight":"You are at 12% of today's sound allowance.",
        "why":"","suggestion":"Keep the volume where it is.","chips":["My week?","Why estimated?"]}
    """.trimIndent()

    /** The user message: the task plus the whitelisted aggregates. Nothing else. */
    fun user(req: CoachRequest): String = MiniJson.write(
        linkedMapOf(
            "task" to req.task.name.lowercase(),
            "alert" to req.alertKind,
            "question" to req.question,
            "data" to req.snapshot.toWire(),
        )
    )
}

/**
 * Rate and cost limits (persisted by the caller). Automatic insights at most hourly; at most
 * [MAX_CALLS_PER_DAY] cloud calls a day; each capped at [CoachPrompt.MAX_TOKENS] output tokens.
 */
data class CoachBudget(val day: Long = 0L, val callsToday: Int = 0, val lastAutoMs: Long = 0L) {
    companion object {
        const val MAX_CALLS_PER_DAY = 20
        const val AUTO_INTERVAL_MS = 60L * 60 * 1000
    }

    private fun rolled(today: Long) = if (day == today) this else CoachBudget(today, 0, lastAutoMs)

    fun canCall(today: Long, nowMs: Long, automatic: Boolean): Boolean {
        val b = rolled(today)
        if (b.callsToday >= MAX_CALLS_PER_DAY) return false
        return !automatic || b.lastAutoMs == 0L || nowMs - b.lastAutoMs >= AUTO_INTERVAL_MS
    }

    fun spend(today: Long, nowMs: Long, automatic: Boolean): CoachBudget {
        val b = rolled(today)
        return b.copy(callsToday = b.callsToday + 1, lastAutoMs = if (automatic) nowMs else b.lastAutoMs)
    }
}

/** Replies are cached per task, day and a coarse fingerprint of the data (5-point buckets). */
fun cacheKey(req: CoachRequest, day: Long): String {
    val s = req.snapshot
    fun b(v: Int?) = v?.let { it / 5 } ?: -1
    return listOf(
        req.task.name, day, s.locale, b(s.dailyDosePercent), b(s.weeklyAmbientPercent),
        b(s.weeklyHeadphonePercent), b(s.currentDba), s.headphoneCategory, s.ambientCategory,
        req.alertKind, req.question?.lowercase()?.trim(),
    ).joinToString("|")
}
