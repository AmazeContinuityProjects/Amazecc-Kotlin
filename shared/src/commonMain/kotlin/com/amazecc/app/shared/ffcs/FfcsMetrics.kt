package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.domain.CHENNAI_CAMPUS_SCHEMA
import com.amazecc.app.shared.domain.CampusSchema as DomainCampusSchema
import com.amazecc.app.shared.domain.MORNING_BEFORE_MIN
import com.amazecc.app.shared.domain.expandSlotSpellings
import com.amazecc.app.shared.domain.hasSlot
import com.amazecc.app.shared.domain.normaliseSlot
import com.amazecc.app.shared.domain.timeToMinutes
import com.amazecc.app.shared.ffcs.planner.AddedCourse as PlannerCourse
import com.amazecc.app.shared.ffcs.planner.Friend as PlannerFriend
import kotlin.math.round

/**
 * D-177 — timetable metrics, ported from `lib/timetableMetrics.ts`.
 *
 * Two shapes live here on purpose:
 *
 * - [CanonicalTimetableMetrics] is the faithful contract. **gaps are MINUTES**
 *   (`MAX_WEEKLY_GAP_MIN = 1200` is 20 hours, and `balancedScore` divides by 60).
 *   Its [CanonicalTimetableMetrics.socialScore] is `null` when nobody supplied
 *   friend data — absence is never silently turned into a real-looking zero.
 * - [calculateTimetableMetrics] is the pre-existing public entry point used by
 *   FfcsEngine, the ViewModel and the screens. Its `TimetableMetrics.gaps` field
 *   is legacy *whole hours*, so it is adapted from the canonical minutes at the
 *   boundary. No caller has to change, but the computation no longer truncates
 *   each individual gap before adding it up.
 *
 * Day walks use the shared Chennai schema, so a period is reported once even
 * when both the theory and the lab half hold it, and Law School spellings answer
 * to their schema ids. Half-days are **free** halves (morning/evening), not
 * compact class days — the two must never be swapped.
 */

/** A gap must be strictly longer than this to count as dead time. */
const val GAP_WORTH_REPORTING_MIN: Int = 5

/** Two classes this close together are a between-building dash, not a gap. */
const val DASH_WINDOW_MIN: Int = 15

/** Legacy slot count used by the web social formula, kept so scores do not drift. */
const val TOTAL_SLOT_COUNT: Int = 60

/** The structural input the metrics/social walk needs, independent of model version. */
data class MetricCourse(
    val slots: List<String>,
    val code: String = "",
    val title: String = "",
    val faculty: String = "",
    val venue: String = "",
    val type: String = "",
)

/** The faithful, minute-based nine-field metric contract (plus free-half ids). */
data class CanonicalTimetableMetrics(
    val halfDays: Int,
    val gapsMinutes: Int,
    val gapsPerDay: Map<String, Int>,
    val gapDetails: List<GapDetail>,
    val buildingDashes: Int,
    val dashDetails: List<DashDetail>,
    val isLongWeekend: Boolean,
    /** Null until real friend data exists — never a fabricated zero. */
    val socialScore: Int?,
    val bestFriendMatches: List<String>,
    val freeHalfDays: List<String>,
)

/** `percentage` is the rounded 0..100 web score; the other fields are its raw inputs. */
data class SocialScoreResult(
    val percentage: Int,
    val actualScore: Int,
    val maxScore: Int,
    val bestFriendMatches: List<String> = emptyList(),
)

fun AddedCourse.asMetricCourse(): MetricCourse = MetricCourse(
    slots = slots, code = code, title = title, faculty = faculty, venue = venue, type = type,
)

fun PlannerCourse.asMetricCourse(): MetricCourse = MetricCourse(
    slots = slots, code = code, title = title, faculty = faculty, venue = venue, type = type,
)

private data class MetricsOccupiedClass(
    val startMin: Int,
    val endMin: Int,
    val start: String,
    val end: String,
    val code: String,
    val title: String,
    val venue: String,
)

private data class MetricsDayOccupancy(
    val classes: List<MetricsOccupiedClass>,
    val morningOccupied: Boolean,
    val eveningOccupied: Boolean,
)

private val METRICS_DAY_KEYS: List<String> = listOf("mon", "tue", "wed", "thu", "fri")

/** Web parity: only an "embedded" or composite venue is split by half. */
private fun metricsVenueForHalf(venue: String, half: String): String {
    if (!venue.contains("embedded", ignoreCase = true) && !venue.contains('/')) return venue
    val parts = venue.split('/')
    if (parts.size < 2) return venue
    return if (half == "theory") parts[0].trim() else parts[1].trim()
}

/** Web parity: `metricsBlockOfVenue` is just the part before the first dash. */
private fun metricsBlockOfVenue(venue: String): String = venue.split('-').firstOrNull()?.trim().orEmpty()

private fun metricsIsRealBlock(block: String): Boolean = block.isNotEmpty() && block != "NIL"

private fun metricsNameOf(occupied: MetricsOccupiedClass): String = "${occupied.code} (${occupied.title})"

/**
 * The single day walk. Theory and lab period lists are read index-aligned after
 * removing lunch markers; a period held by both halves is reported once.
 */
private fun metricsDayOccupancy(
    courses: List<MetricCourse>,
    schema: DomainCampusSchema,
    day: String,
): MetricsDayOccupancy {
    val theoryPeriods = schema.theory.orEmpty().filterNot { it.lunch }
    val labPeriods = schema.lab.orEmpty().filterNot { it.lunch }
    val owned = expandSlotSpellings(courses.flatMap { it.slots })

    val classes = mutableListOf<MetricsOccupiedClass>()
    theoryPeriods.forEachIndexed { index, period ->
        val start = period.start ?: return@forEachIndexed
        val end = period.end ?: return@forEachIndexed
        val startMin = timeToMinutes(start) ?: return@forEachIndexed
        val endMin = timeToMinutes(end) ?: return@forEachIndexed
        val labSlot = labPeriods.getOrNull(index)?.days?.get(day)

        for (half in listOf("theory", "lab")) {
            val slot = if (half == "theory") period.days?.get(day) else labSlot
            if (slot == null || slot.isEmpty()) continue
            if (!owned.contains(normaliseSlot(slot))) continue
            val course = courses.firstOrNull { hasSlot(it.slots, slot) }
            classes.add(
                MetricsOccupiedClass(
                    startMin = startMin,
                    endMin = endMin,
                    start = start,
                    end = end,
                    code = course?.code.orEmpty(),
                    title = course?.title.orEmpty(),
                    venue = metricsVenueForHalf(course?.venue.orEmpty(), half),
                )
            )
            break
        }
    }

    val sorted = classes.sortedBy { it.startMin }
    return MetricsDayOccupancy(
        classes = sorted,
        morningOccupied = sorted.any { it.startMin < MORNING_BEFORE_MIN },
        eveningOccupied = sorted.any { it.startMin >= MORNING_BEFORE_MIN },
    )
}

/** Ids of every unoccupied half of the week. Max ten; empty week returns all ten. */
fun freeHalfDays(courses: Iterable<MetricCourse>): List<String> {
    val metricCourses = courses.toList()
    val free = mutableListOf<String>()
    for (day in METRICS_DAY_KEYS) {
        val occupancy = metricsDayOccupancy(metricCourses, CHENNAI_CAMPUS_SCHEMA, day)
        if (!occupancy.morningOccupied) free.add("${day}_morning")
        if (!occupancy.eveningOccupied) free.add("${day}_evening")
    }
    return free
}

/** Shared classes ×3, mutually free slots ×1, shared free half-days ×5. */
fun pairwiseSocialScore(
    mine: Iterable<MetricCourse>,
    theirs: Iterable<MetricCourse>,
): SocialScoreResult {
    val myCourses = mine.toList()
    val theirCourses = theirs.toList()
    val mySlots = expandSlotSpellings(myCourses.flatMap { it.slots })
    val theirSlots = expandSlotSpellings(theirCourses.flatMap { it.slots })

    val mutuallyFree = TOTAL_SLOT_COUNT - (mySlots + theirSlots).size
    val maxMutuallyFree = TOTAL_SLOT_COUNT - theirSlots.size

    val myCourseBySlot = LinkedHashMap<String, String>()
    for (course in myCourses) {
        for (slot in course.slots) myCourseBySlot[normaliseSlot(slot)] = course.code
    }
    var sharedClasses = 0
    for ((slot, code) in myCourseBySlot) {
        if (theirCourses.any { it.code == code && hasSlot(it.slots, slot) }) sharedClasses++
    }
    val maxSharedClasses = theirSlots.size

    val myFree = freeHalfDays(myCourses).toSet()
    val theirFree = freeHalfDays(theirCourses)
    val sharedHalfDays = theirFree.count { it in myFree }

    val actual = sharedClasses * 3 + mutuallyFree + sharedHalfDays * 5
    val maximum = maxSharedClasses * 3 + maxMutuallyFree + theirFree.size * 5
    val percentage = if (maximum > 0) round(actual.toDouble() / maximum * 100.0).toInt() else 0
    return SocialScoreResult(percentage, actual, maximum)
}

/**
 * Best pairwise score against any one friend, averaged across scored friends.
 * Returns null when there are no friends at all — an honest absence of data.
 */
fun socialScoreAgainst(
    courses: Iterable<MetricCourse>,
    friends: Iterable<PlannerFriend>,
): SocialScoreResult? {
    val friendList = friends.toList()
    if (friendList.isEmpty()) return null
    val mine = courses.toList()

    var total = 0
    var bestOverall = -1
    val closest = mutableListOf<String>()
    for (friend in friendList) {
        val timetables = friend.timetables
        if (timetables.isEmpty()) continue
        var best = 0
        for (timetable in timetables) {
            val percentage = pairwiseSocialScore(mine, timetable.courses.map { it.asMetricCourse() }).percentage
            if (percentage > best) best = percentage
        }
        total += best
        if (best > bestOverall) {
            bestOverall = best
            closest.clear()
            closest.add(friend.name)
        } else if (best == bestOverall) {
            closest.add(friend.name)
        }
    }
    return SocialScoreResult(
        percentage = round(total.toDouble() / friendList.size.toDouble()).toInt(),
        actualScore = total,
        maxScore = friendList.size,
        bestFriendMatches = closest.toList(),
    )
}

/** Canonical, minute-based metrics. This is the contract new waves should use. */
fun canonicalMetrics(
    courses: Iterable<MetricCourse>,
    friends: Iterable<PlannerFriend> = emptyList(),
): CanonicalTimetableMetrics {
    val metricCourses = courses.toList()
    val gapDetails = mutableListOf<GapDetail>()
    val dashDetails = mutableListOf<DashDetail>()
    val gapsPerDay = LinkedHashMap<String, Int>()
    var totalGaps = 0
    var buildingDashes = 0
    var freeHalfDayCount = 0
    var mondayFree = true
    var fridayFree = true

    for (day in METRICS_DAY_KEYS) {
        val occupancy = metricsDayOccupancy(metricCourses, CHENNAI_CAMPUS_SCHEMA, day)
        if (occupancy.classes.isNotEmpty()) {
            if (day == "mon") mondayFree = false
            if (day == "fri") fridayFree = false
        }
        if (!occupancy.morningOccupied) freeHalfDayCount++
        if (!occupancy.eveningOccupied) freeHalfDayCount++

        var dayGaps = 0
        for (i in 1 until occupancy.classes.size) {
            val previous = occupancy.classes[i - 1]
            val current = occupancy.classes[i]
            val gap = current.startMin - previous.endMin

            if (gap > GAP_WORTH_REPORTING_MIN) {
                dayGaps += gap
                gapDetails.add(
                    GapDetail(
                        day = day,
                        startMin = previous.endMin,
                        endMin = current.startMin,
                        durationMins = gap,
                        fromClass = metricsNameOf(previous),
                        toClass = metricsNameOf(current),
                        fromTime = previous.end,
                        toTime = current.start,
                    )
                )
            }
            if (gap in 0..DASH_WINDOW_MIN) {
                val fromBlock = metricsBlockOfVenue(previous.venue)
                val toBlock = metricsBlockOfVenue(current.venue)
                if (metricsIsRealBlock(fromBlock) && metricsIsRealBlock(toBlock) && fromBlock != toBlock) {
                    buildingDashes++
                    dashDetails.add(
                        DashDetail(
                            fromClass = metricsNameOf(previous),
                            toClass = metricsNameOf(current),
                            fromTime = previous.end,
                            toTime = current.start,
                            day = day,
                            fromBlock = fromBlock,
                            toBlock = toBlock,
                        )
                    )
                }
            }
        }
        gapsPerDay[day] = dayGaps
        totalGaps += dayGaps
    }

    val social = socialScoreAgainst(metricCourses, friends)
    return CanonicalTimetableMetrics(
        halfDays = freeHalfDayCount,
        gapsMinutes = totalGaps,
        gapsPerDay = gapsPerDay,
        gapDetails = gapDetails,
        buildingDashes = buildingDashes,
        dashDetails = dashDetails,
        isLongWeekend = mondayFree || fridayFree,
        socialScore = social?.percentage,
        bestFriendMatches = social?.bestFriendMatches ?: emptyList(),
        freeHalfDays = freeHalfDays(metricCourses),
    )
}

/** Convenience for the legacy [AddedCourse] model used by FfcsEngine / VM / screens. */
fun canonicalMetricsFor(
    courses: Iterable<AddedCourse>,
    friends: Iterable<PlannerFriend> = emptyList(),
): CanonicalTimetableMetrics = canonicalMetrics(courses.map { it.asMetricCourse() }, friends)

/** Convenience for the canonical planner model. */
fun canonicalMetricsForPlanner(
    courses: Iterable<PlannerCourse>,
    friends: Iterable<PlannerFriend> = emptyList(),
): CanonicalTimetableMetrics = canonicalMetrics(courses.map { it.asMetricCourse() }, friends)

object FfcsMetrics {

    /**
     * Pre-existing public entry point — signature deliberately unchanged.
     *
     * The legacy [TimetableMetrics.gaps] field is whole hours, so it is adapted
     * from the canonical minute total at this boundary. Callers that display
     * `"${gaps}h"` and rank with `(20 - gaps) * 5` keep working, while the new
     * [canonicalMetrics] path keeps the exact minutes.
     */
    fun calculateTimetableMetrics(courses: List<AddedCourse>): TimetableMetrics =
        calculateTimetableMetrics(courses, emptyList())

    /**
     * Same legacy adapter, but with real friend data when the caller has it.
     * With no friends this is byte-for-byte the old path (socialScore = 0).
     */
    fun calculateTimetableMetrics(
        courses: List<AddedCourse>,
        friends: Iterable<PlannerFriend>,
    ): TimetableMetrics {
        val canonical = canonicalMetricsFor(courses, friends)
        return TimetableMetrics(
            halfDays = canonical.halfDays,
            gaps = canonical.gapsMinutes / 60,
            gapsPerDay = canonical.gapsPerDay.mapValues { (_, minutes) -> minutes / 60 },
            gapDetails = canonical.gapDetails,
            buildingDashes = canonical.buildingDashes,
            dashDetails = canonical.dashDetails,
            socialScore = canonical.socialScore ?: 0,
            isLongWeekend = canonical.isLongWeekend,
        )
    }
}
