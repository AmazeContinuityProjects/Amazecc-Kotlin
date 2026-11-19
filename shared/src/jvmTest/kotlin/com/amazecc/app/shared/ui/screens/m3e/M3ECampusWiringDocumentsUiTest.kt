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

/** Headless JVM UI tests for the stateless, list-based M3E Documents screen. */
@OptIn(ExperimentalTestApi::class)
class M3ECampusWiringDocumentsUiTest {

    private fun sampleState() = M3EDocumentsState(
        documents = listOf(
            M3EDocumentCard(
                id = "bonafide",
                title = "Bonafide Certificate",
                subtitle = "Internship purpose",
                details = listOf(M3EDocumentRow("Register No.", "21BCE1042"))
            ),
            M3EDocumentCard(
                id = "etranscript",
                title = "E-Transcript",
                subtitle = "Semester-wise grades"
            ),
            M3EDocumentCard(
                id = "additional-learning",
                title = "Additional Learning"
            )
        )
    )

    @Test
    fun rendersOneCardPerDocument() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(state = sampleState(), onAction = {})
            }
        }

        onNodeWithText("Documents").assertExists()
        onNodeWithText("Bonafide Certificate").assertExists()
        onNodeWithText("E-Transcript").assertExists()
        onNodeWithText("Additional Learning").assertExists()
        onNodeWithText("21BCE1042").assertExists()
    }

    @Test
    fun refreshEmitsAction() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentsAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Refresh documents").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EDocumentsAction.Refresh)
    }

    @Test
    fun openDocumentAffordanceEmitsActionWithId() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentsAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(state = sampleState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithContentDescription("Open Bonafide Certificate").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions[0] is M3EDocumentsAction.OpenDocument)
        assertEquals("bonafide", (actions[0] as M3EDocumentsAction.OpenDocument).id)
    }

    @Test
    fun emptyStateIsShownWhenNoDocuments() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(state = M3EDocumentsState(), onAction = {})
            }
        }

        onNodeWithText("No documents available").assertExists()
    }

    @Test
    fun errorMessageIsShown() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(
                    state = M3EDocumentsState(errorMessage = "Could not load documents"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Could not load documents").assertExists()
    }
}
