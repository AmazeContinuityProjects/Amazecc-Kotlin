package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.Academics
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Semester
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-191 follow-up — the widget reads the same canonical week index as the screen. It must not
 * resurrect the weekend → Monday clamp, fabricate a "free" room outside a live period, or relabel a
 * stale cached status with an "unknown" placeholder.
 */
class WidgetFreeClassroomsTest {

    private fun snapshot(): DomainSnapshot {
        fun attendance() = Attendance(attended = 1, total = 1, percentage = "100")
        val semester = Semester(
            id = "S1",
            name = "Real semester",
            courses = mapOf(
                "CSE1001" to Course(
                    code = "CSE1001", title = "Monday class", type = "Theory Only",
                    slots = listOf("A1"), venue = "SJT-401", attendance = attendance()
                ),
                "CSE1002" to Course(
                    code = "CSE1002", title = "Tuesday class", type = "Theory Only",
                    slots = listOf("B1"), venue = "TT-201", attendance = attendance()
                )
            )
        )
        return DomainSnapshot(
            academics = Academics(semesters = mapOf(semester.id to semester), selectedSemesterId = semester.id)
        )
    }

    @Test
    fun `weekend and lunch are honest empty states with no weekend clamp`() {
        val sunday = WidgetDataUtils.freeRoomsNow(
            snapshot(), emptyList(), LocalDateTime(2026, 11, 22, 10, 0)
        ).single()
        assertEquals("No live class period right now", sunday.room)
        assertEquals("", sunday.type)
        assertEquals("", sunday.block)

        val lunch = WidgetDataUtils.freeRoomsNow(
            snapshot(), emptyList(), LocalDateTime(2026, 11, 23, 13, 0)
        ).single()
        assertEquals("No live class period right now", lunch.room)
        assertTrue(lunch.type.isBlank())

        val noData = WidgetDataUtils.freeRoomsNow(
            DomainSnapshot(), emptyList(), LocalDateTime(2026, 11, 23, 8, 10)
        ).single()
        assertEquals("No timetable data synced yet", noData.room)
        assertTrue(noData.type.isBlank())
    }

    @Test
    fun `live period shows only real free rooms with a derived lab fact`() {
        val rooms = WidgetDataUtils.freeRoomsNow(
            snapshot(), emptyList(), LocalDateTime(2026, 11, 23, 8, 10)
        )
        assertEquals(listOf("TT-201"), rooms.map { it.room })
        assertEquals("Classroom", rooms.single().type)
        assertEquals("TT", rooms.single().block)
        assertTrue(rooms.none { it.type.equals("unknown", ignoreCase = true) })
    }
}
