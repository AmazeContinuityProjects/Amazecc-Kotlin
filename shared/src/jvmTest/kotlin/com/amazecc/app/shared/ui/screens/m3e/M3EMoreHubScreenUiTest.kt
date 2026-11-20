package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubAction
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3ESubScreen
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EMoreHubScreenUiTest {

    @Test
    fun testMoreHubRendersCategoriesAndFeatures() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EMoreHubScreen(
                    state = M3EMoreHubState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Campus Features").assertExists()
        onNodeWithText("Explore all services & campus utilities").assertExists()
        onNodeWithText("ACADEMICS").assertExists()
        // "Course Detail" legitimately appears twice: once in the RECENT group
        // and once in the ACADEMICS category. Assert both cards are present by
        // exact count rather than demanding a single unique node.
        onAllNodesWithText("Course Detail").assertCountEquals(2)
        // The category list is lazy: scroll each far-down tile into view before
        // asserting it, rather than assuming all rows are composed at once.
        onNodeWithTag("more_hub_list").performScrollToNode(hasText("Faculty Directory"))
        onNodeWithText("Faculty Directory").assertExists()
        onNodeWithTag("more_hub_list").performScrollToNode(hasText("Degree Curriculum"))
        onNodeWithText("Degree Curriculum").assertExists()
        onNodeWithTag("more_hub_list").performScrollToNode(hasText("Course Dashboard"))
        onNodeWithText("Course Dashboard").assertExists()
    }

    @Test
    fun testClickFeatureEmitsOpenSubScreen() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMoreHubAction>()

        setContent {
            AmazeM3ETheme {
                M3EMoreHubScreen(
                    state = M3EMoreHubState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Course Detail").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EMoreHubAction.OpenSubScreen)
        assertEquals(M3ESubScreen.COURSE_DETAIL, (capturedActions[0] as M3EMoreHubAction.OpenSubScreen).screen)
    }

    @Test
    fun testSearchQueryFilterEmitted() = runComposeUiTest {
        val filteredState = M3EMoreHubState(searchQuery = "Hostel")

        setContent {
            AmazeM3ETheme {
                M3EMoreHubScreen(
                    state = filteredState,
                    onAction = {}
                )
            }
        }

        onNodeWithText("Hostel & Mess").assertExists()
        onNodeWithText("CAMPUS LIFE").assertExists()
    }

    @Test
    fun testSearchODTrackerEmitsSubScreen() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMoreHubAction>()
        val filteredState = M3EMoreHubState(searchQuery = "approvals")

        setContent {
            AmazeM3ETheme {
                M3EMoreHubScreen(
                    state = filteredState,
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("OD Tracker").assertExists()
        onNodeWithText("OD Tracker").performClick()
        assertEquals(1, capturedActions.size)
        assertTrue(capturedActions[0] is M3EMoreHubAction.OpenSubScreen)
        assertEquals(M3ESubScreen.OD_TRACKER, (capturedActions[0] as M3EMoreHubAction.OpenSubScreen).screen)
    }
}
