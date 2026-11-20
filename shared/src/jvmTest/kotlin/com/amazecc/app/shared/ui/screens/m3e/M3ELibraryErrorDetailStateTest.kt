package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.amazecc.app.shared.domain.overhaul.M3EKohaBookDetail
import com.amazecc.app.shared.domain.overhaul.M3EKohaHolding
import com.amazecc.app.shared.model.BasicRes
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Evidence-backed JVM checks: a failed catalog search is an error, not an empty result set. */
@OptIn(ExperimentalTestApi::class)
class M3ELibraryErrorDetailStateTest {

    private fun catalogState(error: String? = null) = M3ELibrariesState(
        activeTab = M3ELibraryTab.CATALOG,
        searchQuery = "kotlin",
        hasSearched = true,
        errorMessage = error
    )

    @Test
    fun catalogErrorRendersErrorMessageAndRetryNotEmptyState() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = catalogState(error = "404 - Catalog unavailable"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("404 - Catalog unavailable").assertExists()
        onNodeWithText("Retry Search").assertExists()
        onNodeWithText("No catalog matches for \"kotlin\"").assertDoesNotExist()
        // Search field / button survive the error and stay operable.
        onNodeWithText("Search Catalog").assertExists()
    }

    @Test
    fun successfulZeroResultSearchShowsOnlyEmptyState() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(state = catalogState(), onAction = {})
            }
        }

        onNodeWithText("No catalog matches for \"kotlin\"").assertExists()
        onNodeWithText("Retry Search").assertDoesNotExist()
        onNodeWithText("Search Catalog").assertExists()
    }

    @Test
    fun detailSheetRendersRealKohaFieldsWhenPresent() = runComposeUiTest {
        val detail = M3EKohaBookDetail(
            biblionumber = "4421",
            title = "Kotlin in Action",
            author = "Dmitry Jemerov",
            publisher = "Manning",
            isbn = "9781617293290",
            holdings = listOf(
                M3EKohaHolding(
                    barcode = "BK-4421",
                    location = "Stacks",
                    callNumber = "005.13 K",
                    status = "Available"
                )
            )
        )
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(state = catalogState().copy(bookDetail = detail), onAction = {})
            }
        }

        onNodeWithText("Kotlin in Action").assertExists()
        onNodeWithText("by Dmitry Jemerov · Manning").assertExists()
        onNodeWithText("ISBN: 9781617293290").assertExists()
        onNodeWithText("BK-4421 · Stacks · 005.13 K").assertExists()
        onNodeWithText("Available").assertExists()
        onNodeWithText("Close").assertExists()
    }

    @Test
    fun detailSheetAbsentWhenNoDetailWasLoaded() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(state = catalogState(), onAction = {})
            }
        }

        onNodeWithText("Book details").assertDoesNotExist()
        onNodeWithText("Close").assertDoesNotExist()
    }

    @Test
    fun detailErrorIsShownInsteadOfFabricatedDetail() = runComposeUiTest {
        setContent {
            AmazeM3ETheme {
                M3ELibrariesScreen(
                    state = catalogState().copy(detailErrorMessage = "Detail request failed (404)"),
                    onAction = {}
                )
            }
        }

        onNodeWithText("Detail request failed (404)").assertExists()
        onNodeWithText("Loading book details...").assertDoesNotExist()
    }

    @Test
    fun renewOutcomeChecksSuccessFlagFromServer() {
        val success = renewLibraryOutcome(BasicRes(success = true, message = "Book renewed successfully"))
        assertEquals("Book renewed successfully", success.first)
        assertNull(success.second)

        val notFound = renewLibraryOutcome(BasicRes(success = false, message = "Loan not found (404)"))
        assertNull(notFound.first)
        assertEquals("Loan not found (404)", notFound.second)

        val noMessage = renewLibraryOutcome(BasicRes(success = false, message = null))
        assertNull(noMessage.first)
        assertEquals("Renewal failed", noMessage.second)
    }
}
