package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECoursePredictionItem
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorState
import com.amazecc.app.shared.domain.overhaul.M3EPredictedGpaBreakdown
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EMarksPredictorScreenUiTest {

    private fun sampleState() = M3EMarksPredictorState(
        gpa = M3EPredictedGpaBreakdown(
            optimalSgpa = 9.25,
            maximisableSgpa = 9.75,
            optimalCredits = 20,
            maximisableCredits = 24,
            semesterCredits = 24,
            optimalCgpa = 9.15,
            desirableCgpa = 9.40,
            optimalCgpaIsAnchor = true,
            cumulativeCredits = 60.0
        ),
        courses = listOf(
            M3ECoursePredictionItem(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                credits = 4,
                courseType = "Embedded Theory",
                typeLabel = "ETL",
                internalScored = 46.0,
                internalMax = 50.0,
                pointsLost = 4.0,
                predictedGrade = "S",
                maximisableGrade = "S",
                tone = M3ECardTone.GOOD
            )
        )
    )

    @Test
    fun testMarksPredictorRendersGpaAndCourse() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EMarksPredictorScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Marks Predictor").assertExists()
        onNodeWithText("SGPA PROJECTED").assertExists()
        onNodeWithText("9.25").assertExists()
        onNodeWithText("CGPA PROJECTED").assertExists()
        onNodeWithText("9.15").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("CSE3002", substring = true).assertExists()
        onNodeWithText("-4.0").assertExists()
        onNodeWithText("pts lost").assertExists()
    }

    @Test
    fun testCourseRowClickEmitsOpenCourse() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMarksPredictorAction>()

        setContent {
            AmazeM3ETheme {
                M3EMarksPredictorScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Internet and Web Programming").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val openAction = capturedActions.filterIsInstance<M3EMarksPredictorAction.OpenCourse>().firstOrNull()
        assertEquals("CSE3002", openAction?.courseCode)
    }

    @Test
    fun testEditModeToggleEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMarksPredictorAction>()

        setContent {
            AmazeM3ETheme {
                M3EMarksPredictorScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Edit subjects").performClick()

        assertTrue(capturedActions.any { it is M3EMarksPredictorAction.ToggleEditMode && it.enabled })
    }
}
