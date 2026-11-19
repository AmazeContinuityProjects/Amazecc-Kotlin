package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure unit tests for the host-side classroom filtering used by [M3EFreeClassroomsHost]. */
class M3ECampusWiringFilterTest {

    private val rooms = listOf(
        room("SJT-401", "SJT", available = true),
        room("SJT-402", "SJT", available = false),
        room("TT-712", "TT", available = true),
        room("MB-108", "MB", available = true)
    )

    private fun room(number: String, building: String, available: Boolean) = M3EClassroomSlot(
        roomNumber = number,
        buildingCode = building,
        floor = 4,
        capacity = 60,
        isAirConditioned = true,
        availabilityStatus = if (available) "Free now" else "Occupied",
        isCurrentlyAvailable = available,
        nextOccupiedTime = null
    )

    @Test
    fun allBuildingsReturnsEverythingWhenUnfiltered() {
        val result = filterM3EClassrooms(rooms, M3EClassroomBuilding.ALL, false, "")
        assertEquals(4, result.size)
    }

    @Test
    fun buildingFilterRestrictsToMatchingCode() {
        val result = filterM3EClassrooms(rooms, M3EClassroomBuilding.SJT, false, "")
        assertEquals(listOf("SJT-401", "SJT-402"), result.map { it.roomNumber })
    }

    @Test
    fun onlyAvailableNowDropsOccupiedRooms() {
        val result = filterM3EClassrooms(rooms, M3EClassroomBuilding.ALL, true, "")
        assertEquals(listOf("SJT-401", "TT-712", "MB-108"), result.map { it.roomNumber })
        assertTrue(result.all { it.isCurrentlyAvailable })
    }

    @Test
    fun searchQueryIsCaseInsensitive() {
        val result = filterM3EClassrooms(rooms, M3EClassroomBuilding.ALL, false, "sjt-4")
        assertEquals(listOf("SJT-401", "SJT-402"), result.map { it.roomNumber })
    }

    @Test
    fun combinedFiltersAreAppliedTogether() {
        val result = filterM3EClassrooms(rooms, M3EClassroomBuilding.SJT, true, "401")
        assertEquals(listOf("SJT-401"), result.map { it.roomNumber })
    }
}
