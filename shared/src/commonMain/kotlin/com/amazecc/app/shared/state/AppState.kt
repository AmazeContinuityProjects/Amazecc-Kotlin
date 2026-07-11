package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
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

enum class Screen {
    LOGIN, DASHBOARD, ATTENDANCE, MARKS, TIMETABLE, HOSTEL, PAYMENTS, LIBRARY, TRANSPORT, LMS, PROFILE, EVENTS, CALENDAR, QBANK, SOCIAL, FFCS
}

object AppState {
    private val scope = CoroutineScope(Dispatchers.Main)

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.LOGIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val backstack = mutableListOf<Screen>()

    // Global settings
    private val _theme = MutableStateFlow(AppTheme.MIDNIGHT)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _accent = MutableStateFlow(AccentTheme.OCEAN)
    val accent: StateFlow<AccentTheme> = _accent.asStateFlow()

    private val _uiScale = MutableStateFlow(1.0f)
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    // Semesters
    val semesterIDs = listOf("CH20252601", "CH20242505", "CH20242501", "CH20232405")
    private val _selectedSemester = MutableStateFlow("CH20252601")
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

    private val _timetable = MutableStateFlow<TimetableRes?>(null)
    val timetable: StateFlow<TimetableRes?> = _timetable.asStateFlow()

    private val _marks = MutableStateFlow<MarksRes?>(null)
    val marks: StateFlow<MarksRes?> = _marks.asStateFlow()

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

    private val _payments = MutableStateFlow<PaymentsRes?>(null)
    val payments: StateFlow<PaymentsRes?> = _payments.asStateFlow()

    private val _library = MutableStateFlow<LibraryRes?>(null)
    val library: StateFlow<LibraryRes?> = _library.asStateFlow()

    private val _transport = MutableStateFlow<TransportRes?>(null)
    val transport: StateFlow<TransportRes?> = _transport.asStateFlow()

    private val _lms = MutableStateFlow<LMSRes?>(null)
    val lms: StateFlow<LMSRes?> = _lms.asStateFlow()

    private val _events = MutableStateFlow<EventHubRes?>(null)
    val events: StateFlow<EventHubRes?> = _events.asStateFlow()

    private val _clubs = MutableStateFlow<ClubsRes?>(null)
    val clubs: StateFlow<ClubsRes?> = _clubs.asStateFlow()

    // Temp inputs/states
    val cabShareActive = MutableStateFlow(false)
    val cabShareWaitlisted = MutableStateFlow(false)

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            backstack.add(_currentScreen.value)
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
                                isSuccess = { it.attendance.success && (it.marks?.success != false) },
                                errorMessage = { it.attendance.message ?: it.attendance.error ?: it.marks?.message ?: it.marks?.error },
                                update = {
                                    _attendance.value = it.attendance
                                    it.marks?.let { marks -> _marks.value = marks }
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(semesterId) },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _timetable.value = it }
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
            _syncStatus.value = "Syncing academic and campus data..."
            try {
                val sem = _selectedSemester.value

                val results = supervisorScope {
                    listOf(
                        async {
                            syncModule(
                                name = "Attendance and CGPA",
                                fetch = { AmazeClient.getAcademicData(sem) },
                                isSuccess = { it.attendance.success && (it.marks?.success != false) },
                                errorMessage = { it.attendance.message ?: it.attendance.error ?: it.marks?.message ?: it.marks?.error },
                                update = {
                                    _attendance.value = it.attendance
                                    it.marks?.let { marks -> _marks.value = marks }
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(sem) },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _timetable.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Grade history",
                                fetch = { AmazeClient.getAllGrades() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _allGrades.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Hostel details",
                                fetch = { AmazeClient.getHostelDetails() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _hostelDetails.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Hostel leaves",
                                fetch = { AmazeClient.getHostelLeaves() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _hostelLeaves.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Exam schedule",
                                fetch = { AmazeClient.getExamSchedule() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _examSchedule.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Academic calendar",
                                fetch = { AmazeClient.getCalendar() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _calendar.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Payments",
                                fetch = { AmazeClient.getPayments() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _payments.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Library",
                                fetch = { AmazeClient.getLibrary() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _library.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Transport",
                                fetch = { AmazeClient.getTransport() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _transport.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "LMS",
                                fetch = { AmazeClient.getLMSAssignments() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _lms.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Events",
                                fetch = { AmazeClient.getEventsProfile() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _events.value = it }
                            )
                        },
                        async {
                            syncModule(
                                name = "Clubs",
                                fetch = { AmazeClient.getClubsDetails() },
                                isSuccess = { it.success },
                                errorMessage = { it.message ?: it.error },
                                update = { _clubs.value = it }
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
        _lms.value = null
        _events.value = null
        _clubs.value = null
        _error.value = null
        _syncStatus.value = null
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
}

private data class SyncModuleResult(
    val name: String,
    val success: Boolean,
    val message: String? = null
)
