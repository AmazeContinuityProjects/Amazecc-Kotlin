package com.amazecc.app.shared.domain

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Pure interpretation: the one place a derived number is calculated.
 *
 * Two kinds of function live here. The `snapshot`-taking ones turn a [DomainSnapshot] into a
 * screen view model; the primitive-level ones in "the app's one formula per fact" are the
 * canonical implementations of facts that used to be written out inline wherever they were
 * needed — attendance percentage, OD hours, and so on.
 *
 * Screens take a view model, never a `DomainSnapshot` and never a transport DTO. Legacy call
 * sites in `state/` and `utils/` delegate to the primitive-level formulae rather than
 * re-deriving them, so two surfaces cannot disagree about the same figure — which is exactly
 * what happened when the web app wrote its attendance percentage five times.
 *
 * Every function here is total and side-effect free, which is what makes them cheap to test -
 * and `:shared:jvmTest` can now actually run, so they are.
 */
object Projections {

    /** Which semester to show when the caller has no preference. */
    fun activeSemester(snapshot: DomainSnapshot): Semester? {
        val academics = snapshot.academics
        return academics.semesters[academics.selectedSemesterId]
            ?: academics.semesters.values.maxByOrNull { it.id }
    }

    fun semester(snapshot: DomainSnapshot, semesterId: String): Semester? =
        snapshot.academics.semesters[semesterId]

    // ── academics ────────────────────────────────────────────────────────────

    data class CourseRow(
        val code: String,
        val title: String,
        val type: String,
        val credits: String,
        val faculty: String,
        val slots: String,
        val venue: String,
        val attendance: String,
        val hasMarks: Boolean,
    )

    fun courses(snapshot: DomainSnapshot, semesterId: String): List<CourseRow> {
        val sem = semester(snapshot, semesterId) ?: return emptyList()
        return sem.courses.values
            .sortedBy { it.code }
            .map { c ->
                CourseRow(
                    code = c.code,
                    title = c.title,
                    type = c.type,
                    credits = c.credits.orEmpty(),
                    faculty = c.faculty.orEmpty(),
                    slots = c.slots.joinToString("+"),
                    venue = c.venue.orEmpty(),
                    attendance = c.attendance?.percentage.orEmpty(),
                    hasMarks = !c.marks?.assessments.isNullOrEmpty(),
                )
            }
    }

    // ── attendance ───────────────────────────────────────────────────────────

    data class AttendanceRow(
        val courseCode: String,
        val courseTitle: String,
        val courseType: String,
        val attended: Int,
        val total: Int,
        val percentage: String,
        val faculty: String,
        val isLab: Boolean,
    ) {
        /**
         * Parsed [percentage] when possible, for sorting and colour. VTOP sends it pre-rendered
         * ("89"), so this is best-effort - never the value shown to the user.
         */
        val percentValue: Double? = percentage.trim().toDoubleOrNull()
    }

    fun attendance(snapshot: DomainSnapshot, semesterId: String): List<AttendanceRow> {
        val sem = semester(snapshot, semesterId) ?: return emptyList()
        return sem.courses.values
            .filter { it.attendance != null }
            .sortedBy { it.code }
            .map { c ->
                val a = c.attendance!!
                AttendanceRow(
                    courseCode = c.code,
                    courseTitle = c.title,
                    courseType = c.type,
                    attended = a.attended,
                    total = a.total,
                    percentage = a.percentage,
                    faculty = c.faculty.orEmpty(),
                    isLab = c.type.isLabType,
                )
            }
    }

    /**
     * Every course's attendance in one named semester, or empty when that semester does not
     * exist. This is the semester the user has the filter on, which is a different question from
     * [currentSemesterAttendance].
     */
    fun semesterAttendance(snapshot: DomainSnapshot, semesterId: String): List<CourseAttendance> =
        semester(snapshot, semesterId)?.courses?.values?.map { it.asAttendance() }.orEmpty()

    /**
     * Every course's attendance in the semester attendance *reports* from: the one with the most
     * attendance-bearing courses, ties broken by semester id.
     *
     * This is what `CourseAttendanceScreen` renders - the predictor needs the whole semester to
     * know which days this course meets, and the log needs this course's own rows. It mirrors
     * `AcademicDerivers.resolveCurrentSemester` rather than [activeSemester], because "the
     * semester the user picked" and "the semester that actually has the data" were two different
     * answers, and this screen has always used the latter.
     *
     * [attendance] is a different view of the same data: sorted, filtered to attendance-bearing
     * courses, and without the slot or logs this screen reads.
     */
    fun currentSemesterAttendance(snapshot: DomainSnapshot): List<CourseAttendance> =
        reportingSemester(snapshot)?.let { semesterAttendance(snapshot, it.id) }.orEmpty()

    /**
     * The semester attendance reports from: most attendance-bearing courses, ties to the higher
     * semester id. Lifted out so [currentSemesterAttendance] and its tests read as one rule.
     */
    private fun reportingSemester(snapshot: DomainSnapshot): Semester? =
        snapshot.academics.semesters.values
            .filter { it.courses.values.any { c -> c.attendance != null } }
            .maxWithOrNull(
                compareBy<Semester> { it.courses.values.count { c -> c.attendance != null } }
                    .thenBy { it.id }
            )

    // ── the app's one formula per fact ───────────────────────────────────────

    enum class AttendanceStatus { SAFE, WARNING, CRITICAL, NOT_APPLICABLE }

    /**
     * The app's default attendance target, for callers that read only the figure.
     *
     * The real target is user-configurable and 85 rather than 75 for bus subscribers (see
     * `AppState.effectiveAttendanceTarget`), so this is **not** what [attendanceStatus] should
     * be judged against. It exists only because [AttendanceSummary.percentage] does not depend
     * on the target at all, and the alternative was a second percentage code path.
     */
    const val DEFAULT_ATTENDANCE_TARGET = 75f

    /**
     * A headline attendance figure.
     *
     * [percentage] is 0-100 and **unrounded** — every caller rounds on the way out if it wants
     * to, because a number rounded in two places drifts. [hasData] distinguishes "no class has
     * been held yet" from "attendance is 0", which the label renders as a dash rather than 0%.
     */
    data class AttendanceSummary(
        val percentage: Float,
        val attended: Int,
        val total: Int,
        val status: AttendanceStatus,
    ) {
        val hasData: Boolean get() = total > 0
    }

    /**
     * Below [targetPct] is critical; within five points of it is a warning.
     *
     * The five-point band is the one the web app and the bunk meter card use, so the headline
     * tile and the bunk meter never disagree about the same course.
     */
    fun attendanceStatus(percentage: Float, total: Int, targetPct: Float): AttendanceStatus = when {
        total <= 0 -> AttendanceStatus.NOT_APPLICABLE
        percentage >= targetPct + 5f -> AttendanceStatus.SAFE
        percentage >= targetPct -> AttendanceStatus.WARNING
        else -> AttendanceStatus.CRITICAL
    }

    /**
     * The app's one attendance-percentage formula.
     *
     * Sum VTOP's own attended/total counts across the enrolled courses, **unweighted**, and
     * divide. The unweighted sum is deliberate: a lab is worth two hours against the
     * *requirement* elsewhere in the app, but weighting it here would move the headline away
     * from the figure the institute prints, which is the number students reconcile against.
     *
     * Averaging the per-course percentages is not an acceptable substitute — courses with no
     * held classes would drag the mean down, and a 1-class course would count as much as a
     * 40-class one. The web app shipped that mistake in one of five copies; it must not return
     * as a sixth.
     */
    fun summariseAttendance(
        courses: List<Pair<Int, Int>>,
        targetPct: Float = DEFAULT_ATTENDANCE_TARGET,
    ): AttendanceSummary {
        var attended = 0
        var total = 0
        for ((att, tot) in courses) {
            attended += att
            total += tot
        }
        // Double arithmetic, narrowed to Float once at the end.
        val percentage = if (total > 0) (attended.toDouble() / total.toDouble() * 100.0).toFloat() else 0f
        return AttendanceSummary(percentage, attended, total, attendanceStatus(percentage, total, targetPct))
    }

    /** [summariseAttendance] over a snapshot's semester. */
    fun attendanceSummary(
        snapshot: DomainSnapshot,
        semesterId: String,
        targetPct: Float,
    ): AttendanceSummary = summariseAttendance(
        attendance(snapshot, semesterId).map { it.attended to it.total },
        targetPct,
    )

    /**
     * Credits actually earned: only courses with a posted grade count toward it.
     *
     * Takes `(credits, hasGrade)` pairs rather than a snapshot type so the two existing
     * call sites — both of which wrote these four lines out identically — can share it
     * without the interpretation layer taking a dependency on the legacy store.
     */
    fun creditsEarned(courses: List<Pair<String?, Boolean>>): Double =
        courses
            .filter { (_, hasGrade) -> hasGrade }
            .mapNotNull { (credits, _) -> credits?.trim()?.toDoubleOrNull() }
            .sum()

    /**
     * VTOP's own wording for a session that was on duty.
     *
     * Matched after trimming and lowercasing, because the payload has been seen with leading
     * whitespace. Anything that is not one of these three is not OD — a "partial od" or
     * "sectional holiday" string appearing here would silently inflate the counter.
     */
    fun isOdStatus(status: String): Boolean = when (status.trim().lowercase()) {
        "on duty", "od", "onduty" -> true
        else -> false
    }

    /**
     * Total on-duty hours, given each course's OD session count and whether it is a lab.
     *
     * A lab period is two hours, a theory period one. This is the OD Tracker screen counter,
     * which is why every other surface showing OD hours has to call it rather than recount.
     */
    fun odHours(odSessions: List<Pair<Int, Boolean>>): Int =
        odSessions.sumOf { (count, isLab) -> count * (if (isLab) 2 else 1) }

    /**
     * Classes still needed to reach [targetPct] — the "Need 3 more classes" half of the margin.
     *
     * The shortfall is divided by the share each further class contributes, so being five points
     * short at 100 classes costs far more than five misses. Straight from
     * `CourseDetailSubpage.tsx:781`. A lab is worth two hours against a one-hour target, so the
     * answer halves — the same convention [odHours] uses, and the caller supplies [isLab] from
     * the app's one lab test rather than re-deriving it here.
     *
     * Null when nothing has been held, when the target cannot be met from a 0-100% scale, or
     * when the target is already met — that last case belongs to [bunkableClasses], not here.
     */
    fun classesToTarget(
        attended: Int,
        total: Int,
        targetPct: Float,
        isLab: Boolean = false,
    ): Int? {
        if (total <= 0) return null
        val dec = targetPct / 100.0
        if (dec <= 0.0 || dec >= 1.0) return null
        val needed = ceil((dec * total - attended) / (1.0 - dec)).toInt()
        if (needed <= 0) return null
        return if (isLab) ceil(needed / 2.0).toInt() else needed
    }

    /**
     * Classes that can still be missed while staying at [targetPct] — the "N bunkable" half.
     *
     * `CourseDetailSubpage.tsx:787`. Note it is deliberately not the complement of
     * [classesToTarget]: reaching a target and keeping a safety margin are different questions,
     * and the web app computes them separately for that reason.
     *
     * Zero means the student is sitting exactly on the line, which the card words as "On edge"
     * rather than "0 bunkable". Null when nothing has been held. Lab sessions halve, as above.
     */
    fun bunkableClasses(
        attended: Int,
        total: Int,
        targetPct: Float,
        isLab: Boolean = false,
    ): Int? {
        if (total <= 0) return null
        val dec = targetPct / 100.0
        if (dec <= 0.0) return null
        val canMiss = floor(attended / dec - total).toInt()
        val value = if (isLab) floor(canMiss / 2.0).toInt() else canMiss
        return value.coerceAtLeast(0)
    }

    // ── marks ────────────────────────────────────────────────────────────────

    data class MarksRow(
        val courseCode: String,
        val courseTitle: String,
        val credits: String,
        val assessments: List<AssessmentRow>,
        val total: String,
        val maxTotal: String,
        val isMerged: Boolean,
        val mergedFrom: String?,
    )

    data class AssessmentRow(
        val name: String,
        val score: String,
        val maxMark: String,
        val weightage: String,
        val component: String?,
    )

    fun marks(snapshot: DomainSnapshot, semesterId: String): List<MarksRow> {
        val sem = semester(snapshot, semesterId) ?: return emptyList()
        return sem.courses.values
            .filter { it.marks != null && it.marks!!.assessments.isNotEmpty() }
            .sortedBy { it.code }
            .map { c ->
                val m = c.marks!!
                MarksRow(
                    courseCode = c.code,
                    courseTitle = c.title,
                    credits = c.credits.orEmpty(),
                    assessments = m.assessments.map {
                        AssessmentRow(
                            name = it.name,
                            score = it.score,
                            maxMark = it.maxMark,
                            weightage = it.weightage,
                            component = it.component,
                        )
                    },
                    total = m.total?.let { trimNumber(it) }.orEmpty(),
                    maxTotal = m.maxTotal?.let { trimNumber(it) }.orEmpty(),
                    isMerged = m.mergedFrom != null,
                    mergedFrom = m.mergedFrom,
                )
            }
    }

    // ── exams ────────────────────────────────────────────────────────────────

    data class ExamRow(
        val courseCode: String,
        val courseTitle: String,
        val date: String,
        val session: String,
        val time: String,
        val venue: String,
        val seat: String,
        val isLab: Boolean,
    )

    fun exams(snapshot: DomainSnapshot, semesterId: String): List<ExamRow> {
        val sem = semester(snapshot, semesterId) ?: return emptyList()
        val courseByCode = sem.courses
        return snapshot.schedule.exams
            .filter { it.semesterId == semesterId }
            .sortedBy { it.date }
            .map { e ->
                val type = courseByCode[e.courseCode]?.type.orEmpty()
                ExamRow(
                    courseCode = e.courseCode,
                    courseTitle = e.courseTitle,
                    date = e.date,
                    session = e.session,
                    time = e.reportingTime.ifBlank { e.time },
                    venue = e.venue,
                    seat = listOf(e.seatLocation, e.seatNo).filter { it.isNotBlank() }
                        .joinToString(" / "),
                    isLab = type.isLabType,
                )
            }
    }

    /**
     * Every exam filed against one named semester, in raw form.
     *
     * [exams] is the display projection: seat and time already merged for a card. The predictor and
     * the week grid need those fields kept apart - `time` as a `"09:15 AM - 12:30 PM"` range and
     * `date` unparsed - so they bind here rather than flattening the row twice.
     */
    fun semesterExams(snapshot: DomainSnapshot, semesterId: String): List<Exam> =
        snapshot.schedule.exams.filter { it.semesterId == semesterId }

    /**
     * The Exam Schedule dropdown's rule: exams for `semesterId`, or **every** semester's exams
     * when that one has none.
     *
     * This is deliberately not [examsForKnownSemester]. Two screens wrote their fallback
     * differently - `ifEmpty` here, `?:` there - and the difference is observable: a semester that
     * exists but has published no schedule shows all exams under this rule and none under that
     * one. Both readings are real, so both are kept.
     */
    fun selectedSemesterExams(snapshot: DomainSnapshot, semesterId: String): List<Exam> =
        semesterExams(snapshot, semesterId).ifEmpty { snapshot.schedule.exams }

    /**
     * The calendar's rule: exams for `semesterId` when that id names a semester, and **every**
     * semester's exams only when the id is unknown.
     *
     * The distinction from [selectedSemesterExams] is the `exists-but-empty` case; see its KDoc.
     */
    fun examsForKnownSemester(snapshot: DomainSnapshot, semesterId: String): List<Exam> =
        if (snapshot.academics.semesters.containsKey(semesterId)) semesterExams(snapshot, semesterId)
        else snapshot.schedule.exams

    /**
     * Ids of the semesters that have published at least one exam, in semester order.
     *
     * The schedule dropdown's option list. An empty result means nothing is published yet, which
     * the caller answers by offering every semester instead.
     */
    fun semesterIdsWithExams(snapshot: DomainSnapshot): List<String> =
        snapshot.schedule.exams.map { it.semesterId }.distinct()

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * `Embedded Theory` / `Embedded Lab` / `Lab Only` are the strings VTOP actually sends.
     * Anything else - including the `ETH`/`ELA` shorthand the port used to invent - must not
     * silently become false here, which is exactly the bug the audit found.
     */
    private val String.isLabType: Boolean
        get() {
            val t = trim().lowercase()
            return t == "embedded lab" || t == "lab only" || t == "lab" || t == "ela"
        }

    /** `10.0` -> `10`, `9.5` -> `9.5`. Avoids "Total: 10.0" on a card. */
    internal fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
