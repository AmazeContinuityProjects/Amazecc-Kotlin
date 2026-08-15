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
        name = "Quick Sync (7 Modules)",
        enabledModules = setOf(
            SyncModule.ATTENDANCE,
            SyncModule.TIMETABLE,
            SyncModule.MARKS,
            SyncModule.GRADES,
            SyncModule.HOSTEL_DETAILS,
            SyncModule.PAYMENTS,
            SyncModule.TRANSPORT
        ),
        isBuiltIn = true
    ),
SyncConfigProfile(
        id = "academics_only",
        name = "Academics Only (10 Modules)",
        enabledModules = setOf(
            SyncModule.ATTENDANCE,
            SyncModule.ALL_SEMESTER_ATTENDANCE,
            SyncModule.TIMETABLE,
            SyncModule.GRADES,
            SyncModule.CURRICULUM,
            SyncModule.EXAM_SCHEDULE,
            SyncModule.CALENDAR,
            SyncModule.CIRCULARS,
            SyncModule.QCM_VIEW,
            SyncModule.LMS
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

    fun markModuleSuccess(module: SyncModule) = updateModuleState(
        module, ModuleState(status = SyncStatus.SUCCESS, lastSynced = Clock.System.now())
    )

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
}
