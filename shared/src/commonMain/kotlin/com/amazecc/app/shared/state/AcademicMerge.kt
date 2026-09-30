package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.TimetableRes
import com.amazecc.app.shared.vtop.VtopComponent
import com.amazecc.app.shared.vtop.VtopCourseCode

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

    private fun componentOf(courseType: String?, slot: String? = null): VtopComponent {
        val fromLabel = VtopCourseCode.fromTypeLabel(courseType)
        if (fromLabel != VtopComponent.UNKNOWN) return fromLabel
        return VtopCourseCode.componentOf(rawCode = null, typeHint = courseType, slot = slot)
    }

    private fun creditsOf(raw: String?): Double? =
        raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }

    private fun slotTokens(raw: String?): List<String> =
        raw.orEmpty().split("+").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Folds a theory half and a lab half of one embedded course into a single entry.
     *
     * Marks are combined by **credit weight**, not summed. A 3-credit theory half and a
     * 2-credit lab half give `(theoryTotal * 3 + labTotal * 2) / 5`; adding the two totals
     * together would report a mark out of 200 against a max of 100. When the credit split is
     * unknown the plain mean is used rather than silently guessing a ratio.
     *
     * Attendance is summed rather than weighted — class counts add, and the percentage is
     * recomputed from the combined totals.
     */
    private fun mergeEmbeddedPair(
        theory: StoredCourse,
        lab: StoredCourse,
        theoryMarks: StoredMarks?,
        labMarks: StoredMarks?
    ): StoredCourse {
        val tCredits = creditsOf(theory.credits)
        val lCredits = creditsOf(lab.credits)
        val totalCredits = (tCredits ?: 0.0) + (lCredits ?: 0.0)

        val tTotal = theoryMarks?.totalMark
        val lTotal = labMarks?.totalMark
        val tMax = theoryMarks?.maxMark
        val lMax = labMarks?.maxMark

        val combinedTotal: Double?
        val combinedMax: Double?
        if (tTotal != null && lTotal != null) {
            combinedTotal = if (totalCredits > 0.0) {
                (tTotal * (tCredits ?: 0.0) + lTotal * (lCredits ?: 0.0)) / totalCredits
            } else {
                (tTotal + lTotal) / 2.0
            }
            combinedMax = if (totalCredits > 0.0) {
                ((tMax ?: 0.0) * (tCredits ?: 0.0) + (lMax ?: 0.0) * (lCredits ?: 0.0)) / totalCredits
            } else {
                ((tMax ?: 0.0) + (lMax ?: 0.0)) / 2.0
            }
        } else {
            combinedTotal = tTotal ?: lTotal
            combinedMax = tMax ?: lMax
        }

        val attendance = mergeAttendance(theory.attendance, lab.attendance)

        return StoredCourse(
            courseCode = theory.courseCode.ifBlank { lab.courseCode },
            courseTitle = theory.courseTitle.ifBlank { lab.courseTitle },
            courseType = "${VtopComponent.ETH.label} + ${VtopComponent.ELA.label}",
            category = theory.category ?: lab.category,
            credits = if (totalCredits > 0.0) trimNumber(totalCredits) else (theory.credits ?: lab.credits),
            classId = theory.classId ?: lab.classId,
            slots = (theory.slots + lab.slots).distinct(),
            venue = theory.venue ?: lab.venue,
            faculty = theory.faculty?.takeIf { it.isNotBlank() } ?: lab.faculty,
            courseSystem = theory.courseSystem ?: lab.courseSystem,
            attendance = attendance,
            marks = if (theoryMarks == null && labMarks == null) null else combineMarks(
                theoryMarks, labMarks, combinedTotal, combinedMax
            ),
            grade = theory.grade ?: lab.grade
        )
    }

    private fun combineMarks(
        theory: StoredMarks?,
        lab: StoredMarks?,
        total: Double?,
        max: Double?
    ): StoredMarks = StoredMarks(
        classNbr = theory?.classNbr ?: lab?.classNbr,
        assessments = (theory?.assessments.orEmpty() + lab?.assessments.orEmpty()),
        totalMark = total,
        maxMark = max,
        mergedFrom = "${VtopComponent.ETH.label}+${VtopComponent.ELA.label}"
    )

    private fun mergeAttendance(a: StoredAttendance?, b: StoredAttendance?): StoredAttendance? {
        if (a == null) return b
        if (b == null) return a
        val attended = a.attendedClasses + b.attendedClasses
        val total = a.totalClasses + b.totalClasses
        val pct = if (total > 0) {
            "${"%.2f".format(java.util.Locale.US, attended * 100.0 / total)}%"
        } else {
            a.attendancePercentage.ifBlank { b.attendancePercentage }
        }
        return StoredAttendance(
            attendedClasses = attended,
            totalClasses = total,
            attendancePercentage = pct,
            logs = (a.logs + b.logs).distinctBy { it.date to it.status }
        )
    }

    private fun trimNumber(v: Double): String =
        if (v == v.toInt().toDouble()) v.toInt().toString() else "%.2f".format(java.util.Locale.US, v)

    /** Applies [transform] to one semester, creating the semester if absent. */
    fun updateSemester(academic: AcademicData, semesterId: String, transform: (SemesterData) -> SemesterData): AcademicData {
        val semesters = academic.semesters.toMutableMap()
        val sem = semesters[semesterId] ?: SemesterData(semesterId = semesterId)
        semesters[semesterId] = transform(sem)
        return academic.copy(semesters = semesters)
    }

    /**
     * Re-keys any legacy `(L)`/`(T)`-suffixed rows onto the bare code and folds embedded pairs.
     *
     * Snapshots written before this change still hold suffixed keys, so a plain key change alone
     * would leave them orphaned. Pure and idempotent.
     */
    fun normalizeEmbeddedKeys(snapshot: AppDataSnapshot): AppDataSnapshot {
        val academic = snapshot.academic
        val needsWork = academic.semesters.any { (_, sem) ->
            sem.courses.keys.any { VtopCourseCode.base(it) != it }
        }
        if (!needsWork) return snapshot

        var changed = false
        val semesters = academic.semesters.mapValues { (_, sem) ->
            if (sem.courses.isEmpty()) return@mapValues sem
            val courses = sem.courses.toMutableMap()

            for (oldKey in courses.keys.toList()) {
                val newKey = VtopCourseCode.base(oldKey)
                if (newKey.isEmpty() || newKey == oldKey) continue
                val course = courses.remove(oldKey) ?: continue
                val target = courses[newKey]
                courses[newKey] = if (target == null) {
                    course.copy(courseCode = newKey)
                } else {
                    val theory = if (VtopCourseCode.suffixOf(oldKey) == "L") target else course
                    val lab = if (VtopCourseCode.suffixOf(oldKey) == "L") course else target
                    mergeEmbeddedPair(theory, lab, theory.marks, lab.marks)
                }
            }
            val before = courses.size
            courses.keys.removeIf { VtopCourseCode.base(it) != it }
            if (courses.size != before) changed = true
            sem.copy(courses = courses)
        }
        if (!changed) return snapshot
        return snapshot.copy(academic = academic.copy(semesters = semesters))
    }

    fun upsertAttendance(academic: AcademicData, semesterId: String, res: AttendanceRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeAttendance(res) ?: return academic
        val items = cleaned.attendance.orEmpty()
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(attendance = null) }.toMutableMap()
            // Group by bare code: an embedded course arrives as two rows (ETH + ELA) and must be
            // folded onto one key, or the second overwrites the first.
            for ((key, group) in items.groupBy { VtopCourseCode.base(it.courseCode) }) {
                if (key.isEmpty()) continue
                val existing = courses[key]

                var attendance = existing?.attendance
                for (item in group) {
                    attendance = mergeAttendance(
                        attendance,
                        StoredAttendance(
                            attendedClasses = item.attendedClasses,
                            totalClasses = item.totalClasses,
                            attendancePercentage = item.attendancePercentage,
                            logs = item.logs
                        )
                    )
                }

                // Credits add across an embedded pair rather than one half winning.
                val creditValues = group.mapNotNull { creditsOf(it.credits) }
                val credits = when {
                    creditValues.isEmpty() -> existing?.credits
                    creditValues.size == 1 -> group.firstNotNullOfOrNull { it.credits }
                    else -> trimNumber(creditValues.sum())
                }

                courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                    courseCode = key,
                    courseTitle = existing?.courseTitle?.takeIf { it.isNotBlank() }
                        ?: group.firstOrNull { it.courseTitle.isNotBlank() }?.courseTitle.orEmpty(),
                    courseType = existing?.courseType?.takeIf { it.isNotBlank() }
                        ?: group.firstOrNull { it.courseType.isNotBlank() }?.courseType.orEmpty(),
                    category = existing?.category ?: group.firstNotNullOfOrNull { it.category },
                    credits = credits ?: existing?.credits,
                    slots = (existing?.slots.orEmpty() + group.flatMap { slotTokens(it.slotName) }).distinct(),
                    venue = existing?.venue ?: group.firstNotNullOfOrNull { it.slotVenue },
                    faculty = existing?.faculty?.takeIf { it.isNotBlank() }
                        ?: group.firstOrNull { it.faculty.isNotBlank() }?.faculty.orEmpty(),
                    attendance = attendance
                )
            }
            sem.copy(courses = courses)
        }
    }

    fun upsertMarks(academic: AcademicData, semesterId: String, res: MarksRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeMarks(res) ?: return academic
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(marks = null) }.toMutableMap()

            val groups = cleaned.courses.groupBy { VtopCourseCode.base(it.courseCode) }
            for ((key, group) in groups) {
                if (key.isEmpty()) continue
                val existing = courses[key]

                val theory = group.firstOrNull { !componentOf(it.courseType, it.slot).isLab }
                val lab = group.firstOrNull { componentOf(it.courseType, it.slot).isLab }

                val built = if (theory != null && lab != null) {
                    mergeEmbeddedPair(
                        theory = theory.toStoredCourse(key, existing),
                        lab = lab.toStoredCourse(key, existing),
                        theoryMarks = theory.toStoredMarks(),
                        labMarks = lab.toStoredMarks()
                    )
                } else {
                    val only = group.first()
                    (existing ?: StoredCourse(courseCode = key)).copy(
                        courseTitle = only.courseTitle.ifBlank { existing?.courseTitle.orEmpty() },
                        courseType = only.courseType.ifBlank { existing?.courseType.orEmpty() },
                        courseSystem = only.courseSystem.ifBlank { existing?.courseSystem.orEmpty() },
                        faculty = only.faculty.ifBlank { existing?.faculty.orEmpty() },
                        slots = (existing?.slots.orEmpty() + slotTokens(only.slot)).distinct(),
                        credits = creditsOf(only.credits)?.let(::trimNumber) ?: existing?.credits,
                        marks = only.toStoredMarks()
                    )
                }

                courses[key] = built.copy(
                    courseCode = key,
                    credits = built.credits ?: existing?.credits
                )
            }

            val gpa = sem.gpa?.takeIf { it.isNotBlank() } ?: cleaned.cgpa?.cgpa
            sem.copy(gpa = gpa, courses = courses)
        }
    }

    private fun com.amazecc.app.shared.model.MarksCourseItem.toStoredCourse(
        key: String,
        existing: StoredCourse?
    ) = (existing ?: StoredCourse(courseCode = key)).copy(
        courseTitle = courseTitle,
        courseType = courseType,
        courseSystem = courseSystem.ifBlank { existing?.courseSystem.orEmpty() },
        faculty = faculty.ifBlank { existing?.faculty.orEmpty() },
        slots = slotTokens(slot),
        credits = credits
    )

    private fun com.amazecc.app.shared.model.MarksCourseItem.toStoredMarks(): StoredMarks = StoredMarks(
        classNbr = classNbr.ifBlank { null },
        assessments = assessments,
        totalMark = assessments.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
            .takeIf { assessments.any { it.weightageMark.toDoubleOrNull() != null } },
        maxMark = assessments.sumOf { it.maxMark.toDoubleOrNull() ?: 0.0 }
            .takeIf { assessments.any { it.maxMark.toDoubleOrNull() != null } }
    )

    fun upsertGrades(academic: AcademicData, semesterId: String, gpa: String?, items: List<com.amazecc.app.shared.model.GradeItem>): AcademicData {
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(grade = null) }.toMutableMap()
            for (g in items) {
                val key = VtopCourseCode.base(g.courseCode)
                if (key.isEmpty()) continue
                val existing = courses[key]
                courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                    courseCode = key,
                    courseTitle = g.courseTitle.ifBlank { existing?.courseTitle.orEmpty() },
                    courseType = g.courseType.ifBlank { existing?.courseType.orEmpty() },
                    grade = existing?.grade ?: StoredGrade(
                        grandTotal = g.grandTotal.ifBlank { null },
                        grade = g.grade.ifBlank { null },
                        details = g.details,
                        range = g.range
                    )
                )
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
            // Bare code: the timetable contributes identity and slots only, and an embedded
            // course's ETH and ELA rows both fold onto the one key.
            for (info in cleaned.courseInfo.orEmpty()) {
                val key = VtopCourseCode.base(info.courseCode ?: continue)
                if (key.isEmpty()) continue
                val (slotPart, venue) = AcademicDerivers.splitSlotVenue(info.slotVenue)
                val slotCodes = slotPart?.split("+").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                val ltpjcCredits = info.LTPJC?.split("-")?.lastOrNull()?.trim()?.takeIf { it.toIntOrNull() != null }
                val existing = courses[key]
                courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                    courseCode = key,
                    courseTitle = info.course?.takeIf { it.isNotBlank() } ?: existing?.courseTitle.orEmpty(),
                    courseType = existing?.courseType?.takeIf { it.isNotBlank() }
                        ?: VtopCourseCode.typeLabelOf(
                            rawCode = info.courseCode,
                            typeHint = info.category,
                            slot = info.slotVenue
                        ).orEmpty(),
                    category = info.category ?: existing?.category,
                    // Never overwrite a parseable credits value with the raw LTPJC string; only fill from LTPJC when missing.
                    credits = existing?.credits?.takeIf { it.isNotBlank() } ?: ltpjcCredits ?: existing?.credits,
                    classId = info.classId ?: existing?.classId,
                    slots = (existing?.slots ?: emptyList()) + slotCodes.filter { it !in (existing?.slots.orEmpty()) },
                    venue = venue ?: existing?.venue,
                    faculty = info.facultyDetails ?: existing?.faculty
                )
            }
            sem.copy(courses = courses)
        }
    }
}
