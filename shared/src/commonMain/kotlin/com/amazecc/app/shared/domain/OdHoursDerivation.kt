package com.amazecc.app.shared.domain

/**
 * Web-equivalent On-Duty hours derivation (D-120).
 *
 * Ported intrinsically from `../AmazeCC/src/components/custom/app/useAttendanceState.ts`, which is
 * the *only* source of OD hour counts in the web OD Hours History page (`ODTrackerSubpage.tsx`).
 * The rules there are deliberately narrow:
 *
 *  - a log counts only when its status is exactly `"On Duty"` — `"Partial OD"`, `"od"`,
 *    `"onduty"` and case variants are not OD hours (a half-day is not an OD day);
 *  - a period is a 2-hour lab only when the raw `slotName` starts with `L`; a lab-suffixed
 *    *course code* (`18CSC301(L)`) on an `A1` slot is still a 1-hour theory period to the web;
 *  - logs are grouped by date and every matching log is counted (no slot-level de-duplication);
 *  - there is no cap and no clock-time parsing — hours come from the slot, never from a VTOP
 *    "09:00 - 11:00" cell, and VTOP OD-details records are annotation-only, never added to hours.
 *
 * Every surface showing OD hours must go through this one derivation so the number is identical
 * on the OD Tracker, the home tile and the widget. No value here is fabricated: a row exists only
 * because an attendance log actually said "On Duty".
 */

/** True only for the exact VTOP status the web OD derivation counts. */
fun isOdHoursStatus(status: String): Boolean = status == "On Duty"

/** Web lab test: the raw slot string must begin with `L`. A `(L)` code suffix is not enough. */
fun odHoursForSlot(slotName: String): Int = if (slotName.startsWith("L")) 2 else 1

/** One counted OD period, carrying the real course code so annotations can still be keyed by it. */
data class OdHoursEntry(
    val courseCode: String,
    val title: String,
    val type: String, // "LAB" | "TH", matching the web ODListItem entry
    val hours: Int,
)

/** One day of OD hours, equivalent to a web `ODListItem`. */
data class OdHoursDay(
    val date: String,
    val entries: List<OdHoursEntry>,
    val total: Int = entries.sumOf { it.hours },
)

/**
 * Derive OD days from synced attendance with the exact web rules.
 *
 * Blank dates and non-"On Duty" statuses are skipped. Logs are grouped by date and every match
 * is kept, so duplicate slots and several OD logs on one day are counted exactly as the web counts
 * them. The result is ordered oldest-first like the web's `sort((a, b) => new Date(a.date) - …)`.
 */
fun deriveOdHoursDays(attendance: List<CourseAttendance>): List<OdHoursDay> {
    val byDate = LinkedHashMap<String, MutableList<OdHoursEntry>>()
    for (course in attendance) {
        val hours = odHoursForSlot(course.slotName)
        val type = if (hours == 2) "LAB" else "TH"
        for (log in course.logs) {
            val date = log.date
            if (date.isBlank() || !isOdHoursStatus(log.status)) continue
            byDate.getOrPut(date) { mutableListOf() } +=
                OdHoursEntry(course.courseCode, course.courseTitle, type, hours)
        }
    }
    return byDate
        .map { (date, entries) -> OdHoursDay(date, entries) }
        .sortedWith(compareBy({ odDateSortKey(it.date) }, { it.date }))
}

/** "Total OD" on the web page: the sum of the per-day totals, nothing else. */
fun totalOdHours(days: List<OdHoursDay>): Int = days.sumOf { it.total }

/**
 * Ascending sort key for the two date shapes VTOP sends (`2026-10-05` and `12-Sep-2026`).
 * Unparseable dates sink to the bottom rather than throwing.
 */
private fun odDateSortKey(raw: String): String {
    val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(raw.trim())
    if (iso != null) return iso.groupValues[1] + iso.groupValues[2] + iso.groupValues[3]

    val vtop = Regex("^(\\d{1,2})-([A-Za-z]{3})-(\\d{4})$").matchEntire(raw.trim())
    if (vtop != null) {
        val month = when (vtop.groupValues[2].lowercase()) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
            "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> 0
        }
        return vtop.groupValues[3] + month.toString().padStart(2, '0') +
            vtop.groupValues[1].padStart(2, '0')
    }
    return "9999999999"
}
