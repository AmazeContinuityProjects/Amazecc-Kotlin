package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-117: the feedback status screen is two honest groups, each with a real tick or cross. */
@OptIn(ExperimentalTestApi::class)
class FeedbackStatusGroupingUiTest {

    private fun singleRecordState(
        mid: String,
        tee: String,
        type: String = "CSE1001 \u00B7 Programming / Dr. Anita Rao"
    ) = M3EFeedbackStatusState(
        semesters = listOf(M3EFeedbackSemester("CH20262701", "Fall 2026-27")),
        selectedSemesterValue = "CH20262701",
        rows = listOf(M3EFeedbackRow(type, mid, tee))
    )

    @Test
    fun realPhraseAloneDrivesTheStatus() {
        assertTrue(isFeedbackGiven("Feed Back Given For : CSE1001"))
        assertTrue(isFeedbackGiven("feed back given for : cse1001"))
        assertFalse(isFeedbackGiven("Not Given"))
        assertFalse(isFeedbackGiven("Feedback Given For"))
        assertFalse(isFeedbackGiven(""))
        assertFalse(isFeedbackGiven(null))
    }

    @Test
    fun rendersExactlyTwoGroupsAndNoTableHeader() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFeedbackStatusScreen(
                    state = singleRecordState("Feed Back Given For : CSE1001", "Not Given"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Mid Semester").assertExists()
        onNodeWithText("End Semester (TEE Semester)").assertExists()
        // The old three-column table header is gone.
        onNodeWithText("Feedback").assertDoesNotExist()
    }

    @Test
    fun givenPhraseShowsTickInMidAndCrossInTee() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFeedbackStatusScreen(
                    state = singleRecordState("Feed Back Given For : CSE1001", "Not Given"),
                    onAction = {}
                )
            }
        }

        onNodeWithContentDescription("Mid Semester \u2014 Feedback given").assertExists()
        onNodeWithTag(M3EFeedbackStatusTestTags.LIST)
            .performScrollToNode(hasText("End Semester (TEE Semester)"))
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback not given").assertExists()
        onNodeWithContentDescription("Mid Semester \u2014 Feedback not given").assertDoesNotExist()
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback given").assertDoesNotExist()
    }

    @Test
    fun givenPhraseShowsTickInTeeGroupAsWell() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFeedbackStatusScreen(
                    state = singleRecordState(
                        "Feed Back Given For : CSE1001",
                        "Feed Back Given For : CSE1001"
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithContentDescription("Mid Semester \u2014 Feedback given").assertExists()
        onNodeWithTag(M3EFeedbackStatusTestTags.LIST)
            .performScrollToNode(hasText("End Semester (TEE Semester)"))
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback given").assertExists()
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback not given").assertDoesNotExist()
    }

    @Test
    fun missingPhraseShowsCrossInBothGroups() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFeedbackStatusScreen(
                    state = singleRecordState("Not Given", "Not Given"),
                    onAction = {}
                )
            }
        }

        onNodeWithContentDescription("Mid Semester \u2014 Feedback not given").assertExists()
        onNodeWithTag(M3EFeedbackStatusTestTags.LIST)
            .performScrollToNode(hasText("End Semester (TEE Semester)"))
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback not given").assertExists()
        onNodeWithContentDescription("Mid Semester \u2014 Feedback given").assertDoesNotExist()
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback given").assertDoesNotExist()
    }

    @Test
    fun keepsRealCourseAndRawCellTextVisible() = runComposeUiTest {
        val type = "CSE1001 \u00B7 Programming / Dr. Anita Rao"
        val raw = "Feed Back Given For : CSE1001 / Dr. Anita Rao"

        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFeedbackStatusScreen(
                    state = singleRecordState(raw, "Not Given", type),
                    onAction = {}
                )
            }
        }

        onNodeWithTag(M3EFeedbackStatusTestTags.LIST)
            .performScrollToNode(hasText(type, substring = true))
        // The group label is also present in the section header's semantics, so
        // more than one node can match; the raw cell text must still be visible.
        onAllNodesWithText(type)[0].assertExists()
        onNodeWithText(raw).assertExists()
    }

    @Test
    fun honestEmptyStatePerGroupWithNoRecords() = runComposeUiTest {
        setContent {
            AmazeM3ETheme(isDark = false) {
                M3EFeedbackStatusScreen(
                    state = M3EFeedbackStatusState(
                        semesters = listOf(M3EFeedbackSemester("CH20262701", "Fall 2026-27")),
                        selectedSemesterValue = "CH20262701"
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Mid Semester").assertExists()
        onNodeWithText("End Semester (TEE Semester)").assertExists()
        onNodeWithText("No mid-semester feedback records were returned for this semester.").assertExists()

        onNodeWithTag(M3EFeedbackStatusTestTags.LIST)
            .performScrollToNode(hasText("No end-semester (TEE) feedback records", substring = true))
        onNodeWithText("No end-semester (TEE) feedback records were returned for this semester.").assertExists()

        // No fabricated records means no status icons in either empty group.
        onNodeWithContentDescription("Mid Semester \u2014 Feedback given").assertDoesNotExist()
        onNodeWithContentDescription("Mid Semester \u2014 Feedback not given").assertDoesNotExist()
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback given").assertDoesNotExist()
        onNodeWithContentDescription("End Semester (TEE Semester) \u2014 Feedback not given").assertDoesNotExist()
    }
}
