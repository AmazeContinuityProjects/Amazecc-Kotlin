package com.amazecc.app.shared.utils

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem

data class FacultySlot(
    val day: String,
    val timeRange: String,
    val courseCode: String,
    val courseTitle: String,
    val slotCode: String
)

data class FacultyFreeSlotsResult(
    val facultyName: String,
    val occupiedSlots: List<FacultySlot>,
    val freeSlots: Map<String, List<String>>
)

object FacultyFreeSlotsUtil {

    private val dayOrder = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val dayLabels = mapOf(
        "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday",
        "THU" to "Thursday", "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday"
    )

    val workingDays = listOf("MON", "TUE", "WED", "THU", "FRI")
    val allTimeSlots: List<String> by lazy {
        val times = mutableSetOf<String>()
        for ((_, slots) in SlotMap.map) {
            for ((_, time) in slots) {
                times.add(time)
            }
        }
        times.sortedBy { TimeMath.toMinutes(it.split("-").getOrNull(0)) }
    }

    fun getFacultySchedule(facultyName: String, courses: List<AttendanceItem>): FacultyFreeSlotsResult {
        val occupied = mutableListOf<FacultySlot>()

        for (course in courses) {
            if (!course.faculty.equals(facultyName, ignoreCase = true)) continue
            val slotStr = course.slotName ?: continue
            val slotCodes = slotStr.split("+").map { it.trim() }.filter { it.isNotEmpty() }

            for (code in slotCodes) {
                for ((day, daySlots) in SlotMap.map) {
                    val time = daySlots[code] ?: continue
                    occupied.add(FacultySlot(
                        day = day,
                        timeRange = time,
                        courseCode = course.courseCode,
                        courseTitle = course.courseTitle,
                        slotCode = code
                    ))
                }
            }
        }

        val free = computeFreeSlots(occupied)

        return FacultyFreeSlotsResult(facultyName, occupied, free)
    }

    private fun computeFreeSlots(occupied: List<FacultySlot>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        for (day in workingDays) {
            val dayOccupied = occupied.filter { it.day == day }
                .map { it.timeRange }
                .toSet()

            // Standard college time periods (morning + afternoon sessions)
            val standardPeriods = listOf(
                "8:00-8:50", "8:55-9:45", "9:50-10:40", "10:45-11:35", "11:40-12:30",
                "2:00-2:50", "2:55-3:45", "3:50-4:40", "4:45-5:35"
            )

            val free = standardPeriods.filter { it !in dayOccupied }
            if (free.isNotEmpty()) {
                result[day] = free.toMutableList()
            }
        }

        return result
    }

    fun formatFreeSlotSummary(day: String, times: List<String>): String {
        if (times.isEmpty()) return "No free slots"
        return times.joinToString(", ")
    }

    fun getDayLabel(day: String): String = dayLabels[day] ?: day
}
