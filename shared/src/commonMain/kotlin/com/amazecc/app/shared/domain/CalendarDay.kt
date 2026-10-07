package com.amazecc.app.shared.domain

import com.amazecc.app.shared.utils.AttendanceDay as TeachingDay
import com.amazecc.app.shared.utils.ExamUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * The academic-calendar day model.
 *
 * Ported from `../AmazeCC/src/lib/calendarDay.ts`, which took this out of
 * `CalendarView.tsx` so the month grid and the day sheet could be two
 * components reading one model. Two rules everything else follows from:
 *
 *  1. **One event shape, discriminated by [EventKind].** The old code
 *     re-derived an event's type from its text on every render and every sort.
 *     Classification happens once, in [buildEventSource]s' callers, and the
 *     tone / label / title are stored on the event.
 *  2. **Day type is a property of the day, not of any one event.** A day with an
 *     exam on a holiday is a holiday that has an exam, and the log and the grid
 *     both need to be able to say that.
 *
 * The month-level assembly lives in `CalendarEnrich.kt`; everything here is
 * about a single day or a single event.
 */

/** The calendar variants VTOP exposes, and their human labels. */
val CALENDAR_TYPES: Map<String, String> = linkedMapOf(
    "ALL" to "General Semester",
    "ALL02" to "General Flexible",
    "ALL03" to "General Freshers",
    "ALL05" to "General LAW",
    "ALL06" to "Flexible Freshers",
    "ALL08" to "Cohort LAW",
    "ALL11" to "Flexible Research",
    "WEI" to "Weekend Intra Semester",
)

/** Every kind of thing that can land on a day, in one union. */
enum class EventKind { EXAM, MILESTONE, ASSIGNMENT, OD, CLASS, HOLIDAY, WORKING, EVENT }

/**
 * What kind of day this is.
 *
 * Three questions that used to be conflated into one, and the reason this is an
 * enum rather than a boolean:
 *
 *  - [INSTRUCTIONAL]   - classes run.
 *  - [SEMIHOLIDAY]     - a *shortened* list. Classes still run, fewer of them.
 *  - [NON_INSTRUCTIONAL] - **no classes, but the college is open.** Staff are on
 *                         campus; you are expected in. Not a day off.
 *  - [HOLIDAY]         - the college is shut.
 *  - [OTHER]           - nothing published. Not a claim in either direction.
 *
 * The non-instructional / holiday split is the one that matters. Folding them
 * together is how a working day ends up painted red and labelled a day off, and
 * it is what the old page did: "no instructional" was in the holiday keyword
 * list, so every non-teaching working day came back as a holiday.
 */
enum class DayType { INSTRUCTIONAL, SEMIHOLIDAY, NON_INSTRUCTIONAL, HOLIDAY, OTHER }

/** A calendar event as it arrives from VTOP, or from a source injected into the same list. */
data class RawCalendarEvent(
    val type: String? = null,
    val text: String? = null,
    val category: String? = null,
    val color: String? = null,
    /** Moodle submission link. */
    val url: String? = null,
    /** Moodle withholds the grades of a hidden submission until the deadline. */
    val hidden: Boolean? = null,
    /** Moodle deadline, on a moodle event. */
    val due: String? = null,
    /** The attendance status, on a class event. */
    val status: String? = null,
    val courseCode: String? = null,
    val courseTitle: String? = null,
    val slotName: String? = null,
    /** OD hours. */
    val hours: Int? = null,
) {
    fun classifiable(): ClassifiableEvent =
        ClassifiableEvent(type = type, text = text, category = category)
}

/**
 * A classified calendar event.
 *
 * [priority] is the sort weight inside a day - lower is more important - and
 * [tone] is the marker colour family the grid draws. Both are derived from
 * [kind] unless a caller overrides them, which nothing does.
 */
data class DayEvent(
    val kind: EventKind,
    val title: String,
    val detail: String? = null,
    val priority: Int,
    val tone: String,
    val courseCode: String? = null,
    val courseTitle: String? = null,
    val slotName: String? = null,
    /** Moodle submission link. */
    val url: String? = null,
    /** Moodle marks a submission as hidden while its grades are withheld. */
    val hidden: Boolean? = null,
    /** The deadline, for anything that has one (assignment, task). */
    val dueAt: LocalDate? = null,
    /** The exam series this paper belongs to, in whatever spelling VTOP used. */
    val series: String? = null,
    /**
     * Whether classes still run on this event's day. Set on milestones only, and
     * `false` for an assessment (a CAT, a mid-term) and `true` for a boundary
     * (an LID, which is the last day you attend).
     */
    val classesRun: Boolean? = null,
    /** The papers of a milestone, when a milestone on this day has been folded into one event. */
    val papers: List<DayEvent>? = null,
    /** A class you were marked absent for. */
    val absent: Boolean? = null,
    /** OD hours, when this event is an OD record. */
    val hours: Int? = null,
    /** The task this came from, when it came from the task store. */
    val taskId: String? = null,
    /** The user's own photo, for events they registered for themselves. */
    val avatarUrl: String? = null,
    /** The EventHub id, so a row can deep-link back to the registration. */
    val eventhubId: String? = null,
    val raw: RawCalendarEvent? = null,
)

/** One course's record of having been taught (or not) on a day. */
data class DayClassRecord(
    val courseCode: String,
    val courseTitle: String,
    val status: String,
    /** `YYYY-MM-DD` - the key the notes tracker is written under. */
    val date: String,
    /**
     * The date string exactly as VTOP wrote it in `viewLink[].date`.
     *
     * The Theory and Lab log pages write it under the raw string, and writing a
     * normalised `YYYY-MM-DD` here instead would make the two pages disagree
     * about whether notes for the 12th are already secured.
     */
    val rawDate: String,
)

data class DayAttendance(
    val held: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val onDuty: Int = 0,
    val courses: List<DayClassRecord> = emptyList(),
)

/** One date on the calendar, fully classified. */
data class DayModel(
    /** 1-31. */
    val date: Int,
    val fullDate: LocalDate,
    /** `YYYY-MM-DD`, the join key against tasks, moodle and OD data. */
    val dateKey: String,
    /** `Mon`, `Tue`, ... - the calendar weekday, which is not always the teaching day. */
    val weekday: String,
    /**
     * The timetable this date actually follows, when the college said so.
     *
     * A "day order" is a reschedule: the college publishes
     * `"Instructional Day (Instructional Day Order - Thursday Day Order)"` and
     * means that *on this date* you attend your **Thursday** classes, whatever
     * weekday this date falls on. It exists to recover teaching lost to a
     * holiday, so it lands on Saturdays surprisingly often, and it is the reason
     * [weekday] alone cannot answer "what do I have today".
     *
     * `null` means the ordinary case - this date follows its own weekday - which
     * is the majority of days and must stay the cheap path.
     */
    val dayOrder: TeachingDay? = null,
    val dayType: DayType,
    val events: List<DayEvent>,
    val attendance: DayAttendance,
    /** Number of unfinished tasks due today. */
    val taskCount: Int,
)

data class MonthSummary(
    val total: Int,
    val working: Int,
    val holiday: Int,
    val other: Int,
)

data class MonthModel(
    val id: String,
    /** `August 2026` */
    val label: String,
    /** `Aug 2026` */
    val shortLabel: String,
    /** 0-based, matching the `Date` month index this model's rules were written against. */
    val monthIndex: Int,
    val year: Int,
    val days: List<DayModel>,
    val summary: MonthSummary,
)

/** An event with no meaningful text still has to render as something. */
const val GENERIC_TITLE = "Untitled event"

val KIND_TONE: Map<EventKind, String> = mapOf(
    EventKind.EXAM to "amber",
    EventKind.MILESTONE to "indigo",
    EventKind.ASSIGNMENT to "violet",
    EventKind.OD to "sky",
    EventKind.CLASS to "emerald",
    EventKind.HOLIDAY to "red",
    EventKind.WORKING to "zinc",
    EventKind.EVENT to "zinc",
)

val KIND_LABEL: Map<EventKind, String> = mapOf(
    EventKind.EXAM to "Exam",
    EventKind.MILESTONE to "Milestone",
    EventKind.ASSIGNMENT to "Assignment",
    EventKind.OD to "OD",
    EventKind.CLASS to "Class",
    EventKind.HOLIDAY to "Holiday",
    EventKind.WORKING to "Day Type",
    EventKind.EVENT to "Event",
)

data class LegendItem(val tone: String, val label: String)

/** What the month grid draws, in legend order. */
val LEGEND_ITEMS: List<LegendItem> = listOf(
    LegendItem(KIND_TONE.getValue(EventKind.CLASS), "Class"),
    LegendItem(KIND_TONE.getValue(EventKind.EXAM), "Exam"),
    LegendItem(KIND_TONE.getValue(EventKind.ASSIGNMENT), "Assignment"),
    LegendItem(KIND_TONE.getValue(EventKind.MILESTONE), "Milestone"),
    LegendItem(KIND_TONE.getValue(EventKind.OD), "OD"),
    LegendItem(KIND_TONE.getValue(EventKind.HOLIDAY), "Holiday"),
)

private val DEFAULT_PRIORITY: Map<EventKind, Int> = mapOf(
    EventKind.EXAM to 0,
    EventKind.MILESTONE to 1,
    EventKind.ASSIGNMENT to 2,
    EventKind.CLASS to 3,
    EventKind.OD to 4,
    EventKind.HOLIDAY to 5,
    EventKind.WORKING to 6,
    EventKind.EVENT to 7,
)

internal fun makeEvent(
    kind: EventKind,
    title: String? = null,
    detail: String? = null,
    priority: Int? = null,
    courseCode: String? = null,
    courseTitle: String? = null,
    slotName: String? = null,
    url: String? = null,
    hidden: Boolean? = null,
    dueAt: LocalDate? = null,
    series: String? = null,
    classesRun: Boolean? = null,
    papers: List<DayEvent>? = null,
    absent: Boolean? = null,
    hours: Int? = null,
    taskId: String? = null,
    avatarUrl: String? = null,
    eventhubId: String? = null,
    raw: RawCalendarEvent? = null,
): DayEvent {
    val trimmed = (title ?: "").trim()
    return DayEvent(
        kind = kind,
        title = trimmed.ifEmpty { GENERIC_TITLE },
        detail = detail,
        priority = priority ?: DEFAULT_PRIORITY.getValue(kind),
        tone = KIND_TONE.getValue(kind),
        courseCode = courseCode,
        courseTitle = courseTitle,
        slotName = slotName,
        url = url,
        hidden = hidden,
        dueAt = dueAt,
        series = series,
        classesRun = classesRun,
        papers = papers,
        absent = absent,
        hours = hours,
        taskId = taskId,
        avatarUrl = avatarUrl,
        eventhubId = eventhubId,
        raw = raw,
    )
}

/** Human name for a kind, independent of any event. */
fun kindLabel(kind: EventKind): String = KIND_LABEL.getValue(kind)

// ── day-type text ─────────────────────────────────────────────────────────────

private val SEMI_HOLIDAY_KEYWORDS = listOf("cat - i", "cat - ii", "technovit", "vibrance", "oneday")

/**
 * The type words VTOP writes into `text`, which say no more than the kind.
 */
private val TYPE_WORDS = listOf(
    "instructional day",
    "no instructional day",
    "non instructional day",
    "noninstructional day",
    "holiday",
    "working day",
    "working",
)

private fun isTypeWord(value: String): Boolean = TYPE_WORDS.contains(value.lowercase())

/**
 * A shortened class list is a *semi*-holiday, not an "other" day.
 *
 * VTOP marks these on the day itself rather than in the day's type, so a CAT
 * weekend reads as an ordinary teaching day until you read the text.
 */
internal fun isSemiHolidayEvent(e: RawCalendarEvent): Boolean {
    if (CalendarClassifier.isHolidayEvent(e.classifiable())) return false
    if (CalendarClassifier.isNonInstructionalEvent(e.classifiable())) return false
    val haystack = "${e.text.orEmpty()} ${e.category.orEmpty()}".lowercase()
    return SEMI_HOLIDAY_KEYWORDS.any { haystack.contains(it) }
}

/**
 * The best name for a day-type entry.
 *
 * VTOP writes these as `text (category)` and the split is consistent: `text` is
 * a type word, `category` is the name. `"Holiday (Gandhi Jayanthi)"`,
 * `"No Instructional Day"`, `"Instructional Day (Working Day)"`,
 * `"Instructional Day (Instructional Day Order - Friday Day Order)"`.
 *
 * So the category wins the title, the type word is dropped, and whatever
 * survives is capitalised. The category is split on `/` and each segment that is
 * only a type word is discarded, which is what turns `"Working Day / LAB FAT"`
 * into `"LAB FAT"` while leaving a bare `"Working Day"` with nothing to say.
 * Only `/` splits: the `-` in `"Instructional Day Order - Friday Day Order"` is
 * part of the name, not a separator, so that prefix is stripped instead and
 * `"Friday Day Order"` is what remains.
 *
 * The subtitle is the `text` only when it is not itself a type word, so a row
 * never prints "Working day" twice.
 */
internal fun dayTypeTitle(e: RawCalendarEvent, fallback: String): Pair<String, String?> {
    val text = (e.text ?: "").trim()
    val cat = (e.category ?: "").trim()

    val segments = if (cat.isNotEmpty()) {
        cat.split(Regex("\\s*/\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isTypeWord(it) }
    } else {
        emptyList()
    }

    val name = segments
        .joinToString(" · ")
        .replaceFirst(Regex("(?i)^instructional day order\\s*[-–:]?\\s*"), "")
        .trim()

    if (name.isEmpty()) return fallback.ifEmpty { "Event" } to null

    return name.replaceFirstChar { it.uppercaseChar() } to text.takeIf {
        !isTypeWord(it) && !it.equals(name, ignoreCase = true)
    }
}

private fun stripPrefix(text: String?, prefix: String): String =
    (text ?: "").replaceFirst(prefix, "").trim()

private fun unclassifiedEvent(e: RawCalendarEvent): DayEvent {
    // The catch-all, and the only branch that flips title and detail.
    //
    // Everywhere else the title is a name we chose - "Working Day", "CAT II", the
    // holiday's own text - and the detail is supporting context. Here we have
    // neither: VTOP gave two free-text fields and no type, and for a plain
    // college notice the `category` is the name worth showing ("Gandhi Jayanti")
    // while `text` is the rest ("Gandhi Jayanthi"). Reading them the other way
    // round puts the weaker string in the position the eye reads first.
    val title = (e.category ?: "").trim()
        .ifEmpty { (e.text ?: "").trim().ifEmpty { KIND_LABEL.getValue(EventKind.EVENT) } }
    val rest = (e.text ?: "").trim()
    // Identical fields would render the same string twice, once bold and once not.
    return makeEvent(kind = EventKind.EVENT, title = title, detail = rest.takeIf { it != title }, raw = e)
}

/**
 * Classify one raw calendar entry.
 *
 * Order is load-bearing: the day-type branches come before the milestone match
 * because VTOP's day-type entries carry `text: "Instructional Day"` and put the
 * milestone in `category` - `"Working Day / LID for LAB classes"` - so the
 * instructional branch would claim it and the LID would vanish.
 */
fun classify(e: RawCalendarEvent): DayEvent {
    when (e.type) {
        "exam" -> return makeEvent(
            kind = EventKind.EXAM,
            title = stripPrefix(e.text, "exam").ifEmpty { e.text },
            detail = e.category,
            raw = e,
        )
        "moodle" -> {
            val text = e.text
            val title = if (!text.isNullOrEmpty()) stripPrefix(text, "[Moodle]").ifEmpty { text } else GENERIC_TITLE
            return makeEvent(
                kind = EventKind.ASSIGNMENT,
                title = title,
                detail = e.category,
                dueAt = e.due?.let { parseDayDate(it) },
                url = e.url,
                hidden = e.hidden,
                raw = e,
            )
        }
        "homework" -> return makeEvent(
            kind = EventKind.ASSIGNMENT,
            title = e.category?.takeIf { it.isNotEmpty() } ?: stripPrefix(e.text, "[HW]"),
            detail = e.courseTitle,
            raw = e,
        )
        "od" -> return makeEvent(
            kind = EventKind.OD,
            title = e.courseTitle?.takeIf { it.isNotEmpty() } ?: e.text?.takeIf { it.isNotEmpty() } ?: "On-Duty",
            detail = "On-Duty",
            hours = e.hours,
            raw = e,
        )
        "attendance" -> return makeEvent(
            kind = EventKind.CLASS,
            title = e.courseTitle?.takeIf { it.isNotEmpty() } ?: e.text?.takeIf { it.isNotEmpty() } ?: "Class",
            detail = e.category,
            courseCode = e.courseCode,
            courseTitle = e.courseTitle,
            slotName = e.slotName,
            absent = e.status == "absent",
            raw = e,
        )
    }

    val classifiable = e.classifiable()
    if (CalendarClassifier.isHolidayEvent(classifiable)) {
        val (title, detail) = dayTypeTitle(e, "Holiday")
        return makeEvent(kind = EventKind.HOLIDAY, title = title, detail = detail, raw = e)
    }
    if (CalendarClassifier.isNonInstructionalEvent(classifiable)) {
        // The payload's own wording is the name here: a non-instructional day has
        // no category at all, so "No Instructional Day" is the best thing to show.
        val (title, detail) = dayTypeTitle(e, (e.text ?: "").trim().ifEmpty { "No classes" })
        return makeEvent(kind = EventKind.WORKING, title = title, detail = detail, raw = e)
    }

    // Checked *before* the instructional test, and this ordering is load-bearing
    // twice over. A semester boundary is not the same fact as "teaching happens".
    val milestone = CalendarClassifier.matchImportantEvent(classifiable)
    if (milestone != null) {
        return makeEvent(
            kind = EventKind.MILESTONE,
            title = milestone.short,
            detail = milestone.blurb,
            classesRun = milestone.classesRun,
            raw = e,
        )
    }

    if (CalendarClassifier.isInstructionalEvent(classifiable)) {
        val (title, detail) = dayTypeTitle(e, "Working day")
        return makeEvent(kind = EventKind.WORKING, title = title, detail = detail, raw = e)
    }
    return unclassifiedEvent(e)
}

// ── instructional day order ───────────────────────────────────────────────────

val TEACHING_DAY_LABEL: Map<TeachingDay, String> = mapOf(
    TeachingDay.MON to "Monday",
    TeachingDay.TUE to "Tuesday",
    TeachingDay.WED to "Wednesday",
    TeachingDay.THU to "Thursday",
    TeachingDay.FRI to "Friday",
    TeachingDay.SAT to "Saturday",
    TeachingDay.SUN to "Sunday",
)

private val WEEKDAY_IN_TEXT = Regex("""(?i)\b(mon|tue|tues|wed|weds|thu|thur|thurs|fri|sat|sun)[a-z]*""")

private val WEEKDAY_KEY: Map<String, TeachingDay> = mapOf(
    "mon" to TeachingDay.MON, "monday" to TeachingDay.MON,
    "tue" to TeachingDay.TUE, "tues" to TeachingDay.TUE, "tuesday" to TeachingDay.TUE,
    "wed" to TeachingDay.WED, "weds" to TeachingDay.WED, "wednesday" to TeachingDay.WED,
    "thu" to TeachingDay.THU, "thur" to TeachingDay.THU, "thurs" to TeachingDay.THU,
    "thursday" to TeachingDay.THU,
    "fri" to TeachingDay.FRI, "friday" to TeachingDay.FRI,
    "sat" to TeachingDay.SAT, "saturday" to TeachingDay.SAT,
    "sun" to TeachingDay.SUN, "sunday" to TeachingDay.SUN,
)

/**
 * The stem of a weekday, plus whatever letters the rest of the word is made of.
 *
 * The tail is `[a-z]*` and the *whole* match is then looked up in a table
 * rather than matched against a list of suffixes. Enumerating the suffixes is
 * the obvious way to write this and it is wrong in a way that hides: the seven
 * long forms are `day / sday / nesday / rsday / urday / nes / rs` depending on
 * the stem, and forgetting one silently loses that weekday completely - a
 * Tuesday reschedule then reads as an ordinary teaching day, which is the exact
 * failure this is here to prevent. Looking the word up cannot forget a case, and
 * it also gives a place to put the abbreviations VTOP actually writes (`SAT`).
 *
 * `[a-z]*` is greedy, so `"monsoon"` matches whole and is then *not* in the
 * table - skipped, rather than read as a Monday.
 */
internal fun lastWeekdayIn(text: String): TeachingDay? {
    var found: TeachingDay? = null
    for (m in WEEKDAY_IN_TEXT.findAll(text)) {
        WEEKDAY_KEY[m.value.lowercase()]?.let { found = it }
    }
    return found
}

/**
 * The day order the college published for a date, if any.
 *
 * VTOP writes it into the `category` of an instructional-day entry, under any of
 * these shapes:
 *
 *   `"Instructional Day Order - Thursday Day Order"`   <- the full phrase
 *   `"Instructional Day Order - Thursday Day Order"`   <- en dash
 *   `"Thursday Day Order"`                             <- already stripped
 *   `"Instructional Day Order: Thursday"`
 *   `"Instructional Day Order - SAT"`
 *
 * Three decisions worth stating, because all three are load-bearing:
 *
 *  - **Only instructional entries are read.** A holiday named "Sunday
 *    Observance" or a festival containing a weekday must not be able to declare
 *    a reschedule, so the scan is restricted to events
 *    [CalendarClassifier.isInstructionalEvent] accepts.
 *  - **The LAST weekday named wins.** A reschedule lands on Saturdays
 *    surprisingly often - that is the whole point of publishing one - and the
 *    natural way to write it names the real date first. A Saturday row reading
 *    `"Saturday - Instruction Day Order - Thursday Day Order"` has two weekdays
 *    in it, and the first one is not the answer.
 *  - **`text` is searched too, not just `category`.** VTOP has been seen
 *    splitting the phrase across the two fields, and a reschedule is far too
 *    consequential to drop because it arrived in the other one.
 */
fun parseDayOrder(events: List<RawCalendarEvent>): TeachingDay? {
    var found: TeachingDay? = null
    for (e in events) {
        if (!CalendarClassifier.isInstructionalEvent(e.classifiable())) continue
        found = lastWeekdayIn("${e.text.orEmpty()} ${e.category.orEmpty()}") ?: found
    }
    return found
}

/**
 * A one-line note for the day sheet, or `null` when the day is ordinary.
 *
 * The timetable changing under you with no explanation is the failure this whole
 * feature exists to prevent, so anything that reads a day's classes has to be
 * able to say *why* they are not that weekday's.
 */
fun dayOrderNote(dayOrder: TeachingDay?, fullDate: LocalDate): String? {
    if (dayOrder == null) return null
    val actual = TeachingDay.entries[fullDate.dayOfWeek.isoDayNumber - 1]
    if (actual == dayOrder) return null
    return "Following the ${TEACHING_DAY_LABEL.getValue(dayOrder)} timetable " +
        "on this ${TEACHING_DAY_LABEL.getValue(actual)}."
}

// ── folding ───────────────────────────────────────────────────────────────────

/**
 * Fold a milestone's papers into the milestone.
 *
 * The academic calendar says a milestone happens; the exam schedule says which
 * papers, where and when. Those are one event described twice, and rendering
 * them as separate rows is what the user sees as duplication - a "CAT II"
 * milestone beside a "CAT2" paper that is the same event. So the papers move
 * under the milestone, keyed on the canonical series name so any spelling of the
 * series folds into the same parent.
 *
 * Papers that match no milestone stay at the top level; a FAT paper with no
 * milestone entry on the calendar is still an event.
 *
 * It also settles two milestones competing for one date, which is the other way
 * this arrives. VTOP will put a "CAT - I" row and a "CAT - II" row on the *same*
 * date - around a combined test block the calendar marks the surrounding dates
 * and the reader sees two tests where the schedule has one. The exam schedule is
 * the authority on which of them actually happened, because it is the system
 * that has to name a room and a seat. So once papers have been folded in, a
 * milestone that owns some has been confirmed, and a rival assessment with none
 * has not - the rival goes.
 *
 * Two constraints keep this from eating real information:
 *
 *  - if *no* milestone owns papers there is no authority to appeal to, so
 *    nothing is dropped. Two CATs on one date with no schedule data is two
 *    things the college said, and guessing between them would be inventing a
 *    date.
 *  - only assessments are dropped. An LID is not a rival, it is a separate
 *    claim about the last day of instruction, and it is not an "assessment" even
 *    though it shares the [EventKind.MILESTONE] kind.
 *
 * The claim set is index-based rather than value-based: the original holds the
 * event *objects*, so two structurally identical papers claim separately, and a
 * set keyed on `equals` would silently claim both.
 */
internal fun foldPapersIntoMilestones(events: List<DayEvent>): List<DayEvent> {
    val milestones = events.filter { it.kind == EventKind.MILESTONE }
    if (milestones.isEmpty()) return events

    val exams = events.filter { it.kind == EventKind.EXAM }
    val claimed = BooleanArray(exams.size)

    val folded = milestones.map { m ->
        val mine = exams.indices.filter { ExamSeries.sameSeries(exams[it].series.orEmpty(), m.title) }
        mine.forEach { claimed[it] = true }
        if (mine.isNotEmpty()) m.copy(papers = mine.map { exams[it] }) else m
    }

    // The schedule has spoken. Every milestone carrying papers is confirmed; an
    // assessment with no papers beside a confirmed one is the calendar's noise.
    val confirmed = folded.filter { (it.papers?.size ?: 0) > 0 }
    val kept = if (confirmed.isEmpty()) {
        folded
    } else {
        folded.filter { (it.papers?.size ?: 0) > 0 || it.classesRun == true }
    }

    return kept +
        events.filter { it.kind != EventKind.EXAM && it.kind != EventKind.MILESTONE } +
        exams.filterIndexed { i, _ -> !claimed[i] }
}

// ── day queries ───────────────────────────────────────────────────────────────

/**
 * The exam papers on one date.
 *
 * Papers normally sit at the top level of a day, but a paper that belongs to a
 * milestone on the same day is folded into that milestone - see
 * [foldPapersIntoMilestones]. So this flattens both, and callers that ask
 * "does this day have a paper" cannot be fooled by the folding.
 */
fun examsOn(day: DayModel): List<DayEvent> =
    day.events.flatMap { e -> if (e.kind == EventKind.EXAM) listOf(e) else e.papers.orEmpty() }

/** Milestones on a day, with any folded papers still attached. */
fun milestonesOn(day: DayModel): List<DayEvent> =
    day.events.filter { it.kind == EventKind.MILESTONE }

/**
 * Everything on a day that is an assessment, as one list.
 *
 * The academic calendar and the exam schedule are two systems writing the same
 * news: one says "CAT - II is on the 12th", the other says "Biology, 9am,
 * Room 204, on the 12th". [foldPapersIntoMilestones] joins them when the series
 * names match, and a reader should never have to know which system won. So a day
 * with a milestone *and* a paper whose series did not match - a FAT landing on
 * the same day - is still one assessment, and belongs in one place rather than
 * as a headline plus a separate section further down the sheet.
 *
 * Milestones lead the order. A milestone is the day's announcement and a loose
 * paper is the detail, and that holds whichever of the two sorts first by
 * [DayEvent.priority] on its own.
 *
 * Distinct from [examsOn], which answers "does this day have a paper" and so
 * flattens the folded ones out of their parents. This one is for display and
 * must not flatten anything.
 */
fun assessmentsOn(day: DayModel): List<DayEvent> =
    day.events
        .filter { it.kind == EventKind.EXAM || it.kind == EventKind.MILESTONE }
        .sortedWith(
            compareBy(
                { if (it.kind == EventKind.MILESTONE) 0 else 1 },
                { it.priority },
                { it.title },
            )
        )

/** The holidays written on a day. */
fun holidaysOn(day: DayModel): List<DayEvent> =
    day.events.filter { it.kind == EventKind.HOLIDAY }

enum class DayHeadlineKind { MILESTONE, EXAM, NON_INSTRUCTIONAL, HOLIDAY, SCHEDULE }

/**
 * What a day is "about" - the slot its sheet leads with.
 *
 * [events] holds every event in that slot, not just one. A milestone that owns
 * its papers leads with itself; a day carrying three loose papers leads with all
 * three, because a reader wants the list and not the first entry of it.
 */
data class DayHeadline(
    val kind: DayHeadlineKind,
    val events: List<DayEvent>,
)

/**
 * Which events a day is about.
 *
 * A day can carry a dozen things at once - a holiday, a club workshop, three CAT
 * papers - and they are not peers. One of them is the day's headline and the
 * rest are footnotes to it, so both the day sheet and the Upcoming list have to
 * agree on which is which. If they picked independently they would drift, and
 * the sheet would say "CAT II" while Upcoming said "Robotics Club" for the same
 * date, which reads as a bug in the data rather than in the rule.
 *
 * So the rule lives here once. The order is a claim about consequence, not about
 * importance: an assessment cancels the timetable, a holiday cancels the
 * college, and an event is what is left over.
 */
fun dayHeadline(day: DayModel): DayHeadline {
    // A milestone that already owns its papers speaks for itself.
    day.events.firstOrNull { it.kind == EventKind.MILESTONE && !it.papers.isNullOrEmpty() }
        ?.let { return DayHeadline(DayHeadlineKind.MILESTONE, listOf(it)) }

    // An assessment milestone with no papers attached is still a milestone the
    // college published. The exam schedule may simply not have been fetched yet,
    // or may file the papers under a name the fold could not match - and either
    // way the day has no classes, which [hasClasses] and [isExamDay] already say
    // by looking at `classesRun`. If this did not lead, the sheet would show a
    // timetable for a day that has none, and the three rules would disagree.
    day.events.firstOrNull { it.kind == EventKind.MILESTONE && it.classesRun == false }
        ?.let { return DayHeadline(DayHeadlineKind.MILESTONE, listOf(it)) }

    // Papers with no milestone to fold under still head the day: no classes run.
    examsOn(day).takeIf { it.isNotEmpty() }
        ?.let { return DayHeadline(DayHeadlineKind.EXAM, it) }

    if (day.dayType == DayType.NON_INSTRUCTIONAL) {
        return DayHeadline(DayHeadlineKind.NON_INSTRUCTIONAL, holidaysOn(day))
    }
    if (day.dayType == DayType.HOLIDAY) {
        return DayHeadline(DayHeadlineKind.HOLIDAY, holidaysOn(day))
    }
    return DayHeadline(DayHeadlineKind.SCHEDULE, emptyList())
}

/**
 * The single event that stands for a day, for callers that can only show one.
 *
 * [dayHeadline] can return a list - three loose papers are three entries in the
 * day's slot - but Upcoming shows one row per date. The first entry stands in,
 * and the rest ride along as extras.
 */
fun primaryEventOn(day: DayModel): DayEvent? = dayHeadline(day).events.firstOrNull()

/**
 * Whether classes run on this day.
 *
 * Four things say they do not, and they are different claims:
 *  - the day type is [DayType.NON_INSTRUCTIONAL] - no teaching, college open;
 *  - the day type is [DayType.HOLIDAY] - the college is shut;
 *  - there is an exam paper on it, from the exam schedule;
 *  - there is an *assessment* milestone on it, from the academic calendar. A CAT
 *    is an exam whether or not the schedule row came through, and a calendar
 *    that says "CAT - II" with no paper attached still is not a teaching day.
 *
 * An LID milestone is the deliberate exception: it is the last day of
 * instruction, so the timetable is exactly what the user needs to see. That
 * distinction is why `classesRun` is carried on the event rather than assumed
 * from "is a milestone".
 */
fun hasClasses(day: DayModel): Boolean {
    if (examsOn(day).isNotEmpty()) return false
    if (day.events.any { it.kind == EventKind.MILESTONE && it.classesRun == false }) return false
    return day.dayType == DayType.INSTRUCTIONAL || day.dayType == DayType.SEMIHOLIDAY
}

/** Whether the college is open that day, independently of whether it teaches. */
fun isCollegeOpen(day: DayModel): Boolean = day.dayType != DayType.HOLIDAY

/** Kept as the calendar-page's headline case: an exam day has no classes. */
fun isExamDay(day: DayModel): Boolean =
    examsOn(day).isNotEmpty() ||
        day.events.any { it.kind == EventKind.MILESTONE && it.classesRun == false }

/**
 * The tones worth drawing on a month cell, in priority order.
 *
 * "Worth drawing" is a rule about the cell, not about the day: a class and an
 * instructional-day marker are the grid's background state, not news, and an
 * absent class outranks an exam because it is the one you can still act on.
 * Lives here rather than in the grid so the rule stays next to [LEGEND_ITEMS],
 * which has to agree with it.
 */
fun dayMarkers(day: DayModel): List<String> {
    val tones = mutableListOf<String>()
    if (day.attendance.absent > 0) tones.add("red")
    for (e in day.events) {
        if (e.kind == EventKind.CLASS || e.kind == EventKind.WORKING) continue
        if (e.tone !in tones) tones.add(e.tone)
    }
    if (tones.isEmpty() && day.attendance.present > 0) tones.add("emerald")
    return tones.take(3)
}

/**
 * Classify a day.
 *
 * [published] is the important input: a date the calendar lists with no events
 * is a *non-instructional day* (the college is open, there is no teaching),
 * whereas a date the calendar never mentions at all is simply outside the
 * published range. Without that distinction every gap in the data reads as a day
 * off, which is the failure mode this whole taxonomy exists to prevent.
 *
 * Order is load-bearing throughout. Non-instructional is tested before
 * instructional because VTOP's "Non Instructional Day" carries a category of
 * "Working day", so the instructional test would otherwise match it too.
 */
internal fun decideDayType(
    vtop: List<RawCalendarEvent>,
    events: List<DayEvent>,
    attendance: DayAttendance,
    published: Boolean,
): DayType {
    if (vtop.any { CalendarClassifier.isHolidayEvent(it.classifiable()) }) return DayType.HOLIDAY
    if (vtop.any { CalendarClassifier.isNonInstructionalEvent(it.classifiable()) }) return DayType.NON_INSTRUCTIONAL
    if (vtop.any { isSemiHolidayEvent(it) }) return DayType.SEMIHOLIDAY

    val hasAssessment = events.any { it.kind == EventKind.EXAM || it.kind == EventKind.ASSIGNMENT }
    if (vtop.any { CalendarClassifier.isInstructionalEvent(it.classifiable()) }) {
        return if (hasAssessment) DayType.SEMIHOLIDAY else DayType.INSTRUCTIONAL
    }

    // Published with nothing on it: a day the college lists but does not teach.
    if (published) return DayType.NON_INSTRUCTIONAL

    // Not published, but classes were actually held on it - the record is
    // stronger evidence than the absence of a calendar entry.
    if (attendance.held > 0) return DayType.INSTRUCTIONAL

    return DayType.OTHER
}

// ── dates ─────────────────────────────────────────────────────────────────────

/**
 * Monday-first weekday header, matching both the grid and `slotMap`.
 */
val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val MONTH_LONG = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val MONTH_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** The Monday-first position of a date, 0-6. */
fun mondayIndex(d: LocalDate): Int = d.dayOfWeek.isoDayNumber - 1

/** The calendar weekday of a date, as the grid spells it. */
fun weekdayShort(d: LocalDate): String = WEEKDAY_SHORT[mondayIndex(d)]

/**
 * `YYYY-MM-DD` for a local date.
 *
 * The join key for everything: tasks, Moodle deadlines, OD records and the
 * `viewLink` history all have to line up on one string, and they all carry
 * different date formats natively.
 */
fun dateKey(d: LocalDate): String {
    val m = d.monthNumber.toString().padStart(2, '0')
    val day = d.dayOfMonth.toString().padStart(2, '0')
    return "${d.year}-$m-$day"
}

private val ISO_PREFIX = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")

/**
 * Parse a date from any of the sources, or `null` when nothing reads it.
 *
 * The `YYYY-MM-DD` case is tried first because a bare ISO string is what
 * `viewLink[].date`, `ODhoursData[].date` and the join keys all use; everything
 * else falls through to the general VTOP date parser, which reads `"Aug 12,
 * 2026"`, `"12 Aug 2026"` and `"19-Nov-2025"`.
 */
fun parseDayDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    val raw = value.trim()

    ISO_PREFIX.find(raw)?.let { m ->
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val d = m.groupValues[3].toIntOrNull() ?: return null
        return try {
            LocalDate(y, mo, d)
        } catch (_: Exception) {
            null
        }
    }

    // The VTOP parser tokenises on whitespace, so a comma runs into the day
    // number ("12," is not all-digits) and the whole date is dropped.
    return ExamUtils.parseExamDateToLocalDate(raw.replace(",", " "))
}

/** Whether a parsed date is usable; `null` is the invalid-Date case. */
fun isValidDay(d: LocalDate?): Boolean = d != null

/** Whole days from [from]. Negative in the past. */
fun daysLeft(target: LocalDate?, from: LocalDate): Int? {
    if (target == null) return null
    return target.toEpochDays() - from.toEpochDays()
}

private fun relativeWeekdayLabel(d: LocalDate): String =
    "${weekdayShort(d)}, ${d.dayOfMonth.toString().padStart(2, '0')} ${MONTH_SHORT[d.monthNumber - 1]}"

fun relativeDayLabel(target: LocalDate?, from: LocalDate): String {
    if (target == null) return ""
    val delta = daysLeft(target, from) ?: return ""
    if (delta == 0) return "Today"
    if (delta == 1) return "Tomorrow"
    if (delta == -1) return "Yesterday"
    return relativeWeekdayLabel(target)
}

fun formatDayHeading(d: LocalDate?): String {
    if (d == null) return ""
    val weekday = TEACHING_DAY_LABEL.getValue(TeachingDay.entries[mondayIndex(d)])
    return "$weekday, ${d.dayOfMonth} ${MONTH_LONG[d.monthNumber - 1]} ${d.year}"
}

/** `YYYY-MM-DD` for today in the system time zone. */
fun todayKey(): String = dateKey(today())

fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

// ── month parsing ─────────────────────────────────────────────────────────────

data class ParsedMonth(val monthIndex: Int, val year: Int)

private val MONTH_NAME_MAP = mapOf(
    "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
    "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11,
)

private val FULL_MONTHS = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)

/**
 * Pull `monthIndex` and `year` out of a calendar's month label.
 *
 * VTOP sends `"August 2026"`, sometimes with a trailing range. Falls back to
 * [fallbackYear], then to today, so a malformed month never produces `NaN`
 * indices that would poison the whole grid.
 */
fun parseCalendarMonth(
    month: String?,
    fallbackYear: Int? = null,
    now: LocalDate = today(),
): ParsedMonth {
    var year: Int? = null
    var monthIndex: Int? = null

    val raw = (month ?: "").trim()
    val match = Regex("""([a-zA-Z]+)\s*(\d{4})""").find(raw)
    if (match != null) {
        monthIndex = MONTH_NAME_MAP[match.groupValues[1].lowercase().take(3)]
        year = match.groupValues[2].toIntOrNull()
    }

    if (monthIndex == null && raw.isNotEmpty()) {
        val lowered = raw.lowercase()
        monthIndex = FULL_MONTHS.indexOfFirst { lowered.contains(it) }.takeIf { it >= 0 }
    }
    if (monthIndex == null && month != null) {
        monthIndex = month.trim().toIntOrNull()?.let { if (it in 1..12) it - 1 else it }
    }
    if (year == null) {
        year = (fallbackYear ?: now.year)
    }

    return ParsedMonth(monthIndex = monthIndex ?: now.monthNumber - 1, year = year)
}

/** `August 2026` / `Aug 2026`. */
fun monthLabel(monthIndex: Int, year: Int, short: Boolean = false): String {
    if (monthIndex !in 0..11) return ""
    return if (short) "${MONTH_SHORT[monthIndex]} $year" else "${MONTH_LONG[monthIndex]} $year"
}

/** Number of Monday-first blanks before the 1st. */
fun leadingBlanks(year: Int, monthIndex: Int): Int {
    val first = try {
        LocalDate(year, monthIndex + 1, 1)
    } catch (_: Exception) {
        return 0
    }
    return (first.dayOfWeek.isoDayNumber + 6) % 7
}
