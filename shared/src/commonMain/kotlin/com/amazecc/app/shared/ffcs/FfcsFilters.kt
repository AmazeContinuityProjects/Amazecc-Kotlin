package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.domain.CHENNAI_CAMPUS_SCHEMA
import com.amazecc.app.shared.domain.CampusSchema as DomainCampusSchema
import com.amazecc.app.shared.domain.DAYS_OF_WEEK
import com.amazecc.app.shared.domain.MORNING_BEFORE_MIN
import com.amazecc.app.shared.domain.SlotPeriod
import com.amazecc.app.shared.domain.periodsForSlot
import com.amazecc.app.shared.domain.slotSpellings
import com.amazecc.app.shared.domain.timeToMinutes
import com.amazecc.app.shared.ffcs.planner.CampusSchema as PlannerCampusSchema
import com.amazecc.app.shared.ffcs.planner.AddedCourse as PlannerAddedCourse
import com.amazecc.app.shared.ffcs.planner.CourseLock as PlannerCourseLock
import com.amazecc.app.shared.ffcs.planner.GenerateParams as PlannerParams
import com.amazecc.app.shared.ffcs.planner.ParsedCourse as PlannerParsedCourse
import com.amazecc.app.shared.ffcs.planner.Preference
import com.amazecc.app.shared.ffcs.planner.SortBy
import com.amazecc.app.shared.ffcs.planner.TimetableState as PlannerTimetable
import com.amazecc.app.shared.ffcs.planner.offeringMatches
import com.amazecc.app.shared.ffcs.planner.splitSlots

/**
 * D-177 — the two pure filter stages of reference/04 §2 (`:85-195`) and the
 * visit-time result gates of §5 (`:624-675`).
 *
 * Order is behaviour, not style: an offering rejected by an early gate is never
 * reconsidered by a later one, and an error's wording depends on which gate
 * rejected everything. [filterOfferings] reproduces the nine `optionsFor` steps
 * verbatim in shipped order; [passesResultFilters] applies lab-day → consecutive
 * lab → faculty minimums → min-half-days. Neither function mutates its input.
 */

private val TIME_24H: Regex = Regex("""^(\d{1,2}):(\d{2})$""")

/** `"08:00"` → 480; malformed input returns null, never a fake midnight. */
private fun parse24HourToMinutes(time: String?): Int? {
    val match = TIME_24H.find(time?.trim() ?: "") ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    if (hours > 24 || minutes > 59) return null
    return hours * 60 + minutes
}

private fun isMorningSlot(slot: String): Boolean {
    val periods = periodsForSlot(slot)
    if (periods.isEmpty()) return true // NIL and unknown ids pass through.
    return periods.any { it.startMin < MORNING_BEFORE_MIN }
}

private fun isEveningSlot(slot: String): Boolean {
    val periods = periodsForSlot(slot)
    if (periods.isEmpty()) return true
    return periods.any { it.startMin >= MORNING_BEFORE_MIN }
}

/** An embedded booking must carry a theory half AND a lab half. */
fun isCompleteEmbeddedCourse(option: PlannerParsedCourse): Boolean {
    val type = option.TYPE.trim().uppercase()
    val embedded = type == "ETH" || type == "ELA" || type == "EPJ" ||
        type.contains("EMBEDDED") || type.contains("+")
    if (!embedded) return true
    val slots = splitSlots(option.SLOT)
    val hasTheory = slots.any { !it.startsWith("L") && it != "NIL" }
    val hasLab = slots.any { it.startsWith("L") }
    return hasTheory && hasLab
}

/**
 * The nine offering filters, in the exact shipped order:
 * allowedSlots → allowedFaculty → exact offerings → embedded completeness →
 * morning/evening → blocked slots (both spellings) → time bounds → friend sync →
 * faculty-priority stable sort. Faculty preference orders the survivors last;
 * it never resurrects an offering rejected earlier.
 */
fun filterOfferings(
    all: List<PlannerParsedCourse>,
    lock: PlannerCourseLock,
    params: PlannerParams,
): List<PlannerParsedCourse> {
    val blocked = params.blockedSlots.map { it.trim().uppercase() }.toSet()
    var options = all.toList()

    // 1. The student named specific slots (whole SLOT or one expanded token).
    if (lock.allowedSlots.isNotEmpty()) {
        val allowed = lock.allowedSlots.map { it.trim().uppercase() }.toSet()
        options = options.filter { opt ->
            allowed.contains(opt.SLOT.trim().uppercase()) || splitSlots(opt.SLOT).any { allowed.contains(it) }
        }
    }

    // 2. The student named specific faculty.
    if (lock.allowedFaculty.isNotEmpty()) {
        options = options.filter { opt -> lock.allowedFaculty.contains(opt.FACULTY) }
    }

    // 3. Exact offerings: modern id OR legacy id (so old locks keep matching).
    val selectedOfferings = lock.offerings
    if (selectedOfferings != null && selectedOfferings.isNotEmpty()) {
        options = options.filter { offeringMatches(it, selectedOfferings) }
    }

    // 4. Embedded rows need both halves.
    options = options.filter(::isCompleteEmbeddedCourse)

    // 5. Morning-only / evening-only, unless the preference is soft.
    if (params.strictTheoryPreference != false &&
        (params.preference == Preference.MORNING || params.preference == Preference.EVENING)
    ) {
        options = options.filter { opt ->
            val theory = splitSlots(opt.SLOT).filter { !it.startsWith("L") && it != "NIL" }
            theory.isEmpty() || if (params.preference == Preference.MORNING) {
                isMorningSlot(theory[0])
            } else {
                isEveningSlot(theory[0])
            }
        }
    }

    // 6. Blocked slots through BOTH spellings: blocking "A" also kills "A1".
    options = options.filter { opt ->
        splitSlots(opt.SLOT).none { token -> slotSpellings(token).any { blocked.contains(it) } }
    }

    // 7. Time bounds; each token's periods must fit the global and per-day limits.
    val lower = params.minStartTime?.let(::parse24HourToMinutes) ?: 0
    val upper = params.maxEndTime?.let(::parse24HourToMinutes) ?: (24 * 60)
    val maxByDay = params.maxEndTimeByDay.orEmpty().mapNotNull { (day, time) ->
        val minutes = time?.let(::parse24HourToMinutes) ?: return@mapNotNull null
        day.value to minutes
    }.toMap()
    if (params.minStartTime != null || params.maxEndTime != null || maxByDay.isNotEmpty()) {
        options = options.filter { opt ->
            splitSlots(opt.SLOT).all { token ->
                periodsForSlot(token).all { period ->
                    val dayLimit = maxByDay[period.day]
                    period.startMin >= lower && period.endMin <= upper &&
                        (dayLimit == null || period.endMin <= dayLimit)
                }
            }
        }
    }

    // 8. Match a friend's timetable exactly — but only when that friend has
    //    this course. An absent course means "no opinion", not "reject all".
    if (params.syncFriendClasses) {
        val friendCourses = params.friends
            .flatMap { it.timetables }
            .flatMap { it.courses }
            .filter { it.code == lock.code }
        if (friendCourses.isNotEmpty()) {
            options = options.filter { opt ->
                val optSlots = splitSlots(opt.SLOT).sorted().joinToString(",")
                friendCourses.any { fc ->
                    val theirSlots = fc.slots.map { it.trim().uppercase() }.sorted().joinToString(",")
                    opt.FACULTY == fc.faculty && optSlots == theirSlots
                }
            }
        }
    }

    // 9. Faculty preference sorts the survivors; the D-174 helper is stable.
    return sortOptionsByFacultyPriority(options, params.facultyPriority, lock.code) { it.FACULTY }
}

private data class LabPairKey(val day: String, val first: Int, val second: Int)

private fun labPeriods(course: PlannerAddedCourse): List<SlotPeriod> =
    course.slots.flatMap { periodsForSlot(it) }.filter { it.type == "lab" }

/** Two different courses' lab bookings on the same day. */
private fun labsShareADay(timetable: PlannerTimetable): Boolean {
    val taken = HashSet<String>()
    for (course in timetable.courses) {
        for (day in labPeriods(course).map { it.day }.toSet()) {
            if (!taken.add(day)) return true
        }
    }
    return false
}

/** Adjacent lab periods on one day, keyed by their schema indices. */
private fun consecutiveLabPairs(
    schema: DomainCampusSchema,
    lunchBreakSeparatesLabs: Boolean,
): Set<LabPairKey> {
    val pairs = HashSet<LabPairKey>()
    val labPeriodsInSchema = schema.lab.orEmpty()
    for (day in DAYS_OF_WEEK) {
        val dayPeriods = labPeriodsInSchema.mapIndexedNotNull { index, period ->
            if (period.lunch || period.start == null || period.end == null) return@mapIndexedNotNull null
            if (period.days?.get(day).isNullOrEmpty()) return@mapIndexedNotNull null
            val start = timeToMinutes(period.start) ?: return@mapIndexedNotNull null
            index to start
        }.sortedWith(compareBy<Pair<Int, Int>>({ it.second }, { it.first }))
        for (i in 0 until dayPeriods.size - 1) {
            val first = dayPeriods[i]
            val second = dayPeriods[i + 1]
            if (lunchBreakSeparatesLabs && second.first > first.first + 1 &&
                labPeriodsInSchema.subList(first.first + 1, second.first).any { it.lunch }
            ) {
                continue
            }
            pairs.add(LabPairKey(day, minOf(first.first, second.first), maxOf(first.first, second.first)))
        }
    }
    return pairs
}

/** Two different courses occupying adjacent lab periods on one day. */
private fun labsAreConsecutive(timetable: PlannerTimetable, pairs: Set<LabPairKey>): Boolean {
    val courses = timetable.courses
    for (firstIndex in courses.indices) {
        val firstPeriods = labPeriods(courses[firstIndex])
        for (secondIndex in firstIndex + 1 until courses.size) {
            val secondPeriods = labPeriods(courses[secondIndex])
            for (a in firstPeriods) {
                for (b in secondPeriods) {
                    if (a.day == b.day &&
                        LabPairKey(a.day, minOf(a.index, b.index), maxOf(a.index, b.index)) in pairs
                    ) {
                        return true
                    }
                }
            }
        }
    }
    return false
}

private fun failsFilter(minimum: Double, score: Double?): Boolean =
    minimum > 0.0 && score != null && score < minimum

/** Active minimums are checked against EVERY rated faculty in the week. */
private fun passesFacultyMinimums(timetable: PlannerTimetable, params: PlannerParams): Boolean {
    val filters = params.facultyScoreFilters ?: return true
    val anyActive = filters.minWPct > 0.0 || filters.minLecture > 0.0 ||
        filters.minDa > 0.0 || filters.minVibe > 0.0
    if (!anyActive || params.facultyRatingsAvailable != true || params.facultyRatings.isNullOrEmpty()) return true
    val summary = timetable.facultySummary ?: return true
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

/**
 * Visit-time gates, in the shipped order:
 * separate lab days → no consecutive labs → faculty minimums → minHalfDays.
 */
fun passesResultFilters(timetable: PlannerTimetable, params: PlannerParams): Boolean {
    if (params.separateLabDays == true && labsShareADay(timetable)) return false
    if (params.avoidConsecutiveLabs == true &&
        labsAreConsecutive(timetable, consecutiveLabPairs(CHENNAI_CAMPUS_SCHEMA, params.lunchBreakSeparatesLabs == true))
    ) {
        return false
    }
    if (!passesFacultyMinimums(timetable, params)) return false
    if ((timetable.metrics?.halfDays ?: 0) < params.minHalfDays) return false
    return true
}

/** Pure composition of [passesResultFilters] over a settled result list. */
fun filterResults(timetables: List<PlannerTimetable>, params: PlannerParams): List<PlannerTimetable> =
    timetables.filter { passesResultFilters(it, params) }

/** Minimal valid params for callers that only care about a subset of gates. */
fun defaultGenerateParams(): PlannerParams = PlannerParams(
    schema = PlannerCampusSchema(),
    masterCourses = emptyList(),
    courseLocks = emptyList(),
    blockedSlots = emptyList(),
    friends = emptyList(),
    preference = Preference.NONE,
    syncFriendClasses = false,
    maximizeFreeTimeFriends = emptyList(),
    uniqueFaculties = false,
    noLimit = false,
    minHalfDays = 0,
    sortBy = SortBy.BALANCED,
)
