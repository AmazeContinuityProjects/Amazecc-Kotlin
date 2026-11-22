package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-162 — JVM coverage for the pure free-classroom deriver.
 *
 * The snapshot is hand-built in the same shape the ingestor produces: one semester, courses
 * carrying real VTOP type strings, real slot codes and real venue strings. No capacity exists
 * anywhere in it — the deriver must not invent one.
 */
class FreeClassroomDeriverTest {

    private fun snapshot(): DomainSnapshot {
        fun attendance() = Attendance(attended = 1, total = 1, percentage = "100")
        val semester = Semester(
            id = "CH20262701",
            name = "Fall Semester 2026-27",
            courses = mapOf(
                "CSE1001" to Course(
                    code = "CSE1001",
                    title = "Theory One",
                    type = "Theory Only",
                    slots = listOf("A1"),
                    venue = "SJT-401",
                    attendance = attendance()
                ),
                "CSE1002" to Course(
                    code = "CSE1002",
                    title = "Lab Two",
                    type = "Lab Only",
                    slots = listOf("L31"),
                    venue = "TT-201",
                    attendance = attendance()
                ),
                // VTOP's "no room" marker: must be dropped, never become a fabricated room.
                "CSE1003" to Course(
                    code = "CSE1003",
                    title = "Online Three",
                    type = "Theory Only",
                    slots = listOf("A1"),
                    venue = "NIL",
                    attendance = attendance()
                )
            )
        )
        return DomainSnapshot(
            academics = Academics(
                semesters = mapOf(semester.id to semester),
                selectedSemesterId = semester.id
            )
        )
    }

    @Test
    fun `period options come from the canonical timetable periods`() {
        val options = FreeClassroomDeriver.periodOptions()
        assertEquals(12, options.size)
        assertEquals("p1", options.first().key)
        assertEquals("08:00 - 08:50", options.first().label)
        assertEquals(480, options.first().startMinute)
        assertTrue(options.any { it.isLunch })
    }

    @Test
    fun `derive uses only real venues and never fabricates a room or capacity`() {
        val result = FreeClassroomDeriver.derive(snapshot(), "MON", "p1")

        assertEquals(listOf("SJT-401", "TT-201"), result.allRooms.map { it.code })
        assertEquals(2, result.totalRooms)
        assertTrue(result.allRooms.none { "NIL" in it.code })
        assertTrue(result.allRooms.none { "ONLINE" in it.code })

        // Constructing the exact row proves the type has no capacity/seats parameter to invent.
        assertEquals(
            FreeClassroomRoomRow(
                code = "TT-201",
                block = "TT",
                number = "201",
                isLab = true,
                isFree = true
            ),
            result.freeRooms.single()
        )
    }

    @Test
    fun `derive marks only the classroom occupied in MON first period`() {
        val result = FreeClassroomDeriver.derive(snapshot(), "MON", "p1")

        assertEquals(listOf("SJT-401"), result.occupiedRooms.map { it.code })
        assertEquals(listOf("TT-201"), result.freeRooms.map { it.code })
    }

    @Test
    fun `switching the period changes occupancy`() {
        val afternoon = FreeClassroomDeriver.derive(snapshot(), "MON", "p6")

        assertEquals(listOf("TT-201"), afternoon.occupiedRooms.map { it.code })
        assertEquals(listOf("SJT-401"), afternoon.freeRooms.map { it.code })
    }

    @Test
    fun `switching the day changes occupancy`() {
        val tuesday = FreeClassroomDeriver.derive(snapshot(), "TUE", "p1")

        // Neither A1 nor L31 meets on TUESDAY in the canonical slot map.
        assertEquals(2, tuesday.freeRooms.size)
        assertTrue(tuesday.occupiedRooms.isEmpty())
    }

    @Test
    fun `block summaries count the real rooms and a real percentage`() {
        val result = FreeClassroomDeriver.derive(snapshot(), "MON", "p1")
        val blocks = FreeClassroomDeriver.blockSummaries(result.allRooms)

        assertEquals(2, blocks.size)
        val sjt = blocks.first { it.block == "SJT" }
        assertEquals(1, sjt.total)
        assertEquals(0, sjt.free)
        assertEquals(0, sjt.percentFree)

        val tt = blocks.first { it.block == "TT" }
        assertEquals(1, tt.total)
        assertEquals(1, tt.free)
        assertEquals(1, tt.labFree)
        assertEquals(0, tt.theoryFree)
        assertEquals(100, tt.percentFree)
    }
}
