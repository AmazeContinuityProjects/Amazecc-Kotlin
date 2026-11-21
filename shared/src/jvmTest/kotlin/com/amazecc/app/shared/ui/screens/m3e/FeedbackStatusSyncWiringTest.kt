package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-156: feedback status is fetched only by the central sync module. The host is flow-only on
 * composition and its Refresh control requests a single-module sync retry through an injectable
 * callback (default: AppState.refreshFeedbackStatus(), which delegates to retryModule's
 * working single-module path).
 *
 * No network call is made on composition by construction — the host collects
 * AppState.feedbackStatus and never calls AmazeClient.getFeedbackStatus (code-review invariant;
 * there is no source-file assertion precedent in this test source set).
 */
@OptIn(ExperimentalTestApi::class)
class FeedbackStatusSyncWiringTest {

    @Test
    fun refreshRequestsExactlyOneCentralSyncRetry() = runComposeUiTest {
        var syncRetries = 0

        setContent {
            AmazeM3ETheme {
                M3EFeedbackStatusScreenHost(onRefreshFeedback = { syncRetries++ })
            }
        }

        // The header exposes one real Refresh control.
        onAllNodesWithContentDescription("Refresh").assertCountEquals(1)

        // Composition itself must not trigger the sync callback (no fetch on entry).
        assertEquals(0, syncRetries)

        onAllNodesWithContentDescription("Refresh")[0].performClick()
        assertEquals(1, syncRetries)
    }
}
