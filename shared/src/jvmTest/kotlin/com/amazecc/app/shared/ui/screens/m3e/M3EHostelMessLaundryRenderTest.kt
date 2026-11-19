package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EHostelDetails
import com.amazecc.app.shared.domain.overhaul.M3EHostelState
import com.amazecc.app.shared.domain.overhaul.M3EHostelTab
import com.amazecc.app.shared.domain.overhaul.M3ELaundrySlot
import com.amazecc.app.shared.domain.overhaul.M3EMessMealItem
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test

/**
 * Headless (v2) coverage for the hostel hub's real scraped mess and laundry data.
 * The stateless screen is driven by the exact shapes the HOSTEL bridge produces.
 */
@OptIn(ExperimentalTestApi::class)
class M3EHostelMessLaundryRenderTest {

    private fun baseState() = M3EHostelState(
        hostelDetails = M3EHostelDetails(
            blockName = "Q Block",
            roomNumber = "514",
            roomType = "2 Bed AC",
            bedNumber = "B",
            messType = "NON VEG",
            messCaterer = "NON VEG"
        ),
        selectedTab = M3EHostelTab.MESS_MENU
    )

    @Test
    fun realMessMenuDishesRender() = runComposeUiTest {
        // Numbered dish strings from the real MessMenuRes payload are split by the bridge.
        val state = baseState().copy(
            todayMessMenu = listOf(
                M3EMessMealItem(
                    mealName = "Breakfast",
                    timing = "Monday",
                    items = listOf("Idly", "Vada", "Sambar"),
                    isCurrent = true
                ),
                M3EMessMealItem(
                    mealName = "Lunch",
                    timing = "Monday",
                    items = listOf("Chapathi", "Veg Kurma")
                )
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Today's Menu (NON VEG)").assertExists()
        onNodeWithText("Breakfast").assertExists()
        onNodeWithText("Idly").assertExists()
        onNodeWithText("Vada").assertExists()
        onNodeWithText("Sambar").assertExists()
        onNodeWithText("NOW SERVING").assertExists()
        onNodeWithText("Lunch").assertExists()
        onNodeWithText("Veg Kurma").assertExists()
    }

    @Test
    fun realLaundryScheduleRendersInRoomInfo() = runComposeUiTest {
        val state = baseState().copy(
            selectedTab = M3EHostelTab.ROOM_INFO,
            laundrySchedule = listOf(
                M3ELaundrySlot(date = "1", roomNumber = "112 - 336"),
                M3ELaundrySlot(date = "2", roomNumber = "337 - 561")
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Laundry Schedule (2)").assertExists()
        onNodeWithText("Date 1").assertExists()
        onNodeWithText("Rooms 112 - 336").assertExists()
        onNodeWithText("Date 2").assertExists()
        onNodeWithText("Rooms 337 - 561").assertExists()
    }

    @Test
    fun nullMessFlowKeepsEmptyState() = runComposeUiTest {
        // Empty list == AppState.messMenu null/empty with no matching day row.
        val state = baseState()

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("No mess menu available for today").assertExists()
    }

    @Test
    fun nullLaundryFlowKeepsEmptyState() = runComposeUiTest {
        // Empty list == AppState.laundrySchedule null/empty.
        val state = baseState().copy(selectedTab = M3EHostelTab.ROOM_INFO)

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("No laundry schedule available").assertExists()
    }
}
