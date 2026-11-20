package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ETimetableAction
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.domain.overhaul.M3ETimetableState
import com.amazecc.app.shared.domain.overhaul.TimetableViewMode
import com.amazecc.app.shared.ui.m3e.components.M3EHorizontalTimetableGrid
import com.amazecc.app.shared.ui.m3e.components.M3EVerticalTimetableGrid
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3ETimetableGridsTest {

    private fun sampleDays() = listOf(
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

    private fun sampleState(viewMode: TimetableViewMode = TimetableViewMode.SCHEDULE) = M3ETimetableState(
        semesterName = "Winter Semester 2025-26",
        selectedDayIndex = 0,
        totalCredits = 24,
        totalCourses = 6,
        viewMode = viewMode,
        days = sampleDays()
    )

    @Test
    fun testHorizontalGridRendersPeriodsAndSlots() = runComposeUiTest {
        var clickedSlot: M3ETimetableSlot? = null

        setContent {
            AmazeM3ETheme {
                M3EHorizontalTimetableGrid(
                    days = sampleDays(),
                    denseGrid = true,
                    onSlotClick = { clickedSlot = it }
                )
            }
        }

        // Verify period column headers
        onNodeWithText("08:00 - 08:50").assertExists()
        onNodeWithText("09:00 - 09:50").assertExists()
        onNodeWithText("14:00 - 14:50").assertExists()

        // Verify day headers
        onNodeWithText("DAY").assertExists()
        onNodeWithText("MON").assertExists()

        // Verify rendered course card content
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("SJT 412").assertExists()

        // Verify slot click
        onNodeWithText("CSE3002").performClick()
        assertNotNull(clickedSlot)
        assertEquals("CSE3002", clickedSlot?.courseCode)
    }

    @Test
    fun testVerticalGridRendersDayScheduleAndSlots() = runComposeUiTest {
        var selectedDayIdx: Int? = null
        var clickedSlot: M3ETimetableSlot? = null

        setContent {
            AmazeM3ETheme {
                M3EVerticalTimetableGrid(
                    days = sampleDays(),
                    denseGrid = true,
                    selectedDayIndex = 0,
                    onSelectDay = { selectedDayIdx = it },
                    onSlotClick = { clickedSlot = it }
                )
            }
        }

        // Verify day selector chips
        onNodeWithText("Mon").assertExists()
        onNodeWithText("Tue").assertExists()

        // Verify lunch break banner — no invented venue, only the real time window
        onNodeWithText("Lunch Break").assertExists()
        onNodeWithText("12:40 PM - 01:30 PM").assertExists()
        onNodeWithText("Dining Hall & Mess").assertDoesNotExist()

        // Verify course slot cards
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("SJT 412").assertExists()
        onNodeWithText("Prof. Sundar").assertExists()

        // Verify happening now / active status
        onAllNodesWithText("ACTIVE")[0].assertExists()

        // Click day chip
        onNodeWithText("Tue").performClick()
        assertEquals(1, selectedDayIdx)

        // Click slot
        onAllNodesWithText("CSE3002")[0].performClick()
        assertNotNull(clickedSlot)
        assertEquals("CSE3002", clickedSlot?.courseCode)
    }

    /** A morning class on a non-today day must never be judged against today's clock. */
    private fun futureDayWithMorningClass() = M3ETimetableDay(
        dayName = "Wednesday",
        shortName = "Wed",
        dayNumber = 3,
        isToday = false,
        isSelected = true,
        slots = listOf(
            M3ETimetableSlot(
                id = "future_slot",
                slot = "C1",
                courseCode = "CSE1001",
                courseTitle = "Data Structures",
                venue = "SJT 101",
                faculty = "Dr. Test",
                startTime = "08:00",
                endTime = "08:50",
                isHappeningNow = false,
                attendanceTone = M3ECardTone.NEUTRAL
            )
        )
    )

    @Test
    fun testFutureDayMorningClassIsNeutralInCompactList() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EVerticalTimetableGrid(
                    days = listOf(futureDayWithMorningClass()),
                    selectedDayIndex = 0,
                    onSelectDay = {},
                    onSlotClick = {}
                )
            }
        }

        onNodeWithText("CSE1001").assertExists()
        onNodeWithText("SCHEDULED").assertExists()
        onNodeWithText("COMPLETED").assertDoesNotExist()
        onNodeWithText("UPCOMING").assertDoesNotExist()
    }

    @Test
    fun testFutureDayMorningClassIsNeutralInDenseGrid() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EVerticalTimetableGrid(
                    days = listOf(futureDayWithMorningClass()),
                    selectedDayIndex = 0,
                    onSelectDay = {},
                    onSlotClick = {},
                    denseGrid = true
                )
            }
        }

        onNodeWithText("CSE1001").assertExists()
        onNodeWithText("SCHEDULED").assertExists()
        onNodeWithText("COMPLETED").assertDoesNotExist()
        onNodeWithText("UPCOMING").assertDoesNotExist()
    }

    @Test
    fun testTimetableScreenSwitchesBetweenViewModes() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ETimetableAction>()

        // 1. SCHEDULE mode
        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(
                    state = sampleState(TimetableViewMode.SCHEDULE),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithText("Day Schedule").assertExists()
        onNodeWithText("Vertical Grid").assertExists()
        onNodeWithText("Horizontal Grid").assertExists()

        // Click to switch to Vertical Grid
        onNodeWithText("Vertical Grid").performClick()
        val setModeAction = capturedActions.filterIsInstance<M3ETimetableAction.SetViewMode>().firstOrNull()
        assertEquals(TimetableViewMode.VERTICAL, setModeAction?.mode)
    }

    @Test
    fun testTimetableScreenRendersVerticalMode() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(
                    state = sampleState(TimetableViewMode.VERTICAL),
                    onAction = {}
                )
            }
        }

        // The screen's VERTICAL branch renders M3EVerticalTimetableGrid with its
        // default compact layout. The lunch banner is an opt-in of the dense
        // grid (see testVerticalGridRendersDayScheduleAndSlots), so verify the
        // screen wired the vertical grid itself: the day selector and slots.
        onNodeWithText("Mon").assertExists()
        onNodeWithText("Tue").assertExists()
        onNodeWithText("CSE3002").assertExists()
    }

    @Test
    fun testTimetableScreenRendersHorizontalMode() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ETimetableScreen(
                    state = sampleState(TimetableViewMode.HORIZONTAL),
                    onAction = {}
                )
            }
        }

        // Periods and day column should appear in horizontal grid mode
        onNodeWithText("DAY").assertExists()
        onNodeWithText("08:00 - 08:50").assertExists()
        onNodeWithText("CSE3002").assertExists()
    }
}
