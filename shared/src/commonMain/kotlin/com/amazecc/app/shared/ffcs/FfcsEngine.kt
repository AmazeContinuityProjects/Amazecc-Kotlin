package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.min

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

    private val colors = listOf(
        "#2563EB", "#9333EA", "#10B981", "#DC2626", "#F59E0B",
        "#EC4899", "#4F46E5", "#14B8A6", "#F97316", "#0891B2",
        "#D946EF", "#84CC16", "#E11D48", "#7C3AED", "#0EA5E9",
        "#EAB308", "#16A34A", "#D63384"
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

    private fun periodsOverlap(a: List<Period>, b: List<Period>): Boolean {
        for (pa in a) {
            for (pb in b) {
                if (pa.day == pb.day && max(pa.startMin, pb.startMin) < min(pa.endMin, pb.endMin)) {
                    return true
                }
            }
        }
        return false
    }

    private fun slotInBlockedList(slotStr: String, blockedSlots: Set<String>): Boolean {
        val slots = slotStr.split("+").map { it.trim() }
        for (day in SlotMap.days) {
            for (s in slots) {
                if (blockedSlots.contains("$day|$s")) return true
            }
        }
        return false
    }

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

        val results = mutableListOf<List<ParsedCourse>>()
        val resultsPeriods = mutableListOf<List<Period>>()
        val lockMap = locks.associateBy { it.code.uppercase() }

        val filteredOptions = optionsPerCourse.map { courseOptions ->
            if (courseOptions.isEmpty()) return@map emptyList()
            val code = courseOptions.first().code.uppercase()
            val lock = lockMap[code]
            var opts = courseOptions

            if (lock != null && lock.allowedOfferings.isNotEmpty()) {
                opts = opts.filter { it.offeringKey() in lock.allowedOfferings }
            }

            if (blockedSlots.isNotEmpty()) {
                opts = opts.filter { !slotInBlockedList(it.slot, blockedSlots) }
            }

            opts
        }

        if (filteredOptions.any { it.isEmpty() }) {
            throw Exception("One or more courses have no available options after applying constraints.")
        }

        val optionsWithPeriods = filteredOptions.map { options ->
            options.map { opt -> opt to getPeriodsForSlot(opt.slot) }
        }

        fun backtrack(
            courseIndex: Int,
            currentCombo: MutableList<ParsedCourse>,
            currentPeriods: MutableList<Period>
        ) {
            if (results.size >= maxResults) return
            if (courseIndex == optionsWithPeriods.size) {
                if (uniqueFaculty) {
                    val faculties = currentCombo.map { it.faculty.uppercase() }
                    if (faculties.toSet().size != faculties.size) return
                }
                results.add(currentCombo.toList())
                resultsPeriods.add(currentPeriods.toList())
                return
            }

            val options = optionsWithPeriods[courseIndex]
            for ((opt, periods) in options) {
                if (morningPreference) {
                    val anyAfternoon = periods.any { it.startMin >= 840 }
                    if (anyAfternoon) continue
                }

                if (!periodsOverlap(periods, currentPeriods)) {
                    currentCombo.add(opt)
                    val newPeriods = currentPeriods.toMutableList().apply { addAll(periods) }
                    backtrack(courseIndex + 1, currentCombo, newPeriods)
                    currentCombo.removeLast()
                }
            }
        }

        backtrack(0, mutableListOf(), mutableListOf())
        yield()

        if (results.isEmpty()) {
            throw Exception("No conflict-free timetables found. Try removing some constraints.")
        }

        val timetableStates = results.mapIndexed { idx, combo ->
            val mappedCourses = combo.mapIndexed { i, c ->
                AddedCourse(
                    id = "tt${idx}_${c.code}_$i",
                    code = c.code,
                    title = c.title,
                    slots = c.slot.split("+").map { it.trim() },
                    faculty = c.faculty,
                    venue = c.room,
                    credits = c.credits,
                    type = c.type,
                    color = colors[i % colors.size]
                )
            }

            val metrics = FfcsMetrics.calculateTimetableMetrics(mappedCourses)
            val periodsAll = resultsPeriods[idx]
            TimetableState(
                id = "tt_$idx",
                name = "TT ${idx + 1}",
                courses = mappedCourses,
                metrics = metrics,
                firstStartMin = periodsAll.minOfOrNull { it.startMin } ?: 0,
                lastEndMin = periodsAll.maxOfOrNull { it.endMin } ?: 0
            )
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
