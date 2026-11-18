package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EClassTimelineItem
import com.amazecc.app.shared.domain.overhaul.M3EHomeAction
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState
import com.amazecc.app.shared.domain.overhaul.M3EUserIdentityState
import com.amazecc.app.shared.domain.overhaul.M3EWeekDay
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
        insights = emptyList()
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
        onNodeWithText("21BCE1042 · Vellore Campus").assertExists()
        onNodeWithText("88.5%").assertExists()
        onNodeWithText("9.12").assertExists()
        onNodeWithText("120 Credits Earned").assertExists()
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
}
