package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EExamScheduleScreenUiTest {

    private fun sampleState() = M3EExamScheduleState(
        semesterName = "Winter Semester 2025-26",
        selectedSeries = ExamSeries.ALL,
        nextUpcomingExam = M3EExamEntry(
            id = "exam_1",
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            examSeries = ExamSeries.CAT1,
            date = "12-Oct-2026",
            day = "Monday",
            session = "FN",
            slot = "B1+TB1",
            reportingTime = "09:00 AM",
            examTime = "09:30 AM - 11:15 AM",
            venue = "SJT 412",
            seatNumber = "B-14",
            daysRemaining = 2,
            isHappeningSoon = true
        ),
        exams = listOf(
            M3EExamEntry(
                id = "exam_1",
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                examSeries = ExamSeries.CAT1,
                date = "12-Oct-2026",
                day = "Monday",
                session = "FN",
                slot = "B1+TB1",
                reportingTime = "09:00 AM",
                examTime = "09:30 AM - 11:15 AM",
                venue = "SJT 412",
                seatNumber = "B-14",
                daysRemaining = 2,
                isHappeningSoon = true
            )
        ),
        filteredExams = listOf(
            M3EExamEntry(
                id = "exam_1",
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                examSeries = ExamSeries.CAT1,
                date = "12-Oct-2026",
                day = "Monday",
                session = "FN",
                slot = "B1+TB1",
                reportingTime = "09:00 AM",
                examTime = "09:30 AM - 11:15 AM",
                venue = "SJT 412",
                seatNumber = "B-14",
                daysRemaining = 2,
                isHappeningSoon = true
            )
        )
    )

    @Test
    fun testExamScheduleRendersHeaderAndUpcomingHero() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Exam Schedules").assertExists()
        onNodeWithText("Winter Semester 2025-26").assertExists()
        onNodeWithText("UPCOMING EXAM").assertExists()
        onNodeWithText("In 2 days").assertExists()
        onNodeWithText("SJT 412 | Seat: B-14").assertExists()
    }

    @Test
    fun testSeriesFilterEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EExamScheduleAction>()

        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onAllNodesWithText("CAT-1")[0].performClick()

        assertTrue(capturedActions.isNotEmpty())
        val seriesAction = capturedActions.filterIsInstance<M3EExamScheduleAction.SelectSeries>().firstOrNull()
        assertEquals(ExamSeries.CAT1, seriesAction?.series)
    }

    @Test
    fun testExportCalendarClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3EExamScheduleAction>()

        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Export to Calendar").performClick()

        assertTrue(capturedActions.contains(M3EExamScheduleAction.ExportCalendar))
    }
}
