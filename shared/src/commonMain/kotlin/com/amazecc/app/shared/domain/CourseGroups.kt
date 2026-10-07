package com.amazecc.app.shared.domain

import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.vtop.VtopCourseCode

/**
 * The course-grouping projection the academic spine renders from.
 *
 * `CourseDashboard` and `CourseDetailScreen` both show one card per course across every semester,
 * pairing the halves of an embedded theory/lab pair. They used to build that card themselves,
 * out of `StoredCourse`, through the `toAttendanceItem()` / `toMarksCourseItem()` / `toGradeItem()`
 * shims - so the two screens could disagree about the same course, and neither could be tested
 * without a store.
 *
 * The types here are deliberately field-compatible with the transport DTOs they replace, so a
 * screen converts by swapping a type rather than relearning its layout. What changes is the
 * direction of the dependency: these are built from a [DomainSnapshot], not read out of the store.
 *
 * See `docs/sep-30-2026/screen-transplant-plan.md` 6 (Wave 1).
 */

/**
 * One course's attendance half, flattened to the facts a card renders.
 *
 * Replaces the transport `AttendanceItem`. Attendance is a property of the course rather than a
 * parallel list, so "this course's percentage" and "this course's credits" cannot come from two
 * different places.
 */
data class CourseAttendance(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val slotName: String,
    val slotVenue: String?,
    val faculty: String,
    val credits: String?,
    val category: String?,
    val attendedClasses: Int,
    val totalClasses: Int,
    /** VTOP's pre-rendered percentage text ("89.3"), kept verbatim so we never disagree with the page. */
    val attendancePercentage: String,
    val logs: List<AttendanceDay>,
)

/**
 * One course's marks half. Replaces the transport `MarksCourseItem`.
 *
 * [totalMark] / [maxMark] are the credit-weighted pair the ingest merge computed for an embedded
 * course - never a plain sum of the halves, which would report out of 200 against a max of 100.
 */
data class CourseMarks(
    val classNbr: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val courseSystem: String,
    val faculty: String,
    val slot: String,
    val credits: String?,
    val component: String?,
    val totalMark: Double?,
    val maxMark: Double?,
    val assessments: List<Assessment>,
)

/**
 * A course's letter grade. Replaces the transport `GradeItem`.
 *
 * [details] and [range] are the domain shapes (`GradeComponent`, `GradeBands`) rather than the
 * DTOs they were built from - the ingestor already owns that translation, so a screen reading
 * them here never sees `GradeBreakdown` or the uppercase `GradeRange.S` band names.
 */
data class CourseGrade(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val grandTotal: String,
    val grade: String,
    val details: List<GradeComponent>?,
    val range: GradeBands?,
)

/**
 * One card: a base course code with its theory and lab halves plus the grade a past semester holds.
 *
 * [theory] and [theoryAtt] are two views of the *same* stored course (its marks and its
 * attendance); [lab] and [labAtt] likewise. They stay separate fields because that is how the
 * two screens already read them, and merging them is a structural change worth doing on its own
 * once both screens bind here.
 *
 * A group is "embedded" exactly when both halves are present.
 */
data class CourseGroup(
    val courseCode: String,
    val courseTitle: String,
    val semesterSubId: String,
    val semesterName: String,
    val theory: CourseMarks? = null,
    val lab: CourseMarks? = null,
    val theoryAtt: CourseAttendance? = null,
    val labAtt: CourseAttendance? = null,
    /** Null for the semester the user is currently viewing, which shows marks instead. */
    val grade: CourseGrade? = null,
) {
    /** True when a theory/lab pair sits behind this card rather than a single component. */
    val isEmbedded: Boolean
        get() = theory != null && lab != null
}

private val TRAILING_COMPONENT = Regex("\\([LPT]\\)$")

private fun Course.isLabComponent(): Boolean = AcademicDerivers.isLabCourse(code, type, slots)

/**
 * The field mapping every [CourseAttendance] is built from.
 *
 * Internal rather than private because `Projections.currentSemesterAttendance` hands out the same
 * type one semester at a time; a second copy of this mapping is how a card and a log disagree.
 */
internal fun Course.asAttendance(): CourseAttendance {
    val att = attendance
    return CourseAttendance(
        courseCode = code,
        courseTitle = title,
        courseType = type,
        slotName = slots.joinToString("+"),
        slotVenue = venue,
        faculty = faculty ?: "",
        credits = credits,
        category = category,
        attendedClasses = att?.attended ?: 0,
        totalClasses = att?.total ?: 0,
        attendancePercentage = att?.percentage ?: "",
        logs = att?.logs.orEmpty(),
    )
}

private fun Course.asMarks(): CourseMarks {
    val m = marks
    return CourseMarks(
        // The class number the timetable carries, not [Marks.classNbr]: the card renders
        // "Class #NNNN" from this, and the shim it replaces read `classId` too.
        classNbr = classId ?: "",
        courseCode = code,
        courseTitle = title,
        courseType = type,
        courseSystem = courseSystem ?: "",
        faculty = faculty ?: "",
        slot = slots.joinToString("+"),
        credits = credits,
        component = AcademicDerivers.componentLabel(type),
        totalMark = m?.total,
        maxMark = m?.maxTotal,
        assessments = m?.assessments.orEmpty(),
    )
}

/**
 * Always returns a value: a course with no grade on file yields empty strings, which is what the
 * shims did. The screens branch on `group.grade != null` to mean "this semester recorded a grade",
 * and an empty [CourseGrade] still reads as a recorded-but-blank one, exactly as before.
 */
internal fun Course.asGrade(): CourseGrade {
    val g = grade
    return CourseGrade(
        courseCode = code,
        courseTitle = title,
        courseType = type,
        grandTotal = g?.grandTotal ?: "",
        grade = g?.letter ?: "",
        details = g?.details,
        range = g?.range,
    )
}

private fun semesterLabel(sem: Semester, semesterNames: Map<String, String>): String =
    sem.name ?: semesterNames[sem.id] ?: sem.id

/**
 * One [CourseGroup] per base course code across all semesters, pairing embedded theory/lab
 * components.
 *
 * Pure: reads only [snapshot] and the semester-name lookup the caller supplies. [selectedSemester]
 * is the semester the user has the filter on - it decides whether a group carries a grade (a
 * past semester) or leaves it null so the current semester shows marks instead.
 *
 * Grouping strips a trailing `(L)`/`(P)`/`(T)` marker so a legacy row keyed `BCSE101(L)` lands
 * with `BCSE101`; keys are already bare in a current snapshot, which costs nothing.
 */
internal fun buildCourseGroups(
    snapshot: DomainSnapshot,
    semesterNames: Map<String, String> = emptyMap(),
    selectedSemester: String = "All",
): List<CourseGroup> {
    val groups = mutableListOf<CourseGroup>()
    snapshot.academics.semesters.forEach { (semId, sem) ->
        val semName = semesterLabel(sem, semesterNames)
        val isCurrent = selectedSemester != "All" && semId == selectedSemester
        sem.courses.values
            .filter { it.code.isNotBlank() }
            .groupBy { it.code.replace(TRAILING_COMPONENT, "").trim() }
            .forEach { (baseCode, courses) ->
                val theory = courses.firstOrNull { !it.isLabComponent() }
                val lab = courses.firstOrNull { it.isLabComponent() }
                groups.add(
                    CourseGroup(
                        courseCode = baseCode,
                        courseTitle = theory?.title ?: lab?.title ?: baseCode,
                        semesterSubId = semId,
                        semesterName = semName,
                        theory = theory?.asMarks(),
                        lab = lab?.asMarks(),
                        theoryAtt = theory?.asAttendance(),
                        labAtt = lab?.asAttendance(),
                        // Prioritise marks for the current semester, grades for previous ones.
                        grade = if (isCurrent) null else theory?.asGrade() ?: lab?.asGrade(),
                    )
                )
            }
    }
    return groups
}

/**
 * Resolves the [CourseGroup] for a course code, preferring [semesterId], falling back to any
 * semester.
 *
 * The fallback matters: a course detail page can be opened with a stale semester after a sync, and
 * showing an empty card is worse than showing it from the semester that still holds it.
 */
internal fun findCourseGroup(
    courseCode: String,
    semesterId: String,
    snapshot: DomainSnapshot,
    semesterNames: Map<String, String> = emptyMap(),
    selectedSemester: String = "All",
): CourseGroup? {
    // Keys are bare course codes now; normalise anyway so a stale suffixed key still resolves.
    val cleanCode = VtopCourseCode.base(courseCode).ifEmpty { courseCode.trim() }

    fun courseToGroup(semId: String, sem: Semester): CourseGroup? {
        val matches = sem.courses.values.filter { VtopCourseCode.base(it.code) == cleanCode }
        if (matches.isEmpty()) return null
        // An embedded ETH/ELA pair is merged at the store, so there is normally one course.
        // Legacy snapshots may still hold two rows; fold them the same way.
        val main = matches.firstOrNull { !it.isLabComponent() } ?: matches.first()
        val labLegacy = matches.firstOrNull { it.isLabComponent() && it !== main }
        val isCurrent = selectedSemester != "All" && semId == selectedSemester

        return CourseGroup(
            courseCode = cleanCode,
            courseTitle = main.title.ifBlank { cleanCode },
            semesterSubId = semId,
            semesterName = semesterLabel(sem, semesterNames),
            theory = main.asMarks(),
            lab = labLegacy?.asMarks(),
            theoryAtt = main.asAttendance(),
            labAtt = labLegacy?.asAttendance(),
            // Prioritise marks for the current semester, grades for previous ones.
            grade = if (isCurrent) null else main.asGrade(),
        )
    }

    snapshot.academics.semesters[semesterId]?.let { sem -> courseToGroup(semesterId, sem)?.let { return it } }
    snapshot.academics.semesters.forEach { (semId, sem) -> courseToGroup(semId, sem)?.let { return it } }
    return null
}

/**
 * Every semester's grade for one course code, newest first - what the grade-history tab lists.
 *
 * Replaces a loop that walked `academic.semesters` and called the `toGradeItem()` shim per course,
 * which was the last direct `StoredCourse` read inside `CourseDetailScreen`.
 *
 * Entries are always present for a matching course even when the grade is blank, matching the
 * shim; the trend line reads `grandTotal` and treats a blank as missing.
 */
internal fun gradeHistory(
    snapshot: DomainSnapshot,
    courseCode: String,
): List<Pair<String, CourseGrade?>> {
    val cleanCode = courseCode.replace(TRAILING_COMPONENT, "").trim()
    val items = mutableListOf<Pair<String, CourseGrade?>>()
    snapshot.academics.semesters.forEach { (semId, sem) ->
        sem.courses.values.forEach { course ->
            if (course.code.replace(TRAILING_COMPONENT, "").trim() == cleanCode) {
                items.add(semId to course.asGrade())
            }
        }
    }
    return items.sortedByDescending { it.first }
}
