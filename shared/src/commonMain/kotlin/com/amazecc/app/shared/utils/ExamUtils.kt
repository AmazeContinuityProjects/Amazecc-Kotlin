package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.ExamItem
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours

object ExamUtils {

    private val MONTH_NAMES = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val EXEMPT_SEAT_COURSES = listOf(
        "Qualitative", "Quantitative", "French", "German", "Spanish", "Japanese"
    )

    // ── Date parsing ──

    private fun parseMonthToken(token: String): Int? {
        val t = token.trim().lowercase()
        if (t.isEmpty()) return null
        MONTH_NAMES[t.take(3)]?.let { return it }
        return t.toIntOrNull()?.takeIf { it in 1..12 }
    }

    /**
     * Parses "19-Nov-2025", "2025-11-19", "19/11/2025", "19 Nov 2025" (optionally
     * followed by a time) into a LocalDate. Returns null for anything unparseable.
     */
    fun parseExamDateToLocalDate(raw: String): LocalDate? {
        val tokens = raw.trim().split(Regex("[-/\\s]+"))
            .filter { it.isNotBlank() && !it.contains(":") && !it.equals("am", true) && !it.equals("pm", true) }
        if (tokens.size != 3) return null

        val yearToken = tokens.firstOrNull { it.length == 4 && it.all(Char::isDigit) }
        val year = yearToken?.toIntOrNull()
        if (year == null) return null

        // Prefer alphabetic month name (e.g. "Aug", "Nov") over numeric month
        val monthNameIdx = tokens.indexOfFirst { it.all(Char::isLetter) && parseMonthToken(it) != null }
        val (monthIdx, month) = if (monthNameIdx >= 0) {
            monthNameIdx to parseMonthToken(tokens[monthNameIdx])!!
        } else {
            // No month name: infer from position. YYYY-MM-DD has year at index 0.
            val yearIdx = tokens.indexOf(yearToken!!)
            val monthIdx = if (yearIdx == 0) 1 else 0 // YYYY-MM-DD -> month at 1; DD-MM-YYYY -> month at 0
            val m = parseMonthToken(tokens[monthIdx])
            if (m == null) return null
            monthIdx to m
        }

        val dayToken = tokens.firstOrNull { token ->
            tokens.indexOf(token) != monthIdx && token != yearToken && token.all(Char::isDigit) && token.length <= 2
        }
        val day = dayToken?.toIntOrNull()
        if (day == null) return null

        return try {
            LocalDate(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    // ── Time parsing ──

    /**
     * Parses the first time in "09:00 AM" or "09:15 AM - 12:30 PM" into minutes since
     * midnight. Returns null if no valid 12h/24h time is found.
     */
    fun examTimeToMinutes(raw: String): Int? {
        val first = raw.trim().split("-", "–", "—").first()
        val match = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE).find(first) ?: return null
        var h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        val meridian = match.groupValues[3].uppercase()
        if (h !in 1..12 || m !in 0..59) return null
        if (meridian == "PM" && h != 12) h += 12
        if (meridian == "AM" && h == 12) h = 0
        return h * 60 + m
    }

    /**
     * Parses "09:00 AM - 12:30 PM" or "09:00-12:30" into (startMinutes, endMinutes) since midnight.
     * Returns null if parsing fails.
     */
    fun parseExamTimeRange(raw: String): Pair<Int, Int>? {
        val parts = raw.trim().split("-", "–", "—").map { it.trim() }
        if (parts.size < 2) return null
        val start = examTimeToMinutes(parts[0]) ?: return null
        val end = examTimeToMinutes(parts[1]) ?: return null
        return start to end
    }

    // ── Exam item helpers ──

    /** Minutes since midnight of the reporting time, falling back to the exam start. */
    fun examStartMinutes(exam: ExamItem): Int? =
        examTimeToMinutes(exam.reportingTime) ?: examTimeToMinutes(exam.examTime)

    /** Absolute start instant of the exam (reporting time, else exam time). */
    fun examStartInstant(exam: ExamItem, tz: TimeZone = TimeZone.currentSystemDefault()): Instant? {
        val date = exam.examDateParsed ?: return null
        val minutes = examStartMinutes(exam) ?: return null
        return try {
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, minutes / 60, minutes % 60, 0, 0).toInstant(tz)
        } catch (_: Exception) {
            null
        }
    }

    /** Absolute end instant of the exam (from the examTime range end, else null). */
    fun examEndInstant(exam: ExamItem, tz: TimeZone = TimeZone.currentSystemDefault()): Instant? {
        val date = exam.examDateParsed ?: return null
        val range = parseExamTimeRange(exam.examTime) ?: return null
        return try {
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, range.second / 60, range.second % 60, 0, 0).toInstant(tz)
        } catch (_: Exception) {
            null
        }
    }

    fun hoursUntilExam(exam: ExamItem, now: Instant = Clock.System.now(), tz: TimeZone = TimeZone.currentSystemDefault()): Double? {
        val start = examStartInstant(exam, tz) ?: return null
        return (start - now).inWholeMilliseconds / 3_600_000.0
    }

    /** First future exam whose reporting time is within [withinHours] of [now]. */
    fun nextExamWithin(
        exams: Iterable<ExamItem>,
        now: Instant = Clock.System.now(),
        withinHours: Long = 24,
        tz: TimeZone = TimeZone.currentSystemDefault()
    ): ExamItem? {
        val limit = now.plus(withinHours.hours)
        return exams
            .mapNotNull { e -> examStartInstant(e, tz)?.let { e to it } }
            .filter { (_, start) -> start > now && start <= limit }
            .minByOrNull { it.second }
            ?.first
    }

    fun examsForDate(exams: Iterable<ExamItem>, date: LocalDate): List<ExamItem> =
        exams.filter { it.examDateParsed == date }

    fun isExamDate(exams: Iterable<ExamItem>, date: LocalDate): Boolean =
        exams.any { it.examDateParsed == date }

    /** Dates (distinct) that contain at least one exam — used for class-notif suppression. */
    fun examDates(exams: Iterable<ExamItem>): Set<LocalDate> =
        exams.mapNotNull { it.examDateParsed }.toSet()

    /** Sorts by exam date, then start time, then course code. */
    fun sortedExamDays(exams: Iterable<ExamItem>): List<ExamItem> {
        val fallbackDate = LocalDate(2100, 1, 1)
        return exams.sortedWith(
            compareBy<ExamItem>(
                { it.examDateParsed ?: fallbackDate },
                { examStartMinutes(it) ?: 0 },
                { it.courseCode }
            )
        )
    }

    // ── Seat location ──

    /**
     * Port of the web app's seat-location derivation (ScheduleDisplay.tsx):
     * 18 seats per 2-column group; odd/even seat numbers split across the pair.
     * Returns "-" for exempt courses (soft skills / languages) or invalid seat numbers.
     */
    fun calculateSeatLocation(seatNo: String, courseTitle: String): String {
        val n = seatNo.trim().toIntOrNull() ?: return "-"
        if (n <= 0) return "-"
        val title = courseTitle.trim()
        if (EXEMPT_SEAT_COURSES.any { title.startsWith(it) }) return "-"

        val groupIndex = (n - 1) / 18
        val c1 = groupIndex * 2 + 1
        val c2 = c1 + 1
        val pos = (n - 1) % 18
        val row = pos / 2 + 1
        val col = if (pos % 2 == 0) c1 else c2
        return "R${row}C${col}"
    }
}

// ── ExamItem display extensions ──

val ExamItem.examDateParsed: LocalDate?
    get() = ExamUtils.parseExamDateToLocalDate(examDate)

/** "R5C3" when the API provides it, computed from seat number otherwise, else "TBD". */
val ExamItem.seatLocationDisplay: String
    get() {
        val raw = seatLocation.trim()
        if (raw.isNotBlank() && raw != "-") return raw
        val computed = ExamUtils.calculateSeatLocation(seatNo, courseTitle)
        return if (computed == "-") "TBD" else computed
    }

/** "FN1" -> "Forenoon 1", "AN2" -> "Afternoon 2", unknown -> raw value. */
val ExamItem.sessionDisplay: String
    get() {
        val s = examSession.trim()
        if (s.isBlank()) return "TBD"
        val num = when {
            s.startsWith("FN", ignoreCase = true) -> s.substring(2).trim()
            s.startsWith("AN", ignoreCase = true) -> s.substring(2).trim()
            else -> return s
        }
        val base = if (s.startsWith("FN", ignoreCase = true)) "Forenoon" else "Afternoon"
        return if (num.isEmpty()) base else "$base $num"
    }
