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
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EMarksPredictorScreenUiTest {

    private fun sampleState() = M3EMarksPredictorState(
        predictedGpa = 9.25,
        totalCreditsSimulated = 24,
        courses = listOf(
            M3ECoursePredictionItem(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                credits = 4,
                internalScored = 46.0,
                internalMax = 50.0,
                selectedGrade = "S",
                requiredFatMark = 82.0,
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

        onNodeWithText("Marks & GPA Predictor").assertExists()
        onNodeWithText("9.25").assertExists()
        onNodeWithText("24").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("82 / 100").assertExists()
    }

    @Test
    fun testGradeSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMarksPredictorAction>()

        setContent {
            AmazeM3ETheme {
                M3EMarksPredictorScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNode(hasText("A") and hasClickAction()).performClick()

        assertTrue(capturedActions.isNotEmpty())
        val gradeAction = capturedActions.filterIsInstance<M3EMarksPredictorAction.SetCourseGrade>().firstOrNull()
        assertEquals("CSE3002", gradeAction?.courseCode)
        assertEquals("A", gradeAction?.grade)
    }

    @Test
    fun testResetAllClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EMarksPredictorAction>()

        setContent {
            AmazeM3ETheme {
                M3EMarksPredictorScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Reset Targets").performClick()

        assertTrue(capturedActions.contains(M3EMarksPredictorAction.ResetAll))
    }
}
