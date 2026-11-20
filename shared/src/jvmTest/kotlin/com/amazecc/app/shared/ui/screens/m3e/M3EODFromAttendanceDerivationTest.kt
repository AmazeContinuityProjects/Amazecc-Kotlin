package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.Academics
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.AttendanceDay
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Semester
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-120: OD rows must be derived exactly as ../AmazeCC's `useAttendanceState.ts` derives them —
 * only exact "On Duty" logs, lab hours only from an `L`-prefixed slot, never from a VTOP record.
 */
class M3EODFromAttendanceDerivationTest {

    private fun course(
        code: String,
        title: String,
        slot: String,
        vararg logs: Pair<String, String>,
        courseType: String = "Theory",
    ) = CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = courseType,
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
    fun onDutyRowsDeriveCorrectlyFromRealWebFixtureShape() {
        val attendance = listOf(
            course("CSE1001", "Algorithms", "A1", "2026-10-05" to "On Duty"),
            course("CSE1002", "Systems Lab", "L1+L2", "2026-10-04" to "On Duty"),
            course("CHE1001", "Chemistry", "C1", "2026-10-03" to "Present"),
        )

        val rows = deriveOdRowsFromAttendance(attendance)

        assertEquals(2, rows.size)
        val lab = rows.single { it.courseCode == "CSE1002" }
        assertEquals("2026-10-04", lab.date)
        assertEquals("LAB", lab.type)
        assertEquals(2, lab.hours)
        assertEquals("Systems Lab", lab.title)

        val theory = rows.single { it.courseCode == "CSE1001" }
        assertEquals("2026-10-05", theory.date)
        assertEquals("TH", theory.type)
        assertEquals(1, theory.hours)
        assertTrue(rows.none { it.courseCode == "CHE1001" })
    }

    @Test
    fun labSuffixCodeOnATheorySlotIsStillOneHour() {
        // The exact fixture that made Kotlin overshoot the web page: a `(L)` course code with a
        // non-lab slot. The web reads only slotName, so this is a 1h theory period.
        val rows = deriveOdRowsFromAttendance(
            listOf(course("18CSC301(L)", "Data Structures Lab", "A1", "2026-10-06" to "On Duty"))
        )
        assertEquals(1, rows.size)
        assertEquals("TH", rows.single().type)
        assertEquals(1, rows.single().hours)
    }

    @Test
    fun vtopRecordsNeverAddOrDeDupeHours() {
        val attendance = listOf(
            course("CSE3002", "Internet and Web Programming", "L31+L32", "12-Sep-2026" to "On Duty"),
            course("MAT2001", "Complex Variables", "A1", "08-Sep-2026" to "On Duty"),
        )
        val derived = deriveOdRowsFromAttendance(attendance)

        val days = mapOdRecordsToDays(vtopRes(), emptyMap(), derived)

        // Exactly the two attendance days. The VTOP record for 12-Sep claims 2h from its clock
        // range; it must not create a second entry or change the slot-derived 2h.
        assertEquals(2, days.size)
        assertEquals(2, days.single { it.date == "12-Sep-2026" }.entries.single().hours)
        assertEquals(1, days.single { it.date == "08-Sep-2026" }.entries.single().hours)
        assertEquals(1, days.single { it.date == "12-Sep-2026" }.officialReasons.size)
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
        assertTrue(fromNull.single().officialReasons.isEmpty())

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
                "05-Sep-2026" to "od",
                "06-Sep-2026" to "onduty",
                "07-Sep-2026" to "on duty",
                "08-Sep-2026" to "ON DUTY",
            )
        )
        assertTrue(deriveOdRowsFromAttendance(attendance).isEmpty())

        // A blank date on an On Duty row is skipped rather than filed under a phantom day.
        val blankDate = listOf(course("CHE1001", "Chemistry", "L1", "" to "On Duty"))
        assertTrue(deriveOdRowsFromAttendance(blankDate).isEmpty())
    }

    @Test
    fun snapshotEntryPointCountsEmbeddedLabAtTwoHours() {
        // D-125: the M3E OD host calls deriveOdRowsFromSnapshot(domain). It must read the raw
        // theory/lab rows the web counts, not the EmbeddedLinkMerge output that joined the slots.
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S1" to Semester(
                        id = "S1",
                        courses = listOf(
                            Course(
                                code = "BCSE203E",
                                title = "Database Systems",
                                type = "Embedded Theory",
                                slots = listOf("C1"),
                                attendance = Attendance(
                                    logs = listOf(AttendanceDay("2026-10-05", "On Duty")),
                                ),
                            ),
                            Course(
                                code = "BCSE203E(L)",
                                title = "Database Systems Lab",
                                type = "Embedded Lab",
                                slots = listOf("L31"),
                                attendance = Attendance(
                                    logs = listOf(AttendanceDay("2026-10-06", "On Duty")),
                                ),
                            ),
                        ).associateBy { it.code },
                    ),
                ),
            ),
        )

        val rows = deriveOdRowsFromSnapshot(snapshot)

        assertEquals(2, rows.size)
        assertEquals("TH", rows.single { it.date == "2026-10-05" }.type)
        assertEquals(1, rows.single { it.date == "2026-10-05" }.hours)
        assertEquals("LAB", rows.single { it.date == "2026-10-06" }.type)
        assertEquals(2, rows.single { it.date == "2026-10-06" }.hours)
        assertEquals(3, rows.sumOf { it.hours })
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
