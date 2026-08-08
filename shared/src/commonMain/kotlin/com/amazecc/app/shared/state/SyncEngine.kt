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
    STUDENT_PROFILE("Student Profile", SettingsManager.CACHE_STUDENT_PROFILE, SyncCategory.PROFILE_MISC),
    CIRCULARS("Circulars", SettingsManager.CACHE_CIRCULARS, SyncCategory.ACADEMICS),
    PROFILE_IMAGES("Profile Images", SettingsManager.CACHE_PROFILE_IMAGES, SyncCategory.PROFILE_MISC),
    BANK_INFO("Bank Information", SettingsManager.CACHE_BANK_INFO, SyncCategory.FINANCE_SERVICES),
    DAYBOARDER("Dayboarder Info", SettingsManager.CACHE_DAYBOARDER, SyncCategory.CAMPUS_HOSTEL),
    EPT_SCHEDULE("EPT Schedule", SettingsManager.CACHE_EPT_SCHEDULE, SyncCategory.PROFILE_MISC),
    REGISTRATION_SCHEDULE("Registration Schedule", SettingsManager.CACHE_REGISTRATION_SCHEDULE, SyncCategory.PROFILE_MISC),
    APAAR_ID("APAAR ID", SettingsManager.CACHE_APAAR_ID, SyncCategory.PROFILE_MISC),
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
        get() = if (totalModules == 0) 100f else ((completedModules.toFloat() / totalModules.toFloat()).coerceIn(0f, 1f)) * 100f

    val displayText: String
        get() = when {
            activeModules.isNotEmpty() -> "Syncing ${activeModules.first().displayName} (${completedModules + 1}/$totalModules)"
            completedModules == totalModules && errorCount > 0 -> "Completed with $errorCount errors"
            completedModules == totalModules && totalModules > 0 -> "All $totalModules modules updated"
            else -> "$completedModules / $totalModules modules"
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
        name = "Full Sync (27 Modules)",
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
    )
)

object SyncEngine {
    private val activeJobs = mutableMapOf<SyncModule, Job>()

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

    val activeProfile: SyncConfigProfile
        get() = _profiles.value.firstOrNull { it.id == _activeProfileId.value } ?: _profiles.value.firstOrNull() ?: DEFAULT_SYNC_PROFILES.first()

    fun setShowSyncDialog(show: Boolean, minimized: Boolean = false) {
        _startMinimized.value = minimized
        _showSyncDialog.value = show
    }

    fun toggleSyncDialog() { _showSyncDialog.value = !_showSyncDialog.value }

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
        if (raw.isNullOrBlank()) return DEFAULT_SYNC_PROFILES
        return try {
            Json.decodeFromString<List<SyncConfigProfile>>(raw)
        } catch (_: Exception) {
            DEFAULT_SYNC_PROFILES
        }
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

    fun resetModule(module: SyncModule) {
        updateModuleState(module, ModuleState())
    }

    fun resetAllStates() {
        _moduleStates.value = SyncModule.entries.associateWith { ModuleState(status = SyncStatus.IDLE) }
        _logLines.value = emptyList()
    }

    fun markAllLoading() {
        _moduleStates.value = SyncModule.entries.associateWith { ModuleState(status = SyncStatus.LOADING) }
    }

    fun resetLoadingToIdle() {
        val updated = _moduleStates.value.mapValues { (_, state) ->
            if (state.status == SyncStatus.LOADING) ModuleState() else state
        }
        _moduleStates.value = updated
    }

    fun resetLogs() {
        _logLines.value = emptyList()
    }

    // ── Logging ──

    fun addLog(module: SyncModule, message: String, status: SyncStatus) {
        _logLines.value = (_logLines.value + LogLine(module, message, status, Clock.System.now())).takeLast(500)
    }

    // ── Sync execution ──

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        resetAllStates()
    }

    // ── Progress ──

    private fun recalculateProgress() {
        val states = _moduleStates.value
        val targetModules = states.keys.toSet()
        val total = targetModules.size
        val completed = targetModules.count { mod ->
            val s = states[mod]
            s?.status == SyncStatus.SUCCESS || s?.status == SyncStatus.ERROR
        }
        val active = states.filter { (mod, s) -> mod in targetModules && s.status == SyncStatus.LOADING }.keys
        val successCount = targetModules.count { states[it]?.status == SyncStatus.SUCCESS }
        val errorCount = targetModules.count { states[it]?.status == SyncStatus.ERROR }

        _syncProgress.value = SyncProgress(
            totalModules = total,
            completedModules = completed,
            activeModules = active,
            successCount = successCount,
            errorCount = errorCount
        )
    }
}
