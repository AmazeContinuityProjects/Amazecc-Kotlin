@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AppTheme
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
import kotlinx.serialization.encodeToString

enum class Screen { SPLASH, 
    LOGIN, ONBOARDING, HOME, ATTENDANCE, ACADEMICS, PAYMENTS, LIBRARIES, HOSTEL, CABSHARE, TRANSPORT, MORE, PROFILE,
    EVENTS, QBANK, SOCIAL, FFCS_PLANNER, FREE_CLASSROOMS, CALENDAR, GLASS_MORPH, GRADES, GPA_PREDICTOR,
    COURSE_ATTENDANCE, ARREAR, MAKEUP_COMPRE, CIRCULARS, CURRICULUM, OD_TRACKER, COURSE_DASHBOARD,
    MARKS_TIMELINE, VITOL, FACULTY_INFO, COURSE_MANAGEMENT, PROJECTS, WISHLIST,
    FEEDBACK_STATUS, FRESHER_WELCOME, DOCUMENTS, ABOUT, ACTIVITY_TREE,
    COURSE_DETAIL, SETTINGS, MOODLE
}

object AppState {
    private val scope = CoroutineScope(Dispatchers.Main)

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
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

    private val _calendarView = MutableStateFlow("List")
    val calendarView: StateFlow<String> = _calendarView.asStateFlow()

    private val _residentialStatus = MutableStateFlow("Hosteller")
    val residentialStatus: StateFlow<String> = _residentialStatus.asStateFlow()

    // Sync toggles (mirror web app settings)
    private val _syncArrear = MutableStateFlow(true)
    val syncArrear: StateFlow<Boolean> = _syncArrear.asStateFlow()
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

    // Loading & Error states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    // Cached Data
    private val _attendance = MutableStateFlow<AttendanceRes?>(null)
    val attendance: StateFlow<AttendanceRes?> = _attendance.asStateFlow()

    private val _allSemesterAttendance = MutableStateFlow<Map<String, AttendanceRes?>>(emptyMap())
    val allSemesterAttendance: StateFlow<Map<String, AttendanceRes?>> = _allSemesterAttendance.asStateFlow()

    private val _timetable = MutableStateFlow<TimetableRes?>(null)
    val timetable: StateFlow<TimetableRes?> = _timetable.asStateFlow()

    private val _marks = MutableStateFlow<MarksRes?>(null)
    val marks: StateFlow<MarksRes?> = _marks

    private val settings = Settings()
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private val _moodleData = MutableStateFlow<MoodleRes?>(null)
    val moodleData: StateFlow<MoodleRes?> = _moodleData

    init {
        // Load cached data from local storage
        loadCachedData()
        // Load persisted settings
        _cgpaHidden.value = SettingsManager.getBoolean(SettingsManager.KEY_CGPA_HIDDEN, false)
        _attendanceDisplayMode.value = SettingsManager.getString(SettingsManager.KEY_ATTENDANCE_MODE, "percentage")
        _syncArrear.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_ARREAR, true)
        _syncExam.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_EXAM, true)
        _syncProfile.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_PROFILE, true)
        _syncAdditional.value = SettingsManager.getBoolean(SettingsManager.KEY_SYNC_ADDITIONAL, true)

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

    private fun loadCachedString(key: String): String? {
        val cached = settings.getString(key, "")
        return if (cached.isNotBlank()) cached else null
    }

    private inline fun <reified T> loadCachedData(key: String, state: MutableStateFlow<T?>) {
        val cached = settings.getString(key, "")
        if (cached.isNotBlank()) {
            try {
                state.value = jsonFormat.decodeFromString<T>(cached)
            } catch (e: Exception) { /* ignore corrupt cache */ }
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
        loadCachedData<CurriculumRes>(SettingsManager.CACHE_CURRICULUM, _curriculum)
        loadCachedData<PaymentsRes>(SettingsManager.CACHE_PAYMENTS, _payments)
        loadCachedData<LibraryRes>(SettingsManager.CACHE_LIBRARY, _library)
        loadCachedData<TransportRes>(SettingsManager.CACHE_TRANSPORT, _transport)
        loadCachedData<TransportRoutesRes>(SettingsManager.CACHE_TRANSPORT_ROUTES, _transportRoutes)
        loadCachedData<TransportPassRes>(SettingsManager.CACHE_TRANSPORT_PASS, _transportPass)
        loadCachedData<LMSRes>(SettingsManager.CACHE_LMS, _lms)
        loadCachedData<EventHubRes>(SettingsManager.CACHE_EVENTS, _events)
        loadCachedData<ClubsRes>(SettingsManager.CACHE_CLUBS, _clubs)
        loadCachedData<StudentProfileRes>(SettingsManager.CACHE_STUDENT_PROFILE, _cachedStudentProfile)
        loadCachedData<VitolRes>(SettingsManager.CACHE_VITOL, _vitolData)
        loadCachedData<CabTripsRes>(SettingsManager.CACHE_CAB_TRIPS, _cabTrips)
        // Load all semesters attendance & marks cache
        try {
            val cachedAtt = settings.getString(SettingsManager.CACHE_ALL_SEMESTER_ATTENDANCE, "")
            if (cachedAtt.isNotBlank()) _allSemesterAttendance.value = jsonFormat.decodeFromString(cachedAtt)
        } catch (_: Exception) {}
        try {
            val cachedMarks = settings.getString(SettingsManager.CACHE_ALL_SEMESTER_MARKS, "")
            if (cachedMarks.isNotBlank()) _allSemesterMarks.value = jsonFormat.decodeFromString(cachedMarks)
        } catch (_: Exception) {}
        // Also load moodle
        val cachedMoodle = settings.getString("moodle_data_cache", "")
        if (cachedMoodle.isNotBlank()) {
            try {
                _moodleData.value = jsonFormat.decodeFromString<MoodleRes>(cachedMoodle)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    private inline fun <reified T> cacheData(key: String, value: T?) {
        if (value != null) {
            try {
                settings[key] = jsonFormat.encodeToString(value)
            } catch (e: Exception) { /* ignore serialization error */ }
        } else {
            settings.remove(key)
        }
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
    val vitolData: StateFlow<VitolRes?> = _vitolData

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

    private val _curriculum = MutableStateFlow<CurriculumRes?>(null)
    val curriculum: StateFlow<CurriculumRes?> = _curriculum.asStateFlow()

    private val _payments = MutableStateFlow<PaymentsRes?>(null)
    val payments: StateFlow<PaymentsRes?> = _payments.asStateFlow()

    private val _library = MutableStateFlow<LibraryRes?>(null)
    val library: StateFlow<LibraryRes?> = _library.asStateFlow()

    private val _libraryLoginRequired = MutableStateFlow(false)
    val libraryLoginRequired: StateFlow<Boolean> = _libraryLoginRequired.asStateFlow()

    private val _transport = MutableStateFlow<TransportRes?>(null)
    val transport: StateFlow<TransportRes?> = _transport.asStateFlow()

    private val _transportRoutes = MutableStateFlow<TransportRoutesRes?>(null)
    val transportRoutes: StateFlow<TransportRoutesRes?> = _transportRoutes.asStateFlow()

    private val _transportPass = MutableStateFlow<TransportPassRes?>(null)
    val transportPass: StateFlow<TransportPassRes?> = _transportPass.asStateFlow()

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

    // Cab Share state
    private val _cabTrips = MutableStateFlow<CabTripsRes?>(null)
    val cabTrips: StateFlow<CabTripsRes?> = _cabTrips.asStateFlow()

    private val _myCabTrips = MutableStateFlow<CabTripsRes?>(null)
    val myCabTrips: StateFlow<CabTripsRes?> = _myCabTrips.asStateFlow()

    private val _cabJoinRequests = MutableStateFlow<Map<String, CabJoinRequestsRes>>(emptyMap())
    val cabJoinRequests: StateFlow<Map<String, CabJoinRequestsRes>> = _cabJoinRequests.asStateFlow()

    private val _cabLoading = MutableStateFlow(false)
    val cabLoading: StateFlow<Boolean> = _cabLoading.asStateFlow()

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
            _error.value = null
            _syncStatus.value = "Refreshing VTOP session..."
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
                    } catch (_: Exception) { /* proceed with existing session if refresh fails */ }
                }

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
                                        marksCurrent[semId] = res.marks!!
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
                                name = "Transport",
                                fetch = { AmazeClient.getTransport() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transport.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Transport Routes",
                                fetch = { AmazeClient.getTransportRoutes() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transportRoutes.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT_ROUTES, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Transport Pass",
                                fetch = { AmazeClient.getTransportPass() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transportPass.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT_PASS, it)
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
                                name = "Events",
                                fetch = { AmazeClient.getEventsProfile() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _events.value = it
                                    cacheData(SettingsManager.CACHE_EVENTS, it)
                                }
                            )
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
            }
        }
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
                                        marksCurrent[semId] = res.marks!!
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
                                name = "Transport",
                                fetch = { AmazeClient.getTransport() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transport.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Transport Routes",
                                fetch = { AmazeClient.getTransportRoutes() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transportRoutes.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT_ROUTES, it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Transport Pass",
                                fetch = { AmazeClient.getTransportPass() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    _transportPass.value = it
                                    cacheData(SettingsManager.CACHE_TRANSPORT_PASS, it)
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
        _transport.value = null
        _transportRoutes.value = null
        _transportPass.value = null
        _lms.value = null
        _events.value = null
        _clubs.value = null
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
            } catch (e: Exception) {}
        } else {
            settings.remove("moodle_data_cache")
        }
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
            } catch (_: Exception) {}
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
            } catch (_: Exception) {}
        }
    }

    fun rejectJoinRequest(tripId: String, requestId: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val res = AmazeClient.rejectCabJoinRequest(tripId, requestId)
                onResult(res.success)
                refreshJoinRequests(tripId)
            } catch (_: Exception) {}
        }
    }

    fun refreshMyCabTrips() {
        scope.launch {
            try {
                val res = AmazeClient.getMyCabTrips()
                if (res.error == null) {
                    _myCabTrips.value = res
                }
            } catch (_: Exception) {}
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
            } catch (_: Exception) {}
        }
    }

    fun syncEventsAndClubs() {
        scope.launch {
            try {
                val eventsRes = AmazeClient.getEvents()
                if (eventsRes.error == null) {
                    _events.value = eventsRes
                    cacheData(SettingsManager.CACHE_EVENTS, eventsRes)
                }
            } catch (_: Exception) {}
            try {
                val clubsRes = AmazeClient.getClubsDetails()
                if (clubsRes.error == null) {
                    _clubs.value = clubsRes
                    cacheData(SettingsManager.CACHE_CLUBS, clubsRes)
                }
            } catch (_: Exception) {}
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

    fun changeTheme(theme: AppTheme) {
        _theme.value = theme
    }

    fun changeAccent(accent: AccentTheme) {
        _accent.value = accent
    }

    fun changeUiScale(scale: Float) {
        _uiScale.value = scale
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

    fun setSyncArrear(enabled: Boolean) {
        _syncArrear.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_SYNC_ARREAR, enabled)
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
