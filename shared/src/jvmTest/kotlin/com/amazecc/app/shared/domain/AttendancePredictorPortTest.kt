package com.amazecc.app.shared.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-137 web-parity cases for the ported attendance engine.
 *
 * Fixtures are the dates and event spellings used by AmazeCC's
 * `src/__tests__/attendancePredictor.test.ts` / `milestoneDates.test.ts`. Weekday labels are
 * derived from the timestamps themselves so a fixture cannot quietly exercise the wrong day.
 * Cases already pinned in `AmazeCCPortedBusinessLogicTest` are not repeated except where they
 * guard the two fixes this change makes.
 */

private fun ts(month0: Int, day: Int): Long = AttendancePredictor.makeTimestamp(2026, month0, day)

private fun localDateOf(timestamp: Long) =
    Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun weekdayOf(timestamp: Long): String = when (localDateOf(timestamp).dayOfWeek) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun isWeekend(timestamp: Long): Boolean {
    val day = localDateOf(timestamp).dayOfWeek
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
}

private fun dayWith(timestamp: Long, events: List<PredictorDayEvent> = emptyList()) =
    DayLike(date = timestamp, weekday = weekdayOf(timestamp), events = events)

private val theoryCourse = PredictorCourse(
    courseCode = "BACSE105",
    courseTitle = "Data Structures and Algorithms",
    courseType = "Embedded Theory",
    slotName = "F1+TF1",
    attendedClasses = 20,
    totalClasses = 25,
)

private val labCourse = PredictorCourse(
    courseCode = "BACSE102",
    courseTitle = "Problem Solving Using Java",
    courseType = "Lab Only",
    slotName = "L1+L2",
    attendedClasses = 12,
    totalClasses = 15,
)

private val dayCardsMap = mapOf(
    "MON" to listOf(mapOf("courseCode" to "BACSE105")),
    "WED" to listOf(mapOf("courseCode" to "BACSE105")),
    "FRI" to listOf(mapOf("courseCode" to "BACSE105")),
    "TUE" to listOf(mapOf("courseCode" to "BACSE102")),
    "THU" to listOf(mapOf("courseCode" to "BACSE102")),
)

/** Mon/Wed/Fri 24 Aug – 4 Sep 2026: six real weekdays between the two CAT locks. */
private val futureDays = listOf(ts(7, 24), ts(7, 26), ts(7, 28), ts(7, 31), ts(8, 2), ts(8, 4))
    .map(::dayWith)

/** Tue/Thu 25 Aug – 1 Sep 2026: three real weekdays for the lab half. */
private val labDays = listOf(ts(7, 25), ts(7, 27), ts(8, 1)).map(::dayWith)

private fun project(course: PredictorCourse, days: List<DayLike>, skips: Set<Long> = emptySet()) =
    AttendancePredictor.projectCourse(
        ScheduleInput(
            course = course,
            dayCardsMap = dayCardsMap,
            workingDays = days,
            effectiveMap = AttendancePredictor.buildEffectiveDayMap(days),
            dateStates = emptyMap(),
            skips = skips,
            ceiling = null,
            lockDate = null,
            threshold = 75.0,
        )
    )

private fun calendarEvent(text: String, category: String = "General") =
    CalendarEventItem(type = "Other", text = text, category = category)

private fun month(label: String, vararg days: Pair<Int, List<CalendarEventItem>>) =
    CalendarMonth(
        label = label,
        days = days.map { (date, events) -> CalendarDay(date = date, events = events) },
    )

/** The event spellings the web's `milestoneDates.test.ts` takes from the real 2026 payload. */
private val MILESTONE_MONTHS = listOf(
    month(
        "AUGUST 2026",
        8 to listOf(calendarEvent("CAT - I")),
        14 to listOf(calendarEvent("CAT - I")),
    ),
    month("SEPTEMBER 2026", 25 to listOf(calendarEvent("(CAT - II)", "CAT - II"))),
    month(
        "OCTOBER 2026",
        23 to listOf(calendarEvent("(Working Day / LID for LAB courses)", "Working Day / LID for LAB classes")),
    ),
    month(
        "NOVEMBER 2026",
        3 to listOf(calendarEvent("(Working Day/ LID for Theory Classes)", "Working Day/ LID for Theory Classes")),
    ),
)

class AttendancePredictorPortTest {

    @Test
    fun toggleCourseSkip_is_immutable_and_subject_isolated() {
        val t = ts(7, 26)
        val original = mapOf<String, Set<Long>>()

        val first = AttendancePredictor.toggleCourseSkip(original, "BACSE105", t)
        assertTrue(original.isEmpty())
        assertEquals(1, AttendancePredictor.skipCountFor(first, "BACSE105"))
        assertTrue(AttendancePredictor.skipSetFor(first, "BACSE105").contains(t))

        val toggledOff = AttendancePredictor.toggleCourseSkip(first, "BACSE105", t)
        assertEquals(0, AttendancePredictor.skipCountFor(toggledOff, "BACSE105"))
        assertEquals(1, AttendancePredictor.skipCountFor(first, "BACSE105")) // first not mutated

        val both = AttendancePredictor.toggleCourseSkip(first, "BACSE102", t)
        assertEquals(1, AttendancePredictor.skipCountFor(both, "BACSE105"))
        assertEquals(1, AttendancePredictor.skipCountFor(both, "BACSE102"))
        assertEquals(0, AttendancePredictor.skipCountFor(first, "BACSE102"))
    }

    @Test
    fun skipSetFor_null_or_another_subject_is_empty() {
        assertTrue(AttendancePredictor.skipSetFor(null, "BACSE105").isEmpty())
        val skips = AttendancePredictor.toggleCourseSkip(emptyMap(), "BACSE105", ts(7, 26))
        assertTrue(AttendancePredictor.skipSetFor(skips, "BACSE102").isEmpty())
    }

    @Test
    fun courseMeetingDates_are_sorted_weekdays_bounded_by_ceiling_and_lock() {
        val effective = AttendancePredictor.buildEffectiveDayMap(futureDays)
        val expected = listOf(ts(7, 24), ts(7, 26), ts(7, 28), ts(7, 31), ts(8, 2), ts(8, 4))

        val dates = AttendancePredictor.courseMeetingDates("BACSE105", dayCardsMap, futureDays, effective)
        assertEquals(expected, dates)
        assertEquals(dates.sorted(), dates)
        assertTrue(dates.none(::isWeekend))
        assertEquals(project(theoryCourse, futureDays).futureDays, dates.size)

        assertEquals(
            listOf(ts(7, 24), ts(7, 26)),
            AttendancePredictor.courseMeetingDates("BACSE105", dayCardsMap, futureDays, effective, ceiling = ts(7, 26)),
        )
        assertEquals(
            listOf(ts(7, 24), ts(7, 26), ts(7, 28), ts(7, 31)),
            AttendancePredictor.courseMeetingDates("BACSE105", dayCardsMap, futureDays, effective, lockDate = ts(8, 2)),
        )
    }

    @Test
    fun courseMeetingDates_follow_a_published_thursday_day_order_on_a_saturday() {
        val saturday = ts(7, 29)
        assertEquals("Sat", weekdayOf(saturday)) // confirm the fixture really is a Saturday

        val plainSaturday = dayWith(saturday)
        val thursdayOrder = dayWith(
            saturday,
            listOf(PredictorDayEvent(text = "(Instructional Day - Thursday Day Order)")),
        )
        val plainEffective = AttendancePredictor.buildEffectiveDayMap(listOf(plainSaturday))
        val orderedEffective = AttendancePredictor.buildEffectiveDayMap(listOf(thursdayOrder))

        assertEquals(
            1,
            AttendancePredictor.courseMeetingDates("BACSE102", dayCardsMap, listOf(thursdayOrder), orderedEffective).size,
        )
        assertEquals(
            0,
            AttendancePredictor.courseMeetingDates("BACSE102", dayCardsMap, listOf(plainSaturday), plainEffective).size,
        )
    }

    @Test
    fun summarise_combines_two_courses_safeness_bunks_and_remaining_days() {
        val theory = project(theoryCourse, futureDays)
        val lab = project(labCourse, labDays)
        assertEquals(6, theory.futureDays)
        assertEquals(3, lab.futureDays)
        assertTrue(lab.isLab) // the lab half is weighted as two hours

        val summary = AttendancePredictor.summarise(listOf(theory, lab), 75.0)
        assertEquals(2, summary.totalCourses)
        assertEquals(2, summary.safeCount)
        assertEquals(0, summary.atRiskCount)
        assertEquals(theory.safeBunks + lab.safeBunks, summary.totalSafeBunks)
        assertEquals(maxOf(theory.futureDays, lab.futureDays), summary.remainingDays)

        val expectedPct =
            (theory.predictedAttended + lab.predictedAttended).toDouble() /
                (theory.predictedTotal + lab.predictedTotal).toDouble() * 100.0
        assertTrue(abs(summary.predictedPct - expectedPct) < 1e-9)

        val strict = AttendancePredictor.summarise(listOf(theory, lab), 99.0)
        assertEquals(0, strict.safeCount)
        assertEquals(2, strict.atRiskCount)
    }

    @Test
    fun gridCeiling_lid_and_all_take_the_later_lid() {
        val dates = MilestoneDates(
            cat1Date = ts(7, 8),
            cat2Date = ts(8, 25),
            lidLabDate = ts(9, 23),
            lidTheoryDate = ts(10, 3),
        )
        assertEquals(ts(10, 3), AttendancePredictor.gridCeiling("LID", dates))
        assertEquals(ts(10, 3), AttendancePredictor.gridCeiling("ALL", dates))
    }

    @Test
    fun projectCourse_without_a_calendar_is_an_inert_baseline() {
        val input = ScheduleInput(
            course = theoryCourse,
            dayCardsMap = null,
            workingDays = futureDays,
            effectiveMap = AttendancePredictor.buildEffectiveDayMap(futureDays),
            dateStates = emptyMap(),
            skips = setOf(ts(7, 26)),
            ceiling = null,
            lockDate = null,
            threshold = 75.0,
        )
        val result = AttendancePredictor.projectCourse(input)
        assertTrue(result.meetingDays.isEmpty())
        assertEquals(0, result.futureDays)
        assertEquals(0, result.missedDays)
        assertTrue(abs(result.currentPct - 80.0) < 1e-9)
        assertTrue(abs(result.predictedPct - 80.0) < 1e-9)
        assertTrue(abs(result.deltaPct) < 1e-9)
        assertEquals(0, result.safeBunks)
        assertEquals(0, result.classesNeeded)
        assertFalse(result.isLab)
        assertEquals(1, result.skippedCount) // this course's skip set, not a whole skip map

        val emptyCards = AttendancePredictor.projectCourse(input.copy(dayCardsMap = emptyMap()))
        assertEquals(0, emptyCards.futureDays)
        assertTrue(abs(emptyCards.predictedPct - 80.0) < 1e-9)
    }

    @Test
    fun buildMilestoneDates_resolves_four_canonical_keys_and_the_first_cat_day() {
        val dates = buildMilestoneDates(MILESTONE_MONTHS)
        assertEquals(ts(7, 8), dates.cat1Date)
        assertEquals(ts(8, 25), dates.cat2Date)
        assertEquals(ts(9, 23), dates.lidLabDate)
        assertEquals(ts(10, 3), dates.lidTheoryDate)
        assertTrue(dates.cat1Date != ts(7, 14)) // not the second day of the CAT I window

        val reversed = buildMilestoneDates(MILESTONE_MONTHS.asReversed())
        assertEquals(dates.cat1Date, reversed.cat1Date)
        assertEquals(dates.cat2Date, reversed.cat2Date)
    }

    @Test
    fun buildMilestoneDates_reads_category_only_events_and_ignores_casing() {
        val fromCategory = buildMilestoneDates(
            listOf(month("OCTOBER 2026", 23 to listOf(calendarEvent("Instructional Day", "Working Day / LID for LAB classes")))),
        )
        assertEquals(ts(9, 23), fromCategory.lidLabDate)
        assertNull(fromCategory.cat1Date)

        val shouted = buildMilestoneDates(
            listOf(month("OCTOBER 2026", 23 to listOf(calendarEvent("LID FOR LABORATORY CLASSES")))),
        )
        assertEquals(ts(9, 23), shouted.lidLabDate)
    }

    @Test
    fun buildMilestoneDates_is_all_null_for_an_empty_or_missing_payload() {
        val empty = buildMilestoneDates(emptyList<CalendarMonth>())
        val missing = buildMilestoneDates(null)
        for (dates in listOf(empty, missing)) {
            assertNull(dates.cat1Date)
            assertNull(dates.cat2Date)
            assertNull(dates.lidLabDate)
            assertNull(dates.lidTheoryDate)
        }
    }
}
