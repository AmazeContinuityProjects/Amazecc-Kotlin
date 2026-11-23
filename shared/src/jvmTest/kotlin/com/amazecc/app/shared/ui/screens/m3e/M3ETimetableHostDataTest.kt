package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-186 part B: the full-screen timetable used to read `TimetableRes.slots`, which no producer
 * ever fills. These tests pin the host's real-data derivation over the synced academic store —
 * no Compose mounting, no fabricated classes.
 */
class M3ETimetableHostDataTest {

    private fun realCourse() = StoredCourse(
        courseCode = "18MAB101T",
        courseTitle = "Applied Mathematics",
        courseType = "Theory Only",
        credits = "4",
        classId = "C123",
        // A real campus slot code: A1 is taught on MON 8:00-8:50 and WED 8:55-9:45.
        slots = listOf("A1"),
        venue = "AB1-101",
        faculty = "Dr. X",
        attendance = StoredAttendance(
            attendedClasses = 34,
            totalClasses = 40,
            attendancePercentage = "85"
        )
    )

    @Test
    fun `real A1 slot produces a Monday class in host data`() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf("18MAB101T" to realCourse())
        )

        val data = buildM3ETimetableHostData(sem, todayKey = "MON", selectedDayIndex = 0)

        assertTrue(data.hasSyncedTimetable)
        assertEquals(1, data.totalCourses)
        assertEquals(6, data.days.size)

        val monday = data.days.first { it.dayName == "Monday" }
        assertTrue(monday.isToday)
        assertTrue(monday.isSelected)
        val cls = monday.slots.single()
        assertEquals("A1", cls.slot)
        assertEquals("18MAB101T", cls.courseCode)
        assertEquals("Applied Mathematics", cls.courseTitle)
        assertEquals("08:00", cls.startTime)
        assertEquals("08:50", cls.endTime)
        assertEquals("AB1-101", cls.venue)
        assertEquals("Dr. X", cls.faculty)
        assertEquals(85.0, cls.attendancePercentage)
        assertEquals(M3ECardTone.GOOD, cls.attendanceTone)
        assertFalse(cls.isLab)

        // A1 is also a Wednesday period — deriving from real slots keeps every day it meets.
        val wednesday = data.days.first { it.dayName == "Wednesday" }
        assertEquals("A1", wednesday.slots.single().slot)
        // No day was invented: the remaining days have no classes.
        assertTrue(data.days.filter { it.dayName !in setOf("Monday", "Wednesday") }
            .all { it.slots.isEmpty() })
    }

    @Test
    fun `empty academic store is an honest no-data state`() {
        val noSemester = buildM3ETimetableHostData(null, todayKey = "MON", selectedDayIndex = 0)
        val emptySemester = buildM3ETimetableHostData(
            SemesterData(semesterId = "SEM1"),
            todayKey = "MON",
            selectedDayIndex = 0
        )

        for (data in listOf(noSemester, emptySemester)) {
            assertFalse(data.hasSyncedTimetable)
            assertEquals(0, data.totalCourses)
            assertEquals(6, data.days.size)
            assertTrue(data.days.all { it.slots.isEmpty() })
        }
        // The notice shown by the screen/sheet in this state is real copy, not a fake class.
        assertNotNull(NO_TIMETABLE_NOTICE)
        assertTrue(NO_TIMETABLE_NOTICE.contains("synced", ignoreCase = true))
    }

    @Test
    fun `a free day inside a synced week is not mistaken for missing data`() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf("18MAB101T" to realCourse())
        )

        val data = buildM3ETimetableHostData(sem, todayKey = "TUE", selectedDayIndex = 1)

        assertTrue(data.hasSyncedTimetable)
        val tuesday = data.days.first { it.dayName == "Tuesday" }
        assertTrue(tuesday.slots.isEmpty())
        assertTrue(data.days.first { it.dayName == "Monday" }.slots.isNotEmpty())
    }
}
