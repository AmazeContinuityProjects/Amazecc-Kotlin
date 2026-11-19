package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.AttendanceFilter
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceState
import com.amazecc.app.shared.domain.overhaul.M3EBunkSummary
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseAttendanceCardState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class M3EAttendanceScreenUiTest {

    private fun sampleState() = M3EAttendanceState(
        semesterName = "Winter 2026-27",
        overallPercentage = 84.2,
        bunkSummary = M3EBunkSummary(
            totalBunkableClasses = 5,
            criticalCoursesCount = 0,
            warningCoursesCount = 1,
            targetPercentage = 75f
        ),
        courses = listOf(
            M3ECourseAttendanceCardState(
                courseCode = "BACSE201",
                courseTitle = "Data Structures & Algorithms",
                badge = "EMB",
                attendedClasses = 26,
                totalClasses = 28,
                percentage = 92.8,
                tone = M3ECardTone.GOOD,
                faculty = "Dr. Raman",
                slot = "A1+TA1",
                bunkSummary = "Can bunk 4 more classes",
                isEmbedded = true
            )
        )
    )

    @Test
    fun renders_overall_stats_and_course_cards() = runComposeUiTest {
        val state = sampleState()

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Attendance Hub").assertExists()
        onNodeWithText("Winter 2026-27 · Overall 84.20%").assertExists()
        onNodeWithText("Safe to Bunk ~5 Classes").assertExists()
        onNodeWithText("Data Structures & Algorithms").assertExists()
        onNodeWithText("92.80%").assertExists()
        onNodeWithText("26/28 Attended").assertExists()
    }

    @Test
    fun filter_selection_triggers_set_filter_action() = runComposeUiTest {
        val state = sampleState()
        var selectedFilter: AttendanceFilter? = null

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(
                    state = state,
                    onAction = { action ->
                        if (action is M3EAttendanceAction.SetFilter) {
                            selectedFilter = action.filter
                        }
                    }
                )
            }
        }

        onNodeWithText("At Risk").performClick()
        assertEquals(AttendanceFilter.AT_RISK, selectedFilter)
    }
}
