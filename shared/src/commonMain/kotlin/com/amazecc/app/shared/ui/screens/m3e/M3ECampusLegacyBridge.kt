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
import com.amazecc.app.shared.domain.CalendarIcs
import com.amazecc.app.shared.domain.overhaul.M3EKohaBookDetail
import com.amazecc.app.shared.domain.overhaul.M3EKohaHolding
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.KohaBook
import com.amazecc.app.shared.model.KohaDetailBook
import com.amazecc.app.shared.model.KohaHolding
import com.amazecc.app.shared.model.PaymentItem
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.ChangelogEntry
import com.amazecc.app.shared.utils.ContentData
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.openDocumentBytes
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.utils.rememberFileSaver
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

    // Payments are synced centrally (SyncModule.PAYMENTS); page entry only reads the cache.
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
        receiptNotice = receiptNotice,
        // D-059 — privacy mode masks fee amounts (web parity — blurGrades/privacyMode).
        blurAmounts = SettingsManager.isPrivacyMode()
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
    val kohaBookTarget by AppState.kohaBookTarget.collectAsState()
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
    var bookDetail by remember { mutableStateOf<M3EKohaBookDetail?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var detailErrorMessage by remember { mutableStateOf<String?>(null) }
    var renewNotice by remember { mutableStateOf<String?>(null) }

    // Library dues are synced centrally (SyncModule.LIBRARY); page entry only reads the cache.
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
    // AppState.openKohaBookDetail() sets this target and routes here. Fetch the real detail once,
    // then consume the target so recomposition/navigation cannot replay the request.
    LaunchedEffect(kohaBookTarget) {
        val biblionumber = kohaBookTarget ?: return@LaunchedEffect
        activeTab = M3ELibraryTab.CATALOG
        bookDetail = null
        detailErrorMessage = null
        isDetailLoading = true
        try {
            val res = withContext(Dispatchers.Default) { AmazeClient.getKohaDetail(biblionumber) }
            if (res.success && res.book != null) {
                bookDetail = res.book.toM3EKohaBookDetail()
            } else {
                detailErrorMessage = res.message?.takeIf { it.isNotBlank() }
                    ?: res.error?.takeIf { it.isNotBlank() }
                    ?: "Could not load book details"
            }
        } catch (e: Exception) {
            detailErrorMessage = e.message ?: "Could not load book details"
        } finally {
            isDetailLoading = false
            AppState.consumeKohaBookTarget()
        }
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
            errorMessage = if (res.success) null else {
                res.error?.takeIf { it.isNotBlank() }
                    ?: res.message?.takeIf { it.isNotBlank() }
                    ?: "Search failed"
            }
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
        errorMessage = errorMessage,
        bookDetail = bookDetail,
        isDetailLoading = isDetailLoading,
        detailErrorMessage = detailErrorMessage,
        renewNotice = renewNotice
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
                            errorMessage = null
                            renewNotice = null
                            try {
                                val res = withContext(Dispatchers.Default) {
                                    AmazeClient.renewLibraryBook(action.bookId)
                                }
                                // A 404 still returns a BasicRes; never sync or claim success on failure.
                                val (notice, failure) = renewLibraryOutcome(res)
                                if (res.success) {
                                    renewNotice = notice
                                    AppState.syncLibrary()
                                } else {
                                    errorMessage = failure
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Renewal failed"
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                    is M3ELibrariesAction.OpenBookDetail -> AppState.openKohaBookDetail(action.biblionumber)
                    M3ELibrariesAction.DismissBookDetail -> {
                        bookDetail = null
                        detailErrorMessage = null
                        isDetailLoading = false
                        AppState.consumeKohaBookTarget()
                    }
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

private fun KohaDetailBook.toM3EKohaBookDetail() = M3EKohaBookDetail(
    biblionumber = biblionumber,
    title = title,
    author = author,
    publisher = publisher,
    edition = edition,
    isbn = isbn,
    description = description,
    summary = summary,
    subjects = subjects,
    ddc = ddc,
    holdings = holdings.map { it.toM3EKohaHolding() }
)

private fun KohaHolding.toM3EKohaHolding() = M3EKohaHolding(
    barcode = barcode,
    location = shelvingLocation.ifBlank { currentLibrary },
    callNumber = callNumber,
    status = status,
    dueDate = dateDue,
    notes = notes
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
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()

    // Calendars are synced centrally (SyncModule.CALENDARS_LIST). Page entry reads the cached
    // flows; the Refresh action below is the only user-initiated fetch.
    val rawMonths = remember(calendars, calendarRes, selectedCalendarIndex) {
        (calendars.getOrNull(selectedCalendarIndex)?.months ?: calendarRes?.months).orEmpty()
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
            ?: "No calendar entries available — use Refresh to retry",
        exportMessage = exportMessage
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
                        // Web parity — switching the programme re-fetches that calendar's months.
                        AppState.refreshCalendarsList()
                    }
                    is M3ECalendarAction.SelectMonth -> {
                        selectedMonthIndex = action.index
                        selectedDay = null
                    }
                    is M3ECalendarAction.SelectDay -> selectedDay = action.day
                    is M3ECalendarAction.SetFilter -> eventFilter = action.filter
                    M3ECalendarAction.Refresh -> AppState.refreshCalendarsList()
                    // Web parity — CalendarSubpage actions (D-061).
                    M3ECalendarAction.ExportCalendar -> scope.launch {
                        if (rawMonths.isEmpty()) {
                            exportMessage = "No calendar loaded to export"
                        } else {
                            val ics = CalendarIcs.buildAcademicCalendarIcs(rawMonths)
                            val eventCount = CalendarIcs.countVEvents(ics)
                            if (eventCount == 0) {
                                // An event-less VCALENDAR is valid but useless: never report it as exported.
                                exportMessage = "Nothing to export — this calendar has no holidays or exam events"
                            } else {
                                val saved = saveFile(CalendarIcs.FILE_NAME, ics.toByteArray())
                                exportMessage = if (saved) {
                                    "Exported $eventCount event(s) to Downloads — Open Files → ${CalendarIcs.FILE_NAME}"
                                } else {
                                    "Export failed"
                                }
                            }
                        }
                    }
                    M3ECalendarAction.OpenMoodle -> AppState.navigateTo(Screen.MOODLE)
                    M3ECalendarAction.OpenCirculars -> AppState.navigateTo(Screen.CIRCULARS)
                    M3ECalendarAction.Today -> {
                        val target = months.indexOfFirst { it.days.any { d -> d.isToday } }.coerceAtLeast(0)
                        selectedMonthIndex = target
                        selectedDay = months.getOrNull(target)?.days?.firstOrNull { it.isToday }?.date ?: today.dayOfMonth
                    }
                }
            },
            modifier = modifier
        )
    }
}

private fun CalendarMonth.toM3ECalendarMonth(today: LocalDate): M3ECalendarMonth {
    // Prefer the scraper's explicit year/month; fall back to parsing the month label so the
    // grid always starts on the real weekday and ends on the real last date of the month.
    val parsed = parseMonthLabel(month)
    val resolvedYear = year ?: parsed?.second ?: today.year
    val resolvedMonth = monthNumber ?: parsed?.first ?: today.monthNumber
    return M3ECalendarMonth(
        name = month,
        leadingBlankDays = leadingBlankDays(resolvedYear, resolvedMonth),
        days = days.map { day ->
            M3ECalendarDay(
                date = day.date,
                events = day.events.map { event ->
                    M3ECalendarEvent(title = event.text, type = event.type, category = event.category)
                },
                isToday = resolvedYear == today.year && resolvedMonth == today.monthNumber && day.date == today.dayOfMonth
            )
        }
    )
}

private fun parseMonthLabel(label: String): Pair<Int, Int>? {
    val parts = label.trim().split(Regex("\\s+"))
    val number = Month.entries.firstOrNull { it.name.equals(parts.firstOrNull().orEmpty(), ignoreCase = true) }
        ?.monthNumberValue ?: return null
    return number to (parts.getOrNull(1)?.toIntOrNull() ?: return null)
}

/** 1-based month number; `kotlinx.datetime.Month` has no `number` property in this version. */
private val Month.monthNumberValue: Int get() = ordinal + 1

private fun isSameMonth(monthName: String, date: LocalDate): Boolean {
    val firstPart = monthName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val parsed = Month.entries.firstOrNull { it.name.equals(firstPart, ignoreCase = true) } ?: return false
    val year = monthName.trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: return false
    return parsed.monthNumberValue == date.monthNumber && year == date.year
}

private fun leadingBlankDays(year: Int, monthNumber: Int): Int {
    // Sakamoto's weekday algorithm (0 = Sunday), Monday-first leading blanks.
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val adjustedYear = if (monthNumber < 3) year - 1 else year
    val sundayBased = (adjustedYear + adjustedYear / 4 - adjustedYear / 100 + adjustedYear / 400 +
        offsets[monthNumber - 1] + 1).floorMod(7)
    return (sundayBased + 6) % 7
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
    val circularDownload by AppState.circularDownload.collectAsState()
    val scope = rememberCoroutineScope()

    var expandedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Identity guard, mirroring M3EDocumentsScreenHost: open each downloaded circular exactly once.
    var lastOpenedCircular by remember { mutableStateOf<Any?>(null) }

    // Circulars are synced centrally (SyncModule.CIRCULARS); page entry reads the cache.
    AppBackHandler { navigateBackOrMore() }

    // Downloads run off the UI thread and publish into AppState.circularDownload; the
    // LaunchedEffect performs the platform "open document" call for the just-opened circular.
    LaunchedEffect(circularDownload) {
        val result = circularDownload
        if (result != null && result !== lastOpenedCircular) {
            lastOpenedCircular = result
            openDocumentBytes(result.bytes, result.fileName, result.mimeType)
        }
    }

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
                    // Same pipeline the Documents tab uses: download the circular into
                    // AppState.circularDownload; the LaunchedEffect above opens it.
                    is M3ECircularsAction.OpenCircular ->
                        scope.launch { AppState.downloadDocument(action.id) }
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
                    is M3EMoodleAction.OpenAssignment -> {
                        if (action.url.isNotBlank()) openExternalUrl(action.url)
                    }
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
 * D-110: circulars do not belong on this page. `AppState.documents` is the data worker's
 * flattened view of `/api/circulars` **plus** the small set of static official-document
 * entries, so this host keeps only those official records (identified by their stable ids)
 * and leaves the circular tree to `M3ECircularsScreenHost`. Admissions acknowledgements
 * come from the real whoami snapshot — no entries are invented when it is absent.
 *
 * `OpenDocument(id)` delegates the credentialed download to `AppState.downloadDocument`;
 * the resulting [AppState.circularDownload] value is handed to the platform's
 * `openDocumentBytes` from a `LaunchedEffect`.
 */
private val OFFICIAL_DOCUMENT_IDS = setOf("bonafide", "e-transcript", "additional-learning")

@Composable
fun M3EDocumentsScreenHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val documents by AppState.documents.collectAsState()
    val identity by UserStore.identity.collectAsState()
    val downloadResult by AppState.circularDownload.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val error by AppState.error.collectAsState()
    val scope = rememberCoroutineScope()

    // Identity guard: open each fetched document exactly once. Kept as Any? so the host does not
    // need to name the data worker's result type (only its bytes/fileName/mimeType members).
    var lastOpenedDownload by remember { mutableStateOf<Any?>(null) }

    // Documents are synced centrally (SyncModule.DOCUMENTS); page entry reads the cache.
    LaunchedEffect(downloadResult) {
        val result = downloadResult
        if (result != null && result !== lastOpenedDownload) {
            lastOpenedDownload = result
            openDocumentBytes(result.bytes, result.fileName, result.mimeType)
        }
    }
    AppBackHandler { navigateBackOrMore() }

    // Only the static official documents belong here; the flattened circulars stay on
    // the Circulars page. The id is the discriminator the data layer gives those entries.
    val cards = remember(documents) {
        documents.filter { it.id in OFFICIAL_DOCUMENT_IDS }.map { document ->
            M3EDocumentCard(
                id = document.id,
                title = document.title,
                subtitle = document.description.orEmpty(),
                details = document.rows.map { M3EDocumentRow(it.label, it.value) },
                messages = document.messages,
                error = document.error
            )
        }
    }

    // Real whoami acknowledgement records only — an absent block renders no cards.
    val acknowledgements = remember(identity.acknowledgement) {
        identity.acknowledgement?.documents.orEmpty().mapIndexed { index, doc ->
            M3EAcknowledgementCard(
                id = "${doc.serialNo.orEmpty()}-$index",
                serialNo = doc.serialNo.orEmpty(),
                documentName = doc.documentName.orEmpty(),
                status = doc.status.orEmpty()
            )
        }
    }

    val state = M3EDocumentsState(
        documents = cards,
        acknowledgements = acknowledgements,
        isLoading = isLoading && cards.isEmpty() && acknowledgements.isEmpty(),
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
                    M3EAboutAction.OpenGithub -> openExternalUrl(
                        "https://github.com/${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPO}"
                    )
                    M3EAboutAction.Back -> navigateBackOrMore()
                }
            },
            modifier = modifier
        )
    }
}
