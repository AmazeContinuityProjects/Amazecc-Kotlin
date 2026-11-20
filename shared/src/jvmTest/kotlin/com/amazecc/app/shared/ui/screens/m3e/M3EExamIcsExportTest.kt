package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-040: calendar export must contain only real, dated exam data. */
class M3EExamIcsExportTest {

    private fun entry(
        id: String = "1",
        date: String = "19-Nov-2026",
        time: String = "09:15 AM - 12:30 PM",
        venue: String = "SJT 101"
    ) = M3EExamEntry(
        id = id,
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        examSeries = ExamSeries.CAT2,
        date = date,
        day = "Thursday",
        session = "FN",
        slot = "B1",
        reportingTime = "09:00 AM",
        examTime = time,
        venue = venue,
        seatNumber = "A12"
    )

    @Test
    fun noDatedExportsProducesNoCalendar() {
        assertNull(buildExamIcs(listOf(entry(date = "", time = ""))))
    }

    @Test
    fun exportUsesRealDateAndTimeOnly() {
        // Pin the real parsed values first: if the shared date/time parser moves,
        // this fails here instead of being misdiagnosed as an ICS formatting bug.
        val parsedDate = M3EExamScheduleComputations.parseDate("19-Nov-2026")
        assertEquals(LocalDate(2026, 11, 19), parsedDate)
        val (startMinutes, endMinutes) = M3EExamScheduleComputations.timeRange("09:15 AM - 12:30 PM")
        assertEquals(555, startMinutes, "09:15 AM must be 555 minutes past midnight")
        assertEquals(750, endMinutes, "12:30 PM must be 750 minutes past midnight")

        val ics = buildExamIcs(listOf(entry()))!!
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"), "ICS must open with VCALENDAR: $ics")
        assertTrue(ics.contains("DTSTART:20261119T091500"), "DTSTART must be RFC5545 basic date-time with T: $ics")
        assertTrue(ics.contains("DTEND:20261119T123000"), "DTEND must be RFC5545 basic date-time with T: $ics")
        assertTrue(ics.contains("LOCATION:SJT 101"), "Real venue must be exported: $ics")
        assertTrue(ics.endsWith("END:VCALENDAR"), "ICS must close with VCALENDAR: $ics")
    }

    @Test
    fun undatedPapersAreSkippedButDatedOnesRemain() {
        val ics = buildExamIcs(listOf(entry(id = "bad", date = "not-a-date"), entry(id = "good")))!!
        assertTrue(ics.contains("UID:good@amazecc"))
        assertEquals(false, ics.contains("UID:bad@amazecc"))
    }
}
