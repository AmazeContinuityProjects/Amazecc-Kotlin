package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeInsightSlide
import com.amazecc.app.shared.domain.overhaul.M3EGradeScreen
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermOption
import com.amazecc.app.shared.domain.overhaul.M3EGradeTone
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
        totalCreditsAttempted = 130,
        sGradesCount = 14,
        aGradesCount = 18,
        bGradesCount = 4,
        semesters = listOf(
            M3ESemesterGradeRecord(
                semesterId = "CH20252701",
                semesterName = "Winter 2026-27",
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
        ),
        cumulativeValue = "9.24",
        cumulativeBadge = "124 / 160 cr",
        cumulativeSubtitle = "Credit-weighted CGPA",
        termOptions = listOf(M3EGradeTermOption("CH20252701", "Winter 2026-27", "Winter")),
        selectedTermId = "CH20252701",
        courseRows = listOf(
            M3EGradeCourseRow(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                grandTotal = "92.5%",
                score = 92.5,
                grade = "S",
                tone = M3EGradeTone.AMBER,
                isEmbedded = false,
                courseType = "ETH"
            )
        ),
        insightSlides = listOf(
            M3EGradeInsightSlide("term", "This term", "9.35", "Winter 2026-27 · 1 courses", "GPA", M3EGradeTone.GREEN)
        )
    )

    @Test
    fun overviewRendersCumulativeTileAndCourseRow() = runComposeUiTest {
        setContent {
            AmazeM3ETheme { M3EGradesHistoryScreen(state = sampleState(), onAction = {}) }
        }

        onNodeWithText("Grade History").assertExists()
        onNodeWithText("9.24").assertExists()
        onNodeWithText("124 / 160 cr").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
    }

    @Test
    fun tappingCourseRowEmitsOpenCourse() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("CSE3002").performClick()
        assertTrue(actions.any { it is M3EGradesHistoryAction.OpenCourse })
        assertEquals("CSE3002", (actions.first { it is M3EGradesHistoryAction.OpenCourse } as M3EGradesHistoryAction.OpenCourse).courseCode)
    }

    @Test
    fun insightsScreenShowsDistributionHeading() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(
                    state = sampleState().copy(screen = M3EGradeScreen.INSIGHTS),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Grade Insights").assertExists()
        onNodeWithText("Grade distribution").assertExists()
    }

    @Test
    fun subpageBackButtonEmitsBackToOverview() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(
                    state = sampleState().copy(screen = M3EGradeScreen.INSIGHTS),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Back").performClick()
        assertTrue(actions.contains(M3EGradesHistoryAction.BackToOverview))
    }

    @Test
    fun syncActionEmitsRefreshOnOverview() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Sync grades").performClick()
        assertTrue(actions.contains(M3EGradesHistoryAction.Refresh))
    }
}
