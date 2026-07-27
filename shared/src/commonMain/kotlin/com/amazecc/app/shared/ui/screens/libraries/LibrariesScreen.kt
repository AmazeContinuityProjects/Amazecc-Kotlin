package com.amazecc.app.shared.ui.screens.libraries

import com.amazecc.app.shared.repository.SettingsManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.AmazeCard
import kotlinx.coroutines.launch
import kotlin.math.abs

private val bookColors = listOf(1, 2, 3, 4, 5)

@Composable
fun LibrariesScreen() {
    val colors = AmazeTheme.colors
    val libraryRes by AppState.library.collectAsState()
    val loginRequired by AppState.libraryLoginRequired.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()
    var activeTab by remember { mutableStateOf("Issued Books") }
    val tabs = listOf("Issued Books", "Catalog Search")
    var showLoginDialog by remember { mutableStateOf(false) }
    var hasCheckedCreds by remember { mutableStateOf(false) }

    LaunchedEffect(loginRequired) {
        if (loginRequired && !hasCheckedCreds) {
            showLoginDialog = true
            hasCheckedCreds = true
        }
    }
    LaunchedEffect(Unit) {
        val saved = com.amazecc.app.shared.repository.SettingsManager.getLibraryCredentials()
        if (saved != null) {
            hasCheckedCreds = true
        } else if (libraryRes == null) {
            showLoginDialog = true
        }
    }

    if (showLoginDialog) {
        LibraryLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLogin = { username, password ->
                AppState.saveLibraryCredentials(username, password)
                showLoginDialog = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = "Library",
            description = if (issuedBooks.isNotEmpty()) "${issuedBooks.size} book${if (issuedBooks.size != 1) "s" else ""} issued"
            else if (loginRequired) "Login required"
            else "Browse the catalog",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = { AppState.syncLibrary() }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                            .background(if (isSelected) colors.accent else colors.surface)
                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                            .clickable { activeTab = tab }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            tab,
                            color = if (isSelected) Color.White else colors.textSecondary,
                            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    "Issued Books" -> IssuedBooksContent(issuedBooks, loginRequired) { showLoginDialog = true }
                    "Catalog Search" -> CatalogSearchContent()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Library Login Dialog
// ═══════════════════════════════════════════

@Composable
private fun LibraryLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    val colors = AmazeTheme.colors
    val storedCreds = SettingsManager.getLibraryCredentials()
    var username by remember { mutableStateOf(storedCreds?.first ?: "") }
    var password by remember { mutableStateOf(storedCreds?.second ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.chart2.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null, tint = colors.chart2, modifier = Modifier.size(32.dp)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Library Login", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Enter your library credentials to access issued books and search the catalog.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Library ID") }, placeholder = { Text("Enter your library ID") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = colors.textMuted) },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, placeholder = { Text("Enter your password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = colors.textMuted) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = colors.textMuted) }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Your credentials are stored locally and only used to access your library account.", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, textAlign = TextAlign.Center))

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { isLoggingIn = true; onLogin(username, password) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, disabledContainerColor = colors.border),
                    enabled = username.isNotBlank() && password.isNotBlank() && !isLoggingIn
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Login", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Issued Books Content
// ═══════════════════════════════════════════

@Composable
private fun IssuedBooksContent(
    books: List<BookItem>,
    loginRequired: Boolean,
    onRequestLogin: () -> Unit
) {
    val colors = AmazeTheme.colors

    if (loginRequired) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Library Login Required", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sign in with your library credentials to view issued books.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center), modifier = Modifier.padding(horizontal = 40.dp))
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onRequestLogin, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.accent)) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign In", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    if (books.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Book, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No books issued", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                Text("You have no books currently checked out.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)
    ) {
        item {
            Text(
                "${books.size} book${if (books.size != 1) "s" else ""} currently issued",
                style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium)
            )
        }
        items(books, key = { it.bookId }) { book ->
            IssuedBookCard(book, colors)
        }
    }
}

@Composable
private fun IssuedBookCard(book: BookItem, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val isOverdue = book.dueDate != null && book.fineAmount != null && book.fineAmount != "Rs. 0.00"
    val dueColor = if (isOverdue) colors.chart5 else colors.textSecondary
    val index = book.bookId.hashCode().let { abs(it) % bookColors.size }
    val cardColor = when (bookColors[index]) { 1 -> colors.chart1; 2 -> colors.chart2; 3 -> colors.chart3; 4 -> colors.chart4; else -> colors.chart5 }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(cardColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Book, null, tint = cardColor, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 2)
                    if (book.author != null) {
                        Text(book.author, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isOverdue) colors.chart5.copy(alpha = 0.12f) else colors.successSurface).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(if (isOverdue) "Overdue" else "Issued", color = if (isOverdue) colors.chart5 else colors.successText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarToday, null, tint = dueColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Due: ${book.dueDate ?: "—"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = dueColor))
                }
                if (book.fineAmount != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(book.fineAmount, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = if (isOverdue) colors.chart5 else colors.textSecondary))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("ID: ${book.bookId}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            }
            
            var renewing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            
            Button(
                onClick = {
                    scope.launch {
                        renewing = true
                        val res = AmazeClient.renewLibraryBook(book.bookId)
                        AppState.syncLibrary()
                        renewing = false
                    }
                },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cardColor),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                if (renewing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Renew", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Catalog Search Content
// ═══════════════════════════════════════════

@Composable
private fun CatalogSearchContent() {
    val colors = AmazeTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Title, Author, or ISBN...", color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = ""; searchResults = emptyList(); hasSearched = false; errorMessage = null }) { Icon(Icons.Rounded.Clear, null, tint = colors.textMuted) }
                    }
                },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
            )
        }

        item {
            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        scope.launch {
                            isSearching = true; hasSearched = true; errorMessage = null
                            try {
                                val res = AmazeClient.searchLibrary(searchQuery)
                                searchResults = res.searchResults
                                if (res.error != null) errorMessage = res.error
                            } catch (e: Exception) { errorMessage = e.message ?: "Search failed" }
                            isSearching = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, disabledContainerColor = colors.border),
                enabled = searchQuery.isNotBlank() && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Catalog", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isSearching) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
            }
        } else if (errorMessage != null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(colors.chart5.copy(alpha = 0.06f)).border(1.dp, colors.chart5.copy(alpha = 0.2f), RoundedCornerShape(16.dp)).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Error, null, tint = colors.chart5, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(errorMessage ?: "An error occurred", color = colors.chart5, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium))
                    }
                }
            }
        } else if (hasSearched && searchResults.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No results found", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
                        Text("Try a different search term", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                }
            }
        } else if (!hasSearched) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Search the library catalog", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
                        Text("Find books by title, author, or ISBN", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                }
            }
        } else {
            item {
                Text("${searchResults.size} result${if (searchResults.size != 1) "s" else ""} found", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
            }
            items(searchResults, key = { it.bookId }) { book ->
                SearchResultCard(book, colors)
            }
        }
    }
}

@Composable
private fun SearchResultCard(book: BookItem, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val index = book.bookId.hashCode().let { abs(it) % bookColors.size }
    val cardColor = when (bookColors[index]) { 1 -> colors.chart1; 2 -> colors.chart2; 3 -> colors.chart3; 4 -> colors.chart4; else -> colors.chart5 }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(cardColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = cardColor, modifier = Modifier.size(24.dp)) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 2)
                if (book.author != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(book.author, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("ID: ${book.bookId}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            }
        }
    }
}
