package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
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
        onNodeWithText("60 seats").assertExists()
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
}
