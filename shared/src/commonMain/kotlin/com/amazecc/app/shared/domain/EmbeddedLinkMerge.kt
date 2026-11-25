package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.state.StoredCourse
import com.amazecc.app.shared.state.StoredGrade
import com.amazecc.app.shared.state.StoredMarks
import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * One stored half of a linked course, retained so a screen that needs the raw theory or lab
 * numbers (the course detail card, the predictor's per-half regimen) can still read them.
 * The default course is the merged one; this is the escape hatch, not the primary view.
 */
@Serializable
data class EmbeddedCourseHalf(
    val code: String = "",
    val title: String = "",
    val type: String = "",
    /** `"T"`, `"L"`, or `""` when the row carried no component signal. */
    val half: String = "",
    val credits: String? = null,
    val slots: List<String> = emptyList(),
    val classId: String? = null,
    val attendance: Attendance? = null,
    val marks: Marks? = null,
    val grade: Grade? = null,
)

/**
 * Collapses the two spellings of one course into one logical course.
 *
 * Ported from `buildEnrolledCourses` in the web app's
 * `src/components/custom/exams/marks-predictor/useCoursePredictionScope.ts:290-404`:
 *
 * - group by the canonical base code (a `(T)` API row and a `type`-only scraper row share it);
 * - a theory half and a lab half of that code are folded into ONE row with summed slots,
 *   summed credits, summed attendance and credit-weighted marks — the same credit blend the
 *   web app uses for a published embedded grade (`src/lib/gradeHistory.ts:581-600`);
 * - two reports of the SAME half are de-duplicated by taking the richer row, never summed,
 *   which is what stops `(T)` and bare-code rows of one half double-counting in a summary;
 * - the theory title names the course; the raw halves stay in [Course.halves].
 *
 * Pure and idempotent: merging an already-merged snapshot is a no-op.
 */
object EmbeddedLinkMerge {

    /** The canonical code for a course row, using its code and falling back to its title. */
    fun canonicalCode(course: Course): String =
        EmbeddedLinkCode.baseCode(course.code.ifBlank { course.title })

    /**
     * The consumer-facing default: every semester holds one course per base code.
     * Already-merged snapshots are returned unchanged (no copy, no recomposition churn).
     */
    fun mergeSnapshot(snapshot: DomainSnapshot): DomainSnapshot {
        val curriculumCredits = curriculumCredits(snapshot.academics.curriculum)
        var changed = false
        val semesters = snapshot.academics.semesters.mapValues { (_, sem) ->
            val before = sem.courses
            val after = mergeCourses(before.values.toList(), curriculumCredits).associateBy { it.code }
            if (after != before) changed = true
            if (after == before) sem else sem.copy(courses = after)
        }
        return if (changed) snapshot.copy(academics = snapshot.academics.copy(semesters = semesters)) else snapshot
    }

    /** Curriculum is authoritative for credits: it is the official programme structure. */
    fun curriculumCredits(curriculum: com.amazecc.app.shared.model.CurriculumRes?): Map<String, String> {
        if (curriculum == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (detail in curriculum.details) {
            for (basket in detail.baskets) {
                for (item in basket.items) {
                    if (item.credits > 0) out[item.code] = item.credits.toString()
                }
            }
        }
        return out
    }

    /** Merge one semester's rows. Single-row groups keep their original code (identity only). */
    fun mergeCourses(
        courses: List<Course>,
        curriculumCredits: Map<String, String> = emptyMap()
    ): List<Course> {
        if (courses.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<Course>>()
        for (course in courses) {
            val key = canonicalCode(course).ifEmpty { course.code }
            groups.getOrPut(key) { mutableListOf() }.add(course)
        }
        return groups.map { (base, rows) -> mergeGroup(base, rows, curriculumCredits) }.sortedBy { it.code }
    }

    /** Legacy-ingestion variant: same rule over the persisted `StoredCourse` map. */
    fun mergeStoredCourses(
        courses: Map<String, StoredCourse>,
        curriculumCredits: Map<String, String> = emptyMap()
    ): Map<String, StoredCourse> =
        mergeCourses(courses.values.map { it.toCourse() }, curriculumCredits)
            .associateBy { it.code }
            .mapValues { (_, course) -> course.toStored() }

    private fun mergeGroup(
        base: String,
        rows: List<Course>,
        curriculumCredits: Map<String, String> = emptyMap()
    ): Course {
        if (rows.size == 1) {
            val only = rows.single()
            val resolvedCredits = only.credits?.takeIf { it.isNotBlank() } ?: curriculumCredits[base]
            return only.copy(credits = resolvedCredits, linkedBaseCode = base)
        }

        val theoryRows = rows.filter { halfOf(it) != EmbeddedLinkCode.Half.LAB }
        val labRows = rows.filter { halfOf(it) == EmbeddedLinkCode.Half.LAB }
        val theory = theoryRows.maxByOrNull(::richness)
        val lab = labRows.maxByOrNull(::richness)
        val primary = theory ?: lab ?: rows.first()
        val both = theory != null && lab != null

        val officialTotal = curriculumCredits[base]?.toDoubleOrNull()
        val tVal = creditValue(theory?.credits)
        val lVal = creditValue(lab?.credits)

        val (courseCredits, theoryCredits, labCredits) = when {
            both && officialTotal != null && officialTotal > 0.0 -> {
                val l = lVal ?: 1.0
                val t = (officialTotal - l).coerceAtLeast(1.0)
                Triple(trimDouble(officialTotal), trimDouble(t), trimDouble(l))
            }
            both && tVal != null && lVal != null -> {
                when {
                    // Theory row in VTOP holding 4.0 with a 1.0 lab is 3+1 = 4 total (e.g. BAECE203, BACSE105, BACSE106)
                    tVal == 4.0 && lVal == 1.0 -> Triple("4", "3", "1")
                    // Theory row in VTOP holding 5.0 with a 1.0 lab is 4+1 = 5 total
                    tVal == 5.0 && lVal == 1.0 -> Triple("5", "4", "1")
                    // Otherwise, each half already holds its own component credit (e.g. 3+1, 2+2, 1+1, 3+2)
                    else -> Triple(trimDouble(tVal + lVal), trimDouble(tVal), trimDouble(lVal))
                }
            }
            both -> {
                val c = combinedCredits(theory?.credits, lab?.credits)
                Triple(c, theory?.credits, lab?.credits)
            }
            else -> {
                val c = theory?.credits?.takeIf { it.isNotBlank() }
                    ?: lab?.credits?.takeIf { it.isNotBlank() }
                    ?: primary.credits?.takeIf { it.isNotBlank() }
                    ?: curriculumCredits[base]
                Triple(c, theory?.credits, lab?.credits)
            }
        }

        val resolvedHalves = if (both) {
            rows.map { row ->
                val half = asHalf(row)
                if (halfOf(row) == EmbeddedLinkCode.Half.LAB) {
                    half.copy(credits = labCredits ?: half.credits)
                } else {
                    half.copy(credits = theoryCredits ?: half.credits)
                }
            }
        } else {
            rows.map(::asHalf)
        }

        return primary.copy(
            code = base,
            title = EmbeddedLinkCode.displayTitle(theory?.title, lab?.title, base),
            type = if (both) "Embedded Theory and Lab" else primary.type,
            credits = courseCredits,
            classId = theory?.classId ?: lab?.classId ?: primary.classId,
            slots = (theory?.slots.orEmpty() + lab?.slots.orEmpty()).distinct(),
            venue = theory?.venue ?: lab?.venue ?: primary.venue,
            faculty = theory?.faculty?.takeIf { it.isNotBlank() }
                ?: lab?.faculty?.takeIf { it.isNotBlank() }
                ?: primary.faculty,
            courseSystem = theory?.courseSystem ?: primary.courseSystem ?: lab?.courseSystem,
            attendance = if (both) combinedAttendance(theory!!.attendance, lab!!.attendance)
            else theory?.attendance ?: lab?.attendance,
            marks = combinedMarks(theory, lab, theoryCredits, labCredits),
            grade = theory?.grade ?: lab?.grade ?: primary.grade,
            baskets = (theory?.baskets.orEmpty() + lab?.baskets.orEmpty()).distinct(),
            halves = resolvedHalves,
            isEmbeddedMerged = both,
            linkedBaseCode = base,
        )
    }

    private fun halfOf(course: Course): EmbeddedLinkCode.Half? = EmbeddedLinkCode.halfOf(
        rawCode = course.code,
        typeHint = course.type,
        slot = course.slots.joinToString("+"),
        title = course.title,
    )

    /** Prefer the report that actually carries data; stable on ties (first row wins). */
    private fun richness(course: Course): Int =
        (course.attendance?.total ?: 0) + (course.marks?.assessments?.size ?: 0) * 100

    private fun asHalf(course: Course): EmbeddedCourseHalf = EmbeddedCourseHalf(
        code = course.code,
        title = course.title,
        type = course.type,
        half = halfOf(course)?.marker.orEmpty(),
        credits = course.credits,
        slots = course.slots,
        classId = course.classId,
        attendance = course.attendance,
        marks = course.marks,
        grade = course.grade,
    )

    private fun creditValue(raw: String?): Double? =
        raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

    private fun combinedCredits(theory: String?, lab: String?): String? {
        val t = creditValue(theory)
        val l = creditValue(lab)
        return when {
            t != null && l != null -> trimDouble(t + l)
            t != null -> trimDouble(t)
            l != null -> trimDouble(l)
            else -> theory?.takeIf { it.isNotBlank() } ?: lab?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Different classes, so their counts add — but they are still TWO attendance entries, not one
     * percentage. D-189: when both halves exist the merged row keeps the raw sums for callers that
     * need counts (summaries, bunk maths) while leaving the printed percentage honestly blank. Each
     * half keeps its own printed figure in [Course.halves]. A lone half is returned verbatim.
     */
    private fun combinedAttendance(theory: Attendance?, lab: Attendance?): Attendance? {
        if (theory == null) return lab
        if (lab == null) return theory
        return Attendance(
            attended = theory.attended + lab.attended,
            total = theory.total + lab.total,
            percentage = "",
            logs = (theory.logs + lab.logs).distinctBy { it.date to it.status },
        )
    }

    /**
     * Credit-weighted blend of the two halves — `gradeHistory.ts:415-429`. Each half contributes
     * its OWN credits through the one canonical blend in [EmbeddedCourseSemantics.creditWeighted].
     * A half without usable credits makes the blend null — never an invented 50/50 mean. When only
     * one half published marks, that half's own number is used verbatim.
     */
    private fun combinedMarks(
        theory: Course?,
        lab: Course?,
        theoryCredits: String? = null,
        labCredits: String? = null
    ): Marks? {
        val tm = theory?.marks
        val lm = lab?.marks
        if (tm == null && lm == null) return null

        val tTotal = tm?.total
        val lTotal = lm?.total
        val total = when {
            tTotal == null && lTotal == null -> null
            tTotal == null -> lTotal
            lTotal == null -> tTotal
            else -> EmbeddedCourseSemantics.creditWeighted(
                listOf(
                    EmbeddedEntry(
                        code = theory?.code.orEmpty(),
                        half = EmbeddedHalf.THEORY,
                        credits = creditValue(theoryCredits) ?: creditValue(theory?.credits) ?: 0.0,
                        earnedMarks = tTotal,
                    ),
                    EmbeddedEntry(
                        code = lab?.code.orEmpty(),
                        half = EmbeddedHalf.LAB,
                        credits = creditValue(labCredits) ?: creditValue(lab?.credits) ?: 0.0,
                        earnedMarks = lTotal,
                    ),
                )
            ) { it.earnedMarks }?.let(::round2)
        }
        val maxTotal = listOfNotNull(tm?.maxTotal, lm?.maxTotal).maxOrNull()

        return Marks(
            classNbr = tm?.classNbr ?: lm?.classNbr,
            assessments = (tm?.assessments.orEmpty().map { a ->
                if (a.component.isNullOrBlank()) a.copy(component = "ETH") else a
            } + lm?.assessments.orEmpty().map { a ->
                if (a.component.isNullOrBlank()) a.copy(component = "ELA") else a
            }),
            total = total,
            maxTotal = maxTotal,
            mergedFrom = if (tm != null && lm != null) "Embedded Theory + Embedded Lab"
            else tm?.mergedFrom ?: lm?.mergedFrom,
        )
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0

    private fun trimDouble(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    // ── domain ↔ legacy stored conversion (one mapping, used both ways) ─────────

    private fun StoredCourse.toCourse(): Course = Course(
        code = courseCode,
        title = courseTitle,
        type = courseType,
        category = category,
        credits = credits,
        classId = classId,
        slots = slots,
        venue = venue,
        faculty = faculty,
        courseSystem = courseSystem,
        attendance = attendance?.let { a ->
            Attendance(
                attended = a.attendedClasses,
                total = a.totalClasses,
                percentage = a.attendancePercentage,
                logs = a.logs.map { l -> AttendanceDay(l.date, l.status) },
            )
        },
        marks = marks?.let { m ->
            Marks(
                classNbr = m.classNbr,
                assessments = m.assessments.map { a ->
                    Assessment(
                        name = a.title,
                        score = a.scoredMark,
                        maxMark = a.maxMark,
                        weightage = a.weightageMark,
                        weightagePercent = a.weightagePercent,
                        status = a.status,
                        component = a.component,
                    )
                },
                total = m.totalMark,
                maxTotal = m.maxMark,
                mergedFrom = m.mergedFrom,
            )
        },
        grade = grade?.let { g ->
            Grade(
                letter = g.grade,
                grandTotal = g.grandTotal,
                details = g.details.orEmpty().map { d ->
                    GradeComponent(
                        name = d.component,
                        score = d.scoredMark,
                        maxMark = d.maxMark,
                        weightagePercent = d.weightagePercent,
                        status = d.status,
                        weightage = d.weightageMark,
                    )
                },
                range = g.range?.let { r -> GradeBands(r.S, r.A, r.B, r.C, r.D, r.E, r.F) },
            )
        },
    )

    private fun Course.toStored(): StoredCourse = StoredCourse(
        courseCode = code,
        courseTitle = title,
        courseType = type,
        category = category,
        credits = credits,
        classId = classId,
        slots = slots,
        venue = venue,
        faculty = faculty,
        courseSystem = courseSystem,
        attendance = attendance?.let { a ->
            com.amazecc.app.shared.state.StoredAttendance(
                attendedClasses = a.attended,
                totalClasses = a.total,
                attendancePercentage = a.percentage,
                logs = a.logs.map { l -> AttendanceLog(l.date, l.status) },
            )
        },
        marks = marks?.let { m ->
            StoredMarks(
                classNbr = m.classNbr,
                assessments = m.assessments.map { a ->
                    AssessmentItem(
                        title = a.name,
                        maxMark = a.maxMark,
                        weightagePercent = a.weightagePercent,
                        status = a.status,
                        scoredMark = a.score,
                        weightageMark = a.weightage,
                        component = a.component,
                    )
                },
                totalMark = m.total,
                maxMark = m.maxTotal,
                mergedFrom = m.mergedFrom,
            )
        },
        grade = grade?.let { g ->
            StoredGrade(
                grandTotal = g.grandTotal,
                grade = g.letter,
                details = g.details.map { d ->
                    GradeBreakdown(
                        component = d.name,
                        maxMark = d.maxMark,
                        weightagePercent = d.weightagePercent,
                        status = d.status,
                        scoredMark = d.score,
                        weightageMark = d.weightage,
                    )
                }.takeIf { it.isNotEmpty() },
                range = g.range?.let { r -> GradeRange(r.s, r.a, r.b, r.c, r.d, r.e, r.f) },
            )
        },
    )
}
