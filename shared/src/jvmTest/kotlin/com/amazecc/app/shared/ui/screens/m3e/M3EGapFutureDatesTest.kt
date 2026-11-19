package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.Academics
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Semester
import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the legacy future-class rule the M3E predictor now uses: only real
 * calendar working days that the course actually meets on, up to the cutoff.
 */
class M3EGapFutureDatesTest {

    // Nov 1 2026 is a Sunday, so the Mondays are 2, 9, 16, 23, 30.
    private val today = LocalDate(2026, 11, 10)

    private fun courseDomain(
        code: String = "CSE1001",
        type: String = "Theory Only",
        slot: String = "S11" // Monday-only slot in SlotMap (A1 also occurs on Wednesday)
    ): DomainSnapshot {
        val course = Course(
            code = code,
            title = "Test Course",
            type = type,
            slots = listOf(slot),
            attendance = Attendance(attended = 5, total = 10, percentage = "50")
        )
        return DomainSnapshot(
            academics = Academics(
                selectedSemesterId = "S1",
                semesters = mapOf("S1" to Semester(id = "S1", courses = mapOf(code to course)))
            )
        )
    }

    /** All November 2026 days are instructional days; [extra] adds dated events. */
    private fun novemberCalendar(extra: Map<Int, String> = emptyMap()): List<CalendarMonth> {
        val days = (1..30).map { d ->
            val events = mutableListOf(CalendarEvent(type = "Instructional Day", text = "Instructional Day"))
            extra[d]?.let { events += CalendarEvent(type = "Other", text = it) }
            CalendarDay(date = d, events = events)
        }
        return listOf(CalendarMonth(month = "November 2026", days = days))
    }

    private fun derive(
        months: List<CalendarMonth> = novemberCalendar(),
        domain: DomainSnapshot = courseDomain(),
        code: String = "CSE1001",
        type: String = "Theory Only",
        mode: M3ECutoffMode = M3ECutoffMode.END,
        skipped: Set<Int> = emptySet()
    ) = M3EGapFutureDates.deriveFutureClassDates(
        months = months,
        domain = domain,
        courseCode = code,
        courseType = type,
        cutoffMode = mode,
        today = today,
        skippedKeys = skipped
    )

    @Test
    fun derive_returnsOnlyCourseWeekdaysAfterToday() {
        val dates = derive()
        // "S11" is a Monday-only slot; of those, only 16/23/30 are on/after Nov 10.
        assertEquals(listOf(20261116, 20261123, 20261130), dates.map { it.key })
        assertEquals(listOf("Nov 16", "Nov 23", "Nov 30"), dates.map { it.label })
        assertTrue(dates.none { it.isSkipped })
    }

    @Test
    fun derive_marksHostSkippedDates() {
        val dates = derive(skipped = setOf(20261123))
        assertEquals(
            mapOf(20261116 to false, 20261123 to true, 20261130 to false),
            dates.associate { it.key to it.isSkipped }
        )
    }

    @Test
    fun derive_lidCutoffForTheoryUsesTheoryDate() {
        val months = novemberCalendar(mapOf(25 to "LID for theory classes", 20 to "LID for laboratory classes"))
        // Theory course => theory LID (Nov 25), so Nov 30 is excluded but 16/23 survive.
        assertEquals(listOf(20261116, 20261123), derive(months = months, mode = M3ECutoffMode.LID).map { it.key })
    }

    @Test
    fun derive_lidCutoffForLabUsesLabDate() {
        val months = novemberCalendar(mapOf(18 to "LID for theory classes", 25 to "LID for laboratory classes"))
        // Lab course => lab LID (Nov 25), so Nov 16 and Nov 23 survive.
        assertEquals(
            listOf(20261116, 20261123),
            derive(
                months = months,
                domain = courseDomain(code = "CSE1001(L)", type = "Lab", slot = "L1"),
                code = "CSE1001(L)",
                mode = M3ECutoffMode.LID
            ).map { it.key }
        )
    }

    @Test
    fun derive_cat1CutoffUsesDayBeforeCat() {
        val months = novemberCalendar(mapOf(18 to "CAT I"))
        // CAT I on Nov 18 => cutoff Nov 17, so only Nov 16 remains.
        assertEquals(listOf(20261116), derive(months = months, mode = M3ECutoffMode.CAT1).map { it.key })
    }

    @Test
    fun derive_cat2CutoffUsesDayBeforeCat() {
        val months = novemberCalendar(mapOf(24 to "CAT II"))
        // CAT II on Nov 24 => cutoff Nov 23, so Nov 16 and Nov 23 remain.
        assertEquals(
            listOf(20261116, 20261123),
            derive(months = months, mode = M3ECutoffMode.CAT2).map { it.key }
        )
    }

    @Test
    fun derive_returnsEmptyWhenCalendarHasNoWorkingDays() {
        val month = CalendarMonth(
            month = "November 2026",
            days = listOf(CalendarDay(date = 16, events = listOf(CalendarEvent(type = "Holiday", text = "Holiday"))))
        )
        assertTrue(derive(months = listOf(month)).isEmpty())
    }

    @Test
    fun derive_returnsEmptyWhenCourseHasNoTimetableDays() {
        // "ZZZ" is not a real slot, so the course matches no weekday grid.
        assertTrue(derive(domain = courseDomain(slot = "ZZZ")).isEmpty())
        assertTrue(derive(code = "SOME_OTHER_CODE").isEmpty())
    }

    @Test
    fun derive_returnsEmptyWhenCalendarIsAbsent() {
        assertTrue(derive(months = emptyList()).isEmpty())
    }
}
