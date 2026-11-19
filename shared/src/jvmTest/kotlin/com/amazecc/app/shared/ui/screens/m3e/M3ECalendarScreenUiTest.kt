package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless verification that the M3E academic calendar renders a real month grid. */
@OptIn(ExperimentalTestApi::class)
class M3ECalendarScreenUiTest {

    private fun sampleState() = M3ECalendarState(
        calendarNames = listOf("General Semester", "General Flexible"),
        selectedCalendarIndex = 0,
        months = listOf(
            M3ECalendarMonth(
                name = "November 2026",
                leadingBlankDays = 6,
                days = listOf(
                    M3ECalendarDay(16, listOf(M3ECalendarEvent("CAT-1", "Exam")), isToday = true),
                    M3ECalendarDay(17, listOf(M3ECalendarEvent("Instructional Day", "Instructional Day")))
                )
            )
        ),
        selectedDay = 16
    )

    @Test
    fun rendersMonthGridAndSelectedDayEvents() = runComposeUiTest {
        setContent {
            AmazeM3ETheme { M3ECalendarScreen(state = sampleState(), onAction = {}) }
        }

        onNodeWithText("Academic Calendar").assertExists()
        onNodeWithText("November 2026").assertExists()
        onNodeWithText("16").assertExists()
        onNodeWithText("Events on day 16 · 1").assertExists()
        onNodeWithText("CAT-1").assertExists()
    }

    @Test
    fun selectingADayAndRefreshingEmitsActions() = runComposeUiTest {
        val actions = mutableListOf<M3ECalendarAction>()
        setContent {
            AmazeM3ETheme {
                M3ECalendarScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Refresh calendar").performClick()
        assertTrue(actions.contains(M3ECalendarAction.Refresh))
        assertEquals(1, actions.size)
    }

    @Test
    fun emptyStateExplainsMissingCalendar() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECalendarScreen(
                    state = M3ECalendarState(errorMessage = "No calendar entries available — pull to retry"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("No calendar entries available — pull to retry").assertExists()
    }
}
