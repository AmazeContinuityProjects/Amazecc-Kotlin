package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EHostelAction
import com.amazecc.app.shared.domain.overhaul.M3EHostelDetails
import com.amazecc.app.shared.domain.overhaul.M3EHostelState
import com.amazecc.app.shared.domain.overhaul.M3EHostelTab
import com.amazecc.app.shared.domain.overhaul.M3ELeaveRecord
import com.amazecc.app.shared.domain.overhaul.M3EMessMealItem
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EHostelScreenUiTest {

    private fun sampleHostelState() = M3EHostelState(
        hostelDetails = M3EHostelDetails(
            blockName = "Q Block (Men's Hostel)",
            roomNumber = "514",
            roomType = "2 Bed AC",
            bedNumber = "Bed B",
            messType = "Special Mess",
            messCaterer = "PR Food Services Pvt Ltd",
            wardenName = "Dr. S. Ramesh",
            wardenPhone = "+91 94441 23456"
        ),
        selectedTab = M3EHostelTab.MESS_MENU,
        todayMessMenu = listOf(
            M3EMessMealItem(
                mealName = "Lunch",
                timing = "12:00 PM - 02:00 PM",
                items = listOf("Jeera Rice & Dal Tadka", "Paneer Butter Masala"),
                isCurrent = true
            )
        ),
        leaves = listOf(
            M3ELeaveRecord(
                leaveId = "LV-8812",
                visitPlace = "Chennai, Tamil Nadu",
                reason = "Family wedding celebration",
                leaveType = "Home Leave",
                fromDate = "24 Nov 2026",
                toDate = "28 Nov 2026",
                status = "Approved"
            )
        )
    )

    @Test
    fun testHostelScreenRendersBlockAndMessMenu() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(
                    state = sampleHostelState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Campus Residence").assertExists()
        onNodeWithText("Q Block (Men's Hostel) • Room 514").assertExists()
        onNodeWithText("Q Block (Men's Hostel)").assertExists()
        onNodeWithText("Room 514").assertExists()
        onNodeWithText("Lunch").assertExists()
        onNodeWithText("Jeera Rice & Dal Tadka").assertExists()
        onNodeWithText("NOW SERVING").assertExists()
    }

    @Test
    fun testTabSwitchActionEmitted() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EHostelAction>()

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(
                    state = sampleHostelState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onAllNodesWithText("Leave History")[0].performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EHostelAction.SelectTab)
        assertEquals(M3EHostelTab.LEAVE_HISTORY, (capturedActions[0] as M3EHostelAction.SelectTab).tab)
    }

    @Test
    fun testLeaveHistoryRendersLeaveCards() = runComposeUiTest {
        val stateWithLeaves = sampleHostelState().copy(selectedTab = M3EHostelTab.LEAVE_HISTORY)

        setContent {
            AmazeM3ETheme {
                M3EHostelScreen(
                    state = stateWithLeaves,
                    onAction = {}
                )
            }
        }

        onNodeWithText("Online Leaves (1)").assertExists()
        onNodeWithText("Chennai, Tamil Nadu").assertExists()
        onNodeWithText("Reason: Family wedding celebration").assertExists()
        onNodeWithText("APPROVED").assertExists()
    }
}
