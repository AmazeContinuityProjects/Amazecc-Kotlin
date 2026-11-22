package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.CourseDetailTab
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailAction
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ECourseDetailScreenUiTest {

    private fun sampleState() = M3ECourseDetailState(
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        credits = 4,
        courseType = "ETH",
        slot = "B1+TB1",
        venue = "SJT 412",
        facultyName = "Prof. Sundar",
        facultyEmail = "sundar@vit.ac.in",
        facultyCabin = "SJT 412-A",
        facultyDesignation = "Associate Professor",
        attendedClasses = 28,
        totalClasses = 30,
        attendancePct = 93.3,
        bunkMarginSummary = "Can bunk 6 more classes",
        tone = M3ECardTone.GOOD,
        internalScored = 46.5,
        internalMax = 50.0,
        targetGrade = "S"
    )

    @Test
    fun testCourseDetailRendersHeaderAndOverview() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("4 Credits").assertExists()
        onNodeWithText("93.30%").assertExists()
        onNodeWithText("Slot B1+TB1", substring = true).assertExists()
    }

    @Test
    fun marksOpensAsASectionAndBackReturnsToCourseOverview() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECourseDetailAction>()
        var state by mutableStateOf(sampleState())

        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(
                    state = state,
                    onAction = { action ->
                        capturedActions.add(action)
                        if (action is M3ECourseDetailAction.SelectTab) {
                            state = state.copy(activeTab = action.tab)
                        }
                    }
                )
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Marks"))
        onNodeWithText("Marks").performClick()
        onNodeWithText("Total Internal Score").assertExists()
        onNodeWithText("Course sections").assertDoesNotExist()

        onNodeWithContentDescription("Back").performClick()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Course sections"))
        onNodeWithText("Course sections").assertExists()
        val expectedActions = listOf<M3ECourseDetailAction>(
            M3ECourseDetailAction.SelectTab(CourseDetailTab.MARKS),
            M3ECourseDetailAction.SelectTab(CourseDetailTab.OVERVIEW)
        )
        assertEquals(expectedActions, capturedActions.toList())
    }

    @Test
    fun gradesOpensThePublishedResult() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(state = sampleState().copy(publishedGrade = "A"), onAction = {})
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Grades"))
        onNodeWithText("Grades").performClick()

        onNodeWithText("Final grade: A").assertExists()
        onNodeWithText("No published final grade for this course").assertDoesNotExist()
    }

    @Test
    fun testBackButtonClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECourseDetailAction>()

        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        // Back icon button has contentDescription="Back"
        onNodeWithContentDescription("Back").performClick()

        assertTrue(capturedActions.contains(M3ECourseDetailAction.Back))
    }
}
