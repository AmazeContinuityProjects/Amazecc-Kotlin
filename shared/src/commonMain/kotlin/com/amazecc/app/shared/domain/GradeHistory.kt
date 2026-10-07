package com.amazecc.app.shared.domain

import kotlin.math.abs
import kotlin.math.round

/**
 * Grade history, as numbers - the port of `AmazeCC/src/lib/gradeHistory.ts`.
 *
 * The screen this backs is `GradesScreen`, and every derivation it needs used to be inline in the
 * composable: a semester label, a marks trend, a distribution, an average. Inline they were
 * untestable, and one of them was outright wrong - the switcher derived its own label as
 * `id.endsWith("1") ? Fall : Winter`, which labels a **summer** term "Winter". [semesterName]
 * already existed in `CourseDetailScreen` and is now the single answer.
 *
 * Pure, and free of state, config and storage. Which number a screen shows is a design decision;
 * what the number *is* is decided here, once.
 *
 * Three things in the TypeScript original are deliberately not ported, and the reasons are
 * recorded in `docs/sep-30-2026/screen-transplant-plan.md` 6:
 *
 *  - [embeddedSegments]/[segmentBlend] read Node's per-semester *marks* payload, where an embedded
 *    course is still two rows under one code. This app merges that pair at ingest, so the halves
 *    no longer exist to be blended and a blend computed from the merged row would be a made-up
 *    number.
 *  - `toneForGrade`/`GRADE_TONE` map onto Node's six semantic tone names; this app's grade chips
 *    run through `gradeColorIndex` onto `AmazeColors.chart1..5`, a different token set. Porting
 *    the names would produce colours nothing reads.
 *  - The CGPA summary [cumulativeGpa] needs lives on `MarksRes`, not in [DomainSnapshot], so there
 *    is no snapshot input for it. The function itself is ported and tested; nothing calls it yet.
 */
object GradeHistory {

    /** VIT's 10-point scale. `N` is ungraded and worth nothing. */
    val GRADE_POINTS: Map<String, Int> = mapOf(
        "S" to 10, "A" to 9, "B" to 8, "C" to 7, "D" to 6, "E" to 5, "F" to 0, "N" to 0,
    )

    /**
     * The graded letters, best to worst.
     *
     * `N` (not yet graded) and `P` are deliberately absent. They are letters VTOP prints, but
     * neither is a grade the student received, and a distribution that counted them would put an
     * ungraded course in the denominator - three `N`s in a term and an `S` reads as a minority when
     * it is the only grade there was.
     */
    val GRADE_ORDER: List<String> = listOf("S", "A", "B", "C", "D", "E", "F")

    /** A parsed number, or `null`. VTOP sends every figure as a string. */
    internal fun num(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val n = value.trim().toDoubleOrNull() ?: return null
        return n.takeIf { it.isFinite() }
    }

    /** Round to 2dp, so a page never renders `8.899999999999999`. */
    internal fun round2(n: Double): Double = round(n * 100.0) / 100.0

    /**
     * `CH` + 4-digit entry year + 2-digit calendar year + 2-digit term, e.g. `CH20252601`.
     *
     * For that shape plain lexicographic order already happens to be chronological - which is why
     * the old page was right about ordering and wrong about naming. Anything else is sorted
     * lexicographically rather than dropped, because a term the reader can see in the data must not
     * silently disappear from the list.
     */
    internal fun semesterSortKey(id: String): String =
        if (SEMESTER_ID.matches(id)) id.substring(2) else id

    private val SEMESTER_ID = Regex("^CH\\d{8}$")

    /**
     * One semester, named the way VIT means it: `CH20252601` -> `Fall 2025-26`.
     *
     * This is `courseHelpers.formatSemesterName` from the web app, and the reason it lives here
     * rather than in a screen is the bug it fixes: `GradesScreen` used to compute
     * `if (id.endsWith("1")) "FS yy" else "WS yy"` inline, which calls a summer term "Winter".
     */
    fun semesterName(id: String): String {
        if (!id.uppercase().startsWith("CH") || id.length != 10) return id
        val year1 = id.substring(2, 6)
        val year2 = id.substring(6, 8)
        val term = id.substring(8, 10)
        val termName = when (term) {
            "01" -> "Fall"
            "05" -> "Winter"
            "07" -> "Summer"
            else -> "Term $term"
        }
        return "$termName $year1-$year2"
    }

    /** Whether this is a grade the student was actually awarded. */
    fun isGradedLetter(grade: String?): Boolean =
        (grade?.trim()?.uppercase() ?: "") in GRADE_ORDER

    /** Points for a letter, or `null` when the letter is one we do not know. */
    fun gradePoints(grade: String?): Int? =
        GRADE_POINTS[grade?.trim()?.uppercase() ?: ""]

    /**
     * One semester reduced to the numbers the page shows.
     *
     * [gpa] is VTOP's GPA parsed, `0.0` when it published none - a term the college has not filled
     * in yet is not a term with an average of zero, which is what [courses] and [scored] say.
     */
    data class SemesterRow(
        /** The VTOP id, e.g. `CH20252601`. */
        val id: String,
        /** `Fall 2025-26`. */
        val label: String,
        val gpa: Double,
        /** Courses carrying a grade, sorted by code. */
        val courses: List<CourseGrade>,
        /** The subset of [courses] that carry a numeric `grandTotal`. */
        val scored: List<CourseGrade>,
        /** Mean `grandTotal` across [scored], 0 when none are scored. */
        val avgScore: Double,
        /** Mean of the letter grades as points out of 10, 0 when there are none. */
        val avgPoints: Double,
    )

    /**
     * One semester, or `null` for an entry that is not a semester.
     *
     * Node's guard reads `if (!data || typeof data !== "object")`. A `Map<String, Semester>` cannot
     * hold a non-object, so here the guard is just `null` - kept because `semesterRows` has a test
     * that a null entry disappears rather than becoming a row of zeroes.
     */
    fun toSemesterRow(id: String, semester: Semester?): SemesterRow? {
        if (semester == null) return null

        val courses = semester.courses.values
            .filter { it.grade != null }
            .sortedBy { it.code }
            .map { it.asGrade() }

        // A term with no courses at all is a payload the college has not filled in yet, not a term
        // with an average of zero. It stays in the list so the student can see it is missing, but
        // it contributes to nothing.
        val scored = courses.filter { num(it.grandTotal) != null }
        val avgScore = if (scored.isEmpty()) {
            0.0
        } else {
            scored.sumOf { num(it.grandTotal) ?: 0.0 } / scored.size
        }

        // Graded letters only: an `N` is worth 0 on the scale, but averaging it in as a 0 would
        // report a term the student has not been assessed on yet as a term they failed.
        val withPoints = courses.filter { isGradedLetter(it.grade) }
        val avgPoints = if (withPoints.isEmpty()) {
            0.0
        } else {
            withPoints.sumOf { (gradePoints(it.grade) ?: 0).toDouble() } / withPoints.size
        }

        return SemesterRow(
            id = id,
            label = semesterName(id),
            gpa = num(semester.gpa) ?: 0.0,
            courses = courses,
            scored = scored,
            avgScore = round2(avgScore),
            avgPoints = round2(avgPoints),
        )
    }

    /** Every term in the snapshot, oldest first. */
    fun semesterRows(snapshot: DomainSnapshot): List<SemesterRow> =
        snapshot.academics.semesters
            .mapNotNull { (id, semester) -> toSemesterRow(id, semester) }
            .sortedBy { semesterSortKey(it.id) }

    /** The newest term with any courses, which is what the page opens on. */
    fun latestSemester(rows: List<SemesterRow>): SemesterRow? =
        rows.reversed().firstOrNull { it.courses.isNotEmpty() } ?: rows.lastOrNull()

    /** The highest-GPA term. Ties keep the earlier row, as the original loop did. */
    fun bestTerm(rows: List<SemesterRow>): SemesterRow? =
        rows.filter { it.gpa > 0 }.maxByOrNull { it.gpa }

    /** Where a cumulative figure came from, so a page can say so. */
    enum class CumulativeSource { VTOP, DERIVED, NONE }

    data class Cumulative(
        val value: Double,
        val creditsEarned: Double,
        val creditsRequired: Double,
        val source: CumulativeSource,
    )

    /**
     * The CGPA, preferring the figure VTOP published.
     *
     * The `derived` fallback is an unweighted mean of per-term GPAs, which is *not* the same number
     * - a 3-credit term and a 24-credit term count equally - so the source must travel with it and
     * the UI must label it rather than presenting it as the real figure.
     *
     * Ported and tested, but not yet called: the CGPA summary lives on `MarksRes`, which is not part
     * of [DomainSnapshot].
     */
    fun cumulativeGpa(
        rows: List<SemesterRow>,
        cgpa: String? = null,
        creditsEarned: String? = null,
        creditsRequired: String? = null,
    ): Cumulative {
        val earned = num(creditsEarned) ?: 0.0
        val required = num(creditsRequired) ?: 0.0

        val published = num(cgpa)
        if (published != null && published > 0) {
            return Cumulative(published, earned, required, CumulativeSource.VTOP)
        }

        val withGpa = rows.filter { it.gpa > 0 }
        if (withGpa.isNotEmpty()) {
            return Cumulative(
                value = round2(withGpa.sumOf { it.gpa } / withGpa.size),
                creditsEarned = earned,
                creditsRequired = required,
                source = CumulativeSource.DERIVED,
            )
        }

        return Cumulative(0.0, earned, required, CumulativeSource.NONE)
    }

    data class DistributionRow(val grade: String, val count: Int, val share: Double)

    /**
     * How the grades are spread, across every course passed in.
     *
     * `count` only counts courses carrying a letter the student was actually awarded - see
     * [GRADE_ORDER] for why `N` is not one of them. A letter outside the ladder is dropped rather
     * than bucketed into an "other" column: a column headed by a letter nobody recognises is not
     * information, and an unexpected letter is a data problem worth catching in a test rather than
     * hiding in the UI.
     */
    fun gradeDistribution(courses: List<CourseGrade>): List<DistributionRow> {
        val counts = mutableMapOf<String, Int>()
        for (c in courses) {
            val g = c.grade.trim().uppercase()
            if (!isGradedLetter(g)) continue
            counts[g] = (counts[g] ?: 0) + 1
        }
        val total = counts.values.sum()
        if (total == 0) return emptyList()

        return GRADE_ORDER.filter { counts.containsKey(it) }.map { grade ->
            val count = counts.getValue(grade)
            DistributionRow(grade, count, round2(count.toDouble() / total * 100))
        }
    }

    data class TypeAverage(val type: String, val avg: Double, val count: Int)

    /**
     * Mean score by course type - theory against lab.
     *
     * The reason this exists: an embedded course is published as two entries with the same code,
     * and averaging all of them together hides the fact that every lab is dragging the number down.
     * Grouping by course type is the one cut that answers "where am I weak?".
     *
     * Scored courses only, and a type with none is omitted rather than shown as 0.
     */
    fun averageByType(courses: List<CourseGrade>): List<TypeAverage> {
        val groups = mutableMapOf<String, MutableList<Double>>()
        for (c in courses) {
            val score = num(c.grandTotal) ?: continue
            val type = c.courseType.trim().ifEmpty { "Other" }
            groups.getOrPut(type) { mutableListOf() }.add(score)
        }
        return groups
            .map { (type, scores) -> TypeAverage(type, round2(scores.sum() / scores.size), scores.size) }
            .sortedByDescending { it.avg }
    }

    /**
     * The weighted total implied by a course's assessment breakdown.
     *
     * Each component carries both [GradeComponent.weightage] (this assessment's contribution to the
     * final score) and [GradeComponent.weightagePercent]. Summing the weightage gives the total the
     * assessment list itself implies, which is the number to check `grandTotal` against.
     *
     * `null` when the breakdown has no usable weightage, so a caller can tell "the list sums to
     * nothing" apart from "the list sums to zero".
     */
    fun weightedTotal(details: List<GradeComponent>?): Double? {
        if (details.isNullOrEmpty()) return null
        val parts = details.mapNotNull { num(it.weightage) }
        if (parts.isEmpty()) return null
        return round2(parts.sum())
    }

    /**
     * Whether the assessment list disagrees with the recorded total.
     *
     * A half-point tolerance, because the two are rounded independently and a 0.04 gap is
     * arithmetic noise while a 3-point gap means the list is not the thing that produced the total.
     */
    fun weightedTotalDiffers(grandTotal: String?, details: List<GradeComponent>?): Boolean {
        val weighted = weightedTotal(details) ?: return false
        val reported = num(grandTotal) ?: return false
        return abs(weighted - reported) > 0.5
    }

    /**
     * Is this an embedded (theory + lab) course?
     *
     * Matches on `courseType` alone. The course *code* is not a safe signal: the embedded codes in
     * circulation are unremarkable, so a suffix or prefix rule would both over- and under-match.
     */
    fun isEmbeddedCourse(courseType: String?): Boolean =
        courseType?.contains("embedded", ignoreCase = true) == true
}
