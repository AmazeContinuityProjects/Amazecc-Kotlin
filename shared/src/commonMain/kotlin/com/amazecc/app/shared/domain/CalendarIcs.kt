package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * `.ics` export for the academic calendar (web parity — `attendance/calendarIcs.ts`).
 *
 * Every event is an all-day `VEVENT` (`VALUE=DATE`) — an academic calendar has
 * no times, matching the web export. Days whose only events are instructional
 * are excluded, mirroring the web `isExportable` predicate; one event per
 * exportable day. RFC 5545 escaping lives here: `,` `;` and newlines.
 */
object CalendarIcs {

    const val FILE_NAME = "AmazeCC_Academic_Calendar.ics"

    private const val CRLF = "\r\n"

    private val monthNames = Month.entries

    /** RFC 5545 requires `,` `;` and newlines escaped (the rest is trimmed, as the web does). */
    private fun escapeIcs(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(Regex("\\r?\\n"), "\\n")
            .trim()

    /** Parse a label like "September 26" into (monthNumber, year). */
    private fun parseLabel(label: String): Pair<Int, Int>? {
        val parts = label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val number = monthNames.firstOrNull { it.name.equals(parts[0], ignoreCase = true) } ?: return null
        val year = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return (number.ordinal + 1) to year
    }

    /** A day with something exportable on it: any event that is not an "Instructional Day". */
    fun exportableEvents(day: CalendarDay): List<CalendarEvent> =
        day.events.filter { it.type != "Instructional Day" && it.text.isNotBlank() }

    /** Stable-ish uid: the same day must not duplicate on re-import (web: `amazecc-day-<yyyy-mm-dd>-<i>@amazecc`). */
    private fun uidFor(dayKey: String, index: Int): String = "amazecc-day-$dayKey-$index@amazecc"

    private fun vevent(
        uid: String,
        startKey: String,
        summary: String,
        description: String?
    ): List<String> {
        val endKey = nextDay(startKey)
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:$uid")
            add("DTSTART;VALUE=DATE:${startKey.replace("-", "")}")
            // DTEND is exclusive in iCalendar: a one-day event ends the next day.
            add("DTEND;VALUE=DATE:${endKey?.replace("-", "") ?: startKey.replace("-", "")}")
            add("SUMMARY:${escapeIcs(summary)}")
            if (!description.isNullOrBlank()) add("DESCRIPTION:${escapeIcs(description)}")
            add("END:VEVENT")
        }
    }

    /** `2026-11-13` -> `2026-11-14`, the next calendar day (null when unparseable). */
    private fun nextDay(dayKey: String): String? {
        val local = parseKey(dayKey) ?: return null
        return runCatching {
            kotlinx.datetime.LocalDate.fromEpochDays(local.toEpochDays() + 1).toString()
        }.getOrNull()
    }

    private fun parseKey(dayKey: String): LocalDate? =
        dayKey.split("-").mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }?.let { LocalDate(it[0], it[1], it[2]) }

    /** Number of VEVENT blocks in a generated calendar — used to avoid reporting an empty file as a success. */
    fun countVEvents(ics: String): Int =
        ics.split(CRLF).count { it == "BEGIN:VEVENT" }

    /** One all-day VEVENT per exportable event of every month. */
    fun buildAcademicCalendarIcs(months: List<CalendarMonth>): String {
        val events = buildList {
            months.forEach { month ->
                // monthResolution is (monthNumber, year) — keep the destructure in that order.
                val (monthNumber, year) = monthResolution(month) ?: return@forEach
                month.days.forEach { day ->
                    val local = runCatching { LocalDate(year, monthNumber, day.date) }.getOrNull() ?: return@forEach
                    val dayKey = local.toString()
                    exportableEvents(day).forEachIndexed { index, event ->
                        val uid = uidFor(dayKey, index)
                        addAll(vevent(uid, dayKey, event.text, event.category.takeIf { it.isNotBlank() }))
                    }
                }
            }
        }
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//AmazeCC//Academic Calendar//EN")
            add("CALSCALE:GREGORIAN")
            add("METHOD:PUBLISH")
            addAll(events)
            add("END:VCALENDAR")
        }
        return lines.joinToString(CRLF)
    }

    private fun monthResolution(month: CalendarMonth): Pair<Int, Int>? {
        val number = month.monthNumber ?: parseLabel(month.month)?.first
        val year = month.year ?: parseLabel(month.month)?.second
        return if (number != null && year != null) number to year else null
    }
}
