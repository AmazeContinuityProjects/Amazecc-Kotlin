package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.buildGradeBoundaries
import com.amazecc.app.shared.domain.isRelativeGrading
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3EComponentBreakdown
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Pure, headless-testable arithmetic for the Marks & GPA Predictor.
 *
 * Nothing here reads AppState, Compose or a clock. Every value comes from the real VTOP marks
 * payload copied by the host, so the predictor can never print a made-up GPA or FAT requirement.
 * The absolute grade ladder mirrors the web client (S>=90, A>=80, B>=70, C>=60, D>=55, E>=50).
 */
internal object M3EPredictorDerivation {

    data class CourseInternalMarksResult(
        val weightedScored: Double,
        val weightedMax: Double,
        val components: List<M3EComponentBreakdown>
    )

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
        val courseType: String? = null,
        val theoryCredits: Double? = null,
        val labCredits: Double? = null
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
            it != null && isRelativeGrading(courseSystem ?: "ACE", courseType)
        }
        if (curve != null) {
            return buildGradeBoundaries(curve).firstOrNull { it.grade == letter }?.limit
        }
        return gradeCutoffs[letter]
    }

    /**
     * Weighted internal marks actually scored vs the maximum obtainable from the published
     * component weightages. For embedded courses (Theory + Lab), calculates a credit-weighted
     * average across components:
     * [theory component credits * theory marks + lab component * lab marks + ...] / total credits.
     * Invariant: the returned maximum and scored marks can never exceed 100.0.
     */
    fun internalMarks(
        assessments: List<M3EAssessmentItem>,
        theoryCredits: Double? = null,
        labCredits: Double? = null
    ): Pair<Double, Double>? =
        courseInternalMarks(assessments, theoryCredits, labCredits)?.let {
            it.weightedScored to it.weightedMax
        }

    /**
     * Detailed internal marks computation with component-level breakdown and credit-weighted total.
     */
    fun courseInternalMarks(
        assessments: List<M3EAssessmentItem>,
        theoryCredits: Double? = null,
        labCredits: Double? = null
    ): CourseInternalMarksResult? {
        if (assessments.isEmpty()) return null

        val hasLabSignal = assessments.any { isLabAssessment(it) }
        val hasTheorySignal = assessments.any { !isLabAssessment(it) }
        val isMultiComponent = hasLabSignal && hasTheorySignal

        if (!isMultiComponent) {
            val singleTotal = calculateRawComponentTotal(assessments) ?: return null
            val isLab = hasLabSignal
            val compCode = if (isLab) "ELA" else "ETH"
            val compTitle = if (isLab) "Lab Assessments (ELA)" else "Theory Assessments (ETH)"
            val credits = if (isLab) labCredits else theoryCredits
            val singleBreakdown = M3EComponentBreakdown(
                componentTitle = compTitle,
                componentCode = compCode,
                credits = credits,
                scoredMarks = round2(singleTotal.first),
                maxMarks = round2(singleTotal.second),
                assessments = assessments
            )
            val finalMax = min(100.0, round2(singleTotal.second))
            val finalScored = min(finalMax, round2(singleTotal.first))
            return CourseInternalMarksResult(
                weightedScored = finalScored,
                weightedMax = finalMax,
                components = listOf(singleBreakdown)
            )
        }

        // Multi-component (Embedded Course: Theory + Lab)
        val theoryItems = assessments.filter { !isLabAssessment(it) }
        val labItems = assessments.filter { isLabAssessment(it) }

        val theoryTotal = calculateRawComponentTotal(theoryItems)
        val labTotal = calculateRawComponentTotal(labItems)

        if (theoryTotal == null && labTotal == null) return null

        val tCr = theoryCredits?.takeIf { it > 0.0 } ?: 3.0
        val lCr = labCredits?.takeIf { it > 0.0 } ?: 1.0

        val components = mutableListOf<M3EComponentBreakdown>()
        var sumWeightedScored = 0.0
        var sumWeightedMax = 0.0
        var sumCredits = 0.0

        if (theoryTotal != null) {
            val tScored = round2(theoryTotal.first)
            val tMax = round2(theoryTotal.second)
            components.add(
                M3EComponentBreakdown(
                    componentTitle = "Theory Assessments (ETH)",
                    componentCode = "ETH",
                    credits = tCr,
                    scoredMarks = tScored,
                    maxMarks = tMax,
                    assessments = theoryItems
                )
            )
            sumWeightedScored += tCr * tScored
            sumWeightedMax += tCr * tMax
            sumCredits += tCr
        }

        if (labTotal != null) {
            val lScored = round2(labTotal.first)
            val lMax = round2(labTotal.second)
            components.add(
                M3EComponentBreakdown(
                    componentTitle = "Lab Assessments (ELA)",
                    componentCode = "ELA",
                    credits = lCr,
                    scoredMarks = lScored,
                    maxMarks = lMax,
                    assessments = labItems
                )
            )
            sumWeightedScored += lCr * lScored
            sumWeightedMax += lCr * lMax
            sumCredits += lCr
        }

        val weightedScored = if (sumCredits > 0.0) sumWeightedScored / sumCredits else 0.0
        val weightedMax = if (sumCredits > 0.0) sumWeightedMax / sumCredits else 0.0

        // Invariant: weighted total cannot be above 100
        val finalMax = min(100.0, round2(weightedMax))
        val finalScored = min(finalMax, round2(weightedScored))

        return CourseInternalMarksResult(
            weightedScored = finalScored,
            weightedMax = finalMax,
            components = components
        )
    }

    fun isLabAssessment(item: M3EAssessmentItem): Boolean {
        val comp = item.component.trim().uppercase()
        if (comp == "ELA" || comp == "LAB" || comp.contains("LAB") || comp.contains("PRACTICAL")) return true
        if (comp == "ETH" || comp == "THEORY" || comp.contains("THEORY")) return false
        val title = item.title.trim().uppercase()
        return title.contains("LAB") || title.contains("ELA") || title.contains("PRACTICAL")
    }

    fun isFinalExamTitle(title: String): Boolean =
        title.contains("FAT", ignoreCase = true) ||
            title.contains("Final Assessment", ignoreCase = true) ||
            title.contains("Final Test", ignoreCase = true)

    /**
     * Distinguishes unposted/pending assessments from legitimate 0.0 scores.
     * - "Pending" or "-" status is always pending.
     * - "Simulated" is never pending (even at 0.0).
     * - Blank status with 0.0 score is treated as pending; "Present"/"Absent" with 0.0 is scored.
     */
    fun isPendingAssessment(asm: M3EAssessmentItem): Boolean {
        val status = asm.status.trim()
        if (status.equals("Pending", ignoreCase = true)) return true
        if (status == "-") return true
        if (status.equals("Simulated", ignoreCase = true)) return false
        if (status.isBlank() && asm.scoredMarks <= 0.0) return true
        return false
    }

    /**
     * Gate for the Target Grade Solver: returns true only when every non-FAT assessment
     * (and for embedded courses, the Lab FAT) has a recorded or simulated mark.
     */
    fun onlyFatRemains(
        assessments: List<M3EAssessmentItem>,
        labFatScored: Double? = null
    ): Boolean {
        if (assessments.isEmpty()) return false
        val hasTheory = assessments.any { !isLabAssessment(it) }
        val nonFat = assessments.filter { !isFinalExamTitle(it.title) }
        if (nonFat.any { isPendingAssessment(it) }) return false
        if (hasTheory) {
            val labFat = assessments.firstOrNull { isLabAssessment(it) && isFinalExamTitle(it.title) }
            if (labFat != null && labFatScored == null && isPendingAssessment(labFat)) {
                return false
            }
        }
        return true
    }

    private fun calculateRawComponentTotal(items: List<M3EAssessmentItem>): Pair<Double, Double>? {
        var scored = 0.0
        var maximum = 0.0
        var usable = false
        for (assessment in items) {
            val maxMark = assessment.maxMarks
            val weight = assessment.weightagePercent
            if (maxMark <= 0.0 || weight <= 0.0) continue
            if (!isPendingAssessment(assessment)) {
                val ratio = min(max(assessment.scoredMarks, 0.0), maxMark) / maxMark
                scored += ratio * weight
            }
            maximum += weight
            usable = true
        }
        return if (usable && maximum > 0.0) scored to maximum else null
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0

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

    data class GpaCourseItem(
        val credits: Int,
        val optimalGrade: String?,
        val maximisableGrade: String?
    )

    data class GpaPredictionResult(
        val optimalSgpa: Double,
        val maximisableSgpa: Double,
        val optimalCredits: Int,
        val maximisableCredits: Int,
        val semesterCredits: Int,
        val optimalCgpa: Double?,
        val desirableCgpa: Double?,
        val optimalCgpaIsAnchor: Boolean,
        val cumulativeCredits: Double
    )

    fun predictGpaBreakdown(
        courses: List<GpaCourseItem>,
        cumulativeCredits: Double = 0.0,
        publishedCgpa: Double = 0.0
    ): GpaPredictionResult {
        var optimalCredits = 0
        var optimalWeighted = 0.0
        var maximisableCredits = 0
        var maximisableWeighted = 0.0
        var semesterCredits = 0

        for (c in courses) {
            val cr = if (c.credits > 0) c.credits else 0
            if (cr == 0) continue
            semesterCredits += cr

            val optPoints = pointsFor(c.optimalGrade)
            if (optPoints != null) {
                optimalCredits += cr
                optimalWeighted += optPoints * cr
            }

            val maxPoints = pointsFor(c.maximisableGrade)
            if (maxPoints != null) {
                maximisableCredits += cr
                maximisableWeighted += maxPoints * cr
            }
        }

        val optimalSgpa = if (optimalCredits > 0) round2(optimalWeighted / optimalCredits) else 0.0
        val maximisableSgpa = if (maximisableCredits > 0) round2(maximisableWeighted / maximisableCredits) else 0.0

        fun projectCumulative(sgpa: Double, gradedCr: Int): Pair<Double?, Boolean> {
            if (publishedCgpa <= 0.0 || cumulativeCredits <= 0.0) {
                return null to false
            }
            if (gradedCr <= 0) {
                return round2(publishedCgpa) to true
            }
            val blended = (cumulativeCredits * publishedCgpa + gradedCr * sgpa) / (cumulativeCredits + gradedCr)
            return round2(blended) to false
        }

        val (optCgpa, optIsAnchor) = projectCumulative(optimalSgpa, optimalCredits)
        val (desCgpa, _) = projectCumulative(maximisableSgpa, maximisableCredits)

        return GpaPredictionResult(
            optimalSgpa = optimalSgpa,
            maximisableSgpa = maximisableSgpa,
            optimalCredits = optimalCredits,
            maximisableCredits = maximisableCredits,
            semesterCredits = semesterCredits,
            optimalCgpa = optCgpa,
            desirableCgpa = desCgpa,
            optimalCgpaIsAnchor = optIsAnchor,
            cumulativeCredits = cumulativeCredits
        )
    }

    data class CoursePredictionMetrics(
        val currentScoredScaled: Double,
        val pointsLostScaled: Double,
        val maxPossibleScaled: Double,
        val pacePercentage: Double?,
        val optimalGrade: String?,
        val maximisableGrade: String,
        val passIssues: List<String> = emptyList()
    )

    fun calculateCoursePredictionMetrics(
        assessments: List<M3EAssessmentItem>,
        theoryCredits: Double? = 3.0,
        labCredits: Double? = 1.0,
        classStats: ClassStats? = null,
        courseSystem: String? = null,
        courseType: String? = null
    ): CoursePredictionMetrics {
        val tCr = theoryCredits ?: 3.0
        val lCr = labCredits ?: 1.0
        val hasLab = assessments.any { isLabAssessment(it) }
        val hasTheory = assessments.any { !isLabAssessment(it) }
        val isEmbedded = hasLab && hasTheory && tCr > 0.0 && lCr > 0.0

        val theoryItems = assessments.filter { !isLabAssessment(it) }
        val labItems = assessments.filter { isLabAssessment(it) }

        fun componentMetrics(items: List<M3EAssessmentItem>): Triple<Double, Double, Double> {
            var scored = 0.0
            var lost = 0.0
            var pending = 0.0
            for (asm in items) {
                val weight = asm.weightagePercent
                val maxMark = asm.maxMarks
                if (weight <= 0.0 || maxMark <= 0.0) continue
                val isRecorded = !isPendingAssessment(asm)
                if (isRecorded) {
                    val ratio = min(max(asm.scoredMarks, 0.0), maxMark) / maxMark
                    val scWeight = ratio * weight
                    scored += scWeight
                    lost += max(0.0, weight - scWeight)
                } else {
                    pending += weight
                }
            }
            return Triple(scored, lost, pending)
        }

        val (tScored, tLost, _) = if (theoryItems.isNotEmpty()) componentMetrics(theoryItems) else Triple(0.0, 0.0, 0.0)
        val (lScored, lLost, lPending) = if (labItems.isNotEmpty()) componentMetrics(labItems) else Triple(0.0, 0.0, 0.0)

        val totalCr = if (isEmbedded) max(0.1, tCr + lCr) else 1.0
        val currentScoredScaled = if (isEmbedded) (tScored * tCr + lScored * lCr) / totalCr else (tScored + lScored)
        val pointsLostScaled = if (isEmbedded) (tLost * tCr + lLost * lCr) / totalCr else (tLost + lLost)
        val maxPossibleScaled = max(0.0, 100.0 - pointsLostScaled)

        val releasedWeight = currentScoredScaled + pointsLostScaled
        val pacePct = if (releasedWeight > 0.0) round2((currentScoredScaled / releasedWeight) * 100.0) else null

        fun estimateGrade(score: Double): String = when {
            score >= (cutoffFor("S", classStats, courseSystem, courseType) ?: 90.0) -> "S"
            score >= (cutoffFor("A", classStats, courseSystem, courseType) ?: 80.0) -> "A"
            score >= (cutoffFor("B", classStats, courseSystem, courseType) ?: 70.0) -> "B"
            score >= (cutoffFor("C", classStats, courseSystem, courseType) ?: 60.0) -> "C"
            score >= (cutoffFor("D", classStats, courseSystem, courseType) ?: 55.0) -> "D"
            score >= (cutoffFor("E", classStats, courseSystem, courseType) ?: 50.0) -> "E"
            else -> "F"
        }

        // VIT FFCS Statutory Pass Floors (§9.4 Theory FAT < 40% -> F; §9.6/§9.7 Lab < 50% -> N; N wins over F)
        var paceFloor: String? = null
        var ceilingFloor: String? = null
        val passIssues = mutableListOf<String>()
        fun worse(cur: String?, next: String): String = if (cur == "N") "N" else next

        val theoryFat = theoryItems.firstOrNull { isFinalExamTitle(it.title) }
        if (theoryItems.isNotEmpty() && theoryFat != null) {
            if (!isPendingAssessment(theoryFat) && theoryFat.maxMarks > 0.0) {
                val fatPct = (max(0.0, theoryFat.scoredMarks) / theoryFat.maxMarks) * 100.0
                if (fatPct < 40.0) {
                    paceFloor = worse(paceFloor, "F")
                    ceilingFloor = worse(ceilingFloor, "F")
                    passIssues.add("${theoryFat.title} is ${round2(fatPct)}% — theory FAT needs 40% on its own to pass (§9.4).")
                }
            } else {
                val tReleased = tScored + tLost
                val thPace = if (tReleased > 0.0) (tScored / tReleased) * 100.0 else null
                if (thPace != null && thPace < 40.0) {
                    paceFloor = worse(paceFloor, "F")
                    passIssues.add("At your current ${round2(thPace)}% pace the FAT would land below the 40% pass floor (§9.4).")
                }
            }
        }

        if (labItems.isNotEmpty()) {
            val labTotalWeight = lScored + lLost + lPending
            if (labTotalWeight > 0.0) {
                val labCeilingPct = ((labTotalWeight - lLost) / labTotalWeight) * 100.0
                val labReleased = lScored + lLost
                val labPacePct = if (labReleased > 0.0) (lScored / labReleased) * 100.0 else null
                if (labCeilingPct < 50.0) {
                    paceFloor = "N"
                    ceilingFloor = "N"
                    passIssues.add("Lab can reach at most ${round2(labCeilingPct)}% — it needs 50% of its own marks, or the course is N (§9.6/§9.7).")
                } else if (labPacePct != null && labPacePct < 50.0) {
                    paceFloor = "N"
                    passIssues.add("Lab is at ${round2(labPacePct)}% — below the 50% required to complete the course (§9.6/§9.7).")
                }
            }
        }

        val rawOptimalGrade = pacePct?.let { estimateGrade(it) }
        val rawMaximisableGrade = estimateGrade(maxPossibleScaled)
        val optimalGrade = when {
            rawOptimalGrade == null -> null
            paceFloor != null -> paceFloor
            else -> rawOptimalGrade
        }
        val maximisableGrade = when {
            ceilingFloor != null -> ceilingFloor
            else -> rawMaximisableGrade
        }

        return CoursePredictionMetrics(
            currentScoredScaled = round2(currentScoredScaled),
            pointsLostScaled = round2(pointsLostScaled),
            maxPossibleScaled = round2(maxPossibleScaled),
            pacePercentage = pacePct,
            optimalGrade = optimalGrade,
            maximisableGrade = maximisableGrade,
            passIssues = passIssues
        )
    }

    fun typeLabel(courseType: String, isEmbedded: Boolean): String {
        val lower = courseType.lowercase()
        return when {
            isEmbedded || lower.contains("embedded") -> "ETL"
            lower.contains("lab") -> "LO"
            lower.contains("theory") -> "TO"
            else -> if (courseType.length <= 4) courseType.uppercase() else "TO"
        }
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

    /** Presentational tone for a predicted or target grade letter. */
    fun toneForGrade(grade: String?): M3ECardTone = when (grade?.trim()?.uppercase()) {
        "S", "A" -> M3ECardTone.GOOD
        "B", "C" -> M3ECardTone.NEUTRAL
        "D", "E" -> M3ECardTone.WARNING
        "F", "N" -> M3ECardTone.CRITICAL
        else -> M3ECardTone.NEUTRAL
    }

    /** Presentational tone only; it never feeds back into any number. */
    fun toneFor(requiredFat: Double?): M3ECardTone = when {
        requiredFat == null -> M3ECardTone.NEUTRAL
        requiredFat <= 20.0 -> M3ECardTone.GOOD
        requiredFat <= 50.0 -> M3ECardTone.WARNING
        else -> M3ECardTone.CRITICAL
    }

    data class FatRequirement(
        val targetGrade: String,
        val requiredFatRaw: Double,
        val requiredFatPercent: Double,
        val feasibility: String,
        val feasibilityLabel: String,
        val feasibilityMessage: String,
        val targetCutoff: Double,
        val meetsVitPassFloor: Boolean,
        val onlyFatRemains: Boolean = true
    )

    /**
     * Solves required final exam (FAT) score taking into account internal assessments,
     * embedded course theory/lab credit weighting, simulated Lab FAT, and cohort curves (§9.4, §9.6, §9.7).
     */
    fun solveCourseFat(
        targetGrade: String,
        assessments: List<M3EAssessmentItem>,
        theoryCredits: Double? = 3.0,
        labCredits: Double? = 1.0,
        labFatScored: Double? = null,
        labFatMax: Double = 50.0,
        labFatWeight: Double = 40.0,
        theoryFatWeight: Double = 40.0,
        theoryFatMax: Double = 100.0,
        classStats: ClassStats? = null,
        courseSystem: String? = null,
        courseType: String? = null
    ): FatRequirement {
        val targetCutoff = cutoffFor(targetGrade, classStats, courseSystem, courseType) ?: 80.0
        val gateOpen = onlyFatRemains(assessments, labFatScored)
        if (!gateOpen) {
            return FatRequirement(
                targetGrade = targetGrade,
                requiredFatRaw = 0.0,
                requiredFatPercent = 0.0,
                feasibility = "pending",
                feasibilityLabel = "Pending internals",
                feasibilityMessage = "Assign a mark to every other assessment first. Once only FAT remains, this card answers what you need in it.",
                targetCutoff = targetCutoff,
                meetsVitPassFloor = false,
                onlyFatRemains = false
            )
        }

        val tCredits = theoryCredits ?: 3.0
        val lCredits = labCredits ?: 1.0
        val hasLab = assessments.any { isLabAssessment(it) }
        val hasTheory = assessments.any { !isLabAssessment(it) }
        val isEmbedded = hasLab && hasTheory && tCredits > 0.0 && lCredits > 0.0

        val theoryAssessments = assessments.filter { !isLabAssessment(it) && !isFinalExamTitle(it.title) }
        val labAssessments = assessments.filter { isLabAssessment(it) && !isFinalExamTitle(it.title) }

        val theoryInternalWeight = theoryAssessments.sumOf { if (isPendingAssessment(it)) 0.0 else it.weightageMarks }
        var labInternalWeight = labAssessments.sumOf { if (isPendingAssessment(it)) 0.0 else it.weightageMarks }

        // If Lab FAT is simulated or scored (either via explicit labFatScored or via Lab FAT assessment row)
        val labFatAsm = assessments.firstOrNull {
            isLabAssessment(it) && isFinalExamTitle(it.title)
        }
        val effectiveLabFat = labFatScored ?: labFatAsm?.takeIf {
            !isPendingAssessment(it)
        }?.scoredMarks
        val effectiveLabFatMax = if (labFatScored != null) labFatMax else (labFatAsm?.maxMarks?.takeIf { it > 0.0 } ?: labFatMax)
        val effectiveLabFatWeight = if (labFatScored != null) labFatWeight else (labFatAsm?.weightagePercent?.takeIf { it > 0.0 } ?: labFatWeight)

        if (effectiveLabFat != null && effectiveLabFatMax > 0.0) {
            val labFatWeightScored = (max(0.0, effectiveLabFat) / effectiveLabFatMax) * effectiveLabFatWeight
            labInternalWeight += labFatWeightScored
        }

        val solvesLab = !hasTheory

        // §9.6 / §9.7: In an embedded course, if Lab component total < 50%, course is N regardless of Theory FAT.
        if (isEmbedded && effectiveLabFat != null) {
            val labTotalWeight = labAssessments.sumOf { it.weightagePercent } + effectiveLabFatWeight
            if (labTotalWeight > 0.0) {
                val labPct = (labInternalWeight / labTotalWeight) * 100.0
                if (labPct < 50.0) {
                    return FatRequirement(
                        targetGrade = targetGrade,
                        requiredFatRaw = 0.0,
                        requiredFatPercent = 0.0,
                        feasibility = "impossible",
                        feasibilityLabel = "Lab below 50% (N)",
                        feasibilityMessage = "Lab component is ${round2(labPct)}% (below 50% required by §9.6/§9.7), resulting in N regardless of Theory FAT.",
                        targetCutoff = targetCutoff,
                        meetsVitPassFloor = false,
                        onlyFatRemains = true
                    )
                }
            }
        }

        val (rawScore, activeFatMax, activeFatWeight) = if (solvesLab) {
            val deficit = targetCutoff - labInternalWeight
            val raw = (deficit / max(0.1, labFatWeight)) * labFatMax
            Triple(raw, labFatMax, labFatWeight)
        } else {
            val requiredTheoryWeight = if (isEmbedded) {
                val totalCredits = tCredits + lCredits
                val requiredTotalPoints = targetCutoff * totalCredits
                val labPoints = lCredits * labInternalWeight
                (requiredTotalPoints - labPoints) / tCredits
            } else {
                targetCutoff
            }
            val deficit = requiredTheoryWeight - theoryInternalWeight
            val raw = (deficit / max(0.1, theoryFatWeight)) * theoryFatMax
            Triple(raw, theoryFatMax, theoryFatWeight)
        }

        val requiredPercent = (rawScore / activeFatMax) * 100.0
        val isAlreadyAchieved = rawScore <= 0.0
        val isImpossible = rawScore > activeFatMax

        val feasibility: String
        val feasibilityLabel: String
        val feasibilityMessage: String

        when {
            isAlreadyAchieved -> {
                feasibility = "secured"
                feasibilityLabel = "Already secured"
                feasibilityMessage = if (solvesLab) {
                    "Target already secured! Even with 0 in Lab FAT, you have achieved the target."
                } else {
                    "Target already secured on internal marks! 40% in Theory FAT is required to satisfy the VIT pass floor (§9.4)."
                }
            }
            isImpossible -> {
                feasibility = "impossible"
                feasibilityLabel = "Out of reach"
                feasibilityMessage = "Mathematically out of reach with remaining marks."
            }
            rawScore > 0.9 * activeFatMax -> {
                feasibility = "needs_finish"
                feasibilityLabel = "Needs strong finish"
                feasibilityMessage = "Requires an exceptional final exam performance."
            }
            rawScore > 0.75 * activeFatMax -> {
                feasibility = "tough"
                feasibilityLabel = "Tough"
                feasibilityMessage = "Achievable with dedicated preparation."
            }
            rawScore > 0.5 * activeFatMax -> {
                feasibility = "achievable"
                feasibilityLabel = "Achievable"
                feasibilityMessage = "Comfortable target in the final exam."
            }
            else -> {
                feasibility = "comfortable"
                feasibilityLabel = "Comfortable"
                feasibilityMessage = "Comfortable margin to secure target grade."
            }
        }

        val effectiveRaw = if (isAlreadyAchieved) 0.0 else max(0.0, rawScore)
        val meetsFloor = if (solvesLab) {
            (labInternalWeight + (effectiveRaw / max(0.1, activeFatMax)) * activeFatWeight) >= 50.0
        } else {
            effectiveRaw >= 0.4 * activeFatMax
        }

        return FatRequirement(
            targetGrade = targetGrade,
            requiredFatRaw = effectiveRaw,
            requiredFatPercent = max(0.0, requiredPercent),
            feasibility = feasibility,
            feasibilityLabel = feasibilityLabel,
            feasibilityMessage = feasibilityMessage,
            targetCutoff = targetCutoff,
            meetsVitPassFloor = meetsFloor,
            onlyFatRemains = true
        )
    }
}
