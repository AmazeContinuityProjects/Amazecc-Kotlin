package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.FacultyMetricWeights
import com.amazecc.app.shared.ffcs.planner.FacultyScoreFilters
import com.amazecc.app.shared.ffcs.planner.RankingMode
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import com.amazecc.app.shared.ffcs.planner.SortBy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * D-178 — the FFCS settings record, its defaults and its defensive sanitizer.
 *
 * Ported from `ffcs-planner` reference/07 §1 and the shipped
 * `src/lib/ffcsSettings.ts`. The single most important rule is the one the web
 * source states in its header: **settings are data, and data gets edited by
 * hands and by older app versions.** Every field therefore falls back on its
 * own; a typo in `resultCap` must not throw away the student's sort choice.
 *
 * The record carries a [revision] stamp for exactly one reason: the one-shot
 * migration of the old `resultCap: 50` default to 30,000. A `50` on a
 * pre-revision record rides along with the old default and is upgraded; the
 * same `50` on a stamped record was a deliberate pick and is preserved.
 */

/** Schema stamp written by the sanitizer on every save. */
const val FFCSS_SETTINGS_REVISION: Int = 2
private const val PRE_REVISION: Int = 1
private const val LEGACY_DEFAULT_RESULT_CAP: Int = 50

/** Lower/upper bounds of a user-picked result cap (web `MIN_RESULT_CAP` / `HARD_RESULT_LIMIT`). */
const val FFCSS_MIN_RESULT_CAP: Int = 10
const val FFCSS_MAX_RESULT_CAP: Int = 30_000
const val FFCSS_HARD_RESULT_LIMIT: Int = FFCSS_MAX_RESULT_CAP
private const val MAX_HALF_DAYS: Int = 12

/** Wire values of the web `ExportFormat` union. */
@Serializable
enum class ExportFormat(val value: String) {
    @SerialName("jpg") JPG("jpg"),
    @SerialName("png") PNG("png");

    companion object {
        fun fromString(value: String?): ExportFormat? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The web `DefaultRankingMode = RankingMode | "none"`. Modelled as a serializable
 * string-backed value class so it round-trips as a plain JSON string: `"none"`
 * means "plain sort order, no ranking profile", anything else is a [RankingMode].
 */
@Serializable
@JvmInline
value class DefaultRankingMode(val value: String) {
    val isNone: Boolean get() = value == "none"
    val rankingMode: RankingMode? get() = if (isNone) null else RankingMode.fromString(value)

    companion object {
        val NONE: DefaultRankingMode = DefaultRankingMode("none")

        /** Accepts a persisted string, a [RankingMode], or anything else -> `none`. */
        fun from(raw: Any?): DefaultRankingMode = when (raw) {
            is DefaultRankingMode -> raw
            "none" -> NONE
            else -> asRankingMode(raw)?.let { DefaultRankingMode(it.value) } ?: NONE
        }
    }
}

@Serializable
data class FFCSSettings(
    /** Schema stamp. Always rewritten to [FFCSS_SETTINGS_REVISION] on sanitize. */
    val revision: Int = FFCSS_SETTINGS_REVISION,
    /** `none` = plain sort order; otherwise a legacy profile name. */
    val rankingMode: DefaultRankingMode = DefaultRankingMode.NONE,
    val defaultSortBy: SortBy = SortBy.BALANCED,
    val exportFormat: ExportFormat = ExportFormat.JPG,
    val separateCourseListExport: Boolean = false,
    /** Relative priority of profile / faculty / sort / time scores. */
    val rankingPriorityOrder: List<RankingPriority> = RankingPriority.DEFAULT_ORDER.toList(),
    val facultyMetricWeights: FacultyMetricWeights = FacultyMetricWeights(4.0, 2.0, 2.0, 2.0),
    val facultyScoreFilters: FacultyScoreFilters = FacultyScoreFilters(0.0, 0.0, 0.0, 0.0),
    val minHalfDays: Int = 0,
    val resultCap: Int = FFCSS_HARD_RESULT_LIMIT,
    /** Only timetables with EVERY selected course count; false = drop ladder on. */
    val allSubjectsMode: Boolean = true,
    val showTour: Boolean = true,
    val vitfacIntegration: Boolean = true,
    val showVerticalTimetableBesideWidgets: Boolean = false,
    val showGeneratorOptionPane: Boolean = true,
    val integrateCurriculumAndGrades: Boolean = true,
)

/** Exact web defaults (revision 2). A fresh snapshot is returned per call. */
object FFCSSettingsDefaults {
    val revision: Int = FFCSS_SETTINGS_REVISION
    val rankingMode: DefaultRankingMode = DefaultRankingMode.NONE
    val defaultSortBy: SortBy = SortBy.BALANCED
    val exportFormat: ExportFormat = ExportFormat.JPG
    val separateCourseListExport: Boolean = false
    val rankingPriorityOrder: List<RankingPriority> = RankingPriority.DEFAULT_ORDER.toList()
    val facultyMetricWeights: FacultyMetricWeights = FacultyMetricWeights(4.0, 2.0, 2.0, 2.0)
    val facultyScoreFilters: FacultyScoreFilters = FacultyScoreFilters(0.0, 0.0, 0.0, 0.0)
    val minHalfDays: Int = 0
    val resultCap: Int = FFCSS_HARD_RESULT_LIMIT
    val allSubjectsMode: Boolean = true
    val showTour: Boolean = true
    val vitfacIntegration: Boolean = true
    val showVerticalTimetableBesideWidgets: Boolean = false
    val showGeneratorOptionPane: Boolean = true
    val integrateCurriculumAndGrades: Boolean = true

    fun snapshot(): FFCSSettings = FFCSSettings(
        revision = revision,
        rankingMode = rankingMode,
        defaultSortBy = defaultSortBy,
        exportFormat = exportFormat,
        separateCourseListExport = separateCourseListExport,
        rankingPriorityOrder = rankingPriorityOrder.toList(),
        facultyMetricWeights = facultyMetricWeights,
        facultyScoreFilters = facultyScoreFilters,
        minHalfDays = minHalfDays,
        resultCap = resultCap,
        allSubjectsMode = allSubjectsMode,
        showTour = showTour,
        vitfacIntegration = vitfacIntegration,
        showVerticalTimetableBesideWidgets = showVerticalTimetableBesideWidgets,
        showGeneratorOptionPane = showGeneratorOptionPane,
        integrateCurriculumAndGrades = integrateCurriculumAndGrades,
    )
}

/**
 * Convert a `JsonElement` or a loosely-typed map into plain Kotlin scalars so
 * the sanitizer can be shared by JSON loads, hand-edited maps and tests.
 * Numbers become [Number]s, JSON strings stay [String]s, booleans stay [Boolean]s.
 */
internal fun ffcsLoose(value: Any?): Any? = when (value) {
    null -> null
    JsonNull -> null
    is JsonPrimitive -> when {
        value.isString -> value.content
        else -> {
            // This kotlinx.serialization exposes JsonPrimitive.content but not the
            // typed *OrNull helpers. Parse the raw literal ourselves; quoted JSON
            // strings stay strings and are parsed later by asInt/asScore.
            val text = value.content
            val boolean = text.toBooleanStrictOrNull()
            val int = text.toIntOrNull()
            val long = text.toLongOrNull()
            val double = text.toDoubleOrNull()
            when {
                boolean != null -> boolean
                int != null -> int
                long != null -> long
                double != null -> double
                else -> text
            }
        }
    }
    is JsonObject -> value.entries.associate { (key, entry) -> key to ffcsLoose(entry) }
    is JsonArray -> value.map(::ffcsLoose)
    is Map<*, *> -> value.entries.associate { entry ->
        (entry.key as? String ?: entry.key.toString()) to ffcsLoose(entry.value)
    }
    is Iterable<*> -> value.map(::ffcsLoose)
    is Boolean, is Number, is String -> value
    else -> value.toString()
}

private fun looseNumber(value: Any?): Double? = when (value) {
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

/** JS `Math.round` then clamp: half away from zero, finite only. */
private fun asInt(value: Any?, min: Int, max: Int, fallback: Int): Int {
    val n = looseNumber(value) ?: return fallback
    if (!n.isFinite()) return fallback
    return kotlin.math.round(n).toInt().coerceIn(min, max)
}

/** Clamp a 0..max score, falling back per field on junk or non-finite input. */
private fun asScore(value: Any?, max: Double, fallback: Double): Double {
    val n = looseNumber(value) ?: return fallback
    if (!n.isFinite()) return fallback
    return n.coerceIn(0.0, max)
}

private fun asBool(value: Any?, fallback: Boolean): Boolean = if (value is Boolean) value else fallback

/**
 * Coerce persisted-or-hand-edited data into a whole settings record.
 * Unknown, malformed or wrong-typed fields fall back individually.
 */
fun sanitizeFFCSSettings(raw: Any?): FFCSSettings {
    val r = ffcsLoose(raw) as? Map<*, *> ?: emptyMap<String, Any?>()
    val revision = asInt(r["revision"], PRE_REVISION, FFCSS_SETTINGS_REVISION, PRE_REVISION)

    val rankingPriorityAllowed = RankingPriority.DEFAULT_ORDER
    val supplied = r["rankingPriorityOrder"] as? List<*> ?: emptyList<Any?>()
    val seen = LinkedHashSet<RankingPriority>()
    val unique = supplied.mapNotNull { entry ->
        val key = when (entry) {
            is RankingPriority -> entry
            is String -> rankingPriorityAllowed.firstOrNull { it.value == entry }
            else -> null
        }
        if (key != null && seen.add(key)) key else null
    }
    // Partial orders are completed with the canonical tail, never truncated.
    val rankingPriorityOrder = unique + rankingPriorityAllowed.filterNot { it in unique }

    val weights = r["facultyMetricWeights"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaultWeights = FFCSSettingsDefaults.facultyMetricWeights
    val facultyMetricWeights = FacultyMetricWeights(
        wPct = asScore(weights["wPct"], 5.0, defaultWeights.wPct),
        lecture = asScore(weights["lecture"], 5.0, defaultWeights.lecture),
        da = asScore(weights["da"], 5.0, defaultWeights.da),
        vibe = asScore(weights["vibe"], 5.0, defaultWeights.vibe),
    )

    val filters = r["facultyScoreFilters"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaultFilters = FFCSSettingsDefaults.facultyScoreFilters
    val facultyScoreFilters = FacultyScoreFilters(
        minWPct = asScore(filters["minWPct"], 100.0, defaultFilters.minWPct),
        minLecture = asScore(filters["minLecture"], 5.0, defaultFilters.minLecture),
        minDa = asScore(filters["minDa"], 5.0, defaultFilters.minDa),
        minVibe = asScore(filters["minVibe"], 5.0, defaultFilters.minVibe),
    )

    val storedCap = asInt(r["resultCap"], FFCSS_MIN_RESULT_CAP, FFCSS_MAX_RESULT_CAP, FFCSSettingsDefaults.resultCap)
    val isLegacyDefault = revision < FFCSS_SETTINGS_REVISION && storedCap == LEGACY_DEFAULT_RESULT_CAP

    return FFCSSettings(
        revision = FFCSS_SETTINGS_REVISION,
        rankingMode = DefaultRankingMode.from(r["rankingMode"]),
        defaultSortBy = SortBy.entries.firstOrNull { it.value == r["defaultSortBy"] }
            ?: FFCSSettingsDefaults.defaultSortBy,
        exportFormat = ExportFormat.entries.firstOrNull { it.value == r["exportFormat"] }
            ?: FFCSSettingsDefaults.exportFormat,
        separateCourseListExport = asBool(
            r["separateCourseListExport"],
            FFCSSettingsDefaults.separateCourseListExport,
        ),
        rankingPriorityOrder = rankingPriorityOrder,
        facultyMetricWeights = facultyMetricWeights,
        facultyScoreFilters = facultyScoreFilters,
        minHalfDays = asInt(r["minHalfDays"], 0, MAX_HALF_DAYS, FFCSSettingsDefaults.minHalfDays),
        resultCap = if (isLegacyDefault) FFCSSettingsDefaults.resultCap else storedCap,
        allSubjectsMode = asBool(r["allSubjectsMode"], FFCSSettingsDefaults.allSubjectsMode),
        showTour = asBool(r["showTour"], FFCSSettingsDefaults.showTour),
        vitfacIntegration = asBool(r["vitfacIntegration"], FFCSSettingsDefaults.vitfacIntegration),
        showVerticalTimetableBesideWidgets = asBool(
            r["showVerticalTimetableBesideWidgets"],
            FFCSSettingsDefaults.showVerticalTimetableBesideWidgets,
        ),
        showGeneratorOptionPane = asBool(
            r["showGeneratorOptionPane"],
            FFCSSettingsDefaults.showGeneratorOptionPane,
        ),
        integrateCurriculumAndGrades = asBool(
            r["integrateCurriculumAndGrades"],
            FFCSSettingsDefaults.integrateCurriculumAndGrades,
        ),
    )
}

/** JSON config shared by the typed storage layer (public so inline accessors can use it). */
val FfcsJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = false
}

/** Settings are written in full (including the revision stamp), unlike sparse session prefs. */
val FfcsSettingsJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
}
