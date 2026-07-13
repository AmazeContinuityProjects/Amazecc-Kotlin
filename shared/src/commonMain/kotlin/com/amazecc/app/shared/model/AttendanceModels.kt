package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CourseItem(
    val slNo: String,
    val course: String,
    val courseCode: String,
    val LTPJC: String,
    val category: String,
    val classId: String,
    val slotVenue: String,
    val facultyDetails: String
)

@Serializable
data class DetailedAttendance(
    val date: String,
    val status: String
)

@Serializable
data class AttendanceItem(
    val slNo: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val slotName: String,
    val faculty: String,
    val registrationDate: String = "",
    val attendanceDate: String = "",
    val attendedClasses: Int,
    val totalClasses: Int,
    val attendancePercentage: String,
    val viewLinkRaw: JsonElement? = null,
    val classId: String? = null,
    val credits: String? = null,
    val slotVenue: String? = null,
    val category: String? = null
)

@Serializable
data class AttendanceRes(
    val semesterId: String? = null,
    val attendance: List<AttendanceItem>? = null,
    val error: String? = null,
    val success: Boolean = true,
    val message: String? = null
)

@Serializable
data class ODEntry(
    val title: String,
    val type: String, // "LAB" | "TH"
    val hours: Int
)

@Serializable
data class ODListItem(
    val date: String,
    val courses: List<ODEntry>,
    val total: Int
)
