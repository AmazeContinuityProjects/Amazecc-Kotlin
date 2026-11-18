package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
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
        onNodeWithText("Course Detail").assertExists()
        onNodeWithText("Faculty Directory").assertExists()
        onNodeWithText("Degree Curriculum").assertExists()
        onNodeWithText("FAT Marks Predictor").assertExists()
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
}
