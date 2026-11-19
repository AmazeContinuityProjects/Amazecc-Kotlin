package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless JVM tests for the OpenDocument affordance that now triggers a real download. */
@OptIn(ExperimentalTestApi::class)
class M3EActionsDocumentsTest {

    @Test
    fun openDocumentEmitsSourceId() = runComposeUiTest {
        val actions = mutableListOf<M3EDocumentsAction>()
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(
                    state = M3EDocumentsState(
                        documents = listOf(M3EDocumentCard(id = "circular-42", title = "Bonafide"))
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithContentDescription("Open Bonafide").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3EDocumentsAction.OpenDocument)
        assertEquals("circular-42", (actions.first() as M3EDocumentsAction.OpenDocument).id)
    }

    @Test
    fun noOpenAffordanceWhenIdIsBlank() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3EDocumentsScreen(
                    state = M3EDocumentsState(
                        documents = listOf(M3EDocumentCard(id = "", title = "Untitled"))
                    ),
                    onAction = {}
                )
            }
        }

        onNodeWithContentDescription("Open Untitled").assertDoesNotExist()
    }
}
