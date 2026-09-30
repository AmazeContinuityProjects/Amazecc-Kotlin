package com.amazecc.app.shared.ui.screens.home

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.utils.AttendanceDay
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.NotificationsUtils
import com.amazecc.app.shared.utils.TimeMath
import com.amazecc.app.shared.utils.examDateParsed
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The simplified home's data layer.
 *
 * Everything here is a pure function of state the app already holds. No network,
 * no side effects, no composition — which is what lets the screen be a
 * `remember` away from a recomposition and keeps the derivations unit-testable.
 *
 * The port source is the web app's `SimplifiedMobileHome.tsx` plus the three
 * `lib/` helpers it leans on (`attendanceSummary.ts`, `attendanceTimetable.ts`,
 * `weekStrip.ts`). Where the web and this app disagree about a *number*, this
 * app's existing answer wins — see [homeBunk].
 */

// ── Attendance ──

/** The tone bands the web's `statusForPercentage` uses, resolved to this app's roles. */
enum class HomeAttendanceStatus { SAFE, WARNING, CRITICAL, NONE }

data class HomeAttendanceSummary(
    /** 0-100, unrounded. 0 when no course has held a class. */
    val percentage: Float,
    val attended: Int,
    val total: Int,
    val status: HomeAttendanceStatus
) {
    val hasData: Boolean get() = total > 0
    val label: String
        get() = when (status) {
            HomeAttendanceStatus.SAFE -> "Safe"
            HomeAttendanceStatus.WARNING -> "Warning"
            HomeAttendanceStatus.CRITICAL -> "Critical"
            HomeAttendanceStatus.NONE -> "—"
        }
    val tone: HomeTone
        get() = when (status) {
            HomeAttendanceStatus.SAFE -> HomeTone.SUCCESS
            HomeAttendanceStatus.WARNING -> HomeTone.WARNING
            HomeAttendanceStatus.CRITICAL -> HomeTone.DANGER
            HomeAttendanceStatus.NONE -> HomeTone.NEUTRAL
        }
}

/**
 * Below [targetPct] is critical; within five points of it is a warning.
 *
 * The five-point band is the web's, and it is the same band
 * [com.amazecc.app.shared.ui.components.BunkOMeterCard] uses, so the headline
 * tile and the bunk meter never disagree about the same course.
 */
fun homeAttendanceStatus(percentage: Float, total: Int, targetPct: Float): HomeAttendanceStatus = when {
    total <= 0 -> HomeAttendanceStatus.NONE
    percentage >= targetPct + 5f -> HomeAttendanceStatus.SAFE
    percentage >= targetPct -> HomeAttendanceStatus.WARNING
    else -> HomeAttendanceStatus.CRITICAL
}

/**
 * The app's one attendance formula.
 *
 * Sum VTOP's own `attendedClasses` and `totalClasses` across the enrolled
 * courses, unweighted, and divide. The unweighted sum is deliberate: a lab is
 * worth two hours against the *requirement* elsewhere in the app, but weighting
 * it here would move the headline away from the figure the institute prints,
 * which is the number students reconcile against.
 */
fun summariseHomeAttendance(
    courses: List<Pair<Int, Int>>,
    targetPct: Float
): HomeAttendanceSummary {
    var attended = 0
    var total = 0
    for ((att, tot) in courses) {
        attended += att
        total += tot
    }
    val percentage = if (total > 0) (attended.toFloat() / total.toFloat()) * 100f else 0f
    return HomeAttendanceSummary(percentage, attended, total, homeAttendanceStatus(percentage, total, targetPct))
}

// ── Timetable ──

/**
 * One session on the home's day list.
 *
 * A *session* rather than a slot: two consecutive slots of the same course
 * (theory into its own lab, or an embedded ETH/ELA pair sharing a block) are
 * merged into one row by [buildHomeTimetable], the same way the web merges them.
 */
data class HomeClassCard(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val faculty: String,
    val venue: String,
    /** Merged slot codes, e.g. `A1+L1`. */
    val slotName: String,
    /** Merged time range, e.g. `8:00-9:40`. */
    val time: String,
    val attended: Int,
    val total: Int,
    val percentage: Float,
    val isLab: Boolean
) {
    val startMinutes: Int get() = TimeMath.toMinutes(time.split("-").firstOrNull())
    val endMinutes: Int get() = TimeMath.toMinutes(time.split("-").getOrNull(1))
}

/**
 * The weekly timetable as day -> sessions, for one semester.
 *
 * Mirrors the web's `buildAttendanceDayCardsMap`: for every course, every slot
 * code is looked up in the shared [SlotMap] under each day, then adjacent
 * same-course rows with a gap of five minutes or less are merged into a single
 * session with a joined slot name and a widened time range.
 *
 * ## What is deliberately not here
 *
 * The web's version carries a `saturday_timetable_override` that re-keys the
 * whole Saturday column onto another day. This app has no such setting, and
 * inventing one would be a new concept rather than a port. The general
 * mechanism for the same problem — an academic-calendar day-order
 * announcement — is already honoured upstream via [HomeWeekDay.detectedDayOrder],
 * which is a superset of what the Saturday override could express.
 */
fun buildHomeTimetable(sem: SemesterData?): Map<AttendanceDay, List<HomeClassCard>> {
    val map = AttendanceDay.entries.associateWith { mutableListOf<HomeClassCard>() }.toMutableMap()
    if (sem == null) return map.mapValues { it.value.toList() }

    sem.courses.values.forEach { course ->
        val attendance = course.attendance
        val attended = attendance?.attendedClasses ?: 0
        val total = attendance?.totalClasses ?: 0
        val percentage = if (total > 0) {
            (attended.toFloat() / total.toFloat()) * 100f
        } else {
            attendance?.attendancePercentage?.toDoubleOrNull()?.toFloat() ?: 0f
        }
        val isLab = course.slots.any { it.trimStart().startsWith("L", ignoreCase = true) } ||
            course.courseType.contains("Lab", ignoreCase = true)
        val venue = course.venue?.takeIf { it.isNotBlank() } ?: "Room Assigned"

        course.slots.forEach { rawSlot ->
            val cleanSlot = rawSlot.trim()
            if (cleanSlot.isEmpty()) return@forEach
            AttendanceDay.entries.forEach { day ->
                val time = SlotMap.map[day.name]?.get(cleanSlot) ?: return@forEach
                map.getValue(day).add(
                    HomeClassCard(
                        courseCode = course.courseCode,
                        courseTitle = course.courseTitle,
                        courseType = course.courseType,
                        faculty = course.faculty.orEmpty(),
                        venue = venue,
                        slotName = cleanSlot,
                        time = time,
                        attended = attended,
                        total = total,
                        percentage = percentage,
                        isLab = isLab
                    )
                )
            }
        }
    }

    return map.mapValues { (_, rows) ->
        val sorted = rows.sortedWith(
            compareBy({ TimeMath.toMinutes(it.time.split("-").firstOrNull()) }, { it.slotName })
        )
        val merged = mutableListOf<HomeClassCard>()
        for (current in sorted) {
            val previous = merged.lastOrNull()
            val joinable = previous != null &&
                previous.courseTitle == current.courseTitle &&
                previous.courseType == current.courseType &&
                previous.faculty == current.faculty &&
                previous.venue == current.venue &&
                previous.attended == current.attended &&
                previous.total == current.total
            if (joinable) {
                val gap = current.startMinutes - previous.endMinutes
                if (gap in 0..5) {
                    merged[merged.lastIndex] = previous.copy(
                        slotName = "${previous.slotName}+${current.slotName}",
                        time = "${previous.time.substringBefore('-')}-${current.time.substringAfter('-', "")}"
                    )
                    continue
                }
            }
            merged.add(current)
        }
        merged.sortedBy { it.startMinutes }
    }
}

/** One row of the full weekly timetable: a slot code, its time, and what sits in it. */
data class HomeTimetableRow(
    val slot: String,
    val time: String,
    /** Course per day, or null where the slot is free. */
    val cells: Map<AttendanceDay, HomeClassCard>
)

/**
 * The full weekly timetable as a slot matrix — the same grid the app's FFCS
 * planner builds, read from the enrolled courses rather than a chosen set.
 *
 * Built from the *unmerged* (day, slot) pairs rather than from
 * [buildHomeTimetable]. Merging is right for the day list, where an ETH/ELA pair
 * sharing a block is one row you would sit through once, and wrong for the grid,
 * where each slot is its own cell and a merged `A1+L1` label has nowhere to go.
 */
fun buildHomeTimetableMatrix(sem: SemesterData?): List<HomeTimetableRow> {
    if (sem == null) return emptyList()

    val cells = mutableMapOf<AttendanceDay, MutableMap<String, HomeClassCard>>()

    sem.courses.values.forEach { course ->
        val attendance = course.attendance
        val attended = attendance?.attendedClasses ?: 0
        val total = attendance?.totalClasses ?: 0
        val percentage = if (total > 0) {
            (attended.toFloat() / total.toFloat()) * 100f
        } else {
            attendance?.attendancePercentage?.toDoubleOrNull()?.toFloat() ?: 0f
        }
        val isLab = course.slots.any { it.trimStart().startsWith("L", ignoreCase = true) } ||
            course.courseType.contains("Lab", ignoreCase = true)
        val venue = course.venue?.takeIf { it.isNotBlank() } ?: "Room Assigned"

        course.slots.forEach { rawSlot ->
            val cleanSlot = rawSlot.trim()
            if (cleanSlot.isEmpty()) return@forEach
            AttendanceDay.entries.forEach { day ->
                val time = SlotMap.map[day.name]?.get(cleanSlot) ?: return@forEach
                cells.getOrPut(day) { mutableMapOf() }[cleanSlot] = HomeClassCard(
                    courseCode = course.courseCode,
                    courseTitle = course.courseTitle,
                    courseType = course.courseType,
                    faculty = course.faculty.orEmpty(),
                    venue = venue,
                    slotName = cleanSlot,
                    time = time,
                    attended = attended,
                    total = total,
                    percentage = percentage,
                    isLab = isLab
                )
            }
        }
    }

    // Rows are ordered by the earliest start time the slot has on any day, which
    // is the order a student reads the day in. A slot can sit at 08:00 on one
    // day and 14:00 on another, so the minimum is the one that matters.
    val slotOrder = cells.values
        .flatMap { day -> day.keys.map { it to day.getValue(it).startMinutes } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, starts) -> starts.minOrNull() ?: 0 }
        .toList()
        .sortedBy { it.second }
        .map { it.first }

    return slotOrder.map { slot ->
        val sample = cells.values.firstNotNullOfOrNull { it[slot] }
        val row = mutableMapOf<AttendanceDay, HomeClassCard>()
        cells.forEach { (day, dayCells) ->
            dayCells[slot]?.let { row[day] = it }
        }
        HomeTimetableRow(
            slot = slot,
            time = sample?.time.orEmpty(),
            cells = row
        )
    }
}

// ── Bunk margin ──
enum class HomeBunkStatus { SAFE, WARNING, CRITICAL }

data class HomeBunk(
    val status: HomeBunkStatus,
    /** "3 bunkable", "0 bunkable", "Need 4 classes". */
    val text: String,
    /** The second line under [text] — the reason, not a repeat. */
    val subtext: String
) {
    val tone: HomeTone
        get() = when (status) {
            HomeBunkStatus.SAFE -> HomeTone.SUCCESS
            HomeBunkStatus.WARNING -> HomeTone.WARNING
            HomeBunkStatus.CRITICAL -> HomeTone.DANGER
        }
}

/**
 * How many classes can be missed, or how many are needed to climb back.
 *
 * ## One deliberate divergence from the web
 *
 * The web halves both figures for a lab ("a lab session is worth two"), because
 * the requirement it is solving is measured in *hours*. This app measures the
 * requirement in *class attendances* — see
 * [com.amazecc.app.shared.ui.components.BunkOMeterCard] — so halving here would
 * make the home pill disagree with the bunk meter sitting one screen away, for
 * the same course, on the same sync. The web's own
 * `src/lib/attendanceSummary.ts` opens by describing exactly that class of bug
 * as unacceptable. The hour weighting is already correct in the one place it
 * belongs, [com.amazecc.app.shared.state.AcademicDerivers.computeODHours].
 */
fun homeBunk(card: HomeClassCard, targetPct: Float): HomeBunk {
    val threshold = targetPct / 100f
    val attended = card.attended
    val total = card.total

    if (total == 0) {
        return HomeBunk(HomeBunkStatus.SAFE, "No classes yet", "Nothing has been held")
    }

    if (card.percentage < targetPct) {
        val needed = ceil((threshold * total - attended) / (1f - threshold)).toInt().coerceAtLeast(1)
        return HomeBunk(
            HomeBunkStatus.CRITICAL,
            if (needed == 1) "Need 1 class" else "Need $needed classes",
            "To reach ${targetPct.roundToInt()}%"
        )
    }

    val canMiss = floor(attended / threshold - total).toInt()
    return if (canMiss <= 0) {
        HomeBunk(HomeBunkStatus.WARNING, "0 bunkable", "On the safety margin")
    } else {
        HomeBunk(
            HomeBunkStatus.SAFE,
            "$canMiss bunkable",
            if (canMiss == 1) "Safe for one skip" else "Safe for $canMiss skips"
        )
    }
}

// ── Live class progress ──

enum class HomeClassState { LIVE, UPCOMING, COMPLETED, OTHER_DAY }

data class HomeClassProgress(
    val state: HomeClassState,
    /** 0-100 across the session, only meaningful while [state] is [HomeClassState.LIVE]. */
    val progressPct: Float,
    val minutesLeft: Int,
    val minutesUntilStart: Int
)

/**
 * Where a session sits on the clock, and how far through it is.
 *
 * Only sessions on the *real* current date have a state: browsing to next
 * Tuesday's timetable must not paint anything "live" or "done", so [isToday]
 * is the gate rather than a comparison against the selected day.
 */
fun homeClassProgress(card: HomeClassCard, isToday: Boolean, currentMinutes: Int): HomeClassProgress {
    if (!isToday) return HomeClassProgress(HomeClassState.OTHER_DAY, 0f, 0, 0)

    val start = card.startMinutes
    val end = card.endMinutes
    val duration = (end - start).coerceAtLeast(1)

    return when {
        currentMinutes in start..end -> HomeClassProgress(
            state = HomeClassState.LIVE,
            progressPct = (((currentMinutes - start).toFloat() / duration) * 100f).coerceIn(0f, 100f),
            minutesLeft = end - currentMinutes,
            minutesUntilStart = 0
        )
        currentMinutes < start -> HomeClassProgress(
            state = HomeClassState.UPCOMING,
            progressPct = 0f,
            minutesLeft = 0,
            minutesUntilStart = start - currentMinutes
        )
        else -> HomeClassProgress(HomeClassState.COMPLETED, 100f, 0, 0)
    }
}

// ── Week strip ──

/**
 * One day of the visible week.
 *
 * The subset the strip and the day viewport read. Structurally declared rather
 * than threaded through a wide signature so the two stay independently
 * testable, exactly as the web's `WeekStripDay` interface is.
 */
data class HomeWeekDay(
    val dayCode: AttendanceDay,
    val date: LocalDate,
    val isToday: Boolean,
    val exams: List<ExamItem>,
    /** The holiday's own name, so the empty state can say *which* one. */
    val holidayInfo: String?,
    /** The day the academic calendar reorders classes onto, e.g. `MON`. */
    val detectedDayOrder: AttendanceDay?,
    /** The announcement's text, for the "Calendar Auto" banner. */
    val orderInfo: String?,
    val isInstructional: Boolean,
    val hasDeadline: Boolean
) {
    val dayNumber: Int get() = date.dayOfMonth
    val hasExam: Boolean get() = exams.isNotEmpty()
}

/**
 * The mutually exclusive kinds a week-strip disc can be.
 *
 * "Free" and "N classes" are deliberately *not* kinds. They are the same kind
 * of day — a teaching day with nothing special about it — differing only in how
 * much is on it, and a seven-day strip that gave them separate colours would
 * spend a fifth of its palette saying nothing.
 */
enum class HomeWeekFlavour { EXAM, HOLIDAY, REORDERED, TEACHING }

/**
 * Precedence, in one place, because it is the one thing that must not drift.
 *
 * A reordered day already clears [HomeWeekDay.holidayInfo] upstream (reordering
 * a working day marks it instructional), so [HomeWeekFlavour.REORDERED] is only
 * reached on days that are genuinely teaching. An exam on a holiday is an exam
 * day: the exam is the thing you have to act on, and the sub-header will name
 * the holiday.
 */
fun HomeWeekDay.flavour(): HomeWeekFlavour = when {
    hasExam -> HomeWeekFlavour.EXAM
    holidayInfo != null -> HomeWeekFlavour.HOLIDAY
    detectedDayOrder != null -> HomeWeekFlavour.REORDERED
    else -> HomeWeekFlavour.TEACHING
}

/** The word that used to sit on the disc and now lives in the strip's content description. */
fun HomeWeekFlavour.label(): String = when (this) {
    HomeWeekFlavour.EXAM -> "Exam day"
    HomeWeekFlavour.HOLIDAY -> "Academic holiday"
    HomeWeekFlavour.REORDERED -> "Reordered timetable"
    HomeWeekFlavour.TEACHING -> "Teaching day"
}

/** `en-GB` because the month grid already says "Sept", not "Sep". */
fun HomeWeekDay.whenLabel(): String =
    "${date.dayOfWeek.label()} ${date.dayOfMonth} ${date.month.label()}"

private fun DayOfWeek.label(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun kotlinx.datetime.Month.label(): String = when (this) {
    kotlinx.datetime.Month.JANUARY -> "Jan"
    kotlinx.datetime.Month.FEBRUARY -> "Feb"
    kotlinx.datetime.Month.MARCH -> "Mar"
    kotlinx.datetime.Month.APRIL -> "Apr"
    kotlinx.datetime.Month.MAY -> "May"
    kotlinx.datetime.Month.JUNE -> "Jun"
    kotlinx.datetime.Month.JULY -> "Jul"
    kotlinx.datetime.Month.AUGUST -> "Aug"
    kotlinx.datetime.Month.SEPTEMBER -> "Sept"
    kotlinx.datetime.Month.OCTOBER -> "Oct"
    kotlinx.datetime.Month.NOVEMBER -> "Nov"
    kotlinx.datetime.Month.DECEMBER -> "Dec"
}

/**
 * The disc's full description, read by a screen reader and shown as a tooltip.
 *
 * This is where the pill's text went. The session count is threaded in rather
 * than read from the day so the caller keeps owning the timetable map — and so
 * that dropping the count off the face of the disc did not quietly drop it out
 * of the app.
 */
fun HomeWeekDay.contentDescription(classCount: Int): String {
    val what = when (flavour()) {
        HomeWeekFlavour.REORDERED -> "${HomeWeekFlavour.REORDERED.label()} (${detectedDayOrder?.name})"
        HomeWeekFlavour.TEACHING -> if (classCount > 0) {
            "$classCount ${if (classCount == 1) "session" else "sessions"}"
        } else {
            "No classes"
        }
        else -> flavour().label()
    }
    val examNote = if (exams.isEmpty()) "" else ", ${exams.size} ${if (exams.size == 1) "exam" else "exams"}"
    return "${whenLabel()} · $what$examNote"
}

// ── Academic calendar analysis ──

/**
 * The weekday a calendar column belongs to.
 *
 * [AttendanceDay] is declared Monday-first because that is the order the shared
 * [SlotMap] uses, while [DayOfWeek] is declared Sunday-first. Indexing one with
 * the other is the off-by-one that silently shifts the whole timetable, so the
 * mapping is stated once, here, and named for what it does.
 */
internal fun DayOfWeek.toAttendanceDay(): AttendanceDay = when (this) {
    DayOfWeek.MONDAY -> AttendanceDay.MON
    DayOfWeek.TUESDAY -> AttendanceDay.TUE
    DayOfWeek.WEDNESDAY -> AttendanceDay.WED
    DayOfWeek.THURSDAY -> AttendanceDay.THU
    DayOfWeek.FRIDAY -> AttendanceDay.FRI
    DayOfWeek.SATURDAY -> AttendanceDay.SAT
    DayOfWeek.SUNDAY -> AttendanceDay.SUN
}
/** The attendance column the *current* date maps to, before any calendar override. */
internal fun homeTodayAttendanceDay(today: LocalDate): AttendanceDay = today.dayOfWeek.toAttendanceDay()


/**
 * Picks the month set the home reads its day types from.
 *
 * The user can nominate one of several published calendars in Settings, so the
 * preferred one wins when it is still present. Falling back to the single
 * resolved [calendar] is right for the common case: that is the one the sync
 * engine already resolved against the preference.
 */
fun resolveHomeCalendarMonths(
    calendarsList: com.amazecc.app.shared.model.CalendarsListRes?,
    calendar: com.amazecc.app.shared.model.CalendarRes?,
    preferredName: String?
): List<com.amazecc.app.shared.model.CalendarMonth> {
    val calendars = calendarsList?.calendars.orEmpty()
    if (calendars.isNotEmpty()) {
        val preferred = preferredName?.trim()?.takeIf { it.isNotEmpty() }
        val match = if (preferred != null) {
            calendars.firstOrNull { it.name.equals(preferred, ignoreCase = true) }
        } else {
            null
        }
        val chosen = match ?: calendars.first()
        if (chosen.months.isNotEmpty()) return chosen.months
    }
    return calendar?.months.orEmpty()
}

private val HOLIDAY_WORDS = listOf(
    "holiday", "vacation", "pooja", "puja", "diwali", "pongal",
    "eid", "christmas", "independence", "republic"
)

/**
 * Extracts a day-order override from an academic-calendar announcement.
 *
 * The weekday and an order word must *both* be present, and that conjunction is
 * the whole point: "Monday" alone appears in announcements that have nothing to
 * do with the timetable ("Exam on Monday"), and reading those as overrides would
 * silently re-key the week.
 */
fun extractDayOrderOverride(text: String?): AttendanceDay? {
    val norm = text?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val hasOrderWord = norm.contains("order") || norm.contains("timetable") || norm.contains("table")
    if (!hasOrderWord) return null
    return when {
        norm.contains("mon") -> AttendanceDay.MON
        norm.contains("tue") -> AttendanceDay.TUE
        norm.contains("wed") -> AttendanceDay.WED
        norm.contains("thu") -> AttendanceDay.THU
        norm.contains("fri") -> AttendanceDay.FRI
        norm.contains("sat") -> AttendanceDay.SAT
        else -> null
    }
}

/** Reads the month number and year out of a `CalendarMonth.month` label like "Sept 2026". */
internal data class MonthMeta(val month: Int, val year: Int)
internal fun monthMetaOf(raw: String, fallbackYear: Int): MonthMeta {
    val tokens = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    val year = tokens.firstOrNull { it.length == 4 && it.all(Char::isDigit) }?.toIntOrNull() ?: fallbackYear
    val monthToken = tokens.firstOrNull { it.length >= 3 && !it.all(Char::isDigit) }
    val month = if (monthToken != null) {
        AttendanceTimetable.parseMonthNumber(monthToken)
    } else {
        tokens.firstOrNull { it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it in 1..12 }
    }
    return MonthMeta(month ?: 0, year)
}

private fun isNoInstructional(text: String): Boolean =
    text.contains("no instructional") || text.contains("non instructional") || text.contains("noinstructional")

private fun isInstructionalWord(text: String): Boolean =
    text.contains("instructional") || text.contains("working")

private fun isHolidayWord(text: String): Boolean = HOLIDAY_WORDS.any { text.contains(it) }

private fun eventText(event: CalendarEvent): String =
    event.text.ifBlank { event.category }.ifBlank { event.type }

/**
 * Builds the seven days of the week [weekOffset] weeks from now, Monday first.
 *
 * [calendarMonths] is the already-resolved month set (the preferred calendar when
 * the user has chosen one), [exams] the semester's papers, and
 * [deadlineDates] the dates carrying an assignment deadline, which the strip uses
 * only to decide whether a day is worth opening.
 */
fun buildHomeWeekDays(
    weekOffset: Int,
    today: LocalDate,
    exams: List<ExamItem>,
    calendarMonths: List<CalendarMonth>,
    deadlineDates: Set<LocalDate>
): List<HomeWeekDay> {
    val base = today.plus(DatePeriod(days = 7 * weekOffset))
    // Monday-first. `DayOfWeek.MONDAY.ordinal` is 0, so this is the count of days
    // already elapsed in the week; the web's `DAYS` array is MON-first too and
    // indexing it with a Sunday-first `getDay()` is the off-by-one this avoids.
    // `LocalDate` has `plus(DatePeriod)` but no `minus(DatePeriod)`, hence the
    // negated period.
    val monday = base.plus(DatePeriod(days = -base.dayOfWeek.ordinal))
    val metas = calendarMonths.map { monthMetaOf(it.month, today.year) }
    val parsedExams = exams.mapNotNull { exam -> exam.examDateParsed?.let { it to exam } }

    return AttendanceDay.entries.mapIndexed { index, code ->
        val date = monday.plus(DatePeriod(days = index))

        var holidayInfo: String? = null
        var orderInfo: String? = null
        var detectedDayOrder: AttendanceDay? = null
        var isInstructional = false

        for ((monthIndex, month) in calendarMonths.withIndex()) {
            val meta = metas[monthIndex]
            // Both year and month must match. A "Sept" block in a 2025 calendar is
            // not a description of September 2026 just because the name matches.
            if (meta.year != date.year) continue
            if (meta.month != 0 && meta.month != date.monthNumber) continue

            val dayEntry = month.days.firstOrNull { it.date == date.dayOfMonth } ?: continue
            for (event in dayEntry.events) {
                val text = eventText(event)
                val norm = text.lowercase()
                if (norm.isBlank()) continue

                val noInstructional = isNoInstructional(norm)
                val instructionalWord = isInstructionalWord(norm)
                val holidayWord = isHolidayWord(norm)

                if (noInstructional || (holidayWord && !instructionalWord)) {
                    holidayInfo = text
                } else if (instructionalWord && !noInstructional) {
                    isInstructional = true
                }

                val override = extractDayOrderOverride(text)
                if (override != null) {
                    detectedDayOrder = override
                    orderInfo = text
                    isInstructional = true
                }
            }
        }

        HomeWeekDay(
            dayCode = code,
            date = date,
            isToday = date == today,
            exams = parsedExams.filter { it.first == date }.map { it.second },
            holidayInfo = if (isInstructional) null else holidayInfo,
            detectedDayOrder = detectedDayOrder,
            orderInfo = orderInfo,
            isInstructional = isInstructional,
            hasDeadline = date in deadlineDates
        )
    }
}

/** "Sept 2026" when the week sits inside one month, "Aug - Sept 2026" when it straddles two. */
fun homeWeekHeader(days: List<HomeWeekDay>, today: LocalDate): String {
    val first = days.firstOrNull() ?: return ""
    val last = days.lastOrNull() ?: return ""
    val lastYear = last.date.year.toString()
    return if (first.date.year == last.date.year && first.date.monthNumber == last.date.monthNumber) {
        "${first.date.month.label()} ${first.date.year}"
    } else if (first.date.year == last.date.year) {
        "${first.date.month.label()} - ${last.date.month.label()} $lastYear"
    } else {
        "${first.date.month.label()} ${first.date.year} - ${last.date.month.label()} ${last.date.year}"
    }
}

/** "Tuesday, Sept 29, 2026" — the sub-header under the strip. */
fun HomeWeekDay.longLabel(): String = buildString {
    append(dayCode.name.lowercase().replaceFirstChar { it.uppercase() })
    append(", ")
    append(date.month.label())
    append(' ')
    append(date.dayOfMonth)
    append(", ")
    append(date.year)
}

// ── Tasks ──

/** The task kinds the home's list renders, and the tone each carries. */
enum class HomeTaskKind(val label: String, val tone: HomeTone) {
    HOMEWORK("Homework", HomeTone.ACCENT),
    QUIZ("Quiz", HomeTone.WARNING),
    ASSIGNMENT("Assignment", HomeTone.ACCENT),
    LAB("Lab", HomeTone.SUCCESS),
    PROJECT("Project", HomeTone.ACCENT),
    EXAM("Exam", HomeTone.DANGER),
    OTHER("Task", HomeTone.NEUTRAL);

    companion object {
        fun from(raw: String?): HomeTaskKind = when (raw?.trim()?.lowercase()) {
            "quiz" -> QUIZ
            "assignment" -> ASSIGNMENT
            "lab" -> LAB
            "project" -> PROJECT
            "exam" -> EXAM
            "homework", "lms_auto" -> HOMEWORK
            else -> OTHER
        }
    }
}

data class HomeTaskRow(
    val id: String,
    val kind: HomeTaskKind,
    val courseCode: String,
    val title: String
)

/**
 * The tasks due on the day being viewed.
 *
 * `dueDate` is `YYYY-MM-DD`, written by the task editor and compared as a string
 * throughout the app, so it is compared as one here too. Completed tasks never
 * appear.
 */
fun homeTasksForDay(tasks: List<HomeworkTask>, date: LocalDate): List<HomeTaskRow> =
    tasks
        .filter { !it.completed && it.dueDate == date.toString() }
        .sortedWith(compareBy({ it.dueTime }, { it.courseCode }))
        .map {
            HomeTaskRow(
                id = it.id,
                kind = HomeTaskKind.from(it.type),
                courseCode = it.courseCode,
                title = it.title
            )
        }

// ── Insight slides ──

/**
 * One page of the rotating stat card.
 *
 * A tile is a measurement that does not change under the reader; a set of
 * values worth rotating through is a carousel. That distinction is the web's
 * (`StatTile` vs `InsightCarousel`) and it is why the attendance tile is pinned
 * beside this rather than being one of its slides.
 */
data class HomeInsightSlide(
    val id: String,
    /** The small kicker, e.g. "CGPA". */
    val label: String,
    /** The big number or code. */
    val value: String,
    val sub: String?,
    /** The top-right pill. */
    val badge: String?,
    val tone: HomeTone,
    /** The CGPA privacy toggle blurs the value in place. */
    val blurred: Boolean = false,
    val onClick: () -> Unit
)

/** Every date carrying an assignment deadline, for the week strip's "worth opening" hint. */
fun deadlineDates(tasks: List<HomeworkTask>): Set<LocalDate> =
    tasks.filter { !it.completed }
        .mapNotNull { runCatching { LocalDate.parse(it.dueDate) }.getOrNull() }
        .toSet()

/**
 * The earliest paper that has not finished yet, judged on the clock.
 *
 * Not on the calendar: a CAT that ended at 12:30 is over by three in the
 * afternoon even though it is still "today", so it must not sit on the insight
 * card. Returns null when the term is over.
 */
fun nextUpcomingExam(exams: List<ExamItem>, now: Instant): ExamItem? {
    val dated = exams.filter { it.examDateParsed != null }
    val candidates = dated.mapNotNull { exam ->
        val end = com.amazecc.app.shared.utils.ExamUtils.examEndInstant(exam) ?: return@mapNotNull null
        exam to end
    }.filter { (_, end) -> end > now }

    return candidates.minByOrNull { (_, end) -> end }?.first
        ?: dated.minByOrNull { exam -> exam.examDateParsed ?: LocalDate(2100, 1, 1) }
}

/** One dated thing a student still owes work on. */
data class HomeDeadline(
    val id: String,
    val title: String,
    val courseCode: String,
    val date: LocalDate
)

/**
 * The next outstanding Moodle deadline.
 *
 * ## Where the date comes from
 *
 * Two sources, in order of trust:
 *
 * 1. The `year` / `monthNumber` / `dayOfMonth` integers the payload already
 *    carries. These are read straight off Moodle's calendar event, so they need
 *    no interpretation and no locale.
 * 2. [NotificationsUtils.deadlineDate] on the `due` string, for payloads that
 *    carry only a machine-readable date.
 *
 * ## What is deliberately not parsed
 *
 * `due` itself. The endpoint scrapes it from Moodle's rendered activity page, so
 * it is prose — `"Monday, 28 September 2026, 23:59:00"` — and it changes with
 * the account's Moodle language, the site's phrasing, and whether the course
 * sets a due time. Writing a parser for it would be a guess that silently
 * degrades to "no deadline" for exactly the students whose Moodle is not in
 * English, which is the worst way for this to fail. The numbers exist for this.
 *
 * Submitted and hidden assignments are excluded: a deadline that has already
 * been met is not a deadline, and the app's own scheduler skips them for the
 * same reason.
 */
fun nextMoodleDeadline(
    assignments: List<com.amazecc.app.shared.model.MoodleAssignment>,
    today: LocalDate,
    tz: TimeZone = TimeZone.currentSystemDefault()
): HomeDeadline? =
    assignments
        .asSequence()
        .filter { !it.done && !it.hidden }
        .mapNotNull { assignment ->
            val date = assignment.dueDate
                ?: assignment.due.takeIf { it.isNotBlank() }?.let { NotificationsUtils.deadlineDate(it, tz) }
                ?: return@mapNotNull null
            HomeDeadline(
                id = assignment.name,
                title = assignment.taskTitle,
                courseCode = assignment.courseCode,
                date = date
            )
        }
        .filter { it.date >= today }
        .minByOrNull { it.date.toString() + it.title }

/** Today's date in the device's zone. The one place the clock is read directly. */
fun homeToday(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** Minutes since midnight, for the live-class clock. */
fun homeNowMinutes(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return now.hour * 60 + now.minute
}
