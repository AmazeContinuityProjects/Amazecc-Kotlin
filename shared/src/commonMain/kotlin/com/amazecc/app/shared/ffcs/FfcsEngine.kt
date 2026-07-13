package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.min

object FfcsEngine {

    // Converts a list of options into precise day/min periods based on SlotMap
    private fun getPeriodsForCourse(course: ParsedCourse): List<Period> {
        val periods = mutableListOf<Period>()
        val slots = course.slot.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        
        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        days.forEach { day ->
            val dayMap = SlotMap.map[day] ?: emptyMap()
            slots.forEach { slot ->
                val timeStr = dayMap[slot]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        periods.add(Period(day, TimeMath.toMinutes(parts[0]), TimeMath.toMinutes(parts[1])))
                    }
                }
            }
        }
        return periods
    }

    private data class Period(val day: String, val startMin: Int, val endMin: Int)

    private val colors = listOf(
        "#EF4444", "#F97316", "#F59E0B", "#10B981",
        "#14B8A6", "#06B6D4", "#3B82F6", "#6366F1",
        "#8B5CF6", "#A855F7", "#D946EF", "#EC4899", "#E11D48"
    )

    suspend fun generateTimetables(
        optionsPerCourse: List<List<ParsedCourse>>,
        locks: List<CourseLock> = emptyList() // The new lock constraint
    ): List<TimetableState> = withContext(Dispatchers.Default) {
        val results = mutableListOf<List<ParsedCourse>>()
        val maxResults = 50

        // Filter options by locks
        val filteredOptions = optionsPerCourse.map { courseOptions ->
            if (courseOptions.isEmpty()) return@map emptyList()
            val code = courseOptions.first().code
            val lock = locks.find { it.code == code }
            if (lock != null) {
                courseOptions.filter { opt ->
                    val slotMatches = lock.allowedSlots.isEmpty() || lock.allowedSlots.contains(opt.slot)
                    val facultyMatches = lock.allowedFaculty.isEmpty() || lock.allowedFaculty.contains(opt.faculty)
                    slotMatches && facultyMatches
                }
            } else {
                courseOptions
            }
        }

        if (filteredOptions.any { it.isEmpty() }) {
            throw Exception("One or more courses have no available options (or all options were restricted by locks).")
        }

        val optionsWithPeriods = filteredOptions.map { options ->
            options.map { opt ->
                opt to getPeriodsForCourse(opt)
            }
        }

        fun backtrack(
            courseIndex: Int,
            currentCombo: MutableList<ParsedCourse>,
            currentPeriods: MutableList<Period>
        ) {
            if (results.size >= maxResults) return
            if (courseIndex == optionsWithPeriods.size) {
                results.add(currentCombo.toList())
                return
            }

            val options = optionsWithPeriods[courseIndex]
            for ((opt, periods) in options) {
                var hasConflict = false
                for (np in periods) {
                    for (ep in currentPeriods) {
                        if (np.day == ep.day && max(np.startMin, ep.startMin) < min(np.endMin, ep.endMin)) {
                            hasConflict = true
                            break
                        }
                    }
                    if (hasConflict) break
                }

                if (!hasConflict) {
                    currentCombo.add(opt)
                    val newPeriods = currentPeriods.toMutableList().apply { addAll(periods) }
                    backtrack(courseIndex + 1, currentCombo, newPeriods)
                    currentCombo.removeLast()
                }
            }
        }

        backtrack(0, mutableListOf(), mutableListOf())
        yield() // Allow UI to breathe if this took too long synchronously

        if (results.isEmpty()) {
            throw Exception("Could not generate any conflict-free timetables from the selected options.")
        }

        val generated = results.mapIndexed { idx, combo ->
            val mappedCourses = combo.mapIndexed { i, c ->
                AddedCourse(
                    id = "id_${c.code}_${i}",
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
                name = "Generated TT ${idx + 1}",
                courses = mappedCourses,
                metrics = metrics
            )
        }

        // Sort by most balanced (HalfDays vs Gaps)
        generated.sortedByDescending { tt ->
            (tt.metrics.halfDays * 10) + ((20 - tt.metrics.gaps) * 5)
        }
    }
}
