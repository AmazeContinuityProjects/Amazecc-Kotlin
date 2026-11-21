package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EClassTimelineItem
import com.amazecc.app.shared.domain.overhaul.M3EHomeExamItem
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState
import com.amazecc.app.shared.domain.overhaul.M3EUserIdentityState
import com.amazecc.app.shared.domain.overhaul.M3EWeekDay
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test

/**
 * D-132: home timeline parity for real exams and for calendar day-order overrides.
 * Mirrors the sample state from [M3EHomeScreenUiTest]; the class title is deliberately
 * different from every exam title so "class hidden on exam day" is a real assertion.
 */
@OptIn(ExperimentalTestApi::class)
class M3EHomeExamTimelineUiTest {

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
                courseCode = "CSE3009",
                courseTitle = "Theory of Computation",
                slot = "A1",
                venue = "SJT 101",
                faculty = "Prof. Rao",
                startTime = "08:00",
                endTime = "08:50"
            )
        ),
        insights = emptyList(),
        semesterName = "Fall Sem 2025-2026"
    )

    @Test
    fun exam_day_renders_every_real_exam_and_hides_the_lecture_list() = runComposeUiTest {
        val state = sampleState().copy(
            todayExams = listOf(
                M3EHomeExamItem(
                    courseCode = "CSE3002",
                    courseTitle = "Internet and Web Programming",
                    examSeries = "CAT-1",
                    examTime = "09:30 AM - 11:15 AM",
                    venue = "SJT 412",
                    seatNo = "B-14"
                ),
                M3EHomeExamItem(
                    courseCode = "CSE3004",
                    courseTitle = "Database Management Systems",
                    examSeries = "CAT-1",
                    examTime = "02:00 PM - 04:45 PM",
                    venue = "TT 301",
                    seatNo = "A07"
                )
            )
        )

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("09:30 AM - 11:15 AM").assertExists()
        onNodeWithText("Database Management Systems").assertExists()
        onNodeWithText("02:00 PM - 04:45 PM").assertExists()
        onNodeWithText("No regular lectures on exam day").assertExists()

        // No lecture is silently rendered underneath the exam cards on a full exam day.
        onNodeWithText("Theory of Computation").assertDoesNotExist()
        onNodeWithText("SJT 101").assertDoesNotExist()
    }

    @Test
    fun overridden_day_order_classes_render_when_there_are_no_exams() = runComposeUiTest {
        // A Saturday carrying a published day-order override (Saturday follows Monday's
        // timetable). The host resolves that override into todayClasses, so the screen's
        // contract is simply: render those classes when no exams occupy the day.
        val overriddenDate = LocalDate(2026, 10, 3)
        val state = sampleState().copy(
            weekDays = listOf(
                M3EWeekDay(
                    date = overriddenDate,
                    dayName = "Sat",
                    dayNumber = 3,
                    isToday = false,
                    isSelected = true,
                    hasClasses = true,
                    isHoliday = false
                )
            ),
            selectedDayDate = overriddenDate,
            todayClasses = listOf(
                M3EClassTimelineItem(
                    courseCode = "CSE3001",
                    courseTitle = "Compiler Design",
                    slot = "B2",
                    venue = "SJT 205",
                    faculty = "Prof. Iyer",
                    startTime = "10:00",
                    endTime = "10:50"
                )
            ),
            todayExams = emptyList()
        )

        setContent {
            AmazeM3ETheme {
                M3EHomeScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Compiler Design").assertExists()
        onNodeWithText("SJT 205").assertExists()
        onNodeWithText("No regular lectures on exam day").assertDoesNotExist()
    }
}
