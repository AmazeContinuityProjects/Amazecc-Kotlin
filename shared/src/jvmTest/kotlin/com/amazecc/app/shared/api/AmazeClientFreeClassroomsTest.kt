package com.amazecc.app.shared.api

import com.amazecc.app.shared.domain.CourseAttendance
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-191 follow-up — the cached client derivation must never claim a room is free when the clock is
 * outside a real teaching period. The pure clock-injected core makes both cases deterministic.
 */
class AmazeClientFreeClassroomsTest {

    private fun course(code: String, slot: String, venue: String) = CourseAttendance(
        courseCode = code,
        courseTitle = "Real Course $code",
        courseType = "Theory Only",
        slotName = slot,
        slotVenue = venue,
        faculty = "Dr Rao",
        credits = "3",
        category = null,
        attendedClasses = 1,
        totalClasses = 1,
        attendancePercentage = "100",
        logs = emptyList()
    )

    private val courses = listOf(
        course("CSE1001", "A1", "SJT-401"), // meets MON 08:00
        course("CSE1002", "B1", "TT-201")  // meets TUE 08:00, so free on MON morning
    )

    @Test
    fun `no live period means no free verdict and no unknown literal`() {
        // Sunday: never a teaching day, never silently Monday.
        val weekend = AmazeClient.deriveFreeClassroomsForCourses(
            courses,
            LocalDateTime(2026, 11, 22, 10, 0)
        )
        assertEquals("SUN", weekend.day)
        assertEquals(2, weekend.rooms.size)
        assertTrue(weekend.rooms.all { !it.isCurrentlyAvailable })
        assertTrue(weekend.rooms.all { it.availabilityStatus.isBlank() })
        assertFalse(weekend.rooms.any { it.availabilityStatus.equals("unknown", ignoreCase = true) })

        // A teaching day after the last slot has no current period either.
        val evening = AmazeClient.deriveFreeClassroomsForCourses(
            courses,
            LocalDateTime(2026, 11, 23, 22, 0)
        )
        assertTrue(evening.rooms.all { !it.isCurrentlyAvailable })
        assertTrue(evening.rooms.all { it.availabilityStatus.isBlank() })
    }

    @Test
    fun `a real live period still derives free and occupied from real occupancy`() {
        val result = AmazeClient.deriveFreeClassroomsForCourses(
            courses,
            LocalDateTime(2026, 11, 23, 8, 10) // Monday, first period
        )
        val occupied = result.rooms.single { it.roomNumber == "SJT-401" }
        val free = result.rooms.single { it.roomNumber == "TT-201" }
        assertFalse(occupied.isCurrentlyAvailable)
        assertEquals("occupied", occupied.availabilityStatus)
        assertTrue(free.isCurrentlyAvailable)
        assertEquals("free", free.availabilityStatus)
    }
}
