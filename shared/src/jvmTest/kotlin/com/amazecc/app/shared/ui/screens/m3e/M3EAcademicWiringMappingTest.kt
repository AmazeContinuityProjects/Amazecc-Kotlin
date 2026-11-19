package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.Academics
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Grade
import com.amazecc.app.shared.domain.Semester
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3ETransportFilter
import com.amazecc.app.shared.model.BusRoute
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.TransportDataRes
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless checks for the pure mapping helpers that feed the four ACADEMIC M3E screens.
 * These lock the "real data, no cached approximations" contract without running Gradle UI tests.
 */
class M3EAcademicWiringMappingTest {

    // ---------------------------------------------------------------------
    // Exam schedule
    // ---------------------------------------------------------------------

    private fun examRes() = ExamScheduleRes(
        rawScheduleUpper = mapOf(
            "CAT II" to listOf(
                ExamItem(
                    courseCode = "CSE3002",
                    courseTitle = "Internet and Web Programming",
                    slot = "B1+TB1",
                    examDate = "19-Nov-2026",
                    examSession = "AN",
                    reportingTime = "01:30 PM",
                    examTime = "02:00 PM - 03:45 PM",
                    venue = "SJT 412",
                    seatLocation = "Block A",
                    seatNo = "B-14"
                )
            ),
            "FAT" to listOf(
                ExamItem(
                    courseCode = "MAT2001",
                    courseTitle = "Complex Variables",
                    slot = "A1",
                    examDate = "25-Nov-2026",
                    examSession = "FN",
                    reportingTime = "09:00 AM",
                    examTime = "09:30 AM - 01:00 PM",
                    venue = "TT 301",
                    seatNo = "C-07"
                )
            )
        )
    )

    @Test
    fun examSeriesForRecognisesAllVtopSpellings() {
        assertEquals(ExamSeries.CAT1, examSeriesFor("CAT I"))
        assertEquals(ExamSeries.CAT1, examSeriesFor("CAT-1"))
        assertEquals(ExamSeries.CAT1, examSeriesFor("CAT1"))
        assertEquals(ExamSeries.CAT2, examSeriesFor("CAT II"))
        assertEquals(ExamSeries.CAT2, examSeriesFor("CAT - II"))
        assertEquals(ExamSeries.CAT2, examSeriesFor("CAT2"))
        assertEquals(ExamSeries.FAT, examSeriesFor("FAT"))
        assertEquals(ExamSeries.FAT, examSeriesFor("End Semester"))
        assertEquals(ExamSeries.ALL, examSeriesFor("something unrecognised"))
    }

    @Test
    fun examEntriesCarryRealSeriesSlotDayAndTimes() {
        val entries = mapExamScheduleResToEntries(
            examRes(),
            now = Instant.parse("2026-11-19T10:00:00Z")
        )

        assertEquals(2, entries.size)
        val cat = entries.first { it.courseCode == "CSE3002" }
        assertEquals(ExamSeries.CAT2, cat.examSeries)
        assertEquals("B1+TB1", cat.slot)
        assertEquals("AN", cat.session)
        assertEquals("01:30 PM", cat.reportingTime)
        assertEquals("02:00 PM - 03:45 PM", cat.examTime)
        assertEquals("19-Nov-2026", cat.date)
        assertEquals("Thursday", cat.day)
        assertEquals("B-14 / Block A", cat.seatNumber)
        assertTrue(cat.daysRemaining in 0..1) // exact value depends on the host timezone

        val fat = entries.first { it.courseCode == "MAT2001" }
        assertEquals(ExamSeries.FAT, fat.examSeries)
        assertEquals("A1", fat.slot)
        assertEquals("09:00 AM", fat.reportingTime)
        assertEquals("09:30 AM - 01:00 PM", fat.examTime)
        // Reporting and exam time are distinct real fields, never flattened into one.
        assertTrue(cat.reportingTime != cat.examTime)
    }

    @Test
    fun examSeriesFilterActuallySeparatesCatAndFat() {
        val entries = mapExamScheduleResToEntries(
            examRes(),
            now = Instant.parse("2026-11-19T10:00:00Z")
        )
        fun filtered(series: ExamSeries) =
            if (series == ExamSeries.ALL) entries else entries.filter { it.examSeries == series }

        assertEquals(2, filtered(ExamSeries.ALL).size)
        assertEquals(1, filtered(ExamSeries.CAT2).size)
        assertEquals("CSE3002", filtered(ExamSeries.CAT2).single().courseCode)
        assertEquals(1, filtered(ExamSeries.FAT).size)
        assertEquals("MAT2001", filtered(ExamSeries.FAT).single().courseCode)
        assertTrue(filtered(ExamSeries.CAT1).isEmpty())
    }

    // ---------------------------------------------------------------------
    // Grades history
    // ---------------------------------------------------------------------

    private fun gradeSnapshot(): DomainSnapshot {
        fun course(code: String, letter: String?, credits: String) = Course(
            code = code,
            title = code,
            type = "Theory Only",
            credits = credits,
            grade = if (letter == null) null else Grade(letter = letter, grandTotal = "85")
        )
        val sem1 = Semester(
            id = "CH20252601",
            gpa = "9.00",
            courses = mapOf(
                "CSE1001" to course("CSE1001", "S", "4"),
                "MAT1001" to course("MAT1001", "A", "3")
            )
        )
        val sem2 = Semester(
            id = "CH20252605",
            gpa = "8.00",
            courses = mapOf(
                "CSE2001" to course("CSE2001", "A", "4"),
                "PHY2001" to course("PHY2001", "F", "2")
            )
        )
        return DomainSnapshot(
            academics = Academics(
                selectedSemesterId = "CH20252605",
                semesters = mapOf(sem1.id to sem1, sem2.id to sem2)
            )
        )
    }

    @Test
    fun gradesUseRealCreditsAndRealGradeCounts() {
        val state = buildGradesHistoryState(gradeSnapshot())

        assertEquals(2, state.semesters.size)
        val first = state.semesters.first { it.semesterId == "CH20252601" }
        // 4 + 3 real credits from Course.credits, not 2 courses * hardcoded 3.
        assertEquals(7, first.creditsEarned)
        assertEquals(4, first.courses.first { it.courseCode == "CSE1001" }.credits)
        assertEquals(3, first.courses.first { it.courseCode == "MAT1001" }.credits)
        assertEquals(10, first.courses.first { it.courseCode == "CSE1001" }.gradePoints)

        // S: 1, A: 2, B: 0 - derived from the real letters.
        assertEquals(1, state.sGradesCount)
        assertEquals(2, state.aGradesCount)
        assertEquals(0, state.bGradesCount)
    }

    @Test
    fun gradesDistinguishEarnedFromAttemptedAndDeriveCumulativeGpa() {
        val state = buildGradesHistoryState(gradeSnapshot())

        // Semester 2 attempted 4 + 2 = 6, but the F means only 4 were earned.
        val second = state.semesters.first { it.semesterId == "CH20252605" }
        assertEquals(4, second.creditsEarned)
        assertEquals(6, state.totalCreditsAttempted - state.semesters.first().creditsEarned)
        assertTrue(state.totalCreditsAttempted > state.totalCreditsEarned)

        // CGPA is a running mean of real SGPA values (9.0 and 8.0 -> 8.5), not the 9.15 placeholder,
        // and cgpaAtEnd is distinct from the current semester's SGPA.
        assertEquals(8.5, state.cumulativeGpa, 0.001)
        assertEquals(8.5, second.cgpaAtEnd, 0.001)
        assertEquals(8.0, second.sgpa, 0.001)
    }

    @Test
    fun gradesHaveNoHardcodedFallbackWhenDataIsEmpty() {
        val empty = DomainSnapshot(academics = Academics(selectedSemesterId = "S1"))
        val state = buildGradesHistoryState(empty, isLoading = true)

        assertEquals(0.0, state.cumulativeGpa, 0.001)
        assertEquals(0, state.totalCreditsEarned)
        assertEquals(0, state.totalCreditsAttempted)
        assertEquals(0, state.sGradesCount)
        assertTrue(state.semesters.isEmpty())
        assertTrue(state.isLoading)
    }

    // ---------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------

    private fun busRes() = BusesRes(
        buses = listOf(
            BusRoute(
                id = "R12",
                type = "AC",
                route = "Tambaram - Campus",
                driverName = "Murugan",
                driverPhone = "98765",
                whatsappGroup = "https://chat.whatsapp.com/x",
                busLocation = "Main Gate",
                stops = listOf(
                    com.amazecc.app.shared.model.BusStop(1, "Tambaram", "06:45 AM"),
                    com.amazecc.app.shared.model.BusStop(2, "Campus", "05:10 PM")
                )
            ),
            BusRoute(
                id = "R24",
                type = "Non-AC",
                route = "Katpadi - Campus",
                driverName = "Selvaraj",
                driverPhone = "98766",
                whatsappGroup = "",
                busLocation = "North Gate",
                stops = listOf(
                    com.amazecc.app.shared.model.BusStop(1, "Katpadi", "07:15 AM"),
                    com.amazecc.app.shared.model.BusStop(2, "Campus", "06:20 PM")
                )
            ),
            BusRoute(
                id = "R05",
                type = "AC",
                route = "Morning Only",
                driverName = "Raja",
                driverPhone = "98767",
                whatsappGroup = "",
                busLocation = "",
                stops = listOf(com.amazecc.app.shared.model.BusStop(1, "Stop", "07:00 AM"))
            )
        )
    )

    @Test
    fun busRoutesMapRealFieldsWithoutFabricatedIds() {
        val routes = mapBusesToRoutes(busRes())
        assertEquals(3, routes.size)
        // Non-numeric ids fall back to a stable index, not a made-up constant.
        assertEquals(1, routes[0].id)
        assertEquals("Tambaram - Campus", routes[0].routeNumber)
        assertTrue(routes[0].isAc)
        assertEquals("Murugan", routes[0].driverName)
        assertEquals("06:45 AM", routes[0].stops[0].pickupTime)
        assertNull(routes[1].whatsappGroup)
        assertFalse(routes[1].isAc)
    }

    @Test
    fun clockHourParsingHandlesAllVtopFormats() {
        assertEquals(17, parseClockHour("05:10 PM"))
        assertEquals(18, parseClockHour("6:00 PM"))
        assertEquals(6, parseClockHour("06:45 AM"))
        assertEquals(17, parseClockHour("17:30"))
        assertNull(parseClockHour(""))
    }

    @Test
    fun eveningFiltersDistinguishTheTwoBatchesFromRealStops() {
        val routes = mapBusesToRoutes(busRes())
        val fivePm = routes.filter { matchesTransportFilter(it, M3ETransportFilter.EVENING_5PM) }
        val sixPm = routes.filter { matchesTransportFilter(it, M3ETransportFilter.EVENING_6PM) }

        assertEquals(listOf("Tambaram - Campus"), fivePm.map { it.routeNumber })
        assertEquals(1, sixPm.size)
        assertEquals("Katpadi - Campus", sixPm.single().routeNumber)
        // Morning-only route belongs to neither evening batch.
        assertNull(eveningBatchFor(routes.first { it.routeNumber == "Morning Only" }))
        assertTrue(matchesTransportFilter(routes.first(), M3ETransportFilter.AC))
        assertTrue(matchesTransportFilter(routes[1], M3ETransportFilter.NON_AC))
    }

    @Test
    fun transportRegistrationMapsRealApiPayload() {
        assertNull(transportRegistrationFrom(null))
        val registration = transportRegistrationFrom(
            TransportDataRes(
                hasRegistration = true,
                registerNumber = "21BCE1001",
                name = "A Student",
                branch = "CSE",
                routeSelected = "Tambaram - Campus",
                busRouteId = "R12",
                qrCode = "base64..."
            )
        )
        assertNotNull(registration)
        assertTrue(registration.hasRegistration)
        assertEquals("Tambaram - Campus", registration.routeSelected)
        assertEquals("R12", registration.busRouteId)
        assertEquals("21BCE1001", registration.registerNumber)
    }
}
