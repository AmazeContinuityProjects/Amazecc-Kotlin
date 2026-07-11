package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.AttendanceItem
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class FriendClassSlot(
    val day: String,
    val timeSlot: String,
    val courseCode: String,
    val courseTitle: String,
    val venue: String,
    val slotId: String
)

@Serializable
data class Friend(
    val id: String,
    val name: String,
    val nickname: String,
    val regNumber: String,
    val classSlots: List<FriendClassSlot>,
    val color: String,
    val addedAt: String,
    val showInFriendsSchedule: Boolean,
    val showInHomePage: Boolean
)

object SocialUtils {
    // Map short days to full names
    private val DAYS_MAP = mapOf(
        "MON" to "Monday",
        "TUE" to "Tuesday",
        "WED" to "Wednesday",
        "THU" to "Thursday",
        "FRI" to "Friday",
        "SAT" to "Saturday",
        "SUN" to "Sunday"
    )
    
    fun exportScheduleCode(
        attendance: List<AttendanceItem>,
        name: String,
        regNumber: String
    ): String {
        if (attendance.isEmpty()) return ""
        val friendSlots = mutableListOf<FriendClassSlot>()

        attendance.forEach { course ->
            val slots = course.slotName?.split("+")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            slots.forEach { slot ->
                friendSlots.add(
                    FriendClassSlot(
                        day = "Unknown", // Add parsing logic if necessary
                        timeSlot = "Unknown",
                        courseCode = course.courseCode,
                        courseTitle = course.courseTitle,
                        venue = course.slotVenue ?: "",
                        slotId = slot
                    )
                )
            }
        }

        val slotsString = friendSlots.joinToString("||") { s ->
            "${s.day}|${s.timeSlot}|${s.courseCode}|${s.courseTitle}|${s.venue}|${s.slotId}"
        }

        return "${name}|${regNumber}|${slotsString}"
    }

    fun importScheduleCode(qrData: String, nickname: String? = null): Friend {
        val parts = qrData.split("|")
        if (parts.size < 2) throw Exception("Invalid QR data format")

        val name = parts[0]
        val regNumber = parts[1]
        val slotsData = if (parts.size > 2) parts.drop(2).joinToString("|") else ""

        val classSlots = mutableListOf<FriendClassSlot>()
        if (slotsData.isNotEmpty()) {
            val slotStrings = slotsData.split("||")
            for (slotStr in slotStrings) {
                if (slotStr.isNotEmpty()) {
                    val sParts = slotStr.split("|")
                    if (sParts.size == 6) {
                        classSlots.add(
                            FriendClassSlot(
                                day = sParts[0],
                                timeSlot = sParts[1],
                                courseCode = sParts[2],
                                courseTitle = sParts[3],
                                venue = sParts[4],
                                slotId = sParts[5]
                            )
                        )
                    }
                }
            }
        }

        val colors = listOf(
            "#EC4899", "#10B981", "#A855F7", "#F59E0B",
            "#3B82F6", "#EF4444", "#14B8A6", "#8B5CF6"
        )
        var hash = 0
        for (char in regNumber) {
            hash = char.code + ((hash shl 5) - hash)
        }
        val color = colors[abs(hash) % colors.size]

        return Friend(
            id = regNumber,
            name = name,
            nickname = nickname ?: name,
            regNumber = regNumber,
            classSlots = classSlots,
            color = color,
            addedAt = "Now",
            showInFriendsSchedule = true,
            showInHomePage = false
        )
    }
}
