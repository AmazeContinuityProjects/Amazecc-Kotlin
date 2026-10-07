package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.MoodleAssignment
import kotlinx.datetime.LocalDate

/**
 * The port of `AmazeCC/src/lib/calendarDay.ts` §Enrichment - `buildEnrichedCalendars` and its
 * event sources.
 *
 * Every source that can put something on a day is folded in here - the VTOP academic calendar, the
 * exam schedule, Moodle deadlines, recorded attendance, OD records, the task store and the user's
 * own EventHub registrations - so the grid, the log, the day sheet and any export all read the
 * same objects instead of each re-deriving events from raw payloads. That is the whole reason the
 * old page's day panel and its grid could disagree about what a day contained.
 *
 * There is exactly one of these. `CalendarScreen` and `SimplifiedHomeScreen` must call this rather
 * than each building their own month, which is the contract the plan states as "port
 * `buildEnrichedCalendars` once; do not port it twice".
 *
 * ## What Kotlin cannot supply yet
 *
 * Node feeds [CalendarSources.examSchedule] a `{ "CAT II": [...] }` record because VTOP's exam
 * schedule is keyed by series, and the series is what a day detail prints ("CAT II · 09:15 AM ·
 * LH1"). Kotlin's persisted snapshot keeps `List<Exam>` and has nowhere to put the key - the
 * grouping is dropped in `AcademicMerge`, before the snapshot is written - so callers pass a
 * single blank key. Papers still appear with their time and venue, the series label is omitted
 * rather than guessed, and milestone folding stays off (`ExamSeries.sameSeries("", "CAT II")` is
 * false, so nothing folds into the wrong milestone). Restoring the key is an ingest change and is
 * recorded in the plan's divergence list, not papered over here.
 *
 * A second, smaller gap: [CalendarSources.calendars] carries no `totalDays`. Node only trusts that
 * field when it is *longer* than a real month, so the absence costs the "declared longer than
 * reality" branch and nothing else; the `days.size >= 28` guard below still catches a truncated
 * payload, which is the failure that actually shipped.
 */

/** An EventHub registration, as the calendar needs it - the only source the user authored. */
data class CalendarRegistration(
    val eid: String = "",
    val title: String = "",
    val venue: String? = null,
    val date: String? = null,
    val time: String? = null,
    /**
     * Node drops a registration whose payment has not completed: EventHub records the registration
     * when the form is submitted, which for a paid event is *before* the money moves, and listing
     * it under "Upcoming" would tell the user to turn up to something they have not bought.
     *
     * Kotlin's `EventHubRegisteredEvent` has no such field yet, so callers pass null and the entry
     * counts as confirmed - which is also Node's rule for a free event, where the field is absent.
     */
    val paymentStatus: String? = null,
)

/**
 * Everything that can put an event on a day.
 *
 * Assembled by the screen from `AppState.domain` plus the stores that live outside the snapshot
 * (tasks, the OD tracker), exactly as the Node page assembles its own. The output is
 * [MonthModel] - a projection - so nothing downstream ever sees one of these inputs again.
 */
data class CalendarSources(
    /** The published academic calendar, already flattened from `CalendarRes` / `calendarsList`. */
    val calendars: List<CalendarMonth> = emptyList(),
    /** Node's keyed exam schedule. See the file note: Kotlin currently passes one blank key. */
    val examSchedule: Map<String, List<Exam>> = emptyMap(),
    val moodle: List<MoodleAssignment> = emptyList(),
    /** The attendance projection - `viewLink` history lives on `CourseAttendance.logs`. */
    val courses: List<CourseAttendance> = emptyList(),
    val od: List<OdRecord> = emptyList(),
    val tasks: List<HomeworkTask> = emptyList(),
    /** `date` -> the entries the user recorded by hand for that day. */
    val odTracker: Map<String, List<TrackedOd>> = emptyMap(),
    val registeredEvents: List<CalendarRegistration> = emptyList(),
    /** Already gated by `shouldShowProfilePhoto` by the caller, so the privacy decision stays put. */
    val profileImageUrl: String? = null,
)

/** Node accepts an array, a `{ calendars: [...] }` wrapper, or a single month. */
internal fun asCalendarArray(calendars: List<CalendarMonth>?): List<CalendarMonth> = calendars.orEmpty()

/** Same calendar day, local time. */
internal fun isSameDay(d: LocalDate, year: Int, monthIndex: Int, date: Int): Boolean =
    d.year == year && d.monthNumber - 1 == monthIndex && d.dayOfMonth == date

/**
 * Whether a task falls due on [dayDate].
 *
 * Node parses the due value as a `Date` and compares calendar days; [parseDayDate] does the same
 * for the ISO and textual forms the task store uses. An unparseable due date is *not* due, which
 * matches `isNaN(d.getTime()) -> false`.
 */
fun isDueOnDay(due: String?, dayDate: LocalDate): Boolean {
    if (due.isNullOrEmpty()) return false
    val d = parseDayDate(due) ?: return false
    return d == dayDate
}

/** Minutes past midnight as Moodle prints it, in Node's `toLocaleTimeString` two-digit 12-hour form. */
internal fun moodleTimeLabel(due: String?): String? {
    if (due.isNullOrEmpty()) return null
    val m = TIME_IN_PROSE.find(due) ?: return null
    val hour24 = m.groupValues[1].toIntOrNull() ?: return null
    val minute = m.groupValues[2].toIntOrNull() ?: return null
    val marker = m.groupValues[3].lowercase().replace(".", "")
    val isPm = when {
        marker.startsWith("p") -> true
        marker.startsWith("a") -> false
        else -> hour24 >= 12
    }
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    return pad2(hour12) + ":" + pad2(minute) + " " + if (isPm) "PM" else "AM"
}

private val TIME_IN_PROSE = Regex("""(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp]\.?[Mm]\.?)?""")

private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

internal fun moodleEventsFor(
    moodle: List<MoodleAssignment>,
    year: Int,
    monthIndex: Int,
    date: Int,
): List<DayEvent> {
    val out = mutableListOf<DayEvent>()
    for (m in moodle) {
        // Node gates on the prose due date itself; the numeric fields are the fallback when the
        // prose is a locale we cannot read, which Node (using the engine's own parser) manages.
        if (m.done || m.due.isEmpty()) continue
        val due = m.dueDate ?: parseDayDate(m.due) ?: continue
        if (!isSameDay(due, year, monthIndex, date)) continue

        out += makeEvent(
            kind = EventKind.ASSIGNMENT,
            title = m.name.split("/").lastOrNull().orEmpty().ifEmpty { "Assignment" },
            detail = moodleTimeLabel(m.due),
            dueAt = due,
            url = m.url,
            hidden = m.hidden,
        )
    }
    return out
}

/**
 * The exam papers on one date, with duplicate series merged.
 *
 * The schedule is keyed by series name and those keys are not stable: the same exam has been seen
 * as both `"CAT II"` and `"CAT2"`. Iterating the record would emit every paper of both series, so
 * a day could show the same subject twice. Keys are therefore grouped by canonical name first, and
 * a paper is deduped on course + time + venue so a genuinely repeated entry still collapses.
 *
 * The claim set is scoped to the whole day rather than to one key, for the same reason.
 */
internal fun examEventsFor(
    schedule: Map<String, List<Exam>>,
    year: Int,
    monthIndex: Int,
    date: Int,
): List<DayEvent> {
    val out = mutableListOf<DayEvent>()
    val seen = mutableSetOf<String>()

    for ((examType, subjects) in schedule) {
        val series = ExamSeries.canonicalSeriesName(examType)
        for (s in subjects) {
            if (s.date.isEmpty()) continue
            val d = parseDayDate(s.date) ?: continue
            if (!isSameDay(d, year, monthIndex, date)) continue

            val time = s.time.ifEmpty { "TBA" }
            val venue = s.venue.takeIf { it.isNotBlank() && it != "-" }.orEmpty()
            val fingerprint = "$series|${s.courseCode.ifEmpty { s.courseTitle }}|$time|$venue"
            if (!seen.add(fingerprint)) continue

            // A blank key means "no series available", not "an unnamed series": label nothing
            // rather than letting `prettySeriesName("")` print "Exam".
            val seriesLabel = examType.takeIf { it.isNotBlank() }
                ?.let { CalendarClassifier.prettySeriesName(it) }
                ?.takeIf { it.isNotBlank() }

            out += makeEvent(
                kind = EventKind.EXAM,
                title = s.courseTitle.ifEmpty { s.courseCode },
                detail = listOfNotNull(seriesLabel, time, venue.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                courseCode = s.courseCode,
                courseTitle = s.courseTitle,
                series = examType.takeIf { it.isNotBlank() },
            )
        }
    }
    return out
}

internal fun odEventsFor(
    od: List<OdRecord>,
    odTracker: Map<String, List<TrackedOd>>,
    year: Int,
    monthIndex: Int,
    date: Int,
): List<DayEvent> {
    val out = mutableListOf<DayEvent>()
    for (dayOD in od) {
        val d = parseDayDate(dayOD.date) ?: continue
        if (!isSameDay(d, year, monthIndex, date)) continue
        val matches = resolveTrackedOds(dayOD.courses, odTracker[dayOD.date])

        if (dayOD.courses.isEmpty()) {
            out += makeEvent(
                kind = EventKind.OD,
                title = "On-Duty · ${dayOD.total} hrs",
                hours = dayOD.total,
            )
            continue
        }

        dayOD.courses.forEachIndexed { i, c ->
            out += makeEvent(
                kind = EventKind.OD,
                title = c.title,
                detail = when (matches[i]?.status) {
                    "wasted" -> "OD · wasted"
                    "recovered" -> "OD · recovered"
                    else -> "On-Duty"
                },
                hours = odWeight(c.type),
            )
        }
    }
    return out
}

internal fun classEventsFor(record: DayAttendance?): List<DayEvent> =
    record?.courses.orEmpty().map { c ->
        makeEvent(
            kind = EventKind.CLASS,
            title = c.courseTitle.ifEmpty { c.courseCode },
            detail = c.status,
            courseCode = c.courseCode,
            courseTitle = c.courseTitle,
            absent = c.status.lowercase() == "absent",
        )
    }

internal fun taskEventsFor(tasks: List<HomeworkTask>, fullDate: LocalDate): List<DayEvent> =
    tasks.asSequence()
        .filter { !it.completed && isDueOnDay(it.dueDate, fullDate) }
        .map { t ->
            makeEvent(
                kind = EventKind.ASSIGNMENT,
                title = t.title,
                detail = t.courseCode.ifEmpty { t.courseTitle },
                dueAt = parseDayDate(t.dueDate),
                taskId = t.id,
            )
        }
        .toList()

/**
 * Whether a registration is one the user is actually going to.
 *
 * A free event has no `paymentStatus` at all, so absent means "nothing to pay" and is treated as
 * confirmed. Dropping those would silently lose every club signup on the list.
 */
internal fun isRegistrationConfirmed(r: CalendarRegistration): Boolean {
    val status = r.paymentStatus.orEmpty().lowercase()
    if (status.isEmpty()) return true
    return listOf("paid", "free", "success", "confirmed").any { status.contains(it) }
}

internal fun eventhubEventsFor(
    registrations: List<CalendarRegistration>,
    profileImageUrl: String?,
    year: Int,
    monthIndex: Int,
    date: Int,
): List<DayEvent> {
    val out = mutableListOf<DayEvent>()
    for (r in registrations) {
        val d = r.date?.let { parseDayDate(it) } ?: continue
        if (!isSameDay(d, year, monthIndex, date)) continue
        if (!isRegistrationConfirmed(r)) continue

        val detail = listOfNotNull(
            r.time?.takeIf { it.isNotBlank() },
            r.venue?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { null }

        out += makeEvent(
            kind = EventKind.EVENT,
            title = r.title.trim().ifEmpty { kindLabel(EventKind.EVENT) },
            detail = detail,
            avatarUrl = profileImageUrl,
            eventhubId = r.eid,
        )
    }
    return out
}

/** Days in the month `monthIndex` (0-based) of `year`, matching JS `new Date(y, m + 1, 0)`. */
internal fun daysInMonth(year: Int, monthIndex: Int): Int = when (monthIndex + 1) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
}

/**
 * The whole calendar, classified once.
 *
 * @param now only decides the fallback month for a malformed label; injected so tests are not
 *   wall-clock dependent.
 */
fun buildEnrichedCalendars(
    sources: CalendarSources,
    now: LocalDate = today(),
): List<MonthModel> {
    val attendanceByDate = buildAttendanceByDate(sources.courses)

    return asCalendarArray(sources.calendars).mapIndexed { index, cal ->
        val parsed = parseCalendarMonth(cal.label, fallbackYear = null, now = now)
        val monthIndex = parsed.monthIndex
        val year = parsed.year
        val rawDays = cal.days

        val realLength = runCatching { daysInMonth(year, monthIndex) }.getOrDefault(31)
        val declared = 0
        val totalDays = maxOf(
            realLength,
            if (declared > realLength) declared else 0,
            if (rawDays.size >= 28) rawDays.size else 0,
        )

        val days = mutableListOf<DayModel>()
        for (date in 1..totalDays) {
            val fullDate = runCatching { LocalDate(year, monthIndex + 1, date) }.getOrNull() ?: continue
            val key = dateKey(fullDate)
            val dayRecord = attendanceByDate[key]

            val rawDay = rawDays.firstOrNull { it.date == date }
            val vtop: List<RawCalendarEvent> = rawDay?.events.orEmpty().map { e ->
                RawCalendarEvent(type = e.type, text = e.text, category = e.category, color = e.color)
            }

            // De-duplicate by kind + title: VTOP repeats a course across the theory and lab halves
            // of an embedded course, and the same holiday shows up under both `text` and
            // `category` on some months. A later entry wins, as `Map.set` does in Node.
            val calendarEvents = vtop
                .map { classify(it) }
                .associateBy { "${it.kind}|${it.title}" }
                .values
                .toList()

            val events = (
                examEventsFor(sources.examSchedule, year, monthIndex, date) +
                    moodleEventsFor(sources.moodle, year, monthIndex, date) +
                    taskEventsFor(sources.tasks, fullDate) +
                    odEventsFor(sources.od, sources.odTracker, year, monthIndex, date) +
                    eventhubEventsFor(sources.registeredEvents, sources.profileImageUrl, year, monthIndex, date) +
                    classEventsFor(dayRecord) +
                    calendarEvents
                // Node sorts by `localeCompare`; common Kotlin has no locale collation, so this is
                // code-unit order. Priorities still decide the ranking, and ties on a title are
                // the same titles in practice (course names), so the visible order matches.
                ).sortedWith(compareBy({ it.priority }, { it.title }))

            val attendanceForDay = dayRecord ?: emptyAttendance()
            val dueTasks = sources.tasks.count { !it.completed && isDueOnDay(it.dueDate, fullDate) }

            days += DayModel(
                date = date,
                fullDate = fullDate,
                dateKey = key,
                weekday = weekdayShort(fullDate),
                // Read from the *raw* VTOP entries: the classifier drops the "Instructional Day
                // Order" prefix to make a readable title, and the day it names is exactly the
                // part that got dropped.
                dayOrder = parseDayOrder(vtop),
                dayType = decideDayType(vtop, events, attendanceForDay, rawDay != null),
                events = foldPapersIntoMilestones(events),
                attendance = attendanceForDay,
                taskCount = dueTasks,
            )
        }

        // `working` is "has classes" - instructional plus shortened, exam days excluded. `holiday`
        // counts days the college is shut, so a non-instructional working day lands in `other`: it
        // is neither of those two things.
        var working = 0
        var holidayCount = 0
        var other = 0
        for (d in days) {
            when {
                hasClasses(d) -> working++
                d.dayType == DayType.HOLIDAY -> holidayCount++
                else -> other++
            }
        }

        MonthModel(
            id = "$year-$monthIndex-$index",
            label = monthLabel(monthIndex, year),
            shortLabel = monthLabel(monthIndex, year, short = true),
            monthIndex = monthIndex,
            year = year,
            days = days,
            summary = MonthSummary(
                total = days.size,
                working = working,
                holiday = holidayCount,
                other = other,
            ),
        )
    }.sortedWith(compareBy({ it.monthIndex }, { it.year }))
}

/**
 * Which month the page should open on.
 *
 * Today if today is inside the published calendar, otherwise the first month that has not ended
 * yet - a user checking the calendar in the last week of July for an August-November semester
 * wants August, not a January that has already been and gone. Falls back to the last month when
 * the whole calendar is in the past, because that is the one they actually want to read.
 *
 * Deliberately not persisted: "which month am I looking at" is a cursor, and the old page stored
 * it so reopening months later dropped you in a month with nothing in it.
 */
fun activeMonthIndex(months: List<MonthModel>, now: LocalDate = today()): Int {
    val currentMonth = now.monthNumber - 1
    val current = months.indexOfFirst { it.monthIndex == currentMonth && it.year == now.year }
    if (current != -1) return current

    val next = months.indexOfFirst {
        it.year > now.year || (it.year == now.year && it.monthIndex >= currentMonth)
    }
    return if (next == -1) (months.size - 1).coerceAtLeast(0) else next
}

/** A day found somewhere in the calendar, with the month it belongs to. */
data class FoundDay(val month: MonthModel, val day: DayModel)

fun findDay(months: List<MonthModel>, dayKeyValue: String): FoundDay? {
    for (month in months) {
        val day = month.days.firstOrNull { it.dateKey == dayKeyValue }
        if (day != null) return FoundDay(month, day)
    }
    return null
}

/**
 * A day model for a date the published calendar does not cover.
 *
 * Attendance is recorded against a whole semester and the academic calendar is a published,
 * sometimes-truncated document, so `viewLink` legitimately holds dates with no matching month - a
 * class held in the week before the calendar starts, or after it ends. Those rows still have to
 * open: a log full of dead rows is worse than a slightly emptier one, because the user cannot tell
 * the difference between "no data" and "broken".
 */
fun synthesiseDay(attendance: DayAttendance): DayModel {
    val rawDate = attendance.courses.firstOrNull()?.date
    val safeDate = (rawDate?.let { parseDayDate(it) }) ?: today()
    val key = dateKey(safeDate)

    return DayModel(
        date = safeDate.dayOfMonth,
        fullDate = safeDate,
        dateKey = key,
        weekday = weekdayShort(safeDate),
        // Attendance was recorded for this date, so it taught something even though the published
        // calendar never covered it.
        dayType = if (attendance.held > 0) DayType.INSTRUCTIONAL else DayType.OTHER,
        events = classEventsFor(attendance),
        attendance = attendance,
        taskCount = 0,
    )
}
