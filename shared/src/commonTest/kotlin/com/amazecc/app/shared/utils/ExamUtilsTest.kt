package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.ExamItem
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamUtilsTest {

    // ── Date parsing ──

    @Test
    fun parsesMonthNameDates() {
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("19-Nov-2025"))
        assertEquals(LocalDate(2026, 3, 3), ExamUtils.parseExamDateToLocalDate("03-Mar-2026"))
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("19 Nov 2025"))
    }

    @Test
    fun parsesNumericDates() {
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("2025-11-19"))
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("19/11/2025"))
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("19-11-2025"))
    }

    @Test
    fun parsesDatesWithTimeSuffix() {
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("19-Nov-2025 09:15 AM"))
        assertEquals(LocalDate(2025, 11, 19), ExamUtils.parseExamDateToLocalDate("19-Nov-2025T09:15:00"))
    }

    @Test
    fun rejectsInvalidDates() {
        assertNull(ExamUtils.parseExamDateToLocalDate(""))
        assertNull(ExamUtils.parseExamDateToLocalDate("garbage"))
        assertNull(ExamUtils.parseExamDateToLocalDate("31-Feb-2025"))
        assertNull(ExamUtils.parseExamDateToLocalDate("19-Nov"))
    }

    // ── Time parsing ──

    @Test
    fun parsesExamTimes() {
        assertEquals(540, ExamUtils.examTimeToMinutes("09:00 AM"))
        assertEquals(555, ExamUtils.examTimeToMinutes("09:15 AM - 12:30 PM"))
        assertEquals(720, ExamUtils.examTimeToMinutes("12:00 PM"))
        assertEquals(30, ExamUtils.examTimeToMinutes("12:30 AM"))
        assertEquals(840, ExamUtils.examTimeToMinutes("2:00 PM"))
        assertNull(ExamUtils.examTimeToMinutes(""))
        assertNull(ExamUtils.examTimeToMinutes("no time here"))
    }

    // ── Seat location (formula outputs — web demoData seatLocation values are real
    //    VTOP data, not formula predictions, so they differ) ──

    @Test
    fun calculatesSeatLocations() {
        assertEquals("R1C1", ExamUtils.calculateSeatLocation("1", "Data Structures"))
        assertEquals("R1C2", ExamUtils.calculateSeatLocation("2", "Data Structures"))
        assertEquals("R9C2", ExamUtils.calculateSeatLocation("18", "Data Structures"))
        assertEquals("R1C3", ExamUtils.calculateSeatLocation("19", "Data Structures"))
        assertEquals("R3C5", ExamUtils.calculateSeatLocation("41", "Complex Variables and Linear Algebra"))
        assertEquals("R3C4", ExamUtils.calculateSeatLocation("24", "Operating Systems"))
    }

    @Test
    fun seatCalculationExemptsSoftSkillAndLanguageCourses() {
        assertEquals("-", ExamUtils.calculateSeatLocation("41", "Qualitative Skills Practice II"))
        assertEquals("-", ExamUtils.calculateSeatLocation("29", "French I"))
        assertEquals("-", ExamUtils.calculateSeatLocation("41", "Quantitative Aptitude"))
        assertEquals("-", ExamUtils.calculateSeatLocation("41", "German II"))
    }

    @Test
    fun seatCalculationRejectsInvalidNumbers() {
        assertEquals("-", ExamUtils.calculateSeatLocation("0", "Data Structures"))
        assertEquals("-", ExamUtils.calculateSeatLocation("abc", "Data Structures"))
        assertEquals("-", ExamUtils.calculateSeatLocation("", "Data Structures"))
    }

    // ── Display extensions ──

    @Test
    fun seatLocationDisplayPrefersApiValueAndFallsBack() {
        assertEquals("R5C3", ExamItem(seatLocation = "R5C3", seatNo = "99").seatLocationDisplay)
        assertEquals("R3C5", ExamItem(seatLocation = "-", seatNo = "41", courseTitle = "Maths").seatLocationDisplay)
        assertEquals("TBD", ExamItem(seatLocation = "", seatNo = "").seatLocationDisplay)
        assertEquals("TBD", ExamItem(seatLocation = "-", seatNo = "41", courseTitle = "French I").seatLocationDisplay)
    }

    @Test
    fun sessionDisplayMapsForenoonAfternoon() {
        assertEquals("Forenoon 1", ExamItem(examSession = "FN1").sessionDisplay)
        assertEquals("Afternoon 2", ExamItem(examSession = "AN2").sessionDisplay)
        assertEquals("Forenoon", ExamItem(examSession = "FN").sessionDisplay)
        assertEquals("WEIRD", ExamItem(examSession = "WEIRD").sessionDisplay)
        assertEquals("TBD", ExamItem(examSession = "").sessionDisplay)
    }

    // ── Day helpers ──

    private val sampleExams = listOf(
        ExamItem(courseCode = "BMAT201L", examDate = "19-Nov-2025", reportingTime = "09:00 AM"),
        ExamItem(courseCode = "BCSE202L", examDate = "23-Nov-2025", reportingTime = "09:00 AM"),
        ExamItem(courseCode = "BFRE101L", examDate = "19-Nov-2025", reportingTime = "09:00 AM")
    )

    @Test
    fun filtersExamsByDate() {
        val day = LocalDate(2025, 11, 19)
        val onDay = ExamUtils.examsForDate(sampleExams, day)
        assertEquals(2, onDay.size)
        assertTrue(ExamUtils.isExamDate(sampleExams, day))
        assertTrue(!ExamUtils.isExamDate(sampleExams, LocalDate(2025, 11, 20)))
        assertEquals(setOf(LocalDate(2025, 11, 19), LocalDate(2025, 11, 23)), ExamUtils.examDates(sampleExams))
    }

    @Test
    fun sortsExamsByDateThenTimeThenCode() {
        val shuffled = listOf(
            ExamItem(courseCode = "B", examDate = "20-Nov-2025", reportingTime = "02:00 PM"),
            ExamItem(courseCode = "A", examDate = "20-Nov-2025", reportingTime = "09:00 AM"),
            ExamItem(courseCode = "C", examDate = "19-Nov-2025", reportingTime = "09:00 AM")
        )
        val sorted = ExamUtils.sortedExamDays(shuffled)
        assertEquals(listOf("C", "A", "B"), sorted.map { it.courseCode })
    }

    // ── 24h window ──

    private val tz = TimeZone.currentSystemDefault()

    private fun instantAt(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        kotlinx.datetime.LocalDateTime(y, mo, d, h, mi, 0, 0).toInstant(tz)

    @Test
    fun nextExamWithinReturnsOnlyFutureExamsInsideWindow() {
        val now = instantAt(2025, 11, 19, 8, 0) // 08:00, exam reports at 09:00 (1h away)
        val near = ExamItem(courseCode = "NEAR", examDate = "19-Nov-2025", reportingTime = "09:00 AM")
        val far = ExamItem(courseCode = "FAR", examDate = "21-Nov-2025", reportingTime = "09:00 AM")
        val past = ExamItem(courseCode = "PAST", examDate = "18-Nov-2025", reportingTime = "09:00 AM")

        assertEquals("NEAR", ExamUtils.nextExamWithin(listOf(far, near, past), now, 24, tz)?.courseCode)
        assertEquals("FAR", ExamUtils.nextExamWithin(listOf(far, past), now, 24, tz)?.courseCode)
        assertNull(ExamUtils.nextExamWithin(listOf(past), now, 24, tz))
        assertNull(ExamUtils.nextExamWithin(emptyList(), now, 24, tz))
    }

    @Test
    fun nextExamWithinBoundaryIsExactly24Hours() {
        // Exam reports at 09:00 on 2025-11-20; now is 09:00 on 2025-11-19 → exactly 24h.
        val now = instantAt(2025, 11, 19, 9, 0)
        val exam = ExamItem(courseCode = "BOUND", examDate = "20-Nov-2025", reportingTime = "09:00 AM")
        assertEquals("BOUND", ExamUtils.nextExamWithin(listOf(exam), now, 24, tz)?.courseCode)
        // One minute past the window → excluded.
        val late = instantAt(2025, 11, 19, 9, 1)
        assertNull(ExamUtils.nextExamWithin(listOf(exam), late, 24, tz))
    }

    @Test
    fun hoursUntilExamComputesFractionalHours() {
        val now = instantAt(2025, 11, 19, 8, 30)
        val exam = ExamItem(courseCode = "X", examDate = "19-Nov-2025", reportingTime = "09:00 AM")
        val hours = ExamUtils.hoursUntilExam(exam, now, tz)
        assertEquals(0.5, hours, 1e-9)
    }

    @Test
    fun examStartFallsBackFromReportingToExamTime() {
        val withReporting = ExamItem(examDate = "19-Nov-2025", reportingTime = "09:00 AM", examTime = "09:15 AM - 12:30 PM")
        val withExamTimeOnly = ExamItem(examDate = "19-Nov-2025", examTime = "09:15 AM - 12:30 PM")
        val neither = ExamItem(examDate = "19-Nov-2025")
        assertEquals(540, ExamUtils.examStartMinutes(withReporting))
        assertEquals(555, ExamUtils.examStartMinutes(withExamTimeOnly))
        assertNull(ExamUtils.examStartMinutes(neither))
        assertEquals(instantAt(2025, 11, 19, 9, 0), ExamUtils.examStartInstant(withReporting, tz))
        assertEquals(instantAt(2025, 11, 19, 9, 15), ExamUtils.examStartInstant(withExamTimeOnly, tz))
        assertNull(ExamUtils.examStartInstant(neither, tz))
    }

    @Test
    fun examEndInstantUsesExamTimeRangeEnd() {
        val exam = ExamItem(examDate = "19-Nov-2025", examTime = "09:15 AM - 12:30 PM")
        assertEquals(instantAt(2025, 11, 19, 12, 30), ExamUtils.examEndInstant(exam, tz))
        // No parseable end time -> null
        assertNull(ExamUtils.examEndInstant(ExamItem(examDate = "19-Nov-2025", examTime = "09:15 AM"), tz))
        assertNull(ExamUtils.examEndInstant(ExamItem(examDate = "19-Nov-2025"), tz))
    }

    @Test
    fun nowIsAvailableForDefaults() {
        // Guards against Clock-dependent code paths being broken in commonTest.
        assertTrue(Clock.System.now() > Instant.fromEpochMilliseconds(0))
    }
}
