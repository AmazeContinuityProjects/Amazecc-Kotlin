package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.CalendarIcs
import com.amazecc.app.shared.domain.DayEvent
import com.amazecc.app.shared.domain.DayType
import com.amazecc.app.shared.domain.EventKind
import com.amazecc.app.shared.domain.MonthModel
import com.amazecc.app.shared.domain.ReportDataAssembler
import com.amazecc.app.shared.domain.buildEnrichedCalendars
import com.amazecc.app.shared.domain.calendarSources
import com.amazecc.app.shared.domain.kindLabel
import com.amazecc.app.shared.domain.leadingBlanks
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3EKohaBookDetail
import com.amazecc.app.shared.domain.overhaul.M3EKohaHolding
import com.amazecc.app.shared.domain.overhaul.M3EReportSections
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportAction
import com.amazecc.app.shared.domain.overhaul.M3EStudentReportState
import com.amazecc.app.shared.domain.overhaul.M3ETimetableAction
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.domain.overhaul.M3ETimetableState
import com.amazecc.app.shared.domain.overhaul.TimetableViewMode
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.model.KohaBook
import com.amazecc.app.shared.model.KohaDetailBook
import com.amazecc.app.shared.model.KohaHolding
import com.amazecc.app.shared.model.NamedCalendar
import com.amazecc.app.shared.model.PaymentItem
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.TimetableSlot
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.utils.ChangelogEntry
import com.amazecc.app.shared.utils.ContentData
import com.amazecc.app.shared.utils.ReportExportBuilder
import com.amazecc.app.shared.utils.ReportExportFormat
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.openDocumentBytes
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.utils.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
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
    // D-121: host-owned catalog paging (page 0 is `searchResults`; deeper pages come from Koha).
    var catalogPage by remember { mutableStateOf(0) }
    var catalogPageResults by remember { mutableStateOf<List<M3ELibraryBook>>(emptyList()) }
    var isPaging by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }

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
        // A new query starts at page 0; older pages are no longer valid.
        catalogPage = 0
        catalogPageResults = emptyList()
        pageError = null
        isPaging = false
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
        renewNotice = renewNotice,
        catalogPage = catalogPage,
        catalogPageSize = 20,
        catalogPageResults = catalogPageResults,
        isPaging = isPaging,
        pageError = pageError
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
                        catalogPage = 0
                        catalogPageResults = emptyList()
                        pageError = null
                        isPaging = false
                    }
                    is M3ELibrariesAction.LoadCatalogPage -> {
                        if (searchQuery.isNotBlank() && action.page >= 0) {
                            scope.launch {
                                isPaging = true
                                pageError = null
                                try {
                                    val res = withContext(Dispatchers.Default) {
                                        AmazeClient.searchLibrary(searchQuery, "kw", action.page * 20)
                                    }
                                    catalogPage = action.page
                                    catalogPageResults = res.books.map { it.toM3ELibraryBook() }
                                    pageError = if (res.success) null else {
                                        res.error?.takeIf { it.isNotBlank() }
                                            ?: res.message?.takeIf { it.isNotBlank() }
                                            ?: "Could not load this page"
                                    }
                                } catch (e: Exception) {
                                    pageError = e.message ?: "Could not load this page"
                                } finally {
                                    isPaging = false
                                }
                            }
                        }
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
    // The same five flows the legacy CalendarScreen reads. They already live on AppState; the M3E
    // host used to ignore them and show only the scraped day types. Collecting them here is the
    // entire wiring change for D-115 — no AppState / M3EStateBridge edits are needed.
    val domain by AppState.domain.collectAsState()
    val moodleData by AppState.moodleData.collectAsState()
    val registeredEvents by AppState.registeredEvents.collectAsState()
    val tasks by AppState.tasks.collectAsState()

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    // calendarsList holds every calendar type, including "ALL" → "General Semester" at index 0.
    val calendars = remember(calendarsListRes) {
        calendarsListRes?.calendars.orEmpty()
    }

    // D-122: honour the calendar type chosen in Settings on first composition; valid index
    // falls back to 0 ("ALL") until the calendars-list sync has populated the options.
    var selectedCalendarIndex by remember(calendars) {
        val preferred = com.amazecc.app.shared.repository.SettingsManager.getPreferredCalendar()
        mutableStateOf(calendars.indexOfFirst { it.name == preferred }.takeIf { it >= 0 } ?: 0)
    }
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

    /**
     * The one enriched projection, shared with the legacy CalendarScreen through [calendarSources].
     * Every real source Kotlin has data for is folded in here: published calendar, exam papers,
     * Moodle deadlines, tasks, OD records, attendance classes and EventHub registrations. The
     * screen receives only this projection, so its grid, filter and day list cannot disagree about
     * what a day contains.
     */
    val enrichedMonths = remember(
        calendars, calendarRes, domain, moodleData, registeredEvents, tasks,
        selectedSemester, selectedCalendarIndex, today
    ) {
        val activeCalendar = calendars.getOrNull(selectedCalendarIndex)
            ?: calendarRes?.months?.takeIf { it.isNotEmpty() }?.let {
                NamedCalendar(name = "General Semester", months = it)
            }
        buildEnrichedCalendars(
            calendarSources(
                calendar = activeCalendar,
                snapshot = domain,
                semesterId = selectedSemester,
                moodle = moodleData?.data.orEmpty(),
                tasks = tasks,
                registeredEvents = registeredEvents?.events.orEmpty(),
                // The OD tracker screen still owns its raw JSON; until that screen moves to
                // odRecordsFrom, an OD row reads "On-Duty" instead of "wasted"/"recovered" —
                // exactly the degradation the legacy CalendarScreen documents.
                odTracker = emptyMap(),
            ),
            now = today,
        )
    }

    val months = remember(enrichedMonths) {
        enrichedMonths.map { it.toM3ECalendarMonth() }
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

/**
 * The enriched month, flattened into the screen's immutable view model.
 *
 * The domain's [MonthModel] already carries the authoritative weekday offset and day type, so no
 * label parsing or text sniffing happens here. Keeping this mapping pure makes the merged sources
 * testable without a host, and keeps the screen stateless.
 */
internal fun MonthModel.toM3ECalendarMonth(today: LocalDate = com.amazecc.app.shared.domain.today()): M3ECalendarMonth =
    M3ECalendarMonth(
        name = label,
        leadingBlankDays = leadingBlanks(year, monthIndex),
        days = days.map { day ->
            M3ECalendarDay(
                date = day.date,
                events = day.events.flatMap { event ->
                    val entries = mutableListOf(event.toM3ECalendarEvent())
                    // The projection folds a milestone's papers into the milestone so the day sheet
                    // can show one assessment. Each paper is still a real exam entry, so surface it
                    // here: a CAT day must show an exam dot, not only a milestone dot.
                    event.papers.orEmpty().forEach { paper -> entries += paper.toM3ECalendarEvent() }
                    entries
                },
                isToday = day.fullDate == today,
                dayType = day.dayType.toM3EDayType(),
            )
        }
    )

private fun DayType.toM3EDayType(): M3ECalendarDayType = when (this) {
    DayType.INSTRUCTIONAL -> M3ECalendarDayType.INSTRUCTIONAL
    DayType.SEMIHOLIDAY -> M3ECalendarDayType.SEMIHOLIDAY
    DayType.NON_INSTRUCTIONAL -> M3ECalendarDayType.NON_INSTRUCTIONAL
    DayType.HOLIDAY -> M3ECalendarDayType.HOLIDAY
    DayType.OTHER -> M3ECalendarDayType.OTHER
}

/** One classified domain event, with its source preserved for dots, filters and labels. */
internal fun DayEvent.toM3ECalendarEvent(): M3ECalendarEvent {
    val source = when (kind) {
        EventKind.EXAM -> M3ECalendarSource.EXAM
        EventKind.MILESTONE -> M3ECalendarSource.MILESTONE
        EventKind.ASSIGNMENT -> M3ECalendarSource.DEADLINE
        EventKind.CLASS -> M3ECalendarSource.CLASS
        EventKind.HOLIDAY -> M3ECalendarSource.HOLIDAY
        EventKind.OD -> M3ECalendarSource.OD
        EventKind.EVENT -> M3ECalendarSource.EVENT
        EventKind.WORKING -> M3ECalendarSource.DAY_TYPE
    }
    val secondary = mutableListOf<String>()
    detail?.takeIf { it.isNotBlank() }?.let { secondary += it }
    courseCode?.takeIf { it.isNotBlank() }?.let { secondary += it }
    val typeLabel = when {
        kind == EventKind.ASSIGNMENT && taskId != null -> "Task"
        kind == EventKind.ASSIGNMENT -> "Moodle"
        else -> kindLabel(kind)
    }
    return M3ECalendarEvent(
        title = title,
        type = typeLabel,
        category = secondary.joinToString(" · "),
        source = source,
    )
}

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
    // Per-row download outcomes keyed by circular id. Written from the download coroutine,
    // read by the stateless screen as an immutable snapshot; only real results go in here.
    val downloadStates = remember { mutableStateMapOf<String, M3ECircularDownloadUi>() }
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

    /**
     * D-114: the real download action for a circular row. This is the same mechanism as
     * `AppState.downloadDocument(id)` (AmazeClient credentialed binary fetch → the
     * `AppState.circularDownload` flow → `openDocumentBytes` in the effect above), but it keeps
     * the actual result in hand so the row can show a genuine saved/failed state — the flow
     * publishes `null` on failure and cannot distinguish a first failed attempt from idle.
     */
    fun requestDownload(id: String) {
        if (id.isBlank()) return
        downloadStates[id] = M3ECircularDownloadUi(M3ECircularDownloadStatus.DOWNLOADING)
        scope.launch {
            val result = runCatching { AmazeClient.downloadCircular(id) }.getOrNull()
            // Feed the exact same flow the documents host and the open effect consume.
            AppDataStore.setCircularDownload(result)
            downloadStates[id] = if (result != null) {
                M3ECircularDownloadUi(
                    status = M3ECircularDownloadStatus.SAVED,
                    message = result.fileName
                )
            } else {
                M3ECircularDownloadUi(
                    status = M3ECircularDownloadStatus.FAILED,
                    message = "Download failed. Check your connection and retry."
                )
            }
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
        errorMessage = error ?: circularsRes?.let { if (!it.success) it.error ?: it.message else null },
        downloadStates = downloadStates.toMap()
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
                    // D-114: every row's Download/retry affordance reaches the real
                    // credentialed download pipeline and reports its true outcome per row.
                    is M3ECircularsAction.OpenCircular -> requestDownload(action.id)
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

    // D-115: a failed central sync used to land here with showLogin flipped on and no
    // message, so the screen proudly rendered "You're all caught up!" over nothing.
    // Bind the REAL sync outcome instead: only a genuinely missing payload asks for
    // credentials; an explicit failure stays on the list view with its error visible.
    LaunchedEffect(moodleData) {
        val result = moodleData
        when {
            result == null -> showLogin = true
            !result.success || !result.error.isNullOrBlank() || !result.message.isNullOrBlank() -> {
                errorMessage = listOfNotNull(
                    result.error?.takeIf { it.isNotBlank() },
                    result.message?.takeIf { it.isNotBlank() }
                ).firstOrNull() ?: "Moodle could not be reached"
                showLogin = false
            }
            else -> errorMessage = null
        }
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

    /**
     * D-115: Refresh (and the error state's Retry, which emits the same action) must
     * actually re-fetch. Saved credentials are the fallback when the login form is
     * still empty; a failed fetch is reported in place instead of hiding the list
     * behind a login prompt.
     */
    fun refreshMoodle() {
        val saved = SettingsManager.getMoodleCredentials()
        val user = username.ifBlank { saved?.first.orEmpty() }
        val pass = password.ifBlank { saved?.second.orEmpty() }
        if (user.isBlank() || pass.isBlank()) {
            showLogin = true
            errorMessage = null
            return
        }
        scope.launch {
            isAuthenticating = true
            errorMessage = null
            try {
                val res = withContext(Dispatchers.Default) {
                    AmazeClient.fetchMoodleData(user, pass)
                }
                if (res.success) {
                    AppState.updateMoodleData(res)
                    showLogin = false
                } else {
                    errorMessage = listOfNotNull(
                        res.error?.takeIf { it.isNotBlank() },
                        res.message?.takeIf { it.isNotBlank() }
                    ).firstOrNull() ?: "Moodle could not be reached"
                    showLogin = false
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not refresh Moodle"
                showLogin = false
            } finally {
                isAuthenticating = false
            }
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
                    M3EMoodleAction.Refresh -> refreshMoodle()
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

// ==========================================
// TIMETABLE (D-116)
// ==========================================

private val M3E_TIMETABLE_DAY_KEYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")

private fun m3eTimetableDayName(key: String): String = when (key) {
    "MON" -> "Monday"
    "TUE" -> "Tuesday"
    "WED" -> "Wednesday"
    "THU" -> "Thursday"
    "FRI" -> "Friday"
    "SAT" -> "Saturday"
    else -> key
}

/** VTOP emits "8:00-8:50"; the screen shows zero-padded 24-hour times. */
private fun splitTimetableTime(raw: String?): Pair<String, String> {
    val parts = raw?.trim()?.split("-", limit = 2)?.map { it.trim() } ?: return "" to ""
    fun pad(value: String): String {
        val bits = value.split(":")
        return if (bits.size < 2) value else "${bits[0].padStart(2, '0')}:${bits[1]}"
    }
    return pad(parts[0]) to pad(parts.getOrNull(1).orEmpty())
}

/**
 * Pure derivation behind [M3ETimetableHost], extracted so the real-data mapping is testable
 * without mounting Compose (D-186 part B).
 *
 * `TimetableRes.slots` has no producer in this codebase, so the timetable can no longer be read
 * from that transport payload. The synced academic store is the single source of truth:
 * [AcademicDerivers.buildWeeklyTimetable] expands every real `StoredCourse.slots` code through
 * `SlotMap` into one [TimetableSlot] per (day, slot), carrying time, venue and attendance.
 */
internal data class M3ETimetableHostData(
    val days: List<M3ETimetableDay>,
    val totalCourses: Int,
    /** False when no real slot could be derived — i.e. an honest "nothing synced yet" state. */
    val hasSyncedTimetable: Boolean
)

internal fun buildM3ETimetableHostData(
    semester: SemesterData?,
    todayKey: String,
    selectedDayIndex: Int
): M3ETimetableHostData {
    val weeklySlots: List<TimetableSlot> =
        semester?.let { AcademicDerivers.buildWeeklyTimetable(it) }.orEmpty()

    val days = M3E_TIMETABLE_DAY_KEYS.mapIndexed { index, key ->
        val rows = weeklySlots.filter { it.day?.trim()?.uppercase() == key }
        val slots = rows.mapIndexed { slotIndex, row ->
            val (start, end) = splitTimetableTime(row.time)
            val pct = row.attendancePercentage
            M3ETimetableSlot(
                id = "$key-${row.slotName.orEmpty()}-${row.courseCode.orEmpty()}-$slotIndex",
                slot = row.slotName.orEmpty(),
                courseCode = row.courseCode.orEmpty(),
                courseTitle = row.courseTitle.orEmpty(),
                venue = row.venue.orEmpty(),
                faculty = row.faculty.orEmpty(),
                startTime = start,
                endTime = end,
                isHappeningNow = false,
                attendanceTone = when {
                    pct == null -> M3ECardTone.NEUTRAL
                    pct >= 75.0 -> M3ECardTone.GOOD
                    pct >= 65.0 -> M3ECardTone.WARNING
                    else -> M3ECardTone.CRITICAL
                },
                attendancePercentage = pct,
                isLab = row.courseType?.contains("lab", ignoreCase = true) == true
            )
        }
        M3ETimetableDay(
            dayName = m3eTimetableDayName(key),
            shortName = key.lowercase().replaceFirstChar { it.uppercase() },
            dayNumber = index + 1,
            isToday = key == todayKey,
            isSelected = index == selectedDayIndex,
            slots = slots,
            freeHoursCount = (10 - slots.size).coerceAtLeast(0)
        )
    }

    return M3ETimetableHostData(
        days = days,
        totalCourses = weeklySlots.mapNotNull { it.courseCode }.distinct().size,
        hasSyncedTimetable = weeklySlots.isNotEmpty()
    )
}

/**
 * Stateful host for [M3ETimetableScreen] (D-116). Collects the already-synced academic store,
 * expands the selected semester's real course slots through [AcademicDerivers], and maps them
 * into immutable screen state. No timetable data is invented here.
 *
 * There is deliberately no local refresh: neither [M3ETimetableScreen] nor [M3ETimetableSheet]
 * emits `M3ETimetableAction.Refresh`, so the old handler was dead code. Sync is owned centrally
 * by the app, and the screen's `isLoading` reflects that real sync state instead of a button press.
 */
@Composable
fun M3ETimetableHost(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    /** Home's full-timetable bottom sheet: renders [M3ETimetableSheet] instead of the screen shell. */
    sheetMode: Boolean = false
) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val academic by AppState.academic.collectAsState()
    val isSyncing by AppState.isLoading.collectAsState()

    var viewMode by remember {
        mutableStateOf(if (sheetMode) TimetableViewMode.VERTICAL else TimetableViewMode.SCHEDULE)
    }
    var denseGrid by remember { mutableStateOf(false) }
    var selectedDayIndex by remember { mutableStateOf(0) }

    LaunchedEffect(selectedSemId) {
        selectedDayIndex = 0
    }

    val todayKey = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.name.take(3)
    }

    val semester = academic.semesters[selectedSemId]
    val hostData = remember(semester, todayKey, selectedDayIndex) {
        buildM3ETimetableHostData(semester, todayKey, selectedDayIndex)
    }
    val days = hostData.days
    val safeDayIndex = selectedDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val semesterName = semesterMap[selectedSemId]
        ?: semester?.semesterName
        ?: selectedSemId.ifBlank { "Current semester" }
    val state = M3ETimetableState(
        semesterName = semesterName,
        selectedDayIndex = safeDayIndex,
        days = days.mapIndexed { index, day -> day.copy(isSelected = index == safeDayIndex) },
        viewMode = viewMode,
        totalCredits = semester?.courses?.values?.sumOf { it.credits?.toDoubleOrNull()?.toInt() ?: 0 } ?: 0,
        totalCourses = hostData.totalCourses,
        isLoading = isSyncing,
        denseGrid = denseGrid
    )

    val onTimetableAction: (M3ETimetableAction) -> Unit = { action ->
        when (action) {
            is M3ETimetableAction.SelectDay -> selectedDayIndex = action.dayIndex
            is M3ETimetableAction.SetViewMode -> viewMode = action.mode
            is M3ETimetableAction.SetDenseGrid -> denseGrid = action.enabled
            is M3ETimetableAction.SlotClicked -> Unit // the screen already renders slot details
            // Refresh has no emitter: no screen or sheet exposes a refresh control. Intentionally
            // inert rather than faking a network call the app does not own here.
            else -> Unit
        }
    }

    if (sheetMode) {
        // The sheet owns its own dismissal routes and insets: no page shell, no AppBackHandler.
        AmazeM3ETheme(isDark = isDark) {
            M3ETimetableSheet(
                state = state,
                onAction = onTimetableAction,
                onDismiss = onClose,
                modifier = modifier,
                // The sheet has no empty-copy slot; reuse its notice line so an unsynced week
                // is not silently presented as an empty grid.
                errorMessage = if (hostData.hasSyncedTimetable) null else NO_TIMETABLE_NOTICE
            )
        }
    } else {
        AmazeM3ETheme(isDark = isDark) {
            M3ETimetableScreen(
                state = state,
                onAction = onTimetableAction,
                modifier = modifier,
                hasSyncedTimetable = hostData.hasSyncedTimetable
            )
        }
    }
    if (!sheetMode) {
        AppBackHandler(onBack = onClose)
    }
}

internal const val NO_TIMETABLE_NOTICE =
    "No timetable synced yet — classes appear here after your academic data is synced."

// ==========================================
// STUDENT REPORT (D-115)
// ==========================================

/**
 * Stateful host for [M3EStudentReportScreen] (D-115, restructured in D-153). Every figure
 * comes from the identity and academic caches; export success/error state is written only
 * from a real save or share result — never from a button press alone.
 *
 * D-153: this is now a normal screen route. The host owns the M3E top app bar (back via the
 * regular AppState stack, refresh via the existing Refresh action) and back handling;
 * the stateless screen owns only the scrollable report body.
 *
 * Refresh is a real re-sync of every cache the report reads (the same sweep as the app's
 * "Sync All"), not an identity-only warm-up that would silently do nothing once identity is
 * already populated. [onRefreshReportData] is injectable so the wiring can be tested.
 */
@Composable
fun M3EStudentReportHost(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = { navigateBackOrMore() },
    onRefreshReportData: () -> Unit = { AppState.loadAllData() }
) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identity by UserStore.identity.collectAsState()
    val domain by AppState.domain.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val odDetails by AppState.odDetails.collectAsState()
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()

    var isExportingPdf by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    // Live section toggles. All ten start on (M3EReportSections defaults); flipping one
    // rebuilds the report snapshot so every export path reflects the current selection.
    var sections by remember { mutableStateOf(M3EReportSections()) }

    LaunchedEffect(Unit) { AppState.syncIdentityIfThin() }

    // D-157b — one real, web-parity snapshot feeds the on-screen metrics and every export path.
    // The assembler is pure; the only "now" is the caller's timestamp for the report date/name.
    // `sections` is a key so a toggle rebuilds the snapshot for all exports. generatedAt is
    // recomputed as part of that assembly — a required consequence of the rebuild, not churn.
    val report = remember(domain, allGrades, identity, odDetails, sections) {
        ReportDataAssembler.assemble(
            snapshot = domain,
            allGrades = allGrades,
            identity = identity,
            sections = sections,
            officialOd = odDetails,
            generatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    val state = M3EStudentReportState(
        studentName = report.identity.name,
        registrationNumber = report.identity.registrationNumber,
        programme = report.identity.programme,
        school = report.identity.school,
        campus = report.identity.campus,
        proctorName = report.identity.proctorName?.takeIf { it.isNotBlank() },
        proctorEmail = report.identity.proctorEmail?.takeIf { it.isNotBlank() },
        cgpa = report.summary.cgpa,
        overallAttendancePct = report.summary.attendancePct,
        completedCredits = report.summary.creditsEarned,
        totalRequiredCredits = report.summary.creditsRegistered,
        hasArrears = report.summary.arrearsCount > 0,
        arrearsCount = report.summary.arrearsCount,
        isExportingPdf = isExportingPdf,
        exportSuccessMessage = exportSuccessMessage,
        isLoading = !identity.hasIdentity && report.subjects.isEmpty(),
        sections = sections
    )

    fun exportPdf() {
        scope.launch {
            isExportingPdf = true
            exportSuccessMessage = null
            exportErrorMessage = null
            try {
                val file = withContext(Dispatchers.Default) {
                    ReportExportBuilder.build(report, ReportExportFormat.PDF)
                }
                when {
                    saveFile(file.filename, file.bytes) ->
                        exportSuccessMessage = "Saved to Downloads as ${file.filename}"
                    else -> exportErrorMessage =
                        "Could not save ${file.filename}. Check device storage and retry."
                }
            } catch (e: Exception) {
                exportErrorMessage = e.message ?: "Could not generate the PDF"
            } finally {
                isExportingPdf = false
            }
        }
    }

    /** One action sink so both the screen and the app bar's Refresh control emit the same actions. */
    fun handleReportAction(action: M3EStudentReportAction) {
        when (action) {
            M3EStudentReportAction.ExportPdfReport -> exportPdf()
            M3EStudentReportAction.ExportJsonData -> {
                scope.launch {
                    try {
                        // Serialising a full report is CPU work — keep it off Main like the PDF path.
                        val file = withContext(Dispatchers.Default) {
                            ReportExportBuilder.build(report, ReportExportFormat.JSON)
                        }
                        actionNotice = if (saveFile(file.filename, file.bytes)) {
                            "Saved ${file.filename} to Downloads"
                        } else {
                            "Could not save ${file.filename}"
                        }
                    } catch (e: Exception) {
                        actionNotice = "Could not generate JSON: ${e.message ?: "unknown error"}"
                    }
                }
            }
            M3EStudentReportAction.ExportHtmlReport -> {
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.Default) {
                            ReportExportBuilder.build(report, ReportExportFormat.HTML)
                        }
                        actionNotice = if (saveFile(file.filename, file.bytes)) {
                            "Saved ${file.filename} to Downloads"
                        } else {
                            "Could not save ${file.filename}"
                        }
                    } catch (e: Exception) {
                        actionNotice = "Could not generate HTML: ${e.message ?: "unknown error"}"
                    }
                }
            }
            M3EStudentReportAction.ShareReport -> {
                val shared = shareText(ReportExportBuilder.shareText(report))
                actionNotice = if (shared) "Report handed to the share sheet" else "No app is available to share this report"
            }
            // The report is assembled from many caches (attendance, grades, curriculum,
            // finance, OD, events, services). A thin-identity warm-up would be a no-op for
            // all of them, so Refresh runs the same full sweep as the rest of the app.
            M3EStudentReportAction.Refresh -> onRefreshReportData()
            is M3EStudentReportAction.ToggleSection -> {
                // `when` subject is smart-cast here; there is no implicit `it`.
                sections = sections.with(action.key, action.enabled)
            }
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EStudentReportContent(
            state = state,
            onAction = { action -> handleReportAction(action) },
            onBack = onClose,
            exportErrorMessage = exportErrorMessage,
            // Real JSON/HTML/share outcome from the action sink above, never a fabricated success.
            notice = actionNotice,
            modifier = modifier
        )
    }
    AppBackHandler(onBack = onClose)
}
