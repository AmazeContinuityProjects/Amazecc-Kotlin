package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        onNodeWithText("77%").assertExists() // 124 / 160 = 77.5% -> 77%
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
