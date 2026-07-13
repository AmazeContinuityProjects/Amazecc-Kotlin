package com.amazecc.app.shared.ui.screens.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun LibrariesScreen() {
    val colors = AmazeTheme.colors
    val libraryRes by AppState.library.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()
    
    var activeSubTab by remember { mutableStateOf("Issued Books") }
    val tabs = listOf("Issued Books", "Catalog Search")

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Library",
            description = "Manage your books and search the catalog",
            showBackButton = false,
            showSyncButton = true
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeSubTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accent else colors.surface)
                            .clickable { activeSubTab = tab }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold).copy(
                                color = if (isSelected) colors.background else colors.textSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                when (activeSubTab) {
                    "Issued Books" -> IssuedBooksTab(issuedBooks)
                    "Catalog Search" -> CatalogSearchTab()
                }
            }
        }
    }
}

@Composable
fun IssuedBooksTab(issuedBooks: List<BookItem>) {
    val colors = AmazeTheme.colors
    
    if (issuedBooks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Book, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No books currently issued.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            items(issuedBooks) { book ->
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(book.author ?: "Unknown Author", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            AmazeBadge("ISSUED", variant = BadgeVariant.INFO)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Due: ${book.dueDate}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.dangerText))
                            }
                            Text("ID: ${book.bookId}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogSearchTab() {
    val colors = AmazeTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var searchResults by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        AmazeTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Search Catalog",
            placeholder = "Title, Author, or ISBN..."
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        AmazeButton(
            "Search", 
            onClick = {
                if (searchQuery.isNotBlank()) {
                    coroutineScope.launch {
                        isSearching = true
                        hasSearched = true
                        val res = AmazeClient.searchLibrary(searchQuery)
                        searchResults = res.searchResults ?: emptyList()
                        isSearching = false
                    }
                }
            }, 
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (hasSearched) {
            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No results found.", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { book ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(book.author ?: "Unknown Author", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                if (book.bookId != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("ID: ${book.bookId}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Enter a search term to find books in Koha.", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            }
        }
    }
}