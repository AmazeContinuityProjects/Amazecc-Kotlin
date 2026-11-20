package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.buildGradeBoundaries
import com.amazecc.app.shared.domain.isRelativeGrading
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure, headless-testable arithmetic for the Marks & GPA Predictor.
 *
 * Nothing here reads AppState, Compose or a clock. Every value comes from the real VTOP marks
 * payload copied by the host, so the predictor can never print a made-up GPA or FAT requirement.
 * The absolute grade ladder mirrors the web client (S>=90, A>=80, B>=70, C>=60, D>=55, E>=50).
 */
internal object M3EPredictorDerivation {

    /** One course's real inputs, copied out of the domain snapshot by the host. */
    data class CourseInput(
        val code: String,
        val title: String,
        val credits: Int,
        val publishedGrade: String?,
        val assessments: List<M3EAssessmentItem>,
        /** Real cohort curve from /api/services/curves; null keeps the absolute ladder. */
        val classStats: ClassStats? = null,
        /**
         * VTOP grading system and course type. A curve is only valid for relative-graded courses
         * (theory / embedded). Null means "not supplied", which opts out of curving entirely —
         * the same default canonical `estimateGradeWithCurve` uses (MarksPredictor.kt:634-642).
         */
        val courseSystem: String? = null,
        val courseType: String? = null
    )

    private val gradeCutoffs = mapOf(
        "S" to 90.0, "A" to 80.0, "B" to 70.0, "C" to 60.0, "D" to 55.0, "E" to 50.0
    )

    /** Points for a letter, or null when VTOP never published a recognised grade. */
    fun pointsFor(grade: String?): Int? = GradeHistory.gradePoints(grade)

    /** Cutoff used by the web predictor for a target letter, or null for an unknown letter. */
    fun cutoffFor(grade: String?): Double? = cutoffFor(grade, null)

    /**
     * Cutoff for [grade]. A cohort curve is consulted only when the caller supplies a grading
     * system AND [isRelativeGrading] says the course is relative-graded (theory/embedded).
     * Without a grading system — or with no usable class data — this stays on the absolute v5.0
     * ladder, never a made-up curve. Canonical gate: `estimateGradeWithCurve` /
     * `computeCoursePrediction` in domain/MarksPredictor.kt:634-642, 679-680.
     */
    fun cutoffFor(
        grade: String?,
        classStats: ClassStats?,
        courseSystem: String? = null,
        courseType: String? = null
    ): Double? {
        val letter = grade?.trim()?.uppercase() ?: return null
        val curve = classStats.takeIf {
            it != null && courseSystem != null && isRelativeGrading(courseSystem, courseType)
        }
        if (curve != null) {
            return buildGradeBoundaries(curve).firstOrNull { it.grade == letter }?.limit
        }
        return gradeCutoffs[letter]
    }

    /**
     * Weighted internal marks actually scored vs the maximum obtainable from the published
     * component weightages. Returns null when no assessment carries usable numbers, so the UI
     * says "not available" instead of showing a confident-looking 0.
     *
     * Field semantics matter here: [M3EAssessmentItem.weightagePercent] is the component's share
     * of the 100-point total (e.g. 15 for a CAT), while [M3EAssessmentItem.weightageMarks] is
     * VTOP's *already obtained* weighted score (`ratio * weightagePercent`). Using the latter as
     * the weight would apply the ratio twice and silently deflate both totals.
     */
    fun internalMarks(assessments: List<M3EAssessmentItem>): Pair<Double, Double>? {
        var scored = 0.0
        var maximum = 0.0
        var usable = false
        for (assessment in assessments) {
            val maxMark = assessment.maxMarks
            // The share of the total is the PERCENT, never the obtained marks. Parity:
            // web marksPredictor.ts:317,333 and canonical Kotlin MarksPredictor.kt:296,311-312
            // both accumulate `scored/maxMark * weightagePercent`.
            val weight = assessment.weightagePercent
            if (maxMark <= 0.0 || weight <= 0.0) continue
            val ratio = min(max(assessment.scoredMarks, 0.0), maxMark) / maxMark
            scored += ratio * weight
            maximum += weight
            usable = true
        }
        return if (usable && maximum > 0.0) scored to maximum else null
    }

    /**
     * FAT score (on a 0-100 scale) needed to reach [targetGrade].
     *
     * Internal marks contribute `internalMax` out of 100 and the final exam contributes the
     * remainder. A negative requirement is clamped to zero: already-secured marks are reported
     * honestly rather than as a fabricated deficit. Curves are gated by [courseSystem]/[courseType]
     * exactly as in [cutoffFor]; omitting the grading system keeps the absolute ladder.
     *
     * Deliberate simplified scope (evidence, not an oversight): this helper answers one narrow
     * question — what FAT percentage closes the remaining deficit. It intentionally does NOT
     * implement the web solver's 40% theory-FAT regulatory pass floor or its "impossible" /
     * feasibility verdicts (`AmazeCC/src/lib/marksPredictor.ts:717-748`, especially :721-722 and
     * :727-748; surfaced by `TargetGradeSolver.tsx:132-149`). Those, together with embedded-credit
     * weighting and excluding an already published FAT row from the current score
     * (`TargetGradeSolver.tsx:55-76`), belong to the whole-course engines — web
     * `computeCoursePrediction` and canonical Kotlin `MarksPredictor.kt:657+`. Folding a 40% floor
     * or an "impossible" flag into this single `Double?` would misreport one as the other instead
     * of surfacing either, so the compact deficit-only rule is kept and documented here.
     */
    fun requiredFatPercentage(
        internalScored: Double,
        internalMax: Double,
        targetGrade: String,
        classStats: ClassStats? = null,
        courseSystem: String? = null,
        courseType: String? = null
    ): Double? {
        val cutoff = cutoffFor(targetGrade, classStats, courseSystem, courseType) ?: return null
        val fatWeight = 100.0 - internalMax
        val deficit = max(0.0, cutoff - internalScored)
        val required = if (fatWeight <= 0.0) 0.0 else (deficit / fatWeight) * 100.0
        return min(100.0, max(0.0, required)).roundToInt().toDouble()
    }

    /** Credit-weighted SGPA from the grades the user selected, or null with no graded courses. */
    fun predictGpa(inputs: List<CourseInput>, selectedGrades: Map<String, String>): Double? {
        var weighted = 0.0
        var credits = 0
        for (input in inputs) {
            val points = pointsFor(selectedGrades[input.code]) ?: continue
            if (input.credits <= 0) continue
            weighted += points * input.credits
            credits += input.credits
        }
        if (credits == 0) return null
        return ((weighted / credits) * 100.0).roundToInt() / 100.0
    }

    /** Presentational tone only; it never feeds back into any number. */
    fun toneFor(requiredFat: Double?): M3ECardTone = when {
        requiredFat == null -> M3ECardTone.NEUTRAL
        requiredFat <= 20.0 -> M3ECardTone.GOOD
        requiredFat <= 50.0 -> M3ECardTone.WARNING
        else -> M3ECardTone.CRITICAL
    }
}
