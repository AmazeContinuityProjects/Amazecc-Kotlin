package com.amazecc.app.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The port of the date/month/day-order half of
 * `AmazeCC/src/__tests__/calendarDay.test.ts`.
 *
 * The rest of that suite drives [buildEnrichedCalendars], which lands in
 * `CalendarEnrich.kt`; these are the pieces that do not need a month built to
 * be observable.
 */
class CalendarDayTest {

    private fun ev(type: String, text: String, category: String) =
        RawCalendarEvent(type = type, text = text, category = category)

    private fun iso(day: Int): String = "2026-08-" + (if (day < 10) "0$day" else "$day")

    // ── parseDayOrder ────────────────────────────────────────────────────────

    @Test
    fun `reads the day out of the full published phrase`() {
        val order = parseDayOrder(
            listOf(ev("Other", "Instructional Day", "Instructional Day Order - Thursday Day Order")),
        )
        assertEquals("THU", order?.name)
    }

    @Test
    fun `copes with the separator being a colon or an en dash`() {
        assertEquals(
            "FRI",
            parseDayOrder(listOf(ev("Other", "Instructional Day", "Instructional Day Order: Friday")))?.name,
        )
        assertEquals(
            "MON",
            parseDayOrder(
                listOf(ev("Other", "Instructional Day", "Instructional Day Order - Monday Day Order")),
            )?.name,
        )
    }

    @Test
    fun `reads a bare abbreviation`() {
        assertEquals(
            "SAT",
            parseDayOrder(listOf(ev("Other", "Instructional Day", "Instructional Day Order - SAT")))?.name,
        )
    }

    @Test
    fun `takes the day order and not the dates own weekday`() {
        // The failure this exists to prevent. A reschedule lands on a Saturday
        // and is written the way a human writes it - real date first - so the
        // LAST weekday named is the answer and the first is the trap.
        val order = parseDayOrder(
            listOf(
                ev("Other", "Instructional Day", "Saturday - Instruction Day Order - Thursday Day Order"),
            ),
        )
        assertEquals("THU", order?.name)
    }

    @Test
    fun `finds the phrase in text as well as category`() {
        val order = parseDayOrder(
            listOf(ev("Other", "Instructional Day Order - Tuesday Day Order", "Working day")),
        )
        assertEquals("TUE", order?.name)
    }

    @Test
    fun `reads all seven long forms and not just the easy ones`() {
        // Written as a table on purpose. An earlier version enumerated the
        // suffixes and quietly missed `sday`, so Tuesday - and only Tuesday -
        // stopped matching. A missing weekday does not throw; it turns a
        // rescheduled Tuesday into an ordinary teaching day, which is invisible
        // until you are sitting in the wrong class.
        val forms = listOf(
            "Monday" to "MON", "Tuesday" to "TUE", "Wednesday" to "WED",
            "Thursday" to "THU", "Friday" to "FRI", "Saturday" to "SAT",
            "Sunday" to "SUN",
        )
        for ((name, expected) in forms) {
            val order = parseDayOrder(
                listOf(ev("Other", "Instructional Day", "Instructional Day Order - $name Day Order")),
            )
            assertEquals(expected, order?.name, "failed on $name")
        }
    }

    @Test
    fun `does not read a word that merely starts like a weekday`() {
        assertNull(
            parseDayOrder(listOf(ev("Other", "Instructional Day", "Working day - Monsoon Season"))),
        )
    }

    @Test
    fun `ignores a weekday that only appears on a non-instructional entry`() {
        // A festival named after a weekday must not be able to declare a
        // reschedule.
        assertNull(
            parseDayOrder(
                listOf(
                    ev("Other", "Holiday", "Sunday Observance"),
                    ev("Other", "Instructional Day", "Working day"),
                ),
            ),
        )
    }

    @Test
    fun `is null on an ordinary working day`() {
        assertNull(parseDayOrder(listOf(ev("Other", "Instructional Day", "Working day"))))
        assertNull(parseDayOrder(emptyList()))
    }

    @Test
    fun `does not read a weekday out of the type word itself`() {
        // "Instructional Day" and "No Instructional Day" both contain
        // day-shaped words; neither names a timetable to follow.
        assertNull(parseDayOrder(listOf(ev("Other", "Instructional Day", "Instructional Day Order"))))
    }

    // ── parseDayDate / dateKey ───────────────────────────────────────────────

    @Test
    fun `reads a YYYY-MM-DD key as a calendar date`() {
        assertEquals(LocalDate(2026, 8, 12), parseDayDate("2026-08-12"))
    }

    @Test
    fun `round-trips through dateKey`() {
        for (d in listOf(1, 9, 12, 28, 31)) {
            assertEquals(iso(d), dateKey(parseDayDate(iso(d))!!))
        }
    }

    @Test
    fun `falls back to the general parser for VTOP's textual dates`() {
        assertEquals(12, parseDayDate("Aug 12, 2026")?.dayOfMonth)
    }

    @Test
    fun `returns null rather than throwing on junk`() {
        assertNull(parseDayDate("not a date"))
        assertNull(parseDayDate(null))
        assertNull(parseDayDate(""))
        assertFalse(isValidDay(parseDayDate("not a date")))
        assertTrue(isValidDay(parseDayDate("2026-08-12")))
    }

    // ── parseCalendarMonth ───────────────────────────────────────────────────

    @Test
    fun `reads VTOP's August 2026`() {
        assertEquals(ParsedMonth(monthIndex = 7, year = 2026), parseCalendarMonth("August 2026"))
    }

    @Test
    fun `reads a bare month name using the callers own year`() {
        assertEquals(ParsedMonth(monthIndex = 0, year = 2027), parseCalendarMonth("January", fallbackYear = 2027))
    }

    @Test
    fun `falls back to today rather than producing nonsense`() {
        val now = LocalDate(2026, 3, 9)
        assertEquals(
            ParsedMonth(monthIndex = now.monthNumber - 1, year = now.year),
            parseCalendarMonth(null, fallbackYear = null, now = now),
        )
    }

    // ── leadingBlanks ────────────────────────────────────────────────────────

    @Test
    fun `leadingBlanks is Monday-first`() {
        // 1 Aug 2026 is a Saturday: five Monday-first cells before it.
        assertEquals(5, leadingBlanks(2026, 7))
        // 1 Jun 2026 is a Monday: none.
        assertEquals(0, leadingBlanks(2026, 5))
        // 1 Nov 2026 is a Sunday: a full week of blanks.
        assertEquals(6, leadingBlanks(2026, 10))
    }

    // ── daysLeft / relativeDayLabel ──────────────────────────────────────────

    private val from = LocalDate(2026, 8, 12)

    @Test
    fun `counts whole days forward and back`() {
        assertEquals(0, daysLeft(LocalDate(2026, 8, 12), from))
        assertEquals(1, daysLeft(LocalDate(2026, 8, 13), from))
        assertEquals(-1, daysLeft(LocalDate(2026, 8, 11), from))
    }

    @Test
    fun `names today and tomorrow and otherwise formats`() {
        assertEquals("Today", relativeDayLabel(LocalDate(2026, 8, 12), from))
        assertEquals("Tomorrow", relativeDayLabel(LocalDate(2026, 8, 13), from))
        assertEquals("Yesterday", relativeDayLabel(LocalDate(2026, 8, 11), from))
        assertTrue(relativeDayLabel(LocalDate(2026, 8, 20), from).contains("20"))
    }

    @Test
    fun `has no opinion about a missing or unparseable date`() {
        assertNull(daysLeft(null, from))
        assertNull(daysLeft(parseDayDate("not a date"), from))
        assertEquals("", relativeDayLabel(null, from))
    }
}
