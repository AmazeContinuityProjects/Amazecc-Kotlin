package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class EffectiveGrade(
    val basketTitle: String,
    val distributionType: String,
    val creditsRequired: String,
    val creditsEarned: String
)

@Serializable
data class CurriculumItem(
    val basketTitle: String,
    val creditsRequired: String,
    val creditsEarned: String
)

@Serializable
data class GradeCounts(
    val S: Int? = null,
    val A: Int? = null,
    val B: Int? = null,
    val C: Int? = null,
    val D: Int? = null,
    val E: Int? = null,
    val F: Int? = null,
    val N: Int? = null
)

@Serializable
data class CGPA(
    val grades: GradeCounts? = null,
    val creditsRequired: String? = null,
    val creditsEarned: String? = null,
    val cgpa: String? = null,
    val nonGradedRequirement: String? = null
)

@Serializable
data class FeedbackCategoryStatus(
    val Curriculum: Boolean,
    val Course: Boolean
)

@Serializable
data class FeedbackStatus(
    val MidSem: FeedbackCategoryStatus,
    val EndSem: FeedbackCategoryStatus
)

@Serializable
data class GradeBreakdown(
    val slNo: String? = null,
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
    val slNo: String? = null,
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val grandTotal: String = "",
    val grade: String = "",
    val courseId: String? = null,
    val details: List<GradeBreakdown>? = null,
    val range: GradeRange? = null
)

@Serializable
data class SemesterGradeResult(
    val gpa: String? = null,
    val grades: List<GradeItem> = emptyList()
)

@Serializable
data class AllGradesRes(
    val semesterId: String? = null,
    val grades: Map<String, SemesterGradeResult?>? = null,
    val error: String? = null,
    val success: Boolean = true,
    val message: String? = null
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
