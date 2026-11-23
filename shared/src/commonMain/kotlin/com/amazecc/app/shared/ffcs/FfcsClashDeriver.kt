package com.amazecc.app.shared.ffcs

/**
 * D-185 — derive the clash-report input from the *real* slots of the selected courses.
 *
 * Pure and self-contained: no I/O, no clock, no randomness, no defaults other than the canonical
 * [FfcsSlotSchema.CHENNAI] schema. Every emitted [FfcsClash], [FfcsClashEntry] and
 * [FfcsClashMeeting] is built from an actual open-interval overlap between two real slot periods;
 * when nothing overlaps, `derive` returns an empty list and the builder honestly prints
 * "No clashes - all courses fit".
 *
 * Semantics chosen for the report cell `OTHER (Day time)`:
 *  - one [FfcsClash] row per course that has at least one real overlap;
 *  - one [FfcsClashEntry] per other course it overlaps, in ascending input order;
 *  - [FfcsClashEntry.meetings] are **this row's own** concrete [SlotPeriod]s that overlap the
 *    other course (day = [SlotPeriod.day], time = [SlotPeriod.start]), not the full slot list
 *    and not a synthesised intersection clock; identical meetings are collapsed once;
 *  - rows keep the input order, so a caller can map them straight back to its selection.
 *
 * [FfcsSlotSchema.periodListsClash] is the boolean form of the same test; we use
 * [FfcsSlotSchema.periodsOverlap] directly because the report needs the concrete periods, not just
 * a yes/no answer. `end == start` (back-to-back classes) is never a clash.
 */
object FfcsClashDeriver {

    /** Real overlaps among [courses]; empty when none of them actually collide. */
    fun derive(courses: List<AddedCourse>): List<FfcsClash> {
        if (courses.size < 2) return emptyList()

        // Expand once per course. Blank tokens are dropped by splitSlots; unknown tokens resolve
        // to no periods at all, so they can never create a fabricated meeting.
        val periodsByCourse: List<List<SlotPeriod>> = courses.map { course ->
            course.slots
                .flatMap { FfcsSlotSchema.splitSlots(it) }
                .flatMap { FfcsSlotSchema.periodsForSlot(it, FfcsSlotSchema.CHENNAI) }
        }

        val entriesByCourse: List<MutableList<FfcsClashEntry>> =
            List(courses.size) { mutableListOf() }

        // Unordered pairs only: an overlap is symmetrical, so feed both rows from one scan.
        for (mine in courses.indices) {
            val minePeriods = periodsByCourse[mine]
            if (minePeriods.isEmpty()) continue
            for (other in mine + 1 until courses.size) {
                val otherPeriods = periodsByCourse[other]
                if (otherPeriods.isEmpty()) continue

                val myOverlaps = minePeriods.filter { a ->
                    otherPeriods.any { b -> FfcsSlotSchema.periodsOverlap(a, b) }
                }
                if (myOverlaps.isEmpty()) continue // periodsOverlap is symmetrical
                val theirOverlaps = otherPeriods.filter { b ->
                    minePeriods.any { a -> FfcsSlotSchema.periodsOverlap(a, b) }
                }

                entriesByCourse[mine].add(
                    FfcsClashEntry(
                        code = courses[other].code,
                        meetings = meetingsOf(myOverlaps),
                    ),
                )
                entriesByCourse[other].add(
                    FfcsClashEntry(
                        code = courses[mine].code,
                        meetings = meetingsOf(theirOverlaps),
                    ),
                )
            }
        }

        return courses.indices
            .filter { entriesByCourse[it].isNotEmpty() }
            .map { FfcsClash(course = courses[it], clashesWith = entriesByCourse[it].toList()) }
    }

    /** Day/clock pairs of the concrete overlapping periods, first occurrence first. */
    private fun meetingsOf(periods: List<SlotPeriod>): List<FfcsClashMeeting> =
        periods.map { FfcsClashMeeting(day = it.day, time = it.start) }.distinct()
}
