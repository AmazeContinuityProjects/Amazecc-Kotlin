package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless JVM UI tests for D-114: the Circulars page owns the real download action and
 * shows only outcomes the host actually produced (no fabricated success).
 */
@OptIn(ExperimentalTestApi::class)
class M3ECircularsScreenUiTest {

    private fun sampleState(
        downloadStates: Map<String, M3ECircularDownloadUi> = emptyMap(),
        isLoading: Boolean = false,
        errorMessage: String? = null
    ) = M3ECircularsState(
        folders = listOf(
            M3ECircularFolder(
                key = "academic",
                name = "Academic Announcements",
                documents = listOf(
                    M3ECircularDocument("CIR-1", "Exam schedule"),
                    M3ECircularDocument("CIR-2", "Revaluation window")
                )
            )
        ),
        expandedKeys = setOf("academic"),
        isLoading = isLoading,
        errorMessage = errorMessage,
        downloadStates = downloadStates
    )

    @Test
    fun everyCircularRowHasADownloadAction() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECircularsScreen(state = sampleState(), onAction = {})
            }
        }

        onNodeWithText("Exam schedule").assertExists()
        onNodeWithText("Revaluation window").assertExists()
        onAllNodesWithContentDescription("Download ", substring = true).assertCountEquals(2)
    }

    @Test
    fun downloadAffordanceEmitsRealId() = runComposeUiTest {
        val actions = mutableListOf<M3ECircularsAction>()
        setContent {
            AmazeM3ETheme {
                M3ECircularsScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Download Exam schedule").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3ECircularsAction.OpenCircular)
        assertEquals("CIR-1", (actions[0] as M3ECircularsAction.OpenCircular).id)
    }

    @Test
    fun downloadingRowShowsProgressAndDisablesDownload() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECircularsScreen(
                    state = sampleState(
                        downloadStates = mapOf(
                            "CIR-1" to M3ECircularDownloadUi(M3ECircularDownloadStatus.DOWNLOADING)
                        )
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Downloading\u2026").assertExists()
        onNodeWithContentDescription("Download Exam schedule").assertIsNotEnabled()
    }

    @Test
    fun failedRowShowsRealErrorAndRetriesWithSameId() = runComposeUiTest {
        val actions = mutableListOf<M3ECircularsAction>()
        setContent {
            AmazeM3ETheme {
                M3ECircularsScreen(
                    state = sampleState(
                        downloadStates = mapOf(
                            "CIR-1" to M3ECircularDownloadUi(
                                M3ECircularDownloadStatus.FAILED,
                                "Network unavailable"
                            )
                        )
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Network unavailable").assertExists()
        onNodeWithContentDescription("Retry download Exam schedule").performClick()
        assertEquals(1, actions.size)
        assertEquals("CIR-1", (actions[0] as M3ECircularsAction.OpenCircular).id)
    }

    @Test
    fun savedRowShowsSavedFileOnlyWhenHostReportsIt() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECircularsScreen(
                    state = sampleState(
                        downloadStates = mapOf(
                            "CIR-1" to M3ECircularDownloadUi(
                                M3ECircularDownloadStatus.SAVED,
                                "exam-schedule.pdf"
                            )
                        )
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Saved \u00B7 exam-schedule.pdf").assertExists()
        // The untouched sibling never claims a download happened.
        onNodeWithText("Downloading\u2026").assertDoesNotExist()
    }

    @Test
    fun pageLoadingAndErrorStatesAreMounted() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ECircularsScreen(
                    state = sampleState(isLoading = true, errorMessage = "Could not load circulars"),
                    onAction = {}
                )
            }
        }

        // M3EPageSkeleton owns the label and nests M3EListSkeleton with the same label, so the
        // real loading UI exposes exactly two aria-busy skeleton nodes \u2014 assert that exact count.
        onAllNodesWithContentDescription("Loading circulars").assertCountEquals(2)
        onNodeWithText("Could not load circulars").assertExists()
    }
}
