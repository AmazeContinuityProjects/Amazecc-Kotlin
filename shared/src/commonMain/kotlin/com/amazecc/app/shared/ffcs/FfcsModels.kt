package com.amazecc.app.shared.ffcs

data class ParsedCourse(
    val code: String,
    val title: String,
    val type: String,
    val credits: String,
    val room: String,
    val slot: String,
    val faculty: String,
    val batch: String = "",
    val originalCode: String? = null,
    val linkId: String? = null
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
    val color: String,
    val batch: String = ""
)

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

data class DashDetail(
    val fromClass: String,
    val toClass: String,
    val fromTime: String,
    val toTime: String,
    val day: String,
    val fromBlock: String,
    val toBlock: String
)

data class TimetableMetrics(
    val halfDays: Int,
    val gaps: Int,
    val gapsPerDay: Map<String, Int> = emptyMap(),
    val gapDetails: List<GapDetail> = emptyList(),
    val buildingDashes: Int = 0,
    val dashDetails: List<DashDetail> = emptyList(),
    val socialScore: Int = 0,
    val bestFriendMatches: List<String> = emptyList(),
    val isLongWeekend: Boolean = false
)

data class TimetableState(
    val id: String,
    val name: String,
    val courses: List<AddedCourse>,
    val metrics: TimetableMetrics = TimetableMetrics(halfDays = 0, gaps = 0),
    val variants: List<TimetableState> = emptyList()
)

data class CourseLock(
    val code: String,
    val title: String,
    val allowedSlots: List<String> = emptyList(),
    val allowedFaculty: List<String> = emptyList(),
    val offerings: List<String> = emptyList()
)

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
    val DAYS = listOf(
        "MON" to "Monday",
        "TUE" to "Tuesday",
        "WED" to "Wednesday",
        "THU" to "Thursday",
        "FRI" to "Friday"
    )

    val COLORS = listOf(
        "#2563EB", "#9333EA", "#10B981", "#DC2626", "#F59E0B",
        "#EC4899", "#4F46E5", "#14B8A6", "#F97316", "#0891B2",
        "#D946EF", "#84CC16", "#E11D48", "#7C3AED", "#0EA5E9",
        "#EAB308", "#16A34A", "#D63384"
    )

    val TYPE_LABELS = mapOf(
        "SS" to "Soft Skills",
        "TH" to "Theory Only",
        "LO" to "Lab Only",
        "PJT" to "Project",
        "ETH" to "Embedded Theory",
        "ELA" to "Embedded Lab",
        "EPJ" to "Embedded Project",
        "OC" to "Option Course",
        "ETH+ELA" to "Embedded Theory and Lab",
        "TH+LO" to "Theory + Lab"
    )

    fun getTypeLabel(type: String): String = TYPE_LABELS[type.uppercase()] ?: type
}
