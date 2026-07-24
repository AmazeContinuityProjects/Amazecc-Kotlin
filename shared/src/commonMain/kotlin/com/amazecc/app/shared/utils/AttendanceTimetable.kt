package com.amazecc.app.shared.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parse attendance [viewLink] data which may arrive as:
 * - a [JsonArray] of {date, status} objects
 * - a [JsonObject] mapping date → status
 * - a JSON string (escaped) that needs an extra parse pass
 */
fun parseViewLink(viewLink: JsonElement?): JsonElement? {
    if (viewLink is JsonPrimitive && viewLink.isString) {
        return try {
            Json.parseToJsonElement(viewLink.content)
        } catch (_: Exception) { viewLink }
    }
    return viewLink
}

enum class AttendanceDay {
    MON, TUE, WED, THU, FRI, SAT, SUN
}

private fun DayOfWeek.toAttendanceDay(): AttendanceDay {
    return when (this) {
        DayOfWeek.SUNDAY -> AttendanceDay.SUN
        DayOfWeek.MONDAY -> AttendanceDay.MON
        DayOfWeek.TUESDAY -> AttendanceDay.TUE
        DayOfWeek.WEDNESDAY -> AttendanceDay.WED
        DayOfWeek.THURSDAY -> AttendanceDay.THU
        DayOfWeek.FRIDAY -> AttendanceDay.FRI
        DayOfWeek.SATURDAY -> AttendanceDay.SAT
        else -> AttendanceDay.MON
    }
}

data class TimeRange(val start: Int, val end: Int)

data class SlotInfo(val time: String)

data class CourseAttendanceInfo(
    val courseCode: String?,
    val courseTitle: String?,
    val courseType: String?,
    val faculty: String?,
    var slotName: String?,
    var time: String,
    val attendancePercentage: String?,
    val cls: String?,
    val venue: String? = null
)

object AttendanceTimetable {
    val ATTENDANCE_DAYS = AttendanceDay.entries.toList()

    fun parseAttendanceTime(timeStr: String): Int {
        val parts = timeStr.trim().split(":")
        var h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (h < 8) h += 12
        return h * 60 + m
    }

    fun getAttendanceTimeRange(time: String): TimeRange {
        val parts = time.split("-").map { it.trim() }
        val start = parts.getOrNull(0)?.let { parseAttendanceTime(it) } ?: 0
        val end = parts.getOrNull(1)?.let { parseAttendanceTime(it) } ?: 0
        return TimeRange(start, end)
    }

    fun parseMonthNumber(monthStr: String): Int? {
        val m = monthStr.trim().lowercase()
        val mInt = m.toIntOrNull()
        if (mInt != null && mInt in 1..12) return mInt
        return when (m.take(3)) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
            "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> null
        }
    }

    fun parseDayOrderFromText(text: String?): AttendanceDay? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        return when {
            lower.contains("monday day order") || lower.contains("day order: monday") || lower.contains("mon day order") -> AttendanceDay.MON
            lower.contains("tuesday day order") || lower.contains("day order: tuesday") || lower.contains("tue day order") -> AttendanceDay.TUE
            lower.contains("wednesday day order") || lower.contains("day order: wednesday") || lower.contains("wed day order") -> AttendanceDay.WED
            lower.contains("thursday day order") || lower.contains("day order: thursday") || lower.contains("thu day order") -> AttendanceDay.THU
            lower.contains("friday day order") || lower.contains("day order: friday") || lower.contains("fri day order") -> AttendanceDay.FRI
            lower.contains("saturday day order") || lower.contains("day order: saturday") || lower.contains("sat day order") -> AttendanceDay.SAT
            lower.contains("sunday day order") || lower.contains("day order: sunday") || lower.contains("sun day order") -> AttendanceDay.SUN
            else -> null
        }
    }

    fun getDayOrderOverrideForDate(date: kotlinx.datetime.LocalDate, calendar: com.amazecc.app.shared.model.CalendarRes?): AttendanceDay? {
        if (calendar == null) return null
        val monthNum = date.monthNumber
        val dayNum = date.dayOfMonth
        for (m in calendar.months) {
            val mNum = parseMonthNumber(m.month)
            if (mNum == monthNum) {
                for (d in m.days) {
                    if (d.date == dayNum) {
                        for (e in d.events) {
                            val overrideDay = parseDayOrderFromText(e.text)
                                ?: parseDayOrderFromText(e.category)
                                ?: parseDayOrderFromText(e.type)
                            if (overrideDay != null) return overrideDay
                        }
                    }
                }
            }
        }
        return null
    }

    fun getAttendanceDayForDate(date: kotlinx.datetime.LocalDate, calendar: com.amazecc.app.shared.model.CalendarRes?): AttendanceDay {
        val override = getDayOrderOverrideForDate(date, calendar)
        if (override != null) return override
        return date.dayOfWeek.toAttendanceDay()
    }

    fun getTodayAttendanceDay(calendar: com.amazecc.app.shared.model.CalendarRes? = null): AttendanceDay {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return getAttendanceDayForDate(now, calendar)
    }

    fun buildAttendanceDayCardsMap(
        attendance: List<Map<String, Any>> = emptyList(),
        slotMap: Map<String, Map<String, SlotInfo>> = emptyMap()
    ): Map<AttendanceDay, List<CourseAttendanceInfo>> {
        
        val map = mutableMapOf<AttendanceDay, MutableList<CourseAttendanceInfo>>()
        ATTENDANCE_DAYS.forEach { map[it] = mutableListOf() }

        attendance.forEach { course ->
            val slotsStr = (course["slotName"] as? String) ?: ""
            val slots = slotsStr.split("+").map { it.trim() }.filter { it.isNotEmpty() }

            slots.forEach { cleanSlot ->
                ATTENDANCE_DAYS.forEach { day ->
                    val dayStr = day.name
                    val info = slotMap[dayStr]?.get(cleanSlot)
                    if (info != null) {
                        val pctStr = (course["attendancePercentage"] as? String)?.replace("%", "") ?: "0"
                        val pct = pctStr.toIntOrNull() ?: 0
                        val cls = if (pct < 50) "low" else if (pct < 75) "medium" else "high"

                        val courseInfo = CourseAttendanceInfo(
                            courseCode = course["courseCode"] as? String,
                            courseTitle = course["courseTitle"] as? String,
                            courseType = course["courseType"] as? String,
                            faculty = course["faculty"] as? String,
                            slotName = cleanSlot,
                            time = info.time,
                            attendancePercentage = course["attendancePercentage"] as? String,
                            cls = cls,
                            venue = course["venue"] as? String
                        )
                        map[day]?.add(courseInfo)
                    }
                }
            }
        }

        val resultMap = mutableMapOf<AttendanceDay, List<CourseAttendanceInfo>>()

        ATTENDANCE_DAYS.forEach { day ->
            val dayList = map[day] ?: mutableListOf()
            
            dayList.sortWith(Comparator { a, b ->
                val timeA = getAttendanceTimeRange(a.time)
                val timeB = getAttendanceTimeRange(b.time)
                if (timeA.start != timeB.start) {
                    timeA.start.compareTo(timeB.start)
                } else {
                    (a.slotName ?: "").compareTo(b.slotName ?: "")
                }
            })

            val merged = mutableListOf<CourseAttendanceInfo>()
            for (current in dayList) {
                val previous = merged.lastOrNull()

                if (previous != null &&
                    previous.courseTitle == current.courseTitle &&
                    previous.courseType == current.courseType &&
                    previous.faculty == current.faculty &&
                    previous.cls == current.cls
                ) {
                    val previousRange = getAttendanceTimeRange(previous.time)
                    val currentRange = getAttendanceTimeRange(current.time)
                    val gapInMinutes = currentRange.start - previousRange.end

                    if (gapInMinutes in 0..5) {
                        previous.slotName = "${previous.slotName}+${current.slotName}"
                        val prevStart = previous.time.split("-").getOrNull(0) ?: ""
                        val currEnd = current.time.split("-").getOrNull(1) ?: ""
                        previous.time = "$prevStart-$currEnd"
                        continue
                    }
                }

                merged.add(current.copy())
            }

            merged.sortBy { parseAttendanceTime(it.time.split("-").firstOrNull() ?: "") }
            resultMap[day] = merged
        }

        return resultMap
    }

    fun getTodayAttendanceClasses(
        attendance: List<Map<String, Any>> = emptyList(),
        slotMap: Map<String, Map<String, SlotInfo>> = emptyMap(),
        calendar: com.amazecc.app.shared.model.CalendarRes? = null
    ): List<CourseAttendanceInfo> {
        val dayCardsMap = buildAttendanceDayCardsMap(attendance, slotMap)
        return dayCardsMap[getTodayAttendanceDay(calendar)] ?: emptyList()
    }

    fun currentTimeInMinutes(): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.hour * 60 + now.minute
    }

    fun findCurrentClass(classes: List<CourseAttendanceInfo>): CourseAttendanceInfo? {
        val now = currentTimeInMinutes()
        return classes.firstOrNull { cls ->
            val range = getAttendanceTimeRange(cls.time)
            now in range.start until range.end
        }
    }

    fun findNextClass(classes: List<CourseAttendanceInfo>): CourseAttendanceInfo? {
        val now = currentTimeInMinutes()
        return classes.firstOrNull { cls ->
            val range = getAttendanceTimeRange(cls.time)
            range.start > now
        }
    }

    fun remainingMinutes(timeRange: String): Int {
        val now = currentTimeInMinutes()
        val range = getAttendanceTimeRange(timeRange)
        return (range.end - now).coerceAtLeast(0)
    }

    fun minutesUntil(timeRange: String): Int {
        val now = currentTimeInMinutes()
        val range = getAttendanceTimeRange(timeRange)
        return (range.start - now).coerceAtLeast(0)
    }
}
