package com.amazecc.app.shared.ui.components

import com.amazecc.app.shared.domain.Exam
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Open decision 8: Kotlin's `PAST` / `TODAY` / `IN 3d` / `""` wording stays, and Node's
 * time-of-day rule from `examSchedule.ts` is added behind it.
 *
 * The guard can only move a row *towards* `PAST`. The rest of this file pins that the wording it
 * does not touch still behaves exactly as it did.
 */
class ExamStatusTextTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun instantAt(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime(y, mo, d, h, mi).toInstant(tz)

    private val noon = instantAt(2025, 11, 19, 12, 0)

    @Test
    fun `a past paper with no parseable time follows the calendar instead of reporting nothing`() {
        // No time at all means there is no start instant to count down from, so this used to
        // render "" - an exam the user sat months ago showed no chip whatsoever.
        val pastNoTime = Exam(courseCode = "BPAST", date = "10-Jan-2025")
        assertEquals("PAST", examStatusText(pastNoTime, noon))
    }

    @Test
    fun `a future paper with no parseable time still reports nothing`() {
        // The guard must not promote everything: with no time there is no countdown and the date
        // is ahead, so there is genuinely nothing useful to say yet.
        val futureNoTime = Exam(courseCode = "BFUT", date = "10-Jan-2027")
        assertEquals("", examStatusText(futureNoTime, noon))
    }

    @Test
    fun `a paper that has already started reads PAST, because the hours count down from the start`() {
        // Kotlin's contract, unchanged by decision 8 - worth pinning so the interaction between * the two rules is explicit rather than folklore.
        val started = Exam(courseCode = "BSTART", date = "19-Nov-2025", time = "09:15 AM - 12:30 PM")
        assertEquals("PAST", examStatusText(started, instantAt(2025, 11, 19, 11, 0)))
    }

    @Test
    fun `a paper inside the next day still reads TODAY`() {
        val tomorrow = Exam(courseCode = "BTOM", date = "20-Nov-2025", time = "09:15 AM - 12:30 PM")
        assertEquals("TODAY", examStatusText(tomorrow, noon))
    }

    @Test
    fun `the day and horizon wording is untouched`() {
        val exact72h = Exam(courseCode = "B72", date = "22-Nov-2025", time = "09:15 AM - 12:30 PM")
        assertEquals("IN 3d", examStatusText(exact72h, instantAt(2025, 11, 19, 9, 15)))

        val beyondHorizon = Exam(courseCode = "BFAR", date = "19-Jan-2026", time = "09:15 AM - 12:30 PM")
        assertEquals("", examStatusText(beyondHorizon, noon))
    }
}
