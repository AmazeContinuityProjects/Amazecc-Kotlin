package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.FreeClassroomBlockSummary
import com.amazecc.app.shared.domain.FreeClassroomPeriodOption
import com.amazecc.app.shared.domain.FreeClassroomRoomRow
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomKindFilter
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EFreeClassroomsScreenUiTest {

    private fun sampleFreeClassroomsState() = M3EFreeClassroomsState(
        classrooms = listOf(
            M3EClassroomSlot(
                roomNumber = "SJT-401",
                buildingCode = "SJT",
                floor = 4,
                capacity = 60,
                isAirConditioned = true,
                availabilityStatus = "Free until 02:00 PM (B2+TB2 slot)",
                isCurrentlyAvailable = true,
                nextOccupiedTime = "02:00 PM"
            )
        ),
        filteredClassrooms = listOf(
            M3EClassroomSlot(
                roomNumber = "SJT-401",
                buildingCode = "SJT",
                floor = 4,
                capacity = 60,
                isAirConditioned = true,
                availabilityStatus = "Free until 02:00 PM (B2+TB2 slot)",
                isCurrentlyAvailable = true,
                nextOccupiedTime = "02:00 PM"
            )
        ),
        selectedBuilding = M3EClassroomBuilding.ALL,
        onlyAvailableNow = true,
        searchQuery = ""
    )

    @Test
    fun testFreeClassroomsRendersRoomCards() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = sampleFreeClassroomsState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Free Classrooms").assertExists()
        onNodeWithText("SJT-401").assertExists()
        onNodeWithText("FREE NOW").assertExists()
        onNodeWithText("SJT Block • Floor 4").assertExists()
        onNodeWithText("Free until 02:00 PM (B2+TB2 slot)").assertExists()
        // The source publishes no per-room capacity; the legacy fixture value must not be shown.
        onNodeWithText("60 seats").assertDoesNotExist()
        onNodeWithText("AC").assertExists()
    }

    @Test
    fun testBuildingFilterSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EFreeClassroomsAction>()

        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = sampleFreeClassroomsState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onAllNodesWithText("SJT")[0].performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EFreeClassroomsAction.SelectBuilding)
        assertEquals(M3EClassroomBuilding.SJT, (capturedActions[0] as M3EFreeClassroomsAction.SelectBuilding).building)
    }

    @Test
    fun testToggleOnlyAvailableEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EFreeClassroomsAction>()

        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = sampleFreeClassroomsState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Only Free Right Now").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EFreeClassroomsAction.ToggleOnlyAvailable)
        assertEquals(false, (capturedActions[0] as M3EFreeClassroomsAction.ToggleOnlyAvailable).onlyAvailable)
    }

    // ── D-162: "When" and "Rooms" section coverage ─────────────────────────────

    private fun derivedState(
        blockSummaries: List<FreeClassroomBlockSummary> = emptyList()
    ) = M3EFreeClassroomsState(
        selectedDay = "MON",
        selectedPeriodKey = "p1",
        availablePeriodOptions = listOf(
            FreeClassroomPeriodOption("p1", "08:00 - 08:50", 480, 530),
            FreeClassroomPeriodOption("p2", "09:00 - 09:50", 540, 590)
        ),
        blockSummaries = blockSummaries
    )

    @Test
    fun testDaySelectionEmitsSelectDay() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = derivedState(), onAction = { captured.add(it) })
            }
        }

        onNodeWithText("Tue").performClick()
        assertEquals(1, captured.size)
        assertEquals("TUE", (captured.single() as M3EFreeClassroomsAction.SelectDay).day)
    }

    @Test
    fun testPeriodStepperEmitsStepPeriod() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = derivedState(), onAction = { captured.add(it) })
            }
        }

        onNodeWithContentDescription("Next period").performClick()
        assertEquals(1, captured.size)
        assertEquals(1, (captured.single() as M3EFreeClassroomsAction.StepPeriod).delta)

        onNodeWithContentDescription("Previous period").performClick()
        assertEquals(-1, (captured[1] as M3EFreeClassroomsAction.StepPeriod).delta)
    }

    @Test
    fun testBlockTileShowsRealFreeCountAndPercentage() = runComposeUiTest {
        val rooms = listOf(
            FreeClassroomRoomRow("AB3-201", "AB3", "201", isLab = false, isFree = true),
            FreeClassroomRoomRow("AB3-202", "AB3", "202", isLab = true, isFree = false)
        )
        val block = FreeClassroomBlockSummary(
            block = "AB3", total = 4, free = 1, theoryFree = 1, labFree = 0,
            rooms = rooms, freeRooms = rooms.filter { it.isFree }
        )
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = derivedState(listOf(block)),
                    onAction = {}
                )
            }
        }

        onAllNodesWithText("Rooms")[0].assertExists()
        onNodeWithText("When").assertExists()
        onNodeWithText("AB3 Block").assertExists()
        onNodeWithText("1 of 4 free").assertExists()
        onNodeWithText("25% free").assertExists()
        onNodeWithText("201").assertExists()
    }

    @Test
    fun testBlockChipFiltersAndRoomChipOpensInspector() = runComposeUiTest {
        val manyRooms = (1..13).map { n ->
            FreeClassroomRoomRow(
                code = "X-$n", block = "X", number = "$n", isLab = false, isFree = true
            )
        }
        val block = FreeClassroomBlockSummary(
            block = "X", total = 13, free = 13, theoryFree = 13, labFree = 0,
            rooms = manyRooms, freeRooms = manyRooms
        )
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(
                    state = derivedState(listOf(block)),
                    onAction = { captured.add(it) }
                )
            }
        }

        onNodeWithText("+1 more in X").assertExists()
        onAllNodesWithText("1")[0].performClick()
        assertEquals(M3EFreeClassroomsAction.SelectRoom("X-1"), captured.single())
    }

    @Test
    fun testKindFilterEmitsSetKindFilter() = runComposeUiTest {
        val captured = mutableListOf<M3EFreeClassroomsAction>()
        setContent {
            AmazeM3ETheme {
                M3EFreeClassroomsScreen(state = derivedState(), onAction = { captured.add(it) })
            }
        }

        onNodeWithText("Labs").performClick()
        assertEquals(M3EFreeClassroomsAction.SetKindFilter(M3EFreeClassroomKindFilter.LAB), captured.single())
    }
}
