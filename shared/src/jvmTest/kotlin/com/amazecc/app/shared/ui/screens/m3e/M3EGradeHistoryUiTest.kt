package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EGradeBreakdownRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseDetail
import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeDistributionRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeInsightSlide
import com.amazecc.app.shared.domain.overhaul.M3EGradePointTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeScaleRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeScreen
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermOption
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermScore
import com.amazecc.app.shared.domain.overhaul.M3EGradeTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeTypeAverage
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryAction
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EGradeHistoryUiTest {

    private fun sampleState(
        screen: M3EGradeScreen = M3EGradeScreen.OVERVIEW,
        selectedCourseCode: String? = null,
        courseDetail: M3EGradeCourseDetail? = null
    ) = M3EGradesHistoryState(
        cumulativeGpa = 9.24,
        totalCreditsEarned = 124,
        totalCreditsAttempted = 128,
        semesters = emptyList(),
        screen = screen,
        selectedCourseCode = selectedCourseCode,
        selectedTermId = "t2",
        cumulativeValue = "9.24",
        cumulativeBadge = "124/160 cr",
        cumulativeSubtitle = "Credit-weighted CGPA",
        termOptions = listOf(
            M3EGradeTermOption("t2", "Fall 2025-26", "Fall '26"),
            M3EGradeTermOption("t1", "Winter 2024-25", "Winter '25")
        ),
        courseRows = listOf(
            M3EGradeCourseRow("CSE3002", "Internet and Web Programming", "92.5%", 92.5, "S", M3EGradeTone.AMBER, true, "Embedded Theory"),
            M3EGradeCourseRow("MAT2001", "Complex Variables", "88%", 88.0, "A", M3EGradeTone.GREEN, false, "Theory Only")
        ),
        termRows = listOf(
            M3EGradeTermRow("t2", "Fall 2025-26", 9.35, 2, 2, 90.25, M3EGradePointTone.SUCCESS),
            M3EGradeTermRow("t1", "Winter 2024-25", 8.4, 2, 2, 84.0, M3EGradePointTone.PRIMARY)
        ),
        insightSlides = listOf(
            M3EGradeInsightSlide("term", "This term", "9.35", "Fall 2025-26 · 2 courses", "GPA", M3EGradeTone.GREEN),
            M3EGradeInsightSlide("best", "Best term", "9.35", "Fall 2025-26", "Peak", M3EGradeTone.GREEN),
            M3EGradeInsightSlide("terms", "Terms recorded", "2", "across 2 published", "History", M3EGradeTone.GREY)
        ),
        distributionRows = listOf(
            M3EGradeDistributionRow("S", 3, 60.0, M3EGradeTone.AMBER),
            M3EGradeDistributionRow("A", 2, 40.0, M3EGradeTone.GREEN)
        ),
        typeAverages = listOf(M3EGradeTypeAverage("Theory Only", 88.0, 2)),
        termScores = listOf(
            M3EGradeTermScore("t2", "Fall 2025-26", 90.25, 2),
            M3EGradeTermScore("t1", "Winter 2024-25", 84.0, 2)
        ),
        courseDetail = courseDetail
    )

    private fun sampleCourseDetail() = M3EGradeCourseDetail(
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        courseType = "Embedded Theory",
        overallText = "92.5%",
        grade = "S",
        pointsLabel = "10 / 10",
        assessmentCount = 1,
        breakdown = listOf(
            M3EGradeBreakdownRow("CAT 1", "40", "18", "20", 40.0),
            M3EGradeBreakdownRow("FAT", "60", "28", "30", 60.0)
        ),
        gradeScale = listOf(
            M3EGradeScaleRow("S", "90 and above", M3EGradeTone.AMBER, true),
            M3EGradeScaleRow("A", "80 - 89", M3EGradeTone.GREEN, false)
        ),
        embeddedHalves = emptyList(),
        isEmbedded = true
    )

    @Test
    fun overviewRendersHeroTilesAndSections() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EGradesHistoryScreen(state = sampleState(), onAction = {})
            }
        }

        onNodeWithText("Grade History").assertExists()
        onNodeWithText("Every term's results in one place.").assertExists()
        onNodeWithText("9.24").assertExists()
        onNodeWithText("124/160 cr").assertExists()
        onNodeWithText("Credit-weighted CGPA").assertExists()
        onNodeWithText("Courses").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("92.5%").assertExists()
        onNodeWithText("T+L").assertExists()
        onNodeWithText("Across terms").assertExists()
        onNodeWithText("Fall 2025-26").assertExists()
        onNodeWithText("THIS TERM").assertExists()
        onRoot().performTouchInput { swipeUp() }
        onNodeWithText("Grade insights").assertExists()
    }

    @Test
    fun termSwitchingEmitsSelectTermAction() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Winter '25").performClick()

        assertEquals(
            listOf(M3EGradesHistoryAction.SelectTerm("t1")),
            actions.filterIsInstance<M3EGradesHistoryAction.SelectTerm>()
        )
    }

    @Test
    fun overviewEmitsOpenCourseInsightsAndRefresh() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("CSE3002").performClick()
        onRoot().performTouchInput { swipeUp() }
        onNodeWithText("Grade insights").performClick()
        onNodeWithContentDescription("Sync grades").performClick()

        assertTrue(actions.any { it == M3EGradesHistoryAction.OpenCourse("CSE3002") })
        assertTrue(actions.any { it == M3EGradesHistoryAction.OpenInsights })
        assertTrue(actions.any { it == M3EGradesHistoryAction.Refresh })
    }

    @Test
    fun insightsScreenRendersDerivedSectionsAndBack() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(
                    state = sampleState(screen = M3EGradeScreen.INSIGHTS),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Grade Insights").assertExists()
        onNodeWithText("How your grades are spread, across every term.").assertExists()
        onNodeWithText("TERMS RECORDED").assertExists()
        onNodeWithText("BEST TERM").assertExists()
        onNodeWithText("Grade distribution").assertExists()
        onNodeWithText("60%").assertExists()
        onNodeWithText("Theory against lab").assertExists()
        onNodeWithText("88.0%").assertExists()
        onNodeWithText("Score by term").assertExists()
        onNodeWithText("90.3%").assertExists()

        onNodeWithContentDescription("Back").performClick()
        assertTrue(actions.any { it == M3EGradesHistoryAction.BackToOverview })
    }

    @Test
    fun courseSubpageRendersDetailAndBackToOverview() = runComposeUiTest {
        val actions = mutableListOf<M3EGradesHistoryAction>()
        setContent {
            AmazeM3ETheme {
                M3EGradesHistoryScreen(
                    state = sampleState(
                        screen = M3EGradeScreen.COURSE,
                        selectedCourseCode = "CSE3002",
                        courseDetail = sampleCourseDetail()
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("as recorded by VTOP").assertExists()
        onNodeWithText("10 / 10").assertExists()
        onNodeWithText("1 assessment").assertExists()
        onNodeWithText("Published breakdown").assertExists()
        onNodeWithText("40% weightage").assertExists()
        onNodeWithText("18/20").assertExists()
        onNodeWithText("Grade scale").assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("90 and above"))
        onNodeWithText("90 and above").assertExists()
        onNodeWithText("Yours").assertExists()

        onNodeWithContentDescription("Back").performClick()
        assertTrue(actions.any { it == M3EGradesHistoryAction.BackToOverview })
    }
}
