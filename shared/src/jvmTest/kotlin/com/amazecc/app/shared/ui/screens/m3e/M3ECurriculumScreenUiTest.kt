package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.CourseCompletionStatus
import com.amazecc.app.shared.domain.overhaul.CurriculumBasket
import com.amazecc.app.shared.domain.overhaul.M3EBasketProgress
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumAction
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumCourse
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumState
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The curriculum screen has both a vertical list and a horizontal chip row; target the list. */
private val verticalScroller = SemanticsMatcher("vertical scroller") { node ->
    node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
}

@OptIn(ExperimentalTestApi::class)
class M3ECurriculumScreenUiTest {

    private fun sampleState() = M3ECurriculumState(
        programmeName = "B.Tech CSE (2021-25)",
        totalCreditsCompleted = 124,
        totalCreditsRequired = 160,
        baskets = listOf(
            M3EBasketProgress(CurriculumBasket.PROGRAM_CORE, 54, 58),
            M3EBasketProgress(CurriculumBasket.PROGRAM_ELECTIVE, 24, 36)
        ),
        courses = listOf(
            M3ECurriculumCourse(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                credits = 4,
                basket = CurriculumBasket.PROGRAM_CORE,
                courseType = "Embedded",
                status = CourseCompletionStatus.COMPLETED,
                grade = "S"
            )
        ),
        filteredCourses = listOf(
            M3ECurriculumCourse(
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                credits = 4,
                basket = CurriculumBasket.PROGRAM_CORE,
                courseType = "Embedded",
                status = CourseCompletionStatus.COMPLETED,
                grade = "S"
            )
        )
    )

    @Test
    fun testCurriculumRendersDegreeProgressAndCourse() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECurriculumScreen(
                    state = sampleState(),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Degree Curriculum").assertExists()
        onNodeWithText("B.Tech CSE (2021-25)").assertExists()
        onNodeWithText("77.50%").assertExists() // 124 / 160 = 77.5% -> 77.50%
        onNodeWithText("124 / 160 Credits Completed").assertExists()
        onNodeWithText("36 Credits Remaining").assertExists()
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Grade S").assertExists()
    }

    @Test
    fun testBasketFilterSelectionEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECurriculumAction>()

        setContent {
            AmazeM3ETheme {
                M3ECurriculumScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNode(hasText("Program Core") and hasClickAction()).performClick()

        assertTrue(capturedActions.isNotEmpty())
        val basketAction = capturedActions.filterIsInstance<M3ECurriculumAction.SelectBasket>().firstOrNull()
        assertEquals(CurriculumBasket.PROGRAM_CORE, basketAction?.basket)
    }

    /** Two labelled ETH/ELA halves of one 5-credit embedded course. */
    private fun embeddedState(theoryGrade: String = "S", labGrade: String? = null): M3ECurriculumState {
        val halves = listOf(
            M3ECurriculumCourse(
                courseCode = "BACSE106",
                courseTitle = "Operating Systems",
                credits = 3,
                basket = CurriculumBasket.PROGRAM_CORE,
                courseType = "ETH",
                status = CourseCompletionStatus.COMPLETED,
                grade = theoryGrade,
                embeddedLabel = "Embedded Theory",
                baseCode = "BACSE106",
            ),
            M3ECurriculumCourse(
                courseCode = "BACSE106(L)",
                courseTitle = "Operating Systems Lab",
                credits = 2,
                basket = CurriculumBasket.PROGRAM_CORE,
                courseType = "ELA",
                status = CourseCompletionStatus.COMPLETED,
                grade = labGrade,
                embeddedLabel = "Embedded Lab",
                baseCode = "BACSE106",
            ),
        )
        return M3ECurriculumState(
            programmeName = "B.Tech CSE",
            totalCreditsCompleted = 5,
            totalCreditsRequired = 160,
            baskets = listOf(M3EBasketProgress(CurriculumBasket.PROGRAM_CORE, 5, 5)),
            courses = halves,
            filteredCourses = halves,
        )
    }

    @Test
    fun testEmbeddedPairShowsWebExactLabelAndSummedCreditsOnce() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECurriculumScreen(state = embeddedState(), onAction = {})
            }
        }

        // The course rows are late LazyColumn items: scroll them into view first.
        onNode(hasScrollToNodeAction() and verticalScroller)
            .performScrollToNode(hasText("Embedded Theory & Lab"))

        onAllNodesWithText("Embedded Theory & Lab").assertCountEquals(1) // exactly one header
        onNodeWithText("5 Cr total").assertExists() // 3 + 2 summed ONCE
        onNodeWithText("Embedded Theory").assertExists() // honest per-half chip
        onNodeWithText("Embedded Lab").assertExists()
        onNodeWithText("Grade S").assertExists() // the pair's published letter
        onNodeWithText("Attendance", substring = true).assertDoesNotExist() // no blended percentage
    }

    @Test
    fun testDifferentHalfGradesNeverFabricateACombinedGrade() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECurriculumScreen(
                    state = embeddedState(theoryGrade = "S", labGrade = "A"),
                    onAction = {},
                )
            }
        }

        onNode(hasScrollToNodeAction() and verticalScroller)
            .performScrollToNode(hasText("Embedded Theory & Lab"))

        onNodeWithText("Grade S").assertExists()
        onNodeWithText("Grade A").assertExists() // two separate grade entries
        onNodeWithText("Combined grade", substring = true).assertDoesNotExist()
    }

    @Test
    fun testDownloadCurriculumClickEmitsAction() = runComposeUiTest {
        val capturedActions = mutableListOf<M3ECurriculumAction>()

        setContent {
            AmazeM3ETheme {
                M3ECurriculumScreen(
                    state = sampleState(),
                    onAction = { capturedActions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Download Curriculum").performClick()

        assertTrue(capturedActions.contains(M3ECurriculumAction.DownloadCurriculum))
    }
}
