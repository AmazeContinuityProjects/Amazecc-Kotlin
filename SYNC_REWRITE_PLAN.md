# SyncEngine Rewrite — Implementation Plan

## Problem Summary

1. Global `_isLoading` lock — only one sync at a time; blocks all pages
2. 7+ pages fall back to `loadAllData()` (16-module sync) when a 1-module refresh suffices
3. `cacheData(key, null)` wipes cache entries on null values
4. Sync toggles (`syncArrear`, `syncExam`) are dead code — never checked
5. No "Last synced" indicator anywhere
6. No "Save Offline" user-facing button
7. Sync errors are invisible (pushed to `_error` string, never surfaced)
8. No per-module sync state — UI can't show what's syncing, what failed, etc.
9. No sync progress popup showing module-by-module status with percentage

## Design

### Core: Offline-First Persistence

- Cache is loaded at startup for EVERY module
- `cacheData()` NEVER deletes — only writes non-null values
- A separate `removeCache(key)` for explicit clearing
- `saveOffline()` writes all currently-loaded in-memory state to cache
- Data survives app restarts without any re-sync
- Partial syncs never wipe other modules' data

---

## Phase 1 — Core Engine (`SyncEngine.kt`)

### SyncModule enum

```
ATTENDANCE, ALL_SEMESTER_ATTENDANCE, TIMETABLE, MARKS, GRADES,
CURRICULUM, HOSTEL_DETAILS, HOSTEL_LEAVES, EXAM_SCHEDULE, CALENDAR,
CALENDARS_LIST, PAYMENTS, LIBRARY, TRANSPORT, BUSES, LMS, EVENTS, CLUBS,
QCM_VIEW, STUDENT_PROFILE, CAB_TRIPS, VITOL
```

### Per-module state

```kotlin
data class ModuleState(
    val status: SyncStatus = IDLE,
    val data: Any? = null,
    val lastSynced: Instant? = null,
    val error: String? = null
)
```

### Sync engine

```kotlin
object SyncEngine {
    private val _moduleStates = MutableStateFlow<Map<SyncModule, ModuleState>>()
    val moduleStates: StateFlow<Map<SyncModule, ModuleState>>
    
    // Sync jobs
    fun syncModule(module: SyncModule): Job
    fun syncAll(): Job
    fun syncGroup(vararg modules: SyncModule): Job
    fun saveOffline()
    fun clearCache()
    
    // Derived
    val syncProgress: StateFlow<SyncProgress>
}

data class SyncProgress(
    val totalModules: Int,
    val completedModules: Int,
    val percentage: Float,
    val activeModule: SyncModule?,
    val logLines: List<LogLine>
)

data class LogLine(
    val module: SyncModule,
    val message: String,
    val status: SyncStatus,
    val timestamp: Instant
)
```

### Cache changes

- `cacheData(key, value)` — NEVER delete on null, only write non-null
- `removeCache(key)` — explicit clear
- `loadCachedData()` at startup restores ALL cached modules

---

## Phase 2 — AppState refactor

- Remove: `_isLoading`, `_syncStatus`, `_error` (global)
- Remove: `headerShowSync`, `headerOnRefresh`
- Remove: `loadSemesterData()`, `refreshCurrentSemester()`, `refreshAllAcademic()`, `syncEventsAndClubs()`
- All existing `_attendance`, `_marks`, `_timetable` etc. StateFlows remain for UI
- Sync functions become thin wrappers calling `SyncEngine.syncModule()` + updating the relevant AppState flow
- `cacheData()` calls remain for backward compat during transition

---

## Phase 3 — Sync Progress Popup

A Compose dialog with:
- Full-screen backdrop
- Centered card with rounded corners
- Overall progress bar with percentage
- Module list — each shows name, status icon (spinner/green check/red X/grey), last-synced time
- Animated scrollable log of sync messages
- Minimize/maximize toggle
- Dismiss button
- Reads from `SyncEngine.moduleStates` and `SyncEngine.syncProgress`

---

## Phase 4 — FloatingScreenHeader

- Accepts `Set<SyncModule>` instead of `onRefresh`
- Reads per-module state from SyncEngine
- Shows "Last synced: X ago" subtitle
- Error badge on error
- Sync button opens the progress popup

---

## Phase 5 — Screen wiring

| Screen | Sync Module(s) |
|---|---|
| DashboardScreen | `syncAll()` + opens popup |
| AttendanceScreen | `ATTENDANCE, TIMETABLE` |
| AcademicsScreen | `ATTENDANCE, MARKS, TIMETABLE` |
| MarksTimelineScreen | `MARKS, ATTENDANCE, TIMETABLE` |
| HostelScreen | `HOSTEL_DETAILS, HOSTEL_LEAVES` |
| CalendarScreen | `CALENDARS_LIST` |
| PaymentsScreen | `PAYMENTS` |
| CabShareScreen | `CAB_TRIPS` |
| TransportScreen | `TRANSPORT, BUSES` |
| SocialScreen | `ATTENDANCE, MARKS, TIMETABLE` |
| ProfileScreen | `STUDENT_PROFILE` |
| EventHubScreen | `EVENTS, CLUBS` |
| CourseDetailScreen | `QCM_VIEW` |
| VitolScreen | `VITOL` |
| CurriculumScreen | `CURRICULUM` |
| SettingsScreen | Sync dashboard + Save Offline |
| Others | no sync |

---

## Phase 6 — Settings Sync Dashboard

- Sync status card with overall progress
- "Sync All" button
- "Save Offline" button (persists all in-memory data)
- Per-module cards: status dot, last synced, individual sync button
- Toggle switches for optional modules (actually respected)
- "Clear Cache" button
