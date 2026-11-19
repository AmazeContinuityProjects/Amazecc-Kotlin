package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.ui.screens.academics.SimpleDate
import com.amazecc.app.shared.ui.screens.academics.buildWorkingDays
import com.amazecc.app.shared.ui.screens.academics.computeImportantDates
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Pure derivation of the "future class sessions" shown in the predictor tab of
 * [M3ECourseAttendanceScreen]. Ported from the legacy `EmbeddedCourseAttendanceView`
 * (`ui/screens/academics/CourseAttendanceScreen.kt`) so the M3E conversion keeps the
 * same data instead of shipping an empty list.
 *
 * Every input is passed in (including [today]) so the rule is deterministic and
 * unit-testable; no clocks, no state, no AppState reads live in here.
 */
object M3EGapFutureDates {
    private val MONTH_LABELS = listOf(
        "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /**
     * Working days that this course actually meets on, from "today" up to the
     * selected projection cutoff, tagged with the host's skipped-date set.
     *
     * Returns an empty list whenever the calendar, the working days, or the
     * course's weekday set is unavailable — we never invent session dates.
     */
    fun deriveFutureClassDates(
        months: List<CalendarMonth>,
        domain: DomainSnapshot,
        courseCode: String,
        courseType: String,
        cutoffMode: M3ECutoffMode,
        today: LocalDate,
        skippedKeys: Set<Int> = emptySet()
    ): List<M3EFutureClassDate> {
        if (months.isEmpty() || courseCode.isBlank()) return emptyList()

        val workingDays = buildWorkingDays(months)
        if (workingDays.isEmpty()) return emptyList()

        val courseDays = courseWeekdayAbbreviations(domain, courseCode)
        if (courseDays.isEmpty()) return emptyList()

        val cutoff = cutoffValue(
            importantDates = computeImportantDates(months),
            courseCode = courseCode,
            courseType = courseType,
            mode = cutoffMode
        )
        val todayValue = dateValue(today.year, today.monthNumber, today.dayOfMonth)

        val result = mutableListOf<M3EFutureClassDate>()
        for ((y, m, d) in workingDays) {
            val key = dateValue(y, m, d)
            if (key < todayValue) continue
            if (cutoff != null && key > cutoff) continue
            val date = runCatching { LocalDate(y, m, d) }.getOrNull() ?: continue
            if (weekdayAbbreviation(date) !in courseDays) continue
            result += M3EFutureClassDate(
                key = key,
                label = "${MONTH_LABELS.getOrElse(m) { "" }} $d".trim(),
                isSkipped = key in skippedKeys
            )
        }
        return result
    }

    /**
     * Mirrors the legacy "Find course days" step: the set of weekday abbreviations
     * (`MON`, `TUE`, …) on which the target course appears in the timetable grid.
     */
    private fun courseWeekdayAbbreviations(domain: DomainSnapshot, courseCode: String): Set<String> {
        val slotMap = SlotMap.map.mapValues { (_, inner) -> inner.mapValues { (_, time) -> SlotInfo(time) } }
        val attendance: List<Map<String, Any>> = Projections.currentSemesterAttendance(domain).map { item ->
            val shortType = when (item.courseType.lowercase()) {
                "embedded theory" -> "ETH"
                "embedded lab" -> "ELA"
                "theory only" -> "TO"
                "lab only" -> "LO"
                "soft skill" -> "SS"
                else -> item.courseType
            }
            mapOf(
                "courseCode" to item.courseCode,
                "courseTitle" to item.courseTitle,
                "courseType" to shortType,
                "faculty" to item.faculty,
                "slotName" to item.slotName,
                "attendancePercentage" to item.attendancePercentage,
                "venue" to (item.slotVenue ?: "")
            )
        }
        return AttendanceTimetable.buildAttendanceDayCardsMap(attendance, slotMap)
            .entries
            .filter { (_, cards) -> cards.any { it.courseCode == courseCode } }
            .map { it.key.name }
            .toSet()
    }

    /** The last day a future class may fall on for the selected cutoff, or null for "End of Sem". */
    private fun cutoffValue(
        importantDates: Map<String, SimpleDate>,
        courseCode: String,
        courseType: String,
        mode: M3ECutoffMode
    ): Int? {
        val selection: SimpleDate? = when (mode) {
            M3ECutoffMode.CAT1 -> importantDates["cat i"]
            M3ECutoffMode.CAT2 -> importantDates["cat ii"]
            M3ECutoffMode.LID -> {
                val lab = importantDates["lid for laboratory classes"]
                val theory = importantDates["lid for theory classes"]
                val isLab = courseCode.endsWith("(L)") || courseType == "Lab"
                if (isLab) lab ?: theory else theory ?: lab
            }
            M3ECutoffMode.END -> null
        }
        return selection?.let { dateValue(it.year, it.month, it.day) }
    }

    private fun dateValue(y: Int, m: Int, d: Int): Int = y * 10000 + m * 100 + d

    private fun weekdayAbbreviation(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> "SUN"
        DayOfWeek.MONDAY -> "MON"
        DayOfWeek.TUESDAY -> "TUE"
        DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"
        DayOfWeek.FRIDAY -> "FRI"
        DayOfWeek.SATURDAY -> "SAT"
        else -> ""
    }
}
