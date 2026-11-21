package com.amazecc.app.shared.domain.overhaul

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.splitSlots
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.ui.screens.home.buildHomeWeekDays
import com.amazecc.app.shared.utils.AttendanceTimetable
import kotlinx.datetime.LocalDate

/**
 * D-128: the M3E home strip's week model.
 *
 * The seven days themselves are not reimplemented here. [buildHomeWeekDays] is the
 * canonical port of the web home's super-calendar logic (Monday-first week, CAT/FAT
 * exams, academic-calendar holidays and published day-order overrides, Moodle
 * deadlines), and that is the single source of truth for *which* day is what. This
 * object only adapts that result into [M3EWeekDay] and adds the two pieces the M3E
 * host owns: whether the day actually has classes, and whether the strip should show
 * a weekend at all.
 */
object M3EHomeWeekDays {

    /**
     * Build the M3E week for [weekOffset] weeks from [today].
     *
     * `hasClasses` follows the same rule the M3E host has always used: resolve the
     * attendance column for the date (honouring a published day-order override),
     * then check whether any enrolled course has a slot in that column.
     * `isHoliday` means an *academic-calendar* holiday, never merely "weekend with
     * no classes" — that distinction is what lets [visibleInStrip] hide only the
     * weekends the student has no reason to open.
     */
    fun build(
        weekOffset: Int,
        today: LocalDate,
        exams: List<ExamItem>,
        calendarMonths: List<CalendarMonth>,
        deadlineDates: Set<LocalDate>,
        courses: List<Course>,
        calendarRes: CalendarRes?,
        selectedDate: LocalDate
    ): List<M3EWeekDay> {
        return buildHomeWeekDays(
            weekOffset = weekOffset,
            today = today,
            exams = exams,
            calendarMonths = calendarMonths,
            deadlineDates = deadlineDates,
        ).map { homeDay ->
            val date = homeDay.date
            val dayKey = (homeDay.detectedDayOrder
                ?: AttendanceTimetable.getAttendanceDayForDate(date, calendarRes)).name
            val hasClasses = courses.any { course ->
                course.slots.flatMap { splitSlots(it) }
                    .any { slotToken -> SlotMap.getTime(dayKey, slotToken) != null }
            }

            M3EWeekDay(
                date = date,
                dayName = date.dayOfWeek.name.lowercase()
                    .replaceFirstChar { it.uppercase() }.take(3),
                dayNumber = date.dayOfMonth,
                isToday = homeDay.isToday,
                isSelected = date == selectedDate,
                hasClasses = hasClasses,
                isHoliday = homeDay.holidayInfo != null,
                examCount = homeDay.exams.size,
                holidayName = homeDay.holidayInfo,
                hasDeadline = homeDay.hasDeadline,
                detectedDayOrder = homeDay.detectedDayOrder
            )
        }
    }

    /**
     * The discs the strip actually draws: every day except a Saturday or Sunday
     * that has no classes. A classless weekend that is today or selected is always
     * kept — hiding the day the user is looking at would be worse than showing an
     * extra disc. Input is the canonical seven-day week; output is 5–7 days.
     */
    fun visibleInStrip(days: List<M3EWeekDay>): List<M3EWeekDay> =
        days.filter { day ->
            val isWeekend = day.date.dayOfWeek.value in 6..7
            !isWeekend || day.hasClasses || day.isToday || day.isSelected
        }
}
