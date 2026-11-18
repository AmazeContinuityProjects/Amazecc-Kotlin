package com.amazecc.app.shared.state

import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.Immutable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SyncCategory(val displayName: String) {
    ACADEMICS("Academics"),
    CAMPUS_HOSTEL("Campus & Hostel"),
    FINANCE_SERVICES("Finance & Services"),
    PROFILE_MISC("Profile & Misc")
}

enum class SyncModule(
    val displayName: String,
    val cacheKey: String? = null,
    val category: SyncCategory = SyncCategory.ACADEMICS
) {
    ATTENDANCE("Attendance", SettingsManager.CACHE_ATTENDANCE, SyncCategory.ACADEMICS),
    ALL_SEMESTER_ATTENDANCE("All Semesters", null, SyncCategory.ACADEMICS),
    TIMETABLE("Timetable", SettingsManager.CACHE_TIMETABLE, SyncCategory.ACADEMICS),
    MARKS("Marks", SettingsManager.CACHE_MARKS, SyncCategory.ACADEMICS),
    GRADES("Grade History", SettingsManager.CACHE_GRADES, SyncCategory.ACADEMICS),
    CURRICULUM("Curriculum", SettingsManager.CACHE_CURRICULUM, SyncCategory.ACADEMICS),
    HOSTEL_DETAILS("Hostel Details", SettingsManager.CACHE_HOSTEL_DETAILS, SyncCategory.CAMPUS_HOSTEL),
    EXAM_SCHEDULE("Exam Schedule", SettingsManager.CACHE_EXAM_SCHEDULE, SyncCategory.ACADEMICS),
    CALENDAR("Academic Calendar", SettingsManager.CACHE_CALENDAR, SyncCategory.ACADEMICS),
    CALENDARS_LIST("Calendars List", SettingsManager.CACHE_CALENDARS_LIST, SyncCategory.ACADEMICS),
    PAYMENTS("Payments", SettingsManager.CACHE_PAYMENTS, SyncCategory.FINANCE_SERVICES),
    LIBRARY("Library", SettingsManager.CACHE_LIBRARY, SyncCategory.FINANCE_SERVICES),
    TRANSPORT("Transport Data", SettingsManager.CACHE_TRANSPORT_DATA, SyncCategory.CAMPUS_HOSTEL),
    BUSES("Buses", SettingsManager.CACHE_BUSES, SyncCategory.CAMPUS_HOSTEL),
    LMS("LMS", SettingsManager.CACHE_LMS, SyncCategory.FINANCE_SERVICES),
    EVENTS("Events", SettingsManager.CACHE_EVENTS, SyncCategory.CAMPUS_HOSTEL),
    CLUBS("Clubs", SettingsManager.CACHE_CLUBS, SyncCategory.CAMPUS_HOSTEL),
    QCM_VIEW("QCM View", SettingsManager.CACHE_QCM_VIEW, SyncCategory.FINANCE_SERVICES),
    // All profile modules now sync into the single encrypted UserStore identity cache.
    STUDENT_PROFILE("Student Profile", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    CIRCULARS("Circulars", SettingsManager.CACHE_CIRCULARS, SyncCategory.ACADEMICS),
    PROFILE_IMAGES("Profile Images", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    CREDENTIALS("Credentials", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    BANK_INFO("Bank Information", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.FINANCE_SERVICES),
    DAYBOARDER("Dayboarder Info", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.CAMPUS_HOSTEL),
    EPT_SCHEDULE("EPT Schedule", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    REGISTRATION_SCHEDULE("Registration Schedule", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    UNIVERSITY_DAY("University Day", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    APAAR_ID("APAAR ID", SettingsManager.CACHE_USER_IDENTITY, SyncCategory.PROFILE_MISC),
    MOODLE("Moodle Assignments", SettingsManager.CACHE_MOODLE, SyncCategory.FINANCE_SERVICES),
}

enum class SyncStatus { IDLE, LOADING, SUCCESS, ERROR }

@Immutable
data class ModuleState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSynced: Instant? = null,
    val error: String? = null,
)

@Immutable
data class LogLine(
    val module: SyncModule,
    val message: String,
    val status: SyncStatus,
    val timestamp: Instant,
)

@Immutable
data class SyncProgress(
    val totalModules: Int = 0,
    val completedModules: Int = 0,
    val activeModules: Set<SyncModule> = emptySet(),
    val successCount: Int = 0,
    val errorCount: Int = 0
) {
    val percentage: Float
        get() = if (totalModules == 0) 0f else ((completedModules.toFloat() / totalModules.toFloat()).coerceIn(0f, 1f)) * 100f

    val displayText: String
        get() = when {
            activeModules.isNotEmpty() -> "Syncing ${activeModules.first().displayName} (${completedModules + 1}/$totalModules)"
            totalModules > 0 && completedModules == totalModules && errorCount > 0 -> "Completed with $errorCount errors"
            totalModules > 0 && completedModules == totalModules -> "All $totalModules modules updated"
            totalModules > 0 -> "$completedModules / $totalModules modules"
            completedModules > 0 -> "$completedModules modules ready"
            else -> "Ready"
        }
}

@Serializable
data class SyncModeStep(
    val module: SyncModule,
    val background: Boolean = false,
    val everyDays: Int? = null
)

@Serializable
data class SyncMode(
    val id: String,
    val label: String,
    val description: String,
    val isBuiltIn: Boolean = true,
    val steps: List<SyncModeStep> = emptyList()
) {
    val foregroundSteps: List<SyncModeStep> get() = steps.filter { !it.background }
    val backgroundSteps: List<SyncModeStep> get() = steps.filter { it.background }
    val enabledModules: Set<SyncModule> get() = steps.map { it.module }.toSet()
}

data class CadenceTierMeta(
    val id: String,
    val label: String,
    val cadence: String,
    val description: String,
    val defaultStalenessDays: Int
)

val BUILT_IN_TIERS: List<CadenceTierMeta> = listOf(
    CadenceTierMeta(
        id = "daily",
        label = "Daily Sync",
        cadence = "Every day",
        description = "Attendance, timetable and marks — the per-class check, hostellers' hostel folded in.",
        defaultStalenessDays = 1
    ),
    CadenceTierMeta(
        id = "weekly",
        label = "Weekly Sync",
        cadence = "Once a week",
        description = "Daily + exam schedule, LMS, and Moodle details.",
        defaultStalenessDays = 7
    ),
    CadenceTierMeta(
        id = "monthly",
        label = "Monthly Sync",
        cadence = "Once a month",
        description = "Weekly + curriculum, calendar, finance, transport, grades and the rest.",
        defaultStalenessDays = 30
    ),
    CadenceTierMeta(
        id = "quarterly",
        label = "Quarterly Sync",
        cadence = "Every 3 months",
        description = "Monthly + identity & profile (whoami). Everything the app can pull.",
        defaultStalenessDays = 90
    )
)

private fun step(module: SyncModule, background: Boolean = false, everyDays: Int? = null) =
    SyncModeStep(module = module, background = background, everyDays = everyDays)

val DAILY_STEPS: List<SyncModeStep> = listOf(
    step(SyncModule.ATTENDANCE, everyDays = 1),
    step(SyncModule.TIMETABLE, everyDays = 1),
    step(SyncModule.MARKS, everyDays = 1),
    step(SyncModule.HOSTEL_DETAILS, background = true, everyDays = 1),
)

val WEEKLY_STEPS: List<SyncModeStep> = DAILY_STEPS + listOf(
    step(SyncModule.EXAM_SCHEDULE, background = true, everyDays = 7),
    step(SyncModule.LMS, background = true, everyDays = 7),
    step(SyncModule.MOODLE, background = true, everyDays = 7),
)

val MONTHLY_STEPS: List<SyncModeStep> = listOf(
    step(SyncModule.ATTENDANCE),
    step(SyncModule.TIMETABLE),
    step(SyncModule.MARKS),
    step(SyncModule.HOSTEL_DETAILS, background = true),
    step(SyncModule.EXAM_SCHEDULE, background = true, everyDays = 7),
    step(SyncModule.LMS, background = true, everyDays = 7),
    step(SyncModule.MOODLE, background = true, everyDays = 7),
    step(SyncModule.GRADES, everyDays = 30),
    step(SyncModule.ALL_SEMESTER_ATTENDANCE, everyDays = 30),
    step(SyncModule.CALENDAR, everyDays = 30),
    step(SyncModule.CALENDARS_LIST, everyDays = 30),
    step(SyncModule.CURRICULUM, background = true, everyDays = 30),
    step(SyncModule.PAYMENTS, background = true, everyDays = 30),
    step(SyncModule.LIBRARY, background = true, everyDays = 30),
    step(SyncModule.TRANSPORT, background = true, everyDays = 30),
    step(SyncModule.BUSES, background = true, everyDays = 30),
    step(SyncModule.EVENTS, background = true, everyDays = 30),
    step(SyncModule.CLUBS, background = true, everyDays = 30),
    step(SyncModule.CIRCULARS, background = true, everyDays = 30),
    step(SyncModule.QCM_VIEW, background = true, everyDays = 30),
)

val QUARTERLY_STEPS: List<SyncModeStep> = MONTHLY_STEPS + listOf(
    step(SyncModule.STUDENT_PROFILE, everyDays = 90),
    step(SyncModule.PROFILE_IMAGES, background = true, everyDays = 90),
    step(SyncModule.CREDENTIALS, background = true, everyDays = 90),
    step(SyncModule.BANK_INFO, background = true, everyDays = 90),
    step(SyncModule.DAYBOARDER, background = true, everyDays = 90),
    step(SyncModule.EPT_SCHEDULE, background = true, everyDays = 90),
    step(SyncModule.REGISTRATION_SCHEDULE, background = true, everyDays = 90),
    step(SyncModule.UNIVERSITY_DAY, background = true, everyDays = 90),
    step(SyncModule.APAAR_ID, background = true, everyDays = 90),
)

val BUILT_IN_MODES: List<SyncMode> = listOf(
    SyncMode(
        id = "quick",
        label = "Quick Sync",
        description = "Attendance, timetable and marks — the per-class check.",
        isBuiltIn = true,
        steps = listOf(
            step(SyncModule.ATTENDANCE),
            step(SyncModule.TIMETABLE),
            step(SyncModule.MARKS)
        )
    ),
    SyncMode(
        id = "balanced",
        label = "Balanced Sync",
        description = "Attendance, marks, student profile + background campus & services.",
        isBuiltIn = true,
        steps = listOf(
            step(SyncModule.ATTENDANCE),
            step(SyncModule.TIMETABLE),
            step(SyncModule.MARKS),
            step(SyncModule.GRADES),
            step(SyncModule.STUDENT_PROFILE),
            step(SyncModule.HOSTEL_DETAILS, background = true),
            step(SyncModule.TRANSPORT, background = true),
            step(SyncModule.BUSES, background = true),
            step(SyncModule.EVENTS, background = true),
            step(SyncModule.CLUBS, background = true),
            step(SyncModule.CURRICULUM, background = true),
            step(SyncModule.QCM_VIEW, background = true),
            step(SyncModule.LMS, background = true),
            step(SyncModule.CIRCULARS, background = true),
            step(SyncModule.LIBRARY, background = true),
            step(SyncModule.PAYMENTS, background = true)
        )
    ),
    SyncMode(
        id = "full",
        label = "Full Sync",
        description = "All 28 modules refreshed with background tasks awaited.",
        isBuiltIn = true,
        steps = SyncModule.entries.map { step(it, background = false) }
    ),
    SyncMode(
        id = "academics",
        label = "Academics Only",
        description = "Attendance, marks, grades, exam schedule, calendar, curriculum, QCM and LMS.",
        isBuiltIn = true,
        steps = listOf(
            step(SyncModule.ATTENDANCE),
            step(SyncModule.TIMETABLE),
            step(SyncModule.MARKS),
            step(SyncModule.GRADES),
            step(SyncModule.EXAM_SCHEDULE),
            step(SyncModule.CALENDAR),
            step(SyncModule.CALENDARS_LIST),
            step(SyncModule.ALL_SEMESTER_ATTENDANCE),
            step(SyncModule.CURRICULUM, background = true),
            step(SyncModule.QCM_VIEW, background = true),
            step(SyncModule.LMS, background = true),
            step(SyncModule.CIRCULARS, background = true)
        )
    ),
    SyncMode(
        id = "campus",
        label = "Campus Life",
        description = "Events, clubs, buses, transport, hostel details, dayboarder and services.",
        isBuiltIn = true,
        steps = listOf(
            step(SyncModule.EVENTS),
            step(SyncModule.CLUBS),
            step(SyncModule.BUSES),
            step(SyncModule.TRANSPORT),
            step(SyncModule.HOSTEL_DETAILS),
            step(SyncModule.DAYBOARDER),
            step(SyncModule.PAYMENTS, background = true),
            step(SyncModule.LIBRARY, background = true)
        )
    ),
    SyncMode(
        id = "daily",
        label = "Daily Sync",
        description = "Attendance, timetable and marks (1 day staleness window).",
        isBuiltIn = true,
        steps = DAILY_STEPS
    ),
    SyncMode(
        id = "weekly",
        label = "Weekly Sync",
        description = "Daily + exam schedule, LMS, and Moodle (7 day staleness window).",
        isBuiltIn = true,
        steps = WEEKLY_STEPS
    ),
    SyncMode(
        id = "monthly",
        label = "Monthly Sync",
        description = "Weekly + curriculum, calendar, finance, transport, grades (30 day staleness window).",
        isBuiltIn = true,
        steps = MONTHLY_STEPS
    ),
    SyncMode(
        id = "quarterly",
        label = "Quarterly Sync",
        description = "Monthly + identity & profile (90 day staleness window).",
        isBuiltIn = true,
        steps = QUARTERLY_STEPS
    )
)

@Serializable
data class SyncConfigProfile(
    val id: String,
    val name: String,
    val enabledModules: Set<SyncModule>,
    val isBuiltIn: Boolean = false
)

val DEFAULT_SYNC_PROFILES = listOf(
    SyncConfigProfile(
        id = "full_sync",
        name = "Full Sync (28 Modules)",
        enabledModules = SyncModule.entries.toSet(),
        isBuiltIn = true
    ),
    SyncConfigProfile(
        id = "quick_sync",
        name = "Quick Sync (3 Modules)",
        enabledModules = setOf(
            SyncModule.ATTENDANCE,
            SyncModule.TIMETABLE,
            SyncModule.MARKS
        ),
        isBuiltIn = true
    ),
    SyncConfigProfile(
        id = "balanced_sync",
        name = "Balanced Sync (16 Modules)",
        enabledModules = setOf(
            SyncModule.ATTENDANCE,
            SyncModule.TIMETABLE,
            SyncModule.MARKS,
            SyncModule.GRADES,
            SyncModule.STUDENT_PROFILE,
            SyncModule.HOSTEL_DETAILS,
            SyncModule.TRANSPORT,
            SyncModule.BUSES,
            SyncModule.EVENTS,
            SyncModule.CLUBS,
            SyncModule.CURRICULUM,
            SyncModule.QCM_VIEW,
            SyncModule.LMS,
            SyncModule.CIRCULARS,
            SyncModule.LIBRARY,
            SyncModule.PAYMENTS
        ),
        isBuiltIn = true
    ),
    SyncConfigProfile(
        id = "academics_only",
        name = "Academics Only (12 Modules)",
        enabledModules = setOf(
            SyncModule.ATTENDANCE,
            SyncModule.ALL_SEMESTER_ATTENDANCE,
            SyncModule.TIMETABLE,
            SyncModule.MARKS,
            SyncModule.GRADES,
            SyncModule.CURRICULUM,
            SyncModule.EXAM_SCHEDULE,
            SyncModule.CALENDAR,
            SyncModule.CALENDARS_LIST,
            SyncModule.CIRCULARS,
            SyncModule.QCM_VIEW,
            SyncModule.LMS
        ),
        isBuiltIn = true
    ),
    SyncConfigProfile(
        id = "campus_life",
        name = "Campus Life (8 Modules)",
        enabledModules = setOf(
            SyncModule.EVENTS,
            SyncModule.CLUBS,
            SyncModule.BUSES,
            SyncModule.TRANSPORT,
            SyncModule.HOSTEL_DETAILS,
            SyncModule.DAYBOARDER,
            SyncModule.PAYMENTS,
            SyncModule.LIBRARY
        ),
        isBuiltIn = true
    ),
    SyncConfigProfile(
        id = "daily_reload",
        name = "Daily Reload (9 Modules)",
        enabledModules = setOf(
            SyncModule.ATTENDANCE,
            SyncModule.TIMETABLE,
            SyncModule.MARKS,
            SyncModule.EXAM_SCHEDULE,
            SyncModule.CALENDAR,
            SyncModule.CALENDARS_LIST,
            SyncModule.MOODLE,
            SyncModule.CIRCULARS,
            SyncModule.LMS
        ),
        isBuiltIn = true
    )
)

object SyncEngine {
    private val activeJobs = mutableSetOf<Job>()
    private var sweepActive = false

    private val _moduleStates = MutableStateFlow(
        SyncModule.entries.associateWith { ModuleState() }
    )
    val moduleStates: StateFlow<Map<SyncModule, ModuleState>> = _moduleStates.asStateFlow()

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _showSyncDialog = MutableStateFlow(false)
    val showSyncDialog: StateFlow<Boolean> = _showSyncDialog.asStateFlow()

    private val _startMinimized = MutableStateFlow(false)
    val startMinimized: StateFlow<Boolean> = _startMinimized.asStateFlow()

    private val _profiles = MutableStateFlow<List<SyncConfigProfile>>(loadProfiles())
    val profiles: StateFlow<List<SyncConfigProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String>(
        SettingsManager.getString(SettingsManager.KEY_ACTIVE_SYNC_PROFILE_ID) ?: "full_sync"
    )
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    // Optional stage-enforced profile: when set, activeProfile resolves to it
    // (for module gating) while activeProfileId / persisted settings stay
    // untouched. Used by onboarding and scheduled sync runs.
    private var profileOverride: String? = null

    val activeProfile: SyncConfigProfile
        get() {
            val id = profileOverride ?: _activeProfileId.value
            return _profiles.value.firstOrNull { it.id == id }
                ?: _profiles.value.firstOrNull()
                ?: DEFAULT_SYNC_PROFILES.first()
        }

    /** Runs [block] while the given profile is enforced, then restores. */
    suspend fun withStageProfile(profileId: String, block: suspend () -> Unit) {
        val previous = profileOverride
        profileOverride = profileId
        try {
            block()
        } finally {
            profileOverride = previous
        }
    }

    fun setShowSyncDialog(show: Boolean, minimized: Boolean = false) {
        _startMinimized.value = minimized
        _showSyncDialog.value = show
    }

    fun toggleSyncDialog() { _showSyncDialog.value = !_showSyncDialog.value }

    // ── Module name → enum mapping (names come from AppState sweep artifacts) ──
    private val MODULE_ALIASES = mapOf(
        "Attendance" to SyncModule.ATTENDANCE,
        "Attendance and CGPA" to SyncModule.ATTENDANCE,
        "All Semesters" to SyncModule.ALL_SEMESTER_ATTENDANCE,
        "All Semesters Attendance" to SyncModule.ALL_SEMESTER_ATTENDANCE,
        "Timetable" to SyncModule.TIMETABLE,
        "Marks" to SyncModule.MARKS,
        "Grade history" to SyncModule.GRADES,
        "Curriculum" to SyncModule.CURRICULUM,
        "Hostel details" to SyncModule.HOSTEL_DETAILS,
        "Exam schedule" to SyncModule.EXAM_SCHEDULE,
        "All Semesters Exam Schedule" to SyncModule.EXAM_SCHEDULE,
        "Academic calendar" to SyncModule.CALENDAR,
        "Calendars list" to SyncModule.CALENDARS_LIST,
        "Calendar" to SyncModule.CALENDARS_LIST,
        "Payments" to SyncModule.PAYMENTS,
        "Library" to SyncModule.LIBRARY,
        "Transport Data" to SyncModule.TRANSPORT,
        "Buses" to SyncModule.BUSES,
        "LMS" to SyncModule.LMS,
        "Registered Events" to SyncModule.EVENTS,
        "Clubs" to SyncModule.CLUBS,
        "QCM View" to SyncModule.QCM_VIEW,
        "Student Profile" to SyncModule.STUDENT_PROFILE,
        "Profile Images" to SyncModule.PROFILE_IMAGES,
        "Credentials" to SyncModule.CREDENTIALS,
        "Bank Information" to SyncModule.BANK_INFO,
        "Dayboarder Info" to SyncModule.DAYBOARDER,
        "EPT Schedule" to SyncModule.EPT_SCHEDULE,
        "Registration Schedule" to SyncModule.REGISTRATION_SCHEDULE,
        "APAAR ID" to SyncModule.APAAR_ID,
        "Circulars" to SyncModule.CIRCULARS,
        "Moodle Assignments" to SyncModule.MOODLE,
        "Moodle" to SyncModule.MOODLE,
    )

    fun moduleOf(name: String): SyncModule? = MODULE_ALIASES[name]

    fun isModuleEnabled(module: SyncModule): Boolean = module in activeProfile.enabledModules

    /**
     * Authoritative pre-flight session check for all sync sweeps and operations.
     *
     * Guarantees that VTOP credentials / session cookies are verified, fresh,
     * and authenticated before any route scraping or API fetching begins.
     *
     * Bypassed automatically when:
     * - [com.amazecc.app.shared.api.AmazeClient.vtopSource] is [com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE].
     * - The sweep contains only modules that do not use VTOP (e.g. LMS, MOODLE).
     */
    suspend fun ensureAuthenticatedSession(
        modules: Set<SyncModule> = emptySet(),
        forceFresh: Boolean = false,
        captchaHandler: com.amazecc.app.shared.vtop.CaptchaHandler? = null
    ): Boolean {
        if (com.amazecc.app.shared.api.AmazeClient.vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return true
        }
        val needsVtop = modules.isEmpty() || modules.any { it != SyncModule.LMS && it != SyncModule.MOODLE }
        if (!needsVtop) return true

        val handler = captchaHandler ?: com.amazecc.app.shared.vtop.CaptchaHandler { challenge ->
            com.amazecc.app.shared.vtop.Vtop.awaitUiCaptcha(challenge)
        }

        return com.amazecc.app.shared.api.AmazeClient.ensureSession(
            forceFresh = forceFresh,
            captchaHandler = handler
        )
    }

    // ── Sweep lifecycle ──
    fun beginSweep(modules: Set<SyncModule>) {
        sweepActive = true
        _moduleStates.value = SyncModule.entries.associateWith { m ->
            if (m in modules) ModuleState(status = SyncStatus.LOADING) else ModuleState()
        }
        recalculateProgress()
    }

    fun endSweep() {
        sweepActive = false
        val updated = _moduleStates.value.mapValues { (_, s) ->
            if (s.status == SyncStatus.LOADING) ModuleState() else s
        }
        _moduleStates.value = updated
        recalculateProgress()
    }

    fun registerJob(job: Job) {
        activeJobs += job
    }

    fun unregisterJob(job: Job) {
        activeJobs -= job
    }

    // ── Module Enablement & Profile Management ──

    fun setActiveProfile(id: String) {
        _activeProfileId.value = id
        SettingsManager.setString(SettingsManager.KEY_ACTIVE_SYNC_PROFILE_ID, id)
    }

    fun setModuleEnabled(module: SyncModule, enabled: Boolean, profileId: String = activeProfile.id) {
        val list = _profiles.value.toMutableList()
        val index = list.indexOfFirst { it.id == profileId }
        if (index != -1) {
            val old = list[index]
            val updated = if (enabled) old.enabledModules + module else old.enabledModules - module
            list[index] = old.copy(enabledModules = updated)
            _profiles.value = list
            saveProfiles(list)
        }
    }

    fun setAllModulesEnabled(enabled: Boolean, profileId: String = activeProfile.id) {
        val list = _profiles.value.toMutableList()
        val index = list.indexOfFirst { it.id == profileId }
        if (index != -1) {
            val old = list[index]
            val updated = if (enabled) SyncModule.entries.toSet() else emptySet()
            list[index] = old.copy(enabledModules = updated)
            _profiles.value = list
            saveProfiles(list)
        }
    }

    fun createProfile(name: String, enabledModules: Set<SyncModule> = SyncModule.entries.toSet()): SyncConfigProfile {
        val newId = "custom_" + Clock.System.now().toEpochMilliseconds()
        val newProfile = SyncConfigProfile(id = newId, name = name, enabledModules = enabledModules, isBuiltIn = false)
        val updated = _profiles.value + newProfile
        _profiles.value = updated
        saveProfiles(updated)
        setActiveProfile(newId)
        return newProfile
    }

    fun updateProfileName(id: String, newName: String) {
        val list = _profiles.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(name = newName)
            _profiles.value = list
            saveProfiles(list)
        }
    }

    fun deleteProfile(id: String) {
        val profile = _profiles.value.firstOrNull { it.id == id }
        if (profile?.isBuiltIn == true) return
        val updated = _profiles.value.filterNot { it.id == id }
        _profiles.value = updated
        saveProfiles(updated)
        if (_activeProfileId.value == id) {
            setActiveProfile(updated.firstOrNull()?.id ?: "full_sync")
        }
    }

    private fun loadProfiles(): List<SyncConfigProfile> {
        val raw = SettingsManager.getString(SettingsManager.KEY_SYNC_PROFILES)
        if (raw.isNullOrBlank()) {
            SettingsManager.setString(SettingsManager.KEY_SYNC_PROFILES_VERSION, "1")
            return DEFAULT_SYNC_PROFILES
        }
        val stored = try {
            Json.decodeFromString<List<SyncConfigProfile>>(raw)
        } catch (_: Exception) {
            DEFAULT_SYNC_PROFILES
        }
        val byId = stored.associateBy { it.id }
        val version = SettingsManager.getString(SettingsManager.KEY_SYNC_PROFILES_VERSION)
        // One-time migration: union built-in defaults into stored built-ins so
        // newly added modules (e.g. MOODLE) appear without undoing later edits.
        val migrated = DEFAULT_SYNC_PROFILES.map { def ->
            val existing = byId[def.id]
            when {
                existing == null -> def
                version.isEmpty() -> existing.copy(enabledModules = (existing.enabledModules + def.enabledModules).toSet())
                else -> existing
            }
        } + stored.filterNot { it.isBuiltIn }
        if (version.isEmpty()) {
            SettingsManager.setString(SettingsManager.KEY_SYNC_PROFILES_VERSION, "1")
            saveProfiles(migrated)
        }
        return migrated
    }

    fun resetProfileToBuiltin(id: String) {
        val def = DEFAULT_SYNC_PROFILES.firstOrNull { it.id == id } ?: return
        val list = _profiles.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(enabledModules = def.enabledModules)
        } else {
            list += def
        }
        _profiles.value = list
        saveProfiles(list)
    }

    private fun saveProfiles(profiles: List<SyncConfigProfile>) {
        try {
            val json = Json.encodeToString(profiles)
            SettingsManager.setString(SettingsManager.KEY_SYNC_PROFILES, json)
        } catch (_: Exception) {}
    }

    // ── State queries ──

    fun getModuleState(module: SyncModule): ModuleState = _moduleStates.value[module] ?: ModuleState()
    fun isModuleLoading(module: SyncModule): Boolean = _moduleStates.value[module]?.status == SyncStatus.LOADING
    fun isModuleSuccess(module: SyncModule): Boolean = _moduleStates.value[module]?.status == SyncStatus.SUCCESS
    fun isModuleError(module: SyncModule): Boolean = _moduleStates.value[module]?.status == SyncStatus.ERROR

    fun isAnyModuleLoading(): Boolean =
        _moduleStates.value.any { (_, s) -> s.status == SyncStatus.LOADING }

    fun isGroupLoading(vararg modules: SyncModule): Boolean =
        modules.any { isModuleLoading(it) }

    // ── Module state mutations ──

    fun updateModuleState(module: SyncModule, state: ModuleState) {
        _moduleStates.value = _moduleStates.value + (module to state)
        recalculateProgress()
    }

    fun markModuleLoading(module: SyncModule) = updateModuleState(module, ModuleState(status = SyncStatus.LOADING))

    fun markModuleSuccess(module: SyncModule) {
        val now = Clock.System.now()
        updateModuleState(
            module, ModuleState(status = SyncStatus.SUCCESS, lastSynced = now)
        )
        recordSyncTime(module, now)
    }

    fun markModuleError(module: SyncModule, error: String?) = updateModuleState(
        module, ModuleState(status = SyncStatus.ERROR, error = error)
    )

    fun resetModule(module: SyncModule) {
        updateModuleState(module, ModuleState())
    }

    fun resetAllStates() {
        _moduleStates.value = SyncModule.entries.associateWith { ModuleState(status = SyncStatus.IDLE) }
        _logLines.value = emptyList()
    }

    // ── Logging ──

    fun addLog(module: SyncModule, message: String, status: SyncStatus) {
        _logLines.value = (_logLines.value + LogLine(module, message, status, Clock.System.now())).takeLast(500)
    }

    fun resetLogs() {
        _logLines.value = emptyList()
    }

    // ── Sync execution ──

    fun cancelAll() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        resetAllStates()
        sweepActive = false
        _syncProgress.value = SyncProgress()
    }

    // ── Progress ──

    private fun recalculateProgress() {
        val states = _moduleStates.value
        val active = states.filter { (_, s) -> s.status == SyncStatus.LOADING }.keys
        val total = states.count { (_, s) -> s.status != SyncStatus.IDLE }
        val completed = states.count { (_, s) -> s.status == SyncStatus.SUCCESS || s.status == SyncStatus.ERROR }
        val successCount = states.count { (_, s) -> s.status == SyncStatus.SUCCESS }
        val errorCount = states.count { (_, s) -> s.status == SyncStatus.ERROR }

        val flatProgress = if (sweepActive) {
            SyncProgress(
                totalModules = total,
                completedModules = completed,
                activeModules = active,
                successCount = successCount,
                errorCount = errorCount
            )
        } else {
            SyncProgress(completedModules = completed, activeModules = active, successCount = successCount, errorCount = errorCount)
        }
        _syncProgress.value = flatProgress
    }

    // ── Sync Modes & Cadence Tiers ──

    val BUILT_IN_MODES: Map<String, SyncMode> get() = com.amazecc.app.shared.state.BUILT_IN_MODES.associateBy { it.id }

    fun listModes(): List<SyncMode> = com.amazecc.app.shared.state.BUILT_IN_MODES

    fun resolveMode(id: String): SyncMode {
        val normalized = when (id) {
            "quick_sync" -> "quick"
            "balanced_sync" -> "balanced"
            "full_sync" -> "full"
            "academics_only" -> "academics"
            "campus_life" -> "campus"
            "daily_reload" -> "daily"
            else -> id
        }
        return com.amazecc.app.shared.state.BUILT_IN_MODES.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
            ?: com.amazecc.app.shared.state.BUILT_IN_MODES.first { it.id == "balanced" }
    }

    fun resolveReloadModeId(switchedSemester: Boolean, modeId: String? = null, defaultSyncMode: String? = null): String {
        if (switchedSemester) return "quarterly"
        return modeId ?: defaultSyncMode ?: "quick"
    }

    fun recordSyncTime(module: SyncModule, at: Instant = Clock.System.now()) {
        try {
            SettingsManager.setString("sync_time_${module.name}", at.toString())
        } catch (_: Exception) {}
    }

    fun getLastSyncedAt(module: SyncModule): Instant? {
        val inMemory = _moduleStates.value[module]?.lastSynced
        if (inMemory != null) return inMemory
        val stored = SettingsManager.getString("sync_time_${module.name}")
        if (stored.isNotBlank()) {
            return runCatching { Instant.parse(stored) }.getOrNull()
        }
        return null
    }

    fun isFresh(module: SyncModule, maxAgeMs: Long, nowMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        val last = getLastSyncedAt(module) ?: return false
        val elapsed = nowMs - last.toEpochMilliseconds()
        return elapsed < maxAgeMs
    }

    fun isFresh(module: SyncModule, windowDays: Int, force: Boolean = false): Boolean {
        if (force || windowDays <= 0) return false
        val last = getLastSyncedAt(module) ?: return false
        val elapsed = Clock.System.now().toEpochMilliseconds() - last.toEpochMilliseconds()
        return elapsed < windowDays * 86_400_000L
    }

    fun isFresh(step: SyncModeStep, force: Boolean = false): Boolean {
        if (force) return false
        val days = step.everyDays ?: return false
        return isFresh(step.module, days, force)
    }

    fun formatSyncAge(module: SyncModule, nowMs: Long): String {
        val last = getLastSyncedAt(module) ?: return "Never"
        val s = maxOf(0L, (nowMs - last.toEpochMilliseconds()) / 1000L)
        if (s < 60) return "Just now"
        val m = s / 60
        if (m < 60) return "${m}m ago"
        val h = m / 60
        if (h < 24) return "${h}h ago"
        val d = h / 24
        return "${d}d ago"
    }

    fun formatSyncAge(module: SyncModule, now: Instant = Clock.System.now()): String {
        val last = getLastSyncedAt(module) ?: return "Never"
        val s = maxOf(0L, (now.toEpochMilliseconds() - last.toEpochMilliseconds()) / 1000L)
        if (s < 60) return "just now"
        val m = s / 60
        if (m < 60) return "$m min ago"
        val h = m / 60
        if (h < 24) return "$h h ago"
        val d = h / 24
        return if (d == 1L) "yesterday" else "$d days ago"
    }

    fun estimateModeCost(mode: SyncMode): Int {
        return mode.steps.size + mode.foregroundSteps.size
    }
}
