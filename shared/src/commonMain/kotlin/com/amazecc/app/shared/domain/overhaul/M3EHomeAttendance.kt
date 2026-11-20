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
