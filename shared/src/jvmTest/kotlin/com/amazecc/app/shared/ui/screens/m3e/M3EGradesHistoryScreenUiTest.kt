package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryAction
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryState
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeCourse
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeRecord
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EGradesHistoryScreenUiTest {

    private fun sampleState() = M3EGradesHistoryState(
        cumulativeGpa = 9.24,
        totalCreditsEarned = 124,
        totalCreditsAttempted = 124,
        sGradesCount = 14,
        aGradesCount = 18,
        bGradesCount = 4,
        semesters = listOf(
            M3ESemesterGradeRecord(
                semesterId = "sem_winter_2024",
                semesterName = "Winter Semester 2024-25",
                sgpa = 9.35,
                cgpaAtEnd = 9.24,
                creditsEarned = 24,
                courses = listOf(
                    M3ESemesterGradeCourse(
                        courseCode = "CSE3002",
                        courseTitle = "Internet and Web Programming",
                        credits = 4,
                        grade = "S",
                        gradePoints = 10,
                        courseType = "ETH"
                    )
                )
            )
        )
    )

    @Test
    fun testGradesHistoryRendersCgpaAndGradeCounts() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Grade History & CGPA").assertExists()
        onNodeWithText("9.24").assertExists()
        onNodeWithText("124 / 124").assertExists()
        onNodeWithText("14").assertExists()
        onNodeWithText("S Grades").assertExists()
        onNodeWithText("Winter Semester 2024-25").assertExists()
        onNodeWithText("SGPA: 9.35").assertExists()
    }

    @Test
    fun testSemesterExpandEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EGradesHistoryAction>()

        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Winter Semester 2024-25").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val expandAction = capturedActions.filterIsInstance<M3EGradesHistoryAction.ToggleExpandSemester>().firstOrNull()
        assertEquals("sem_winter_2024", expandAction?.semesterId)
    }
}
