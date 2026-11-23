package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.FREE_CLASSROOM_TEACHING_DAYS
import com.amazecc.app.shared.domain.FreeClassroomDay
import com.amazecc.app.shared.domain.FreeClassroomDayAnswer
import com.amazecc.app.shared.domain.FreeClassroomDeriver
import com.amazecc.app.shared.domain.FreeClassroomKindFilter
import com.amazecc.app.shared.domain.FreeClassroomOccupant
import com.amazecc.app.shared.domain.FreeClassroomPeriodAnswer
import com.amazecc.app.shared.domain.FreeClassroomQuery
import com.amazecc.app.shared.domain.FreeClassroomRoomInfo
import com.amazecc.app.shared.domain.FreeClassroomWeek
import com.amazecc.app.shared.domain.FreeClassroomWeekIndex
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-191 — the host hands one [FreeClassroomQuery] to [FreeClassroomWeekIndex.query]; these tests
 * pin that every control (building included) narrows the SAME result set and the SAME count.
 * The legacy per-room `filterM3EClassrooms` track is gone from the screen contract.
 */
class M3ECampusWiringFilterTest {

    private fun week(): FreeClassroomWeek {
        val periods = FreeClassroomDeriver.periodOptions()
        val rooms = mapOf(
            "SJT-401" to FreeClassroomRoomInfo("SJT-401", "SJT", "401", false),
            "TT-201" to FreeClassroomRoomInfo("TT-201", "TT", "201", true),
            "MB-108" to FreeClassroomRoomInfo("MB-108", "MB", "108", false)
        )
        val dayAnswers = FREE_CLASSROOM_TEACHING_DAYS.associateWith { day ->
            FreeClassroomDayAnswer(
                day = day,
                periods = periods.map { period ->
                    val occupants = when {
                        day == FreeClassroomDay.MON && period.key == "p1" -> mapOf(
                            "SJT-401" to listOf(FreeClassroomOccupant("k1", "SJT-401", "CSE1001", "Theory", "Dr Rao", source = "snapshot"))
                        )
                        day == FreeClassroomDay.MON && period.key == "p2" -> mapOf(
                            "TT-201" to listOf(FreeClassroomOccupant("k2", "TT-201", "CSE1002", "Lab", "Dr Rao", source = "catalogue"))
                        )
                        else -> emptyMap()
                    }
                    FreeClassroomPeriodAnswer(
                        period = period,
                        occupants = occupants,
                        freeRoomCodes = rooms.keys.filter { it !in occupants }.toList()
                    )
                },
                rooms = rooms,
                blocks = listOf("MB", "SJT", "TT")
            )
        }
        return FreeClassroomWeek(dayAnswers, periods)
    }

    private val base = FreeClassroomQuery(
        day = FreeClassroomDay.MON,
        periodKey = "p1",
        kind = FreeClassroomKindFilter.ALL
    )

    @Test
    fun allBuildingsReturnsTheSingleUnfilteredAnswer() {
        val result = FreeClassroomWeekIndex.query(week(), base)
        assertEquals(2, result.count)
        assertEquals(listOf("MB-108", "TT-201"), result.blocks.flatMap { it.freeRooms }.map { it.code }.sorted())
    }

    @Test
    fun buildingFilterRestrictsTheSameAnswer() {
        val result = FreeClassroomWeekIndex.query(week(), base.copy(building = M3EClassroomBuilding.SJT.code))
        // SJT-401 is taken in p1, so the one SJT block contributes zero free rooms.
        assertEquals(0, result.count)
        assertTrue(result.blocks.isEmpty())
    }

    @Test
    fun onlyFreeOffAddsTakenRoomsWithoutChangingTheFreeCount() {
        val result = FreeClassroomWeekIndex.query(week(), base.copy(onlyFree = false))
        assertEquals(3, result.blocks.sumOf { it.rooms.size })
        assertEquals(2, result.count)
        assertTrue(result.blocks.any { it.block == "SJT" && it.free == 0 })
    }

    @Test
    fun blockDenominatorCountsOnlyTheVisibleFilteredRooms() {
        // One real block with two rooms; a search narrows the visible set to a single chip.
        val periods = FreeClassroomDeriver.periodOptions()
        val rooms = mapOf(
            "AB1-101" to FreeClassroomRoomInfo("AB1-101", "AB1", "101", false),
            "AB1-102" to FreeClassroomRoomInfo("AB1-102", "AB1", "102", true)
        )
        val days = FREE_CLASSROOM_TEACHING_DAYS.associateWith { day ->
            FreeClassroomDayAnswer(
                day = day,
                periods = periods.map {
                    FreeClassroomPeriodAnswer(it, emptyMap(), rooms.keys.toList())
                },
                rooms = rooms,
                blocks = listOf("AB1")
            )
        }
        val narrowed = FreeClassroomWeekIndex.query(
            FreeClassroomWeek(days, periods),
            base.copy(search = "101")
        )
        val summary = narrowed.blocks.single()
        assertEquals(listOf("AB1-101"), summary.rooms.map { it.code })
        // Old code counted both rooms in the day here and printed "1 of 2 free" over one chip.
        assertEquals(1, summary.total)
        assertEquals(summary.rooms.size, summary.total)

        // Switching off onlyFree widens the visible set, and the denominator follows it.
        val all = FreeClassroomWeekIndex.query(
            FreeClassroomWeek(days, periods),
            base.copy(onlyFree = false)
        )
        assertEquals(2, all.blocks.single().total)
    }

    @Test
    fun searchQueryIsCaseInsensitiveAndDrivesTheSameCount() {
        val result = FreeClassroomWeekIndex.query(week(), base.copy(search = "108"))
        assertEquals(1, result.count)
        assertEquals("MB-108", result.blocks.single().freeRooms.single().code)
    }

    @Test
    fun runOnlyAndBlockFilterComposeIntoOneResult() {
        val run = FreeClassroomWeekIndex.query(week(), base.copy(runOnly = true))
        // MB-108 is free for all three periods; TT-201 is taken in p2.
        assertEquals(1, run.count)
        assertEquals("MB-108", run.blocks.single().freeRooms.single().code)

        val combined = FreeClassroomWeekIndex.query(
            week(),
            base.copy(block = "TT", search = "201", kind = FreeClassroomKindFilter.LAB)
        )
        assertEquals(1, combined.count)
        assertEquals("TT-201", combined.blocks.single().freeRooms.single().code)
    }
}
