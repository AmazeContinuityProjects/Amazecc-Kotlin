package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.AttendanceDay
import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** On-duty OD rows must be derived locally from synced attendance and unioned with VTOP records. */
class M3EODFromAttendanceDerivationTest {

    private fun course(
        code: String,
        title: String,
        slot: String,
        vararg logs: Pair<String, String>,
    ) = CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = "Theory",
        slotName = slot,
        slotVenue = null,
        faculty = "",
        credits = null,
        category = null,
        attendedClasses = 0,
        totalClasses = 0,
        attendancePercentage = "0",
        logs = logs.map { (date, status) -> AttendanceDay(date, status) },
    )

    private fun vtopRes() = OdDetailsRes(
        success = true,
        records = listOf(
            OdRecordItem(
                slNo = "1",
                type = "Lab",
                reason = "CSE3002",
                basis = "Practical",
                date = "12-Sep-2026",
                time = "09:00 AM - 11:00 AM",
            )
        ),
    )

    @Test
    fun onDutyRowsDeriveCorrectly() {
        val attendance = listOf(
            course("CSE3002", "Internet and Web Programming", "L31+L32", "12-Sep-2026" to "On Duty"),
            course("MAT2001", "Complex Variables", "A1", "08-Sep-2026" to "od"),
            course("PHY1001", "Physics", "B2", "01-Sep-2026" to "  ONDUTY  "),
            course("CHE1001", "Chemistry", "C1", "02-Sep-2026" to "Present"),
        )

        val rows = deriveOdRowsFromAttendance(attendance)

        assertEquals(3, rows.size)
        val lab = rows.single { it.courseCode == "CSE3002" }
        assertEquals("12-Sep-2026", lab.date)
        assertEquals("LAB", lab.type)
        assertEquals(2, lab.hours)
        assertEquals("Internet and Web Programming", lab.title)

        val theory = rows.single { it.courseCode == "MAT2001" }
        assertEquals("08-Sep-2026", theory.date)
        assertEquals("TH", theory.type)
        assertEquals(1, theory.hours)

        val caseInsensitive = rows.single { it.courseCode == "PHY1001" }
        assertEquals("01-Sep-2026", caseInsensitive.date)
        assertEquals(1, caseInsensitive.hours)
        assertTrue(rows.none { it.courseCode == "CHE1001" })
    }

    @Test
    fun derivedRowsDeDupeWithVtopRecords() {
        val attendance = listOf(
            course("CSE3002", "Internet and Web Programming", "L31+L32", "12-Sep-2026" to "On Duty"),
            course("MAT2001", "Complex Variables", "A1", "08-Sep-2026" to "On Duty"),
        )

        val days = mapOdRecordsToDays(vtopRes(), emptyMap(), deriveOdRowsFromAttendance(attendance))

        assertEquals(2, days.size)
        val dupDay = days.single { it.date == "12-Sep-2026" }
        assertEquals(1, dupDay.entries.size)
        // The real VTOP record wins the de-duplication (its title is `reason`).
        assertEquals("CSE3002", dupDay.entries.single().title)
        assertEquals(2, dupDay.entries.single().hours)
        // The non-duplicated derived row still shows up.
        assertEquals(1, days.single { it.date == "08-Sep-2026" }.entries.single().hours)
    }

    @Test
    fun emptyOrFailedVtopPayloadStillShowsDerivedRows() {
        val attendance = listOf(
            course("CSE3002", "Internet and Web Programming", "L31+L32", "12-Sep-2026" to "On Duty")
        )
        val derived = deriveOdRowsFromAttendance(attendance)

        val fromNull = mapOdRecordsToDays(null, emptyMap(), derived)
        assertEquals(1, fromNull.size)
        assertEquals("CSE3002", fromNull.single().entries.single().courseCode)

        val fromFailure = mapOdRecordsToDays(
            OdDetailsRes(success = false, error = "offline"),
            emptyMap(),
            derived,
        )
        assertEquals(1, fromFailure.size)
        assertEquals(2, fromFailure.single().entries.single().hours)

        val fromEmpty = mapOdRecordsToDays(OdDetailsRes(success = true), emptyMap(), derived)
        assertEquals(1, fromEmpty.size)
    }

    @Test
    fun irrelevantStatusesAreIgnored() {
        val attendance = listOf(
            course(
                "CHE1001", "Chemistry", "L1",
                "01-Sep-2026" to "Present",
                "02-Sep-2026" to "Absent",
                "03-Sep-2026" to "",
                "04-Sep-2026" to "partial od",
                "05-Sep-2026" to "p",
            )
        )
        assertTrue(deriveOdRowsFromAttendance(attendance).isEmpty())

        // A blank date on an On Duty row is skipped rather than filed under a phantom day.
        val blankDate = listOf(course("CHE1001", "Chemistry", "L1", "" to "On Duty"))
        assertTrue(deriveOdRowsFromAttendance(blankDate).isEmpty())
    }

    @Test
    fun annotationsOnDerivedRowsArePreserved() {
        val attendance = listOf(
            course("MAT2001", "Complex Variables", "A1", "08-Sep-2026" to "On Duty")
        )
        val days = mapOdRecordsToDays(
            null,
            mapOf("08-Sep-2026|MAT2001" to M3EODStatus.WASTED),
            deriveOdRowsFromAttendance(attendance),
        )
        assertEquals(M3EODStatus.WASTED, days.single().entries.single().status)
    }
}
