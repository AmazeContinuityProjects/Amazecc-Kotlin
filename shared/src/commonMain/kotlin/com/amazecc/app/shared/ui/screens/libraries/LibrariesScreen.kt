package com.amazecc.app.shared.ui.screens.libraries

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
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.coroutines.launch
import kotlin.math.abs

private val bookColors = listOf(1, 2, 3, 4, 5)

@Composable
fun LibrariesScreen() {
    val colors = AmazeTheme.colors
    val libraryRes by AppState.library.collectAsState()
    val loginRequired by AppState.libraryLoginRequired.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()
    var activeTab by remember { mutableStateOf("My Books") }
    val tabs = listOf("My Books")

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()

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
                IssuedBooksContent(issuedBooks, loginRequired)
            }
        }
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  Inline Library Login Card
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
private fun InlineLibraryLoginCard(onLoginSuccess: () -> Unit) {
    val colors = AmazeTheme.colors
    val storedCreds = SettingsManager.getLibraryCredentials()
    var username by remember { mutableStateOf(storedCreds?.first ?: "") }
    var password by remember { mutableStateOf(storedCreds?.second ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null, tint = colors.accent, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                Text("Library Sign In", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sign in to check your issued books, due dates, and renewals. Library catalog search is freely available anytime!",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center)
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Library ID / Reg No") },
                    placeholder = { Text("Enter your Library ID") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = colors.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(AmazeTheme.radius.small),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = colors.textMuted) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = colors.textMuted)
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(AmazeTheme.radius.small),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
                )

                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

                Button(
                    onClick = {
                        isLoggingIn = true
                        AppState.saveLibraryCredentials(username, password)
                        AppState.syncLibrary()
                        onLoginSuccess()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(AmazeTheme.radius.small),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, disabledContainerColor = colors.border),
                    enabled = username.isNotBlank() && password.isNotBlank() && !isLoggingIn
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Login, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.signIn, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your credentials are saved locally to fetch your library account.",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro, textAlign = TextAlign.Center)
                )
            }
        }
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
//  Issued Books Content
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
private fun IssuedBooksContent(
    books: List<BookItem>,
    loginRequired: Boolean
) {
    val colors = AmazeTheme.colors
    val hasCreds = remember(loginRequired) { SettingsManager.getLibraryCredentials() != null }

    if (loginRequired || !hasCreds) {
        InlineLibraryLoginCard(onLoginSuccess = { AppState.syncLibrary() })
        return
    }

    if (books.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Book, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                Text("No books issued", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                Text("You have no books currently checked out.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                TextButton(onClick = { AppState.saveLibraryCredentials("", "") }) {
                    Text("Sign Out Library Account", color = colors.danger, fontSize = AmazeTheme.fontSize.sm)
                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${books.size} book${if (books.size != 1) "s" else ""} currently issued",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium)
                )
                TextButton(onClick = { AppState.saveLibraryCredentials("", "") }) {
                    Text("Sign Out", color = colors.danger, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                }
            }
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
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(cardColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Book, null, tint = cardColor, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 2)
                    if (book.author != null) {
                        Text(book.author, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(if (isOverdue) colors.chart5.copy(alpha = 0.12f) else colors.successSurface).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(if (isOverdue) "Overdue" else "Issued", color = if (isOverdue) colors.chart5 else colors.successText, fontSize = AmazeTheme.fontSize.xs, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarToday, null, tint = dueColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                    Text("Due: ${book.dueDate ?: "â€”"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = dueColor))
                }
                if (book.fineAmount != null) {
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                    Text(book.fineAmount, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = if (isOverdue) colors.chart5 else colors.textSecondary))
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Text("ID: ${book.bookId}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            }

            var renewing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            Button(
                onClick = {
                    scope.launch {
                        renewing = true
                        AmazeClient.renewLibraryBook(book.bookId)
                        AppState.syncLibrary()
                        renewing = false
                    }
                },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(AmazeTheme.radius.xs),
                colors = ButtonDefaults.buttonColors(containerColor = cardColor),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                if (renewing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Renew", fontSize = AmazeTheme.fontSize.base, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

