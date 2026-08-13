package com.amazecc.app.shared.ui.screens.libraries

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.model.KohaBook
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupCard
import com.amazecc.app.shared.ui.screens.settings.SettingsRow
import com.amazecc.app.shared.ui.screens.settings.SettingsRowDivider
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.utils.toFixed
import kotlinx.coroutines.launch
import kotlin.math.abs

private val bookColors = listOf(1, 2, 3, 4, 5)

private enum class LibrarySubPage(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    MY_BOOKS("My Books", "Issued books, due dates & renewals", Icons.AutoMirrored.Rounded.MenuBook),
    CATALOG_SEARCH("Catalog Search", "Find books by title, author, or ISBN", Icons.Rounded.Search)
}

@Composable
fun LibrariesScreen() {
    val colors = AmazeTheme.colors
    val libraryRes by AppState.library.collectAsState()
    val loginRequired by AppState.libraryLoginRequired.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()

    var activeTab by remember { mutableStateOf(LibrarySubPage.MY_BOOKS) }

    // Search activation via header localSearchTick
    val localSearchTick by AppState.localSearchTick.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(localSearchTick) {
        if (localSearchTick > 0) {
            activeTab = LibrarySubPage.CATALOG_SEARCH
        }
    }

    AppBackHandler(enabled = activeTab != LibrarySubPage.MY_BOOKS) {
        activeTab = LibrarySubPage.MY_BOOKS
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (targetState == LibrarySubPage.MY_BOOKS) {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 3 } + fadeOut())
                        }
                    },
                    label = "librarySubNav"
                ) { sub ->
                    when (sub) {
                        LibrarySubPage.MY_BOOKS -> MyBooksOverview(
                            issuedBooks = issuedBooks,
                            loginRequired = loginRequired,
                            onOpenSearch = { activeTab = LibrarySubPage.CATALOG_SEARCH },
                            colors = colors
                        )
                        LibrarySubPage.CATALOG_SEARCH -> CatalogSearchContent(
                            onBack = { activeTab = LibrarySubPage.MY_BOOKS },
                            focusRequester = searchFocusRequester,
                            keyboardController = keyboardController,
                            autoFocus = localSearchTick > 0,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
//  Overview: Hero + SubMenu + Issued Books
// ═══════════════════════════════════════════

@Composable
private fun MyBooksOverview(
    issuedBooks: List<BookItem>,
    loginRequired: Boolean,
    onOpenSearch: () -> Unit,
    colors: AmazeColors
) {
    val hasCreds = remember(loginRequired) { SettingsManager.getLibraryCredentials() != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = BOTTOM_NAV_PADDING),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Card with summary stats
        LibraryHeroCard(issuedBooks = issuedBooks, loginRequired = loginRequired || !hasCreds, colors = colors)

        // 2. Sub-menu (SettingsGroupCard style)
        SettingsGroupCard {
            SettingsRow(
                icon = LibrarySubPage.MY_BOOKS.icon,
                title = LibrarySubPage.MY_BOOKS.title,
                subtitle = if (hasCreds && !loginRequired) "${issuedBooks.size} book${if (issuedBooks.size != 1) "s" else ""} currently issued" else "Check issued books & renewals",
                tint = colors.accent,
                onClick = { /* Already on overview */ }
            )
            SettingsRowDivider()
            SettingsRow(
                icon = LibrarySubPage.CATALOG_SEARCH.icon,
                title = LibrarySubPage.CATALOG_SEARCH.title,
                subtitle = LibrarySubPage.CATALOG_SEARCH.description,
                tint = colors.chart1,
                onClick = onOpenSearch
            )
        }

        // 3. Main Content: Login Card OR Issued Books List
        if (loginRequired || !hasCreds) {
            InlineLibraryLoginCard(onLoginSuccess = { AppState.syncLibrary() })
        } else if (issuedBooks.isEmpty()) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Book, null, tint = colors.textMuted, modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    Text("No books issued", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("You have no books currently checked out.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                    TextButton(onClick = { AppState.saveLibraryCredentials("", "") }) {
                        Text("Sign Out Library Account", color = colors.danger, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${issuedBooks.size} book${if (issuedBooks.size != 1) "s" else ""} currently issued",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium)
                )
                TextButton(onClick = { AppState.saveLibraryCredentials("", "") }) {
                    Text("Sign Out", color = colors.danger, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                }
            }

            issuedBooks.forEach { book ->
                IssuedBookCard(book = book, colors = colors)
            }
        }
    }
}

// ═══════════════════════════════════════════
//  CourseDetail-style Hero Card
// ═══════════════════════════════════════════

@Composable
private fun LibraryHeroCard(
    issuedBooks: List<BookItem>,
    loginRequired: Boolean,
    colors: AmazeColors
) {
    val heroGradient = remember(colors) {
        Brush.linearGradient(
            colors = listOf(colors.accent, colors.accent.copy(alpha = 0.65f))
        )
    }

    val overdueCount = remember(issuedBooks) {
        issuedBooks.count { it.dueDate != null && it.fineAmount != null && it.fineAmount != "Rs. 0.00" && it.fineAmount != "0" }
    }

    val totalFines = remember(issuedBooks) {
        var sum = 0.0
        issuedBooks.forEach { b ->
            b.fineAmount?.let { f ->
                val digits = f.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                sum += digits
            }
        }
        if (sum > 0) "Rs. ${sum.toFixed(2)}" else "Rs. 0.00"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(heroGradient)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Library Account",
                    color = Color.White.copy(alpha = 0.95f),
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (loginRequired) "Login Required" else if (overdueCount > 0) "$overdueCount Overdue" else "Active",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.micro
                    )
                }
            }

            if (!loginRequired) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat("Issued", "${issuedBooks.size}", Color.White)
                    HeroStat("Overdue", "$overdueCount", if (overdueCount > 0) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.9f))
                    HeroStat("Fines", totalFines, Color.White.copy(alpha = 0.9f))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(AmazeTheme.radius.medium))
                    .padding(12.dp)
            ) {
                if (loginRequired) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sign in below to check issued books, due dates & renewals",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    }
                } else if (issuedBooks.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "No books currently issued. Search catalog to explore books!",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (overdueCount > 0) "$overdueCount book(s) past due date — please renew or return"
                            else "All issued books are within due date",
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.lg, color = color)
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = Color.White.copy(alpha = 0.8f))
    }
}

// ═══════════════════════════════════════════
//  Inline Library Login Card
// ═══════════════════════════════════════════

@Composable
private fun InlineLibraryLoginCard(onLoginSuccess: () -> Unit) {
    val colors = AmazeTheme.colors
    val storedCreds = SettingsManager.getLibraryCredentials()
    var username by remember { mutableStateOf(storedCreds?.first ?: "") }
    var password by remember { mutableStateOf(storedCreds?.second ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
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

// ═══════════════════════════════════════════
//  Issued Book Card
// ═══════════════════════════════════════════

@Composable
private fun IssuedBookCard(book: BookItem, colors: AmazeColors) {
    val isOverdue = book.dueDate != null && book.fineAmount != null && book.fineAmount != "Rs. 0.00" && book.fineAmount != "0"
    val dueColor = if (isOverdue) colors.chart5 else colors.textSecondary
    val index = book.bookId.hashCode().let { abs(it) % bookColors.size }
    val cardColor = when (bookColors[index]) { 1 -> colors.chart1; 2 -> colors.chart2; 3 -> colors.chart3; 4 -> colors.chart4; else -> colors.chart5 }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(cardColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Book, null, tint = cardColor, modifier = Modifier.size(24.dp)) }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 2)
                        if (!book.author.isNullOrBlank()) {
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
                        Text("Due: ${book.dueDate ?: "—"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = dueColor))
                    }
                    if (book.fineAmount != null && book.fineAmount != "Rs. 0.00" && book.fineAmount != "0") {
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                        Text("Fine: ${book.fineAmount}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.chart5))
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
}

// ═══════════════════════════════════════════
//  Catalog Search Sub-Page
// ═══════════════════════════════════════════

@Composable
private fun CatalogSearchContent(
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    autoFocus: Boolean,
    colors: AmazeColors
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KohaBook>>(emptyList()) }
    var totalResults by remember { mutableStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top row with back button and heading
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    LibrarySubPage.CATALOG_SEARCH.title,
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    "Search books in Koha Library Catalog",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Title, Author, or ISBN...", color = colors.textMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = ""; searchResults = emptyList(); hasSearched = false; errorMessage = null }) {
                        Icon(Icons.Rounded.Clear, null, tint = colors.textMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        // Search Button
        Button(
            onClick = {
                if (searchQuery.isNotBlank()) {
                    scope.launch {
                        isSearching = true
                        hasSearched = true
                        errorMessage = null
                        try {
                            val res = AmazeClient.searchLibrary(searchQuery)
                            searchResults = res.books
                            totalResults = res.total
                            if (res.error != null) errorMessage = res.error
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Search failed"
                        }
                        isSearching = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, disabledContainerColor = colors.border),
            enabled = searchQuery.isNotBlank() && !isSearching
        ) {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                Text("Searching...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                Text("Search Catalog", fontWeight = FontWeight.Bold)
            }
        }

        // Results List / States
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
        ) {
            if (isSearching) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
            } else if (errorMessage != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                            .background(colors.chart5.copy(alpha = 0.08f))
                            .border(1.dp, colors.chart5.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.medium))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Error, null, tint = colors.chart5, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                            Text(errorMessage ?: "An error occurred", color = colors.chart5, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium))
                        }
                    }
                }
            } else if (hasSearched && searchResults.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                            Text("No results found", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold))
                            Text("Try searching by exact title, author name, or ISBN", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        }
                    }
                }
            } else if (!hasSearched) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                            Text("Search the Koha library catalog", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
                            Text("Find books freely available in the campus library", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "${searchResults.size} of $totalResults book${if (totalResults != 1) "s" else ""} found",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    )
                }
                items(searchResults, key = { it.biblionumber.ifBlank { it.title } }) { book ->
                    SearchResultCard(book = book, colors = colors)
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(book: KohaBook, colors: AmazeColors) {
    val index = book.biblionumber.hashCode().let { abs(it) % bookColors.size }
    val cardColor = when (bookColors[index]) { 1 -> colors.chart1; 2 -> colors.chart2; 3 -> colors.chart3; 4 -> colors.chart4; else -> colors.chart5 }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(cardColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = cardColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        book.title,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            book.author,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (book.publisher.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            book.publisher,
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (book.isbn.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "ISBN: ${book.isbn}",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Medium, fontSize = AmazeTheme.fontSize.micro)
                        )
                    }
                }
            }
        }
    }
}
