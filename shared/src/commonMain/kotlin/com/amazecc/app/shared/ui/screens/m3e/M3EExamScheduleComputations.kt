package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.ExamSeries as DomainExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamStatus
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamSeriesGroup
import com.amazecc.app.shared.domain.overhaul.M3EExamStatusCounts
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.utils.ExamUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pure, headless-testable exam-schedule logic — the Kotlin counterpart of the web app's
 * `lib/examSchedule.ts` (`parseExamDate`, `examWindow`, `classifyExamState`, `buildExamRows`,
 * `calculateSeatLocation`, `nextExamLabel`).
 *
 * Nothing here reads AppState, Compose, a clock by itself or a platform API: [now] and [timeZone]
 * are always supplied by the host, so every branch can be driven from a JVM test. Date/time
 * parsing is delegated to the app's shared [ExamUtils] so this file cannot disagree with the
 * timetable, grade and notification screens about what "19-Nov-2025" means.
 */
object M3EExamScheduleComputations {

    /** "19-Nov-2025" / "19/11/2025" -> LocalDate, or null when VTOP published nothing usable. */
    fun parseDate(raw: String?): LocalDate? =
        if (raw.isNullOrBlank()) null else ExamUtils.parseExamDateToLocalDate(raw)

    /** "09:15 AM" -> minutes since midnight. */
    fun clockMinutes(raw: String?): Int? =
        if (raw.isNullOrBlank()) null else ExamUtils.examTimeToMinutes(raw)

    /** "09:15 AM - 12:30 PM" -> (start, end), either side nullable. */
    fun timeRange(raw: String?): Pair<Int?, Int?> =
        ExamUtils.slotMinutes(raw.orEmpty())

    /**
     * Map one raw VTOP series key (the `schedule` map key, e.g. "CAT II", "FAT", "End Semester")
     * onto the M3E filter enum via the shared canonicaliser. Unknown spellings deliberately fall
     * back to [ExamSeries.ALL] rather than guessing FAT: a paper whose series we cannot read must
     * not silently appear under a specific filter it may not belong to.
     */
    fun seriesFor(rawType: String?): ExamSeries {
        val canonical = DomainExamSeries.canonicalSeriesName(rawType)
        return when {
            canonical.contains("cat2") -> ExamSeries.CAT2
            canonical.contains("cat1") -> ExamSeries.CAT1
            // Real VTOP keys include "MID SEMESTER EXAMINATION" / "MIDTERM EXAMINATION",
            // which used to fold into ALL and then vanish behind the CAT/FAT filters.
            canonical.contains("midsem") || canonical.contains("midterm") || canonical == "mt" ||
                rawType?.trim()?.equals("MT", ignoreCase = true) == true -> ExamSeries.CAT2
            canonical.contains("fat") || canonical.contains("final") || canonical.contains("end") ||
                canonical.contains("semestrexam") -> ExamSeries.FAT
            canonical.contains("cat") -> ExamSeries.CAT1
            else -> ExamSeries.ALL
        }
    }

    /** CAT runs 105 min, FAT 210 min; used only when VTOP gives no end time. */
    fun durationMinutes(rawType: String?): Int {
        val upper = rawType.orEmpty().trim().uppercase()
        return when {
            "CAT" in upper -> 105
            "FAT" in upper -> 210
            else -> 0
        }
    }

    /**
     * Resolve a paper's real start/end on its own local day. Exam time wins; reporting time is the
     * fallback start; the series duration fills a missing end. Returns (null, null) when the
     * payload has no usable time at all so callers can fall back to day-level reasoning.
     */
    fun examWindow(item: ExamItem, rawType: String, date: LocalDate?): Pair<LocalDateTime?, LocalDateTime?> {
        if (date == null) return null to null
        val (rangeStart, rangeEnd) = ExamUtils.slotMinutes(item.examTime)
        val startMin = rangeStart ?: clockMinutes(item.reportingTime) ?: return null to null
        val endMin = rangeEnd ?: durationMinutes(rawType).takeIf { it > 0 }?.let { startMin + it }
        return at(date, startMin) to endMin?.let { at(date, it) }
    }

    private fun at(date: LocalDate, minutes: Int): LocalDateTime? = try {
        if (minutes < 0 || minutes >= 24 * 60) null
        else LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, minutes / 60, minutes % 60, 0, 0)
    } catch (_: Exception) {
        null
    }

    /**
     * Past / today / upcoming for a paper, judged against [now]. A finished window wins over the
     * calendar-day check, so a paper that ended earlier today is "completed", not "today".
     */
    fun classify(date: LocalDate?, endAt: LocalDateTime?, now: LocalDateTime): ExamStatus {
        if (date == null) return ExamStatus.UPCOMING
        if (endAt != null && now >= endAt) return ExamStatus.COMPLETED
        val today = now.date
        return when {
            date < today -> ExamStatus.COMPLETED
            date == today -> ExamStatus.TODAY
            else -> ExamStatus.UPCOMING
        }
    }

    /** Whole days from today to the exam, floored at zero for past/today papers. */
    fun daysUntil(date: LocalDate?, today: LocalDate): Int =
        date?.let { maxOf(0, it.toEpochDays() - today.toEpochDays()) } ?: 0

    /** "Today" / "Tomorrow" / "In 3 days" / "Completed"; "Now" while a today window is open. */
    fun countdownLabel(
        status: ExamStatus,
        date: LocalDate?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        now: LocalDateTime
    ): String = when (status) {
        ExamStatus.COMPLETED -> "Completed"
        ExamStatus.TODAY ->
            if (startAt != null && endAt != null && now >= startAt && now < endAt) "Now" else "Today"
        ExamStatus.UPCOMING -> {
            val days = date?.let { it.toEpochDays() - now.date.toEpochDays() }
            when {
                days == null -> "Date TBA"
                days <= 1 -> "Tomorrow"
                else -> "In $days days"
            }
        }
    }

    /** "Monday" from a parsed date; empty when VTOP gave no date. */
    fun dayName(date: LocalDate?): String =
        date?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() }.orEmpty()

    /**
     * VTOP returns "-" for language/soft-skill papers and often omits `seatLocation` for others.
     * In that case the hall position is derived from the numeric `seatNo` (18 seats per group,
     * two columns) — exactly the web app's `calculateSeatLocation` / `seatLabel` pair.
     */
    fun deriveSeatLocation(seatNo: String?, seatLocation: String?, courseTitle: String?): String {
        val raw = seatLocation?.trim().orEmpty()
        if (raw.isNotBlank() && raw != "-") return raw
        val computed = ExamUtils.calculateSeatLocation(seatNo.orEmpty(), courseTitle.orEmpty())
        return if (computed == "-") "TBD" else computed
    }

    /** "-" and blanks are VTOP's "to be announced"; never print them as a real venue. */
    fun venueDisplay(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return if (value.isBlank() || value == "-") "TBA" else value
    }

    private val entryComparator = compareBy<M3EExamEntry>(
        { parseDate(it.date) ?: LocalDate(9999, 12, 31) },
        { clockMinutes(it.examTime) ?: clockMinutes(it.reportingTime) ?: 0 },
        { it.courseCode }
    )

    /**
     * Flatten `{ FAT: [...], CAT1: [...] }` into enriched, soonest-first rows. The series comes
     * from the schedule map key (which the domain snapshot discards), and every display field comes
     * straight from [ExamItem] — no hardcoded FAT, empty day/slot, or flattened reporting/exam time.
     */
    fun buildEntries(
        res: ExamScheduleRes,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<M3EExamEntry> {
        val nowLocal = now.toLocalDateTime(timeZone)
        val today = nowLocal.date
        return res.schedule.entries
            .flatMap { (seriesKey, items) ->
                items.mapIndexed { idx, item ->
                    val date = parseDate(item.examDate)
                    val (startAt, endAt) = examWindow(item, seriesKey, date)
                    val status = classify(date, endAt, nowLocal)
                    val days = daysUntil(date, today)
                    M3EExamEntry(
                        id = "$seriesKey-${item.courseCode}-${item.examDate}-$idx",
                        courseCode = item.courseCode,
                        courseTitle = item.courseTitle,
                        examSeries = seriesFor(seriesKey),
                        date = item.examDate,
                        day = dayName(date),
                        session = item.examSession,
                        slot = item.slot,
                        reportingTime = item.reportingTime,
                        examTime = item.examTime,
                        venue = item.venue,
                        seatNumber = item.seatNo,
                        daysRemaining = days,
                        isHappeningSoon = status != ExamStatus.COMPLETED && days <= 3,
                        status = status,
                        countdownLabel = countdownLabel(status, date, startAt, endAt, nowLocal),
                        seatLocation = deriveSeatLocation(item.seatNo, item.seatLocation, item.courseTitle),
                        venueDisplay = venueDisplay(item.venue)
                    )
                }
            }
            .sortedWith(entryComparator)
    }

    /** ALL is a no-op; every other option keeps only its own series. */
    fun filterBySeries(entries: List<M3EExamEntry>, series: ExamSeries): List<M3EExamEntry> =
        if (series == ExamSeries.ALL) entries else entries.filter { it.examSeries == series }

    /**
     * Filter options are derived from the data-driven schedule keys, not hardcoded to CAT/FAT.
     * ALL is always present; the known series appear in canonical order, unknown keys fold into ALL.
     */
    fun availableSeries(rawKeys: Collection<String>): List<ExamSeries> {
        val present = rawKeys.map(::seriesFor).toSet()
        val known = listOf(ExamSeries.CAT1, ExamSeries.CAT2, ExamSeries.FAT).filter { it in present }
        return listOf(ExamSeries.ALL) + known
    }

    /** Grouped timeline: one section per series, rows sorted soonest-first within each section. */
    fun groupBySeries(entries: List<M3EExamEntry>): List<M3EExamSeriesGroup> =
        entries.groupBy { it.examSeries }
            .map { (series, rows) ->
                val sorted = rows.sortedWith(entryComparator)
                M3EExamSeriesGroup(series = series, label = series.label, entries = sorted)
            }
            .sortedBy { group ->
                group.entries.firstOrNull()?.let { parseDate(it.date)?.toEpochDays() } ?: Int.MAX_VALUE
            }

    fun statusCounts(entries: List<M3EExamEntry>) = M3EExamStatusCounts(
        upcoming = entries.count { it.status == ExamStatus.UPCOMING },
        today = entries.count { it.status == ExamStatus.TODAY },
        completed = entries.count { it.status == ExamStatus.COMPLETED }
    )

    /** First not-yet-completed paper in chronological order. */
    fun nextUpcoming(entries: List<M3EExamEntry>): M3EExamEntry? =
        entries.filter { it.status != ExamStatus.COMPLETED }.minWithOrNull(entryComparator)
}
