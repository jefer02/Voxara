package com.example.voxara.core.situation

import com.example.voxara.core.scene.ConfidenceHysteresis
import com.example.voxara.core.scene.SceneFeatures
import com.example.voxara.core.scene.SceneModel
import com.example.voxara.core.scene.SceneModelSource

/**
 * AUTO MODE DETECTION — which situation the wearer is in, from what the watch already measures.
 *
 * One VOTE per burst that produced scene features (the bursts the dosimeter captures anyway; the
 * microphone is never opened for this): the scene model's mode probabilities, the burst level
 * (calibrated dBA) and whether the sound is music-like. Per call, the context: headphones
 * playing on the watch and the local minute of day.
 *
 *  01  PLAUSIBILITY — the level vetoes what cannot be: below [Config.onlyQuietBelowDba] it is
 *      Quiet whatever the spectrum says; Quiet above [Config.quietMaxDba], Work above
 *      [Config.workMaxDba] and Transport below [Config.transportMinDba] are ruled out. Mild
 *      priors: headphones playing makes Transport likelier, the night (22-06) halves Work.
 *  02  VOTING WINDOW — the votes of the last [Config.windowMs], soft-averaged and weighted by AGE (a
 *      vote loses half its weight every [Config.voteHalfLifeMs], so a new scene overtakes the old one in
 *      a couple of votes while one odd burst still cannot). A winner needs [Config.minVotes] votes, a
 *      weighted mean probability >= [Config.minConfidence] and the top spot in >= [Config.minAgreement]
 *      of the weight. At most one vote per [Config.minVoteGapMs].
 *  03  HYSTERESIS — the winner must hold [Config.consecutive] evaluations in a row
 *      ([ConfidenceHysteresis], the same gate the scene label uses).
 *  04  DWELL — a detected mode stays at least [Config.minDwellMs] before another replaces it.
 *  04b FAST VOTES — while it is unsure (nothing detected yet) or a different scene is knocking, the
 *      detector asks for a vote every [Config.fastVotePeriodMs] instead of the sampling ladder's pace
 *      ([fastVotePeriodMs]), from a small budget that refills slowly: a change is noticed sooner but
 *      the microphone is never kept busy.
 *  05  DOUBT — in doubt the current mode is KEPT; only when it has lost support for
 *      [Config.doubtFallbackMs], or no vote arrived for [Config.staleAfterMs], does the detector
 *      give up and return null, which Situation.resolve turns into Street.
 *  06  CONCERT — never a model output (no training data has it). A deterministic rule over the
 *      last [Config.concertWindowMs]: median level >= [Config.concertMinDba], at least
 *      [Config.concertMusicShare] of the votes music-like and no headphones playing, holding
 *      without a break for [Config.concertMinSpanMs]. Entering skips the dwell (it only
 *      adds protection); leaving takes [Config.concertExitQuietMs] below
 *      [Config.concertExitBelowDba], exactly like a manual Concert.
 *
 * LICENCE GATE: with [Config.sceneModel] false (release builds by default — the model's training
 * data is non-commercial) only the Concert rule runs; it uses features and levels, not the model.
 * Everything else stays null, i.e. Street.
 *
 * SAFETY: the only output is a [SituationMode] for Situation.resolve(AUTO, detected). The detector
 * never sees or returns an alert setting, and a fixed choice ignores it, so by Situation's rule
 * (modes can only ADD protection) no detection can relax an alert. Haptics for a change go
 * through ModeChangeHapticGate in the caller. It keeps probabilities and levels only — never
 * audio, never feature vectors. Pure and clock-free: every time is passed in.
 */
class AutoModeDetector(private val config: Config = Config()) {

    data class Config(
        /** False: no scene model (licence gate) — Concert rule only, otherwise Street. */
        val sceneModel: Boolean = true,
        val windowMs: Long = 5L * 60_000,
        val maxVotes: Int = 60,
        val minVotes: Int = 2,
        val minVoteGapMs: Long = 5_000L,
        val minConfidence: Double = 0.50,
        val minAgreement: Double = 0.50,
        val consecutive: Int = 2,
        val minDwellMs: Long = 90_000L,
        /** A vote's weight (its covered time) halves every this long: a new scene must hold about this long to win. */
        val voteHalfLifeMs: Long = 60_000L,
        /** Pace of the extra votes while unsure or while a different scene is knocking. */
        val fastVotePeriodMs: Long = 15_000L,
        /** Extra votes available at once, and the time it takes to earn one back. */
        val fastBudget: Int = 8,
        val fastRefillMs: Long = 90_000L,
        /** The current mode counts as unsupported when its mean probability is below this. */
        val doubtBelow: Double = 0.25,
        val doubtFallbackMs: Long = 10L * 60_000,
        val staleAfterMs: Long = 15L * 60_000,
        val onlyQuietBelowDba: Double = 38.0,
        val quietMaxDba: Double = 70.0,
        val workMaxDba: Double = 80.0,
        val transportMinDba: Double = 50.0,
        val headphoneTransportBoost: Double = 1.3,
        val nightWorkFactor: Double = 0.5,
        val nightStartMinute: Int = 22 * 60,
        val nightEndMinute: Int = 6 * 60,
        /** A vote is music-like when its mean local tonality is at most this (dB). */
        val musicTonalityMaxDb: Double = DEFAULT_MUSIC_TONALITY_MAX_DB,
        val concertWindowMs: Long = 3L * 60_000,
        val concertMinSpanMs: Long = 2L * 60_000,
        val concertMinVotes: Int = 6,
        val concertMinDba: Double = 90.0,
        val concertMusicShare: Double = 0.35,
        val concertExitBelowDba: Double = Situation.CONCERT_EXIT_BELOW_DBA,
        val concertExitQuietMs: Long = Situation.CONCERT_EXIT_QUIET_MS,
    )

    /** One burst's evidence. [probabilities] are over [SceneModel.MODES]. No audio, no features. */
    class Vote(
        val atMs: Long,
        val probabilities: DoubleArray,
        val levelDba: Double,
        val musicLike: Boolean,
        /** The model's scene-class probabilities (debug log only); null without the model. */
        val classProbabilities: DoubleArray? = null,
        /** Mean local tonality of the burst (dB), for the debug log. */
        val tonalityDb: Double = Double.NaN,
    ) {
        init { require(probabilities.size == SceneModel.MODES.size) }
    }

    /** What the level and the priors did to a vote (debug log). */
    enum class Veto { ONLY_QUIET, NO_QUIET, NO_WORK, NO_TRANSPORT, HEADPHONE_PRIOR, NIGHT_PRIOR, ALL_RULED_OUT }

    data class Context(
        val headphonesPlaying: Boolean = false,
        /** Local minute of day 0..1439, or null to skip the time-of-day prior. */
        val localMinuteOfDay: Int? = null,
    )

    /**
     * [detected] null means "not sure": Auto then resolves to Street. [vote] and [vetoes] describe
     * the vote just taken (after the vetoes and priors); null / empty when none was taken.
     */
    data class Decision(
        val detected: SituationMode?,
        val changed: Boolean,
        val confidence: Double,
        val vote: DoubleArray? = null,
        val vetoes: Set<Veto> = emptySet(),
    )

    companion object {
        /**
         * Tonality threshold for "music-like" (dB, see SceneFeatures.TONALITY), generated by
         * derive_concert_threshold.py from freely licensed live music ONLY (never the scene
         * model's non-commercial data): the median over clips of each clip's median vote
         * tonality, watch-like chain + crowd noise at 10 dB. The typical live recording then has
         * about half its votes music-like, above the rule's [Config.concertMusicShare] of 0.35;
         * heavily distorted rock stays near 0% (no tonality measure catches it).
         */
        const val DEFAULT_MUSIC_TONALITY_MAX_DB = SceneModelSource.MUSIC_TONALITY_MAX_DB

        private val QUIET = SceneModel.MODES.indexOf(SituationMode.QUIET)
        private val STREET = SceneModel.MODES.indexOf(SituationMode.STREET)
        private val TRANSPORT = SceneModel.MODES.indexOf(SituationMode.TRANSPORT)
        private val WORK = SceneModel.MODES.indexOf(SituationMode.WORK)
    }

    /** A vote from the feature vectors of one burst (averaged when the burst gave several). */
    fun voteOf(atMs: Long, vectors: List<DoubleArray>, levelDba: Double): Vote? {
        if (vectors.isEmpty()) return null
        var tonality = 0.0
        for (v in vectors) tonality += v[SceneFeatures.TONALITY] / vectors.size
        val musicLike = tonality <= config.musicTonalityMaxDb
        // Licence gate: the model is never called; the vote only carries level and music.
        if (!useModel) {
            return Vote(atMs, DoubleArray(SceneModel.MODES.size) { 1.0 / SceneModel.MODES.size }, levelDba, musicLike, null, tonality)
        }
        var classes: DoubleArray? = null
        for (v in vectors) {
            val c = SceneModel.classProbabilities(v)
            val acc = classes ?: DoubleArray(c.size).also { classes = it }
            for (i in c.indices) acc[i] += c[i] / vectors.size
        }
        val cls = classes!!
        return Vote(atMs, SceneModel.modesOf(cls), levelDba, musicLike, cls, tonality)
    }

    /** The model runs only when asked AND present (release builds carry no weights). */
    private val useModel = config.sceneModel && SceneModel.available

    private class Entry(val atMs: Long, val p: DoubleArray, val levelDba: Double, val musicLike: Boolean)

    private val votes = ArrayDeque<Entry>()
    private val hysteresis = ConfidenceHysteresis<SituationMode?>(null, config.minConfidence.toFloat(), config.consecutive)

    var detected: SituationMode? = null
        private set
    private var detectedSinceMs = 0L
    private var doubtSinceMs: Long? = null
    private var concertQuietSinceMs: Long? = null
    private var concertEvidenceSinceMs: Long? = null
    private var lastVoteAtMs: Long? = null
    private var confidence = 0.0
    private var fastTokens = config.fastBudget.toDouble()
    private var fastCheckedAtMs: Long? = null

    /**
     * The pace to take the next vote at when it should be faster than the sampling ladder's, or null.
     * Asked once per loop; each fast answer spends one token of a budget that refills slowly, so a
     * flickering scene cannot keep the microphone busy. Only meaningful with the scene model.
     */
    fun fastVotePeriodMs(nowMs: Long): Long? {
        val last = fastCheckedAtMs
        fastCheckedAtMs = nowMs
        if (last != null && nowMs > last) {
            fastTokens = minOf(config.fastBudget.toDouble(), fastTokens + (nowMs - last).toDouble() / config.fastRefillMs)
        }
        if (!useModel || fastTokens < 1.0) return null
        val knocking = votes.lastOrNull()?.let { v ->
            val top = v.p.indices.maxByOrNull { v.p[it] } ?: return@let false
            detected != null && detected != SituationMode.CONCERT && SceneModel.MODES[top] != detected
        } ?: false
        val learning = detected == null && votes.size < config.minVotes + config.consecutive
        if (!knocking && !learning) return null
        fastTokens -= 1.0
        return config.fastVotePeriodMs
    }

    /** False while a new vote would be ignored (rate limit) — the caller can skip the model. */
    fun wantsVote(nowMs: Long): Boolean = lastVoteAtMs.let { it == null || nowMs - it >= config.minVoteGapMs }

    fun offer(vote: Vote, context: Context = Context()): Decision {
        val before = detected
        expireIfStale(vote.atMs)
        if (!wantsVote(vote.atMs)) return Decision(detected, detected != before, confidence)
        lastVoteAtMs = vote.atMs
        val vetoes = LinkedHashSet<Veto>()
        val adjusted = plausible(vote.probabilities, vote.levelDba, context, vetoes)
        votes.addLast(Entry(vote.atMs, adjusted, vote.levelDba, vote.musicLike))
        while (votes.size > config.maxVotes || votes.first().atMs < vote.atMs - config.windowMs) votes.removeFirst()
        evaluate(vote.atMs, vote.levelDba, context)
        return Decision(detected, detected != before, confidence, adjusted, vetoes)
    }

    /** Applies the staleness rule when no vote arrives (call it from the loop; it is cheap). */
    fun tick(nowMs: Long): Decision {
        val before = detected
        expireIfStale(nowMs)
        return Decision(detected, detected != before, confidence)
    }

    /** Forget everything (Auto switched off, a fixed mode chosen, the service restarted). */
    fun reset() {
        votes.clear()
        hysteresis.reset()
        detected = null
        detectedSinceMs = 0L
        doubtSinceMs = null
        concertQuietSinceMs = null
        concertEvidenceSinceMs = null
        lastVoteAtMs = null
        confidence = 0.0
    }

    private fun expireIfStale(nowMs: Long) {
        val last = lastVoteAtMs ?: return
        if (nowMs - last > config.staleAfterMs) reset()
    }

    /** 01 — level vetoes and mild priors; never returns all zeros (Street then). */
    private fun plausible(p: DoubleArray, levelDba: Double, context: Context, vetoes: MutableSet<Veto>): DoubleArray {
        val q = DoubleArray(p.size)
        if (levelDba < config.onlyQuietBelowDba) {
            vetoes += Veto.ONLY_QUIET
            return q.also { it[QUIET] = 1.0 }
        }
        for (i in p.indices) q[i] = p[i].coerceAtLeast(0.0)
        if (levelDba > config.quietMaxDba) { q[QUIET] = 0.0; vetoes += Veto.NO_QUIET }
        if (levelDba > config.workMaxDba) { q[WORK] = 0.0; vetoes += Veto.NO_WORK }
        if (levelDba < config.transportMinDba) { q[TRANSPORT] = 0.0; vetoes += Veto.NO_TRANSPORT }
        if (context.headphonesPlaying) { q[TRANSPORT] *= config.headphoneTransportBoost; vetoes += Veto.HEADPHONE_PRIOR }
        if (isNight(context.localMinuteOfDay)) { q[WORK] *= config.nightWorkFactor; vetoes += Veto.NIGHT_PRIOR }
        val sum = q.sum()
        if (sum <= 0.0) {
            vetoes += Veto.ALL_RULED_OUT
            return DoubleArray(p.size).also { it[STREET] = 1.0 }
        }
        for (i in q.indices) q[i] /= sum
        return q
    }

    private fun isNight(minute: Int?): Boolean {
        minute ?: return false
        val s = config.nightStartMinute
        val e = config.nightEndMinute
        return if (s <= e) minute in s until e else minute >= s || minute < e
    }

    private fun evaluate(nowMs: Long, levelDba: Double, context: Context) {
        // 06 — Concert: sticky until the room has been quiet long enough, like a manual Concert.
        if (detected == SituationMode.CONCERT) {
            concertEvidenceSinceMs = null
            if (levelDba < config.concertExitBelowDba) {
                val since = concertQuietSinceMs ?: nowMs.also { concertQuietSinceMs = it }
                if (nowMs - since >= config.concertExitQuietMs) {
                    detected = null
                    concertQuietSinceMs = null
                    hysteresis.reset()
                    confidence = 0.0
                }
            } else {
                concertQuietSinceMs = null
            }
            return
        }
        // The rule must hold without a break for concertMinSpanMs before Concert is entered.
        val evidenceSince = if (concertEvidence(nowMs, context)) {
            concertEvidenceSinceMs ?: nowMs.also { concertEvidenceSinceMs = it }
        } else {
            concertEvidenceSinceMs = null
            null
        }
        if (evidenceSince != null && nowMs - evidenceSince >= config.concertMinSpanMs) {
            concertEvidenceSinceMs = null
            detected = SituationMode.CONCERT
            detectedSinceMs = nowMs
            doubtSinceMs = null
            concertQuietSinceMs = null
            confidence = 1.0
            return
        }

        // Licence gate: without the scene model nothing but Concert is ever detected.
        if (!useModel) {
            confidence = 0.0
            return
        }

        // 02 — soft vote over the window.
        val mean = DoubleArray(SceneModel.MODES.size)
        var totalWeight = 0.0
        val weights = DoubleArray(votes.size)
        for ((k, v) in votes.withIndex()) {
            // A vote stands for the stretch of time it covers (until the next vote; the last one for the
            // gap before it), so quick extra votes never outvote a long quiet history by sheer count.
            val next = votes.getOrNull(k + 1)?.atMs
            val covered = (next ?: (v.atMs + (v.atMs - (votes.getOrNull(k - 1)?.atMs ?: (v.atMs - config.fastVotePeriodMs))))) - v.atMs
            val seconds = covered.coerceIn(config.minVoteGapMs, config.windowMs / 5).toDouble() / 1000.0
            val w = seconds * Math.pow(0.5, (nowMs - v.atMs).coerceAtLeast(0L).toDouble() / config.voteHalfLifeMs)
            weights[k] = w
            totalWeight += w
            for (i in mean.indices) mean[i] += w * v.p[i]
        }
        for (i in mean.indices) mean[i] /= totalWeight
        val winner = mean.indices.maxByOrNull { mean[it] } ?: return
        var agreeing = 0.0
        for ((k, v) in votes.withIndex()) {
            if (v.p.indices.maxByOrNull { v.p[it] } == winner) agreeing += weights[k]
        }
        val agreement = agreeing / totalWeight
        val candidate = SceneModel.MODES[winner].takeIf {
            votes.size >= config.minVotes && mean[winner] >= config.minConfidence && agreement >= config.minAgreement
        }

        // 03 — hysteresis; 04 — dwell.
        val held = hysteresis.offer(candidate, if (candidate == null) 0f else mean[winner].toFloat())
        val current = detected
        if (held != null && held != current &&
            (current == null || nowMs - detectedSinceMs >= config.minDwellMs)
        ) {
            detected = held
            detectedSinceMs = nowMs
            doubtSinceMs = null
        }

        // 05 — doubt: keep the current mode for a while, then fall back.
        val now = detected
        if (now != null) {
            val support = mean[SceneModel.MODES.indexOf(now)]
            confidence = support
            if (support < config.doubtBelow) {
                val since = doubtSinceMs ?: nowMs.also { doubtSinceMs = it }
                if (nowMs - since >= config.doubtFallbackMs) {
                    detected = null
                    doubtSinceMs = null
                    hysteresis.reset()
                    confidence = 0.0
                }
            } else {
                doubtSinceMs = null
            }
        } else {
            confidence = 0.0
        }
    }

    private fun concertEvidence(nowMs: Long, context: Context): Boolean {
        if (context.headphonesPlaying) return false
        val recent = votes.filter { nowMs - it.atMs <= config.concertWindowMs }
        if (recent.size < config.concertMinVotes) return false
        val levels = recent.map { it.levelDba }.sorted()
        val median = levels[levels.size / 2]
        val music = recent.count { it.musicLike }.toDouble() / recent.size
        return median >= config.concertMinDba && music >= config.concertMusicShare
    }
}
