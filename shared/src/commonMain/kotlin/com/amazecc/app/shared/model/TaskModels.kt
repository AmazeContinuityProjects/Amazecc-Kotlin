package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class HomeworkTask(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val title: String,
    val description: String = "",
    val dueDate: String,
    val type: String = "homework",
    val priority: String = "medium", // high, medium, low
    val estimatedMinutes: Int = 0,
    val completed: Boolean = false,
    val createdAt: String
)
