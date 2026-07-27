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

    private data class CsvRecord(
        val code: String,
        val title: String,
        val slotStr: String,
        val facultyRaw: String
    )

    private var csvParsed = false
    private var csvRecords: List<CsvRecord> = emptyList()
    private val idRegex = Regex("""\(([^)]+)\)""")
    private val normalizeRegex1 = Regex("""\b(dr|prof|mr|mrs|ms)\.?\b""")
    private val normalizeRegex2 = Regex("""[.\s]+""")

    private fun ensureCsvParsed() {
        if (csvParsed) return
        val lines = FfcsReportData.CSV_DATA.replace("\r", "").split("\n").drop(1)
        val records = mutableListOf<CsvRecord>()
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
            val code = cols[0].trim().replace("\r", "")
            val title = cols[1].trim().replace("\r", "")
            val slotStr = cols[4].trim().replace("\r", "")
            val facultyRaw = cols[5].trim().replace("\r", "")
            if (slotStr.isEmpty() || slotStr.equals("NIL", ignoreCase = true)) continue
            records.add(CsvRecord(code, title, slotStr.trim(), facultyRaw))
        }
        csvRecords = records
        csvParsed = true
    }

    private fun matchesFaculty(csvFacultyRaw: String, faculty: FacultyProfile): Boolean {
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
                .replace(normalizeRegex1, "")
                .replace(normalizeRegex2, " ")
                .trim()
        }

        val normCsv = normalize(csvName)
        val normFaculty = normalize(faculty.name)
        if (normCsv.isEmpty() || normFaculty.isEmpty()) return false
        if (normCsv == normFaculty) return true

        if (normCsv.contains(normFaculty) && normCsv.length >= normFaculty.length * 3) return true
        if (normFaculty.contains(normCsv) && normFaculty.length >= normCsv.length * 3) return true

        val csvTokens = normCsv.split(" ").filter { it.length > 1 }
        val facultyTokens = normFaculty.split(" ").filter { it.length > 1 }
        if (csvTokens.isEmpty() || facultyTokens.isEmpty()) return false

        val common = csvTokens.intersect(facultyTokens.toSet()).size
        return common >= csvTokens.size / 2 && common >= facultyTokens.size / 2 && common > 0
    }

    fun getFacultySchedule(faculty: FacultyProfile): FacultyFreeSlotsResult {
        ensureCsvParsed()
        val occupied = mutableListOf<FacultySlot>()

        for (record in csvRecords) {
            if (!matchesFaculty(record.facultyRaw, faculty)) continue

            val slotCodes = record.slotStr.split("+").map { it.trim().uppercase() }.filter { it.isNotEmpty() }

            for (slotCode in slotCodes) {
                for ((day, daySlots) in SlotMap.map) {
                    val time = daySlots[slotCode] ?: continue
                    occupied.add(FacultySlot(
                        day = day,
                        timeRange = time,
                        courseCode = record.code,
                        courseTitle = record.title,
                        slotCode = slotCode
                    ))
                }
            }
        }

        val free = computeFreeSlots(occupied)
        return FacultyFreeSlotsResult(faculty.name, occupied, free)
    }

    fun getAllTimePeriods(): List<String> {
        val periods = mutableSetOf<String>()
        for (day in workingDays) {
            SlotMap.map[day]?.values?.let { periods.addAll(it) }
        }
        return periods.toList().sortedBy { time ->
            val start = time.substringBefore("-").trim()
            val hour = start.substringBefore(":").toIntOrNull() ?: 0
            val minute = start.substringAfter(":").toIntOrNull() ?: 0
            val adjustedHour = if (hour < 7) hour + 12 else hour
            adjustedHour * 60 + minute
        }
    }

    private fun computeFreeSlots(occupied: List<FacultySlot>): Map<String, List<String>> {
        val allPeriods = getAllTimePeriods()
        val result = mutableMapOf<String, MutableList<String>>()

        for (day in workingDays) {
            val dayOccupied = occupied.filter { it.day == day }
                .map { it.timeRange }
                .toSet()

            val free = allPeriods.filter { it !in dayOccupied }
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
