package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GradeBreakdown(
    val component: String = "",
    val maxMark: String = "",
    val weightagePercent: String = "",
    val status: String = "",
    val scoredMark: String = "",
    val weightageMark: String = "",
    // Additive, verbatim from `getGradeViewDetails`. `slNo` is the row's own ordinal and `type`
    // is the web grouping ("Theory" / "Lab" / "Component N") assigned by the reference parser
    // (`amazecc-api-hono/src/lib/grades/fetchAllGradesHistory.ts:92-116`). Both are optional so
    // legacy payloads and the domain fallback keep decoding unchanged. Never fabricated.
    val slNo: String = "",
    val type: String? = null
)

@Serializable
data class GradeRange(
    val S: String,
    val A: String,
    val B: String,
    val C: String,
    val D: String,
    val E: String,
    val F: String,
    // Additive: some VTOP grade-range tables publish an "N" band after F. Blank when absent.
    val N: String = ""
)

@Serializable
data class GradeItem(
    /** D-026: the web `GradeItem` publishes its own row ordinal. */
    val slNo: String? = null,
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val grandTotal: String = "",
    val grade: String = "",
    val details: List<GradeBreakdown>? = null,
    val range: GradeRange? = null,
    // Additive enrichment from the published effective-grade row. These let the UI show
    // per-course credits/context straight from the grade payload instead of joining the
    // academics snapshot. Never fabricated: each mirrors a real `<td>` when present.
    val credits: String? = null,
    val examMonth: String? = null,
    val resultDeclared: String? = null,
    val distributionType: String? = null,
    val courseId: String? = null,
    // Additive verbatim cells from the semester grade view (`doStudentGradeView`):
    // tds[4]=L, [5]=P, [6]=J, [7]=C. `credits` above mirrors C when the detail scrape supplies it.
    // Null when the page did not publish the cell — never reconstructed.
    val l: String? = null,
    val p: String? = null,
    val j: String? = null
)

@Serializable
data class SemesterGradeResult(
    val gpa: String? = null,
    val grades: List<GradeItem> = emptyList(),
    // Effective-grade details merged by /api/remote/grades:
    val courseId: String? = null,
    val creditsEarned: String? = null,
    val examMonth: String? = null,
    val resultDeclared: String? = null,
    val distributionType: String? = null,
    val effectiveCourseType: String? = null
)

/**
 * `/api/remote/grades` response: `overall` carries the published CGPA and the
 * curriculum baskets, `semesters` carries the per-term grade rows. The legacy
 * `/api/all-grades` alias used `grades` instead of `semesters`; both are accepted.
 */
@Serializable
data class AllGradesRes(
    val semesterId: String? = null,
    @kotlinx.serialization.SerialName("grades")
    val grades: Map<String, SemesterGradeResult?>? = null,
    @kotlinx.serialization.SerialName("semesters")
    val semesters: Map<String, SemesterGradeResult?>? = null,
    val overall: OverallGrades? = null,
    val error: String? = null,
    val success: Boolean = true,
    val message: String? = null
) {
    val allSemesters: Map<String, SemesterGradeResult?> get() = semesters ?: grades.orEmpty()
    /** Published CGPA as returned by VTOP — authoritative, never reconstructed. */
    val publishedCgpa: String? get() = overall?.cgpa?.cgpa?.takeIf { it.isNotBlank() }
}

@Serializable
data class OverallGrades(
    val cgpa: CgpaSummary? = null,
    val curriculum: List<GradeCurriculumBasket> = emptyList()
)

@Serializable
data class CgpaSummary(
    val creditsRegistered: String? = null,
    val creditsEarned: String? = null,
    val cgpa: String? = null,
    val grades: GradeCounts? = null
)

@Serializable
data class GradeCounts(
    val S: Int = 0,
    val A: Int = 0,
    val B: Int = 0,
    val C: Int = 0,
    val D: Int = 0,
    val E: Int = 0,
    val F: Int = 0,
    val N: Int = 0
)

@Serializable
data class GradeCurriculumBasket(
    val basketTitle: String? = null,
    val creditsRequired: String? = null,
    val creditsEarned: String? = null
)

/**
 * One published effective-grade row from the `StudentGradeHistory` page.
 *
 * Column map is verbatim from `amazecc-api-hono/src/routes/remote/grades.ts`:
 * tds[1]=code, [2]=title, [3]=type, [4]=credits, [5]=grade, [6]=examMonth,
 * [7]=resultDeclared, [8]=distributionType. Rows without numeric credits or a valid
 * grade letter are rejected by the scraper before they reach this model.
 */
@Serializable
data class GradeHistoryCourse(
    val code: String = "",
    val title: String = "",
    val type: String = "",
    val credits: String = "",
    val grade: String = "",
    val examMonth: String? = null,
    val resultDeclared: String? = null,
    val distributionType: String? = null
)

/**
 * Everything the `StudentGradeHistory` page publishes, parsed in one pass:
 * effective-grade courses, the authoritative CGPA summary and curriculum baskets.
 */
@Serializable
data class GradeHistorySummary(
    val ok: Boolean = true,
    val courses: List<GradeHistoryCourse> = emptyList(),
    val cgpa: CgpaSummary = CgpaSummary(),
    val baskets: List<GradeCurriculumBasket> = emptyList()
)

@Serializable
data class FeedbackSemester(
    val text: String,
    val value: String,
    val selected: Boolean = false
)

@Serializable
data class FeedbackTableRow(
    val feedbackType: String? = null,
    val midSemester: String? = null,
    val teeSemester: String? = null
)

@Serializable
data class FeedbackStatusRes(
    val success: Boolean = true,
    val error: String? = null,
    val semesters: List<FeedbackSemester>? = null,
    val feedbackTable: List<FeedbackTableRow>? = null
)

@Serializable
data class EffectiveGradeCourse(
    val basketTitle: String? = null,
    val courseType: String? = null,
    val creditsEarned: String? = null,
    val grade: String? = null,
    val distributionType: String? = null,
    // Additive: the real identity of the row. The legacy scraper only filled basketTitle,
    // which is code-less and gets dropped by AppSanitizers.sanitizeAllGrades().
    val courseCode: String? = null,
    val courseTitle: String? = null,
    val examMonth: String? = null,
    val resultDeclared: String? = null
)

@Serializable
data class FeedbackKindStatus(
    val Curriculum: Boolean? = null,
    val Course: Boolean? = null
)

@Serializable
data class SemFeedbackDetails(
    val MidSem: FeedbackKindStatus? = null,
    val EndSem: FeedbackKindStatus? = null
)

@Serializable
data class SemesterGradesRes(
    val success: Boolean = true,
    val error: String? = null,
    val effectiveGrades: List<EffectiveGradeCourse>? = null,
    val feedback: SemFeedbackDetails? = null
)
