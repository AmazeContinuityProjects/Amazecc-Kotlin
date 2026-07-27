package com.amazecc.app.shared.utils

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.data.FfcsReportData
import com.amazecc.app.shared.model.FacultyProfile

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

    fun getFacultySchedule(faculty: FacultyProfile): FacultyFreeSlotsResult {
        val occupied = mutableListOf<FacultySlot>()

        val lines = FfcsReportData.CSV_DATA.replace("\r", "").split("\n").drop(1)
        for (line in lines) {
            if (line.isBlank()) continue

            var inQuotes = false
            val cols = mutableListOf<String>()
            val current = StringBuilder()
            for (char in line) {
                if (char == '\"') {
                    inQuotes = !inQuotes
                } else if (char == ',' && !inQuotes) {
                    cols.add(current.toString().trim())
                    current.clear()
                } else {
                    current.append(char)
                }
            }
            cols.add(current.toString().trim())

            if (cols.size < 6) continue

            val csvFacultyRaw = cols[5].trim().replace("\r", "")
            if (!matchesFaculty(csvFacultyRaw, faculty)) continue

            val code = cols[0].trim().replace("\r", "")
            val title = cols[1].trim().replace("\r", "")
            val slotStr = cols[4].trim().replace("\r", "")

            if (slotStr.isEmpty() || slotStr.equals("NIL", ignoreCase = true)) continue

            val slotCodes = slotStr.split("+").map { it.trim().uppercase() }.filter { it.isNotEmpty() }

            for (slotCode in slotCodes) {
                for ((day, daySlots) in SlotMap.map) {
                    val time = daySlots[slotCode] ?: continue
                    occupied.add(FacultySlot(
                        day = day,
                        timeRange = time,
                        courseCode = code,
                        courseTitle = title,
                        slotCode = slotCode
                    ))
                }
            }
        }

        val free = computeFreeSlots(occupied)
        return FacultyFreeSlotsResult(faculty.name, occupied, free)
    }

    private fun matchesFaculty(csvFacultyRaw: String, faculty: FacultyProfile): Boolean {
        val idRegex = Regex("""\(([^)]+)\)""")
        val idMatch = idRegex.find(csvFacultyRaw)
        val extractedId = idMatch?.groupValues?.getOrNull(1)?.trim()

        if (extractedId != null) {
            if (extractedId.equals(faculty.id, ignoreCase = true) ||
                extractedId.equals(faculty.employeeId, ignoreCase = true)
            ) return true
        }

        val csvName = csvFacultyRaw.replace(idRegex, "").trim()

        fun normalize(s: String): String {
            return s.lowercase()
                .replace(Regex("""\b(dr|prof|mr|mrs|ms)\.?\b"""), "")
                .replace(Regex("""[.\s]+"""), " ")
                .trim()
        }

        val normCsv = normalize(csvName)
        val normFaculty = normalize(faculty.name)
        if (normCsv.isEmpty() || normFaculty.isEmpty()) return false
        if (normCsv == normFaculty) return true

        // Containment — only when the longer string is substantially longer
        if (normCsv.contains(normFaculty) && normCsv.length >= normFaculty.length * 3) return true
        if (normFaculty.contains(normCsv) && normFaculty.length >= normCsv.length * 3) return true

        // Token overlap — exclude single-char tokens (initials are too common)
        val csvTokens = normCsv.split(" ").filter { it.length > 1 }
        val facultyTokens = normFaculty.split(" ").filter { it.length > 1 }
        if (csvTokens.isEmpty() || facultyTokens.isEmpty()) return false

        val common = csvTokens.intersect(facultyTokens.toSet()).size
        return common >= csvTokens.size / 2 && common >= facultyTokens.size / 2 && common > 0
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
