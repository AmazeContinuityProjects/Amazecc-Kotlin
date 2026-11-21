package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.AttendancePredictor
import com.amazecc.app.shared.domain.MilestoneDates
import com.amazecc.app.shared.domain.ScheduleResult
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState
import com.amazecc.app.shared.domain.overhaul.M3EPredictorCalendarDay
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-139b: acceptance tests for the stateless attendance predictor screen.
 *
 * Fixtures use real [AttendancePredictor] timestamps and real [ScheduleResult]
 * projections. Weekday labels are derived from those timestamps so a fixture
 * cannot quietly assert a day the data does not contain.
 */
@OptIn(ExperimentalTestApi::class)
class M3EAttendancePredictorScreenUiTest {

    private fun timestamp(day: Int): Long = AttendancePredictor.makeTimestamp(2026, 10, day)

    private fun weekdayShort(epochMillis: Long): String = when (
        Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek
    ) {
        DayOfWeek.MONDAY -> "MON"
        DayOfWeek.TUESDAY -> "TUE"
        DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"
        DayOfWeek.FRIDAY -> "FRI"
        DayOfWeek.SATURDAY -> "SAT"
        DayOfWeek.SUNDAY -> "SUN"
    }

    private fun calendarDay(day: Int, classCount: Int): M3EPredictorCalendarDay {
        val date = timestamp(day)
        val short = weekdayShort(date)
        return M3EPredictorCalendarDay(
            date = date,
            dayNumber = day,
            monthLabel = "NOVEMBER 2026",
            weekdayShort = short,
            effectiveWeekday = short,
            classCount = classCount,
            isToday = false,
            state = 0
        )
    }

    /** One safe theory course and one at-risk lab course. */
    private fun sampleResults() = listOf(
        ScheduleResult(
            futureDays = 6,
            missedDays = 2,
            meetingDays = listOf("MON", "WED", "FRI"),
            attended = 41,
            total = 45,
            currentPct = 91.11,
            predictedAttended = 45,
            predictedTotal = 49,
            predictedPct = 91.84,
            deltaPct = 0.73,
            safeBunks = 4,
            classesNeeded = 0,
            isLab = false,
            skippedCount = 2,
            courseCode = "BACSE105"
        ),
        ScheduleResult(
            futureDays = 4,
            missedDays = 1,
            meetingDays = listOf("TUE", "THU"),
            attended = 20,
            total = 32,
            currentPct = 62.50,
            predictedAttended = 23,
            predictedTotal = 40,
            predictedPct = 57.50,
            deltaPct = -5.0,
            safeBunks = 0,
            classesNeeded = 9,
            isLab = true,
            skippedCount = 1,
            courseCode = "BACSE102"
        )
    )

    /** Real course codes live in the skip map; their set sizes match the projections. */
    private fun sampleState(): M3EAttendancePredictorState {
        val results = sampleResults()
        return M3EAttendancePredictorState(
            targetPercent = 75,
            mode = "CAT1",
            milestoneDates = MilestoneDates(
                cat1Date = AttendancePredictor.makeTimestamp(2026, 11, 15),
                cat2Date = AttendancePredictor.makeTimestamp(2026, 11, 30)
            ),
            courseResults = results,
            calendarDays = listOf(
                calendarDay(23, 3),
                calendarDay(24, 2),
                calendarDay(25, 3),
                calendarDay(26, 2)
            ),
            courseSkips = mapOf(
                "BACSE105" to setOf(timestamp(23), timestamp(25)),
                "BACSE102" to setOf(timestamp(24))
            ),
            summary = AttendancePredictor.summarise(results, 75.0)
        )
    }

    private fun ComposeUiTest.scrollTo(text: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun ComposeUiTest.expect(text: String) {
        scrollTo(text)
        onNodeWithText(text).assertExists()
    }

    @Test
    fun renders_header_targets_courses_calendar_and_advisor_copy() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EAttendancePredictorScreen(state = sampleState(), onAction = {})
            }
        }

        expect("ATTENDANCE \u00B7 PREDICTOR")
        expect("Attendance Predictor & Simulator")
        expect("75%")
        expect("80%")
        expect("85%")
        expect("90%")
        expect("Attend All")
        expect("Skip Fridays")

        // Real course codes are recovered from the per-course skip map.
        expect("BACSE105")
        expect("BACSE102")

        // At-risk / safe copy comes from the real summarise() output.
        // Summary cell labels are uppercased by the M3E tile.
        expect("TOTAL SAFE LEAVES")
        expect("COURSES AT RISK")
        expect("Below 75%")
        expect("1 of 2 courses safe")

        // Calendar labels are the real labels carried by the four fixture days.
        expect("Interactive Day Simulator (NOVEMBER 2026)")
        expect("MON")
        expect("23")
        expect("TUE")
        expect("24")
        expect("WED")
        expect("25")
        expect("THU")
        expect("26")

        expect("Smart Attendance Insights & Exam Guidelines")
        onNodeWithText("No courses match your filter").assertDoesNotExist()
    }

    @Test
    fun target_and_quick_actions_emit_real_actions() = runComposeUiTest {
        val actions = mutableListOf<M3EAttendancePredictorAction>()
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EAttendancePredictorScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("90%").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(M3EAttendancePredictorAction.SetTarget(90) in actions)

        scrollTo("Attend All")
        onNodeWithText("Attend All").performClick()
        assertTrue(M3EAttendancePredictorAction.AttendAll in actions)

        scrollTo("Skip Fridays")
        onNodeWithText("Skip Fridays").performClick()
        assertTrue(M3EAttendancePredictorAction.SkipFridays in actions)
    }

    @Test
    fun calendar_day_click_emits_toggle_day_state_with_real_date() = runComposeUiTest {
        val actions = mutableListOf<M3EAttendancePredictorAction>()
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EAttendancePredictorScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        scrollTo("Interactive Day Simulator")
        scrollTo("23")
        onNodeWithContentDescription("Toggle attendance for MON NOVEMBER 2026 23").performClick()

        assertEquals(
            listOf<M3EAttendancePredictorAction>(M3EAttendancePredictorAction.ToggleDayState(timestamp(23))),
            actions
        )
    }

    @Test
    fun course_row_and_filter_clicks_emit_real_actions() = runComposeUiTest {
        val actions = mutableListOf<M3EAttendancePredictorAction>()
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EAttendancePredictorScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        scrollTo("BACSE105")
        onNodeWithContentDescription("Choose dates to skip for BACSE105").performClick()
        assertEquals(M3EAttendancePredictorAction.ToggleCourseExpanded("BACSE105"), actions.last())

        scrollTo("Lab")
        onNodeWithText("Lab").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(M3EAttendancePredictorAction.SetFilter("LAB"), actions.last())
    }

    @Test
    fun expanded_lab_course_shows_real_dates_and_toggle_emits_action() = runComposeUiTest {
        val actions = mutableListOf<M3EAttendancePredictorAction>()
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EAttendancePredictorCourseSections(
                    state = sampleState().copy(expandedCourseCode = "BACSE102"),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("A lab session counts as two hours, so skipping one costs two classes.").assertExists()
        // The lab meets Tue/Thu; only those real calendar days are offered.
        onNodeWithText("THU 26 \u00B7 NOVEMBER 2026").assertExists()
        onNodeWithText("Skipped").assertExists()
        onNodeWithText("Clear 1 skipped date").assertExists()

        onNodeWithContentDescription("Skip THU 26 NOVEMBER 2026").performClick()
        assertEquals(M3EAttendancePredictorAction.ToggleCourseSkip("BACSE102", timestamp(26)), actions.last())
    }

    @Test
    fun no_matching_course_shows_honest_empty_state() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EAttendancePredictorCourseSections(
                    state = sampleState().copy(searchQuery = "ZZZ-NOT-A-COURSE"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("No courses match your filter").assertExists()
        onNodeWithText("Try a different filter, or clear the search.").assertExists()
    }
}
