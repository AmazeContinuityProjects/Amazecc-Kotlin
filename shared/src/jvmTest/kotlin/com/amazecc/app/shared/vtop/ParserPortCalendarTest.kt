package com.amazecc.app.shared.vtop

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Headless port tests for [VtopCalendarScraper]: month derivation, day/event parsing and grid maths. */
class ParserPortCalendarTest {

    @Test
    fun `derives semester months for 01, 05 and 07 semesters`() {
        assertEquals(
            listOf(7 to 2026, 8 to 2026, 9 to 2026, 10 to 2026, 11 to 2026),
            VtopCalendarScraper.semesterMonths("CH20262701")
        )
        assertEquals(
            listOf(12 to 2026, 1 to 2027, 2 to 2027, 3 to 2027, 4 to 2027),
            VtopCalendarScraper.semesterMonths("CH20262705")
        )
        assertEquals(
            listOf(5 to 2027, 6 to 2027, 7 to 2027),
            VtopCalendarScraper.semesterMonths("CH20262707")
        )
    }

    @Test
    fun `coerces class group id per semester`() {
        assertEquals("ALL", VtopCalendarScraper.calendarClassGroup("CH20262705", "SOMETHING"))
        assertEquals("ALL02", VtopCalendarScraper.calendarClassGroup("CH20262705", "ALL02"))
        assertEquals("ALL", VtopCalendarScraper.calendarClassGroup("CH20262707", "ALL05"))
        assertEquals("CSE1", VtopCalendarScraper.calendarClassGroup("CH20262701", "CSE1"))
    }

    @Test
    fun `resolves month bounds from label and semester code`() {
        assertEquals(2026 to 7, VtopCalendarScraper.calendarMonthBounds("CH20262701", "July"))
        assertEquals(2026 to 11, VtopCalendarScraper.calendarMonthBounds("CH20262701", "November 2026"))
        assertEquals(2027 to 1, VtopCalendarScraper.calendarMonthBounds("CH20262705", "January"))
        assertEquals(2026 to 12, VtopCalendarScraper.calendarMonthBounds("CH20262705", "DEC"))
        assertEquals(2027 to 7, VtopCalendarScraper.calendarMonthBounds("CH20262707", "July"))
        assertNull(VtopCalendarScraper.calendarMonthBounds("CH20262701", "NotAMonth"))
    }

    @Test
    fun `grid bounds and leading blanks use real calendar facts`() {
        val bounds = VtopCalendarScraper.calendarMonthBounds("CH20262701", "November")!!
        assertEquals(2026 to 11, bounds)

        // kotlinx.datetime confirms 1 Nov 2026 is a Sunday, i.e. 6 Monday-first blanks (not 0).
        assertEquals(DayOfWeek.SUNDAY, LocalDate(2026, 11, 1).dayOfWeek)
        assertEquals(6, VtopCalendarScraper.leadingBlankDays(bounds.first, bounds.second))
        assertEquals(30, VtopCalendarScraper.daysInMonth(bounds.first, bounds.second))

        assertEquals(0, VtopCalendarScraper.leadingBlankDays(2026, 6)) // 1 Jun 2026 is a Monday
        assertEquals(28, VtopCalendarScraper.daysInMonth(2026, 2))
        assertEquals(29, VtopCalendarScraper.daysInMonth(2028, 2))
        assertEquals(0, VtopCalendarScraper.daysInMonth(2026, 13))
    }

    @Test
    fun `emitted months carry year and month number from the semester code`() {
        val payload = """
        {"ok":true,"calendars":[{"month":"November","days":[]},{"month":"July","days":[]}]}
        """.trimIndent()

        val res = VtopCalendarScraper.parsePayload(payload, "CH20262701")
        assertNotNull(res)
        assertEquals(2026, res.months[0].year)
        assertEquals(11, res.months[0].monthNumber)
        assertEquals(2026, res.months[1].year)
        assertEquals(7, res.months[1].monthNumber)

        val other = VtopCalendarScraper.parsePayload(payload, "CH20262707")
        assertNotNull(other)
        assertEquals(2027, other.months[1].year, "07 semester July is in the following year")
        assertEquals(7, other.months[1].monthNumber)
    }

    @Test
    fun `parses days and derives event type and category`() {
        val payload = """
        {
          "ok": true,
          "calendars": [{
            "month": "November",
            "days": [
              {"date": 8, "events": [
                {"text": "Diwali Holiday (General)", "color": "#B22222"},
                {"text": "Regular Instructional Day (Academic)", "color": null},
                {"text": "Sports meet", "color": null}
              ]},
              {"date": 19, "events": [{"text": "Mid Semester Examinations (Exams)", "color": "red"}]}
            ]
          }]
        }
        """.trimIndent()

        val res = VtopCalendarScraper.parsePayload(payload)
        assertNotNull(res)
        assertTrue(res.success)
        assertEquals(1, res.months.size)
        val november = res.months[0]
        assertEquals("November", november.month)
        assertEquals(2, november.days.size)

        val day8 = november.days[0]
        assertEquals(8, day8.date)
        assertEquals(3, day8.events.size)
        assertEquals("Holiday", day8.events[0].type)
        assertEquals("#B22222", day8.events[0].color)
        assertEquals("General", day8.events[0].category)
        assertEquals("Instructional Day", day8.events[1].type)
        assertEquals("Academic", day8.events[1].category)
        assertNull(day8.events[1].color)
        assertEquals("Other", day8.events[2].type)
        assertEquals("General", day8.events[2].category)

        assertEquals(19, november.days[1].date)
        assertEquals("Exams", november.days[1].events[0].category)
    }

    @Test
    fun `complete month grid fills every box through the real last day`() {
        // Only two days carry events; the rest must still exist as empty day boxes.
        val payload = """
        {"ok":true,"calendars":[{"month":"November","days":[
          {"date":8,"events":[{"text":"Diwali Holiday (General)","color":null}]},
          {"date":19,"events":[{"text":"Mid Semester Examinations (Exams)","color":null}]}
        ]}]}
        """.trimIndent()

        val res = VtopCalendarScraper.parsePayload(payload, "CH20262701")
        assertNotNull(res)
        val november = res.months[0]
        assertEquals(30, november.days.size, "November 2026 has exactly 30 boxes")
        assertEquals(1, november.days.first().date)
        assertEquals(30, november.days.last().date)
        assertEquals(1, november.days[7].events.size, "real event day keeps its event")
        assertEquals(0, november.days[0].events.size, "filler days are empty, never fabricated events")
    }

    @Test
    fun `failed payload returns null`() {
        assertNull(VtopCalendarScraper.parsePayload(""))
        assertNull(VtopCalendarScraper.parsePayload("not json"))
        assertNull(VtopCalendarScraper.parsePayload("""{"ok":false,"error":"no calendar data"}"""))
    }
}
