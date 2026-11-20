package com.amazecc.app.shared.domain.overhaul

import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.ExamItem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-128: the strip's week model is pure, so the two things that used to go wrong
 * on screen — the wrong seven days, and classless weekends taking up room — are
 * pinned here without needing a device.
 */
class M3EHomeWeekDaysTest {

    /** Tue 29 Sept 2026, matching the web fixture: Mon 28 Sept .. Sun 4 Oct. */
    private val tuesday = LocalDate(2026, 9, 29)
    private val dates = listOf(
        LocalDate(2026, 9, 28), // Mon
        LocalDate(2026, 9, 29), // Tue
        LocalDate(2026, 9, 30), // Wed
        LocalDate(2026, 10, 1), // Thu
        LocalDate(2026, 10, 2), // Fri
        LocalDate(2026, 10, 3), // Sat
        LocalDate(2026, 10, 4), // Sun
    )

    @Test
    fun `build returns a monday-first seven day week with super-calendar markings`() {
        val paper = ExamItem(courseCode = "CSE3002", examDate = "29-09-2026")
        val months = listOf(
            CalendarMonth("Sept 2026"),
            CalendarMonth(
                "Oct 2026",
                listOf(
                    CalendarDay(1, listOf(CalendarEvent(type = "event", text = "No instructional day"))),
                    CalendarDay(3, listOf(CalendarEvent(type = "event", text = "No instructional day"))),
                ),
            ),
        )

        val days = M3EHomeWeekDays.build(
            weekOffset = 0,
            today = tuesday,
            exams = listOf(paper),
            calendarMonths = months,
            deadlineDates = setOf(LocalDate(2026, 9, 30)),
            courses = emptyList(),
            calendarRes = null,
            selectedDate = tuesday
        )

        assertEquals(7, days.size)
        assertEquals(dates, days.map { it.date })
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), days.map { it.dayName })
        assertEquals(28, days.first().dayNumber)
        assertEquals(29, days[1].dayNumber)

        // The real Tuesday is both today and the selected day, and carries the exam.
        assertTrue(days[1].isToday)
        assertTrue(days[1].isSelected)
        assertEquals(1, days[1].examCount)

        // Academic-calendar holidays, not "weekend with no classes".
        assertNotNull(days[3].holidayName)
        assertTrue(days[3].isHoliday)
        assertNotNull(days[5].holidayName)
        assertTrue(days[5].isHoliday)
        assertFalse(days[2].isHoliday)

        // The unfinished-task (Moodle) deadline reaches the strip as its own hint.
        assertTrue(days[2].hasDeadline)
        assertFalse(days[1].hasDeadline)

        // No courses enrolled, so nobody has classes.
        assertTrue(days.none { it.hasClasses })
    }

    private fun week(
        classesOn: Set<Int>,
        todayIndex: Int? = null,
        selectedIndex: Int? = null
    ): List<M3EWeekDay> = dates.mapIndexed { index, date ->
        M3EWeekDay(
            date = date,
            dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3),
            dayNumber = date.dayOfMonth,
            isToday = index == todayIndex,
            isSelected = index == selectedIndex,
            hasClasses = index in classesOn,
            isHoliday = false
        )
    }

    @Test
    fun `visibleInStrip hides classless weekends but keeps all five weekdays`() {
        val visible = M3EHomeWeekDays.visibleInStrip(
            week(classesOn = setOf(0, 1, 2, 3, 4), todayIndex = 1, selectedIndex = 1)
        )

        assertEquals(5, visible.size)
        assertEquals(dates.take(5), visible.map { it.date })
        assertFalse(visible.any { it.date.dayOfWeek.value in 6..7 })
    }

    @Test
    fun `visibleInStrip keeps weekend days when they have classes`() {
        val visible = M3EHomeWeekDays.visibleInStrip(
            week(classesOn = setOf(0, 1, 2, 3, 4, 5, 6), todayIndex = 1, selectedIndex = 1)
        )

        assertEquals(7, visible.size)
        assertTrue(visible.any { it.date == dates[5] && it.hasClasses })
        assertTrue(visible.any { it.date == dates[6] && it.hasClasses })
    }

    @Test
    fun `visibleInStrip never hides a classless weekend that is today or selected`() {
        val visible = M3EHomeWeekDays.visibleInStrip(
            week(classesOn = setOf(0, 1, 2, 3, 4), todayIndex = 5, selectedIndex = 6)
        )

        assertEquals(7, visible.size)
        assertTrue(visible.any { it.date == dates[5] && it.isToday && !it.hasClasses })
        assertTrue(visible.any { it.date == dates[6] && it.isSelected && !it.hasClasses })
    }
}
