package com.amazecc.app.shared.ui.screens.libraries

import androidx.compose.foundation.layout.*
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val scope = rememberCoroutineScope()
    val libraryRes by AppState.library.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()
    
    var searchQuery by remember { mutableStateOf("") }
    
    val searchIndexes = listOf("kw" to "Keyword", "ti" to "Title", "au" to "Author", "nb" to "ISBN")
    var selectedIndex by remember { mutableStateOf(searchIndexes[0].first) }
    
    var searchResults by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var totalResults by remember { mutableStateOf(0) }
    var currentOffset by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "Library",
            description = "Search catalog and view issued books",
            showBackButton = false,
            showSyncButton = true
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(2f)) {
                AmazeTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search Catalog",
                    placeholder = "Query..."
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AmazeDropdown(
                    label = "Index",
                    selectedOption = searchIndexes.find { it.first == selectedIndex }?.second ?: "",
                    options = searchIndexes.map { it.second },
                    onOptionSelected = { sel -> selectedIndex = searchIndexes.find { it.second == sel }?.first ?: "kw" }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AmazeButton(
            text = if (isSearching) "Searching..." else "Search",
            onClick = {
                if (searchQuery.isNotBlank()) {
                    scope.launch {
                        isSearching = true
                        currentOffset = 0
                        try {
                            val res = AmazeClient.searchLibrary(searchQuery, selectedIndex, currentOffset)
                            searchResults = res.searchResults
                            totalResults = res.total
                        } catch (e: Exception) {
                            searchResults = emptyList()
                            totalResults = 0
                        } finally {
                            isSearching = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (searchResults.isNotEmpty()) {
            Text("Search Results (${searchResults.size}/$totalResults)", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(searchResults) { book ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(book.author ?: "Unknown Author", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
                
                if (searchResults.size < totalResults) {
                    item {
                        AmazeButton(
                            text = if (isLoadingMore) "Loading..." else "Load More",
                            onClick = {
                                scope.launch {
                                    isLoadingMore = true
                                    currentOffset += 20
                                    try {
                                        val res = AmazeClient.searchLibrary(searchQuery, selectedIndex, currentOffset)
                                        searchResults = searchResults + res.searchResults
                                    } catch (e: Exception) {
                                        // Ignore
                                    } finally {
                                        isLoadingMore = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    }
                }
            }
        } else {
            Text("Active Checked Out Books", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(10.dp))
            if (issuedBooks.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No active book issues.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(issuedBooks) { book ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("Issued: ${book.issueDate ?: "—"} | Due: ${book.dueDate ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fine: ${book.fineAmount ?: "Rs. 0.00"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.danger))
                                    AmazeBadge(text = "Issued", variant = BadgeVariant.SUCCESS)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
