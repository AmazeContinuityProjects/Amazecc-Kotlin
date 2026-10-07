package com.amazecc.app.shared.ui.screens.home

import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.MoodleAssignment
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import com.amazecc.app.shared.ui.design.HomeTone
import com.amazecc.app.shared.utils.AttendanceDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * The home screen's derivations, which is the code the transplant leans on hardest.
 *
 * Every assertion here is against a number or a string the screen actually renders, so a
 * regression shows up as a failing test rather than as a subtly wrong card. Where a function
 * deliberately disagrees with the web app - [homeBunk] not halving the margin for a lab - the
 * test names the disagreement, because that is the assertion most likely to be "fixed" by
 * someone reading the Node source.
 */
class HomeModelsTest {

    /** 2026-09-29 is a Tuesday, which is what makes the label and week-strip tests concrete. */
    private val tuesday = LocalDate(2026, 9, 29)
    private val monday = LocalDate(2026, 9, 28)

    private fun card(
        attended: Int = 0,
        total: Int = 0,
        time: String = "8:00-8:50",
        isLab: Boolean = false,
        slotName: String = "A1",
        courseCode: String = "BAMAT209",
        courseTitle: String = "Applied Mathematics",
        courseType: String = "Theory",
        faculty: String = "Dr Nair",
        venue: String = "SJT 101",
    ) = HomeClassCard(
        courseCode = courseCode,
        courseTitle = courseTitle,
        courseType = courseType,
        faculty = faculty,
        venue = venue,
        slotName = slotName,
        time = time,
        attended = attended,
        total = total,
        percentage = if (total > 0) attended * 100f / total else 0f,
        isLab = isLab,
    )

    private fun weekDay(
        date: LocalDate = tuesday,
        dayCode: AttendanceDay = AttendanceDay.TUE,
        isToday: Boolean = true,
        exams: List<ExamItem> = emptyList(),
        holidayInfo: String? = null,
        detectedDayOrder: AttendanceDay? = null,
        orderInfo: String? = null,
        isInstructional: Boolean = false,
        hasDeadline: Boolean = false,
    ) = HomeWeekDay(
        dayCode = dayCode,
        date = date,
        isToday = isToday,
        exams = exams,
        holidayInfo = holidayInfo,
        detectedDayOrder = detectedDayOrder,
        orderInfo = orderInfo,
        isInstructional = isInstructional,
        hasDeadline = hasDeadline,
    )

    private fun task(
        id: String,
        dueDate: String,
        dueTime: String = "23:59",
        courseCode: String = "BCH2001",
        type: String = "homework",
        completed: Boolean = false,
    ) = com.amazecc.app.shared.model.HomeworkTask(
        id = id,
        courseCode = courseCode,
        courseTitle = "Chemistry",
        title = "Task $id",
        dueDate = dueDate,
        dueTime = dueTime,
        type = type,
        completed = completed,
        createdAt = "2026-09-01 09:00",
    )

    // ── Attendance ──

    @Test
    fun `home status maps the projection bands onto the home vocabulary`() {
        assertEquals(HomeAttendanceStatus.SAFE, homeAttendanceStatus(80f, 10, 75f))
        assertEquals(HomeAttendanceStatus.WARNING, homeAttendanceStatus(79.9f, 10, 75f))
        assertEquals(HomeAttendanceStatus.CRITICAL, homeAttendanceStatus(74.9f, 10, 75f))
        assertEquals(HomeAttendanceStatus.NONE, homeAttendanceStatus(100f, 0, 75f))
    }

    @Test
    fun `the home summary is the unweighted sum, not an average of percentages`() {
        // 1/1 is 100% and 9/99 is 9.09%; averaging those gives 54.5%. Summing the counts
        // gives 10/100 = 10%, which is the figure the institute prints.
        val summary = summariseHomeAttendance(listOf(1 to 1, 9 to 99), 75f)

        assertEquals(10f, summary.percentage)
        assertEquals(10, summary.attended)
        assertEquals(100, summary.total)
        assertEquals(HomeAttendanceStatus.CRITICAL, summary.status)
    }

    @Test
    fun `a summary with no held classes reads as no data, not as zero percent`() {
        val summary = summariseHomeAttendance(emptyList(), 75f)

        assertFalse(summary.hasData)
        assertEquals(HomeAttendanceStatus.NONE, summary.status)
        assertEquals("—", summary.label)
        assertEquals(HomeTone.NEUTRAL, summary.tone)
    }

    @Test
    fun `each status carries its own word and tone`() {
        val safe = summariseHomeAttendance(listOf(90 to 100), 75f)
        assertEquals("Safe", safe.label)
        assertEquals(HomeTone.SUCCESS, safe.tone)
        assertTrue(safe.hasData)

        val warning = summariseHomeAttendance(listOf(76 to 100), 75f)
        assertEquals("Warning", warning.label)
        assertEquals(HomeTone.WARNING, warning.tone)

        val critical = summariseHomeAttendance(listOf(10 to 100), 75f)
        assertEquals("Critical", critical.label)
        assertEquals(HomeTone.DANGER, critical.tone)
    }

    // ── Bunk margin ──

    @Test
    fun `bunk says no classes before it says anything about the arithmetic`() {
        assertEquals(
            HomeBunk(HomeBunkStatus.SAFE, "No classes yet", "Nothing has been held"),
            homeBunk(card(attended = 0, total = 0), 75f),
        )
    }

    @Test
    fun `bunk counts the classes that can still be missed`() {
        val bunk = homeBunk(card(attended = 9, total = 10), 75f)

        assertEquals(HomeBunkStatus.SAFE, bunk.status)
        assertEquals("2 bunkable", bunk.text)
        assertEquals("Safe for 2 skips", bunk.subtext)
        assertEquals(HomeTone.SUCCESS, bunk.tone)
    }

    @Test
    fun `bunk pluralises a single skip differently`() {
        val bunk = homeBunk(card(attended = 3, total = 3), 75f)

        assertEquals(HomeBunkStatus.SAFE, bunk.status)
        assertEquals("1 bunkable", bunk.text)
        assertEquals("Safe for one skip", bunk.subtext)
    }

    @Test
    fun `bunk says zero when you are sitting exactly on the margin`() {
        // 1/1 is above target, but floor(1/0.75 - 1) = floor(0.33) = 0: the next absence
        // would already put the course under.
        val bunk = homeBunk(card(attended = 1, total = 1), 75f)

        assertEquals(HomeBunkStatus.WARNING, bunk.status)
        assertEquals("0 bunkable", bunk.text)
        assertEquals("On the safety margin", bunk.subtext)
        assertEquals(HomeTone.WARNING, bunk.tone)
    }

    @Test
    fun `bunk asks for classes when below target`() {
        val bunk = homeBunk(card(attended = 70, total = 100), 75f)

        assertEquals(HomeBunkStatus.CRITICAL, bunk.status)
        assertEquals("Need 20 classes", bunk.text)
        assertEquals("To reach 75%", bunk.subtext)
        assertEquals(HomeTone.DANGER, bunk.tone)
    }

    @Test
    fun `bunk asks for exactly one class when the deficit is a single attendance`() {
        // (0.75 * 3 - 2) / 0.25 = 1 exactly, which is the singular branch of the label.
        val bunk = homeBunk(card(attended = 2, total = 3), 75f)

        assertEquals(HomeBunkStatus.CRITICAL, bunk.status)
        assertEquals("Need 1 class", bunk.text)
    }

    @Test
    fun `bunk does not halve the margin for a lab`() {
        // The web app halves both figures for a lab because it measures *hours*; this app
        // measures class attendances (see BunkOMeterCard), and halving here would make the
        // home pill disagree with the bunk meter one screen away, for the same course.
        val theory = homeBunk(card(attended = 9, total = 10), 75f)
        val lab = homeBunk(card(attended = 9, total = 10, isLab = true), 75f)

        assertEquals(theory, lab)
        assertEquals("2 bunkable", lab.text)
    }

    // ── Live class progress ──

    @Test
    fun `a session on another day has no state at all`() {
        assertEquals(
            HomeClassProgress(HomeClassState.OTHER_DAY, 0f, 0, 0),
            homeClassProgress(card(), isToday = false, currentMinutes = 500),
        )
    }

    @Test
    fun `a session in progress reports its position and the time left`() {
        // Slot 8:00-8:50 is 480..530 minutes.
        val start = homeClassProgress(card(), isToday = true, currentMinutes = 480)
        assertEquals(HomeClassState.LIVE, start.state)
        assertEquals(0f, start.progressPct)
        assertEquals(50, start.minutesLeft)
        assertEquals(0, start.minutesUntilStart)

        val middle = homeClassProgress(card(), isToday = true, currentMinutes = 505)
        assertEquals(HomeClassState.LIVE, middle.state)
        assertEquals(50f, middle.progressPct)
        assertEquals(25, middle.minutesLeft)

        val end = homeClassProgress(card(), isToday = true, currentMinutes = 530)
        assertEquals(HomeClassState.LIVE, end.state)
        assertEquals(100f, end.progressPct)
        assertEquals(0, end.minutesLeft)
    }

    @Test
    fun `a session that has not started counts down to it`() {
        val progress = homeClassProgress(card(), isToday = true, currentMinutes = 479)

        assertEquals(HomeClassState.UPCOMING, progress.state)
        assertEquals(0f, progress.progressPct)
        assertEquals(0, progress.minutesLeft)
        assertEquals(1, progress.minutesUntilStart)
    }

    @Test
    fun `a session past its end is complete`() {
        val progress = homeClassProgress(card(), isToday = true, currentMinutes = 531)

        assertEquals(HomeClassState.COMPLETED, progress.state)
        assertEquals(100f, progress.progressPct)
        assertEquals(0, progress.minutesLeft)
        assertEquals(0, progress.minutesUntilStart)
    }

    // ── Week strip ──

    @Test
    fun `flavour precedence is exam, then holiday, then reordered, then teaching`() {
        assertEquals(
            HomeWeekFlavour.EXAM,
            weekDay(exams = listOf(ExamItem()), holidayInfo = "Diwali", detectedDayOrder = AttendanceDay.WED).flavour(),
        )
        assertEquals(
            HomeWeekFlavour.HOLIDAY,
            weekDay(holidayInfo = "Diwali", detectedDayOrder = AttendanceDay.WED).flavour(),
        )
        assertEquals(HomeWeekFlavour.REORDERED, weekDay(detectedDayOrder = AttendanceDay.WED).flavour())
        assertEquals(HomeWeekFlavour.TEACHING, weekDay().flavour())
    }

    @Test
    fun `each flavour has its own word`() {
        assertEquals("Exam day", HomeWeekFlavour.EXAM.label())
        assertEquals("Academic holiday", HomeWeekFlavour.HOLIDAY.label())
        assertEquals("Reordered timetable", HomeWeekFlavour.REORDERED.label())
        assertEquals("Teaching day", HomeWeekFlavour.TEACHING.label())
    }

    @Test
    fun `whenLabel spells the day and the month the way the grid does`() {
        assertEquals("Tue 29 Sept", weekDay().whenLabel())
        assertEquals("Mon 28 Sept", weekDay(date = monday, dayCode = AttendanceDay.MON).whenLabel())
    }

    @Test
    fun `the disc description carries the session count and pluralises it`() {
        assertEquals("Tue 29 Sept · No classes", weekDay().contentDescription(0))
        assertEquals("Tue 29 Sept · 1 session", weekDay().contentDescription(1))
        assertEquals("Tue 29 Sept · 3 sessions", weekDay().contentDescription(3))
    }

    @Test
    fun `the disc description names the day a reordered timetable follows`() {
        assertEquals(
            "Tue 29 Sept · Reordered timetable (WED)",
            weekDay(detectedDayOrder = AttendanceDay.WED).contentDescription(2),
        )
    }

    @Test
    fun `the disc description counts exams and pluralises them`() {
        assertEquals(
            "Tue 29 Sept · Exam day, 1 exam",
            weekDay(exams = listOf(ExamItem())).contentDescription(0),
        )
        assertEquals(
            "Tue 29 Sept · Exam day, 2 exams",
            weekDay(exams = listOf(ExamItem(), ExamItem())).contentDescription(0),
        )
        assertEquals("Tue 29 Sept · Academic holiday", weekDay(holidayInfo = "Diwali").contentDescription(0))
    }

    @Test
    fun `longLabel spells out a full date under the strip`() {
        assertEquals("Tue, Sept 29, 2026", weekDay().longLabel())
    }

    // ── Academic calendar analysis ──

    @Test
    fun `day of week maps onto the monday-first attendance column without shifting`() {
        // Both enums happen to be declared Monday-first (ISO), which is what makes indexing one
        // with the other *look* safe in `AutoSyncTabContent`. It is only safe while both stay
        // Monday-first, and the names differ anyway, so the mapping is stated once, by name.
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.entries.first())
        assertEquals(AttendanceDay.MON, AttendanceDay.entries.first())

        assertEquals(AttendanceDay.MON, DayOfWeek.MONDAY.toAttendanceDay())
        assertEquals(AttendanceDay.TUE, DayOfWeek.TUESDAY.toAttendanceDay())
        assertEquals(AttendanceDay.WED, DayOfWeek.WEDNESDAY.toAttendanceDay())
        assertEquals(AttendanceDay.THU, DayOfWeek.THURSDAY.toAttendanceDay())
        assertEquals(AttendanceDay.FRI, DayOfWeek.FRIDAY.toAttendanceDay())
        assertEquals(AttendanceDay.SAT, DayOfWeek.SATURDAY.toAttendanceDay())
        assertEquals(AttendanceDay.SUN, DayOfWeek.SUNDAY.toAttendanceDay())
    }

    @Test
    fun `today maps to its own column`() {
        assertEquals(AttendanceDay.TUE, homeTodayAttendanceDay(tuesday))
        assertEquals(AttendanceDay.MON, homeTodayAttendanceDay(monday))
        assertEquals(AttendanceDay.SUN, homeTodayAttendanceDay(LocalDate(2026, 10, 4)))
    }

    @Test
    fun `a day order needs both a weekday and an order word`() {
        // "Monday" alone appears in announcements that have nothing to do with the timetable
        // ("Exam on Monday"), and reading those as overrides would re-key the whole week.
        assertNull(extractDayOrderOverride(null))
        assertNull(extractDayOrderOverride(""))
        assertNull(extractDayOrderOverride("Exam on Monday"))
        assertNull(extractDayOrderOverride("Monday"))
        assertNull(extractDayOrderOverride("Sports day on Thursday"))
        assertNull(extractDayOrderOverride("Classes are cancelled"))

        assertEquals(AttendanceDay.MON, extractDayOrderOverride("Monday order"))
        assertEquals(AttendanceDay.TUE, extractDayOrderOverride("Following Tuesday's timetable"))
        assertEquals(AttendanceDay.WED, extractDayOrderOverride("Classes follow Wednesday order"))
        assertEquals(AttendanceDay.FRI, extractDayOrderOverride("Friday table"))
        assertEquals(AttendanceDay.SAT, extractDayOrderOverride("SATURDAY ORDER"))
    }

    @Test
    fun `month meta reads a month name and a year out of a label`() {
        assertEquals(MonthMeta(9, 2026), monthMetaOf("Sept 2026", 1999))
        assertEquals(MonthMeta(10, 2026), monthMetaOf("October", 2026))
        assertEquals(MonthMeta(1, 2025), monthMetaOf("Jan", 2025))
    }

    @Test
    fun `month meta falls back to the caller's year when the label has none`() {
        assertEquals(MonthMeta(9, 2026), monthMetaOf("Sept", 2026))
        assertEquals(MonthMeta(0, 2026), monthMetaOf("", 2026))
    }

    @Test
    fun `month meta leaves the month at zero when the label carries only a year`() {
        // Zero is meaningful downstream: buildHomeWeekDays reads `month != 0` as "match any
        // month", so a year-only label describes the whole year rather than nothing.
        assertEquals(MonthMeta(0, 2026), monthMetaOf("2026-09", 1999))
    }

    // ── buildHomeWeekDays ──

    private fun monthOf(
        label: String,
        day: Int,
        vararg events: CalendarEvent,
    ) = CalendarMonth(label, listOf(CalendarDay(day, events.toList())))

    private fun event(type: String, text: String) = CalendarEvent(type = type, text = text)

    private fun week(
        calendarMonths: List<CalendarMonth> = emptyList(),
        offset: Int = 0,
        exams: List<ExamItem> = emptyList(),
        deadlines: Set<LocalDate> = emptySet(),
    ) = buildHomeWeekDays(
        weekOffset = offset,
        today = tuesday,
        exams = exams,
        calendarMonths = calendarMonths,
        deadlineDates = deadlines,
    )

    @Test
    fun `the week runs monday to sunday and marks today exactly once`() {
        val days = week()

        assertEquals(7, days.size)
        assertEquals(AttendanceDay.entries, days.map { it.dayCode })
        assertEquals(monday, days.first().date)
        assertEquals(LocalDate(2026, 10, 4), days.last().date)
        assertEquals(1, days.count { it.isToday })
        assertTrue(days[1].isToday)
    }

    @Test
    fun `a holiday is read off the calendar for its own date only`() {
        val days = week(
            calendarMonths = listOf(monthOf("Sept 2026", 29, event("Holiday", "Diwali"))),
        )

        assertEquals("Diwali", days[1].holidayInfo)
        assertEquals(HomeWeekFlavour.HOLIDAY, days[1].flavour())
        assertNull(days[0].holidayInfo)
        assertNull(days[3].holidayInfo) // 1 Oct is October, so a "Sept" block does not describe it
    }

    @Test
    fun `an event with no text and no category is skipped and its type does not speak for it`() {
        // Node reads `ev.text || ev.category || ""` and drops an empty result before it looks at
        // anything else (`SimplifiedMobileHome.tsx:633-635`), so a row that carries a `type` but no
        // words marks nothing - not a holiday, not an instructional day. The type field says what
        // the row *is*, not what it says.
        val days = week(
            calendarMonths = listOf(monthOf("Sept 2026", 29, event("Holiday", ""))),
        )

        assertNull(days[1].holidayInfo)
        assertFalse(days[1].isInstructional)
        assertNull(days[1].detectedDayOrder)
    }

    @Test
    fun `an instructional announcement is not a holiday`() {
        val days = week(
            calendarMonths = listOf(monthOf("Sept 2026", 29, event("Instructional Day", "Instructional Day"))),
        )

        assertNull(days[1].holidayInfo)
        assertTrue(days[1].isInstructional)
        assertEquals(HomeWeekFlavour.TEACHING, days[1].flavour())
    }

    @Test
    fun `a day order announcement reorders the day and clears any holiday with it`() {
        val reordered = week(
            calendarMonths = listOf(monthOf("Sept 2026", 29, event("Other", "Classes follow Wednesday order"))),
        )
        assertEquals(AttendanceDay.WED, reordered[1].detectedDayOrder)
        assertEquals("Classes follow Wednesday order", reordered[1].orderInfo)
        assertTrue(reordered[1].isInstructional)
        assertNull(reordered[1].holidayInfo)
        assertEquals(HomeWeekFlavour.REORDERED, reordered[1].flavour())

        // Reordering a working day marks it instructional, so a holiday that also carries an
        // order does not stay a holiday - which is why REORDERED is only reached on days that
        // were genuinely teaching days to begin with.
        val both = week(
            calendarMonths = listOf(monthOf("Sept 2026", 29, event("Other", "Diwali holiday, Monday order"))),
        )
        assertNull(both[1].holidayInfo)
        assertEquals(AttendanceDay.MON, both[1].detectedDayOrder)
    }

    @Test
    fun `a calendar from another year does not describe this one`() {
        val days = week(
            calendarMonths = listOf(monthOf("Sept 2025", 29, event("Holiday", "Diwali"))),
        )

        assertNull(days[1].holidayInfo)
    }

    @Test
    fun `deadline dates mark the day as worth opening`() {
        val days = week(deadlines = setOf(LocalDate(2026, 9, 30)))

        assertTrue(days[2].hasDeadline)
        assertFalse(days[1].hasDeadline)
    }

    // ── Week strip ──

    /**
     * Node's week-strip fixture, in the shape this file reads it.
     *
     * Ported from `AmazeCC/src/__tests__/simplifiedMobileHome.weekStrip.test.tsx`, which renders
     * the real page and drives the real gesture. What can be asserted without a UI harness is
     * here: the week's dates and labels, which disc carries which kind of day, and that paging
     * moves all seven. The disc's tint, its today ring and the swipe itself are Compose surface
     * and belong to a UI test - see the plan's §8.3.
     *
     * The holidays live in an October block because the strip matches a calendar to a week by
     * month *and* year, strictly: a "Sept 2026" entry says nothing about 1 Oct. That strictness
     * is the app's, not the fixture's, and the test has to respect it or the day types silently
     * stop being detected.
     */
    private fun stripMonths() = listOf(
        CalendarMonth("Sept 2026", emptyList()),
        CalendarMonth(
            "Oct 2026",
            listOf(
                CalendarDay(1, listOf(CalendarEvent(type = "event", text = "No instructional day"))),
                CalendarDay(3, listOf(CalendarEvent(type = "event", text = "No instructional day"))),
            ),
        ),
    )

    private fun stripWeek(offset: Int = 0, exams: List<ExamItem> = emptyList()) = buildHomeWeekDays(
        weekOffset = offset,
        today = tuesday,
        exams = exams,
        calendarMonths = stripMonths(),
        deadlineDates = emptySet(),
    )

    @Test
    fun `the strip is seven discs, monday first, with today on the second`() {
        val days = stripWeek()

        assertEquals(
            listOf("MON28", "TUE29", "WED30", "THU1", "FRI2", "SAT3", "SUN4"),
            days.map { "${it.dayCode.name}${it.dayNumber}" },
        )
        assertEquals(monday, days.first().date)
        assertEquals(LocalDate(2026, 10, 4), days.last().date)
        assertEquals(listOf(false, true, false, false, false, false, false), days.map { it.isToday })
    }

    @Test
    fun `an exam marks its own disc as an exam day and leaves the rest ordinary`() {
        val exam = ExamItem(courseCode = "BAGER101", courseTitle = "German Level I", examDate = "29-09-2026")

        val days = stripWeek(exams = listOf(exam))

        assertEquals(HomeWeekFlavour.EXAM, days[1].flavour())
        assertTrue(days[1].hasExam)
        assertEquals(1, days[1].exams.size)
        assertTrue(days.filterIndexed { index, _ -> index != 1 }.none { it.hasExam })
    }

    @Test
    fun `october's no-instructional days are read from october not from september`() {
        val days = stripWeek()

        assertEquals("No instructional day", days[3].holidayInfo)
        assertEquals("No instructional day", days[5].holidayInfo)
        assertNull(days[0].holidayInfo)
        assertNull(days[1].holidayInfo)
        assertNull(days[4].holidayInfo)
    }

    @Test
    fun `paging the strip moves all seven days and keeps monday first`() {
        val next = stripWeek(offset = 1)

        assertEquals(LocalDate(2026, 10, 5), next.first().date)
        assertEquals(LocalDate(2026, 10, 11), next.last().date)
        assertEquals(AttendanceDay.entries, next.map { it.dayCode })
        assertTrue(next.none { it.isToday })
    }

    // ── Week header ──

    @Test
    fun `week header names one month, two months, or two years`() {
        val sept = listOf(
            weekDay(date = monday),
            weekDay(date = tuesday),
        )
        assertEquals("Sept 2026", homeWeekHeader(sept, tuesday))

        val straddle = listOf(
            weekDay(date = LocalDate(2026, 8, 31), dayCode = AttendanceDay.MON),
            weekDay(date = LocalDate(2026, 9, 1), dayCode = AttendanceDay.TUE),
        )
        assertEquals("Aug - Sept 2026", homeWeekHeader(straddle, tuesday))

        val newYear = listOf(
            weekDay(date = LocalDate(2026, 12, 31), dayCode = AttendanceDay.THU),
            weekDay(date = LocalDate(2027, 1, 1), dayCode = AttendanceDay.FRI),
        )
        assertEquals("Dec 2026 - Jan 2027", homeWeekHeader(newYear, tuesday))
    }

    @Test
    fun `week header over nothing is empty rather than a crash`() {
        assertEquals("", homeWeekHeader(emptyList(), tuesday))
    }

    // ── Timetable ──

    private fun semester(vararg courses: StoredCourse) = SemesterData(
        semesterId = "CH20262701",
        semesterName = "Fall Semester 2026-27",
        courses = courses.associateBy { it.courseCode },
    )

    @Test
    fun `no semester means an empty week rather than a crash`() {
        val map = buildHomeTimetable(null)

        assertEquals(AttendanceDay.entries.toSet(), map.keys)
        assertTrue(map.values.all { it.isEmpty() })
        assertTrue(buildHomeTimetableMatrix(null).isEmpty())
    }

    @Test
    fun `a course lands only on the days its slot actually exists`() {
        // L1 and L2 are Monday-only in the slot map; Tuesday runs L7/L8 instead.
        val sem = semester(
            StoredCourse(
                courseCode = "BCH2001",
                courseTitle = "Chemistry",
                courseType = "Laboratory",
                slots = listOf("L1", "L2"),
                venue = null,
                faculty = "Dr Raman",
                attendance = StoredAttendance(attendedClasses = 8, totalClasses = 10),
            ),
        )

        val map = buildHomeTimetable(sem)
        assertEquals(1, map.values.sumOf { it.size })
        assertTrue(map.getValue(AttendanceDay.TUE).isEmpty())

        val merged = map.getValue(AttendanceDay.MON).single()
        assertEquals("BCH2001", merged.courseCode)
        assertTrue(merged.isLab)
        assertEquals("Room Assigned", merged.venue)
        assertEquals(8, merged.attended)
        assertEquals(10, merged.total)
        assertEquals(80f, merged.percentage)
    }

    @Test
    fun `back to back sessions of one course merge into a single row`() {
        val sem = semester(
            StoredCourse(
                courseCode = "BCH2001",
                courseTitle = "Chemistry",
                courseType = "Laboratory",
                slots = listOf("L1", "L2"),
            ),
        )

        val merged = buildHomeTimetable(sem).getValue(AttendanceDay.MON).single()
        assertEquals("L1+L2", merged.slotName)
        assertEquals("8:00-9:40", merged.time)
    }

    @Test
    fun `sessions that are not adjacent stay separate rows`() {
        // MON: A1 is 8:00-8:50 and D1 is 9:50-10:40 - a gap of an hour, not of five minutes.
        val sem = semester(
            StoredCourse(
                courseCode = "BAMAT209",
                courseTitle = "Applied Mathematics",
                courseType = "Theory",
                slots = listOf("A1", "D1"),
            ),
        )

        val rows = buildHomeTimetable(sem).getValue(AttendanceDay.MON)
        assertEquals(2, rows.size)
        assertEquals(listOf("A1", "D1"), rows.map { it.slotName })
    }

    @Test
    fun `a slot the map does not know is skipped rather than guessed`() {
        val sem = semester(
            StoredCourse(courseCode = "ZZZ999", courseTitle = "Nowhere", slots = listOf("ZZ9")),
        )

        assertTrue(buildHomeTimetable(sem).values.all { it.isEmpty() })
        assertTrue(buildHomeTimetableMatrix(sem).isEmpty())
    }

    @Test
    fun `the matrix keeps one cell per slot instead of merging them`() {
        val sem = semester(
            StoredCourse(
                courseCode = "BCH2001",
                courseTitle = "Chemistry",
                courseType = "Laboratory",
                slots = listOf("L1", "L2"),
            ),
        )

        val rows = buildHomeTimetableMatrix(sem)
        assertEquals(listOf("L1", "L2"), rows.map { it.slot })
        assertEquals("8:00-8:50", rows[0].time)
        assertEquals(setOf(AttendanceDay.MON), rows[0].cells.keys)
    }

    // ── Tasks ──

    @Test
    fun `task kinds map from the raw type and default to a plain task`() {
        assertEquals(HomeTaskKind.QUIZ, HomeTaskKind.from("quiz"))
        assertEquals(HomeTaskKind.QUIZ, HomeTaskKind.from(" Quiz "))
        assertEquals(HomeTaskKind.ASSIGNMENT, HomeTaskKind.from("assignment"))
        assertEquals(HomeTaskKind.LAB, HomeTaskKind.from("lab"))
        assertEquals(HomeTaskKind.PROJECT, HomeTaskKind.from("project"))
        assertEquals(HomeTaskKind.EXAM, HomeTaskKind.from("exam"))
        assertEquals(HomeTaskKind.HOMEWORK, HomeTaskKind.from("homework"))
        assertEquals(HomeTaskKind.HOMEWORK, HomeTaskKind.from("lms_auto"))
        assertEquals(HomeTaskKind.HOMEWORK, HomeTaskKind.from("LMS_AUTO"))
        assertEquals(HomeTaskKind.OTHER, HomeTaskKind.from("reading"))
        assertEquals(HomeTaskKind.OTHER, HomeTaskKind.from(null))
    }

    @Test
    fun `only this day's open tasks appear, ordered by time then course`() {
        val tasks = listOf(
            task("t1", dueDate = "2026-09-29", dueTime = "09:00", courseCode = "BCH2001"),
            task("t2", dueDate = "2026-09-29", dueTime = "08:00", courseCode = "BAMAT209"),
            task("t3", dueDate = "2026-09-29", dueTime = "09:00", courseCode = "BAECE203", completed = true),
            task("t4", dueDate = "2026-09-30", dueTime = "09:00"),
            task("t5", dueDate = "2026-09-29", dueTime = "09:00", courseCode = "BAECE203"),
        )

        val rows = homeTasksForDay(tasks, tuesday)

        assertEquals(listOf("t2", "t5", "t1"), rows.map { it.id })
        assertEquals(HomeTaskKind.HOMEWORK, rows[0].kind)
    }

    @Test
    fun `deadline dates drop completed tasks and anything that will not parse`() {
        val dates = deadlineDates(
            listOf(
                task("t1", dueDate = "2026-09-29"),
                task("t2", dueDate = "2026-09-30", completed = true),
                task("t3", dueDate = "sometime soon"),
            ),
        )

        assertEquals(setOf(LocalDate(2026, 9, 29)), dates)
    }

    // ── Moodle deadlines ──

    private fun assignment(
        name: String,
        year: Int = 0,
        month: Int = 0,
        day: Int = 0,
        due: String = "",
        done: Boolean = false,
        hidden: Boolean = false,
    ) = MoodleAssignment(
        name = name,
        due = due,
        done = done,
        hidden = hidden,
        year = year,
        monthNumber = month,
        dayOfMonth = day,
    )

    @Test
    fun `the earliest deadline still outstanding wins`() {
        val next = nextMoodleDeadline(
            listOf(
                assignment("BCH2001/Chemistry/Lab report", year = 2026, month = 9, day = 30),
                assignment("BAMAT209/Maths/Problem set", year = 2026, month = 9, day = 29),
            ),
            today = tuesday,
        )

        assertEquals(HomeDeadline("BAMAT209/Maths/Problem set", "Problem set", "BAMAT209", LocalDate(2026, 9, 29)), next)
    }

    @Test
    fun `done, hidden, and already-past assignments are not deadlines`() {
        val next = nextMoodleDeadline(
            listOf(
                assignment("A/Chem/Done", year = 2026, month = 9, day = 29, done = true),
                assignment("B/Chem/Hidden", year = 2026, month = 9, day = 29, hidden = true),
                assignment("C/Chem/Yesterday", year = 2026, month = 9, day = 28),
            ),
            today = tuesday,
        )

        assertNull(next)
    }

    @Test
    fun `an assignment with no usable date source is skipped rather than guessed`() {
        // `due` is localised prose scraped from Moodle, and deliberately not parsed.
        val next = nextMoodleDeadline(
            listOf(assignment("A/Chem/No date", due = "whenever the tutor gets to it")),
            today = tuesday,
        )

        assertNull(next)
    }

    // ── Exams ──

    @Test
    fun `no dated exams means there is no next exam`() {
        assertNull(nextUpcomingExam(emptyList(), Instant.parse("2026-09-29T12:00:00Z")))
    }
}
