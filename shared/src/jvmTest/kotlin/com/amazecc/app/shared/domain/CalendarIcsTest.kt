package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarIcsTest {

    /**
     * Regressions: scraper months leave `year`/`monthNumber` null when the
     * semester bounds are unavailable, so the label is the only source of the
     * date. A (monthNumber, year) vs (year, monthNumber) destructure swap used
     * to build LocalDate(9, 2026, 26), throw, and silently skip every day.
     */
    @Test
    fun septemberLabelWithNullExplicitFields_exports_dtstart_20260926() {
        val month = CalendarMonth(
            month = "September 2026",
            days = listOf(
                CalendarDay(
                    date = 26,
                    events = listOf(CalendarEvent(type = "Holiday", text = "Public Holiday"))
                )
            )
            // year and monthNumber deliberately left null.
        )

        val ics = CalendarIcs.buildAcademicCalendarIcs(listOf(month))

        assertTrue(ics.contains("BEGIN:VEVENT"), "expected a VEVENT block: $ics")
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260926"), "wrong or missing DTSTART: $ics")
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260927"), "DTEND must be exclusive next day: $ics")
        assertEquals(1, CalendarIcs.countVEvents(ics))
    }

    @Test
    fun explicitMonthFieldsAndLabelBothResolve() {
        val month = CalendarMonth(
            month = "September 2026",
            year = 2026,
            monthNumber = 9,
            days = listOf(CalendarDay(26, listOf(CalendarEvent("Other", "CAT II"))))
        )

        val ics = CalendarIcs.buildAcademicCalendarIcs(listOf(month))

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260926"), ics)
        assertEquals(1, CalendarIcs.countVEvents(ics))
    }

    @Test
    fun instructionalDaysAreExcluded() {
        val month = CalendarMonth(
            month = "September 26",
            days = listOf(CalendarDay(26, listOf(CalendarEvent("Instructional Day", "Instructional Day"))))
        )

        val ics = CalendarIcs.buildAcademicCalendarIcs(listOf(month))

        assertEquals(0, CalendarIcs.countVEvents(ics))
    }

    @Test
    fun countVEvents_countsEachEventOnADay() {
        val month = CalendarMonth(
            month = "September 2026",
            days = listOf(
                CalendarDay(26, listOf(CalendarEvent("Holiday", "Holiday"), CalendarEvent("Other", "CAT II")))
            )
        )

        assertEquals(2, CalendarIcs.countVEvents(CalendarIcs.buildAcademicCalendarIcs(listOf(month))))
    }
}
