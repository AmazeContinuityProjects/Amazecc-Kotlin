package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.components.M3EPageActionsMenuTestTags
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

        onNodeWithText("Super Calendar").assertExists()
        onNodeWithText("November 2026").assertExists()
        onNodeWithText("16").assertExists()
        // Match the day header without coupling to the separator glyph / event
        // count, which changes as the super calendar merges more sources.
        onNodeWithText("Events on day 16", substring = true).assertExists()
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

        // D-116/D-124: the five controls live behind one page-actions FAB, hosted
        // at the page root overlay (never inline in the scrolling header).
        onNodeWithTag(M3EPageActionsMenuTestTags.HOST_ROOT).assertExists()
        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).assertExists()
        onNodeWithContentDescription("More calendar actions").performClick()
        waitForIdle()
        onNodeWithText("Refresh calendar").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(actions.contains(M3ECalendarAction.Refresh))
        assertEquals(1, actions.size)
    }

    @Test
    fun emptyStateExplainsMissingCalendar() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECalendarScreen(
                    state = M3ECalendarState(errorMessage = "No calendar entries available \u2014 pull to retry"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("No calendar entries available \u2014 pull to retry").assertExists()
    }

    // --- D-113: grid geometry ---

    @Test
    fun gridBuilderAlwaysProducesFullWeeksOfSeven() {
        // 6 leading blanks + 3 real days = 9; padded with 5 trailing blanks to 14 (2 x 7).
        val days = (1..3).map { M3ECalendarDay(it) }
        val cells = buildCalendarCells(leadingBlanks = 6, days = days)

        assertEquals(14, cells.size)
        assertTrue(cells.size % 7 == 0)
        assertTrue(cells.chunked(7).all { it.size == 7 })
        assertEquals(5, cells.takeLast(5).count { it == null })
        assertEquals(1, cells[6]?.date) // first real day fills the last leading slot
        assertEquals(2, cells[7]?.date) // final week starts with the remaining real days
    }

    @Test
    fun gridBuilderDoesNotPadAnAlreadyFullWeek() {
        val cells = buildCalendarCells(leadingBlanks = 0, days = (1..14).map { M3ECalendarDay(it) })

        assertEquals(14, cells.size)
        assertTrue(cells.none { it == null })
        assertTrue(cells.chunked(7).all { it.size == 7 })
    }

    @Test
    fun monthGridRendersSevenCellsPerWeekIncludingPartialFinalWeek() = runComposeUiTest {
        val partialMonth = M3ECalendarState(
            months = listOf(
                M3ECalendarMonth(
                    name = "November 2026",
                    leadingBlankDays = 6,
                    days = (1..3).map { M3ECalendarDay(it) }
                )
            )
        )
        setContent {
            AmazeM3ETheme { M3ECalendarScreen(state = partialMonth, onAction = {}) }
        }

        // 6 leading + 3 real + 5 trailing = 14 cells across 2 rows of exactly 7.
        onAllNodesWithTag(M3ECalendarTestTags.WEEK_ROW, useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithTag(M3ECalendarTestTags.CELL, useUnmergedTree = true).assertCountEquals(14)
    }

    // --- D-113: tonal day-state tint semantics ---

    @Test
    fun dayCellsExposeInstructionalAndNonInstructionalTintSemantics() = runComposeUiTest {
        val state = M3ECalendarState(
            months = listOf(
                M3ECalendarMonth(
                    name = "November 2026",
                    days = listOf(
                        M3ECalendarDay(1, listOf(M3ECalendarEvent("Instructional Day", "Instructional Day"))),
                        M3ECalendarDay(2, listOf(M3ECalendarEvent("Non-instructional Day", "Non-instructional Day"))),
                        M3ECalendarDay(3, listOf(M3ECalendarEvent("Holiday", "Holiday"))),
                        M3ECalendarDay(4), // no markings at all
                        M3ECalendarDay(5, listOf(M3ECalendarEvent("CAT-1", "Exam"))) // no instructional marking
                    )
                )
            )
        )
        setContent {
            AmazeM3ETheme { M3ECalendarScreen(state = state, onAction = {}) }
        }

        onNodeWithContentDescription("Day 1, instructional", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Day 2, non-instructional", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Day 3, non-instructional", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Day 4, no status recorded", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Day 5, non-instructional", useUnmergedTree = true).assertExists()
        // A day cannot carry both tints: Day 1 is asserted instructional above, so its
        // non-instructional description must not be present (exclusive classification).
        onNodeWithContentDescription("Day 1, instructional", useUnmergedTree = true).assertExists()
    }

    @Test
    fun classificationHandlesCaseInsensitiveAndMixedMarkings() {
        assertEquals(
            M3ECalendarDayTone.INSTRUCTIONAL,
            M3ECalendarDay(1, listOf(M3ECalendarEvent("Theory Class", "Class", "class"))).dayTone()
        )
        assertEquals(
            M3ECalendarDayTone.NON_INSTRUCTIONAL,
            M3ECalendarDay(2, listOf(M3ECalendarEvent("non instructional day", "non instructional day"))).dayTone()
        )
        assertEquals(
            M3ECalendarDayTone.NON_INSTRUCTIONAL,
            M3ECalendarDay(3, listOf(M3ECalendarEvent("HOLIDAY", "holiday"))).dayTone()
        )
        assertEquals(M3ECalendarDayTone.NON_INSTRUCTIONAL, M3ECalendarDay(4).dayTone())
        // A non-instructional marker wins even when the text also contains "instructional".
        assertEquals(
            M3ECalendarDayTone.NON_INSTRUCTIONAL,
            M3ECalendarDay(
                5,
                listOf(
                    M3ECalendarEvent("Instructional Day", "Instructional Day"),
                    M3ECalendarEvent("Holiday", "Holiday")
                )
            ).dayTone()
        )
    }

    // --- D-115: merged sources stay distinguishable ---

    @Test
    fun domainDayTypeKeepsInstructionalTintWithOnlyNonDayTypeEvents() {
        // The merged day holds a deadline, not a scraped "Instructional Day" string. The tint must
        // still follow the college's published day type rather than sniffing the event text.
        val day = M3ECalendarDay(
            9,
            listOf(M3ECalendarEvent("Moodle quiz", "Moodle", source = M3ECalendarSource.DEADLINE)),
            dayType = M3ECalendarDayType.INSTRUCTIONAL
        )
        assertEquals(M3ECalendarDayTone.INSTRUCTIONAL, day.dayTone())
    }

    @Test
    fun mergedTintAndLegendRenderFromProjectedState() = runComposeUiTest {
        val state = M3ECalendarState(
            months = listOf(
                M3ECalendarMonth(
                    name = "November 2026",
                    days = listOf(
                        M3ECalendarDay(
                            12,
                            listOf(
                                M3ECalendarEvent("Biology", "Exam", source = M3ECalendarSource.EXAM),
                                M3ECalendarEvent("Moodle quiz", "Moodle", source = M3ECalendarSource.DEADLINE),
                                M3ECalendarEvent("Theory class", "Class", source = M3ECalendarSource.CLASS)
                            ),
                            dayType = M3ECalendarDayType.INSTRUCTIONAL
                        )
                    )
                )
            ),
            selectedDay = 12
        )
        setContent {
            AmazeM3ETheme { M3ECalendarScreen(state = state, onAction = {}) }
        }

        onNodeWithContentDescription("Day 12, instructional", useUnmergedTree = true).assertExists()
        onNodeWithTag(M3ECalendarTestTags.SOURCE_LEGEND).assertExists()
        onAllNodesWithTag(M3ECalendarTestTags.EVENT_ROW, useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun sourceFilterShowsOnlyTheChosenSources() = runComposeUiTest {
        val state = M3ECalendarState(
            months = listOf(
                M3ECalendarMonth(
                    name = "November 2026",
                    days = listOf(
                        M3ECalendarDay(
                            12,
                            listOf(
                                M3ECalendarEvent("Biology", "Exam", source = M3ECalendarSource.EXAM),
                                M3ECalendarEvent("Moodle quiz", "Moodle", source = M3ECalendarSource.DEADLINE)
                            ),
                            dayType = M3ECalendarDayType.SEMIHOLIDAY
                        )
                    )
                )
            ),
            selectedDay = 12,
            eventFilter = M3ECalendarFilter.DEADLINES
        )
        setContent {
            AmazeM3ETheme { M3ECalendarScreen(state = state, onAction = {}) }
        }

        onNodeWithText("Moodle quiz").assertExists()
        onNodeWithText("Biology").assertDoesNotExist()
        onAllNodesWithTag(M3ECalendarTestTags.EVENT_ROW, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun rawEntriesStillInferTheirSource() {
        assertEquals(M3ECalendarSource.EXAM, M3ECalendarEvent("CAT-1", "Exam").effectiveSource())
        assertEquals(
            M3ECalendarSource.HOLIDAY,
            M3ECalendarEvent("Gandhi Jayanthi", "Holiday").effectiveSource()
        )
        assertEquals(
            M3ECalendarSource.DEADLINE,
            M3ECalendarEvent("Assignment 1", "Assignment").effectiveSource()
        )
    }
}
