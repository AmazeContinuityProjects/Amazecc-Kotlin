package com.amazecc.app.shared.utils

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

expect suspend fun showDownloadCompleteNotification(fileName: String)

object NotificationsUtils {

    const val CLASS_REMINDER_ID_BASE = 1000
    const val ASSIGNMENT_REMINDER_ID_BASE = 2000
    const val EXAM_REMINDER_ID_BASE = 3000
    const val TASK_REMINDER_ID_BASE = 4000
    const val REG_REMINDER_ID_BASE = 5000
    const val TEST_NOTIFICATION_ID = 9999

    val scheduleableNotificationIds: List<Int> = buildList {
        addAll(CLASS_REMINDER_ID_BASE until ASSIGNMENT_REMINDER_ID_BASE)
        addAll(ASSIGNMENT_REMINDER_ID_BASE until EXAM_REMINDER_ID_BASE)
        addAll(EXAM_REMINDER_ID_BASE until TASK_REMINDER_ID_BASE)
        addAll(TASK_REMINDER_ID_BASE until REG_REMINDER_ID_BASE)
        addAll(REG_REMINDER_ID_BASE until TEST_NOTIFICATION_ID)
        add(TEST_NOTIFICATION_ID)
    }

    suspend fun scheduleClassReminders(
        attendance: List<Map<String, Any>>,
        slotMap: Map<String, Map<String, SlotInfo>>,
        offsetMinutes: Int = SettingsManager.getNotifOffsetMinutes(),
        calendar: CalendarRes? = com.amazecc.app.shared.state.AppState.calendar.value,
        examDays: Set<LocalDate> = emptySet()
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
            if (targetDate in examDays) continue // Whole exam day: no class reminders
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

    suspend fun scheduleExamReminders(
        exams: List<ExamItem>,
        offsetMinutes: Int = SettingsManager.getNotifOffsetMinutes()
    ) {
        if (!SettingsManager.isNotifExamRemindersEnabled()) return
        if (!requestNotificationPermissions()) return

        createNotificationChannels()
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        var id = EXAM_REMINDER_ID_BASE

        for (exam in ExamUtils.sortedExamDays(exams)) {
            val start = ExamUtils.examStartInstant(exam, tz) ?: continue
            val dateStr = exam.examDate.ifBlank { "TBD" }
            val timeStr = exam.reportingTime.ifBlank { exam.examTime }.ifBlank { "TBD" }
            val venuePart = if (exam.venue.isNotBlank()) " @ ${exam.venue}" else ""
            val seatPart = "Seat ${exam.seatNo.ifBlank { "-" }} (${exam.seatLocationDisplay})"

            val dayBefore = start.minus(24.hours)
            if (dayBefore > now) {
                scheduleLocalNotification(
                    id = id++,
                    title = "Exam Tomorrow",
                    body = "${exam.courseCode} · ${exam.courseTitle} — $dateStr, $timeStr$venuePart · $seatPart",
                    triggerTimeMs = dayBefore.toEpochMilliseconds()
                )
            }

            val reportTrigger = start.minus(offsetMinutes.minutes)
            if (reportTrigger > now) {
                val leadLabel = if (offsetMinutes >= 60) {
                    "in ${offsetMinutes / 60}h ${offsetMinutes % 60}m"
                } else {
                    "in $offsetMinutes min"
                }
                scheduleLocalNotification(
                    id = id++,
                    title = "Exam $leadLabel",
                    body = "${exam.courseCode} · ${exam.courseTitle} — report by $timeStr$venuePart · ${exam.sessionDisplay} · $seatPart",
                    triggerTimeMs = reportTrigger.toEpochMilliseconds()
                )
            }
        }
    }

    private val monthIndex = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    /** Parses registration dates like "27-Jun-2026" (also tolerates "-", "/", " " and "2026-06-27"). */
    fun parseRegistrationDate(raw: String): LocalDate? {
        val parts = raw.trim().substringBefore('T').split("-", "/", " ", ",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        val first = parts[0].toIntOrNull()
        val third = parts[2].toIntOrNull()
        val year: Int
        val month: Int
        val day: Int
        if (first != null && first in 2000..2100) {
            year = first
            val m = monthIndex[parts[1].lowercase().take(3)]
            if (m != null) { month = m; day = parts[2].toIntOrNull() ?: return null }
            else { month = parts[1].toIntOrNull() ?: return null; day = third ?: return null }
        } else {
            day = first ?: return null
            val m = monthIndex[parts[1].lowercase().take(3)]
            if (m != null) month = m
            else month = parts[1].toIntOrNull() ?: return null
            year = third ?: return null
        }
        return try { LocalDate(year, month, day) } catch (_: Exception) { null }
    }

    /** Parses "9:00:00 AM" style times into (hour, minute) 24h. */
    fun parseMeridiemTime(raw: String): Pair<Int, Int>? {
        val t = raw.trim().lowercase()
        val isPm = "pm" in t
        val hourPart = t.substringBefore(' ').substringBefore('m').trim()
        val nums = hourPart.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (nums.isEmpty()) return null
        var h = nums[0]
        val m = if (nums.size > 1) nums[1] else 0
        if (isPm && h != 12) h += 12
        if (!isPm && h == 12) h = 0
        return if (h in 0..23 && m in 0..59) h to m else null
    }

    fun registrationStartLocalDateTime(info: FfcsRegistrationInfo): LocalDateTime? {
        val d = parseRegistrationDate(info.date) ?: return null
        val (h, m) = parseMeridiemTime(info.fromTime) ?: return null
        return LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, h, m, 0, 0)
    }

    fun registrationStartInstant(info: FfcsRegistrationInfo, tz: TimeZone = TimeZone.currentSystemDefault()): Instant? =
        registrationStartLocalDateTime(info)?.toInstant(tz)

    suspend fun scheduleRegistrationReminders(info: FfcsRegistrationInfo) {
        if (info.date.isBlank()) return
        if (!requestNotificationPermissions()) return
        createNotificationChannels()
        val tz = TimeZone.currentSystemDefault()
        val start = registrationStartInstant(info, tz) ?: return
        val now = Clock.System.now()
        val dateStr = info.date
        val slot = buildString {
            if (info.fromTime.isNotBlank()) append(info.fromTime)
            if (info.toTime.isNotBlank()) {
                if (isNotEmpty()) append(" - ")
                append(info.toTime)
            }
        }
        var id = REG_REMINDER_ID_BASE
        val dayBefore = start.minus(24.hours)
        if (dayBefore > now) {
            scheduleLocalNotification(
                id = id++,
                title = "FFCS Registration Tomorrow",
                body = "Your course registration slot opens $dateStr ($slot). Be ready!",
                triggerTimeMs = dayBefore.toEpochMilliseconds()
            )
        }
        val dayOf = start.minus(1.hours)
        if (dayOf > now) {
            scheduleLocalNotification(
                id = id++,
                title = "FFCS Registration Today",
                body = "Registration opens today at ${info.fromTime.ifBlank { dateStr }} ($slot). Don't miss your slot!",
                triggerTimeMs = dayOf.toEpochMilliseconds()
            )
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

    /** Exams of the semester selected in the Exam Schedule dropdown, falling back to all synced semesters. */
    fun selectedSemesterExams(): List<ExamItem> {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val all = SettingsManager.getString(SettingsManager.CACHE_ALL_SEMESTER_EXAMS, "").let { raw ->
            if (raw.isBlank()) emptyMap()
            else try { json.decodeFromString<Map<String, ExamScheduleRes>>(raw) } catch (_: Exception) { emptyMap() }
        }
        val selectedId = com.amazecc.app.shared.state.AppState.selectedExamSemester.value
        val fromSelected = all[selectedId]?.schedule?.values?.flatten().orEmpty()
        if (fromSelected.isNotEmpty()) return fromSelected
        return all.values.mapNotNull { it }.flatMap { it.schedule.values.flatten() }
    }

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
        val exams = selectedSemesterExams()
        val registration = SettingsManager.getString(SettingsManager.CACHE_FFCS_REG_INFO, "").let { raw ->
            if (raw.isBlank()) null else try { json.decodeFromString<FfcsRegistrationInfo>(raw) } catch (_: Exception) { null }
        }
        if (attendanceItems == null && assignments == null && tasks.isEmpty() && exams.isEmpty() && registration == null) return
        scheduleAll(attendanceItems?.let { buildAttendanceMaps(it) }, typedSlotMap(), assignments, if (tasks.isEmpty()) null else tasks, if (exams.isEmpty()) null else exams, registration)
    }

    fun scheduleAll(
        attendance: List<Map<String, Any>>?,
        slotMap: Map<String, Map<String, SlotInfo>>?,
        assignments: List<LMSAssignment>?,
        tasks: List<HomeworkTask>? = null,
        exams: List<ExamItem>? = null,
        registration: FfcsRegistrationInfo? = null
    ) {
        // Alarms/permission work involves binder transactions — never on the main thread.
        CoroutineScope(Dispatchers.Default).launch {
            if (attendance != null && slotMap != null && attendance.isNotEmpty()) {
                scheduleClassReminders(attendance, slotMap, examDays = ExamUtils.examDates(exams.orEmpty()))
            }
            if (assignments != null && assignments.isNotEmpty()) {
                scheduleAssignmentReminders(assignments)
            }
            if (tasks != null && tasks.isNotEmpty()) {
                scheduleTaskReminders(tasks)
            }
            if (exams != null && exams.isNotEmpty()) {
                scheduleExamReminders(exams)
            }
            if (registration != null) {
                scheduleRegistrationReminders(registration)
            }
        }
    }
}
