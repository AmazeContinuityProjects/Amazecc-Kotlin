package com.amazecc.app.shared.utils

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.SemesterData
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    /**
     * Total on-duty hours across all courses (lab = 2h, theory = 1h).
     *
     * The same formula as [AcademicDerivers.computeODHours], over the transport row shape rather
     * than the stored one. These two disagreed about lab courses — this one keyed off `slotName`
     * while that one also read the `(L)` code suffix — which is why the status vocabulary, the
     * multiplier and the lab test are now all shared.
     */
    fun computeODHours(courses: List<AttendanceItem>): Int =
        Projections.odHours(
            courses.map { course ->
                val odCount = course.logs.count { Projections.isOdStatus(it.status) }
                odCount to AcademicDerivers.isLabCourse(
                    courseCode = course.courseCode,
                    courseType = course.courseType,
                    slots = course.slotName.split("+").filter { it.isNotBlank() },
                )
            }
        )

    /**
     * Widget processes have no AppState, so the "current semester" is resolved
     * deterministically (see AcademicDerivers.resolveCurrentSemester). Falls back
     * to the app-state selected semester when available; self-heals to the real
     * current semester after the next app sync.
     */
    fun currentSemesterData(): SemesterData? =
        AcademicDerivers.resolveCurrentSemester(AppDataStore.academic.value)

    /** Resolves the current semester against a given snapshot (used by app-process widgets). */
    fun currentSemesterData(academic: AcademicData): SemesterData? =
        AcademicDerivers.resolveCurrentSemester(academic)

    fun getScheduleData(): AppWidgetScheduleData {
        AppDataStore.loadPersistedSnapshot()
        val sem = currentSemesterData() ?: return AppWidgetScheduleData(null, null, 0, false)
        val calendarRes = AppDataStore.calendar.value

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

        sem.courses.values.forEach { course ->
            course.slots.forEach { slot ->
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
                                venue = course.venue?.takeIf { it.isNotBlank() } ?: "N/A",
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
        AppDataStore.loadPersistedSnapshot()
        val sem = currentSemesterData()
        val courses = sem?.courses?.values ?: emptyList()

        val summary = Projections.summariseAttendance(
            courses.map { it.attendance?.let { att -> att.attendedClasses to att.totalClasses } ?: (0 to 0) },
        )
        val overallPctNum = summary.percentage.toDouble()
        val overallPctStr = if (summary.hasData) "${overallPctNum.toInt()}%" else "N/A"

        val cgpaStr = sem?.gpa?.takeIf { it.isNotBlank() } ?: "N/A"
        val earnedCredits = Projections.creditsEarned(courses.map { it.credits to (it.grade != null) })
        val creditsStr = if (earnedCredits > 0) earnedCredits.toString() else "N/A"

        val odHours = if (sem != null) AcademicDerivers.computeODHours(sem) else 0

        return AppWidgetStatsData(
            overallPercentage = overallPctStr,
            cgpa = cgpaStr,
            earnedCredits = creditsStr,
            odHours = "$odHours hrs",
            isSafe = overallPctNum >= 75.0 || !summary.hasData
        )
    }

    fun getUpcomingTasks(): List<AppWidgetTaskItem> {
        AppDataStore.loadPersistedSnapshot()
        val tasks = AppDataStore.tasks.value

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
        AppDataStore.loadPersistedSnapshot()
        val sem = currentSemesterData()
        val courses = sem?.courses?.values ?: emptyList()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = now.dayOfWeek.name.take(3).uppercase()

        val occupiedRooms = mutableSetOf<String>()
        val dayMap = SlotMap.map[dayOfWeek] ?: emptyMap()
        val currentMins = now.hour * 60 + now.minute

        courses.forEach { c ->
            c.slots.forEach { s ->
                val timeStr = dayMap[s]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = TimeMath.toMinutes(parts[0])
                        val end = TimeMath.toMinutes(parts[1])
                        if (currentMins in start..end && !c.venue.isNullOrBlank()) {
                            occupiedRooms.add(c.venue.trim().uppercase())
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
