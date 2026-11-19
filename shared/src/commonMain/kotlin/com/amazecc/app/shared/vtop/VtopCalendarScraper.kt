package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.CalendarRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Headless-VTOP port of the academic-calendar parser (amazecc-api-hono `calendar.ts:17-167`,
 * AmazeCC-API `/api/calendar/route.ts`).
 *
 * The WebView posts one `processViewCalendar` request per month and returns raw `{date, events}`
 * cells; month names, day numbers, event text and inline colours are read from the DOM. Event
 * type/category derivation and the calendar-grid helpers live here in Kotlin, so both the parser
 * and the M3E grid maths are pure and testable without a browser.
 *
 * This is a standalone implementation; it does not touch `VtopDataSource` or `VtopScripts`.
 */
object VtopCalendarScraper {

    private const val PATH = "/vtop/processViewCalendar"

    /** VTOP wants `DD-MMM-YYYY`, e.g. `01-JUL-2026`. */
    private val MONTH_ABBR = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )

    private val MONTH_NUMBERS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Fetches and parses the whole calendar for [semesterId].
     *
     * [type] is the `classGroupId`; it is coerced per semester the same way the reference does:
     * a `05` semester only accepts ALL/ALL02/ALL05, and a `07` semester is always ALL.
     * Returns null when there is no live session, the engine is unsupported, or every month failed.
     */
    suspend fun fetchCalendar(semesterId: String, type: String = "ALL"): CalendarRes? =
        withContext(Dispatchers.Default) {
            if (!vtopEngineSupported) return@withContext null
            val base = VtopSession.verifyMenuParams(Clock.System.now().toEpochMilliseconds())
                ?: return@withContext null
            val sem = semesterId.takeIf { it.isNotBlank() }
                ?: VtopSession.semesterSubId.value?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val group = calendarClassGroup(sem, type)
            val dates = semesterMonths(sem).map { (m, y) -> "01-${MONTH_ABBR[m - 1]}-$y" }

            val script = buildScript(base, sem, group, dates)
            val raw = runCatching {
                Vtop.engineInstance().evaluate(script, timeoutMs = 60_000)
            }.getOrNull() ?: return@withContext null
            parsePayload(raw, sem)
        }

    /**
     * Pure decode + event derivation of the JS payload. When [semesterId] is supplied, each
     * [CalendarMonth] also carries its explicit [CalendarMonth.year] / [CalendarMonth.monthNumber]
     * so the M3E grid can start on the correct weekday and end on the real last day.
     */
    fun parsePayload(raw: String, semesterId: String = ""): CalendarRes? {
        val root = runCatching { json.parseToJsonElement(raw.ifBlank { "{}" }) }.getOrNull() as? JsonObject
            ?: return null
        if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true) return null
        val arr = root["calendars"] as? JsonArray ?: return null
        val months = runCatching {
            json.decodeFromJsonElement(ListSerializer(RawCalendarMonth.serializer()), arr)
        }.getOrNull() ?: return null

        return CalendarRes(
            success = true,
            months = months.map { rm ->
                val bounds = calendarMonthBounds(semesterId, rm.month)
                val byDate = rm.days.associateBy { it.date }
                // VTOP only lists days that carry an event. The screen must still render every
                // day box from the 1st through the real last date (28/29/30/31) of the month.
                val completeDays = if (bounds != null) {
                    (1..daysInMonth(bounds.first, bounds.second)).map { d ->
                        byDate[d] ?: RawCalendarDay(date = d)
                    }
                } else rm.days
                CalendarMonth(
                    month = rm.month,
                    year = bounds?.first,
                    monthNumber = bounds?.second,
                    days = completeDays.map { rd ->
                        CalendarDay(
                            date = rd.date,
                            events = rd.events.mapNotNull { re ->
                                val text = re.text.trim()
                                if (text.isBlank()) null else CalendarEvent(
                                    type = eventType(text),
                                    text = text,
                                    color = re.color,
                                    category = eventCategory(text)
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    /** "instructional" -> Instructional Day, "holiday" -> Holiday, else Other (case-insensitive). */
    fun eventType(text: String): String = when {
        text.contains("instructional", ignoreCase = true) -> "Instructional Day"
        text.contains("holiday", ignoreCase = true) -> "Holiday"
        else -> "Other"
    }

    /** Parenthesized suffix is the category, otherwise "General". */
    fun eventCategory(text: String): String =
        Regex("\\(([^)]+)\\)").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: "General"

    /** `processViewCalendar` classGroupId coercion, mirroring the reference route. */
    fun calendarClassGroup(semesterId: String, requested: String): String = when {
        semesterId.endsWith("05") && requested !in setOf("ALL", "ALL02", "ALL05") -> "ALL"
        semesterId.endsWith("07") -> "ALL"
        else -> requested
    }

    /**
     * (monthNumber, year) pairs the semester's calendar spans, derived from the semester code
     * alone. `CH20262701` -> Jul..Nov 2026; `...05` -> Dec 2026..Apr 2027; anything else -> May..Jul
     * of the following year.
     */
    fun semesterMonths(semesterId: String): List<Pair<Int, Int>> {
        val code = semesterId.takeLast(2)
        val startYear = semesterId.drop(2).take(4).toIntOrNull() ?: 2024
        val nextYear = startYear + 1
        return when (code) {
            "01" -> listOf(7 to startYear, 8 to startYear, 9 to startYear, 10 to startYear, 11 to startYear)
            "05" -> listOf(12 to startYear, 1 to nextYear, 2 to nextYear, 3 to nextYear, 4 to nextYear)
            else -> listOf(5 to nextYear, 6 to nextYear, 7 to nextYear)
        }
    }

    /**
     * Resolves the (year, monthNumber) of a month label emitted by VTOP's `<h4>`, using the
     * semester code to pick the right year (e.g. July 2026 for `01` vs July 2027 for `07`).
     * Accepts full/short names, numeric months and an optional year suffix. Returns null when the
     * label is not a month or cannot be placed in the semester.
     */
    fun calendarMonthBounds(semesterId: String, monthName: String): Pair<Int, Int>? {
        val monthNumber = monthNameToNumber(monthName) ?: return null
        val inSemester = semesterMonths(semesterId).firstOrNull { it.first == monthNumber }
        if (inSemester != null) return inSemester.second to inSemester.first
        val embeddedYear = Regex("\\b(20\\d{2})\\b").find(monthName)?.value?.toIntOrNull()
            ?: return null
        return embeddedYear to monthNumber
    }

    private fun monthNameToNumber(name: String): Int? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        trimmed.toIntOrNull()?.let { return it.takeIf { n -> n in 1..12 } }
        val token = trimmed.filter { it.isLetter() }.take(3).lowercase()
        return MONTH_NUMBERS[token]
    }

    /**
     * Leading blank cells before day 1 for a Monday-first week grid (Mon=0 ... Sun=6).
     * Uses kotlinx.datetime so weekday drift (leap years, century rules) is never guessed.
     */
    fun leadingBlankDays(year: Int, monthNumber: Int): Int = runCatching {
        when (LocalDate(year, monthNumber, 1).dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
    }.getOrDefault(0)

    /** Real number of days in the month (including leap years), for the grid's final box. */
    fun daysInMonth(year: Int, monthNumber: Int): Int {
        if (monthNumber !in 1..12) return 0
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return if (monthNumber == 2 && leap) 29 else DAYS_IN_MONTH[monthNumber - 1]
    }

    // ── JS builder ──────────────────────────────────────────────────────────

    private fun buildScript(body: String, semesterId: String, group: String, dates: List<String>): String {
        val datesJson = dates.joinToString(",", "[", "]") { jsString(it) }
        return wrapped(
            """
            var out = [];
            var dates = $datesJson;

            for (var mi = 0; mi < dates.length; mi++) {
              var postBody = ${jsString(body)}
                + '&semSubId=' + encodeURIComponent(${jsString(semesterId)})
                + '&calDate=' + encodeURIComponent(dates[mi])
                + '&classGroupId=' + encodeURIComponent(${jsString(group)})
                + '&x=' + Date.now();

              var xhr = new XMLHttpRequest();
              xhr.open('POST', ${jsString(PATH)}, false);
              xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
              xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
              xhr.send(postBody);
              if (xhr.status === 0 || xhr.status >= 400) continue;

              var res = xhr.responseText || '';
              var lower = res.toLowerCase();
              if (lower.indexOf('not authorized') !== -1 ||
                  lower.indexOf('session timed out') !== -1 ||
                  lower.indexOf('invalid session') !== -1 ||
                  lower.indexOf('session expired') !== -1) continue;

              var doc = new DOMParser().parseFromString(res, 'text/html');
              var h4 = doc.querySelector('h4');
              var month = h4 ? (h4.textContent || '').replace(/\s+/g, ' ').trim() : '';

              var days = [];
              var cells = doc.querySelectorAll('table.calendar-table tbody tr td');
              for (var ci = 0; ci < cells.length; ci++) {
                var spans = cells[ci].querySelectorAll('span');
                if (spans.length === 0) continue;
                var dayText = (spans[0].textContent || '').replace(/\s+/g, ' ').trim();
                if (!dayText) continue;
                var date = parseInt(dayText, 10);
                if (isNaN(date)) continue;

                var events = [];
                for (var si = 1; si < spans.length; si++) {
                  var el = spans[si];
                  var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
                  if (!text) continue;
                  var color = null;
                  var style = el.getAttribute('style') || '';
                  var cm = style.match(/color:\s*([^;]+)/);
                  if (cm) color = cm[1].trim();
                  events.push({ text: text, color: color });
                }
                // Days with no events are dropped, matching the reference parser.
                if (events.length > 0) days.push({ date: date, events: events });
              }
              out.push({ month: month, days: days });
            }

            if (out.length === 0) return JSON.stringify({ ok: false, error: 'no calendar data' });
            return JSON.stringify({ ok: true, calendars: out });
            """
        )
    }

    /** Escapes [value] as a JavaScript string literal (JSON-style escaping). */
    private fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 2).append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun wrapped(body: String): String = """
        (function() {
          try {
            $body
          } catch (e) {
            return JSON.stringify({ ok: false, __error: String(e && e.message ? e.message : e) });
          }
        })();
    """.trimIndent()

    @Serializable
    private data class RawCalendarEvent(val text: String = "", val color: String? = null)

    @Serializable
    private data class RawCalendarDay(val date: Int = 0, val events: List<RawCalendarEvent> = emptyList())

    @Serializable
    private data class RawCalendarMonth(val month: String = "", val days: List<RawCalendarDay> = emptyList())
}
