package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.ffcs.planner.RankingComponents
import com.amazecc.app.shared.ffcs.planner.RankingFactorsConfig
import com.amazecc.app.shared.ffcs.planner.RankingMode
import com.amazecc.app.shared.ffcs.planner.RankingPriority
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

// ── D-180 request vocabulary ────────────────────────────────────────────────

/** The five preference modes the web generator accepts. */
enum class FfcsPreference(val wire: String) {
    NONE("none"), MORNING("morning"), EVENING("evening"),
    COMPACT("compact"), SPREAD("spread");

    companion object {
        fun fromWire(value: String?): FfcsPreference =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: NONE
    }
}

/** Engine sort profiles (the web `SortBy` union). */
enum class FfcsSortBy(val wire: String) {
    SOCIAL("social"), HALFDAYS("halfdays"), COMPACTNESS("compactness"),
    BALANCED("balanced"), FACULTY("faculty");

    companion object {
        fun fromWire(value: String?): FfcsSortBy =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: BALANCED
    }
}

/**
 * A lock as the engine needs it: the legacy [CourseLock] only carries exact
 * offerings, whereas the web `CourseLock` also carries allowed slots/faculty.
 * All three lists are plain data; empty means "unrestricted".
 */
data class FfcsLock(
    val code: String,
    val title: String = code,
    val allowedSlots: List<String> = emptyList(),
    val allowedFaculty: List<String> = emptyList(),
    val offerings: List<String> = emptyList(),
)

/** A friend whose settled timetables can be used for social scoring / sync. */
data class FfcsFriend(
    val id: String,
    val name: String = id,
    val timetables: List<TimetableState> = emptyList(),
)

/**
 * The pure generation request (reference/04 §1.1). Unset ranking fields really
 * are `null`: exactly one ranking path runs per call, never two stacked.
 */
data class FfcsGenerationParams(
    val masterCourses: List<ParsedCourse> = emptyList(),
    val courseLocks: List<FfcsLock> = emptyList(),
    val blockedSlots: List<String> = emptyList(),
    val friends: List<FfcsFriend> = emptyList(),
    val preference: FfcsPreference = FfcsPreference.NONE,
    /** Web default is strict: only an explicit `false` enables soft theory preference. */
    val strictTheoryPreference: Boolean = true,
    val syncFriendClasses: Boolean = false,
    val maximizeFreeTimeFriends: List<String> = emptyList(),
    val uniqueFaculties: Boolean = false,
    val noLimit: Boolean = false,
    val removeHardResultLimit: Boolean = false,
    val resultCap: Int? = null,
    val minHalfDays: Int = 0,
    val sortBy: FfcsSortBy = FfcsSortBy.BALANCED,
    val minStartTime: String? = null,
    val maxEndTime: String? = null,
    val maxEndTimeByDay: Map<String, String> = emptyMap(),
    val separateLabDays: Boolean = false,
    val avoidConsecutiveLabs: Boolean = false,
    val lunchBreakSeparatesLabs: Boolean = false,
    val rankingMode: RankingMode? = null,
    val rankingPriorityOrder: List<RankingPriority>? = null,
    val rankingFactors: RankingFactorsConfig? = null,
    val facultyRatings: FacultyRatingLookup? = null,
    val facultyRatingsAvailable: Boolean = false,
    val facultyMetricWeights: FacultyMetricWeights = FacultyMetricWeights.DEFAULT,
    val facultyScoreFilters: FacultyScoreFilters = FacultyScoreFilters.DEFAULT,
    val facultyPriority: FacultyPriorityMap? = null,
)

// ── pure helpers ────────────────────────────────────────────────────────────

/** `"08:00"` → 480; malformed clocks return `null`, never midnight. */
private fun parse24HourToMinutes(time: String?): Int? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(time?.trim().orEmpty()) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    if (hours > 24 || minutes > 59) return null
    return hours * 60 + minutes
}

private fun periodsOfSlot(slot: String, schema: CampusSchema = FfcsSlotSchema.CHENNAI): List<SlotPeriod> =
    FfcsSlotSchema.splitSlots(slot).flatMap { FfcsSlotSchema.periodsForSlot(it, schema) }

private fun isMorningSlot(slot: String): Boolean {
    val periods = periodsOfSlot(slot)
    return periods.isEmpty() || periods.any { it.startMin < FfcsSlotSchema.MORNING_BEFORE_MIN }
}

private fun isEveningSlot(slot: String): Boolean {
    val periods = periodsOfSlot(slot)
    return periods.isEmpty() || periods.any { it.startMin >= FfcsSlotSchema.MORNING_BEFORE_MIN }
}

/** An embedded booking must carry a theory half AND a lab half. */
private fun isCompleteEmbeddedCourse(option: ParsedCourse): Boolean {
    val type = option.type.trim().uppercase()
    val embedded = type == "ETH" || type == "ELA" || type == "EPJ" ||
        type.contains("EMBEDDED") || type.contains("+")
    if (!embedded) return true
    val slots = FfcsSlotSchema.splitSlots(option.slot)
    val hasTheory = slots.any { !it.startsWith("L") && it != "NIL" }
    val hasLab = slots.any { it.startsWith("L") }
    return hasTheory && hasLab
}

/** Legacy plus modern offering identity (the legacy model has no ERP id). */
private fun offeringMatches(option: ParsedCourse, selected: List<String>): Boolean =
    selected.contains(option.offeringKey()) ||
        selected.contains("${option.faculty}|${option.slot}|${option.room}")

/**
 * The nine `optionsFor` filters in the exact shipped order (reference/04 §2):
 * allowed slots → allowed faculty → exact offerings → complete embedded →
 * morning/evening strict → blocked slots (both spellings) → time bounds →
 * friend sync → faculty-priority stable sort LAST. An offering rejected early
 * is never reconsidered later.
 */
private fun optionsFor(params: FfcsGenerationParams, lock: FfcsLock, all: List<ParsedCourse>): List<ParsedCourse> {
    val blocked = params.blockedSlots.map { it.trim().uppercase() }.toSet()
    var options = all.toList()

    // 1. Named slots.
    if (lock.allowedSlots.isNotEmpty()) {
        val allowed = lock.allowedSlots.map { it.trim().uppercase() }.toSet()
        options = options.filter { opt ->
            allowed.contains(opt.slot.trim().uppercase()) ||
                FfcsSlotSchema.splitSlots(opt.slot).any { allowed.contains(it) }
        }
    }
    // 2. Named faculty.
    if (lock.allowedFaculty.isNotEmpty()) {
        options = options.filter { opt -> lock.allowedFaculty.contains(opt.faculty) }
    }
    // 3. Exact offerings.
    if (lock.offerings.isNotEmpty()) {
        options = options.filter { opt -> offeringMatches(opt, lock.offerings) }
    }
    // 4. Embedded rows need both halves.
    options = options.filter(::isCompleteEmbeddedCourse)

    // 5. Morning-only / evening-only.
    if (params.preference == FfcsPreference.MORNING && params.strictTheoryPreference) {
        options = options.filter { opt ->
            val theory = FfcsSlotSchema.splitSlots(opt.slot).filter { !it.startsWith("L") && it != "NIL" }
            theory.isEmpty() || isMorningSlot(theory.first())
        }
    } else if (params.preference == FfcsPreference.EVENING && params.strictTheoryPreference) {
        options = options.filter { opt ->
            val theory = FfcsSlotSchema.splitSlots(opt.slot).filter { !it.startsWith("L") && it != "NIL" }
            theory.isEmpty() || isEveningSlot(theory.first())
        }
    }

    // 6. Blocked slots, through both canonical spellings (A also rules out A1).
    options = options.filter { opt ->
        FfcsSlotSchema.splitSlots(opt.slot).none { slot ->
            FfcsSlotSchema.slotSpellings(slot).any { spelling -> blocked.contains(spelling) }
        }
    }

    // 7. Global and per-day time bounds.
    val lower = params.minStartTime?.let { parse24HourToMinutes(it) } ?: 0
    val upper = params.maxEndTime?.let { parse24HourToMinutes(it) } ?: (24 * 60)
    val byDay = params.maxEndTimeByDay.mapNotNull { (day, time) ->
        val minutes = time?.let { parse24HourToMinutes(it) }
        if (minutes == null) null else day.lowercase() to minutes
    }.toMap()
    if (params.minStartTime != null || params.maxEndTime != null || byDay.isNotEmpty()) {
        options = options.filter { opt ->
            FfcsSlotSchema.splitSlots(opt.slot).all { slot ->
                periodsOfSlot(slot).all { period ->
                    val dayLimit = byDay[period.day]
                    period.startMin >= lower && period.endMin <= upper &&
                        (dayLimit == null || period.endMin <= dayLimit)
                }
            }
        }
    }

    // 8. Match a friend's exact offering — only when the friend has the course.
    if (params.syncFriendClasses) {
        val friendCourses = params.friends
            .flatMap { it.timetables }
            .flatMap { it.courses }
            .filter { it.code == lock.code }
        if (friendCourses.isNotEmpty()) {
            options = options.filter { opt ->
                val optSlots = FfcsSlotSchema.splitSlots(opt.slot).sorted().joinToString(",")
                friendCourses.any { fc ->
                    val theirSlots = fc.slots.map { it.trim().uppercase() }.sorted().joinToString(",")
                    opt.faculty == fc.faculty && optSlots == theirSlots
                }
            }
        }
    }

    // 9. Faculty ordering — ordering only, applied after every other filter.
    return sortOptionsByFacultyPriority(options, params.facultyPriority, lock.code) { it.faculty }
}

/** Precompute one offering's period window once, not at every DFS node. */
private fun periodsForOffering(offering: ParsedCourse, schema: CampusSchema): List<SlotPeriod> =
    FfcsSlotSchema.splitSlots(offering.slot).flatMap { FfcsSlotSchema.periodsForSlot(it, schema) }

/** One class meeting, as ranked by D-173 and scored by the metrics walk. */
private data class OccupiedClass(
    val startMin: Int,
    val endMin: Int,
    val start: String,
    val end: String,
    val code: String,
    val title: String,
    val venue: String,
)

private val ENGINE_DAYS: List<String> = listOf("mon", "tue", "wed", "thu", "fri")

private fun slotSpellingsOf(token: String): Set<String> = FfcsSlotSchema.slotSpellings(token)

private fun hasOwnSlot(slots: List<String>, slot: String): Boolean {
    val wanted = slotSpellingsOf(slot)
    if (wanted.isEmpty()) return false
    return slots.any { candidate -> wanted.contains(candidate.trim().uppercase()) }
}

private fun venueForHalf(venue: String, half: String): String {
    if (!venue.contains("/", ignoreCase = true) &&
        !venue.contains("embedded", ignoreCase = true)
    ) return venue
    val parts = venue.split("/")
    if (parts.size < 2) return venue
    return (if (half == "theory") parts[0] else parts[1]).trim()
}

/** What one week occupies on one day, with theory/lab read index-aligned. */
private fun dayOccupancy(
    courses: List<AddedCourse>,
    schema: CampusSchema,
    day: String,
): List<OccupiedClass> {
    val theory = schema.theory.filterNot { it.lunch }
    val lab = schema.lab.filterNot { it.lunch }
    val owned = courses.flatMap { it.slots }.flatMap { slotSpellingsOf(it) }.toSet()

    val classes = ArrayList<OccupiedClass>()
    theory.forEachIndexed { index, period ->
        if (period.start.isBlank() || period.end.isBlank()) return@forEachIndexed
        val startMin = parseTimeToMinutes(period.start) ?: return@forEachIndexed
        val endMin = parseTimeToMinutes(period.end) ?: return@forEachIndexed
        val labSlot = lab.getOrNull(index)?.days?.get(day)
        for (half in listOf("theory", "lab")) {
            val slot = if (half == "theory") period.days[day] else labSlot
            if (slot.isNullOrEmpty() || !owned.contains(slot)) continue
            val course = courses.firstOrNull { c -> c.slots.any { hasOwnSlot(listOf(it), slot) } }
            classes.add(
                OccupiedClass(
                    startMin = startMin,
                    endMin = endMin,
                    start = period.start,
                    end = period.end,
                    code = course?.code.orEmpty(),
                    title = course?.title.orEmpty(),
                    venue = venueForHalf(course?.venue.orEmpty(), half),
                )
            )
            break // reported once even if both halves are held
        }
    }
    return classes.sortedBy { it.startMin }
}

private fun freeHalfDayKeys(courses: List<AddedCourse>, schema: CampusSchema): Set<String> {
    val free = HashSet<String>()
    for (day in ENGINE_DAYS) {
        val classes = dayOccupancy(courses, schema, day)
        if (classes.none { it.startMin < FfcsSlotSchema.MORNING_BEFORE_MIN }) free.add("${day}_morning")
        if (classes.none { it.startMin >= FfcsSlotSchema.MORNING_BEFORE_MIN }) free.add("${day}_evening")
    }
    return free
}

/**
 * The web nine-field metrics contract. Gaps are MINUTES here (the legacy
 * [FfcsMetrics] reports hours); the engine uses this for ranking/sorting/filters.
 */
private fun timetableMetrics(courses: List<AddedCourse>, schema: CampusSchema): TimetableMetrics {
    val gapDetails = ArrayList<GapDetail>()
    val dashDetails = ArrayList<DashDetail>()
    val gapsPerDay = LinkedHashMap<String, Int>()
    var totalGaps = 0
    var buildingDashes = 0
    var freeHalfDays = 0
    var mondayFree = true
    var fridayFree = true

    for (day in ENGINE_DAYS) {
        val classes = dayOccupancy(courses, schema, day)
        if (classes.isNotEmpty()) {
            if (day == "mon") mondayFree = false
            if (day == "fri") fridayFree = false
        }
        if (classes.none { it.startMin < FfcsSlotSchema.MORNING_BEFORE_MIN }) freeHalfDays++
        if (classes.none { it.startMin >= FfcsSlotSchema.MORNING_BEFORE_MIN }) freeHalfDays++

        var dayGaps = 0
        for (i in 1 until classes.size) {
            val prev = classes[i - 1]
            val curr = classes[i]
            val gap = curr.startMin - prev.endMin
            if (gap > 5) {
                dayGaps += gap
                gapDetails.add(
                    GapDetail(
                        day = day, startMin = prev.endMin, endMin = curr.startMin, durationMins = gap,
                        fromClass = "${prev.code} (${prev.title})", toClass = "${curr.code} (${curr.title})",
                        fromTime = prev.end, toTime = curr.start,
                    )
                )
            }
            if (gap in 0..15) {
                val fromBlock = prev.venue.substringBefore("-").trim()
                val toBlock = curr.venue.substringBefore("-").trim()
                if (fromBlock.isNotEmpty() && fromBlock != "NIL" &&
                    toBlock.isNotEmpty() && toBlock != "NIL" && fromBlock != toBlock
                ) {
                    buildingDashes++
                    dashDetails.add(
                        DashDetail(
                            fromClass = "${prev.code} (${prev.title})", toClass = "${curr.code} (${curr.title})",
                            fromTime = prev.end, toTime = curr.start, day = day,
                            fromBlock = fromBlock, toBlock = toBlock,
                        )
                    )
                }
            }
        }
        gapsPerDay[day] = dayGaps
        totalGaps += dayGaps
    }

    return TimetableMetrics(
        halfDays = freeHalfDays,
        gaps = totalGaps,
        gapsPerDay = gapsPerDay,
        gapDetails = gapDetails,
        buildingDashes = buildingDashes,
        dashDetails = dashDetails,
        socialScore = 0,
        isLongWeekend = mondayFree || fridayFree,
    )
}

/** Overlap percentage against one friend timetable (60-slot legacy scale). */
private fun pairwiseSocialScore(
    mine: List<AddedCourse>,
    theirs: List<AddedCourse>,
    schema: CampusSchema,
): Int {
    val mySlots = mine.flatMap { it.slots }.map { it.trim().uppercase() }.toSet()
    val theirSlots = theirs.flatMap { it.slots }.map { it.trim().uppercase() }.toSet()
    val mutuallyFree = 60 - (mySlots + theirSlots).size
    val maxMutuallyFree = 60 - theirSlots.size
    val myCourseBySlot = LinkedHashMap<String, String>()
    mine.forEach { c -> c.slots.forEach { myCourseBySlot[it.trim().uppercase()] = c.code } }
    var sharedClasses = 0
    for ((slot, code) in myCourseBySlot) {
        if (theirs.any { c -> c.code == code && c.slots.any { hasOwnSlot(listOf(it), slot) } }) sharedClasses++
    }
    val maxSharedClasses = theirSlots.size
    val myFree = freeHalfDayKeys(mine, schema)
    val theirFree = freeHalfDayKeys(theirs, schema)
    val sharedHalfDays = theirFree.count { it in myFree }
    val actual = sharedClasses * 3 + mutuallyFree + sharedHalfDays * 5
    val max = maxSharedClasses * 3 + maxMutuallyFree + theirFree.size * 5
    return if (max > 0) ((actual.toDouble() / max.toDouble()) * 100.0).toInt() else 0
}

/** Best single-timetable match per scored friend, averaged (rounded). */
private fun socialScoreAgainst(
    courses: List<AddedCourse>,
    friends: List<FfcsFriend>,
    schema: CampusSchema,
): Pair<Int, List<String>> {
    if (friends.isEmpty()) return 0 to emptyList()
    var total = 0
    var bestOverall = -1
    val closest = ArrayList<String>()
    for (friend in friends) {
        if (friend.timetables.isEmpty()) continue
        var best = 0
        for (tt in friend.timetables) {
            best = maxOf(best, pairwiseSocialScore(courses, tt.courses, schema))
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
    val score = if (friends.isNotEmpty()) ((total.toDouble() / friends.size.toDouble())).toInt() else 0
    return score to closest.toList()
}

/** Feed D-173's pure ranking reader with one meeting list per active day. */
private fun rankingInputsFor(courses: List<AddedCourse>, schema: CampusSchema): RankingInputs {
    val days = ENGINE_DAYS.map { day ->
        dayOccupancy(courses, schema, day).map { ClassMeeting(it.startMin, it.endMin) }
    }
    return rankingInputs(days)
}

/** One call: read the week, then score it under a legacy profile. */
private fun rankCoursesFor(
    courses: List<AddedCourse>,
    schema: CampusSchema,
    mode: RankingMode?,
    facultyQuality: Double?,
): RankingScore = scoreRanking(rankingInputsFor(courses, schema).copy(facultyQuality = facultyQuality), mode)

/** Share of theory periods on the preferred side of the 2 PM boundary. */
private fun preferredTheoryScore(courses: List<AddedCourse>, preference: FfcsPreference): Double? {
    val theoryPeriods = courses.flatMap { course ->
        course.slots.flatMap { slot -> periodsOfSlot(slot).filter { it.type == "theory" } }
    }
    if (theoryPeriods.isEmpty()) return null
    val preferred = theoryPeriods.count { period ->
        if (preference == FfcsPreference.MORNING) {
            period.startMin < FfcsSlotSchema.MORNING_BEFORE_MIN
        } else {
            period.startMin >= FfcsSlotSchema.MORNING_BEFORE_MIN
        }
    }
    return preferred.toDouble() / theoryPeriods.size.toDouble() * 100.0
}

/** Preference fit 0..100, shared by the factor and legacy `time` paths. */
private fun timeScoreFrom(
    params: FfcsGenerationParams,
    inputs: RankingInputs?,
    courses: List<AddedCourse>,
    hasSoftTheoryPreference: Boolean,
): Double? {
    if (inputs == null) return null
    fun bounded(value: Double) = value.coerceIn(0.0, 1.0)
    val earlyStart = 1.0 - bounded((inputs.averageDayStartMinutes - 8 * 60.0) / (6 * 60.0))
    val lateFinish = bounded((inputs.averageDayEndMinutes - 12 * 60.0) / (7 * 60.0))
    return when (params.preference) {
        FfcsPreference.MORNING ->
            if (hasSoftTheoryPreference) preferredTheoryScore(courses, FfcsPreference.MORNING)
            else earlyStart * 100.0
        FfcsPreference.EVENING ->
            if (hasSoftTheoryPreference) preferredTheoryScore(courses, FfcsPreference.EVENING)
            else lateFinish * 100.0
        FfcsPreference.COMPACT ->
            if (inputs.activeDays > 0) inputs.compactDays.toDouble() / inputs.activeDays.toDouble() * 100.0 else 0.0
        FfcsPreference.SPREAD -> inputs.activeDays.toDouble() / 6.0 * 100.0
        FfcsPreference.NONE -> ((earlyStart + (1.0 - lateFinish)) / 2.0) * 100.0
    }
}

private fun balancedScore(metrics: TimetableMetrics): Double =
    metrics.halfDays * 10.0 + ((MAX_WEEKLY_GAP_MIN - metrics.gaps).toDouble() / 60.0) * 5.0 + metrics.socialScore

/** D-177 settled sort: strongest first, metric-less plans always last. */
private fun sortTimetables(options: List<FfcsOption>, by: FfcsSortBy): List<FfcsOption> {
    fun rank(option: FfcsOption) = balancedScore(option.timetable.metrics)
    return options.sortedWith { a, b ->
        val am = a.timetable.metrics
        val bm = b.timetable.metrics
        when (by) {
            FfcsSortBy.SOCIAL -> bm.socialScore.compareTo(am.socialScore)
            FfcsSortBy.HALFDAYS -> bm.halfDays.compareTo(am.halfDays)
            FfcsSortBy.COMPACTNESS -> am.gaps.compareTo(bm.gaps)
            FfcsSortBy.FACULTY -> (b.facultyScore ?: -1.0).compareTo(a.facultyScore ?: -1.0)
            FfcsSortBy.BALANCED -> rank(b).compareTo(rank(a))
        }
    }
}

/** Greedy faculty dedupe — an employee id separates people who share a name. */
private fun dedupeByFaculty(options: List<FfcsOption>): List<FfcsOption> {
    // The legacy AddedCourse has no employee id, so the report's faculty name
    // is the only identity available (the web uses `erp:<id>` when present).
    val used = HashMap<String, HashSet<String>>()
    return options.filter { option ->
        val courses = option.timetable.courses
        if (courses.any { used[it.code]?.contains(it.faculty) == true }) return@filter false
        for (course in courses) used.getOrPut(course.code) { HashSet() }.add(course.faculty)
        true
    }
}

/** Collapse weeks with the same physical slot multiset into Option/Variant. */
private fun groupVariants(options: List<FfcsOption>): List<FfcsOption> {
    val grouped = LinkedHashMap<String, FfcsOption>()
    for (option in options) {
        val signature = option.timetable.courses
            .flatMap { it.slots }
            .sorted()
            .joinToString("|")
        val existing = grouped[signature]
        if (existing != null) {
            val variant = option.copy(
                timetable = option.timetable.copy(name = "Variant ${existing.variants.size + 1}")
            )
            grouped[signature] = existing.copy(variants = existing.variants + variant)
        } else {
            val first = option.copy(timetable = option.timetable.copy(name = "Variant 1"))
            grouped[signature] = option.copy(variants = listOf(first))
        }
    }
    return grouped.values.mapIndexed { index, option ->
        option.copy(timetable = option.timetable.copy(name = "Option ${index + 1}"))
    }
}

/** Score one combination, wiring real metrics and exactly one ranking path. */
private fun scoreCombination(
    params: FfcsGenerationParams,
    combination: List<ParsedCourse>,
    scoredFriends: List<FfcsFriend>,
    schema: CampusSchema,
    id: String,
): FfcsOption {
    val courses = combination.mapIndexed { index, course ->
        AddedCourse(
            id = "${id}_${course.code}_$index",
            code = course.code,
            title = course.title,
            slots = FfcsSlotSchema.splitSlots(course.slot),
            faculty = course.faculty,
            venue = course.room,
            credits = course.credits,
            type = course.type,
            color = FfcsConstants.COLORS[index % FfcsConstants.COLORS.size],
        )
    }
    val base = timetableMetrics(courses, schema)
    val social = socialScoreAgainst(courses, scoredFriends, schema)
    val metrics = base.copy(socialScore = social.first)
    val facultySummary = if (params.facultyRatingsAvailable && params.facultyRatings != null) {
        facultyRankingSummary(
            courses.map { RatedCourseRef(it.code, it.faculty, null) },
            params.facultyRatings!!,
            params.facultyMetricWeights,
        )
    } else {
        null
    }

    val hasProfile = params.rankingMode != null
    val hasFacultyData = (facultySummary?.ratedFacultyCount ?: 0) > 0
    val hasSoftTheoryPreference = !params.strictTheoryPreference &&
        (params.preference == FfcsPreference.MORNING || params.preference == FfcsPreference.EVENING)
    val hasTimePreference = params.preference != FfcsPreference.NONE ||
        params.minStartTime != null || params.maxEndTime != null || params.maxEndTimeByDay.isNotEmpty()

    val timetable = TimetableState(
        id = id,
        name = "Generated Option",
        courses = courses,
        metrics = metrics,
        firstStartMin = courses.flatMap { it.slots }.flatMap { periodsOfSlot(it, schema) }
            .minOfOrNull { it.startMin } ?: 0,
        lastEndMin = courses.flatMap { it.slots }.flatMap { periodsOfSlot(it, schema) }
            .maxOfOrNull { it.endMin } ?: 0,
    )

    var rankingScore: Double? = null
    var breakdown: Map<String, Double> = emptyMap()
    var components: RankingComponents? = null

    if (params.rankingFactors != null) {
        // Factor path: a present config supersedes every legacy ranking field.
        val inputs = rankingInputsFor(courses, schema)
        val scores = rankingFactorScores(
            RankingFactorInputs(
                inputs = inputs,
                halfDays = metrics.halfDays.toDouble(),
                socialScore = metrics.socialScore.toDouble(),
                facultyQuality = facultySummary?.facultyScore,
                timeScore = if (hasTimePreference) timeScoreFrom(params, inputs, courses, hasSoftTheoryPreference) else null,
            )
        )
        val disabled = params.rankingFactors!!.disabled.toSet()
        val active = params.rankingFactors!!.order.filterNot { it in disabled }
        val composite = combineFactorScores(scores, active)
        rankingScore = composite.score
        breakdown = composite.breakdown
    } else if (hasProfile || hasFacultyData || hasSoftTheoryPreference) {
        // Legacy path: profile + faculty + sort + time, combined once.
        val inputs = if (hasTimePreference) rankingInputsFor(courses, schema) else null
        val ranked = params.rankingMode?.let { mode ->
            rankCoursesFor(courses, schema, mode, facultySummary?.facultyScore?.let { it / 100.0 })
        }
        val sortScore = when (params.sortBy) {
            FfcsSortBy.SOCIAL -> metrics.socialScore.coerceIn(0, 100).toDouble()
            FfcsSortBy.HALFDAYS -> (metrics.halfDays.toDouble() / 12.0 * 100.0).coerceIn(0.0, 100.0)
            FfcsSortBy.COMPACTNESS -> (100.0 - metrics.gaps.toDouble() / 1200.0 * 100.0).coerceIn(0.0, 100.0)
            FfcsSortBy.FACULTY -> facultySummary?.facultyScore ?: 0.0
            FfcsSortBy.BALANCED -> (balancedScore(metrics) / 3.2).coerceIn(0.0, 100.0)
        }
        components = RankingComponents(
            profile = ranked?.score
                ?: if (params.rankingMode == RankingMode.BEST_FACULTY) facultySummary?.facultyScore else null,
            faculty = facultySummary?.facultyScore,
            sort = sortScore,
            time = timeScoreFrom(params, inputs, courses, hasSoftTheoryPreference),
        )
        val composite = combineRankingScores(components, params.rankingPriorityOrder ?: RankingPriority.DEFAULT_ORDER)
        rankingScore = composite.score
        breakdown = composite.breakdown
    }

    return FfcsOption(
        timetable = timetable,
        rankingScore = rankingScore,
        rankingBreakdown = breakdown,
        facultyScore = facultySummary?.facultyScore,
        variants = emptyList(),
    )
}

object FfcsEngine {

    data class Period(val day: String, val startMin: Int, val endMin: Int)

    /**
     * Generation filters, mirroring the web app's FFCS generator preferences
     * (FFCSTimetableTab): [closeGaps] ranks gap-free timetables first, [lateStart]
     * prefers timetables whose first class starts later in the day, and
     * [earlyFinish] prefers timetables whose last class ends earlier.
     */
    data class GenerateOptions(
        val closeGaps: Boolean = false,
        val lateStart: Boolean = false,
        val earlyFinish: Boolean = false
    )

    fun getPeriodsForSlot(slotStr: String): List<Period> {
        val periods = mutableListOf<Period>()
        val slots = slotStr.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        for (day in SlotMap.days) {
            val dayMap = SlotMap.map[day] ?: emptyMap()
            for (s in slots) {
                val time = dayMap[s] ?: continue
                val parts = time.split("-")
                if (parts.size == 2) {
                    periods.add(Period(day, TimeMath.toMinutes(parts[0]), TimeMath.toMinutes(parts[1])))
                }
            }
        }
        return periods
    }

    /**
     * D-180 pure engine: filter options → precompute periods → bitmask walk →
     * score → result filters → sort → ranking sort → faculty dedupe → faculty
     * preference sort → group variants. Every expected failure is a typed
     * [FfcsEngineResult.Error]; nothing outside the arguments is read or
     * mutated, and there is no RNG seed.
     */
    suspend fun generate(
        params: FfcsGenerationParams,
        hooks: FfcsGenerationHooks = FfcsGenerationHooks(),
    ): FfcsEngineResult = withContext(Dispatchers.Default) {
        try {
            if (params.courseLocks.isEmpty()) {
                return@withContext FfcsEngineResult.Error(FfcsErrorCode.NO_COURSES_SELECTED)
            }

            val schema = FfcsSlotSchema.CHENNAI
            val byCode = LinkedHashMap<String, MutableList<ParsedCourse>>()
            for (course in params.masterCourses) {
                byCode.getOrPut(course.code.uppercase()) { ArrayList() }.add(course)
            }

            val optionsPerCourse = ArrayList<List<ParsedCourse>>()
            val windowsPerCourse = ArrayList<List<List<SlotPeriod>>>()
            val labPeriodsByOffering = LinkedHashMap<ParsedCourse, List<SlotPeriod>>()
            val consecutiveLabPairs = consecutiveLabPeriodPairs(schema, params.lunchBreakSeparatesLabs)

            for (lock in params.courseLocks) {
                val options = optionsFor(params, lock, byCode[lock.code.uppercase()].orEmpty())
                if (options.isEmpty()) {
                    return@withContext FfcsEngineResult.Error(FfcsErrorCode.NO_VALID_SLOTS, lock.code)
                }
                optionsPerCourse.add(options)
                val windows = options.map { offering ->
                    val periods = periodsForOffering(offering, schema)
                    labPeriodsByOffering[offering] = periods.filter { it.type == "lab" }
                    periods
                }
                windowsPerCourse.add(windows)
            }

            val walkRules = buildWalkRules(
                minHalfDays = params.minHalfDays,
                separateLabDays = params.separateLabDays,
                avoidConsecutiveLabs = params.avoidConsecutiveLabs,
                schema = schema,
                optionsPerCourse = optionsPerCourse,
                labPeriodsByOffering = labPeriodsByOffering,
                consecutiveLabPairs = consecutiveLabPairs,
            )

            val limit = resolveResultLimit(params.noLimit, params.removeHardResultLimit, params.resultCap)
            val batchSize = if (hooks.batchSize > 0) hooks.batchSize else DEFAULT_BATCH_SIZE
            val scoredFriends = if (params.maximizeFreeTimeFriends.isNotEmpty()) {
                params.friends.filter { it.id in params.maximizeFreeTimeFriends }
            } else {
                emptyList()
            }

            val accepted = ArrayList<FfcsOption>()
            var labDayRejected = false
            var consecutiveLabsRejected = false
            var facultyScoreRejected = false
            var halfDaysRejected = false
            var emitted = 0
            var idCounter = 0

            fun flush() {
                if (accepted.size <= emitted) return
                val batch = accepted.subList(emitted, accepted.size).toList()
                emitted = accepted.size
                hooks.onBatch?.invoke(batch)
            }

            val visit = object : WalkHooks {
                override fun visit(combination: List<ParsedCourse>) {
                    if (params.separateLabDays && labsShareADay(combination, labPeriodsByOffering)) {
                        labDayRejected = true
                        return
                    }
                    if (params.avoidConsecutiveLabs &&
                        labsAreConsecutive(combination, labPeriodsByOffering, consecutiveLabPairs)
                    ) {
                        consecutiveLabsRejected = true
                        return
                    }
                    val timetable = scoreCombination(
                        params, combination, scoredFriends, schema, "tt-${++idCounter}"
                    )
                    if (params.facultyScoreFilters != FacultyScoreFilters.DEFAULT &&
                        params.facultyRatingsAvailable && !params.facultyRatings.isNullOrEmpty() &&
                        timetable.facultyScore != null &&
                        !passesFacultyScoreFilters(
                            facultyRankingSummary(
                                timetable.timetable.courses.map { RatedCourseRef(it.code, it.faculty, null) },
                                params.facultyRatings.orEmpty(),
                                params.facultyMetricWeights,
                            ),
                            params.facultyScoreFilters,
                        )
                    ) {
                        facultyScoreRejected = true
                        return
                    }
                    if (timetable.timetable.metrics.halfDays < params.minHalfDays) {
                        halfDaysRejected = true
                        return
                    }
                    accepted.add(timetable)
                    if (accepted.size - emitted >= batchSize) flush()
                }
            }

            val walk = walkCombinations(
                options = optionsPerCourse,
                windows = windowsPerCourse,
                limit = limit,
                hooks = visit,
                rules = walkRules,
                tick = { nodes, found -> hooks.onProgress?.invoke(nodes, found, accepted.size) },
                shouldStop = hooks.shouldStop,
            )

            if (walk.labDayPruned) labDayRejected = true
            if (walk.consecutivePruned) consecutiveLabsRejected = true
            if (walk.halfDaysPruned) halfDaysRejected = true
            flush()

            if (walk.cancelled) {
                return@withContext FfcsEngineResult.Cancelled(groupVariants(accepted))
            }
            if (walk.found == 0 && !labDayRejected && !consecutiveLabsRejected && !halfDaysRejected) {
                return@withContext FfcsEngineResult.Error(FfcsErrorCode.NO_CONFLICT_FREE)
            }
            if (accepted.isEmpty()) {
                val code = when {
                    labDayRejected -> FfcsErrorCode.NO_SEPARATE_LABS
                    consecutiveLabsRejected -> FfcsErrorCode.NO_CONSECUTIVE_LABS
                    facultyScoreRejected && !halfDaysRejected -> FfcsErrorCode.BELOW_MIN_FACULTY_SCORES
                    else -> FfcsErrorCode.BELOW_MIN_HALF_DAYS
                }
                return@withContext FfcsEngineResult.Error(code)
            }

            var timetables = sortTimetables(accepted, params.sortBy)
            if (params.rankingFactors != null ||
                params.rankingMode != null ||
                timetables.any { (it.facultyScore ?: 0.0) > 0.0 } ||
                (!params.strictTheoryPreference &&
                    (params.preference == FfcsPreference.MORNING || params.preference == FfcsPreference.EVENING))
            ) {
                timetables = timetables.sortedWith { a, b ->
                    (b.rankingScore ?: -1.0).compareTo(a.rankingScore ?: -1.0)
                }
            }
            if (params.uniqueFaculties) timetables = dedupeByFaculty(timetables)
            if (hasFacultyPriority(params.facultyPriority)) {
                val preference = params.facultyPriority
                val scores = timetables.associateWith { option ->
                    timetablePreferenceScore(option.timetable.courses, preference) ?: 0.0
                }
                timetables = timetables.sortedWith { a, b ->
                    (scores[b] ?: 0.0).compareTo(scores[a] ?: 0.0)
                }
            }

            val grouped = groupVariants(timetables)
            FfcsEngineResult.Ok(
                timetables = grouped,
                capped = limit != Long.MAX_VALUE && walk.found.toLong() >= limit,
                truncated = grouped.size < accepted.size,
                found = walk.found,
            )
        } catch (e: CancellationException) {
            // Cooperative cancellation is not a solver failure.
            throw e
        } catch (e: Exception) {
            // Genuine solver crash — distinct from "nothing matched".
            FfcsEngineResult.Error(FfcsErrorCode.ERROR)
        }
    }

    /**
     * Backwards-compatible entry point. The signature, the legacy metrics and
     * the closeGaps/lateStart/earlyFinish comparator are deliberately kept:
     * `FfcsParityTest` and the ViewModel still speak this shape. The pure typed
     * surface is [generate]; this wrapper is only the adapter to it.
     */
    suspend fun generateTimetables(
        optionsPerCourse: List<List<ParsedCourse>>,
        locks: List<CourseLock> = emptyList(),
        blockedSlots: Set<String> = emptySet(),
        maxResults: Int = 50,
        uniqueFaculty: Boolean = false,
        morningPreference: Boolean = false,
        options: GenerateOptions = GenerateOptions()
    ): List<TimetableState> = withContext(Dispatchers.Default) {
        if (optionsPerCourse.isEmpty()) throw Exception("No courses selected for generation.")

        val lockMap = locks.associateBy { it.code.uppercase() }
        val effectiveLocks = optionsPerCourse.mapNotNull { courseOptions ->
            val code = courseOptions.firstOrNull()?.code ?: return@mapNotNull null
            val legacy = lockMap[code.uppercase()]
            FfcsLock(
                code = code,
                title = legacy?.title ?: code,
                offerings = legacy?.allowedOfferings.orEmpty(),
            )
        }
        val blocked = blockedSlots.mapNotNull { key ->
            // Keys are FACULTY|SLOT|ROOM (or a bare slot). Reduce to the bare slot so
            // the engine's slotSpellings comparison can actually match (same as the
            // typed path in FfcsViewModel.buildEngineParams).
            key.substringAfter('|', key)
                .substringBefore('|')
                .trim()
                .uppercase()
                .takeIf { it.isNotEmpty() }
        }

        val result = generate(
            FfcsGenerationParams(
                masterCourses = optionsPerCourse.flatten(),
                courseLocks = effectiveLocks,
                blockedSlots = blocked,
                uniqueFaculties = uniqueFaculty,
                resultCap = maxResults,
                preference = if (morningPreference) FfcsPreference.MORNING else FfcsPreference.NONE,
                strictTheoryPreference = true,
                sortBy = FfcsSortBy.BALANCED,
            )
        )

        val timetableStates = when (result) {
            is FfcsEngineResult.Ok -> result.timetables.map { option ->
                val mappedCourses = option.timetable.courses
                val metrics = FfcsMetrics.calculateTimetableMetrics(mappedCourses)
                val periodsAll = mappedCourses.flatMap { course ->
                    course.slots.flatMap { slot -> getPeriodsForSlot(slot) }
                }
                TimetableState(
                    id = option.timetable.id,
                    name = option.timetable.name,
                    courses = mappedCourses,
                    metrics = metrics,
                    firstStartMin = periodsAll.minOfOrNull { it.startMin } ?: 0,
                    lastEndMin = periodsAll.maxOfOrNull { it.endMin } ?: 0,
                )
            }
            is FfcsEngineResult.Cancelled -> emptyList()
            is FfcsEngineResult.Error ->
                throw Exception("${result.code.wire}${result.subjectCode?.let { ": $it" } ?: ""}")
        }

        timetableStates.sortedWith(
            compareByDescending<TimetableState> { if (options.closeGaps) it.metrics.gaps == 0 else false }
                .thenByDescending { (it.metrics.halfDays * 10) + ((20 - it.metrics.gaps) * 5) }
                .thenByDescending { if (options.lateStart) it.firstStartMin else 0 }
                .thenBy { if (options.earlyFinish) it.lastEndMin else Int.MAX_VALUE }
        )
    }

    /**
     * Builds an RFC 5545 ICS calendar covering the week containing the first class of the
     * timetable, one event per unique class period. Returns null when there is nothing to export.
     */
    fun buildFfcsIcs(courses: List<AddedCourse>): String? {
        if (courses.isEmpty()) return null
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz).date
        // kotlinx.datetime LocalDate here exposes plus(DatePeriod) (see AnalyzeCalendar.kt);
        // use a negative offset rather than minus(DatePeriod), which is not available.
        val daysSinceMonday = now.dayOfWeek.isoDayNumber - 1
        val monday = LocalDate.fromEpochDays(now.toEpochDays() - daysSinceMonday)

        fun dayStr(d: LocalDate): String =
            "${d.year}${d.monthNumber.toString().padStart(2, '0')}${d.dayOfMonth.toString().padStart(2, '0')}"
        fun stamp(m: Int): String = "${(m / 60).toString().padStart(2, '0')}${(m % 60).toString().padStart(2, '0')}00"
        fun escape(value: String): String =
            value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

        // RFC 5545 DTSTAMP is the UTC creation time, not the event's local start time.
        val utcNow = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val dtstamp = "${dayStr(utcNow.date)}T" +
            "${utcNow.hour.toString().padStart(2, '0')}" +
            "${utcNow.minute.toString().padStart(2, '0')}" +
            "${utcNow.second.toString().padStart(2, '0')}Z"

        val events = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (course in courses) {
            for (slot in course.slots) {
                for (period in getPeriodsForSlot(slot)) {
                    val eventDate = LocalDate.fromEpochDays(
                        monday.toEpochDays() + SlotMap.days.indexOf(period.day).coerceIn(0, SlotMap.days.size - 1)
                    )
                    val start = period.startMin.coerceIn(0, 1439)
                    val end = period.endMin.coerceAtLeast(start + 1).coerceAtMost(1439)
                    if (!seen.add("${course.id}|${period.day}|$start")) continue
                    events.add(
                        listOf(
                            "BEGIN:VEVENT",
                            "UID:${course.id}-${period.day}-$start@amazecc",
                            "DTSTAMP:$dtstamp",
                            "DTSTART:${dayStr(eventDate)}T${stamp(start)}",
                            "DTEND:${dayStr(eventDate)}T${stamp(end)}",
                            // Web parity: exportIcal.ts uses `${code} - ${title}` for SUMMARY.
                            "SUMMARY:${escape("${course.code} - ${course.title}")}",
                            "LOCATION:${escape(course.venue)}",
                            "DESCRIPTION:${escape(course.faculty)}",
                            "END:VEVENT"
                        ).joinToString("\r\n")
                    )
                }
            }
        }
        if (events.isEmpty()) return null
        return listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//AmazeCC//FFCS Timetable//EN")
            .plus(events)
            .plus("END:VCALENDAR")
            .joinToString("\r\n")
    }
}
