package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.odWeight

/**
 * One on-duty row derived locally from a real attendance log row.
 *
 * Pure value object: a date, a course, and the lab/theory hours implied by that course's slot.
 * It is never fabricated - a row only exists because an attendance log actually said "On Duty".
 */
data class DerivedOdRow(
    val date: String,
    val courseCode: String,
    val title: String,
    val type: String,
    val hours: Int,
)

/** True when a VTOP attendance status text denotes on duty (case/whitespace insensitive). */
internal fun isOnDutyStatus(status: String): Boolean {
    val value = status.trim().lowercase()
    return value == "on duty" || value == "od" || value == "onduty"
}

/**
 * Derive on-duty rows from the attendance data the central sync already persisted.
 *
 * Pure and local: no network call, no `AppState` access, no side effects. Every returned row comes
 * from a real log whose status is "On Duty" / "od" / "onduty"; blank dates and every other status
 * are skipped rather than guessed at. The lab split uses the legacy ODTrackerScreen rule (a slot
 * beginning with `L`), and hours come from the canonical [odWeight] heuristic (lab/ELA/PBL = 2h,
 * otherwise 1h), so these rows agree with `CalendarAttendance.odRecordsFrom` and can be unioned
 * with VTOP OD-details records without inventing anything.
 */
fun deriveOdRowsFromAttendance(attendance: List<CourseAttendance>): List<DerivedOdRow> {
    val rows = ArrayList<DerivedOdRow>()
    for (course in attendance) {
        val type = if (course.slotName.startsWith("L")) "LAB" else "TH"
        val hours = odWeight(type)
        for (entry in course.logs) {
            val date = entry.date.trim()
            if (date.isEmpty() || !isOnDutyStatus(entry.status)) continue
            rows += DerivedOdRow(
                date = date,
                courseCode = course.courseCode,
                title = course.courseTitle,
                type = type,
                hours = hours,
            )
        }
    }
    return rows
}
