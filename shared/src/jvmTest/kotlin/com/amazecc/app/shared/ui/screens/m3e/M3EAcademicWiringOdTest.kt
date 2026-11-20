package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.AttendanceDay
import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-120: VTOP OD-details are real data but they explain a day; they are not a second hour source.
 * The web OD Hours History page counts only attendance logs, so this test locks that boundary down.
 */
class M3EAcademicWiringOdTest {

    private fun odRes() = OdDetailsRes(
        success = true,
        semesterId = "CH20262701",
        totalCount = 3,
        records = listOf(
            OdRecordItem(
                slNo = "1",
                type = "Lab",
                reason = "CSE3002",
                basis = "Practical",
                date = "12-Sep-2026",
                time = "09:00 AM - 11:00 AM",
                remarks = "On duty",
            ),
            OdRecordItem(
                slNo = "2",
                type = "OD",
                reason = "MAT2001",
                basis = "Theory",
                date = "08-Sep-2026 to 12-Sep-2026",
                time = "2 hours",
            ),
        )
    )

    private fun attendance() = listOf(
        CourseAttendance(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            courseType = "Theory",
            slotName = "L31+L32",
            slotVenue = null,
            faculty = "",
            credits = null,
            category = null,
            attendedClasses = 0,
            totalClasses = 0,
            attendancePercentage = "0",
            logs = listOf(AttendanceDay("12-Sep-2026", "On Duty")),
        )
    )

    @Test
    fun vtopTimeHelpersAreInspectionOnly() {
        assertEquals(2, odHoursFrom("09:00 AM - 11:00 AM"))
        assertEquals(0, odHoursFrom("unparseable"))
        assertEquals("LAB", odTypeFor("Lab", "Practical"))
        assertEquals("TH", odTypeFor("OD", "Theory"))
    }

    @Test
    fun vtopOnlyPayloadProducesNoCountedDays() {
        val days = mapOdRecordsToDays(odRes(), emptyMap())
        assertTrue(days.isEmpty())
    }

    @Test
    fun vtopRecordsAnnotateAttendanceDaysWithoutChangingHours() {
        val tracker = mapOf("12-Sep-2026|CSE3002" to M3EODStatus.WASTED)
        val derived = deriveOdRowsFromAttendance(attendance())
        val days = mapOdRecordsToDays(odRes(), tracker, derived)

        val day = days.single()
        assertEquals("12-Sep-2026", day.date)
        assertEquals(2, day.totalHours)
        assertEquals("LAB", day.entries.single().type)
        assertEquals(M3EODStatus.WASTED, day.entries.single().status)
        // Both VTOP records cover the day: one exact, one date range. Neither adds hours.
        assertEquals(2, day.officialReasons.size)
        assertTrue(day.officialReasons.any { it.reason == "CSE3002" && it.remarks == "On duty" })
    }

    @Test
    fun failureOrEmptyPayloadYieldsNoFabricatedRows() {
        assertTrue(mapOdRecordsToDays(null, emptyMap()).isEmpty())
        assertTrue(
            mapOdRecordsToDays(
                OdDetailsRes(success = false, error = "offline"),
                emptyMap(),
            ).isEmpty()
        )
        assertTrue(mapOdRecordsToDays(OdDetailsRes(success = true), emptyMap()).isEmpty())
    }
}
