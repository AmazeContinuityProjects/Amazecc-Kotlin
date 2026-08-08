package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentItem(
    val title: String = "",
    val maxMark: String = "",
    val weightagePercent: String = "",
    val status: String = "",
    val scoredMark: String = "",
    val weightageMark: String = ""
)

@Serializable
data class MarksCourseItem(
    val classNbr: String = "",
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val courseSystem: String = "",
    val faculty: String = "",
    val slot: String = "",
    val assessments: List<AssessmentItem> = emptyList()
)
