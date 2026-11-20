package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.CalendarsListRes
import com.amazecc.app.shared.model.NamedCalendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Regression: the headless scraper backfills empty day boxes; sanitizing must not delete them. */
class CalendarSanitizerKeepsEmptyDaysTest {

    private val november = CalendarMonth(
        month = "November",
        year = 2026,
        monthNumber = 11,
        days = listOf(
            CalendarDay(1), // empty box produced by the scraper's full-month backfill
            CalendarDay(8, listOf(CalendarEvent(type = "Holiday", text = "Diwali Holiday"))),
            CalendarDay(30) // final box of a 30-day month
        )
    )

    @Test
    fun `sanitizeCalendar keeps eventless day boxes`() {
        val cleaned = assertNotNull(AppSanitizers.sanitizeCalendar(CalendarRes(success = true, months = listOf(november))))
        val days = cleaned.months.single().days
        assertEquals(listOf(1, 8, 30), days.map { it.date })
        assertEquals(0, days[0].events.size)
        assertEquals(0, days[2].events.size)
        assertEquals("Diwali Holiday", days[1].events.single().text)
        assertEquals(2026, cleaned.months.single().year)
        assertEquals(11, cleaned.months.single().monthNumber)
    }

    @Test
    fun `sanitizeCalendarsList keeps eventless day boxes`() {
        val cleaned = assertNotNull(
            AppSanitizers.sanitizeCalendarsList(
                CalendarsListRes(success = true, calendars = listOf(NamedCalendar(name = "General Semester", months = listOf(november))))
            )
        )
        val days = cleaned.calendars.single().months.single().days
        assertEquals(listOf(1, 8, 30), days.map { it.date })
    }
}
