package com.amazecc.app.shared.state

import com.amazecc.app.shared.vtop.VtopCourseCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a timetable row finds its attendance row.
 *
 * `fetchAttendance` walks the timetable and, for each row, takes the raw attendance row with the
 * same *base* code — the rule that pairs a theory half with its lab half, and therefore sends both
 * timetable rows to the same theory attendance row. The lab's 18/20 became unreachable before the
 * merge ever saw it, which is why an embedded course's lab half reported 0 classes.
 *
 * The fixtures are CH20262701 verbatim: `resources/fixtures/attendance.json` for the raw rows and
 * `vtop-snapshots/timetable-grid__CH20262701.html` for the timetable.
 */
class AttendanceHalfMatchTest {

    /** One raw attendance row, as `fetchRawAttendance` yields it. */
    private data class Raw(
        val courseCode: String,
        val courseType: String,
        val slotName: String,
        val attended: Int,
        val total: Int,
    )

    /** One timetable row, as `fetchCourses` yields it. */
    private data class Sched(
        val courseCode: String,
        val category: String,
        val slotVenue: String,
    )

    private val raw = listOf(
        Raw("BACSE105", "Embedded Theory", "F1+TF1", 24, 27),
        Raw("BACSE105", "Embedded Lab", "L11+L12", 18, 20),
        Raw("BACSE106", "Embedded Theory", "C1+TC1", 26, 28),
        Raw("BACSE106", "Embedded Lab", "L55+L56", 16, 18),
        Raw("BAHUM251", "Theory Only", "G1+TG1", 27, 29)
    )

    private val timetable = listOf(
        Sched("BACSE105", "University Core Courses", "F1+TF1 - AB1-101"),
        Sched("BACSE105", "University Core Courses", "L11+L12 - AB2-205"),
        Sched("BACSE106", "University Core Courses", "C1+TC1 - AB1-102"),
        Sched("BACSE106", "University Core Courses", "L55+L56 - AB2-206"),
        Sched("BAHUM251", "University Core Courses", "G1+TG1 - AB1-103")
    )

    /**
     * The pairing rule, as it now stands: same base code **and** same lab-ness.
     *
     * Duplicated here rather than called so the test pins the rule itself. `fetchAttendance` is a
     * suspend function over a live session and cannot be run off-device.
     */
    private fun match(course: Sched, candidates: List<Raw>): Raw? = candidates.firstOrNull { candidate ->
        VtopCourseCode.sameCourse(candidate.courseCode, course.courseCode) &&
            AcademicMerge.isLabRow(candidate.courseType, candidate.slotName) ==
            AcademicMerge.isLabRow(course.category, course.slotVenue)
    }

    @Test
    fun theLabTimetableRowFindsTheLabAttendanceRowNotTheTheoryOne() {
        val labSlot = timetable.first { it.slotVenue.startsWith("L") && it.courseCode == "BACSE105" }
        val found = assertNotNull(match(labSlot, raw), "the lab row must match something")
        assertEquals("Embedded Lab", found.courseType)
        assertEquals(18, found.attended)
        assertEquals(20, found.total)
    }

    @Test
    fun theTheoryTimetableRowStillFindsTheTheoryRow() {
        val theorySlot = timetable.first { it.courseCode == "BACSE105" && it.slotVenue.startsWith("F") }
        val found = assertNotNull(match(theorySlot, raw))
        assertEquals("Embedded Theory", found.courseType)
        assertEquals(24, found.attended)
    }

    @Test
    fun aStandaloneCourseStillMatchesItsOnlyRow() {
        val only = timetable.first { it.courseCode == "BAHUM251" }
        val found = assertNotNull(match(only, raw))
        assertEquals(27, found.attended)
        assertEquals(29, found.total)
    }

    @Test
    fun everyTimetableRowFindsItsOwnRowSoNoCourseFallsBackToZero() {
        timetable.forEach { course ->
            assertNotNull(match(course, raw), "unmatched: ${course.courseCode} ${course.slotVenue}")
        }
    }

    /**
     * The pairing must survive being fed into the store, or fixing the match upstream would still
     * end in one merged row.
     */
    @Test
    fun theMatchedHalvesLandAsTwoStoreRowsWithTheirOwnCounts() {
        val items = timetable.mapNotNull { course ->
            val found = match(course, raw) ?: return@mapNotNull null
            com.amazecc.app.shared.model.AttendanceItem(
                courseCode = found.courseCode,
                courseTitle = "Data Structures and Algorithms",
                courseType = found.courseType,
                slotName = found.slotName,
                attendedClasses = found.attended,
                totalClasses = found.total,
                attendancePercentage = "0"
            )
        }
        val courses = AcademicMerge
            .upsertAttendance(
                AcademicData(),
                "CH20262701",
                com.amazecc.app.shared.model.AttendanceRes(attendance = items)
            )
            .semesters.getValue("CH20262701").courses

        assertTrue("BACSE105" in courses, "theory half missing")
        assertTrue("BACSE105(L)" in courses, "lab half missing — this was the reported bug")
        assertEquals(24, assertNotNull(courses["BACSE105"]).attendance!!.attendedClasses)
        assertEquals(18, assertNotNull(courses["BACSE105(L)"]).attendance!!.attendedClasses)
        assertEquals(18, assertNotNull(courses["BACSE106(L)"]).attendance!!.totalClasses - 0)
    }

    @Test
    fun aCourseWithNoLabRowDoesNotInventOne() {
        // BAHUM251 is Theory Only; nothing may create a lab half for it.
        val items = timetable.filterNot { it.courseCode == "BACSE105" && it.slotVenue.startsWith("L") }
            .mapNotNull { course ->
                val found = match(course, raw) ?: return@mapNotNull null
                com.amazecc.app.shared.model.AttendanceItem(
                    courseCode = found.courseCode,
                    courseType = found.courseType,
                    slotName = found.slotName,
                    attendedClasses = found.attended,
                    totalClasses = found.total,
                    attendancePercentage = "0"
                )
            }
        val courses = AcademicMerge
            .upsertAttendance(
                AcademicData(),
                "CH20262701",
                com.amazecc.app.shared.model.AttendanceRes(attendance = items)
            )
            .semesters.getValue("CH20262701").courses
        assertNull(courses["BAHUM251(L)"])
    }
}