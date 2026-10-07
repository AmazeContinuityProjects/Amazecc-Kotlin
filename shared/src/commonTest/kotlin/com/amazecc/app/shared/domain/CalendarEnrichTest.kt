package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.MoodleAssignment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The port of the `buildEnrichedCalendars` / `exam days` / `exam series naming` / `class-free days` /
 * `EventHub registrations` / `activeMonthIndex` suites of `AmazeCC/src/__tests__/calendarDay.test.ts`.
 *
 * This is the suite that decides what a day *contains*, and it exists because the old page's month
 * grid and its day sheet each derived that answer for themselves and could disagree - a grid cell
 * showing a dot for a deadline while the sheet under it said "Nothing due today".
 */
class CalendarEnrichTest {

    // ── fixtures ─────────────────────────────────────────────────────────────

    /**
     * A VTOP academic calendar for one month, every day marked instructional.
     *
     * `text` is the type word and `category` carries the name, as the real payload does - so a
     * holiday is `text: "Holiday", category: "Pongal"`. Fixtures that put the name in `text` hid the
     * fact that the day sheet was titling every holiday "Holiday".
     */
    private fun monthCalendar(month: String, holidays: Set<Int> = emptySet()): List<CalendarMonth> =
        listOf(
            CalendarMonth(
                label = month,
                days = (1..31).map { d ->
                    CalendarDay(
                        date = d,
                        events = listOf(
                            if (d in holidays) {
                                CalendarEventItem(type = "Other", text = "Holiday", category = "Pongal")
                            } else {
                                CalendarEventItem(
                                    type = "Other",
                                    text = "Instructional Day",
                                    category = "Working day",
                                )
                            },
                        ),
                    )
                },
            ),
        )

    private fun course(
        code: String,
        title: String = code,
        entries: List<Pair<String, String>>,
    ) = CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = "Embedded Theory",
        slotName = "A1",
        slotVenue = null,
        faculty = "",
        credits = null,
        category = null,
        attendedClasses = 0,
        totalClasses = 0,
        attendancePercentage = "",
        logs = entries.map { AttendanceDay(date = it.first, status = it.second) },
    )

    private fun exam(
        code: String,
        title: String,
        date: String,
        time: String = "",
        venue: String = "",
    ) = Exam(courseCode = code, courseTitle = title, date = date, time = time, venue = venue)

    private fun moodle(name: String, due: String, done: Boolean = false, url: String? = null) =
        MoodleAssignment(name = name, due = due, done = done, url = url)

    private fun task(
        id: String,
        title: String,
        dueDate: String,
        completed: Boolean = false,
    ) = HomeworkTask(
        id = id,
        courseCode = "25BLC1081",
        courseTitle = "",
        title = title,
        dueDate = dueDate,
        createdAt = "",
        completed = completed,
    )

    private fun dayAt(month: MonthModel, date: Int): DayModel = month.days.first { it.date == date }

    private fun build(
        calendars: List<CalendarMonth>,
        examSchedule: Map<String, List<Exam>> = emptyMap(),
        moodle: List<MoodleAssignment> = emptyList(),
        courses: List<CourseAttendance> = emptyList(),
        tasks: List<HomeworkTask> = emptyList(),
        registrations: List<CalendarRegistration> = emptyList(),
        profileImageUrl: String? = null,
    ): List<MonthModel> =
        buildEnrichedCalendars(
            sources = CalendarSources(
                calendars = calendars,
                examSchedule = examSchedule,
                moodle = moodle,
                courses = courses,
                tasks = tasks,
                registeredEvents = registrations,
                profileImageUrl = profileImageUrl,
            ),
            // Pinned, so the fallback-year branch never reads the wall clock.
            now = LocalDate(2026, 8, 1),
        )

    // ── buildEnrichedCalendars ───────────────────────────────────────────────

    @Test
    fun `marks a published holiday as a holiday and a teaching day as instructional`() {
        val august = build(monthCalendar("August 2026", setOf(15))).first()
        assertEquals(DayType.HOLIDAY, dayAt(august, 15).dayType)
        assertEquals(DayType.INSTRUCTIONAL, dayAt(august, 10).dayType)
        // `working` is "has classes" - instructional plus shortened.
        assertEquals(31, august.summary.total)
        assertEquals(1, august.summary.holiday)
        assertEquals(30, august.summary.working)
    }

    @Test
    fun `labels an unknown date as other not as a holiday`() {
        // The old grid classified anything it did not recognise as a holiday, which turned a fetch
        // gap into a red day.
        val august = build(listOf(CalendarMonth(label = "August 2026", days = emptyList()))).first()
        assertEquals(DayType.OTHER, dayAt(august, 1).dayType)
    }

    @Test
    fun `calls a shortened class list a semi-holiday not a working day`() {
        val august = build(
            listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(
                            date = 20,
                            events = listOf(
                                CalendarEventItem(
                                    type = "Instructional Day",
                                    text = "Instructional Day",
                                    category = "Working day",
                                ),
                                CalendarEventItem(
                                    type = "Other",
                                    text = "CAT - I",
                                    category = "Continuous Assessment Test",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).first()
        assertEquals(DayType.SEMIHOLIDAY, dayAt(august, 20).dayType)
    }

    @Test
    fun `folds attendance exams moodle and tasks onto the right day`() {
        val august = build(
            calendars = monthCalendar("August 2026"),
            examSchedule = mapOf(
                "CAT" to listOf(exam("25BLC1081", "Biology", "2026-08-20", "10:00 AM", "AB1-101")),
            ),
            moodle = listOf(moodle("Course/Assignment 3", "2026-08-25T23:59:00", url = "https://moodle/3")),
            courses = listOf(course("25BLC1081", "Biology", entries = listOf("2026-08-12" to "Absent"))),
            tasks = listOf(task("t1", "Read Chapter 5", "2026-08-25T18:00:00")),
        ).first()

        val twelfth = dayAt(august, 12)
        assertEquals(1, twelfth.attendance.absent)
        assertTrue(twelfth.events.firstOrNull { it.kind == EventKind.CLASS }?.absent == true)

        val twentieth = dayAt(august, 20)
        assertEquals("Biology", twentieth.events.firstOrNull { it.kind == EventKind.EXAM }?.title)
        assertEquals(DayType.SEMIHOLIDAY, twentieth.dayType)

        val twentyfifth = dayAt(august, 25)
        val assignments = twentyfifth.events.filter { it.kind == EventKind.ASSIGNMENT }
        assertTrue(assignments.any { it.title == "Assignment 3" })
        assertTrue(assignments.any { it.title == "Read Chapter 5" })
        assertEquals(1, twentyfifth.taskCount)
    }

    @Test
    fun `skips a completed moodle submission`() {
        val august = build(
            calendars = monthCalendar("August 2026"),
            moodle = listOf(moodle("Course/Done", "2026-08-25T23:59:00", done = true)),
        ).first()
        val day = dayAt(august, 25)
        assertEquals(0, day.events.count { it.kind == EventKind.ASSIGNMENT })
        assertEquals(0, day.taskCount)
    }

    @Test
    fun `does not truncate a month whose payload lists only some days`() {
        // The old page used `rawDays.length` as the day count, so a three-day payload rendered a
        // three-day month and the rest of the semester vanished.
        val august = build(
            listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(
                            date = 1,
                            events = listOf(
                                CalendarEventItem(
                                    type = "Instructional Day",
                                    text = "Instructional Day",
                                    category = "Working day",
                                ),
                            ),
                        ),
                        CalendarDay(date = 2, events = emptyList()),
                        CalendarDay(date = 3, events = emptyList()),
                    ),
                ),
            ),
        ).first()
        assertEquals(31, august.days.size)
        assertEquals(31, august.days[30].date)
    }

    @Test
    fun `orders a day's events so the important one is first`() {
        val august = build(
            calendars = monthCalendar("August 2026"),
            examSchedule = mapOf("CAT" to listOf(exam("X", "Exam", "2026-08-20"))),
            moodle = listOf(moodle("Course/DA", "2026-08-20T23:59:00")),
            courses = listOf(course("Y", "Class", entries = listOf("2026-08-20" to "Present"))),
        ).first()
        val kinds = dayAt(august, 20).events.map { it.kind }
        assertEquals(EventKind.EXAM, kinds[0])
        assertTrue(kinds.indexOf(EventKind.CLASS) > kinds.indexOf(EventKind.ASSIGNMENT))
    }

    @Test
    fun `takes the whole semester and sorts it chronologically`() {
        val months = build(
            listOf(
                CalendarMonth(label = "September 2026", days = emptyList()),
                CalendarMonth(label = "August 2026", days = emptyList()),
            ),
        )
        assertEquals(listOf("August 2026", "September 2026"), months.map { it.label })
    }

    @Test
    fun `puts a Moodle deadline and a task due the same day in one list`() {
        // The day model is the sheet's only source for its Tasks section. If Moodle deadlines were
        // not in it, the grid would show a dot for a deadline and the sheet would then say
        // "Nothing due today".
        val august = build(
            calendars = monthCalendar("August 2026"),
            moodle = listOf(moodle("Course/DA 2", "2026-08-25T23:59:00", url = "https://moodle/2")),
            tasks = listOf(task("t1", "Read Chapter 5", "2026-08-25T18:00:00")),
        ).first()

        val assignments = dayAt(august, 25).events.filter { it.kind == EventKind.ASSIGNMENT }
        assertEquals(2, assignments.size)
        // Only the task-store entry is cyclable; a Moodle one is marked in Moodle.
        assertEquals(1, assignments.count { it.taskId != null })
        assertTrue(assignments.all { it.dueAt != null })
    }

    @Test
    fun `does not let a completed or non-due task appear`() {
        val august = build(
            calendars = monthCalendar("August 2026"),
            tasks = listOf(
                task("done", "Done", "2026-08-25", completed = true),
                task("other-day", "Other", "2026-08-26"),
            ),
        ).first()
        fun assignmentsOn(d: Int) = dayAt(august, d).events.filter { it.kind == EventKind.ASSIGNMENT }
        assertEquals(0, assignmentsOn(25).size)
        assertEquals(1, assignmentsOn(26).size)
        assertEquals("Other", assignmentsOn(26)[0].title)
    }

    @Test
    fun `returns nothing when there is nothing`() {
        assertEquals(0, buildEnrichedCalendars(CalendarSources()).size)
        assertEquals(0, build(emptyList()).size)
    }

    // ── exam days ────────────────────────────────────────────────────────────

    private val withExam: List<MonthModel>
        get() = build(
            calendars = monthCalendar("August 2026"),
            examSchedule = mapOf(
                "CAT" to listOf(
                    exam("25BLC1081", "Biology", "2026-08-20", "10:00 AM", "AB1-101"),
                    exam("25BLC1082", "Physics", "2026-08-20", "02:00 PM", "AB1-102"),
                ),
            ),
        )

    @Test
    fun `recognises a day that carries an exam`() {
        val examDay = dayAt(withExam[0], 20)
        assertTrue(isExamDay(examDay))
        assertEquals(2, examsOn(examDay).size)
        assertEquals(listOf("Biology", "Physics"), examsOn(examDay).map { it.title }.sorted())
    }

    @Test
    fun `does not call an ordinary working day an exam day`() {
        val ordinary = dayAt(withExam[0], 11)
        assertFalse(isExamDay(ordinary))
        assertEquals(0, examsOn(ordinary).size)
    }

    @Test
    fun `does not call a working day with a Moodle deadline an exam day`() {
        // A submission deadline is not a paper. Treating the two alike would blank the timetable on
        // ordinary days whenever an assignment happened to land.
        val august = build(
            calendars = monthCalendar("August 2026"),
            moodle = listOf(moodle("Course/DA", "2026-08-15T23:59:00")),
        ).first()
        val day = dayAt(august, 15)
        assertEquals(DayType.SEMIHOLIDAY, day.dayType)
        assertFalse(isExamDay(day))
    }

    @Test
    fun `lets a real holiday stay a holiday deadline or not`() {
        val august = build(
            calendars = monthCalendar("August 2026", setOf(15)),
            moodle = listOf(moodle("Course/DA", "2026-08-15T23:59:00")),
        ).first()
        assertEquals(DayType.HOLIDAY, dayAt(august, 15).dayType)
    }

    @Test
    fun `classifies a working day carrying an exam as a semi-holiday`() {
        assertEquals(DayType.SEMIHOLIDAY, dayAt(withExam[0], 20).dayType)
    }

    // ── exam series naming ───────────────────────────────────────────────────

    /** A CAT II day, in whatever spellings each source happens to use. */
    private fun catDay(milestoneText: String, vararg seriesKeys: String): List<MonthModel> =
        build(
            calendars = listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(
                            date = 20,
                            events = listOf(
                                CalendarEventItem(
                                    type = "Other",
                                    text = "Instructional Day",
                                    category = "Working day",
                                ),
                                CalendarEventItem(
                                    type = "Other",
                                    text = milestoneText,
                                    category = "Working day",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            examSchedule = seriesKeys.associateWith {
                listOf(
                    exam("25BLC1081", "Biology", "2026-08-20", "10:00 AM", "AB1-101"),
                    exam("25BLC1082", "Physics", "2026-08-20", "02:00 PM", "AB1-102"),
                )
            },
        )

    @Test
    fun `folds the papers into the milestone as one event`() {
        val day = dayAt(catDay("CAT - II", "CAT2")[0], 20)

        // One top-level milestone, no loose exam rows beside it.
        assertEquals(1, day.events.count { it.kind == EventKind.MILESTONE })
        assertEquals(0, day.events.count { it.kind == EventKind.EXAM })

        val milestone = day.events.first { it.kind == EventKind.MILESTONE }
        assertEquals("CAT II", milestone.title)
        assertEquals(
            listOf("Biology", "Physics"),
            milestone.papers.orEmpty().map { it.title }.sorted(),
        )
    }

    @Test
    fun `folds whichever spelling each source used`() {
        val spellings = listOf(
            "CAT - II" to "CAT2",
            "CAT-II" to "CAT II",
            "CAT 2" to "CAT-2",
            "CATII" to "cat 2",
        )
        for ((milestone, series) in spellings) {
            val day = dayAt(catDay(milestone, series)[0], 20)
            val owner = day.events.first { it.kind == EventKind.MILESTONE }
            assertEquals("CAT II", owner.title, "$milestone + $series")
            assertEquals(2, owner.papers.orEmpty().size, "$milestone + $series")
            assertEquals(
                0,
                day.events.count { it.kind == EventKind.EXAM },
                "$milestone + $series",
            )
        }
    }

    @Test
    fun `merges two series keys that name the same exam`() {
        // The schedule has been seen carrying both "CAT II" and "CAT2". Without a canonical name
        // each paper is emitted twice, so the day lists four papers.
        val day = dayAt(catDay("CAT - II", "CAT II", "CAT2")[0], 20)
        val owner = day.events.first { it.kind == EventKind.MILESTONE }
        assertEquals(2, owner.papers.orEmpty().size)
        assertEquals(2, owner.papers.orEmpty().map { it.courseCode }.toSet().size)
    }

    @Test
    fun `labels every paper with one spelling of the series`() {
        val owner = dayAt(catDay("CAT - II", "CAT2")[0], 20)
            .events.first { it.kind == EventKind.MILESTONE }
        // Not "CAT2" beside a "CAT II" milestone.
        assertTrue(owner.papers.orEmpty().all { it.detail.orEmpty().startsWith("CAT II") })
    }

    @Test
    fun `still counts as an exam day after the fold`() {
        // The papers are no longer top-level, so anything that asks "is this an exam day" has to
        // look inside the milestone or it will answer wrong and show a timetable on a CAT day.
        val day = dayAt(catDay("CAT - II", "CAT2")[0], 20)
        assertTrue(isExamDay(day))
        assertFalse(hasClasses(day))
        assertEquals(2, examsOn(day).size)
    }

    @Test
    fun `leaves a paper alone when no milestone claims it`() {
        val august = build(
            calendars = monthCalendar("August 2026"),
            examSchedule = mapOf("FAT" to listOf(exam("X", "Biology", "2026-08-30"))),
        ).first()
        val day = dayAt(august, 30)
        assertEquals(1, day.events.count { it.kind == EventKind.EXAM })
        assertEquals(0, milestonesOn(day).size)
    }

    @Test
    fun `does not fold a CAT I paper into the CAT II milestone`() {
        val day = dayAt(catDay("CAT - II", "CAT1")[0], 20)
        val owner = day.events.first { it.kind == EventKind.MILESTONE }
        assertEquals(0, owner.papers.orEmpty().size)
        // Both of the fixture's papers stay loose: "cat1" is not "cat2".
        assertEquals(2, day.events.count { it.kind == EventKind.EXAM })
    }

    // ── class-free days ──────────────────────────────────────────────────────

    @Test
    fun `treats a holiday as class-free and the college as shut`() {
        val holiday = dayAt(build(monthCalendar("August 2026", setOf(15))).first(), 15)
        assertEquals(DayType.HOLIDAY, holiday.dayType)
        assertFalse(hasClasses(holiday))
        assertFalse(isCollegeOpen(holiday))
        assertTrue(holidaysOn(holiday).map { it.title }.contains("Pongal"))
        assertFalse(isExamDay(holiday))
    }

    @Test
    fun `treats a non-instructional day as class-free but the college as open`() {
        // The distinction the old page got wrong: a published day with nothing on it is a working
        // day at an open college, not a day off.
        val august = build(
            listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(date = 3, events = emptyList()),
                        CalendarDay(
                            date = 4,
                            events = listOf(
                                CalendarEventItem(
                                    type = "Instructional Day",
                                    text = "Instructional Day",
                                    category = "Working day",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).first()
        val quiet = dayAt(august, 3)
        assertEquals(DayType.NON_INSTRUCTIONAL, quiet.dayType)
        assertFalse(hasClasses(quiet))
        assertTrue(isCollegeOpen(quiet))
        // Its neighbour is an ordinary teaching day, so the split is doing work.
        assertEquals(DayType.INSTRUCTIONAL, dayAt(august, 4).dayType)
    }

    @Test
    fun `reads VTOP's Non Instructional Day as open not as a holiday`() {
        val august = build(
            listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(
                            date = 4,
                            // The category says "Working day", which is also what the instructional
                            // test looks for - so order matters here.
                            events = listOf(
                                CalendarEventItem(
                                    type = "Other",
                                    text = "Non Instructional Day",
                                    category = "Working day",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).first()
        val day = dayAt(august, 4)
        assertEquals(DayType.NON_INSTRUCTIONAL, day.dayType)
        assertFalse(hasClasses(day))
        assertTrue(isCollegeOpen(day))
    }

    @Test
    fun `treats an exam day as class-free`() {
        val august = build(
            calendars = monthCalendar("August 2026"),
            examSchedule = mapOf("CAT" to listOf(exam("X", "Bio", "2026-08-20"))),
        ).first()
        val examDay = dayAt(august, 20)
        assertFalse(hasClasses(examDay))
        assertTrue(isCollegeOpen(examDay))
    }

    @Test
    fun `treats an instructional day as having classes`() {
        val teaching = dayAt(build(monthCalendar("August 2026")).first(), 11)
        assertEquals(DayType.INSTRUCTIONAL, teaching.dayType)
        assertTrue(hasClasses(teaching))
        assertTrue(isCollegeOpen(teaching))
    }

    @Test
    fun `keeps the timetable on a semi-holiday because the list is only shortened`() {
        val august = build(
            listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(
                            date = 20,
                            events = listOf(
                                CalendarEventItem(
                                    type = "Instructional Day",
                                    text = "Instructional Day",
                                    category = "Working day",
                                ),
                                CalendarEventItem(type = "Other", text = "Vibrance", category = "Club event"),
                            ),
                        ),
                    ),
                ),
            ),
        ).first()
        val day = dayAt(august, 20)
        assertEquals(DayType.SEMIHOLIDAY, day.dayType)
        assertTrue(hasClasses(day))
    }

    @Test
    fun `does not invent a holiday for a date outside the published calendar`() {
        val august = build(listOf(CalendarMonth(label = "August 2026", days = emptyList()))).first()
        val unknown = dayAt(august, 11)
        assertEquals(DayType.OTHER, unknown.dayType)
        assertFalse(hasClasses(unknown))
    }

    @Test
    fun `trusts a recorded class over a missing calendar entry`() {
        val august = build(
            calendars = listOf(CalendarMonth(label = "August 2026", days = emptyList())),
            courses = listOf(course("25BLC1081", "Biology", entries = listOf("2026-08-11" to "Present"))),
        ).first()
        val taught = dayAt(august, 11)
        assertEquals(DayType.INSTRUCTIONAL, taught.dayType)
        assertTrue(hasClasses(taught))
    }

    // ── milestones ───────────────────────────────────────────────────────────

    /** A teaching month with one milestone on the given date. */
    private fun withMilestone(date: Int, text: String): List<CalendarMonth> =
        listOf(
            CalendarMonth(
                label = "August 2026",
                days = listOf(
                    CalendarDay(
                        date = date,
                        events = listOf(
                            CalendarEventItem(
                                type = "Instructional Day",
                                text = "Instructional Day",
                                category = "Working day",
                            ),
                            // The category is "Working day" on the real payload too, which is
                            // why the ordering inside `classify` matters.
                            CalendarEventItem(type = "Other", text = text, category = "Working day"),
                        ),
                    ),
                ),
            ),
        )

    @Test
    fun `recognises LID for theory classes and keeps its blurb`() {
        val day = dayAt(build(withMilestone(10, "LID FOR THEORY CLASSES")).first(), 10)
        val milestone = day.events.first { it.kind == EventKind.MILESTONE }
        assertEquals("LID — Theory", milestone.title)
        assertEquals("Last instructional day for theory classes", milestone.detail)
        assertEquals("indigo", milestone.tone)
    }

    @Test
    fun `marks a LID day as still a teaching day`() {
        // The last day of instruction is a day you are in class.
        val day = dayAt(build(withMilestone(10, "LID FOR THEORY CLASSES")).first(), 10)
        assertEquals(DayType.INSTRUCTIONAL, day.dayType)
        assertTrue(hasClasses(day))
    }

    @Test
    fun `does not let a milestone be mistaken for the working-day marker`() {
        // Both entries on this day carry category "Working day". The milestone check has to win, or
        // LID renders as an anonymous "Working Day" and the semester's boundary date is invisible.
        val day = dayAt(build(withMilestone(10, "LID FOR THEORY CLASSES")).first(), 10)
        assertEquals(1, day.events.count { it.kind == EventKind.WORKING })
        assertEquals(1, day.events.count { it.kind == EventKind.MILESTONE })
    }

    @Test
    fun `leaves a curated title alone`() {
        // The catch-all inversion must not reach the classified kinds: here the category is
        // "Working day", and the branch owns its own wording.
        val august = build(monthCalendar("August 2026")).first()
        val working = dayAt(august, 5).events.first { it.kind == EventKind.WORKING }
        assertEquals("Working day", working.title)
        assertNull(working.detail)
    }

    @Test
    fun `sorts a milestone above an assignment on the same day`() {
        val august = build(
            calendars = withMilestone(10, "LID FOR THEORY CLASSES"),
            moodle = listOf(moodle("Course/DA", "2026-08-10T23:59:00")),
        ).first()
        assertEquals(EventKind.MILESTONE, dayAt(august, 10).events[0].kind)
    }

    @Test
    fun `puts a milestone on the grid marker row`() {
        val day = dayAt(build(withMilestone(10, "LID FOR THEORY CLASSES")).first(), 10)
        assertTrue(dayMarkers(day).contains("indigo"))
    }

    // ── EventHub registrations on the calendar ───────────────────────────────

    private fun augustRegistrations(
        registrations: List<CalendarRegistration>,
        profileImageUrl: String? = null,
    ): MonthModel = build(
        calendars = listOf(
            CalendarMonth(
                label = "August 2026",
                days = listOf(
                    CalendarDay(
                        date = 22,
                        events = listOf(
                            CalendarEventItem(
                                type = "Instructional Day",
                                text = "Instructional Day",
                                category = "Working day",
                            ),
                        ),
                    ),
                ),
            ),
        ),
        registrations = registrations,
        profileImageUrl = profileImageUrl,
    ).first()

    @Test
    fun `puts a registration on its date with the time and venue`() {
        val day = dayAt(
            augustRegistrations(
                listOf(
                    CalendarRegistration(
                        eid = "9",
                        title = "Robotics Club Workshop",
                        date = "2026-08-22",
                        time = "4:00 PM",
                        venue = "AB1-204",
                    ),
                ),
            ),
            22,
        )

        val ev = day.events.first { it.kind == EventKind.EVENT }
        assertEquals("Robotics Club Workshop", ev.title)
        assertEquals("4:00 PM · AB1-204", ev.detail)
        assertEquals("9", ev.eventhubId)
    }

    @Test
    fun `leaves other dates alone`() {
        val day = dayAt(
            augustRegistrations(
                listOf(CalendarRegistration(title = "Robotics Club Workshop", date = "2026-08-29", time = "4:00 PM")),
            ),
            22,
        )
        assertEquals(0, day.events.count { it.kind == EventKind.EVENT })
    }

    @Test
    fun `carries the profile photo on the event so the row can show it`() {
        val day = dayAt(
            augustRegistrations(
                listOf(CalendarRegistration(title = "Vibrance", date = "2026-08-22")),
                profileImageUrl = "https://cdn.test/me.jpg",
            ),
            22,
        )
        assertEquals("https://cdn.test/me.jpg", day.events.first { it.kind == EventKind.EVENT }.avatarUrl)
    }

    @Test
    fun `omits the photo entirely when the caller gated it off`() {
        // The gate runs before the model is built, so a user who turned their photo off has no URL
        // anywhere downstream - not hidden, absent.
        val day = dayAt(
            augustRegistrations(listOf(CalendarRegistration(title = "Vibrance", date = "2026-08-22"))),
            22,
        )
        assertNull(day.events.first { it.kind == EventKind.EVENT }.avatarUrl)
    }

    @Test
    fun `keeps a free registration which carries no payment status`() {
        // EventHub omits `paymentStatus` for a free event. Dropping those would lose every club
        // signup, which is most of what people register for.
        val day = dayAt(
            augustRegistrations(listOf(CalendarRegistration(title = "Free Talk", date = "2026-08-22"))),
            22,
        )
        assertEquals(1, day.events.count { it.kind == EventKind.EVENT })
    }

    @Test
    fun `skips a registration whose payment has not gone through`() {
        // EventHub records the form before the money moves, so a pending payment is not something
        // the user is going to turn up to.
        val day = dayAt(
            augustRegistrations(
                listOf(
                    CalendarRegistration(
                        title = "Paid Workshop",
                        date = "2026-08-22",
                        paymentStatus = "PENDING",
                    ),
                ),
            ),
            22,
        )
        assertEquals(0, day.events.count { it.kind == EventKind.EVENT })
    }

    @Test
    fun `accepts the payment spellings EventHub actually uses`() {
        for (status in listOf("Paid", "FREE", "Payment Success")) {
            val day = dayAt(
                augustRegistrations(
                    listOf(
                        CalendarRegistration(title = "Event X", date = "2026-08-22", paymentStatus = status),
                    ),
                ),
                22,
            )
            assertEquals(1, day.events.count { it.kind == EventKind.EVENT }, status)
        }
    }

    @Test
    fun `never lets a registration outrank the college's own events`() {
        // `event` has the lowest priority of any kind, so on a CAT day the exam still leads and the
        // workshop rides underneath it.
        val month = build(
            calendars = listOf(
                CalendarMonth(
                    label = "August 2026",
                    days = listOf(
                        CalendarDay(
                            date = 22,
                            events = listOf(CalendarEventItem(type = "Other", text = "CAT - II", category = "Working day")),
                        ),
                    ),
                ),
            ),
            examSchedule = mapOf(
                "CAT - II" to listOf(exam("25BLC1081", "Biology", "2026-08-22", venue = "AB1-101")),
            ),
            registrations = listOf(CalendarRegistration(title = "Vibrance", date = "2026-08-22")),
        ).first()
        val day = dayAt(month, 22)

        assertEquals(DayHeadlineKind.MILESTONE, dayHeadline(day).kind)
        val kinds = day.events
            .filter { it.kind != EventKind.CLASS && it.kind != EventKind.WORKING }
            .map { it.kind }
        assertTrue(kinds.indexOf(EventKind.EVENT) > kinds.indexOf(EventKind.MILESTONE))
    }

    @Test
    fun `does not turn a teaching day into something else`() {
        val day = dayAt(
            augustRegistrations(listOf(CalendarRegistration(title = "Robotics Club Workshop", date = "2026-08-22"))),
            22,
        )
        assertEquals(DayType.INSTRUCTIONAL, day.dayType)
        assertTrue(hasClasses(day))
        assertFalse(isExamDay(day))
        assertTrue(isCollegeOpen(day))
    }

    @Test
    fun `lands on the right day for the YYYY-MM-DD format EventHub sends`() {
        // The confirmed wire format, and worth pinning properly: `parseDayDate` also tolerates a
        // locale string, so reading the wrong format would not fail loudly, it would just quietly
        // drop every registration off the calendar.
        val month = build(
            calendars = listOf(
                CalendarMonth(
                    label = "October 2026",
                    days = listOf(
                        CalendarDay(
                            date = 7,
                            events = listOf(
                                CalendarEventItem(
                                    type = "Instructional Day",
                                    text = "Instructional Day",
                                    category = "Working day",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            registrations = listOf(
                CalendarRegistration(title = "Vibrance 2026", date = "2026-10-07", time = "5:00 PM"),
            ),
        ).first()

        val day = dayAt(month, 7)
        assertEquals("2026-10-07", day.dateKey)
        val ev = day.events.first { it.kind == EventKind.EVENT }
        assertEquals("Vibrance 2026", ev.title)
        assertEquals("5:00 PM", ev.detail)
    }

    @Test
    fun `ignores a registration with no usable date`() {
        val day = dayAt(
            augustRegistrations(
                listOf(
                    CalendarRegistration(title = "Mystery Event", date = ""),
                    CalendarRegistration(title = "Mystery Event 2"),
                ),
            ),
            22,
        )
        assertEquals(0, day.events.count { it.kind == EventKind.EVENT })
    }

    // ── activeMonthIndex ─────────────────────────────────────────────────────

    private val monthTriple: List<MonthModel>
        get() = build(
            listOf(
                CalendarMonth(label = "August 2026", days = emptyList()),
                CalendarMonth(label = "September 2026", days = emptyList()),
                CalendarMonth(label = "October 2026", days = emptyList()),
            ),
        )

    @Test
    fun `opens on the month containing today`() {
        assertEquals(1, activeMonthIndex(monthTriple, LocalDate(2026, 9, 15)))
    }

    @Test
    fun `opens on the next month to start when today precedes the calendar`() {
        assertEquals(0, activeMonthIndex(monthTriple, LocalDate(2026, 6, 15)))
    }

    @Test
    fun `falls back to the last month when the whole calendar is past`() {
        assertEquals(2, activeMonthIndex(monthTriple, LocalDate(2027, 1, 10)))
    }

    @Test
    fun `does not divide by zero on an empty calendar`() {
        assertEquals(0, activeMonthIndex(emptyList(), LocalDate(2026, 8, 1)))
    }
}
