package com.amazecc.app.shared.ffcs

import androidx.compose.runtime.Immutable

@Immutable
data class ParsedCourse(
    val code: String,
    val title: String,
    val type: String,
    val credits: String,
    val room: String,
    val slot: String,
    val faculty: String
) {
    fun offeringKey(): String = "$faculty|$slot|$room"
}

@Immutable
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

@Immutable
data class GapDetail(
    val day: String,
    val startMin: Int,
    val endMin: Int,
    val durationMins: Int,
    val fromClass: String? = null,
    val toClass: String? = null,
    val fromTime: String? = null,
    val toTime: String? = null
)

@Immutable
data class DashDetail(
    val fromClass: String,
    val toClass: String,
    val fromTime: String,
    val toTime: String,
    val day: String,
    val fromBlock: String,
    val toBlock: String
)

@Immutable
data class TimetableMetrics(
    val halfDays: Int,
    val gaps: Int,
    val gapsPerDay: Map<String, Int> = emptyMap(),
    val gapDetails: List<GapDetail> = emptyList(),
    val buildingDashes: Int = 0,
    val dashDetails: List<DashDetail> = emptyList(),
    val socialScore: Int = 0,
    val isLongWeekend: Boolean = false
)

@Immutable
data class TimetableState(
    val id: String,
    val name: String,
    val courses: List<AddedCourse>,
    val metrics: TimetableMetrics = TimetableMetrics(halfDays = 0, gaps = 0)
)

@Immutable
data class CourseLock(
    val code: String,
    val title: String,
    val allowedOfferings: List<String> = emptyList()
) {
    val hasLock: Boolean get() = allowedOfferings.isNotEmpty()
}

@Immutable
data class CourseOffering(
    val faculty: String,
    val slot: String,
    val room: String,
    val code: String,
    val title: String,
    val type: String,
    val credits: String
) {
    fun toKey(): String = "$faculty|$slot|$room"
}

object FfcsConstants {
    val COLORS = listOf(
        "#2563EB", "#9333EA", "#10B981", "#DC2626", "#F59E0B",
        "#EC4899", "#4F46E5", "#14B8A6", "#F97316", "#0891B2",
        "#D946EF", "#84CC16", "#E11D48", "#7C3AED", "#0EA5E9",
        "#EAB308", "#16A34A", "#D63384"
    )
}
