package com.amazecc.app.shared.domain

import com.amazecc.app.shared.ffcs.planner.ParsedCourse
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-191 — JVM coverage for the pure free-classroom derivation and its week index.
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

    // ── D-191: week index, strict clock, single-track query ────────────────────

    @Test
    fun `week index covers five teaching days and keeps occupants period-scoped`() {
        fun attendance() = Attendance(attended = 1, total = 1, percentage = "100")
        val semester = Semester(
            id = "S1",
            courses = mapOf(
                "AAA1001" to Course(
                    code = "AAA1001", title = "Morning class", type = "Theory Only",
                    slots = listOf("A1"), venue = "SJT-401", attendance = attendance()
                ),
                "BBB1002" to Course(
                    code = "BBB1002", title = "Later class", type = "Theory Only",
                    slots = listOf("F1"), venue = "SJT-401", attendance = attendance()
                )
            )
        )
        val domain = DomainSnapshot(
            academics = Academics(semesters = mapOf("S1" to semester), selectedSemesterId = "S1")
        )
        val week = FreeClassroomWeekIndex.build(domain)

        assertEquals(
            listOf(FreeClassroomDay.MON, FreeClassroomDay.TUE, FreeClassroomDay.WED, FreeClassroomDay.THU, FreeClassroomDay.FRI),
            week.days.keys.toList()
        )
        val monday = week.day("MON")
        assertEquals(
            listOf("AAA1001"),
            monday.period("p1").occupants["SJT-401"]?.map { it.courseCode }
        )
        assertEquals(
            listOf("BBB1002"),
            monday.period("p2").occupants["SJT-401"]?.map { it.courseCode }
        )
        assertTrue(monday.period("p3").occupants["SJT-401"].isNullOrEmpty())

        // The inspector timeline names only the class in each period, never the room's whole day.
        val timeline = FreeClassroomWeekIndex.timeline(week, "MON", "SJT-401", "MON", "p1")
        assertFalse(timeline.first { it.periodKey == "p1" }.occupants.any { it.courseCode == "BBB1002" })
        assertFalse(timeline.first { it.periodKey == "p2" }.occupants.any { it.courseCode == "AAA1001" })
        assertTrue(timeline.first { it.periodKey == "p1" }.isSelected)
    }

    @Test
    fun `FFCS catalogue rows supply faculty, ERP id, class id and school to the occupant`() {
        val catalogue = ParsedCourse(
            CODE = "ABC1001",
            TITLE = "Catalogue Class",
            TYPE = "Theory Only",
            CREDITS = "3",
            ROOM = "AB1-101",
            SLOT = "A1",
            FACULTY = "Dr Rao",
            ERP_ID = "12345",
            CLASS_ID = "C-9",
            EMPLOYEE_SCHOOL = "SCOPE"
        )
        val week = FreeClassroomWeekIndex.build(DomainSnapshot(), listOf(catalogue))
        val occupant = week.day("MON").period("p1").occupants["AB1-101"]?.single()
        assertNotNull(occupant)
        assertEquals("Dr Rao", occupant.faculty)
        assertEquals("12345", occupant.erpId)
        assertEquals("C-9", occupant.classId)
        assertEquals("SCOPE", occupant.schoolId)
        assertEquals("catalogue", occupant.source)
    }

    @Test
    fun `resolveNow is strict about weekends lunch and the five minute grace`() {
        val periods = FreeClassroomDeriver.periodOptions()

        // 2026-11-23 is a Monday. Five minutes early still counts as the live period.
        val early = FreeClassroomWeekIndex.resolveNow(periods, LocalDateTime(2026, 11, 23, 7, 58))
        assertEquals(FreeClassroomDay.MON, early.day)
        assertEquals("p1", early.period?.key)
        assertTrue(early.isLive)

        // Sunday is never Monday: no live claim and no fake jump target.
        val sunday = FreeClassroomWeekIndex.resolveNow(periods, LocalDateTime(2026, 11, 22, 10, 0))
        assertEquals(null, sunday.day)
        assertEquals(null, sunday.period)
        assertFalse(sunday.isLive)

        // 13:00 is inside the lunch window, which is never a live teaching period.
        val lunch = FreeClassroomWeekIndex.resolveNow(periods, LocalDateTime(2026, 11, 23, 13, 0))
        assertEquals(null, lunch.period)
        assertFalse(lunch.isLive)

        val afternoon = FreeClassroomWeekIndex.resolveNow(periods, LocalDateTime(2026, 11, 23, 14, 1))
        assertEquals("p6", afternoon.period?.key)
        assertTrue(afternoon.isLive)
    }

    /** A deterministic week: SJT busy in p1, TT busy in p2, AB1 free all day. */
    private fun fixtureWeek(): FreeClassroomWeek {
        val periods = FreeClassroomDeriver.periodOptions()
        val rooms = mapOf(
            "SJT-401" to FreeClassroomRoomInfo("SJT-401", "SJT", "401", false),
            "TT-201" to FreeClassroomRoomInfo("TT-201", "TT", "201", true),
            "AB1-101" to FreeClassroomRoomInfo("AB1-101", "AB1", "101", false)
        )
        val occupant = FreeClassroomOccupant("k1", "SJT-401", "CSE1001", "Theory", "Prof", source = "snapshot")
        val labOccupant = FreeClassroomOccupant("k2", "TT-201", "CSE1002", "Lab", "Prof", source = "catalogue")
        val dayAnswers = FREE_CLASSROOM_TEACHING_DAYS.associateWith { day ->
            FreeClassroomDayAnswer(
                day = day,
                periods = periods.map { period ->
                    val occupants = when {
                        day == FreeClassroomDay.MON && period.key == "p1" -> mapOf("SJT-401" to listOf(occupant))
                        day == FreeClassroomDay.MON && period.key == "p2" -> mapOf("TT-201" to listOf(labOccupant))
                        else -> emptyMap()
                    }
                    FreeClassroomPeriodAnswer(
                        period = period,
                        occupants = occupants,
                        freeRoomCodes = rooms.keys.filter { it !in occupants }.toList()
                    )
                },
                rooms = rooms,
                blocks = listOf("AB1", "SJT", "TT")
            )
        }
        return FreeClassroomWeek(dayAnswers, periods)
    }

    @Test
    fun `every filter narrows the one result set and one count`() {
        val week = fixtureWeek()
        val base = FreeClassroomQuery(FreeClassroomDay.MON, "p1", FreeClassroomKindFilter.ALL)

        val all = FreeClassroomWeekIndex.query(week, base)
        // SJT is taken in p1; TT and AB1 are free.
        assertEquals(2, all.count)
        assertEquals(2, all.blocks.size)

        val labs = FreeClassroomWeekIndex.query(week, base.copy(kind = FreeClassroomKindFilter.LAB))
        assertEquals(1, labs.count)
        assertEquals(listOf("TT-201"), labs.blocks.single().freeRooms.map { it.code })

        val oneBlock = FreeClassroomWeekIndex.query(week, base.copy(block = "TT"))
        assertEquals(1, oneBlock.count)

        val search = FreeClassroomWeekIndex.query(week, base.copy(search = "101"))
        assertEquals(1, search.count)
        assertEquals("AB1-101", search.blocks.single().freeRooms.single().code)

        // RUN_LENGTH=3: TT is taken in p2, so only AB1 survives the whole run.
        val run = FreeClassroomWeekIndex.query(week, base.copy(runOnly = true))
        assertEquals(FREE_CLASSROOM_RUN_LENGTH, 3)
        assertEquals(1, run.count)
        assertEquals("AB1-101", run.blocks.single().freeRooms.single().code)

        // Turning "only free" off adds the taken room to the one result set; the count stays honest.
        val withTaken = FreeClassroomWeekIndex.query(week, base.copy(onlyFree = false))
        assertEquals(3, withTaken.blocks.sumOf { it.rooms.size })
        assertEquals(2, withTaken.count)
    }

    @Test
    fun `best period skips lunch and reports the emptiest real period`() {
        val best = FreeClassroomWeekIndex.bestPeriod(fixtureWeek().day("MON"))
        assertNotNull(best)
        assertEquals(false, best.first.isLunch)
        assertEquals(3, best.second)
    }
}
