package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.min

object FfcsEngine {

    data class Period(val day: String, val startMin: Int, val endMin: Int)

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
        morningPreference: Boolean = false
    ): List<TimetableState> = withContext(Dispatchers.Default) {
        if (optionsPerCourse.isEmpty()) throw Exception("No courses selected for generation.")

        val results = mutableListOf<List<ParsedCourse>>()
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

        results.mapIndexed { idx, combo ->
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
            TimetableState(
                id = "tt_$idx",
                name = "TT ${idx + 1}",
                courses = mappedCourses,
                metrics = metrics
            )
        }.sortedByDescending { (it.metrics.halfDays * 10) + ((20 - it.metrics.gaps) * 5) }
    }
}
