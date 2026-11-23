package com.amazecc.app.shared.domain

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.ffcs.planner.ParsedCourse
import com.amazecc.app.shared.ffcs.planner.splitSlots
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime

/**
 * D-191 — the real week index behind the free-classroom answer.
 *
 * The web reference (`lib/freeClassrooms.ts` + `lib/roomMerge.ts`) answers every question from
 * one index built once from the FFCS report: day → period → room → the classes actually sitting in
 * that room *in that period*. This file is that index, built from the two sources the signed-in
 * app really has:
 *
 *  - the [DomainSnapshot] (the user's own timetable, already synced), and
 *  - the FFCS catalogue rows already stored by the planner ([ParsedCourse]).
 *
 * Nothing here invents a room, a capacity, an AC flag or an occupant. Venue strings are normalised
 * with the deriver's canonical helper, slots are matched against [SlotMap] through the law-school
 * spellings it already knows, and an occupant is recorded only when its slot overlaps the period
 * window. The index is pure and deterministic so `jvmTest` can assert it without a clock, a
 * database or Compose.
 */

/** How many consecutive periods the "Free for 3 periods" answer spans (web `RUN_LENGTH`). */
const val FREE_CLASSROOM_RUN_LENGTH = 3

/** The five teachable days. Saturday is not a teaching day in the web page's DayId. */
val FREE_CLASSROOM_TEACHING_DAYS: List<FreeClassroomDay> =
    FreeClassroomDay.entries.filter { it != FreeClassroomDay.SAT }

/** Kind filter, mirrored in the M3E state model by `M3EFreeClassroomKindFilter`. */
enum class FreeClassroomKindFilter { ALL, THEORY, LAB }

/** A real room, seen in a real venue string. No capacity/AC field exists. */
data class FreeClassroomRoomInfo(
    val code: String,
    val block: String,
    val number: String,
    val isLab: Boolean
)

/**
 * One class sitting in one room in one period.
 *
 * [key] is stable for the lifetime of the index and unique across the week, so the class-details
 * overlay can be addressed by id instead of by a nullable mutable object.
 */
data class FreeClassroomOccupant(
    val key: String,
    val roomCode: String,
    val courseCode: String,
    val title: String,
    val faculty: String,
    val erpId: String? = null,
    val classId: String? = null,
    val slot: String = "",
    val type: String = "",
    val schoolId: String? = null,
    val source: String = "report"
)

/** One period of one day: who is in which room, and which rooms stay empty. */
data class FreeClassroomPeriodAnswer(
    val period: FreeClassroomPeriodOption,
    /** Room code → every occupant in *this* period (never the room's whole day). */
    val occupants: Map<String, List<FreeClassroomOccupant>>,
    val freeRoomCodes: List<String>
)

/** One day of the week index. */
data class FreeClassroomDayAnswer(
    val day: FreeClassroomDay,
    val periods: List<FreeClassroomPeriodAnswer>,
    val rooms: Map<String, FreeClassroomRoomInfo>,
    /** Every real block in the universe, sorted, for the single Filters sheet. */
    val blocks: List<String>
) {
    fun period(key: String): FreeClassroomPeriodAnswer =
        periods.firstOrNull { it.period.key == key } ?: periods.firstOrNull()
        ?: FreeClassroomPeriodAnswer(
            period = FreeClassroomPeriodOption(key, key, 0, 0),
            occupants = emptyMap(),
            freeRoomCodes = emptyList()
        )

    fun room(code: String): FreeClassroomRoomInfo? = rooms[code]
}

/** The whole teaching week, built once per domain/catalogue change. */
data class FreeClassroomWeek(
    val days: Map<FreeClassroomDay, FreeClassroomDayAnswer>,
    val periodOptions: List<FreeClassroomPeriodOption>
) {
    fun day(key: String): FreeClassroomDayAnswer =
        days[FreeClassroomDay.fromKey(key)]
            ?: days[FreeClassroomDay.MON]
            ?: FreeClassroomDayAnswer(FreeClassroomDay.MON, emptyList(), emptyMap(), emptyList())
}

/**
 * Strict clock answer. [day] is null on Saturday and Sunday — the old "Sunday → Monday" clamp
 * made a weekend page claim to be live on a day nobody was on campus. [period] is null outside a
 * real teaching window and during lunch.
 */
data class FreeClassroomNow(
    val day: FreeClassroomDay?,
    val period: FreeClassroomPeriodOption?,
    val isLive: Boolean,
    /** Real wall-clock text, e.g. `10:24 · Mon`; always present because it comes from a tick. */
    val clockLabel: String
)

/** Every filter, in one place. The host builds this once and passes it to [query]. */
data class FreeClassroomQuery(
    val day: FreeClassroomDay,
    val periodKey: String,
    val kind: FreeClassroomKindFilter,
    val building: String = "ALL",
    val block: String = "All",
    val search: String = "",
    val runOnly: Boolean = false,
    val onlyFree: Boolean = true
)

/** The ONE answer set behind the screen. [count] is the only number shown as a result count. */
data class FreeClassroomResult(
    val day: FreeClassroomDay,
    val period: FreeClassroomPeriodOption,
    val blocks: List<FreeClassroomBlockSummary>
) {
    val count: Int get() = blocks.sumOf { it.freeRooms.size }
}

/** One row of the room inspector's week timeline. */
data class FreeClassroomTimelineEntry(
    val periodKey: String,
    val label: String,
    val isFree: Boolean,
    val isSelected: Boolean,
    val occupants: List<FreeClassroomOccupant>
)

object FreeClassroomWeekIndex {

    /**
     * Build all five days in one pass per source.
     *
     * The room universe is the union of real venues from the domain snapshot and the FFCS
     * catalogue; an occupant recorded by either source occupies the room. Both sources are kept
     * in the details (the catalogue row wins the ordering because it carries ERP/class ids).
     */
    fun build(
        domain: DomainSnapshot,
        catalogue: List<ParsedCourse> = emptyList()
    ): FreeClassroomWeek {
        val periodOptions = FreeClassroomDeriver.periodOptions()
        val attendance = Projections.currentSemesterAttendance(domain)
        val labVenues = FreeClassroomDeriver.labVenuesFor(domain, attendance)

        val rooms = LinkedHashMap<String, FreeClassroomRoomInfo>()
        fun rememberRoom(rawVenue: String?, labHint: Boolean) {
            val code = FreeClassroomDeriver.normalizeVenue(rawVenue) ?: return
            val existing = rooms[code]
            if (existing == null) {
                rooms[code] = FreeClassroomRoomInfo(
                    code = code,
                    block = FreeClassroomDeriver.blockOf(code),
                    number = FreeClassroomDeriver.numberOf(code),
                    isLab = labHint
                )
            } else if (labHint && !existing.isLab) {
                rooms[code] = existing.copy(isLab = true)
            }
        }

        attendance.forEach { row ->
            rememberRoom(
                rawVenue = row.slotVenue,
                labHint = row.slotVenue?.let { FreeClassroomDeriver.normalizeVenue(it) in labVenues } == true
            )
        }
        catalogue.forEach { course ->
            rememberRoom(
                rawVenue = course.ROOM,
                labHint = FreeClassroomDeriver.isLabRow(course.TYPE, course.SLOT, "")
            )
        }

        val sortedRooms = rooms.values.sortedWith(compareBy({ it.block }, { it.code }))
        val universe = sortedRooms.associateBy { it.code }
        val blocks = sortedRooms.map { it.block }.distinct().sorted()

        val days = linkedMapOf<FreeClassroomDay, FreeClassroomDayAnswer>()
        for (day in FREE_CLASSROOM_TEACHING_DAYS) {
            val periods = mutableListOf<FreeClassroomPeriodAnswer>()
            var keyCounter = 0
            for (period in periodOptions) {
                val byRoom = LinkedHashMap<String, MutableList<FreeClassroomOccupant>>()

                fun addOccupant(
                    venue: String?,
                    slot: String,
                    courseCode: String,
                    title: String,
                    faculty: String,
                    erpId: String?,
                    classId: String?,
                    type: String,
                    schoolId: String?,
                    source: String
                ) {
                    val room = FreeClassroomDeriver.normalizeVenue(venue) ?: return
                    if (room !in universe) return
                    if (!meetsInPeriod(day.key, period, slot)) return
                    keyCounter++
                    val occupant = FreeClassroomOccupant(
                        key = "${day.key}|${period.key}|$room|$courseCode|$keyCounter",
                        roomCode = room,
                        courseCode = courseCode,
                        title = title,
                        faculty = faculty,
                        erpId = erpId?.takeIf { it.isNotBlank() },
                        classId = classId?.takeIf { it.isNotBlank() },
                        slot = slot,
                        type = type,
                        schoolId = schoolId?.takeIf { it.isNotBlank() },
                        source = source
                    )
                    val inRoom = byRoom.getOrPut(room) { mutableListOf() }
                    val identity = "${occupant.courseCode}|${occupant.slot}|${occupant.faculty}|${occupant.erpId}"
                    if (inRoom.none { "${it.courseCode}|${it.slot}|${it.faculty}|${it.erpId}" == identity }) {
                        inRoom.add(occupant)
                    }
                }

                attendance.forEach { row ->
                    addOccupant(
                        venue = row.slotVenue,
                        slot = row.slotName,
                        courseCode = row.courseCode,
                        title = row.courseTitle,
                        faculty = row.faculty,
                        erpId = null,
                        classId = null,
                        type = row.courseType,
                        schoolId = null,
                        source = "snapshot"
                    )
                }
                catalogue.forEach { course ->
                    addOccupant(
                        venue = course.ROOM,
                        slot = course.SLOT,
                        courseCode = course.CODE,
                        title = course.TITLE,
                        faculty = course.FACULTY,
                        erpId = course.ERP_ID,
                        classId = course.CLASS_ID,
                        type = course.TYPE,
                        schoolId = course.EMPLOYEE_SCHOOL,
                        source = "catalogue"
                    )
                }

                val occupants = byRoom.mapValues { it.value.sortedBy { o -> o.courseCode } }
                val free = if (period.isLunch) {
                    emptyList()
                } else {
                    universe.keys.filter { it !in occupants }.sortedWith(compareBy<String> { code ->
                        universe[code]?.block.orEmpty()
                    }.thenBy { it })
                }
                periods += FreeClassroomPeriodAnswer(period, occupants, free)
            }
            days[day] = FreeClassroomDayAnswer(day, periods, universe, blocks)
        }
        return FreeClassroomWeek(days, periodOptions)
    }

    /** A slot meets a period when its real time range overlaps the window, law spellings included. */
    private fun meetsInPeriod(dayKey: String, period: FreeClassroomPeriodOption, slot: String): Boolean {
        if (period.isLunch) return false
        val tokens = splitSlots(slot)
        if (tokens.isEmpty()) return false
        return tokens.any { token ->
            dayKey in SlotMap.daysForSlot(token) &&
                SlotMap.resolveRange(dayKey, token)?.let { (start, end) ->
                    start < period.endMinute && end > period.startMinute
                } == true
        }
    }

    /**
     * Resolve "now" strictly. Five minutes of grace at the start; lunch is never a live period;
     * weekends are never live and never silently remapped to Monday.
     */
    fun resolveNow(periods: List<FreeClassroomPeriodOption>, now: LocalDateTime): FreeClassroomNow {
        val day = when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> FreeClassroomDay.MON
            DayOfWeek.TUESDAY -> FreeClassroomDay.TUE
            DayOfWeek.WEDNESDAY -> FreeClassroomDay.WED
            DayOfWeek.THURSDAY -> FreeClassroomDay.THU
            DayOfWeek.FRIDAY -> FreeClassroomDay.FRI
            else -> null
        }
        val minute = now.hour * 60 + now.minute
        val period = if (day == null) null else periods.firstOrNull { p ->
            !p.isLunch && minute >= p.startMinute - 5 && minute <= p.endMinute
        }
        val hh = now.hour.toString().padStart(2, '0')
        val mm = now.minute.toString().padStart(2, '0')
        return FreeClassroomNow(
            day = day,
            period = period,
            isLive = day != null && period != null,
            clockLabel = "$hh:$mm · ${day?.label ?: "—"}"
        )
    }

    /** Periods from [periodKey] onwards, skipping lunch, capped at [length]. */
    fun runPeriods(
        dayAnswer: FreeClassroomDayAnswer,
        periodKey: String,
        length: Int = FREE_CLASSROOM_RUN_LENGTH
    ): List<FreeClassroomPeriodAnswer> {
        val start = dayAnswer.periods.indexOfFirst { it.period.key == periodKey }.coerceAtLeast(0)
        return dayAnswer.periods.drop(start).filter { !it.period.isLunch }.take(length)
    }

    /** Rooms free for the whole run — the intersection, not three separate answers. */
    fun freeCodesForRun(
        dayAnswer: FreeClassroomDayAnswer,
        periodKey: String,
        length: Int = FREE_CLASSROOM_RUN_LENGTH
    ): Set<String> {
        val run = runPeriods(dayAnswer, periodKey, length)
        if (run.isEmpty()) return emptySet()
        return run.first().freeRoomCodes.toSet()
            .let { acc -> run.drop(1).fold(acc) { kept, p -> kept intersect p.freeRoomCodes.toSet() } }
    }

    /** The non-lunch period with the most free rooms, or null when the day is fully booked. */
    fun bestPeriod(dayAnswer: FreeClassroomDayAnswer): Pair<FreeClassroomPeriodOption, Int>? =
        dayAnswer.periods.filter { !it.period.isLunch }
            .map { it.period to it.freeRoomCodes.size }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }

    /**
     * The single-track answer. Every filter narrows this one result; there is no second list,
     * no second count and no per-room card track behind it.
     */
    fun query(week: FreeClassroomWeek, q: FreeClassroomQuery): FreeClassroomResult {
        val dayAnswer = week.day(q.day.key)
        val periodAnswer = dayAnswer.period(q.periodKey)
        val freeCodes = if (q.runOnly) {
            freeCodesForRun(dayAnswer, q.periodKey)
        } else {
            periodAnswer.freeRoomCodes.toSet()
        }
        val needle = q.search.trim()
        val visibleBlocks = LinkedHashMap<String, MutableList<FreeClassroomRoomRow>>()

        dayAnswer.rooms.values.forEach { info ->
            if (q.block != "All" && q.block != info.block) return@forEach
            if (q.building != "ALL" && q.building != info.block) return@forEach
            when (q.kind) {
                FreeClassroomKindFilter.THEORY -> if (info.isLab) return@forEach
                FreeClassroomKindFilter.LAB -> if (!info.isLab) return@forEach
                FreeClassroomKindFilter.ALL -> Unit
            }
            if (needle.isNotBlank() &&
                !info.code.contains(needle, ignoreCase = true) &&
                !info.number.contains(needle, ignoreCase = true)
            ) return@forEach
            val isFree = info.code in freeCodes
            if (q.onlyFree && !isFree) return@forEach
            visibleBlocks.getOrPut(info.block) { mutableListOf() } += FreeClassroomRoomRow(
                code = info.code,
                block = info.block,
                number = info.number,
                isLab = info.isLab,
                isFree = isFree
            )
        }

        val blocks = visibleBlocks.map { (block, rooms) ->
            val freeRooms = rooms.filter { it.isFree }
            FreeClassroomBlockSummary(
                block = block,
                // The denominator is the SAME filtered room set the chips are drawn from. Counting
                // the whole day here made "1 of 10 free" sit above a single visible chip.
                total = rooms.size,
                free = freeRooms.size,
                theoryFree = freeRooms.count { !it.isLab },
                labFree = freeRooms.count { it.isLab },
                rooms = rooms.sortedBy { it.code },
                freeRooms = freeRooms.sortedBy { it.code }
            )
        }.filter { if (q.onlyFree) it.free > 0 else it.rooms.isNotEmpty() }
            .sortedWith(compareByDescending<FreeClassroomBlockSummary> { it.free }.thenBy { it.block })

        return FreeClassroomResult(dayAnswer.day, periodAnswer.period, blocks)
    }

    /**
     * The room inspector's own week view. [inspectDay] is deliberately independent of the selected
     * day: browsing a room's Thursday never yanks the free-room list away from Monday.
     */
    fun timeline(
        week: FreeClassroomWeek,
        inspectDayKey: String,
        roomCode: String,
        selectedDayKey: String,
        selectedPeriodKey: String
    ): List<FreeClassroomTimelineEntry> {
        val dayAnswer = week.day(inspectDayKey)
        return dayAnswer.periods.filter { !it.period.isLunch }.map { answer ->
            val occupants = answer.occupants[roomCode].orEmpty()
            FreeClassroomTimelineEntry(
                periodKey = answer.period.key,
                label = answer.period.label,
                isFree = occupants.isEmpty(),
                isSelected = inspectDayKey == selectedDayKey && answer.period.key == selectedPeriodKey,
                occupants = occupants
            )
        }
    }
}
