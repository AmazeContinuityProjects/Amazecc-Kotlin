package com.amazecc.app.shared.domain

import com.amazecc.app.shared.ui.m3e.components.StandardTimetablePeriods

/**
 * D-162 — pure derivation of real classroom occupancy from a [DomainSnapshot].
 *
 * The web free-classrooms page answers "which rooms are free in this block" from the FFCS
 * course report: one row per course, carrying a slot and a venue. The unit of the answer is the
 * BLOCK, not the room, and the report carries no per-room capacity. This file keeps that contract:
 * the only facts it produces come from real snapshot rows (venue strings from attendance), and
 * there is deliberately no capacity, seat count, or invented room anywhere in it.
 *
 * Everything here is total, deterministic and side-effect free: no clock, no I/O, no Compose.
 * Canonical helpers are reused rather than reimplemented:
 *  - [Projections.currentSemesterAttendance] / [Projections.mergedCourses] pick the semester that
 *    actually has data and flatten it to slot + venue rows;
 *  - [SlotMap] maps a slot to a time range and tells us which weekdays it meets on;
 *  - [StandardTimetablePeriods] is the single source of truth for the period list.
 */

/** The five teachable weekdays, matching the web page's DayId. No Sunday: the report has no Sunday slots. */
enum class FreeClassroomDay(val key: String, val label: String) {
    MON("MON", "Mon"),
    TUE("TUE", "Tue"),
    WED("WED", "Wed"),
    THU("THU", "Thu"),
    FRI("FRI", "Fri"),
    SAT("SAT", "Sat");

    companion object {
        fun fromKey(key: String?): FreeClassroomDay =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: MON
    }
}

/** One selectable period window, projected from the canonical timetable grid periods. */
data class FreeClassroomPeriodOption(
    val key: String,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val isLunch: Boolean = false
)

/**
 * One real room, seen in a real attendance row. [isFree] is occupancy for the *selected*
 * period; it is not a capacity and never will be.
 */
data class FreeClassroomRoomRow(
    val code: String,
    val block: String,
    val number: String,
    val isLab: Boolean,
    val isFree: Boolean,
    val courseCode: String = "",
    val courseTitle: String = "",
    val slot: String = "",
    val faculty: String = ""
)

/**
 * A block's room counts for the one filtered query that produced it: [total] is the number of
 * rooms actually visible in this block (not the whole day's room universe), so the "N of M free"
 * line can never contradict the chips beneath it.
 */
data class FreeClassroomBlockSummary(
    val block: String,
    val total: Int,
    val free: Int,
    val theoryFree: Int,
    val labFree: Int,
    val rooms: List<FreeClassroomRoomRow>,
    val freeRooms: List<FreeClassroomRoomRow>
) {
    /** Rounded, whole-number share of the block's real rooms that are free (0..100). */
    val percentFree: Int get() = if (total > 0) ((free * 100f) / total).toInt() else 0
}

/** Everything a screen needs for one (day, period) selection. */
data class FreeClassroomDerivationResult(
    val day: FreeClassroomDay,
    val period: FreeClassroomPeriodOption,
    /** Every real, non-placeholder venue in the snapshot — the room universe, never fabricated. */
    val allRooms: List<FreeClassroomRoomRow>,
    val freeRooms: List<FreeClassroomRoomRow>,
    val occupiedRooms: List<FreeClassroomRoomRow>,
    val blocks: List<FreeClassroomBlockSummary>
) {
    val totalRooms: Int get() = allRooms.size
    val freeCount: Int get() = freeRooms.size
}

object FreeClassroomDeriver {

    /** The period list, lifted verbatim from the canonical timetable grid. */
    fun periodOptions(): List<FreeClassroomPeriodOption> = StandardTimetablePeriods.map { column ->
        FreeClassroomPeriodOption(
            key = column.id,
            label = column.timeLabel,
            startMinute = column.startMinute,
            endMinute = column.endMinute,
            isLunch = column.isLunch
        )
    }

    /**
     * Derive occupancy for [dayKey] (MON..SAT) and [periodKey] from real snapshot rows.
     * An unknown period key falls back to the first period rather than blanking the page.
     *
     * The whole computation now runs through [FreeClassroomWeekIndex], so the block tiles and the
     * room inspector can never disagree about who is sitting in a room in a period. This overload
     * keeps the pure, snapshot-only API used by the JVM tests and any non-host caller.
     */
    fun derive(
        domain: DomainSnapshot,
        dayKey: String,
        periodKey: String
    ): FreeClassroomDerivationResult {
        val week = FreeClassroomWeekIndex.build(domain)
        val dayAnswer = week.day(dayKey)
        val answer = dayAnswer.period(periodKey)
        val allRooms = dayAnswer.rooms.values.map { info ->
            val occupant = answer.occupants[info.code]?.firstOrNull()
            FreeClassroomRoomRow(
                code = info.code,
                block = info.block,
                number = info.number,
                isLab = info.isLab,
                isFree = occupant == null,
                courseCode = occupant?.courseCode.orEmpty(),
                courseTitle = occupant?.title.orEmpty(),
                slot = occupant?.slot.orEmpty(),
                faculty = occupant?.faculty.orEmpty()
            )
        }.sortedWith(compareBy({ it.block }, { it.code }))

        val freeRooms = allRooms.filter { it.isFree }
        return FreeClassroomDerivationResult(
            day = dayAnswer.day,
            period = answer.period,
            allRooms = allRooms,
            freeRooms = freeRooms,
            occupiedRooms = allRooms.filter { !it.isFree },
            blocks = blockSummaries(allRooms)
        )
    }

    /** Lab venues from the snapshot's canonical roster; shared with the week index. */
    internal fun labVenuesFor(domain: DomainSnapshot, rows: List<CourseAttendance>): Set<String> {
        val semesterId = Projections.reportingSemester(domain)?.id
            ?: Projections.activeSemester(domain)?.id.orEmpty()
        val mergedTypesByCode = Projections.mergedCourses(domain, semesterId)
            .associate { Projections.canonicalCourseKey(it.code) to it.type }
        return rows.filter { row ->
            isLabRow(row.courseType, row.slotName, mergedTypesByCode[row.courseCode].orEmpty())
        }.mapNotNull { normalizeVenue(it.slotVenue) }.toSet()
    }

    /**
     * Group real rooms into blocks. [rooms] is the full room universe (free and occupied);
     * each row's [FreeClassroomRoomRow.isFree] decides the free counts. No capacity is consulted.
     */
    fun blockSummaries(rooms: List<FreeClassroomRoomRow>): List<FreeClassroomBlockSummary> =
        rooms.groupBy { it.block }.map { (block, blockRooms) ->
            val free = blockRooms.filter { it.isFree }
            FreeClassroomBlockSummary(
                block = block,
                total = blockRooms.size,
                free = free.size,
                theoryFree = free.count { !it.isLab },
                labFree = free.count { it.isLab },
                rooms = blockRooms.sortedBy { it.code },
                freeRooms = free.sortedBy { it.code }
            )
        }.sortedBy { it.block }

    // ── pure helpers ─────────────────────────────────────────────────────────

    /** Trim + uppercase; drop the placeholder venues VTOP uses for "no room". */
    internal fun normalizeVenue(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim().uppercase()
        if (value.isEmpty()) return null
        if (value.contains("ONLINE")) return null
        if (value in PLACEHOLDER_VENUES) return null
        if (value.all { it == '-' || it.isWhitespace() }) return null
        return value
    }

    private val PLACEHOLDER_VENUES = setOf("NIL", "N/A", "NA", "N.A.", "UNK-UNK", "-", "NULL", "TBA")

    /** Web parity: the block is the text before the first dash. */
    internal fun blockOf(code: String): String {
        val dash = code.indexOf('-')
        return if (dash >= 0) code.substring(0, dash).trim() else code
    }

    internal fun numberOf(code: String): String {
        val dash = code.indexOf('-')
        return if (dash >= 0) code.substring(dash + 1).trim() else code
    }

    /** VTOP's real vocabulary: "Lab Only" / "Embedded Lab", plus L-prefixed lab slot codes. */
    internal fun isLabRow(type: String, slot: String, mergedType: String): Boolean {
        if (type.contains("lab", ignoreCase = true)) return true
        if (mergedType.contains("lab", ignoreCase = true) && type.isBlank()) return true
        return splitSlots(slot).any { token ->
            token.length > 1 && token.startsWith("L", ignoreCase = true) && token[1].isDigit()
        }
    }

}
