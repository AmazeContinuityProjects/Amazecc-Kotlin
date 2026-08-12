# Export / Import System

## Requirements

### Export Type 1: Custom Settings + Tasks + OD Hours
**Contents**:
- User settings: theme, accent, widget order, notification prefs, UI scale, haptics, animations
- All tasks: `HomeworkTask` list (including workSessions, reminders, OD hours)
- OD Tracker state: wasted/recovered hours
- Quiz stats (SWOT config, QBANK stats)
- Course notes

**Format**: Single JSON file
**Filename**: `amazecc-custom-export-<YYYYMMDD-HHMMSS>.json`

### Export Type 2: Full Data Export
**Contents**: ALL cached data from SettingsManager:
- Attendance (current + all semesters)
- Marks (current + all semesters)
- Grades (all semesters)
- Timetable
- Exam schedule
- Calendar (all calendars + months)
- Curriculum
- Payments
- Library
- Transport
- Events
- Clubs
- Student profile
- Moodle data
- Tasks
- OD tracker
- QCM view
- Circulars
- Cab share
- All settings
- Quiz stats
- SWOT config

**Format**: Single JSON file
**Filename**: `amazecc-full-export-<YYYYMMDD-HHMMSS>.json`

### Import
- Both formats importable via file picker (ACTION_OPEN_DOCUMENT)
- Merge strategy:
  - Settings: overwrite
  - Tasks: upsert by ID (merge workSessions, reminders)
  - Cache data: overwrite (full export) or merge (custom)
  - OD tracker: merge by date
  - Quiz stats: merge by course/topic

---

## Implementation

### New File: `ExportImportManager.kt` (commonMain)
```kotlin
object ExportImportManager {
    @Serializable
    data class CustomExport(
        val version: Int = 1,
        val exportedAt: String, // ISO datetime
        val settings: SettingsExport,
        val tasks: List<HomeworkTask>,
        val odTracker: String, // JSON string
        val quizStats: Map<String, QuizCourseStats>,
        val swotConfig: SwotConfig,
        val courseNotes: Map<String, String>
    )

    @Serializable
    data class FullExport(
        val version: Int = 1,
        val exportedAt: String,
        val allCache: Map<String, String>, // key -> JSON string
        val settings: SettingsExport
    )

    @Serializable
    data class SettingsExport(
        val theme: String,
        val accent: String,
        val uiScale: Float,
        val widgetOrder: List<String>,
        val notificationPrefs: NotificationPrefs,
        // ... all settings keys
    )

    suspend fun exportCustom(context: Context): Result<Uri>
    suspend fun exportFull(context: Context): Result<Uri>
    suspend fun importCustom(context: Context, uri: Uri): Result<Unit>
    suspend fun importFull(context: Context, uri: Uri): Result<Unit>
}
```

### Settings Export Keys
Collect from SettingsManager all user-preference keys (not cache keys)

### Android Integration
- Use `DocumentsContract` + `ContentResolver` for file picker
- Write to `Downloads/AmazeCC/` or let user choose
- Share intent for easy transfer

---

## Files to Create/Modify
1. `ExportImportManager.kt` (new, commonMain)
2. `SettingsManager.kt` - Add `exportAllSettings()` / `importSettings()`
3. `AppState.kt` - Add export/import trigger functions
4. Settings UI - Add "Export Data" / "Import Data" section