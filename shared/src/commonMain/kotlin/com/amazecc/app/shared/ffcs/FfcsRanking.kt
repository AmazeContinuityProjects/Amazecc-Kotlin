package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.RankingComponents
import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.RankingMode
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import kotlin.math.floor

/**
 * D-173 — the FFCS ranking math, ported byte-for-byte from the shipped web
 * implementation `vendor/amazecc-ffcs/src/lib/ffcsRanking.ts:46-418`.
 *
 * Two ranking systems coexist on purpose and are **never stacked**:
 *  - the six legacy profiles with fixed weight tables ([scoreRanking]);
 *  - the eight-factor ordered list ([rankingFactorScores] / [combineFactorScores]).
 *
 * The legacy four-component blend ([combineRankingScores]) is a third surface
 * used by the guided selector. [RankingSelection] pins the exclusivity rule:
 * a present `rankingFactors` field supersedes `rankingMode` / `rankingPriorityOrder`
 * entirely (web `ffcsGenerate.ts:188-200`).
 *
 * ## Rounding contract — read before changing anything
 *
 * JavaScript's `Number(x.toFixed(1))` is half-away-from-zero, reproduced by
 * [round1]. There are **three** deliberately different accumulation rules:
 *
 *  | function | breakdown entries | score accumulation | source |
 *  |---|---|---|---|
 *  | [scoreRanking] | rounded to 1 dp | sums the **rounded** entries | `ffcsRanking.ts:239-246` |
 *  | [combineFactorScores] | rounded to 1 dp | sums the **raw** points | `ffcsRanking.ts:362-370` |
 *  | [combineRankingScores] | rounded to 1 dp | sums the **raw** points | `ffcsRanking.ts:404-416` |
 *
 * That asymmetry is load-bearing: the sample report scores **52.1** under the
 * raw rule (its rounded entries only sum to 52.0). Do not "fix" it.
 *
 * The enums ([RankingMode], [RankingFactor], [RankingPriority]), the wire config
 * ([RankingFactorsConfig]) and the four components ([RankingComponents]) are
 * owned by D-171 in `ffcs.planner` and are reused here rather than redeclared.
 */

// ── constants (ffcsRanking.ts:126-130) ──────────────────────────────────────

/** Four hours — the longest class day still counted as "compact". */
const val COMPACT_DAY_MAX_MIN: Int = 240
/** A gap this short is a changeover, not an empty period. */
const val GAP_NOT_COUNTED_MIN: Int = 20
/** One period is 55 minutes, so each remaining gap is worth ~that many slots. */
const val PERIOD_MIN: Int = 55

// ── value types ─────────────────────────────────────────────────────────────

/** One class meeting on one day, in minutes of day. */
data class ClassMeeting(val startMin: Int, val endMin: Int)

/** Everything a ranking factor reads. Absent faculty quality stays `null`. */
data class RankingInputs(
    /** Days with at least one class. Empty days contribute nothing. */
    val activeDays: Int,
    /** **Class** days whose span is <= 4h — NOT free half-days. Range 0..activeDays. */
    val compactDays: Int,
    /** Weighted count of gaps long enough to be empty periods. */
    val totalGapSlots: Int,
    /** Average first-class start across class days, in minutes. */
    val averageDayStartMinutes: Double,
    /** Average last-class end across class days, in minutes. */
    val averageDayEndMinutes: Double,
    /** Population variance of classes-per-day across active days only. */
    val dailyLoadVariance: Double,
    /** Coverage-adjusted 0..1 vitfac aggregate; null when unrated. */
    val facultyQuality: Double? = null,
)

/** A 0..100 score plus its per-factor points, also out of 100. */
data class RankingScore(val score: Double, val breakdown: Map<String, Double>)

/** Raw inputs for the eight-factor path, plus the three metric-derived scores. */
data class RankingFactorInputs(
    val inputs: RankingInputs,
    /** Free half-days over the week (the timetableMetrics measure, 0..12 used). */
    val halfDays: Double,
    /** Overlap-with-friends score, 0..100. */
    val socialScore: Double,
    /** Vitfac aggregate, 0..100; null when no faculty in the week is rated. */
    val facultyQuality: Double?,
    /** Preference-fit score, 0..100; null when there is nothing to fit. */
    val timeScore: Double?,
)

/** Result shared by both combiners: a 0..100 score and a 1-dp breakdown. */
data class CompositeRankingResult(val score: Double, val breakdown: Map<String, Double>)

/** A legacy profile's multipliers on a 0..1 factor. */
data class ProfileWeights(
    val compactDays: Double,
    val lowGaps: Double,
    val earlyFinish: Double,
    val lateStart: Double,
    val balancedLoad: Double,
    val facultyQuality: Double? = null,
)

// ── numeric primitives ──────────────────────────────────────────────────────

/** JavaScript `Number(value.toFixed(1))`: round half away from zero. */
private fun round1(value: Double): Double {
    if (!value.isFinite()) return value
    return if (value >= 0.0) floor(value * 10.0 + 0.5) / 10.0
    else -floor(-value * 10.0 + 0.5) / 10.0
}

private fun clamp100(value: Double): Double =
    if (value.isFinite()) value.coerceIn(0.0, 100.0) else 0.0

private fun normalize(value: Double, max: Double): Double =
    if (!value.isFinite() || max <= 0.0) 0.0 else (value / max).coerceIn(0.0, 1.0)

private fun inverted(value: Double, max: Double): Double = 1.0 - normalize(value, max)

/** Population variance (divide by n), not sample variance. Empty -> 0. */
private fun populationVariance(values: List<Int>): Double {
    if (values.isEmpty()) return 0.0
    val mean = values.sum().toDouble() / values.size
    return values.sumOf { v -> val d = v - mean; d * d } / values.size
}

// ── the six legacy profiles (ffcsRanking.ts:46-95) ──────────────────────────

/** Published modes, faculty-focused -> schedule-focused. */
val RANKING_MODES: List<RankingMode> = RankingMode.entries.toList()

/** The generator's factor list, in canonical engine order (D-171 owns the enum). */
val RANKING_FACTORS: List<RankingFactor> = RankingFactor.CANONICAL_ORDER

/** Exact weight tables from `PROFILES` in ffcsRanking.ts:80-87. */
private val PROFILES: Map<RankingMode, ProfileWeights> = mapOf(
    RankingMode.BEST_FACULTY to ProfileWeights(2.0, 2.0, 1.0, 1.0, 1.0, 45.0),
    RankingMode.BALANCED to ProfileWeights(10.0, 14.0, 10.0, 8.0, 8.0),
    RankingMode.HALF_DAYS to ProfileWeights(30.0, 10.0, 8.0, 4.0, 3.0),
    RankingMode.MINIMIZE_GAPS to ProfileWeights(6.0, 32.0, 6.0, 3.0, 8.0),
    RankingMode.EARLY_FINISH to ProfileWeights(8.0, 10.0, 30.0, 2.0, 4.0),
    RankingMode.LATE_START to ProfileWeights(8.0, 8.0, 4.0, 30.0, 4.0),
)

/**
 * Accepts any persisted or hand-edited value; an unknown value reads as `null`
 * rather than throwing. Exact (case-sensitive) match, mirroring the web's
 * `RANKING_MODES.includes(String(value))`.
 */
fun asRankingMode(value: Any?): RankingMode? = when (value) {
    is RankingMode -> value
    null -> null
    else -> RANKING_MODES.firstOrNull { it.value == value.toString() }
}

/** The profile a mode names; absent or unknown -> Balanced. */
fun profileFor(mode: RankingMode?): ProfileWeights =
    PROFILES[mode ?: RankingMode.DEFAULT] ?: PROFILES.getValue(RankingMode.DEFAULT)

// ── reading a week (ffcsRanking.ts:145-182) ─────────────────────────────────

/**
 * Derive ranking inputs from already-resolved class meetings.
 *
 * One entry per day of the week; an empty list means "no class" and is skipped.
 * The web gets these meetings from `dayOccupancy(courses, schema, day)`; slot
 * resolution is the D-172 boundary, so this pure function takes the meetings
 * directly. Meetings are sorted by start minute so the caller need not.
 *
 * Invariants: gaps <= 20 min are changeovers; each remaining gap contributes
 * `max(1, round(gap / 55))` slots; compact days count class days whose
 * first-start -> last-end span is <= 240 min (a free half-day is a different
 * metric and is never read here); variance is over active days only.
 */
fun rankingInputs(days: List<List<ClassMeeting>>): RankingInputs {
    var compactDays = 0
    var startSum = 0.0
    var endSum = 0.0
    val classCounts = mutableListOf<Int>()
    val gapSlots = mutableListOf<Int>()

    for (rawDay in days) {
        val day = rawDay.sortedBy { it.startMin }
        if (day.isEmpty()) continue

        classCounts += day.size
        val first = day.first()
        val last = day.last()
        startSum += first.startMin
        endSum += last.endMin
        if (last.endMin - first.startMin <= COMPACT_DAY_MAX_MIN) compactDays++

        for (i in 1 until day.size) {
            val gap = day[i].startMin - day[i - 1].endMin
            if (gap <= GAP_NOT_COUNTED_MIN) continue
            gapSlots += maxOf(1, floor(gap / PERIOD_MIN.toDouble() + 0.5).toInt())
        }
    }

    val activeDays = classCounts.size
    return RankingInputs(
        activeDays = activeDays,
        compactDays = compactDays,
        totalGapSlots = gapSlots.sum(),
        averageDayStartMinutes = if (activeDays > 0) startSum / activeDays else 0.0,
        averageDayEndMinutes = if (activeDays > 0) endSum / activeDays else 0.0,
        dailyLoadVariance = populationVariance(classCounts),
    )
}

// ── legacy profile scoring (ffcsRanking.ts:186-257) ─────────────────────────

/**
 * Score a week 0..100 under a legacy profile. `mode == null` reads as Balanced.
 *
 * The score is the sum of the **rounded** breakdown entries (then re-rounded),
 * which is deliberately different from the two combiners below. This is the
 * "legacy asymmetry" and must not be normalised away.
 */
fun scoreRanking(inputs: RankingInputs, mode: RankingMode?): RankingScore {
    val weights = profileFor(mode)
    val perDay = if (inputs.activeDays > 0) inputs.totalGapSlots.toDouble() / inputs.activeDays else 0.0

    val factors = linkedMapOf<String, Pair<Double, Double>>()
    factors["compactDays"] =
        (if (inputs.activeDays > 0) inputs.compactDays.toDouble() / inputs.activeDays else 0.0) to weights.compactDays
    factors["lowGaps"] = inverted(perDay, 6.0) to weights.lowGaps
    factors["earlyFinish"] = inverted(inputs.averageDayEndMinutes - 12 * 60.0, 7 * 60.0) to weights.earlyFinish
    factors["lateStart"] = normalize(inputs.averageDayStartMinutes - 8 * 60.0, 6 * 60.0) to weights.lateStart
    factors["balancedLoad"] = inverted(inputs.dailyLoadVariance, 4.0) to weights.balancedLoad

    val facultyWeight = weights.facultyQuality
    if (inputs.facultyQuality != null && facultyWeight != null && facultyWeight != 0.0) {
        factors["facultyQuality"] = normalize(inputs.facultyQuality, 1.0) to facultyWeight
    }

    val totalWeight = factors.values.sumOf { it.second }
    val breakdown = linkedMapOf<String, Double>()
    var raw = 0.0
    factors.forEach { (key, factor) ->
        val (value, weight) = factor
        val points = if (totalWeight > 0) 100.0 * value * weight / totalWeight else 0.0
        val rounded = round1(points)
        breakdown[key] = rounded
        raw += rounded // ROUNDED accumulation — cf. ffcsRanking.ts:243
    }
    return RankingScore(round1(raw.coerceIn(0.0, 100.0)), breakdown)
}

// ── the eight-factor path (ffcsRanking.ts:288-371) ──────────────────────────

/**
 * Each enabled factor's score, 0..100. A factor with no data (unrated faculty,
 * no time preference) is **omitted**, never zeroed — absent must not out-rank
 * present. The expressions match [scoreRanking]'s profile factors exactly.
 */
fun rankingFactorScores(f: RankingFactorInputs): Map<RankingFactor, Double> {
    val perDay = if (f.inputs.activeDays > 0) f.inputs.totalGapSlots.toDouble() / f.inputs.activeDays else 0.0
    val scores = linkedMapOf(
        RankingFactor.GAPS to clamp100(inverted(perDay, 6.0) * 100.0),
        RankingFactor.HALFDAYS to clamp100(f.halfDays / 12.0 * 100.0),
        RankingFactor.SOCIAL to clamp100(f.socialScore),
        RankingFactor.EARLY_FINISH to clamp100(inverted(f.inputs.averageDayEndMinutes - 12 * 60.0, 7 * 60.0) * 100.0),
        RankingFactor.LATE_START to clamp100(normalize(f.inputs.averageDayStartMinutes - 8 * 60.0, 6 * 60.0) * 100.0),
        RankingFactor.BALANCED_LOAD to clamp100(inverted(f.inputs.dailyLoadVariance, 4.0) * 100.0),
    )
    if (f.facultyQuality != null) scores[RankingFactor.FACULTY] = clamp100(f.facultyQuality)
    if (f.timeScore != null) scores[RankingFactor.TIME] = clamp100(f.timeScore)
    return scores
}

/**
 * Combine factors by list position: with `k` present factors the weights are
 * `k, k-1, ..., 1`. Unknown factors, duplicates and factors with absent or
 * non-finite scores are dropped **before** weights are assigned, so disabling
 * the top factor promotes the next one instead of leaving a hole.
 *
 * Breakdown entries are rounded to 1 dp; the score accumulates **raw** points
 * and is rounded once at the end (`ffcsRanking.ts:366-367`). All factors off ->
 * `{0, {}}` — every option ties and the caller's sort order decides.
 */
fun combineFactorScores(scores: Map<RankingFactor, Double?>, order: List<RankingFactor>): CompositeRankingResult {
    val seen = linkedSetOf<RankingFactor>()
    val active = order.filter { it in RANKING_FACTORS && seen.add(it) }
    val present = active.filter { factor -> scores[factor]?.isFinite() == true }
    val totalWeight = present.indices.sumOf { present.size - it }
    if (totalWeight == 0) return CompositeRankingResult(0.0, emptyMap())

    val breakdown = linkedMapOf<String, Double>()
    var score = 0.0
    present.forEachIndexed { index, factor ->
        val weight = present.size - index
        val points = clamp100(scores[factor]!!) * weight / totalWeight
        breakdown[factor.value] = round1(points)
        score += points // RAW accumulation — cf. ffcsRanking.ts:367
    }
    return CompositeRankingResult(round1(score), breakdown)
}

/** Boundary overload: parse raw wire keys, dropping unknown ones (no guessing). */
fun combineFactorScoresByKey(scores: Map<String, Double?>, order: List<String>): CompositeRankingResult {
    val normalizedOrder = order.mapNotNull { key -> RANKING_FACTORS.firstOrNull { it.value == key } }
    val normalizedScores = scores.mapNotNull { (key, value) ->
        RANKING_FACTORS.firstOrNull { it.value == key }?.let { it to value }
    }.toMap()
    return combineFactorScores(normalizedScores, normalizedOrder)
}

// ── the legacy four-component blend (ffcsRanking.ts:373-418) ────────────────

/**
 * Combine the selected profile/faculty/sort/time scores with the priority list.
 *
 * Unlike [combineFactorScores], a partial user order is **completed** with the
 * canonical tail `profile, faculty, sort, time` rather than truncated. Weights
 * are `priorities.size - index` over that full four-slot list, so absent
 * components leave a hole in the weights instead of being removed from it.
 * Null and non-finite values are skipped. Breakdown is rounded to 1 dp; the
 * score accumulates **raw** points (`ffcsRanking.ts:410-411`).
 */
fun combineRankingScores(
    components: RankingComponents,
    priorityOrder: List<RankingPriority> = RankingPriority.DEFAULT_ORDER,
): CompositeRankingResult {
    val canonical = RankingPriority.DEFAULT_ORDER
    val unique = priorityOrder.filterIndexed { index, key ->
        key in canonical && priorityOrder.indexOf(key) == index
    }
    val priorities = unique + canonical.filter { it !in unique }

    fun valueOf(key: RankingPriority): Double? = when (key) {
        RankingPriority.PROFILE -> components.profile
        RankingPriority.FACULTY -> components.faculty
        RankingPriority.SORT -> components.sort
        RankingPriority.TIME -> components.time
    }
    fun present(key: RankingPriority): Boolean = valueOf(key)?.isFinite() == true

    val totalWeight = priorities.indices.filter { present(priorities[it]) }.sumOf { priorities.size - it }
    if (totalWeight == 0) return CompositeRankingResult(0.0, emptyMap())

    val breakdown = linkedMapOf<String, Double>()
    var score = 0.0
    priorities.forEachIndexed { index, key ->
        if (!present(key)) return@forEachIndexed
        val weight = priorities.size - index
        val points = clamp100(valueOf(key)!!) * weight / totalWeight
        breakdown[key.value] = round1(points)
        score += points // RAW accumulation — cf. ffcsRanking.ts:411
    }
    return CompositeRankingResult(round1(score), breakdown)
}

/** Boundary overload: parse raw wire keys, dropping unknown ones. */
fun combineRankingScoresByKey(
    components: Map<String, Double?>,
    priorityOrder: List<String>,
): CompositeRankingResult {
    val priorities = priorityOrder.mapNotNull { key -> RankingPriority.DEFAULT_ORDER.firstOrNull { it.value == key } }
    val parsed = RankingComponents(
        profile = components[RankingPriority.PROFILE.value],
        faculty = components[RankingPriority.FACULTY.value],
        sort = components[RankingPriority.SORT.value],
        time = components[RankingPriority.TIME.value],
    )
    return combineRankingScores(parsed, priorities)
}

// ── exclusivity (ffcsGenerate.ts:188-200) ───────────────────────────────────

/**
 * One kind of ranking runs per call. A present [RankingFactorsConfig] always
 * wins over the legacy `rankingMode` / `rankingPriorityOrder` blend; the
 * legacy path runs only when factors are absent and some legacy ranking was
 * requested. Both absent means "no ranking score at all" (never zero).
 */
sealed interface RankingSelection {
    data class Factors(val config: RankingFactorsConfig) : RankingSelection
    data class Legacy(val priorityOrder: List<RankingPriority> = RankingPriority.DEFAULT_ORDER) : RankingSelection
    object None : RankingSelection

    companion object {
        fun resolve(rankingFactors: RankingFactorsConfig?, hasLegacyRanking: Boolean): RankingSelection =
            when {
                rankingFactors != null -> Factors(rankingFactors)
                hasLegacyRanking -> Legacy()
                else -> None
            }
    }
}

/** Dispatch the single ranking path selected for a generation call. */
fun combineForSelection(
    selection: RankingSelection,
    factorScores: Map<RankingFactor, Double?>,
    components: RankingComponents,
): CompositeRankingResult? = when (selection) {
    is RankingSelection.Factors -> {
        val disabled = selection.config.disabled.toSet()
        combineFactorScores(factorScores, selection.config.order.filterNot { it in disabled })
    }
    is RankingSelection.Legacy -> combineRankingScores(components, selection.priorityOrder)
    RankingSelection.None -> null
}
