package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ETimetableAction
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.domain.overhaul.M3ETimetableState
import com.amazecc.app.shared.domain.overhaul.TimetableViewMode
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The home full-timetable sheet. Covers the real sheet chrome, both connected
 * button groups, the exact actions they emit, and that the chosen dense grid is the
 * one actually mounted. State is real-shaped (non-empty days and slots) — no fakes.
 */
@OptIn(ExperimentalTestApi::class)
class M3ETimetableSheetUiTest {

    private fun sampleState() = M3ETimetableState(
        semesterName = "Winter Semester 2025-26",
        selectedDayIndex = 0,
        totalCredits = 24,
        totalCourses = 2,
        days = listOf(
            M3ETimetableDay(
                dayName = "Monday",
                shortName = "Mon",
                dayNumber = 1,
                isToday = true,
                isSelected = true,
                slots = listOf(
                    M3ETimetableSlot(
                        id = "slot_1",
                        slot = "A1",
                        courseCode = "CSE3002",
                        courseTitle = "Internet and Web Programming",
                        venue = "SJT 412",
                        faculty = "Prof. Sundar",
                        startTime = "08:00",
                        endTime = "08:50",
                        attendanceTone = M3ECardTone.GOOD
                    ),
                    M3ETimetableSlot(
                        id = "slot_2",
                        slot = "B1+TB1",
                        courseCode = "CSE2005",
                        courseTitle = "Operating Systems",
                        venue = "TT 314",
                        faculty = "Dr. Anita",
                        startTime = "09:00",
                        endTime = "09:50",
                        isHappeningNow = true,
                        attendanceTone = M3ECardTone.WARNING
                    )
                )
            ),
            M3ETimetableDay(
                dayName = "Tuesday",
                shortName = "Tue",
                dayNumber = 2,
                isToday = false,
                isSelected = false
            )
        )
    )

    @Test
    fun sheetShowsTitleSubtitleAndBothSwitchers() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableSheet(
                    state = sampleState(),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        // Web parity copy, owned by this sheet (title by M3EDismissibleSheet, subtitle here).
        onNodeWithText("Full Weekly Timetable").assertExists()
        onNodeWithText("Slot matrix, timeslots & classroom venues").assertExists()

        // Exactly two view options and two density options.
        onNodeWithText("Vertical").assertExists()
        onNodeWithText("Horizontal").assertExists()
        onNodeWithText("Full").assertExists()
        onNodeWithText("Compact").assertExists()
        onAllNodesWithText("Vertical").assertCountEquals(1)
        onAllNodesWithText("Horizontal").assertCountEquals(1)
        onAllNodesWithText("Full").assertCountEquals(1)
        onAllNodesWithText("Compact").assertCountEquals(1)
    }

    @Test
    fun switchersEmitTheRealViewAndDensityActions() = runComposeUiTest {
        val actions = mutableListOf<M3ETimetableAction>()

        setContent {
            AmazeM3ETheme {
                M3ETimetableSheet(
                    state = sampleState(),
                    onAction = { actions.add(it) },
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("Horizontal").performClick()
        onNodeWithText("Compact").performClick()
        onNodeWithText("Vertical").performClick()
        onNodeWithText("Full").performClick()

        assertTrue(
            actions.contains(M3ETimetableAction.SetViewMode(TimetableViewMode.HORIZONTAL))
        )
        assertTrue(actions.contains(M3ETimetableAction.SetDenseGrid(true)))
        assertTrue(
            actions.contains(M3ETimetableAction.SetViewMode(TimetableViewMode.VERTICAL))
        )
        assertTrue(actions.contains(M3ETimetableAction.SetDenseGrid(false)))
    }

    @Test
    fun horizontalViewWithCompactDensityMountsTheDenseHorizontalGrid() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableSheet(
                    state = sampleState().copy(
                        viewMode = TimetableViewMode.HORIZONTAL,
                        denseGrid = true
                    ),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        // The dense horizontal matrix's markers: "Lunch", the DAY gutter and a period time.
        // The whole sheet is one merged semantics node on this API, so assert the markers
        // are present on it rather than expecting six separately merged Lunch nodes.
        onNodeWithText("Lunch").assertExists()
        onNodeWithText("DAY").assertExists()
        onNodeWithText("08:00 - 08:50").assertExists()
    }

    @Test
    fun verticalViewWithCompactDensityMountsTheDenseVerticalGrid() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableSheet(
                    state = sampleState().copy(
                        viewMode = TimetableViewMode.VERTICAL,
                        denseGrid = true
                    ),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        // The dense vertical timeline renders its own "Lunch Break" band (not "Lunch").
        onNodeWithText("Lunch Break").assertExists()
        onNodeWithContentDescription("Lunch Break").assertExists()
    }

    @Test
    fun defaultFullDensityRendersReadableListsWithoutDenseMarkers() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableSheet(
                    state = sampleState(),
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        // SCHEDULE coerces to the vertical view; default density is Full (denseGrid = false).
        onNodeWithText("Vertical").assertExists()
        onNodeWithText("Full").assertExists()
        onNodeWithText("Lunch").assertDoesNotExist()
        onNodeWithText("Lunch Break").assertDoesNotExist()
    }

    @Test
    fun dismissalRequiresTheRegisteredOverlayId() = runComposeUiTest {
        var dismissals = 0
        setContent {
            AmazeM3ETheme {
                M3ETimetableSheet(
                    state = sampleState(),
                    onAction = {},
                    onDismiss = { dismissals++ }
                )
            }
        }

        // With no registered overlay the primitive's LIFO guard blocks every route.
        onAllNodesWithContentDescription("Dismiss sheet")[0].performClick()
        assertEquals(0, dismissals)

        // The real opener pushes this id on OpenTimetableSheet; once registered, dismissal works.
        AppState.pushOverlay(HOME_TIMETABLE_OVERLAY_ID)
        try {
            onAllNodesWithContentDescription("Dismiss sheet")[0].performClick()
            assertEquals(1, dismissals)
        } finally {
            AppState.popOverlay(HOME_TIMETABLE_OVERLAY_ID)
        }
    }
}
