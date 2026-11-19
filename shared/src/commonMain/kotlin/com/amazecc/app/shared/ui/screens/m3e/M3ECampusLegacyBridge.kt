package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.KohaBook
import com.amazecc.app.shared.model.PaymentItem
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.ChangelogEntry
import com.amazecc.app.shared.utils.ContentData
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.openDocumentBytes
import com.amazecc.app.shared.utils.openExternalUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Stateful hosts for the campus & services M3E screens.
 *
 * Every host collects the existing [AppState] flows, maps them into the immutable
 * screen state, and forwards user intents to the existing AppState / AmazeClient
 * pipeline. No host invents a network layer; online and offline paths keep using
 * the same scraper / cache tiers as the legacy screens they replace. App.kt routes
 * to the `*ScreenHost` composables below.
 */
private fun navigateBackOrMore() {
    if (!AppState.navigateBack()) AppState.navigateTo(Screen.MORE)
}

// ==========================================
// PAYMENTS
// ==========================================

/** Stateful host for [M3EPaymentsScreen]. */
@Composable
fun M3EPaymentsScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val paymentsRes by AppState.payments.collectAsState()
    val receiptDetail by AppState.receiptDetail.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val error by AppState.error.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(M3EPaymentsTab.DUES) }
    var receiptDismissed by remember { mutableStateOf(false) }
    var receiptNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(paymentsRes) {
        if (paymentsRes == null) AppState.refreshPayments()
    }
    // A freshly fetched detail reopens the sheet; dismissing it keeps it closed until new data
    // arrives (no AppState clear API is part of the receipt contract).
    LaunchedEffect(receiptDetail) {
        if (receiptDetail != null) receiptDismissed = false
    }
    AppBackHandler { navigateBackOrMore() }

    val dues = remember(paymentsRes) {
        paymentsRes?.payments.orEmpty()
            .filter { !it.status.equals("PAID", ignoreCase = true) }
            .map { it.toM3EPaymentItem() }
    }
    val receipts = remember(paymentsRes) {
        paymentsRes?.payments.orEmpty()
            .filter { it.status.equals("PAID", ignoreCase = true) }
            .map { it.toM3EPaymentItem() }
    }

    val state = M3EPaymentsState(
        walletBalance = normalizeWalletBalance(paymentsRes?.walletBalance),
        dues = dues,
        receipts = receipts,
        selectedTab = selectedTab,
        isLoading = isLoading && paymentsRes == null,
        errorMessage = error ?: paymentsRes?.error,
        receiptDetail = receiptDetail?.takeIf { !receiptDismissed },
        receiptNotice = receiptNotice
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EPaymentsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EPaymentsAction.SelectTab -> selectedTab = action.tab
                    M3EPaymentsAction.Refresh -> AppState.refreshPayments()
                    // No in-app gateway exists: open VTOP's real payments page in a browser.
                    is M3EPaymentsAction.PayNow -> openExternalUrl(VTOP_PAYMENTS_URL)
                    is M3EPaymentsAction.DownloadReceipt -> {
                        val receiptNo = (dues + receipts)
                            .firstOrNull { it.id == action.paymentId }?.receiptNo
                        if (receiptNo.isNullOrBlank()) {
                            receiptNotice = "No receipt number is attached to this payment."
                        } else {
                            receiptNotice = null
                            receiptDismissed = false
                            scope.launch { AppState.refreshReceiptDetail(receiptNo) }
                        }
                    }
                    M3EPaymentsAction.DismissReceipt -> receiptDismissed = true
                }
            },
            modifier = modifier
        )
    }
}

private const val VTOP_PAYMENTS_URL = "https://vtopcc.vit.ac.in/vtop/p2p/Payments"

private fun PaymentItem.toM3EPaymentItem() = M3EPaymentItem(
    id = billingId,
    reference = receiptNo?.takeIf { it.isNotBlank() } ?: billingId,
    date = paymentDate ?: dueDate ?: "",
    description = description,
    amount = normalizeWalletBalance(amount),
    isPaid = status.equals("PAID", ignoreCase = true),
    receiptNo = receiptNo.orEmpty()
)

private fun normalizeWalletBalance(raw: String?): String =
    raw?.replace("Rs.", "₹")?.replace("INR", "₹")?.trim().orEmpty()

// ==========================================
// LIBRARIES
// ==========================================

/** Stateful host for [M3ELibrariesScreen]. */
@Composable
fun M3ELibrariesScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val libraryRes by AppState.library.collectAsState()
    val loginRequired by AppState.libraryLoginRequired.collectAsState()
    val localSearchTick by AppState.localSearchTick.collectAsState()
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(M3ELibraryTab.MY_BOOKS) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<M3ELibraryBook>>(emptyList()) }
    var totalResults by remember { mutableStateOf(0) }
    var hasSearched by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hasCredentials by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(libraryRes) {
        if (libraryRes == null && !loginRequired) AppState.refreshLibrary()
    }
    LaunchedEffect(Unit) {
        SettingsManager.getLibraryCredentials()?.let { creds ->
            username = creds.first
            password = creds.second
            hasCredentials = true
        }
    }
    LaunchedEffect(localSearchTick) {
        if (localSearchTick > 0) activeTab = M3ELibraryTab.CATALOG
    }
    AppBackHandler { navigateBackOrMore() }

    suspend fun runCatalogSearch(query: String) {
        isBusy = true
        errorMessage = null
        hasSearched = true
        try {
            val res = withContext(Dispatchers.Default) { AmazeClient.searchLibrary(query) }
            searchResults = res.books.map { it.toM3ELibraryBook() }
            totalResults = res.total
            errorMessage = res.error
        } catch (e: Exception) {
            errorMessage = e.message ?: "Search failed"
        } finally {
            isBusy = false
        }
    }

    val issuedBooks = remember(libraryRes) {
        libraryRes?.booksIssued.orEmpty().map { it.toM3ELibraryBook() }
    }

    val state = M3ELibrariesState(
        activeTab = activeTab,
        issuedBooks = issuedBooks,
        searchResults = searchResults,
        totalResults = totalResults,
        searchQuery = searchQuery,
        hasSearched = hasSearched,
        loginRequired = loginRequired,
        hasCredentials = hasCredentials,
        username = username,
        password = password,
        isBusy = isBusy,
        errorMessage = errorMessage
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ELibrariesScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ELibrariesAction.SelectTab -> activeTab = action.tab
                    is M3ELibrariesAction.UpdateSearch -> searchQuery = action.query
                    M3ELibrariesAction.SearchCatalog -> {
                        if (searchQuery.isNotBlank()) scope.launch { runCatalogSearch(searchQuery) }
                    }
                    M3ELibrariesAction.ClearSearch -> {
                        searchQuery = ""
                        searchResults = emptyList()
                        totalResults = 0
                        hasSearched = false
                        errorMessage = null
                    }
                    is M3ELibrariesAction.UpdateUsername -> username = action.value
                    is M3ELibrariesAction.UpdatePassword -> password = action.value
                    M3ELibrariesAction.SubmitLogin -> {
                        hasCredentials = true
                        errorMessage = null
                        // Existing AppState path: persists credentials, fetches, updates the cache.
                        AppState.saveLibraryCredentials(username, password)
                    }
                    M3ELibrariesAction.ClearCredentials -> {
                        SettingsManager.saveLibraryCredentials("", "")
                        username = ""
                        password = ""
                        hasCredentials = false
                        errorMessage = null
                    }
                    is M3ELibrariesAction.RenewBook -> {
                        scope.launch {
                            isBusy = true
                            try {
                                withContext(Dispatchers.Default) { AmazeClient.renewLibraryBook(action.bookId) }
                                AppState.syncLibrary()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Renewal failed"
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                    is M3ELibrariesAction.OpenBookDetail -> AppState.openKohaBookDetail(action.biblionumber)
                    M3ELibrariesAction.Refresh -> {
                        if (activeTab == M3ELibraryTab.CATALOG && searchQuery.isNotBlank()) {
                            scope.launch { runCatalogSearch(searchQuery) }
                        } else {
                            AppState.refreshLibrary()
                        }
                    }
                }
            },
            modifier = modifier
        )
    }
}

private fun BookItem.toM3ELibraryBook() = M3ELibraryBook(
    id = bookId,
    title = title,
    author = author.orEmpty(),
    dueDate = dueDate,
    fineAmount = fineAmount
)

private fun KohaBook.toM3ELibraryBook() = M3ELibraryBook(
    id = biblionumber,
    title = title,
    author = author,
    publisher = publisher.takeIf { it.isNotBlank() },
    isbn = isbn.takeIf { it.isNotBlank() }
)

// ==========================================
// ACADEMIC CALENDAR
// ==========================================

/** Stateful host for [M3ECalendarScreen]. */
@Composable
fun M3ECalendarScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val calendarsListRes by AppState.calendarsList.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val error by AppState.error.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    // calendarsList holds every calendar type, including "ALL" → "General Semester" at index 0.
    val calendars = remember(calendarsListRes) {
        calendarsListRes?.calendars.orEmpty()
    }

    var selectedCalendarIndex by remember { mutableStateOf(0) }
    var selectedMonthIndex by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var eventFilter by remember { mutableStateOf(M3ECalendarFilter.ALL) }

    // Always (re)fetch when there is nothing to show — including a null/empty first load.
    // `refreshCalendarsList` also primes the single General Semester cache, so the grid renders
    // even when only the ALL calendar comes back from the scraper/API.
    LaunchedEffect(calendars.isEmpty(), calendarRes?.months.isNullOrEmpty(), selectedSemester) {
        if (calendars.isEmpty() || calendarRes?.months.isNullOrEmpty() == true) {
            AppState.refreshCalendarsList()
        }
    }

    val months = remember(calendars, calendarRes, selectedCalendarIndex, today) {
        val activeCalendar = calendars.getOrNull(selectedCalendarIndex)
        when {
            activeCalendar != null -> activeCalendar.months.map { it.toM3ECalendarMonth(today) }
            else -> calendarRes?.months?.map { it.toM3ECalendarMonth(today) } ?: emptyList()
        }
    }

    LaunchedEffect(months) {
        if (months.isEmpty()) return@LaunchedEffect
        if (selectedMonthIndex >= months.size) selectedMonthIndex = 0
        if (selectedDay == null) {
            selectedDay = months.getOrNull(selectedMonthIndex)?.days
                ?.firstOrNull { it.isToday }?.date
                ?: months[selectedMonthIndex].days.firstOrNull()?.date
        }
    }
    AppBackHandler { navigateBackOrMore() }

    val state = M3ECalendarState(
        calendarNames = calendars.map { it.name },
        selectedCalendarIndex = selectedCalendarIndex.coerceIn(0, (calendars.size - 1).coerceAtLeast(0)),
        months = months,
        selectedMonthIndex = selectedMonthIndex.coerceIn(0, (months.size - 1).coerceAtLeast(0)),
        selectedDay = selectedDay,
        eventFilter = eventFilter,
        isLoading = months.isEmpty() && error == null,
        errorMessage = error
            ?: calendarsListRes?.takeIf { !it.success }?.let { it.error ?: it.message }
            ?: "No calendar entries available — pull to retry"
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECalendarScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECalendarAction.SelectCalendar -> {
                        selectedCalendarIndex = action.index
                        selectedMonthIndex = 0
                        selectedDay = null
                    }
                    is M3ECalendarAction.SelectMonth -> {
                        selectedMonthIndex = action.index
                        selectedDay = null
                    }
                    is M3ECalendarAction.SelectDay -> selectedDay = action.day
                    is M3ECalendarAction.SetFilter -> eventFilter = action.filter
                    M3ECalendarAction.Refresh -> AppState.refreshCalendarsList()
                }
            },
            modifier = modifier
        )
    }
}

private fun CalendarMonth.toM3ECalendarMonth(today: LocalDate) = M3ECalendarMonth(
    name = month,
    leadingBlankDays = leadingBlankDaysFor(month, today),
    days = days.map { day ->
        M3ECalendarDay(
            date = day.date,
            events = day.events.map { event ->
                M3ECalendarEvent(title = event.text, type = event.type, category = event.category)
            },
            isToday = isSameMonth(month, today) && day.date == today.dayOfMonth
        )
    }
)

/** 1-based month number; `kotlinx.datetime.Month` has no `number` property in this version. */
private val Month.monthNumberValue: Int get() = ordinal + 1

private fun isSameMonth(monthName: String, date: LocalDate): Boolean {
    val firstPart = monthName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val parsed = Month.entries.firstOrNull { it.name.equals(firstPart, ignoreCase = true) } ?: return false
    val year = monthName.trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: return false
    return parsed.monthNumberValue == date.monthNumber && year == date.year
}

private fun leadingBlankDaysFor(monthName: String, today: LocalDate): Int {
    val parts = monthName.trim().split(Regex("\\s+"))
    val monthNumber = Month.entries
        .firstOrNull { it.name.equals(parts.firstOrNull().orEmpty(), ignoreCase = true) }
        ?.monthNumberValue ?: return 0
    val year = parts.getOrNull(1)?.toIntOrNull() ?: today.year
    // Sakamoto's weekday algorithm (0 = Sunday). Avoids platform-specific DayOfWeek APIs and
    // keeps this helper usable from commonMain without extra kotlinx-datetime extensions.
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val adjustedYear = if (monthNumber < 3) year - 1 else year
    val sundayBased = (adjustedYear + adjustedYear / 4 - adjustedYear / 100 + adjustedYear / 400 +
        offsets[monthNumber - 1] + 1).floorMod(7)
    return (sundayBased + 6) % 7 // convert to Monday-first leading blanks
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

// ==========================================
// CIRCULARS
// ==========================================

/** Stateful host for [M3ECircularsScreen]. */
@Composable
fun M3ECircularsScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val circularsRes by AppState.circulars.collectAsState()
    val isSyncing by AppState.isSyncing.collectAsState()
    val error by AppState.error.collectAsState()

    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(circularsRes) {
        if (circularsRes == null) AppState.refreshCirculars()
    }
    AppBackHandler { navigateBackOrMore() }

    val folders = remember(circularsRes) {
        circularsRes?.circulars.orEmpty().mapIndexed { index, folder ->
            val name = folder.title ?: folder.name ?: "Untitled folder"
            M3ECircularFolder(
                key = folder.id ?: "$name-$index",
                name = name,
                documents = folder.children.orEmpty().map { child ->
                    M3ECircularDocument(
                        id = child.id.orEmpty(),
                        title = child.title ?: child.name ?: "Untitled"
                    )
                }
            )
        }
    }

    val state = M3ECircularsState(
        folders = folders,
        expandedKeys = expandedKeys,
        isLoading = isSyncing && circularsRes == null,
        errorMessage = error ?: circularsRes?.let { if (!it.success) it.error ?: it.message else null }
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECircularsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECircularsAction.ToggleFolder -> {
                        expandedKeys = if (action.key in expandedKeys) {
                            expandedKeys - action.key
                        } else {
                            expandedKeys + action.key
                        }
                    }
                    // Legacy screen had no document viewer; id is carried for a future deep link.
                    is M3ECircularsAction.OpenCircular -> Unit
                    M3ECircularsAction.Refresh -> AppState.refreshCirculars()
                }
            },
            modifier = modifier
        )
    }
}

// ==========================================
// MOODLE
// ==========================================

/** Stateful host for [M3EMoodleScreen]. */
@Composable
fun M3EMoodleScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val moodleData by AppState.moodleData.collectAsState()
    val scope = rememberCoroutineScope()

    var showLogin by remember { mutableStateOf(moodleData == null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hasSavedCredentials by remember { mutableStateOf(false) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(moodleData) {
        if (moodleData == null) showLogin = true
    }
    LaunchedEffect(Unit) {
        SettingsManager.getMoodleCredentials()?.let { creds ->
            username = creds.first
            password = creds.second
            hasSavedCredentials = true
        }
    }
    AppBackHandler {
        if (showLogin && moodleData != null) showLogin = false else navigateBackOrMore()
    }

    val assignments = remember(moodleData) {
        moodleData?.data.orEmpty().mapIndexed { index, assignment ->
            M3EMoodleAssignment(
                id = assignment.url ?: "${assignment.courseCode.ifBlank { "moodle" }}-$index",
                courseCode = assignment.courseCode,
                courseTitle = assignment.courseTitle,
                title = assignment.taskTitle.ifBlank { assignment.name },
                due = assignment.due,
                isDone = assignment.done,
                url = assignment.url,
                teachers = assignment.teachers
            )
        }
    }

    val state = M3EMoodleState(
        assignments = assignments,
        showLogin = showLogin,
        username = username,
        password = password,
        isAuthenticating = isAuthenticating,
        errorMessage = errorMessage,
        hasSavedCredentials = hasSavedCredentials,
        isLoading = moodleData == null && !showLogin
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EMoodleScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EMoodleAction.UpdateUsername -> username = action.value
                    is M3EMoodleAction.UpdatePassword -> password = action.value
                    M3EMoodleAction.SubmitCredentials -> {
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both fields"
                        } else {
                            scope.launch {
                                isAuthenticating = true
                                errorMessage = null
                                try {
                                    val res = withContext(Dispatchers.Default) {
                                        AmazeClient.fetchMoodleData(username, password)
                                    }
                                    if (res.success) {
                                        SettingsManager.saveMoodleCredentials(username, password)
                                        AppState.updateMoodleData(res)
                                        hasSavedCredentials = true
                                        showLogin = false
                                    } else {
                                        errorMessage = res.message ?: "Authentication failed"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Could not connect to Moodle"
                                } finally {
                                    isAuthenticating = false
                                }
                            }
                        }
                    }
                    M3EMoodleAction.ClearCredentials -> {
                        SettingsManager.clearMoodleCredentials()
                        username = ""
                        password = ""
                        hasSavedCredentials = false
                        errorMessage = null
                    }
                    // No commonMain browser intent in the legacy screen either.
                    is M3EMoodleAction.OpenAssignment -> Unit
                    M3EMoodleAction.Refresh -> showLogin = true
                }
            },
            modifier = modifier
        )
    }
}

// ==========================================
// DOCUMENTS
// ==========================================

/**
 * Stateful host for [M3EDocumentsScreen].
 *
 * There is no documents listing endpoint: `AppState.documents` is the data
 * worker's flattened view of the existing `/api/circulars` folder tree. The host
 * collects that flow, refreshes when empty, maps each `DocumentItem` into an
 * immutable [M3EDocumentCard], and forwards user intents. No tabs.
 *
 * Only `id` and `title` are read from `DocumentItem` (owned by the data-layer
 * worker). `OpenDocument(id)` delegates the credentialed circular download to
 * `AppState.downloadDocument`; the resulting [AppState.circularDownload] value is
 * handed to the platform's `openDocumentBytes` from a `LaunchedEffect`.
 */
@Composable
fun M3EDocumentsScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val documents by AppState.documents.collectAsState()
    val downloadResult by AppState.circularDownload.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val error by AppState.error.collectAsState()
    val scope = rememberCoroutineScope()

    // Identity guard: open each fetched document exactly once. Kept as Any? so the host does not
    // need to name the data worker's result type (only its bytes/fileName/mimeType members).
    var lastOpenedDownload by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(documents) {
        if (documents.isEmpty()) AppState.refreshDocuments()
    }
    LaunchedEffect(downloadResult) {
        val result = downloadResult
        if (result != null && result !== lastOpenedDownload) {
            lastOpenedDownload = result
            openDocumentBytes(result.bytes, result.fileName, result.mimeType)
        }
    }
    AppBackHandler { navigateBackOrMore() }

    val cards = remember(documents) {
        documents.map { document ->
            M3EDocumentCard(
                id = document.id,
                title = document.title
            )
        }
    }

    val state = M3EDocumentsState(
        documents = cards,
        isLoading = isLoading && documents.isEmpty(),
        errorMessage = error
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EDocumentsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    M3EDocumentsAction.Refresh -> AppState.refreshDocuments()
                    // Download runs off the UI thread and publishes into AppState.circularDownload;
                    // the LaunchedEffect above performs the actual platform "open document" call.
                    is M3EDocumentsAction.OpenDocument ->
                        scope.launch { AppState.downloadDocument(action.id) }
                }
            },
            modifier = modifier
        )
    }
}

// ==========================================
// CHANGELOG
// ==========================================

/** Stateful host for [M3EChangelogScreen]. */
@Composable
fun M3EChangelogScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val latestReleaseNotes by AppState.latestReleaseNotes.collectAsState()
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf(UpdateConfig.getCurrentVersion()) }
    var bundled by remember { mutableStateOf<List<ChangelogEntry>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bundled = withContext(Dispatchers.Default) { ContentData.changelog() }
    }
    AppBackHandler { navigateBackOrMore() }

    val entries = remember(latestReleaseNotes, bundled) {
        if (latestReleaseNotes.isNotBlank()) {
            latestReleaseNotes.lines().filter { it.isNotBlank() }
                .map { M3EChangelogEntry("Release Notes", it.trim()) }
        } else {
            bundled.mapIndexed { index, entry ->
                M3EChangelogEntry(
                    title = entry.title ?: "Update ${bundled.size - index}",
                    description = entry.description
                )
            }
        }
    }

    val state = M3EChangelogState(
        version = version,
        entries = entries,
        isRefreshing = isRefreshing
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EChangelogScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    M3EChangelogAction.Refresh -> {
                        scope.launch {
                            isRefreshing = true
                            AppState.forceCheckForUpdate()
                            bundled = withContext(Dispatchers.Default) { ContentData.changelog() }
                            isRefreshing = false
                        }
                    }
                    M3EChangelogAction.Back -> navigateBackOrMore()
                }
            },
            modifier = modifier
        )
    }
}

// ==========================================
// ABOUT
// ==========================================

/** Stateful host for [M3EAboutScreen]. */
@Composable
fun M3EAboutScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val updateStatus by AppState.updateStatus.collectAsState()
    val latestReleaseNotes by AppState.latestReleaseNotes.collectAsState()

    var version by remember { mutableStateOf(UpdateConfig.getCurrentVersion()) }
    var bundled by remember { mutableStateOf<List<ChangelogEntry>>(emptyList()) }
    var showChangelog by remember { mutableStateOf(false) }
    var showUpdateCheck by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bundled = withContext(Dispatchers.Default) { ContentData.changelog() }
    }
    AppBackHandler {
        if (showChangelog || showUpdateCheck) {
            showChangelog = false
            showUpdateCheck = false
        } else {
            navigateBackOrMore()
        }
    }

    val highlights = remember(latestReleaseNotes, bundled) {
        if (latestReleaseNotes.isNotBlank()) {
            latestReleaseNotes.lines().filter { it.isNotBlank() }.map { it.trim() }
        } else {
            bundled.map { entry ->
                (entry.title?.let { "$it: " } ?: "") + entry.description
            }
        }
    }

    val statusText = when (updateStatus) {
        is AppState.UpdateStatus.Checking -> "Checking for updates..."
        is AppState.UpdateStatus.Available -> "A newer version is available"
        is AppState.UpdateStatus.UpToDate -> "You are up to date"
        is AppState.UpdateStatus.Error -> (updateStatus as AppState.UpdateStatus.Error).message
        else -> null
    }

    val state = M3EAboutState(
        version = version,
        updateStatusText = statusText,
        releaseHighlights = highlights,
        isCheckingUpdate = updateStatus is AppState.UpdateStatus.Checking,
        showChangelog = showChangelog,
        showUpdateCheck = showUpdateCheck
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EAboutScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    M3EAboutAction.CheckForUpdates -> {
                        showUpdateCheck = true
                        AppState.forceCheckForUpdate()
                    }
                    M3EAboutAction.OpenChangelog -> showChangelog = true
                    M3EAboutAction.DismissDialog -> {
                        showChangelog = false
                        showUpdateCheck = false
                    }
                    // Legacy credits card had no external link target.
                    M3EAboutAction.OpenGithub -> Unit
                    M3EAboutAction.Back -> navigateBackOrMore()
                }
            },
            modifier = modifier
        )
    }
}
