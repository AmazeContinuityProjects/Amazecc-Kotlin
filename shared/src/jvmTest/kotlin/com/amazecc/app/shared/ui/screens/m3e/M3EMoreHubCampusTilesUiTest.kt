package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3ESubScreen
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.screens.more.appLibraryItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FFCS-CAMPUS-01 — regression coverage for the campus utility the user reported as missing from
 * "Campus Features" (the More hub): Friend Finder. The stateless hub is rendered with the
 * data-driven [M3ESubScreen] catalog, and the App Library group membership is asserted from the
 * same single source of truth the palette and sheet consume. Follows the v2 headless Compose test
 * conventions.
 */
@OptIn(ExperimentalTestApi::class)
class M3EMoreHubCampusTilesUiTest {

    private val campusTileLabels = listOf("Friend Finder")

    @Test
    fun moreHubRendersFriendFinderCampusLifeTile() = runComposeUiTest {
        // Filtering by the category hides the RECENT group (only shown for a blank query) and
        // keeps the list short, so the tile is scrolled into view before its label is asserted.
        setContent {
            AmazeM3ETheme {
                M3EMoreHubScreen(
                    state = M3EMoreHubState(searchQuery = "Campus Life"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("CAMPUS LIFE").assertExists()
        campusTileLabels.forEach { label ->
            onNodeWithTag("more_hub_list").performScrollToNode(hasText(label))
            onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun campusLifeEnumEntryCarriesTheExpectedLabel() {
        assertEquals("Friend Finder", M3ESubScreen.FRIEND_FINDER.label)
        campusTileLabels.forEach { label ->
            assertTrue(
                M3ESubScreen.entries.any { it.label == label && it.category == "Campus Life" },
                "Expected a Campus Life sub-screen labelled $label"
            )
        }
    }

    @Test
    fun appLibraryCampusGroupContainsFriendFinderAndToolsDoesNot() {
        val campusLabels = appLibraryItems.filter { it.groupName == "Campus" }.map { it.label }
        campusTileLabels.forEach { label ->
            assertTrue(label in campusLabels, "Expected $label in the App Library Campus group")
            assertEquals(1, campusLabels.count { it == label }, "Expected exactly one $label tile")
        }

        // The moved tile must not be duplicated in the old Tools group.
        val toolsLabels = appLibraryItems.filter { it.groupName == "Tools" }.map { it.label }
        assertTrue("Friend Finder" !in toolsLabels)
    }
}
