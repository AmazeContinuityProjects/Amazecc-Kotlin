package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EHostelAction
import com.amazecc.app.shared.domain.overhaul.M3EHostelDetails
import com.amazecc.app.shared.domain.overhaul.M3EHostelState
import com.amazecc.app.shared.domain.overhaul.M3EHostelTab
import com.amazecc.app.shared.domain.overhaul.M3ELaundrySlot
import com.amazecc.app.shared.domain.overhaul.M3ELeaveRecord
import com.amazecc.app.shared.domain.overhaul.M3EMessMealItem
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies wiring remains intact while mess/laundry flows are populated. */
@OptIn(ExperimentalTestApi::class)
class M3EHostelMessLaundryActionsTest {

    private fun state(tab: M3EHostelTab) = M3EHostelState(
        hostelDetails = M3EHostelDetails(
            blockName = "Q Block",
            roomNumber = "514",
            roomType = "2 Bed AC",
            bedNumber = "B",
            messType = "NON VEG",
            messCaterer = "NON VEG",
            wardenName = "Dr. Ramesh K",
            wardenPhone = "+91 94421 00000"
        ),
        selectedTab = tab,
        todayMessMenu = listOf(
            M3EMessMealItem("Breakfast", "Monday", listOf("Idly", "Vada"))
        ),
        laundrySchedule = listOf(M3ELaundrySlot("1", "112 - 336")),
        leaves = listOf(
            M3ELeaveRecord("LV-1", "Chennai", "Family wedding", "Home", "24 Nov", "28 Nov", "Approved")
        )
    )

    @Test
    fun tabSwitchAndRefreshActionsStillEmit() = runComposeUiTest {
        val actions = mutableListOf<M3EHostelAction>()

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state(M3EHostelTab.MESS_MENU), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Leave History").performClick()
        onNodeWithContentDescription("Refresh").performClick()

        assertEquals(2, actions.size)
        assertTrue(actions[0] is M3EHostelAction.SelectTab)
        assertEquals(M3EHostelTab.LEAVE_HISTORY, (actions[0] as M3EHostelAction.SelectTab).tab)
        assertTrue(actions[1] is M3EHostelAction.Refresh)
    }

    @Test
    fun applyLeaveActionStillEmits() = runComposeUiTest {
        val actions = mutableListOf<M3EHostelAction>()

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state(M3EHostelTab.LEAVE_HISTORY), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Apply Leave").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EHostelAction.ApplyNewLeave)
    }

    @Test
    fun callWardenActionStillEmits() = runComposeUiTest {
        val actions = mutableListOf<M3EHostelAction>()

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(state = state(M3EHostelTab.ROOM_INFO), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Call Warden").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EHostelAction.CallWarden)
    }
}
