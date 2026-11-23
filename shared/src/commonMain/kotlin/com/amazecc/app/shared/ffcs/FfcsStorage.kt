package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.CourseLock as PlannerCourseLock
import com.amazecc.app.shared.ffcs.planner.Friend as PlannerFriend
import com.amazecc.app.shared.ffcs.planner.ParsedCourse as PlannerParsedCourse
import com.amazecc.app.shared.ffcs.planner.TimetableState as PlannerTimetableState
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * D-178 — typed FFCS persistence, layered **above** [SettingsManager].
 *
 * Nothing here edits SettingsManager: it is used only as the durable backing
 * store via [SettingsManagerFfcsStore]. All keys live under the literal `ffcs_`
 * namespace, normal keys are JSON-encoded, and the three [RAW_STRING_KEYS] are
 * stored verbatim. Every operation swallows parse/quota/storage errors and
 * degrades to a default, exactly like the web's `storage.ts` primitives.
 *
 * A second, in-memory [session] store mirrors `sessionStorage`: generator
 * preferences live there so a new tab starts clean, while targets and the rest
 * live in the persistent store. [InMemoryFfcsKeyValueStore] is the deterministic
 * test double; production uses it for the session store too.
 */

/** Minimal string key/value seam over the durable settings backend. */
interface FfcsKeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
    fun clear()
    fun keys(): Set<String>
}

/** Production backend: delegates to the existing [SettingsManager], unmodified. */
object SettingsManagerFfcsStore : FfcsKeyValueStore {
    override fun get(key: String): String? =
        if (key in SettingsManager.allKeys()) SettingsManager.getString(key, "") else null

    override fun set(key: String, value: String) = SettingsManager.setString(key, value)
    override fun remove(key: String) = SettingsManager.remove(key)

    /** Only clear our own namespace — never credentials or global app settings. */
    override fun clear() {
        SettingsManager.allKeys().filter { it.startsWith("ffcs_") }.forEach(::remove)
    }

    override fun keys(): Set<String> = SettingsManager.allKeys().filter { it.startsWith("ffcs_") }.toSet()
}

/** Deterministic in-memory backend for JVM tests and for session-scoped state. */
class InMemoryFfcsKeyValueStore : FfcsKeyValueStore {
    private val data = LinkedHashMap<String, String>()

    override fun get(key: String): String? = data[key]
    override fun set(key: String, value: String) { data[key] = value }
    override fun remove(key: String) { data.remove(key) }
    override fun clear() { data.clear() }
    override fun keys(): Set<String> = data.keys.toSet()

    /** Seed a raw value directly — used by tests to inject corrupt JSON. */
    fun seed(key: String, value: String) { data[key] = value }
}

/** Complete `ffcs_*` key inventory from reference/07 §3. */
object FfcsStorageKeys {
    const val TIMETABLES = "ffcs_timetables"
    const val ACTIVE_TIMETABLE_ID = "ffcs_activeTimetableId"
    const val BLOCKED_SLOTS = "ffcs_blockedSlots"
    const val FRIENDS = "ffcs_friends"
    const val FRIEND_GROUPS = "ffcs_friendGroups"
    const val SOCIAL_SCORE_METHOD = "ffcs_socialScoreGroupMethod"
    const val COURSE_LOCKS = "ffcs_courseLocks"
    /** camelCase P on purpose — the docs' `ffcs_faculty_priority` is stale. */
    const val FACULTY_PRIORITY = "ffcs_facultyPriority"
    const val MANUAL_LINKS = "ffcs_manual_links"
    const val RAW_COURSES = "ffcs_raw_courses"
    const val SETTINGS = "ffcs_settings"
    const val TOUR_SEEN = "ffcs_tour_seen"
    const val TIPS_DISMISSED = "ffcs_tips_dismissed"
    const val LAYOUT_MODE = "ffcs_layout_mode"
    /** JSON-encoded `"true"`, NOT a raw string despite the value looking raw. */
    const val LAYOUT_MODE_PROMPT_SEEN = "ffcs_layout_mode_prompt_seen"
    const val GENERATOR_TARGETS = "ffcs_generator_targets"
    /** Lives in the SESSION store, not the persistent one. */
    const val GENERATOR_SESSION_PREFS = "ffcs_generator_session_prefs"
    const val GENERATOR_VIEW_MODE = "ffcs_generator_view_mode"
    const val FINAL_LIST_NOTICE_SEEN = "ffcs_final_list_notice_seen"
    const val DISMISS_FACULTY_GUIDE = "ffcs_dismiss_faculty_guide"
    const val DASHBOARD_WIDGETS = "ffcs_dashboard_widgets"
    const val SEMESTER = "ffcs_semester"
    const val ANALYTICS_ENABLED = "ffcs_analytics_enabled"
    const val ANALYTICS_QUEUE = "ffcs_analytics_queue"
    const val VITFAC_ENABLED = "ffcs_vitfac_enabled"

    /** Per-semester cache variant: `ffcs_raw_courses_${semesterId}`. */
    fun rawCoursesFor(semesterId: String): String = "${RAW_COURSES}_$semesterId"

    val ALL: Set<String> = setOf(
        TIMETABLES, ACTIVE_TIMETABLE_ID, BLOCKED_SLOTS, FRIENDS, FRIEND_GROUPS,
        SOCIAL_SCORE_METHOD, COURSE_LOCKS, FACULTY_PRIORITY, MANUAL_LINKS,
        RAW_COURSES, SETTINGS, TOUR_SEEN, TIPS_DISMISSED, LAYOUT_MODE,
        LAYOUT_MODE_PROMPT_SEEN, GENERATOR_TARGETS, GENERATOR_SESSION_PREFS,
        GENERATOR_VIEW_MODE, FINAL_LIST_NOTICE_SEEN, DISMISS_FACULTY_GUIDE,
        DASHBOARD_WIDGETS, SEMESTER, ANALYTICS_ENABLED, ANALYTICS_QUEUE, VITFAC_ENABLED,
    )
}

/** Serializable mirror of the web `FriendGroup` shape (`state.FriendGroup` is not serializable). */
@Serializable
data class FfcsFriendGroup(
    val id: String,
    val name: String,
    val friendIds: List<String> = emptyList(),
)

/** Serializable mirror of `FfcsCourseProcessor.ManualLink`. */
@Serializable
data class FfcsManualLink(
    val CODE: String,
    val TYPE: String,
    val SLOT: String,
    val FACULTY: String,
    val LINK_ID: String,
)

/** One queued analytics event; the queue is capped at 100 (oldest dropped). */
@Serializable
data class FfcsAnalyticsEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val timestamp: Long = 0L,
)

class FfcsStorage(
    private val persistent: FfcsKeyValueStore,
    private val session: FfcsKeyValueStore,
) {
    companion object {
        /**
         * Keys the typed facade writes verbatim. Other "raw-in-practice" keys
         * (tour seen, semester, analytics flags, …) are written by their web
         * callers through `safeStorage.setItem`; use [saveRawString] for them.
         */
        val RAW_STRING_KEYS: Set<String> = setOf(
            FfcsStorageKeys.ACTIVE_TIMETABLE_ID,
            FfcsStorageKeys.DISMISS_FACULTY_GUIDE,
            FfcsStorageKeys.LAYOUT_MODE,
        )

        val Default: FfcsStorage by lazy {
            FfcsStorage(SettingsManagerFfcsStore, InMemoryFfcsKeyValueStore())
        }
    }

    // ── error-swallowing primitives ─────────────────────────────────────────

    fun rawGet(key: String): String? = try { persistent.get(key) } catch (_: Exception) { null }

    fun rawSet(key: String, value: String) {
        try { persistent.set(key, value) } catch (_: Exception) { /* quota / blocked storage */ }
    }

    fun sessionRawGet(key: String): String? = try { session.get(key) } catch (_: Exception) { null }

    fun sessionRawSet(key: String, value: String) {
        try { session.set(key, value) } catch (_: Exception) { /* quota / blocked storage */ }
    }

    fun remove(key: String) {
        try { persistent.remove(key) } catch (_: Exception) { /* swallow */ }
    }

    fun removeSession(key: String) {
        try { session.remove(key) } catch (_: Exception) { /* swallow */ }
    }

    fun clearPersistent() {
        try { persistent.clear() } catch (_: Exception) { /* swallow */ }
    }

    fun clearSession() {
        try { session.clear() } catch (_: Exception) { /* swallow */ }
    }

    fun has(key: String): Boolean = rawGet(key) != null

    /** JSON for normal keys, raw for [RAW_STRING_KEYS]. Junk -> [fallback]. */
    inline fun <reified T> load(key: String, fallback: T? = null): T? {
        val raw = rawGet(key) ?: return fallback
        if (key in RAW_STRING_KEYS) return raw as? T ?: fallback
        return try { FfcsJson.decodeFromString<T>(raw) } catch (_: Exception) { fallback }
    }

    /** JSON for normal keys, raw for [RAW_STRING_KEYS]. Quota errors swallowed. */
    inline fun <reified T> store(key: String, value: T) {
        try {
            val encoded = if (key in RAW_STRING_KEYS && value is String) {
                value
            } else {
                FfcsJson.encodeToString(value)
            }
            rawSet(key, encoded)
        } catch (_: Exception) { /* quota / serialization */ }
    }

    inline fun <reified T> loadSession(key: String, fallback: T? = null): T? {
        val raw = sessionRawGet(key) ?: return fallback
        return try { FfcsJson.decodeFromString<T>(raw) } catch (_: Exception) { fallback }
    }

    inline fun <reified T> storeSession(key: String, value: T) {
        try { sessionRawSet(key, FfcsJson.encodeToString(value)) } catch (_: Exception) { /* quota */ }
    }

    /** Direct raw access for the keys the web writes via `safeStorage.setItem`. */
    fun loadRawString(key: String): String? = rawGet(key)
    fun saveRawString(key: String, value: String) = rawSet(key, value)

    // ── settings & faculty priority (sanitized on every read AND write) ─────

    fun loadSettings(): FFCSSettings {
        val raw = rawGet(FfcsStorageKeys.SETTINGS) ?: return FFCSSettingsDefaults.snapshot()
        if (raw.isBlank()) return FFCSSettingsDefaults.snapshot()
        return try {
            sanitizeFFCSSettings(FfcsSettingsJson.parseToJsonElement(raw))
        } catch (_: Exception) {
            FFCSSettingsDefaults.snapshot()
        }
    }

    fun saveSettings(value: FFCSSettings) {
        try {
            val encoded = FfcsSettingsJson.encodeToString(value)
            val clean = sanitizeFFCSSettings(FfcsSettingsJson.parseToJsonElement(encoded))
            rawSet(FfcsStorageKeys.SETTINGS, FfcsSettingsJson.encodeToString(clean))
        } catch (_: Exception) { /* quota */ }
    }

    fun loadFacultyPriority(): FacultyPriorityMap {
        val raw = rawGet(FfcsStorageKeys.FACULTY_PRIORITY) ?: return emptyMap()
        return try {
            sanitizeFacultyPriority(ffcsLoose(FfcsJson.parseToJsonElement(raw)))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveFacultyPriority(map: FacultyPriorityMap) = store(FfcsStorageKeys.FACULTY_PRIORITY, map)

    // ── typed accessors for the remaining inventory ─────────────────────────

    fun loadTimetables(): List<PlannerTimetableState> =
        load<List<PlannerTimetableState>>(FfcsStorageKeys.TIMETABLES, emptyList()) ?: emptyList()

    fun saveTimetables(value: List<PlannerTimetableState>) = store(FfcsStorageKeys.TIMETABLES, value)

    fun loadActiveTimetableId(): String? = load(FfcsStorageKeys.ACTIVE_TIMETABLE_ID)

    fun saveActiveTimetableId(id: String?) {
        if (id == null) remove(FfcsStorageKeys.ACTIVE_TIMETABLE_ID)
        else store(FfcsStorageKeys.ACTIVE_TIMETABLE_ID, id)
    }

    fun loadBlockedSlots(): List<String> =
        load<List<String>>(FfcsStorageKeys.BLOCKED_SLOTS, emptyList()) ?: emptyList()

    fun saveBlockedSlots(value: List<String>) = store(FfcsStorageKeys.BLOCKED_SLOTS, value)

    fun loadFriends(): List<PlannerFriend> =
        load<List<PlannerFriend>>(FfcsStorageKeys.FRIENDS, emptyList()) ?: emptyList()

    fun saveFriends(value: List<PlannerFriend>) = store(FfcsStorageKeys.FRIENDS, value)

    fun loadFriendGroups(): List<FfcsFriendGroup> =
        load<List<FfcsFriendGroup>>(FfcsStorageKeys.FRIEND_GROUPS, emptyList()) ?: emptyList()

    fun saveFriendGroups(value: List<FfcsFriendGroup>) = store(FfcsStorageKeys.FRIEND_GROUPS, value)

    fun loadCourseLocks(): List<PlannerCourseLock> =
        load<List<PlannerCourseLock>>(FfcsStorageKeys.COURSE_LOCKS, emptyList()) ?: emptyList()

    fun saveCourseLocks(value: List<PlannerCourseLock>) = store(FfcsStorageKeys.COURSE_LOCKS, value)

    fun loadManualLinks(): List<FfcsManualLink> =
        load<List<FfcsManualLink>>(FfcsStorageKeys.MANUAL_LINKS, emptyList()) ?: emptyList()

    fun saveManualLinks(value: List<FfcsManualLink>) = store(FfcsStorageKeys.MANUAL_LINKS, value)

    fun loadRawCourses(): List<PlannerParsedCourse> =
        load<List<PlannerParsedCourse>>(FfcsStorageKeys.RAW_COURSES, emptyList()) ?: emptyList()

    fun saveRawCourses(value: List<PlannerParsedCourse>) = store(FfcsStorageKeys.RAW_COURSES, value)

    fun loadRawCourses(semesterId: String): List<PlannerParsedCourse> =
        load<List<PlannerParsedCourse>>(FfcsStorageKeys.rawCoursesFor(semesterId), emptyList()) ?: emptyList()

    fun saveRawCourses(semesterId: String, value: List<PlannerParsedCourse>) =
        store(FfcsStorageKeys.rawCoursesFor(semesterId), value)

    fun loadTipsDismissed(): List<String> =
        load<List<String>>(FfcsStorageKeys.TIPS_DISMISSED, emptyList()) ?: emptyList()

    fun saveTipsDismissed(value: List<String>) = store(FfcsStorageKeys.TIPS_DISMISSED, value)

    fun loadDashboardWidgets(): List<String> =
        load<List<String>>(FfcsStorageKeys.DASHBOARD_WIDGETS, emptyList()) ?: emptyList()

    fun saveDashboardWidgets(value: List<String>) = store(FfcsStorageKeys.DASHBOARD_WIDGETS, value)

    fun loadAnalyticsQueue(): List<FfcsAnalyticsEvent> =
        load<List<FfcsAnalyticsEvent>>(FfcsStorageKeys.ANALYTICS_QUEUE, emptyList()) ?: emptyList()

    /** Capped at 100; the oldest events are dropped first. */
    fun saveAnalyticsQueue(value: List<FfcsAnalyticsEvent>) =
        store(FfcsStorageKeys.ANALYTICS_QUEUE, value.takeLast(100))

    fun loadLayoutMode(): String? = load(FfcsStorageKeys.LAYOUT_MODE)
    fun saveLayoutMode(value: String) = store(FfcsStorageKeys.LAYOUT_MODE, value)

    /** JSON `"true"` — deliberately NOT a raw key. */
    fun loadLayoutModePromptSeen(): String? = load(FfcsStorageKeys.LAYOUT_MODE_PROMPT_SEEN)
    fun saveLayoutModePromptSeen(value: String) = store(FfcsStorageKeys.LAYOUT_MODE_PROMPT_SEEN, value)

    fun isAnalyticsEnabled(): Boolean = loadRawString(FfcsStorageKeys.ANALYTICS_ENABLED) != "false"
    fun setAnalyticsEnabled(enabled: Boolean) =
        saveRawString(FfcsStorageKeys.ANALYTICS_ENABLED, enabled.toString())

    fun isVitfacEnabled(): Boolean = loadRawString(FfcsStorageKeys.VITFAC_ENABLED) != "false"
    fun setVitfacEnabled(enabled: Boolean) =
        saveRawString(FfcsStorageKeys.VITFAC_ENABLED, enabled.toString())
}
