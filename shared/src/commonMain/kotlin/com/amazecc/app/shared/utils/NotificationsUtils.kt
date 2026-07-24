package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.*

expect suspend fun requestNotificationPermissions(): Boolean

expect suspend fun scheduleLocalNotification(
    id: Int,
    title: String,
    body: String,
    triggerTimeMs: Long
)

expect suspend fun clearPendingNotifications()

expect suspend fun createNotificationChannels()

expect suspend fun testLocalNotification()

object NotificationsUtils {

    suspend fun scheduleClassReminders(
        attendance: List<Map<String, Any>>,
        slotMap: Map<String, Map<String, SlotInfo>>,
        offsetMinutes: Int = SettingsManager.getNotifOffsetMinutes()
    ) {
        if (!SettingsManager.isNotifClassRemindersEnabled()) return
        if (!requestNotificationPermissions()) return

        createNotificationChannels()
        clearPendingNotifications()

        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        var id = 1000

        for (i in 0 until 7) {
            val targetDate = today.plus(DatePeriod(days = i))
            val dayOfWeek = targetDate.dayOfWeek
            val attDay = when (dayOfWeek) {
                DayOfWeek.MONDAY -> AttendanceDay.MON
                DayOfWeek.TUESDAY -> AttendanceDay.TUE
                DayOfWeek.WEDNESDAY -> AttendanceDay.WED
                DayOfWeek.THURSDAY -> AttendanceDay.THU
                DayOfWeek.FRIDAY -> AttendanceDay.FRI
                DayOfWeek.SATURDAY -> AttendanceDay.SAT
                DayOfWeek.SUNDAY -> AttendanceDay.SUN
                else -> continue
            }

            val dayCards = AttendanceTimetable.buildAttendanceDayCardsMap(attendance, slotMap)[attDay] ?: emptyList()
            for (c in dayCards) {
                val timeRange = AttendanceTimetable.getAttendanceTimeRange(c.time)
                val classStart = LocalDateTime(
                    targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth,
                    timeRange.start / 60, timeRange.start % 60, 0, 0
                )
                val notifyInstant = classStart.toInstant(tz).minus(offsetMinutes.toLong(), DateTimeUnit.MINUTE)
                if (notifyInstant > now) {
                    val timeStr = c.time.split("-").firstOrNull()?.trim() ?: ""
                    val venuePart = if (!c.venue.isNullOrBlank()) " in ${c.venue}" else ""
                    scheduleLocalNotification(
                        id = id++,
                        title = "Upcoming Class",
                        body = "${c.courseTitle} at $timeStr$venuePart (${c.courseType})",
                        triggerTimeMs = notifyInstant.toEpochMilliseconds()
                    )
                }
            }
        }
    }

    suspend fun scheduleAssignmentReminders(
        assignments: List<LMSAssignment>,
        offsetMinutes: Int = 120
    ) {
        if (!SettingsManager.isNotifAssignmentRemindersEnabled()) return
        if (!requestNotificationPermissions()) return

        createNotificationChannels()
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        var id = 2000

        for (a in assignments) {
            if (a.status == "Submitted" || a.dueDate.isBlank()) continue
            val dueInstant = try {
                val parts = a.dueDate.split(" ")
                if (parts.size >= 3) {
                    val dateParts = parts[0].split("-")
                    val timeParts = parts[1].split(":")
                    if (dateParts.size == 3 && timeParts.size >= 2) {
                        LocalDateTime(
                            dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt(),
                            timeParts[0].toInt(), timeParts[1].toInt(), 0, 0
                        ).toInstant(tz)
                    } else null
                } else null
            } catch (_: Exception) { null } ?: continue

            val notifyInstant = dueInstant.minus(offsetMinutes.toLong(), DateTimeUnit.MINUTE)
            if (notifyInstant > now) {
                scheduleLocalNotification(
                    id = id++,
                    title = "Assignment Due Soon",
                    body = "${a.title} (${a.courseCode}) — Due ${a.dueDate}",
                    triggerTimeMs = notifyInstant.toEpochMilliseconds()
                )
            }
        }
    }

    suspend fun scheduleTaskReminders(
        tasks: List<HomeworkTask>,
        offsetMinutes: Int = 60
    ) {
        if (!SettingsManager.isNotifTaskRemindersEnabled()) return
        if (!requestNotificationPermissions()) return

        createNotificationChannels()
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        var id = 4000

        for (t in tasks) {
            if (t.completed) continue
            val dueDate = try {
                val d = t.dueDate.split("-").map { s -> s.toInt() }
                LocalDate(d[0], d[1], d[2])
            } catch (_: Exception) { continue }

            val dueStart = LocalDateTime(dueDate.year, dueDate.monthNumber, dueDate.dayOfMonth, 7, 0, 0, 0)
            val notifyInstant = dueStart.toInstant(tz)
            if (notifyInstant > now) {
                val taskType = if (t.type == "exam") "Exam" else "Task"
                scheduleLocalNotification(
                    id = id++,
                    title = "$taskType Due Today",
                    body = "${t.title} — ${t.courseCode} (Due ${t.dueDate})",
                    triggerTimeMs = notifyInstant.toEpochMilliseconds()
                )
            }
        }
    }

    suspend fun scheduleVitolReminders(limit: String?, consumed: String?) {
        if (!SettingsManager.isNotifVitolRemindersEnabled()) return
        if (!requestNotificationPermissions()) return
        createNotificationChannels()

        val limitVal = limit?.toIntOrNull() ?: return
        val consumedVal = consumed?.toIntOrNull() ?: return
        val remaining = (limitVal - consumedVal).coerceAtLeast(0)
        val usagePercent = (consumedVal.toFloat() / limitVal.toFloat()) * 100f

        if (usagePercent >= 80f) {
            scheduleLocalNotification(
                id = 3001,
                title = "VITOL Limit Warning",
                body = "You have used $consumedVal / $limitVal (${usagePercent.toInt()}%) — only $remaining trips left",
                triggerTimeMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    fun scheduleAll(
        attendance: List<Map<String, Any>>?,
        slotMap: Map<String, Map<String, SlotInfo>>?,
        assignments: List<LMSAssignment>?,
        vitolLimit: String?,
        vitolConsumed: String?,
        tasks: List<HomeworkTask>? = null
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            if (attendance != null && slotMap != null && attendance.isNotEmpty()) {
                scheduleClassReminders(attendance, slotMap)
            }
            if (assignments != null && assignments.isNotEmpty()) {
                scheduleAssignmentReminders(assignments)
            }
            scheduleVitolReminders(vitolLimit, vitolConsumed)
            if (tasks != null && tasks.isNotEmpty()) {
                scheduleTaskReminders(tasks)
            }
        }
    }
}
