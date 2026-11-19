package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamStatus
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.domain.overhaul.M3EExamSeriesGroup
import com.amazecc.app.shared.domain.overhaul.M3EExamStatusCounts
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless Compose tests for the stateless exam screen: grouped timeline, semantic status,
 * loading / empty / error states, and every emitted action.
 */
@OptIn(ExperimentalTestApi::class)
class M3EExamScheduleUiStateTest {

    private val upcoming = M3EExamEntry(
        id = "cat_1",
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        examSeries = ExamSeries.CAT1,
        date = "19-Nov-2025",
        day = "Wednesday",
        session = "FN",
        slot = "B1+TB1",
        reportingTime = "09:00 AM",
        examTime = "09:15 AM - 11:00 AM",
        venue = "SJT 412",
        venueDisplay = "SJT 412",
        seatNumber = "B-14",
        seatLocation = "R9C2",
        daysRemaining = 2,
        status = ExamStatus.UPCOMING,
        countdownLabel = "In 2 days"
    )

    private val completed = M3EExamEntry(
        id = "fat_1",
        courseCode = "MAT2001",
        courseTitle = "Complex Variables",
        examSeries = ExamSeries.FAT,
        date = "12-Nov-2025",
        day = "Wednesday",
        session = "FN",
        slot = "A1",
        reportingTime = "09:00 AM",
        examTime = "09:30 AM - 01:00 PM",
        venue = "TT 301",
        venueDisplay = "TT 301",
        seatNumber = "5",
        seatLocation = "R1C5",
        status = ExamStatus.COMPLETED,
        countdownLabel = "Completed"
    )

    private fun baseState() = M3EExamScheduleState(
        semesterName = "Winter Semester 2025-26",
        selectedSeries = ExamSeries.ALL,
        nextUpcomingExam = upcoming,
        exams = listOf(upcoming, completed),
        filteredExams = listOf(upcoming, completed),
        groupedEntries = listOf(
            M3EExamSeriesGroup(ExamSeries.CAT1, ExamSeries.CAT1.label, listOf(upcoming)),
            M3EExamSeriesGroup(ExamSeries.FAT, ExamSeries.FAT.label, listOf(completed))
        ),
        availableSeries = ExamSeries.entries.toList(),
        statusCounts = M3EExamStatusCounts(upcoming = 1, today = 0, completed = 1)
    )

    @Test
    fun rendersGroupedTimelineWithHeroStatusAndDetailRows() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(state = baseState(), onAction = {})
            }
        }

        onNodeWithText("Exam Schedules").assertExists()
        onNodeWithText("Winter Semester 2025-26").assertExists()
        onNodeWithText("UPCOMING EXAM").assertExists()
        onNodeWithText("SJT 412 | Seat: B-14 (R9C2)").assertExists()
        onNodeWithText("CSE3002 • Internet and Web Programming").assertExists()
        onNodeWithText("MAT2001").assertExists()
        onNodeWithText("Complex Variables").assertExists()
        onAllNodesWithText("Reporting").assertCountEquals(2)
        onAllNodesWithText("Exam time").assertCountEquals(2)
        onAllNodesWithText("Seat").assertCountEquals(2)
    }

    @Test
    fun seriesSelectionEmitsSelectSeriesAction() = runComposeUiTest {
        val actions = mutableListOf<M3EExamScheduleAction>()
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(state = baseState(), onAction = { actions.add(it) })
            }
        }

        onAllNodesWithText("CAT-2")[0].performClick()

        assertTrue(actions.isNotEmpty())
        assertEquals(ExamSeries.CAT2, actions.filterIsInstance<M3EExamScheduleAction.SelectSeries>().first().series)
    }

    @Test
    fun refreshEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EExamScheduleAction>()
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(state = baseState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Refresh exam schedule").performClick()

        assertTrue(actions.contains(M3EExamScheduleAction.Refresh))
    }

    @Test
    fun hallTicketActionIsEmittedOnlyWhenUrlIsPresent() = runComposeUiTest {
        val actions = mutableListOf<M3EExamScheduleAction>()
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = baseState().copy(hallTicketUrl = "https://vtop.example/hall-ticket"),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Hall ticket").performClick()

        val open = actions.filterIsInstance<M3EExamScheduleAction.OpenHallTicket>().firstOrNull()
        assertEquals("https://vtop.example/hall-ticket", open?.url)
    }

    @Test
    fun loadingStateShowsWavyProgressAndHidesRows() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = baseState().copy(isLoading = true, errorMessage = null),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Syncing exam schedule…").assertExists()
        onNodeWithText("MAT2001").assertDoesNotExist()
    }

    @Test
    fun errorStateOffersRetryThatEmitsRefresh() = runComposeUiTest {
        val actions = mutableListOf<M3EExamScheduleAction>()
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = baseState().copy(isLoading = false, errorMessage = "Network down"),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Network down").assertExists()
        onNodeWithText("Try again").performClick()
        assertTrue(actions.contains(M3EExamScheduleAction.Refresh))
    }

    @Test
    fun emptyStateNamesTheCurrentSeries() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EExamScheduleScreen(
                    state = baseState().copy(
                        nextUpcomingExam = null,
                        filteredExams = emptyList(),
                        groupedEntries = emptyList(),
                        errorMessage = null
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("No exams scheduled for All Exams").assertExists()
    }
}
