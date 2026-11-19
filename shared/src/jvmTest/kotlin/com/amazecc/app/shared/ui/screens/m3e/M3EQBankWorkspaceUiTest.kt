package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class M3EQBankWorkspaceUiTest {

    private fun sampleState() = M3EQBankWorkspaceState(
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        questions = listOf(
            M3EQBankQuestionItem("q1", "What does HTTP stand for?", "MCQ", mapOf("a" to "Hyper Text Transfer Protocol", "b" to "High Text Transfer Protocol"), "a", 1, "Web", "Winter 2025"),
            M3EQBankQuestionItem("q2", "Explain REST in your own words.", "Descriptive", null, "Representational state transfer", 10, "Web", "Winter 2025")
        ),
        papers = listOf(M3EQBankPaperItem("CAT 1 — 2025", "https://example.com/paper", "CAT 1")),
        stats = M3EQBankCourseStatsItem(
            courseCode = "CSE3002",
            attempts = 4,
            correct = 3,
            topics = listOf(M3EQBankTopicStatItem("Web", 4, 3, 240L)),
            missedIds = listOf("q2")
        )
    )

    @Test
    fun rendersHeaderStatsTabsQuestionsAndReview() = runComposeUiTest {
        setContent {
            AmazeM3ETheme { M3EQBankWorkspaceScreen(state = sampleState(), onAction = {}) }
        }
        onNodeWithText("CSE3002").assertExists()
        onNodeWithText("Internet and Web Programming").assertExists()
        onNodeWithText("Practice performance").assertExists()
        onNodeWithText("75.00%").assertExists()
        onNodeWithText("What does HTTP stand for?").assertExists()
        onNodeWithText("Review 1 missed question").assertExists()
    }

    @Test
    fun switchingTabsAndFiltersEmitsActions() = runComposeUiTest {
        val actions = mutableListOf<M3EQBankWorkspaceAction>()
        setContent {
            AmazeM3ETheme {
                M3EQBankWorkspaceScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }
        onNodeWithText("Paper Archive").performClick()
        onNodeWithText("Analysis").performClick()
        assertTrue(actions.filterIsInstance<M3EQBankWorkspaceAction.SelectTab>().any { it.tab == M3EQBankWorkspaceTab.ARCHIVE })
        assertTrue(actions.filterIsInstance<M3EQBankWorkspaceAction.SelectFilter>().any { it.filter == M3EQBankPracticeFilter.ANALYSIS })
    }

    @Test
    fun openingAQuestionEmitsIndexedAction() = runComposeUiTest {
        val actions = mutableListOf<M3EQBankWorkspaceAction>()
        setContent {
            AmazeM3ETheme {
                M3EQBankWorkspaceScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }
        onNodeWithText("Explain REST in your own words.").performClick()
        val open = actions.filterIsInstance<M3EQBankWorkspaceAction.OpenQuestion>().firstOrNull()
        assertEquals(1, open?.index)
    }

    @Test
    fun quizRendersOptionsAndEmitsAnswerFlagTimerAndNavActions() = runComposeUiTest {
        val actions = mutableListOf<M3EQBankWorkspaceAction>()
        val quizState = sampleState().copy(view = M3EQBankWorkspaceView.QUIZ, activeIndex = 0, secondsLeft = 45)
        setContent {
            AmazeM3ETheme {
                M3EQBankWorkspaceScreen(state = quizState, onAction = { actions.add(it) })
            }
        }
        onNodeWithText("Question 1 of 2").assertExists()
        onNodeWithText("Hyper Text Transfer Protocol").performClick()
        onNodeWithContentDescription("Flag question").performClick()
        onNodeWithContentDescription("Timer on").performClick()
        onNodeWithText("›").performClick()
        assertTrue(actions.filterIsInstance<M3EQBankWorkspaceAction.SetAnswer>().any { it.questionId == "q1" && it.value == "a" })
        assertTrue(actions.any { it is M3EQBankWorkspaceAction.ToggleFlag })
        assertTrue(actions.any { it is M3EQBankWorkspaceAction.ToggleTimer })
        assertTrue(actions.any { it is M3EQBankWorkspaceAction.NextQuestion })
    }

    @Test
    fun offlineBannerIsShown() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EQBankWorkspaceScreen(
                    state = sampleState().copy(isOffline = true, errorMessage = "offline"),
                    onAction = {}
                )
            }
        }
        onNodeWithText("Offline — papers and questions need a connection. Your local practice stats below are still available.").assertExists()
        onNodeWithText("Practice performance").assertExists()
    }

    @Test
    fun catalogRendersOfflineBannerAndEmitsOpenCourse() = runComposeUiTest {
        val actions = mutableListOf<M3EQBankAction>()
        val state = M3EQBankState(
            courses = listOf(M3EQBankCourse("CSE3002", "Internet and Web Programming")),
            filteredCourses = listOf(M3EQBankCourse("CSE3002", "Internet and Web Programming")),
            stats = M3EQBankStats(attempted = 4, correct = 3, accuracyPct = 75.0),
            isOffline = true
        )
        setContent {
            AmazeM3ETheme { M3EQBankScreen(state = state, onAction = { actions.add(it) }) }
        }
        onNodeWithText("Offline — the course catalog requires a connection. Your local practice stats above are still available.").assertExists()
        onNodeWithText("Internet and Web Programming").performClick()
        assertTrue(actions.filterIsInstance<M3EQBankAction.OpenCourse>().any { it.courseCode == "CSE3002" })
    }
}
