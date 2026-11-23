package com.amazecc.app.shared.ffcs

import kotlin.jvm.JvmName

/**
 * D-174 — Vitfac aggregation layer, ported from `src/lib/ffcsFacultyRanking.ts`.
 *
 * Faculty ratings are an OPTIONAL external signal: when the source has no
 * rating for a person, that person is simply unrated. No rating is ever
 * fabricated — every aggregate stays `null` (or a zero count / empty list)
 * when there is nothing to average, and an active score filter ignores a
 * metric whose score is absent rather than rejecting it.
 *
 * The public result ([FacultyRankingSummary]) is plain data so it can cross a
 * generator-worker boundary unchanged.
 */

/** One faculty member's optional metrics. `null` = metric unavailable. */
data class FacultyRating(
    val wPct: Double? = null,
    val lecture: Double? = null,
    val da: Double? = null,
    val vibe: Double? = null,
    val reviews: Int = 0,
)

/** Lookup key → rating. Keys are `id:<employeeId>` then `name:<normalised>`. */
typealias FacultyRatingLookup = Map<String, FacultyRating>

data class FacultyCourseScore(
    val code: String,
    val faculty: String,
    val rating: FacultyRating?,
)

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

/** Documented weights: W% is twice as important as each of the other three. */
data class FacultyMetricWeights(
    val wPct: Double = 4.0,
    val lecture: Double = 2.0,
    val da: Double = 2.0,
    val vibe: Double = 2.0,
) {
    companion object {
        val DEFAULT: FacultyMetricWeights = FacultyMetricWeights()
    }
}

/** All minimums are inactive at 0: `min > 0 && score != null && score < min` rejects. */
data class FacultyScoreFilters(
    val minWPct: Double = 0.0,
    val minLecture: Double = 0.0,
    val minDa: Double = 0.0,
    val minVibe: Double = 0.0,
) {
    companion object {
        val DEFAULT: FacultyScoreFilters = FacultyScoreFilters()
    }
}

/** The identity fields the vitfac lookup needs, independent of the course model. */
interface FacultyIdentity {
    val employeeId: String?
    val faculty: String
}

data class FacultyRatingPerson(
    override val employeeId: String? = null,
    override val faculty: String = "",
) : FacultyIdentity

/** A course offering reduced to the fields the rating summary needs. */
data class RatedCourseRef(
    val code: String,
    override val faculty: String,
    override val employeeId: String? = null,
) : FacultyIdentity

fun ParsedCourse.asFacultyIdentity(): FacultyRatingPerson = FacultyRatingPerson(faculty = this.faculty)

fun AddedCourse.asRatedCourseRef(): RatedCourseRef =
    RatedCourseRef(code = this.code, faculty = this.faculty)

// ─── name keys ──────────────────────────────────────────────────────────────
// Two distinct normalisations live in the source; both are kept here.
//  • facultyRatingKeys uses the vitfac-specific ASCII key (lowercase, drop a
//    leading honorific, keep only a-z0-9) so a catalog name can find its rating.
//  • normalizeFacultyName / facultyGroupKey are the documented grouping identity
//    from reference/03 §4.1 (honorific-aware, word-order independent).

private val VITFAC_HONORIFIC = Regex("^(dr|prof|mr|mrs|ms)\\.?\\s+", RegexOption.IGNORE_CASE)
private val VITFAC_NON_ALNUM = Regex("[^a-z0-9]")

/** ASCII-only lowercase keeps the key platform/locale independent. */
private fun asciiLower(value: String): String = buildString(value.length) {
    for (ch in value) {
        append(if (ch in 'A'..'Z') ('a'.code + (ch.code - 'A'.code)).toChar() else ch)
    }
}

/** ASCII-only uppercase keeps the grouping key platform/locale independent. */
private fun asciiUpper(value: String): String = buildString(value.length) {
    for (ch in value) {
        append(if (ch in 'a'..'z') ('A'.code + (ch.code - 'a'.code)).toChar() else ch)
    }
}

private fun ratingNameKey(name: String): String {
    val lowered = asciiLower(name)
    val withoutHonorific = VITFAC_HONORIFIC.replace(lowered, "")
    return VITFAC_NON_ALNUM.replace(withoutHonorific, "")
}

/** Employee id first (unique), normalised name second (legacy reports). */
fun facultyRatingKeys(person: FacultyIdentity): List<String> {
    val keys = ArrayList<String>(2)
    val employeeId = person.employeeId?.trim()
    val normalizedName = if (person.faculty.isEmpty()) "" else ratingNameKey(person.faculty)
    if (!employeeId.isNullOrEmpty()) keys.add("id:$employeeId")
    if (normalizedName.isNotEmpty()) keys.add("name:$normalizedName")
    return keys
}

private val HONORIFICS = setOf(
    "DR", "PROF", "PROFESSOR", "MR", "MRS", "MS", "MISS", "SMT", "SHRI", "SRI", "ER", "KUM"
)
private val PAREN_TOKEN = Regex("\\([^)]*\\)")
private val NON_LETTER_OR_SPACE = Regex("[^A-Z\\s]")
private val WHITESPACE = Regex("\\s+")

/** Grouping identity from reference/03:456-493 (facultyGroups.ts). */
fun normalizeFacultyName(name: String?): String {
    var value = asciiUpper(name ?: "")
    value = PAREN_TOKEN.replace(value, " ")
    value = NON_LETTER_OR_SPACE.replace(value, " ")
    value = WHITESPACE.replace(value, " ").trim()
    val tokens = value.split(' ').filter { it.isNotEmpty() }.toMutableList()
    while (tokens.size > 1 && tokens[0] in HONORIFICS) tokens.removeAt(0)
    return tokens.joinToString(" ")
}

/** Normalised tokens, sorted, so word order cannot split a group. */
fun facultyGroupKey(name: String?): String =
    normalizeFacultyName(name)
        .split(' ')
        .filter { it.isNotEmpty() }
        .sorted()
        .joinToString(" ")

// ─── rating construction & aggregation ─────────────────────────────────────

private fun boundedScore(value: Double?, max: Double): Double? {
    if (value == null || value.isNaN() || value.isInfinite()) return null
    return value.coerceIn(0.0, max)
}

/**
 * Build a plain-data lookup from master catalogue rows. A missing rating is
 * skipped (no entry created); present ratings are clamped to their documented
 * ranges (W% 0..100, lecture/da/vibe 0..5, reviews ≥ 0).
 */
@JvmName("buildFacultyRatingLookupForIdentities")
fun buildFacultyRatingLookup(
    masterCourses: Iterable<FacultyIdentity>,
    lookup: (FacultyIdentity) -> FacultyRating?,
): FacultyRatingLookup {
    val result = LinkedHashMap<String, FacultyRating>()
    for (course in masterCourses) {
        val rating = lookup(course) ?: continue
        val safe = FacultyRating(
            wPct = boundedScore(rating.wPct, 100.0),
            lecture = boundedScore(rating.lecture, 5.0),
            da = boundedScore(rating.da, 5.0),
            vibe = boundedScore(rating.vibe, 5.0),
            reviews = maxOf(0, rating.reviews),
        )
        for (key in facultyRatingKeys(course)) result[key] = safe
    }
    return result
}

/** Convenience overload for the shared 7-field [ParsedCourse] (no ERP id). */
@JvmName("buildFacultyRatingLookupForCourses")
fun buildFacultyRatingLookup(
    masterCourses: Iterable<ParsedCourse>,
    lookup: (FacultyIdentity) -> FacultyRating?,
): FacultyRatingLookup = buildFacultyRatingLookup(masterCourses.map { it.asFacultyIdentity() }, lookup)

private fun ratingForCourse(course: FacultyIdentity, lookup: FacultyRatingLookup): FacultyRating? {
    for (key in facultyRatingKeys(course)) {
        lookup[key]?.let { return it }
    }
    return null
}

private data class WeightedValue(val value: Double?, val weight: Double)

private fun weightedAverage(values: List<WeightedValue>): Double? {
    val present = values.filter { it.value != null && it.weight > 0.0 }
    if (present.isEmpty()) return null
    val totalWeight = present.sumOf { it.weight }
    if (totalWeight == 0.0) return null
    return present.sumOf { (it.value ?: 0.0) * it.weight } / totalWeight
}

private data class MetricContribution(val value: Double?, val weight: Double)

/**
 * Coverage-adjusted Vitfac summary for the faculty in a week's timetable.
 *
 * Averages skip absent metrics, aggregates skip absent weights, and [facultyScore]
 * is `ratingAverage * (rated / total)` — so a week with no ratings has a `null`
 * score, never a zero pretending to be data.
 */
@JvmName("facultyRankingSummaryForRefs")
fun facultyRankingSummary(
    courses: Iterable<RatedCourseRef>,
    lookup: FacultyRatingLookup,
    weights: FacultyMetricWeights = FacultyMetricWeights.DEFAULT,
): FacultyRankingSummary {
    val unique = LinkedHashMap<String, FacultyCourseScore>()
    val courseScores = courses.map { course ->
        val keys = facultyRatingKeys(course)
        val identity = keys.firstOrNull() ?: "name:${ratingNameKey(course.faculty)}"
        val score = FacultyCourseScore(course.code, course.faculty, ratingForCourse(course, lookup))
        if (!unique.containsKey(identity)) unique[identity] = score
        score
    }

    val faculty = unique.values.toList()
    val rated = faculty.filter { it.rating != null }
    fun weightFor(rating: FacultyRating): Double = maxOf(1, minOf(rating.reviews, 20)).toDouble()
    fun valuesFor(metric: (FacultyRating) -> Double?): List<WeightedValue> = rated.map { person ->
        val rating = person.rating!!
        WeightedValue(metric(rating), weightFor(rating))
    }

    val averageWPct = weightedAverage(valuesFor { it.wPct })
    val averageLecture = weightedAverage(valuesFor { it.lecture })
    val averageDa = weightedAverage(valuesFor { it.da })
    val averageVibe = weightedAverage(valuesFor { it.vibe })

    val metrics = listOf(
        MetricContribution(averageWPct?.let { it / 100.0 }, weights.wPct),
        MetricContribution(averageLecture?.let { it / 5.0 }, weights.lecture),
        MetricContribution(averageDa?.let { it / 5.0 }, weights.da),
        MetricContribution(averageVibe?.let { it / 5.0 }, weights.vibe),
    )
    val available = metrics.filter { it.value != null && it.value!!.isFinite() && it.weight > 0.0 }
    val totalWeight = available.sumOf { it.weight }
    val ratingAverage = if (totalWeight > 0.0) {
        100.0 * available.sumOf { (it.value ?: 0.0) * it.weight } / totalWeight
    } else {
        null
    }
    val coverage = if (faculty.isNotEmpty()) rated.size.toDouble() / faculty.size.toDouble() else 0.0

    return FacultyRankingSummary(
        averageWPct = averageWPct,
        averageLecture = averageLecture,
        averageDa = averageDa,
        averageVibe = averageVibe,
        facultyScore = ratingAverage?.let { it * coverage },
        ratedFacultyCount = rated.size,
        unratedFacultyCount = faculty.size - rated.size,
        totalFacultyCount = faculty.size,
        courses = courseScores,
    )
}

/** Convenience overload for the shared [AddedCourse] model (no employee id). */
@JvmName("facultyRankingSummaryForAdded")
fun facultyRankingSummary(
    courses: Iterable<AddedCourse>,
    lookup: FacultyRatingLookup,
    weights: FacultyMetricWeights = FacultyMetricWeights.DEFAULT,
): FacultyRankingSummary = facultyRankingSummary(courses.map { it.asRatedCourseRef() }, lookup, weights)

private fun failsFilter(minimum: Double, score: Double?): Boolean =
    minimum > 0.0 && score != null && score < minimum

/** An active minimum is checked against EVERY rated faculty in the week. */
fun passesFacultyScoreFilters(summary: FacultyRankingSummary, filters: FacultyScoreFilters): Boolean {
    if (summary.ratedFacultyCount == 0) return true
    for (person in summary.courses) {
        val rating = person.rating ?: continue
        if (failsFilter(filters.minWPct, rating.wPct)) return false
        if (failsFilter(filters.minLecture, rating.lecture)) return false
        if (failsFilter(filters.minDa, rating.da)) return false
        if (failsFilter(filters.minVibe, rating.vibe)) return false
    }
    return true
}
