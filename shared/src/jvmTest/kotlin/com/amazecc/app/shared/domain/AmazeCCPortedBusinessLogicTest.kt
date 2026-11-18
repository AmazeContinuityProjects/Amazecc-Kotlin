package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of unit tests from AmazeCC:
 * - `AmazeCC/src/__tests__/slots.test.ts`
 * - `AmazeCC/src/__tests__/attendancePredictor.test.ts`
 * - `AmazeCC/src/__tests__/marksPredictor.test.ts`
 *
 * Verifies slot algebra, attendance threshold predictions (75%, 80%, 85%),
 * safe-to-miss margin calculations, and marks/FAT prediction algorithms.
 */
class AmazeCCPortedBusinessLogicTest {

    // =========================================================================
    // 1. SLOTS TESTS (from slots.test.ts)
    // =========================================================================

    @Test
    fun slotSpellings_gives_schema_slot_and_law_spelling_the_same_spellings() {
        assertEquals(listOf("A", "A1"), slotSpellings("A1").sorted())
        assertEquals(listOf("A", "A1"), slotSpellings("A").sorted())
    }

    @Test
    fun slotSpellings_answers_the_same_whichever_way_round_the_question_is_asked() {
        assertEquals(slotSpellings("A1").sorted(), slotSpellings("A").sorted())
    }

    @Test
    fun slotSpellings_covers_the_whole_third_row_not_just_the_first_column() {
        assertEquals(listOf("TCC", "TCC1"), slotSpellings("TCC1").sorted())
        assertEquals(listOf("TDD", "TDD1"), slotSpellings("TDD").sorted())
    }

    @Test
    fun slotSpellings_maps_the_two_S_id_periods_onto_law_school_last_two_slots() {
        assertEquals(listOf("S11", "TEE"), slotSpellings("S11").sorted())
        assertEquals(listOf("S15", "TFF"), slotSpellings("S15").sorted())
        assertEquals(listOf("S11", "TEE"), slotSpellings("TEE").sorted())
        assertEquals(listOf("S15", "TFF"), slotSpellings("TFF").sorted())
    }

    @Test
    fun slotSpellings_gives_evening_slot_only_itself() {
        assertEquals(listOf("A2"), slotSpellings("A2"))
        assertEquals(listOf("TDD2"), slotSpellings("TDD2"))
    }

    @Test
    fun slotSpellings_leaves_real_evening_S_slot_alone() {
        assertEquals(listOf("S1"), slotSpellings("S1"))
    }

    @Test
    fun slotSpellings_leaves_labs_alone() {
        assertEquals(listOf("L1"), slotSpellings("L1"))
        assertEquals(listOf("L31"), slotSpellings("L31"))
    }

    @Test
    fun slotSpellings_leaves_NIL_alone() {
        assertEquals(listOf("NIL"), slotSpellings("NIL"))
    }

    @Test
    fun slotSpellings_tolerates_casing_and_padding() {
        assertEquals(listOf("A", "A1"), slotSpellings(" a1 ").sorted())
    }

    @Test
    fun slotSpellings_has_nothing_to_say_about_empty() {
        assertEquals(emptyList(), slotSpellings(""))
    }

    @Test
    fun lawSlotFor_drops_lone_trailing_1() {
        assertEquals("A", lawSlotFor("A1"))
        assertEquals("TG", lawSlotFor("TG1"))
    }

    @Test
    fun lawSlotFor_keeps_S_id_table_ahead_of_shape() {
        assertEquals("TEE", lawSlotFor("S11"))
        assertEquals("TFF", lawSlotFor("S15"))
        assertNull(lawSlotFor("S12"))
    }

    @Test
    fun schemaSlotFor_puts_number_back_on_law_slot() {
        assertEquals("A1", schemaSlotFor("A"))
        assertEquals("TAA1", schemaSlotFor("TAA"))
    }

    @Test
    fun schemaSlotFor_returns_S_id_where_law_school_has_one() {
        assertEquals("S11", schemaSlotFor("TEE"))
        assertEquals("S15", schemaSlotFor("TFF"))
    }

    @Test
    fun schemaSlotFor_refuses_ids_that_are_not_law_slots() {
        assertNull(schemaSlotFor("NIL"))
        assertNull(schemaSlotFor("S"))
    }

    @Test
    fun expandSlotSpellings_makes_one_Set_answer_for_both_spellings() {
        val owned = expandSlotSpellings(listOf("A", "TA", "TAA"))
        assertTrue(owned.contains("A1"))
        assertTrue(owned.contains("TA1"))
        assertTrue(owned.contains("TAA1"))
    }

    @Test
    fun expandSlotSpellings_resolves_two_law_slots_landing_on_S_id_period() {
        val owned = expandSlotSpellings(listOf("E", "TE", "TEE", "F", "TF", "TFF"))
        assertTrue(owned.contains("S11"))
        assertTrue(owned.contains("S15"))
    }

    @Test
    fun expandSlotSpellings_still_reports_evening_slot_free() {
        val owned = expandSlotSpellings(listOf("A"))
        assertFalse(owned.contains("A2"))
    }

    @Test
    fun hasSlot_matches_correctly() {
        assertTrue(hasSlot(listOf("A", "TA"), "A1"))
        assertTrue(hasSlot(listOf("A", "TA"), "TA1"))
        assertTrue(hasSlot(listOf("A1"), "A"))
        assertFalse(hasSlot(listOf("A"), "A2"))
        assertFalse(hasSlot(listOf("A1"), "ZZZ"))
        assertFalse(hasSlot(emptyList(), "A1"))
    }

    @Test
    fun slotIntervals_and_clashes_and_lunch() {
        // Split slots
        assertEquals(listOf("B1", "TB1"), splitSlots("B1+TB1"))
        assertEquals(listOf("L31", "L32"), splitSlots("L31+L32"))

        // Time to minutes
        assertEquals(480, timeToMinutes("8:00 AM"))
        assertEquals(530, timeToMinutes("08:50 AM"))
        assertEquals(840, timeToMinutes("2:00 PM"))
        assertEquals(720, timeToMinutes("12:00 PM"))
        assertEquals(0, timeToMinutes("12:00 AM"))
        assertNull(timeToMinutes("invalid"))

        // Period overlap
        val p1 = SlotPeriod(day = "mon", start = "8:00 AM", end = "8:50 AM", startMin = 480, endMin = 530, type = "theory", index = 0)
        val p2 = SlotPeriod(day = "mon", start = "8:30 AM", end = "9:20 AM", startMin = 510, endMin = 560, type = "lab", index = 1)
        val p3 = SlotPeriod(day = "mon", start = "9:50 AM", end = "10:40 AM", startMin = 590, endMin = 640, type = "theory", index = 2)
        val pTue = SlotPeriod(day = "tue", start = "8:00 AM", end = "8:50 AM", startMin = 480, endMin = 530, type = "theory", index = 0)

        assertTrue(periodsOverlap(p1, p2))
        assertFalse(periodsOverlap(p1, p3))
        assertFalse(periodsOverlap(p1, pTue))

        val schema = CampusSchema(
            theory = listOf(
                SchemaPeriod(start = "8:00 AM", end = "8:50 AM", days = mapOf("mon" to "A1", "tue" to "B1")),
                SchemaPeriod(start = "8:55 AM", end = "9:45 AM", days = mapOf("mon" to "F1", "tue" to "G1")),
            ),
            lab = listOf(
                SchemaPeriod(start = "8:00 AM", end = "8:50 AM", days = mapOf("mon" to "L1", "tue" to "L7")),
                SchemaPeriod(start = "8:50 AM", end = "9:40 AM", days = mapOf("mon" to "L2", "tue" to "L8")),
            )
        )

        // slotsOverlap
        assertTrue(slotsOverlap("A1", "L1+L2", schema))
        assertFalse(slotsOverlap("A1", "L7+L8", schema)) // A1 is mon, L7+L8 is tue

        // ensureLunchPeriod
        val withoutLunch = listOf(
            SchemaPeriod(start = "8:00 AM", end = "8:50 AM"),
            SchemaPeriod(start = "2:00 PM", end = "2:50 PM"),
        )
        val withLunch = ensureLunchPeriod(withoutLunch)
        assertEquals(3, withLunch.size)
        assertTrue(withLunch[1].lunch)
        assertEquals("1:20 PM", withLunch[1].start)
        assertEquals("2:00 PM", withLunch[1].end)
    }

    // =========================================================================
    // 2. ATTENDANCE PREDICTOR TESTS (from attendancePredictor.test.ts)
    // =========================================================================

    @Test
    fun calculateClassesRequired_for_75_80_85_thresholds() {
        // Attended 10 of 20 = 50%.
        // Target 75%: needs 20 classes -> (10 + 20) / (20 + 20) = 30 / 40 = 75%
        assertEquals(20, AttendancePredictor.calculateClassesRequired(10, 20, 75.0))

        // Target 80%: needs 30 classes -> (10 + 30) / (20 + 30) = 40 / 50 = 80%
        assertEquals(30, AttendancePredictor.calculateClassesRequired(10, 20, 80.0))

        // Target 85%: needs 47 classes -> (10 + 47) / (20 + 47) = 57 / 67 = 85.07%
        assertEquals(47, AttendancePredictor.calculateClassesRequired(10, 20, 85.0))

        // When already above target, 0 classes required
        assertEquals(0, AttendancePredictor.calculateClassesRequired(18, 20, 75.0))
        assertEquals(0, AttendancePredictor.calculateClassesRequired(16, 20, 80.0))

        // Lab course halving
        // Normal needs 20 -> lab needs ceil(20 / 2) = 10
        assertEquals(10, AttendancePredictor.calculateClassesRequired(10, 20, 75.0, isLab = true))
    }

    @Test
    fun calculateSafeBunks_above_thresholds() {
        // Attended 20 of 25 = 80%.
        // Target 75%: can bunk 1 class -> 20 / 26 = 76.92% (bunking 2 would be 20/27 = 74.07% < 75%)
        assertEquals(1, AttendancePredictor.calculateSafeBunks(20, 25, 75.0))

        // Attended 18 of 20 = 90%.
        // Target 75%: can bunk 4 classes -> 18 / 24 = 75% exactly
        assertEquals(4, AttendancePredictor.calculateSafeBunks(18, 20, 75.0))

        // Target 80%: can bunk 2 classes -> 18 / 22 = 81.8% (bunking 3 would be 18/23 = 78.26% < 80%)
        assertEquals(2, AttendancePredictor.calculateSafeBunks(18, 20, 80.0))

        // Target 85%: can bunk 1 class -> 18 / 21 = 85.71%
        assertEquals(1, AttendancePredictor.calculateSafeBunks(18, 20, 85.0))

        // Below target returns 0
        assertEquals(0, AttendancePredictor.calculateSafeBunks(10, 20, 75.0))

        // Lab course halving
        // Safe 4 -> lab safe floor(4 / 2) = 2
        assertEquals(2, AttendancePredictor.calculateSafeBunks(18, 20, 75.0, isLab = true))
    }

    @Test
    fun lockDateFor_subtracts_calendar_days() {
        val aug8 = AttendancePredictor.makeTimestamp(2026, 7, 8)
        val aug5 = AttendancePredictor.makeTimestamp(2026, 7, 5)
        assertEquals(aug5, AttendancePredictor.lockDateFor(aug8, 3))

        val sep25 = AttendancePredictor.makeTimestamp(2026, 8, 25)
        val sep22 = AttendancePredictor.makeTimestamp(2026, 8, 22)
        assertEquals(sep22, AttendancePredictor.lockDateFor(sep25, 3))

        assertNull(AttendancePredictor.lockDateFor(null, 3))
    }

    @Test
    fun courseCeiling_and_gridCeiling() {
        val aug8 = AttendancePredictor.makeTimestamp(2026, 7, 8)
        val sep25 = AttendancePredictor.makeTimestamp(2026, 8, 25)
        val oct23 = AttendancePredictor.makeTimestamp(2026, 9, 23)
        val nov3 = AttendancePredictor.makeTimestamp(2026, 10, 3)

        val dates = MilestoneDates(
            cat1Date = aug8,
            cat2Date = sep25,
            lidLabDate = oct23,
            lidTheoryDate = nov3,
        )

        val aug5 = AttendancePredictor.makeTimestamp(2026, 7, 5)
        val sep22 = AttendancePredictor.makeTimestamp(2026, 8, 22)

        assertEquals(aug5, AttendancePredictor.courseCeiling("CAT1", false, dates))
        assertEquals(aug5, AttendancePredictor.courseCeiling("CAT1", true, dates))
        assertEquals(sep22, AttendancePredictor.courseCeiling("CAT2", false, dates))
        assertEquals(oct23, AttendancePredictor.courseCeiling("LID", true, dates))
        assertEquals(nov3, AttendancePredictor.courseCeiling("LID", false, dates))
        assertEquals(oct23, AttendancePredictor.courseCeiling("ALL", true, dates))
        assertEquals(nov3, AttendancePredictor.courseCeiling("ALL", false, dates))

        assertEquals(aug5, AttendancePredictor.gridCeiling("CAT1", dates))
        assertEquals(sep22, AttendancePredictor.gridCeiling("CAT2", dates))
        assertEquals(nov3, AttendancePredictor.gridCeiling("LID", dates))
        assertEquals(nov3, AttendancePredictor.gridCeiling("ALL", dates))
    }

    @Test
    fun effectiveWeekday_follows_published_day_orders() {
        assertEquals("WED", AttendancePredictor.effectiveWeekday("Wed", emptyList()))
        assertEquals("MON", AttendancePredictor.effectiveWeekday("MON", null))

        val saturdayThursdayOrder = listOf(PredictorDayEvent(text = "(Instructional Day - Thursday Day Order)"))
        assertEquals("THU", AttendancePredictor.effectiveWeekday("Sat", saturdayThursdayOrder))

        val categoryOrder = listOf(PredictorDayEvent(text = "Instructional Day", category = "Instructional Day - Friday Day Order"))
        assertEquals("FRI", AttendancePredictor.effectiveWeekday("SAT", categoryOrder))

        val plainSat = listOf(PredictorDayEvent(text = "Instructional Day"))
        assertEquals("SAT", AttendancePredictor.effectiveWeekday("Sat", plainSat))
    }

    @Test
    fun courseClassification_and_meeting_days() {
        assertTrue(AttendancePredictor.isLabCourse("BACSE102", "Lab Only"))
        assertTrue(AttendancePredictor.isLabCourse("BACSE102(L)", ""))
        assertFalse(AttendancePredictor.isLabCourse("BACSE105", "Embedded Theory"))

        assertTrue(AttendancePredictor.isProjectableCourse("BACSE105", "F1", 20))
        assertFalse(AttendancePredictor.isProjectableCourse("", "F1", 20))
        assertFalse(AttendancePredictor.isProjectableCourse("BACSE105", "NILL", 20))

        val dayCardsMap = mapOf(
            "MON" to listOf(mapOf("courseCode" to "BACSE105")),
            "WED" to listOf(mapOf("courseCode" to "BACSE105")),
            "FRI" to listOf(mapOf("courseCode" to "BACSE105")),
            "TUE" to listOf(mapOf("courseCode" to "BACSE102")),
            "THU" to listOf(mapOf("courseCode" to "BACSE102")),
        )

        assertEquals(listOf("FRI", "MON", "WED"), AttendancePredictor.courseMeetingDays("BACSE105", dayCardsMap).sorted())
        assertEquals(listOf("THU", "TUE"), AttendancePredictor.courseMeetingDays("BACSE102", dayCardsMap).sorted())
    }

    @Test
    fun projectCourse_and_summarise() {
        val theoryCourse = PredictorCourse(
            courseCode = "BACSE105",
            courseTitle = "DSA",
            courseType = "Embedded Theory",
            slotName = "F1+TF1",
            attendedClasses = 20,
            totalClasses = 25,
        )

        val dayCardsMap = mapOf(
            "MON" to listOf(mapOf("courseCode" to "BACSE105")),
            "WED" to listOf(mapOf("courseCode" to "BACSE105")),
            "FRI" to listOf(mapOf("courseCode" to "BACSE105")),
        )

        val d1 = AttendancePredictor.makeTimestamp(2026, 7, 24) // Mon
        val d2 = AttendancePredictor.makeTimestamp(2026, 7, 26) // Wed
        val d3 = AttendancePredictor.makeTimestamp(2026, 7, 28) // Fri
        val d4 = AttendancePredictor.makeTimestamp(2026, 7, 31) // Mon
        val d5 = AttendancePredictor.makeTimestamp(2026, 8, 2)  // Wed
        val d6 = AttendancePredictor.makeTimestamp(2026, 8, 4)  // Fri

        val workingDays = listOf(
            DayLike(date = d1, weekday = "Mon"),
            DayLike(date = d2, weekday = "Wed"),
            DayLike(date = d3, weekday = "Fri"),
            DayLike(date = d4, weekday = "Mon"),
            DayLike(date = d5, weekday = "Wed"),
            DayLike(date = d6, weekday = "Fri"),
        )
        val effMap = AttendancePredictor.buildEffectiveDayMap(workingDays)

        val result = AttendancePredictor.projectCourse(
            ScheduleInput(
                course = theoryCourse,
                dayCardsMap = dayCardsMap,
                workingDays = workingDays,
                effectiveMap = effMap,
                dateStates = emptyMap(),
                skips = emptySet(),
                ceiling = null,
                lockDate = null,
                threshold = 75.0,
            )
        )

        assertEquals(6, result.futureDays)
        assertEquals(0, result.missedDays)
        assertEquals(26, result.predictedAttended) // 20 + 6
        assertEquals(31, result.predictedTotal)    // 25 + 6
        assertTrue(result.predictedPct > 80.0)

        // Summary
        val summary = AttendancePredictor.summarise(listOf(result), 75.0)
        assertEquals(1, summary.totalCourses)
        assertEquals(1, summary.safeCount)
        assertEquals(0, summary.atRiskCount)

        // Format percentage
        assertEquals("87.46", AttendancePredictor.formatPct(87.4567, true))
        assertEquals("87.5", AttendancePredictor.formatPct(87.4567, false))
    }

    // =========================================================================
    // 3. MARKS PREDICTOR TESTS (from marksPredictor.test.ts)
    // =========================================================================

    @Test
    fun computeComponentStats_accurately_calculates_theory_stats() {
        val theoryAssessments = listOf(
            PredictorAssessment(id = "t1", title = "CAT - I", maxMark = 50.0, weightagePercent = 15.0, scoredMark = 40.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t2", title = "CAT - II", maxMark = 50.0, weightagePercent = 15.0, scoredMark = 45.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t3", title = "DA - 1", maxMark = 10.0, weightagePercent = 10.0, scoredMark = 10.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t4", title = "DA - 2", maxMark = 10.0, weightagePercent = 10.0, scoredMark = 8.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t5", title = "DA - 3", maxMark = 10.0, weightagePercent = 10.0, scoredMark = null, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t6", title = "FAT", maxMark = 100.0, weightagePercent = 40.0, scoredMark = null, component = PredictorComponent.THEORY),
        )

        val stats = computeComponentStats(theoryAssessments, "theory", 3.0)

        assertEquals(43.5, stats.scoredWeight)
        assertEquals(6.5, stats.lostWeight)
        assertEquals(50.0, stats.pendingWeight)
        assertEquals(93.5, stats.maxPossibleWeight)
        assertEquals(4, stats.completedAssessmentsCount)
        assertEquals(2, stats.pendingAssessmentsCount)
    }

    @Test
    fun computeComponentStats_handles_pats_and_continuous_evaluation() {
        val patAssessments = listOf(
            PredictorAssessment(id = "p1", title = "PAT - I", maxMark = 50.0, weightagePercent = 10.0, scoredMark = 45.0, component = PredictorComponent.LAB),
            PredictorAssessment(id = "p2", title = "PAT - II", maxMark = 50.0, weightagePercent = 15.0, scoredMark = 40.0, component = PredictorComponent.LAB),
            PredictorAssessment(id = "p3", title = "PAT - III", maxMark = 50.0, weightagePercent = 15.0, scoredMark = 50.0, component = PredictorComponent.LAB),
            PredictorAssessment(id = "p4", title = "PAT - IV", maxMark = 50.0, weightagePercent = 10.0, scoredMark = null, component = PredictorComponent.LAB),
            PredictorAssessment(id = "p5", title = "Record", maxMark = 100.0, weightagePercent = 10.0, scoredMark = 95.0, component = PredictorComponent.LAB),
            PredictorAssessment(id = "p6", title = "Lab FAT", maxMark = 50.0, weightagePercent = 40.0, scoredMark = null, component = PredictorComponent.LAB),
        )

        val stats = computeComponentStats(patAssessments, "lab", 1.0)

        assertEquals(45.5, stats.scoredWeight)
        assertEquals(4.5, stats.lostWeight)
        assertEquals(95.5, stats.maxPossibleWeight)
    }

    @Test
    fun computeCoursePrediction_embedded_theory_plus_lab() {
        val theoryAssessments = listOf(
            PredictorAssessment(id = "t1", title = "CAT - I", maxMark = 50.0, weightagePercent = 15.0, scoredMark = 40.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t2", title = "CAT - II", maxMark = 50.0, weightagePercent = 15.0, scoredMark = 45.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t3", title = "DA - 1", maxMark = 10.0, weightagePercent = 10.0, scoredMark = 10.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t4", title = "DA - 2", maxMark = 10.0, weightagePercent = 10.0, scoredMark = 10.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t5", title = "DA - 3", maxMark = 10.0, weightagePercent = 10.0, scoredMark = 10.0, component = PredictorComponent.THEORY),
            PredictorAssessment(id = "t6", title = "FAT", maxMark = 100.0, weightagePercent = 40.0, scoredMark = 80.0, component = PredictorComponent.THEORY),
        )

        val labAssessments = listOf(
            PredictorAssessment(id = "l1", title = "Continuous", maxMark = 100.0, weightagePercent = 60.0, scoredMark = 95.0, component = PredictorComponent.LAB),
            PredictorAssessment(id = "l2", title = "Lab FAT", maxMark = 50.0, weightagePercent = 40.0, scoredMark = 45.0, component = PredictorComponent.LAB),
        )

        val prediction = computeCoursePrediction(
            courseCode = "BCSE202E",
            courseTitle = "Data Structures",
            courseType = "Embedded Theory",
            theoryAssessments = theoryAssessments,
            labAssessments = labAssessments,
            theoryCredits = 3.0,
            labCredits = 1.0,
        )

        assertEquals(88.88, prediction.currentScoredScaled)
        assertEquals(11.13, prediction.pointsLostScaled)
        assertTrue(prediction.isEmbedded)
        assertEquals(88.87, prediction.pacePct)
        assertEquals("A", prediction.optimalGrade?.letter)
        assertEquals("A", prediction.maximisableGrade.letter)
    }

    @Test
    fun solveTargetFAT_calculations_and_cutoffs() {
        // Target 90 (S grade) with 52.0 scored without FAT
        val sResult = solveTargetFAT(
            targetScore = 90.0,
            currentScoredScaled = 52.0,
            fatWeightagePercent = 40.0,
            fatMaxMark = 100.0,
        )
        assertEquals(38.0, sResult.requiredFATWeight)
        assertEquals(95.0, sResult.requiredFATRawScore)
        assertTrue(sResult.isAchievable)
        assertEquals(Feasibility.MIRACLE, sResult.feasibility)
        assertTrue(sResult.meetsVITMinimumCutoff)

        // Impossible target: 45.0 scored, deficit is 45, but FAT is only 40 wt
        val impossibleResult = solveTargetFAT(
            targetScore = 90.0,
            currentScoredScaled = 45.0,
            fatWeightagePercent = 40.0,
            fatMaxMark = 100.0,
        )
        assertFalse(impossibleResult.isAchievable)
        assertEquals(Feasibility.IMPOSSIBLE, impossibleResult.feasibility)

        // VIT 40% minimum passing cutoff validation:
        // Target 55 with 50 scored without FAT. Deficit is 5 wt (12.5/100 raw mark)
        // 12.5 < 40, so meetsVITMinimumCutoff must be false
        val cutoffResult = solveTargetFAT(
            targetScore = 55.0,
            currentScoredScaled = 50.0,
            fatWeightagePercent = 40.0,
            fatMaxMark = 100.0,
        )
        assertEquals(12.5, cutoffResult.requiredFATRawScore)
        assertFalse(cutoffResult.meetsVITMinimumCutoff)
    }

    @Test
    fun normalizeWeights_scales_arbitrary_weights_to_100() {
        val items1 = listOf(
            WeightageItem(title = "CAT 1", weightagePercent = 20.0),
            WeightageItem(title = "CAT 2", weightagePercent = 20.0),
            WeightageItem(title = "FAT", weightagePercent = 60.0),
        )
        val normalized1 = normalizeWeights(items1)
        val sum1 = normalized1.sumOf { it.weightagePercent }
        assertEquals(100.0, sum1)

        val items2 = listOf(
            WeightageItem(title = "Quiz 1", weightagePercent = 15.0),
            WeightageItem(title = "Quiz 2", weightagePercent = 15.0),
            WeightageItem(title = "Assignment", weightagePercent = 20.0),
        ) // sum = 50
        val normalized2 = normalizeWeights(items2)
        val sum2 = normalized2.sumOf { it.weightagePercent }
        assertEquals(100.0, sum2)
        assertEquals(30.0, normalized2[0].weightagePercent)
        assertEquals(30.0, normalized2[1].weightagePercent)
        assertEquals(40.0, normalized2[2].weightagePercent)
    }

    @Test
    fun estimateGrade_returns_appropriate_grade_letter_for_brackets() {
        assertEquals("S", estimateGrade(95.0).letter)
        assertEquals("A", estimateGrade(85.0).letter)
        assertEquals("B", estimateGrade(75.0).letter)
        assertEquals("C", estimateGrade(65.0).letter)
        assertEquals("D", estimateGrade(57.0).letter)
        assertEquals("E", estimateGrade(45.0).letter)
        assertEquals("F", estimateGrade(35.0).letter)
    }
}
