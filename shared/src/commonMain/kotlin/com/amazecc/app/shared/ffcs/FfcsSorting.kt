package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.ffcs.planner.GenerateParams as PlannerParams
import com.amazecc.app.shared.ffcs.planner.Preference
import com.amazecc.app.shared.ffcs.planner.SortBy
import com.amazecc.app.shared.ffcs.planner.TimetableMetrics as PlannerMetrics
import com.amazecc.app.shared.ffcs.planner.TimetableState as PlannerTimetable

/**
 * D-177 — the settled-result half of the web pipeline (reference/04 §5.2:616).
 *
 * `timetableMetrics.ts:464-488`, `ffcsGenerate.ts:961-992` and the faculty
 * comparison sort are reproduced here. All sorts are stable and return a new
 * list; a timetable without metrics always sinks to the bottom regardless of
 * [SortBy]. A missing `rankingScore` is `-1` for ordering only — the field
 * itself stays null (absence is not zero).
 */

/** `MAX_WEEKLY_GAP_MIN` (1200) is declared once in FfcsLimits.kt — reuse it. */

/** `halfDays * 10 + ((1200 - gaps)/60)*5 + socialScore` (gaps are MINUTES). */
fun balancedScore(metrics: PlannerMetrics): Double =
    metrics.halfDays * 10.0 +
        ((MAX_WEEKLY_GAP_MIN - metrics.gaps).toDouble() / 60.0) * 5.0 +
        metrics.socialScore.toDouble()

/** Stable sort with an explicit original-index tiebreak on every platform. */
private fun <T> stableSorted(values: Iterable<T>, comparator: Comparator<T>): List<T> =
    values.toList()
        .mapIndexed { index, value -> index to value }
        .sortedWith { a, b ->
            val byValue = comparator.compare(a.second, b.second)
            if (byValue != 0) byValue else a.first.compareTo(b.first)
        }
        .map { it.second }

/**
 * Base order: strongest first under [sortBy], unscored (metric-less) weeks last.
 * `compactness` compares raw gap minutes, so smaller is better.
 */
fun sortTimetables(
    timetables: List<PlannerTimetable>,
    sortBy: SortBy = SortBy.BALANCED,
): List<PlannerTimetable> {
    val comparator = Comparator<PlannerTimetable> { a, b ->
        val am = a.metrics
        val bm = b.metrics
        when {
            am == null && bm == null -> 0
            am == null -> 1
            bm == null -> -1
            else -> when (sortBy) {
                SortBy.SOCIAL -> bm.socialScore.compareTo(am.socialScore)
                SortBy.HALFDAYS -> bm.halfDays.compareTo(am.halfDays)
                SortBy.COMPACTNESS -> am.gaps.compareTo(bm.gaps)
                SortBy.FACULTY ->
                    (b.facultySummary?.facultyScore ?: -1.0).compareTo(a.facultySummary?.facultyScore ?: -1.0)
                SortBy.BALANCED -> balancedScore(bm).compareTo(balancedScore(am))
            }
        }
    }
    return stableSorted(timetables, comparator)
}

/** Stable ranking blend pass. A null score sorts as -1, never as 0. */
fun sortByRankingScore(timetables: List<PlannerTimetable>): List<PlannerTimetable> =
    stableSorted(timetables, compareByDescending<PlannerTimetable> { it.rankingScore ?: -1.0 })

/**
 * Greedy dedupe: keep the first week whose per-course faculty identities have
 * not been used. ERP id separates two people who share a name.
 */
fun dedupeByFaculty(timetables: List<PlannerTimetable>): List<PlannerTimetable> {
    val used = HashMap<String, MutableSet<String>>()
    return timetables.filter { timetable ->
        val identities = timetable.courses.map { course ->
            course.code to if (course.employeeId.isNullOrEmpty()) course.faculty else "erp:${course.employeeId}"
        }
        if (identities.any { (code, identity) -> used[code]?.contains(identity) == true }) {
            false
        } else {
            for ((code, identity) in identities) {
                used.getOrPut(code) { HashSet() }.add(identity)
            }
            true
        }
    }
}

/**
 * D-174 comparison sort: plans honouring the student's per-course ordering
 * rank first. A plan with no ranked course is a tie (0 for comparison only),
 * so the stable sort preserves the earlier automatic ordering.
 */
fun sortByFacultyPreference(
    timetables: List<PlannerTimetable>,
    priority: FacultyPriorityMap?,
): List<PlannerTimetable> {
    if (!hasFacultyPriority(priority)) return timetables.toList()
    return stableSorted(
        timetables,
        compareByDescending<PlannerTimetable> { timetable ->
            timetablePreferenceScore(timetable.courses, priority, { it.code }, { it.faculty }) ?: 0.0
        },
    )
}

/** The full settle tail: base sort → ranking blend → faculty dedupe → preference. */
fun settleTimetables(
    timetables: List<PlannerTimetable>,
    sortBy: SortBy = SortBy.BALANCED,
    applyRankingSort: Boolean = false,
    uniqueFaculties: Boolean = false,
    facultyPriority: FacultyPriorityMap? = null,
): List<PlannerTimetable> {
    var result = sortTimetables(timetables, sortBy)
    if (applyRankingSort) result = sortByRankingScore(result)
    if (uniqueFaculties) result = dedupeByFaculty(result)
    if (hasFacultyPriority(facultyPriority)) result = sortByFacultyPreference(result, facultyPriority)
    return result
}

/** Same settle, driven directly by the canonical generator params. */
fun settleTimetables(timetables: List<PlannerTimetable>, params: PlannerParams): List<PlannerTimetable> {
    val softPreference = params.strictTheoryPreference == false &&
        (params.preference == Preference.MORNING || params.preference == Preference.EVENING)
    val rankingRan = params.rankingMode != null || params.rankingFactors != null ||
        timetables.any { (it.facultySummary?.ratedFacultyCount ?: 0) > 0 } || softPreference
    return settleTimetables(
        timetables = timetables,
        sortBy = params.sortBy,
        applyRankingSort = rankingRan,
        uniqueFaculties = params.uniqueFaculties,
        facultyPriority = params.facultyPriority,
    )
}
