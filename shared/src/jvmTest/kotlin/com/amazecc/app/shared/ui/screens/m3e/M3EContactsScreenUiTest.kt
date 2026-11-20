package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless JVM UI tests for the dedicated Contacts page (D-110). */
@OptIn(ExperimentalTestApi::class)
class M3EContactsScreenUiTest {

    private fun sampleState() = M3EContactsState(
        contacts = listOf(
            M3EContactCard(
                id = "accommodation",
                office = "Accommodation Office",
                description = "Hostel allotment queries",
                email = "hostel@vit.ac.in"
            ),
            M3EContactCard(
                id = "examinations",
                office = "Examination Office",
                description = "Seating and hall tickets"
            )
        )
    )

    @Test
    fun oneCardPerRealContactWithDescriptionBody() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EContactsScreen(state = sampleState(), onAction = {})
            }
        }

        onNodeWithText("Contacts").assertExists()
        onAllNodesWithTag(M3EContactsTestTags.CONTACT_CARD).assertCountEquals(2)
        onNodeWithText("Accommodation Office").assertExists()
        // One description title above each body — the two are stacked, not side by side.
        onAllNodesWithText("Description").assertCountEquals(2)
        onNodeWithText("Hostel allotment queries").assertExists()
        onNodeWithText("hostel@vit.ac.in").assertExists()
        onNodeWithText("Examination Office").assertExists()
        // No email is fabricated for a contact without one.
        onNodeWithText("seating@vit.ac.in").assertDoesNotExist()
    }

    @Test
    fun emailAffordanceEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EContactsAction>()
        setContent {
            AmazeM3ETheme {
                M3EContactsScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Email hostel@vit.ac.in").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EContactsAction.OpenEmail)
        assertEquals("hostel@vit.ac.in", (actions[0] as M3EContactsAction.OpenEmail).email)
    }

    @Test
    fun refreshEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EContactsAction>()
        setContent {
            AmazeM3ETheme {
                M3EContactsScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Refresh contacts").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EContactsAction.Refresh)
    }

    @Test
    fun emptyStateIsShownWhenNoContacts() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EContactsScreen(state = M3EContactsState(), onAction = {})
            }
        }

        onNodeWithText("No campus contacts available").assertExists()
        onAllNodesWithTag(M3EContactsTestTags.CONTACT_CARD).assertCountEquals(0)
    }

    @Test
    fun errorMessageIsShown() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EContactsScreen(
                    state = M3EContactsState(errorMessage = "Could not load contacts"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Could not load contacts").assertExists()
    }
}
