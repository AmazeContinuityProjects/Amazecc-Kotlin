package com.amazecc.app.shared.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The port of `AmazeCC/src/lib/examSchedule.ts`.
 *
 * Two behaviours here are the point of the port and are asserted directly: the paper's start is *its own* time rather than the reporting time (open decision 7, also pinned in `ExamUtilsTest`),
 * and [ExamSchedule.classifyExamState] closes a paper by the clock rather than at midnight (open
 * decision 8).
 */
class ExamScheduleTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun instantAt(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime(y, mo, d, h, mi).toInstant(tz)

    private val nov19 = LocalDate(2025, 11, 19)

    private fun exam(
        date: String = "19-Nov-2025",
        time: String = "",
        reportingTime: String = "",
        courseCode: String = "BMA101",
    ) = Exam(courseCode = courseCode, date = date, time = time, reportingTime = reportingTime)

    @Test
    fun `exam window takes both ends from the exam time`() {
        val w = ExamSchedule.examWindow(exam(time = "09:15 AM - 12:30 PM"), "FAT", nov19)
        assertEquals(instantAt(2025, 11, 19, 9, 15), w.startAt)
        assertEquals(instantAt(2025, 11, 19, 12, 30), w.endAt)
    }

    @Test
    fun `a reporting-only paper takes its length from the series`() {
        // FAT is 3h30 (210 minutes), CAT 1h45 (105).
        val fat = ExamSchedule.examWindow(exam(reportingTime = "09:00 AM"), "FAT", nov19)
        assertEquals(instantAt(2025, 11, 19, 9, 0), fat.startAt)
        assertEquals(instantAt(2025, 11, 19, 12, 30), fat.endAt)

        val cat = ExamSchedule.examWindow(exam(reportingTime = "09:00 AM"), "CAT1", nov19)
        assertEquals(instantAt(2025, 11, 19, 9, 0), cat.startAt)
        assertEquals(instantAt(2025, 11, 19, 10, 45), cat.endAt)
    }

    @Test
    fun `unknown series, missing date and missing time all fall back to day-level reasoning`() {
        val noSeries = ExamSchedule.examWindow(exam(reportingTime = "09:00 AM"), "XAM", nov19)
        assertEquals(instantAt(2025, 11, 19, 9, 0), noSeries.startAt)
        assertNull(noSeries.endAt)

        assertNull(ExamSchedule.examWindow(exam(time = "09:15 AM - 12:30 PM"), "FAT", null).startAt)
        assertNull(ExamSchedule.examWindow(exam(time = "09:15 AM - 12:30 PM"), "FAT", null).endAt)

        val noTime = ExamSchedule.examWindow(exam(), "FAT", nov19)
        assertNull(noTime.startAt)
        assertNull(noTime.endAt)
    }

    @Test
    fun `classifyExamState closes a paper by the clock, not at midnight`() {
        val end = instantAt(2025, 11, 19, 12, 30)
        val at3pm = instantAt(2025, 11, 19, 15, 0)
        val at11am = instantAt(2025, 11, 19, 11, 0)

        // The whole point: 3 PM on the paper's own day is past, because the window shut at 12:30.
        assertEquals(ExamSchedule.ExamState.PAST, ExamSchedule.classifyExamState(nov19, end, at3pm))
        // Still under way at 11.
        assertEquals(ExamSchedule.ExamState.TODAY, ExamSchedule.classifyExamState(nov19, end, at11am))

        // No end time: only the calendar can decide.
        assertEquals(ExamSchedule.ExamState.TODAY, ExamSchedule.classifyExamState(nov19, null, instantAt(2025, 11, 19, 23, 59)))
        assertEquals(ExamSchedule.ExamState.PAST, ExamSchedule.classifyExamState(nov19, null, instantAt(2025, 11, 20, 0, 1)))
        assertEquals(ExamSchedule.ExamState.UPCOMING, ExamSchedule.classifyExamState(nov19, null, instantAt(2025, 11, 18, 12, 0)))

        // No date at all can be neither past nor today.
        assertEquals(ExamSchedule.ExamState.UPCOMING, ExamSchedule.classifyExamState(null, null, at11am))
    }

    @Test
    fun `stateOf reports a finished same-day paper as past`() {
        val e = exam(time = "09:15 AM - 12:30 PM")
        assertEquals(ExamSchedule.ExamState.PAST, ExamSchedule.stateOf(e, instantAt(2025, 11, 19, 15, 0)))
        assertEquals(ExamSchedule.ExamState.TODAY, ExamSchedule.stateOf(e, instantAt(2025, 11, 19, 11, 0)))
        assertEquals(ExamSchedule.ExamState.UPCOMING, ExamSchedule.stateOf(e, instantAt(2025, 11, 18, 11, 0)))
    }

    @Test
    fun `buildExamRows orders by real start time, not by the printed clock string`() {
        val afternoon = Exam(courseCode = "BZ", date = "19-Nov-2025", time = "02:00 PM - 05:00 PM")
        val morning = Exam(courseCode = "BA", date = "19-Nov-2025", time = "09:15 AM - 12:30 PM")

        // Afternoon listed first on purpose: as text "02:00 PM" sorts before "09:15 AM".
        val rows = ExamSchedule.buildExamRows(
            mapOf("FAT" to listOf(afternoon, morning)),
            now = instantAt(2025, 11, 19, 11, 0),
        )

        assertEquals(listOf("BA", "BZ"), rows.map { it.exam.courseCode })
        assertEquals(listOf("FAT-BA-19-Nov-2025-1", "FAT-BZ-19-Nov-2025-0"), rows.map { it.key })
        assertEquals(listOf(ExamSchedule.ExamState.TODAY, ExamSchedule.ExamState.TODAY), rows.map { it.state })
    }

    @Test
    fun `buildExamRows breaks a tie on course code`() {
        val b = Exam(courseCode = "BZ", date = "19-Nov-2025", time = "09:15 AM - 12:30 PM")
        val a = Exam(courseCode = "BA", date = "19-Nov-2025", time = "09:15 AM - 12:30 PM")
        val rows = ExamSchedule.buildExamRows(
            mapOf("CAT1" to listOf(b, a)),
            now = instantAt(2025, 11, 18, 11, 0),
        )
        assertEquals(listOf("BA", "BZ"), rows.map { it.exam.courseCode })
    }
}
