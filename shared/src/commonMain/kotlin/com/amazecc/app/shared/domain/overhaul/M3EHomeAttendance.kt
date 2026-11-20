package com.amazecc.app.shared.domain.overhaul

import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.state.AcademicDerivers
import kotlin.math.round

/**
 * Home-only projection: the attendance figure a class timeline card shows.
 *
 * A merged [Course] keeps its raw theory/lab rows in [Course.halves]. The home timeline is drawn
 * one class meeting at a time, so the slot on that card picks the half: a lab meeting shows the lab
 * half's attendance, a theory meeting the theory half's. A standalone Lab Only / Theory Only course
 * has no halves and reads its own [Course.attendance], which already *is* that component's figure.
 *
 * Parsing mirrors `AcademicDerivers.percentOf` (VTOP's printed percentage first, then the
 * attended/total counts), but keeps "no data" as null instead of 0.0 so the card can hide its badge
 * rather than paint a course nobody has sat as 0%.
 */
object M3EHomeAttendance {

    /**
     * The real percentage for this class card's component, or null when nothing was published.
     *
     * @param slotToken one of the slot codes the card was built from (e.g. "L55"), used to pick the
     *   owning half of an embedded course. When it does not resolve, the course's canonical lab test
     *   decides which half to read.
     */
    fun percentForSlot(course: Course, slotToken: String? = null): Double? {
        val halves = course.halves
        if (halves.isEmpty()) return percentOf(course.attendance)

        // The store keeps a timetable cell such as "L55+L56" as one string, so compare the
        // individual slot tokens rather than the whole cell.
        val matchingHalf = slotToken?.takeIf { it.isNotBlank() }
            ?.let { token -> halves.firstOrNull { half -> half.slots.any { it.split("+").any { part -> part.trim() == token } } } }
        val wantLab = when {
            matchingHalf != null -> matchingHalf.isLab
            else -> AcademicDerivers.isLabCourse(course.code, course.type, course.slots)
        }
        val half = halves.firstOrNull { it.isLab == wantLab }
            ?: halves.firstOrNull { it.half.equals(if (wantLab) "L" else "T", ignoreCase = true) }
        return percentOf(half?.attendance ?: course.attendance)
    }

    /**
     * Which component a slot of an embedded course belongs to: "LAB" or "THEORY".
     *
     * VTOP stores a timetable cell such as "L31+L32" as one string, so [slotToken] is split on
     * '+' first. When [Course.halves] is present the half that owns any atomic token decides,
     * using the very same lab test [EmbeddedCourseHalf.isLab] that [percentForSlot] relies on —
     * so the component a card is classified as can never disagree with the percentage it reads.
     * A token no half owns (a theory-only "TB1", or a standalone course) falls back to the
     * canonical [AcademicDerivers.isLabCourse] identity test.
     */
    fun componentForSlot(course: Course, slotToken: String): String {
        val atoms = splitAtoms(slotToken)
        val halves = course.halves
        if (halves.isNotEmpty()) {
            val owningHalf = halves.firstOrNull { half ->
                half.slots.any { cell -> splitAtoms(cell).any { it in atoms } }
            }
            if (owningHalf != null) return if (owningHalf.isLab) "LAB" else "THEORY"
        }
        val isLab = AcademicDerivers.isLabCourse(
            course.code,
            course.type,
            atoms.ifEmpty { listOf(slotToken) }
        )
        return if (isLab) "LAB" else "THEORY"
    }

    /**
     * True only when two adjacent slots of the same course are the same component and may merge.
     *
     * This is the home-timeline counterpart of the web guard in `attendanceTimetable.ts:122-157`,
     * which refuses to glue two rows that differ in component. Two meetings of one embedded
     * course must never merge across halves: doing so would keep the first card's percentage and
     * discard the other half's. Every atomic token on either side must also belong to the resolved
     * component, so a mixed cell such as "B1+L55" can never be folded into one card.
     */
    fun canMergeAdjacentSlots(course: Course, previousSlot: String, currentSlot: String): Boolean {
        val component = componentForSlot(course, previousSlot)
        if (component != componentForSlot(course, currentSlot)) return false
        return (splitAtoms(previousSlot) + splitAtoms(currentSlot))
            .all { componentForSlot(course, it) == component }
    }

    /** A timetable cell ("L31+L32") is a set of atomic slot tokens. */
    private fun splitAtoms(cell: String): List<String> =
        cell.split("+").map { it.trim() }.filter { it.isNotBlank() }

    private val EmbeddedCourseHalf.isLab: Boolean
        get() = half.equals("L", ignoreCase = true) ||
            AcademicDerivers.isLabCourse(code, type, slots)

    private fun percentOf(attendance: Attendance?): Double? {
        if (attendance == null) return null
        val printed = attendance.percentage.replace("%", "").trim().toDoubleOrNull()
        if (printed != null) return printed
        return if (attendance.total > 0) {
            round(attendance.attended * 10000.0 / attendance.total) / 100.0
        } else null
    }
}
