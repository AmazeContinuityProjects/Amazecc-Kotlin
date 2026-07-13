package com.amazecc.app.shared.ffcs

data class ParsedCourse(
    val code: String,
    val title: String,
    val type: String,
    val credits: String,
    val room: String,
    val slot: String,
    val faculty: String
)

data class AddedCourse(
    val id: String,
    val code: String,
    val title: String,
    val slots: List<String>,
    val faculty: String,
    val venue: String,
    val credits: String,
    val type: String,
    val color: String
)

data class TimetableMetrics(
    val halfDays: Int,
    val gaps: Int,
    val gapsPerDay: Map<String, Int> = emptyMap(),
    val buildingDashes: Int = 0,
    val socialScore: Int = 0
)

data class TimetableState(
    val id: String,
    val name: String,
    val courses: List<AddedCourse>,
    val metrics: TimetableMetrics
)

data class CourseLock(
    val code: String,
    val title: String,
    val allowedSlots: List<String> = emptyList(), // empty = all
    val allowedFaculty: List<String> = emptyList(), // empty = all
    val offerings: List<String> = emptyList() // The available raw choices
)
