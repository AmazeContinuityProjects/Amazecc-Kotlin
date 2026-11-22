package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.Assessment
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.AttendanceDay
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.Marks
import com.amazecc.app.shared.domain.overhaul.CourseDetailTab
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class M3ECourseEmbeddedMenuUiTest {

    private fun theoryHalf() = EmbeddedCourseHalf(
        code = "CSE3002(T)",
        title = "Internet and Web Programming",
        type = "Embedded Theory",
        half = "T",
        credits = "3",
        slots = listOf("B1"),
        attendance = Attendance(
            attended = 28,
            total = 30,
            percentage = "93.33",
            logs = listOf(AttendanceDay("2026-09-01", "Present")),
        ),
        marks = Marks(
            assessments = listOf(
                Assessment(name = "CAT-1", score = "45", maxMark = "50", weightagePercent = "15"),
            ),
        ),
    )

    private fun labHalf() = EmbeddedCourseHalf(
        code = "CSE3002(L)",
        title = "Internet and Web Programming Lab",
        type = "Embedded Lab",
        half = "L",
        credits = "2",
        slots = listOf("L55"),
        attendance = Attendance(
            attended = 25,
            total = 40,
            percentage = "62.5",
            logs = listOf(AttendanceDay("2026-09-02", "Absent")),
        ),
        marks = Marks(
            assessments = listOf(
                Assessment(name = "Lab CA1", score = "18", maxMark = "20", weightagePercent = "10"),
            ),
        ),
    )

    private fun embeddedState(activeTab: CourseDetailTab = CourseDetailTab.OVERVIEW) = M3ECourseDetailState(
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        credits = 5,
        courseType = "Embedded Theory and Lab",
        slot = "B1+L55",
        venue = "SJT 412",
        facultyName = "Prof. Sundar",
        attendedClasses = 53,
        totalClasses = 70,
        attendancePct = 75.71,
        bunkMarginSummary = "Safe margin (target 75%)",
        tone = M3ECardTone.GOOD,
        activeTab = activeTab,
        halves = listOf(theoryHalf(), labHalf()),
    )

    private fun standaloneState() = M3ECourseDetailState(
        courseCode = "CSE3001",
        courseTitle = "Data Structures",
        credits = 3,
        courseType = "Theory Only",
        slot = "A1",
        venue = "TT 301",
        facultyName = "Prof. Rao",
        attendedClasses = 28,
        totalClasses = 30,
        attendancePct = 93.33,
        // halves defaults to emptyList(): a standalone course has no theory/lab pair.
    )

    @Test
    fun embeddedCourseShowsAllFourComponentMenuRows() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(state = embeddedState(), onAction = {})
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Course sections"))
        onNodeWithText("Theory Log").assertExists()
        onNodeWithText("Lab Log").assertExists()
        onNodeWithText("Theory Assessments").assertExists()
        onNodeWithText("Lab Assessments").assertExists()
        // Marks/grades stay common: the menu has one Marks row, not per-half grades.
        onNodeWithText("Marks Breakdown").assertExists()
        onNodeWithText("Grade Predictor").assertExists()
    }

    @Test
    fun selectingLabLogShowsTheLabHalvesRealAttendance() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(state = embeddedState(), onAction = {})
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Lab Log"))
        onNodeWithText("Lab Log").performClick()

        // Real lab-half numbers (25/40, 62.5%) and its own log — not the combined course figure.
        onNodeWithText("Lab attendance").assertExists()
        onNodeWithText("62.50%").assertExists()
        onNodeWithText("25/40 classes attended").assertExists()
        onNodeWithText("2026-09-02 · Absent").assertExists()
        // Lab margin halves the session count: 20 needed sessions -> 10 lab meetings.
        onNodeWithText("Need 10 more labs to reach 75%").assertExists()

        onNodeWithContentDescription("Back").performClick()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Course sections"))
        onNodeWithText("Course sections").assertExists()
    }

    @Test
    fun selectingTheoryAssessmentsShowsOnlyTheoryAssessments() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(state = embeddedState(), onAction = {})
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Theory Assessments"))
        onNodeWithText("Theory Assessments").performClick()

        onNodeWithText("Theory assessment breakdown").assertExists()
        onNodeWithText("CAT-1").assertExists()
        onNodeWithText("Lab CA1").assertDoesNotExist()
    }

    @Test
    fun nonEmbeddedCourseDoesNotShowComponentRows() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(state = standaloneState(), onAction = {})
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Course sections"))
        onNodeWithText("Theory Log").assertDoesNotExist()
        onNodeWithText("Lab Log").assertDoesNotExist()
        onNodeWithText("Theory Assessments").assertDoesNotExist()
        onNodeWithText("Lab Assessments").assertDoesNotExist()
        // The common academics rows remain real for a standalone course.
        onNodeWithText("Marks Breakdown").assertExists()
    }

    @Test
    fun standaloneLabLogUsesTheStandaloneCourseAttendance() = runComposeUiTest {
        val state = standaloneState().copy(
            courseType = "Lab Only",
            slot = "L55",
            standaloneAttendance = labHalf().attendance
        )
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(state = state, onAction = {})
            }
        }

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Attendance Log"))
        onNodeWithText("Attendance Log").performClick()

        onNodeWithText("Lab attendance").assertExists()
        onNodeWithText("25/40 classes attended").assertExists()
        onNodeWithText("Course sections").assertDoesNotExist()
    }

    @Test
    fun embeddedMarksSectionDeclaresTheCreditWeightedBlend() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECourseDetailScreen(
                    state = embeddedState(activeTab = CourseDetailTab.MARKS),
                    onAction = {}
                )
            }
        }

        onNodeWithText(
            "Theory and lab credits are combined into one weighted score on a single grading ladder."
        ).assertExists()
    }
}
