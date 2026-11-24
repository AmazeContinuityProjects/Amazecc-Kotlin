package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.VtopIngestor
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    EVENTS, QBANK, SOCIAL,
    // D-164: server-backed Friend Finder discovery screen (distinct from SOCIAL).
    FRIEND_FINDER,
    // D-193 follow-up: the real Amaze ID token flow. LOGIN is a VTOP-only form and can never
    // persist an Amaze ID session, so social surfaces park their return target and open this
    // route instead. Normal route (not a gate) so back/dismissal follows the regular stack.
    AMAZE_ID_SIGN_IN,
    FFCS_PLANNER, FREE_CLASSROOMS, CALENDAR, GRADES, GPA_PREDICTOR,
    // D-153: the academic report is a normal, nav-bar-visible screen (not a LIFO overlay).
    STUDENT_REPORT,
    // D-138: the attendance (bunk) predictor is a real route, distinct from GPA_PREDICTOR.
    ATTENDANCE_PREDICTOR,
    COURSE_ATTENDANCE, CIRCULARS, CURRICULUM, OD_TRACKER, COURSE_DASHBOARD,
    FACULTY_INFO,
    FEEDBACK_STATUS, FRESHER_WELCOME, DOCUMENTS, CONTACTS, ABOUT, CLUB_DETAIL,
    COURSE_DETAIL, SETTINGS, MOODLE, CLUB_HUB, TASKS, EXAM_SCHEDULE,
    CHANGELOG, HALL_OF_FAME
}

/**
 * How dense a session card is on the simplified home.
 *
 * [COMPACT] is two lines — slot, title, time, venue, bunk count, percentage.
 * [DETAILED] is spacious and adds the faculty and a bunk subtext. The web's
 * setting is `timetablePillStyle`; the values are carried across verbatim so a
 * habit formed on one build transfers.
 */
enum class HomePillStyle(val value: String) {
    COMPACT("compact"),
    DETAILED("detailed");

    companion object {
        fun fromString(raw: String?): HomePillStyle =
            entries.firstOrNull { it.value == raw } ?: COMPACT
    }
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
    
    // D-136: tabScreens prepends HOME, so the default bar is HOME / ACADEMICS / PROFILE / MORE.
    private val _pinnedNavTabs = MutableStateFlow(listOf(Screen.ACADEMICS, Screen.PROFILE, Screen.MORE))
    val pinnedNavTabs: StateFlow<List<Screen>> = _pinnedNavTabs.asStateFlow()

    // D-131: a reactive owner for the user-selectable nav treatment. The scaffold
    // collects this, so changing the preference recomposes the whole nav immediately.
    private val _navBarStyle = MutableStateFlow(
        SettingsManager.getString("nav_bar_style", "expanding").ifBlank { "expanding" }
    )
    val navBarStyle: StateFlow<String> = _navBarStyle.asStateFlow()

    fun setNavBarStyle(style: String) {
        val normalized = if (style.equals("labelled_docked", ignoreCase = true) ||
            style.equals("docked", ignoreCase = true)
        ) "labelled_docked" else "expanding"
        _navBarStyle.value = normalized
        SettingsManager.setString("nav_bar_style", normalized)
    }

    private val _isAppLibraryOpen = MutableStateFlow(false)
    val isAppLibraryOpen: StateFlow<Boolean> = _isAppLibraryOpen.asStateFlow()

    fun openAppLibrary() {
        _isAppLibraryOpen.value = true
        navigateTo(Screen.MORE)
    }

    fun closeAppLibrary() {
        _isAppLibraryOpen.value = false
        if (_currentScreen.value == Screen.MORE) {
            navigateBack()
        }
    }

    val tabScreens: List<Screen>
        get() = listOf(Screen.HOME) + _pinnedNavTabs.value

    // Sync Notifications
    private val notificationService = com.amazecc.app.shared.services.NotificationService()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

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

    // ── Home screen ──

    private val _homePillStyle = MutableStateFlow(
        HomePillStyle.fromString(SettingsManager.getString(SettingsManager.KEY_HOME_PILL_STYLE).takeIf { it.isNotBlank() })
    )
    val homePillStyle: StateFlow<HomePillStyle> = _homePillStyle.asStateFlow()

    fun setHomePillStyle(style: HomePillStyle) {
        _homePillStyle.value = style
        SettingsManager.setString(SettingsManager.KEY_HOME_PILL_STYLE, style.value)
    }

    private val _homeTasksInline = MutableStateFlow(
        SettingsManager.getBoolean(SettingsManager.KEY_HOME_TASKS_INLINE, true)
    )
    val homeTasksInline: StateFlow<Boolean> = _homeTasksInline.asStateFlow()

    fun setHomeTasksInline(enabled: Boolean) {
        _homeTasksInline.value = enabled
        SettingsManager.setBoolean(SettingsManager.KEY_HOME_TASKS_INLINE, enabled)
    }

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

    /**
     * D-112: clear a settled update status when its check sheet is dismissed. The
     * sheet's callers only clear their own local visibility flag, so without this
     * an Error (or UpToDate) status survives in AppState and reappears the next
     * time the sheet opens. Deliberately does not touch updateDialogDismissedVersion
     * or the Available hand-off owned by [dismissUpdateDialog].
     */
    fun clearUpdateStatus() {
        _updateStatus.value = UpdateStatus.Idle
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
    val ffcsRegistration: StateFlow<FfcsRegistrationInfo?> = AppDataStore.ffcsRegistration

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
    private val _selectedSemester = MutableStateFlow(
        SettingsManager.getString(SettingsManager.KEY_SELECTED_SEMESTER, DEFAULT_SEMESTER_ID)
    )
    val selectedSemester: StateFlow<String> = _selectedSemester.asStateFlow()

    // Loading & Error states (driven by SyncEngine for backward compat)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    // Offline / Local Cache status indicators
    private val _isUsingCachedData = MutableStateFlow(false)
    val isUsingCachedData: StateFlow<Boolean> = _isUsingCachedData.asStateFlow()

    private val _offlineCacheStatus = MutableStateFlow<String?>(null)
    val offlineCacheStatus: StateFlow<String?> = _offlineCacheStatus.asStateFlow()

    fun markServedFromCache(module: String? = null) {
        _isUsingCachedData.value = true
        _offlineCacheStatus.value = if (module != null) "Serving $module from local cache" else "Serving data from local cache"
    }

    fun clearServedFromCache() {
        _isUsingCachedData.value = false
        _offlineCacheStatus.value = null
    }

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

    // Cached Data (all reads/writes go through AppDataStore — the single source of truth)
    val academic: StateFlow<AcademicData> = AppDataStore.academic

    /**
     * The canonical screen-facing model: one [DomainSnapshot] over the store's snapshot and the
     * selected semester.
     *
     * This is the bridge that lets a screen use
     * [com.amazecc.app.shared.domain.Projections] today, while `AppDataStore` still holds
     * `AppDataSnapshot` in memory. It is a pure mapping over a value the store already has - no
     * extra persistence, no second copy on disk - and it replaces a dozen `StateFlow<*Res>`
     * handles with one.
     *
     * When the last screen reads this, `AppDataStore`'s in-memory shape becomes `DomainSnapshot`,
     * `VtopIngestor.fromLegacy`/`toLegacy` are deleted, and this collapses to a plain alias.
     */
    val domain: StateFlow<DomainSnapshot> = combine(AppDataStore.data, selectedSemester) { snapshot, semester ->
        VtopIngestor.fromLegacy(snapshot, semester)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = VtopIngestor.fromLegacy(AppDataStore.data.value, selectedSemester.value),
    )

    private val _currentLiveClass = MutableStateFlow<CourseAttendanceInfo?>(null)
    val currentLiveClass: StateFlow<CourseAttendanceInfo?> = _currentLiveClass.asStateFlow()
    
    private val _liveClassTick = MutableStateFlow(0)
    val liveClassTick: StateFlow<Int> = _liveClassTick.asStateFlow()

    private val settings = Settings()
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    val moodleData: StateFlow<MoodleRes?> = AppDataStore.moodleData

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
                // D-136: MORE is a default pinned tab, so it must survive a reload like any
                // other saved pin. HOME is always prepended by tabScreens and stays excluded.
            }.filter { it != Screen.HOME && it != Screen.ATTENDANCE && it != Screen.LIBRARIES }
            if (tabs.isNotEmpty()) {
                _pinnedNavTabs.value = tabs.take(4)
            }
        }
    }

    /**
     * Load cached data from local storage (single encrypted snapshot via [AppDataStore]).
     * Must be called from an [androidx.compose.runtime.LaunchedEffect] in App() — NOT from init,
     * because many referenced StateFlow properties are declared after the init block.
     */
    fun loadFromCache() {
        AppDataStore.restore()
        UserStore.loadFromCache()
        UserStore.merge(
            IdentityExtractor.fromVtopPhoto(SettingsManager.getString(SettingsManager.CACHE_VTOP_PHOTO)),
            IdentitySource.VTOP_PHOTO
        )
        reconcileExamSeatAlerts()
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
        val prev = AppDataStore.ffcsRegistration.value
        AppDataStore.setFfcsRegistration(info)
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
        AppDataStore.upsertGrades(res)
        mergeSemestersFromAllGrades()
    }

    private fun mergeSemestersFromAllGrades() {
        val ids = (AppDataStore.academic.value.semesters.keys + _selectedSemester.value).distinct()
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

    // ── Save Offline: the store persists every update automatically ──
    fun saveOffline() {
        SyncEngine.resetLogs()
        AppDataStore.persistNow()
        SyncEngine.addLog(SyncModule.ATTENDANCE, "Saved data offline", SyncStatus.SUCCESS)
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

    val hostelDetails: StateFlow<HostelDetails?> = AppDataStore.hostelDetails
    val messMenu: StateFlow<MessMenuRes?> = AppDataStore.messMenu
    val laundrySchedule: StateFlow<LaundryRes?> = AppDataStore.laundrySchedule
    val hostelCounselling: StateFlow<ArrearResponse?> = AppDataStore.hostelCounselling
    private val _selectedExamSemester = MutableStateFlow("CH20262701")
    val selectedExamSemester: StateFlow<String> = _selectedExamSemester.asStateFlow()

    val calendar: StateFlow<CalendarRes?> = AppDataStore.calendar
    val calendarsList: StateFlow<CalendarsListRes?> = AppDataStore.calendarsList
    val qcmView: StateFlow<QcmViewRes?> = AppDataStore.qcmView
    val curriculum: StateFlow<CurriculumRes?> = AppDataStore.curriculum
    val payments: StateFlow<PaymentsRes?> = AppDataStore.payments
    val library: StateFlow<LibraryRes?> = AppDataStore.library

    private val _libraryLoginRequired = MutableStateFlow(false)
    val libraryLoginRequired: StateFlow<Boolean> = _libraryLoginRequired.asStateFlow()

    val transportData: StateFlow<TransportDataRes?> = AppDataStore.transportData
    val buses: StateFlow<BusesRes?> = AppDataStore.buses
    val lms: StateFlow<LMSRes?> = AppDataStore.lms

    // Wired M3E data paths (the caches live in AppDataStore; these are the screen-facing aliases).
    val freeClassrooms: StateFlow<FreeClassroomsRes?> = AppDataStore.freeClassrooms
    /** Full grade payload from /api/remote/grades — includes the published CGPA and curriculum. */
    val allGrades: StateFlow<com.amazecc.app.shared.model.AllGradesRes?> = AppDataStore.allGrades
    val documents: StateFlow<List<DocumentItem>> = AppDataStore.documents
    /** Binary of the most recent document/circular download (transient, never persisted). */
    val circularDownload: StateFlow<CircularDownloadResult?> = AppDataStore.circularDownload
    /** Detail tables of the most recently requested payment receipt (transient). */
    val receiptDetail: StateFlow<ArrearResponse?> = AppDataStore.receiptDetail
    val odDetails: StateFlow<OdDetailsRes?> = AppDataStore.odDetails
    val feedbackStatus: StateFlow<FeedbackStatusRes?> = AppDataStore.feedbackStatus
    val events: StateFlow<EventHubRes?> = AppDataStore.events
    val registeredEvents: StateFlow<EventHubRegisteredEventsRes?> = AppDataStore.registeredEvents
    /** Last event-detail preview; refreshed with [refreshEventPreview]. */
    val eventPreview: StateFlow<EventHubPreview?> = AppDataStore.eventPreview
    /** Outcome of the last [registerForEvent] attempt (success, payment or already registered). */
    val eventRegistration: StateFlow<EventHubRegisterRes?> = AppDataStore.eventRegistration
    val clubs: StateFlow<ClubsRes?> = AppDataStore.clubs

    private val _pastSemestersSynced = MutableStateFlow(SettingsManager.getBoolean(SettingsManager.PAST_SEMESTER_SYNCED, false))
    val pastSemestersSynced: StateFlow<Boolean> = _pastSemestersSynced.asStateFlow()

    // Selected course for detail view
    private val _selectedCourseCode = MutableStateFlow<String?>(null)
    val selectedCourseCode: StateFlow<String?> = _selectedCourseCode.asStateFlow()

    private val _selectedCourseSemester = MutableStateFlow<String?>(null)
    val selectedCourseSemester: StateFlow<String?> = _selectedCourseSemester.asStateFlow()

    fun openCourseDetail(courseCode: String, semesterId: String? = null) {
        _selectedCourseCode.value = courseCode
        _selectedCourseSemester.value = semesterId ?: selectedSemester.value
        if (_currentScreen.value == Screen.COURSE_DETAIL) {
            AppBackStack.replaceTop(historyEntryFor(Screen.COURSE_DETAIL))
        } else {
            navigateTo(Screen.COURSE_DETAIL)
        }
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

    /**
     * D-191: call once the Free Classrooms host has applied [freeRoomTarget] to its search field.
     * Without this the value is write-only, so a second deep link with a different room is ignored.
     * The API name is unchanged; the host owns the one-shot consume.
     */
    fun consumeFreeRoomTarget() {
        _freeRoomTarget.value = null
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

    val cabShareUser: StateFlow<CabShareUser?> = AppDataStore.cabShareUser

    private val _cabShareAuthLoading = MutableStateFlow(false)
    val cabShareAuthLoading: StateFlow<Boolean> = _cabShareAuthLoading.asStateFlow()

    val cabHubs: StateFlow<List<CabShareHub>> = AppDataStore.cabHubs

    // Circulars state
    val circulars: StateFlow<CircularsRes?> = AppDataStore.circulars

    // Tasks state (all task CRUD lives in AppDataStore)
    val tasks: StateFlow<List<HomeworkTask>> = AppDataStore.tasks


    fun addTask(task: HomeworkTask) {
        AppDataStore.addTask(task)
    }

    /** Merges imported backup tasks into the current list (imported wins on id conflict). */
    fun applyImportedTasks(imported: List<HomeworkTask>) {
        AppDataStore.mergeImportedTasks(imported)
    }

    fun updateTask(id: String, transform: (HomeworkTask) -> HomeworkTask) {
        AppDataStore.updateTask(id, transform)
    }

    fun deleteTask(id: String) {
        AppDataStore.removeTask(id)
    }

    fun toggleTaskCompleted(id: String) {
        AppDataStore.toggleTask(id)
    }

    fun toggleSubtaskCompleted(taskId: String, subtaskId: String) {
        AppDataStore.updateTask(taskId) { task ->
            if (task.id == taskId) {
                val updatedSubtasks = task.subtasks.map { sub ->
                    if (sub.id == subtaskId) sub.copy(completed = !sub.completed) else sub
                }
                val allDone = updatedSubtasks.isNotEmpty() && updatedSubtasks.all { it.completed }
                task.copy(subtasks = updatedSubtasks, completed = if (allDone) true else task.completed)
            } else task
        }
    }

    fun addFocusTime(taskId: String, additionalMinutes: Int) {
        AppDataStore.updateTask(taskId) { task ->
            task.copy(actualMinutesSpent = task.actualMinutesSpent + additionalMinutes)
        }
    }

    val todayTasks: List<HomeworkTask>
        get() {
            val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            return AppDataStore.tasks.value.filter { it.dueDate == today && !it.completed }
        }

    /** Web parity (D-071): screens that gate the app — back can never walk out of them. */
    private val gateScreens = setOf(Screen.SPLASH, Screen.LOGIN, Screen.ONBOARDING)

    /**
     * The entry pushed when [screen] is visited (web parity: `useDashboardScreenHistory` pushes
     * the new screenKey). The attendance sub-tab is captured because back/restore returns the
     * user to where they were on that screen.
     */
    private fun historyEntryFor(screen: Screen): AppBackStack.Entry = AppBackStack.Entry(
        screen = screen.name,
        subTab = when (screen) {
            Screen.ATTENDANCE -> _attendanceInitialView.value.takeIf { it.isNotBlank() }
            else -> null
        },
        courseCode = _selectedCourseCode.value.takeIf { screen == Screen.COURSE_DETAIL },
        semesterId = _selectedCourseSemester.value.takeIf { screen == Screen.COURSE_DETAIL }
    )

    /**
     * Applies the sub-tab stored on a history entry when the user returns to that screen
     * (web parity: `restoreScreen`; Kotlin sub-tab state lives in [AppState]-owned fields
     * such as [attendanceInitialView]).
     */
    private fun restoreEntryArguments(entry: AppBackStack.Entry) {
        when (entry.screen) {
            Screen.ATTENDANCE.name -> _attendanceInitialView.value = entry.subTab ?: "Timetable"
            Screen.COURSE_DETAIL.name -> {
                _selectedCourseCode.value = entry.courseCode
                _selectedCourseSemester.value = entry.semesterId
            }
        }
    }

    private fun String.parseScreen(): Screen? = Screen.entries.firstOrNull { it.name == this }

    fun navigateTo(screen: Screen) {
        _isAppLibraryOpen.value = (screen == Screen.MORE)
        headerOverride.value = null
        _headerOverrideOwner.value = null
        if (screen in gateScreens) {
            // Gates clear the history: back cannot walk out of the app through them.
            AppBackStack.clear()
            _currentScreen.value = screen
            return
        }
        if (_currentScreen.value == screen) return
        AppBackStack.push(historyEntryFor(screen))
        _currentScreen.value = screen
    }

    /**
     * Web parity (D-071): tab switches push history like any other navigation, so back
     * returns to the previous tab.
     */
    fun switchTopLevel(screen: Screen) {
        if (_currentScreen.value != screen) {
            AppBackStack.push(historyEntryFor(screen))
            _currentScreen.value = screen
        }
    }

    fun canNavigateBack(): Boolean = AppBackStack.size() >= 2

    fun navigateBackTo(screen: Screen) {
        if (AppBackStack.peek()?.screen == screen.name) {
            AppBackStack.pop()
        }
        _currentScreen.value = screen
    }

    /**
     * D-055: second tap on the active tab returns to that section's root.
     * [collapseFirstTap] folds the first-tap history entry; either way the section's sub-view
     * is reset to its landing value, so the user never lands in a stale detail screen.
     */
    fun goToSectionRoot(screen: Screen, collapseFirstTap: Boolean) {
        if (collapseFirstTap) navigateBackTo(screen) else navigateTo(screen)
        when (screen) {
            Screen.ATTENDANCE -> resetAttendanceView()
            else -> Unit
        }
    }

    fun navigateBack(): Boolean {
        val stack = AppBackStack.entries.value
        if (stack.size >= 2) {
            val target = stack[stack.size - 2]
            AppBackStack.pop()
            restoreEntryArguments(target)
            _currentScreen.value = target.screen.parseScreen() ?: Screen.HOME
            return true
        }
        return false
    }

    /** Web parity (D-071): restores the newest entry on app start, when signed in. */
    fun restoreFromHistory() {
        if (!SessionManager.isLoggedIn) return
        AppBackStack.peek()?.let { entry ->
            restoreEntryArguments(entry)
            _currentScreen.value = entry.screen.parseScreen() ?: Screen.SPLASH
        }
    }

    // ── D-004: web module-registry metadata (metadata-only; no nav behavior change) ──

    /** Web parity (D-004): the surface metadata the web assigns a screen in its module registry. */
    data class ScreenNavMeta(
        val screen: Screen,
        val webIds: Set<String> = emptySet(),
        val pinnable: Boolean = false,
        val pinId: String? = null,
        val pinLabel: String? = null,
        val sidebar: Boolean = false,
        val appLibrary: Boolean = false,
        val commandPalette: Boolean = false,
        val hostellerGated: Boolean = false
    )

    private val screenNavMetaTable: Map<Screen, ScreenNavMeta> = mapOf(
        Screen.HOME to ScreenNavMeta(Screen.HOME, setOf("home"), pinnable = true, pinId = "home", pinLabel = "Home", sidebar = true, appLibrary = true, commandPalette = true),
        Screen.ATTENDANCE to ScreenNavMeta(Screen.ATTENDANCE, setOf("attendance-calendar"), pinnable = true, pinId = "attendance", pinLabel = "Timetable Calendar", sidebar = true, appLibrary = true, commandPalette = true),
        Screen.ACADEMICS to ScreenNavMeta(Screen.ACADEMICS, setOf("academics"), pinnable = true, pinId = "academics", pinLabel = "Academics", sidebar = true, appLibrary = true, commandPalette = true),
        Screen.PAYMENTS to ScreenNavMeta(Screen.PAYMENTS, setOf("payments"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.LIBRARIES to ScreenNavMeta(Screen.LIBRARIES, setOf("libraries"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.TRANSPORT to ScreenNavMeta(Screen.TRANSPORT, setOf("transport", "ds-bus-finder", "ds-transport-reg", "tr-bus-routes", "tr-placements"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.EVENTS to ScreenNavMeta(Screen.EVENTS, setOf("events"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.CLUB_HUB to ScreenNavMeta(Screen.CLUB_HUB, setOf("clubs"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.FACULTY_INFO to ScreenNavMeta(Screen.FACULTY_INFO, setOf("faculty-schedule", "tools-faculty"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.FREE_CLASSROOMS to ScreenNavMeta(Screen.FREE_CLASSROOMS, setOf("free-class"), pinnable = true, sidebar = true, appLibrary = true, commandPalette = true),
        Screen.TASKS to ScreenNavMeta(Screen.TASKS, setOf("tools", "tools-tasks"), pinnable = true, pinId = "tools", pinLabel = "Tools", commandPalette = true),
        Screen.QBANK to ScreenNavMeta(Screen.QBANK, setOf("tools-qbank"), commandPalette = true),
        Screen.SOCIAL to ScreenNavMeta(Screen.SOCIAL, setOf("tools-social"), commandPalette = true),
        // D-164: same surface class as SOCIAL (command-palette discoverable, not a pinned tab).
        Screen.FRIEND_FINDER to ScreenNavMeta(
            Screen.FRIEND_FINDER,
            setOf("friend-finder", "tools-social-friend-finder"),
            commandPalette = true
        ),
        // D-193 follow-up: a transit route for the Amaze ID token sheet, not a discoverable
        // module. Metadata only — no pinnable/sidebar/app-library/command-palette presence.
        Screen.AMAZE_ID_SIGN_IN to ScreenNavMeta(Screen.AMAZE_ID_SIGN_IN),
        Screen.CABSHARE to ScreenNavMeta(Screen.CABSHARE, setOf("tools-cabshare", "cabshare"), pinnable = true, commandPalette = true),
        Screen.PROFILE to ScreenNavMeta(Screen.PROFILE, setOf("profile"), pinnable = true, pinId = "profile", pinLabel = "Profile", sidebar = true, appLibrary = true, commandPalette = true),
        Screen.MORE to ScreenNavMeta(Screen.MORE, setOf("more"), pinnable = true, pinId = "more", pinLabel = "More"),
        Screen.HOSTEL to ScreenNavMeta(Screen.HOSTEL, setOf("hostel", "hostel-overview", "hostel-mess", "hostel-laundry", "hostel-leave", "hostel-counselling"), sidebar = true, commandPalette = true, hostellerGated = true),
    )

    /**
     * Web module-registry metadata mapped onto Kotlin screens (D-004). Metadata only:
     * the Kotlin tab bar still follows [tabScreens]/[pinnedNavTabs]; no surface consumes
     * this table yet, so no behavior change is claimed.
     */
    val navigationRegistry: List<ScreenNavMeta> get() = screenNavMetaTable.values.toList()

    fun navMetaFor(screen: Screen): ScreenNavMeta? = screenNavMetaTable[screen]

    // ── D-057: global keyboard shortcuts ──

    private const val SHORTCUT_SEQUENCE_TIMEOUT_MS = 1500L

    data class AppShortcutTarget(val letter: String, val screen: Screen, val alt: Boolean = false)

    /**
     * Web parity (web `lib/shortcuts.ts` NAV_SHORTCUTS): the "g" + letter navigation set.
     * `alt = true` entries are additionally bound to Alt+letter (web `app/useGlobalShortcuts.ts`).
     */
    val APP_SHORTCUTS: List<AppShortcutTarget> = listOf(
        AppShortcutTarget("h", Screen.HOME, alt = true),
        AppShortcutTarget("a", Screen.ATTENDANCE, alt = true),
        AppShortcutTarget("c", Screen.ACADEMICS, alt = false),
        AppShortcutTarget("g", Screen.GRADES, alt = true),
        AppShortcutTarget("o", Screen.OD_TRACKER, alt = true),
        AppShortcutTarget("e", Screen.EVENTS, alt = true),
        AppShortcutTarget("l", Screen.LIBRARIES, alt = true),
        AppShortcutTarget("p", Screen.PAYMENTS, alt = false),
        AppShortcutTarget("t", Screen.TASKS, alt = false),
        AppShortcutTarget("f", Screen.FREE_CLASSROOMS, alt = false),
        AppShortcutTarget("s", Screen.SETTINGS, alt = true),
    )

    /** Pure, injectable-clock tracker for the "g" + letter sequences (web `createSequenceTracker`). */
    class AppShortcutSequenceTracker(
        private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }
    ) {
        private var pendingAt: Long? = null

        /** Returns the shortcut the key completes, or null. Consumes the "g" prefix itself. */
        fun feed(letter: String): AppShortcutTarget? {
            val now = nowMs()
            val pending = pendingAt
            pendingAt = null
            if (pending != null && now - pending <= SHORTCUT_SEQUENCE_TIMEOUT_MS) {
                return APP_SHORTCUTS.firstOrNull { it.letter.equals(letter, ignoreCase = true) }
            }
            if (letter == "g") {
                pendingAt = now
                return null
            }
            return null
        }

        /** True while a "g" prefix is awaiting its second key (web parity: sequence tracker). */
        fun isPending(): Boolean = pendingAt != null &&
            nowMs() - pendingAt!! <= SHORTCUT_SEQUENCE_TIMEOUT_MS

        fun reset() { pendingAt = null }
    }

    // ── D-057: "?" shortcuts help sheet ──

    private val _shortcutsHelpOpen = MutableStateFlow(false)
    val shortcutsHelpOpen: StateFlow<Boolean> = _shortcutsHelpOpen.asStateFlow()

    fun openShortcutsHelp() { _shortcutsHelpOpen.value = true }
    fun closeShortcutsHelp() { _shortcutsHelpOpen.value = false }

    // ── D-054: guest landing + entry intents (web parity: HomeEntryController/GuestExperience) ──

    enum class EntryIntent { LOGIN, DEMO }

    private val _pendingEntryIntent = MutableStateFlow<EntryIntent?>(null)
    val pendingEntryIntent: StateFlow<EntryIntent?> = _pendingEntryIntent.asStateFlow()

    private val _guestLandingOpen = MutableStateFlow(false)
    val guestLandingOpen: StateFlow<Boolean> = _guestLandingOpen.asStateFlow()

    /**
     * Web parity: `/?signin=1` starts the "login" entry — a guest gets the login form, and a
     * student whose saved credentials stopped working gets the credential editor.
     */
    fun setPendingEntryIntent(intent: EntryIntent) {
        _pendingEntryIntent.value = intent
        if (!SessionManager.isLoggedIn) _guestLandingOpen.value = true
    }

    fun clearPendingEntryIntent() { _pendingEntryIntent.value = null }

    fun openGuestLanding() { _guestLandingOpen.value = true }
    fun closeGuestLanding() { _guestLandingOpen.value = false }

    // ── D-070: LIFO overlay back stack (web parity: src/lib/overlayStack.ts) ──

    /** Id registered for the sync sheet overlay (D-070 / D-086). */
    const val SYNC_SHEET_OVERLAY_ID = "sync-sheet"
    const val COMMAND_PALETTE_OVERLAY_ID = "command-palette"
    const val SHORTCUTS_HELP_OVERLAY_ID = "shortcuts-help"

    private val _overlayStack = MutableStateFlow<List<String>>(emptyList())
    val overlayStack: StateFlow<List<String>> = _overlayStack.asStateFlow()

    fun hasOpenOverlays(): Boolean = _overlayStack.value.isNotEmpty()

    fun pushOverlay(id: String) {
        if (id in _overlayStack.value) return
        _overlayStack.value = _overlayStack.value + id
    }

    fun popOverlay(id: String) {
        if (id in _overlayStack.value) _overlayStack.value = _overlayStack.value.filter { it != id }
    }

    /**
     * Top-only dismissal (LIFO): closes the top overlay and returns true. D-086: Android
     * back/Escape on the sync sheet cancels the sweep via [cancelSync], so the inline sheet's
     * `isSyncing`-based `shouldShow` drops to false and it actually disappears.
     */
    fun closeTopOverlay(): Boolean {
        if (_overlayStack.value.isEmpty()) return false
        val top = _overlayStack.value.last()
        _overlayStack.value = _overlayStack.value.dropLast(1)
        when (top) {
            SYNC_SHEET_OVERLAY_ID -> cancelSync()
            COMMAND_PALETTE_OVERLAY_ID -> closeCommandPalette()
            SHORTCUTS_HELP_OVERLAY_ID -> closeShortcutsHelp()
        }
        return true
    }

    // ── D-069: push prompt auto-open (web parity: PushPromptModal) ──

    /** Web parity: three-day dedup window between push prompts. */
    const val PUSH_PROMPT_THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000L
    /** Web parity: PushPromptModal auto-opens 3s after mount. */
    const val PUSH_PROMPT_DELAY_MS = 3000L
    const val PUSH_PROMPT_LAST_SHOWN_KEY = "push_prompt_last_shown"

    private val _pushPromptOpen = MutableStateFlow(false)
    val pushPromptOpen: StateFlow<Boolean> = _pushPromptOpen.asStateFlow()

    fun openPushPrompt() { _pushPromptOpen.value = true }

    fun closePushPrompt() {
        _pushPromptOpen.value = false
        SettingsManager.setLong(PUSH_PROMPT_LAST_SHOWN_KEY, Clock.System.now().toEpochMilliseconds())
    }

    /** Web parity: `shouldShow = !lastPromptTime || now - lastPromptTime > THREE_DAYS_MS`. */
    fun pushPromptDue(): Boolean {
        val last = SettingsManager.getLong(PUSH_PROMPT_LAST_SHOWN_KEY, 0L)
        return last == 0L || (Clock.System.now().toEpochMilliseconds() - last) > PUSH_PROMPT_THREE_DAYS_MS
    }

    // ── D-072: deep links (web parity: `/?tab=`, share codes; manifest delivery in MainActivity) ──

    sealed class DeepLinkRoute {
        /** A schedule share code, parsed & imported via [com.amazecc.app.shared.utils.SocialUtils]. */
        data class ShareCode(val code: String) : DeepLinkRoute()
        data class Tab(val screen: Screen) : DeepLinkRoute()
        /** `?add=<AMZ-XXXX-XXXX>` — an invite: park the handle and open Social's Add-peer sheet. */
        data class AddPeer(val handle: String) : DeepLinkRoute()
        /** `amazecc://auth-callback/…` — OIDC callback for Amaze ID (D-002, deferred). */
        data class AuthCallback(val raw: String) : DeepLinkRoute()
    }

    /** Web `HANDLE_RE` — Crockford-style alphabet without I/L/O/U (D-193 invite links). */
    private val inviteHandleRe = Regex("^AMZ-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")

    fun isValidInviteHandle(raw: String?): Boolean =
        inviteHandleRe.matches(raw.orEmpty().trim().uppercase())

    private val _incomingDeepLink = MutableStateFlow<String?>(null)
    val incomingDeepLink: StateFlow<String?> = _incomingDeepLink.asStateFlow()

    /**
     * A validated `add=` handle waits here until the Social host opens. It is a one-shot
     * parking slot: [consumePendingInviteHandle] clears it, so a later invite can replace it.
     */
    private val _pendingInviteHandle = MutableStateFlow<String?>(null)
    val pendingInviteHandle: StateFlow<String?> = _pendingInviteHandle.asStateFlow()

    fun parkInviteHandle(handle: String) {
        val normalised = handle.trim().uppercase()
        if (isValidInviteHandle(normalised)) _pendingInviteHandle.value = normalised
    }

    /** Read-and-clear the parked invite; null when none is pending. */
    fun consumePendingInviteHandle(): String? {
        val handle = _pendingInviteHandle.value
        _pendingInviteHandle.value = null
        return handle
    }

    /**
     * Both Android delivery paths land here: an `intent.data` URI and the legacy
     * `target_screen` extra (encoded as `amazecc://tab?screen=<Screen.name>` so the same
     * parser, application and clearing rules cover both). An invalid extra is ignored.
     */
    fun setPendingDeepLink(uri: String? = null, targetScreen: String? = null) {
        _incomingDeepLink.value = when {
            !uri.isNullOrBlank() -> uri.trim()
            !targetScreen.isNullOrBlank() &&
                runCatching { Screen.valueOf(targetScreen.trim().uppercase()) }.getOrNull() != null ->
                "amazecc://tab?screen=${targetScreen.trim().uppercase()}"
            else -> null
        }
    }

    /** Applies a parsed deep link; returns false when nothing routed. */
    fun consumeIncomingDeepLink(): Boolean {
        val uri = _incomingDeepLink.value
        _incomingDeepLink.value = null
        return uri != null && applyDeepLink(uri)
    }

    fun applyDeepLink(uri: String): Boolean {
        val queryPart = uri.trim().substringAfter('?', "")

        // D-193: `?add=<handle>` (web invite link or amazecc:// URI) parks the handle and
        // switches to Social, where the Add-peer sheet is prefilled and looked up once.
        // Query params are read before the host switch so both link families behave alike.
        val addParam = queryPart.substringAfter("add=", "").substringBefore('&').substringBefore('#')
        if (isValidInviteHandle(addParam)) {
            parkInviteHandle(addParam)
            closeGuestLanding()
            navigateTo(Screen.SOCIAL)
            return true
        }
        // A new non-invite link must not resurrect a stale invite on a later Social visit.
        consumePendingInviteHandle()

        // D-164: the web opens Friend Finder with `/?open=friend-finder`. Accept the same
        // `open=<web tab name>` spelling on any amazecc:// URI before the host-based routes,
        // so a web-originated link lands on the real screen instead of being dropped.
        val openTarget = queryPart.substringAfter("open=", "").substringBefore('&')
        if (openTarget.isNotBlank()) {
            screenFromTabParam(openTarget)?.let {
                closeGuestLanding()
                navigateTo(it)
                return true
            }
        }
        return when (val route = parseDeepLink(uri)) {
            is DeepLinkRoute.ShareCode -> {
                val friend = try {
                    com.amazecc.app.shared.utils.SocialUtils.importScheduleCode(route.code)
                } catch (_: Exception) { null }
                // Persist through the same store the Social screen reads — FriendsViewModel
                // owns the friend cache/StateFlow, so bypassing it with a direct write would
                // leave the UI empty after the deep link.
                if (friend?.regNumber?.isNotBlank() == true) FriendsViewModel.addFriendFromCode(route.code)
                closeGuestLanding()
                navigateTo(Screen.SOCIAL)
                true
            }
            is DeepLinkRoute.Tab -> {
                if (route.screen != Screen.SPLASH) {
                    closeGuestLanding()
                    navigateTo(route.screen)
                }
                true
            }
            is DeepLinkRoute.AddPeer -> {
                parkInviteHandle(route.handle)
                closeGuestLanding()
                navigateTo(Screen.SOCIAL)
                true
            }
            is DeepLinkRoute.AuthCallback -> { /* D-002: manifest + login wiring deferred */ true }
            null -> false
        }
    }

    /**
     * Parses `amazecc://<host>[/path][?query][#fragment]` deep links. Routes:
     * - `amazecc://share?code=<code>` / `amazecc://share#s=<code>` — schedule share code
     *   (the reader at [com.amazecc.app.shared.utils.SocialUtils.importScheduleCode] accepts
     *   `v6|…` codes and `#s=`/`s=`/`#share=`/`share=` encodings — web parity, D-007)
     * - `amazecc://tab?tab=<Screen>` — web parity: `/?tab=` — open a tab
     * - `amazecc://tab?open=<Screen>` / `amazecc://open?open=<Screen>` — web parity:
     *   `/?open=friend-finder` (D-164); [applyDeepLink] also accepts `open=` on any host.
     * - `?add=<AMZ-XXXX-XXXX>` (D-193) — an invite, parked for Social's Add-peer sheet.
     * - `amazecc://tab?screen=<Screen.name>` — the legacy Android `target_screen` extra,
     *   encoded by [setPendingDeepLink] so it uses the same pending queue.
     * - `amazecc://auth-callback/…` — OIDC callback (D-002, deferred)
     */
    fun parseDeepLink(uri: String): DeepLinkRoute? {
        val trimmed = uri.trim()
        // The web invite link is https://amazecc.app/?add=… — not an amazecc:// URI. Accept it
        // (and the scheme form) by reading the query before the host-based routes.
        val addParam = trimmed.substringAfter('?', "").substringAfter("add=", "")
            .substringBefore('&').substringBefore('#')
        if (isValidInviteHandle(addParam)) return DeepLinkRoute.AddPeer(addParam.trim().uppercase())

        val match = Regex("^amazecc://([^/?#]+)([^#]*)(#.*)?$").find(trimmed) ?: return null
        val host = match.groupValues[1].lowercase()
        val query = match.groupValues[2]
        return when (host) {
            "share" -> {
                val code = query.substringAfter("code=", "").ifBlank { match.groupValues[3] }
                if (code.isBlank()) null else DeepLinkRoute.ShareCode(code)
            }
            "tab" -> screenFromTabParam(
                query.substringAfter("tab=", "").ifBlank { query.substringAfter("open=", "") }
            )?.let { DeepLinkRoute.Tab(it) }
                ?: query.substringAfter("screen=", "").takeIf { it.isNotBlank() }
                    ?.let { runCatching { Screen.valueOf(it.trim().uppercase()) }.getOrNull() }
                    ?.let { DeepLinkRoute.Tab(it) }
            "open" -> screenFromTabParam(query.substringAfter("open=", ""))?.let { DeepLinkRoute.Tab(it) }
            "auth-callback" -> DeepLinkRoute.AuthCallback(trimmed)
            else -> null
        }
    }

    /** Maps the web `/?tab=` names onto Kotlin [Screen]s; null when no Kotlin screen exists. */
    fun screenFromTabParam(raw: String): Screen? = raw.trim().lowercase().let { param ->
        when (param) {
            "home" -> Screen.HOME
            "attendance" -> Screen.ATTENDANCE
            "academics" -> Screen.ACADEMICS
            "payments" -> Screen.PAYMENTS
            "libraries" -> Screen.LIBRARIES
            "transport" -> Screen.TRANSPORT
            "free-class" -> Screen.FREE_CLASSROOMS
            "tools" -> Screen.TASKS
            "hostel" -> Screen.HOSTEL
            "more" -> Screen.MORE
            "profile" -> Screen.PROFILE
            "events" -> Screen.EVENTS
            "clubs" -> Screen.CLUB_HUB
            "about" -> Screen.ABOUT
            "qa-bank" -> Screen.QBANK
            "friend-finder" -> Screen.FRIEND_FINDER  // D-164: web /?open=friend-finder
            else -> null  // "community", "student-report", ... — no Kotlin screen
        }
    }

    // ── D-062 (deferred): exam schedule actions ──
    // M3EExamScheduleScreen/Host are not in the owning file list; copy-codes, sort, .ics
    // download and calendar import stay deferred to a follow-up touching them.

    // ── Privacy mode toggle (companion to D-008; blur application is D-008's own work) ──

    private val _privacyMode = MutableStateFlow(SettingsManager.getBoolean("privacy_mode", false))
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    fun setPrivacyMode(enabled: Boolean) {
        _privacyMode.value = enabled
        SettingsManager.setBoolean("privacy_mode", enabled)
    }

    fun togglePrivacyMode() { setPrivacyMode(!_privacyMode.value) }

    /** Web parity (Alt+T): toggle light/dark (web: `setTheme(theme === "dark" ? "light" : "dark")`). */
    fun toggleAppTheme() {
        _theme.value = if (_theme.value in setOf(AppTheme.DARK, AppTheme.AMOLED)) AppTheme.LIGHT else AppTheme.DARK
        SettingsManager.setString(SettingsManager.KEY_APP_THEME, _theme.value.name)
    }

    private var pendingSemesterSwitch: String? = null

    fun selectSemester(semesterId: String) {
        _selectedSemester.value = semesterId
        _selectedExamSemester.value = semesterId
        SettingsManager.setString(SettingsManager.KEY_SELECTED_SEMESTER, semesterId)
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
                            isSuccess = { it.attendance.error == null },
                            errorMessage = { it.attendance.error },
                            update = {
                                AppDataStore.upsertAttendance(semesterId, it.attendance)
                                if (it.marks?.error == null) {
                                    it.marks?.let { marks ->
                                        AppDataStore.upsertMarks(semesterId, marks)
                                    }
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
                                AppDataStore.upsertTimetable(semesterId, it)
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
                                AppDataStore.setCalendarsList(it)
                            }
                        )
                    }
                ).awaitAll()
            }
            updateSyncSummary(results)
        }
    }

    private var sweepJob: Job? = null

    /**
     * Gives a sweep a genuinely fresh VTOP session to work with, before any module runs.
     *
     * This is the single login choke point for a sweep: it forces a real VTOP prelogin/setup ->
     * login (with captcha) -> open page -> authorizedID, persisting fresh cookies/csrf/authorizedID.
     * A cached-fresh session is never accepted here. Modules do their own lightweight verification
     * from the session established by this one call, so there is exactly one login per sweep.
     *
     * Skipped entirely for the two sweeps that need no VTOP session (LMS / MOODLE only) and for
     * [com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE].
     *
     * Deliberate background degradation: when there is no foreground Activity (e.g. an alarm
     * `BroadcastReceiver`), `SyncEngine.ensureAuthenticatedSession` cannot clear cookies and start
     * a fresh interactive login — there is no window to host the VTOP WebView and no UI to answer a
     * captcha. It verifies the cached session instead. On failure this reports the single
     * "Sign in again to sync" error below rather than throwing; due syncs are caught up from
     * `MainActivity.onResume()` while an Activity exists so the strict login still runs in normal use.
     */
    private suspend fun ensureVtopSessionForSweep(modules: Set<SyncModule>): Boolean {
        if (AmazeClient.vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return true
        val needsVtop = modules.any { it != SyncModule.LMS && it != SyncModule.MOODLE }
        if (!needsVtop) return true

        val authenticated = runCatching {
            SyncEngine.ensureAuthenticatedSession(modules, forceFresh = true)
        }.getOrElse { false }

        if (authenticated) return true

        val message = "Your VTOP session has expired. Sign in again to sync."
        _error.value = message
        _syncMessage.value = message
        _syncStatus.value = message
        modules.forEach {
            SyncEngine.markModuleError(it, message)
            SyncEngine.addLog(it, message, SyncStatus.ERROR)
        }
        return false
    }

    private fun launchSweep(modules: Set<SyncModule>, stageProfile: String? = null, block: suspend () -> Unit = {}) {
        if (sweepJob?.isActive == true) {
            _syncMessage.value = "A sync is already running — tap again after it finishes"
            return
        }
        sweepJob = scope.launch {
            val thisJob = currentCoroutineContext()[Job]
            _isLoading.value = true
            _isSyncing.value = true
            _syncMessage.value = "Starting sync (${modules.size} modules)"
            SyncEngine.beginSweep(modules)
            if (thisJob != null) SyncEngine.registerJob(thisJob)
            try {
                // No session means every module below would report the same "No VTOP session"
                // against itself. Stopping here turns fifteen identical lines into one sentence
                // that says what to do about it.
                val authenticated = SyncEngine.networkScope(null, "VTOP session login") {
                    ensureVtopSessionForSweep(modules)
                }
                if (!authenticated) return@launch
                if (stageProfile != null) {
                    SyncEngine.withStageProfile(stageProfile) { block() }
                } else {
                    block()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Sync failed: ${e::class.simpleName}: ${e.message}"
                SyncEngine.addLog(SyncModule.STUDENT_PROFILE, "Sync failed: ${e.message}", SyncStatus.ERROR)
            } finally {
                if (thisJob != null) SyncEngine.unregisterJob(thisJob)
                SyncEngine.endSweep()
                _isLoading.value = false
                _isSyncing.value = false
                // A semester switch requested mid-sweep must run *after* this sweep finishes.
                // Calling it from inside the sweep only re-queued it (the _isLoading guard) and
                // it was never consumed. Do not run it for a cancelled sweep.
                if (thisJob?.isCancelled == false) consumePendingSemesterSwitch()
            }
        }
    }

    private fun consumePendingSemesterSwitch() {
        val semester = pendingSemesterSwitch ?: return
        pendingSemesterSwitch = null
        sweepJob = null
        _syncMessage.value = "Refreshing data for new semester..."
        loadSemesterData(semester)
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

    fun loadMode(modeId: String, force: Boolean = false) {
        if (_isLoading.value) return
        val mode = SyncEngine.resolveMode(modeId) ?: SyncEngine.resolveMode("quick") ?: return
        val stepsToRun = if (force) mode.steps else mode.steps.filter { !SyncEngine.isFresh(it, force = false) }
        val sweepModules = stepsToRun.map { it.module }.toSet()
        if (sweepModules.isEmpty()) {
            _syncStatus.value = "All modules in ${mode.label} are up-to-date"
            return
        }
        val stageProfile = mode.id
        launchSweep(sweepModules, stageProfile = stageProfile) {
            _error.value = null
            _syncMessage.value = "Running ${mode.label}..."
            _syncStatus.value = "Running ${mode.label}..."
            notificationService.showLoadingNotification("AmazeCC Sync", "Running ${mode.label}...")
            try {
                val sem = _selectedSemester.value
                val results = supervisorScope {
                    val jobs = mutableListOf<kotlinx.coroutines.Deferred<SyncModuleResult>>()
                    if (SyncModule.ATTENDANCE in sweepModules) {
                        jobs += async {
                            syncModule("Attendance", { AmazeClient.getAcademicData(sem) }, { it.attendance.error == null }, { it.attendance.error }) {
                                AppDataStore.upsertAttendance(sem, it.attendance)
                                if (it.marks?.error == null && it.marks != null) {
                                    AppDataStore.upsertMarks(sem, it.marks)
                                }
                            }
                        }
                    }
                    if (SyncModule.TIMETABLE in sweepModules) {
                        jobs += async {
                            syncModule("Timetable", { AmazeClient.getTimetable(sem) }, { it.error == null }, { it.error }) {
                                AppDataStore.upsertTimetable(sem, it)
                            }
                        }
                    }
                    if (SyncModule.MARKS in sweepModules && SyncModule.ATTENDANCE !in sweepModules) {
                        jobs += async {
                            syncModule("Marks", { AmazeClient.getMarks(sem) }, { it.error == null }, { it.error }) {
                                AppDataStore.upsertMarks(sem, it)
                            }
                        }
                    }
                    if (SyncModule.GRADES in sweepModules) {
                        jobs += async {
                            syncModule("Grade history", { AmazeClient.getAllGrades() }, { it.error == null }, { it.error }) {
                                applyAllGrades(it)
                            }
                        }
                    }
                    if (SyncModule.EXAM_SCHEDULE in sweepModules) {
                        jobs += async {
                            syncModule("Exam schedule", { AmazeClient.getExamSchedule(semesterId = sem) }, { it.error == null }, { it.error }) {
                                AppDataStore.upsertExams(sem, it)
                            }
                        }
                    }
                    if (SyncModule.HOSTEL_DETAILS in sweepModules) {
                        jobs += async {
                            syncModule("Hostel details", { AmazeClient.getHostelDetails() }, { it.error == null }, { it.error }) {
                                AppDataStore.setHostelDetails(it)
                            }
                        }
                    }
                    if (SyncModule.CURRICULUM in sweepModules) {
                        jobs += async {
                            syncModule("Curriculum", { AmazeClient.getCurriculum(semesterId = sem) }, { it.error == null }, { it.error }) {
                                AppDataStore.setCurriculum(it)
                            }
                        }
                    }
                    if (SyncModule.CALENDAR in sweepModules) {
                        jobs += async {
                            syncModule("Academic calendar", { AmazeClient.getCalendar(semesterId = sem) }, { it.error == null }, { it.error }) {
                                AppDataStore.setCalendar(it)
                            }
                        }
                    }
                    if (SyncModule.CALENDARS_LIST in sweepModules) {
                        jobs += async {
                            syncModule("Calendars list", { AmazeClient.getCalendars(semesterId = sem) }, { it.error == null }, { it.error }) {
                                AppDataStore.setCalendarsList(it)
                            }
                        }
                    }
                    if (SyncModule.PAYMENTS in sweepModules) {
                        jobs += async {
                            syncModule("Payments", { AmazeClient.getPayments() }, { it.error == null }, { it.error }) {
                                AppDataStore.setPayments(it)
                            }
                        }
                    }
                    if (SyncModule.LIBRARY in sweepModules) {
                        jobs += async {
                            val creds = SettingsManager.getLibraryCredentials()
                            if (creds == null) {
                                SyncEngine.markModuleError(SyncModule.LIBRARY, "NO_LIB_CREDS")
                                SyncModuleResult("Library", false, "NO_LIB_CREDS")
                            } else {
                                syncModule("Library", { AmazeClient.getLibrary(creds.first, creds.second) }, { it.error == null }, { it.error }) {
                                    AppDataStore.setLibrary(it)
                                }
                            }
                        }
                    }
                    if (SyncModule.TRANSPORT in sweepModules) {
                        jobs += async {
                            syncModule("Transport Data", { AmazeClient.getTransportData() }, { it.error == null }, { it.error }) {
                                AppDataStore.setTransportData(it)
                            }
                        }
                    }
                    if (SyncModule.BUSES in sweepModules) {
                        jobs += async {
                            syncModule(
                                "Buses",
                                { AmazeClient.getBuses() },
                                { AmazeClient.busesSucceeded(it) },
                                { AmazeClient.busesFailureMessage(it) }
                            ) {
                                AppDataStore.setBuses(it)
                            }
                        }
                    }
                    if (SyncModule.LMS in sweepModules) {
                        jobs += async {
                            syncModule("LMS", { AmazeClient.getLMSAssignments() }, { it.error == null }, { it.error }) {
                                AppDataStore.setLms(it)
                            }
                        }
                    }
                    if (SyncModule.EVENTS in sweepModules) {
                        jobs += async {
                            syncModule("Registered Events", { AmazeClient.getEvents() }, { it.error == null }, { it.error }) {
                                AppDataStore.setEvents(it)
                            }
                        }
                    }
                    if (SyncModule.CLUBS in sweepModules) {
                        jobs += async {
                            syncModule("Clubs", { AmazeClient.getClubs() }, { it.error == null }, { it.error }) {
                                AppDataStore.setClubs(it)
                            }
                        }
                    }
                    if (SyncModule.QCM_VIEW in sweepModules) {
                        jobs += async {
                            syncModule("QCM View", { AmazeClient.getQcmView() }, { it.error == null }, { it.error }) {
                                AppDataStore.setQcmView(it)
                            }
                        }
                    }
                    if (SyncModule.CIRCULARS in sweepModules) {
                        jobs += async {
                            syncModule("Circulars", { AmazeClient.getCirculars() }, { it.error == null }, { it.error }) {
                                AppDataStore.setCirculars(it)
                            }
                        }
                    }
                    // D-156: manual Sync (Quick/Daily/Weekly/...) steps include FEEDBACK_STATUS,
                    // so the mode sweep must run the same canonical module pipeline as Sync All.
                    if (SyncModule.FEEDBACK_STATUS in sweepModules) {
                        jobs += async { syncFeedbackStatusModule(sem) }
                    }
                    if (SyncModule.MOODLE in sweepModules) {
                        jobs += async {
                            val creds = SettingsManager.getMoodleCredentials()
                            if (creds == null) {
                                SyncEngine.markModuleError(SyncModule.MOODLE, "NO_MOODLE_CREDS")
                                SyncModuleResult("Moodle Assignments", false, "NO_MOODLE_CREDS")
                            } else {
                                syncModule("Moodle Assignments", { AmazeClient.fetchMoodleData(creds.first, creds.second) }, { it.error == null }, { it.error }) {
                                    AppDataStore.setMoodle(it)
                                }
                            }
                        }
                    }
                    val idModules = sweepModules.intersect(setOf(
                        SyncModule.STUDENT_PROFILE, SyncModule.PROFILE_IMAGES, SyncModule.CREDENTIALS,
                        SyncModule.BANK_INFO, SyncModule.APAAR_ID, SyncModule.DAYBOARDER,
                        SyncModule.EPT_SCHEDULE, SyncModule.REGISTRATION_SCHEDULE, SyncModule.UNIVERSITY_DAY
                    ))
                    if (idModules.isNotEmpty()) {
                        jobs += async {
                            syncIdentityModules()
                            SyncModuleResult("Identity", true)
                        }
                    }
                    jobs.awaitAll()
                }
                updateSyncSummary(results)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Sync failed: ${e::class.simpleName}: ${e.message}"
                SyncEngine.addLog(SyncModule.STUDENT_PROFILE, "Mode sync failed: ${e.message}", SyncStatus.ERROR)
            } finally {
                _isLoading.value = false
                _isSyncing.value = false
            }
        }
    }

    /** D-029: single-module retry — re-run one module immediately from the progress sheet. */
    fun retryModule(module: SyncModule) {
        if (_isLoading.value) return
        // D-156: feedback has a real single-module pipeline. The generic empty-block launchSweep
        // would mark the module ERROR ("No result reported by module") without ever fetching.
        if (module == SyncModule.FEEDBACK_STATUS) {
            refreshFeedbackStatus()
            return
        }
        launchSweep(setOf(module))
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
            "APAAR ID", "Circulars", "Moodle Assignments",
            // Page-local refresh effects are gone: the central sweep is now the only place
            // these campus/academic modules get fetched, so they must be part of it.
            "Free Classrooms", "Documents", "OD Details", "Feedback Status"
            // Do NOT filter by the user's active profile here: a scheduled full/light run applies
            // its own stage profile *inside* launchSweep. syncModule() gates each module against
            // that staged profile and records disabled ones as SKIPPED.
        ).mapNotNull { SyncEngine.moduleOf(it) }.toSet()

        launchSweep(sweepModules, stageProfile = stageProfile) {
            _error.value = null
            _syncMessage.value = "Refreshing VTOP session..."
            _syncStatus.value = "Refreshing VTOP session..."
            notificationService.showLoadingNotification("AmazeCC Sync", "Refreshing VTOP session...")
            var sweepSucceeded = false
            try {
                _syncMessage.value = "Syncing academic and campus data..."
                _syncStatus.value = "Syncing academic and campus data..."
                val sem = _selectedSemester.value

                // D-031: Foreground sequential with critical abort — web parity
                // (`sync-engine/index.ts:230-311`): the daily check runs one module
                // at a time; a critical failure aborts the remaining foreground steps.
                var criticalAborted = false
                val fgResults = mutableListOf<SyncModuleResult>()
                fgResults += syncModule(
                    name = "Attendance",
                    fetch = { AmazeClient.getAcademicData(sem) },
                    isSuccess = { it.attendance.error == null },
                    errorMessage = { it.attendance.error },
                    update = {
                        AppDataStore.upsertAttendance(sem, it.attendance)
                        if (it.marks?.error == null) {
                            it.marks?.let { marks ->
                                AppDataStore.upsertMarks(sem, marks)
                            }
                        }
                    }
                ).also { if (!it.success) criticalAborted = true }
                if (criticalAborted) {
                    fgResults += SyncModuleResult("Timetable", true, "SKIPPED: critical abort on earlier module")
                    fgResults += SyncModuleResult("Grade history", true, "SKIPPED: critical abort on earlier module")
                } else {
                    fgResults += syncModule(
                        name = "Timetable",
                        fetch = { AmazeClient.getTimetable(sem) },
                        isSuccess = { it.error == null },
                        errorMessage = { it.error },
                        update = { AppDataStore.upsertTimetable(sem, it) }
                    )
                    fgResults += syncModule(
                        name = "Grade history",
                        fetch = { AmazeClient.getAllGrades() },
                        isSuccess = { it.error == null },
                        errorMessage = { it.error },
                        update = { applyAllGrades(it) }
                    )
                }

                val results = supervisorScope {
                    val syncResults = listOf(
                        async {
                            val module = SyncModule.ALL_SEMESTER_ATTENDANCE
                            if (!SyncEngine.isModuleEnabled(module)) {
                                SyncEngine.markModuleSkipped(module, "Disabled by the active sync profile")
                                SyncModuleResult("All Semesters Attendance", true, "SKIPPED: disabled by active profile")
                            } else if (_pastSemestersSynced.value) {
                                // Honest: no fetch happened, so do not stamp lastSynced = now.
                                SyncEngine.markModuleSkipped(module, "Already synced in an earlier run; no fetch performed")
                                SyncModuleResult("All Semesters Attendance", true, "SKIPPED: already synced previously")
                            } else {
                                var failed = 0
                                val gradeSemIds = AppDataStore.academic.value.semesters.keys.filter { it != sem }
                                val allSemIds = (gradeSemIds + semesterIDs).distinct().filter { it != sem }
                                for (semId in allSemIds) {
                                    try {
                                        val res = SyncEngine.networkScope(module, "Past semester $semId") {
                                            AmazeClient.getAcademicData(semId)
                                        }
                                        if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                            AppDataStore.upsertAttendance(semId, res.attendance)
                                        }
                                        if (res.marks?.error == null && res.marks?.marks?.isNotEmpty() == true) {
                                            AppDataStore.upsertMarks(semId, res.marks)
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failed++
                                        SyncEngine.addLog(module, "Past semester $semId failed: ${e.message}", SyncStatus.ERROR)
                                    }
                                }
                                // Only latch success when every semester actually fetched.
                                if (failed == 0) {
                                    SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                                    _pastSemestersSynced.value = true
                                    SyncEngine.markModuleSuccess(module, "fetched ${allSemIds.size} past semester(s)")
                                    SyncModuleResult("All Semesters Attendance", true)
                                } else {
                                    SyncEngine.markModuleError(module, "$failed of ${allSemIds.size} past semesters failed")
                                    SyncModuleResult("All Semesters Attendance", false, "$failed past semester(s) failed")
                                }
                            }
                        },
                        async {
                            syncModule(
                                name = "Curriculum",
                                fetch = { AmazeClient.getCurriculum(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    AppDataStore.setCurriculum(it)
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
                                    AppDataStore.setHostelDetails(it)
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
                                    AppDataStore.upsertExams(sem, it)
                                }
                            )
                        },
                        async {
                            // This block only fills *past* semesters. The module's own state is
                            // owned by the "Exam schedule" syncModule above — writing it here was
                            // a race that could overwrite a real ERROR with a green SUCCESS.
                            if (!SyncEngine.isModuleEnabled(SyncModule.EXAM_SCHEDULE)) {
                                SyncModuleResult("All Semesters Exam Schedule", true, "SKIPPED: disabled by active profile")
                            } else {
                                var failed = 0
                                var attempted = 0
                                for (semId in semesterIDs) {
                                    if (semId == sem) continue
                                    attempted++
                                    try {
                                        val res = SyncEngine.networkScope(SyncModule.EXAM_SCHEDULE, "Past exam schedule $semId") {
                                            AmazeClient.getExamSchedule(semesterId = semId)
                                        }
                                        if (res.error == null && res.schedule.isNotEmpty()) {
                                            AppDataStore.upsertExams(semId, res)
                                        } else {
                                            failed++
                                            SyncEngine.addLog(SyncModule.EXAM_SCHEDULE, "Past schedule $semId: ${res.error ?: "empty"}", SyncStatus.ERROR)
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failed++
                                        SyncEngine.addLog(SyncModule.EXAM_SCHEDULE, "Past schedule $semId failed: ${e.message}", SyncStatus.ERROR)
                                    }
                                }
                                if (attempted == 0) {
                                    SyncModuleResult("All Semesters Exam Schedule", true, "SKIPPED: no past semesters")
                                } else {
                                    SyncModuleResult("All Semesters Exam Schedule", failed == 0, if (failed > 0) "$failed past schedule(s) failed" else null)
                                }
                            }
                        },
                        async {
                            syncModule(
                                name = "Academic calendar",
                                fetch = { AmazeClient.getCalendar(semesterId = sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    AppDataStore.setCalendar(it)
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
                                    AppDataStore.setCalendarsList(it)
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
                                    AppDataStore.setPayments(it)
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
                                    AppDataStore.setLibrary(it)
                                    _libraryLoginRequired.value = false
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
                                    AppDataStore.setTransportData(it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Buses",
                                fetch = { AmazeClient.getBuses() },
                                isSuccess = { AmazeClient.busesSucceeded(it) },
                                errorMessage = { AmazeClient.busesFailureMessage(it) },
                                update = {
                                    AppDataStore.setBuses(it)
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
                                    AppDataStore.setLms(it)
                                }
                            )
                        },
                        async {
                            val moodleCreds = SettingsManager.getMoodleCredentials()
                            if (moodleCreds == null) {
                                SyncEngine.markModuleSkipped(SyncModule.MOODLE, "Moodle credentials not configured")
                                SyncModuleResult("Moodle Assignments", true, "SKIPPED: Moodle credentials not configured")
                            } else {
                                syncModule(
                                    name = "Moodle Assignments",
                                    fetch = { AmazeClient.fetchMoodleData(moodleCreds.first, moodleCreds.second) },
                                    isSuccess = { it.success },
                                    errorMessage = { it.error ?: it.message },
                                    update = {
                                    AppDataStore.setMoodle(it)
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
                                        AppDataStore.setRegisteredEvents(it)
                                    }
                                )
                            } else {
                                SyncEngine.markModuleSkipped(SyncModule.EVENTS, "EventHub club token not available")
                                SyncModuleResult("Registered Events", true, "SKIPPED: EventHub club token not available")
                            }
                        },
                        async {
                            syncModule(
                                name = "Clubs",
                                fetch = { AmazeClient.getClubsDetails() },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    AppDataStore.setClubs(it)
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
                                    AppDataStore.setQcmView(it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Free Classrooms",
                                fetch = { AmazeClient.getFreeClassrooms() },
                                isSuccess = { it.success },
                                errorMessage = { it.error ?: it.message },
                                update = { AppDataStore.setFreeClassrooms(it) }
                            )
                        },
                        async {
                            syncModule(
                                name = "Documents",
                                fetch = { AmazeClient.getDocuments() },
                                isSuccess = { it.isNotEmpty() },
                                errorMessage = { "No documents available" },
                                update = { AppDataStore.setDocuments(it) }
                            )
                        },
                        async {
                            syncModule(
                                name = "OD Details",
                                fetch = { AmazeClient.getOdDetails(sem) },
                                isSuccess = { it.success && it.error == null },
                                errorMessage = { it.error ?: "OD details unavailable" },
                                update = { AppDataStore.setOdDetails(it) }
                            )
                        },
                        async { syncFeedbackStatusModule(sem) },
                        async {
                            val anyIdentityEnabled = ConsolidatedIdentityModuleNames.any { name ->
                                SyncEngine.moduleOf(name)?.let { SyncEngine.isModuleEnabled(it) } ?: false
                            }
                            if (!anyIdentityEnabled) {
                                SyncModuleResult("Student Profile", true, "SKIPPED: all profile modules disabled")
                            } else {
                                val profResults = syncIdentityModules()
                                profResults.firstOrNull { !it.success }
                                    ?: profResults.firstOrNull { it.message?.startsWith("SKIPPED: ") == true }
                                    ?: SyncModuleResult("Student Profile", true)
                            }
                        },
                        async {
                            syncModule(
                                name = "Circulars",
                                fetch = { AmazeClient.getCirculars() },
                                isSuccess = { it.success },
                                errorMessage = { it.error ?: it.message },
                                update = {
                                    AppDataStore.setCirculars(it)
                                }
                            )
                        }
                    ).awaitAll()

                    // ── Gap-fill: fetch attendance/marks for any semester in academic that we missed ──
                    val grades = AppDataStore.academic.value.semesters.keys
                    if (grades.isNotEmpty()) {
                        val missingSemIds = grades
                            .filter { it != sem }
                            .filter { semId ->
                                val semData = AppDataStore.academic.value.semesters[semId]
                                semData == null || semData.courses.values.none { it.marks != null }
                            }
                        var gapFailures = 0
                        for (semId in missingSemIds) {
                            try {
                                val res = SyncEngine.networkScope(SyncModule.ALL_SEMESTER_ATTENDANCE, "Gap-fill $semId") {
                                    AmazeClient.getAcademicData(semId)
                                }
                                if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                    AppDataStore.upsertAttendance(semId, res.attendance)
                                }
                                if (res.marks?.error == null && res.marks?.marks?.isNotEmpty() == true) {
                                    AppDataStore.upsertMarks(semId, res.marks)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                gapFailures++
                                SyncEngine.addLog(SyncModule.ALL_SEMESTER_ATTENDANCE, "Gap-fill $semId failed: ${e.message}", SyncStatus.ERROR)
                            }
                        }
                        if (gapFailures == 0) {
                            SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                            _pastSemestersSynced.value = true
                        }
                    }
                    fgResults + syncResults
                }
                updateSyncSummary(results)
                sweepSucceeded = results.none { !it.success } && SyncEngine.syncProgress.value.errorCount == 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                _syncMessage.value = null
                if (scheduledFor != null) {
                    // Only a genuinely successful run may stamp "last synced". A failed or
                    // cancelled run still re-arms so it can retry at the next window.
                    if (sweepSucceeded) SyncScheduler.markSynced()
                    SyncScheduler.advanceAndArm(scheduledFor)
                }
                if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    notificationService.showLoadingNotification(
                        "AmazeCC Sync",
                        if (sweepSucceeded) "Sync completed" else "Sync finished with problems"
                    )
                    scheduleReminders()
                }
            }
        }
    }

    private fun scheduleReminders() {
        val courses = AppDataStore.academic.value.semesters[_selectedSemester.value]?.courses?.values?.toList().orEmpty()
        val assignments = AppDataStore.lms.value?.assignments
        val moodleAssignments = AppDataStore.moodleData.value?.data?.map { a ->
            // The Moodle module has its own shape; adapt it onto the LMS one so both feed the same
            // reminder path. `done` is the real submission flag - the old DTO carried a "status"
            // string that no route ever produced.
            LMSAssignment(
                name = "${a.courseCode}/${a.taskTitle}",
                courseCode = a.courseCode,
                assignmentTitle = a.taskTitle,
                due = a.due,
                done = a.done,
            )
        } ?: emptyList()
        val allAssignments = (assignments ?: emptyList()) + moodleAssignments
        val examsForSem = AppDataStore.academic.value.semesters[_selectedExamSemester.value]?.exams
        val selectedExams = examsForSem.orEmpty()
        val exams = if (selectedExams.isNotEmpty()) selectedExams
            else AppDataStore.academic.value.semesters.values.flatMap { it.exams }
        com.amazecc.app.shared.utils.NotificationsUtils.scheduleAll(
            attendance = com.amazecc.app.shared.utils.NotificationsUtils.buildAttendanceMaps(courses).takeIf { it.isNotEmpty() },
            slotMap = com.amazecc.app.shared.utils.NotificationsUtils.typedSlotMap(),
            assignments = allAssignments,
            tasks = AppDataStore.tasks.value,
            exams = if (exams.isEmpty()) null else exams,
            registration = AppDataStore.ffcsRegistration.value
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
                                    AppDataStore.upsertAttendance(sem, it.attendance)
                                    if (it.marks?.error == null) {
                                        it.marks?.let { marks ->
                                            AppDataStore.upsertMarks(sem, marks)
                                        }
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
                                    AppDataStore.upsertTimetable(sem, it)
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
                                    AppDataStore.upsertAttendance(sem, it.attendance)
                                    if (it.marks?.error == null) {
                                        it.marks?.let { m ->
                                            AppDataStore.upsertMarks(sem, m)
                                        }
                                    }
                                }
                            )
                        },
                        async {
                            val module = SyncModule.ALL_SEMESTER_ATTENDANCE
                            if (_pastSemestersSynced.value) {
                                SyncEngine.markModuleSkipped(module, "Already synced in an earlier run; no fetch performed")
                                SyncModuleResult("All Semesters", true, "SKIPPED: already synced previously")
                            } else {
                                var failed = 0
                                val gradeSemIds = AppDataStore.academic.value.semesters.keys.filter { it != sem }
                                val allSemIds = (gradeSemIds + semesterIDs).distinct().filter { it != sem }
                                for (semId in allSemIds) {
                                    try {
                                        val res = SyncEngine.networkScope(module, "Past semester $semId") {
                                            AmazeClient.getAcademicData(semId)
                                        }
                                        if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                            AppDataStore.upsertAttendance(semId, res.attendance)
                                        }
                                        if (res.marks?.error == null && res.marks?.marks?.isNotEmpty() == true) {
                                            AppDataStore.upsertMarks(semId, res.marks)
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failed++
                                        SyncEngine.addLog(module, "Past semester $semId failed: ${e.message}", SyncStatus.ERROR)
                                    }
                                }
                                if (failed == 0) {
                                    SyncEngine.markModuleSuccess(module, "fetched ${allSemIds.size} past semester(s)")
                                    SyncModuleResult("All Semesters", true)
                                } else {
                                    SyncEngine.markModuleError(module, "$failed of ${allSemIds.size} past semesters failed")
                                    SyncModuleResult("All Semesters", false, "$failed past semester(s) failed")
                                }
                            }
                        },
                        async {
                            syncModule(
                                name = "Timetable",
                                fetch = { AmazeClient.getTimetable(sem) },
                                isSuccess = { it.error == null },
                                errorMessage = { it.error },
                                update = {
                                    AppDataStore.upsertTimetable(sem, it)
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
                                }
                            )
                        }
                    ).awaitAll()

                    // ── Gap-fill for refreshAllAcademic ──
                    val grades = AppDataStore.academic.value.semesters.keys
                    if (grades.isNotEmpty()) {
                        val missingSemIds = grades
                            .filter { it != sem }
                            .filter { semId ->
                                val semData = AppDataStore.academic.value.semesters[semId]
                                semData == null || semData.courses.values.none { it.marks != null }
                            }
                        var gapFailures = 0
                        for (semId in missingSemIds) {
                            try {
                                val res = SyncEngine.networkScope(SyncModule.ALL_SEMESTER_ATTENDANCE, "Gap-fill $semId") {
                                    AmazeClient.getAcademicData(semId)
                                }
                                if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                                    AppDataStore.upsertAttendance(semId, res.attendance)
                                }
                                if (res.marks?.error == null && res.marks?.marks?.isNotEmpty() == true) {
                                    AppDataStore.upsertMarks(semId, res.marks)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                gapFailures++
                                SyncEngine.addLog(SyncModule.ALL_SEMESTER_ATTENDANCE, "Gap-fill $semId failed: ${e.message}", SyncStatus.ERROR)
                            }
                        }
                        if (gapFailures == 0) {
                            SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                            _pastSemestersSynced.value = true
                        }
                    }
                    syncResults
                }
                updateSyncSummary(results)
        }
    }

    fun refreshPastSemesters() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) {
            _syncMessage.value = "A sync is already running"
            return
        }
        launchSweep(setOf(SyncModule.ALL_SEMESTER_ATTENDANCE)) {
            _syncStatus.value = "Refreshing past semester data..."
            val module = SyncModule.ALL_SEMESTER_ATTENDANCE
            val sem = _selectedSemester.value
            val allSemIds = (AppDataStore.academic.value.semesters.keys + semesterIDs)
                .distinct().filter { it != sem }
            var failed = 0
            for (semId in allSemIds) {
                try {
                    val res = SyncEngine.networkScope(module, "Past semester $semId") {
                        AmazeClient.getAcademicData(semId)
                    }
                    if (res.attendance.error == null && res.attendance.attendance?.isNotEmpty() == true) {
                        AppDataStore.upsertAttendance(semId, res.attendance)
                    }
                    if (res.marks?.error == null && res.marks?.marks?.isNotEmpty() == true) {
                        AppDataStore.upsertMarks(semId, res.marks)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    SyncEngine.addLog(module, "Past semester $semId failed: ${e.message}", SyncStatus.ERROR)
                }
            }
            if (failed == 0) {
                SettingsManager.setBoolean(SettingsManager.PAST_SEMESTER_SYNCED, true)
                _pastSemestersSynced.value = true
                SyncEngine.markModuleSuccess(module, "fetched ${allSemIds.size} past semester(s)")
                updateSyncSummary(listOf(SyncModuleResult("All Semesters Attendance", true)))
            } else {
                SyncEngine.markModuleError(module, "$failed of ${allSemIds.size} past semesters failed")
                updateSyncSummary(listOf(SyncModuleResult("All Semesters Attendance", false, "$failed past semester(s) failed")))
            }
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
                    AppDataStore.setPayments(it)
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
                    AppDataStore.setHostelDetails(it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    fun refreshMessMenu(gender: String?, messType: String?) {
        scope.launch {
            val res = AmazeClient.getMessMenu(gender, messType)
            if (res.list.isNotEmpty()) {
                AppDataStore.setMessMenu(res)
            }
        }
    }

    fun refreshLaundrySchedule(gender: String?, blockPrefix: String) {
        scope.launch {
            val res = AmazeClient.getLaundrySchedule(gender, blockPrefix)
            if (res.list.isNotEmpty()) {
                AppDataStore.setLaundrySchedule(res)
            }
        }
    }

    fun refreshHostelCounselling() {
        scope.launch {
            val res = AmazeClient.getHostelCounselling()
            if (res.success) {
                AppDataStore.setHostelCounselling(res)
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
                    AppDataStore.setCalendar(it)
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
            var fetched: com.amazecc.app.shared.model.CalendarsListRes? = null
            val res = syncModule(
                name = "Calendars list",
                fetch = { AmazeClient.getCalendars(semesterId = sem).also { fetched = it } },
                isSuccess = { it.success },
                errorMessage = { it.message },
                update = { list ->
                    AppDataStore.setCalendarsList(list)
                    // Cache the General Semester calendar separately too, but never overwrite a
                    // populated single calendar with an empty subset of the list.
                    list.calendars.firstOrNull { it.name == "General Semester" }
                        ?.months?.takeIf { it.isNotEmpty() }?.let { months ->
                            AppDataStore.setCalendar(
                                com.amazecc.app.shared.model.CalendarRes(success = true, months = months)
                            )
                        }
                }
            )
            // An empty list must not leave the Calendar screen blank: fall back to the dedicated
            // single-calendar fetch (itself cache-aware) and seed a one-entry list from it.
            val list = fetched
            if (list == null || list.calendars.isEmpty()) {
                val single = AmazeClient.getCalendar(semesterId = sem)
                if (single.months.isNotEmpty()) {
                    AppDataStore.setCalendar(single)
                    AppDataStore.setCalendarsList(
                        com.amazecc.app.shared.model.CalendarsListRes(
                            success = true,
                            calendars = listOf(
                                com.amazecc.app.shared.model.NamedCalendar("General Semester", single.months)
                            )
                        )
                    )
                    _syncStatus.value = "Calendars list empty; served the single semester calendar"
                }
            }
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
                    AppDataStore.setQcmView(it)
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
                    AppDataStore.setCurriculum(it)
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
                // `error` alone was not enough: a failure reported through `message` (which is how
                // every "Empty response" / "menu unavailable" comes back) left `error` null, so the
                // module was marked green while syncing nothing.
                isSuccess = { it.error == null && it.success && !it.grades.isNullOrEmpty() },
                errorMessage = { it.error ?: it.message ?: "No grade history returned" },
                update = {
                    applyAllGrades(it)
                }
            )
            updateSyncSummary(listOf(result))
        }
    }

    /**
     * Non-suspending, bounded identity warm-up for the Profile surface. The stateful host
     * calls this on entry; all work runs on AppState's supervised sweep scope, never on the
     * composable's coroutine. Fills the consolidated whoami snapshot only when the store
     * holds a thin identity, so opening Profile actually populates name/programme/mentors.
     */
    fun syncIdentityIfThin() {
        if (_isLoading.value || sweepJob?.isActive == true) return
        if (!isThinIdentity(UserStore.identity.value)) return
        launchSweep(
            setOf(
                SyncModule.STUDENT_PROFILE, SyncModule.PROFILE_IMAGES, SyncModule.CREDENTIALS,
                SyncModule.BANK_INFO, SyncModule.APAAR_ID, SyncModule.DAYBOARDER,
                SyncModule.EPT_SCHEDULE, SyncModule.REGISTRATION_SCHEDULE, SyncModule.UNIVERSITY_DAY
            )
        ) {
            _syncStatus.value = "Loading student profile..."
            val results = syncIdentityModules()
            reportIdentitySync(results)
        }
    }

    /** Identity-specific summary: keep a partial-data warning visible above the generic count. */
    private fun reportIdentitySync(results: List<SyncModuleResult>) {
        updateSyncSummary(results)
        results.firstOrNull { it.success && !it.message.isNullOrBlank() }?.message?.let { warning ->
            _syncStatus.value = "Profile synced with partial data: $warning"
        }
    }

    fun refreshProfile() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(
            setOf(
                SyncModule.STUDENT_PROFILE, SyncModule.PROFILE_IMAGES, SyncModule.CREDENTIALS,
                SyncModule.BANK_INFO, SyncModule.APAAR_ID, SyncModule.DAYBOARDER,
                SyncModule.EPT_SCHEDULE, SyncModule.REGISTRATION_SCHEDULE, SyncModule.UNIVERSITY_DAY
            )
        ) {
            _syncStatus.value = "Syncing profile..."
            // The consolidated whoami payload already carries the folded records,
            // so one identity sweep covers every profile module without fetching
            // the same records a second time. UNIVERSITY_DAY is now part of the set.
            val results = syncIdentityModules()
            reportIdentitySync(results)
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
                        AppDataStore.setLibrary(it)
                        _libraryLoginRequired.value = false
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
                                    AppDataStore.setTransportData(it)
                                }
                            )
                        },
                        async {
                            syncModule(
                                name = "Buses",
                                fetch = { AmazeClient.getBuses() },
                                isSuccess = { AmazeClient.busesSucceeded(it) },
                                errorMessage = { AmazeClient.busesFailureMessage(it) },
                                update = {
                                    AppDataStore.setBuses(it)
                                }
                            )
                        }
                    ).awaitAll()
                }
                updateSyncSummary(results)
        }
    }

    /**
     * Offline derivation from the domain snapshot; see AmazeClient.getFreeClassrooms.
     *
     * D-191 follow-up: never publish this feature's refresh feedback to the global [_error].
     * A busy tap used to replace valid classroom results with a full-page "Couldn't load the
     * timetable" and could then surface on unrelated screens. The host shows a local, auto-cleared
     * inline note instead; a real derivation failure is reported by `freeClassroomsRes` itself.
     */
    fun refreshFreeClassrooms() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.FREE_CLASSROOMS)) {
            val result = syncModule(
                name = "Free Classrooms",
                fetch = { AmazeClient.getFreeClassrooms() },
                isSuccess = { it.success },
                errorMessage = { it.error ?: it.message },
                update = { AppDataStore.setFreeClassrooms(it) }
            )
            updateSyncSummary(listOf(result))
        }
    }

    /** Flattened circulars tree plus the static official-document entries. */
    fun refreshDocuments() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.DOCUMENTS)) {
            val result = syncModule(
                name = "Documents",
                fetch = { AmazeClient.getDocuments() },
                isSuccess = { it.isNotEmpty() },
                errorMessage = { "No documents available" },
                update = { AppDataStore.setDocuments(it) }
            )
            _error.value = if (result.success) null else result.message
            updateSyncSummary(listOf(result))
        }
    }

    /**
     * Fetches the detail tables for one payment receipt into [receiptDetail].
     *
     * Non-suspend: follows the refresh-function pattern by running on the app scope via
     * [launchSweep]. There is no dedicated SyncModule for a single receipt, so the module set is
     * empty and no VTOP session gate is forced; the client applies its own source rules. The
     * result is published to the transient store flow, and a failed fetch leaves a null detail
     * (never faked).
     */
    fun refreshReceiptDetail(applNo: String) {
        // One-off drill-down: don't use launchSweep here — it would be dropped while a real
        // sync sweep is active and would reset every module's progress state.
        scope.launch {
            _syncStatus.value = "Loading receipt detail..."
            val detail = runCatching { AmazeClient.getReceiptDetail(applNo) }.getOrNull()
            AppDataStore.setReceiptDetail(detail)
            if (detail == null) _error.value = "Receipt detail unavailable"
        }
    }

    /**
     * Downloads a document/circular binary into [circularDownload].
     *
     * Non-suspend: launches a coroutine that calls [AmazeClient.downloadCircular] and publishes
     * the result (or null on failure) to the transient store flow.
     */
    fun downloadDocument(id: String) {
        scope.launch {
            val result = AmazeClient.downloadCircular(id)
            AppDataStore.setCircularDownload(result)
        }
    }

    /**
     * D-156 test seam: the single feedback fetch entry point. Production behaviour is unchanged;
     * tests swap in a fake to prove the module pipeline actually executes. The default is restored
     * by each test in its own `finally` block.
     */
    internal var feedbackStatusFetcher: suspend (String) -> FeedbackStatusRes = { AmazeClient.getFeedbackStatus(it) }

    /**
     * D-156: the one canonical feedback module pipeline, shared by [loadAllData], [loadMode] and
     * the targeted [refreshFeedbackStatus] retry, so no path can drift into a page-local fetch.
     */
    internal suspend fun syncFeedbackStatusModule(sem: String): SyncModuleResult = syncModule(
        name = "Feedback Status",
        fetch = { feedbackStatusFetcher(sem) },
        isSuccess = { it.success && it.error == null },
        errorMessage = { it.error ?: "Feedback status unavailable" },
        update = { AppDataStore.setFeedbackStatus(it) }
    )

    /** D-156: working targeted refresh — a real single-module sweep with a reported result. */
    fun refreshFeedbackStatus(semesterId: String? = null) {
        if (_isLoading.value) return
        val sem = semesterId ?: _selectedSemester.value
        launchSweep(setOf(SyncModule.FEEDBACK_STATUS)) {
            updateSyncSummary(listOf(syncFeedbackStatusModule(sem)))
        }
    }

    fun refreshOdDetails(semesterId: String) {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.OD_DETAILS)) {
            val result = syncModule(
                name = "OD Details",
                fetch = { AmazeClient.getOdDetails(semesterId) },
                isSuccess = { it.success && it.error == null },
                errorMessage = { it.error ?: "OD details unavailable" },
                update = { AppDataStore.setOdDetails(it) }
            )
            _error.value = if (result.success) null else result.message
            updateSyncSummary(listOf(result))
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
                        AppDataStore.setLms(it)
                    }
                )
                updateSyncSummary(listOf(result))
        }
    }

    fun selectExamSemester(semesterId: String) {
        _selectedExamSemester.value = semesterId
        if (AppDataStore.academic.value.semesters[semesterId]?.exams?.isEmpty() != false) {
            refreshExamSchedule()
        }
    }

    fun refreshExamSchedule() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.EXAM_SCHEDULE)) {
            _syncStatus.value = "Logging into VTOP & syncing exam schedule..."
            val semId = _selectedExamSemester.value
                val result = syncModule(
                    name = "Exam schedule",
                    fetch = { AmazeClient.getExamSchedule(semesterId = semId) },
                    isSuccess = { it.error == null },
                    errorMessage = { it.error },
                    update = {
                        AppDataStore.upsertExams(semId, it)
                    }
                )
                
                // Fetch other semesters in parallel if needed
                for (otherSemId in semesterIDs) {
                    if (otherSemId == semId) continue
                    try {
                        val res = AmazeClient.getExamSchedule(semesterId = otherSemId)
                        if (res.error == null && res.schedule.isNotEmpty()) {
                            AppDataStore.upsertExams(otherSemId, res)
                        }
                    } catch (_: Exception) {}
                }
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
                        AppDataStore.setCirculars(it)
                    }
                )
                updateSyncSummary(listOf(result))
        }
    }


    /**
     * The single fetch → validate → cache → report pipeline. Every network request made by a
     * module runs inside [SyncEngine.networkScope], so the gate sees it with the right module tag.
     *
     * Skips are reported as SKIPPED (never as SUCCESS), failures as ERROR, and cancellation is
     * re-thrown so it can never be mislabelled as a module failure.
     */
    private suspend fun <T> syncModule(
        name: String,
        fetch: suspend () -> T,
        isSuccess: (T) -> Boolean,
        errorMessage: (T) -> String?,
        update: (T) -> Unit
    ): SyncModuleResult {
        val module = SyncEngine.moduleOf(name)
        if (module != null && !SyncEngine.isModuleEnabled(module)) {
            SyncEngine.markModuleSkipped(module, "Disabled by the active sync profile")
            return SyncModuleResult(name, true, "SKIPPED: disabled by active profile")
        }
        module?.let { SyncEngine.markModuleLoading(it) }
        return try {
            val result = SyncEngine.networkScope(module, "Fetch $name") { fetch() }
            if (isSuccess(result)) {
                update(result)
                module?.let { SyncEngine.markModuleSuccess(it, "fetched via ${module.cacheKey ?: "in-memory cache"}") }
                SyncModuleResult(name, true)
            } else {
                val msg = errorMessage(result) ?: "Empty or failed response"
                val skipped = msg.startsWith("NO_") && msg.endsWith("CREDS")
                module?.let {
                    if (skipped) SyncEngine.markModuleSkipped(it, msg) else SyncEngine.markModuleError(it, msg)
                    SyncEngine.addLog(it, msg, if (skipped) SyncStatus.SKIPPED else SyncStatus.ERROR)
                }
                SyncModuleResult(name, skipped, if (skipped) "SKIPPED: $msg" else msg)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "${e::class.simpleName}: ${e.message ?: e.toString()}"
            module?.let {
                SyncEngine.markModuleError(it, msg)
                SyncEngine.addLog(it, msg, SyncStatus.ERROR)
            }
            SyncModuleResult(name, false, msg)
        }
    }

    /**
     * Identity sync: one consolidated `/api/remote/whoami` fetch merged under the
     * ME tier, covering identity core plus every folded profile module. Falls
     * back to the individual per-endpoint fetches when the consolidated payload
     * is unavailable (old server, demo mode, partial failure).
     */
    private suspend fun syncIdentityModules(): List<SyncModuleResult> {
        val me = SyncEngine.networkScope(SyncModule.STUDENT_PROFILE, "Fetch consolidated identity") {
            AmazeClient.getMe()
        }
        // HTTP 200 may still carry a few fetchErrors. Every field that did arrive is
        // authoritative at the ME tier: merge it and surface the warning rather than
        // throwing the whole payload away and falling back to failing per-endpoint scrapes.
        val identity = me.identity?.takeIf { it.hasIdentity }
        if (identity != null) {
            val warning = me.error?.takeIf { it.isNotBlank() }
            UserStore.merge(identity, IdentitySource.ME)
            reconcileResidentialStatus(identity)
            reconcileExamSeatAlerts()
            if (warning != null) _syncStatus.value = "Profile synced with partial data: $warning"
            val names = ConsolidatedIdentityModuleNames.filter { name ->
                SyncEngine.moduleOf(name)?.let { SyncEngine.isModuleEnabled(it) } ?: true
            }
            names.forEachIndexed { index, name ->
                SyncEngine.moduleOf(name)?.let { module ->
                    if (warning != null && index > 0) {
                        // A partial payload does not justify nine green rows with fresh timestamps.
                        SyncEngine.markModuleSkipped(module, "Partial whoami payload: $warning")
                    } else {
                        SyncEngine.markModuleSuccess(module, "via /api/remote/whoami")
                    }
                }
            }
            // Registration schedule & university day are not among the ten folded
            // VTOP pages. Fill them individually when the snapshot did not carry
            // them so their pages still have real data — no double-fetch of data
            // the consolidated payload already supplied.
            try {
                if (identity.registrationFields.isEmpty() && identity.registrationTables.isEmpty()) {
                    SyncEngine.networkScope(SyncModule.REGISTRATION_SCHEDULE, "Fill registration schedule") {
                        AmazeClient.getRegistrationSchedule()
                    }.takeIf { it.success }?.let { res ->
                        UserStore.merge(IdentityExtractor.fromRegistrationSchedule(res), IdentitySource.RECORDS)
                        updateFfcsRegistration(parseFfcsRegistration(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
            try {
                if (identity.universityDayTitle == null && identity.universityDayFields.isEmpty() &&
                    identity.universityDayTables.isEmpty()
                ) {
                    SyncEngine.networkScope(SyncModule.UNIVERSITY_DAY, "Fill university day") {
                        AmazeClient.getUniversityDay()
                    }.takeIf { it.success }?.let { res ->
                        UserStore.merge(IdentityExtractor.fromUniversityDay(res), IdentitySource.RECORDS)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
            return names.mapIndexed { index, name ->
                if (warning != null && index > 0) {
                    SyncModuleResult(name, true, "SKIPPED: partial whoami payload — $warning")
                } else {
                    SyncModuleResult(name, true, warning)
                }
            }
        }
        // Consolidated payload unavailable — sweep each source individually,
        // including the records that whoami would otherwise have carried.
        return supervisorScope {
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
                    syncModule("APAAR ID", { AmazeClient.getApaarId() }, { it.success }, { it.error }) {
                        UserStore.merge(IdentityExtractor.fromApaarId(it), IdentitySource.APAAR)
                    }
                },
                async {
                    syncModule("Dayboarder Info", { AmazeClient.getDayboarderInfo() }, { it.success }, { it.error }) {
                        UserStore.merge(IdentityExtractor.fromDayboarder(it), IdentitySource.RECORDS)
                        reconcileResidentialStatus(UserStore.identity.value)
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
                }
            ).awaitAll()
        }
    }

    /** Modules covered by the consolidated identity snapshot / its fallback sweep. */
    private val ConsolidatedIdentityModuleNames = listOf(
        "Student Profile", "Profile Images", "Credentials",
        "Bank Information", "APAAR ID", "Dayboarder Info",
        "EPT Schedule", "Registration Schedule", "University Day"
    )

    /**
     * Reconciles the onboarding-time [residentialStatus] with authoritative
     * identity data. Precedence mirrors the web client: a bus registration forces
     * DAYSCHOLAR; otherwise a tri-state identity residence (HOSTELLER /
     * DAYSCHOLAR) wins; a null derived status keeps the onboarding choice.
     */
    private fun reconcileResidentialStatus(identity: com.amazecc.app.shared.model.StudentIdentity) {
        val hasBusRegistration = runCatching { AppDataStore.transportData.value?.hasRegistration == true }
            .getOrDefault(false)
        val resolved = when {
            hasBusRegistration -> "Dayscholar"
            identity.residenceStatus == com.amazecc.app.shared.model.RESIDENCE_DAYSCHOLAR -> "Dayscholar"
            identity.residenceStatus == com.amazecc.app.shared.model.RESIDENCE_HOSTELLER -> "Hosteller"
            else -> null
        }
        if (resolved != null && _residentialStatus.value != resolved) {
            _residentialStatus.value = resolved
            SettingsManager.setString(SettingsManager.RESIDENTIAL_STATUS, resolved)
        }
        // The web client forces "DAYSCHOLAR + bus" when a bus registration exists.
        if (hasBusRegistration && !_isBusSubscriber.value) setBusSubscriber(true)
    }

    private fun updateSyncSummary(results: List<SyncModuleResult>) {
        val skipped = results.filter { it.message?.startsWith("SKIPPED: ") == true }
        val failures = results.filterNot { it.success }
            .filter { it.message?.startsWith("SKIPPED: ") != true }
        val successCount = results.size - failures.size - skipped.size
        _syncStatus.value = buildString {
            append("Synced $successCount/${results.size - skipped.size} modules")
            if (skipped.isNotEmpty()) append(", ${skipped.size} skipped")
            if (failures.isNotEmpty()) append(", ${failures.size} failed")
        }
        _error.value = failures
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "${it.name}: ${it.message}" }
        if (skipped.isNotEmpty()) {
            SyncEngine.addLog(
                SyncModule.STUDENT_PROFILE,
                "Skipped: ${skipped.joinToString { it.message?.removePrefix("SKIPPED: ") ?: it.name }}",
                SyncStatus.SKIPPED
            )
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun logout() {
        SettingsManager.remove(SettingsManager.KEY_USERNAME)
        SettingsManager.remove(SettingsManager.KEY_PASSWORD)
        SettingsManager.clearMoodleCredentials()
        SessionManager.clearSession()
        AppBackStack.clear()
        _currentScreen.value = Screen.LOGIN
        
        // Clear caches
        AppDataStore.clear()
        UserStore.clear()
        _pendingFfcsAlert.value = null
        _pastSemestersSynced.value = false
        _selectedSemester.value = DEFAULT_SEMESTER_ID
        _selectedExamSemester.value = DEFAULT_SEMESTER_ID
        _selectedCourseCode.value = null
        _selectedCourseSemester.value = null
        clearTargets()
        _isLoading.value = false
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
        _pinnedNavTabs.value = listOf(Screen.ACADEMICS, Screen.PROFILE, Screen.MORE)
        _updateDialogDismissedVersion.value = ""
        _latestReleaseNotes.value = ""
        _pendingExamSeatAlerts.value = emptyList()
        scope.launch { com.amazecc.app.shared.utils.clearPendingNotifications() }
    }

    fun updateAttendance(data: AttendanceRes?) {
        AppDataStore.upsertAttendance(_selectedSemester.value, data)
    }

fun updateMoodleData(data: MoodleRes?) {
        AppDataStore.setMoodle(data)
        scope.launch { scheduleReminders() }
    }

    fun getMoodleAssignmentsForCourse(courseCode: String): List<MoodleAssignment> {
        return AppDataStore.moodleData.value?.data?.filter { a ->
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
                    AppDataStore.setCabShareUser(res.user)
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
        AppDataStore.setCabShareUser(null)
    }

    fun fetchCabHubs() {
        scope.launch {
            try {
                val hubs = AmazeClient.getCabHubs()
                AppDataStore.setCabHubs(hubs)
            } catch (_: Exception) {
                AppDataStore.setCabHubs(fallbackCabHubs)
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
                val user = AppDataStore.cabShareUser.value
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
                val user = AppDataStore.cabShareUser.value ?: run {
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
                val user = AppDataStore.cabShareUser.value ?: run {
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
                val eventsRes = SyncEngine.networkScope(SyncModule.EVENTS, "Fetch events") {
                    AmazeClient.getEvents()
                }
                if (eventsRes.error == null) {
                    AppDataStore.setEvents(eventsRes)
                    SyncEngine.markModuleSuccess(SyncModule.EVENTS, "events feed")
                } else {
                    SyncEngine.markModuleError(SyncModule.EVENTS, eventsRes.error)
                    SyncEngine.addLog(SyncModule.EVENTS, eventsRes.error ?: "empty response", SyncStatus.ERROR)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncEngine.markModuleError(SyncModule.EVENTS, e.message ?: "Network error")
                SyncEngine.addLog(SyncModule.EVENTS, e.message ?: "Network error", SyncStatus.ERROR)
            }
            // The authenticated registration list carries receipt/certificate/payment links. It
            // is fetched through VtopEventHub in LOCAL mode and from the cache in OFFLINE_CACHE —
            // only a genuinely successful response may replace the cached list.
            try {
                val profileRes = SyncEngine.networkScope(SyncModule.EVENTS, "Fetch event registrations") {
                    AmazeClient.getEventsProfile()
                }
                if (profileRes.success && profileRes.error == null) {
                    AppDataStore.setRegisteredEvents(profileRes)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the last known registration list; the profile is best-effort here.
            }
            try {
                val clubsRes = SyncEngine.networkScope(SyncModule.CLUBS, "Fetch clubs") {
                    AmazeClient.getClubsDetails()
                }
                if (clubsRes.error == null) {
                    AppDataStore.setClubs(clubsRes)
                    SyncEngine.markModuleSuccess(SyncModule.CLUBS, "clubs directory")
                } else {
                    SyncEngine.markModuleError(SyncModule.CLUBS, clubsRes.error)
                    SyncEngine.addLog(SyncModule.CLUBS, clubsRes.error ?: "empty response", SyncStatus.ERROR)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
                AppDataStore.upsertAttendance(sem, it.attendance)
                it.marks?.let { m ->
                    AppDataStore.upsertMarks(sem, m)
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
                AppDataStore.upsertTimetable(sem, it)
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
                AppDataStore.setCurriculum(it)
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
                AppDataStore.setHostelDetails(it)
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
                AppDataStore.setPayments(it)
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
                AppDataStore.setEvents(it)
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

            // The sweep's single forced login (ensureVtopSessionForSweep) already established a
            // fresh session; only verify it here so onboarding does not trigger a second login.
            updateOnboardingStep("Session", "syncing")
            val ok = SyncEngine.ensureAuthenticatedSession(forceFresh = false)
            if (!ok) {
                updateOnboardingStep("Session", "failed")
                return@withStageProfile
            }
            updateOnboardingStep("Session", "done")

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
            .toSet()
        launchSweep(dailyModules, stageProfile = SyncScheduler.lightProfileId()) {
            _syncMessage.value = "Running scheduled refresh..."
            _syncStatus.value = "Running scheduled refresh..."
            notificationService.showLoadingNotification("AmazeCC Sync", "Running scheduled refresh...")
            var sweepSucceeded = false
            try {
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
                                    AppDataStore.upsertAttendance(sem, it.attendance)
                                    if (it.marks?.error == null) {
                                        it.marks?.let { marks ->
                                            AppDataStore.upsertMarks(sem, marks)
                                        }
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
                                    AppDataStore.upsertTimetable(sem, it)
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
                                    AppDataStore.upsertExams(sem, it)
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
                                    AppDataStore.setCalendar(it)
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
                                    AppDataStore.setCalendarsList(it)
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
                                    AppDataStore.setLms(it)
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
                                    AppDataStore.setCirculars(it)
                                }
                            )
                        },
                        async {
                            val moodleCreds = SettingsManager.getMoodleCredentials()
                            if (moodleCreds == null) {
                                SyncEngine.markModuleSkipped(SyncModule.MOODLE, "Moodle credentials not configured")
                                SyncModuleResult("Moodle Assignments", true, "SKIPPED: Moodle credentials not configured")
                            } else {
                                syncModule(
                                    name = "Moodle Assignments",
                                    fetch = { AmazeClient.fetchMoodleData(moodleCreds.first, moodleCreds.second) },
                                    isSuccess = { it.success },
                                    errorMessage = { it.error ?: it.message },
                                    update = {
                                    AppDataStore.setMoodle(it)
                                    }
                                )
                            }
                        }
                    ).awaitAll()
                    syncResults
                }
                updateSyncSummary(results)
                sweepSucceeded = results.none { !it.success } && SyncEngine.syncProgress.value.errorCount == 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                if (sweepSucceeded) SyncScheduler.markSynced()
                SyncScheduler.advanceAndArm(SyncScheduler.LIGHT_KIND)
                if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    notificationService.showLoadingNotification(
                        "AmazeCC Sync",
                        if (sweepSucceeded) "Sync completed" else "Sync finished with problems"
                    )
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
                AppDataStore.setLibrary(res)
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
                AppDataStore.setMoodle(res)
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
            try {
                val result = SyncEngine.userAction("EventHub login") { AmazeClient.eventLogin() }
                SyncEngine.addLog(
                    SyncModule.EVENTS,
                    if (result != null) "EventHub login OK" else "EventHub login failed (no token returned)",
                    if (result != null) SyncStatus.SUCCESS else SyncStatus.ERROR
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncEngine.addLog(SyncModule.EVENTS, "EventHub login failed: ${e.message}", SyncStatus.ERROR)
            }
        }
    }

    /** Fetches the detail page for one event and publishes it to [eventPreview]. */
    fun refreshEventPreview(eid: String) {
        scope.launch {
            AppDataStore.setEventPreview(AmazeClient.getEventPreview(eid))
        }
    }

    /** Registers for one event and publishes the outcome to [eventRegistration]. */
    fun registerForEvent(eid: String) {
        scope.launch {
            AppDataStore.setEventRegistration(AmazeClient.registerForEvent(eid))
        }
    }

    /** Refreshes and caches the authenticated registration list (receipt/certificate/payment). */
    fun refreshEventsProfile() {
        if (_isLoading.value || SyncEngine.isAnyModuleLoading()) return
        launchSweep(setOf(SyncModule.EVENTS)) {
            val result = syncModule(
                name = "Registered Events",
                fetch = { AmazeClient.getEventsProfile() },
                isSuccess = { it.error == null },
                errorMessage = { it.error },
                update = { AppDataStore.setRegisteredEvents(it) }
            )
            updateSyncSummary(listOf(result))
        }
    }
}

/**
 * A thin identity has a registration number (and possibly a cached photo) but none of the
 * profile detail the consolidated whoami snapshot supplies. Pure so the Profile-wiring tests
 * can pin exactly when the host should warm the snapshot.
 */
internal fun isThinIdentity(identity: StudentIdentity): Boolean {
    if (identity.regNo.isNullOrBlank()) return false
    if (!identity.name.isNullOrBlank() || !identity.friendlyName.isNullOrBlank()) return false
    return !identity.hasProfileDetail()
}

private fun StudentIdentity.hasProfileDetail(): Boolean =
    !email.isNullOrBlank() || !mobile.isNullOrBlank() || !dob.isNullOrBlank() ||
    !program.isNullOrBlank() || !school.isNullOrBlank() || !campus.isNullOrBlank() ||
    !applicationNumber.isNullOrBlank() || proctor != null || hodDean.isNotEmpty() ||
    bank != null || apaar != null || dayboarder != null || acknowledgement != null ||
    credentials.isNotEmpty() || ranks.isNotEmpty() || contacts.isNotEmpty() ||
    currentAddress.isNotEmpty() || permanentAddress.isNotEmpty() ||
    eptTables.isNotEmpty() || eptFields.isNotEmpty() || eptMessages.isNotEmpty() ||
    registrationFields.isNotEmpty() || registrationTables.isNotEmpty() ||
    universityDayTitle != null || universityDayFields.isNotEmpty() || universityDayTables.isNotEmpty()

internal data class SyncModuleResult(
    val name: String,
    val success: Boolean,
    val message: String? = null
)
