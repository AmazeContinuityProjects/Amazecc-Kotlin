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
    val weightageMark: String = ""
)

@Serializable
data class GradeRange(
    val S: String,
    val A: String,
    val B: String,
    val C: String,
    val D: String,
    val E: String,
    val F: String
)

@Serializable
data class GradeItem(
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val grandTotal: String = "",
    val grade: String = "",
    val details: List<GradeBreakdown>? = null,
    val range: GradeRange? = null
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
    val distributionType: String? = null
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
