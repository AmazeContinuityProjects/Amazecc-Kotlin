package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch


@Composable
fun TimetableScreen() = AcademicsScreen(initialTab = "Schedule")

@Composable
fun PaymentsScreen() = ServicesScreen(initialTab = "Payments")

@Composable
fun LibraryScreen() = ServicesScreen(initialTab = "Library")

@Composable
fun TransportScreen() = ServicesScreen(initialTab = "Transport")

@Composable
fun LMSScreen() = ServicesScreen(initialTab = "LMS")

// Unified top-level header with back navigation and a concurrent Sync/Refresh action

@Composable
fun ServicesScreen(initialTab: String = "Payments") {
    val colors = AmazeTheme.colors
    var activeSubTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Payments", "Library", "Transport", "LMS")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Campus Services",
            description = "Dues, Koha, buses & LMS tasks",
            showBackButton = false,
            showSyncButton = true
        )

        // Sub-Tab Navigation row
        TabRow(
            selectedTabIndex = tabs.indexOf(activeSubTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeSubTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            when (activeSubTab) {
                "Payments" -> PaymentsSubScreen()
                "Library" -> LibrarySubScreen()
                "Transport" -> TransportSubScreen()
                "LMS" -> LMSSubScreen()
            }
        }
    }
}

// ── SUB-SCREEN IMPLEMENTATIONS ──


@Composable
fun PaymentsSubScreen() {
    val colors = AmazeTheme.colors
    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        MetricCard(
            title = "VTOP WALLET BALANCE",
            value = paymentsRes?.walletBalance ?: "—",
            caption = "Available when wallet data is returned by the API",
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Transactions & Dues", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))

        if (payments.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No billing receipts found.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(payments) { bill ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(bill.description, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                                AmazeBadge(text = bill.status, variant = if (bill.status == "PAID") BadgeVariant.SUCCESS else BadgeVariant.DANGER)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Billing ID: ${bill.billingId}", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                Text(bill.amount, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
                            }
                            if (bill.paymentDate != null) {
                                Text("Paid on: ${bill.paymentDate} (Receipt: ${bill.receiptNo})", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySubScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    val libraryRes by AppState.library.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()
    
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Book Search Catalog",
            placeholder = "Search by Title, Author, or ISBN..."
        )
        Spacer(modifier = Modifier.height(8.dp))
        AmazeButton(
            text = if (isSearching) "Searching..." else "Search Catalog",
            onClick = {
                if (searchQuery.isNotBlank()) {
                    scope.launch {
                        isSearching = true
                        try {
                            val res = AmazeClient.searchLibrary(searchQuery)
                            searchResults = res.searchResults
                        } catch (e: Exception) {
                            searchResults = emptyList()
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
            Text("Search Results", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
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

@Composable
fun TransportSubScreen() {
    val colors = AmazeTheme.colors
    val transportRes by AppState.transport.collectAsState()
    val buses = transportRes?.buses ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("DAYBOARDER STATUS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transportRes?.dayBoarderStatus ?: "APPROVED (Bus Pass Active)",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Bus Timings & Routes", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))

        if (buses.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No bus routes found.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(buses) { bus ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.accent)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Route No: ${bus.routeNo}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Text(bus.routeName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("Departs at: ${bus.time}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                if (bus.driverName != null) {
                                    Text("Driver: ${bus.driverName} (${bus.driverPhone})", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LMSSubScreen() {
    val colors = AmazeTheme.colors
    val lmsRes by AppState.lms.collectAsState()
    val examRes by AppState.examSchedule.collectAsState()

    val assignments = lmsRes?.assignments ?: emptyList()
    val examSchedule = examRes?.schedule ?: emptyMap()

    var activeViewTab by remember { mutableStateOf("Assignments") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmazeButton(
                text = "Assignments",
                onClick = { activeViewTab = "Assignments" },
                variant = if (activeViewTab == "Assignments") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AmazeButton(
                text = "Exam Schedule",
                onClick = { activeViewTab = "Exam Schedule" },
                variant = if (activeViewTab == "Exam Schedule") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeViewTab == "Assignments") {
            if (assignments.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No pending LMS assignments.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(assignments) { assign ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(assign.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    AmazeBadge(
                                        text = assign.status,
                                        variant = if (assign.status == "Submitted") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                                    )
                                }
                                Text(assign.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("Due Date: ${assign.dueDate}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                if (assign.score != null) {
                                    Text("Score: ${assign.score} / ${assign.maxMarks}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (examSchedule.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No exam schedules announced.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    examSchedule.forEach { (semId, exams) ->
                        item {
                            Text(semId, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(exams) { exam ->
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(exam.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                        AmazeBadge(text = "${exam.examDate} (${exam.examSession})", variant = BadgeVariant.INFO)
                                    }
                                    Text(exam.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("Time: ${exam.examTime} (Report: ${exam.reportingTime})", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Exam Venue", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                            Text(exam.venue, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Seat Number", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                            Text(exam.seatNo, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 5. PROFILE & PREFERENCES SCREEN ──


