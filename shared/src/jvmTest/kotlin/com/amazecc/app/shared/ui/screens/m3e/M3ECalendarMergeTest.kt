package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.AttendanceDay
import com.amazecc.app.shared.domain.CalendarEventItem
import com.amazecc.app.shared.domain.CalendarRegistration
import com.amazecc.app.shared.domain.CalendarSources
import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.DayEvent
import com.amazecc.app.shared.domain.DayModel
import com.amazecc.app.shared.domain.DayType
import com.amazecc.app.shared.domain.EventKind
import com.amazecc.app.shared.domain.Exam
import com.amazecc.app.shared.domain.MonthSummary
import com.amazecc.app.shared.domain.MonthModel
import com.amazecc.app.shared.domain.OdCourse
import com.amazecc.app.shared.domain.OdRecord
import com.amazecc.app.shared.domain.buildEnrichedCalendars
import com.amazecc.app.shared.domain.CalendarDay as DomainCalendarDay
import com.amazecc.app.shared.domain.CalendarMonth as DomainCalendarMonth
import com.amazecc.app.shared.domain.emptyAttendance
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.MoodleAssignment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-115: the super calendar merges every real source Kotlin has data for, then keeps each entry's
 * source through to the M3E view model so the dots, legend and filter can tell them apart.
 */
class M3ECalendarMergeTest {

    private val day = LocalDate(2026, 11, 12)
    private val dayKey = "2026-11-12"

    private fun mergedSources() = CalendarSources(
        calendars = listOf(
            DomainCalendarMonth(
                label = "November 2026",
                days = listOf(
                    DomainCalendarDay(
                        date = 12,
                        events = listOf(
                            CalendarEventItem(type = "Holiday", text = "Holiday", category = "Gandhi Jayanthi")
                        )
                    )
                )
            )
        ),
        // The snapshot drops the exam series key; Kotlin passes one blank bucket, exactly as the
        // legacy CalendarScreen does. Papers still land on the day with time and venue.
        examSchedule = mapOf(
            "" to listOf(Exam(courseTitle = "Biology", date = dayKey, time = "09:15 AM", venue = "LH1"))
        ),
        moodle = listOf(
            MoodleAssignment(name = "Moodle Quiz", due = dayKey, year = 2026, monthNumber = 11, dayOfMonth = 12)
        ),
        courses = listOf(
            CourseAttendance(
                courseCode = "CS101",
                courseTitle = "Theory Class",
                courseType = "Theory",
                slotName = "A1",
                slotVenue = "LH2",
                faculty = "",
                credits = null,
                category = null,
                attendedClasses = 1,
                totalClasses = 1,
                attendancePercentage = "100",
                logs = listOf(AttendanceDay(date = dayKey, status = "present")),
            )
        ),
        od = listOf(OdRecord(date = dayKey, courses = listOf(OdCourse(title = "Lab OD", type = "lab")), total = 2)),
        tasks = listOf(
            HomeworkTask(
                id = "t1",
                courseCode = "CS101",
                courseTitle = "Theory Class",
                title = "Task A",
                dueDate = dayKey,
                createdAt = "2026-01-01",
            )
        ),
        registeredEvents = listOf(
            CalendarRegistration(title = "Robotics Club", date = dayKey, time = "14:00", venue = "LH3")
        ),
    )

    @Test
    fun everyRealSourceLandsOnTheSameDayAndKeepsItsSource() {
        val months = buildEnrichedCalendars(mergedSources(), now = day)
        val mapped = months.single().toM3ECalendarMonth(day)
        val entries = mapped.days.single { it.date == 12 }.events

        assertEquals(7, entries.size)
        val sources = entries.map { it.source }.toSet()
        assertTrue(M3ECalendarSource.HOLIDAY in sources)
        assertTrue(M3ECalendarSource.EXAM in sources)
        assertTrue(M3ECalendarSource.DEADLINE in sources) // Moodle deadline + task
        assertTrue(M3ECalendarSource.CLASS in sources)
        assertTrue(M3ECalendarSource.OD in sources)
        assertTrue(M3ECalendarSource.EVENT in sources)
        assertEquals(2, entries.count { it.source == M3ECalendarSource.DEADLINE })
        assertTrue(entries.any { it.title == "Biology" && it.category.contains("LH1") })
    }

    @Test
    fun foldedExamPapersStillSurfaceAsAnExamDot() {
        val paper = DayEvent(
            kind = EventKind.EXAM,
            title = "Biology",
            detail = "CAT II · 09:15 · LH1",
            priority = 0,
            tone = "amber",
        )
        val month = MonthModel(
            id = "2026-10-0",
            label = "November 2026",
            shortLabel = "Nov 2026",
            monthIndex = 10,
            year = 2026,
            days = listOf(
                DayModel(
                    date = 12,
                    fullDate = day,
                    dateKey = dayKey,
                    weekday = "Thu",
                    dayType = DayType.INSTRUCTIONAL,
                    events = listOf(
                        DayEvent(
                            kind = EventKind.MILESTONE,
                            title = "CAT II",
                            detail = "Continuous Assessment Test II",
                            priority = 1,
                            tone = "indigo",
                            classesRun = false,
                            papers = listOf(paper),
                        )
                    ),
                    attendance = emptyAttendance(),
                    taskCount = 0,
                )
            ),
            summary = MonthSummary(total = 30, working = 0, holiday = 0, other = 0),
        )

        val entries = month.toM3ECalendarMonth(day).days.single().events
        assertEquals(listOf(M3ECalendarSource.MILESTONE, M3ECalendarSource.EXAM), entries.map { it.source })
    }
}
