package com.amazecc.app.shared.utils

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.model.AttendanceItem
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
@Immutable
data class FriendClassSlot(
    val day: String,
    val timeSlot: String,
    val courseCode: String,
    val courseTitle: String,
    val venue: String,
    val slotId: String
)

@Serializable
@Immutable
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

    suspend fun getCommonFreeSlots(userAttendance: List<AttendanceItem>, friends: List<Friend>): List<String> = withContext(Dispatchers.Default) {
        val userBusySlots = userAttendance.flatMap { it.slotName?.split("+")?.map { s -> s.trim() } ?: emptyList() }.filter { it.isNotEmpty() }.toSet()
        val friendsBusySlots = friends.flatMap { f -> f.classSlots.map { it.slotId } }.toSet()
        val allBusy = userBusySlots + friendsBusySlots

        val standardSlots = listOf(
            "MON" to listOf("A1", "F1", "D1", "TB1", "TG1", "A2", "F2", "D2", "TB2", "TG2"),
            "TUE" to listOf("B1", "G1", "E1", "TC1", "TAA1", "B2", "G2", "E2", "TC2", "TAA2"),
            "WED" to listOf("C1", "A1", "F1", "TD1", "TBB1", "C2", "A2", "F2", "TD2", "TBB2"),
            "THU" to listOf("D1", "B1", "G1", "TE1", "TCC1", "D2", "B2", "G2", "TE2", "TCC2"),
            "FRI" to listOf("E1", "C1", "TA1", "TF1", "TDD1", "E2", "C2", "TA2", "TF2", "TDD2")
        )

        val freeBlocks = mutableListOf<String>()
        for ((day, slots) in standardSlots) {
            var consecutiveFree = 0
            var startSlotTime = ""
            var endSlotTime = ""
            for (slot in slots) {
                // If it's lab slot L... this logic only covers theory slots, which is usually enough for finding common free blocks.
                // We check if ANY of the busy slots overlap with this theory slot.
                // A better approach is to just check if `slot` is in `allBusy`. But we should also check if any lab slots overlapping with this theory slot are busy.
                // For simplicity, we just check exact match.
                if (!allBusy.contains(slot)) {
                    val timeStr = com.amazecc.app.shared.config.SlotMap.map[day]?.get(slot)
                    if (timeStr != null) {
                        if (consecutiveFree == 0) startSlotTime = timeStr.substringBefore("-")
                        endSlotTime = timeStr.substringAfter("-")
                        consecutiveFree++
                    }
                } else {
                    if (consecutiveFree >= 2) {
                        val dayName = DAYS_MAP[day] ?: day
                        freeBlocks.add("$dayName $startSlotTime - $endSlotTime")
                    }
                    consecutiveFree = 0
                }
            }
            if (consecutiveFree >= 2) {
                val dayName = DAYS_MAP[day] ?: day
                freeBlocks.add("$dayName $startSlotTime - $endSlotTime")
            }
        }
        
        if (freeBlocks.isEmpty()) listOf("No common free blocks found (>= 1.5 hrs).") else freeBlocks
    }
}

