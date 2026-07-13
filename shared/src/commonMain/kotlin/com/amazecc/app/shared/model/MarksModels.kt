package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AssessmentItem(
    val slNo: String,
    val title: String,
    val maxMark: String,
    val weightagePercent: String,
    val status: String,
    val scoredMark: String,
    val weightageMark: String
)

@Serializable
data class MarksCourseItem(
    val slNo: String,
    val classNbr: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val courseSystem: String,
    val credits: String? = null, // Can be number or string in TS, String simplifies Kotlin
    val faculty: String,
    val slot: String,
    val courseMode: String,
    val assessments: List<AssessmentItem> = emptyList()
)
