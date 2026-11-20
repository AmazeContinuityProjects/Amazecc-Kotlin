package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * D-119: every sheet hosted by [M3EDismissibleSheet] exposes one tagged content
 * area, and that area is the one carrying the bottom nav-bar inset.
 */
@OptIn(ExperimentalTestApi::class)
class M3EDismissibleSheetUiTest {

    @Test
    fun openSheetExposesBottomPaddedContentAreaTag() = runComposeUiTest {
        setContent {
            M3EDismissibleSheet(
                isOpen = true,
                onDismiss = {},
                title = "D-119 sheet"
            ) {
                Text("D-119 sheet body")
            }
        }

        // The tagged column is the sheet's content area; D-119 adds
        // navigationBarsPadding() + 16 dp bottom padding to it.
        onNodeWithTag("m3e_sheet_content", useUnmergedTree = true).assertExists()
        onNodeWithText("D-119 sheet body").assertExists()
    }
}
