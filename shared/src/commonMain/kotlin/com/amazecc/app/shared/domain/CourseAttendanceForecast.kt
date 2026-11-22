package com.amazecc.app.shared.domain

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth as PublishedMonth
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

internal data class CourseAttendanceForecast(
    val schedule: ScheduleInput,
    val meetings: List<Long>,
    val milestones: MilestoneDates,
) {
    private val isLab = AttendancePredictor.isLabCourse(schedule.course.courseCode, schedule.course.courseType)

    private fun ceiling(cutoff: String) =
        if (cutoff == "END") null else AttendancePredictor.courseCeiling(cutoff, isLab, milestones)

    fun project(skips: Set<Long>, cutoff: String): ScheduleResult {
        return AttendancePredictor.projectCourse(
            schedule.copy(
                skips = skips,
                ceiling = ceiling(cutoff),
                lockDate = AttendancePredictor.courseLockDate(cutoff, isLab, milestones),
            )
        )
    }

    fun meetingsThrough(cutoff: String): List<Long> {
        val ceiling = ceiling(cutoff)
        val lock = AttendancePredictor.courseLockDate(cutoff, isLab, milestones)
        return meetings.filter { date -> (ceiling == null || date <= ceiling) && (lock == null || date < lock) }
    }
}

/** Builds the forecast from one registration's slots and the calendar requested for its term. */
internal fun buildCourseAttendanceForecast(
    component: EmbeddedCourseHalf,
    months: List<PublishedMonth>,
    yearByMonth: Map<Int, Int>,
    now: LocalDateTime,
    target: Double,
): CourseAttendanceForecast? {
    val attendance = component.attendance ?: return null
    if (component.code.isBlank() || attendance.total <= 0) return null
    val courseType = if (component.half == "L") "Embedded Lab" else component.type
    val rawSlots = component.slots.joinToString("+").uppercase()
    val slots = SlotMap.map.mapValues { (_, times) -> times.mapValues { (_, range) -> SlotInfo(range) } }
    val byDay = AttendanceTimetable.buildAttendanceDayCardsMap(
        attendance = listOf(mapOf(
            "courseCode" to component.code,
            "courseTitle" to component.title,
            "courseType" to courseType,
            "slotName" to rawSlots,
        )),
        slotMap = slots,
    ).mapKeys { it.key.name }.filterValues { it.isNotEmpty() }
    if (byDay.isEmpty()) return null

    val datedEvents = months.flatMap { month ->
        val number = month.monthNumber ?: AttendanceTimetable.parseMonthNumber(month.month)
            ?: return@flatMap emptyList()
        val year = month.year ?: month.month.trim().substringAfterLast(' ').toIntOrNull()?.takeIf { it >= 2000 }
            ?: yearByMonth[number] ?: return@flatMap emptyList()
        month.days.mapNotNull { day ->
            val date = runCatching { LocalDate(year, number, day.date) }.getOrNull() ?: return@mapNotNull null
            date to day.events
        }
    }.sortedBy { it.first }.groupBy({ it.first }, { it.second }).mapValues { (_, lists) -> lists.flatten().distinct() }
    if (datedEvents.isEmpty()) return null

    val recordedDays = attendance.logs.mapNotNull { parseDayDate(it.date) }.toSet()
    val nowMinutes = now.hour * 60 + now.minute
    val workingDays = datedEvents.mapNotNull { (date, events) ->
        if (date < now.date || date in recordedDays || !events.isInstructional()) return@mapNotNull null
        val effectiveDay = events.firstNotNullOfOrNull { event ->
            if (event.type.contains("order", true) || event.category.contains("order", true) ||
                event.text.contains("order", true) || date.dayOfWeek.name == "SATURDAY"
            ) {
                AttendanceTimetable.parseDayOrderFromText(event.category)
                    ?: AttendanceTimetable.parseDayOrderFromText(event.text)
                    ?: AttendanceTimetable.parseDayOrderFromText(event.type)
            } else null
        }?.name ?: date.dayOfWeek.name.take(3)
        val slotsOnDate = byDay[effectiveDay].orEmpty()
        val remaining = slotsOnDate.filter { card ->
            date > now.date || AttendanceTimetable.getAttendanceTimeRange(card.time).start >= nowMinutes
        }
        if (remaining.isEmpty()) return@mapNotNull null
        DayLike(
            date = AttendancePredictor.makeTimestamp(date.year, date.monthNumber - 1, date.dayOfMonth),
            weekday = effectiveDay,
        )
    }
    val dayCards = byDay.mapValues { (_, cards) ->
        cards.map { mapOf("courseCode" to it.courseCode.orEmpty()) }
    }
    val effective = AttendancePredictor.buildEffectiveDayMap(workingDays)
    val milestoneMonths = datedEvents.entries.groupBy { it.key.year to it.key.monthNumber }.map { (month, days) ->
        val (year, number) = month
        val label = kotlinx.datetime.Month.entries[number - 1].name
        CalendarMonth(
            label = "$label $year",
            days = days.map { (date, events) ->
                CalendarDay(date.dayOfMonth, events.map { CalendarEventItem(type = it.type, text = it.text, category = it.category) })
            },
        )
    }
    return CourseAttendanceForecast(
        schedule = ScheduleInput(
            course = PredictorCourse(
                courseCode = component.code,
                courseTitle = component.title,
                courseType = courseType,
                slotName = rawSlots,
                attendedClasses = attendance.attended,
                totalClasses = attendance.total,
            ),
            dayCardsMap = dayCards,
            workingDays = workingDays,
            effectiveMap = effective,
            threshold = target,
        ),
        meetings = AttendancePredictor.courseMeetingDates(component.code, dayCards, workingDays, effective),
        milestones = buildMilestoneDates(milestoneMonths),
    )
}

private fun List<CalendarEvent>.isInstructional(): Boolean {
    val fields = flatMap { listOf(it.type, it.category, it.text) }.map(String::lowercase)
    val isClosed = fields.any {
        it.contains("holiday") || it.contains("non-working") || it.contains("non working") ||
            it.contains("pooja") || it.contains("vacation")
    }
    return !isClosed && fields.any {
        it.contains("instructional day") || it.contains("working") ||
            (it.contains("order") && AttendanceTimetable.getDayOrderLabelFromText(it) != null)
    }
}
