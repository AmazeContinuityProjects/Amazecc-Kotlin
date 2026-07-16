package com.amazecc.app.shared.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    val cls: String?
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

    fun getTodayAttendanceDay(): AttendanceDay {
        val currentMoment = Clock.System.now()
        val datetime = currentMoment.toLocalDateTime(TimeZone.currentSystemDefault())
        return datetime.dayOfWeek.toAttendanceDay()
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
                            cls = cls
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
        slotMap: Map<String, Map<String, SlotInfo>> = emptyMap()
    ): List<CourseAttendanceInfo> {
        val dayCardsMap = buildAttendanceDayCardsMap(attendance, slotMap)
        return dayCardsMap[getTodayAttendanceDay()] ?: emptyList()
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
