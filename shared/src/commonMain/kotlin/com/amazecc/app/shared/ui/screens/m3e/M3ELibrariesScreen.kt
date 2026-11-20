package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.overhaul.M3EKohaBookDetail
import com.amazecc.app.shared.domain.overhaul.M3EKohaHolding
import com.amazecc.app.shared.model.KohaBook
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A library book, mapped from either an issued [BookItem] or a Koha catalog hit. */
@Immutable
data class M3ELibraryBook(
    val id: String,
    val title: String,
    val author: String = "",
    val dueDate: String? = null,
    val fineAmount: String? = null,
    val publisher: String? = null,
    val isbn: String? = null
)

/** Immutable, screen-owned state for [M3ELibrariesScreen]. */
@Immutable
data class M3ELibrariesState(
    val activeTab: M3ELibraryTab = M3ELibraryTab.MY_BOOKS,
    val issuedBooks: List<M3ELibraryBook> = emptyList(),
    val searchResults: List<M3ELibraryBook> = emptyList(),
    val totalResults: Int = 0,
    val searchQuery: String = "",
    val hasSearched: Boolean = false,
    val loginRequired: Boolean = false,
    val hasCredentials: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    // Additive Koha detail / renewal status fields (all default to "absent").
    val bookDetail: M3EKohaBookDetail? = null,
    val isDetailLoading: Boolean = false,
    val detailErrorMessage: String? = null,
    val renewNotice: String? = null
)

/** The two library surfaces: the loan account and the Koha catalog. */
enum class M3ELibraryTab(val label: String) {
    MY_BOOKS("My Books"),
    CATALOG("Catalog Search")
}

/** User intents emitted by [M3ELibrariesScreen]. */
sealed interface M3ELibrariesAction {
    data class SelectTab(val tab: M3ELibraryTab) : M3ELibrariesAction
    data class UpdateSearch(val query: String) : M3ELibrariesAction
    data object SearchCatalog : M3ELibrariesAction
    data object ClearSearch : M3ELibrariesAction
    data class UpdateUsername(val value: String) : M3ELibrariesAction
    data class UpdatePassword(val value: String) : M3ELibrariesAction
    data object SubmitLogin : M3ELibrariesAction
    data object ClearCredentials : M3ELibrariesAction
    data class RenewBook(val bookId: String) : M3ELibrariesAction
    data class OpenBookDetail(val biblionumber: String) : M3ELibrariesAction
    data object DismissBookDetail : M3ELibrariesAction
    data object Refresh : M3ELibrariesAction
}

/**
 * Pure success/failure mapping for a renewal response. The host must consult `success` (a 404
 * can carry a message but `success = false`); keeping the mapping here makes that branch testable
 * on the headless JVM without calling AmazeClient.
 *
 * Returns `Pair<renewNotice, errorMessage>` — exactly one side is non-null.
 */
internal fun renewLibraryOutcome(result: com.amazecc.app.shared.model.BasicRes): Pair<String?, String?> =
    if (result.success) {
        (result.message?.takeIf { it.isNotBlank() } ?: "Book renewed successfully") to null
    } else {
        null to (result.message?.takeIf { it.isNotBlank() } ?: "Renewal failed")
    }

/** Same field mapping the host uses for page 0, so later pages render identically (D-063). */
private fun KohaBook.toM3ELibraryBook() = M3ELibraryBook(
    id = biblionumber,
    title = title,
    author = author,
    publisher = publisher.takeIf { it.isNotBlank() },
    isbn = isbn.takeIf { it.isNotBlank() }
)

/**
 * Material 3 Expressive Library screen.
 * 100% stateless: receives [M3ELibrariesState] and emits [M3ELibrariesAction].
 * Issued books come from AppState.library (online scraper / offline cache);
 * catalog search and renewals go through AmazeClient, both owned by the host bridge.
 */
@Composable
fun M3ELibrariesScreen(
    state: M3ELibrariesState,
    onAction: (M3ELibrariesAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val showBookDetail = state.bookDetail != null || state.isDetailLoading || state.detailErrorMessage != null

    // D-063: the host owns page 0 (state.searchResults). Deeper pages hit the same real Koha
    // endpoint with its offset — this shared action/state model has no page intent, so paging is
    // screen-local rather than a fake slice of the first 20 rows. Detail stays host-driven.
    val pageScope = rememberCoroutineScope()
    var currentPage by remember { mutableStateOf(0) }
    var pagedResults by remember { mutableStateOf(state.searchResults) }
    var isPaging by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var selectedResultIndex by remember { mutableStateOf(-1) }
    val catalogPageSize = 20 // matches AmazeClient.searchLibrary's `count=20`
    val totalPages = if (state.totalResults <= 0) 1 else
        (state.totalResults + catalogPageSize - 1) / catalogPageSize
    val resultFocusRequesters = remember(pagedResults.size) { List(pagedResults.size) { FocusRequester() } }

    LaunchedEffect(state.searchQuery, state.hasSearched, state.searchResults) {
        currentPage = 0
        pagedResults = state.searchResults
        pageError = null
        selectedResultIndex = -1
    }

    fun requestCatalogPage(page: Int) {
        if (page !in 0 until totalPages || isPaging) return
        if (page == 0) {
            currentPage = 0
            pagedResults = state.searchResults
            selectedResultIndex = -1
            pageError = null
            return
        }
        isPaging = true
        pageError = null
        pageScope.launch {
            try {
                val res = withContext(Dispatchers.Default) {
                    AmazeClient.searchLibrary(state.searchQuery, "kw", page * catalogPageSize)
                }
                if (res.success) {
                    pagedResults = res.books.map { it.toM3ELibraryBook() }
                    currentPage = page
                    selectedResultIndex = -1
                } else {
                    pageError = res.error?.takeIf { it.isNotBlank() }
                        ?: res.message?.takeIf { it.isNotBlank() }
                        ?: "Could not load page ${page + 1}"
                }
            } catch (e: Exception) {
                pageError = e.message ?: "Could not load page ${page + 1}"
            } finally {
                isPaging = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.surface)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("library_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Loans, dues & the Koha catalog",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onAction(M3ELibrariesAction.Refresh) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh library",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item(key = "tabs") {
            M3EConnectedButtonGroup(
                items = M3ELibraryTab.entries,
                selectedItem = state.activeTab,
                onItemSelected = { onAction(M3ELibrariesAction.SelectTab(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }

        when (state.activeTab) {
            M3ELibraryTab.MY_BOOKS -> {
                item(key = "library_hero") {
                    LibraryHeroCard(
                        issuedCount = state.issuedBooks.size,
                        needsLogin = state.loginRequired || !state.hasCredentials
                    )
                }

                if (state.loginRequired || !state.hasCredentials) {
                    item(key = "library_login") {
                        LibraryLoginCard(state = state, onAction = onAction)
                    }
                } else {
                    // Renewal outcome / sync error status. Only real host results populate these.
                    if (state.errorMessage != null || state.renewNotice != null) {
                        item(key = "library_status") {
                            M3ESurfaceCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerLevel = SurfaceContainerLevel.LOW,
                                borderColor = if (state.errorMessage != null) colors.error else colors.success
                            ) {
                                if (state.errorMessage != null) {
                                    Text(
                                        text = state.errorMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.error
                                    )
                                }
                                if (state.renewNotice != null) {
                                    Text(
                                        text = state.renewNotice,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.success
                                    )
                                }
                            }
                        }
                    }
                }

                if (!state.loginRequired && state.hasCredentials && state.issuedBooks.isEmpty() &&
                    state.errorMessage == null) {
                    item(key = "no_books") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No books issued",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "You have no books currently checked out.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!state.loginRequired && state.hasCredentials) {
                    items(state.issuedBooks, key = { "issued_${it.id}" }) { book ->
                        IssuedBookCard(
                            book = book,
                            onRenew = { onAction(M3ELibrariesAction.RenewBook(book.id)) }
                        )
                    }
                }
            }

            M3ELibraryTab.CATALOG -> {
                // The search field/button are emitted first and are never replaced by the error.
                item(key = "catalog_search") {
                    CatalogSearchCard(state = state, onAction = onAction)
                }

                if (state.errorMessage != null) {
                    item(key = "catalog_error") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW,
                            borderColor = colors.error
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.error
                                )
                                if (state.searchQuery.isNotBlank() && !state.isBusy) {
                                    ElevatedButton(
                                        onClick = { onAction(M3ELibrariesAction.SearchCatalog) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = colors.error,
                                            contentColor = colors.onError
                                        )
                                    ) {
                                        Text(
                                            text = "Retry Search",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isBusy) {
                    item(key = "catalog_progress") {
                        M3EWavyLinearProgress(progress = 0.5f)
                    }
                }

                // "No matches" means the request succeeded with zero rows — never an error.
                if (state.hasSearched && state.searchResults.isEmpty() && !state.isBusy &&
                    state.errorMessage == null) {
                    item(key = "catalog_empty") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Text(
                                text = "No catalog matches for \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = "catalog_result_meta") {
                    if (pagedResults.isNotEmpty() && state.totalResults > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.totalResults > pagedResults.size)
                                    "${pagedResults.size} of ${state.totalResults} results"
                                else "${pagedResults.size} result${if (pagedResults.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "↑↓ navigate · Enter open",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                if (pageError != null) {
                    item(key = "catalog_page_error") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW,
                            borderColor = colors.error
                        ) {
                            Text(
                                text = pageError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error
                            )
                        }
                    }
                }

                itemsIndexed(pagedResults, key = { _, book -> "koha_${currentPage}_${book.id}" }) { index, book ->
                    val requester = resultFocusRequesters.getOrNull(index)
                    CatalogBookCard(
                        book = book,
                        onOpen = { onAction(M3ELibrariesAction.OpenBookDetail(book.id)) },
                        modifier = Modifier
                            .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                            .onFocusChanged { if (it.isFocused) selectedResultIndex = index }
                            .onKeyEvent { event ->
                                if (showBookDetail || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        val next = (index + 1).coerceAtMost(pagedResults.lastIndex)
                                        selectedResultIndex = next
                                        resultFocusRequesters.getOrNull(next)?.requestFocus()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        val previous = (index - 1).coerceAtLeast(0)
                                        selectedResultIndex = previous
                                        resultFocusRequesters.getOrNull(previous)?.requestFocus()
                                        true
                                    }
                                    Key.Enter -> {
                                        onAction(M3ELibrariesAction.OpenBookDetail(book.id))
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .then(
                                if (selectedResultIndex == index) {
                                    Modifier.border(2.dp, colors.primary, RoundedCornerShape(16.dp))
                                } else Modifier
                            )
                    )
                }

                item(key = "catalog_pager") {
                    if (totalPages > 1) {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { requestCatalogPage(currentPage - 1) },
                                    enabled = currentPage > 0 && !isPaging
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronLeft,
                                        contentDescription = "Previous page",
                                        tint = colors.primary
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                if (isPaging) {
                                    com.amazecc.app.shared.ui.m3e.components.M3EInlineSkeleton(
                                        width = 36.dp,
                                        height = 14.dp,
                                        label = "Loading next page"
                                    )
                                } else {
                                    Text(
                                        text = "Page ${currentPage + 1} of $totalPages",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.onSurface
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                IconButton(
                                    onClick = { requestCatalogPage(currentPage + 1) },
                                    enabled = currentPage < totalPages - 1 && !isPaging
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Next page",
                                        tint = colors.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
        }
    }

    if (showBookDetail) {
        BookDetailDialog(
            detail = state.bookDetail,
            isLoading = state.isDetailLoading,
            errorMessage = state.detailErrorMessage,
            onDismiss = { onAction(M3ELibrariesAction.DismissBookDetail) }
        )
    }
}

@Composable
private fun LibraryHeroCard(
    issuedCount: Int,
    needsLogin: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "My Library",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Text(
                        text = if (needsLogin) "Sign in to see your loans" else "Account connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$issuedCount book${if (issuedCount == 1) "" else "s"} currently issued",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun LibraryLoginCard(
    state: M3ELibrariesState,
    onAction: (M3ELibrariesAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Library Account",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = { onAction(M3ELibrariesAction.UpdateUsername(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Library username") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = libraryFieldColors(colors)
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { onAction(M3ELibrariesAction.UpdatePassword(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password / PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                colors = libraryFieldColors(colors)
            )
            state.errorMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
            ElevatedButton(
                onClick = { onAction(M3ELibrariesAction.SubmitLogin) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                enabled = !state.isBusy && state.username.isNotBlank() && state.password.isNotBlank(),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text(
                    text = if (state.isBusy) "Signing in..." else "Sign In & Sync",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun libraryFieldColors(
    colors: com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
): androidx.compose.material3.TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.onSurface,
    unfocusedTextColor = colors.onSurface,
    focusedContainerColor = colors.surfaceContainerHigh,
    unfocusedContainerColor = colors.surfaceContainerLow,
    focusedBorderColor = colors.primary,
    unfocusedBorderColor = colors.outlineVariant,
    focusedLabelColor = colors.primary,
    unfocusedLabelColor = colors.onSurfaceVariant
)

@Composable
private fun IssuedBookCard(
    book: M3ELibraryBook,
    onRenew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "by ${book.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Due: ${book.dueDate ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    if (!book.fineAmount.isNullOrBlank()) {
                        Text(
                            text = "Fine: ${book.fineAmount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.error
                        )
                    }
                }
                ElevatedButton(
                    onClick = onRenew,
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = "Renew",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogSearchCard(
    state: M3ELibrariesState,
    onAction: (M3ELibrariesAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Search the catalog",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onAction(M3ELibrariesAction.UpdateSearch(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Title, author or ISBN...", color = colors.onSurfaceVariant) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.primary)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onAction(M3ELibrariesAction.ClearSearch) }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search", tint = colors.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = libraryFieldColors(colors)
            )
            ElevatedButton(
                onClick = { onAction(M3ELibrariesAction.SearchCatalog) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                enabled = state.searchQuery.isNotBlank() && !state.isBusy,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Search Catalog",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (state.hasSearched && state.totalResults > 0) {
                Text(
                    text = "${state.totalResults} results",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CatalogBookCard(
    book: M3ELibraryBook,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onOpen
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "by ${book.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            val secondary = listOfNotNull(book.publisher, book.isbn).joinToString(" · ")
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Detail sheet for a catalog hit. Every value comes from the real Koha detail response. */
@Composable
private fun BookDetailDialog(
    detail: M3EKohaBookDetail?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = detail?.title ?: "Book details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Text(
                        text = "Loading book details...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error
                    )
                }
                if (detail != null) {
                    val byline = listOfNotNull(
                        detail.author.takeIf { it.isNotBlank() }?.let { "by $it" },
                        detail.publisher.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (byline.isNotBlank()) {
                        Text(text = byline, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    val meta = listOfNotNull(
                        detail.edition.takeIf { it.isNotBlank() }?.let { "Edition: $it" },
                        detail.isbn.takeIf { it.isNotBlank() }?.let { "ISBN: $it" },
                        detail.ddc.takeIf { it.isNotBlank() }?.let { "DDC: $it" }
                    )
                    meta.forEach {
                        Text(text = it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    val description = detail.description.takeIf { it.isNotBlank() }
                        ?: detail.summary.takeIf { it.isNotBlank() }
                    if (description != null) {
                        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    }
                    if (detail.subjects.isNotEmpty()) {
                        Text(
                            text = "Subjects: ${detail.subjects.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    detail.holdings.forEach { holding -> HoldingRow(holding = holding) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = colors.primary) }
        }
    )
}

@Composable
private fun HoldingRow(holding: M3EKohaHolding, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    val label = listOfNotNull(
        holding.barcode.takeIf { it.isNotBlank() },
        holding.location.takeIf { it.isNotBlank() },
        holding.callNumber.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        }
        val status = listOfNotNull(
            holding.status.takeIf { it.isNotBlank() },
            holding.dueDate?.takeIf { it.isNotBlank() }?.let { "Due: $it" },
            holding.notes.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (status.isNotBlank()) {
            Text(text = status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewLibrariesState() = M3ELibrariesState(
    issuedBooks = listOf(
        M3ELibraryBook(
            id = "bk-1",
            title = "Clean Code: A Handbook of Agile Software Craftsmanship",
            author = "Robert C. Martin",
            dueDate = "28 Nov 2026",
            fineAmount = "₹ 0.00"
        )
    ),
    hasCredentials = true
)

@Preview
@Composable
private fun M3ELibrariesScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ELibrariesScreen(state = previewLibrariesState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ELibrariesCatalogPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ELibrariesScreen(
            state = M3ELibrariesState(
                activeTab = M3ELibraryTab.CATALOG,
                searchQuery = "kotlin",
                hasSearched = true,
                totalResults = 1,
                searchResults = listOf(
                    M3ELibraryBook(
                        id = "4421",
                        title = "Kotlin in Action",
                        author = "Dmitry Jemerov",
                        publisher = "Manning",
                        isbn = "9781617293290"
                    )
                )
            ),
            onAction = {}
        )
    }
}
