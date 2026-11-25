package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless checks for D-033: the predictor must derive every number from real marks and grades,
 * and must stay silent (null) when the snapshot has nothing to say.
 */
class M3EPredictorDerivationTest {

    /**
     * Realistic VTOP row: [weight] is the component's share of the 100-point total, while the
     * obtained weighted score is `(scored/max) * weight`. Keeping the two fields deliberately
     * different is what catches a double-application of the ratio.
     */
    private fun assessment(scored: Double, max: Double, weight: Double) = M3EAssessmentItem(
        title = "CAT",
        maxMarks = max,
        scoredMarks = scored,
        weightagePercent = weight,
        weightageMarks = (scored / max) * weight
    )

    @Test
    fun noAssessmentsMeansNoInternalMarks() {
        assertNull(M3EPredictorDerivation.internalMarks(emptyList()))
    }

    @Test
    fun internalMarksUsePublishedWeightages() {
        val (scored, max) = M3EPredictorDerivation.internalMarks(
            listOf(assessment(40.0, 50.0, 10.0), assessment(45.0, 50.0, 15.0))
        )!!
        // contribution = (scored/maxMarks) * weight: 0.8*10 + 0.9*15 = 8 + 13.5 = 21.5 of 25.
        assertEquals(8.0 + 13.5, scored, 0.001)
        assertEquals(25.0, max, 0.001)
    }

    @Test
    fun obtainedWeightedMarksAreNotReusedAsComponentWeights() {
        // A real VTOP row: 25/50 (ratio 0.5) with a 20% share carries weightageMarks = 10.
        // Mistaking that 10 for the weight would yield 0.5*10 = 5 of 10 instead of 0.5*20 = 10
        // of 20. This is the divergence case the old equal-valued fixture could never catch.
        val (scored, max) = M3EPredictorDerivation.internalMarks(
            listOf(
                M3EAssessmentItem(
                    title = "CAT-1",
                    maxMarks = 50.0,
                    scoredMarks = 25.0,
                    weightagePercent = 20.0,
                    weightageMarks = 10.0
                )
            )
        )!!
        assertEquals(10.0, scored, 0.001)
        assertEquals(20.0, max, 0.001)
    }

    @Test
    fun cohortCurveIsUsedOnlyForRelativeGradedCourses() {
        // mean 70 + 0.5*sd 10 => curved A cutoff 75; absolute A cutoff is 80.
        // manual=true keeps sd exact (a sampled curve pools σ with a prior via effectiveSd).
        val curve = ClassStats(count = 20, mean = 70.0, sd = 10.0, manual = true)
        assertEquals(80.0, M3EPredictorDerivation.cutoffFor("A", curve)!!, 0.001)
        assertEquals(
            75.0,
            M3EPredictorDerivation.cutoffFor("A", curve, "ACE", "Theory Only")!!,
            0.001
        )
        assertEquals(
            80.0,
            M3EPredictorDerivation.cutoffFor("A", curve, "ACE", "Lab Only")!!,
            0.001
        )
    }

    @Test
    fun requiredFatIsDerivedFromCutoffAndAlreadySecuredMarks() {
        // Internal 45 of 60, target A (80): FAT weight 40, need 35 raw => 87.5% of FAT.
        val required = M3EPredictorDerivation.requiredFatPercentage(45.0, 60.0, "A")
        assertEquals(88.0, required!!, 0.001)
        // Already above the cutoff: no fabricated deficit.
        assertEquals(0.0, M3EPredictorDerivation.requiredFatPercentage(85.0, 60.0, "A")!!)
        assertNull(M3EPredictorDerivation.requiredFatPercentage(45.0, 60.0, "UNKNOWN"))
    }

    @Test
    fun gpaIsCreditWeightedAndNullWithoutGrades() {
        val inputs = listOf(
            M3EPredictorDerivation.CourseInput("CSE1", "One", 4, "A", emptyList()),
            M3EPredictorDerivation.CourseInput("MAT1", "Two", 2, "S", emptyList())
        )
        assertNull(M3EPredictorDerivation.predictGpa(inputs, emptyMap()))
        val gpa = M3EPredictorDerivation.predictGpa(
            inputs, mapOf("CSE1" to "A", "MAT1" to "S")
        )!!
        // A=9 (4 credits), S=10 (2 credits) => (36+20)/6 = 9.33
        assertEquals(9.33, gpa, 0.001)
    }

    @Test
    fun toneIsNeutralWhenThereIsNoRealRequirement() {
        assertEquals(M3ECardTone.NEUTRAL, M3EPredictorDerivation.toneFor(null))
        assertTrue(M3EPredictorDerivation.cutoffFor("S")!! > M3EPredictorDerivation.cutoffFor("A")!!)
    }

    @Test
    fun embeddedCourseInternalMarksAreCreditWeightedAndNeverExceed100() {
        val theoryItems = listOf(
            M3EAssessmentItem(
                title = "Continuous Assessment Test - I",
                maxMarks = 50.0,
                scoredMarks = 50.0,
                weightagePercent = 30.0,
                weightageMarks = 30.0,
                component = "ETH"
            ),
            M3EAssessmentItem(
                title = "Continuous Assessment Test - II",
                maxMarks = 50.0,
                scoredMarks = 50.0,
                weightagePercent = 30.0,
                weightageMarks = 30.0,
                component = "ETH"
            )
        )
        val labItems = listOf(
            M3EAssessmentItem(
                title = "Lab Assessment",
                maxMarks = 50.0,
                scoredMarks = 25.0, // 50%
                weightagePercent = 60.0,
                weightageMarks = 30.0,
                component = "ELA"
            )
        )
        val allAssessments = theoryItems + labItems

        // Theory: 60/60 (3 credits). Lab: 30/60 (1 credit). Total: 4 credits.
        // Formula: (3 * 60 + 1 * 30) / 4 = (180 + 30) / 4 = 210 / 4 = 52.5
        // Max: (3 * 60 + 1 * 60) / 4 = 60.0
        val result = M3EPredictorDerivation.courseInternalMarks(
            assessments = allAssessments,
            theoryCredits = 3.0,
            labCredits = 1.0
        )
        org.junit.Assert.assertNotNull(result)
        assertEquals(52.5, result!!.weightedScored, 0.001)
        assertEquals(60.0, result.weightedMax, 0.001)
        assertTrue(result.weightedMax <= 100.0)
        assertTrue(result.weightedScored <= 100.0)

        // Verify individual component subtotals
        assertEquals(2, result.components.size)
        val theoryComp = result.components.first { it.componentCode == "ETH" }
        val labComp = result.components.first { it.componentCode == "ELA" }
        assertEquals(60.0, theoryComp.scoredMarks, 0.001)
        assertEquals(60.0, theoryComp.maxMarks, 0.001)
        assertEquals(3.0, theoryComp.credits)
        assertEquals(30.0, labComp.scoredMarks, 0.001)
        assertEquals(60.0, labComp.maxMarks, 0.001)
        assertEquals(1.0, labComp.credits)

        // Also test convenience wrapper internalMarks
        val (scored, max) = M3EPredictorDerivation.internalMarks(
            allAssessments,
            theoryCredits = 3.0,
            labCredits = 1.0
        )!!
        assertEquals(52.5, scored, 0.001)
        assertEquals(60.0, max, 0.001)
    }

    @Test
    fun pendingAssessmentsDoNotCountAsLostWeightWhileZeroPresentDoes() {
        // SWARM-02: CAT-1 scored 24/30 (12/15 weight), CAT-2 is Pending (not lost), DA-1 is legitimate 0/10 (Present -> 10 lost)
        val cat1 = M3EAssessmentItem(
            title = "CAT-1",
            maxMarks = 30.0,
            scoredMarks = 24.0,
            weightagePercent = 15.0,
            weightageMarks = 12.0,
            status = "Present"
        )
        val cat2Pending = M3EAssessmentItem(
            title = "CAT-2",
            maxMarks = 30.0,
            scoredMarks = 0.0,
            weightagePercent = 15.0,
            weightageMarks = 0.0,
            status = "Pending"
        )
        val daDashPending = M3EAssessmentItem(
            title = "DA-1",
            maxMarks = 10.0,
            scoredMarks = 0.0,
            weightagePercent = 10.0,
            weightageMarks = 0.0,
            status = "-"
        )
        val daLegitZero = M3EAssessmentItem(
            title = "DA-2",
            maxMarks = 10.0,
            scoredMarks = 0.0,
            weightagePercent = 10.0,
            weightageMarks = 0.0,
            status = "Present"
        )

        assertFalse(M3EPredictorDerivation.isPendingAssessment(cat1))
        assertTrue(M3EPredictorDerivation.isPendingAssessment(cat2Pending))
        assertTrue(M3EPredictorDerivation.isPendingAssessment(daDashPending))
        assertFalse(M3EPredictorDerivation.isPendingAssessment(daLegitZero))

        val metrics = M3EPredictorDerivation.calculateCoursePredictionMetrics(
            listOf(cat1, cat2Pending, daDashPending, daLegitZero)
        )
        // Recorded: CAT-1 (12 scored, 3 lost) + DA-2 (0 scored, 10 lost) => 12 scored, 13 lost; Pending: 15+10=25 not lost.
        assertEquals(12.0, metrics.currentScoredScaled, 0.001)
        assertEquals(13.0, metrics.pointsLostScaled, 0.001)
        assertEquals(87.0, metrics.maxPossibleScaled, 0.001)
    }

    @Test
    fun onlyFatRemainsGatesFatSolverUntilAllOtherAssessmentsAreScored() {
        // SWARM-05: CAT-2 is still Pending -> solver must be gated (onlyFatRemains = false)
        val cat1 = M3EAssessmentItem("CAT-1", 50.0, 40.0, 15.0, 12.0, status = "Present", component = "ETH")
        val cat2Pending = M3EAssessmentItem("CAT-2", 50.0, 0.0, 15.0, 0.0, status = "Pending", component = "ETH")
        val fatPending = M3EAssessmentItem("Final Assessment Test", 100.0, 0.0, 40.0, 0.0, status = "Pending", component = "ETH")

        assertFalse(M3EPredictorDerivation.onlyFatRemains(listOf(cat1, cat2Pending, fatPending)))
        val gated = M3EPredictorDerivation.solveCourseFat("S", listOf(cat1, cat2Pending, fatPending))
        assertFalse(gated.onlyFatRemains)
        assertEquals("pending", gated.feasibility)
        assertEquals(0.0, gated.requiredFatRaw, 0.001)

        // Once CAT-2 has a legitimate 0.0 (Present), only FAT remains -> solver unlocks
        val cat2Zero = cat2Pending.copy(status = "Present")
        assertTrue(M3EPredictorDerivation.onlyFatRemains(listOf(cat1, cat2Zero, fatPending)))
        val unlocked = M3EPredictorDerivation.solveCourseFat("A", listOf(cat1, cat2Zero, fatPending))
        assertTrue(unlocked.onlyFatRemains)
        assertTrue(unlocked.requiredFatRaw > 0.0)

        // Embedded course: Lab FAT Pending blocks solver unless labFatScored is supplied
        val labAsm = M3EAssessmentItem("Lab Assessment", 60.0, 50.0, 60.0, 50.0, status = "Present", component = "ELA")
        val labFatPending = M3EAssessmentItem("Lab FAT", 50.0, 0.0, 40.0, 0.0, status = "Pending", component = "ELA")
        val embeddedList = listOf(cat1, cat2Zero, fatPending, labAsm, labFatPending)
        assertFalse(M3EPredictorDerivation.onlyFatRemains(embeddedList))
        assertTrue(M3EPredictorDerivation.onlyFatRemains(embeddedList, labFatScored = 40.0))
    }

    @Test
    fun statutoryPassFloorTheoryFatBelow40ForcesF() {
        // SWARM-06 (§9.4): 60/60 internal + 35/100 Theory FAT (< 40%) = 74 total, but must be F!
        val internals = M3EAssessmentItem("Internals", 60.0, 60.0, 60.0, 60.0, status = "Present", component = "ETH")
        val fatLow = M3EAssessmentItem("Final Assessment Test", 100.0, 35.0, 40.0, 14.0, status = "Present", component = "ETH")

        val metrics = M3EPredictorDerivation.calculateCoursePredictionMetrics(listOf(internals, fatLow))
        assertEquals("F", metrics.optimalGrade)
        assertEquals("F", metrics.maximisableGrade)
        assertTrue(metrics.passIssues.any { it.contains("40%") })
    }

    @Test
    fun statutoryPassFloorLabBelow50ForcesNAndWinsOverF() {
        // SWARM-06 (§9.6 / §9.7): Theory FAT < 40% (F) AND Lab < 50% (N) => N wins over F
        val theoryInternals = M3EAssessmentItem("Internals", 60.0, 60.0, 60.0, 60.0, status = "Present", component = "ETH")
        val theoryFatLow = M3EAssessmentItem("Final Assessment Test", 100.0, 30.0, 40.0, 12.0, status = "Present", component = "ETH")
        val labLow = M3EAssessmentItem("Lab Assessment", 100.0, 40.0, 100.0, 40.0, status = "Present", component = "ELA")

        val metrics = M3EPredictorDerivation.calculateCoursePredictionMetrics(
            assessments = listOf(theoryInternals, theoryFatLow, labLow),
            theoryCredits = 3.0,
            labCredits = 1.0
        )
        assertEquals("N", metrics.optimalGrade)
        assertEquals("N", metrics.maximisableGrade)
        assertEquals(M3ECardTone.CRITICAL, M3EPredictorDerivation.toneForGrade("N"))
        assertTrue(metrics.passIssues.any { it.contains("50%") })

        // Embedded solveCourseFat with Lab total < 50% reports impossible / N
        val theoryFatPending = theoryFatLow.copy(scoredMarks = 0.0, weightageMarks = 0.0, status = "Pending")
        val labAsm = M3EAssessmentItem("Lab Work", 60.0, 20.0, 60.0, 20.0, status = "Present", component = "ELA")
        val labFat = M3EAssessmentItem("Lab FAT", 50.0, 10.0, 40.0, 8.0, status = "Present", component = "ELA")
        val solver = M3EPredictorDerivation.solveCourseFat(
            targetGrade = "A",
            assessments = listOf(theoryInternals, theoryFatPending, labAsm, labFat),
            theoryCredits = 3.0,
            labCredits = 1.0
        )
        assertEquals("impossible", solver.feasibility)
        assertFalse(solver.meetsVitPassFloor)
        assertTrue(solver.feasibilityLabel.contains("N"))
    }
}
