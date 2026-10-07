package com.amazecc.app.shared.domain

import kotlinx.datetime.LocalDate

/**
 * The port of `AmazeCC/src/lib/calendarDay.ts` §Attendance, §The day log and §OD summary.
 *
 * Three things here are load-bearing and are pinned by tests rather than trusted:
 *
 *  - [statusBucket] counts an unrecognised status as *present*, which is what the grid has always
 *    shown, so an unexpected VTOP status cannot silently turn into an absence.
 *  - [buildAttendanceLog] keeps two different ideas of "missed" apart. `missedClasses` includes
 *    on-duty (there are no notes for it, which is what the filter counts); the day's *verdict*
 *    counts only absences, because an on-duty is an approved absence. Collapsing the two is what
 *    used to render "absent 8am lab + on-duty 2pm theory" as a wholly absent day and throw away
 *    the morning/evening split that makes the row worth reading.
 *  - [resolveTrackedOds] is an *assignment*, not a lookup: each tracked entry claims at most one
 *    OD record and each record is claimed at most once, with an exact title match outranking a
 *    substring one. Containment alone double-counted the hours of an embedded course, because
 *    "Biology" is a substring of "Biology Lab".
 */

/** Which counter of [DayAttendance] a status increments. */
enum class AttendanceBucket { PRESENT, ABSENT, ON_DUTY }

/**
 * Which counter this status belongs to.
 *
 * Anything VTOP has not told us about before counts as present - a status that fell through to
 * "absent" would be inventing an absence.
 */
internal fun statusBucket(status: String?): AttendanceBucket = when (status.orEmpty().lowercase()) {
    "present" -> AttendanceBucket.PRESENT
    "absent" -> AttendanceBucket.ABSENT
    "on duty", "partial od" -> AttendanceBucket.ON_DUTY
    else -> AttendanceBucket.PRESENT
}

fun emptyAttendance(): DayAttendance = DayAttendance()

/** Mutable twin of [DayAttendance]; `courses` stays a list so freeze order is stable. */
private class AttendanceAcc {
    var held = 0
    var present = 0
    var absent = 0
    var onDuty = 0
    val courses = mutableListOf<DayClassRecord>()

    fun freeze(): DayAttendance = DayAttendance(
        held = held,
        present = present,
        absent = absent,
        onDuty = onDuty,
        courses = courses.toList(),
    )
}

/**
 * `attendance[].viewLink[]` collapsed to one record per calendar day.
 *
 * Keyed by [dateKey], the same string tasks, Moodle deadlines and OD records join on. A date that
 * does not parse is skipped rather than filed under a blank key, which would have merged every
 * malformed row into one phantom day.
 */
fun buildAttendanceByDate(attendance: List<CourseAttendance> = emptyList()): Map<String, DayAttendance> {
    val byDate = linkedMapOf<String, AttendanceAcc>()

    for (course in attendance) {
        for (entry in course.logs) {
            val parsed = parseDayDate(entry.date) ?: continue
            val key = dateKey(parsed)
            val acc = byDate.getOrPut(key) { AttendanceAcc() }

            when (statusBucket(entry.status)) {
                AttendanceBucket.PRESENT -> acc.present++
                AttendanceBucket.ABSENT -> acc.absent++
                AttendanceBucket.ON_DUTY -> acc.onDuty++
            }
            acc.held++
            acc.courses += DayClassRecord(
                courseCode = course.courseCode,
                courseTitle = course.courseTitle,
                status = entry.status,
                date = key,
                rawDate = entry.date,
            )
        }
    }

    return byDate.mapValues { it.value.freeze() }
}

/** Look up the recorded status of one course on one day. */
fun statusForClass(
    byDate: Map<String, DayAttendance>,
    dayKey: String,
    courseCode: String,
): String? = byDate[dayKey]?.courses?.firstOrNull { it.courseCode == courseCode }?.status

/** What a day's attendance adds up to, for the log row. */
enum class LogStatus { PRESENT, ABSENT, MORNING_HALF_DAY, EVENING_HALF_DAY, PARTIALLY_ABSENT, PARTIAL_OD }

val LOG_STATUS_TONE: Map<LogStatus, String> = mapOf(
    LogStatus.PRESENT to "emerald",
    LogStatus.ABSENT to "red",
    LogStatus.MORNING_HALF_DAY to "amber",
    LogStatus.EVENING_HALF_DAY to "amber",
    LogStatus.PARTIALLY_ABSENT to "red",
    LogStatus.PARTIAL_OD to "amber",
)

val LOG_STATUS_LABEL: Map<LogStatus, String> = mapOf(
    LogStatus.PRESENT to "Full day",
    LogStatus.ABSENT to "Absent",
    LogStatus.MORNING_HALF_DAY to "Morning half-day",
    LogStatus.EVENING_HALF_DAY to "Evening half-day",
    LogStatus.PARTIALLY_ABSENT to "Partially absent",
    LogStatus.PARTIAL_OD to "Partial OD",
)

/** One row per day with any recorded class, newest first. */
data class AttendanceLogRow(
    /** The raw `viewLink` date, kept so the notes tracker keeps matching. */
    val date: String,
    val dateObj: LocalDate?,
    val dateKey: String,
    val weekday: String,
    val status: LogStatus,
    val label: String,
    val tone: String,
    val attendance: DayAttendance,
    val missedClasses: List<DayClassRecord>,
    val isMissed: Boolean,
    val isFuture: Boolean,
)

/**
 * One row per day with any recorded class, newest first.
 *
 * @param startMinutes minutes from midnight a course starts, or null when unknown - unknown starts
 *   count as morning, which is what the old page did and is right more often than not (a 7am lab
 *   is the only thing before the morning column anyway).
 * @param now the day "today" is judged against, injected so the tests are not wall-clock flaky.
 */
fun buildAttendanceLog(
    byDate: Map<String, DayAttendance>,
    startMinutes: (String) -> Int?,
    now: LocalDate = today(),
): List<AttendanceLogRow> {
    val todayKey = dateKey(now)

    fun isAbsent(c: DayClassRecord): Boolean = c.status.equals("absent", ignoreCase = true)
    fun isOd(c: DayClassRecord): Boolean {
        val s = c.status.lowercase()
        return s == "on duty" || s == "partial od"
    }

    fun allMissed(list: List<DayClassRecord>): Boolean = list.isNotEmpty() && list.all { isAbsent(it) }
    fun noneMissed(list: List<DayClassRecord>): Boolean = list.none { isAbsent(it) }

    return byDate.entries.map { (key, attendance) ->
        val dateObj = parseDayDate(key)
        val weekday = dateObj?.let { weekdayShort(it) } ?: ""

        val needsNotes = attendance.courses.filter { !it.status.equals("present", ignoreCase = true) }
        val startOf = { code: String -> startMinutes(code) ?: 0 }
        val morning = attendance.courses.filter { startOf(it.courseCode) < 13 * 60 }
        val evening = attendance.courses.filter { startOf(it.courseCode) >= 13 * 60 }

        val status: LogStatus = when {
            attendance.courses.any { isAbsent(it) } -> when {
                allMissed(morning) && allMissed(evening) -> LogStatus.ABSENT
                allMissed(morning) && noneMissed(evening) -> LogStatus.MORNING_HALF_DAY
                allMissed(evening) && noneMissed(morning) -> LogStatus.EVENING_HALF_DAY
                attendance.courses.all { isAbsent(it) || isOd(it) } -> LogStatus.PARTIAL_OD
                else -> LogStatus.PARTIALLY_ABSENT
            }

            attendance.courses.any { isOd(it) } -> LogStatus.PARTIAL_OD
            else -> LogStatus.PRESENT
        }

        AttendanceLogRow(
            date = attendance.courses.firstOrNull()?.date ?: key,
            dateObj = dateObj,
            dateKey = key,
            weekday = weekday,
            status = status,
            label = LOG_STATUS_LABEL.getValue(status),
            tone = LOG_STATUS_TONE.getValue(status),
            attendance = attendance,
            missedClasses = needsNotes,
            isMissed = needsNotes.isNotEmpty(),
            isFuture = key > todayKey,
        )
    }.sortedByDescending { it.dateKey }
}

/** Which slice of the log a filter chip shows. `PRESENT` means "nothing needs notes". */
enum class LogFilter { ALL, MISSED, PRESENT, UPCOMING }

fun filterLog(rows: List<AttendanceLogRow>, filter: LogFilter): List<AttendanceLogRow> = when (filter) {
    LogFilter.MISSED -> rows.filter { it.isMissed }
    LogFilter.PRESENT -> rows.filter { !it.isMissed }
    LogFilter.UPCOMING -> rows.filter { it.isFuture }
    LogFilter.ALL -> rows
}

// ── OD ────────────────────────────────────────────────────────────────────────

/** One course inside one day's OD record. */
data class OdCourse(val title: String, val type: String)

/** One day's on-duty record: `date`, the courses it covered, and the hours VTOP counted. */
data class OdRecord(val date: String, val courses: List<OdCourse>, val total: Int)

/** What the user recorded by hand about one OD: whether it ended up wasted or recovered. */
data class TrackedOd(val courseTitle: String, val status: String)

data class OdSummary(
    val totalHours: Int = 0,
    val validHours: Int = 0,
    val wastedHours: Int = 0,
    val recoveredHours: Int = 0,
    val wastedCount: Int = 0,
    val recoveredCount: Int = 0,
)

/**
 * Pair each OD record with what the user recorded about it.
 *
 * `tracked` is hand-written, keyed by course title as it was typed, while an OD record carries the
 * title as VTOP sent it, so the two drift ("Design & Analysis" vs "Design and Analysis") and
 * containment is the fallback. It is an assignment rather than a lookup for the reason above:
 * an exact title match scores 3, a containment match 1, and a record already claimed is skipped.
 * Untouched records get null, meaning "the user recorded nothing", which is not the same as zero.
 *
 * The entries arrive as a list because Node reads `Object.values(tracked)`; re-keying them by
 * title first would merge two entries that happen to be spelled the same.
 */
internal fun resolveTrackedOds(
    courses: List<OdCourse>,
    tracked: List<TrackedOd>?,
): List<TrackedOd?> {
    val assigned = arrayOfNulls<TrackedOd>(courses.size)
    if (tracked == null) return assigned.toList()

    for (t in tracked) {
        val known = t.courseTitle.lowercase().trim()
        if (known.isEmpty()) continue

        var best = -1
        var bestScore = 0
        courses.forEachIndexed { i, c ->
            if (assigned[i] != null) return@forEachIndexed
            val title = c.title.lowercase().trim()
            if (title.isEmpty()) return@forEachIndexed
            val score = when {
                title == known -> 3
                title.contains(known) || known.contains(title) -> 1
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }

        if (best != -1) assigned[best] = t
    }

    return assigned.toList()
}

/** A lab/ELA/PBL is worth two hours against the requirement. */
internal fun odWeight(type: String): Int =
    if (listOf("lab", "ela", "pbl").any { type.lowercase().contains(it) }) 2 else 1

/**
 * OD hours split by what actually happened to them.
 *
 * A "valid" OD that was later marked Present was wasted; one later marked Absent was recovered.
 * The user tracks that transition by hand, so [tracker] is the only place that knowledge exists.
 */
fun summariseOd(
    odData: List<OdRecord>,
    tracker: Map<String, List<TrackedOd>> = emptyMap(),
): OdSummary {
    if (odData.isEmpty()) return OdSummary()

    var totalHours = 0
    var validHours = 0
    var wastedHours = 0
    var recoveredHours = 0
    var wastedCount = 0
    var recoveredCount = 0

    for (dayOD in odData) {
        val matches = resolveTrackedOds(dayOD.courses, tracker[dayOD.date])

        dayOD.courses.forEachIndexed { i, c ->
            val hours = odWeight(c.type)
            when (matches[i]?.status) {
                "wasted" -> {
                    wastedHours += hours
                    wastedCount += 1
                }

                "recovered" -> {
                    recoveredHours += hours
                    recoveredCount += 1
                    validHours += hours
                }

                else -> validHours += hours
            }
        }

        totalHours += dayOD.total
    }

    return OdSummary(
        totalHours = totalHours,
        validHours = validHours,
        wastedHours = wastedHours,
        recoveredHours = recoveredHours,
        wastedCount = wastedCount,
        recoveredCount = recoveredCount,
    )
}

/**
 * Build the OD list from the same `viewLink` history the calendar reads.
 *
 * Kotlin has no VTOP on-duty payload - the OD screen derives its days from attendance, so this
 * derives them the same way to keep the two surfaces agreeing. The lab split matches
 * `ODTrackerScreen`'s rule (a slot beginning with `L`) rather than Node's, because that is the
 * rule the hours on the OD screen are already computed with; unifying the two is Wave 3's job.
 */
fun odRecordsFrom(attendance: List<CourseAttendance>): List<OdRecord> {
    val raw = mutableListOf<Pair<String, OdCourse>>()

    for (course in attendance) {
        val isLab = course.slotName.startsWith("L")
        for (entry in course.logs) {
            if (entry.date.isBlank()) continue
            val s = entry.status.trim().lowercase()
            val isOd = s == "on duty" || s == "od" || s == "onduty"
            if (!isOd) continue
            raw += entry.date to OdCourse(
                title = course.courseTitle,
                type = if (isLab) "LAB" else "TH",
            )
        }
    }

    return raw.groupBy({ it.first }, { it.second })
        .map { (date, courses) -> OdRecord(date, courses, courses.sumOf { odWeight(it.type) }) }
        .sortedByDescending { it.date }
}
