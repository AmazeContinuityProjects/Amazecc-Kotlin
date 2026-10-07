package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.EventHubRegisteredEvent
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.MoodleAssignment
import com.amazecc.app.shared.model.NamedCalendar
import com.amazecc.app.shared.model.CalendarDay as PayloadCalendarDay
import com.amazecc.app.shared.model.CalendarEvent as PayloadCalendarEvent
import com.amazecc.app.shared.model.CalendarMonth as PayloadCalendarMonth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The assembly between the snapshot and `buildEnrichedCalendars`.
 *
 * Two questions, and the screen must not be the place either is answered: what goes into the
 * calendar (this suite), and what a day then contains (`CalendarEnrichTest`).
 */
class CalendarProjectionTest {

    private val sem = "CH20262701"

    // ── fixtures ─────────────────────────────────────────────────────────────

    /**
     * The picker's payload shape: a name, and months whose days carry VTOP's own wording - the
     * type word in `text`, the name in `category`.
     */
    private fun namedCalendar(name: String = "General Semester") = NamedCalendar(
        name = name,
        months = listOf(
            PayloadCalendarMonth(
                month = "August 2026",
                days = listOf(
                    PayloadCalendarDay(
                        date = 12,
                        events = listOf(
                            PayloadCalendarEvent(
                                type = "Other",
                                text = "Instructional Day",
                                category = "Working day",
                            ),
                        ),
                    ),
                    PayloadCalendarDay(
                        date = 15,
                        events = listOf(
                            PayloadCalendarEvent(
                                type = "Other",
                                text = "Holiday",
                                category = "Independence Day",
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * One semester with one attendance-bearing course, and a schedule whose second paper belongs
     * somewhere else - so "which exams" can be told apart from "which semester".
     *
     * The log is On Duty rather than Present, because that is the status that has to surface twice:
     * once as a class record and once as an on-duty hour.
     */
    private fun payload() = DomainSnapshot(
        academics = Academics(
            semesters = mapOf(
                sem to Semester(
                    id = sem,
                    courses = mapOf(
                        "CH20241101" to Course(
                            code = "CH20241101",
                            title = "Fluid Mechanics",
                            type = "Embedded Theory",
                            slots = listOf("A1"),
                            attendance = Attendance(
                                attended = 8,
                                total = 10,
                                percentage = "80",
                                logs = listOf(AttendanceDay(date = "2026-08-12", status = "On Duty")),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        schedule = Schedule(
            exams = listOf(
                Exam(
                    courseCode = "CH20241101",
                    courseTitle = "Fluid Mechanics",
                    date = "2026-08-12",
                    time = "09:15 AM",
                    venue = "LH1",
                    semesterId = sem,
                ),
                Exam(courseCode = "MA20241102", courseTitle = "Maths", date = "2026-08-20", semesterId = "OTHER"),
            ),
        ),
    )

    private fun moodle(name: String, due: String) = MoodleAssignment(name = name, due = due)

    private fun task(id: String, title: String, dueDate: String) = HomeworkTask(
        id = id,
        courseCode = "CH20241101",
        courseTitle = "Fluid Mechanics",
        title = title,
        dueDate = dueDate,
        createdAt = "",
    )

    private fun dayAt(month: MonthModel, date: Int): DayModel = month.days.first { it.date == date }

    private fun sources(
        calendar: NamedCalendar? = namedCalendar(),
        snapshot: DomainSnapshot = payload(),
        semesterId: String = sem,
        moodle: List<MoodleAssignment> = emptyList(),
        tasks: List<HomeworkTask> = emptyList(),
        odTracker: Map<String, List<TrackedOd>> = emptyMap(),
        registeredEvents: List<EventHubRegisteredEvent> = emptyList(),
        profileImageUrl: String? = null,
    ) = calendarSources(
        calendar = calendar,
        snapshot = snapshot,
        semesterId = semesterId,
        moodle = moodle,
        tasks = tasks,
        odTracker = odTracker,
        registeredEvents = registeredEvents,
        profileImageUrl = profileImageUrl,
    )

    // ── toCalendarMonths ─────────────────────────────────────────────────────

    @Test
    fun `flattens the chosen calendar into domain months`() {
        val months = namedCalendar().toCalendarMonths()

        assertEquals(1, months.size)
        assertEquals("August 2026", months[0].label)
        assertEquals(listOf(12, 15), months[0].days.map { it.date })

        val event = months[0].days.first().events.single()
        assertEquals("Instructional Day", event.text)
        assertEquals("Working day", event.category)
        assertEquals("Other", event.type)
    }

    @Test
    fun `no calendar picked yields no months rather than failing`() {
        assertEquals(emptyList(), sources(calendar = null).calendars)
    }

    // ── toCalendarRegistrations ──────────────────────────────────────────────

    @Test
    fun `maps EventHub's registration fields onto the calendar's`() {
        val mapped = listOf(
            EventHubRegisteredEvent(
                eid = "e1",
                title = "Robotics Workshop",
                location = "AB1-02",
                date = "2026-08-12",
                time = "04:00 PM",
            ),
        ).toCalendarRegistrations().single()

        assertEquals("e1", mapped.eid)
        assertEquals("Robotics Workshop", mapped.title)
        assertEquals("AB1-02", mapped.venue)
        assertEquals("2026-08-12", mapped.date)
        assertEquals("04:00 PM", mapped.time)
        // Kotlin's payload has no such field yet, so nothing is dropped as unpaid.
        assertNull(mapped.paymentStatus)
    }

    // ── calendarSources ──────────────────────────────────────────────────────

    @Test
    fun `scopes the exam schedule to the chosen semester under one blank key`() {
        val schedule = sources().examSchedule

        assertEquals(setOf(""), schedule.keys)
        assertEquals(listOf("CH20241101"), schedule.getValue("").map { it.courseCode })
    }

    @Test
    fun `an unknown semester id still gets every exam on the schedule`() {
        val schedule = sources(semesterId = "NOPE").examSchedule

        assertEquals(listOf("CH20241101", "MA20241102"), schedule.getValue("").map { it.courseCode })
    }

    @Test
    fun `derives the on-duty records from the attendance it describes`() {
        val od = sources().od

        assertEquals(1, od.size)
        assertEquals("2026-08-12", od.single().date)
        assertEquals("Fluid Mechanics", od.single().courses.single().title)
        // The slot is `A1`, so the row is theory and counts one hour rather than two.
        assertEquals(1, od.single().total)
    }

    @Test
    fun `passes the stores that live outside the snapshot straight through`() {
        val moodle = listOf(moodle("Lab report", "2026-08-12"))
        val tasks = listOf(task("t1", "Read chapter 4", "2026-08-12"))
        val tracker = mapOf("2026-08-12" to listOf(TrackedOd("Fluid Mechanics", "wasted")))
        val registrations = listOf(
            EventHubRegisteredEvent(eid = "e1", title = "Workshop", location = "AB1-02", date = "2026-08-12"),
        )

        val built = sources(
            moodle = moodle,
            tasks = tasks,
            odTracker = tracker,
            registeredEvents = registrations,
            profileImageUrl = "https://example.test/me.png",
        )

        assertEquals(moodle, built.moodle)
        assertEquals(tasks, built.tasks)
        assertEquals(tracker, built.odTracker)
        assertEquals(listOf("e1"), built.registeredEvents.map { it.eid })
        assertEquals("https://example.test/me.png", built.profileImageUrl)
    }

    // ── end to end ───────────────────────────────────────────────────────────

    @Test
    fun `every source lands on the day it belongs to`() {
        val months = buildEnrichedCalendars(
            sources(
                moodle = listOf(moodle("Lab report", "2026-08-12")),
                tasks = listOf(task("t1", "Read chapter 4", "2026-08-12")),
                registeredEvents = listOf(
                    EventHubRegisteredEvent(
                        eid = "e1",
                        title = "Workshop",
                        location = "AB1-02",
                        date = "2026-08-12",
                    ),
                ),
            ),
            now = LocalDate(2026, 8, 1),
        )

        val kinds = dayAt(months.first(), 12).events.map { it.kind }
        assertTrue(EventKind.EXAM in kinds, "exam schedule: $kinds")
        assertEquals(2, kinds.count { it == EventKind.ASSIGNMENT }, "moodle + task: $kinds")
        assertTrue(EventKind.CLASS in kinds, "attendance log: $kinds")
        assertTrue(EventKind.OD in kinds, "on-duty hours: $kinds")
        assertTrue(EventKind.EVENT in kinds, "registration: $kinds")
        assertTrue(EventKind.WORKING in kinds, "the published day type: $kinds")
    }

    @Test
    fun `a holiday the picker published survives the round trip`() {
        val months = buildEnrichedCalendars(sources(), now = LocalDate(2026, 8, 1))

        val day = dayAt(months.first(), 15)
        assertEquals(DayType.HOLIDAY, day.dayType)
        assertEquals(listOf(EventKind.HOLIDAY), day.events.map { it.kind })
    }
}
