package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ExamItem(
    val courseCode: String = "",
    val courseTitle: String = "",
    val slot: String = "",
    val examDate: String = "",
    val examTime: String = "",
    val venue: String = "",
    val seatNo: String = ""
)
