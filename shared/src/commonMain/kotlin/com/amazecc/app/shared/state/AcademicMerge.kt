package com.amazecc.app.shared.state

import com.amazecc.app.shared.domain.EmbeddedLinkCode
import com.amazecc.app.shared.domain.EmbeddedLinkMerge
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

    /** `"L31+L32"` → `["L31", "L32"]`. VTOP writes a course's slots `+`-separated. */
    private fun slotTokens(raw: String?): List<String> =
        raw.orEmpty().split("+").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Whether a row is the lab half of its course.
     *
     * Node's rule, verbatim (`CourseDetailSubpage.tsx:173`): the type cell says "lab", or the slot
     * starts with an L. That is deliberately weaker than [VtopCourseCode.componentOf] - it does not
     * need to tell an *embedded* lab from a *lab-only* course, because both belong on the lab side
     * of the card; only whether a course has a counterpart matters, and that is decided when the
     * two rows are read back together.
     *
     * Public because attendance has to make the same call from the other end: the timetable row and
     * the attendance row for one half are matched on it, and matching them on the base code alone
     * pairs both timetable rows with the same attendance row and loses the lab's numbers.
     */
    fun isLabRow(courseType: String?, slot: String?): Boolean =
        EmbeddedLinkCode.isLabRow(courseType, slot)

    /**
     * The store key for one half of a course.
     *
     * An embedded course is two rows - `BACSE105` and `BACSE105(L)` - and the lab half needs its
     * own key because [com.amazecc.app.shared.state.SemesterData.courses] is a map. Every reader
     * already folds the two back together: `VtopCourseCode.base` strips the marker,
     * `buildCourseGroups` groups on the stripped code, and `findCourseGroup` matches on it.
     *
     * This is the arrangement Node uses, in a map keyed by base code with `theory`/`lab` inside
     * each entry (`CourseDetailSubpage.tsx:174-186`). The halves are never summed there, which is
     * why `CoursePredictorHero` can print `{theoryCredits}T + {labCredits}L` and why
     * `isEmbedded = theory && lab` is meaningful.
     */
    internal fun halfKey(base: String, isLab: Boolean): String =
        if (isLab && base.isNotEmpty()) "$base$L_MARKER" else base

    /** True for a key produced by [halfKey] for the lab half. */
    internal fun isLabKey(key: String): Boolean =
        key.endsWith(L_MARKER) && key.length > L_MARKER.length

    private const val L_MARKER = "(L)"

    /**
     * Combines two reports of the *same* half.
     *
     * Class counts add because they are the same classes counted twice - two endpoints reporting
     * one course. This is not the same operation as combining an embedded pair, which is why that
     * no longer happens here: an ETH half and an ELA half are different classes, and folding them
     * into one row is what erased the lab.
     */
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
        val existing = academic.semesters[semesterId]
        val next = transform(existing ?: SemesterData(semesterId = semesterId))
        // A no-op sync used to return a fresh copy anyway, so every write rebuilt the academic
        // tree and recomposed every screen watching it. Returning the original instance when the
        // semester is unchanged makes the function observably idempotent.
        if (existing != null && next == existing) return academic
        return academic.copy(semesters = academic.semesters + (semesterId to next))
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
            sem.courses.keys.any { canonicalHalfKey(it) != it }
        }
        if (!needsWork) return snapshot

        var changed = false
        val semesters = academic.semesters.mapValues { (_, sem) ->
            if (sem.courses.isEmpty()) return@mapValues sem
            val courses = sem.courses.toMutableMap()

            for (oldKey in courses.keys.toList()) {
                val newKey = canonicalHalfKey(oldKey)
                if (newKey.isEmpty() || newKey == oldKey) continue
                // `changed` used to be set only by the removeIf() below, but by the time that runs
                // the re-key loop has already collapsed the map, so it found nothing to remove and
                // the function returned the *original* snapshot - the migration was computed and
                // then thrown away, leaving every legacy row orphaned.
                changed = true
                val course = courses.remove(oldKey) ?: continue
                // Two rows of one course stay two rows, keyed theory-then-lab. They used to be
                // folded into a single summed row here, which is the migration that lost the lab.
                val target = courses[newKey]
                courses[newKey] = if (target == null) {
                    course.copy(courseCode = newKey)
                } else {
                    // Keep whichever row knows more; never average or sum their numbers.
                    target.copy(
                        courseTitle = target.courseTitle.ifBlank { course.courseTitle },
                        courseType = target.courseType.ifBlank { course.courseType },
                        faculty = target.faculty?.takeIf { it.isNotBlank() } ?: course.faculty,
                        venue = target.venue ?: course.venue,
                        category = target.category ?: course.category,
                        classId = target.classId ?: course.classId,
                        courseSystem = target.courseSystem ?: course.courseSystem,
                        credits = target.credits ?: course.credits,
                        slots = (target.slots + course.slots).distinct(),
                        attendance = mergeAttendance(target.attendance, course.attendance),
                        marks = target.marks ?: course.marks,
                        grade = target.grade ?: course.grade
                    )
                }
            }
            val before = courses.size
            courses.keys.removeIf { canonicalHalfKey(it) != it }
            if (courses.size != before) changed = true
            sem.copy(courses = courses)
        }
        if (!changed) return snapshot
        return snapshot.copy(academic = academic.copy(semesters = semesters))
    }

    /**
     * The one canonical store key for a course or one of its halves.
     *
     * Normalises every spelling the app has ever written - `BACSE106L`, `BACSE106 (L)`,
     * `BACSE106(T)` - onto either the bare code or the bare code plus `(L)`, so a lab half is
     * never written under a key a reader cannot pair it with.
     */
    internal fun canonicalHalfKey(raw: String?): String {
        // One canonical rule: EmbeddedLinkCode strips (T)/(L)/-T/-L and classifies the half from
        // the code suffix or the course type. Only the lab marker survives; a "(T)" marker is
        // redundant because the bare key already is the theory half.
        return EmbeddedLinkCode.halfKey(raw)
    }

    /**
     * The linking step of the ingestion path: fold each semester's theory and lab rows of one
     * base code into a single logical course with combined credits, slots, attendance and marks.
     *
     * Additive by design - the `upsert*` functions above still keep one key per half so a module
     * that synced before this step can be re-linked without being re-fetched. Call this once
     * after a sync (or on a legacy snapshot) and every downstream reader sees one course.
     * Pure and idempotent.
     */
    fun linkEmbeddedPairs(academic: AcademicData): AcademicData = academic.copy(
        semesters = academic.semesters.mapValues { (_, sem) ->
            if (sem.courses.isEmpty()) sem
            else sem.copy(courses = EmbeddedLinkMerge.mergeStoredCourses(sem.courses))
        }
    )

    fun upsertAttendance(academic: AcademicData, semesterId: String, res: AttendanceRes?): AcademicData {
        val cleaned = AppSanitizers.sanitizeAttendance(res) ?: return academic
        val items = cleaned.attendance.orEmpty()
        return updateSemester(academic, semesterId) { sem ->
            val courses = sem.courses.mapValues { (_, c) -> c.copy(attendance = null) }.toMutableMap()
            // An embedded course arrives as two rows and each keeps its own key, its own slot and
            // its own class counts. Summing them here is what made the lab half unrepresentable:
            // once 24/27 and 18/20 have become 42/47 there is nothing left to pair at read time.
            for (item in items) {
                val base = VtopCourseCode.base(item.courseCode)
                if (base.isEmpty()) continue
                val key = halfKey(base, isLabRow(item.courseType, item.slotName))
                val existing = courses[key]
                val incoming = StoredAttendance(
                    attendedClasses = item.attendedClasses,
                    totalClasses = item.totalClasses,
                    attendancePercentage = item.attendancePercentage,
                    logs = item.logs
                )
                // Two rows can share a half (the same course reported by attendance and by the
                // detail page); summing those is correct, unlike summing across halves.
                val attendance = mergeAttendance(existing?.attendance, incoming)

                courses[key] = (existing ?: StoredCourse(courseCode = key)).copy(
                    courseCode = key,
                    courseTitle = existing?.courseTitle?.takeIf { it.isNotBlank() }
                        ?: item.courseTitle.ifBlank { base },
                    courseType = existing?.courseType?.takeIf { it.isNotBlank() }
                        ?: item.courseType.ifBlank { VtopCourseCode.componentOf(null, item.slotName).label },
                    category = existing?.category ?: item.category,
                    credits = item.credits ?: existing?.credits,
                    slots = (existing?.slots.orEmpty() + slotTokens(item.slotName)).distinct(),
                    venue = existing?.venue ?: item.slotVenue,
                    faculty = existing?.faculty?.takeIf { it.isNotBlank() }
                        ?: item.faculty.ifBlank { null },
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

            // Each row keeps its own key. A course whose lab half has no marks row this term keeps
            // the lab half that attendance and timetable put there - marks fills a half in, it does
            // not decide which halves exist.
            for (item in cleaned.courses) {
                val base = VtopCourseCode.base(item.courseCode)
                if (base.isEmpty()) continue
                val key = halfKey(base, isLabRow(item.courseType, item.slot))
                val existing = courses[key]
                courses[key] = item.toStoredCourse(key, existing).copy(
                    credits = creditsOf(item.credits)?.let(::trimNumber) ?: existing?.credits
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
        courseTitle = courseTitle.ifBlank { existing?.courseTitle.orEmpty() },
        courseType = courseType.ifBlank { existing?.courseType.orEmpty() },
        courseSystem = courseSystem.ifBlank { existing?.courseSystem.orEmpty() },
        faculty = faculty.ifBlank { existing?.faculty.orEmpty() },
        // Marks contributes slots to a half, never replaces them: attendance and timetable have
        // usually reported this course's slot first and are the authoritative source for it.
        slots = (existing?.slots.orEmpty() + slotTokens(slot)).distinct(),
        credits = credits ?: existing?.credits,
        classId = classNbr.ifBlank { existing?.classId } ?: existing?.classId,
        marks = toStoredMarks()
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
                val base = VtopCourseCode.base(g.courseCode)
                if (base.isEmpty()) continue
                // A grade is reported once per course, not once per half. When the row does carry
                // a component it belongs on that half; otherwise it lands on the theory half, which
                // is where `CourseGroup.grade` reads it from.
                val key = if (isLabRow(g.courseType, null)) halfKey(base, true) else base
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
        cleaned.allSemesters.forEach { (semId, semResult) ->
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
                val base = VtopCourseCode.base(info.courseCode ?: continue)
                if (base.isEmpty()) continue
                // Same rule as the other two upserts: an embedded course's ETH and ELA rows are
                // two halves, so the slot belongs to the half it was scheduled for.
                val key = halfKey(base, isLabRow(info.category, info.slotVenue))
                val (slotPart, venue) = AcademicDerivers.splitSlotVenue(info.slotVenue)
                val slotCodes = slotPart?.split("+").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                    // VTOP sends LTPJC space separated with a trailing decimal - e.g.
                    // "0 0 4 0 2.0" - and the 5th token is the credit. This was split on "-",
                    // which never matches real markup, so the timetable never contributed credits
                    // at all; and the guard rejected "2.0" because it is not an Int. Verified
                    // against timetable-grid__CH20262701.html.
                    val ltpjcCredits = info.LTPJC?.split(Regex("\\s+"))
                        ?.getOrNull(4)?.trim()
                        ?.takeIf { it.isNotBlank() && it.toDoubleOrNull() != null }
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
