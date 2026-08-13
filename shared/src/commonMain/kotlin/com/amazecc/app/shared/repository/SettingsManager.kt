package com.amazecc.app.shared.repository

import com.russhwolf.settings.Settings
import com.amazecc.app.shared.security.Encryption
import kotlinx.serialization.encodeToString

/**
 * SettingsManager mirrors the localStorage caching layer found in the AmazeCC web application.
 * It provides synchronous read/write access to cached API responses and user preferences.
 */
object SettingsManager {
    private val settings: Settings by lazy { Settings() }

    // Key constants mirroring web app
    const val KEY_GPA_GOAL = "uni_cc_gpa_goal"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_CGPA_HIDDEN = "cgpa_hidden"
    const val KEY_ATTENDANCE_MODE = "attendance_display_mode"
    const val KEY_SYNC_EXAM = "sync_exam"
    const val KEY_SYNC_PROFILE = "sync_profile"
    const val KEY_SYNC_ADDITIONAL = "sync_additional"
    const val KEY_SYNC_ARREAR = "sync_arrear"
    const val KEY_NAVBAR_ITEMS = "navbar_items"
    const val KEY_DASHBOARD_WIDGETS = "dashboard_widgets"
    const val KEY_SHOW_ATTENDANCE_IN_STATS = "show_attendance_in_stats"
    const val KEY_STATS_CARDS_ORDER = "stats_cards_order"
    const val KEY_ENABLED_STATS_CARDS = "enabled_stats_cards"
    const val KEY_BUS_SUBSCRIBER = "is_bus_subscriber"
    const val KEY_SYNC_ENABLED_MODULES = "sync_enabled_modules"
    const val KEY_SYNC_PROFILES = "sync_named_profiles"
    const val KEY_ACTIVE_SYNC_PROFILE_ID = "active_sync_profile_id"
    const val KEY_PREFERRED_CALENDAR = "preferred_calendar_name"
    const val KEY_SYNC_PROFILES_VERSION = "sync_profiles_version"

    // Sync automation (light/full scheduled reloads)
    const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    const val KEY_LIGHT_RECURRENCE = "sync_light_recurrence"
    const val KEY_LIGHT_INTERVAL_DAYS = "sync_light_interval_days"
    const val KEY_LIGHT_HOUR = "sync_light_hour"
    const val KEY_LIGHT_MINUTE = "sync_light_minute"
    const val KEY_LIGHT_PROFILE_ID = "sync_light_profile_id"
    const val KEY_FULL_DAY_OF_WEEK = "sync_full_day_of_week"
    const val KEY_FULL_HOUR = "sync_full_hour"
    const val KEY_FULL_MINUTE = "sync_full_minute"
    const val KEY_FULL_PROFILE_ID = "sync_full_profile_id"
    const val KEY_NEXT_LIGHT_SYNC = "next_light_sync"
    const val KEY_NEXT_FULL_SYNC = "next_full_sync"
    const val KEY_LAST_SYNCED_AT = "last_synced_at"

    // Theme & Display preferences
    const val KEY_APP_THEME = "app_theme"
    const val KEY_APP_ACCENT = "app_accent"
    const val KEY_CUSTOM_ACCENT = "app_accent_custom"
    const val KEY_CUSTOM_PALETTE = "custom_palette"
    const val KEY_UI_SCALE = "app_ui_scale"
    const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    const val KEY_ANIMATIONS_ENABLED = "animations_enabled"

    // Update checker
    const val KEY_UPDATE_DISMISSED_VERSION = "update_dismissed_version"
    const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    const val KEY_LATEST_RELEASE_NOTES = "latest_release_notes"

    // Session cache (VTOP credentials)
    const val SESSION_COOKIES = "session_cookies"
    const val SESSION_CSRF = "session_csrf"
    const val SESSION_AUTHORIZED_ID = "session_authorized_id"
    const val SESSION_CLUB_TOKEN = "session_club_token"
    const val SESSION_CREATED_AT = "session_created_at"

    // Library credentials (separate from VTOP)
    const val KEY_LIBRARY_USERNAME = "library_username"
    const val KEY_LIBRARY_PASSWORD = "library_password"

    // Cache keys for all data types
    const val CACHE_VTOP_PHOTO = "cache_vtop_photo"
    const val CACHE_GRADES = "cache_grades"
    const val CACHE_MARKS = "cache_marks"
    const val CACHE_ATTENDANCE = "cache_attendance"
    const val CACHE_TIMETABLE = "cache_timetable"
    const val CACHE_HOSTEL_DETAILS = "cache_hostel_details"
    const val CACHE_HOSTEL_LEAVES = "cache_hostel_leaves"
    const val CACHE_EXAM_SCHEDULE = "cache_exam_schedule"
    const val CACHE_CALENDAR = "cache_calendar"
    const val CACHE_CURRICULUM = "cache_curriculum"
    const val CACHE_PAYMENTS = "cache_payments"
    const val CACHE_LIBRARY = "cache_library"
    const val CACHE_TRANSPORT_DATA = "cache_transport_data"
    const val CACHE_BUSES = "cache_buses"
    const val CACHE_LMS = "cache_lms"
    const val CACHE_EVENTS = "cache_events"
    const val CACHE_CLUBS = "cache_clubs"
    const val CACHE_STUDENT_PROFILE = "cache_student_profile"
    const val KEY_MOODLE_USERNAME = "moodle_username"
    const val KEY_MOODLE_PASSWORD = "moodle_password"
    const val CACHE_ALL_SEMESTER_ATTENDANCE = "cache_all_semester_attendance"
    const val CACHE_ALL_SEMESTER_MARKS = "cache_all_semester_marks"
    const val CACHE_ALL_SEMESTER_EXAMS = "cache_all_semester_exams"
    const val CACHE_CALENDARS_LIST = "cache_calendars_list"
    const val CACHE_QCM_VIEW = "cache_qcm_view"
    const val CACHE_TASKS = "cache_tasks"
    const val CACHE_CIRCULARS = "cache_circulars"
    const val CACHE_CAB_USER = "cache_cab_user"
    const val RESIDENTIAL_STATUS = "residential_status"
    
    // Additional profile cache keys
    const val CACHE_PROFILE_IMAGES = "cache_profile_images"
    const val CACHE_BANK_INFO = "cache_bank_info"
    const val CACHE_DAYBOARDER = "cache_dayboarder"
    const val CACHE_EPT_SCHEDULE = "cache_ept_schedule"
    const val CACHE_REGISTRATION_SCHEDULE = "cache_registration_schedule"
    const val CACHE_UNIVERSITY_DAY = "cache_university_day"
    const val CACHE_APAAR_ID = "cache_apaarid"
    const val CACHE_OD_TRACKER_STATE = "od_tracker_state"
    const val CACHE_MOODLE = "moodle_data_cache"
 
    // Notification preferences
    const val NOTIF_CLASS_REMINDERS = "notif_class_reminders"
    const val NOTIF_ASSIGNMENT_REMINDERS = "notif_assignment_reminders"
    const val NOTIF_TASK_REMINDERS = "notif_task_reminders"
    const val NOTIF_EXAM_REMINDERS = "notif_exam_reminders"
    const val NOTIF_OFFSET_MINUTES = "notif_offset_minutes"
    
    // Quiz mode preferences
    const val KEY_QUIZ_TIMER_ENABLED = "quiz_timer_enabled"

    // SWOT analysis configuration
    const val KEY_SWOT_CONFIG = "swot_config"

    // Quiz performance stats (per-course, per-topic)
    const val KEY_QBANK_STATS = "qbank_stats"

    // Onboarding
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    // Past semester sync flag
    const val PAST_SEMESTER_SYNCED = "past_semester_synced"

    // Securely stored linked-account credentials (encrypted at rest)
    const val CACHE_CREDENTIALS_SECURE = "cache_credentials_secure"

    // Accounts already shown an exam venue/seat alert (entries removed here re-alert if they return)
    const val KEY_EXAM_SEAT_ALERTED = "exam_seat_alerted"

    // Last-known FFCS registration slot (used for change detection + reminder re-scheduling)
    const val CACHE_FFCS_REG_INFO = "cache_ffcs_reg_info"

    // Custom attendance target percentage (overrides bus subscriber / standard defaults)
    const val KEY_CUSTOM_ATTENDANCE_TARGET = "custom_attendance_target_pct"

    fun setFloatString(key: String, value: Float) {
        settings.putString(key, value.toString())
    }

    fun getFloatString(key: String): Float? {
        return settings.getString(key, "")?.toFloatOrNull()
    }

    fun setString(key: String, value: String) {
        settings.putString(key, value)
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return settings.getString(key, defaultValue)
    }

    fun getNullableString(key: String): String? {
        val value = settings.getString(key, "")
        return if (value.isEmpty()) null else value
    }

    fun setBoolean(key: String, value: Boolean) {
        settings.putBoolean(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return settings.getBoolean(key, defaultValue)
    }

    fun setLong(key: String, value: Long) {
        settings.putLong(key, value)
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return settings.getLong(key, defaultValue)
    }

    fun remove(key: String) {
        settings.remove(key)
    }

    fun clearAll() {
        settings.clear()
    }

    fun getExamSeatAlerted(): Set<String> {
        val raw = getString(KEY_EXAM_SEAT_ALERTED, "")
        if (raw.isBlank()) return emptySet()
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<String>>(raw)
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setExamSeatAlerted(accounts: Set<String>) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            setString(KEY_EXAM_SEAT_ALERTED, json.encodeToString(accounts.toList()))
        } catch (_: Exception) { /* non-critical */ }
    }

    // ── Backup & Export support ──
    @kotlinx.serialization.Serializable
    data class ExportEntry(val type: String, val value: String)

    fun allKeys(): Set<String> = settings.keys

    /**
     * Reads a stored value without knowing its type up-front.
     * [type] is "s" (string), "b" (boolean) or "l" (long); null if the key is absent.
     *
     * Reads are exception-safe: on Android, `SharedPreferences.getString` throws a
     * ClassCastException when the stored value is a Boolean/Long, so each getter is
     * probed individually in string -> long -> boolean order.
     */
    fun getExportValue(key: String): ExportEntry? {
        if (key !in settings.keys) return null
        val str = runCatching { settings.getStringOrNull(key) }.getOrNull()
        if (str != null) return ExportEntry("s", str)
        val long = runCatching { settings.getLong(key, Long.MIN_VALUE) }.getOrDefault(Long.MIN_VALUE)
        if (long != Long.MIN_VALUE) return ExportEntry("l", long.toString())
        val bool = runCatching { settings.getBooleanOrNull(key) }.getOrNull()
        return if (bool != null) ExportEntry("b", bool.toString()) else null
    }

    fun importExportEntry(key: String, entry: ExportEntry) {
        when (entry.type) {
            "s" -> setString(key, entry.value)
            "b" -> setBoolean(key, entry.value.toBooleanStrictOrNull() ?: false)
            "l" -> setLong(key, entry.value.toLongOrNull() ?: 0L)
        }
    }
    
    // Web app specific getters/setters for Profile/Settings parity
    fun saveCredentials(username: String, pass: String) {
        setString(KEY_USERNAME, username)
        setString(KEY_PASSWORD, Encryption.encryptOrPlain(pass))
    }
    
    fun getCredentials(): Pair<String, String>? {
        val u = getNullableString(KEY_USERNAME)
        val p = getNullableString(KEY_PASSWORD)
        return if (u != null && p != null) Pair(u, Encryption.decryptOrPlain(p)) else null
    }

    fun saveLibraryCredentials(username: String, password: String) {
        setString(KEY_LIBRARY_USERNAME, Encryption.encryptOrPlain(username))
        setString(KEY_LIBRARY_PASSWORD, Encryption.encryptOrPlain(password))
    }

    fun getLibraryCredentials(): Pair<String, String>? {
        val u = getNullableString(KEY_LIBRARY_USERNAME)
        val p = getNullableString(KEY_LIBRARY_PASSWORD)
        return if (u != null && p != null) Pair(Encryption.decryptOrPlain(u), Encryption.decryptOrPlain(p)) else null
    }

    fun clearLibraryCredentials() {
        remove(KEY_LIBRARY_USERNAME)
        remove(KEY_LIBRARY_PASSWORD)
    }

    fun saveMoodleCredentials(username: String, password: String) {
        setString(KEY_MOODLE_USERNAME, Encryption.encryptOrPlain(username))
        setString(KEY_MOODLE_PASSWORD, Encryption.encryptOrPlain(password))
    }

    fun getMoodleCredentials(): Pair<String, String>? {
        val u = getNullableString(KEY_MOODLE_USERNAME)
        val p = getNullableString(KEY_MOODLE_PASSWORD)
        return if (u != null && p != null) Pair(Encryption.decryptOrPlain(u), Encryption.decryptOrPlain(p)) else null
    }

    fun clearMoodleCredentials() {
        remove(KEY_MOODLE_USERNAME)
        remove(KEY_MOODLE_PASSWORD)
    }

    fun savePreferredCalendar(name: String) {
        setString(KEY_PREFERRED_CALENDAR, name)
    }

    fun getPreferredCalendar(): String? = getNullableString(KEY_PREFERRED_CALENDAR)

    // ── Attendance Notes (per-course per-date "Got Notes?" tracking) ──
    const val CACHE_ATTENDANCE_NOTES = "cache_attendance_notes"

    @kotlinx.serialization.Serializable
    data class NoteEntry(val key: String, val hasNotes: Boolean)

    fun getAttendanceNotes(): Map<String, Boolean> {
        val raw = getString(CACHE_ATTENDANCE_NOTES, "[]")
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<NoteEntry>>(raw).associate { it.key to it.hasNotes }
        } catch (_: Exception) { emptyMap() }
    }

    fun saveAttendanceNote(key: String, hasNotes: Boolean) {
        val notes = getAttendanceNotes().toMutableMap()
        notes[key] = hasNotes
        val entries = notes.map { NoteEntry(it.key, it.value) }
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        setString(CACHE_ATTENDANCE_NOTES, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(NoteEntry.serializer()), entries))
    }

    // ── OD Tracker (wasted/recovered) ──
    fun getODTrackerState(): String {
        return getString(CACHE_OD_TRACKER_STATE, "{}")
    }

    fun saveODTrackerState(json: String) {
        setString(CACHE_OD_TRACKER_STATE, json)
    }

    fun getCourseNote(courseCode: String): String = getString("course_note_$courseCode", "")
    fun saveCourseNote(courseCode: String, note: String) = setString("course_note_$courseCode", note)

    // ── Notification preferences ──
    fun isNotifClassRemindersEnabled(): Boolean = getBoolean(NOTIF_CLASS_REMINDERS, false)
    fun setNotifClassRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_CLASS_REMINDERS, enabled)

    fun isNotifAssignmentRemindersEnabled(): Boolean = getBoolean(NOTIF_ASSIGNMENT_REMINDERS, false)
    fun setNotifAssignmentRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_ASSIGNMENT_REMINDERS, enabled)

    fun isNotifTaskRemindersEnabled(): Boolean = getBoolean(NOTIF_TASK_REMINDERS, false)
    fun setNotifTaskRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_TASK_REMINDERS, enabled)

    fun isNotifExamRemindersEnabled(): Boolean = getBoolean(NOTIF_EXAM_REMINDERS, false)
    fun setNotifExamRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_EXAM_REMINDERS, enabled)

    fun getNotifOffsetMinutes(): Int = getString(NOTIF_OFFSET_MINUTES, "15").toIntOrNull() ?: 15

    fun setNotifOffsetMinutes(minutes: Int) = setString(NOTIF_OFFSET_MINUTES, minutes.toString())

    // Onboarding
    fun isOnboardingComplete(): Boolean = getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(complete: Boolean) = setBoolean(KEY_ONBOARDING_COMPLETE, complete)

    // ── Quiz mode ──
    fun isQuizTimerEnabled(): Boolean = getBoolean(KEY_QUIZ_TIMER_ENABLED, true)
    fun setQuizTimerEnabled(enabled: Boolean) = setBoolean(KEY_QUIZ_TIMER_ENABLED, enabled)

    @kotlinx.serialization.Serializable
    data class SwotConfig(
        val strengthMinAttempts: Int = 3,
        val strengthAccuracy: Int = 70,
        val weaknessMinAttempts: Int = 2,
        val weaknessAccuracy: Int = 50
    )

    fun getSwotConfig(): SwotConfig {
        val raw = getString(KEY_SWOT_CONFIG, "")
        if (raw.isBlank()) return SwotConfig()
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<SwotConfig>(raw)
        } catch (_: Exception) { SwotConfig() }
    }

    fun saveSwotConfig(config: SwotConfig) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        setString(KEY_SWOT_CONFIG, json.encodeToString(config))
    }

    @kotlinx.serialization.Serializable
    data class QuizTopicStat(
        val topic: String = "",
        val attempts: Int = 0,
        val correct: Int = 0,
        val totalTimeSec: Long = 0
    )

    @kotlinx.serialization.Serializable
    data class QuizCourseStats(
        val courseCode: String = "",
        val attempts: Int = 0,
        val correct: Int = 0,
        val totalQuestions: Int = 0,
        val topics: Map<String, QuizTopicStat> = emptyMap(),
        val missedQuestionIds: List<String> = emptyList(),
        val lastPracticedAt: Long = 0
    )

    fun getQBankStats(): Map<String, QuizCourseStats> {
        val raw = getString(KEY_QBANK_STATS, "{}")
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<Map<String, QuizCourseStats>>(raw)
        } catch (_: Exception) { emptyMap() }
    }

    fun saveQBankStats(stats: Map<String, QuizCourseStats>) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        setString(KEY_QBANK_STATS, json.encodeToString<Map<String, QuizCourseStats>>(stats))
    }

    fun getCourseQBankStats(courseCode: String): QuizCourseStats =
        getQBankStats()[courseCode] ?: QuizCourseStats(courseCode = courseCode)

    /**
     * Records a single answered question into the cumulative per-course stats.
     * [correct] may be null for descriptive/reviewed questions (counted as attempts, not correct).
     * Wrong answers are tracked in [missedQuestionIds] until answered correctly.
     */
    fun recordQBankAnswer(courseCode: String, topic: String?, correct: Boolean?, timeSpentSec: Int, questionId: String) {
        val all = getQBankStats().toMutableMap()
        val course = (all[courseCode] ?: QuizCourseStats(courseCode = courseCode))
        val topicKey = topic?.takeIf { it.isNotBlank() } ?: "General"
        val topics = course.topics.toMutableMap()
        val topicStat = topics[topicKey] ?: QuizTopicStat(topic = topicKey)
        topics[topicKey] = topicStat.copy(
            attempts = topicStat.attempts + 1,
            correct = topicStat.correct + if (correct == true) 1 else 0,
            totalTimeSec = topicStat.totalTimeSec + timeSpentSec
        )
        val missed = course.missedQuestionIds.toMutableList()
        when (correct) {
            true -> missed.remove(questionId)
            false -> if (!missed.contains(questionId)) missed.add(questionId)
            null -> {}
        }
        all[courseCode] = course.copy(
            attempts = course.attempts + 1,
            correct = course.correct + if (correct == true) 1 else 0,
            topics = topics,
            missedQuestionIds = missed,
            lastPracticedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        saveQBankStats(all)
    }
}
