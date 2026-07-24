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
    private val DAYS_MAP = mapOf(
        "MON" to "Monday",
        "TUE" to "Tuesday",
        "WED" to "Wednesday",
        "THU" to "Thursday",
        "FRI" to "Friday",
        "SAT" to "Saturday",
        "SUN" to "Sunday"
    )

    private val SLOT_DAY_MAP = mapOf(
        'A' to "Monday", 'B' to "Tuesday", 'C' to "Wednesday",
        'D' to "Thursday", 'E' to "Friday",
        'F' to "Saturday", 'G' to "Sunday"
    )

    private val SLOT_TIME_MAP = mapOf(
        "1" to "8:00 AM", "2" to "8:50 AM", "3" to "9:40 AM",
        "4" to "10:30 AM", "5" to "11:20 AM", "6" to "12:10 PM",
        "7" to "1:00 PM", "8" to "1:50 PM", "9" to "2:40 PM",
        "10" to "3:30 PM", "11" to "4:20 PM", "12" to "5:10 PM"
    )

    private fun parseSlotDay(slot: String): String {
        val c = slot.firstOrNull()?.uppercaseChar()
        return SLOT_DAY_MAP[c] ?: "Unknown"
    }

    private fun parseSlotTime(slot: String): String {
        val numPart = slot.drop(1).takeWhile { it.isDigit() }
        return SLOT_TIME_MAP[numPart] ?: "Unknown"
    }

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
                        day = parseSlotDay(slot),
                        timeSlot = parseSlotTime(slot),
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
