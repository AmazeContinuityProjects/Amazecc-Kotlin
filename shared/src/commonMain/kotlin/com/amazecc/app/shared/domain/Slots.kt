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
 * Every period a slot id names, under either spelling.
 */
fun periodsForSlot(schema: CampusSchema, slot: String): List<SlotPeriod> {
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
 */
fun slotsOverlap(theorySlotStr: String, labSlotStr: String, schema: CampusSchema): Boolean {
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
