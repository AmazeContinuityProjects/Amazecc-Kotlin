package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.buildGradeBoundaries
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
        val classStats: ClassStats? = null
    )

    private val gradeCutoffs = mapOf(
        "S" to 90.0, "A" to 80.0, "B" to 70.0, "C" to 60.0, "D" to 55.0, "E" to 50.0
    )

    /** Points for a letter, or null when VTOP never published a recognised grade. */
    fun pointsFor(grade: String?): Int? = GradeHistory.gradePoints(grade)

    /** Cutoff used by the web predictor for a target letter, or null for an unknown letter. */
    fun cutoffFor(grade: String?): Double? = cutoffFor(grade, null)

    /**
     * Cutoff for [grade], relative to the real cohort curve when one is available.
     * With no usable class data this is the absolute v5.0 ladder, never a made-up number.
     */
    fun cutoffFor(grade: String?, classStats: ClassStats?): Double? {
        val letter = grade?.trim()?.uppercase() ?: return null
        if (classStats != null) {
            return buildGradeBoundaries(classStats).firstOrNull { it.grade == letter }?.limit
        }
        return gradeCutoffs[letter]
    }

    /**
     * Weighted internal marks actually scored vs the maximum obtainable from the published
     * component weightages. Returns null when no assessment carries usable numbers, so the UI
     * says "not available" instead of showing a confident-looking 0.
     */
    fun internalMarks(assessments: List<M3EAssessmentItem>): Pair<Double, Double>? {
        var scored = 0.0
        var maximum = 0.0
        var usable = false
        for (assessment in assessments) {
            val maxMark = assessment.maxMarks
            // weightageMarks is the component's share of the total (typically 60); fall back to
            // the weightage percentage when the marks-shaped field was not published.
            val weight = assessment.weightageMarks.takeIf { it > 0.0 } ?: assessment.weightagePercent
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
     * honestly rather than as a fabricated deficit.
     */
    fun requiredFatPercentage(
        internalScored: Double,
        internalMax: Double,
        targetGrade: String,
        classStats: ClassStats? = null
    ): Double? {
        val cutoff = cutoffFor(targetGrade, classStats) ?: return null
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
