package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3EClassTimelineItem
import com.amazecc.app.shared.domain.overhaul.M3EComputations
import com.amazecc.app.shared.domain.overhaul.M3EHomeAction
import com.amazecc.app.shared.domain.overhaul.M3EHomeAttendance
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState
import com.amazecc.app.shared.domain.overhaul.M3EUserIdentityState
import com.amazecc.app.shared.domain.overhaul.M3EWeekDay
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EHomeScreenUiTest {

    private val sampleDate = LocalDate(2026, 9, 29)

    private fun sampleState() = M3EHomeState(
        identity = M3EUserIdentityState(
            name = "Aravind Kumar",
            regNumber = "21BCE1042",
            programme = "B.Tech CSE",
            school = "SCOPE",
            campus = "Vellore"
        ),
        weekDays = listOf(
            M3EWeekDay(
                date = sampleDate,
                dayName = "Tue",
                dayNumber = 29,
                isToday = true,
                isSelected = true,
                hasClasses = true,
                isHoliday = false
            ),
            M3EWeekDay(
                date = LocalDate(2026, 9, 30),
                dayName = "Wed",
                dayNumber = 30,
                isToday = false,
                isSelected = false,
                hasClasses = true,
                isHoliday = false
            )
        ),
        selectedDayDate = sampleDate,
        quickStats = M3EQuickStatsState(
            attendancePercentage = 88.5,
            cgpa = 9.12,
            creditsEarned = 120,
            safeBunksAvailable = 6,
            attendanceStatusText = "Safe margin across all courses"
        ),
        todayClasses = listOf(
            M3EClassTimelineItem(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                slot = "B1+TB1",
                venue = "SJT 412",
                faculty = "Prof. Sundar",
                startTime = "09:00",
                endTime = "09:50",
                isHappeningNow = true
            )
        ),
        insights = emptyList(),
        semesterName = "Fall Sem 2025-2026"
    )

    @Test
    fun renders_student_welcome_and_quick_stats_correctly() = runComposeUiTest {
        val state = sampleState()

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Aravind Kumar").assertExists()
        onNodeWithText("Fall Sem 2025-2026").assertExists()
        onNodeWithText("21BCE1042 · Vellore Campus").assertDoesNotExist()
        onNodeWithText("88.50%").assertExists()
        // The attendance subtitle is composed into one M3EStatCard line as
        // "75% safety target · <status>" (no separate uppercase ATTENDANCE label).
        onNodeWithText("75% safety target · Safe margin across all courses", substring = true)
            .assertExists()
    }

    @Test
    fun renders_timeline_card_and_fires_course_click() = runComposeUiTest {
        val state = sampleState()
        var clickedCourse: String? = null

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(
                    state = state,
                    onAction = { action ->
                        if (action is M3EHomeAction.ClickCourse) {
                            clickedCourse = action.courseCode
                        }
                    }
                )
            }
        }

        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("SJT 412").assertExists()
        onNodeWithText("NOW").assertExists()

        onNodeWithText("Internet and Web Programming").performClick()
        assertEquals("CSE3002", clickedCourse)
    }

    @Test
    fun renders_week_strip_with_accessible_descriptions() = runComposeUiTest {
        val state = sampleState()

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithContentDescription("Tue 29 · Classes scheduled").assertExists()
    }

    @Test
    fun date_switcher_fires_select_day_action() = runComposeUiTest {
        val state = sampleState()
        var selectedDate: LocalDate? = null

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(
                    state = state,
                    onAction = { action ->
                        if (action is M3EHomeAction.SelectDay) {
                            selectedDate = action.date
                        }
                    }
                )
            }
        }

        onNodeWithContentDescription("Wed 30 · Classes scheduled").performClick()
        assertEquals(LocalDate(2026, 9, 30), selectedDate)
    }

    @Test
    fun subject_cards_show_component_attendance_at_top_right() = runComposeUiTest {
        val prototype = sampleState().todayClasses.first()
        val state = sampleState().copy(
            todayClasses = listOf(
                prototype.copy(
                    courseCode = "CSE3002L",
                    courseTitle = "Web Programming Lab",
                    slot = "L55",
                    attendancePercentage = 82.0,
                    isHappeningNow = false
                ),
                prototype.copy(
                    courseCode = "CSE3002",
                    courseTitle = "Web Programming Theory",
                    slot = "B1",
                    attendancePercentage = 91.5,
                    isHappeningNow = false
                )
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithContentDescription("Attendance 82%").assertExists()
        onNodeWithContentDescription("Attendance 91.5%").assertExists()
    }

    @Test
    fun subject_card_hides_badge_when_attendance_unavailable() = runComposeUiTest {
        val state = sampleState().copy(
            todayClasses = listOf(
                sampleState().todayClasses.first().copy(
                    attendancePercentage = null,
                    isHappeningNow = false
                )
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithContentDescription("Attendance 0%").assertDoesNotExist()
        onNodeWithText("—").assertDoesNotExist()
    }

    @Test
    fun home_attendance_reads_the_half_owned_by_the_class_slot() {
        val embedded = Course(
            code = "BACSE102",
            title = "Embedded Systems",
            type = "Embedded Theory",
            attendance = Attendance(percentage = "80"),
            halves = listOf(
                EmbeddedCourseHalf(
                    code = "BACSE102", type = "Embedded Theory", half = "T",
                    slots = listOf("B1"), attendance = Attendance(attended = 38, total = 40)
                ),
                EmbeddedCourseHalf(
                    code = "BACSE102L", type = "Embedded Lab", half = "L",
                    slots = listOf("L55"), attendance = Attendance(percentage = "72%")
                )
            )
        )
        assertEquals(95.0, M3EHomeAttendance.percentForSlot(embedded, "B1"))
        assertEquals(72.0, M3EHomeAttendance.percentForSlot(embedded, "L55"))
    }

    @Test
    fun home_attendance_handles_standalone_lab_and_missing_data() {
        val lab = Course(
            code = "BACSE301L", title = "Data Structures Lab", type = "Lab Only",
            slots = listOf("L55"), attendance = Attendance(attended = 9, total = 12)
        )
        val noData = Course(code = "BACSE302", title = "Theory", type = "Theory Only")
        assertEquals(75.0, M3EHomeAttendance.percentForSlot(lab, "L55"))
        assertNull(M3EHomeAttendance.percentForSlot(noData, "A1"))
    }

    @Test
    fun attendance_label_uses_the_threaded_live_target() = runComposeUiTest {
        val state = sampleState().copy(
            quickStats = sampleState().quickStats.copy(
                attendancePercentage = 82.0,
                attendanceTargetPercentage = 85f,
                attendanceStatusText = "Attendance below 85% target"
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("85% safety target", substring = true).assertExists()
        onNodeWithText("75% safety target", substring = true).assertDoesNotExist()
    }

    @Test
    fun attendance_target_is_recovered_from_host_status_when_not_threaded() = runComposeUiTest {
        val state = sampleState().copy(
            quickStats = sampleState().quickStats.copy(
                attendancePercentage = 88.0,
                attendanceTargetPercentage = null,
                attendanceStatusText = "Safe margin across courses (target 85%)"
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("85% safety target", substring = true).assertExists()
    }

    @Test
    fun pending_load_shows_neutral_placeholder_not_an_empty_schedule() = runComposeUiTest {
        val state = sampleState().copy(isRefreshing = true, todayClasses = emptyList())

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Loading schedule…").assertExists()
        onNodeWithText("No classes scheduled for today.").assertDoesNotExist()
    }

    @Test
    fun empty_schedule_copy_appears_only_after_loading_finishes() = runComposeUiTest {
        val state = sampleState().copy(isRefreshing = false, todayClasses = emptyList())

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("No classes scheduled for today.").assertExists()
        onNodeWithText("Loading schedule…").assertDoesNotExist()
    }

    @Test
    fun error_banner_retry_and_dismiss_emit_real_actions() = runComposeUiTest {
        val actions = mutableListOf<M3EHomeAction>()
        val state = sampleState().copy(errorMessage = "Backend unavailable", isRefreshing = false)

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Backend unavailable").assertExists()
        onNodeWithText("Retry").performClick()
        onNodeWithContentDescription("Dismiss error").performClick()
        assertTrue(actions.any { it is M3EHomeAction.RefreshData })
        assertTrue(actions.any { it is M3EHomeAction.DismissError })
    }

    @Test
    fun one_band_drives_hub_tone_and_deep_dive_verdict() {
        assertEquals(
            M3EComputations.AttendanceBand.SAFE,
            M3EComputations.attendanceBand(85.0, 75f)
        )
        assertEquals(
            M3EComputations.AttendanceBand.NEAR_TARGET,
            M3EComputations.attendanceBand(82.0, 75f)
        )
        assertEquals(
            M3EComputations.AttendanceBand.AT_RISK,
            M3EComputations.attendanceBand(74.9, 75f)
        )
        assertEquals(M3ECardTone.WARNING, M3EComputations.toneForPercentage(82.0, 75f))
        assertEquals(M3ECardTone.CRITICAL, M3EComputations.toneForPercentage(82.0, 85f))
    }

    @Test
    fun target_resolution_prefers_state_then_status_then_default() {
        assertEquals(
            85.0,
            M3EComputations.resolveAttendanceTarget(null, "Safe margin across courses (target 85%)")
                .toDouble()
        )
        assertEquals(
            90.0,
            M3EComputations.resolveAttendanceTarget(90f, "Safe margin across courses (target 85%)")
                .toDouble()
        )
        assertEquals(
            75.0,
            M3EComputations.resolveAttendanceTarget(null, "Safe margin across all courses").toDouble()
        )
        assertTrue(!M3EComputations.hasAttendanceData("Attendance not available"))
    }
}
