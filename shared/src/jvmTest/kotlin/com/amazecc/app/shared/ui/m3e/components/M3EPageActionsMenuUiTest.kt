package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.ui.Modifier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-116: the page-actions FAB must stay out of the way on single-action pages,
 * expose one labelled toggle for 2+ actions, and invoke the exact lambda of the
 * item that was clicked (then collapse).
 */
@OptIn(ExperimentalTestApi::class)
class M3EPageActionsMenuUiTest {

    @Test
    fun zeroActionsRendersNothing() = runComposeUiTest {
        setContent { M3EPageActionsMenu(actions = emptyList()) }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE_AREA).assertDoesNotExist()
        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).assertDoesNotExist()
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertDoesNotExist()
    }

    @Test
    fun oneActionRendersNothingSoTheInlineButtonIsKept() = runComposeUiTest {
        setContent {
            M3EPageActionsMenu(
                actions = listOf(M3EPageAction("Refresh", Icons.Rounded.Refresh, {}))
            )
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).assertDoesNotExist()
        onNodeWithText("Refresh").assertDoesNotExist()
    }

    @Test
    fun twoActionsExposeToggleAndExpandToLabelledItems() = runComposeUiTest {
        setContent {
            M3EPageActionsMenu(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, {}),
                    M3EPageAction("Add task", Icons.Rounded.Add, {})
                )
            )
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).assertExists()
        onNodeWithContentDescription("More actions").assertExists()
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertDoesNotExist()

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        waitForIdle()

        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertExists()
        onNodeWithText("Refresh").assertExists()
        onNodeWithText("Add task").assertExists()
        onNodeWithContentDescription("Close actions").assertExists()
    }

    @Test
    fun clickingAnItemInvokesOnlyItsLambdaAndCollapses() = runComposeUiTest {
        val called = mutableListOf<String>()
        setContent {
            M3EPageActionsMenu(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, { called.add("refresh") }),
                    M3EPageAction("Add task", Icons.Rounded.Add, { called.add("add") }),
                    M3EPageAction("Delete", Icons.Rounded.Delete, { called.add("delete") })
                )
            )
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        waitForIdle()
        // Fire the item's own OnClick semantics action directly. The items are
        // laid out below the 56dp toggle box, so desktop mouse injection at the
        // node centre can miss them; the semantics action is the a11y contract.
        onNodeWithTag(M3EPageActionsMenuTestTags.item(2))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle() // let the M3E exit spring finish removing the items

        assertEquals(listOf("delete"), called)
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertDoesNotExist()
        onNodeWithContentDescription("More actions").assertExists()
    }

    @Test
    fun disabledItemIsVisibleButDoesNotInvokeItsLambda() = runComposeUiTest {
        var calls = 0
        setContent {
            M3EPageActionsMenu(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, { calls++ }),
                    M3EPageAction("Delete", Icons.Rounded.Delete, { calls++ }, enabled = false)
                )
            )
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        waitForIdle()
        onNodeWithText("Delete").assertExists()
        // A disabled item must be inert on the real click path (Compose's clickable
        // honours enabled=false); performSemanticsAction would bypass that guard.
        onNodeWithTag(M3EPageActionsMenuTestTags.item(1)).performClick()

        assertEquals(0, calls)
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertExists()
    }

    @Test
    fun customToggleLabelsAreHonouredForWiredScreens() = runComposeUiTest {
        setContent {
            M3EPageActionsMenu(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, {}),
                    M3EPageAction("Add task", Icons.Rounded.Add, {})
                ),
                collapsedLabel = "More calendar actions",
                expandedLabel = "Close calendar actions"
            )
        }

        onNodeWithContentDescription("More calendar actions").assertExists()
        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        onNodeWithContentDescription("Close calendar actions").assertExists()
    }

    // ---- D-124: page-root overlay host -------------------------------------

    @Test
    fun hostRootOverlayMountsFabAfterBodyAndOpensRealActions() = runComposeUiTest {
        var called = ""
        setContent {
            M3EPageActionsHost(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, { called = "refresh" }),
                    M3EPageAction("Add task", Icons.Rounded.Add, { called = "add" })
                )
            ) {
                // A full-size sibling body: the FAB must still be on top of it.
                Box(Modifier.fillMaxSize()) { Text("Page body") }
            }
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.HOST_ROOT).assertExists()
        onNodeWithText("Page body").assertExists()
        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).assertExists()

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        waitForIdle()
        onNodeWithText("Add task").assertExists()
        onNodeWithTag(M3EPageActionsMenuTestTags.item(1))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals("add", called)
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertDoesNotExist()
    }

    @Test
    fun backdropTapDismissesExpandedMenu() = runComposeUiTest {
        setContent {
            M3EPageActionsMenu(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, {}),
                    M3EPageAction("Add task", Icons.Rounded.Add, {})
                )
            )
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        waitForIdle()
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertExists()

        onNodeWithTag(M3EPageActionsMenuTestTags.BACKDROP).performClick()
        waitForIdle()

        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertDoesNotExist()
    }

    @Test
    fun escapeDismissesExpandedMenu() = runComposeUiTest {
        setContent {
            M3EPageActionsMenu(
                actions = listOf(
                    M3EPageAction("Refresh", Icons.Rounded.Refresh, {}),
                    M3EPageAction("Add task", Icons.Rounded.Add, {})
                )
            )
        }

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performClick()
        waitForIdle()
        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertExists()

        onNodeWithTag(M3EPageActionsMenuTestTags.TOGGLE).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        onNodeWithTag(M3EPageActionsMenuTestTags.ITEMS).assertDoesNotExist()
    }
}
