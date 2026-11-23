package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.CourseLock
import com.amazecc.app.shared.ffcs.planner.DayId
import com.amazecc.app.shared.ffcs.planner.FacultyMetricWeights
import com.amazecc.app.shared.ffcs.planner.FacultyScoreFilters
import com.amazecc.app.shared.ffcs.planner.Preference
import com.amazecc.app.shared.ffcs.planner.RankingFactor
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import com.amazecc.app.shared.ffcs.planner.SortBy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * D-178 — per-run generator preferences (reference/07 §4).
 *
 * These live in the SESSION store (`ffcs_generator_session_prefs` in
 * `sessionStorage`), never in the durable store: a new tab starts clean. All
 * fields are optional so a sparse write never fabricates a user choice, and a
 * corrupt blob degrades to an empty record rather than throwing.
 */

/** The four Vitfac metrics a student can reorder / disable, in canonical order. */
@Serializable
enum class FacultyMetricKey(val value: String) {
    @SerialName("wPct") WPCT("wPct"),
    @SerialName("lecture") LECTURE("lecture"),
    @SerialName("da") DA("da"),
    @SerialName("vibe") VIBE("vibe");

    companion object {
        val CANONICAL_ORDER: List<FacultyMetricKey> = entries.toList()

        fun fromString(value: String?): FacultyMetricKey? =
            entries.firstOrNull { it.value.equals(value?.trim(), ignoreCase = true) }
    }
}

@Serializable
data class GeneratorSessionPreferences(
    val preference: Preference? = null,
    val strictTheoryPreference: Boolean? = null,
    val separateLabDays: Boolean? = null,
    val avoidConsecutiveLabs: Boolean? = null,
    val lunchBreakSeparatesLabs: Boolean? = null,
    val uniqueFaculties: Boolean? = null,
    val noLimit: Boolean? = null,
    val resultCap: Int? = null,
    val rankingMode: DefaultRankingMode? = null,
    val rankingPriorityOrder: List<RankingPriority>? = null,
    /** Ranked factor list: priority order + a per-factor off switch. */
    val rankingFactorOrder: List<RankingFactor>? = null,
    val rankingFactorDisabled: List<RankingFactor>? = null,
    /** Faculty sub-list (metric priorities), same recipe as the factor list. */
    val facultyMetricOrder: List<FacultyMetricKey>? = null,
    val facultyMetricDisabled: List<FacultyMetricKey>? = null,
    /** Exclude the extramural-hours slots from every run. */
    val blockExtramuralSlots: Boolean? = null,
    val facultyMetricWeights: FacultyMetricWeights? = null,
    val facultyScoreFilters: FacultyScoreFilters? = null,
    val minHalfDays: Int? = null,
    val minStartTime: String? = null,
    val maxEndTime: String? = null,
    val maxEndTimeByDay: Map<DayId, String>? = null,
    val sortBy: SortBy? = null,
    val syncFriendsClasses: Boolean? = null,
    /** Backwards-compatible alias for [syncFriendsClasses]. */
    val syncFriendClasses: Boolean? = null,
    val maximizeFreeTimeFriends: List<String>? = null,
    val targets: List<CourseLock>? = null,
    val isReviewing: Boolean? = null,
) {
    /** The web writes both spellings; the canonical one wins when present. */
    val effectiveSyncFriendsClasses: Boolean?
        get() = syncFriendsClasses ?: syncFriendClasses
}

object FfcsGeneratorSessionPrefs {
    /** Reads ONLY the session store — never any durable SettingsManager key. */
    fun load(storage: FfcsStorage = FfcsStorage.Default): GeneratorSessionPreferences {
        val raw = storage.sessionRawGet(FfcsStorageKeys.GENERATOR_SESSION_PREFS)
        if (raw.isNullOrBlank()) return GeneratorSessionPreferences()
        return try {
            FfcsJson.decodeFromString<GeneratorSessionPreferences>(raw)
        } catch (_: Exception) {
            GeneratorSessionPreferences()
        }
    }

    /**
     * Writes ONLY the session store. A stale durable copy left by an older build
     * is purged first so it can never be restored or mistaken for the live record.
     */
    fun save(prefs: GeneratorSessionPreferences, storage: FfcsStorage = FfcsStorage.Default) {
        storage.remove(FfcsStorageKeys.GENERATOR_SESSION_PREFS)
        try {
            storage.sessionRawSet(FfcsStorageKeys.GENERATOR_SESSION_PREFS, FfcsJson.encodeToString(prefs))
        } catch (_: Exception) { /* quota / blocked session storage */ }
    }

    fun clear(storage: FfcsStorage = FfcsStorage.Default) {
        storage.remove(FfcsStorageKeys.GENERATOR_SESSION_PREFS)
        storage.removeSession(FfcsStorageKeys.GENERATOR_SESSION_PREFS)
    }
}
