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
    const val KEY_HONO_API_URL = "hono_api_url"
    const val KEY_GPA_GOAL = "uni_cc_gpa_goal"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_CGPA_HIDDEN = "cgpa_hidden"
    const val KEY_ATTENDANCE_MODE = "attendance_display_mode"
    const val KEY_SYNC_EXAM = "sync_exam"
    const val KEY_SYNC_PROFILE = "sync_profile"
    const val KEY_SYNC_ADDITIONAL = "sync_additional"
    const val KEY_NAVBAR_ITEMS = "navbar_items"
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
    const val KEY_HERO_COLOR_ENABLED = "hero_color_enabled"

    // Current semester selection (persisted across restarts)
    const val KEY_SELECTED_SEMESTER = "selected_semester"

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

    // Where VTOP data is fetched from: "LOCAL" (on-device) or "REMOTE" (hono.amazecc.com).
    // VTOP only accepts Indian IP space, so LOCAL is the default — the server proxy cannot
    // reach it. See docs/sep-29-2026/vtop-local-integration-plan.md
    const val KEY_VTOP_SOURCE = "vtop_source"

    // Cached User-Agent currently in use by the local VTOP engine
    const val KEY_VTOP_LAST_UA = "vtop_last_ua"

    /**
     * Opt-in for always typing the VTOP captcha by hand.
     *
     * Off by default, which leaves the recogniser to decide: it auto-submits when it is confident
     * enough and otherwise falls back to the manual prompt on its own. Turning this on skips the
     * recogniser entirely, for when auto-submitting a wrong guess wastes an attempt.
     */
    const val KEY_VTOP_MANUAL_CAPTCHA = "vtop_manual_captcha"

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
const val CACHE_MESS_MENU = "cache_mess_menu"
const val CACHE_LAUNDRY = "cache_laundry"
const val CACHE_HOSTEL_COUNSELLING = "cache_hostel_counselling"
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

    // Merged student identity (encrypted at rest — contains aadhar/bank/credential passwords)
    const val CACHE_USER_IDENTITY = "cache_user_identity"

    // Unified academic/campus data snapshot (encrypted at rest — replaces the per-module CACHE_* caches)
    const val CACHE_APP_DATA = "cache_app_data"

    // Accounts already shown an exam venue/seat alert (entries removed here re-alert if they return)
    const val KEY_EXAM_SEAT_ALERTED = "exam_seat_alerted"

    // Last-known FFCS registration slot (used for change detection + reminder re-scheduling)
    const val CACHE_FFCS_REG_INFO = "cache_ffcs_reg_info"

    // D-154: last-known FFCS feedback status, mirrored from AppDataStore for cold starts
    const val CACHE_FFCS_FEEDBACK_STATUS = "cache_ffcs_feedback_status"

    // Custom attendance target percentage (overrides bus subscriber / standard defaults)
    const val KEY_CUSTOM_ATTENDANCE_TARGET = "custom_attendance_target_pct"

    // ── App icon theme (web parity — ProfilePage Application Icon) ──
    const val KEY_APP_ICON = "app_icon"

    fun getAppIconName(): String = getString(KEY_APP_ICON, "DEFAULT")
    fun setAppIconName(name: String) = setString(KEY_APP_ICON, name)

    // ── Privacy mode (web parity — settingsAtoms.ts privacyMode) ──
    const val KEY_PRIVACY_MODE = "privacy_mode"

    fun isPrivacyMode(): Boolean = getBoolean(KEY_PRIVACY_MODE, false)
    fun setPrivacyMode(enabled: Boolean) = setBoolean(KEY_PRIVACY_MODE, enabled)

    // ── Pomodoro defaults (web parity — ProfilePage taskPomodoro*) ──
    const val KEY_POMODORO_FOCUS = "task_pomodoro_focus"
    const val KEY_POMODORO_BREAK = "task_pomodoro_break"
    const val KEY_POMODORO_ROUNDS = "task_pomodoro_rounds"

    fun getPomodoroFocusMinutes(): Int = getString(KEY_POMODORO_FOCUS, "25").toIntOrNull() ?: 25
    fun setPomodoroFocusMinutes(minutes: Int) = setString(KEY_POMODORO_FOCUS, minutes.toString())

    fun getPomodoroBreakMinutes(): Int = getString(KEY_POMODORO_BREAK, "5").toIntOrNull() ?: 5
    fun setPomodoroBreakMinutes(minutes: Int) = setString(KEY_POMODORO_BREAK, minutes.toString())

    fun getPomodoroRounds(): Int = getString(KEY_POMODORO_ROUNDS, "4").toIntOrNull() ?: 4
    fun setPomodoroRounds(rounds: Int) = setString(KEY_POMODORO_ROUNDS, rounds.toString())

    // ── Quiet hours (web parity — PushNotificationManager quiet hours) ──
    const val KEY_PUSH_QUIET_HOURS_ENABLED = "push_quiet_hours_enabled"
    const val KEY_PUSH_QUIET_HOURS_START = "push_quiet_hours_start"
    const val KEY_PUSH_QUIET_HOURS_END = "push_quiet_hours_end"

    fun isQuietHoursEnabled(): Boolean = getBoolean(KEY_PUSH_QUIET_HOURS_ENABLED, false)
    fun setQuietHoursEnabled(enabled: Boolean) = setBoolean(KEY_PUSH_QUIET_HOURS_ENABLED, enabled)

    fun getQuietHoursStart(): String = getString(KEY_PUSH_QUIET_HOURS_START, "22:00")
    fun setQuietHoursStart(time: String) = setString(KEY_PUSH_QUIET_HOURS_START, time)

    fun getQuietHoursEnd(): String = getString(KEY_PUSH_QUIET_HOURS_END, "07:00")
    fun setQuietHoursEnd(time: String) = setString(KEY_PUSH_QUIET_HOURS_END, time)

    /**
     * True when the wall-clock time is inside the user's quiet-hours window.
     * Mirrors the web semantics loosely: the range wraps past midnight when end < start
     * (22:00–07:00), inclusive start, exclusive end.
     */
    fun isQuietHoursActive(now: kotlinx.datetime.LocalDateTime): Boolean {
        if (!isQuietHoursEnabled()) return false
        val start = parseTimeMinutes(getQuietHoursStart()) ?: return false
        val end = parseTimeMinutes(getQuietHoursEnd()) ?: return false
        val current = now.hour * 60 + now.minute
        return if (start <= end) current in start until end
        else (current >= start) || (current < end)
    }

    private fun parseTimeMinutes(time: String): Int? {
        val parts = time.trim().split(":").mapNotNull(String::toIntOrNull)
        if (parts.size < 2) return null
        return parts[0] * 60 + parts[1]
    }

    // Push is local-only by design (AlarmManager); no VAPID/FCM key is stored.

    // ── API target (web parity — the Primary/Backup selector in fetch-utils.ts) ──
    const val KEY_API_TARGET = "api_target"

    fun getApiTargetName(): String = getString(KEY_API_TARGET, "PRIMARY")
    fun setApiTargetName(name: String) = setString(KEY_API_TARGET, name)

    // ── Home screen ──

    /** [HomePillStyle] names: how dense a session card is on the simplified home. */
    const val KEY_HOME_PILL_STYLE = "home_pill_style"

    /**
     * When true the day's tasks appear as their own section under the timetable.
     * When false they are left out of the home entirely, for anyone who would
     * rather see them on the Tasks screen.
     */
    const val KEY_HOME_TASKS_INLINE = "home_tasks_inline"

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

    /**
     * Purges all application data caches (academic, profiles, transport, qbank stats, od tracker)
     * without erasing user authentication credentials or theme preferences.
     */
    fun clearLocalCache() {
        com.amazecc.app.shared.state.AppDataStore.clear()
        com.amazecc.app.shared.state.UserStore.clear()
        val keysToRemove = settings.keys.filter { it.startsWith("cache_") }
        keysToRemove.forEach { remove(it) }
        remove(KEY_SWOT_CONFIG)
        remove(KEY_QBANK_STATS)
        remove(PAST_SEMESTER_SYNCED)
    }

    fun getEstimatedCacheSizeBytes(): Long {
        var total = 0L
        for (k in settings.keys) {
            if (k.startsWith("cache_") || k == KEY_SWOT_CONFIG || k == KEY_QBANK_STATS) {
                val s = settings.getStringOrNull(k)
                if (s != null) total += s.length * 2L
            }
        }
        return total
    }

    fun getFormattedCacheSize(): String {
        val bytes = getEstimatedCacheSizeBytes()
        return when {
            bytes >= 1024 * 1024 -> "${((bytes * 10) / (1024 * 1024)) / 10.0} MB"
            bytes >= 1024 -> "${((bytes * 10) / 1024) / 10.0} KB"
            else -> "$bytes B"
        }
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

    // ── VTOP data source ────────────────────────────────────────────────────

    fun getVtopSourceName(): String = getString(KEY_VTOP_SOURCE, "LOCAL")

    fun setVtopSourceName(name: String) = setString(KEY_VTOP_SOURCE, name)

    /** False until the user has explicitly confirmed the migration prompt. */
    fun isVtopSourcePrompted(): Boolean = getBoolean("vtop_source_prompted", false)

    fun setVtopSourcePrompted(value: Boolean) = setBoolean("vtop_source_prompted", value)

    /** True when the user has opted into always typing the captcha themselves. */
    fun isVtopManualCaptcha(): Boolean = getBoolean(KEY_VTOP_MANUAL_CAPTCHA, false)

    fun setVtopManualCaptcha(value: Boolean) = setBoolean(KEY_VTOP_MANUAL_CAPTCHA, value)

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

    // ─────────────────────────────────────────────────────────────────────────────
    // D-122 — Global settings parity with ../AmazeCC.
    //
    // The web app persists one JSON blob under localStorage["settings"]
    // (src/store/settingsAtoms.ts). The blob's getItem merge over `defaultSettings`
    // IS its migration shim: a field added in a later release is picked up from the
    // default on the next read. [WebSettings] is the Kotlin mirror of that blob —
    // every field, every web default, verbatim. Individual Kotlin features keep
    // reading their own flat keys (AppState, SyncEngine, …); this blob is the
    // single source of truth for the *web-shaped* preferences and is what the
    // settings UI patches so a backup/restore round-trips the same configuration.
    // ─────────────────────────────────────────────────────────────────────────────

    /** Mirrors localStorage["settings"] in the web app. */
    const val KEY_WEB_SETTINGS = "settings"

    @kotlinx.serialization.Serializable
    data class WebCustomPalette(
        val accent: String = "#0ea5e9",
        val background: String = "#f8fafc",
        val surface: String = "#ffffff",
    )

    /** Exact Kotlin mirror of `Settings` from `../AmazeCC/src/store/settingsAtoms.ts`. */
    @kotlinx.serialization.Serializable
    data class WebSettings(
        val decimalValues: Boolean = false,
        val CGPAHidden: Boolean = false,
        /** "percentage" | "str" */
        val attendancePercentageOrString: String = "percentage",
        /** Stamped by the host from the selected semester; web derives it from config.json. */
        val currSemesterID: String = "",
        /** ALL | ALL02 | ALL03 | ALL05 | ALL06 | ALL08 | ALL11 | WEI */
        val calendarType: String = "ALL",
        val isDayscholarWithBus: Boolean = false,
        val targetAttendance: Int = 75,
        val showGpa: Boolean = false,
        val showProfilePhoto: Boolean = true,
        val blurGrades: Boolean = false,
        val privacyMode: Boolean = false,
        val colorPalette: String = "default",
        val customPalette: WebCustomPalette = WebCustomPalette(),
        val cardSurfaceZinc: Boolean = false,
        val hideHomeSearchBar: Boolean = false,
        val reloadAllData: Boolean = false,
        val isSidebarCollapsed: Boolean = false,
        /** "hosteller" | "dayscholar" */
        val residentialStatus: String = "hosteller",
        val friendlyName: String = "",
        val syncProfileData: Boolean = true,
        val syncAdditionalData: Boolean = true,
        val syncExcRegistration: Boolean = true,
        val syncMinorHonour: Boolean = true,
        val syncCourseCompletion: Boolean = true,
        val defaultSyncMode: String = "quarterly",
        val pinnedNavTabs: List<String> = listOf("academics", "attendance"),
        val defaultAcademicsTab: String = "overview",
        /** "off" | "15m" | "30m" | "1h" */
        val autoSyncInterval: String = "off",
        val lowDataMode: Boolean = false,
        val soundEnabled: Boolean = true,
        val smartMessFilter: Boolean = false,
        /** "compact" | "detailed" */
        val timetablePillStyle: String = "compact",
        /** "auto" | "vertical" | "horizontal" | "classic" */
        val timetableViewMode: String = "auto",
        /** "full" | "compact" */
        val timetableCellDensity: String = "full",
        /** Null = pristine; stamped on the first density pick. */
        val timetableDensityTouched: Boolean? = null,
        val interfaceChosen: Boolean? = null,

        // Push notification preferences (web posts these to notifications/subscribe).
        val pushNotificationsEnabled: Boolean = true,
        val notifyLowAttendance: Boolean = true,
        val notifyClassReminders: Boolean = true,
        val notifyExamAlerts: Boolean = true,
        val notifyMessServing: Boolean = true,
        val notifyAssignments: Boolean = true,
        val notifyCirculars: Boolean = true,
        val notifyHostelLeave: Boolean = true,
        val notifyBusUpdates: Boolean = true,
        val notifyLibraryBooks: Boolean = true,
        val notifyMarksRelease: Boolean = true,
        val classReminderLeadMinutes: Int = 15,
        val pushSoundEnabled: Boolean = true,
        val pushVibrationEnabled: Boolean = true,
        val pushQuietHoursEnabled: Boolean = false,
        val pushQuietHoursStart: String = "22:00",
        val pushQuietHoursEnd: String = "07:00",
        val customVapidKey: String = "",

        // Tasks & Pomodoro preferences.
        val tasksInlineOnHome: Boolean = true,
        val taskPomodoroFocus: Int = 25,
        val taskPomodoroBreak: Int = 5,
        val taskPomodoroRounds: Int = 4,
        val taskDefaultReminderMinutes: Int = 30,
    ) {
        companion object {
            /** The web `defaultSettings` object, as a value. */
            val DEFAULT = WebSettings()
        }
    }

    private val webSettingsJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
    }

    /** Web `normalizeDefaultSyncMode` + the legacy `manual` → `off` picker shim. */
    private fun normalizeWebSettings(value: WebSettings): WebSettings {
        val mode = when (value.defaultSyncMode) {
            "quick" -> "daily"
            "balanced" -> "monthly"
            "academics" -> "weekly"
            "full", "campus" -> "quarterly"
            else -> value.defaultSyncMode.ifBlank { "quarterly" }
        }
        val interval = if (value.autoSyncInterval == "manual") "off" else value.autoSyncInterval
        return value.copy(defaultSyncMode = mode, autoSyncInterval = interval)
    }

    /**
     * Reads the web settings blob. A missing, corrupt or partially-populated blob
     * degrades to [WebSettings.DEFAULT] — the Kotlin equivalent of the web
     * `{ ...defaultSettings, ...raw }` merge performed on every `getItem`.
     */
    fun getWebSettings(): WebSettings {
        val raw = getString(KEY_WEB_SETTINGS, "")
        if (raw.isBlank()) return WebSettings.DEFAULT
        return try {
            normalizeWebSettings(webSettingsJson.decodeFromString(raw))
        } catch (_: Exception) {
            WebSettings.DEFAULT
        }
    }

    /** Writes the whole blob, mirroring `localStorage.setItem("settings", JSON.stringify(value))`. */
    fun setWebSettings(value: WebSettings) =
        setString(KEY_WEB_SETTINGS, webSettingsJson.encodeToString(value))

    /** Applies a patch to the persisted blob in one read-modify-write. */
    fun updateWebSetting(block: (WebSettings) -> WebSettings) {
        setWebSettings(block(getWebSettings()))
    }

    /** Preferred name. The web keeps it in the blob; Kotlin's profile host also
     * persists this dedicated key, so both now share one constant. */
    const val KEY_FRIENDLY_NAME = "friendly_name"

    fun getFriendlyName(): String = getString(KEY_FRIENDLY_NAME, "")

    fun setFriendlyName(name: String) = setString(KEY_FRIENDLY_NAME, name)
}
