package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.TimetableRes

/**
 * Pure upsert functions for the unified academic schema.
 *
 * Semantics (see docs/features/schemas/02-target-schema.md):
 * - attendance / marks / grades / exams are COMPLETE server lists → they replace
 *   their own domain block per semester, never touching other domains.
 * - timetable is an IDENTITY MERGE → fills missing fields, never blanks existing
 *   attendance / marks / grade blocks.
 * Every transport value passes through [AppSanitizers] at the store boundary.
 */
object AcademicMerge {

    private val embeddedSuffix = Regex("\\([LPT]\\)$")

    private enum class CourseComponent { THEORY, LAB, UNKNOWN }

    /**
     * Infers which embedded component an incoming row belongs to, from the row's
     * own code suffix, its type/category hint, or its slot codes (lab slots are
     * `L*`). Used so theory rows land only on the `(T)` variant and lab rows only
     * on the `(L)` variant — a row must never leak into the sibling component.
     */
    private fun componentOf(rawCode: String?, typeHint: String?, slotTokens: List<String>): CourseComponent = when {
        rawCode?.endsWith("(T)", ignoreCase = true) == true -> CourseComponent.THEORY
        rawCode?.endsWith("(L)", ignoreCase = true) == true -> CourseComponent.LAB
        typeHint?.contains("Lab", ignoreCase = true) == true -> CourseComponent.LAB
        slotTokens.isNotEmpty() && slotTokens.all { it.uppercase().startsWith("L") } -> CourseComponent.LAB
        slotTokens.isNotEmpty() -> CourseComponent.THEORY
        else -> CourseComponent.UNKNOWN
    }

    /**
     * Resolves the map key an incoming course row should land on.
     *
     * VTOP attendance rows carry `(T)`/`(L)` suffixed codes (one entry per
     * embedded component), while marks / grades / timetable rows usually carry
     * the plain base code. Suffixed rows always write to their exact key; base
     * rows are routed to the suffixed variant matching their component
     * (theory → `(T)`, lab → `(L)`), preferring theory when ambiguous, and only
     * fall back to the base key when no suffixed variant exists yet.
     */
    private fun targetKeys(courses: Map<String, StoredCourse>, rawCode: String, component: CourseComponent = CourseComponent.UNKNOWN): List<String> {
        val code = rawCode.trim()
        if (code.endsWith("(T)", ignoreCase = true) || code.endsWith("(L)", ignoreCase = true)) return listOf(code)
        val stripped = code.replace(embeddedSuffix, "").trim()
        val variants = courses.keys.filter { it.replace(embeddedSuffix, "").trim() == stripped }
        if (variants.isEmpty()) return listOf(code)
        val preferred = when (component) {
            CourseComponent.LAB -> variants.firstOrNull { it.endsWith("(L)", ignoreCase = true) }
            else -> variants.firstOrNull { it.endsWith("(T)", ignoreCase = true) }
        }
        return listOf(preferred ?: variants.first())
    }

    /**
     * Drops stale unsuffixed duplicates of embedded courses. The attendance
     * payload is the complete server list; embedded components live on the
     * suffixed keys, so a lingering base key would double-schedule every slot.
     * The base's data is folded into the sibling(s) only where they are missing it.
     */
    private fun pruneStaleBaseKeys(courses: MutableMap<String, StoredCourse>, skipKeys: Set<String> = emptySet()) {
        val suffixedKeys = courses.keys
            .filter { it.endsWith("(T)", ignoreCase = true) || it.endsWith("(L)", ignoreCase = true) }
        if (suffixedKeys.isEmpty()) return
        val bases = suffixedKeys.map { it.replace(embeddedSuffix, "").trim() }.toSet()
        for (key in courses.keys.filter { key ->
            key !in skipKeys &&
                !key.endsWith("(T)", ignoreCase = true) && !key.endsWith("(L)", ignoreCase = true) &&
                key.replace(embeddedSuffix, "").trim() in bases
        }.toList()) {
            val base = courses[key] ?: continue
            val stripped = key.replace(embeddedSuffix, "").trim()
            for (sib in suffixedKeys.filter { it.replace(embeddedSuffix, "").trim() == stripped }) {
                val sibCourse = courses[sib] ?: continue
                courses[sib] = sibCourse.copy(
                    slots = sibCourse.slots.ifEmpty { base.slots },
                    venue = sibCourse.venue ?: base.venue,
                    faculty = sibCourse.faculty?.takeIf { it.isNotBlank() } ?: base.faculty,
                    courseTitle = sibCourse.courseTitle.ifBlank { base.courseTitle },
                    courseType = sibCourse.courseType.ifBlank { base.courseType },
                    category = sibCourse.category ?: base.category,
                    credits = sibCourse.credits ?: base.credits,
                    courseSystem = sibCourse.courseSystem ?: base.courseSystem,
                    attendance = sibCourse.attendance ?: base.attendance,
                    marks = sibCourse.marks ?: base.marks,
                    grade = sibCourse.grade ?: base.grade
                )
            }
            courses.remove(key)
        }
    }

    /** Applies [transform] to one semester, creating the semester if absent. */
    fun updateSemester(academic: AcademicData, semesterId: String, transform: (SemesterData) -> SemesterData): AcademicData {
        val semesters = academic.semesters.toMutableMap()
        val sem = semesters[semesterId] ?: SemesterData(semesterId = semesterId)
        semesters[semesterId] = transform(sem)
        return academic.copy(semesters = semesters)
    }

    /** Normalises a whole snapshot's academic data (stale base keys → embedded variants). Pure, idempotent. */
    fun normalizeEmbeddedKeys(snapshot: AppDataSnapshot): AppDataSnapshot {
        val academic = snapshot.academic
        if (academic.semesters.none { sem -> sem.value.courses.keys.any { !it.endsWith("(T)", true) && !it.endsWith("(L)", true) } }) return snapshot
        var changed = false
        val semesters = academic.semesters.mapValues { (_, sem) ->
            if (sem.courses.isEmpty()) return@mapValues sem
            val courses = sem.courses.toMutableMap()
            val before = courses.size
            pruneStaleBaseKeys(courses)
            if (courses.size == before) sem else { changed = true; sem.copy(courses = courses) }
        }
        if (!changed) return snapshot
        return snapshot.copy(academic = academic.copy(semesters = semesters))
    }

    fun upsertAttendance(academic: AcademicData, semesterId: String, res: AttendanceRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeAttendance(res) ?: return academic
        val items = cleaned.attendance.orEmpty()
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(attendance = null) }.toMutableMap()
            for (item in items) {
                val existing = courses[item.courseCode]
                courses[item.courseCode] = (existing ?: StoredCourse(courseCode = item.courseCode)).copy(
                    courseCode = item.courseCode,
                    courseTitle = item.courseTitle.ifBlank { existing?.courseTitle.orEmpty() },
                    courseType = item.courseType.ifBlank { existing?.courseType.orEmpty() },
                    category = item.category ?: existing?.category,
                    credits = item.credits ?: existing?.credits,
                    slots = existing?.slots?.ifEmpty {
                        item.slotName.split("+").map { it.trim() }.filter { it.isNotEmpty() }
                    } ?: item.slotName.split("+").map { it.trim() }.filter { it.isNotEmpty() },
                    venue = item.slotVenue ?: existing?.venue,
                    faculty = item.faculty.ifBlank { existing?.faculty.orEmpty() },
                    attendance = StoredAttendance(
                        attendedClasses = item.attendedClasses,
                        totalClasses = item.totalClasses,
                        attendancePercentage = item.attendancePercentage,
                        logs = item.logs
                    )
                )
            }
            // The attendance list is complete: drop stale unsuffixed duplicates of
            // embedded courses (their data was folded into the suffixed siblings).
            pruneStaleBaseKeys(courses, items.map { it.courseCode }.toSet())
            sem.copy(courses = courses)
        }
    }

    fun upsertMarks(academic: AcademicData, semesterId: String, res: MarksRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeMarks(res) ?: return academic
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(marks = null) }.toMutableMap()
            for (course in cleaned.courses) {
                val slotTokens = course.slot.split("+").map { it.trim() }.filter { it.isNotEmpty() }
                val keys = targetKeys(courses, course.courseCode, componentOf(course.courseCode, course.courseType, slotTokens))
                for (key in keys) {
                    val existing = courses[key]
                    courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                        courseCode = key,
                        courseTitle = course.courseTitle.ifBlank { existing?.courseTitle.orEmpty() },
                        courseType = course.courseType.ifBlank { existing?.courseType.orEmpty() },
                        courseSystem = course.courseSystem.ifBlank { existing?.courseSystem.orEmpty() },
                        faculty = course.faculty.ifBlank { existing?.faculty.orEmpty() },
                        slots = existing?.slots?.ifEmpty { course.slot.split("+").map { it.trim() }.filter { it.isNotEmpty() } }
                            ?: course.slot.split("+").map { it.trim() }.filter { it.isNotEmpty() },
                        marks = StoredMarks(
                            classNbr = course.classNbr.ifBlank { null },
                            assessments = course.assessments
                        )
                    )
                }
            }
            // Fill the semester CGPA from the live marks payload when grades haven't published one yet.
            val gpa = sem.gpa?.takeIf { it.isNotBlank() } ?: cleaned.cgpa?.cgpa
            sem.copy(gpa = gpa, courses = courses)
        }
    }

    fun upsertGrades(academic: AcademicData, semesterId: String, gpa: String?, items: List<com.amazecc.app.shared.model.GradeItem>): AcademicData {
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(grade = null) }.toMutableMap()
            for (g in items) {
                val keys = targetKeys(courses, g.courseCode, componentOf(g.courseCode, g.courseType, emptyList()))
                for (key in keys) {
                    val existing = courses[key]
                    courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                        courseCode = key,
                        courseTitle = g.courseTitle.ifBlank { existing?.courseTitle.orEmpty() },
                        courseType = g.courseType.ifBlank { existing?.courseType.orEmpty() },
                        grade = StoredGrade(
                            grandTotal = g.grandTotal.ifBlank { null },
                            grade = g.grade.ifBlank { null },
                            details = g.details,
                            range = g.range
                        )
                    )
                }
            }
            sem.copy(gpa = gpa, courses = courses)
        }
    }

    fun upsertGrades(academic: AcademicData, res: AllGradesRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeAllGrades(res) ?: return academic
        var out = academic
        cleaned.grades.orEmpty().forEach { (semId, semResult) ->
            if (semResult != null) {
                out = upsertGrades(out, semId, semResult.gpa, semResult.grades)
            }
        }
        return out
    }

    fun upsertExams(academic: AcademicData, semesterId: String, res: ExamScheduleRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeExamSchedule(res) ?: return academic
        val exams = cleaned.schedule.values.flatten()
        return updateSemester(academic, semesterId) { sem ->
            sem.copy(exams = exams)
        }
    }

    fun upsertTimetable(academic: AcademicData, semesterId: String, res: TimetableRes?): AcademicData {
        val cleaned = AcademicDerivers.cleanTimetableInfo(res) ?: return academic
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.toMutableMap()
            for (info in cleaned.courseInfo.orEmpty()) {
                val code = info.courseCode ?: continue
                val (slotPart, venue) = AcademicDerivers.splitSlotVenue(info.slotVenue)
                val slotCodes = slotPart?.split("+").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                val keys = targetKeys(courses, code, componentOf(code, info.category, slotCodes))
                val ltpjcCredits = info.LTPJC?.split("-")?.lastOrNull()?.trim()?.takeIf { it.toIntOrNull() != null }
                for (key in keys) {
                    val existing = courses[key]
                    val type = AcademicDerivers.courseTypeOf(key)
                        ?: info.category?.takeIf { it.contains("Lab", true) }?.let { "Lab Only" }
                    courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                        courseCode = key,
                        courseTitle = info.course?.ifBlank { existing?.courseTitle.orEmpty() } ?: existing?.courseTitle.orEmpty(),
                        courseType = type ?: existing?.courseType.orEmpty(),
                        category = info.category ?: existing?.category,
                        // Never overwrite a parseable credits value with the raw LTPJC string; only fill from LTPJC when missing.
                        credits = existing?.credits?.takeIf { it.isNotBlank() } ?: ltpjcCredits ?: existing?.credits,
                        classId = info.classId ?: existing?.classId,
                        slots = (existing?.slots ?: emptyList()) + slotCodes.filter { it !in (existing?.slots.orEmpty()) },
                        venue = venue ?: existing?.venue,
                        faculty = info.facultyDetails ?: existing?.faculty
                    )
                }
            }
            sem.copy(courses = courses)
        }
    }
}
