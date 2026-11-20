package com.amazecc.app.shared.domain

import com.amazecc.app.shared.utils.TimeMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Web-parity checks for the grading/formatting drift fixes D-005, D-079..D-083.
 *
 * Every expectation here is read from the current web source (`gradeBoundaries.ts`,
 * `attendancePredictor.ts`, `attendanceCalculators.ts`, `payments.ts`, `timeUtils.ts`), not from the
 * old Kotlin behaviour — several of these deliberately contradict it.
 */
class GradingParityTest {

    // ── D-005: v5.0 absolute ladder ───────────────────────────────────────────

    @Test
    fun absoluteLadderUsesTheV50Boundaries() {
        assertEquals("F", estimateGrade(49.0).letter)
        assertEquals("E", estimateGrade(50.0).letter)
        assertEquals("E", estimateGrade(54.0).letter)
        assertEquals("D", estimateGrade(55.0).letter)
        assertEquals("D", estimateGrade(59.0).letter)
        assertEquals("C", estimateGrade(60.0).letter)
        assertEquals("B", estimateGrade(70.0).letter)
        assertEquals("A", estimateGrade(80.0).letter)
        assertEquals("S", estimateGrade(90.0).letter)
    }

    @Test
    fun thePassLineIsFiftyAndTheGradePointsFollowTheLetter() {
        assertEquals("Fail (0 Grade Points)", estimateGrade(49.0).description)
        assertEquals("Pass (5 Grade Points)", estimateGrade(50.0).description)
        assertEquals("Average (6 Grade Points)", estimateGrade(55.0).description)
        assertEquals(listOf(90.0, 80.0, 70.0, 60.0, 55.0, 50.0, 0.0),
            ABSOLUTE_GRADE_BOUNDARIES.map { it.limit })
    }

    @Test
    fun gradeAtRoundsTheTotalUpBeforeReadingTheBand() {
        // Web `gradeAt`: ceil(pct - 1e-9) — 79.5 reads as an 80, i.e. an A.
        assertEquals("A", gradeAt(79.5))
        assertEquals("S", gradeAt(89.99))
        assertEquals("F", gradeAt(49.0))
    }

    // ── D-005: cohort curve model ─────────────────────────────────────────────

    private val sampleStats = ClassStats(count = 5, mean = 70.0, sd = 10.0)

    @Test
    fun curveRequiresFiveContributorsAndPoolsSigmaWithThePrior() {
        assertFalse(hasUsableClassStats(ClassStats(4, 70.0, 10.0)))
        assertTrue(hasUsableClassStats(sampleStats))
        assertNull(effectiveClassStats(ClassStats(4, 70.0, 10.0)))

        // sqrt((4*100 + 4*144) / 8) = sqrt(122) = 11.0453…
        assertEquals(11.045361, effectiveSd(sampleStats), 1e-5)
        // A manual curve is taken exactly as typed — no pooling.
        assertEquals(11.0, effectiveSd(ClassStats(0, 62.0, 11.0, manual = true)), 1e-9)
    }

    @Test
    fun manualCurveValidationMatchesTheWebRanges() {
        val manual = manualClassStats(62.0, 11.0)
        assertNotNull(manual)
        assertTrue(manual.manual)
        assertEquals(0, manual.count)
        assertNull(manualClassStats(62.0, 0.2))
        assertNull(manualClassStats(62.0, 51.0))
        assertNull(manualClassStats(101.0, 11.0))
        assertFalse(hasUsableClassStats(ClassStats(0, 62.0, 0.0, manual = true)))
    }

    @Test
    fun curveIsFlaggedAsAnEarlyEstimateUntilFifteenContributors() {
        assertTrue(isEarlyEstimate(sampleStats))
        assertFalse(isEarlyEstimate(ClassStats(15, 70.0, 10.0)))
        assertFalse(isEarlyEstimate(ClassStats(0, 62.0, 11.0, manual = true)))
        assertFalse(isEarlyEstimate(null))
    }

    @Test
    fun curvedLadderStepsDownByHalfASigmaAndIsClamped() {
        // effectiveSd = 11.0453; S=round(70+1.5σ)=87, A=76, B=64, C=59, D=53,
        // E=min(round(70-2σ), 50)=48, then F.
        val ladder = buildGradeBoundaries(sampleStats)
        assertEquals(listOf("S", "A", "B", "C", "D", "E", "F"), ladder.map { it.grade })
        assertEquals(listOf(87.0, 76.0, 64.0, 59.0, 53.0, 48.0, 0.0), ladder.map { it.limit })

        assertEquals("S", estimateGrade(87.0, sampleStats).letter)
        assertEquals("A", estimateGrade(86.0, sampleStats).letter)
        assertEquals("B", estimateGrade(64.0, sampleStats).letter)
        assertEquals("C", estimateGrade(59.0, sampleStats).letter)
        assertEquals("D", estimateGrade(53.0, sampleStats).letter)
        assertEquals("E", estimateGrade(49.0, sampleStats).letter)
        assertEquals("F", estimateGrade(47.0, sampleStats).letter)

        // The same 49 is an F on the absolute ladder — the curve is what changes it.
        assertEquals("F", estimateGrade(49.0).letter)
    }

    @Test
    fun unusableOrAbsoluteCoursesFallBackToTheFixedLadder() {
        assertEquals("E", estimateGrade(54.0, ClassStats(3, 70.0, 10.0)).letter)
        assertEquals("F", estimateGrade(49.0, null).letter)
        assertEquals(listOf(90.0, 80.0, 70.0, 60.0, 55.0, 50.0, 0.0),
            buildGradeBoundaries(sampleStats, relative = false).map { it.limit })
    }

    @Test
    fun onlyTheoryCoursesAreCurved() {
        assertTrue(isRelativeGrading(null, "Theory Only"))
        assertTrue(isRelativeGrading(null, "Embedded Theory"))
        assertTrue(isRelativeGrading(null, "Embedded"))
        assertTrue(isRelativeGrading(null, "Embedded Lab"))
        assertFalse(isRelativeGrading(null, "Lab Only"))
        assertFalse(isRelativeGrading(null, "Project"))
        assertFalse(isRelativeGrading(null, ""))
    }

    @Test
    fun topShareCutoffStaysInsideTheRegulatedRange() {
        val cutoff = estimateTopShareCutoff(95.0, 8.0)
        assertTrue(cutoff in 80.0..100.0, "cutoff was $cutoff")
        assertEquals(100.0, estimateTopShareCutoff(100.0, 5.0), 1e-9)
    }

    // ── D-079: JS toFixed parity ──────────────────────────────────────────────

    @Test
    fun toFixedRoundsTheExactBinaryValueLikeV8() {
        assertEquals("1.00", FormattingParity.toFixed(1.005, 2))
        assertEquals("2.67", FormattingParity.toFixed(2.675, 2))
        assertEquals("0.61", FormattingParity.toFixed(0.615, 2))
        assertEquals("80.00", FormattingParity.toFixed(80.0, 2))
        assertEquals("-1.00", FormattingParity.toFixed(-1.005, 2))
        assertEquals("-2", FormattingParity.toFixed(-1.5, 0))
    }

    @Test
    fun formatPctKeepsTrailingZerosAndUsesWebRounding() {
        assertEquals("87.46", AttendancePredictor.formatPct(87.4567, true))
        assertEquals("87.5", AttendancePredictor.formatPct(87.4567, false))
        assertEquals("2.67", AttendancePredictor.formatPct(2.675, true))
        assertEquals("80.00", AttendancePredictor.formatPct(80.0, true))
        assertEquals("80.0", AttendancePredictor.formatPct(80.0, false))
    }

    // ── D-080: no epsilon off-by-one at exact thresholds ──────────────────────

    @Test
    fun bunkCountUsesPlainFloorAtExactThresholds() {
        // (4 - 0.8*4)/0.8 evaluates to 0.9999999999999998; epsilon made it 1.
        assertEquals(0, AttendancePredictor.calculateSafeBunks(4, 4, 80.0))
        // (8 - 0.8*8)/0.8 evaluates to 1.9999999999999996; epsilon made it 2.
        assertEquals(1, AttendancePredictor.calculateSafeBunks(8, 8, 80.0))
    }

    @Test
    fun neededCountUsesPlainCeilAtExactThresholds() {
        // (0.8*4 - 3)/(1 - 0.8) evaluates to 1.000000000000001; epsilon made it 1.
        assertEquals(2, AttendancePredictor.calculateClassesRequired(3, 4, 80.0))
        assertEquals(5, AttendancePredictor.calculateClassesRequired(0, 1, 80.0))
    }

    @Test
    fun projectCourseMarginsHaveNoEpsilonGuardEither() {
        val base = PredictorCourse(courseCode = "CS101", attendedClasses = 8, totalClasses = 8)
        val cards = mapOf("MON" to listOf(mapOf("courseCode" to "CS101")))

        val safe = AttendancePredictor.projectCourse(ScheduleInput(course = base, dayCardsMap = cards, threshold = 80.0))
        assertEquals(1, safe.safeBunks)

        val short = base.copy(attendedClasses = 3, totalClasses = 4)
        val needed = AttendancePredictor.projectCourse(ScheduleInput(course = short, dayCardsMap = cards, threshold = 80.0))
        assertEquals(2, needed.classesNeeded)
    }

    // ── D-081: 1-dp current percentage drives the safety flag ─────────────────

    @Test
    fun currentPercentageIsRoundedToOneDecimalBeforeTheThresholdCheck() {
        // 2999/4000 = 74.975%, which the web reads as 75.0 and therefore safe.
        assertEquals(75.0, AttendancePredictor.roundedCurrentPct(2999, 4000), 1e-9)
        assertEquals(74.9, AttendancePredictor.roundedCurrentPct(2997, 4000), 1e-9)
        assertEquals(100.0, AttendancePredictor.roundedCurrentPct(0, 0), 1e-9)
        assertEquals(0, AttendancePredictor.calculateClassesRequired(2999, 4000, 75.0))
    }

    // ── D-082: ₹ en-IN currency formatting ─────────────────────────────────────

    @Test
    fun currencyHasNoSpaceAndNoTrailingZeros() {
        val formatted = listOf(
            FormattingParity.fmtAmt("₹ 1,240.00"),
            FormattingParity.fmtAmt("₹2,12,500.00"),
            FormattingParity.fmtAmt("₹ 8,500.00"),
        )
        assertEquals(listOf("₹1,240", "₹2,12,500", "₹8,500"), formatted)
        formatted.forEach {
            assertFalse(it.contains(" "), "$it contains a space")
            assertFalse(it.endsWith(".00"), "$it has trailing .00")
        }
    }

    @Test
    fun currencyHandlesNumbersEmptyAndFractionalAmounts() {
        assertEquals("₹0", FormattingParity.fmtAmt(null as String?))
        assertEquals("₹0", FormattingParity.fmtAmt(""))
        assertEquals("₹1,240", FormattingParity.fmtAmt(1240))
        assertEquals("₹1,240.5", FormattingParity.fmtAmt(1240.5))
        assertEquals("₹12,34,567.89", FormattingParity.fmtAmt("12,34,567.89"))
        assertEquals("$-1,240", FormattingParity.fmtAmt("-1,240", symbol = "$"))
    }

    // ── D-083: one web-parity time parser ─────────────────────────────────────

    @Test
    fun bareTimesAreTwentyFourHourNotVitSlots() {
        assertEquals(480, TimeMath.parseTimeToMinutes("08:00"))
        assertEquals(420, TimeMath.parseTimeToMinutes("07:00"))
        assertEquals(870, TimeMath.parseTimeToMinutes("14:30"))
        // Contrast: the legacy VIT slot parser still applies its afternoon heuristic.
        assertEquals(1140, TimeMath.toMinutes("07:00"))
    }

    @Test
    fun meridiansAndRawMinutesAreAccepted() {
        assertEquals(480, TimeMath.parseTimeToMinutes("8:00 AM"))
        assertEquals(1200, TimeMath.parseTimeToMinutes("08:00PM"))
        assertEquals(750, TimeMath.parseTimeToMinutes("12:30 pm"))
        assertEquals(0, TimeMath.parseTimeToMinutes("12:00 AM"))
        assertEquals(720, TimeMath.parseTimeToMinutes("12:00 PM"))
        assertEquals(1080, TimeMath.parseTimeToMinutes(" 6:00 PM "))
        assertEquals(90, TimeMath.parseTimeToMinutes(90))
        assertEquals(90, TimeMath.parseTimeToMinutes(90.9))
    }

    @Test
    fun unparseableTimesReturnNullAndResultsAreClamped() {
        assertNull(TimeMath.parseTimeToMinutes(null))
        assertNull(TimeMath.parseTimeToMinutes(""))
        assertNull(TimeMath.parseTimeToMinutes("not a time"))
        assertNull(TimeMath.parseTimeToMinutes("9:00 - 9:50"))
        assertEquals(1439, TimeMath.parseTimeToMinutes("25:00"))
    }
}
