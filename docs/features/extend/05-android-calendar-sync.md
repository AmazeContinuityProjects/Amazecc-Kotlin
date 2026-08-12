# Android Calendar & Tasks Sync (Local SDK)

## Requirements
- Sync with **local Android Calendar** (CalendarContract) and **Tasks** provider
- **No Google account required** - uses local calendar database
- **Explicit user approval** via runtime permissions
- **Bi-directional**: App → System, System → App (pull on app open)
- Android 11+ (API 30+) for Tasks provider; fallback for older

---

## Permissions
```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

---

## Calendar Sync (CalendarContract)

### Calendar Selection
- On first sync: show calendar picker (local calendars only)
- Save selected calendar ID in SettingsManager

### Event Mapping
| Task Type | Calendar Event |
|-----------|----------------|
| `exam`, `quiz` | Event with title "[EXAM] Course - Title", start/end from examTime, alarms at reportingTime |
| `assignment` | Event with title "[ASSIGNMENT] Course - Title", all-day at deadline, reminder 1 day before |
| `homework`, `project`, `lab` | Optional: all-day event at dueDate |
| Work sessions | Optional: events at session times |

### Sync Logic (App → Calendar)
1. Query tasks with `showOnCalendar=true` or type in [exam, quiz, assignment]
2. For each: check if event exists (by custom extended property `amazecc_task_id`)
3. Insert/update/delete accordingly
4. Set reminders via `Reminders` table

### Sync Logic (Calendar → App)
1. Query events from selected calendar modified since last sync
2. Filter by extended property `amazecc_task_id` exists
3. Create/update tasks (type inferred from prefix or extended property)
4. Don't delete app tasks if calendar event deleted (user may have deleted accidentally)

### Conflict Resolution
- Last-write-wins based on `updatedAt` timestamp
- If conflict: show notification, let user choose

---

## Tasks Sync (Tasks Provider)

### Android 11+ (Tasks Provider)
- Content URI: `content://com.google.android.apps.tasks.provider/tasks`
- Or local: `content://tasks/tasks` (device-dependent)
- Use `TasksContract` if available

### Pre-Android 11 / Fallback
- Local Room database mirror
- Export/import via JSON (already covered in Phase 4)

### Task Mapping
| HomeworkTask Field | Tasks Provider Column |
|--------------------|----------------------|
| title | title |
| description | notes |
| dueDate + dueTime | dueDate (ms) |
| completed | status (COMPLETED/NEEDS_ACTION) |
| priority | priority (HIGH/MEDIUM/LOW) |
| reminderAt | hasAlarm + alarmTime |
| amazecc_task_id | extendedProperty |

---

## Settings UI
```
Settings → Data & Sync
├── Sync with System Calendar [Toggle]
│   └── Select Calendar [Picker]
├── Sync with System Tasks [Toggle] (Android 11+)
├── Auto-sync on app open [Toggle]
└── Last sync: <timestamp>
```

---

## Implementation Files

### New: `CalendarSyncManager.kt` (androidMain)
```kotlin
class CalendarSyncManager(context: Context) {
    suspend fun syncTasksToCalendar(tasks: List<HomeworkTask>)
    suspend fun syncCalendarToTasks(): List<HomeworkTask>
    suspend fun requestPermissions(): Boolean
    fun pickCalendar(): CalendarPickerDialog
}
```

### New: `TasksSyncManager.kt` (androidMain, API 30+)
```kotlin
class TasksSyncManager(context: Context) {
    suspend fun syncTasks(tasks: List<HomeworkTask>)
    suspend fun pullTasks(): List<HomeworkTask>
}
```

### AppState.kt additions
- `calendarSyncEnabled: Boolean`
- `tasksSyncEnabled: Boolean`
- `selectedCalendarId: String?`
- `lastCalendarSync: Long`
- `lastTasksSync: Long`
- `syncCalendarAndTasks()` function

---

## Files to Create/Modify
1. `CalendarSyncManager.kt` (androidMain)
2. `TasksSyncManager.kt` (androidMain)
3. `AppState.kt` - Sync state + triggers
4. `SettingsManager.kt` - Calendar ID storage
5. Settings screen - Sync toggles + calendar picker
6. `build.gradle.kts` - Add calendar/tasks dependencies if needed