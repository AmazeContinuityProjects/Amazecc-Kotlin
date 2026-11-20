package com.amazecc.app.shared.domain

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of Node's `src/lib/marksPredictor.ts`.
 *
 * This is a *prediction engine*, not a display model: it turns a list of assessments (each with a
 * max mark, a weightage percentage and either a score or nothing yet) into the three numbers the
 * predictor screen exists to answer - where you are now, what you could still reach, and how much
 * of the mark you have already lost.
 *
 * Why it is worth porting rather than keeping the screen's own arithmetic: `GPAPredictorScreen`
 * already had a FAT solver, but it hardcoded one regimen (two CATs out of 50 worth 30 marks each,
 * a quiz worth its raw score, FAT at 40%) and, for an embedded course, *guessed* the theory/lab
 * split with a 75/25 constant and a comment saying so. This computes the split from the course's
 * real credits, works for any regimen VTOP publishes, and adds the two things the screen had no
 * answer for: the VIT 40% FAT floor, and a feasibility ladder.
 *
 * Node's Tailwind class strings (`text-emerald-600`, `bg-teal-500/10`, …) are **not** ported -
 * this theme has no such vocabulary. Grade colour goes through `gradeColorIndex` onto
 * `AmazeColors.chart1..5`, exactly as `GradesScreen` already does. Same call as Wave 1's
 * `toneForGrade`.
 */

// ── Types ─────────────────────────────────────────────────────────────────────

enum class PredictorComponent(val wire: String) {
    THEORY("theory"),
    LAB("lab"),
    PROJECT("project"),
    SOFTSKILL("softskill"),
    ;

    /** The component an assessment row belongs to, for the three that can carry marks. */
    fun asMarkComponent(): MarkComponent? = when (this) {
        THEORY -> MarkComponent.THEORY
        LAB -> MarkComponent.LAB
        PROJECT -> MarkComponent.PROJECT
        SOFTSKILL -> null
    }

    companion object {
        fun fromWire(value: String?): PredictorComponent? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

/** The three components a course's marks are actually summed over. */
enum class MarkComponent(val wire: String) {
    THEORY("theory"),
    LAB("lab"),
    PROJECT("project"),
}

data class PredictorAssessment(
    val id: String,
    val title: String,
    val maxMark: Double,
    val weightagePercent: Double,
    /** `null` when the assessment has not happened or has not been recorded. */
    val scoredMark: Double?,
    val status: String? = null,
    val isSimulated: Boolean = false,
    val component: PredictorComponent = PredictorComponent.THEORY,
)

data class RegimenItem(val title: String, val maxMark: Double, val weightagePercent: Double)

data class RegimenPreset(
    val id: String,
    val name: String,
    val category: PredictorComponent,
    val description: String,
    /** The preset a soft-skill regimen drives: Node files those under theory. */
    val component: MarkComponent,
    val items: List<RegimenItem>,
)

data class ProcessedAssessment(
    val id: String,
    val title: String,
    val maxMark: Double,
    val weightagePercent: Double,
    val scoredMark: Double?,
    val scoredWeight: Double,
    val lostWeight: Double,
    val percentage: Double,
    val isPending: Boolean,
    val isSimulated: Boolean,
)

data class ComponentStats(
    val component: MarkComponent,
    val credits: Double,
    val totalWeightConfigured: Double,
    val scoredWeight: Double,
    val pendingWeight: Double,
    val lostWeight: Double,
    val maxPossibleWeight: Double,
    val completedAssessmentsCount: Int,
    val pendingAssessmentsCount: Int,
    val totalAssessmentsCount: Int,
    val assessments: List<ProcessedAssessment>,
)

/** A letter and what it means in words. Colour is the caller's, per the file note. */
data class EstimatedGrade(val letter: String, val description: String)

enum class RiskLevel { SAFE, MODERATE, WARNING, CRITICAL }

enum class Feasibility(val wire: String) {
    SECURED("secured"),
    EASY("easy"),
    MODERATE("moderate"),
    TOUGH("tough"),
    MIRACLE("miracle"),
    IMPOSSIBLE("impossible"),
}

data class CoursePredictionResult(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val isEmbedded: Boolean,
    val theoryCredits: Double,
    val labCredits: Double,
    val totalCredits: Double,
    /** 0-100, scaled to the course. */
    val currentScoredScaled: Double,
    val potentialPendingScaled: Double,
    /** The deficit in total course marks: what has already been lost. */
    val pointsLostScaled: Double,
    /** `100 - pointsLostScaled`. */
    val maxPossibleScaled: Double,
    val theoryStats: ComponentStats?,
    val labStats: ComponentStats?,
    val projectStats: ComponentStats?,
    val estimatedGrade: EstimatedGrade,
    val ceilingGrade: EstimatedGrade,
    val riskLevel: RiskLevel,
    val pacePct: Double = 0.0,
    val currentStandingPct: Double = 0.0,
    val optimalGrade: EstimatedGrade? = estimatedGrade,
    val maximisableGrade: EstimatedGrade = ceilingGrade,
)

data class TargetFatResult(
    val targetScore: Double,
    val targetGrade: String,
    val currentScoreWithoutFat: Double,
    val requiredFatWeight: Double,
    val fatWeightagePercent: Double,
    val fatMaxMark: Double,
    val requiredFatRawScore: Double,
    val requiredFatPercentage: Double,
    val isAchievable: Boolean,
    val isAlreadyAchieved: Boolean,
    /** VIT requires at least 40% in the FAT. */
    val meetsVitMinimumCutoff: Boolean,
    val feasibility: Feasibility,
    val feasibilityMessage: String,
) {
    val requiredFATWeight: Double get() = requiredFatWeight
    val requiredFATRawScore: Double get() = requiredFatRawScore
    val requiredFATPercentage: Double get() = requiredFatPercentage
    val meetsVITMinimumCutoff: Boolean get() = meetsVitMinimumCutoff
    val currentScoreWithoutFAT: Double get() = currentScoreWithoutFat
}

// ── Arithmetic helpers ───────────────────────────────────────────────────────

/**
 * JavaScript's `Math.round`, which rounds halves towards **+Infinity**.
 *
 * `kotlin.math.round` rounds halves away from zero, so the two disagree on negatives
 * (`Math.round(-2.5)` is -2, `round(-2.5)` is -3). Node clamps most of these values at zero
 * first, but `normalizeWeights` does not, so the difference is reachable with a junk weight.
 */
private fun jsRound(value: Double): Double = kotlin.math.floor(value + 0.5)

/** `Math.round(x * 100) / 100`, which is how every weight in Node's output is rounded. */
fun round2(value: Double): Double = jsRound(value * 100.0) / 100.0

/** `Math.round(x * 10) / 10`. */
fun round1(value: Double): Double = jsRound(value * 10.0) / 10.0

/**
 * Node's `safeNumber`.
 *
 * One deliberate quirk carried over: JavaScript's `Number("")` is `0`, not `NaN`, so a blank
 * string is **zero** rather than the fallback. Callers that care check for blank before calling -
 * `predictorAssessmentsFromVtop` does exactly that, for the same reason.
 */
fun safeNumber(raw: String?, fallback: Double = 0.0): Double {
    val text = raw?.trim() ?: return fallback
    if (text.isEmpty()) return 0.0
    return text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback
}

/** Renders one decimal place the way Node's `toFixed(1)` does - trailing zeros kept. */
private fun fixed1(value: Double): String {
    val rounded = jsRound(value * 10.0) / 10.0
    val whole = rounded.toLong()
    val frac = kotlin.math.abs((rounded - whole) * 10.0).toLong()
    return "$whole.$frac"
}

// ── Weight normalisation ─────────────────────────────────────────────────────

data class WeightageItem(
    val title: String,
    val weightagePercent: Double,
)

@kotlin.jvm.JvmName("normalizeGenericWeights")
fun normalizeWeights(items: List<WeightageItem>): List<WeightageItem> {
    if (items.isEmpty()) return items
    val total = items.sumOf { safeNumber(it.weightagePercent.toString()) }
    if (total <= 0.0) return items

    var runningSum = 0.0
    return items.mapIndexed { index, item ->
        if (index == items.size - 1) {
            val remaining = maxOf(0.0, 100.0 - runningSum)
            item.copy(weightagePercent = round2(remaining))
        } else {
            val scaled = round2((item.weightagePercent / total) * 100.0)
            runningSum += scaled
            item.copy(weightagePercent = scaled)
        }
    }
}

/**
 * Scales an arbitrary weight list so it sums to exactly 100, with the last row absorbing the
 * rounding remainder.
 *
 * Vit's published regimens do not always add up - a course can arrive as 15/15/10/10/10/40 with a
 * row missing, or 20/20/20 with the FAT unpublished. Every downstream number is a share of 100,
 * so an unnormalised list silently reports a student as losing marks they never had.
 */
fun normalizeWeights(items: List<PredictorAssessment>): List<PredictorAssessment> {
    if (items.isEmpty()) return items
    val total = items.sumOf { safeNumber(it.weightagePercent.toString()) }
    if (total <= 0.0) return items

    var runningSum = 0.0
    return items.mapIndexed { index, item ->
        if (index == items.size - 1) {
            val remaining = maxOf(0.0, 100.0 - runningSum)
            item.copy(weightagePercent = round2(remaining))
        } else {
            val scaled = round2((item.weightagePercent / total) * 100.0)
            runningSum += scaled
            item.copy(weightagePercent = scaled)
        }
    }
}

// ── Per-component statistics ──────────────────────────────────────────────────

/**
 * The scored / pending / lost split for one component's assessments.
 *
 * The arithmetic that matters: an assessment you have already sat contributes
 * `(scored / maxMark) * weightage` to what you have, and whatever it fell short of its full weight
 * is *lost* - not pending. So `maxPossibleWeight` is `configured - lost`, which stays reachable
 * even when the FAT has not happened. That is the number the "you can still get an S" message is
 * built on.
 *
 * A `maxMark` of 0 is floored at 0.1 so a course whose assessment has no published maximum cannot
 * divide by zero.
 */
fun computeComponentStats(
    assessments: List<PredictorAssessment>,
    component: MarkComponent,
    credits: Double = 1.0,
): ComponentStats {
    var totalWeightConfigured = 0.0
    var scoredWeight = 0.0
    var pendingWeight = 0.0
    var lostWeight = 0.0
    var completedCount = 0
    var pendingCount = 0

    val processed = assessments.map { asm ->
        val maxMark = maxOf(0.1, safeNumber(asm.maxMark.toString(), 100.0))
        val weightagePercent = safeNumber(asm.weightagePercent.toString(), 0.0)
        totalWeightConfigured += weightagePercent

        val isPending = asm.scoredMark == null
        val isSimulated = asm.isSimulated

        var asmScoredWeight = 0.0
        var asmLostWeight = 0.0
        var percentage = 0.0

        if (isPending) {
            pendingWeight += weightagePercent
            pendingCount++
        } else {
            val scored = asm.scoredMark!!.coerceIn(0.0, maxMark)
            percentage = ((scored / maxMark) * 100.0).coerceIn(0.0, 100.0)
            asmScoredWeight = (percentage / 100.0) * weightagePercent
            asmLostWeight = maxOf(0.0, weightagePercent - asmScoredWeight)
            scoredWeight += asmScoredWeight
            lostWeight += asmLostWeight
            completedCount++
        }

        ProcessedAssessment(
            id = asm.id,
            title = asm.title,
            maxMark = maxMark,
            weightagePercent = weightagePercent,
            scoredMark = asm.scoredMark,
            scoredWeight = round2(asmScoredWeight),
            lostWeight = round2(asmLostWeight),
            percentage = round1(percentage),
            isPending = isPending,
            isSimulated = isSimulated,
        )
    }

    val maxPossibleWeight = maxOf(0.0, totalWeightConfigured - lostWeight)

    return ComponentStats(
        component = component,
        credits = maxOf(0.5, credits),
        totalWeightConfigured = round2(totalWeightConfigured),
        scoredWeight = round2(scoredWeight),
        pendingWeight = round2(pendingWeight),
        lostWeight = round2(lostWeight),
        maxPossibleWeight = round2(maxPossibleWeight),
        completedAssessmentsCount = completedCount,
        pendingAssessmentsCount = pendingCount,
        totalAssessmentsCount = assessments.size,
        assessments = processed,
    )
}

// ── Grade bands ───────────────────────────────────────────────────────────────

/**
 * Class-level cohort statistics, as `marksStats[classNbr].overall` carries them.
 *
 * Mirrors the web `ClassStats` type. A [manual] curve is typed by the reader ("what if the class
 * averages 62 with σ 11?") and is taken exactly as given: no contributor minimum, no σ pooling,
 * no early-estimate flag.
 */
data class ClassStats(
    val count: Int,
    val mean: Double,
    val sd: Double,
    val manual: Boolean = false,
)

/** One rung of a ladder: [grade] starts at [limit], inclusive. */
data class GradedBoundary(val grade: String, val limit: Double)

/**
 * The fixed ladder: FFCS Academic Regulations v5.0 (ACE) §9.5 Table 6.
 *
 * D and E are five points wide, not ten: the pass line is 50 (§9.4 c), so nothing below 50 passes.
 */
val ABSOLUTE_GRADE_BOUNDARIES: List<GradedBoundary> = listOf(
    GradedBoundary("S", 90.0),
    GradedBoundary("A", 80.0),
    GradedBoundary("B", 70.0),
    GradedBoundary("C", 60.0),
    GradedBoundary("D", 55.0),
    GradedBoundary("E", 50.0),
    GradedBoundary("F", 0.0),
)

/** How many classmates must have synced before the app curves at all. */
const val MIN_CURVE_SAMPLE: Int = 5

/** Below this many contributors a real curve is flagged as an early estimate. */
const val CONFIDENT_CURVE_SAMPLE: Int = 15

/** A typical σ for a VIT theory class's grand total, and its weight in degrees of freedom. */
private const val PRIOR_SD: Double = 12.0
private const val PRIOR_DF: Int = 4

private val GRADE_DESCRIPTIONS = mapOf(
    "S" to "Outstanding (10 Grade Points)",
    "A" to "Excellent (9 Grade Points)",
    "B" to "Very Good (8 Grade Points)",
    "C" to "Good (7 Grade Points)",
    "D" to "Average (6 Grade Points)",
    "E" to "Pass (5 Grade Points)",
    "F" to "Fail (0 Grade Points)",
)

/** The words a band carries, kept in one place so every grade surface reads the same string. */
fun gradeDescription(letter: String): String =
    GRADE_DESCRIPTIONS[letter] ?: GRADE_DESCRIPTIONS.getValue("F")

private fun estimated(letter: String): EstimatedGrade = EstimatedGrade(letter, gradeDescription(letter))

/**
 * A reader's own mean and σ as class statistics, or null when either is out of range.
 *
 * σ below 0.5 would collapse every band onto the mean; above 50 the whole ladder leaves 0–100.
 */
fun manualClassStats(mean: Double?, sd: Double?): ClassStats? {
    if (mean == null || sd == null) return null
    if (!mean.isFinite() || !sd.isFinite()) return null
    if (mean < 0.0 || mean > 100.0) return null
    if (sd < 0.5 || sd > 50.0) return null
    return ClassStats(count = 0, mean = mean, sd = sd, manual = true)
}

/**
 * The σ the ladder actually uses: the sample σ pooled with [PRIOR_SD].
 *
 * `((n − 1)·s² + k·σ₀²) / ((n − 1) + k)`. A sample of 5 can report σ = 3 or σ = 25 for the same
 * class; pooling keeps a handful of contributors from swinging every band by ten marks.
 */
fun effectiveSd(classStats: ClassStats): Double {
    if (classStats.manual) return classStats.sd
    val df = max(0, classStats.count - 1).toDouble()
    val sd = if (classStats.sd.isFinite()) max(0.0, classStats.sd) else 0.0
    return sqrt((df * sd * sd + PRIOR_DF * PRIOR_SD * PRIOR_SD) / (df + PRIOR_DF))
}

/** Whether these statistics are enough to curve on; a σ of 0 is allowed once n ≥ 5. */
fun hasUsableClassStats(classStats: ClassStats?): Boolean {
    val stats = classStats ?: return false
    return if (stats.manual) {
        stats.mean.isFinite() && stats.sd.isFinite() && stats.sd > 0.0
    } else {
        stats.count >= MIN_CURVE_SAMPLE &&
            stats.mean.isFinite() && stats.sd.isFinite() && stats.sd >= 0.0
    }
}

/** The statistics the ladder is built from, with σ stabilised; null when unusable. */
fun effectiveClassStats(classStats: ClassStats?): ClassStats? {
    val stats = classStats ?: return null
    if (!hasUsableClassStats(stats)) return null
    return stats.copy(sd = effectiveSd(stats))
}

/** Whether a curve rests on few enough contributors to call it an early estimate. */
fun isEarlyEstimate(classStats: ClassStats?): Boolean {
    val stats = classStats ?: return false
    return hasUsableClassStats(stats) && !stats.manual && stats.count < CONFIDENT_CURVE_SAMPLE
}

/**
 * Whether a course is graded on a curve. The grading *system* does not change the answer; the
 * course type does. Theory (including an embedded course's theory half) is relative; lab-only,
 * project, soft-skill and online courses are absolute.
 */
@Suppress("UNUSED_PARAMETER")
fun isRelativeGrading(courseSystem: String?, courseType: String?): Boolean {
    val type = courseType.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
    if (type.isEmpty()) return false
    if (type.contains("theory")) return true
    return type == "embedded" || type == "embedded lab"
}

/** ln Γ(x), Lanczos (g = 7, n = 9). */
private fun lnGamma(x: Double): Double {
    val c = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7,
    )
    if (x < 0.5) return ln(PI / sin(PI * x)) - lnGamma(1.0 - x)
    val z = x - 1.0
    var a = c[0]
    val t = z + 7.5
    for (i in 1..8) a += c[i] / (z + i)
    return 0.5 * ln(2.0 * PI) + (z + 0.5) * ln(t) - t + ln(a)
}

/** Regularised lower incomplete gamma P(a, x): series below a + 1, continued fraction above. */
private fun gammaP(a: Double, x: Double): Double {
    if (x <= 0.0) return 0.0
    val lnPrefix = a * ln(x) - x - lnGamma(a)
    if (x < a + 1.0) {
        var term = 1.0 / a
        var sum = term
        for (n in 1 until 500) {
            term *= x / (a + n)
            sum += term
            if (kotlin.math.abs(term) < kotlin.math.abs(sum) * 1e-15) break
        }
        return minOf(1.0, sum * exp(lnPrefix))
    }
    val tiny = 1e-300
    var b = x + 1.0 - a
    var c = 1.0 / tiny
    var d = 1.0 / b
    var h = d
    for (i in 1 until 500) {
        val an = -i * (i - a)
        b += 2.0
        d = an * d + b
        if (kotlin.math.abs(d) < tiny) d = tiny
        c = b + an / c
        if (kotlin.math.abs(c) < tiny) c = tiny
        d = 1.0 / d
        val delta = d * c
        h *= delta
        if (kotlin.math.abs(delta - 1.0) < 1e-15) break
    }
    return max(0.0, 1.0 - exp(lnPrefix) * h)
}

/** The p-quantile of a unit-scale gamma(shape): bisection on the monotone CDF. */
private fun gammaQuantile(p: Double, shape: Double): Double {
    if (p <= 0.0) return 0.0
    var lo = 0.0
    var hi = max(1.0, shape)
    while (gammaP(shape, hi) < p) hi *= 2.0
    var i = 0
    while (i < 200 && hi - lo > 1e-12 * max(1.0, hi)) {
        val mid = (lo + hi) / 2.0
        if (gammaP(shape, mid) < p) lo = mid else hi = mid
        i++
    }
    return (lo + hi) / 2.0
}

/**
 * The S cutoff when `mean + 1.5σ` passes 100 (v5.0 §9.5): "the maximum of 3 or the top 5%
 * students who have scored ≥ 80 marks in aggregate". A mean and σ cannot give a rank, so the
 * cutoff is estimated by modelling the *deficit* as a gamma distribution bounded at the ceiling.
 */
fun estimateTopShareCutoff(mean: Double, sd: Double): Double {
    val deficit = 100.0 - mean
    if (!(deficit > 0.0) || !(sd > 0.0)) return 100.0
    val shape = deficit * deficit / (sd * sd)
    val scale = sd * sd / deficit
    val cutoff = 100.0 - gammaQuantile(0.05, shape) * scale
    return minOf(100.0, max(80.0, jsRound(cutoff)))
}

/**
 * The ladder for one course (v5.0 §9.5 Table 5): S at +1.5σ, then A/B/C/D/E half a σ down,
 * with the E–F line at `min(mean − 2σ, 50)`. Every rung is a `Math.round` integer, clamped so
 * no band is below zero or above the one before it. With no usable class data — or for a course
 * that is not graded on a curve — the absolute ladder is returned unchanged.
 */
fun buildGradeBoundaries(classStats: ClassStats?, relative: Boolean = true): List<GradedBoundary> {
    if (!relative) return ABSOLUTE_GRADE_BOUNDARIES
    val stats = effectiveClassStats(classStats) ?: return ABSOLUTE_GRADE_BOUNDARIES
    val mean = stats.mean
    val sd = stats.sd
    val sRaw = jsRound(mean + 1.5 * sd)
    val sBoundary = if (sRaw > 100) estimateTopShareCutoff(mean, sd) else sRaw

    val raw = listOf(
        "S" to sBoundary,
        "A" to jsRound(mean + 0.5 * sd),
        "B" to jsRound(mean - 0.5 * sd),
        "C" to jsRound(mean - 1.0 * sd),
        "D" to jsRound(mean - 1.5 * sd),
        "E" to minOf(jsRound(mean - 2.0 * sd), 50.0),
    )

    val ladder = mutableListOf<GradedBoundary>()
    var ceiling = 100.0
    for ((grade, limit) in raw) {
        val clamped = max(0.0, minOf(limit, ceiling))
        ladder += GradedBoundary(grade, clamped)
        ceiling = clamped
    }
    ladder += GradedBoundary("F", 0.0)
    return ladder
}

/**
 * Scan the ladder down until the student's weightage meets a band's floor.
 *
 * A total is rounded **up** to the next whole number before it is read — 79.5 is an 80 — which is
 * how grades are calculated against these tables. The epsilon keeps binary noise from ceiling a
 * whole-number total up a mark.
 */
fun gradeAt(pct: Double, boundaries: List<GradedBoundary> = ABSOLUTE_GRADE_BOUNDARIES): String {
    val weightage = ceil(pct - 1e-9)
    for (band in boundaries) {
        if (weightage >= band.limit) return band.grade
    }
    return "F"
}

/**
 * The letter for a 0-100 score on the absolute ladder.
 *
 * The score-only overload stays on [ABSOLUTE_GRADE_BOUNDARIES] so existing callers keep getting an
 * absolute grade; pass [classStats] to the other overload for relative grading.
 */
fun estimateGrade(score: Double): EstimatedGrade = estimated(gradeAt(score))

/** The letter for a 0-100 score against a cohort curve, falling back to absolute when unusable. */
fun estimateGrade(score: Double, classStats: ClassStats?): EstimatedGrade =
    estimated(gradeAt(score, buildGradeBoundaries(classStats, relative = true)))

// ── Course-level prediction ───────────────────────────────────────────────────

/**
 * Combines a course's components into one credit-weighted verdict.
 *
 * For an embedded course each component is weighted by **its own credits** - a 3-credit theory
 * half and a 1-credit lab half give `(3 * theory + 1 * lab) / 4`. For a course that is not
 * embedded every present component counts as 1, because there is nothing to split.
 *
 * [pointsLostScaled] is the number the risk level and the whole screen hang off, so it is worth
 * being precise about: it is the credit-weighted sum of what each *completed* assessment fell
 * short by. Pending assessments contribute nothing to it.
 */
fun computeCoursePrediction(
    courseCode: String,
    courseTitle: String,
    courseType: String,
    theoryAssessments: List<PredictorAssessment> = emptyList(),
    labAssessments: List<PredictorAssessment> = emptyList(),
    projectAssessments: List<PredictorAssessment> = emptyList(),
    theoryCredits: Double = 3.0,
    labCredits: Double = 1.0,
    projectCredits: Double = 0.0,
): CoursePredictionResult {
    val hasTheory = theoryAssessments.isNotEmpty()
    val hasLab = labAssessments.isNotEmpty()
    val hasProject = projectAssessments.isNotEmpty()
    val isEmbedded = (hasTheory && hasLab) ||
        EmbeddedLinkCode.componentOf(courseCode, courseType).isEmbedded

    val theoryStats = if (hasTheory) computeComponentStats(theoryAssessments, MarkComponent.THEORY, theoryCredits) else null
    val labStats = if (hasLab) computeComponentStats(labAssessments, MarkComponent.LAB, labCredits) else null
    val projectStats = if (hasProject) computeComponentStats(projectAssessments, MarkComponent.PROJECT, projectCredits) else null

    var totalCredits = 0.0
    var weightedScoredSum = 0.0
    var weightedPendingSum = 0.0
    var weightedLostSum = 0.0

    if (theoryStats != null) {
        val cred = if (isEmbedded) theoryCredits else 1.0
        totalCredits += cred
        weightedScoredSum += theoryStats.scoredWeight * cred
        weightedPendingSum += theoryStats.pendingWeight * cred
        weightedLostSum += theoryStats.lostWeight * cred
    }
    if (labStats != null) {
        val cred = if (isEmbedded) labCredits else 1.0
        totalCredits += cred
        weightedScoredSum += labStats.scoredWeight * cred
        weightedPendingSum += labStats.pendingWeight * cred
        weightedLostSum += labStats.lostWeight * cred
    }
    if (projectStats != null) {
        val cred = if (isEmbedded) projectCredits else 1.0
        totalCredits += cred
        weightedScoredSum += projectStats.scoredWeight * cred
        weightedPendingSum += projectStats.pendingWeight * cred
        weightedLostSum += projectStats.lostWeight * cred
    }

    val effectiveCredits = maxOf(1.0, totalCredits)

    // Node's source reads `(weightedScoredSum / effectiveCredits) * 100` here
    // (`marksPredictor.ts:439-441`), but that contradicts its own test, which asserts 88.88 for a
    // combined score of 88.875 - and to `maxPossibleScaled` below, which is `100 - pointsLost`
    // and so only means anything while the lost figure is on a 0-100 scale. Taken literally the
    // `* 100` puts every course past 900 and grades all of them S. The test is the authority.
    val currentScoredScaled = round2(weightedScoredSum / effectiveCredits)
    val potentialPendingScaled = round2(weightedPendingSum / effectiveCredits)
    val pointsLostScaled = round2(weightedLostSum / effectiveCredits)
    val maxPossibleScaled = maxOf(0.0, round2(100.0 - pointsLostScaled))

    val releasedWeight = currentScoredScaled + pointsLostScaled
    val pacePct = if (releasedWeight > 0.0) round2((currentScoredScaled / releasedWeight) * 100.0) else 0.0
    val currentStandingPct = if (releasedWeight > 0.0) round2((currentScoredScaled / releasedWeight) * 100.0) else 0.0
    val optimalGrade = if (releasedWeight > 0.0) estimateGrade(pacePct) else null
    val maximisableGrade = estimateGrade(maxPossibleScaled)

    return CoursePredictionResult(
        courseCode = courseCode,
        courseTitle = courseTitle,
        courseType = courseType,
        isEmbedded = isEmbedded,
        theoryCredits = maxOf(0.0, theoryCredits),
        labCredits = maxOf(0.0, labCredits),
        totalCredits = effectiveCredits,
        currentScoredScaled = currentScoredScaled,
        potentialPendingScaled = potentialPendingScaled,
        pointsLostScaled = pointsLostScaled,
        maxPossibleScaled = maxPossibleScaled,
        theoryStats = theoryStats,
        labStats = labStats,
        projectStats = projectStats,
        // What you have if every pending assessment scores full marks.
        estimatedGrade = estimateGrade(currentScoredScaled + potentialPendingScaled),
        // What you could still reach if you aced everything still to come.
        ceilingGrade = estimateGrade(maxPossibleScaled),
        riskLevel = riskLevelFor(pointsLostScaled),
        pacePct = pacePct,
        currentStandingPct = currentStandingPct,
        optimalGrade = optimalGrade,
        maximisableGrade = maximisableGrade,
    )
}

/** Boundaries are strict `>`, so a course that has lost exactly 40 points reads WARNING. */
fun riskLevelFor(pointsLostScaled: Double): RiskLevel = when {
    pointsLostScaled > 40.0 -> RiskLevel.CRITICAL
    pointsLostScaled > 20.0 -> RiskLevel.WARNING
    pointsLostScaled > 10.0 -> RiskLevel.MODERATE
    else -> RiskLevel.SAFE
}

// ── Target FAT solver ─────────────────────────────────────────────────────────

/**
 * How much of the FAT you need for a target score.
 *
 * For an embedded course the target has to be unpicked across both halves before it can be
 * compared to one component's own weighted score:
 *
 * ```
 * target = (compCredits * compWeighted + otherCredits * otherWeighted) / totalCredits
 *   =>  compWeighted = (target * totalCredits - otherCredits * otherWeighted) / compCredits
 * ```
 *
 * That is the step the screen's old arithmetic could not do, which is why it guessed 75/25.
 *
 * Three results matter to the caller and are easy to conflate:
 *  * [isAlreadyAchieved] - the deficit is already met, so you can score zero in the FAT.
 *  * [isAchievable] - the required raw score is inside the FAT's maximum.
 *  * [meetsVitMinimumCutoff] - the required raw score clears VIT's 40% FAT floor, which is a
 *    *regulation* rule rather than an arithmetic one. A target can be mathematically achievable
 *    and still fail this.
 */
fun solveTargetFat(
    targetScore: Double,
    currentScoredScaled: Double,
    fatWeightagePercent: Double = 40.0,
    fatMaxMark: Double = 100.0,
    theoryCredits: Double = 3.0,
    labCredits: Double = 1.0,
    isEmbedded: Boolean = false,
    component: MarkComponent = MarkComponent.THEORY,
    otherComponentWeighted: Double = 0.0,
): TargetFatResult {
    val targetGradeObj = estimateGrade(targetScore)
    val totalCredits = if (isEmbedded) theoryCredits + labCredits else 1.0
    val compCredits = if (component == MarkComponent.THEORY) theoryCredits else labCredits
    val otherCredits = if (component == MarkComponent.THEORY) labCredits else theoryCredits

    var requiredCompWeighted = targetScore
    if (isEmbedded && totalCredits > 0.0) {
        val requiredTotalPoints = targetScore * totalCredits
        val otherPoints = otherCredits * otherComponentWeighted
        requiredCompWeighted = (requiredTotalPoints - otherPoints) / compCredits
    }

    val deficitWeight = requiredCompWeighted - currentScoredScaled
    val rawScore = (deficitWeight / maxOf(0.1, fatWeightagePercent)) * fatMaxMark
    val requiredPercentage = (rawScore / fatMaxMark) * 100.0

    val isAlreadyAchieved = deficitWeight <= 0.0
    val isAchievable = rawScore <= fatMaxMark
    val meetsCutoff = rawScore >= 0.4 * fatMaxMark

    val feasibility: Feasibility
    val message: String
    when {
        isAlreadyAchieved -> {
            feasibility = Feasibility.SECURED
            message = "Target already secured! Even with 0 in FAT, you have achieved $targetScore%."
        }
        !isAchievable -> {
            feasibility = Feasibility.IMPOSSIBLE
            val reachable = 100.0 - (targetScore - currentScoredScaled)
            message = "Mathematically out of reach. Max possible score is ${fixed1(reachable)}%."
        }
        rawScore > 0.90 * fatMaxMark -> {
            feasibility = Feasibility.MIRACLE
            message = "Miracle required: You need ${fixed1(rawScore)} / $fatMaxMark (${jsRound(requiredPercentage).toLong()}%) in FAT."
        }
        rawScore > 0.75 * fatMaxMark -> {
            feasibility = Feasibility.TOUGH
            message = "Tough challenge: You need ${fixed1(rawScore)} / $fatMaxMark (${jsRound(requiredPercentage).toLong()}%) in FAT."
        }
        rawScore > 0.50 * fatMaxMark -> {
            feasibility = Feasibility.MODERATE
            message = "Achievable with solid prep: You need ${fixed1(rawScore)} / $fatMaxMark (${jsRound(requiredPercentage).toLong()}%) in FAT."
        }
        else -> {
            feasibility = Feasibility.EASY
            message = "Comfortable target: You need ${fixed1(maxOf(0.0, rawScore))} / $fatMaxMark (${jsRound(maxOf(0.0, requiredPercentage)).toLong()}%) in FAT."
        }
    }

    return TargetFatResult(
        targetScore = targetScore,
        targetGrade = targetGradeObj.letter,
        currentScoreWithoutFat = round2(currentScoredScaled),
        requiredFatWeight = maxOf(0.0, round2(deficitWeight)),
        fatWeightagePercent = fatWeightagePercent,
        fatMaxMark = fatMaxMark,
        requiredFatRawScore = maxOf(0.0, round2(rawScore)),
        requiredFatPercentage = maxOf(0.0, round1(requiredPercentage)),
        isAchievable = isAchievable,
        isAlreadyAchieved = isAlreadyAchieved,
        meetsVitMinimumCutoff = meetsCutoff,
        feasibility = feasibility,
        feasibilityMessage = message,
    )
}

fun solveTargetFAT(
    targetScore: Double,
    currentScoredScaled: Double,
    fatWeightagePercent: Double = 40.0,
    fatMaxMark: Double = 100.0,
    theoryCredits: Double = 3.0,
    labCredits: Double = 1.0,
    isEmbedded: Boolean = false,
    component: MarkComponent = MarkComponent.THEORY,
    otherComponentWeighted: Double = 0.0,
): TargetFatResult = solveTargetFat(
    targetScore = targetScore,
    currentScoredScaled = currentScoredScaled,
    fatWeightagePercent = fatWeightagePercent,
    fatMaxMark = fatMaxMark,
    theoryCredits = theoryCredits,
    labCredits = labCredits,
    isEmbedded = isEmbedded,
    component = component,
    otherComponentWeighted = otherComponentWeighted,
)

fun computeComponentStats(
    assessments: List<PredictorAssessment>,
    component: String,
    credits: Double = 1.0,
): ComponentStats {
    val comp = when (component.lowercase()) {
        "lab" -> MarkComponent.LAB
        "project" -> MarkComponent.PROJECT
        else -> MarkComponent.THEORY
    }
    return computeComponentStats(assessments, comp, credits)
}

// ── Building the assessment list from live data ───────────────────────────────

/**
 * Turns one live assessment row into a predictor row.
 *
 * VTOP writes a pending score as `""`, `"-"` or nothing at all; all three mean "has not happened"
 * and must not be read as zero. Reading them as zero is how a student is told they have already
 * lost the mark.
 */
fun predictorAssessmentFromVtop(
    id: String,
    title: String?,
    maxMark: String?,
    weightagePercent: String?,
    scoredMark: String?,
    component: PredictorComponent,
): PredictorAssessment {
    val raw = scoredMark?.trim()
    val isPending = raw.isNullOrEmpty() || raw == "-"
    return PredictorAssessment(
        id = id,
        title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: "Assessment",
        maxMark = safeNumber(maxMark, 100.0),
        weightagePercent = safeNumber(weightagePercent, 10.0),
        scoredMark = if (isPending) null else safeNumber(raw, 0.0),
        status = if (isPending) "Pending" else "Present",
        isSimulated = false,
        component = component,
    )
}

/** Node's `/FAT|Final Assessment|Final Test/i` test, kept as one place so it cannot drift. */
private val FAT_TITLE = Regex("FAT|Final Assessment|Final Test", RegexOption.IGNORE_CASE)

/**
 * Node's FAT-gap repair.
 *
 * Vit does not publish the FAT row until the exam is scheduled, so a course mid-term arrives with
 * its published weightage adding up to well under 100 - and every downstream number is a share of
 * 100, so the missing weight reads as marks already lost. When the total is short and no FAT row
 * is present, one is appended for the shortfall, floored at 10%.
 *
 * Returns the list unchanged when the weights already add up or a FAT is already listed.
 */
fun withInferredFat(assessments: List<PredictorAssessment>, component: PredictorComponent): List<PredictorAssessment> {
    if (assessments.isEmpty()) return assessments
    val hasFat = assessments.any { FAT_TITLE.containsMatchIn(it.title) }
    val totalWeight = assessments.sumOf { safeNumber(it.weightagePercent.toString()) }
    if (hasFat || totalWeight >= 100.0) return assessments

    val missingWeight = maxOf(10.0, 100.0 - totalWeight)
    val isLab = component == PredictorComponent.LAB
    return assessments + PredictorAssessment(
        id = "$component-auto-fat",
        title = if (isLab) "Lab FAT" else "FAT",
        maxMark = if (isLab) 50.0 else 100.0,
        weightagePercent = missingWeight,
        scoredMark = null,
        status = "Pending",
        isSimulated = false,
        component = component,
    )
}

/** The preset used when a course publishes no assessments at all. */
fun defaultRegimen(component: PredictorComponent): RegimenPreset? =
    PRESET_REGIMENS.firstOrNull {
        it.id == if (component == PredictorComponent.LAB) "lab-10-continuous" else "theory-standard"
    }

/** A preset's items as pending predictor rows. */
fun RegimenPreset.toPendingAssessments(component: PredictorComponent): List<PredictorAssessment> =
    items.mapIndexed { index, item ->
        PredictorAssessment(
            id = "$component-${index + 1}",
            title = item.title,
            maxMark = item.maxMark,
            weightagePercent = item.weightagePercent,
            scoredMark = null,
            status = "Pending",
            isSimulated = false,
            component = component,
        )
    }

// ── Built-in VIT regimens ────────────────────────────────────────────────────

/**
 * Vit's published assessment patterns, used when a course has not published its own yet.
 *
 * Weightages are transcribed from the handbook rather than derived, which is why they do not all
 * sum to 100 by construction - `lab-5-cycle` is 12 x 5 = 60 plus 40, and `theory-quizzes` is
 * 15+15+10+10+5+5+40 = 100. Anything short of 100 is repaired by [withInferredFat].
 */
val PRESET_REGIMENS: List<RegimenPreset> = listOf(
    RegimenPreset(
        id = "theory-standard",
        name = "Theory: 2 CATs + 3 DAs + FAT (Standard)",
        category = PredictorComponent.THEORY,
        component = MarkComponent.THEORY,
        description = "Standard Theory: CAT-1 (15%), CAT-2 (15%), 3 DAs / Quizzes (10% each), FAT (40%)",
        items = listOf(
            RegimenItem("CAT - I", 50.0, 15.0),
            RegimenItem("CAT - II", 50.0, 15.0),
            RegimenItem("DA - 1", 10.0, 10.0),
            RegimenItem("DA - 2", 10.0, 10.0),
            RegimenItem("DA - 3", 10.0, 10.0),
            RegimenItem("FAT", 100.0, 40.0),
        ),
    ),
    RegimenPreset(
        id = "theory-quizzes",
        name = "Theory: 2 CATs + 2 DAs + 2 Quizzes + FAT",
        category = PredictorComponent.THEORY,
        component = MarkComponent.THEORY,
        description = "CAT-1 (15%), CAT-2 (15%), DA-1 (10%), DA-2 (10%), Quiz-1 (5%), Quiz-2 (5%), FAT (40%)",
        items = listOf(
            RegimenItem("CAT - I", 50.0, 15.0),
            RegimenItem("CAT - II", 50.0, 15.0),
            RegimenItem("DA - 1", 10.0, 10.0),
            RegimenItem("DA - 2", 10.0, 10.0),
            RegimenItem("Quiz - I", 10.0, 5.0),
            RegimenItem("Quiz - II", 10.0, 5.0),
            RegimenItem("FAT", 100.0, 40.0),
        ),
    ),
    RegimenPreset(
        id = "lab-10-continuous",
        name = "Lab: 10 Continuous Experiments + FAT (60/40)",
        category = PredictorComponent.LAB,
        component = MarkComponent.LAB,
        description = "Standard Lab: 10 Lab Continuous Evaluation / Assignments (6% each = 60%) + Lab FAT (40%)",
        items = listOf(
            RegimenItem("Lab Experiment - 1", 100.0, 6.0),
            RegimenItem("Lab Experiment - 2", 100.0, 6.0),
            RegimenItem("Lab Experiment - 3", 100.0, 6.0),
            RegimenItem("Lab Experiment - 4", 100.0, 6.0),
            RegimenItem("Lab Experiment - 5", 100.0, 6.0),
            RegimenItem("Lab Experiment - 6", 100.0, 6.0),
            RegimenItem("Lab Experiment - 7", 100.0, 6.0),
            RegimenItem("Lab Experiment - 8", 100.0, 6.0),
            RegimenItem("Lab Experiment - 9", 100.0, 6.0),
            RegimenItem("Lab Experiment - 10", 100.0, 6.0),
            RegimenItem("Lab FAT", 50.0, 40.0),
        ),
    ),
    RegimenPreset(
        id = "lab-4-pats",
        name = "Lab: 4 PATs (10+15+15+10) + Record (10) + FAT (40)",
        category = PredictorComponent.LAB,
        component = MarkComponent.LAB,
        description = "Periodic Assessment Tests (50%) + Continuous Record/Viva (10%) + Lab FAT (40%)",
        items = listOf(
            RegimenItem("PAT - I", 50.0, 10.0),
            RegimenItem("PAT - II", 50.0, 15.0),
            RegimenItem("PAT - III", 50.0, 15.0),
            RegimenItem("PAT - IV", 50.0, 10.0),
            RegimenItem("Assignment & Record Completion", 100.0, 10.0),
            RegimenItem("Lab FAT", 50.0, 40.0),
        ),
    ),
    RegimenPreset(
        id = "lab-5-cycle",
        name = "Lab: 5 Assessments (12% each) + FAT (40%)",
        category = PredictorComponent.LAB,
        component = MarkComponent.LAB,
        description = "5 Modular Assessments / Experiments (12% each = 60%) + Lab FAT (40%)",
        items = listOf(
            RegimenItem("Assessment - 1", 10.0, 12.0),
            RegimenItem("Assessment - 2", 10.0, 12.0),
            RegimenItem("Assessment - 3", 10.0, 12.0),
            RegimenItem("Assessment - 4", 10.0, 12.0),
            RegimenItem("Assessment - 5", 10.0, 12.0),
            RegimenItem("Lab FAT", 50.0, 40.0),
        ),
    ),
    RegimenPreset(
        id = "project-jcomp",
        name = "Project / J-Component (3 Reviews + Report)",
        category = PredictorComponent.PROJECT,
        component = MarkComponent.PROJECT,
        description = "Review-1 (20%), Review-2 (30%), Final Project Review & Viva (50%)",
        items = listOf(
            RegimenItem("Project Review - I", 50.0, 20.0),
            RegimenItem("Project Review - II", 50.0, 30.0),
            RegimenItem("Final Review, Report & Viva", 100.0, 50.0),
        ),
    ),
    RegimenPreset(
        id = "softskills-sts",
        name = "Soft Skills / STS (2 Assessments + 2 CATs + Final)",
        category = PredictorComponent.SOFTSKILL,
        // Node files soft skills under theory: they are marked out of the same course total.
        component = MarkComponent.THEORY,
        description = "Assessment-1 (15%), Assessment-2 (15%), CAT-1 (15%), CAT-2 (15%), Final Test (40%)",
        items = listOf(
            RegimenItem("Assessment - 1", 15.0, 15.0),
            RegimenItem("Assessment - 2", 15.0, 15.0),
            RegimenItem("CAT - 1", 25.0, 15.0),
            RegimenItem("CAT - 2", 25.0, 15.0),
            RegimenItem("Final Test", 50.0, 40.0),
        ),
    ),
)