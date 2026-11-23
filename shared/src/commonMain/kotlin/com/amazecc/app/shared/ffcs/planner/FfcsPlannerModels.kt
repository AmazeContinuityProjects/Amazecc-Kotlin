package com.amazecc.app.shared.ffcs.planner

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * D-171 — canonical FFCS-planner type contract.
 *
 * Ported verbatim from the ffcs-planner skill:
 *  - `reference/03-data-model-and-schema.md` (`ParsedCourse`, `AddedCourse`,
 *    `TimetableState`, `SlotMap`, `TimetablePeriod`, `CampusSchema`),
 *  - `reference/04-generation-engine.md` §1 (`GenerateParams`, `CourseLock`,
 *    offering identity, `RankingFactor` / `RankingPriority` / `RankingMode`),
 *  - `vendor/amazecc-ffcs/src/lib/ffcsTypes.ts:63-164` (exact field names and
 *    optionality),
 *  - `vendor/amazecc-ffcs/src/lib/ffcsRanking.ts` (enum order and wire values),
 *  - `vendor/amazecc-ffcs/src/lib/timetableMetrics.ts` (nine-field metrics).
 *
 * The docs' "lowercase ParsedCourse" claim is stale; the web code is canonical:
 * ParsedCourse fields really are UPPERCASE and SLOT is one '+'-joined string.
 *
 * IMPORTANT — package seam: the legacy `com.amazecc.app.shared.ffcs` package
 * still owns the old lowercase ParsedCourse / AddedCourse / TimetableState /
 * CourseLock / TimetableMetrics names, and other waves still consume them.
 * These canonical types therefore live in the `.planner` sub-package so both
 * sets can coexist. Later adapter waves bridge the two; do not merge them here.
 *
 * Everything below is plain data: no Set, no class instances with behaviour,
 * no functions — this is the structured-clone wire shape the web worker uses.
 */

// ── Schema / period primitives (reference/03 §1.2, §3.1) ──────────────────

/** `{ [day: string]: string }` — e.g. `{ mon: "A1", wed: "A1" }`. */
typealias SlotMap = Map<String, String>

@Serializable
data class TimetablePeriod(
    val slot: String? = null,
    val start: String? = null,
    val end: String? = null,
    val lunch: Boolean? = null,
    val days: SlotMap? = null,
)

/** `CampusSchema = { theory: SchemaPeriod[], lab: SchemaPeriod[] }`. */
@Serializable
data class CampusSchema(
    val theory: List<TimetablePeriod> = emptyList(),
    val lab: List<TimetablePeriod> = emptyList(),
)

// ── Enum contracts — order and wire values are exact ──────────────────────

/** `DAYS_OF_WEEK = ["mon","tue","wed","thu","fri"]`. Aliases accept full names. */
@Serializable
enum class DayId(val value: String, val aliases: List<String>) {
    @SerialName("mon") MON("mon", listOf("monday", "mon")),
    @SerialName("tue") TUE("tue", listOf("tuesday", "tue", "tues")),
    @SerialName("wed") WED("wed", listOf("wednesday", "wed")),
    @SerialName("thu") THU("thu", listOf("thursday", "thu", "thur", "thurs")),
    @SerialName("fri") FRI("fri", listOf("friday", "fri"));

    companion object {
        /** Stable order — the same one as the web `DAYS_OF_WEEK` constant. */
        val DAYS_OF_WEEK: List<DayId> = entries.toList()

        /** Accepts the wire value (`mon`) and every alias (`monday`, `MON`, …). */
        fun fromString(value: String?): DayId? {
            if (value == null) return null
            val trimmed = value.trim()
            return entries.firstOrNull {
                it.value.equals(trimmed, ignoreCase = true) ||
                    it.aliases.any { alias -> alias.equals(trimmed, ignoreCase = true) }
            }
        }

        fun isDayId(value: String?): Boolean = fromString(value) != null
    }
}

/**
 * The generator's factor list, in canonical engine order. Position is the
 * weight; disabled factors are excluded, never zeroed.
 */
@Serializable
enum class RankingFactor(val value: String) {
    @SerialName("gaps") GAPS("gaps"),
    @SerialName("halfdays") HALFDAYS("halfdays"),
    @SerialName("social") SOCIAL("social"),
    @SerialName("faculty") FACULTY("faculty"),
    @SerialName("earlyFinish") EARLY_FINISH("earlyFinish"),
    @SerialName("lateStart") LATE_START("lateStart"),
    @SerialName("balancedLoad") BALANCED_LOAD("balancedLoad"),
    @SerialName("time") TIME("time");

    companion object {
        val CANONICAL_ORDER: List<RankingFactor> = entries.toList()

        fun fromString(value: String?): RankingFactor? {
            if (value == null) return null
            val trimmed = value.trim()
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}

/** Declared ONCE — the four inputs of the legacy ranking blend. */
@Serializable
enum class RankingPriority(val value: String) {
    @SerialName("profile") PROFILE("profile"),
    @SerialName("faculty") FACULTY("faculty"),
    @SerialName("sort") SORT("sort"),
    @SerialName("time") TIME("time");

    companion object {
        val DEFAULT_ORDER: List<RankingPriority> = listOf(PROFILE, FACULTY, SORT, TIME)

        fun fromString(value: String?): RankingPriority? {
            if (value == null) return null
            val trimmed = value.trim()
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}

/** The six ranking profiles, faculty-focused → schedule-focused. */
@Serializable
enum class RankingMode(val value: String) {
    @SerialName("Best Faculty") BEST_FACULTY("Best Faculty"),
    @SerialName("Balanced") BALANCED("Balanced"),
    @SerialName("Half Days") HALF_DAYS("Half Days"),
    @SerialName("Minimize Gaps") MINIMIZE_GAPS("Minimize Gaps"),
    @SerialName("Early Finish") EARLY_FINISH("Early Finish"),
    @SerialName("Late Start") LATE_START("Late Start");

    companion object {
        val DEFAULT: RankingMode = BALANCED

        fun fromString(value: String?): RankingMode? {
            if (value == null) return null
            val trimmed = value.trim()
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}

@Serializable
enum class Preference(val value: String) {
    @SerialName("none") NONE("none"),
    @SerialName("morning") MORNING("morning"),
    @SerialName("evening") EVENING("evening"),
    @SerialName("compact") COMPACT("compact"),
    @SerialName("spread") SPREAD("spread");

    companion object {
        fun fromString(value: String?): Preference? {
            if (value == null) return null
            val trimmed = value.trim()
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}

@Serializable
enum class SortBy(val value: String) {
    @SerialName("social") SOCIAL("social"),
    @SerialName("halfdays") HALFDAYS("halfdays"),
    @SerialName("compactness") COMPACTNESS("compactness"),
    @SerialName("balanced") BALANCED("balanced"),
    @SerialName("faculty") FACULTY("faculty");

    companion object {
        fun fromString(value: String?): SortBy? {
            if (value == null) return null
            val trimmed = value.trim()
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        }
    }
}

// ── Canonical report / lock / result types ────────────────────────────────

/**
 * One report row. Field names are UPPERCASE on the wire and in Kotlin — the
 * parser, share codecs and generator all use this exact shape.
 *
 * CREDITS is deliberately a String (`"3"`, `"10"`), and SLOT is one '+'-joined
 * string (`"A1+TA1"`), not a list.
 */
@Serializable
data class ParsedCourse(
    @SerialName("CODE") val CODE: String,
    @SerialName("TITLE") val TITLE: String,
    @SerialName("TYPE") val TYPE: String,
    @SerialName("CREDITS") val CREDITS: String,
    @SerialName("ROOM") val ROOM: String,
    @SerialName("SLOT") val SLOT: String,
    @SerialName("FACULTY") val FACULTY: String,
    @SerialName("ORIGINAL_CODE") val ORIGINAL_CODE: String? = null,
    @SerialName("LINK_ID") val LINK_ID: String? = null,
    @SerialName("BATCH") val BATCH: String? = null,
    @SerialName("CLASS_ID") val CLASS_ID: String? = null,
    @SerialName("CLASS_IDS") val CLASS_IDS: List<String>? = null,
    @SerialName("ERP_ID") val ERP_ID: String? = null,
    @SerialName("EMPLOYEE_SCHOOL") val EMPLOYEE_SCHOOL: String? = null,
) {
    /** Modern identity: `FACULTY|SLOT|ROOM` plus `|ERP_ID` when the report has one. */
    fun offeringId(): String {
        val base = "$FACULTY|$SLOT|$ROOM"
        return if (!ERP_ID.isNullOrEmpty()) "$base|$ERP_ID" else base
    }

    /** Legacy identity, still accepted so old locks keep matching. */
    fun legacyOfferingId(): String = "$FACULTY|$SLOT|$ROOM"
}

/** Split a '+'-joined SLOT into trimmed, upper-cased, non-empty tokens. */
fun splitSlots(slot: String?): List<String> =
    (slot ?: "").split('+').map { it.trim().uppercase() }.filter { it.isNotEmpty() }

/** True when the selection holds either the modern or the legacy offering id. */
fun offeringMatches(offering: ParsedCourse, selected: Iterable<String>): Boolean =
    selected.any { it == offering.offeringId() || it == offering.legacyOfferingId() }

/** A settled timetable course; `slots` is expanded (cf. ParsedCourse.SLOT). */
@Serializable
data class AddedCourse(
    val id: String,
    val code: String,
    val title: String,
    val slots: List<String>,
    val faculty: String,
    val venue: String,
    val credits: String,
    val type: String,
    val color: String,
    val employeeId: String? = null,
    val classId: String? = null,
    val classIds: List<String>? = null,
    val batch: String? = null,
)

/** Empty allowedSlots / allowedFaculty both mean "unrestricted". */
@Serializable
data class CourseLock(
    val code: String,
    val title: String,
    val allowedSlots: List<String>,
    val allowedFaculty: List<String>,
    /** Exact offerings: `FACULTY|SLOT|ROOM` plus `|ERP_ID` when available. */
    val offerings: List<String>? = null,
)

/** One free half-day gap, in minutes. */
@Serializable
data class GapDetail(
    val day: DayId,
    val startMin: Int,
    val endMin: Int,
    val durationMins: Int,
    val fromClass: String? = null,
    val toClass: String? = null,
    val fromTime: String? = null,
    val toTime: String? = null,
)

/** A between-building dash. */
@Serializable
data class DashDetail(
    val fromClass: String,
    val toClass: String,
    val fromTime: String,
    val toTime: String,
    val day: String,
    val fromBlock: String,
    val toBlock: String,
)

/**
 * The web's exact nine-field scoring contract. The legacy Kotlin copy in
 * `FfcsModels.kt` is stale (no `bestFriendMatches`), so the canonical shape
 * lives here instead of being imported from there.
 */
@Serializable
data class TimetableMetrics(
    val halfDays: Int,
    val gaps: Int,
    val gapsPerDay: Map<String, Int> = emptyMap(),
    val gapDetails: List<GapDetail> = emptyList(),
    val buildingDashes: Int = 0,
    val dashDetails: List<DashDetail> = emptyList(),
    val isLongWeekend: Boolean = false,
    val socialScore: Int = 0,
    val bestFriendMatches: List<String> = emptyList(),
) {
    companion object {
        val KEYS: List<String> = listOf(
            "halfDays", "gaps", "gapsPerDay", "gapDetails", "buildingDashes",
            "dashDetails", "isLongWeekend", "socialScore", "bestFriendMatches",
        )
    }
}

@Serializable
data class FacultyRating(
    val wPct: Double? = null,
    val lecture: Double? = null,
    val da: Double? = null,
    val vibe: Double? = null,
    val reviews: Int = 0,
)

@Serializable
data class FacultyCourseScore(
    val code: String,
    val faculty: String,
    val rating: FacultyRating? = null,
)

@Serializable
data class FacultyRankingSummary(
    val averageWPct: Double? = null,
    val averageLecture: Double? = null,
    val averageDa: Double? = null,
    val averageVibe: Double? = null,
    val facultyScore: Double? = null,
    val ratedFacultyCount: Int = 0,
    val unratedFacultyCount: Int = 0,
    val totalFacultyCount: Int = 0,
    val courses: List<FacultyCourseScore> = emptyList(),
)

/** The four input scores combined to produce rankingScore; all may be absent. */
@Serializable
data class RankingComponents(
    val profile: Double? = null,
    val faculty: Double? = null,
    val sort: Double? = null,
    val time: Double? = null,
)

/**
 * A generated timetable.
 *
 * `rankingScore` defaults to and stays null when absent. Null means "no ranking
 * was asked for", NOT "scored zero" — never substitute 0.0.
 */
@Serializable
data class TimetableState(
    val id: String,
    val name: String,
    val courses: List<AddedCourse>,
    val metrics: TimetableMetrics? = null,
    val variants: List<TimetableState>? = null,
    val rankingScore: Double? = null,
    val rankingBreakdown: Map<String, Double>? = null,
    val facultySummary: FacultyRankingSummary? = null,
    val rankingComponents: RankingComponents? = null,
)

@Serializable
data class Friend(
    val id: String,
    val name: String,
    val timetables: List<TimetableState>,
)

/** Per-metric weights used by the Vitfac aggregate. */
@Serializable
data class FacultyMetricWeights(
    val wPct: Double,
    val lecture: Double,
    val da: Double,
    val vibe: Double,
)

/** Minimum per-metric scores a faculty member must clear to be counted. */
@Serializable
data class FacultyScoreFilters(
    val minWPct: Double,
    val minLecture: Double,
    val minDa: Double,
    val minVibe: Double,
)

/** Course code → faculties in the student's preferred order (best first). */
typealias FacultyPriorityMap = Map<String, List<String>>

/** Faculty key → prepared Vitfac rating. */
typealias FacultyRatingLookup = Map<String, FacultyRating>

/** The generator's single ranked list; disabled factors are excluded. */
@Serializable
data class RankingFactorsConfig(
    val order: List<RankingFactor>,
    val disabled: List<RankingFactor> = emptyList(),
)

// ── GenerateParams: plain, structured-clone-safe data ─────────────────────

/**
 * Web `GenerateParams` (reference/04 §1.1, ffcsGenerate.ts:96-230).
 *
 * Required fields have NO default — the web requires them, so nothing is
 * fabricated here. Fields the web marks optional (`?`) default to null.
 * In particular rankingMode / rankingFactors / facultyPriority are unset by
 * default: exactly one ranking path runs per call, never two stacked.
 */
@Serializable
data class GenerateParams(
    val schema: CampusSchema,
    val masterCourses: List<ParsedCourse>,
    val courseLocks: List<CourseLock>,
    val blockedSlots: List<String>,
    val friends: List<Friend>,
    val preference: Preference,
    val syncFriendClasses: Boolean,
    val maximizeFreeTimeFriends: List<String>,
    val uniqueFaculties: Boolean,
    val noLimit: Boolean,
    val minHalfDays: Int,
    val sortBy: SortBy,
    val strictTheoryPreference: Boolean? = null,
    val minStartTime: String? = null,
    val maxEndTime: String? = null,
    val maxEndTimeByDay: Map<DayId, String?>? = null,
    val resultCap: Int? = null,
    val removeHardResultLimit: Boolean? = null,
    val rankingMode: RankingMode? = null,
    val facultyRatings: FacultyRatingLookup? = null,
    /** Distinguishes a loaded catalog with unrated faculty from an unavailable integration. */
    val facultyRatingsAvailable: Boolean? = null,
    val facultyMetricWeights: FacultyMetricWeights? = null,
    val facultyScoreFilters: FacultyScoreFilters? = null,
    val rankingPriorityOrder: List<RankingPriority>? = null,
    val rankingFactors: RankingFactorsConfig? = null,
    val separateLabDays: Boolean? = null,
    val avoidConsecutiveLabs: Boolean? = null,
    val lunchBreakSeparatesLabs: Boolean? = null,
    val facultyPriority: FacultyPriorityMap? = null,
)
