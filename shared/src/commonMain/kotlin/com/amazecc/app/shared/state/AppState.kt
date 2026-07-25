package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.requestNotificationPermissions
import com.amazecc.app.shared.utils.testLocalNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class Screen { SPLASH, 
    LOGIN, ONBOARDING, HOME, ATTENDANCE, ACADEMICS, PAYMENTS, LIBRARIES, HOSTEL, CABSHARE, TRANSPORT, MORE, PROFILE,
    EVENTS, QBANK, SOCIAL, FFCS_PLANNER, FREE_CLASSROOMS, CALENDAR, GRADES, GPA_PREDICTOR,
    COURSE_ATTENDANCE, MAKEUP_COMPRE, CIRCULARS, CURRICULUM, OD_TRACKER, COURSE_DASHBOARD,
    MARKS_TIMELINE, VITOL, FACULTY_INFO, COURSE_MANAGEMENT, PROJECTS, WISHLIST,
    FEEDBACK_STATUS, FRESHER_WELCOME, DOCUMENTS, ABOUT, CLUB_DETAIL,
    COURSE_DETAIL, SETTINGS, MOODLE, CLUB_HUB, TASKS
}

object AppState {
    private val scope = CoroutineScope(Dispatchers.Main)

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
    // Floating Header State
    val headerTitle = MutableStateFlow("")
    val headerDescription = MutableStateFlow("")
    val headerShowBack = MutableStateFlow(true)
    val headerShowSync = MutableStateFlow(true)
    val headerOnRefresh = MutableStateFlow<(() -> Unit)?>(null)
    val headerSyncModules = MutableStateFlow<Set<SyncModule>>(emptySet())
    
    // Global Spotlight Search
    private val _showSearch = MutableStateFlow(false)
    val showSearch: StateFlow<Boolean> = _showSearch.asStateFlow()

    fun setSearchOpen(open: Boolean) {
        _showSearch.value = open
    }
    
    private val _pinnedNavTabs = MutableStateFlow(listOf(Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE))
    val pinnedNavTabs: StateFlow<List<Screen>> = _pinnedNavTabs.asStateFlow()

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

    private val _uiScale = MutableStateFlow(1.0f)
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    private val _decimalValues = MutableStateFlow(true)
    val decimalValues: StateFlow<Boolean> = _decimalValues.asStateFlow()

    private val _friendlyName = MutableStateFlow(true)
    val friendlyName: StateFlow<Boolean> = _friendlyName.asStateFlow()

    private val _cgpaHidden = MutableStateFlow(false)
    val cgpaHidden: StateFlow<Boolean> = _cgpaHidden.asStateFlow()

    private val _attendanceDisplayMode = MutableStateFlow("percentage")
    val attendanceDisplayMode: StateFlow<String> = _attendanceDisplayMode.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _animationsEnabled = MutableStateFlow(true)
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    private val _calendarView = MutableStateFlow("List")
    val calendarView: StateFlow<String> = _calendarView.asStateFlow()

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

    fun dismissUpdateDialog() {
        val status = _updateStatus.value
        if (status is UpdateStatus.Available) {
            val ver = status.release.tagName.removePrefix("v")
            _updateDialogDismissedVersion.value = ver
            SettingsManager.setString(SettingsManager.KEY_UPDATE_DISMISSED_VERSION, ver)
            _updateStatus.value = UpdateStatus.Idle
        }
    }

    fun checkForUpdate() {
        if (_updateStatus.value is UpdateStatus.Checking) return
        scope.launch {
            _updateStatus.value = UpdateStatus.Checking
            try {
                val currentVersion = UpdateConfig.getCurrentVersion()
                val release = AmazeClient.checkForUpdate()
                val latestVer = release.tagName.removePrefix("v")
                val dismissed = _updateDialogDismissedVersion.value
                if (latestVer == dismissed) {
                    _updateStatus.value = UpdateStatus.Idle
                } else if (compareVersions(latestVer, currentVersion) > 0) {
                    _updateStatus.value = UpdateStatus.Available(release, currentVersion)
                } else {
                    _updateStatus.value = UpdateStatus.UpToDate
                }
            } catch (e: Exception) {
                _updateStatus.value = UpdateStatus.Error(e.message ?: "Failed to check for update")
            }
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    // Sync toggles (mirror web app settings)
    private val _syncExam = MutableStateFlow(true)
    val syncExam: StateFlow<Boolean> = _syncExam.asStateFlow()
    private val _syncProfile = MutableStateFlow(true)
    val syncProfile: StateFlow<Boolean> = _syncProfile.asStateFlow()
    private val _syncAdditional = MutableStateFlow(true)
    val syncAdditional: StateFlow<Boolean> = _syncAdditional.asStateFlow()

    // Student profile data
    private val _studentProfile = MutableStateFlow<StudentProfile?>(null)
    val studentProfile: StateFlow<StudentProfile?> = _studentProfile.asStateFlow()
    private val _cachedStudentProfile = MutableStateFlow<StudentProfileRes?>(null)

    val semesterMap = mapOf(
        "CH20262705" to "Winter Semester 2026-27",
        "CH20262701" to "Fall Semester 2026-27",
        "CH20252605" to "Winter Semester 2025-26",
        "CH20252601" to "Fall Semester 2025-26",
        "CH20242505" to "Winter Semester 2024-25",
        "CH20242501" to "Fall Semester 2024-25",
        "CH20232405" to "Winter Semester 2023-24",
        "CH20232401" to "Fall Semester 2023-24"
    )
    val semesterIDs = semesterMap.keys.toList()
    private val _selectedSemester = MutableStateFlow("CH20262701")
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

    // Cached Data
    private val _attendance = MutableStateFlow<AttendanceRes?>(null)
    val attendance: StateFlow<AttendanceRes?> = _attendance.asStateFlow()

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
        _attendanceDisplayMode.value = SettingsManager.getString(SettingsManager.KEY_ATTENDANCE_MODE, "percentage")
        _syncExam.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_EXAM, true)
        _syncProfile.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_PROFILE, true)
        _syncAdditional.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_ADDITIONAL, true)
        _updateDialogDismissedVersion.value = SettingsManager.getString(SettingsManager.KEY_UPDATE_DISMISSED_VERSION, "")

        val savedTheme = SettingsManager.getString(SettingsManager.KEY_APP_THEME, "")
        if (savedTheme.isNotEmpty()) {
            try { _theme.value = AppTheme.valueOf(savedTheme) } catch (_: Exception) {}
        }
        val savedAccent = SettingsManager.getString(SettingsManager.KEY_APP_ACCENT, "")
        if (savedAccent.isNotEmpty()) {
            try { _accent.value = AccentTheme.valueOf(savedAccent) } catch (_: Exception) {}
        }
        val savedScale = SettingsManager.getString(SettingsManager.KEY_UI_SCALE, "")
        if (savedScale.isNotEmpty()) {
            savedScale.toFloatOrNull()?.let { _uiScale.value = it }
        }

        _hapticEnabled.value = SettingsManager.getBoolean(SettingsManager.KEY_HAPTIC_ENABLED, true)
        _animationsEnabled.value = SettingsManager.getBoolean(SettingsManager.KEY_ANIMATIONS_ENABLED, true)

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
        loadCachedData<HostelDetails>(SettingsManager.CACHE_HOSTEL_DETAILS, _hostelDetails)
        loadCachedData<HostelLeaveRes>(SettingsManager.CACHE_HOSTEL_LEAVES, _hostelLeaves)
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
        loadCachedData<StudentProfileRes>(SettingsManager.CACHE_STUDENT_PROFILE, _cachedStudentProfile)
        _studentProfile.value = _cachedStudentProfile.value?.data
        loadCachedData<VitolRes>(SettingsManager.CACHE_VITOL, _vitolData)
        loadCachedData<CabTripsRes>(SettingsManager.CACHE_CAB_TRIPS, _cabTrips)

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
        // Also load moodle
        val cachedMoodle = settings.getString("moodle_data_cache", "")
        if (cachedMoodle.isNotBlank()) {
            try {
                _moodleData.value = jsonFormat.decodeFromString<MoodleRes>(cachedMoodle)
            } catch (e: Exception) { println("AmazeCC: AppState loadCachedData moodle — ${e.message}") }
        }
        // Sync cached modules state to SyncEngine
        updateModuleStatesFromCache()
        loadTasks()
    }

    private inline fun <reified T> cacheData(key: String, value: T) {
        try {
            settings[key] = jsonFormat.encodeToString(value)
        } catch (e: Exception) { println("AmazeCC: AppState cacheData — ${e.message}") }
    }

    @Suppress("unused")
    private fun removeCache(key: String) {
        settings.remove(key)
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
        if (_hostelLeaves.value != null) { cacheData(SettingsManager.CACHE_HOSTEL_LEAVES, _hostelLeaves.value); saved++ }
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
        if (_cachedStudentProfile.value != null) { cacheData(SettingsManager.CACHE_STUDENT_PROFILE, _cachedStudentProfile.value); saved++ }
        if (_vitolData.value != null) { cacheData(SettingsManager.CACHE_VITOL, _vitolData.value); saved++ }
        if (_cabTrips.value != null) { cacheData(SettingsManager.CACHE_CAB_TRIPS, _cabTrips.value); saved++ }
        if (_allSemesterAttendance.value.isNotEmpty()) { cacheData(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, _allSemesterAttendance.value); saved++ }
        if (_allSemesterMarks.value.isNotEmpty()) { cacheData(SettingsManager.CACHE_ALL_SEMESTER_MARKS, _allSemesterMarks.value); saved++ }
        SyncEngine.addLog(SyncModule.ATTENDANCE, "Saved $saved modules offline", SyncStatus.SUCCESS)
    }

    // ── Mark cached modules as SUCCESS in SyncEngine ──
    private fun updateModuleStatesFromCache() {
        if (_attendance.value != null) SyncEngine.updateModuleState(SyncModule.ATTENDANCE, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_timetable.value != null) SyncEngine.updateModuleState(SyncModule.TIMETABLE, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_marks.value != null) SyncEngine.updateModuleState(SyncModule.MARKS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_allGrades.value != null) SyncEngine.updateModuleState(SyncModule.GRADES, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_curriculum.value != null) SyncEngine.updateModuleState(SyncModule.CURRICULUM, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_hostelDetails.value != null) SyncEngine.updateModuleState(SyncModule.HOSTEL_DETAILS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_hostelLeaves.value != null) SyncEngine.updateModuleState(SyncModule.HOSTEL_LEAVES, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_examSchedule.value != null) SyncEngine.updateModuleState(SyncModule.EXAM_SCHEDULE, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_calendar.value != null) SyncEngine.updateModuleState(SyncModule.CALENDAR, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_calendarsList.value != null) SyncEngine.updateModuleState(SyncModule.CALENDARS_LIST, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_qcmView.value != null) SyncEngine.updateModuleState(SyncModule.QCM_VIEW, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_payments.value != null) SyncEngine.updateModuleState(SyncModule.PAYMENTS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_library.value != null) SyncEngine.updateModuleState(SyncModule.LIBRARY, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_transportData.value != null) SyncEngine.updateModuleState(SyncModule.TRANSPORT, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_buses.value != null) SyncEngine.updateModuleState(SyncModule.BUSES, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_lms.value != null) SyncEngine.updateModuleState(SyncModule.LMS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_events.value != null) SyncEngine.updateModuleState(SyncModule.EVENTS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_clubs.value != null) SyncEngine.updateModuleState(SyncModule.CLUBS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_cachedStudentProfile.value != null) SyncEngine.updateModuleState(SyncModule.STUDENT_PROFILE, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_vitolData.value != null) SyncEngine.updateModuleState(SyncModule.VITOL, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_cabTrips.value != null) SyncEngine.updateModuleState(SyncModule.CAB_TRIPS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_allSemesterAttendance.value.isNotEmpty()) SyncEngine.updateModuleState(SyncModule.ALL_SEMESTER_ATTENDANCE, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
        if (_allSemesterMarks.value.isNotEmpty()) SyncEngine.updateModuleState(SyncModule.MARKS, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now()))
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

    private val _vitolData = MutableStateFlow<VitolRes?>(null)
    val vitolData: StateFlow<VitolRes?> = _vitolData.asStateFlow()

    private val _allGrades = MutableStateFlow<AllGradesRes?>(null)
    val allGrades: StateFlow<AllGradesRes?> = _allGrades.asStateFlow()

    private val _hostelDetails = MutableStateFlow<HostelDetails?>(null)
    val hostelDetails: StateFlow<HostelDetails?> = _hostelDetails.asStateFlow()

    private val _hostelLeaves = MutableStateFlow<HostelLeaveRes?>(null)
    val hostelLeaves: StateFlow<HostelLeaveRes?> = _hostelLeaves.asStateFlow()

    private val _examSchedule = MutableStateFlow<ExamScheduleRes?>(null)
    val examSchedule: StateFlow<ExamScheduleRes?> = _examSchedule.asStateFlow()

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

    private val _clubs = MutableStateFlow<ClubsRes?>(null)
    val clubs: StateFlow<ClubsRes?> = _clubs.asStateFlow()

    private val _allSemesterMarks = MutableStateFlow<Map<String, MarksRes>>(emptyMap())
    val allSemesterMarks: StateFlow<Map<String, MarksRes>> = _allSemesterMarks.asStateFlow()

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

    // Cab Share state
    private val _cabTrips = MutableStateFlow<CabTripsRes?>(null)
    val cabTrips: StateFlow<CabTripsRes?> = _cabTrips.asStateFlow()

    private val _myCabTrips = MutableStateFlow<CabTripsRes?>(null)
    val myCabTrips: StateFlow<CabTripsRes?> = _myCabTrips.asStateFlow()

    private val _cabJoinRequests = MutableStateFlow<Map<String, CabJoinRequestsRes>>(emptyMap())
    val cabJoinRequests: StateFlow<Map<String, CabJoinRequestsRes>> = _cabJoinRequests.asStateFlow()

    private val _cabLoading = MutableStateFlow(false)
    val cabLoading: StateFlow<Boolean> = _cabLoading.asStateFlow()

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

    val todayTasks: List<HomeworkTask>
        get() {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            return _tasks.value.filter { it.dueDate == today && !it.completed }
        }

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            backstack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

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

    fun selectSemester(semesterId: String) {
        _selectedSemester.value = semesterId
        // Refresh semester-specific data
        if (SessionManager.isLoggedIn) {
            loadSemesterData(semesterId)
        }
    }

    fun loadSemesterData(semesterId: String) {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _error.value = null
            _syncStatus.value = "Syncing semester data..."
            try {
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
                                    cacheData(SettingsManager.CACHE_ATTENDANCE, it.attendance)
                                    it.marks?.let { marks ->
                                        _marks.value = marks
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
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAllData() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _isSyncing.value = true
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
                        }
                    } catch (e: Exception) { println("AmazeCC: AppState loadAllData sessionRefresh — ${e.message}") }
                }

                _syncMessage.value = "Syncing academic and campus data..."
                _syncStatus.value = "Syncing academic and campus data..."
                val sem = _selectedSemester.value

                val results = supervisorScope {
                    listOf(
                        async {
                            syncModule(
                                name = "Attendance and CGPA",
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
                            var failed = false
                            for (semId in semesterIDs) {
                                if (semId == sem) continue
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
                            SyncModuleResult("All Semesters Attendance", !failed)
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
                                    _allGrades.value = it
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
                                name = "Hostel leaves",
                                fetch = { AmazeClient.getHostelLeaves() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _hostelLeaves.value = it
                                    cacheData(SettingsManager.CACHE_HOSTEL_LEAVES, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Exam schedule",
                                fetch = { AmazeClient.getExamSchedule() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _examSchedule.value = it
                                    cacheData(SettingsManager.CACHE_EXAM_SCHEDULE, it)
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
                            val clubToken = SessionManager.clubToken.value
                            if (!clubToken.isNullOrBlank()) {
                                syncModule(
                                    name = "Events",
                                    fetch = { AmazeClient.getEventsProfile() },
                                    isSuccess = { it.error == null },
                                    errorMessage = { it.error },
                                    update = {
                                        _events.value = it
                                        cacheData(SettingsManager.CACHE_EVENTS, it)
                                    }
                                )
                            } else {
                                SyncModuleResult("Events", true)
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
                            if (syncProfile.value) {
                                syncModule(
                                    name = "Student Profile",
                                    fetch = { AmazeClient.getStudentProfile() },
                                    isSuccess = { it.success && it.data != null },
                                    errorMessage = { it.error },
                                    update = {
                                        _studentProfile.value = it.data
                                        cacheData(SettingsManager.CACHE_STUDENT_PROFILE, it)
                                    }
                                )
                            } else SyncModuleResult("Student Profile", true)
                        },
                        async {
                            syncModule(
                                name = "Cab Share",
                                fetch = { AmazeClient.getMyCabTrips() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _myCabTrips.value = it
                                }
                            )
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
            } finally {
                _isLoading.value = false
                _isSyncing.value = false
                _syncMessage.value = null
                notificationService.showLoadingNotification("AmazeCC Sync", "Sync completed")
                scheduleReminders()
            }
        }
    }

    private fun scheduleReminders() {
        val attendanceItems = _attendance.value?.attendance
        val assignments = _lms.value?.assignments
        val vitolData = _vitolData.value?.data
        val moodleAssignments = _moodleData.value?.data?.filter { !it.done }?.map { a ->
            LMSAssignment("moodle_${a.hashCode()}", a.courseCode, a.taskTitle, "", a.due, "Pending")
        } ?: emptyList()
        val allAssignments = (assignments ?: emptyList()) + moodleAssignments
        val attMaps = attendanceItems?.map { item ->
            mapOf(
                "courseCode" to item.courseCode,
                "courseTitle" to item.courseTitle,
                "courseType" to item.courseType,
                "faculty" to item.faculty,
                "slotName" to (item.slotName ?: ""),
                "attendancePercentage" to item.attendancePercentage,
                "venue" to (item.slotVenue ?: "")
            )
        }
        val typedSlotMap = SlotMap.map.mapValues { (_, inner) ->
            inner.mapValues { (_, time) -> SlotInfo(time) }
        }
        com.amazecc.app.shared.utils.NotificationsUtils.scheduleAll(
            attendance = attMaps,
            slotMap = typedSlotMap,
            assignments = allAssignments,
            vitolLimit = vitolData?.limit,
            vitolConsumed = vitolData?.consumed,
            tasks = _tasks.value
        )
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
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing current semester..."
            try {
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
            } finally { _isLoading.value = false }
        }
    }

    fun refreshAllAcademic() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing academic data..."
            try {
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
                            var failed = false
                            for (semId in semesterIDs) {
                                if (semId == sem) continue
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
                                    _allGrades.value = it
                                    cacheData(SettingsManager.CACHE_GRADES, it)
                                }
                            )
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
            } finally { _isLoading.value = false }
        }
    }

    fun refreshPayments() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing payments..."
            try {
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
            } finally { _isLoading.value = false }
        }
    }

    fun refreshHostel() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing hostel..."
            try {
                val results = supervisorScope {
                    listOf(
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
                                name = "Hostel leaves",
                                fetch = { AmazeClient.getHostelLeaves() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _hostelLeaves.value = it
                                    cacheData(SettingsManager.CACHE_HOSTEL_LEAVES, it)
                                }
                            )
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
            } finally { _isLoading.value = false }
        }
    }

    fun refreshCalendar() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing calendar..."
            try {
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
            } catch (e: Exception) {
                _syncStatus.value = "Calendar sync failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshCalendarsList() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing calendars list..."
            try {
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
            } catch (e: Exception) {
                _syncStatus.value = "Calendars list sync failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshQcmView() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing QCM data..."
            try {
                val res = AmazeClient.getQcmView()
                if (res.success) {
                    _qcmView.value = res
                    cacheData(SettingsManager.CACHE_QCM_VIEW, res)
                }
                _syncStatus.value = if (res.success) "QCM synced" else "QCM sync failed"
            } catch (e: Exception) {
                _syncStatus.value = "QCM sync failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshCurriculum() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing curriculum..."
            try {
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
            } catch (e: Exception) {
                _syncStatus.value = "Curriculum sync failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshGrades() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing grades..."
            try {
                val result = syncModule(
                    name = "Grade history",
                    fetch = { AmazeClient.getAllGrades() },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = {
                        _allGrades.value = it
                        cacheData(SettingsManager.CACHE_GRADES, it)
                    }
                )
                updateSyncSummary(listOf(result))
            } finally { _isLoading.value = false }
        }
    }

    fun refreshProfile() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing profile..."
            try {
                val result = syncModule(
                    name = "Student Profile",
                    fetch = { AmazeClient.getStudentProfile() },
                    isSuccess = { it.success && it.data != null },
                    errorMessage = { it.error },
                    update = {
                        _studentProfile.value = it.data
                        cacheData(SettingsManager.CACHE_STUDENT_PROFILE, it)
                    }
                )
                updateSyncSummary(listOf(result))
            } finally { _isLoading.value = false }
        }
    }

    fun refreshLibrary() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing library..."
            try {
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
            } finally { _isLoading.value = false }
        }
    }

    fun refreshTransport() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing transport..."
            try {
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
            } finally { _isLoading.value = false }
        }
    }

    fun refreshLMS() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing LMS..."
            try {
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
            } finally { _isLoading.value = false }
        }
    }

    fun refreshExamSchedule() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing exam schedule..."
            try {
                val result = syncModule(
                    name = "Exam schedule",
                    fetch = { AmazeClient.getExamSchedule() },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = {
                        _examSchedule.value = it
                        cacheData(SettingsManager.CACHE_EXAM_SCHEDULE, it)
                    }
                )
                updateSyncSummary(listOf(result))
            } finally { _isLoading.value = false }
        }
    }

    fun refreshCabShare() {
        if (_isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing cab share..."
            try {
                val result = syncModule(
                    name = "Cab Share",
                    fetch = { AmazeClient.getMyCabTrips() },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = { _myCabTrips.value = it }
                )
                updateSyncSummary(listOf(result))
            } finally { _isLoading.value = false }
        }
    }

    private suspend fun <T> syncModule(
        name: String,
        fetch: suspend () -> T,
        isSuccess: (T) -> Boolean,
        errorMessage: (T) -> String?,
        update: (T) -> Unit
    ): SyncModuleResult {
        return try {
            val result = fetch()
            if (isSuccess(result)) {
                update(result)
                SyncModuleResult(name, true)
            } else {
                SyncModuleResult(name, false, errorMessage(result) ?: "Empty or failed response")
            }
        } catch (e: Exception) {
            SyncModuleResult(name, false, e.message ?: e.toString())
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
        SessionManager.clearSession()
        backstack.clear()
        _currentScreen.value = Screen.LOGIN
        
        // Clear caches
        _attendance.value = null
        _timetable.value = null
        _marks.value = null
        _allGrades.value = null
        _hostelDetails.value = null
        _hostelLeaves.value = null
        _examSchedule.value = null
        _calendar.value = null
        _payments.value = null
        _library.value = null
        _transportData.value = null
        _buses.value = null
        _lms.value = null
        _events.value = null
        _clubs.value = null
        
        _moodleData.value = null
        _vitolData.value = null
        _cachedStudentProfile.value = null
        _curriculum.value = null
        _selectedSemester.value = "CH20262701"
        _selectedCourseCode.value = null
        _selectedCourseSemester.value = null
        _isLoading.value = false
        _cabTrips.value = null
        _myCabTrips.value = null
        _cabJoinRequests.value = emptyMap()
        _allSemesterMarks.value = emptyMap()
        _allSemesterAttendance.value = emptyMap()
        _libraryLoginRequired.value = false
        _error.value = null
        _syncStatus.value = null

        // Clear persisted caches
        settings.remove(SettingsManager.CACHE_ATTENDANCE)
        settings.remove(SettingsManager.CACHE_TIMETABLE)
        settings.remove(SettingsManager.CACHE_MARKS)
        settings.remove(SettingsManager.CACHE_GRADES)
        settings.remove(SettingsManager.CACHE_HOSTEL_DETAILS)
        settings.remove(SettingsManager.CACHE_HOSTEL_LEAVES)
        settings.remove(SettingsManager.CACHE_EXAM_SCHEDULE)
        settings.remove(SettingsManager.CACHE_CALENDAR)
        settings.remove(SettingsManager.CACHE_CURRICULUM)
        settings.remove(SettingsManager.CACHE_PAYMENTS)
        settings.remove(SettingsManager.CACHE_LIBRARY)
        settings.remove(SettingsManager.CACHE_TRANSPORT)
        settings.remove(SettingsManager.CACHE_TRANSPORT_ROUTES)
        settings.remove(SettingsManager.CACHE_TRANSPORT_PASS)
        settings.remove(SettingsManager.CACHE_LMS)
        settings.remove(SettingsManager.CACHE_EVENTS)
        settings.remove(SettingsManager.CACHE_CLUBS)
        settings.remove(SettingsManager.CACHE_CAB_TRIPS)
        settings.remove(SettingsManager.CACHE_STUDENT_PROFILE)
        settings.remove(SettingsManager.CACHE_VITOL)
        SettingsManager.clearLibraryCredentials()
        settings.remove(SettingsManager.SESSION_COOKIES)
        settings.remove(SettingsManager.SESSION_CSRF)
        settings.remove(SettingsManager.SESSION_AUTHORIZED_ID)
        settings.remove(SettingsManager.SESSION_CLUB_TOKEN)
        settings.remove("moodle_data_cache")
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
                settings["moodle_data_cache"] = jsonFormat.encodeToString(data)
            } catch (e: Exception) { println("AmazeCC: AppState updateMoodleData — ${e.message}") }
        } else {
            settings.remove("moodle_data_cache")
        }
        scope.launch { scheduleReminders() }
    }

    fun getMoodleAssignmentsForCourse(courseCode: String): List<MoodleAssignment> {
        return _moodleData.value?.data?.filter { a ->
            a.courseCode.equals(courseCode, ignoreCase = true) ||
            a.name.contains(courseCode, ignoreCase = true)
        } ?: emptyList()
    }

    fun updateVitolData(data: VitolRes?) {
        _vitolData.value = data
    }

    fun searchCabTrips(from: String, to: String, date: String) {
        scope.launch {
            _cabLoading.value = true
            try {
                val res = AmazeClient.searchCabTrips(from, to, date)
                if (res.error == null) {
                    _cabTrips.value = res
                    cacheData(SettingsManager.CACHE_CAB_TRIPS, res)
                }
            } catch (e: Exception) { println("AmazeCC: AppState searchCabTrips — ${e.message}") }
            _cabLoading.value = false
        }
    }

    fun createCabTrip(
        from: String, to: String, date: String, time: String,
        seats: Int, fare: String,
        vehicleModel: String?, vehicleColor: String?, vehiclePlate: String?,
        onSuccess: (String) -> Unit = {}, onError: (String) -> Unit = {}
    ) {
        scope.launch {
            _cabLoading.value = true
            try {
                val request = CabCreateTripRequest(
                    from = from, to = to, date = date, time = time,
                    seats = seats, fare = fare,
                    vehicleModel = vehicleModel, vehicleColor = vehicleColor, vehiclePlate = vehiclePlate
                )
                val res = AmazeClient.createCabTrip(request)
                if (res.success && res.tripId != null) {
                    onSuccess(res.tripId)
                    // Refresh my trips
                    val myTrips = AmazeClient.getMyCabTrips()
                    if (myTrips.error == null) _myCabTrips.value = myTrips
                } else {
                    onError(res.message ?: "Failed to create trip")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Network error")
            }
            _cabLoading.value = false
        }
    }

    fun requestJoinTrip(tripId: String, seats: Int, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val res = AmazeClient.requestJoinTrip(tripId, seats)
                onResult(res.success, res.message ?: if (res.success) "Request sent!" else "Failed to join")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
            }
        }
    }

    fun acceptJoinRequest(tripId: String, requestId: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val res = AmazeClient.acceptCabJoinRequest(tripId, requestId)
                onResult(res.success)
                refreshJoinRequests(tripId)
            } catch (e: Exception) { println("AmazeCC: AppState acceptJoinRequest — ${e.message}") }
        }
    }

    fun rejectJoinRequest(tripId: String, requestId: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val res = AmazeClient.rejectCabJoinRequest(tripId, requestId)
                onResult(res.success)
                refreshJoinRequests(tripId)
            } catch (e: Exception) { println("AmazeCC: AppState rejectJoinRequest — ${e.message}") }
        }
    }

    fun refreshMyCabTrips() {
        scope.launch {
            try {
                val res = AmazeClient.getMyCabTrips()
                if (res.error == null) {
                    _myCabTrips.value = res
                }
            } catch (e: Exception) { println("AmazeCC: AppState refreshMyCabTrips — ${e.message}") }
        }
    }

    fun refreshJoinRequests(tripId: String) {
        scope.launch {
            try {
                val res = AmazeClient.getCabJoinRequests(tripId)
                if (res.error == null) {
                    val current = _cabJoinRequests.value.toMutableMap()
                    current[tripId] = res
                    _cabJoinRequests.value = current
                }
            } catch (e: Exception) { println("AmazeCC: AppState refreshJoinRequests — ${e.message}") }
        }
    }

    fun syncEventsAndClubs() {
        scope.launch {
            _isLoading.value = true
            try {
                val eventsRes = AmazeClient.getEvents()
                if (eventsRes.error == null) {
                    _events.value = eventsRes
                    cacheData(SettingsManager.CACHE_EVENTS, eventsRes)
                }
            } catch (e: Exception) { println("AmazeCC: AppState syncEventsAndClubs events — ${e.message}") }
            try {
                val clubsRes = AmazeClient.getClubsDetails()
                if (clubsRes.error == null) {
                    _clubs.value = clubsRes
                    cacheData(SettingsManager.CACHE_CLUBS, clubsRes)
                }
            } catch (e: Exception) { println("AmazeCC: AppState syncEventsAndClubs clubs — ${e.message}") }
            _isLoading.value = false
        }
    }

    fun startOnboardingSync() {
        scope.launch {
            val steps = listOf(
                "Session", "Attendance", "Timetable", "Grades",
                "Curriculum", "Hostel", "Payments", "Events"
            )
            _onboardingSyncSteps.value = steps.map { AppState.SyncStep(it, "pending") }

            fun updateStep(name: String, status: String) {
                _onboardingSyncSteps.value = _onboardingSyncSteps.value.map {
                    if (it.name == name) it.copy(status = status) else it
                }
            }

            // Session refresh
            updateStep("Session", "syncing")
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
                    }
                }
                updateStep("Session", "done")
            } catch (_: Exception) { updateStep("Session", "failed") }

            val sem = _selectedSemester.value

            supervisorScope {
                launch {
                    updateStep("Attendance", "syncing")
                    try {
                        val res = AmazeClient.getAcademicData(sem)
                        if (res.attendance.error == null) {
                            _attendance.value = res.attendance
                            cacheData(SettingsManager.CACHE_ATTENDANCE, res.attendance)
                            res.marks?.let {
                                _marks.value = it
                                cacheData(SettingsManager.CACHE_MARKS, it)
                            }
                        }
                        updateStep("Attendance", "done")
                    } catch (_: Exception) { updateStep("Attendance", "failed") }
                }
                launch {
                    updateStep("Timetable", "syncing")
                    try {
                        val res = AmazeClient.getTimetable(sem)
                        if (res.error == null) {
                            _timetable.value = res
                            cacheData(SettingsManager.CACHE_TIMETABLE, res)
                        }
                        updateStep("Timetable", "done")
                    } catch (_: Exception) { updateStep("Timetable", "failed") }
                }
                launch {
                    updateStep("Grades", "syncing")
                    try {
                        val res = AmazeClient.getAllGrades()
                        if (res.error == null) {
                            _allGrades.value = res
                            cacheData(SettingsManager.CACHE_GRADES, res)
                        }
                        updateStep("Grades", "done")
                    } catch (_: Exception) { updateStep("Grades", "failed") }
                }
                launch {
                    updateStep("Curriculum", "syncing")
                    try {
                        val res = AmazeClient.getCurriculum(semesterId = sem)
                        if (res.error == null) {
                            _curriculum.value = res
                            cacheData(SettingsManager.CACHE_CURRICULUM, res)
                        }
                        updateStep("Curriculum", "done")
                    } catch (_: Exception) { updateStep("Curriculum", "failed") }
                }
                launch {
                    updateStep("Hostel", "syncing")
                    try {
                        val res = AmazeClient.getHostelDetails()
                        if (res.error == null) {
                            _hostelDetails.value = res
                            cacheData(SettingsManager.CACHE_HOSTEL_DETAILS, res)
                        }
                        updateStep("Hostel", "done")
                    } catch (_: Exception) { updateStep("Hostel", "failed") }
                }
                launch {
                    updateStep("Payments", "syncing")
                    try {
                        val res = AmazeClient.getPayments()
                        if (res.error == null) {
                            _payments.value = res
                            cacheData(SettingsManager.CACHE_PAYMENTS, res)
                        }
                        updateStep("Payments", "done")
                    } catch (_: Exception) { updateStep("Payments", "failed") }
                }
                launch {
                    updateStep("Events", "syncing")
                    try {
                        val res = AmazeClient.getEvents()
                        if (res.error == null) {
                            _events.value = res
                            cacheData(SettingsManager.CACHE_EVENTS, res)
                        }
                        updateStep("Events", "done")
                    } catch (_: Exception) { updateStep("Events", "failed") }
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

    fun changeUiScale(scale: Float) {
        _uiScale.value = scale
        SettingsManager.setString(SettingsManager.KEY_UI_SCALE, scale.toString())
    }

    fun setDecimalValues(enabled: Boolean) {
        _decimalValues.value = enabled
    }

    fun setFriendlyName(enabled: Boolean) {
        _friendlyName.value = enabled
    }

    fun setCalendarView(view: String) {
        _calendarView.value = view
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

    fun setAttendanceDisplayMode(mode: String) {
        _attendanceDisplayMode.value = mode
        SettingsManager.setString(SettingsManager.KEY_ATTENDANCE_MODE, mode)
    }

    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_HAPTIC_ENABLED, enabled)
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_ANIMATIONS_ENABLED, enabled)
    }

    fun setSyncExam(enabled: Boolean) {
        _syncExam.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_SYNC_EXAM, enabled)
    }

    fun setSyncProfile(enabled: Boolean) {
        _syncProfile.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_SYNC_PROFILE, enabled)
    }

    fun setSyncAdditional(enabled: Boolean) {
        _syncAdditional.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_SYNC_ADDITIONAL, enabled)
    }

    fun updateStudentProfile(profile: StudentProfile?) {
        _studentProfile.value = profile
    }

    fun setSyncStatus(isSyncing: Boolean, message: String? = null) {
        _isSyncing.value = isSyncing
        if (message != null) {
            _syncMessage.value = message
        }
    }
}

private data class SyncModuleResult(
    val name: String,
    val success: Boolean,
    val message: String? = null
)
