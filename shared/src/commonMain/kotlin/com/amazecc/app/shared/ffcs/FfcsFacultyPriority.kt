package com.amazecc.app.shared.ffcs

import kotlin.jvm.JvmName

/**
 * D-174 — per-course manual faculty ordering ("I'd rather have Prof A than Prof B").
 *
 * Ported from `src/lib/ffcsFacultyPriority.ts` (reference/02, section B). The map is
 * keyed by *course code* and holds faculty *names*, in the student's preferred
 * order (best first). Names are matched against the report's own `FACULTY` cell,
 * split on `/` for combined theory/lab rows, compared case-insensitively.
 *
 * Two independent uses, both observable in the shipped planner:
 *  1. [sortOptionsByFacultyPriority] runs before the backtracking walk, so the
 *     preferred offerings are walked first and survive a result cap;
 *  2. [timetablePreferenceScore] runs again after the plans have settled, as a
 *     comparison sort so plans that honour the ordering rank first.
 *
 * "No ordering expressed" is [null], never zero — an unranked course must not
 * take part in plan comparison, otherwise plans differing only in that course
 * would stop being tied and the stable sort would no longer preserve order.
 */

/** Course code → faculties in the student's preferred order (best first). */
typealias FacultyPriorityMap = Map<String, List<String>>

/** A minimal course reference used by the plan-level scorer. */
data class FacultyPreferenceCourse(val code: String, val faculty: String)

/** Split a report `FACULTY` cell into individual, trimmed, non-empty names. */
fun splitFacultyNames(faculty: String?): List<String> =
    (faculty ?: "")
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Coerce persisted / hand-edited data into a usable [FacultyPriorityMap].
 *
 * Entries fall back individually: a malformed course entry is dropped while the
 * others survive, duplicates inside one list collapse to the FIRST occurrence
 * (case-insensitively), unknown course codes are kept verbatim, and courses
 * whose cleaned list is empty are dropped entirely.
 */
fun sanitizeFacultyPriority(raw: Any?): FacultyPriorityMap {
    if (raw !is Map<*, *>) return emptyMap()
    val out = LinkedHashMap<String, List<String>>()
    for ((rawCode, rawValue) in raw) {
        val code = (rawCode as? String)?.trim().orEmpty()
        if (code.isEmpty() || rawValue !is List<*>) continue
        val seen = HashSet<String>()
        val names = ArrayList<String>()
        for (entry in rawValue) {
            if (entry !is String) continue
            val name = entry.trim()
            if (name.isEmpty()) continue
            val key = name.lowercase()
            if (!seen.add(key)) continue // first occurrence wins
            names.add(name)
        }
        if (names.isNotEmpty()) out[code] = names
    }
    return out
}

/**
 * Where this offering sits in a course's ordering, as a `0..1` score.
 *
 * - `null` — no ordering for this course (NEVER coerce to 0);
 * - `0` — the course *is* ranked but this faculty is absent from the list;
 * - `(n - i) / n` for the first matching name at index `i` of `n`.
 */
fun facultyPreferenceScore(priority: List<String>?, faculty: String): Double? {
    if (priority.isNullOrEmpty()) return null
    val wanted = splitFacultyNames(faculty).map { it.lowercase() }
    val index = priority.indexOfFirst { name -> wanted.contains(name.trim().lowercase()) }
    if (index == -1) return 0.0
    return (priority.size - index).toDouble() / priority.size.toDouble()
}

/** Average [facultyPreferenceScore] over courses that actually have an ordering. */
fun <T> timetablePreferenceScore(
    courses: Iterable<T>,
    map: FacultyPriorityMap?,
    codeOf: (T) -> String,
    facultyOf: (T) -> String,
): Double? {
    if (map == null) return null
    var total = 0.0
    var counted = 0
    for (course in courses) {
        val score = facultyPreferenceScore(map[codeOf(course)], facultyOf(course)) ?: continue
        total += score
        counted += 1
    }
    return if (counted > 0) total / counted else null
}

/** Convenience overload for the shared [AddedCourse] model. */
@JvmName("timetablePreferenceScoreForAdded")
fun timetablePreferenceScore(courses: Iterable<AddedCourse>, map: FacultyPriorityMap?): Double? =
    timetablePreferenceScore(courses, map, { it.code }, { it.faculty })

/** Convenience overload for courses already reduced to [code, faculty]. */
@JvmName("timetablePreferenceScoreForRefs")
fun timetablePreferenceScore(courses: Iterable<FacultyPreferenceCourse>, map: FacultyPriorityMap?): Double? =
    timetablePreferenceScore(courses, map, { it.code }, { it.faculty })

private data class RankedOption<T>(val option: T, val index: Int, val rank: Int)

/**
 * Stable sort of one course's offerings: listed faculties keep the student's
 * order, unlisted ones sink behind them in their original (report) order.
 *
 * Rank is `n * (1 - score)` = `i` for a listed faculty, `n` for an unlisted one;
 * the original index is the tiebreak, so the sort is stable and NEVER filters —
 * every input option is present in the output. Call this before the DFS (search
 * order matters at a result cap) and again as the comparison sort after settle.
 */
fun <T> sortOptionsByFacultyPriority(
    options: List<T>,
    map: FacultyPriorityMap?,
    code: String,
    getFaculty: (T) -> String,
): List<T> {
    val priority = map?.get(code)
    if (priority.isNullOrEmpty() || options.size < 2) return options.toList()
    val rankOf: (T) -> Int = { option ->
        val score = facultyPreferenceScore(priority, getFaculty(option))
        // score is null only for an empty priority list, already handled above.
        val rank = (priority.size.toDouble() * (1.0 - (score ?: 0.0)))
        // roundToInt avoids binary-float truncation (e.g. 6*(1-5/6) == 0.9999…).
        kotlin.math.round(rank).toInt()
    }
    return options
        .mapIndexed { index, option -> RankedOption(option, index, rankOf(option)) }
        .sortedWith(compareBy<RankedOption<T>> { it.rank }.thenBy { it.index })
        .map { it.option }
}

/** True when the map carries at least one usable ordering. */
fun hasFacultyPriority(map: FacultyPriorityMap?): Boolean = map != null && map.isNotEmpty()
