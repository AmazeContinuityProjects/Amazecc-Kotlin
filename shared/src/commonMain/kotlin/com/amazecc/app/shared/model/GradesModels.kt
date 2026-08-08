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
