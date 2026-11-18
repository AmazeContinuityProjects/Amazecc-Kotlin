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

/** Headless JVM UI tests for the stateless M3E Changelog screen. */
@OptIn(ExperimentalTestApi::class)
class M3ECampusChangelogUiTest {

    private fun sampleState() = M3EChangelogState(
        version = "2.4.0",
        entries = listOf(
            M3EChangelogEntry("Faster sync", "Campus modules sync in parallel."),
            M3EChangelogEntry("Expressive surfaces", "Screens adopt M3 Expressive motion.")
        )
    )

    @Test
    fun rendersReleaseNotes() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EChangelogScreen(state = sampleState(), onAction = {})
            }
        }

        onNodeWithText("What's New").assertExists()
        onNodeWithText("Version 2.4.0").assertExists()
        onNodeWithText("Faster sync").assertExists()
        onNodeWithText("Campus modules sync in parallel.").assertExists()
        onNodeWithText("Expressive surfaces").assertExists()
    }

    @Test
    fun emptyStateRendersFallback() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EChangelogScreen(state = M3EChangelogState(), onAction = {})
            }
        }

        onNodeWithText("No release notes available yet").assertExists()
    }

    @Test
    fun refreshEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EChangelogAction>()
        setContent {
            AmazeM3ETheme {
                M3EChangelogScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Refresh changelog").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EChangelogAction.Refresh)
    }
}
