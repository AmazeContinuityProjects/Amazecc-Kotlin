package com.amazecc.app.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One day's attendance record deciphered from the raw `viewLink` payload. */
@Serializable
data class AttendanceLog(
    val date: String,
    val status: String
)

@Serializable
data class AttendanceItem(
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val slotName: String = "",
    val faculty: String = "",
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
    val attendancePercentage: String = "",
    @SerialName("viewLink")
    val viewLinkRaw: JsonElement? = null,
    val credits: String? = null,
    val slotVenue: String? = null,
    val category: String? = null,
    /** Typed daily history; filled by the store sanitizer (raw [viewLinkRaw] is dropped at the store boundary). */
    val logs: List<AttendanceLog> = emptyList()
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
    val hours: Int,
    val courseCode: String? = null
)

@Serializable
data class ODTrackedEntry(
    val courseTitle: String,
    val type: String,
    val slotName: String? = null,
    val status: String // "wasted" | "recovered"
)

@Serializable
data class ODListItem(
    val date: String,
    val courses: List<ODEntry>,
    val total: Int
)
