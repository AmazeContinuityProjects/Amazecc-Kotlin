package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EKohaBookDetail
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Headless JVM checks for the library intents behind renew, retry and detail navigation. */
@OptIn(ExperimentalTestApi::class)
class M3ELibraryErrorDetailActionsTest {

    private val issued = M3ELibraryBook(id = "loan-9", title = "Clean Code", dueDate = "28 Nov 2026")

    private fun loansState() = M3ELibrariesState(
        activeTab = M3ELibraryTab.MY_BOOKS,
        hasCredentials = true,
        issuedBooks = listOf(issued)
    )

    @Test
    fun renewButtonEmitsBookId() = runComposeUiTest {
        val actions = mutableListOf<M3ELibrariesAction>()
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(state = loansState(), onAction = { actions.add(it) })
            }
        }

        onNodeWithText("Renew").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ELibrariesAction.RenewBook)
        assertEquals("loan-9", (actions.first() as M3ELibrariesAction.RenewBook).bookId)
    }

    @Test
    fun failedRenewNoticeIsRenderedAsErrorNotSuccess() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = loansState().copy(errorMessage = "Loan not found (404)"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Loan not found (404)").assertExists()
        onNodeWithText("No books issued").assertDoesNotExist()
    }

    @Test
    fun successfulRenewNoticeIsRendered() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = loansState().copy(renewNotice = "Book renewed successfully"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Book renewed successfully").assertExists()
    }

    @Test
    fun retrySearchEmitsSearchCatalog() = runComposeUiTest {
        val actions = mutableListOf<M3ELibrariesAction>()
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = M3ELibrariesState(
                        activeTab = M3ELibraryTab.CATALOG,
                        searchQuery = "kotlin",
                        hasSearched = true,
                        errorMessage = "HTTP 404"
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Retry Search").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ELibrariesAction.SearchCatalog)
    }

    @Test
    fun catalogBookClickEmitsOpenBookDetail() = runComposeUiTest {
        val actions = mutableListOf<M3ELibrariesAction>()
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = M3ELibrariesState(
                        activeTab = M3ELibraryTab.CATALOG,
                        searchResults = listOf(
                            M3ELibraryBook(id = "4421", title = "Kotlin in Action", author = "Dmitry Jemerov")
                        )
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Kotlin in Action").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ELibrariesAction.OpenBookDetail)
        assertEquals("4421", (actions.first() as M3ELibrariesAction.OpenBookDetail).biblionumber)
    }

    @Test
    fun closingDetailSheetEmitsDismiss() = runComposeUiTest {
        val actions = mutableListOf<M3ELibrariesAction>()
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = M3ELibrariesState(
                        activeTab = M3ELibraryTab.CATALOG,
                        bookDetail = M3EKohaBookDetail(biblionumber = "4421", title = "Kotlin in Action")
                    ),
                    onAction = { actions.add(it) }
                )
            }
        }

        onNodeWithText("Close").performClick()
        assertEquals(1, actions.size)
        assertTrue(actions.first() is M3ELibrariesAction.DismissBookDetail)
    }
}
