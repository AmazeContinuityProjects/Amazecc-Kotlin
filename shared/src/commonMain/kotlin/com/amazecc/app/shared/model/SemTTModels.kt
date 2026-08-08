package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val type: String, // "Instructional Day" | "Holiday" | "Other"
    val text: String,
    val color: String? = null,
    val category: String = ""
)

@Serializable
data class CalendarDay(
    val date: Int,
    val events: List<CalendarEvent> = emptyList()
)

@Serializable
data class CalendarMonth(
    val month: String,
    val days: List<CalendarDay> = emptyList()
)
