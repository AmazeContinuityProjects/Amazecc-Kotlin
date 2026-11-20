package com.amazecc.app.shared.domain.overhaul

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M3EComputationsTest {

    @Test
    fun calculateBunkMargin_returns_safe_bunkable_classes_when_above_75() = runTest {
        // Attended 18 of 20 = 90%
        val (margin, text) = M3EComputations.calculateBunkMargin(attended = 18, total = 20, targetPct = 75f)
        assertTrue(margin >= 3, "Should be able to bunk at least 3 classes")
        assertTrue(text.contains("Can bunk"), "Text should indicate safe bunks")
    }

    @Test
    fun calculateBunkMargin_returns_negative_deficit_when_below_75() = runTest {
        // Attended 10 of 20 = 50%
        val (margin, text) = M3EComputations.calculateBunkMargin(attended = 10, total = 20, targetPct = 75f)
        assertTrue(margin < 0, "Deficit margin must be negative")
        assertTrue(text.contains("Attend next"), "Text should guide student on recovery")
    }

    @Test
    fun filterCourses_filters_concurrently_by_tone_and_query() = runTest {
        val courses = listOf(
            M3ECourseAttendanceCardState(
                courseCode = "CSE1001",
                courseTitle = "Problem Solving",
                badge = "Theory",
                attendedClasses = 18,
                totalClasses = 20,
                percentage = 90.0,
                tone = M3ECardTone.GOOD,
                faculty = "Dr. Alice",
                slot = "A1",
                bunkSummary = "Can bunk 3"
            ),
            M3ECourseAttendanceCardState(
                courseCode = "MAT2001",
                courseTitle = "Differential Equations",
                badge = "Theory",
                attendedClasses = 12,
                totalClasses = 20,
                percentage = 60.0,
                tone = M3ECardTone.CRITICAL,
                faculty = "Dr. Bob",
                slot = "B1",
                bunkSummary = "Attend next 8"
            )
        )

        val atRisk = M3EComputations.filterCourses(courses, AttendanceFilter.AT_RISK, "")
        assertEquals(1, atRisk.size)
        assertEquals("MAT2001", atRisk.first().courseCode)

        val queryMatched = M3EComputations.filterCourses(courses, AttendanceFilter.ALL, "Problem")
        assertEquals(1, queryMatched.size)
        assertEquals("CSE1001", queryMatched.first().courseCode)
    }
}
