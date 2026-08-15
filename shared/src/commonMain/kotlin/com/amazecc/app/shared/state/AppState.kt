package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.theme.AccentOcean
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.theme.CustomPalette
import com.amazecc.app.shared.theme.PaletteMode
import com.amazecc.app.shared.theme.PaletteRole
import com.amazecc.app.shared.theme.parseHexColor
import com.amazecc.app.shared.theme.toHexString
import androidx.compose.ui.graphics.Color
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.DemoData
import com.amazecc.app.shared.utils.requestNotificationPermissions
import com.amazecc.app.shared.utils.testLocalNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class AttendanceDisplayMode(val value: String) {
    PERCENTAGE("percentage"),
    FRACTION("fraction");

    companion object {
        fun fromString(s: String): AttendanceDisplayMode =
            entries.find { it.value == s } ?: PERCENTAGE
    }
}

private val REG_FIELD_NAMES = setOf("username", "date", "fromtime", "totime")

enum class Screen { SPLASH,
    LOGIN, ONBOARDING, HOME, ATTENDANCE, ACADEMICS, PAYMENTS, LIBRARIES, HOSTEL, CABSHARE, TRANSPORT, MORE, PROFILE,
    EVENTS, QBANK, SOCIAL, FFCS_PLANNER, FREE_CLASSROOMS, CALENDAR, GRADES, GPA_PREDICTOR,
    COURSE_ATTENDANCE, CIRCULARS, CURRICULUM, OD_TRACKER, COURSE_DASHBOARD,
    FACULTY_INFO, COURSE_MANAGEMENT,
    FEEDBACK_STATUS, FRESHER_WELCOME, DOCUMENTS, ABOUT, CLUB_DETAIL,
    COURSE_DETAIL, SETTINGS, MOODLE, CLUB_HUB, TASKS, EXAM_SCHEDULE,
    CHANGELOG, HALL_OF_FAME, ARREAR
}

object AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
    // Floating Header State — single source of truth is HeaderConfigs.headerConfigFor(currentScreen);
    // overrides are published only by dynamic (non-static) screens and are cleared on navigation.
    val headerOverride = MutableStateFlow<com.amazecc.app.shared.ui.components.HeaderConfig?>(null)
    private val _headerOverrideOwner = MutableStateFlow<Set<Screen>?>(null)
    
    private val _pinnedNavTabs = MutableStateFlow(listOf(Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE))
    val pinnedNavTabs: StateFlow<List<Screen>> = _pinnedNavTabs.asStateFlow()

    private val _isAppLibraryOpen = MutableStateFlow(false)
    val isAppLibraryOpen: StateFlow<Boolean> = _isAppLibraryOpen.asStateFlow()

    fun openAppLibrary() {
        _isAppLibraryOpen.value = true
    }

    fun closeAppLibrary() {
        _isAppLibraryOpen.value = false
    }

    val tabScreens: List<Screen>
        get() = listOf(Screen.HOME) + _pinnedNavTabs.value

    // Sync Notifications
    private val notificationService = com.amazecc.app.shared.services.NotificationService()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val backstack = mutableListOf<Screen>()

    // Global settings
    private val _theme = MutableStateFlow(AppTheme.SYSTEM)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _accent = MutableStateFlow(AccentTheme.OCEAN)
    val accent: StateFlow<AccentTheme> = _accent.asStateFlow()

    private val _customAccentColor = MutableStateFlow(AccentOcean)
    val customAccentColor: StateFlow<Color> = _customAccentColor.asStateFlow()

    private val _customPalette = MutableStateFlow(CustomPalette())
    val customPalette: StateFlow<CustomPalette> = _customPalette.asStateFlow()

    private val _uiScale = MutableStateFlow(1.0f)
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    private val _cgpaHidden = MutableStateFlow(false)
    val cgpaHidden: StateFlow<Boolean> = _cgpaHidden.asStateFlow()

    private val _attendanceDisplayMode = MutableStateFlow(AttendanceDisplayMode.PERCENTAGE)
    val attendanceDisplayMode: StateFlow<AttendanceDisplayMode> = _attendanceDisplayMode.asStateFlow()

    private val _showAttendanceInStats = MutableStateFlow(false)
    val showAttendanceInStats: StateFlow<Boolean> = _showAttendanceInStats.asStateFlow()

    private val _isBusSubscriber = MutableStateFlow(false)
    val isBusSubscriber: StateFlow<Boolean> = _isBusSubscriber.asStateFlow()

    private val _customAttendanceTarget = MutableStateFlow(
        SettingsManager.getFloatString(SettingsManager.KEY_CUSTOM_ATTENDANCE_TARGET)
    )
    val customAttendanceTarget: StateFlow<Float?> = _customAttendanceTarget.asStateFlow()

    fun effectiveAttendanceTarget(isBusSubscriber: Boolean): Float =
        _customAttendanceTarget.value ?: if (isBusSubscriber) 85f else 75f

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _animationsEnabled = MutableStateFlow(true)
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    private val _heroColorEnabled = MutableStateFlow(true)
    val heroColorEnabled: StateFlow<Boolean> = _heroColorEnabled.asStateFlow()

    private val _residentialStatus = MutableStateFlow(SettingsManager.getString(SettingsManager.RESIDENTIAL_STATUS, "Hosteller"))
    val residentialStatus: StateFlow<String> = _residentialStatus.asStateFlow()

    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        object Checking : UpdateStatus()
        data class Available(val release: GitHubRelease, val currentVersion: String) : UpdateStatus()
        object UpToDate : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
    }

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _updateDialogDismissedVersion = MutableStateFlow("")
    val updateDialogDismissedVersion: StateFlow<String> = _updateDialogDismissedVersion.asStateFlow()

    private val _latestReleaseNotes = MutableStateFlow("")
    val latestReleaseNotes: StateFlow<String> = _latestReleaseNotes.asStateFlow()

    fun dismissUpdateDialog() {
        val status = _updateStatus.value
        if (status is UpdateStatus.Available) {
            val ver = status.release.tagName.removePrefix("v")
            _updateDialogDismissedVersion.value = ver
            SettingsManager.setString(SettingsManager.KEY_UPDATE_DISMISSED_VERSION, ver)
            _updateStatus.value = UpdateStatus.Idle
        }
    }

    fun checkForUpdate(force: Boolean = false) {
        if (!force && _updateStatus.value is UpdateStatus.Checking) return
        val lastCheck = SettingsManager.getLong(SettingsManager.KEY_LAST_UPDATE_CHECK, 0L)
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastCheck < 24 * 60 * 60 * 1000L) {
            return // throttle: once per 24h
        }
        scope.launch {
            _updateStatus.value = UpdateStatus.Checking
            try {
                val currentVersion = UpdateConfig.getCurrentVersion()
                val release = AmazeClient.checkForUpdate()
                val latestVer = release.tagName.removePrefix("v").removePrefix("V").trim()
                val dismissed = _updateDialogDismissedVersion.value
                if (latestVer == dismissed) {
                    _updateStatus.value = UpdateStatus.UpToDate
                } else if (currentVersion.isNotBlank() && compareVersions(latestVer, currentVersion) > 0) {
                    // Capture release notes for About/Changelog screens
                    _latestReleaseNotes.value = release.body ?: ""
                    SettingsManager.setString(SettingsManager.KEY_LATEST_RELEASE_NOTES, release.body ?: "")
                    _updateStatus.value = UpdateStatus.Available(release, currentVersion)
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate
                }
                SettingsManager.setLong(SettingsManager.KEY_LAST_UPDATE_CHECK, Clock.System.now().toEpochMilliseconds())
            } catch (e: Exception) {
                _updateStatus.value = UpdateStatus.Error(e.message ?: "Failed to check for update")
            }
        }
    }

    fun forceCheckForUpdate() = checkForUpdate(force = true)

    private fun compareVersions(v1: String, v2: String): Int {
        val clean1 = normalizeVersion(v1)
        val clean2 = normalizeVersion(v2)
        if (clean1.isBlank() || clean2.isBlank()) return 0 // unknown versions -> treat as equal
        val parts1 = clean1.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun normalizeVersion(v: String): String {
        return v.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split("-")
            .firstOrNull() ?: ""
    }

    // Student identity lives in UserStore — the single source of truth

    private val _pendingExamSeatAlerts = MutableStateFlow<List<AccountCredential>>(emptyList())
    val pendingExamSeatAlerts: StateFlow<List<AccountCredential>> = _pendingExamSeatAlerts.asStateFlow()

    private val _statsCardsOrder = MutableStateFlow(listOf("attendance", "cgpa", "credits", "od"))
    val statsCardsOrder: StateFlow<List<String>> = _statsCardsOrder.asStateFlow()

    private val _enabledStatsCards = MutableStateFlow(setOf("attendance", "cgpa", "credits", "od"))
    val enabledStatsCards: StateFlow<Set<String>> = _enabledStatsCards.asStateFlow()

    private val _gpaGoal = MutableStateFlow("9.0")
    val gpaGoal: StateFlow<String> = _gpaGoal.asStateFlow()

    private val _ffcsRegistration = MutableStateFlow<FfcsRegistrationInfo?>(null)
    val ffcsRegistration: StateFlow<FfcsRegistrationInfo?> = _ffcsRegistration.asStateFlow()

    private val _pendingFfcsAlert = MutableStateFlow<FfcsRegistrationInfo?>(null)
    val pendingFfcsAlert: StateFlow<FfcsRegistrationInfo?> = _pendingFfcsAlert.asStateFlow()

    const val DEFAULT_SEMESTER_ID = "CH20262701"

    private val fallbackSemesterMap = mapOf(
        "CH20262705" to "Winter Semester 2026-27",
        "CH20262701" to "Fall Semester 2026-27",
        "CH20252605" to "Winter Semester 2025-26",
        "CH20252601" to "Fall Semester 2025-26",
        "CH20242505" to "Winter Semester 2024-25",
        "CH20242501" to "Fall Semester 2024-25",
        "CH20232405" to "Winter Semester 2023-24",
        "CH20232401" to "Fall Semester 2023-24"
    )
    private val _semesterMap = MutableStateFlow(fallbackSemesterMap)
    val semesterMap: StateFlow<Map<String, String>> = _semesterMap.asStateFlow()
    val semesterIDs: List<String> get() = _semesterMap.value.keys.toList()
    private val _selectedSemester = MutableStateFlow(DEFAULT_SEMESTER_ID)
    val selectedSemester: StateFlow<String> = _selectedSemester.asStateFlow()

    // Loading & Error states (driven by SyncEngine for backward compat)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    // Onboarding sync progress
    data class SyncStep(val name: String, val status: String) // status: "pending", "syncing", "done", "failed"
    private val _onboardingSyncSteps = MutableStateFlow<List<SyncStep>>(emptyList())
    val onboardingSyncSteps: StateFlow<List<SyncStep>> = _onboardingSyncSteps.asStateFlow()
    
    // Command Palettes
private val _commandPaletteOpen = MutableStateFlow(false)
    val commandPaletteOpen: StateFlow<Boolean> = _commandPaletteOpen.asStateFlow()

    fun openCommandPalette() { _commandPaletteOpen.value = true }
    fun closeCommandPalette() { _commandPaletteOpen.value = false }

    // Screens whose header search icon reveals their in-page search instead of the palette.
    val localSearchScreens: Set<Screen> = setOf(
        Screen.TRANSPORT,
        Screen.FFCS_PLANNER,
        Screen.FREE_CLASSROOMS,
        Screen.CURRICULUM,
        Screen.FACULTY_INFO
    )

    private val _localSearchTick = MutableStateFlow(0)
    val localSearchTick: StateFlow<Int> = _localSearchTick.asStateFlow()

    /** Header search icon tapped on a local-search screen: bump the tick so the active screen reveals its search. */
    fun requestLocalSearch() { _localSearchTick.value++ }

    // Cached Data
    private val _attendance = MutableStateFlow<AttendanceRes?>(null)
    val attendance: StateFlow<AttendanceRes?> = _attendance.asStateFlow()

    private val _currentLiveClass = MutableStateFlow<CourseAttendanceInfo?>(null)
    val currentLiveClass: StateFlow<CourseAttendanceInfo?> = _currentLiveClass.asStateFlow()
    
    private val _liveClassTick = MutableStateFlow(0)
    val liveClassTick: StateFlow<Int> = _liveClassTick.asStateFlow()

    private val _allSemesterAttendance = MutableStateFlow<Map<String, AttendanceRes?>>(emptyMap())
    val allSemesterAttendance: StateFlow<Map<String, AttendanceRes?>> = _allSemesterAttendance.asStateFlow()

    private val _timetable = MutableStateFlow<TimetableRes?>(null)
    val timetable: StateFlow<TimetableRes?> = _timetable.asStateFlow()

    private val _marks = MutableStateFlow<MarksRes?>(null)
    val marks: StateFlow<MarksRes?> = _marks.asStateFlow()

    private val settings = Settings()
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private val _moodleData = MutableStateFlow<MoodleRes?>(null)
    val moodleData: StateFlow<MoodleRes?> = _moodleData.asStateFlow()

    init {
        // Load persisted settings
        _cgpaHidden.value = SettingsManager.getBoolean(SettingsManager.KEY_CGPA_HIDDEN, false)
        _attendanceDisplayMode.value = AttendanceDisplayMode.fromString(SettingsManager.getString(SettingsManager.KEY_ATTENDANCE_MODE, "percentage"))
        _showAttendanceInStats.value = SettingsManager.getBoolean(SettingsManager.KEY_SHOW_ATTENDANCE_IN_STATS, false)
        _isBusSubscriber.value = SettingsManager.getBoolean(SettingsManager.KEY_BUS_SUBSCRIBER, false)
        
        val savedOrder = SettingsManager.getString(SettingsManager.KEY_STATS_CARDS_ORDER, "")
        if (savedOrder.isNotEmpty()) {
            _statsCardsOrder.value = savedOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val savedEnabled = SettingsManager.getString(SettingsManager.KEY_ENABLED_STATS_CARDS, "")
        if (savedEnabled.isNotEmpty()) {
            _enabledStatsCards.value = savedEnabled.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else {
            val showAtt = SettingsManager.getBoolean(SettingsManager.KEY_SHOW_ATTENDANCE_IN_STATS, false)
            _enabledStatsCards.value = if (showAtt) setOf("attendance", "cgpa", "credits", "od") else setOf("cgpa", "credits", "od")
        }
        _gpaGoal.value = SettingsManager.getString(SettingsManager.KEY_GPA_GOAL, "9.0")
        
        _updateDialogDismissedVersion.value = SettingsManager.getString(SettingsManager.KEY_UPDATE_DISMISSED_VERSION, "")
        _latestReleaseNotes.value = SettingsManager.getString(SettingsManager.KEY_LATEST_RELEASE_NOTES, "")

        val savedTheme = SettingsManager.getString(SettingsManager.KEY_APP_THEME, "")
        if (savedTheme.isNotEmpty()) {
            try { _theme.value = AppTheme.valueOf(savedTheme) } catch (_: Exception) {}
        }
        val savedAccent = SettingsManager.getString(SettingsManager.KEY_APP_ACCENT, "")
        if (savedAccent.isNotEmpty()) {
            try { _accent.value = AccentTheme.valueOf(savedAccent) } catch (_: Exception) {}
        }
        val savedCustomAccent = SettingsManager.getString(SettingsManager.KEY_CUSTOM_ACCENT, "")
        if (savedCustomAccent.isNotEmpty()) {
            parseHexColor(savedCustomAccent)?.let { _customAccentColor.value = it }
        }
        val savedPalette = SettingsManager.getString(SettingsManager.KEY_CUSTOM_PALETTE, "")
        if (savedPalette.isNotEmpty()) {
            try { _customPalette.value = jsonFormat.decodeFromString<CustomPalette>(savedPalette) } catch (_: Exception) {}
        }
        val savedScale = SettingsManager.getString(SettingsManager.KEY_UI_SCALE, "")
        if (savedScale.isNotEmpty()) {
            savedScale.toFloatOrNull()?.let { _uiScale.value = it }
        }

        _hapticEnabled.value = SettingsManager.getBoolean(SettingsManager.KEY_HAPTIC_ENABLED, true)
        _animationsEnabled.value = SettingsManager.getBoolean(SettingsManager.KEY_ANIMATIONS_ENABLED, true)
        _heroColorEnabled.value = SettingsManager.getBoolean(SettingsManager.KEY_HERO_COLOR_ENABLED, true)

        val savedNav = SettingsManager.getString(SettingsManager.KEY_NAVBAR_ITEMS, "")
        if (savedNav.isNotEmpty()) {
            val tabs = savedNav.split(",").mapNotNull { name ->
                try { Screen.valueOf(name) } catch (e: Exception) { null }
            }
            if (tabs.isNotEmpty()) {
                _pinnedNavTabs.value = tabs.take(4)
            }
        }
    }

    /**
     * Load cached data from local storage.
     * Must be called from an [androidx.compose.runtime.LaunchedEffect] in App() — NOT from init,
     * because many referenced StateFlow properties are declared after the init block.
     */
    fun loadFromCache() {
        loadCachedData()
    }

    /**
     * Wire SyncEngine module states into backward-compatible [isLoading], [error], [syncStatus] flows.
     * Must be called from a [androidx.compose.runtime.LaunchedEffect] in App() — NOT from init.
     */
    suspend fun observeSyncEngine() {
        SyncEngine.moduleStates.collect { states ->
            _isLoading.value = states.any { (_, s) -> s.status == SyncStatus.LOADING }
            val errors = states.filter { (_, s) ->
                s.status == SyncStatus.ERROR && s.error != null && s.error != "NO_LIB_CREDS"
            }
            _error.value = errors.entries.joinToString("\n") { "${it.key.displayName}: ${it.value.error}" }.ifEmpty { null }
            val active = states.filter { (_, s) -> s.status == SyncStatus.LOADING }
            _syncStatus.value = if (active.isNotEmpty()) {
                "Syncing: ${active.keys.joinToString(", ") { it.displayName }}"
            } else null
        }
    }

    private fun loadCachedString(key: String): String? {
        val cached = settings.getString(key, "")
        return if (cached.isNotBlank()) cached else null
    }

    private inline fun <reified T> loadCachedData(key: String, state: MutableStateFlow<T?>) {
        val cached = settings.getString(key, "")
        if (cached.isNotBlank()) {
            try {
                state.value = jsonFormat.decodeFromString<T>(cached)
            } catch (e: Exception) { println("AmazeCC: AppState loadCachedData — ${e.message}") }
        }
    }

    private fun loadCachedData() {
        loadCachedData<AttendanceRes>(SettingsManager.CACHE_ATTENDANCE, _attendance)
        loadCachedData<TimetableRes>(SettingsManager.CACHE_TIMETABLE, _timetable)
        loadCachedData<MarksRes>(SettingsManager.CACHE_MARKS, _marks)
        loadCachedData<AllGradesRes>(SettingsManager.CACHE_GRADES, _allGrades)
        mergeSemestersFromAllGrades()
        loadCachedData<HostelDetails>(SettingsManager.CACHE_HOSTEL_DETAILS, _hostelDetails)
        loadCachedData<MessMenuRes>(SettingsManager.CACHE_MESS_MENU, _messMenu)
        loadCachedData<LaundryRes>(SettingsManager.CACHE_LAUNDRY, _laundrySchedule)
        loadCachedData<ArrearResponse>(SettingsManager.CACHE_HOSTEL_COUNSELLING, _hostelCounselling)
        loadCachedData<ExamScheduleRes>(SettingsManager.CACHE_EXAM_SCHEDULE, _examSchedule)
        loadCachedData<CalendarRes>(SettingsManager.CACHE_CALENDAR, _calendar)
        loadCachedData<CalendarsListRes>(SettingsManager.CACHE_CALENDARS_LIST, _calendarsList)
        loadCachedData<QcmViewRes>(SettingsManager.CACHE_QCM_VIEW, _qcmView)
        loadCachedData<CurriculumRes>(SettingsManager.CACHE_CURRICULUM, _curriculum)
        loadCachedData<PaymentsRes>(SettingsManager.CACHE_PAYMENTS, _payments)
        loadCachedData<LibraryRes>(SettingsManager.CACHE_LIBRARY, _library)
        loadCachedData<TransportDataRes>(SettingsManager.CACHE_TRANSPORT_DATA, _transportData)
        loadCachedData<BusesRes>(SettingsManager.CACHE_BUSES, _buses)
        loadCachedData<LMSRes>(SettingsManager.CACHE_LMS, _lms)
        loadCachedData<EventHubRes>(SettingsManager.CACHE_EVENTS, _events)
        loadCachedData<ClubsRes>(SettingsManager.CACHE_CLUBS, _clubs)
        UserStore.loadFromCache()
        UserStore.merge(
            IdentityExtractor.fromVtopPhoto(SettingsManager.getString(SettingsManager.CACHE_VTOP_PHOTO)),
            IdentitySource.VTOP_PHOTO
        )
        reconcileExamSeatAlerts()
        _ffcsRegistration.value = try {
            settings.getString(SettingsManager.CACHE_FFCS_REG_INFO, "").let { raw ->
                if (raw.isBlank()) null
                else jsonFormat.decodeFromString(FfcsRegistrationInfo.serializer(), raw)
            }
        } catch (e: Exception) {
            null
        }
        loadCachedData<CabShareUser>(SettingsManager.CACHE_CAB_USER, _cabShareUser)
        loadCachedData<CircularsRes>(SettingsManager.CACHE_CIRCULARS, _circulars)

        // Auto-sync Moodle & Library in background if credentials are saved
        if (_moodleData.value == null && SettingsManager.getMoodleCredentials() != null) {
            syncMoodle()
        }
        if ((_library.value == null || _libraryLoginRequired.value) && SettingsManager.getLibraryCredentials() != null) {
            syncLibrary()
        }
        // Load all semesters attendance & marks cache
        try {
            val cachedAtt = settings.getString(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, "")
            if (cachedAtt.isNotBlank()) _allSemesterAttendance.value = jsonFormat.decodeFromString(cachedAtt)
        } catch (e: Exception) { println("AmazeCC: AppState loadCachedData allSemesterAttendance — ${e.message}") }
        try {
            val cachedMarks = settings.getString(SettingsManager.CACHE_ALL_SEMESTER_MARKS, "")
            if (cachedMarks.isNotBlank()) _allSemesterMarks.value = jsonFormat.decodeFromString(cachedMarks)
        } catch (e: Exception) { println("AmazeCC: AppState loadCachedData allSemesterMarks — ${e.message}") }
        try {
            val cachedExams = settings.getString(SettingsManager.CACHE_ALL_SEMESTER_EXAMS, "")
            if (cachedExams.isNotBlank()) _allSemesterExams.value = jsonFormat.decodeFromString(cachedExams)
        } catch (e: Exception) { println("AmazeCC: AppState loadCachedData allSemesterExams — ${e.message}") }
        // Also load moodle
        val cachedMoodle = settings.getString(SettingsManager.CACHE_MOODLE, "")
        if (cachedMoodle.isNotBlank()) {
            try {
                _moodleData.value = jsonFormat.decodeFromString<MoodleRes>(cachedMoodle)
            } catch (e: Exception) { println("AmazeCC: AppState loadCachedData moodle — ${e.message}") }
        }
        // Module states are left IDLE until an actual sync writes SUCCESS/ERROR
        loadTasks()
    }

    private inline fun <reified T> cacheData(key: String, value: T) {
        try {
            settings[key] = jsonFormat.encodeToString(value)
        } catch (e: Exception) { println("AmazeCC: AppState cacheData — ${e.message}") }
    }

    // Credentials (incl. passwords) live in the encrypted UserStore cache; nothing
    // identity-related is persisted in the plain per-endpoint caches anymore.

    fun dismissExamSeatAlerts() {
        _pendingExamSeatAlerts.value = emptyList()
    }

    // Alert once per newly added exam venue/seat entry. Entries that disappear are
    // forgotten, so a returning entry alerts again.
    private fun reconcileExamSeatAlerts() {
        val creds = UserStore.identity.value.credentials
        val current = creds
            .filter { it.venueDate.isNotBlank() || it.seatLocation.isNotBlank() }
            .associateBy { it.account.lowercase() }
        val seen = SettingsManager.getExamSeatAlerted()
        val fresh = current.filterKeys { it !in seen }
        if (fresh.isNotEmpty()) {
            _pendingExamSeatAlerts.value = fresh.values.toList()
            SettingsManager.setExamSeatAlerted(current.keys)
        } else if (seen != current.keys) {
            SettingsManager.setExamSeatAlerted(current.keys)
        }
    }

    fun dismissFfcsAlert() {
        _pendingFfcsAlert.value = null
    }

    // Parses the FFCS registration slot from /registration-schedule keyValuePairs
    // (falls back to the header/value table the endpoint also returns).
    private fun parseFfcsRegistration(res: RegistrationScheduleRes): FfcsRegistrationInfo? {
        val pairs = mutableMapOf<String, String>()
        res.keyValuePairs?.forEach { (k, v) ->
            val norm = k.lowercase().filter { it.isLetterOrDigit() }
            val value = (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
            if (norm in REG_FIELD_NAMES && value.isNotBlank()) pairs[norm] = value
        }
        if (pairs.isEmpty()) {
            val table = res.tables.orEmpty().firstOrNull { it is kotlinx.serialization.json.JsonObject }
                as? kotlinx.serialization.json.JsonObject
            if (table != null) {
                val headers = (table["headers"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                if (headers.size >= 2) {
                    val labelKey = headers[0]
                    val valueKey = headers[1]
                    (table["rows"] as? kotlinx.serialization.json.JsonArray).orEmpty().forEach { row ->
                        val obj = row as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val label = (obj[labelKey] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@forEach
                        val value = (obj[valueKey] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@forEach
                        val norm = label.lowercase().filter { it.isLetterOrDigit() }
                        if (norm in REG_FIELD_NAMES && value.isNotBlank()) pairs[norm] = value
                    }
                }
            }
        }
        val date = pairs["date"] ?: ""
        if (date.isBlank()) return null
        return FfcsRegistrationInfo(
            userName = pairs["username"] ?: "",
            date = date,
            fromTime = pairs["fromtime"] ?: "",
            toTime = pairs["totime"] ?: ""
        )
    }

    // Tracked on change + persisted so a returned/changed slot re-alerts; also re-arms reminders.
    private fun updateFfcsRegistration(info: FfcsRegistrationInfo?) {
        val prev = _ffcsRegistration.value
        _ffcsRegistration.value = info
        try {
            if (info != null) {
                settings[SettingsManager.CACHE_FFCS_REG_INFO] =
                    jsonFormat.encodeToString(FfcsRegistrationInfo.serializer(), info)
            } else {
                settings.remove(SettingsManager.CACHE_FFCS_REG_INFO)
            }
        } catch (e: Exception) { println("AmazeCC: updateFfcsRegistration — ${e.message}") }
        if (info != null && info != prev) {
            _pendingFfcsAlert.value = info
        }
        if (info != null) {
            scope.launch {
                com.amazecc.app.shared.utils.NotificationsUtils.scheduleRegistrationReminders(info)
            }
        }
    }

    private fun applyAllGrades(res: AllGradesRes) {
        _allGrades.value = res
        mergeSemestersFromAllGrades()
    }

    private fun mergeSemestersFromAllGrades() {
        val grades = _allGrades.value?.grades ?: return
        val ids = (grades.keys.filter { it != "curriculum" && it != "effectiveGrades" } + _selectedSemester.value).distinct()
        if (ids.isEmpty()) return
        val merged = ids.associateWith { fallbackSemesterMap[it] ?: deriveSemesterName(it) }
        if (merged.keys.containsAll(_semesterMap.value.keys) && _semesterMap.value.keys.containsAll(merged.keys)) return
        _semesterMap.value = merged
    }

    fun deriveSemesterName(semId: String): String {
        val match = Regex("^CH(\\d{4})(\\d{2})$").find(semId) ?: return semId
        val year = match.groupValues[1]
        val suffix = match.groupValues[2]
        val nextShort = (year.substring(2).toInt() + 1).toString().padStart(2, '0')
        return when (suffix) {
            "01" -> "Fall Semester $year-$nextShort"
            "05" -> "Winter Semester $year-$nextShort"
            "07" -> "Summer Semester $year-$nextShort"
            else -> semId
        }
    }

    // ── Save Offline: persists all currently-loaded in-memory data to cache ──
    fun saveOffline() {
        SyncEngine.resetLogs()
        var saved = 0
        if (_attendance.value != null) { cacheData(SettingsManager.CACHE_ATTENDANCE, _attendance.value); saved++ }
        if (_timetable.value != null) { cacheData(SettingsManager.CACHE_TIMETABLE, _timetable.value); saved++ }
        if (_marks.value != null) { cacheData(SettingsManager.CACHE_MARKS, _marks.value); saved++ }
        if (_allGrades.value != null) { cacheData(SettingsManager.CACHE_GRADES, _allGrades.value); saved++ }
        if (_curriculum.value != null) { cacheData(SettingsManager.CACHE_CURRICULUM, _curriculum.value); saved++ }
        if (_hostelDetails.value != null) { cacheData(SettingsManager.CACHE_HOSTEL_DETAILS, _hostelDetails.value); saved++ }
        if (_messMenu.value != null) { cacheData(SettingsManager.CACHE_MESS_MENU, _messMenu.value); saved++ }
        if (_laundrySchedule.value != null) { cacheData(SettingsManager.CACHE_LAUNDRY, _laundrySchedule.value); saved++ }
        if (_hostelCounselling.value != null) { cacheData(SettingsManager.CACHE_HOSTEL_COUNSELLING, _hostelCounselling.value); saved++ }
        if (_examSchedule.value != null) { cacheData(SettingsManager.CACHE_EXAM_SCHEDULE, _examSchedule.value); saved++ }
        if (_calendar.value != null) { cacheData(SettingsManager.CACHE_CALENDAR, _calendar.value); saved++ }
        if (_calendarsList.value != null) { cacheData(SettingsManager.CACHE_CALENDARS_LIST, _calendarsList.value); saved++ }
        if (_qcmView.value != null) { cacheData(SettingsManager.CACHE_QCM_VIEW, _qcmView.value); saved++ }
        if (_payments.value != null) { cacheData(SettingsManager.CACHE_PAYMENTS, _payments.value); saved++ }
        if (_library.value != null) { cacheData(SettingsManager.CACHE_LIBRARY, _library.value); saved++ }
        if (_libraryLoginRequired.value) { /* skip — no data to cache */ }
        if (_transportData.value != null) { cacheData(SettingsManager.CACHE_TRANSPORT_DATA, _transportData.value); saved++ }
        if (_buses.value != null) { cacheData(SettingsManager.CACHE_BUSES, _buses.value); saved++ }
        if (_lms.value != null) { cacheData(SettingsManager.CACHE_LMS, _lms.value); saved++ }
        if (_events.value != null) { cacheData(SettingsManager.CACHE_EVENTS, _events.value); saved++ }
        if (_clubs.value != null) { cacheData(SettingsManager.CACHE_CLUBS, _clubs.value); saved++ }
        if (_cabShareUser.value != null) { cacheData(SettingsManager.CACHE_CAB_USER, _cabShareUser.value); saved++ }
        if (_allSemesterAttendance.value.isNotEmpty()) { cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value); saved++ }
        if (_allSemesterMarks.value.isNotEmpty()) { cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value); saved++ }
        if (_allSemesterExams.value.isNotEmpty()) { cacheData(SettingsManager.CACHE_ALL_SEMESTER_EXAMS, _allSemesterExams.value); saved++ }
        SyncEngine.addLog(SyncModule.ATTENDANCE, "Saved $saved modules offline", SyncStatus.SUCCESS)
    }

    fun restoreSession(): Boolean {
        val cookies = loadCachedString(SettingsManager.SESSION_COOKIES) ?: return false
        val csrf = loadCachedString(SettingsManager.SESSION_CSRF) ?: return false
        val authorizedID = loadCachedString(SettingsManager.SESSION_AUTHORIZED_ID) ?: return false
        val clubToken = loadCachedString(SettingsManager.SESSION_CLUB_TOKEN)
        SessionManager.saveSession(cookies, csrf, authorizedID, clubToken)
        AmazeClient.setUseMockData(false)
        return true
    }

    fun enterDemoMode(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            val loaded = DemoData.load()
            if (!loaded) {
                onResult(false, "Demo data could not be loaded")
                return@launch
            }
            AmazeClient.setUseMockData(true)
            SessionManager.saveInMemorySession(
                cookies = "vtop_session_cookie=demo; csrf_token=demo",
                csrf = "demo",
                authorizedID = "DEMO",
                clubToken = null
            )
            navigateTo(if (SettingsManager.isOnboardingComplete()) Screen.HOME else Screen.ONBOARDING)
            onResult(true, "Demo mode enabled")
        }
    }

    private val _allGrades = MutableStateFlow<AllGradesRes?>(null)
    val allGrades: StateFlow<AllGradesRes?> = _allGrades.asStateFlow()

    private val _hostelDetails = MutableStateFlow<HostelDetails?>(null)
    val hostelDetails: StateFlow<HostelDetails?> = _hostelDetails.asStateFlow()

    private val _messMenu = MutableStateFlow<MessMenuRes?>(null)
    val messMenu: StateFlow<MessMenuRes?> = _messMenu.asStateFlow()

    private val _laundrySchedule = MutableStateFlow<LaundryRes?>(null)
    val laundrySchedule: StateFlow<LaundryRes?> = _laundrySchedule.asStateFlow()

    private val _hostelCounselling = MutableStateFlow<ArrearResponse?>(null)
    val hostelCounselling: StateFlow<ArrearResponse?> = _hostelCounselling.asStateFlow()

    private val _examSchedule = MutableStateFlow<ExamScheduleRes?>(null)
    val examSchedule: StateFlow<ExamScheduleRes?> = _examSchedule.asStateFlow()
    private val _allSemesterExams = MutableStateFlow<Map<String, ExamScheduleRes?>>(emptyMap())
    val allSemesterExams: StateFlow<Map<String, ExamScheduleRes?>> = _allSemesterExams.asStateFlow()
    private val _selectedExamSemester = MutableStateFlow("CH20262701")
    val selectedExamSemester: StateFlow<String> = _selectedExamSemester.asStateFlow()

    private val _calendar = MutableStateFlow<CalendarRes?>(null)
    val calendar: StateFlow<CalendarRes?> = _calendar.asStateFlow()

    private val _calendarsList = MutableStateFlow<CalendarsListRes?>(null)
    val calendarsList: StateFlow<CalendarsListRes?> = _calendarsList.asStateFlow()

    private val _qcmView = MutableStateFlow<QcmViewRes?>(null)
    val qcmView: StateFlow<QcmViewRes?> = _qcmView.asStateFlow()

    private val _curriculum = MutableStateFlow<CurriculumRes?>(null)
    val curriculum: StateFlow<CurriculumRes?> = _curriculum.asStateFlow()

    private val _payments = MutableStateFlow<PaymentsRes?>(null)
    val payments: StateFlow<PaymentsRes?> = _payments.asStateFlow()

    private val _library = MutableStateFlow<LibraryRes?>(null)
    val library: StateFlow<LibraryRes?> = _library.asStateFlow()

    private val _libraryLoginRequired = MutableStateFlow(false)
    val libraryLoginRequired: StateFlow<Boolean> = _libraryLoginRequired.asStateFlow()

    private val _transportData = MutableStateFlow<TransportDataRes?>(null)
    val transportData: StateFlow<TransportDataRes?> = _transportData.asStateFlow()

    private val _buses = MutableStateFlow<BusesRes?>(null)
    val buses: StateFlow<BusesRes?> = _buses.asStateFlow()

    private val _lms = MutableStateFlow<LMSRes?>(null)
    val lms: StateFlow<LMSRes?> = _lms.asStateFlow()

    private val _events = MutableStateFlow<EventHubRes?>(null)
    val events: StateFlow<EventHubRes?> = _events.asStateFlow()

    private val _registeredEvents = MutableStateFlow<EventHubRegisteredEventsRes?>(null)
    val registeredEvents: StateFlow<EventHubRegisteredEventsRes?> = _registeredEvents.asStateFlow()

    private val _clubs = MutableStateFlow<ClubsRes?>(null)
    val clubs: StateFlow<ClubsRes?> = _clubs.asStateFlow()

    private val _allSemesterMarks = MutableStateFlow<Map<String, MarksRes>>(emptyMap())
    val allSemesterMarks: StateFlow<Map<String, MarksRes>> = _allSemesterMarks.asStateFlow()

    private val _pastSemestersSynced = MutableStateFlow(SettingsManager.getBoolean(SettingsManager.PAST_SEMESTER_SYNCED, false))
    val pastSemestersSynced: StateFlow<Boolean> = _pastSemestersSynced.asStateFlow()

    // Selected course for detail view
    private val _selectedCourseCode = MutableStateFlow<String?>(null)
    val selectedCourseCode: StateFlow<String?> = _selectedCourseCode.asStateFlow()

    private val _selectedCourseSemester = MutableStateFlow<String?>(null)
    val selectedCourseSemester: StateFlow<String?> = _selectedCourseSemester.asStateFlow()

    fun openCourseDetail(courseCode: String, semesterId: String? = null) {
        _selectedCourseCode.value = courseCode
        _selectedCourseSemester.value = semesterId
        navigateTo(Screen.COURSE_DETAIL)
    }

    fun openCourseAttendance(courseCode: String) {
        _selectedCourseCode.value = courseCode
        navigateTo(Screen.COURSE_ATTENDANCE)
    }

    private val _selectedClubId = MutableStateFlow<String?>(null)
    val selectedClubId: StateFlow<String?> = _selectedClubId.asStateFlow()

    fun openClubDetail(clubId: String) {
        _selectedClubId.value = clubId
        navigateTo(Screen.CLUB_DETAIL)
    }

    private val _clubHubInitialTab = MutableStateFlow("Directory")
    val clubHubInitialTab: StateFlow<String> = _clubHubInitialTab.asStateFlow()

    fun openClubHub(initialTab: String = "Directory") {
        _clubHubInitialTab.value = initialTab
        navigateTo(Screen.CLUB_HUB)
    }

    // ── Search deep-link targets (consumed once by the target screen) ──

    /** Settings sub-section name to open (SettingsSubScreen.name) */
    private val _settingsSectionTarget = MutableStateFlow<String?>(null)
    val settingsSectionTarget: StateFlow<String?> = _settingsSectionTarget.asStateFlow()

    /** Bus route id to preselect / expand on Transport */
    private val _transportRouteTarget = MutableStateFlow<String?>(null)
    val transportRouteTarget: StateFlow<String?> = _transportRouteTarget.asStateFlow()

    /** Room query to prefill the Free Classrooms search */
    private val _freeRoomTarget = MutableStateFlow<String?>(null)
    val freeRoomTarget: StateFlow<String?> = _freeRoomTarget.asStateFlow()

    /** Faculty deep-link: school id then employee id */
    private val _facultySchoolTarget = MutableStateFlow<String?>(null)
    val facultySchoolTarget: StateFlow<String?> = _facultySchoolTarget.asStateFlow()
    private val _facultyEmployeeTarget = MutableStateFlow<String?>(null)
    val facultyEmployeeTarget: StateFlow<String?> = _facultyEmployeeTarget.asStateFlow()

    /** Course code to preselect / prefill on FFCS Planner and Curriculum */
    private val _ffcsCourseTarget = MutableStateFlow<String?>(null)
    val ffcsCourseTarget: StateFlow<String?> = _ffcsCourseTarget.asStateFlow()
    private val _curriculumCourseTarget = MutableStateFlow<String?>(null)
    val curriculumCourseTarget: StateFlow<String?> = _curriculumCourseTarget.asStateFlow()

    /** Course code to auto-open on QBank */
    private val _qbankCourseTarget = MutableStateFlow<String?>(null)
    val qbankCourseTarget: StateFlow<String?> = _qbankCourseTarget.asStateFlow()

    /** Koha book biblionumber for detail view */
    private val _kohaBookTarget = MutableStateFlow<String?>(null)
    val kohaBookTarget: StateFlow<String?> = _kohaBookTarget.asStateFlow()

    private fun clearTargets() {
        _settingsSectionTarget.value = null
        _transportRouteTarget.value = null
        _freeRoomTarget.value = null
        _facultySchoolTarget.value = null
        _facultyEmployeeTarget.value = null
        _ffcsCourseTarget.value = null
        _curriculumCourseTarget.value = null
        _qbankCourseTarget.value = null
        _kohaBookTarget.value = null
    }

    fun openSettingsSection(sectionName: String) {
        clearTargets()
        _settingsSectionTarget.value = sectionName
        navigateTo(Screen.SETTINGS)
    }

    fun consumeSettingsSectionTarget() {
        _settingsSectionTarget.value = null
    }

    fun openTransportRoute(routeId: String) {
        clearTargets()
        _transportRouteTarget.value = routeId
        navigateTo(Screen.TRANSPORT)
    }

    fun openFreeRoom(roomQuery: String) {
        clearTargets()
        _freeRoomTarget.value = roomQuery
        navigateTo(Screen.FREE_CLASSROOMS)
    }

    fun openFaculty(schoolId: String, employeeId: String) {
        clearTargets()
        _facultySchoolTarget.value = schoolId
        _facultyEmployeeTarget.value = employeeId
        navigateTo(Screen.FACULTY_INFO)
    }

    fun openFfcsCourse(courseCode: String) {
        clearTargets()
        _ffcsCourseTarget.value = courseCode
        navigateTo(Screen.FFCS_PLANNER)
    }

    fun openCurriculumCourse(courseCode: String) {
        clearTargets()
        _curriculumCourseTarget.value = courseCode
        navigateTo(Screen.CURRICULUM)
    }

    fun openQBankCourse(courseCode: String) {
        clearTargets()
        _qbankCourseTarget.value = courseCode
        navigateTo(Screen.QBANK)
    }

    fun openKohaBookDetail(biblionumber: String) {
        clearTargets()
        _kohaBookTarget.value = biblionumber
        navigateTo(Screen.LIBRARIES)
    }

    fun consumeKohaBookTarget() {
        _kohaBookTarget.value = null
    }

    fun consumeQBankCourseTarget() {
        _qbankCourseTarget.value = null
    }

    // Attendance initial view (Timetable / Predictor / Calendar)
    private val _attendanceInitialView = MutableStateFlow("Timetable")
    val attendanceInitialView: StateFlow<String> = _attendanceInitialView.asStateFlow()

    fun openAttendanceView(view: String = "Timetable") {
        _attendanceInitialView.value = view
        navigateTo(Screen.ATTENDANCE)
    }

    fun resetAttendanceView() {
        _attendanceInitialView.value = "Timetable"
    }

    // Cab Share state
    private val _cabLoading = MutableStateFlow(false)
    val cabLoading: StateFlow<Boolean> = _cabLoading.asStateFlow()

    private val _cabShareUser = MutableStateFlow<CabShareUser?>(null)
    val cabShareUser: StateFlow<CabShareUser?> = _cabShareUser.asStateFlow()

    private val _cabShareAuthLoading = MutableStateFlow(false)
    val cabShareAuthLoading: StateFlow<Boolean> = _cabShareAuthLoading.asStateFlow()

    private val _cabHubs = MutableStateFlow<List<CabShareHub>>(emptyList())
    val cabHubs: StateFlow<List<CabShareHub>> = _cabHubs.asStateFlow()

    // Circulars state
    private val _circulars = MutableStateFlow<CircularsRes?>(null)
    val circulars: StateFlow<CircularsRes?> = _circulars.asStateFlow()

    // Tasks state
    private val _tasks = MutableStateFlow<List<HomeworkTask>>(emptyList())
    val tasks: StateFlow<List<HomeworkTask>> = _tasks.asStateFlow()


    private val tasksJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val taskListSerializer = ListSerializer(HomeworkTask.serializer())

    private fun loadTasks() {
        val raw = SettingsManager.getString(SettingsManager.CACHE_TASKS, "[]")
        _tasks.value = try {
            tasksJson.decodeFromString(taskListSerializer, raw)
        } catch (_: Exception) { emptyList() }
    }

    private fun saveTasks() {
        val raw = tasksJson.encodeToString(taskListSerializer, _tasks.value)
        SettingsManager.setString(SettingsManager.CACHE_TASKS, raw)
    }

    fun addTask(task: HomeworkTask) {
        _tasks.value = _tasks.value + task
        saveTasks()
    }

    /** Merges imported backup tasks into the current list (imported wins on id conflict). */
    fun applyImportedTasks(imported: List<HomeworkTask>) {
        val importedIds = imported.map { it.id }.toSet()
        val merged = imported + _tasks.value.filter { it.id !in importedIds }
        _tasks.value = merged
        saveTasks()
    }

    fun updateTask(id: String, transform: (HomeworkTask) -> HomeworkTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        saveTasks()
    }

    fun deleteTask(id: String) {
        _tasks.value = _tasks.value.filter { it.id != id }
        saveTasks()
    }

    fun toggleTaskCompleted(id: String) {
        _tasks.value = _tasks.value.map { if (it.id == id) it.copy(completed = !it.completed) else it }
        saveTasks()
    }

    fun toggleSubtaskCompleted(taskId: String, subtaskId: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val updatedSubtasks = task.subtasks.map { sub ->
                    if (sub.id == subtaskId) sub.copy(completed = !sub.completed) else sub
                }
                val allDone = updatedSubtasks.isNotEmpty() && updatedSubtasks.all { it.completed }
                task.copy(subtasks = updatedSubtasks, completed = if (allDone) true else task.completed)
            } else task
        }
        saveTasks()
    }

    fun addFocusTime(taskId: String, additionalMinutes: Int) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(actualMinutesSpent = task.actualMinutesSpent + additionalMinutes)
            } else task
        }
        saveTasks()
    }

    val todayTasks: List<HomeworkTask>
        get() {
            val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            return _tasks.value.filter { it.dueDate == today && !it.completed }
        }

    fun navigateTo(screen: Screen) {
        if (screen == Screen.MORE) {
            _isAppLibraryOpen.value = true
            return
        }
        _isAppLibraryOpen.value = false
        headerOverride.value = null
        _headerOverrideOwner.value = null
        if (_currentScreen.value == screen) return
        if (screen in rootScreens) {
            backstack.clear()
            _currentScreen.value = screen
            return
        }
        backstack.removeAll { it == screen }
        if (backstack.lastOrNull() != _currentScreen.value) {
            backstack.add(_currentScreen.value)
        }
        _currentScreen.value = screen
    }

    fun canNavigateBack(): Boolean = backstack.isNotEmpty()

    fun navigateBackTo(screen: Screen) {
        if (backstack.isNotEmpty() && backstack.last() == screen) {
            backstack.removeAt(backstack.size - 1)
        }
        _currentScreen.value = screen
    }

    private val rootScreens = setOf(Screen.HOME, Screen.LOGIN, Screen.ONBOARDING, Screen.SPLASH)

    fun switchTopLevel(screen: Screen) {
        if (_currentScreen.value != screen) {
            backstack.clear()
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (backstack.isNotEmpty()) {
            _currentScreen.value = backstack.removeAt(backstack.size - 1)
            return true
        }
        return false
    }

    private var pendingSemesterSwitch: String? = null

    fun selectSemester(semesterId: String) {
        _selectedSemester.value = semesterId
        _selectedExamSemester.value = semesterId
        _examSchedule.value = _allSemesterExams.value[semesterId]
        // Refresh semester-specific data (chains after any in-flight sweep)
        if (SessionManager.isLoggedIn) {
            loadSemesterData(semesterId)
        }
    }

    fun loadSemesterData(semesterId: String) {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) {
            pendingSemesterSwitch = semesterId
            return
        }
        val sweepModules = listOf(
            SyncEngine.moduleOf("Attendance and CGPA"), SyncEngine.moduleOf("Timetable"),
            SyncEngine.moduleOf("Calendar")
        ).filterNotNull().filter { SyncEngine.isModuleEnabled(it) }.toSet()
        launchSweep(sweepModules) {
            _error.value = null
            _syncStatus.value = "Syncing semester data..."
            val results = supervisorScope {
                listOf(
                    async {
                        syncModule(
                            name = "Attendance and CGPA",
                            fetch = { AmazeClient.getAcademicData(semesterId) },
                            isSuccess = { it.attendance.error == null && it.marks?.error == null },
                            errorMessage = { it.attendance.error ?: it.marks?.error },
                            update = {
                                _attendance.value = it.attendance
                                _allSemesterAttendance.value = _allSemesterAttendance.value + (semesterId to it.attendance)
                                cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                                it.marks?.let { marks ->
                                    _marks.value = marks
                                    _allSemesterMarks.value = _allSemesterMarks.value + (semesterId to marks)
                                    cacheData(SettingsManager.CACHE_MARKS, marks)
                                }
                            }
                        )
                    },
                    async {
                        syncModule(
                            name = "Timetable",
                            fetch = { AmazeClient.getTimetable(semesterId) },
                            isSuccess = { it.error == null },
                            errorMessage = { it.error },
                            update = {
                                _timetable.value = it
                                cacheData(SettingsManager.CACHE_TIMETABLE, it)
                            }
                        )
                    },
                    async {
                        syncModule(
                            name = "Calendar",
                            fetch = { AmazeClient.getCalendars(semesterId) },
                            isSuccess = { it.success },
                            errorMessage = { it.message },
                            update = {
                                _calendarsList.value = it
                                cacheData(SettingsManager.CACHE_CALENDARS_LIST, it)
                            }
                        )
                    }
                ).awaitAll()
            }
            updateSyncSummary(results)
        }
    }

    private var sweepJob: Job? = null

    private fun launchSweep(modules: Set<SyncModule>, stageProfile: String? = null, block: suspend () -> Unit) {
        if (sweepJob?.isActive == true) return
        sweepJob = scope.launch {
            _isLoading.value = true
            _isSyncing.value = true
            SyncEngine.beginSweep(modules)
            try {
                if (stageProfile != null) {
                    SyncEngine.withStageProfile(stageProfile) { block() }
                } else {
                    block()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                SyncEngine.endSweep()
                _isLoading.value = false
                _isSyncing.value = false
            }
        }
        SyncEngine.registerJob(sweepJob!!)
    }

    fun cancelSync() {
        SyncEngine.cancelAll()
        sweepJob = null
        pendingSemesterSwitch = null
        _isLoading.value = false
        _isSyncing.value = false
        _syncMessage.value = null
        _syncStatus.value = "Sync cancelled"
        SyncEngine.setShowSyncDialog(false)
    }

    fun loadAllData(scheduledFor: String? = null) {
        if (_isLoading.value) return
        val stageProfile = when (scheduledFor) {
            SyncScheduler.FULL_KIND -> SyncScheduler.fullProfileId()
            SyncScheduler.LIGHT_KIND -> SyncScheduler.lightProfileId()
            else -> null
        }
        val sweepModules = listOf(
            "Attendance", "All Semesters Attendance", "Timetable", "Grade history", "Curriculum",
            "Hostel details", "Exam schedule", "All Semesters Exam Schedule", "Academic calendar",
            "Calendars list", "Payments", "Library", "Transport Data", "Buses", "LMS",
            "Registered Events", "Clubs", "QCM View", "Student Profile", "Profile Images", "Credentials",
            "Bank Information", "Dayboarder Info", "EPT Schedule", "Registration Schedule",
            "APAAR ID", "Circulars", "Moodle Assignments"
        ).mapNotNull { SyncEngine.moduleOf(it) }.filter { SyncEngine.isModuleEnabled(it) }.toSet()

        launchSweep(sweepModules, stageProfile = stageProfile) {
            _error.value = null
            _syncMessage.value = "Refreshing VTOP session..."
            _syncStatus.value = "Refreshing VTOP session..."
            notificationService.showLoadingNotification("AmazeCC Sync", "Refreshing VTOP session...")
            try {
                // ── Refresh VTOP session before syncing (cookies expire every 10 min) ──
                val creds = SettingsManager.getCredentials()
                if (creds != null) {
                    try {
                        val loginRes = AmazeClient.login(creds.first, creds.second)
                        if (loginRes.success && loginRes.cookies != null && loginRes.csrf != null && loginRes.authorizedID != null) {
                            SessionManager.saveSession(
                                cookies = loginRes.cookies,
                                csrf = loginRes.csrf,
                                authorizedID = loginRes.authorizedID,
                                clubToken = loginRes.clubToken
                            )
                            SettingsManager.setString(SettingsManager.SESSION_COOKIES, loginRes.cookies)
                            SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
                            SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
                            loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                            UserStore.merge(IdentityExtractor.fromSession(loginRes.authorizedID), IdentitySource.SESSION)
                        }
                    } catch (e: Exception) { println("AmazeCC: AppState loadAllData sessionRefresh — ${e.message}") }
                }

                _syncMessage.value = "Syncing academic and campus data..."
                _syncStatus.value = "Syncing academic and campus data..."
                val sem = _selectedSemester.value

                val results = supervisorScope {
                    val syncResults = listOf(
                        async {
                            syncModule(
                                name = "Attendance",
                                fetch = { AmazeClient.getAcademicData(sem) },
                                isSuccess = { it.attendance.error == null && it.marks?.error == null },
                                errorMessage = { it.attendance.error ?: it.marks?.error },
                                update = {
                                    _attendance.value = it.attendance
                                    cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                                    val attMap = _allSemesterAttendance.value.toMutableMap()
                                    attMap[sem] = it.attendance
                                    _allSemesterAttendance.value = attMap
                                    it.marks?.let { marks ->
                                        _marks.value = marks
                                        cacheData(SettingsManager.CACHE_MARKS, marks)
                                        val marksMap = _allSemesterMarks.value.toMutableMap()
                                        marksMap[sem] = marks
                                        _allSemesterMarks.value = marksMap
                                    }
                                }
                            )
                        },
                        async {
                            if (!SyncEngine.isModuleEnabled(SyncModule.ALL_SEMESTER_ATTENDANCE)) {
                                SyncModuleResult("All Semesters Attendance", true)
                            } else if (_pastSemestersSynced.value) {
                                SyncEngine.markModuleSuccess(SyncModule.ALL_SEMESTER_ATTENDANCE)
                                SyncModuleResult("All Semesters Attendance", true)
                            } else {
                                var failed = false
                                val gradeSemIds = _allGrades.value?.grades?.keys
                                    ?.filter { it != "curriculum" && it != "effectiveGrades" && it != sem }
                                    ?: emptyList()
                                val allSemIds = (gradeSemIds + semesterIDs).distinct().filter { it != sem }
                                val newAttMap = _allSemesterAttendance.value.toMutableMap()
                                val newMarksMap = _allSemesterMarks.value.toMutableMap()
                                for (semId in allSemIds) {
                                    try {
                                        val res = AmazeClient.getAcademicData(semId)
                                        if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                            newAttMap[semId] = res.attendance
                                        }
                                        if (res.marks?.marks?.isNotEmpty() == true) {
                                            newMarksMap[semId] = res.marks
                                        }
                                    } catch (_: Exception) { failed = true }
                                }
                                _allSemesterAttendance.value = newAttMap
                                _allSemesterMarks.value = newMarksMap
                                cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value)
                                cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value)
                                if (failed) SyncEngine.markModuleError(SyncModule.ALL_SEMESTER_ATTENDANCE, "Some past semesters failed")
                                else SyncEngine.markModuleSuccess(SyncModule.ALL_SEMESTER_ATTENDANCE)
                                SyncModuleResult("All Semesters Attendance", !failed)
                            }
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _timetable.value = it
                                    cacheData(SettingsManager.CACHE_TIMETABLE, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Grade history",
                                fetch = { AmazeClient.getAllGrades() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    applyAllGrades(it)
                                    cacheData(SettingsManager.CACHE_GRADES, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Curriculum",
                                fetch = { AmazeClient.getCurriculum(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _curriculum.value = it
                                    cacheData(SettingsManager.CACHE_CURRICULUM, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Hostel details",
                                fetch = { AmazeClient.getHostelDetails() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _hostelDetails.value = it
                                    cacheData(SettingsManager.CACHE_HOSTEL_DETAILS, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Exam schedule",
                                fetch = { AmazeClient.getExamSchedule(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _examSchedule.value = it
                                    cacheData(SettingsManager.CACHE_EXAM_SCHEDULE, it)
                                    val examMap = _allSemesterExams.value.toMutableMap()
                                    examMap[sem] = it
                                    _allSemesterExams.value = examMap
                                }
                            )
                        },
                        async {
                            if (!SyncEngine.isModuleEnabled(SyncModule.EXAM_SCHEDULE)) {
                                SyncModuleResult("All Semesters Exam Schedule", true)
                            } else {
                                var examFailed = false
                                for (semId in semesterIDs) {
                                    if (semId == sem) continue
                                    try {
                                        val res = AmazeClient.getExamSchedule(semesterId = semId)
                                        if (res.error == null && res.schedule.isNotEmpty()) {
                                            val examCurrent = _allSemesterExams.value.toMutableMap()
                                            examCurrent[semId] = res
                                            _allSemesterExams.value = examCurrent
                                        }
                                    } catch (_: Exception) { examFailed = true }
                                }
                                cacheData(SettingsManager.CACHE_ALL_SEMESTER_EXAMS, _allSemesterExams.value)
                                if (examFailed) SyncEngine.markModuleError(SyncModule.EXAM_SCHEDULE, "Some past semester schedules failed")
                                else SyncEngine.markModuleSuccess(SyncModule.EXAM_SCHEDULE)
                                SyncModuleResult("All Semesters Exam Schedule", !examFailed)
                            }
                        },
                        async {
                            syncModule(
                                name = "Academic calendar",
                                fetch = { AmazeClient.getCalendar(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _calendar.value = it
                                    cacheData(SettingsManager.CACHE_CALENDAR, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Calendars list",
                                fetch = { AmazeClient.getCalendars(semesterId = sem) },
                                isSuccess = { it.success },
                                errorMessage = { it.message },
                                update = {
                                    _calendarsList.value = it
                                    cacheData(SettingsManager.CACHE_CALENDARS_LIST, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Payments",
                                fetch = { AmazeClient.getPayments() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _payments.value = it
                                    cacheData(SettingsManager.CACHE_PAYMENTS, it)
                                }
                            )
                        },
                        async {
                            val libRes = syncModule(
                                name = "Library",
                                fetch = { AmazeClient.getLibrary() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _library.value = it
                                    _libraryLoginRequired.value = false
                                    cacheData(SettingsManager.CACHE_LIBRARY, it)
                                }
                            )
                            if (!libRes.success && libRes.message == "NO_LIB_CREDS") {
                                _libraryLoginRequired.value = true
                            }
                            libRes
                        },
                        async {
                            syncModule(
                                name = "Transport Data",
                                fetch = { AmazeClient.getTransportData() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transportData.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT_DATA, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Buses",
                                fetch = { AmazeClient.getBuses() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _buses.value = it
                                    cacheData(SettingsManager.CACHE_BUSES, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "LMS",
                                fetch = { AmazeClient.getLMSAssignments() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _lms.value = it
                                    cacheData(SettingsManager.CACHE_LMS, it)
                                }
                            )
                        },
                        async {
                            val moodleCreds = SettingsManager.getMoodleCredentials()
                            if (moodleCreds == null) {
                                SyncEngine.resetModule(SyncModule.MOODLE)
                                SyncModuleResult("Moodle Assignments", true)
                            } else {
                                syncModule(
                                    name = "Moodle Assignments",
                                    fetch = { AmazeClient.fetchMoodleData(moodleCreds.first, moodleCreds.second) },
                                    isSuccess = { it.success },
                                    errorMessage = { it.error ?: it.message },
                                    update = {
                                        _moodleData.value = it
                                        cacheData(SettingsManager.CACHE_MOODLE, it)
                                    }
                                )
                            }
                        },
                        async {
                            val clubToken = SessionManager.clubToken.value
                            if (!clubToken.isNullOrBlank()) {
                                syncModule(
                                    name = "Registered Events",
                                    fetch = { AmazeClient.getEventsProfile() },
                                    isSuccess = { it.error == null },
                                    errorMessage = { it.error },
                                    update = {
                                        _registeredEvents.value = it
                                    }
                                )
                            } else {
                                if (SyncEngine.isModuleEnabled(SyncModule.EVENTS)) SyncEngine.resetModule(SyncModule.EVENTS)
                                SyncModuleResult("Registered Events", true)
                            }
                        },
                        async {
                            syncModule(
                                name = "Clubs",
                                fetch = { AmazeClient.getClubsDetails() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _clubs.value = it
                                    cacheData(SettingsManager.CACHE_CLUBS, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "QCM View",
                                fetch = { AmazeClient.getQcmView() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _qcmView.value = it
                                    cacheData(SettingsManager.CACHE_QCM_VIEW, it)
                                }
                            )
                        },
                        async {
                            val profResults = supervisorScope {
                                    listOf(
                                        async {
                                            syncModule("Student Profile", { AmazeClient.getStudentProfile() }, { it.success && it.data != null }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromStudentProfile(it), IdentitySource.STUDENT)
                                            }
                                        },
                                        async {
                                            syncModule("Profile Images", { AmazeClient.getProfileImages() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromProfileImages(it), IdentitySource.PROFILE_IMAGES)
                                            }
                                        },
                                        async {
                                            syncModule("Credentials", { AmazeClient.getCredentials() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromCredentials(it), IdentitySource.CREDENTIALS)
                                                reconcileExamSeatAlerts()
                                            }
                                        },
                                        async {
                                            syncModule("Bank Information", { AmazeClient.getBankInfo() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromBankInfo(it), IdentitySource.BANK)
                                            }
                                        },
                                        async {
                                            syncModule("Dayboarder Info", { AmazeClient.getDayboarderInfo() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromDayboarder(it), IdentitySource.RECORDS)
                                            }
                                        },
                                        async {
                                            syncModule("EPT Schedule", { AmazeClient.getEptSchedule() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromEptSchedule(it), IdentitySource.RECORDS)
                                            }
                                        },
                                        async {
                                            syncModule("Registration Schedule", { AmazeClient.getRegistrationSchedule() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromRegistrationSchedule(it), IdentitySource.RECORDS)
                                                updateFfcsRegistration(parseFfcsRegistration(it))
                                            }
                                        },
                                        async {
                                            syncModule("University Day", { AmazeClient.getUniversityDay() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromUniversityDay(it), IdentitySource.RECORDS)
                                            }
                                        },
                                        async {
                                            syncModule("APAAR ID", { AmazeClient.getApaarId() }, { it.success }, { it.error }) {
                                                UserStore.merge(IdentityExtractor.fromApaarId(it), IdentitySource.APAAR)
                                            }
                                        }
                                    ).awaitAll()
                                }
                                profResults.firstOrNull { !it.success } ?: SyncModuleResult("Student Profile", true)
                        },
                        async {
                            syncModule(
                                name = "Circulars",
                                fetch = { AmazeClient.getCirculars() },
                                isSuccess = { it.success },
                                errorMessage = { it.error ?: it.message },
                                update = {
                                    _circulars.value = it
                                    cacheData(SettingsManager.CACHE_CIRCULARS, it)
                                }
                            )
                        }
                    ).awaitAll()

                    // ── Gap-fill: fetch attendance/marks for any semester in allGrades that we missed ──
                    val grades = _allGrades.value
                    if (grades?.grades != null) {
                        val missingSemIds = grades.grades.keys
                            .filter { it != "curriculum" && it != "effectiveGrades" && it != sem }
                            .filter { it !in _allSemesterMarks.value.keys }
                        for (semId in missingSemIds) {
                            try {
                                val res = AmazeClient.getAcademicData(semId)
                                if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                    val attCurrent = _allSemesterAttendance.value.toMutableMap()
                                    attCurrent[semId] = res.attendance
                                    _allSemesterAttendance.value = attCurrent
                                }
                                if (res.marks?.marks?.isNotEmpty() == true) {
                                    val marksCurrent = _allSemesterMarks.value.toMutableMap()
                                    marksCurrent[semId] = res.marks
                                    _allSemesterMarks.value = marksCurrent
                                }
                            } catch (_: Exception) { }
                        }
                        if (missingSemIds.isNotEmpty()) {
                            cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value)
                            cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value)
                        }
                        SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                        _pastSemestersSynced.value = true
                    }
                    syncResults
                }
                updateSyncSummary(results)
                pendingSemesterSwitch?.let {
                    val semSwitch = it
                    pendingSemesterSwitch = null
                    _syncMessage.value = "Refreshing data for new semester..."
                    loadSemesterData(semSwitch)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                _syncMessage.value = null
                if (scheduledFor != null) {
                    SyncScheduler.markSynced()
                    SyncScheduler.advanceAndArm(scheduledFor)
                }
                if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    notificationService.showLoadingNotification("AmazeCC Sync", "Sync completed")
                    scheduleReminders()
                }
            }
        }
    }

    private fun scheduleReminders() {
        val attendanceItems = _attendance.value?.attendance
        val assignments = _lms.value?.assignments
        val moodleAssignments = _moodleData.value?.data?.filter { !it.done }?.map { a ->
            LMSAssignment("moodle_${a.hashCode()}", a.courseCode, a.taskTitle, "", a.due, "Pending")
        } ?: emptyList()
        val allAssignments = (assignments ?: emptyList()) + moodleAssignments
        val selectedExams = _allSemesterExams.value[_selectedExamSemester.value]?.schedule?.values?.flatten().orEmpty()
        val exams = if (selectedExams.isNotEmpty()) selectedExams
            else _allSemesterExams.value.values.mapNotNull { it }.flatMap { it.schedule.values.flatten() }
        com.amazecc.app.shared.utils.NotificationsUtils.scheduleAll(
            attendance = com.amazecc.app.shared.utils.NotificationsUtils.buildAttendanceMaps(attendanceItems),
            slotMap = com.amazecc.app.shared.utils.NotificationsUtils.typedSlotMap(),
            assignments = allAssignments,
            tasks = _tasks.value,
            exams = if (exams.isEmpty()) null else exams,
            registration = _ffcsRegistration.value
        )
        com.amazecc.app.shared.utils.pushWidgetUpdates()
    }

    suspend fun sendTestNotification(): String {
        return if (requestNotificationPermissions()) {
            testLocalNotification()
            "Test notification sent! Check your phone in 5 seconds."
        } else {
            "Notification permission not granted. Enable a toggle above first."
        }
    }

    fun rescheduleNotifications() {
        scope.launch { scheduleReminders() }
    }

    // ── Targeted refreshes for specific screens ──

    fun refreshCurrentSemester() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.ATTENDANCE, SyncModule.TIMETABLE)) {
            _syncStatus.value = "Syncing current semester..."
            val sem = _selectedSemester.value
            val results = supervisorScope {
                    listOf(
                        async {
                            syncModule(
                                name = "Attendance",
                                fetch = { AmazeClient.getAcademicData(sem) },
                                isSuccess = { it.attendance.error == null },
                                errorMessage = { it.attendance.error },
                                update = {
                                    _attendance.value = it.attendance
                                    cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                                    it.marks?.let { m ->
                                        _marks.value = m
                                        cacheData(SettingsManager.CACHE_MARKS, m)
                                    }
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _timetable.value = it
                                    cacheData(SettingsManager.CACHE_TIMETABLE, it)
                                }
                            )
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
        }
    }

    fun refreshAllAcademic() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.ATTENDANCE, SyncModule.ALL_SEMESTER_ATTENDANCE, SyncModule.TIMETABLE, SyncModule.GRADES)) {
            _syncStatus.value = "Syncing academic data..."
            val sem = _selectedSemester.value
            val results = supervisorScope {
                    val syncResults = listOf(
                        async {
                            syncModule(
                                name = "Attendance",
                                fetch = { AmazeClient.getAcademicData(sem) },
                                isSuccess = { it.attendance.error == null },
                                errorMessage = { it.attendance.error },
                                update = {
                                    _attendance.value = it.attendance
                                    cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                                    it.marks?.let { m ->
                                        _marks.value = m
                                        cacheData(SettingsManager.CACHE_MARKS, m)
                                    }
                                }
                            )
                        },
                        async {
                            if (_pastSemestersSynced.value) {
                                SyncModuleResult("All Semesters", true)
                            } else {
                                var failed = false
                                val gradeSemIds = _allGrades.value?.grades?.keys
                                    ?.filter { it != "curriculum" && it != "effectiveGrades" && it != sem }
                                    ?: emptyList()
                                val allSemIds = (gradeSemIds + semesterIDs).distinct().filter { it != sem }
                                for (semId in allSemIds) {
                                    try {
                                        val res = AmazeClient.getAcademicData(semId)
                                        if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                            val attCurrent = _allSemesterAttendance.value.toMutableMap()
                                            attCurrent[semId] = res.attendance
                                            _allSemesterAttendance.value = attCurrent
                                        }
                                        if (res.marks?.marks?.isNotEmpty() == true) {
                                            val marksCurrent = _allSemesterMarks.value.toMutableMap()
                                            marksCurrent[semId] = res.marks
                                            _allSemesterMarks.value = marksCurrent
                                        }
                                    } catch (_: Exception) { failed = true }
                                }
                                cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value)
                                cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value)
                                SyncModuleResult("All Semesters", !failed)
                            }
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _timetable.value = it
                                    cacheData(SettingsManager.CACHE_TIMETABLE, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Grade history",
                                fetch = { AmazeClient.getAllGrades() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    applyAllGrades(it)
                                    cacheData(SettingsManager.CACHE_GRADES, it)
                                }
                            )
                        }
                    ).awaitAll()

                    // ── Gap-fill for refreshAllAcademic ──
                    val grades = _allGrades.value
                    if (grades?.grades != null) {
                        val missingSemIds = grades.grades.keys
                            .filter { it != "curriculum" && it != "effectiveGrades" && it != sem }
                            .filter { it !in _allSemesterMarks.value.keys }
                        for (semId in missingSemIds) {
                            try {
                                val res = AmazeClient.getAcademicData(semId)
                                if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                    val attCurrent = _allSemesterAttendance.value.toMutableMap()
                                    attCurrent[semId] = res.attendance
                                    _allSemesterAttendance.value = attCurrent
                                }
                                if (res.marks?.marks?.isNotEmpty() == true) {
                                    val marksCurrent = _allSemesterMarks.value.toMutableMap()
                                    marksCurrent[semId] = res.marks
                                    _allSemesterMarks.value = marksCurrent
                                }
                            } catch (_: Exception) { }
                        }
                        if (missingSemIds.isNotEmpty()) {
                            cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value)
                            cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value)
                        }
                        SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                        _pastSemestersSynced.value = true
                    }
                    syncResults
                }
                updateSyncSummary(results)
        }
    }

    fun refreshPastSemesters() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.ALL_SEMESTER_ATTENDANCE)) {
            _syncStatus.value = "Refreshing past semester data..."
            val sem = _selectedSemester.value
                val gradeSemIds = _allGrades.value?.grades?.keys
                    ?.filter { it != "curriculum" && it != "effectiveGrades" && it != sem }
                    ?: emptyList()
                val allSemIds = (gradeSemIds + semesterIDs).distinct().filter { it != sem }
                var failed = false
                for (semId in allSemIds) {
                    try {
                        val res = AmazeClient.getAcademicData(semId)
                        if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                            val attCurrent = _allSemesterAttendance.value.toMutableMap()
                            attCurrent[semId] = res.attendance
                            _allSemesterAttendance.value = attCurrent
                        }
                        if (res.marks?.marks?.isNotEmpty() == true) {
                            val marksCurrent = _allSemesterMarks.value.toMutableMap()
                            marksCurrent[semId] = res.marks
                            _allSemesterMarks.value = marksCurrent
                        }
                    } catch (_: Exception) { failed = true }
                }
                cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value)
                cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value)
                SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                _pastSemestersSynced.value = true
                updateSyncSummary(listOf(SyncModuleResult("All Semesters Attendance", !failed)))
        }
    }

    fun refreshPayments() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.PAYMENTS)) {
            _syncStatus.value = "Syncing payments..."
            val result = syncModule(
                name = "Payments",
                fetch = { AmazeClient.getPayments() },
                isSuccess = { it.error == null },
                errorMessage = { it.error },
                update = {
                    _payments.value = it
                    cacheData(SettingsManager.CACHE_PAYMENTS, it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    fun refreshHostel() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.HOSTEL_DETAILS)) {
            _syncStatus.value = "Syncing hostel..."
            val result = syncModule(
                name = "Hostel details",
                fetch = { AmazeClient.getHostelDetails() },
                isSuccess = { it.error == null },
                errorMessage = { it.error },
                update = {
                    _hostelDetails.value = it
                    cacheData(SettingsManager.CACHE_HOSTEL_DETAILS, it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    fun refreshMessMenu(gender: String?, messType: String?) {
        val cached = settings.getString(SettingsManager.CACHE_MESS_MENU, "")
        if (cached.isNotBlank()) {
            try { _messMenu.value = jsonFormat.decodeFromString<MessMenuRes>(cached) } catch (_: Exception) {}
        }
        scope.launch {
            val res = AmazeClient.getMessMenu(gender, messType)
            if (res.list.isNotEmpty()) {
                _messMenu.value = res
                SettingsManager.setString(SettingsManager.CACHE_MESS_MENU, jsonFormat.encodeToString(MessMenuRes.serializer(), res))
            }
        }
    }

    fun refreshLaundrySchedule(gender: String?, blockPrefix: String) {
        val cached = settings.getString(SettingsManager.CACHE_LAUNDRY, "")
        if (cached.isNotBlank()) {
            try { _laundrySchedule.value = jsonFormat.decodeFromString<LaundryRes>(cached) } catch (_: Exception) {}
        }
        scope.launch {
            val res = AmazeClient.getLaundrySchedule(gender, blockPrefix)
            if (res.list.isNotEmpty()) {
                _laundrySchedule.value = res
                SettingsManager.setString(SettingsManager.CACHE_LAUNDRY, jsonFormat.encodeToString(LaundryRes.serializer(), res))
            }
        }
    }

    fun refreshHostelCounselling() {
        val cached = settings.getString(SettingsManager.CACHE_HOSTEL_COUNSELLING, "")
        if (cached.isNotBlank()) {
            try { _hostelCounselling.value = jsonFormat.decodeFromString<ArrearResponse>(cached) } catch (_: Exception) {}
        }
        scope.launch {
            val res = AmazeClient.getHostelCounselling()
            if (res.success) {
                _hostelCounselling.value = res
                SettingsManager.setString(SettingsManager.CACHE_HOSTEL_COUNSELLING, jsonFormat.encodeToString(ArrearResponse.serializer(), res))
            }
        }
    }

    fun refreshCalendar() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.CALENDAR)) {
            _syncStatus.value = "Syncing calendar..."
            val result = syncModule(
                name = "Academic calendar",
                fetch = { AmazeClient.getCalendar(semesterId = _selectedSemester.value) },
                isSuccess = { it.error == null },
                errorMessage = { it.error },
                update = {
                    _calendar.value = it
                    cacheData(SettingsManager.CACHE_CALENDAR, it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    fun refreshCalendarsList() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.CALENDARS_LIST)) {
            _syncStatus.value = "Syncing calendars list..."
            val sem = _selectedSemester.value
            val res = syncModule(
                name = "Calendars list",
                fetch = { AmazeClient.getCalendars(semesterId = sem) },
                isSuccess = { it.success },
                errorMessage = { it.message },
                update = {
                    _calendarsList.value = it
                    cacheData(SettingsManager.CACHE_CALENDARS_LIST, it)
                }
            )
            updateSyncSummary(listOf(res))
        }
    }

    fun refreshQcmView() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.QCM_VIEW)) {
            _syncStatus.value = "Syncing QCM data..."
            val res = syncModule(
                name = "QCM View",
                fetch = { AmazeClient.getQcmView() },
                isSuccess = { it.success },
                errorMessage = { it.message },
                update = {
                    _qcmView.value = it
                    cacheData(SettingsManager.CACHE_QCM_VIEW, it)
                }
            )
            updateSyncSummary(listOf(res))
        }
    }

    fun refreshCurriculum() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.CURRICULUM)) {
            _syncStatus.value = "Syncing curriculum..."
            val result = syncModule(
                name = "Curriculum",
                fetch = { AmazeClient.getCurriculum(semesterId = _selectedSemester.value) },
                isSuccess = { it.error == null },
                errorMessage = { it.error },
                update = {
                    _curriculum.value = it
                    cacheData(SettingsManager.CACHE_CURRICULUM, it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    fun refreshGrades() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.GRADES)) {
            _syncStatus.value = "Syncing grades..."
            val result = syncModule(
                name = "Grade history",
                fetch = { AmazeClient.getAllGrades() },
                isSuccess = { it.error == null },
                errorMessage = { it.error },
                update = {
                    applyAllGrades(it)
                    cacheData(SettingsManager.CACHE_GRADES, it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    fun refreshProfile() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.STUDENT_PROFILE, SyncModule.PROFILE_IMAGES, SyncModule.BANK_INFO,
            SyncModule.DAYBOARDER, SyncModule.EPT_SCHEDULE, SyncModule.REGISTRATION_SCHEDULE, SyncModule.APAAR_ID)) {
            _syncStatus.value = "Syncing profile..."
            val results = supervisorScope {
                    listOf(
                        async {
                            syncModule("Student Profile", { AmazeClient.getStudentProfile() }, { it.success && it.data != null }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromStudentProfile(it), IdentitySource.STUDENT)
                            }
                        },
                        async {
                            syncModule("Profile Images", { AmazeClient.getProfileImages() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromProfileImages(it), IdentitySource.PROFILE_IMAGES)
                            }
                        },
                        async {
                            syncModule("Credentials", { AmazeClient.getCredentials() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromCredentials(it), IdentitySource.CREDENTIALS)
                                reconcileExamSeatAlerts()
                            }
                        },
                        async {
                            syncModule("Bank Information", { AmazeClient.getBankInfo() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromBankInfo(it), IdentitySource.BANK)
                            }
                        },
                        async {
                            syncModule("Dayboarder Info", { AmazeClient.getDayboarderInfo() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromDayboarder(it), IdentitySource.RECORDS)
                            }
                        },
                        async {
                            syncModule("EPT Schedule", { AmazeClient.getEptSchedule() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromEptSchedule(it), IdentitySource.RECORDS)
                            }
                        },
                        async {
                            syncModule("Registration Schedule", { AmazeClient.getRegistrationSchedule() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromRegistrationSchedule(it), IdentitySource.RECORDS)
                                updateFfcsRegistration(parseFfcsRegistration(it))
                            }
                        },
                        async {
                            syncModule("University Day", { AmazeClient.getUniversityDay() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromUniversityDay(it), IdentitySource.RECORDS)
                            }
                        },
                        async {
                            syncModule("APAAR ID", { AmazeClient.getApaarId() }, { it.success }, { it.error }) {
                                UserStore.merge(IdentityExtractor.fromApaarId(it), IdentitySource.APAAR)
                            }
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
        }
    }

    fun refreshLibrary() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.LIBRARY)) {
            _syncStatus.value = "Syncing library..."
            val result = syncModule(
                    name = "Library",
                    fetch = { AmazeClient.getLibrary() },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = {
                        _library.value = it
                        _libraryLoginRequired.value = false
                        cacheData(SettingsManager.CACHE_LIBRARY, it)
                    }
                )
                if (!result.success && result.message == "NO_LIB_CREDS") {
                    _libraryLoginRequired.value = true
                }
                updateSyncSummary(listOf(result))
        }
    }

    fun refreshTransport() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.TRANSPORT, SyncModule.BUSES)) {
            _syncStatus.value = "Syncing transport..."
            val results = supervisorScope {
                    listOf(
                        async {
                            syncModule(
                                name = "Transport Data",
                                fetch = { AmazeClient.getTransportData() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transportData.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT_DATA, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Buses",
                                fetch = { AmazeClient.getBuses() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _buses.value = it
                                    cacheData(SettingsManager.CACHE_BUSES, it)
                                }
                            )
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
        }
    }

    fun refreshLMS() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.LMS)) {
            _syncStatus.value = "Syncing LMS..."
            val result = syncModule(
                    name = "LMS",
                    fetch = { AmazeClient.getLMSAssignments() },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = {
                        _lms.value = it
                        cacheData(SettingsManager.CACHE_LMS, it)
                    }
                )
                updateSyncSummary(listOf(result))
        }
    }

    fun selectExamSemester(semesterId: String) {
        _selectedExamSemester.value = semesterId
        _examSchedule.value = _allSemesterExams.value[semesterId]
        if (_allSemesterExams.value[semesterId]?.schedule?.isEmpty() != false) {
            refreshExamSchedule()
        }
    }

    fun refreshExamSchedule() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.EXAM_SCHEDULE)) {
            _syncStatus.value = "Logging into VTOP & syncing exam schedule..."
            // Ensure fresh VTOP login session before fetching exam schedule
                val creds = SettingsManager.getCredentials()
                if (creds != null) {
                    try {
                        val loginRes = AmazeClient.login(creds.first, creds.second)
                        if (loginRes.success && loginRes.cookies != null && loginRes.csrf != null && loginRes.authorizedID != null) {
                            SessionManager.saveSession(
                                cookies = loginRes.cookies,
                                csrf = loginRes.csrf,
                                authorizedID = loginRes.authorizedID,
                                clubToken = loginRes.clubToken
                            )
                            SettingsManager.setString(SettingsManager.SESSION_COOKIES, loginRes.cookies)
                            SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
                            SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
                            loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                            UserStore.merge(IdentityExtractor.fromSession(loginRes.authorizedID), IdentitySource.SESSION)
                        }
                    } catch (e: Exception) { println("AmazeCC: refreshExamSchedule session refresh error — ${e.message}") }
                }

                val semId = _selectedExamSemester.value
                val result = syncModule(
                    name = "Exam schedule",
                    fetch = { AmazeClient.getExamSchedule(semesterId = semId) },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = {
                        _examSchedule.value = it
                        cacheData(SettingsManager.CACHE_EXAM_SCHEDULE, it)
                        val map = _allSemesterExams.value.toMutableMap()
                        map[semId] = it
                        _allSemesterExams.value = map
                    }
                )
                
                // Fetch other semesters in parallel if needed
                for (otherSemId in semesterIDs) {
                    if (otherSemId == semId) continue
                    try {
                        val res = AmazeClient.getExamSchedule(semesterId = otherSemId)
                        if (res.error == null && res.schedule.isNotEmpty()) {
                            val examCurrent = _allSemesterExams.value.toMutableMap()
                            examCurrent[otherSemId] = res
                            _allSemesterExams.value = examCurrent
                        }
                    } catch (_: Exception) {}
                }
                cacheData(SettingsManager.CACHE_ALL_SEMESTER_EXAMS, _allSemesterExams.value)
                updateSyncSummary(listOf(result))
        }
    }

    fun refreshCirculars() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.CIRCULARS)) {
            _syncStatus.value = "Syncing circulars..."
            val result = syncModule(
                    name = "Circulars",
                    fetch = { AmazeClient.getCirculars() },
                    isSuccess = { it.success },
                    errorMessage = { it.error ?: it.message },
                    update = {
                        _circulars.value = it
                        cacheData(SettingsManager.CACHE_CIRCULARS, it)
                    }
                )
                updateSyncSummary(listOf(result))
        }
    }


    private suspend fun <T> syncModule(
        name: String,
        fetch: suspend () -> T,
        isSuccess: (T) -> Boolean,
        errorMessage: (T) -> String?,
        update: (T) -> Unit
    ): SyncModuleResult {
        val module = SyncEngine.moduleOf(name)
        if (module != null && !SyncEngine.isModuleEnabled(module)) return SyncModuleResult(name, true)
        module?.let { SyncEngine.markModuleLoading(it) }
        return try {
            val result = fetch()
            if (isSuccess(result)) {
                update(result)
                module?.let {
                    SyncEngine.markModuleSuccess(it)
                    SyncEngine.addLog(it, "Synced", SyncStatus.SUCCESS)
                }
                SyncModuleResult(name, true)
            } else {
                val msg = errorMessage(result) ?: "Empty or failed response"
                module?.let {
                    if (msg != "NO_LIB_CREDS") {
                        SyncEngine.markModuleError(it, msg)
                        SyncEngine.addLog(it, msg, SyncStatus.ERROR)
                    } else {
                        SyncEngine.resetModule(it)
                    }
                }
                SyncModuleResult(name, false, msg)
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            module?.let {
                SyncEngine.markModuleError(it, msg)
                SyncEngine.addLog(it, msg, SyncStatus.ERROR)
            }
            SyncModuleResult(name, false, msg)
        }
    }

    private fun updateSyncSummary(results: List<SyncModuleResult>) {
        val failures = results.filterNot { it.success }
            .filter { it.message != "NO_LIB_CREDS" }
        val successCount = results.size - failures.size
        _syncStatus.value = "Synced $successCount/${results.size} modules"
        _error.value = failures
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "${it.name}: ${it.message}" }
    }

    fun dismissError() {
        _error.value = null
    }

    fun logout() {
        SettingsManager.remove(SettingsManager.KEY_USERNAME)
        SettingsManager.remove(SettingsManager.KEY_PASSWORD)
        SettingsManager.clearMoodleCredentials()
        SessionManager.clearSession()
        backstack.clear()
        _currentScreen.value = Screen.LOGIN
        
        // Clear caches
        _attendance.value = null
        UserStore.clear()
        _ffcsRegistration.value = null
        _pendingFfcsAlert.value = null
        _circulars.value = null
        _timetable.value = null
        _marks.value = null
        _allGrades.value = null
        _allSemesterAttendance.value = emptyMap()
        _allSemesterMarks.value = emptyMap()
        _allSemesterExams.value = emptyMap()
        _pastSemestersSynced.value = false
        _hostelDetails.value = null
        _messMenu.value = null
        _laundrySchedule.value = null
        _hostelCounselling.value = null
        _examSchedule.value = null
        _calendar.value = null
        _payments.value = null
        _library.value = null
        _transportData.value = null
        _buses.value = null
        _lms.value = null
        _events.value = null
        _registeredEvents.value = null
        _clubs.value = null
        
        _moodleData.value = null
        _curriculum.value = null
        _selectedSemester.value = "CH20262701"
        _selectedCourseCode.value = null
        _selectedCourseSemester.value = null
        clearTargets()
        _isLoading.value = false
        _cabShareUser.value = null
        _cabHubs.value = emptyList()
        _allSemesterMarks.value = emptyMap()
        _allSemesterAttendance.value = emptyMap()
        _allSemesterExams.value = emptyMap()
        _libraryLoginRequired.value = false
        _error.value = null
        _syncStatus.value = null

        // Clear persisted caches — wipe ALL data, caches, credentials, and preferences.
        // Both Settings instances share the same backing store, so clear both.
        SettingsManager.clearAll()
        settings.clear()

        // Reset preference flows to defaults so a fresh login starts clean
        _theme.value = AppTheme.SYSTEM
        _accent.value = AccentTheme.OCEAN
        _customAccentColor.value = AccentOcean
        _customPalette.value = CustomPalette()
        _uiScale.value = 1.0f
        _cgpaHidden.value = false
        _attendanceDisplayMode.value = AttendanceDisplayMode.PERCENTAGE
        _showAttendanceInStats.value = false
        _isBusSubscriber.value = false
        _customAttendanceTarget.value = null
        _hapticEnabled.value = true
        _animationsEnabled.value = true
        _heroColorEnabled.value = true
        _residentialStatus.value = "Hosteller"
        _statsCardsOrder.value = listOf("attendance", "cgpa", "credits", "od")
        _enabledStatsCards.value = setOf("attendance", "cgpa", "credits", "od")
        _gpaGoal.value = "9.0"
        _pinnedNavTabs.value = listOf(Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE)
        _updateDialogDismissedVersion.value = ""
        _latestReleaseNotes.value = ""
        _pendingExamSeatAlerts.value = emptyList()
        scope.launch { com.amazecc.app.shared.utils.clearPendingNotifications() }
    }

    fun updateAttendance(data: AttendanceRes?) {
        _attendance.value = data
    }

    fun updateMarks(data: MarksRes?) {
        _marks.value = data
    }

fun updateMoodleData(data: MoodleRes?) {
        _moodleData.value = data
        if (data != null) {
            try {
                settings[SettingsManager.CACHE_MOODLE] = jsonFormat.encodeToString(data)
            } catch (e: Exception) { println("AmazeCC: AppState updateMoodleData — ${e.message}") }
        } else {
            settings.remove(SettingsManager.CACHE_MOODLE)
        }
        scope.launch { scheduleReminders() }
    }

    fun getMoodleAssignmentsForCourse(courseCode: String): List<MoodleAssignment> {
        return _moodleData.value?.data?.filter { a ->
            a.courseCode.equals(courseCode, ignoreCase = true) ||
            a.name.contains(courseCode, ignoreCase = true)
        } ?: emptyList()
    }

    fun cabShareLogin(username: String, password: String, phoneNumber: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            _cabShareAuthLoading.value = true
            try {
                val res = AmazeClient.cabShareAuth(username, password, phoneNumber)
                if (res.success && res.user != null) {
                    _cabShareUser.value = res.user
                    cacheData(SettingsManager.CACHE_CAB_USER, res.user)
                    onResult(true, "Authenticated!")
                } else {
                    onResult(false, res.error ?: "Authentication failed")
                }
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}")
            }
            _cabShareAuthLoading.value = false
        }
    }

    fun cabShareLogout() {
        _cabShareUser.value = null
        settings.remove(SettingsManager.CACHE_CAB_USER)
    }

    fun fetchCabHubs() {
        scope.launch {
            try {
                val hubs = AmazeClient.getCabHubs()
                _cabHubs.value = hubs
            } catch (_: Exception) {
                _cabHubs.value = fallbackCabHubs
            }
        }
    }

    fun cabSearchTripsNew(fromHubId: Int?, toHubId: Int?, date: String, onResult: (List<CabShareTrip>) -> Unit = {}) {
        scope.launch {
            _cabLoading.value = true
            try {
                val res = AmazeClient.searchCabShareTrips(fromHubId, toHubId, date)
                onResult(res.trips)
            } catch (_: Exception) {
                onResult(emptyList())
            }
            _cabLoading.value = false
        }
    }

    fun cabCreateTripNew(
        fromHubId: Int, toHubId: Int, date: String, time: String,
        tolerance: Double, seats: Int, gender: String, notes: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            _cabLoading.value = true
            try {
                val res = AmazeClient.createCabShareTrip(
                    fromHubId, toHubId, date, time, tolerance, seats, gender, notes
                )
                if (res.success) {
                    onResult(true, "Trip created!")
                } else {
                    onResult(false, res.error ?: "Failed to create trip")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
            }
            _cabLoading.value = false
        }
    }

    fun cabRefreshMyTripsNew(onResult: (myTrips: List<CabShareTrip>, joinedTrips: List<CabShareTrip>) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val user = _cabShareUser.value
                if (user == null) {
                    onResult(emptyList(), emptyList())
                    return@launch
                }
                val res = AmazeClient.getMyCabShareTrips(user.reg_number)
                onResult(res.my_trips, res.joined_trips)
            } catch (_: Exception) {
                onResult(emptyList(), emptyList())
            }
        }
    }

    fun cabRequestJoinNew(tripId: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val user = _cabShareUser.value ?: run {
                    onResult(false, "Not authenticated")
                    return@launch
                }
                val res = AmazeClient.requestCabShareJoin(user.reg_number, tripId)
                onResult(res.success, if (res.success) "Request sent!" else (res.error ?: "Failed"))
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}")
            }
        }
    }

    fun cabHandleMatchAction(matchId: Long, action: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val user = _cabShareUser.value ?: run {
                    onResult(false, "Not authenticated")
                    return@launch
                }
                val res = AmazeClient.cabShareMatchAction(user.reg_number, matchId, action)
                onResult(res.success, res.message ?: "")
            } catch (e: Exception) {
                onResult(false, "Network error: ${e.message}")
            }
        }
    }

    fun syncEventsAndClubs() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.EVENTS, SyncModule.CLUBS)) {
            try {
                val eventsRes = AmazeClient.getEvents()
                if (eventsRes.error == null) {
                    _events.value = eventsRes
                    cacheData(SettingsManager.CACHE_EVENTS, eventsRes)
                    SyncEngine.markModuleSuccess(SyncModule.EVENTS)
                    SyncEngine.addLog(SyncModule.EVENTS, "Synced", SyncStatus.SUCCESS)
                } else {
                    SyncEngine.markModuleError(SyncModule.EVENTS, eventsRes.error!!)
                    SyncEngine.addLog(SyncModule.EVENTS, eventsRes.error!!, SyncStatus.ERROR)
                }
            } catch (e: Exception) {
                SyncEngine.markModuleError(SyncModule.EVENTS, e.message ?: "Network error")
                SyncEngine.addLog(SyncModule.EVENTS, e.message ?: "Network error", SyncStatus.ERROR)
            }
            try {
                val clubsRes = AmazeClient.getClubsDetails()
                if (clubsRes.error == null) {
                    _clubs.value = clubsRes
                    cacheData(SettingsManager.CACHE_CLUBS, clubsRes)
                    SyncEngine.markModuleSuccess(SyncModule.CLUBS)
                    SyncEngine.addLog(SyncModule.CLUBS, "Synced", SyncStatus.SUCCESS)
                } else {
                    SyncEngine.markModuleError(SyncModule.CLUBS, clubsRes.error!!)
                    SyncEngine.addLog(SyncModule.CLUBS, clubsRes.error!!, SyncStatus.ERROR)
                }
            } catch (e: Exception) {
                SyncEngine.markModuleError(SyncModule.CLUBS, e.message ?: "Network error")
                SyncEngine.addLog(SyncModule.CLUBS, e.message ?: "Network error", SyncStatus.ERROR)
            }
        }
    }

    private fun updateOnboardingStep(name: String, status: String) {
        _onboardingSyncSteps.value = _onboardingSyncSteps.value.map {
            if (it.name == name) it.copy(status = status) else it
        }
    }

    private suspend fun syncOnboardingAttendance(sem: String) {
        updateOnboardingStep("Attendance", "syncing")
        val res = syncModule(
            name = "Attendance",
            fetch = { AmazeClient.getAcademicData(sem) },
            isSuccess = { it.attendance.error == null },
            errorMessage = { it.attendance.error },
            update = {
                _attendance.value = it.attendance
                cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                it.marks?.let { m ->
                    _marks.value = m
                    cacheData(SettingsManager.CACHE_MARKS, m)
                }
            }
        )
        updateOnboardingStep("Attendance", if (res.success) "done" else "failed")
    }

    private suspend fun syncOnboardingTimetable(sem: String) {
        updateOnboardingStep("Timetable", "syncing")
        val res = syncModule(
            name = "Timetable",
            fetch = { AmazeClient.getTimetable(sem) },
            isSuccess = { it.error == null },
            errorMessage = { it.error },
            update = {
                _timetable.value = it
                cacheData(SettingsManager.CACHE_TIMETABLE, it)
            }
        )
        updateOnboardingStep("Timetable", if (res.success) "done" else "failed")
    }

    private suspend fun syncOnboardingGrades() {
        updateOnboardingStep("Grades", "syncing")
        val res = syncModule(
            name = "Grade history",
            fetch = { AmazeClient.getAllGrades() },
            isSuccess = { it.error == null },
            errorMessage = { it.error },
            update = {
                applyAllGrades(it)
                cacheData(SettingsManager.CACHE_GRADES, it)
            }
        )
        updateOnboardingStep("Grades", if (res.success) "done" else "failed")
    }

    private suspend fun syncOnboardingCurriculum(sem: String) {
        updateOnboardingStep("Curriculum", "syncing")
        val res = syncModule(
            name = "Curriculum",
            fetch = { AmazeClient.getCurriculum(semesterId = sem) },
            isSuccess = { it.error == null },
            errorMessage = { it.error },
            update = {
                _curriculum.value = it
                cacheData(SettingsManager.CACHE_CURRICULUM, it)
            }
        )
        updateOnboardingStep("Curriculum", if (res.success) "done" else "failed")
    }

    private suspend fun syncOnboardingHostel() {
        updateOnboardingStep("Hostel", "syncing")
        val res = syncModule(
            name = "Hostel details",
            fetch = { AmazeClient.getHostelDetails() },
            isSuccess = { it.error == null },
            errorMessage = { it.error },
            update = {
                _hostelDetails.value = it
                cacheData(SettingsManager.CACHE_HOSTEL_DETAILS, it)
            }
        )
        updateOnboardingStep("Hostel", if (res.success) "done" else "failed")
    }

    private suspend fun syncOnboardingPayments() {
        updateOnboardingStep("Payments", "syncing")
        val res = syncModule(
            name = "Payments",
            fetch = { AmazeClient.getPayments() },
            isSuccess = { it.error == null },
            errorMessage = { it.error },
            update = {
                _payments.value = it
                cacheData(SettingsManager.CACHE_PAYMENTS, it)
            }
        )
        updateOnboardingStep("Payments", if (res.success) "done" else "failed")
    }

    private suspend fun syncOnboardingEvents() {
        updateOnboardingStep("Events", "syncing")
        val res = syncModule(
            name = "Registered Events",
            fetch = { AmazeClient.getEvents() },
            isSuccess = { it.error == null },
            errorMessage = { it.error },
            update = {
                _events.value = it
                cacheData(SettingsManager.CACHE_EVENTS, it)
            }
        )
        updateOnboardingStep("Events", if (res.success) "done" else "failed")
    }

    fun startOnboardingSync() {
        launchSweep(
            setOf(
                SyncModule.ATTENDANCE, SyncModule.TIMETABLE, SyncModule.GRADES,
                SyncModule.CURRICULUM, SyncModule.HOSTEL_DETAILS, SyncModule.PAYMENTS, SyncModule.EVENTS
            )
        ) {
            SyncEngine.withStageProfile("full_sync") {
            val steps = listOf(
                "Session", "Attendance", "Timetable", "Grades",
                "Curriculum", "Hostel", "Payments", "Events"
            )
            _onboardingSyncSteps.value = steps.map { AppState.SyncStep(it, "pending") }

            // Session refresh
            updateOnboardingStep("Session", "syncing")
            try {
                val creds = SettingsManager.getCredentials()
                if (creds != null) {
                    val loginRes = AmazeClient.login(creds.first, creds.second)
                    if (loginRes.success && loginRes.cookies != null && loginRes.csrf != null && loginRes.authorizedID != null) {
                        SessionManager.saveSession(
                            cookies = loginRes.cookies, csrf = loginRes.csrf,
                            authorizedID = loginRes.authorizedID, clubToken = loginRes.clubToken
                        )
                        SettingsManager.setString(SettingsManager.SESSION_COOKIES, loginRes.cookies)
                        SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
                        SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
                        loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                            UserStore.merge(IdentityExtractor.fromSession(loginRes.authorizedID), IdentitySource.SESSION)
                    }
                }
                updateOnboardingStep("Session", "done")
            } catch (_: Exception) { updateOnboardingStep("Session", "failed") }

            val sem = _selectedSemester.value

            syncOnboardingAttendance(sem)
            syncOnboardingTimetable(sem)
            syncOnboardingGrades()
            syncOnboardingCurriculum(sem)
            syncOnboardingHostel()
            syncOnboardingPayments()
            syncOnboardingEvents()
            }
        }
    }

    // ── Scheduled sync automation ──

    /** Foreground catch-up: called on app launch / resume / alarm. */
    fun checkDueSync() {
        if (!SyncScheduler.isEnabled()) return
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        if (!SessionManager.isLoggedIn) return
        val now = kotlinx.datetime.Clock.System.now()
        val fullDue = SyncScheduler.getNextRun(SyncScheduler.FULL_KIND)?.let { it <= now } == true
        val lightDue = SyncScheduler.getNextRun(SyncScheduler.LIGHT_KIND)?.let { it <= now } == true
        if (fullDue) {
            runScheduledSync(SyncScheduler.FULL_KIND)
            if (lightDue) SyncScheduler.advanceAndArm(SyncScheduler.LIGHT_KIND)
        } else if (lightDue) {
            runScheduledSync(SyncScheduler.LIGHT_KIND)
        }
    }

    /** Entry point for a fired alarm (receivers/platform alarm hooks). */
    fun runScheduledSync(kind: String = SyncScheduler.LIGHT_KIND, force: Boolean = false) {
        if (!SyncScheduler.isEnabled() && !force) return
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        if (!SessionManager.isLoggedIn) {
            // Nothing to fetch while logged out — rearm for the next occurrence.
            if (!force) SyncScheduler.advanceAndArm(kind)
            return
        }
        if (kind == SyncScheduler.FULL_KIND) {
            loadAllData(scheduledFor = SyncScheduler.FULL_KIND)
        } else {
            runLightReload()
        }
    }

    /** Light scheduled reload — the modules in the daily_reload profile. */
    private fun runLightReload() {
        val dailyModules = listOf(
            "Attendance", "Timetable", "Grade history", "Exam schedule", "Academic calendar",
            "Calendars list", "LMS", "Circulars", "Moodle Assignments"
        ).mapNotNull { SyncEngine.moduleOf(it) }
            .filter { SyncEngine.isModuleEnabled(it) }
            .toSet()
        launchSweep(dailyModules, stageProfile = SyncScheduler.lightProfileId()) {
            _syncMessage.value = "Running scheduled refresh..."
            _syncStatus.value = "Running scheduled refresh..."
            notificationService.showLoadingNotification("AmazeCC Sync", "Running scheduled refresh...")
            try {
                val creds = SettingsManager.getCredentials()
                if (creds != null) {
                    try {
                        val loginRes = AmazeClient.login(creds.first, creds.second)
                        if (loginRes.success && loginRes.cookies != null && loginRes.csrf != null && loginRes.authorizedID != null) {
                            SessionManager.saveSession(
                                cookies = loginRes.cookies, csrf = loginRes.csrf,
                                authorizedID = loginRes.authorizedID, clubToken = loginRes.clubToken
                            )
                            SettingsManager.setString(SettingsManager.SESSION_COOKIES, loginRes.cookies)
                            SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
                            SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
                            loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                            UserStore.merge(IdentityExtractor.fromSession(loginRes.authorizedID), IdentitySource.SESSION)
                        }
                    } catch (e: Exception) { println("AmazeCC: runLightReload sessionRefresh — ${e.message}") }
                }

                val sem = _selectedSemester.value
                val results = supervisorScope {
                    val syncResults = listOf(
                        async {
                            syncModule(
                                name = "Attendance",
                                fetch = { AmazeClient.getAcademicData(sem) },
                                isSuccess = { it.attendance.error == null && it.marks?.error == null },
                                errorMessage = { it.attendance.error ?: it.marks?.error },
                                update = {
                                    _attendance.value = it.attendance
                                    cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                                    val attMap = _allSemesterAttendance.value.toMutableMap()
                                    attMap[sem] = it.attendance
                                    _allSemesterAttendance.value = attMap
                                    it.marks?.let { marks ->
                                        _marks.value = marks
                                        cacheData(SettingsManager.CACHE_MARKS, marks)
                                        val marksMap = _allSemesterMarks.value.toMutableMap()
                                        marksMap[sem] = marks
                                        _allSemesterMarks.value = marksMap
                                    }
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _timetable.value = it
                                    cacheData(SettingsManager.CACHE_TIMETABLE, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Grade history",
                                fetch = { AmazeClient.getAllGrades() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    applyAllGrades(it)
                                    cacheData(SettingsManager.CACHE_GRADES, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Exam schedule",
                                fetch = { AmazeClient.getExamSchedule(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _examSchedule.value = it
                                    cacheData(SettingsManager.CACHE_EXAM_SCHEDULE, it)
                                    val examMap = _allSemesterExams.value.toMutableMap()
                                    examMap[sem] = it
                                    _allSemesterExams.value = examMap
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Academic calendar",
                                fetch = { AmazeClient.getCalendar(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _calendar.value = it
                                    cacheData(SettingsManager.CACHE_CALENDAR, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Calendars list",
                                fetch = { AmazeClient.getCalendars(semesterId = sem) },
                                isSuccess = { it.success },
                                errorMessage = { it.message },
                                update = {
                                    _calendarsList.value = it
                                    cacheData(SettingsManager.CACHE_CALENDARS_LIST, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "LMS",
                                fetch = { AmazeClient.getLMSAssignments() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _lms.value = it
                                    cacheData(SettingsManager.CACHE_LMS, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Circulars",
                                fetch = { AmazeClient.getCirculars() },
                                isSuccess = { it.success },
                                errorMessage = { it.error ?: it.message },
                                update = {
                                    _circulars.value = it
                                    cacheData(SettingsManager.CACHE_CIRCULARS, it)
                                }
                            )
                        },
                        async {
                            val moodleCreds = SettingsManager.getMoodleCredentials()
                            if (moodleCreds == null) {
                                SyncEngine.resetModule(SyncModule.MOODLE)
                                SyncModuleResult("Moodle Assignments", true)
                            } else {
                                syncModule(
                                    name = "Moodle Assignments",
                                    fetch = { AmazeClient.fetchMoodleData(moodleCreds.first, moodleCreds.second) },
                                    isSuccess = { it.success },
                                    errorMessage = { it.error ?: it.message },
                                    update = {
                                        _moodleData.value = it
                                        cacheData(SettingsManager.CACHE_MOODLE, it)
                                    }
                                )
                            }
                        }
                    ).awaitAll()
                    syncResults
                }
                updateSyncSummary(results)
            } finally {
                SyncScheduler.markSynced()
                SyncScheduler.advanceAndArm(SyncScheduler.LIGHT_KIND)
                if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    notificationService.showLoadingNotification("AmazeCC Sync", "Sync completed")
                    scheduleReminders()
                }
            }
        }
    }

    fun saveLibraryCredentials(username: String, password: String) {
        SettingsManager.saveLibraryCredentials(username, password)
        _libraryLoginRequired.value = false
        scope.launch {
            _isSyncing.value = true
            val res = AmazeClient.getLibrary(username, password)
            if (res.error == null) {
                _library.value = res
                cacheData(SettingsManager.CACHE_LIBRARY, res)
            } else if (res.error == "NO_LIB_CREDS") {
                _libraryLoginRequired.value = true
            }
            _isSyncing.value = false
        }
    }

    fun saveMoodleCredentials(username: String, password: String) {
        SettingsManager.saveMoodleCredentials(username, password)
        scope.launch {
            _isSyncing.value = true
            val res = AmazeClient.fetchMoodleData(username, password)
            if (res.success) {
                _moodleData.value = res
            }
            _isSyncing.value = false
        }
    }

    fun syncMoodle() {
        val creds = SettingsManager.getMoodleCredentials() ?: return
        saveMoodleCredentials(creds.first, creds.second)
    }

    fun syncLibrary() {
        val creds = SettingsManager.getLibraryCredentials() ?: return
        saveLibraryCredentials(creds.first, creds.second)
    }

    fun changeTheme(theme: AppTheme) {
        _theme.value = theme
        SettingsManager.setString(SettingsManager.KEY_APP_THEME, theme.name)
    }

    fun changeAccent(accent: AccentTheme) {
        _accent.value = accent
        SettingsManager.setString(SettingsManager.KEY_APP_ACCENT, accent.name)
    }

    fun setCustomAccent(color: Color) {
        _customAccentColor.value = color
        _accent.value = AccentTheme.CUSTOM
        SettingsManager.setString(SettingsManager.KEY_APP_ACCENT, AccentTheme.CUSTOM.name)
        SettingsManager.setString(SettingsManager.KEY_CUSTOM_ACCENT, color.toHexString())
    }

    fun setPaletteEnabled(enabled: Boolean) {
        _customPalette.value = _customPalette.value.withEnabled(enabled)
        persistCustomPalette()
    }

    fun setPaletteRole(mode: PaletteMode, role: PaletteRole, color: Color) {
        _customPalette.value = _customPalette.value.withRole(mode, role, color.toHexString())
        persistCustomPalette()
    }

    fun clearPaletteRole(mode: PaletteMode, role: PaletteRole) {
        _customPalette.value = _customPalette.value.clearRole(mode, role)
        persistCustomPalette()
    }

    fun resetCustomPalette() {
        _customPalette.value = _customPalette.value.resetAll()
        persistCustomPalette()
    }

    private fun persistCustomPalette() {
        try {
            SettingsManager.setString(SettingsManager.KEY_CUSTOM_PALETTE, jsonFormat.encodeToString(_customPalette.value))
        } catch (_: Exception) {}
    }

    fun changeUiScale(scale: Float) {
        _uiScale.value = scale
        SettingsManager.setString(SettingsManager.KEY_UI_SCALE, scale.toString())
    }

    fun setResidentialStatus(status: String) {
        _residentialStatus.value = status
        SettingsManager.setString(SettingsManager.RESIDENTIAL_STATUS, status)
    }

    fun setPinnedNavTabs(tabs: List<Screen>) {
        if (tabs.size <= 4) {
            _pinnedNavTabs.value = tabs
            SettingsManager.setString(SettingsManager.KEY_NAVBAR_ITEMS, tabs.joinToString(",") { it.name })
        }
    }

    fun setCgpaHidden(hidden: Boolean) {
        _cgpaHidden.value = hidden
        SettingsManager.setBoolean(SettingsManager.KEY_CGPA_HIDDEN, hidden)
    }

    fun setAttendanceDisplayMode(mode: AttendanceDisplayMode) {
        _attendanceDisplayMode.value = mode
        SettingsManager.setString(SettingsManager.KEY_ATTENDANCE_MODE, mode.value)
    }

    fun setShowAttendanceInStats(enabled: Boolean) {
        _showAttendanceInStats.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_SHOW_ATTENDANCE_IN_STATS, enabled)
        setStatCardEnabled("attendance", enabled)
    }

    fun setStatsCardsOrder(order: List<String>) {
        _statsCardsOrder.value = order
        SettingsManager.setString(SettingsManager.KEY_STATS_CARDS_ORDER, order.joinToString(","))
    }

    fun setStatCardEnabled(cardKey: String, enabled: Boolean) {
        val current = _enabledStatsCards.value.toMutableSet()
        if (enabled) current.add(cardKey) else current.remove(cardKey)
        _enabledStatsCards.value = current
        SettingsManager.setString(SettingsManager.KEY_ENABLED_STATS_CARDS, current.joinToString(","))
        if (cardKey == "attendance") {
            _showAttendanceInStats.value = enabled
            SettingsManager.setBoolean(SettingsManager.KEY_SHOW_ATTENDANCE_IN_STATS, enabled)
        }
    }

    fun setGpaGoal(goal: String) {
        _gpaGoal.value = goal
        SettingsManager.setString(SettingsManager.KEY_GPA_GOAL, goal)
    }

    fun setBusSubscriber(enabled: Boolean) {
        _isBusSubscriber.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_BUS_SUBSCRIBER, enabled)
    }

    fun setCustomAttendanceTarget(value: Float?) {
        _customAttendanceTarget.value = value
        if (value != null) {
            SettingsManager.setFloatString(SettingsManager.KEY_CUSTOM_ATTENDANCE_TARGET, value)
        } else {
            SettingsManager.remove(SettingsManager.KEY_CUSTOM_ATTENDANCE_TARGET)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_HAPTIC_ENABLED, enabled)
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_ANIMATIONS_ENABLED, enabled)
    }

    fun setHeroColorEnabled(enabled: Boolean) {
        _heroColorEnabled.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_HERO_COLOR_ENABLED, enabled)
    }

    fun setSyncStatus(isSyncing: Boolean, message: String? = null) {
        _isSyncing.value = isSyncing
        if (message != null) {
            _syncMessage.value = message
        }
    }

    private val _widgetOrder = MutableStateFlow<List<DashboardWidget>>(loadWidgetOrder())
    val widgetOrder: StateFlow<List<DashboardWidget>> = _widgetOrder.asStateFlow()

    private val _dashboardEditMode = MutableStateFlow(false)
    val dashboardEditMode: StateFlow<Boolean> = _dashboardEditMode.asStateFlow()
    val isDashboardEditMode: StateFlow<Boolean> = _dashboardEditMode.asStateFlow()

    fun toggleDashboardEditMode() {
        _dashboardEditMode.value = !_dashboardEditMode.value
    }

    fun updateWidgetOrder(newOrder: List<DashboardWidget>) {
        _widgetOrder.value = newOrder
        saveWidgetOrder(newOrder)
    }

    fun moveWidgetUp(widget: DashboardWidget) {
        val current = _widgetOrder.value.toMutableList()
        val index = current.indexOf(widget)
        if (index > 0) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            updateWidgetOrder(current)
        }
    }

    fun moveWidgetDown(widget: DashboardWidget) {
        val current = _widgetOrder.value.toMutableList()
        val index = current.indexOf(widget)
        if (index >= 0 && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            updateWidgetOrder(current)
        }
    }

    fun removeWidget(widget: DashboardWidget) {
        val current = _widgetOrder.value.filter { it != widget }
        updateWidgetOrder(current)
    }

    fun reorderWidget(fromIndex: Int, toIndex: Int) {
        val current = _widgetOrder.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            updateWidgetOrder(current)
        }
    }

    fun resetWidgetsToDefault() {
        updateWidgetOrder(DashboardWidget.entries)
    }

    fun setWidgetEnabled(widget: DashboardWidget, enabled: Boolean) {
        if (enabled) {
            restoreWidget(widget)
        } else {
            removeWidget(widget)
        }
    }

    fun restoreWidget(widget: DashboardWidget) {
        if (widget !in _widgetOrder.value) {
            val newOrder = _widgetOrder.value + widget
            _widgetOrder.value = newOrder
            saveWidgetOrder(newOrder)
        }
    }

    private fun loadWidgetOrder(): List<DashboardWidget> {
        val raw = SettingsManager.getString(SettingsManager.KEY_DASHBOARD_WIDGETS) ?: return DashboardWidget.entries
        if (raw.isBlank()) return DashboardWidget.entries
        return try {
            raw.split(",").mapNotNull { name ->
                when (name.trim()) {
                    // CURRENT_NEXT_CLASS + EXAM_ALERT merged into one widget
                    "CURRENT_NEXT_CLASS" -> DashboardWidget.EXAM_AND_CLASS
                    "EXAM_ALERT" -> null
                    else -> try { DashboardWidget.valueOf(name.trim()) } catch (_: Exception) { null }
                }
            }.distinct().ifEmpty { DashboardWidget.entries }
        } catch (_: Exception) {
            DashboardWidget.entries
        }
    }

    private fun saveWidgetOrder(order: List<DashboardWidget>) {
        val raw = order.joinToString(",") { it.name }
        SettingsManager.setString(SettingsManager.KEY_DASHBOARD_WIDGETS, raw)
    }

    /** Re-reads the persisted widget order (e.g. after importing a backup). */
    fun reloadWidgetOrder() {
        _widgetOrder.value = loadWidgetOrder()
    }

    fun setHeaderOverride(
        config: com.amazecc.app.shared.ui.components.HeaderConfig,
        enabledScreens: Set<Screen>? = null
    ) {
        // Only screens that opt in via enabledScreens may override the static config;
        // this keeps the header deterministic (pager neighbors / crossfades cannot corrupt it).
        if (enabledScreens == null || _currentScreen.value !in enabledScreens) return
        headerOverride.value = config
        _headerOverrideOwner.value = enabledScreens
    }

    fun clearHeaderOverride(owner: Set<Screen>? = null) {
        if (_headerOverrideOwner.value == owner) {
            headerOverride.value = null
            _headerOverrideOwner.value = null
        }
    }

    fun refreshEventsAndClubs() {
        scope.launch {
            syncEventsAndClubs()
            try { AmazeClient.eventLogin() } catch (_: Exception) { }
        }
    }
}

enum class DashboardWidget {
    PROFILE_HEADER,
    METRIC_CARDS,
    EXAM_AND_CLASS,
    ATTENDANCE_BUNK,
    TODAYS_CLASSES,
    COURSE_ATTENDANCE,
    QUICK_ACTIONS,
    FREE_CLASSROOMS
}

private data class SyncModuleResult(
    val name: String,
    val success: Boolean,
    val message: String? = null
)
