package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ETimetableAction
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.domain.overhaul.M3ETimetableState
import com.amazecc.app.shared.domain.overhaul.TimetableViewMode
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ETimetableScreenUiTest {

    private fun sampleState() = M3ETimetableState(
        semesterName = "Winter Semester 2025-26",
        selectedDayIndex = 0,
        totalCredits = 24,
        totalCourses = 6,
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
                        isHappeningNow = false,
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
                isSelected = false,
                slots = emptyList()
            )
        )
    )

    @Test
    fun testTimetableRendersHeaderAndCourseSlots() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Timetable").assertExists()
        onNodeWithText("Winter Semester 2025-26 • 24 Credits Registered").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("CSE2005").assertExists()
        onNodeWithText("Operating Systems").assertExists()
        onNodeWithText("HAPPENING NOW").assertExists()
    }

    @Test
    fun testDaySelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETimetableAction>()

        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Tue").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val selectDayAction = capturedActions.filterIsInstance<M3ETimetableAction.SelectDay>().firstOrNull()
        assertEquals(1, selectDayAction?.dayIndex)
    }

    @Test
    fun testSlotClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETimetableAction>()

        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("CSE3002").performClick()

        assertTrue(capturedActions.isNotEmpty())
        val slotAction = capturedActions.filterIsInstance<M3ETimetableAction.SlotClicked>().firstOrNull()
        assertEquals("CSE3002", slotAction?.slot?.courseCode)
    }

    @Test
    fun default_view_is_the_day_schedule_list() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(state = sampleState(), onAction = {})
            }
        }

        assertEquals(TimetableViewMode.SCHEDULE, sampleState().viewMode)
        onNodeWithText("Day Schedule").assertExists()
        // The dense grid is never mounted until the user explicitly picks it.
        onNodeWithText("CSE3002").assertExists()
    }

    @Test
    fun dense_grid_is_only_an_explicit_opt_in() = runComposeUiTest {
        val actions = mutableListOf<M3ETimetableAction>()

        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Horizontal Grid").performClick()
        assertTrue(
            actions.filterIsInstance<M3ETimetableAction.SetViewMode>()
                .any { it.mode == TimetableViewMode.HORIZONTAL }
        )
    }
}
