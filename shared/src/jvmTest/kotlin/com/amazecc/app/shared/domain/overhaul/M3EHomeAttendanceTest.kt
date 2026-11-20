package com.amazecc.app.shared.domain.overhaul

import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-129: an embedded course is TWO VTOP rows with one code and two different percentages.
 * The home timeline must keep the theory and lab meetings separate — both the figure a card
 * shows and the decision to merge adjacent cards — so one half's attendance is never discarded.
 */
class M3EHomeAttendanceTest {

    /**
     * Mirrors the embedded fixture in [com.amazecc.app.shared.ui.screens.m3e.M3EHomeScreenUiTest]
     * (theory 95% from 38/40, lab 72% printed). Extra lab slot tokens are realistic lab cells —
     * they let the "lab + lab" merge case be two *distinct* meetings rather than one token twice.
     */
    private fun embeddedCourse() = Course(
        code = "BACSE102",
        title = "Embedded Systems",
        type = "Embedded Theory",
        attendance = Attendance(percentage = "80"),
        halves = listOf(
            EmbeddedCourseHalf(
                code = "BACSE102", type = "Embedded Theory", half = "T",
                slots = listOf("B1"), attendance = Attendance(attended = 38, total = 40)
            ),
            EmbeddedCourseHalf(
                code = "BACSE102L", type = "Embedded Lab", half = "L",
                slots = listOf("L55", "L56", "L31+L32"), attendance = Attendance(percentage = "72%")
            )
        )
    )

    @Test
    fun embedded_attendance_and_component_read_the_half_that_owns_the_slot() {
        val course = embeddedCourse()

        assertEquals(95.0, M3EHomeAttendance.percentForSlot(course, "B1"))
        assertEquals(72.0, M3EHomeAttendance.percentForSlot(course, "L55"))

        assertEquals("THEORY", M3EHomeAttendance.componentForSlot(course, "B1"))
        assertEquals("LAB", M3EHomeAttendance.componentForSlot(course, "L55"))
        // A VTOP timetable cell ("L31+L32") is split before a half is looked up.
        assertEquals("LAB", M3EHomeAttendance.componentForSlot(course, "L31+L32"))
    }

    @Test
    fun adjacent_slots_merge_only_within_the_same_component() {
        val course = embeddedCourse()

        assertTrue(M3EHomeAttendance.canMergeAdjacentSlots(course, "B1", "TB1"))
        assertFalse(M3EHomeAttendance.canMergeAdjacentSlots(course, "B1", "L55"))
        assertTrue(M3EHomeAttendance.canMergeAdjacentSlots(course, "L55", "L56"))
        // A mixed cell claims both halves; it can never be folded onto one single-component card.
        assertFalse(M3EHomeAttendance.canMergeAdjacentSlots(course, "B1+L55", "B1"))
    }

    @Test
    fun standalone_lab_course_stays_lab_and_keeps_its_own_attendance() {
        val lab = Course(
            code = "BACSE301L", title = "Data Structures Lab", type = "Lab Only",
            slots = listOf("L55"), attendance = Attendance(attended = 9, total = 12)
        )

        assertEquals("LAB", M3EHomeAttendance.componentForSlot(lab, "L55"))
        assertEquals(75.0, M3EHomeAttendance.percentForSlot(lab, "L55"))
        assertTrue(M3EHomeAttendance.canMergeAdjacentSlots(lab, "L55", "L56"))
    }
}
