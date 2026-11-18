package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless JVM UI tests for the stateless M3E About screen. */
@OptIn(ExperimentalTestApi::class)
class M3ECampusAboutUiTest {

    private fun sampleState() = M3EAboutState(
        version = "2.4.0",
        updateStatusText = "You are up to date",
        releaseHighlights = listOf("Faster sync")
    )

    @Test
    fun rendersIdentityAndActions() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EAboutScreen(state = sampleState(), onAction = {})
            }
        }

        onNodeWithText("About AmazeCC").assertExists()
        onNodeWithText("AmazeCC").assertExists()
        onNodeWithText("v2.4.0").assertExists()
        onNodeWithText("Check for Updates").assertExists()
        onNodeWithText("What's New (Changelog)").assertExists()
        onNodeWithText("You are up to date").assertExists()
    }

    @Test
    fun checkForUpdatesEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EAboutAction>()
        setContent {
            AmazeM3ETheme {
                M3EAboutScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Check for Updates").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EAboutAction.CheckForUpdates)
    }

    @Test
    fun changelogDialogShowsHighlights() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EAboutScreen(
                    state = sampleState().copy(showChangelog = true),
                    onAction = {}
                )
            }
        }

        onNodeWithText("What's New in v2.4.0").assertExists()
        onNodeWithText("• Faster sync").assertExists()
        onNodeWithText("Close").performClick()
    }
}
