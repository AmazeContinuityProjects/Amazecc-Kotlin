package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.EventHubRegisteredEvent
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.MoodleAssignment
import com.amazecc.app.shared.model.NamedCalendar

/**
 * The calendar's inputs, assembled once from the snapshot plus the stores that live outside it.
 *
 * This is the only place a screen decides *what* goes into [buildEnrichedCalendars]; the months it
 * returns are the projection every calendar surface binds to. `CalendarScreen` and
 * `SimplifiedHomeScreen` both come through here, which is what makes "port `buildEnrichedCalendars`
 * once; do not port it twice" true rather than aspirational.
 *
 * @param calendar the calendar the user picked from `Schedule.calendarsList`. The list is a
 *   transport payload held verbatim on the snapshot (see [DomainSnapshot]'s contract), so the
 *   screen picks a name and this function is what turns it into domain months.
 * @param semesterId scopes both the exam papers and the attendance that becomes classes and OD
 *   hours, the same semester the exam schedule screen reads.
 * @param odTracker the entries the user recorded by hand about an OD, keyed by date. Left to the
 *   caller because it is read out of `SettingsManager`, not the snapshot; passing the empty map
 *   degrades the day's OD row from "wasted"/"recovered" to "On-Duty" and nothing else.
 * @param profileImageUrl already gated by the profile-photo setting by the caller, so the privacy
 *   decision stays where that setting lives rather than moving in here.
 */
fun calendarSources(
    calendar: NamedCalendar?,
    snapshot: DomainSnapshot,
    semesterId: String,
    moodle: List<MoodleAssignment> = emptyList(),
    tasks: List<HomeworkTask> = emptyList(),
    odTracker: Map<String, List<TrackedOd>> = emptyMap(),
    registeredEvents: List<EventHubRegisteredEvent> = emptyList(),
    profileImageUrl: String? = null,
): CalendarSources {
    val courses = Projections.semesterAttendance(snapshot, semesterId)
    val exams = Projections.examsForKnownSemester(snapshot, semesterId)

    return CalendarSources(
        calendars = calendar?.toCalendarMonths().orEmpty(),
        // One blank key: Kotlin's snapshot dropped the series the exam schedule is keyed by, so
        // there is nothing better to use. See `CalendarSources.examSchedule`.
        examSchedule = mapOf("" to exams),
        moodle = moodle,
        courses = courses,
        od = odRecordsFrom(courses),
        tasks = tasks,
        odTracker = odTracker,
        registeredEvents = registeredEvents.toCalendarRegistrations(),
        profileImageUrl = profileImageUrl,
    )
}

/**
 * One named calendar from the picker, flattened into the shape [buildEnrichedCalendars] reads.
 *
 * Same mapping `VtopIngestor` applies to the single selected calendar; the payload types are
 * identical, only the wrapper differs (`NamedCalendar` versus `CalendarRes`).
 */
fun NamedCalendar.toCalendarMonths(): List<CalendarMonth> = months.map { month ->
    CalendarMonth(
        label = month.month,
        days = month.days.map { day ->
            CalendarDay(
                date = day.date,
                events = day.events.map { e ->
                    CalendarEventItem(
                        type = e.type,
                        text = e.text,
                        color = e.color,
                        category = e.category,
                    )
                },
            )
        },
    )
}

/**
 * EventHub's registrations, in the shape the calendar joins them in.
 *
 * `EventHubRegisteredEvent` has no `paymentStatus`, so every entry counts as confirmed - which is
 * also Node's rule for a free event, where the field is absent. Dropping paid-but-unpaid entries is
 * therefore not available yet rather than deliberately skipped.
 */
fun List<EventHubRegisteredEvent>.toCalendarRegistrations(): List<CalendarRegistration> = map { r ->
    CalendarRegistration(
        eid = r.eid,
        title = r.title,
        venue = r.location,
        date = r.date,
        time = r.time,
    )
}
