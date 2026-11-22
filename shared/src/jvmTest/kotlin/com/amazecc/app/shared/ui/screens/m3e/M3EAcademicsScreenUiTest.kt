package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.AcademicCourseFilter
import com.amazecc.app.shared.domain.overhaul.M3EAcademicCourse
import com.amazecc.app.shared.domain.overhaul.M3EAcademicSummary
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsAction
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsState
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EAcademicsScreenUiTest {

    private fun sampleState() = M3EAcademicsState(
        semesterName = "Winter Semester 2025-26",
        summary = M3EAcademicSummary(
            cgpa = 9.24,
            creditsEarned = 124,
            creditsRegistered = 24,
            rankOrStanding = "Top 5% in Program"
        ),
        courses = listOf(
            M3EAcademicCourse(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                credits = 4,
                courseType = "ETH",
                faculty = "Prof. Sundar",
                internalScored = 45.5,
                internalMax = 50.0,
                assessments = listOf(
                    M3EAssessmentItem(
                        title = "Continuous Assessment 1",
                        maxMarks = 50.0,
                        scoredMarks = 46.0,
                        weightagePercent = 15.0,
                        weightageMarks = 13.8
                    )
                )
            ),
            M3EAcademicCourse(
                courseCode = "MAT2001",
                courseTitle = "Complex Variables & Linear Algebra",
                credits = 4,
                courseType = "TH",
                faculty = "Dr. Anita",
                grade = "A",
                internalScored = 52.0,
                internalMax = 60.0
            )
        )
    )

    @Test
    fun testAcademicsRendersCgpaAndCourses() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EAcademicsScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Academics").assertExists()
        onNodeWithText("9.24").assertExists()
        onNodeWithText("Cumulative GPA").assertExists()
        onNodeWithText("124").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("Grade: A").assertExists()
    }

    @Test
    fun testCourseExpansionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EAcademicsAction>()

        setContent {
            AmazeM3ETheme {
                M3EAcademicsScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("CSE3002").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val toggleAction = capturedActions.filterIsInstance<M3EAcademicsAction.ToggleExpandCourse>().firstOrNull()
        assertEquals("CSE3002", toggleAction?.courseCode)
    }

    @Test
    fun repeatedCourseOpensTheSemesterOfTheExpandedRow() = runComposeUiTest {
        val state = sampleState()
        val current = state.courses.first().copy(semesterId = "term-new", semesterLabel = "Repeat term")
        val previous = current.copy(semesterId = "term-old", faculty = "Earlier faculty")
        var opened: M3EAcademicCourse? = null
        setContent {
            AmazeM3ETheme {
                M3EAcademicsScreen(
                    state = state.copy(
                        courses = listOf(current, previous),
                        expandedCourseCode = previous.courseCode,
                        expandedCourseSemesterId = previous.semesterId,
                        showAllSemesters = true
                    ),
                    onAction = {},
                    onOpenCourse = { opened = it }
                )
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Open course detail", substring = true))
        onNodeWithText("Open course detail", substring = true).performClick()
        runOnIdle { assertEquals(previous, opened) }
    }

    @Test
    fun testFilterSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EAcademicsAction>()

        setContent {
            AmazeM3ETheme {
                M3EAcademicsScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Theory").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val filterAction = capturedActions.filterIsInstance<M3EAcademicsAction.SetFilter>().firstOrNull()
        assertEquals(AcademicCourseFilter.THEORY, filterAction?.filter)
    }
}
