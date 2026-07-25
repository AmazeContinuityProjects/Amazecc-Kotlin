package com.amazecc.app.shared.repository

import com.russhwolf.settings.Settings

/**
 * SettingsManager mirrors the localStorage caching layer found in the AmazeCC web application.
 * It provides synchronous read/write access to cached API responses and user preferences.
 */
object SettingsManager {
    private val settings: Settings by lazy { Settings() }

    // Key constants mirroring web app
    const val KEY_GPA_GOAL = "uni_cc_gpa_goal"
    const val KEY_APP_ICON = "app-icon"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_CGPA_HIDDEN = "cgpa_hidden"
    const val KEY_ATTENDANCE_MODE = "attendance_display_mode"
    const val KEY_SYNC_EXAM = "sync_exam"
    const val KEY_SYNC_PROFILE = "sync_profile"
    const val KEY_SYNC_ADDITIONAL = "sync_additional"
    const val KEY_NAVBAR_ITEMS = "navbar_items"
    const val KEY_PREFERRED_CALENDAR = "preferred_calendar_name"

    // Theme & Display preferences
    const val KEY_APP_THEME = "app_theme"
    const val KEY_APP_ACCENT = "app_accent"
    const val KEY_UI_SCALE = "app_ui_scale"
    const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    const val KEY_ANIMATIONS_ENABLED = "animations_enabled"

    // Update checker
    const val KEY_UPDATE_DISMISSED_VERSION = "update_dismissed_version"

    // Session cache (VTOP credentials)
    const val SESSION_COOKIES = "session_cookies"
    const val SESSION_CSRF = "session_csrf"
    const val SESSION_AUTHORIZED_ID = "session_authorized_id"
    const val SESSION_CLUB_TOKEN = "session_club_token"

    // Library credentials (separate from VTOP)
    const val KEY_LIBRARY_USERNAME = "library_username"
    const val KEY_LIBRARY_PASSWORD = "library_password"

    // Cache keys for all data types
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
    const val CACHE_TRANSPORT = "cache_transport"
    const val CACHE_TRANSPORT_ROUTES = "cache_transport_routes"
    const val CACHE_TRANSPORT_PASS = "cache_transport_pass"
    const val CACHE_LMS = "cache_lms"
    const val CACHE_EVENTS = "cache_events"
    const val CACHE_CLUBS = "cache_clubs"
    const val CACHE_STUDENT_PROFILE = "cache_student_profile"
    const val KEY_MOODLE_USERNAME = "moodle_username"
    const val KEY_MOODLE_PASSWORD = "moodle_password"
    const val CACHE_VITOL = "cache_vitol"
    const val CACHE_CAB_TRIPS = "cache_cab_trips"
    const val CACHE_ALL_SEMESTER_ATTENDANCE = "cache_all_semester_attendance"
    const val CACHE_ALL_SEMESTER_MARKS = "cache_all_semester_marks"
    const val CACHE_CALENDARS_LIST = "cache_calendars_list"
    const val CACHE_QCM_VIEW = "cache_qcm_view"
    const val CACHE_TASKS = "cache_tasks"
    const val RESIDENTIAL_STATUS = "residential_status"

    // Notification preferences
    const val NOTIF_CLASS_REMINDERS = "notif_class_reminders"
    const val NOTIF_ASSIGNMENT_REMINDERS = "notif_assignment_reminders"
    const val NOTIF_VITOL_REMINDERS = "notif_vitol_reminders"
    const val NOTIF_TASK_REMINDERS = "notif_task_reminders"
    const val NOTIF_OFFSET_MINUTES = "notif_offset_minutes"
    
    // Onboarding
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    
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

    fun remove(key: String) {
        settings.remove(key)
    }

    fun clearAll() {
        settings.clear()
    }
    
    // Web app specific getters/setters for Profile/Settings parity
    fun saveCredentials(username: String, pass: String) {
        setString(KEY_USERNAME, username)
        setString(KEY_PASSWORD, pass)
    }
    
    fun getCredentials(): Pair<String, String>? {
        val u = getNullableString(KEY_USERNAME)
        val p = getNullableString(KEY_PASSWORD)
        return if (u != null && p != null) Pair(u, p) else null
    }

    fun saveLibraryCredentials(username: String, password: String) {
        setString(KEY_LIBRARY_USERNAME, username)
        setString(KEY_LIBRARY_PASSWORD, password)
    }

    fun getLibraryCredentials(): Pair<String, String>? {
        val u = getNullableString(KEY_LIBRARY_USERNAME)
        val p = getNullableString(KEY_LIBRARY_PASSWORD)
        return if (u != null && p != null) Pair(u, p) else null
    }

    fun clearLibraryCredentials() {
        remove(KEY_LIBRARY_USERNAME)
        remove(KEY_LIBRARY_PASSWORD)
    }

    fun saveMoodleCredentials(username: String, password: String) {
        setString(KEY_MOODLE_USERNAME, username)
        setString(KEY_MOODLE_PASSWORD, password)
    }

    fun getMoodleCredentials(): Pair<String, String>? {
        val u = getNullableString(KEY_MOODLE_USERNAME)
        val p = getNullableString(KEY_MOODLE_PASSWORD)
        return if (u != null && p != null) Pair(u, p) else null
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

    // ── Notification preferences ──
    fun isNotifClassRemindersEnabled(): Boolean = getBoolean(NOTIF_CLASS_REMINDERS, false)
    fun setNotifClassRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_CLASS_REMINDERS, enabled)

    fun isNotifAssignmentRemindersEnabled(): Boolean = getBoolean(NOTIF_ASSIGNMENT_REMINDERS, false)
    fun setNotifAssignmentRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_ASSIGNMENT_REMINDERS, enabled)

    fun isNotifVitolRemindersEnabled(): Boolean = getBoolean(NOTIF_VITOL_REMINDERS, false)
    fun setNotifVitolRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_VITOL_REMINDERS, enabled)

    fun isNotifTaskRemindersEnabled(): Boolean = getBoolean(NOTIF_TASK_REMINDERS, false)
    fun setNotifTaskRemindersEnabled(enabled: Boolean) = setBoolean(NOTIF_TASK_REMINDERS, enabled)

    fun getNotifOffsetMinutes(): Int = getString(NOTIF_OFFSET_MINUTES, "15").toIntOrNull() ?: 15

    fun setNotifOffsetMinutes(minutes: Int) = setString(NOTIF_OFFSET_MINUTES, minutes.toString())

    // Onboarding
    fun isOnboardingComplete(): Boolean = getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(complete: Boolean) = setBoolean(KEY_ONBOARDING_COMPLETE, complete)
}
