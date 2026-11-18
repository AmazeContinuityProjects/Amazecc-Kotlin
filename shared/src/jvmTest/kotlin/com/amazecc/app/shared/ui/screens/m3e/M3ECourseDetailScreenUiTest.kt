package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        onNodeWithText("Prof. Sundar").assertExists()
        onNodeWithText("93.3%").assertExists()
        onNodeWithText("Slot B1+TB1").assertExists()
    }

    @Test
    fun testTabSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECourseDetailAction>()

        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Marks").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val tabAction = capturedActions.filterIsInstance<M3ECourseDetailAction.SelectTab>().firstOrNull()
        assertEquals(CourseDetailTab.MARKS, tabAction?.tab)
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
