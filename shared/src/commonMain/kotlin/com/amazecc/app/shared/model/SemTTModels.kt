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

@Serializable
data class HolidayEvent(
    val type: String = "Holiday",
    val text: String,
    val color: String,
    val category: String? = null
)

@Serializable
data class CalendarRequestBody(
    val type: String // "ALL", "ALL02", etc.
)

@Serializable
data class CalendarInput(
    val year: String? = null,
    val month: String? = null,
    val totalDays: Int? = null,
    val days: List<CalendarDay>? = null
)
