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

        if (!extractedId.isNullOrEmpty() && extractedId.any { it.isDigit() }) {
            if (extractedId.equals(faculty.id, ignoreCase = true) ||
                extractedId.equals(faculty.employeeId, ignoreCase = true)
            ) return true
            // An id that is present but differs means a different person - never fall through to name matching.
            return false
        }

        val csvName = csvFacultyRaw.replace(idRegex, "").trim()

        val normCsv = FacultyUtils.normalizeName(csvName)
        val normFaculty = FacultyUtils.normalizeName(faculty.name)
        if (normCsv.isEmpty() || normFaculty.isEmpty()) return false
        if (normCsv == normFaculty) return true

        // Match the name exactly or 95%+ (the VTOP name from the course matches the FFCS report)
        if (FacultyUtils.nameSimilarity(normCsv, normFaculty) >= 0.95) return true

        // CSV-quirk fallback: one name is fully contained in the other (extra title/suffix),
        // so people sharing a first name (e.g. "Karthik S" vs "Karthik R") never collapse.
        val csvTokens = normCsv.split(" ").filter { it.length > 1 }
        val facultyTokens = normFaculty.split(" ").filter { it.length > 1 }
        if (csvTokens.isEmpty() || facultyTokens.isEmpty()) return false

        val csvSet = csvTokens.toSet()
        val facultySet = facultyTokens.toSet()
        return csvSet.containsAll(facultySet) || facultySet.containsAll(csvSet)
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
}
