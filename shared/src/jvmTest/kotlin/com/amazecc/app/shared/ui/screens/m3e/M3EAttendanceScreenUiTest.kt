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
                badge = "Theory",
                attendedClasses = 26,
                totalClasses = 28,
                percentage = 92.8,
                tone = M3ECardTone.GOOD,
                faculty = "Dr. Raman",
                slot = "A1+TA1",
                bunkSummary = "Can bunk 4 more classes"
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

    @Test
    fun first_load_shows_skeleton_instead_of_a_false_empty_result() = runComposeUiTest {
        val state = sampleState().copy(isLoading = true, courses = emptyList())

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Loading courses…").assertExists()
        onNodeWithText("No courses match the active filter.").assertDoesNotExist()
    }

    @Test
    fun empty_result_appears_only_after_loading_completes() = runComposeUiTest {
        val state = sampleState().copy(isLoading = false, courses = emptyList())

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("No courses match the active filter.").assertExists()
        onNodeWithText("Loading courses…").assertDoesNotExist()
    }

    private fun embeddedPair() = M3ECourseAttendanceCardState(
        courseCode = "BACSE106",
        courseTitle = "Operating Systems",
        badge = "Embedded",
        attendedClasses = 42,
        totalClasses = 46,
        percentage = 0.0, // D-189 sentinel: the summed counts are not one class's attendance
        tone = M3ECardTone.NEUTRAL,
        faculty = "Dr. Rao",
        slot = "C1+L55",
        bunkSummary = "Theory 93% · Lab 89%",
        isEmbedded = true,
        theoryPct = 93.0,
        labPct = 89.0,
        isMergedPair = true
    )

    @Test
    fun embeddedPairShowsBothHalvesAndNeverAZeroHeadline() = runComposeUiTest {
        val state = sampleState().copy(courses = listOf(embeddedPair()))

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Theory 93.00%").assertExists()
        onNodeWithText("Lab 89.00%").assertExists()
        onNodeWithText("0.00%").assertDoesNotExist()
        onNodeWithText("42/46 Attended").assertExists()
    }

    @Test
    fun atRiskKeepsRealZeroAndLowCardsButDropsNeutralEmbeddedPairs() = runComposeUiTest {
        val nearTarget = M3ECourseAttendanceCardState(
            courseCode = "PHY1002",
            courseTitle = "Near Target Course",
            badge = "Theory",
            attendedClasses = 15,
            totalClasses = 20,
            percentage = 75.0,
            tone = M3ECardTone.WARNING,
            faculty = "Dr. Low",
            slot = "F1",
            bunkSummary = "On the limit"
        )
        val realZero = M3ECourseAttendanceCardState(
            courseCode = "MAT1001",
            courseTitle = "Real Published Zero",
            badge = "Theory",
            attendedClasses = 0,
            totalClasses = 20,
            percentage = 0.0,
            tone = M3ECardTone.CRITICAL,
            faculty = "Dr. Zero",
            slot = "E1",
            bunkSummary = "Attend next 15 classes"
        )
        val state = sampleState().copy(
            activeFilter = AttendanceFilter.AT_RISK,
            courses = listOf(embeddedPair(), nearTarget, realZero)
        )

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        // A published 0% is a real at-risk fact and a standalone card renders exactly as before.
        onNodeWithText("0.00%").assertExists()
        onNodeWithText("Real Published Zero").assertExists()
        // The LOW/DANGER bands stay; the neutral pair is not "at risk" and must not pad the list.
        onNodeWithText("Near Target Course").assertExists()
        onNodeWithText("Operating Systems").assertDoesNotExist()
        onNodeWithText("Theory 93.00%").assertDoesNotExist()
    }

    private fun loneEmbeddedHalf() = M3ECourseAttendanceCardState(
        courseCode = "BACSE106",
        courseTitle = "Operating Systems Theory",
        badge = "Embedded",
        attendedClasses = 25,
        totalClasses = 40,
        percentage = 62.5, // real published figure for one lone half
        tone = M3ECardTone.CRITICAL,
        faculty = "Dr. Rao",
        slot = "C1",
        bunkSummary = "Attend next 6 classes",
        isEmbedded = true,
        // isMergedPair defaults to false: a lone half is NOT a two-half merged pair
    )

    @Test
    fun loneEmbeddedHalfShowsItsOwnRealPercentage() = runComposeUiTest {
        val state = sampleState().copy(courses = listOf(loneEmbeddedHalf()))

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Operating Systems Theory").assertExists()
        onNodeWithText("62.50%").assertExists()
        onNodeWithText("Attendance not published").assertDoesNotExist()
        onNodeWithText("0.00%").assertDoesNotExist()
    }

    @Test
    fun loneEmbeddedHalfStaysInAtRisk() = runComposeUiTest {
        val state = sampleState().copy(
            activeFilter = AttendanceFilter.AT_RISK,
            courses = listOf(loneEmbeddedHalf())
        )

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Operating Systems Theory").assertExists()
        onNodeWithText("62.50%").assertExists()
    }

    @Test
    fun standaloneWithoutAPublishedFigureShowsHonestAbsence() = runComposeUiTest {
        val noData = M3ECourseAttendanceCardState(
            courseCode = "BAHUM251",
            courseTitle = "Indian Constitution",
            badge = "Theory",
            attendedClasses = 0,
            totalClasses = 0,
            percentage = 0.0,
            tone = M3ECardTone.NEUTRAL,
            faculty = "Dr. Rao",
            slot = "A1",
            bunkSummary = "Not available"
        )
        val state = sampleState().copy(courses = listOf(noData))

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Indian Constitution").assertExists()
        onNodeWithText("Attendance not published").assertExists()
        onNodeWithText("0.00%").assertDoesNotExist()
    }

    @Test
    fun publishedZeroStillRendersAndStaysAtRisk() = runComposeUiTest {
        val zero = M3ECourseAttendanceCardState(
            courseCode = "MAT1001",
            courseTitle = "Real Published Zero",
            badge = "Theory",
            attendedClasses = 0,
            totalClasses = 20,
            percentage = 0.0,
            tone = M3ECardTone.CRITICAL,
            faculty = "Dr. Zero",
            slot = "E1",
            bunkSummary = "Attend next 15 classes"
        )
        val state = sampleState().copy(
            activeFilter = AttendanceFilter.AT_RISK,
            courses = listOf(zero)
        )

        setContent {
            AmazeM3ETheme {
                M3EAttendanceScreen(state = state, onAction = {})
            }
        }

        onNodeWithText("Real Published Zero").assertExists()
        onNodeWithText("0.00%").assertExists()
        onNodeWithText("0/20 Attended").assertExists()
    }
}
