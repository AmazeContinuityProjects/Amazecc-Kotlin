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
 * The *raw* per-course attendance rows for the semester that actually carries attendance —
 * re-expanded from `Course.halves` so the web's row grain is restored.
 *
 * D-125: the web counts `attendanceRes.attendance` row by row (see
 * `../AmazeCC/src/components/custom/app/useAttendanceState.ts`). An embedded theory/lab pair is two
 * rows with two `slotName`s and two `viewLink`s there.
 *
 * D-133: the snapshot the app stores is *already merged*. `VtopIngestor.fromLegacy()` runs
 * `EmbeddedLinkMerge.mergeSnapshot` at ingest time, folding the pair into one `Course` whose
 * `halves` retain the original per-component rows. Production never holds an unmerged snapshot, so
 * "raw" here does not mean "unmerged" — it means re-expanding each merged course into one row per
 * half, built from that half's OWN slot and OWN logs (i.e. before the merge's `(date, status)`
 * de-duplication). Going through `Course.asAttendance()` instead yields a single `"C1+L31"` row:
 * the merged slot no longer starts with `L` (lab charged 1h instead of 2h) and the merge dropped
 * any second OD log that two halves shared on a date. Fields a half does not carry (venue,
 * faculty, category) fall back to the parent course.
 *
 * The widget, which reads the unmerged `SemesterData`, never had that bug — which is exactly why
 * the two surfaces disagreed. Counting these re-expanded rows keeps every consumer on the web's grain.
 */
internal fun rawReportingAttendance(snapshot: DomainSnapshot): List<CourseAttendance> {
    val semester = snapshot.academics.semesters.values
        .filter { sem -> sem.courses.values.any { it.attendance != null } }
        .maxWithOrNull(
            compareBy<Semester> { it.courses.values.count { c -> c.attendance != null } }
                .thenBy { it.id }
        ) ?: return emptyList()
    return semester.courses.values.flatMap { course ->
        if (course.halves.isEmpty()) {
            listOf(course.asAttendance())
        } else {
            course.halves.map { half -> half.asReportingAttendance(parent = course) }
        }
    }
}

/**
 * One [CourseAttendance] row from an embedded half's own slot and logs. Only fields the half does
 * not carry fall back to the merged [parent] course; crucially the slot stays the half's raw slot
 * (so an `L31` half still reads as a 2h lab) and the logs stay the half's own, un-deduplicated ones.
 */
private fun EmbeddedCourseHalf.asReportingAttendance(parent: Course): CourseAttendance {
    val att = attendance
    return CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = type,
        slotName = slots.joinToString("+"),
        slotVenue = parent.venue,
        faculty = parent.faculty ?: "",
        credits = credits ?: parent.credits,
        category = parent.category,
        attendedClasses = att?.attended ?: 0,
        totalClasses = att?.total ?: 0,
        attendancePercentage = att?.percentage ?: "",
        logs = att?.logs.orEmpty(),
    )
}

/** Web-parity OD days straight from the synced snapshot, one raw course row at a time. */
fun deriveOdDaysFromSnapshot(snapshot: DomainSnapshot): List<OdHoursDay> =
    deriveOdHoursDays(rawReportingAttendance(snapshot))

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
