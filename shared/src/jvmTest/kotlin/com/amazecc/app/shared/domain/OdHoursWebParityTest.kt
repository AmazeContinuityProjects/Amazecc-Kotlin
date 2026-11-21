package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-120 web-parity fixtures for OD hours.
 *
 * These are the same shapes ../AmazeCC feeds through `useAttendanceState.ts`: `slotName` is the
 * raw VTOP slot string, a day is one `viewLink` entry with a `status`, and the OD Hours History
 * page's "Total OD" is the sum of per-day totals. Kotlin must land on the identical number.
 */
class OdHoursWebParityTest {

    private fun course(
        code: String,
        title: String,
        slot: String,
        vararg logs: Pair<String, String>,
    ) = CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = if (slot.startsWith("L")) "Lab" else "Theory",
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

    @Test
    fun webUseAttendanceStateFixtureCountsTheoryOneAndLabTwo() {
        // Exact inputs/expected values from ../AmazeCC/src/__tests__/useAttendanceState.test.tsx.
        val days = deriveOdHoursDays(
            listOf(
                course("CSE1001", "Algorithms", "A1", "2026-10-05" to "On Duty"),
                course("CSE1002", "Systems Lab", "L1+L2", "2026-10-04" to "On Duty"),
            )
        )

        assertEquals(2, days.size)
        assertEquals("2026-10-04", days.first().date) // web sorts oldest first
        assertEquals(2, days.first().total)
        assertEquals("LAB", days.first().entries.single().type)
        assertEquals("2026-10-05", days.last().date)
        assertEquals(1, days.last().total)
        assertEquals("TH", days.last().entries.single().type)
        assertEquals(3, totalOdHours(days))
    }

    @Test
    fun labSuffixCodeOnTheorySlotIsOneHourNotTwo() {
        // `18CSC301(L)` used to be read as a lab by the broad Kotlin `isLabCourse` test, adding an
        // extra hour per OD log. The web reads only the slot prefix, so A1 stays a 1h theory period.
        val days = deriveOdHoursDays(
            listOf(
                course("18CSC301(L)", "Data Structures", "A1", "2026-10-06" to "On Duty"),
                course("18CSC301L", "Data Structures Lab", "L31", "2026-10-07" to "On Duty"),
            )
        )
        assertEquals(1, days.single { it.date == "2026-10-06" }.total)
        assertEquals("TH", days.single { it.date == "2026-10-06" }.entries.single().type)
        assertEquals(2, days.single { it.date == "2026-10-07" }.total)
        assertEquals("LAB", days.single { it.date == "2026-10-07" }.entries.single().type)
        assertEquals(3, totalOdHours(days))
    }

    @Test
    fun duplicateSlotsAndSeveralLogsPerDayAreAllCounted() {
        // The web groups by date but does NOT de-duplicate by slot: every On Duty viewLink entry
        // becomes an entry, even when two courses share a slot or one course has two logs that day.
        val days = deriveOdHoursDays(
            listOf(
                course("CSE1001", "Algorithms", "A1", "2026-10-05" to "On Duty"),
                course("CSE1003", "Discrete Maths", "A1", "2026-10-05" to "On Duty"),
                course("CSE1004", "Economics", "A1", "2026-10-05" to "On Duty", "2026-10-05" to "On Duty"),
            )
        )
        assertEquals(1, days.size)
        assertEquals(4, days.single().entries.size)
        assertEquals(4, days.single().total)
    }

    @Test
    fun onlyTheExactOnDutyStatusCounts() {
        val days = deriveOdHoursDays(
            listOf(
                course(
                    "CHE1001", "Chemistry", "L1",
                    "2026-09-01" to "On Duty",
                    "2026-09-02" to "on duty",
                    "2026-09-03" to "ONDUTY",
                    "2026-09-04" to "od",
                    "2026-09-05" to "Partial OD",
                    "2026-09-06" to "Present",
                    "2026-09-07" to "",
                )
            )
        )
        assertEquals(1, days.size)
        assertEquals("2026-09-01", days.single().date)
        // A half-day is not an OD day: no partial status, no clock-time guess, no cap.
        assertEquals(2, totalOdHours(days))
        assertTrue(days.single().entries.single().title == "Chemistry")
    }

    @Test
    fun blankDateIsNotFiledUnderAPhantomDay() {
        val days = deriveOdHoursDays(
            listOf(course("CHE1001", "Chemistry", "L1", "" to "On Duty"))
        )
        assertTrue(days.isEmpty())
        assertEquals(0, totalOdHours(days))
    }

    // ── D-125: the input-grain regression the arithmetic-only D-120 fix missed ──────────

    private fun embeddedSnapshot(vararg labDates: String) = DomainSnapshot(
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
                                logs = labDates.map { AttendanceDay(it, "On Duty") },
                            ),
                        ),
                    ).associateBy { it.code },
                ),
            ),
        ),
    )

    @Test
    fun embeddedTheoryAndLabHalvesKeepTheirOwnSlotsAndHours() {
        // ../AmazeCC counts attendanceRes.attendance row by row: the theory row's C1 viewLink is
        // 1h and the lab row's L31 viewLink is 2h. EmbeddedLinkMerge used to fold them into one
        // "C1+L31" row (theory first), so the lab OD was charged 1h. D-125 counts raw rows.
        val days = deriveOdDaysFromSnapshot(embeddedSnapshot("2026-10-06"))
        assertEquals(2, days.size)
        assertEquals(1, days.single { it.date == "2026-10-05" }.total)
        assertEquals(2, days.single { it.date == "2026-10-06" }.total)
        assertEquals(3, totalOdHours(days))
    }

    @Test
    fun embeddedHalvesDoNotLoseASharedDateLog() {
        // Both halves report On Duty on the same date: the web counts two entries (1h + 2h).
        // EmbeddedLinkMerge.combinedAttendance de-duplicates by (date,status) and dropped one.
        val days = deriveOdDaysFromSnapshot(embeddedSnapshot("2026-10-05"))
        assertEquals(1, days.size)
        assertEquals(2, days.single().entries.size)
        assertEquals(3, days.single().total)
        assertEquals(3, totalOdHours(days))
    }

    @Test
    fun rawReportingAttendanceMatchesTheWebRowGrain() {
        val rows = rawReportingAttendance(embeddedSnapshot("2026-10-06"))
        // Two rows, each with its own raw slot — never a merged "C1+L31" row.
        assertEquals(2, rows.size)
        assertEquals(setOf("C1", "L31"), rows.map { it.slotName }.toSet())
        assertEquals(3, totalOdHours(deriveOdHoursDays(rows)))
    }

    // ── D-133: the PRODUCTION grain — the stored snapshot is already merged ──────────

    @Test
    fun mergedEmbeddedCourseReexpandsBothHalvesAndCountsTheoryOneLabTwoOnSharedDate() {
        // VtopIngestor.fromLegacy() runs EmbeddedLinkMerge.mergeSnapshot at ingest time, so the
        // Course the app stores is ONE merged row: its two raw components live in `halves`, and
        // the merged attendance has already de-duplicated their On Duty logs by (date, status).
        // Both halves report On Duty on the SAME date — the web counts a 1h C1 row and a 2h L31
        // row; EmbeddedLinkMerge would have collapsed them into a single "C1+L31" log.
        val date = "2026-11-02"
        val theoryLog = AttendanceDay(date, "On Duty")
        val labLog = AttendanceDay(date, "On Duty")
        val theoryHalf = EmbeddedCourseHalf(
            code = "BCSE203E",
            title = "Database Systems",
            type = "Embedded Theory",
            half = "T",
            slots = listOf("C1"),
            attendance = Attendance(logs = listOf(theoryLog)),
        )
        val labHalf = EmbeddedCourseHalf(
            code = "BCSE203E(L)",
            title = "Database Systems Lab",
            type = "Embedded Lab",
            half = "L",
            slots = listOf("L31"),
            attendance = Attendance(logs = listOf(labLog)),
        )
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S1" to Semester(
                        id = "S1",
                        courses = mapOf(
                            "BCSE203E" to Course(
                                code = "BCSE203E",
                                title = "Database Systems",
                                type = "Embedded Theory and Lab",
                                slots = listOf("C1", "L31"),
                                // The merge's combinedAttendance de-duplicates the shared date.
                                attendance = Attendance(
                                    logs = listOf(theoryLog, labLog).distinctBy { it.date to it.status },
                                ),
                                halves = listOf(theoryHalf, labHalf),
                                isEmbeddedMerged = true,
                                linkedBaseCode = "BCSE203E",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val rows = rawReportingAttendance(snapshot)
        // Two separate rows, each with its own raw slot — never one merged "C1+L31" row.
        assertEquals(2, rows.size)
        assertEquals(setOf("C1", "L31"), rows.map { it.slotName }.toSet())

        val days = deriveOdDaysFromSnapshot(snapshot)
        val day = days.single()
        assertEquals(date, day.date)
        assertEquals(2, day.entries.size)
        assertEquals(3, day.total)
        assertEquals(3, totalOdHours(days))
        // Theory (C1) is 1h TH; lab (L31) is 2h LAB — both counted on the shared date.
        assertEquals(
            setOf("TH" to 1, "LAB" to 2),
            day.entries.map { it.type to it.hours }.toSet(),
        )
    }
}
