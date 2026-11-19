package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamStatus
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless checks for the exam-schedule helpers ported from the web app's `lib/examSchedule.ts`.
 * Every instant is pinned to UTC so the assertions never depend on the host machine's timezone.
 */
class M3EExamScheduleComputationsTest {

    private val utc = TimeZone.UTC

    private fun payload() = ExamScheduleRes(
        rawScheduleUpper = mapOf(
            "CAT II" to listOf(
                ExamItem(
                    courseCode = "CSE3002",
                    courseTitle = "Internet and Web Programming",
                    slot = "B1+TB1",
                    examDate = "19-Nov-2025",
                    examSession = "FN",
                    reportingTime = "09:00 AM",
                    examTime = "09:15 AM - 11:00 AM",
                    venue = "SJT 412",
                    seatLocation = "-",
                    seatNo = "18"
                )
            ),
            "FAT" to listOf(
                ExamItem(
                    courseCode = "MAT2001",
                    courseTitle = "Complex Variables",
                    slot = "A1",
                    examDate = "25-Nov-2025",
                    examSession = "FN",
                    reportingTime = "09:00 AM",
                    examTime = "09:15 AM - 12:45 PM",
                    venue = "TT 301",
                    seatLocation = "R1C5",
                    seatNo = "5"
                )
            )
        )
    )

    // ── Date / time parsing ──────────────────────────────────────────────────────────

    @Test
    fun parsesVtopDateFormatsAndRejectsGarbage() {
        assertEquals(LocalDate(2025, 11, 19), M3EExamScheduleComputations.parseDate("19-Nov-2025"))
        assertEquals(LocalDate(2025, 11, 19), M3EExamScheduleComputations.parseDate("19/11/2025"))
        assertEquals(LocalDate(2025, 11, 19), M3EExamScheduleComputations.parseDate("19 Nov 2025"))
        assertNull(M3EExamScheduleComputations.parseDate(""))
        assertNull(M3EExamScheduleComputations.parseDate("not a date"))
    }

    @Test
    fun parsesClockTimesAndRangesNumerically() {
        assertEquals(555, M3EExamScheduleComputations.clockMinutes("09:15 AM"))
        assertEquals(0, M3EExamScheduleComputations.clockMinutes("12:00 AM"))
        assertEquals(750, M3EExamScheduleComputations.clockMinutes("12:30 PM"))
        assertNull(M3EExamScheduleComputations.clockMinutes(""))
        assertEquals(555 to 750, M3EExamScheduleComputations.timeRange("09:15 AM - 12:30 PM"))
    }

    // ── Series classification ────────────────────────────────────────────────────────

    @Test
    fun seriesForRecognisesAllVtopSpellingsAndFallsBackToAll() {
        assertEquals(ExamSeries.CAT1, M3EExamScheduleComputations.seriesFor("CAT I"))
        assertEquals(ExamSeries.CAT1, M3EExamScheduleComputations.seriesFor("CAT-1"))
        assertEquals(ExamSeries.CAT1, M3EExamScheduleComputations.seriesFor("CAT1"))
        assertEquals(ExamSeries.CAT2, M3EExamScheduleComputations.seriesFor("CAT II"))
        assertEquals(ExamSeries.CAT2, M3EExamScheduleComputations.seriesFor("CAT - II"))
        assertEquals(ExamSeries.CAT2, M3EExamScheduleComputations.seriesFor("CAT2"))
        assertEquals(ExamSeries.FAT, M3EExamScheduleComputations.seriesFor("FAT"))
        assertEquals(ExamSeries.FAT, M3EExamScheduleComputations.seriesFor("End Semester"))
        assertEquals(ExamSeries.ALL, M3EExamScheduleComputations.seriesFor("something unrecognised"))
    }

    @Test
    fun durationFallsBackToSeriesLengthOnlyWhenNoEndTimeIsGiven() {
        assertEquals(105, M3EExamScheduleComputations.durationMinutes("CAT II"))
        assertEquals(210, M3EExamScheduleComputations.durationMinutes("FAT"))
        assertEquals(0, M3EExamScheduleComputations.durationMinutes("Quiz"))

        val reportingOnly = ExamItem(reportingTime = "09:00 AM", examTime = "")
        val (start, end) = M3EExamScheduleComputations.examWindow(
            reportingOnly, "CAT II", LocalDate(2025, 11, 19)
        )
        assertEquals(LocalDateTime(2025, 11, 19, 9, 0), start)
        // Reporting time + CAT 105 min = 10:45.
        assertEquals(LocalDateTime(2025, 11, 19, 10, 45), end)
    }

    // ── Status / countdown ───────────────────────────────────────────────────────────

    @Test
    fun classifyUsesEndTimeBeforeCalendarDay() {
        val date = LocalDate(2025, 11, 19)
        val end = LocalDateTime(2025, 11, 19, 11, 0)
        assertEquals(
            ExamStatus.TODAY,
            M3EExamScheduleComputations.classify(date, end, LocalDateTime(2025, 11, 19, 4, 0))
        )
        // A paper that ended at 11:00 is completed by noon, not at midnight.
        assertEquals(
            ExamStatus.COMPLETED,
            M3EExamScheduleComputations.classify(date, end, LocalDateTime(2025, 11, 19, 12, 0))
        )
        assertEquals(
            ExamStatus.COMPLETED,
            M3EExamScheduleComputations.classify(LocalDate(2025, 11, 18), null, LocalDateTime(2025, 11, 19, 4, 0))
        )
        assertEquals(
            ExamStatus.UPCOMING,
            M3EExamScheduleComputations.classify(LocalDate(2025, 11, 25), null, LocalDateTime(2025, 11, 19, 4, 0))
        )
    }

    @Test
    fun countdownLabelsTodayTomorrowInDaysAndCompleted() {
        val date = LocalDate(2025, 11, 19)
        val start = LocalDateTime(2025, 11, 19, 9, 15)
        val end = LocalDateTime(2025, 11, 19, 11, 0)
        assertEquals(
            "Today",
            M3EExamScheduleComputations.countdownLabel(
                ExamStatus.TODAY, date, start, end, LocalDateTime(2025, 11, 19, 4, 0)
            )
        )
        assertEquals(
            "Now",
            M3EExamScheduleComputations.countdownLabel(
                ExamStatus.TODAY, date, start, end, LocalDateTime(2025, 11, 19, 10, 0)
            )
        )
        assertEquals(
            "Tomorrow",
            M3EExamScheduleComputations.countdownLabel(
                ExamStatus.UPCOMING, LocalDate(2025, 11, 20), null, null, LocalDateTime(2025, 11, 19, 4, 0)
            )
        )
        assertEquals(
            "In 6 days",
            M3EExamScheduleComputations.countdownLabel(
                ExamStatus.UPCOMING, LocalDate(2025, 11, 25), null, null, LocalDateTime(2025, 11, 19, 4, 0)
            )
        )
        assertEquals(
            "Completed",
            M3EExamScheduleComputations.countdownLabel(
                ExamStatus.COMPLETED, date, start, end, LocalDateTime(2025, 11, 19, 12, 0)
            )
        )
        assertEquals(6, M3EExamScheduleComputations.daysUntil(LocalDate(2025, 11, 25), LocalDate(2025, 11, 19)))
        assertEquals(0, M3EExamScheduleComputations.daysUntil(LocalDate(2025, 11, 18), LocalDate(2025, 11, 19)))
    }

    // ── Seat / venue ─────────────────────────────────────────────────────────────────

    @Test
    fun derivesSeatLocationWhenVtopSendsADash() {
        assertEquals("R1C1", M3EExamScheduleComputations.deriveSeatLocation("1", "-", "Programming"))
        assertEquals("R9C2", M3EExamScheduleComputations.deriveSeatLocation("18", "-", "Programming"))
        assertEquals("R1C3", M3EExamScheduleComputations.deriveSeatLocation("19", "", "Programming"))
        // An explicit VTOP location always wins over the derivation.
        assertEquals("Block A", M3EExamScheduleComputations.deriveSeatLocation("18", "Block A", "Programming"))
        // Languages have no allocated hall position.
        assertEquals("TBD", M3EExamScheduleComputations.deriveSeatLocation("4", "-", "French"))
        assertEquals("TBD", M3EExamScheduleComputations.deriveSeatLocation("B-14", "-", "Programming"))
    }

    @Test
    fun venueDashBecomesTba() {
        assertEquals("TBA", M3EExamScheduleComputations.venueDisplay("-"))
        assertEquals("TBA", M3EExamScheduleComputations.venueDisplay(""))
        assertEquals("SJT 412", M3EExamScheduleComputations.venueDisplay(" SJT 412 "))
    }

    // ── Build / group / filter ───────────────────────────────────────────────────────

    @Test
    fun buildEntriesCarriesRealFieldsDerivedStatusAndSeat() {
        val entries = M3EExamScheduleComputations.buildEntries(
            payload(), now = Instant.parse("2025-11-19T04:00:00Z"), timeZone = utc
        )
        assertEquals(2, entries.size)

        val cat = entries.first { it.courseCode == "CSE3002" }
        assertEquals(ExamSeries.CAT2, cat.examSeries)
        assertEquals("B1+TB1", cat.slot)
        assertEquals("FN", cat.session)
        assertEquals("09:00 AM", cat.reportingTime)
        assertEquals("09:15 AM - 11:00 AM", cat.examTime)
        assertEquals("19-Nov-2025", cat.date)
        assertEquals("Wednesday", cat.day)
        assertEquals("18", cat.seatNumber)
        assertEquals("R9C2", cat.seatLocation)
        assertEquals("SJT 412", cat.venueDisplay)
        assertEquals(ExamStatus.TODAY, cat.status)
        assertEquals("Today", cat.countdownLabel)
        // Reporting and exam time stay distinct fields; neither is flattened into the other.
        assertTrue(cat.reportingTime != cat.examTime)

        val fat = entries.first { it.courseCode == "MAT2001" }
        assertEquals(ExamSeries.FAT, fat.examSeries)
        assertEquals("R1C5", fat.seatLocation)
        assertEquals(ExamStatus.UPCOMING, fat.status)
        assertEquals("In 6 days", fat.countdownLabel)
    }

    @Test
    fun statusTimeOfDayChangesTheSamePayloadFromTodayToCompleted() {
        val entries = M3EExamScheduleComputations.buildEntries(
            payload(), now = Instant.parse("2025-11-19T12:00:00Z"), timeZone = utc
        )
        val cat = entries.first { it.courseCode == "CSE3002" }
        assertEquals(ExamStatus.COMPLETED, cat.status)
        assertEquals("Completed", cat.countdownLabel)
    }

    @Test
    fun filteringGroupingCountsAndNextExamFollowTheSeries() {
        val entries = M3EExamScheduleComputations.buildEntries(
            payload(), now = Instant.parse("2025-11-19T04:00:00Z"), timeZone = utc
        )

        assertEquals(1, M3EExamScheduleComputations.filterBySeries(entries, ExamSeries.CAT2).size)
        assertEquals(1, M3EExamScheduleComputations.filterBySeries(entries, ExamSeries.FAT).size)
        assertTrue(M3EExamScheduleComputations.filterBySeries(entries, ExamSeries.CAT1).isEmpty())
        assertEquals(2, M3EExamScheduleComputations.filterBySeries(entries, ExamSeries.ALL).size)

        val groups = M3EExamScheduleComputations.groupBySeries(entries)
        assertEquals(listOf(ExamSeries.CAT2, ExamSeries.FAT), groups.map { it.series })
        assertEquals("CSE3002", groups.first().entries.single().courseCode)

        val counts = M3EExamScheduleComputations.statusCounts(entries)
        assertEquals(1, counts.today)
        assertEquals(1, counts.upcoming)
        assertEquals(0, counts.completed)

        assertEquals("CSE3002", M3EExamScheduleComputations.nextUpcoming(entries)?.courseCode)
    }

    @Test
    fun availableSeriesIsDrivenByTheScheduleKeys() {
        val options = M3EExamScheduleComputations.availableSeries(listOf("CAT II", "FAT"))
        assertEquals(listOf(ExamSeries.ALL, ExamSeries.CAT2, ExamSeries.FAT), options)
        assertEquals(
            listOf(ExamSeries.ALL, ExamSeries.FAT),
            M3EExamScheduleComputations.availableSeries(listOf("End Semester"))
        )
        // Unknown keys never add a phantom series.
        assertEquals(
            listOf(ExamSeries.ALL),
            M3EExamScheduleComputations.availableSeries(listOf("something unrecognised"))
        )
    }
}
