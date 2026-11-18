package com.amazecc.app.shared.network.hono

import kotlinx.serialization.Serializable

/**
 * Heavily typed data models for the AmazeCC Hono API (Cloudflare Worker backend).
 * Sourced directly from ../amazecc-api-hono and ../AmazeCC/src/types.
 */

@Serializable
data class HonoApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long = 0L
)

@Serializable
data class HonoLoginRequest(
    val username: String,
    val password: String,
    val semesterSubId: String? = null
)

@Serializable
data class HonoLoginResponse(
    val authorized: Boolean,
    val token: String? = null,
    val message: String? = null,
    val user: HonoUserIdentity? = null
)

@Serializable
data class HonoUserIdentity(
    val name: String,
    val regNumber: String,
    val programme: String = "",
    val school: String = "",
    val campus: String = "Vellore",
    val email: String = "",
    val phone: String? = null,
    val bloodGroup: String? = null,
    val photo: String? = null
)

@Serializable
data class HonoAttendanceRecord(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String = "Theory Only",
    val slotName: String = "",
    val slotVenue: String? = null,
    val faculty: String = "",
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
    val attendancePercentage: Double = 0.0,
    val isEmbedded: Boolean = false,
    val theoryPct: Double? = null,
    val labPct: Double? = null
)

@Serializable
data class HonoScheduleItem(
    val dayOfWeek: String, // MON, TUE, WED, THU, FRI, SAT, SUN
    val slot: String,
    val courseCode: String,
    val courseTitle: String,
    val venue: String,
    val faculty: String,
    val startTime: String, // HH:mm format, e.g. "08:30"
    val endTime: String     // HH:mm format, e.g. "10:00"
)

@Serializable
data class HonoGradeRecord(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int,
    val grade: String,
    val semester: String
)

@Serializable
data class HonoMarksAssessment(
    val title: String,
    val maxMarks: Double,
    val scoredMarks: Double,
    val weightagePct: Double? = null
)

@Serializable
data class HonoCourseMarks(
    val courseCode: String,
    val courseTitle: String,
    val assessments: List<HonoMarksAssessment> = emptyList(),
    val totalScored: Double = 0.0,
    val maxPossible: Double = 100.0
)

@Serializable
data class HonoAcademicsOverview(
    val semesterId: String,
    val semesterName: String,
    val cgpa: Double,
    val totalCreditsEarned: Int,
    val attendanceRecords: List<HonoAttendanceRecord> = emptyList(),
    val timetable: List<HonoScheduleItem> = emptyList(),
    val grades: List<HonoGradeRecord> = emptyList(),
    val marks: List<HonoCourseMarks> = emptyList()
)
