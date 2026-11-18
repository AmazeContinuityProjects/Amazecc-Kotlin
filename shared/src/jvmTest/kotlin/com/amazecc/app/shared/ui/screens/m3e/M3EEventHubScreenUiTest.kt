package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EEventCategory
import com.amazecc.app.shared.domain.overhaul.M3EEventHubAction
import com.amazecc.app.shared.domain.overhaul.M3EEventHubState
import com.amazecc.app.shared.domain.overhaul.M3EEventItem
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EEventHubScreenUiTest {

    private fun sampleEventState() = M3EEventHubState(
        events = listOf(
            M3EEventItem(
                id = "evt_01",
                title = "Battle of the Bands",
                festName = "Riviera 2027",
                category = M3EEventCategory.CULTURAL,
                date = "15 Feb 2027",
                time = "05:30 PM",
                venue = "Outdoor Stadium",
                price = "Free Entry",
                isFree = true,
                isRegistered = false,
                certificateEligible = true
            )
        ),
        filteredEvents = listOf(
            M3EEventItem(
                id = "evt_01",
                title = "Battle of the Bands",
                festName = "Riviera 2027",
                category = M3EEventCategory.CULTURAL,
                date = "15 Feb 2027",
                time = "05:30 PM",
                venue = "Outdoor Stadium",
                price = "Free Entry",
                isFree = true,
                isRegistered = false,
                certificateEligible = true
            )
        ),
        selectedCategory = M3EEventCategory.ALL,
        searchQuery = "",
        registeredCount = 0
    )

    @Test
    fun testEventHubRendersEventsAndTags() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = sampleEventState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Event Hub").assertExists()
        onNodeWithText("Battle of the Bands").assertExists()
        onNodeWithText("RIVIERA 2027").assertExists()
        onNodeWithText("Outdoor Stadium").assertExists()
        onNodeWithText("Free Entry").assertExists()
        onNodeWithText("Register").assertExists()
    }

    @Test
    fun testRegisterActionEmitted() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EEventHubAction>()

        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = sampleEventState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Register").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EEventHubAction.RegisterEvent)
        assertEquals("evt_01", (capturedActions[0] as M3EEventHubAction.RegisterEvent).eventId)
    }

    @Test
    fun testCategoryFilterSelectionEmitted() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EEventHubAction>()

        setContent {
            AmazeM3ETheme {
                M3EEventHubScreen(
                    state = sampleEventState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onAllNodesWithText("Cultural")[0].performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EEventHubAction.SelectCategory)
        assertEquals(M3EEventCategory.CULTURAL, (capturedActions[0] as M3EEventHubAction.SelectCategory).category)
    }
}
