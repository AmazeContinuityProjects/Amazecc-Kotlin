package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.deriveOdHoursDays
import com.amazecc.app.shared.domain.isOdHoursStatus

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

/** Kept as a named seam for callers that used the old broader vocabulary; now web-exact. */
internal fun isOnDutyStatus(status: String): Boolean = isOdHoursStatus(status)

/**
 * Derive on-duty rows from the attendance data the central sync already persisted.
 *
 * Pure and local: no network call, no `AppState` access, no side effects. Delegates entirely to
 * the canonical web-equivalent derivation in `domain/OdHoursDerivation.kt`, so the OD Tracker,
 * home tile and widget cannot disagree about which statuses count or whether a `(L)` course code
 * with a theory slot is worth one hour or two.
 */
fun deriveOdRowsFromAttendance(attendance: List<CourseAttendance>): List<DerivedOdRow> =
    deriveOdHoursDays(attendance).flatMap { day ->
        day.entries.map { entry ->
            DerivedOdRow(
                date = day.date,
                courseCode = entry.courseCode,
                title = entry.title,
                type = entry.type,
                hours = entry.hours,
            )
        }
    }
