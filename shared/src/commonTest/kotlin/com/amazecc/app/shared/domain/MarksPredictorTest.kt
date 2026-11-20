package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of `src/__tests__/marksPredictor.test.ts` (9 cases, Node's own fixtures and Node's own
 * arithmetic comments) plus coverage for the pieces Node left in the component and this port moved
 * out: the presets, the FAT-gap repair, and the VTOP row coercion.
 */
class MarksPredictorTest {

    private fun a(
        id: String,
        title: String,
        maxMark: Double,
        weight: Double,
        scored: Double?,
        component: PredictorComponent = PredictorComponent.THEORY,
    ) = PredictorAssessment(
        id = id,
        title = title,
        maxMark = maxMark,
        weightagePercent = weight,
        scoredMark = scored,
        component = component,
    )

    // ── computeComponentStats ────────────────────────────────────────────────

    private val theoryAssessments = listOf(
        a("t1", "CAT - I", 50.0, 15.0, 40.0),
        a("t2", "CAT - II", 50.0, 15.0, 45.0),
        a("t3", "DA - 1", 10.0, 10.0, 10.0),
        a("t4", "DA - 2", 10.0, 10.0, 8.0),
        a("t5", "DA - 3", 10.0, 10.0, null), // pending
        a("t6", "FAT", 100.0, 40.0, null), // pending
    )

    @Test
    fun scoresPendingAndLostWeightSeparatelyForTheory() {
        // CAT 1: 40/50 * 15 = 12.0 scored, 3.0 lost
        // CAT 2: 45/50 * 15 = 13.5 scored, 1.5 lost
        // DA 1: 10/10 * 10 = 10.0 scored, 0 lost
        // DA 2: 8/10 * 10 = 8.0 scored, 2.0 lost
        // DA 3 + FAT: pending (10 + 40)
        val stats = computeComponentStats(theoryAssessments, MarkComponent.THEORY, credits = 3.0)

        assertEquals(43.5, stats.scoredWeight, 1e-9)
        assertEquals(6.5, stats.lostWeight, 1e-9)
        assertEquals(50.0, stats.pendingWeight, 1e-9)
        assertEquals(93.5, stats.maxPossibleWeight, 1e-9)
        assertEquals(4, stats.completedAssessmentsCount)
        assertEquals(2, stats.pendingAssessmentsCount)
    }

    @Test
    fun handlesFourPatsAndLabContinuousEvaluation() {
        val pats = listOf(
            a("p1", "PAT - I", 50.0, 10.0, 45.0, PredictorComponent.LAB),
            a("p2", "PAT - II", 50.0, 15.0, 40.0, PredictorComponent.LAB),
            a("p3", "PAT - III", 50.0, 15.0, 50.0, PredictorComponent.LAB),
            a("p4", "PAT - IV", 50.0, 10.0, null, PredictorComponent.LAB),
            a("p5", "Record", 100.0, 10.0, 95.0, PredictorComponent.LAB),
            a("p6", "Lab FAT", 50.0, 40.0, null, PredictorComponent.LAB),
        )
        val stats = computeComponentStats(pats, MarkComponent.LAB, credits = 1.0)

        // 9 + 12 + 15 + 9.5 scored, 1 + 3 + 0 + 0.5 lost, 10 + 40 pending
        assertEquals(45.5, stats.scoredWeight, 1e-9)
        assertEquals(4.5, stats.lostWeight, 1e-9)
        assertEquals(95.5, stats.maxPossibleWeight, 1e-9)
    }

    @Test
    fun maxPossibleStaysReachableWhileTheFatIsStillPending() {
        // 100 configured, 6.5 already lost, 50 still pending -> 93.5 is reachable.
        val stats = computeComponentStats(theoryAssessments, MarkComponent.THEORY)
        assertEquals(100.0, stats.totalWeightConfigured, 1e-9)
        assertEquals(93.5, stats.maxPossibleWeight, 1e-9)
        assertTrue(stats.maxPossibleWeight > stats.scoredWeight)
    }

    @Test
    fun aScoreAboveTheMaxIsClampedRatherThanCredited() {
        val stats = computeComponentStats(
            listOf(a("x", "Quiz", 10.0, 100.0, 25.0)),
            MarkComponent.THEORY,
        )
        assertEquals(100.0, stats.scoredWeight, 1e-9)
        assertEquals(0.0, stats.lostWeight, 1e-9)
    }

    @Test
    fun aNegativeScoreCountsAsZeroNotAsCredit() {
        val stats = computeComponentStats(
            listOf(a("x", "Quiz", 10.0, 100.0, -5.0)),
            MarkComponent.THEORY,
        )
        assertEquals(0.0, stats.scoredWeight, 1e-9)
        assertEquals(100.0, stats.lostWeight, 1e-9)
    }

    @Test
    fun aZeroMaxMarkIsFlooredSoTheCourseCannotDivideByZero() {
        val stats = computeComponentStats(
            listOf(a("x", "Unpublished", 0.0, 100.0, 0.0)),
            MarkComponent.THEORY,
        )
        assertEquals(0.1, stats.assessments[0].maxMark, 1e-9)
        assertEquals(0.0, stats.scoredWeight, 1e-9)
    }

    @Test
    fun creditsAreFlooredAtAHalf() {
        val stats = computeComponentStats(emptyList(), MarkComponent.THEORY, credits = 0.0)
        assertEquals(0.5, stats.credits, 1e-9)
    }

    // ── computeCoursePrediction ─────────────────────────────────────────────

    @Test
    fun computesCreditWeightedCombinedMarksForAnEmbeddedCourse() {
        val theory = listOf(
            a("t1", "CAT - I", 50.0, 15.0, 40.0),
            a("t2", "CAT - II", 50.0, 15.0, 45.0),
            a("t3", "DA - 1", 10.0, 10.0, 10.0),
            a("t4", "DA - 2", 10.0, 10.0, 10.0),
            a("t5", "DA - 3", 10.0, 10.0, 10.0),
            a("t6", "FAT", 100.0, 40.0, 80.0),
        )
        // Theory: 12 + 13.5 + 10 + 10 + 10 + 32 = 87.5 scored, 3 + 1.5 + 8 = 12.5 lost
        val lab = listOf(
            a("l1", "Continuous", 100.0, 60.0, 95.0, PredictorComponent.LAB), // 57 scored, 3 lost
            a("l2", "Lab FAT", 50.0, 40.0, 45.0, PredictorComponent.LAB), // 36 scored, 4 lost
        )
        // Lab: 57 + 36 = 93.0 scored, 7.0 lost

        val prediction = computeCoursePrediction(
            courseCode = "BCSE202E",
            courseTitle = "Data Structures",
            courseType = "Embedded Theory",
            theoryAssessments = theory,
            labAssessments = lab,
            theoryCredits = 3.0,
            labCredits = 1.0,
        )

        // (3 * 87.5 + 1 * 93.0) / 4 = 88.875 -> 88.88
        // (3 * 12.5 + 1 * 7.0) / 4 = 11.125 -> 11.13
        assertEquals(88.88, prediction.currentScoredScaled, 0.01)
        assertEquals(11.13, prediction.pointsLostScaled, 0.01)
        assertTrue(prediction.isEmbedded)
        assertEquals("A", prediction.estimatedGrade.letter)
    }

    @Test
    fun aNonEmbeddedCourseCountsEachPresentComponentAsOne() {
        val prediction = computeCoursePrediction(
            courseCode = "BCSE101M",
            courseTitle = "Mathematics",
            courseType = "Theory Only",
            theoryAssessments = theoryAssessments,
            theoryCredits = 3.0,
        )
        assertFalse(prediction.isEmbedded)
        assertEquals(1.0, prediction.totalCredits, 1e-9)
        assertEquals(43.5, prediction.currentScoredScaled, 1e-9)
    }

    @Test
    fun embeddedIsDetectedFromTheTypeAloneWhenOnlyOneHalfHasAssessments() {
        val prediction = computeCoursePrediction(
            courseCode = "BCSE202E",
            courseTitle = "Data Structures",
            courseType = "Embedded Theory",
            theoryAssessments = theoryAssessments,
        )
        assertTrue(prediction.isEmbedded)
        assertNull(prediction.labStats)
        // Embedded, so the lone theory half still carries its real 3 credits.
        assertEquals(3.0, prediction.totalCredits, 1e-9)
    }

    @Test
    fun theCeilingGradeIsWhatIsStillReachableNotWhatIsAlreadyThere() {
        val theory = listOf(a("t1", "CAT - I", 50.0, 100.0, 10.0))
        val prediction = computeCoursePrediction(
            courseCode = "X",
            courseTitle = "X",
            courseType = "Theory Only",
            theoryAssessments = theory,
        )
        // Scored 20, lost 80, so nothing is pending and the ceiling is the lost-adjusted max.
        assertEquals(20.0, prediction.currentScoredScaled, 1e-9)
        assertEquals(0.0, prediction.potentialPendingScaled, 1e-9)
        assertEquals(20.0, prediction.maxPossibleScaled, 1e-9)
        assertEquals("F", prediction.ceilingGrade.letter)
    }

    @Test
    fun riskBandsAreStrictlyGreaterThan() {
        // 40 points lost exactly is WARNING, not CRITICAL.
        assertEquals(RiskLevel.WARNING, riskLevelFor(40.0))
        assertEquals(RiskLevel.CRITICAL, riskLevelFor(40.01))
        assertEquals(RiskLevel.WARNING, riskLevelFor(20.01))
        assertEquals(RiskLevel.MODERATE, riskLevelFor(10.01))
        assertEquals(RiskLevel.SAFE, riskLevelFor(10.0))
    }

    // ── solveTargetFAT ──────────────────────────────────────────────────────

    @Test
    fun calculatesTheExactFatScoreNeededForAnS() {
        // Current scored without FAT is 52.0; deficit 38 weight points; (38 / 40) * 100 = 95.0
        val result = solveTargetFat(targetScore = 90.0, currentScoredScaled = 52.0)

        assertEquals(38.0, result.requiredFatWeight, 1e-9)
        assertEquals(95.0, result.requiredFatRawScore, 1e-9)
        assertTrue(result.isAchievable)
        assertEquals(Feasibility.MIRACLE, result.feasibility)
        assertTrue(result.meetsVitMinimumCutoff)
        assertEquals("S", result.targetGrade)
    }

    @Test
    fun detectsAnImpossibleTargetWhenTheDeficitExceedsTheFatWeight() {
        val result = solveTargetFat(targetScore = 90.0, currentScoredScaled = 45.0)
        assertFalse(result.isAchievable)
        assertEquals(Feasibility.IMPOSSIBLE, result.feasibility)
        assertTrue(result.feasibilityMessage.contains("out of reach"))
    }

    @Test
    fun appliesTheVitFortyPercentFatFloorSeparatelyFromArithmetic() {
        // Deficit is only 5 weight points (12.5 raw), which is arithmetically fine but below
        // VIT's 40% FAT floor.
        val result = solveTargetFat(targetScore = 55.0, currentScoredScaled = 50.0)

        assertEquals(12.5, result.requiredFatRawScore, 1e-9)
        assertTrue(result.isAchievable, "arithmetically achievable")
        assertFalse(result.meetsVitMinimumCutoff, "but it would fail the FAT floor")
    }

    @Test
    fun anAlreadySecuredTargetNeedsNothingFromTheFat() {
        val result = solveTargetFat(targetScore = 55.0, currentScoredScaled = 60.0)
        assertTrue(result.isAlreadyAchieved)
        assertEquals(Feasibility.SECURED, result.feasibility)
        assertEquals(0.0, result.requiredFatRawScore, 1e-9)
        assertTrue(result.feasibilityMessage.contains("already secured"))
    }

    @Test
    fun anEmbeddedTargetIsUnpickedAcrossBothHalvesRatherThanAssumed() {
        // Target 90 with 3 theory + 1 lab credits. Lab has already scored 93 weighted, so the
        // theory half alone has to carry (90 * 4 - 1 * 93) / 3 = 89.0 of its own 100.
        val result = solveTargetFat(
            targetScore = 90.0,
            currentScoredScaled = 52.0,
            theoryCredits = 3.0,
            labCredits = 1.0,
            isEmbedded = true,
            component = MarkComponent.THEORY,
            otherComponentWeighted = 93.0,
        )
        val expectedDeficit = ((90.0 * 4.0) - (1.0 * 93.0)) / 3.0 - 52.0
        assertEquals(expectedDeficit, result.requiredFatWeight, 1e-6)
    }

    @Test
    fun theFeasibilityLadderFollowsTheRequiredFractionOfTheFat() {
        // Target 100 against a 40-weight FAT: the deficit is 100 - scored, and the required raw
        // mark is deficit / 40 * 100. Achievable only while that is <= 100, i.e. scored >= 60.
        fun band(current: Double) = solveTargetFat(targetScore = 100.0, currentScoredScaled = current).feasibility
        assertEquals(Feasibility.EASY, band(80.0)) // needs 50 / 100
        assertEquals(Feasibility.MODERATE, band(75.0)) // needs 62.5
        assertEquals(Feasibility.TOUGH, band(65.0)) // needs 87.5
        assertEquals(Feasibility.MIRACLE, band(62.0)) // needs 95
    }

    // ── normalizeWeights ────────────────────────────────────────────────────

    @Test
    fun normalisesArbitraryWeightsToSumToOneHundred() {
        val items = listOf(
            a("a", "CAT 1", 50.0, 20.0, null),
            a("b", "CAT 2", 50.0, 20.0, null),
            a("c", "FAT", 100.0, 60.0, null),
        )
        val sum = normalizeWeights(items).sumOf { it.weightagePercent }
        assertEquals(100.0, sum, 1e-9)
    }

    @Test
    fun scalesUnevenWeightsToOneHundred() {
        // 15 + 15 + 20 = 50 total
        val items = listOf(
            a("a", "Quiz 1", 10.0, 15.0, null),
            a("b", "Quiz 2", 10.0, 15.0, null),
            a("c", "Assignment", 10.0, 20.0, null),
        )
        val normalized = normalizeWeights(items)
        assertEquals(30.0, normalized[0].weightagePercent, 1e-9)
        assertEquals(30.0, normalized[1].weightagePercent, 1e-9)
        assertEquals(40.0, normalized[2].weightagePercent, 1e-9)
    }

    @Test
    fun theLastRowAbsorbsTheRoundingRemainder() {
        // Three thirds do not divide evenly; without the last-row rule the sum would not be 100.
        val items = listOf(
            a("a", "A", 10.0, 33.33, null),
            a("b", "B", 10.0, 33.33, null),
            a("c", "C", 10.0, 33.33, null),
        )
        val normalized = normalizeWeights(items)
        assertEquals(100.0, normalized.sumOf { it.weightagePercent }, 1e-9)
        assertEquals(100.0 - normalized[0].weightagePercent - normalized[1].weightagePercent, normalized[2].weightagePercent, 1e-9)
    }

    @Test
    fun weightsThatAlreadySumToZeroAreLeftAloneRatherThanDividedByZero() {
        val items = listOf(a("a", "A", 10.0, 0.0, null), a("b", "B", 10.0, 0.0, null))
        val normalized = normalizeWeights(items)
        assertEquals(0.0, normalized[0].weightagePercent, 1e-9)
        assertEquals(0.0, normalized[1].weightagePercent, 1e-9)
    }

    // ── estimateGrade ───────────────────────────────────────────────────────

    @Test
    fun returnsTheRightLetterForEveryBand() {
        assertEquals("S", estimateGrade(95.0).letter)
        assertEquals("A", estimateGrade(85.0).letter)
        assertEquals("B", estimateGrade(75.0).letter)
        assertEquals("C", estimateGrade(65.0).letter)
        assertEquals("D", estimateGrade(55.0).letter)
        assertEquals("F", estimateGrade(45.0).letter)
        assertEquals("F", estimateGrade(35.0).letter)
    }

    @Test
    fun everyBandBoundaryIsInclusive() {
        assertEquals("S", estimateGrade(90.0).letter)
        assertEquals("S", estimateGrade(89.99).letter) // web: gradeAt() ceils
        assertEquals("F", estimateGrade(40.0).letter)
        assertEquals("F", estimateGrade(39.99).letter)
    }

    @Test
    fun bandsCarryTheGradePointValueInWords() {
        assertEquals("Outstanding (10 Grade Points)", estimateGrade(95.0).description)
        assertEquals("Fail (0 Grade Points)", estimateGrade(10.0).description)
    }

    // ── VTOP row coercion ───────────────────────────────────────────────────

    @Test
    fun aPendingScoreIsNeverReadAsZero() {
        for (raw in listOf(null, "", "   ", "-")) {
            val row = predictorAssessmentFromVtop("t1", "FAT", "100", "40", raw, PredictorComponent.THEORY)
            assertNull(row.scoredMark, "raw=$raw must read as pending, not as a zero score")
            assertEquals("Pending", row.status)
        }
    }

    @Test
    fun aRecordedScoreIsReadAndMarkedPresent() {
        val row = predictorAssessmentFromVtop("t1", "CAT - I", "50", "15", "40", PredictorComponent.THEORY)
        assertEquals(40.0, row.scoredMark)
        assertEquals("Present", row.status)
    }

    @Test
    fun anAssessmentWithNoTitleGetsAPlaceholderRatherThanABlankRow() {
        val row = predictorAssessmentFromVtop("t1", "  ", "50", "15", "40", PredictorComponent.THEORY)
        assertEquals("Assessment", row.title)
    }

    @Test
    fun anUnpublishedMaxMarkFallsBackToOneHundred() {
        val row = predictorAssessmentFromVtop("t1", "FAT", null, null, null, PredictorComponent.THEORY)
        assertEquals(100.0, row.maxMark, 1e-9)
        assertEquals(10.0, row.weightagePercent, 1e-9)
    }

    // ── FAT-gap repair ──────────────────────────────────────────────────────

    @Test
    fun aMissingFatRowIsAppendedForTheShortfall() {
        // Vit publishes the CATs and nothing else mid-term: 15 + 15 = 30 of 100.
        val partial = listOf(
            a("t1", "CAT - I", 50.0, 15.0, 40.0),
            a("t2", "CAT - II", 50.0, 15.0, 45.0),
        )
        val repaired = withInferredFat(partial, PredictorComponent.THEORY)
        assertEquals(3, repaired.size)
        assertEquals("FAT", repaired.last().title)
        assertEquals(70.0, repaired.last().weightagePercent, 1e-9)
        assertNull(repaired.last().scoredMark)
    }

    @Test
    fun aMissingFatIsFlooredAtTenPercent() {
        // 95 published leaves only 5, which would be an implausible FAT. The title deliberately
        // avoids FAT/Final so the repair fires at all - a row that looks like a FAT suppresses it.
        val nearlyComplete = listOf(a("t1", "Continuous Record", 10.0, 95.0, 9.0))
        val repaired = withInferredFat(nearlyComplete, PredictorComponent.THEORY)
        assertEquals(2, repaired.size)
        assertEquals(10.0, repaired.last().weightagePercent, 1e-9)
    }

    @Test
    fun aLabGetsAFiftyMarkFatAndItsOwnTitle() {
        val repaired = withInferredFat(listOf(a("l1", "Experiment 1", 100.0, 60.0, 90.0, PredictorComponent.LAB)), PredictorComponent.LAB)
        assertEquals("Lab FAT", repaired.last().title)
        assertEquals(50.0, repaired.last().maxMark, 1e-9)
    }

    @Test
    fun anExistingFatRowIsNeverDuplicated() {
        val withFat = listOf(
            a("t1", "CAT - I", 50.0, 15.0, 40.0),
            a("t2", "FAT", 100.0, 40.0, null),
        )
        assertEquals(2, withInferredFat(withFat, PredictorComponent.THEORY).size)
    }

    @Test
    fun weightsThatAlreadyReachOneHundredAreLeftAlone() {
        val complete = listOf(
            a("t1", "CAT - I", 50.0, 60.0, 40.0),
            a("t2", "CAT - II", 50.0, 40.0, null),
        )
        assertEquals(2, withInferredFat(complete, PredictorComponent.THEORY).size)
    }

    // ── presets ─────────────────────────────────────────────────────────────

    @Test
    fun theDefaultRegimensAreTheTwoVitPublishesMostOften() {
        assertEquals("theory-standard", defaultRegimen(PredictorComponent.THEORY)?.id)
        assertEquals("lab-10-continuous", defaultRegimen(PredictorComponent.LAB)?.id)
        // A project or soft-skill course falls back to the theory regimen, as Node does.
        assertEquals("theory-standard", defaultRegimen(PredictorComponent.PROJECT)?.id)
    }

    @Test
    fun aPresetBecomesPendingRowsThatStillAddUpToOneHundred() {
        val rows = defaultRegimen(PredictorComponent.THEORY)!!.toPendingAssessments(PredictorComponent.THEORY)
        assertEquals(6, rows.size)
        assertEquals(100.0, rows.sumOf { it.weightagePercent }, 1e-9)
        assertTrue(rows.all { it.scoredMark == null && it.status == "Pending" })
    }

    @Test
    fun softSkillsAreFiledUnderTheoryBecauseTheyShareTheCourseTotal() {
        val sts = PRESET_REGIMENS.first { it.id == "softskills-sts" }
        assertEquals(PredictorComponent.SOFTSKILL, sts.category)
        assertEquals(MarkComponent.THEORY, sts.component)
    }

    @Test
    fun everyPresetHasAUniqueIdAndAtLeastOneItem() {
        assertEquals(PRESET_REGIMENS.size, PRESET_REGIMENS.map { it.id }.toSet().size)
        assertTrue(PRESET_REGIMENS.all { it.items.isNotEmpty() })
    }

    // ── safeNumber ──────────────────────────────────────────────────────────

    @Test
    fun safeNumberTreatsBlankAsZeroTheWayJavaScriptDoes() {
        // `Number("")` is 0 in JavaScript, not NaN - carried over deliberately.
        assertEquals(0.0, safeNumber(""), 1e-9)
        assertEquals(0.0, safeNumber("   "), 1e-9)
        assertEquals(7.0, safeNumber("abc", 7.0), 1e-9)
        assertEquals(18.5, safeNumber("18.5"), 1e-9)
        assertEquals(4.0, safeNumber(null, 4.0), 1e-9)
    }
}