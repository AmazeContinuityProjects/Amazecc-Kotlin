package com.amazecc.app.shared.domain

/**
 * Port of AmazeCC's `src/lib/slots.ts`.
 *
 * Theory vs Lab slot intervals, lunch period determination, slot spelling expansions
 * (e.g. `B1+TB1`, `L31+L32`), and slot clash/collision detection algorithm.
 */

data class SchemaPeriod(
    val start: String? = null,
    val end: String? = null,
    val lunch: Boolean = false,
    val days: Map<String, String>? = null,
)

data class CampusSchema(
    val theory: List<SchemaPeriod>? = null,
    val lab: List<SchemaPeriod>? = null,
)

val CHENNAI_CAMPUS_SCHEMA: CampusSchema = CampusSchema(
    theory = listOf(
        SchemaPeriod(start = "8:00 AM", end = "8:50 AM", days = mapOf("mon" to "A1", "tue" to "B1", "wed" to "C1", "thu" to "D1", "fri" to "E1")),
        SchemaPeriod(start = "8:55 AM", end = "9:45 AM", days = mapOf("mon" to "F1", "tue" to "G1", "wed" to "A1", "thu" to "B1", "fri" to "C1")),
        SchemaPeriod(start = "9:50 AM", end = "10:40 AM", days = mapOf("mon" to "D1", "tue" to "E1", "wed" to "F1", "thu" to "G1", "fri" to "TA1")),
        SchemaPeriod(start = "10:45 AM", end = "11:35 AM", days = mapOf("mon" to "TB1", "tue" to "TC1", "wed" to "TD1", "thu" to "TE1", "fri" to "TF1")),
        SchemaPeriod(start = "11:40 AM", end = "12:30 PM", days = mapOf("mon" to "TG1", "tue" to "TAA1", "wed" to "TBB1", "thu" to "TCC1", "fri" to "TDD1")),
        SchemaPeriod(start = "12:35 PM", end = "1:25 PM", days = mapOf("mon" to "S11", "fri" to "S15")),
        SchemaPeriod(lunch = true, start = "1:25 PM", end = "2:00 PM"),
        SchemaPeriod(start = "2:00 PM", end = "2:50 PM", days = mapOf("mon" to "A2", "tue" to "B2", "wed" to "C2", "thu" to "D2", "fri" to "E2")),
        SchemaPeriod(start = "2:55 PM", end = "3:45 PM", days = mapOf("mon" to "F2", "tue" to "G2", "wed" to "A2", "thu" to "B2", "fri" to "C2")),
        SchemaPeriod(start = "3:50 PM", end = "4:40 PM", days = mapOf("mon" to "D2", "tue" to "E2", "wed" to "F2", "thu" to "G2", "fri" to "TA2")),
        SchemaPeriod(start = "4:45 PM", end = "5:35 PM", days = mapOf("mon" to "TB2", "tue" to "TC2", "wed" to "TD2", "thu" to "TE2", "fri" to "TF2")),
        SchemaPeriod(start = "5:40 PM", end = "6:30 PM", days = mapOf("mon" to "TG2", "tue" to "TAA2", "wed" to "TBB2", "thu" to "TCC2", "fri" to "TDD2")),
        SchemaPeriod(start = "6:35 PM", end = "7:25 PM", days = mapOf("mon" to "S3", "tue" to "S1", "wed" to "S4", "thu" to "S2"))
    ),
    lab = listOf(
        SchemaPeriod(start = "08:00 AM", end = "08:50 AM", days = mapOf("mon" to "L1", "tue" to "L7", "wed" to "L13", "thu" to "L19", "fri" to "L25")),
        SchemaPeriod(start = "08:50 AM", end = "09:40 AM", days = mapOf("mon" to "L2", "tue" to "L8", "wed" to "L14", "thu" to "L20", "fri" to "L26")),
        SchemaPeriod(start = "09:50 AM", end = "10:40 AM", days = mapOf("mon" to "L3", "tue" to "L9", "wed" to "L15", "thu" to "L21", "fri" to "L27")),
        SchemaPeriod(start = "10:40 AM", end = "11:30 AM", days = mapOf("mon" to "L4", "tue" to "L10", "wed" to "L16", "thu" to "L22", "fri" to "L28")),
        SchemaPeriod(start = "11:40 AM", end = "12:30 PM", days = mapOf("mon" to "L5", "tue" to "L11", "wed" to "L17", "thu" to "L23", "fri" to "L29")),
        SchemaPeriod(start = "12:30 PM", end = "1:20 PM", days = mapOf("mon" to "L6", "tue" to "L12", "wed" to "L18", "thu" to "L24", "fri" to "L30")),
        SchemaPeriod(lunch = true, start = "1:20 PM", end = "2:00 PM"),
        SchemaPeriod(start = "2:00 PM", end = "2:50 PM", days = mapOf("mon" to "L31", "tue" to "L37", "wed" to "L43", "thu" to "L49", "fri" to "L55")),
        SchemaPeriod(start = "2:50 PM", end = "3:40 PM", days = mapOf("mon" to "L32", "tue" to "L38", "wed" to "L44", "thu" to "L50", "fri" to "L56")),
        SchemaPeriod(start = "3:50 PM", end = "4:40 PM", days = mapOf("mon" to "L33", "tue" to "L39", "wed" to "L45", "thu" to "L51", "fri" to "L57")),
        SchemaPeriod(start = "4:40 PM", end = "5:30 PM", days = mapOf("mon" to "L34", "tue" to "L40", "wed" to "L46", "thu" to "L52", "fri" to "L58")),
        SchemaPeriod(start = "5:40 PM", end = "6:30 PM", days = mapOf("mon" to "L35", "tue" to "L41", "wed" to "L47", "thu" to "L53", "fri" to "L59")),
        SchemaPeriod(start = "6:30 PM", end = "7:20 PM", days = mapOf("mon" to "L36", "tue" to "L42", "wed" to "L48", "thu" to "L54", "fri" to "L60"))
    )
)

val DAYS_OF_WEEK: List<String> = listOf("mon", "tue", "wed", "thu", "fri")

/**
 * `"8:00 AM"` -> 480. Returns null for anything unreadable so a bad schema
 * entry drops out of a lookup instead of sorting to the front of it.
 */
fun timeToMinutes(time: String?): Int? {
    val trimmed = time?.trim() ?: return null
    val regex = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?$""", RegexOption.IGNORE_CASE)
    val match = regex.find(trimmed) ?: return null

    var hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    val period = match.groupValues[3].uppercase()

    if (hours > 23 || minutes > 59) return null
    if (period.isEmpty()) return null // A 24h time is not in this schema.

    if (period == "PM" && hours != 12) hours += 12
    else if (period == "AM" && hours == 12) hours = 0

    return hours * 60 + minutes
}

/**
 * `"08:00 AM"` -> `"8:00am"`, `"8:00 AM"` -> `"8:00am"`.
 * The normalisation that makes lab and theory agree.
 */
fun normaliseTime(time: String?): String {
    if (time == null) return ""
    val total = timeToMinutes(time) ?: return time.trim().lowercase().replace(Regex("""\s+"""), "")
    val hours24 = (total / 60) % 24
    val mm = total % 60
    val suffix = if (hours24 < 12) "am" else "pm"
    val h12 = if (hours24 % 12 == 0) 12 else hours24 % 12
    val mmStr = mm.toString().padStart(2, '0')
    return "$h12:$mmStr$suffix"
}

/** The identity of a period, independent of zero-padding. */
fun periodKey(start: String, end: String): String =
    "${normaliseTime(start)}-${normaliseTime(end)}"

val LAW_SLOT_BY_S_SLOT: Map<String, String> = mapOf(
    "S11" to "TEE",
    "S15" to "TFF",
)

val NOT_A_LAW_SLOT: Set<String> = setOf("NIL", "S", "L")

fun normaliseSlot(slot: String?): String =
    slot?.trim()?.uppercase() ?: ""

/**
 * The law school's spelling of a schema slot id, or null when there is none.
 * `A1` -> `A`, `TC1` -> `TC`, `S11` -> `TEE`.
 */
fun lawSlotFor(schemaSlot: String?): String? {
    val id = normaliseSlot(schemaSlot)
    if (id.isEmpty()) return null
    LAW_SLOT_BY_S_SLOT[id]?.let { return it }

    val match = Regex("""^([A-Z]+)1$""").find(id) ?: return null
    val base = match.groupValues[1]
    if (base == "S" || base == "L") return null
    return base
}

/**
 * The schema's spelling of a law slot id, or null when there is none.
 * `A` -> `A1`, `TAA` -> `TAA1`, `TEE` -> `S11`.
 */
fun schemaSlotFor(lawSlot: String?): String? {
    val id = normaliseSlot(lawSlot)
    if (id.isEmpty() || !Regex("""^[A-Z]+$""").matches(id) || NOT_A_LAW_SLOT.contains(id)) return null
    for ((schemaSlot, law) in LAW_SLOT_BY_S_SLOT) {
        if (law == id) return schemaSlot
    }
    return "${id}1"
}

/**
 * Every id that names the same period as `slot`, `slot` itself included.
 */
fun slotSpellings(slot: String?): List<String> {
    val id = normaliseSlot(slot)
    if (id.isEmpty()) return emptyList()
    val spellings = mutableSetOf(id)
    lawSlotFor(id)?.let { spellings.add(it) }
    schemaSlotFor(id)?.let { spellings.add(it) }
    return spellings.toList()
}

/**
 * A set holding every spelling of every id given.
 */
fun expandSlotSpellings(slots: Iterable<String>): Set<String> {
    val out = mutableSetOf<String>()
    for (s in slots) {
        out.addAll(slotSpellings(s))
    }
    return out
}

/**
 * Does a course holding `slots` run in `slot`?
 */
fun hasSlot(slots: Iterable<String>, slot: String): Boolean {
    val wanted = slotSpellings(slot).toSet()
    if (wanted.isEmpty()) return false
    for (candidate in slots) {
        if (wanted.contains(normaliseSlot(candidate))) return true
    }
    return false
}

interface SlottedCourse {
    val slots: List<String>
}

data class SimpleSlottedCourse(override val slots: List<String>) : SlottedCourse

fun <T : SlottedCourse> courseInPeriod(courses: Iterable<T>, schemaSlot: String): T? {
    for (course in courses) {
        if (hasSlot(course.slots, schemaSlot)) return course
    }
    return null
}

fun <T : SlottedCourse> ownSlotSpelling(courses: Iterable<T>, schemaSlot: String): String {
    val course = courseInPeriod(courses, schemaSlot) ?: return schemaSlot
    return course.slots.firstOrNull { hasSlot(listOf(it), schemaSlot) } ?: schemaSlot
}

const val MORNING_BEFORE_MIN: Int = 840

/** One period of one day, as the schema writes it and as the clock reads it. */
data class SlotPeriod(
    val day: String,
    val start: String,
    val end: String,
    val startMin: Int,
    val endMin: Int,
    val type: String, // "theory" or "lab"
    val index: Int,
)

/**
 * Every period a slot id names, under either spelling (Law School or Chennai schema).
 */
fun periodsForSlot(schema: CampusSchema = CHENNAI_CAMPUS_SCHEMA, slot: String): List<SlotPeriod> {
    val wanted = slotSpellings(slot).toSet()
    if (wanted.isEmpty()) return emptyList()

    val found = mutableListOf<SlotPeriod>()

    fun collect(periods: List<SchemaPeriod>?, type: String) {
        periods?.forEachIndexed { index, period ->
            if (period.lunch || period.start == null || period.end == null || period.days == null) return@forEachIndexed
            for ((day, slotStr) in period.days) {
                if (slotStr.isEmpty()) continue
                val ids = slotStr.split(Regex("""[/+]""")).map { normaliseSlot(it) }.filter { it.isNotEmpty() }
                if (ids.none { wanted.contains(it) }) continue
                if (!DAYS_OF_WEEK.contains(day.lowercase())) continue
                val startMin = timeToMinutes(period.start)
                val endMin = timeToMinutes(period.end)
                if (startMin == null || endMin == null) continue
                found.add(SlotPeriod(day.lowercase(), period.start, period.end, startMin, endMin, type, index))
            }
        }
    }

    collect(schema.theory, "theory")
    collect(schema.lab, "lab")
    return found
}

fun periodsForSlot(slot: String): List<SlotPeriod> = periodsForSlot(CHENNAI_CAMPUS_SCHEMA, slot)

/** Split `"A1+TA1"` into its slot tokens, upper-cased and trimmed. */
fun splitSlots(slot: String?): List<String> {
    if (slot.isNullOrBlank()) return emptyList()
    return slot.split("+")
        .map { normaliseSlot(it) }
        .filter { it.isNotEmpty() }
}

/** Do two scheduled periods overlap in time on the same day? */
fun periodsOverlap(a: SlotPeriod, b: SlotPeriod): Boolean {
    return a.day == b.day && maxOf(a.startMin, b.startMin) < minOf(a.endMin, b.endMin)
}

/** Do any periods in list `a` collide with any periods in list `b`? */
fun periodListsClash(a: List<SlotPeriod>, b: List<SlotPeriod>): Boolean {
    for (x in a) {
        for (y in b) {
            if (periodsOverlap(x, y)) return true
        }
    }
    return false
}

/** Find the first pair of clashing periods between two lists, or null if clash-free. */
fun findPeriodClash(a: List<SlotPeriod>, b: List<SlotPeriod>): Pair<SlotPeriod, SlotPeriod>? {
    for (x in a) {
        for (y in b) {
            if (periodsOverlap(x, y)) return x to y
        }
    }
    return null
}

/**
 * Do a theory booking and a lab booking land on the clock at the same time?
 * Defaults to [CHENNAI_CAMPUS_SCHEMA].
 */
fun slotsOverlap(
    theorySlotStr: String,
    labSlotStr: String,
    schema: CampusSchema = CHENNAI_CAMPUS_SCHEMA
): Boolean {
    val theoryPeriods = splitSlots(theorySlotStr).flatMap { periodsForSlot(schema, it) }
    val labPeriods = splitSlots(labSlotStr).flatMap { periodsForSlot(schema, it) }
    return periodListsClash(theoryPeriods, labPeriods)
}

/**
 * Ensures a periods list contains a lunch spacer
 * positioned between the morning block and afternoon block.
 */
fun ensureLunchPeriod(periods: List<SchemaPeriod>): List<SchemaPeriod> {
    if (periods.isEmpty()) return emptyList()
    if (periods.any { it.lunch }) return periods.toList()

    val afternoonIdx = periods.indexOfFirst {
        val start = it.start ?: return@indexOfFirst false
        val mins = timeToMinutes(start)
        mins != null && mins >= 14 * 60
    }

    if (afternoonIdx != -1) {
        val next = periods.toMutableList()
        next.add(afternoonIdx, SchemaPeriod(lunch = true, start = "1:20 PM", end = "2:00 PM"))
        return next
    }

    return periods.toList()
}
