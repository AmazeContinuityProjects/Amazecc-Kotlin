package com.amazecc.app.shared.utils

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.CGPA
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class AppWidgetClassEvent(
    val code: String,
    val title: String,
    val slot: String,
    val venue: String,
    val startMins: Int,
    val endMins: Int
)

data class AppWidgetScheduleData(
    val ongoing: AppWidgetClassEvent?,
    val next: AppWidgetClassEvent?,
    val totalToday: Int,
    val isDone: Boolean
)

data class AppWidgetStatsData(
    val overallPercentage: String,
    val cgpa: String,
    val earnedCredits: String,
    val odHours: String,
    val isSafe: Boolean
)

data class AppWidgetTaskItem(
    val id: String,
    val title: String,
    val courseCode: String,
    val dueDate: String,
    val priority: String,
    val subtasksProgress: String
)

data class AppWidgetRoomItem(
    val room: String,
    val type: String,
    val block: String
)

object WidgetDataUtils {

    fun getScheduleData(): AppWidgetScheduleData {
        val rawAttendance = SettingsManager.getString(SettingsManager.CACHE_ATTENDANCE, "")
        if (rawAttendance.isBlank()) return AppWidgetScheduleData(null, null, 0, false)

        val json = Json { ignoreUnknownKeys = true }
        val attendanceRes = try {
            json.decodeFromString<AttendanceRes>(rawAttendance)
        } catch (_: Exception) { null }

        val rawCalendar = SettingsManager.getString(SettingsManager.CACHE_CALENDAR, "")
        val calendarRes = try {
            if (rawCalendar.isNotBlank()) json.decodeFromString<com.amazecc.app.shared.model.CalendarRes>(rawCalendar) else null
        } catch (_: Exception) { null }

        val courses = attendanceRes?.attendance ?: emptyList()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMins = now.hour * 60 + now.minute

        // Determine effective day abbrev matching SlotMap keys ("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val dayOfWeek = try {
            com.amazecc.app.shared.utils.AttendanceTimetable.getTodayAttendanceDay(calendarRes).name
        } catch (_: Exception) {
            now.dayOfWeek.name.take(3).uppercase()
        }

        val dayMap = SlotMap.map[dayOfWeek] ?: emptyMap()
        val dayClasses = mutableListOf<AppWidgetClassEvent>()

        courses.forEach { course ->
            val slotStr = course.slotName
            val slots = slotStr.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            slots.forEach { slot ->
                val timeStr = dayMap[slot]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = TimeMath.toMinutes(parts[0])
                        val end = TimeMath.toMinutes(parts[1])
                        dayClasses.add(
                            AppWidgetClassEvent(
                                code = course.courseCode,
                                title = course.courseTitle,
                                slot = slot,
                                venue = course.slotVenue?.takeIf { it.isNotBlank() } ?: "N/A",
                                startMins = start,
                                endMins = end
                            )
                        )
                    }
                }
            }
        }
        dayClasses.sortBy { it.startMins }

        val ongoing = dayClasses.firstOrNull { currentMins in it.startMins..it.endMins }
        val next = dayClasses.firstOrNull { it.startMins > currentMins }
        val isDone = dayClasses.isNotEmpty() && ongoing == null && next == null

        return AppWidgetScheduleData(
            ongoing = ongoing,
            next = next,
            totalToday = dayClasses.size,
            isDone = isDone
        )
    }

    fun getAttendanceStats(): AppWidgetStatsData {
        val rawAttendance = SettingsManager.getString(SettingsManager.CACHE_ATTENDANCE, "")
        val rawGrades = SettingsManager.getString(SettingsManager.CACHE_GRADES, "")
        val json = Json { ignoreUnknownKeys = true }

        val attendanceRes = try {
            if (rawAttendance.isNotBlank()) json.decodeFromString<AttendanceRes>(rawAttendance) else null
        } catch (_: Exception) { null }

        val cgpaObj = try {
            if (rawGrades.isNotBlank()) json.decodeFromString<CGPA>(rawGrades) else null
        } catch (_: Exception) { null }

        val courses = (attendanceRes?.attendance ?: emptyList()).filter { it.totalClasses > 0 }
        val totalAttended = courses.sumOf { it.attendedClasses }
        val totalClasses = courses.sumOf { it.totalClasses }

        val overallPctNum = if (totalClasses > 0) (totalAttended.toDouble() / totalClasses.toDouble()) * 100.0 else 0.0
        val overallPctStr = if (totalClasses > 0) "${overallPctNum.toInt()}%" else "N/A"

        val cgpaStr = cgpaObj?.cgpa?.takeIf { it.isNotBlank() } ?: "N/A"
        val creditsStr = cgpaObj?.creditsEarned?.takeIf { it.isNotBlank() } ?: "N/A"

        return AppWidgetStatsData(
            overallPercentage = overallPctStr,
            cgpa = cgpaStr,
            earnedCredits = creditsStr,
            odHours = "0 hrs",
            isSafe = overallPctNum >= 75.0 || totalClasses == 0
        )
    }

    fun getUpcomingTasks(): List<AppWidgetTaskItem> {
        val rawTasks = SettingsManager.getString(SettingsManager.CACHE_TASKS, "[]")
        val json = Json { ignoreUnknownKeys = true }

        val tasks = try {
            json.decodeFromString(ListSerializer(HomeworkTask.serializer()), rawTasks)
        } catch (_: Exception) { emptyList() }

        val pending = tasks.filter { !it.completed }.sortedBy { it.dueDate }
        return pending.take(4).map { t ->
            val subtasksCount = t.subtasks.size
            val subtasksDone = t.subtasks.count { it.completed }
            val progressStr = if (subtasksCount > 0) "$subtasksDone/$subtasksCount" else ""
            AppWidgetTaskItem(
                id = t.id,
                title = t.title,
                courseCode = t.courseCode,
                dueDate = t.dueDate,
                priority = t.priority,
                subtasksProgress = progressStr
            )
        }
    }

    fun getFreeClassroomsSample(): List<AppWidgetRoomItem> {
        val rawAttendance = SettingsManager.getString(SettingsManager.CACHE_ATTENDANCE, "")
        val json = Json { ignoreUnknownKeys = true }
        val attendanceRes = try {
            if (rawAttendance.isNotBlank()) json.decodeFromString<AttendanceRes>(rawAttendance) else null
        } catch (_: Exception) { null }

        val courses = attendanceRes?.attendance ?: emptyList()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = now.dayOfWeek.name.take(3).uppercase()

        val occupiedRooms = mutableSetOf<String>()
        val dayMap = SlotMap.map[dayOfWeek] ?: emptyMap()
        val currentMins = now.hour * 60 + now.minute

        courses.forEach { c ->
            val slots = c.slotName.split("+").map { it.trim() }
            slots.forEach { s ->
                val timeStr = dayMap[s]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = TimeMath.toMinutes(parts[0])
                        val end = TimeMath.toMinutes(parts[1])
                        if (currentMins in start..end && !c.slotVenue.isNullOrBlank()) {
                            occupiedRooms.add(c.slotVenue.trim().uppercase())
                        }
                    }
                }
            }
        }

        val rooms = listOf(
            AppWidgetRoomItem("SJT 101", "Theory", "SJT"),
            AppWidgetRoomItem("SJT 204", "Theory", "SJT"),
            AppWidgetRoomItem("AB1 102", "Lab", "AB1"),
            AppWidgetRoomItem("TT 305", "Theory", "TT"),
            AppWidgetRoomItem("SMV 201", "Theory", "SMV")
        )

        return rooms.filter { it.room.uppercase() !in occupiedRooms }.take(3)
    }
}
