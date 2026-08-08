package com.amazecc.app.shared.utils

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.json.Json

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

    const val CLASS_REMINDER_ID_BASE = 1000
    const val ASSIGNMENT_REMINDER_ID_BASE = 2000
    const val TASK_REMINDER_ID_BASE = 4000
    const val TEST_NOTIFICATION_ID = 9999

    val scheduleableNotificationIds: List<Int> = buildList {
        addAll(CLASS_REMINDER_ID_BASE until ASSIGNMENT_REMINDER_ID_BASE)
        addAll(ASSIGNMENT_REMINDER_ID_BASE until TASK_REMINDER_ID_BASE)
        addAll(TASK_REMINDER_ID_BASE until TEST_NOTIFICATION_ID)
        add(TEST_NOTIFICATION_ID)
    }

    suspend fun scheduleClassReminders(
        attendance: List<Map<String, Any>>,
        slotMap: Map<String, Map<String, SlotInfo>>,
        offsetMinutes: Int = SettingsManager.getNotifOffsetMinutes(),
        calendar: CalendarRes? = com.amazecc.app.shared.state.AppState.calendar.value
    ) {
        if (!SettingsManager.isNotifClassRemindersEnabled()) return
        if (!requestNotificationPermissions()) return

        createNotificationChannels()
        clearPendingNotifications()

        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        var id = CLASS_REMINDER_ID_BASE

        for (i in 0 until 7) {
            val targetDate = today.plus(DatePeriod(days = i))
            val attDay = AttendanceTimetable.getAttendanceDayForDate(targetDate, calendar)

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
        var id = ASSIGNMENT_REMINDER_ID_BASE

        for (a in assignments) {
            if (a.status == "Submitted" || a.dueDate.isBlank()) continue
            val dueInstant = parseDeadlineInstant(a.dueDate, tz) ?: continue

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
        var id = TASK_REMINDER_ID_BASE

        for (t in tasks) {
            if (t.completed) continue
            val (y, m, d) = parseDateComponents(t.dueDate) ?: continue

            val dueStart = LocalDateTime(y, m, d, 7, 0, 0, 0)
            val notifyInstant = dueStart.toInstant(tz).minus(offsetMinutes.toLong(), DateTimeUnit.MINUTE)
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

    private fun parseDateComponents(raw: String): Triple<Int, Int, Int>? {
        val p = raw.trim().substringBefore('T').substringBefore(' ').split("-").map { it.toIntOrNull() ?: return null }
        if (p.size < 3) return null
        return if (p[0] in 0..99 && p[2] in 2000..2100) Triple(p[2], p[1], p[0]) else Triple(p[0], p[1], p[2])
    }

    private fun parseDeadlineInstant(raw: String, tz: TimeZone): Instant? {
        val (dateStr, timeStr) = raw.trim().let { s ->
            when {
                'T' in s -> s.substringBefore('T') to s.substringAfter('T', "")
                ' ' in s -> s.substringBefore(' ') to s.substringAfter(' ', "")
                else -> s to ""
            }
        }
        val (y, m, d) = parseDateComponents(dateStr) ?: return null
        val hm = timeStr.split(":").map { it.toIntOrNull() ?: return null }
        val (hh, mm) = if (hm.size >= 2) hm[0] to hm[1] else 0 to 0
        if (y < 1900 || y > 2100 || m !in 1..12 || d !in 1..31 || hh !in 0..23 || mm !in 0..59) return null
        return try {
            LocalDateTime(y, m, d, hh, mm, 0, 0).toInstant(tz)
        } catch (_: Exception) { null }
    }

    fun buildAttendanceMaps(items: List<AttendanceItem>?): List<Map<String, Any>> =
        items?.map { item ->
            mapOf(
                "courseCode" to item.courseCode,
                "courseTitle" to item.courseTitle,
                "courseType" to item.courseType,
                "faculty" to item.faculty,
                "slotName" to (item.slotName ?: ""),
                "attendancePercentage" to item.attendancePercentage,
                "venue" to (item.slotVenue ?: "")
            )
        } ?: emptyList()

    fun typedSlotMap(): Map<String, Map<String, SlotInfo>> =
        SlotMap.map.mapValues { (_, inner) -> inner.mapValues { (_, time) -> SlotInfo(time) } }

    suspend fun rescheduleFromCache() {
        if (SettingsManager.getString(SettingsManager.SESSION_AUTHORIZED_ID, "").isBlank()) return
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val attendanceItems = SettingsManager.getString(SettingsManager.CACHE_ATTENDANCE, "").let { raw ->
            if (raw.isBlank()) null else try { json.decodeFromString<AttendanceRes>(raw).attendance } catch (_: Exception) { null }
        }
        val assignments = SettingsManager.getString(SettingsManager.CACHE_LMS, "").let { raw ->
            if (raw.isBlank()) null else try { json.decodeFromString<LMSRes>(raw).assignments } catch (_: Exception) { null }
        }
        val tasks = SettingsManager.getString(SettingsManager.CACHE_TASKS, "[]").let { raw ->
            if (raw.isBlank()) emptyList() else try { json.decodeFromString<List<HomeworkTask>>(raw) } catch (_: Exception) { emptyList() }
        }
        if (attendanceItems == null && assignments == null && tasks.isEmpty()) return
        scheduleAll(attendanceItems?.let { buildAttendanceMaps(it) }, typedSlotMap(), assignments, if (tasks.isEmpty()) null else tasks)
    }

    fun scheduleAll(
        attendance: List<Map<String, Any>>?,
        slotMap: Map<String, Map<String, SlotInfo>>?,
        assignments: List<LMSAssignment>?,
        tasks: List<HomeworkTask>? = null
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            if (attendance != null && slotMap != null && attendance.isNotEmpty()) {
                scheduleClassReminders(attendance, slotMap)
            }
            if (assignments != null && assignments.isNotEmpty()) {
                scheduleAssignmentReminders(assignments)
            }
            if (tasks != null && tasks.isNotEmpty()) {
                scheduleTaskReminders(tasks)
            }
        }
    }
}
