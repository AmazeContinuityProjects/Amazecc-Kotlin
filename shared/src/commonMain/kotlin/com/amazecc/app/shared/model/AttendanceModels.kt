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
    @Serializable(with = NullableStringAsEmptySerializer::class)
    val courseType: String = "",
    val slotName: String = "",
    @Serializable(with = NullableStringAsEmptySerializer::class)
    val faculty: String = "",
    @Serializable(with = NullableIntAsZeroSerializer::class)
    val attendedClasses: Int = 0,
    @Serializable(with = NullableIntAsZeroSerializer::class)
    val totalClasses: Int = 0,
    @Serializable(with = NullableStringAsEmptySerializer::class)
    val attendancePercentage: String = "",
    @SerialName("viewLink")
    val viewLinkRaw: JsonElement? = null,
    val credits: String? = null,
    val slotVenue: String? = null,
    val category: String? = null,
    /** Typed daily history; filled by the store sanitizer (raw [viewLinkRaw] is dropped at the store boundary). */
    val logs: List<AttendanceLog> = emptyList(),

    // Contract fields from ../amazecc-api-hono/src/types/data/attendance.ts and
    // ../AmazeCC/src/types/data/attendance.d.ts. Additive + nullable so older payloads still decode.
    val slNo: String? = null,
    val registrationDate: String? = null,
    val attendanceDate: String? = null,
    val classId: String? = null
)

@Serializable
data class AttendanceRes(
    val semesterId: String? = null,
    /** `attRes.semester` from `POST /api/attendance`; kept additive so legacy `semesterId` still works. */
    @SerialName("semester") val semester: String? = null,
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

@Serializable
data class OdRecordItem(
    val slNo: String = "",
    val type: String = "",
    val reason: String = "",
    val basis: String = "",
    val date: String = "",
    val time: String = "",
    val remarks: String = ""
)

@Serializable
data class OdDetailsRes(
    val success: Boolean = true,
    val semesterId: String = "",
    val totalCount: Int = 0,
    val note: String? = null,
    val records: List<OdRecordItem> = emptyList(),
    val error: String? = null
)

