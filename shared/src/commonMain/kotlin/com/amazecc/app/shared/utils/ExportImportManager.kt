package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupSetting(val key: String, val type: String, val value: String)

@Serializable
data class BackupFile(
    val app: String = "AmazeCC",
    val formatVersion: Int = 1,
    val type: String,
    val exportedAt: String,
    val settings: List<BackupSetting> = emptyList(),
    val tasks: List<HomeworkTask> = emptyList()
)

data class ImportResult(val settingsImported: Int, val tasksImported: Int)

/**
 * Builds / restores JSON backups of local AmazeCC data.
 * Custom backups hold preferences + tasks; full backups additionally include
 * all cached API data (attendance, grades, timetable, ...). Credentials and
 * session data are never exported.
 */
object ExportImportManager {

    private val json = Json { ignoreUnknownKeys = true }

    // Preference keys exported with custom (and full) backups. Session cookies,
    // CSRF tokens and saved credentials are deliberately excluded.
    val preferenceKeys: List<String> = listOf(
        SettingsManager.KEY_GPA_GOAL,
        SettingsManager.KEY_CGPA_HIDDEN,
        SettingsManager.KEY_ATTENDANCE_MODE,
        SettingsManager.KEY_SYNC_EXAM,
        SettingsManager.KEY_SYNC_PROFILE,
        SettingsManager.KEY_SYNC_ADDITIONAL,
        SettingsManager.KEY_SYNC_ARREAR,
        SettingsManager.KEY_NAVBAR_ITEMS,
        SettingsManager.KEY_DASHBOARD_WIDGETS,
        SettingsManager.KEY_SYNC_ENABLED_MODULES,
        SettingsManager.KEY_SYNC_PROFILES,
        SettingsManager.KEY_ACTIVE_SYNC_PROFILE_ID,
        SettingsManager.KEY_PREFERRED_CALENDAR,
        SettingsManager.KEY_SYNC_PROFILES_VERSION,
        SettingsManager.KEY_AUTO_SYNC_ENABLED,
        SettingsManager.KEY_LIGHT_RECURRENCE,
        SettingsManager.KEY_LIGHT_INTERVAL_DAYS,
        SettingsManager.KEY_LIGHT_HOUR,
        SettingsManager.KEY_LIGHT_MINUTE,
        SettingsManager.KEY_LIGHT_PROFILE_ID,
        SettingsManager.KEY_FULL_DAY_OF_WEEK,
        SettingsManager.KEY_FULL_HOUR,
        SettingsManager.KEY_FULL_MINUTE,
        SettingsManager.KEY_FULL_PROFILE_ID,
        SettingsManager.KEY_NEXT_LIGHT_SYNC,
        SettingsManager.KEY_NEXT_FULL_SYNC,
        SettingsManager.KEY_LAST_SYNCED_AT,
        SettingsManager.KEY_APP_THEME,
        SettingsManager.KEY_APP_ACCENT,
        SettingsManager.KEY_UI_SCALE,
        SettingsManager.KEY_HAPTIC_ENABLED,
        SettingsManager.KEY_ANIMATIONS_ENABLED,
        SettingsManager.KEY_UPDATE_DISMISSED_VERSION,
        SettingsManager.KEY_LAST_UPDATE_CHECK,
        SettingsManager.KEY_LATEST_RELEASE_NOTES,
        SettingsManager.RESIDENTIAL_STATUS,
        SettingsManager.NOTIF_CLASS_REMINDERS,
        SettingsManager.NOTIF_ASSIGNMENT_REMINDERS,
        SettingsManager.NOTIF_TASK_REMINDERS,
        SettingsManager.NOTIF_EXAM_REMINDERS,
        SettingsManager.NOTIF_OFFSET_MINUTES,
        SettingsManager.KEY_QUIZ_TIMER_ENABLED,
        SettingsManager.KEY_SWOT_CONFIG,
        SettingsManager.KEY_QBANK_STATS,
        SettingsManager.KEY_ONBOARDING_COMPLETE,
        SettingsManager.PAST_SEMESTER_SYNCED
    )

    /** Builds the JSON backup string. [includeCache] selects custom vs full scope. */
    fun buildBackupJson(includeCache: Boolean): String {
        val allKeys = SettingsManager.allKeys()
        val settingKeys = if (includeCache) {
            (preferenceKeys + allKeys.filter { it.startsWith("cache_") && it != SettingsManager.CACHE_TASKS }).distinct()
        } else {
            preferenceKeys
        }
        val settings = settingKeys.mapNotNull { key ->
            SettingsManager.getExportValue(key)?.let { BackupSetting(key, it.type, it.value) }
        }
        val tasks = try {
            val raw = SettingsManager.getString(SettingsManager.CACHE_TASKS, "[]")
            json.decodeFromString(ListSerializer(HomeworkTask.serializer()), raw)
        } catch (_: Exception) {
            emptyList()
        }
        val backup = BackupFile(
            type = if (includeCache) "full" else "custom",
            exportedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
            settings = settings,
            tasks = tasks
        )
        return json.encodeToString(backup)
    }

    /** Restores a backup. Returns counts of imported settings/tasks on success. */
    fun importFromJson(raw: String): Result<ImportResult> = runCatching {
        val backup = json.decodeFromString<BackupFile>(raw)
        require(backup.app == "AmazeCC") { "Not an AmazeCC backup file" }
        backup.settings.forEach { entry ->
            SettingsManager.importExportEntry(entry.key, SettingsManager.ExportEntry(entry.type, entry.value))
        }
        if (backup.tasks.isNotEmpty()) {
            AppState.applyImportedTasks(backup.tasks)
        }
        AppState.reloadWidgetOrder()
        AppState.loadAllData()
        ImportResult(backup.settings.size, backup.tasks.size)
    }

    fun backupFileName(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun pad(value: Int) = value.toString().padStart(2, '0')
        val stamp = "${now.year}${pad(now.monthNumber)}${pad(now.dayOfMonth)}-${pad(now.hour)}${pad(now.minute)}"
        return "amazecc-backup-$stamp.json"
    }
}
