package com.amazecc.app.shared.state

import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class SyncModule(
    val displayName: String,
    val cacheKey: String? = null,
) {
    ATTENDANCE("Attendance", SettingsManager.CACHE_ATTENDANCE),
    ALL_SEMESTER_ATTENDANCE("All Semesters", null),
    TIMETABLE("Timetable", SettingsManager.CACHE_TIMETABLE),
    MARKS("Marks", SettingsManager.CACHE_MARKS),
    GRADES("Grade History", SettingsManager.CACHE_GRADES),
    CURRICULUM("Curriculum", SettingsManager.CACHE_CURRICULUM),
    HOSTEL_DETAILS("Hostel Details", SettingsManager.CACHE_HOSTEL_DETAILS),
    HOSTEL_LEAVES("Hostel Leaves", SettingsManager.CACHE_HOSTEL_LEAVES),
    EXAM_SCHEDULE("Exam Schedule", SettingsManager.CACHE_EXAM_SCHEDULE),
    CALENDAR("Academic Calendar", SettingsManager.CACHE_CALENDAR),
    CALENDARS_LIST("Calendars List", SettingsManager.CACHE_CALENDARS_LIST),
    PAYMENTS("Payments", SettingsManager.CACHE_PAYMENTS),
    LIBRARY("Library", SettingsManager.CACHE_LIBRARY),
    TRANSPORT("Transport Data", SettingsManager.CACHE_TRANSPORT_DATA),
    BUSES("Buses", SettingsManager.CACHE_BUSES),
    LMS("LMS", SettingsManager.CACHE_LMS),
    EVENTS("Events", SettingsManager.CACHE_EVENTS),
    CLUBS("Clubs", SettingsManager.CACHE_CLUBS),
    QCM_VIEW("QCM View", SettingsManager.CACHE_QCM_VIEW),
    STUDENT_PROFILE("Student Profile", SettingsManager.CACHE_STUDENT_PROFILE),
    CAB_TRIPS("Cab Trips", SettingsManager.CACHE_CAB_TRIPS),
    VITOL("Vitol", SettingsManager.CACHE_VITOL),
}

enum class SyncStatus { IDLE, LOADING, SUCCESS, ERROR }

data class ModuleState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSynced: Instant? = null,
    val error: String? = null,
)

data class LogLine(
    val module: SyncModule,
    val message: String,
    val status: SyncStatus,
    val timestamp: Instant,
)

data class SyncProgress(
    val totalModules: Int = SyncModule.entries.size,
    val completedModules: Int = 0,
    val activeModules: Set<SyncModule> = emptySet(),
) {
    val percentage: Float
        get() = if (totalModules == 0) 0f else (completedModules.toFloat() / totalModules.toFloat()) * 100f
    val displayText: String
        get() = "$completedModules / $totalModules modules"
}

object SyncEngine {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

    fun setShowSyncDialog(show: Boolean) { _showSyncDialog.value = show }
    fun toggleSyncDialog() { _showSyncDialog.value = !_showSyncDialog.value }

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

    fun resetLogs() {
        _logLines.value = emptyList()
    }

    // ── Logging ──

    fun addLog(module: SyncModule, message: String, status: SyncStatus) {
        _logLines.value = _logLines.value + LogLine(module, message, status, Clock.System.now())
    }

    // ── Sync execution ──

    fun startSync(module: SyncModule, block: suspend () -> ModuleState): Job? {
        if (activeJobs[module]?.isActive == true) return null
        addLog(module, "Starting...", SyncStatus.LOADING)
        val job = scope.launch {
            updateModuleState(module, ModuleState(status = SyncStatus.LOADING))
            addLog(module, "In progress...", SyncStatus.LOADING)
            try {
                val result = block()
                updateModuleState(module, result)
                val msg = if (result.status == SyncStatus.SUCCESS) "Completed" else (result.error ?: "Failed")
                addLog(module, msg, result.status)
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                updateModuleState(module, ModuleState(status = SyncStatus.ERROR, error = errMsg))
                addLog(module, errMsg, SyncStatus.ERROR)
            } finally {
                activeJobs.remove(module)
            }
        }
        activeJobs[module] = job
        return job
    }

    fun startSyncGroup(vararg modules: SyncModule, block: suspend (SyncModule) -> ModuleState) {
        modules.forEach { module ->
            startSync(module) { block(module) }
        }
    }

    fun startSyncAll(block: suspend (SyncModule) -> ModuleState) {
        SyncModule.entries.forEach { module ->
            if (module.cacheKey != null || module == SyncModule.ALL_SEMESTER_ATTENDANCE || module == SyncModule.CAB_TRIPS || module == SyncModule.VITOL) {
                startSync(module) { block(module) }
            }
        }
    }

    fun cancelSync(module: SyncModule) {
        activeJobs[module]?.cancel()
        activeJobs.remove(module)
        updateModuleState(module, ModuleState())
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        resetAllStates()
    }

    // ── Progress ──

    private fun recalculateProgress() {
        val states = _moduleStates.value
        val completed = states.count { (_, s) ->
            s.status == SyncStatus.SUCCESS || s.status == SyncStatus.ERROR
        }
        val active = states.filter { (_, s) -> s.status == SyncStatus.LOADING }.keys
        _syncProgress.value = SyncProgress(
            totalModules = states.size,
            completedModules = completed,
            activeModules = active,
        )
    }

    // ── Save offline (cache all loaded data) ──
    // This function is called from AppState.saveOffline() which provides the actual data
    fun logSaveOffline(module: SyncModule, data: Any?) {
        if (data != null) {
            addLog(module, "Saved offline", SyncStatus.SUCCESS)
        } else {
            addLog(module, "No data to save", SyncStatus.IDLE)
        }
    }

    // ── Session refresh tracking ──
    private val _lastSessionRefresh = MutableStateFlow<Instant?>(null)
    val lastSessionRefresh: StateFlow<Instant?> = _lastSessionRefresh.asStateFlow()

    fun markSessionRefreshed() {
        _lastSessionRefresh.value = Clock.System.now()
    }

    // ── Last synced timestamps (aggregated for display) ──
    val lastSyncTime: Instant?
        get() = _moduleStates.value.values
            .mapNotNull { it.lastSynced }
            .maxOrNull()

    // ── Sync buttons tracking ──
    private val _lastSyncButtonTap = MutableStateFlow<Instant?>(null)
    val lastSyncButtonTap: StateFlow<Instant?> = _lastSyncButtonTap.asStateFlow()

    fun markSyncButtonTapped() {
        _lastSyncButtonTap.value = Clock.System.now()
    }
}
