package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
