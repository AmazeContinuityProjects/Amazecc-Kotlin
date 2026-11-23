package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-185 wiring evidence: the three new export cards in the RESULTS tab emit the
 * exact actions the host bridge depends on. These cards are late LazyColumn items,
 * so each must be scrolled into the viewport before its click or the click is
 * silently dropped. The captured list is compared verbatim, which proves both the
 * correct action per card and the absence of any unexpected actions.
 */
@OptIn(ExperimentalTestApi::class)
class M3EFfcsExportWiringUiTest {

    @Test
    fun exportCardsEmitExactActions() = runComposeUiTest {
        val captured = mutableListOf<M3EFfcsPlannerAction>()
        setContent {
            AmazeM3ETheme {
                M3EFfcsPlannerScreen(
                    state = M3EFfcsPlannerState(activeTab = M3EFfcsTab.RESULTS),
                    onAction = { captured.add(it) }
                )
            }
        }

        // Scroll each late LazyColumn item into view before clicking it.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export PDF"))
        onNodeWithText("Export PDF").performClick()

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export JSON"))
        onNodeWithText("Export JSON").performClick()

        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export clash report"))
        onNodeWithText("Export clash report").performClick()

        assertEquals(
            listOf(
                M3EFfcsPlannerAction.ExportPdf,
                M3EFfcsPlannerAction.ExportJson,
                M3EFfcsPlannerAction.ExportClashReport
            ),
            captured
        )
    }
}
