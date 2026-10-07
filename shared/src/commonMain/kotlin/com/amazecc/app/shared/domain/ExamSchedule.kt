package com.amazecc.app.shared.domain

import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.utils.examDateParsed
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Exam schedule parsing, ordering and state classification.
 *
 * A port of `AmazeCC/src/lib/examSchedule.ts`, kept free of Compose so the schedule screen and its
 * tests share it. The VTOP payload is `{ semester, Schedule: { FAT: [...], CAT1: [...] } }`, where
 * the series keys are data-driven - nothing here assumes CAT or FAT.
 *
 * What is deliberately *not* taken from Node is display wording: `prettyDate`, `relativeDay` and
 * `formatClock` already have Kotlin equivalents the screens use, and open decision 8 keeps
 * `examStatusText` as what a row says. The port supplies the facts those words describe - chiefly
 * [ExamState], so a paper that finished at 12:30 PM is `PAST` at 3 PM instead of waiting for
 * midnight.
 *
 * The clock parser itself is [ExamUtils.examTimeToMinutes], not Node's `clockMinutes`: two parsers
 * for one fact would eventually disagree, and the Kotlin one is the tested one (it also accepts the
 * bare 24-hour clock VTOP writes into `reportingTime`).
 */
object ExamSchedule {

    /** `past` / `today` / `upcoming`, judged against the clock. */
    enum class ExamState { PAST, TODAY, UPCOMING }

    /**
     * One paper, resolved against its own local day.
     *
     * @property date local midnight of the exam date - what is displayed and grouped by
     * @property startAt date + reported start time, when VTOP gives one
     * @property endAt date + reported end time, when it can be determined
     * @property state [classifyExamState] applied to this row
     */
    data class ExamRow(
        val key: String,
        val examType: String,
        val exam: Exam,
        val date: LocalDate?,
        val startAt: Instant?,
        val endAt: Instant?,
        val state: ExamState,
    )

    data class Window(val startAt: Instant?, val endAt: Instant?)

    /** How long a paper runs when VTOP gives no end time: CAT 1h45, FAT 3h30. */
    private val SERIES_DURATION_MIN = mapOf("CAT" to 105, "FAT" to 210)

    private fun seriesDurationMin(examType: String): Int {
        val upper = examType.uppercase()
        return when {
            upper.contains("CAT") -> SERIES_DURATION_MIN.getValue("CAT")
            upper.contains("FAT") -> SERIES_DURATION_MIN.getValue("FAT")
            else -> 0
        }
    }

    /**
     * Resolve a paper's real start/end instants on its own local day.
     *
     * [Exam.time] is `"09:15 AM - 12:30 PM"`; when only [Exam.reportingTime] is present the end is
     * derived from the series duration. Nulls when the payload has no usable time at all, so
     * callers can fall back to day-level reasoning.
     */
    fun examWindow(exam: Exam, examType: String, date: LocalDate?): Window {
        if (date == null) return Window(null, null)

        val (rangeStart, rangeEnd) = ExamUtils.slotMinutes(exam.time)
        val startMin = rangeStart ?: ExamUtils.examTimeToMinutes(exam.reportingTime)
            ?: return Window(null, null)

        val duration = seriesDurationMin(examType)
        val endMin = rangeEnd ?: if (duration > 0) startMin + duration else null

        return Window(
            startAt = atLocal(date, startMin),
            endAt = endMin?.let { atLocal(date, it) },
        )
    }

    private fun atLocal(date: LocalDate, minutes: Int): Instant? = try {
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, minutes / 60, minutes % 60)
            .toInstant(TimeZone.currentSystemDefault())
    } catch (_: Exception) {
        null
    }

    /**
     * Past / today / upcoming for a paper, judged against [now].
     *
     * Time of day matters: a CAT that finished at 12:30 PM is done by 3 PM, not at midnight. When
     * no end time is known we can only judge by calendar day, which is the old behaviour.
     *
     * Node compares the epoch milliseconds of the two local midnights; comparing [LocalDate]s
     * directly is the same predicate without the arithmetic.
     */
    fun classifyExamState(date: LocalDate?, endAt: Instant?, now: Instant): ExamState {
        if (date == null) return ExamState.UPCOMING
        if (endAt != null && now >= endAt) return ExamState.PAST

        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return when {
            date < today -> ExamState.PAST
            date == today -> ExamState.TODAY
            else -> ExamState.UPCOMING
        }
    }

    /** [classifyExamState] for one paper, using only what the payload itself says. */
    fun stateOf(exam: Exam, now: Instant = Clock.System.now()): ExamState {
        val date = exam.examDateParsed
        return classifyExamState(date, examWindow(exam, "", date).endAt, now)
    }

    /**
     * Flatten `{ FAT: [...], CAT1: [...] }` into one ordered list of rows, each classified against
     * [now] and keyed for stable expansion state.
     */
    fun buildExamRows(schedule: Map<String, List<Exam>>, now: Instant = Clock.System.now()): List<ExamRow> =
        schedule.entries
            .flatMap { (examType, subjects) ->
                subjects.mapIndexed { idx, exam ->
                    val date = exam.examDateParsed
                    val window = examWindow(exam, examType, date)
                    ExamRow(
                        key = "$examType-${exam.courseCode}-${exam.date}-$idx",
                        examType = examType,
                        exam = exam,
                        date = date,
                        startAt = window.startAt,
                        endAt = window.endAt,
                        state = classifyExamState(date, window.endAt, now),
                    )
                }
            }
            .sortedWith(::compareRows)

    /**
     * Soonest-first by real start time, then course code.
     *
     * Times must be compared as instants - sorting the `"09:15 AM"` / `"02:00 PM"` strings as text
     * would put every afternoon exam before every morning one. An undated paper sorts last, which
     * is Node's `Number.POSITIVE_INFINITY`.
     */
    private fun compareRows(a: ExamRow, b: ExamRow): Int {
        val at = startOrDay(a) ?: Instant.DISTANT_FUTURE
        val bt = startOrDay(b) ?: Instant.DISTANT_FUTURE
        if (at != bt) return at.compareTo(bt)
        return a.exam.courseCode.compareTo(b.exam.courseCode)
    }

    private fun startOrDay(row: ExamRow): Instant? =
        row.startAt ?: row.date?.atStartOfDayIn(TimeZone.currentSystemDefault())
}
