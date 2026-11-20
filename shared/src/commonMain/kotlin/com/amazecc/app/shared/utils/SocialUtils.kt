package com.amazecc.app.shared.utils

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonArray
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

    /**
     * Deterministic slot vocabulary, mirroring web `lib/socialUtils.ts`:
     * `MON:A1`, `MON:F1`, … per day, sorted. Web-compatible (web `config.json slotMap`).
     */
    private fun staticSlotsList(): List<String> =
        SlotMap.days.flatMap { day -> (SlotMap.map[day]?.keys?.sorted() ?: emptyList()).map { "$day:$it" } }

    /** Web parity (D-007): `v6|<expiresAtSec>|<name>|<regNumber>|<courses>|<assignments>`. */
    fun exportScheduleCode(
        attendance: List<AttendanceItem>,
        name: String,
        regNumber: String,
        expiryMinutes: Int? = null
    ): String {
        if (attendance.isEmpty()) return ""
        val staticSlots = staticSlotsList()

        // Web parity: unique course titles, capped like web `getCourseIndex` (20 chars + "...").
        val uniqueTitles = mutableListOf<String>()
        fun courseIndex(courseTitle: String?): Int {
            val title = courseTitle ?: ""
            val cleanTitle = if (title.length > 20) title.substring(0, 20).trim() + "..." else title
            uniqueTitles.indexOf(cleanTitle).takeIf { it >= 0 }?.let { return it }
            uniqueTitles.add(cleanTitle)
            return uniqueTitles.size - 1
        }

        val assignments = mutableListOf<String>()
        attendance.forEach { course ->
            val courseIdx = courseIndex(course.courseTitle)
            val slots = course.slotName?.split("+")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            slots.forEach { slot ->
                SlotMap.days.forEach { day ->
                    if (SlotMap.map[day]?.containsKey(slot) == true) {
                        val key = "$day:$slot"
                        val slotIdx = staticSlots.indexOf(key)
                        if (slotIdx >= 0) {
                            assignments.add("${slotIdx.toString(36).padStart(2, '0')}${courseIdx.toString(36).substring(0, 1)}")
                        }
                    }
                }
            }
        }

        if (expiryMinutes != null && expiryMinutes > 0) {
            val expiresAtSec = Clock.System.now().toEpochMilliseconds() / 1000 + expiryMinutes.toLong() * 60
            return "v6|$expiresAtSec|$name|$regNumber|${uniqueTitles.joinToString(";")}|${assignments.joinToString("")}"
        }

        // Default matches web `exportScheduleCode`: v5 with the same compressed course/assignment
        // payload. (The legacy v1 `name|reg|day|time|code|title|venue|slot` writer was Kotlin-only;
        // the reader below still accepts v1 codes produced by older builds.)
        return "v5|$name|$regNumber|${uniqueTitles.joinToString(";")}|${assignments.joinToString("")}"
    }

    /** Web parity (D-007): `amazecc://share#s=<compressed code>` share link. */
    fun exportShareableLink(
        attendance: List<AttendanceItem>,
        name: String,
        regNumber: String,
        expiryMinutes: Int? = null
    ): String {
        val code = exportScheduleCode(attendance, name, regNumber, expiryMinutes)
        if (code.isEmpty()) return ""
        return "amazecc://share#s=" + compressScheduleCode(code)
    }

    private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Web parity (web `compressScheduleCode`): standard base64 → base64url, padding stripped. */
    fun compressScheduleCode(code: String): String {
        if (code.isEmpty()) return ""
        val bytes = code.toByteArray(Charsets.UTF_8)
        val out = StringBuilder()
        var i = 0
        while (i + 3 <= bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val b2 = bytes[i + 2].toInt() and 0xFF
            out.append(B64_ALPHABET[b0 ushr 2])
            out.append(B64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(B64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            out.append(B64_ALPHABET[b2 and 0x3F])
            i += 3
        }
        val rem = bytes.size - i
        if (rem == 1) {
            val b0 = bytes[i].toInt() and 0xFF
            out.append(B64_ALPHABET[b0 ushr 2])
            out.append(B64_ALPHABET[(b0 and 0x03) shl 4])
        } else if (rem == 2) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            out.append(B64_ALPHABET[b0 ushr 2])
            out.append(B64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(B64_ALPHABET[(b1 and 0x0F) shl 2])
        }
        return out.toString().replace('+', '-').replace('/', '_')
    }

    /** Web parity (web `decompressScheduleCode`): base64url → raw; already-v5/v6 codes pass through. */
    fun decompressScheduleCode(compressed: String): String {
        if (compressed.isEmpty()) return ""
        if (compressed.startsWith("v5|") || compressed.startsWith("v6|")) return compressed
        // Web `atob` rejects any character outside the base64 alphabet. Our decoder below
        // is lenient (it skips junk), so without this guard a plain `v3|…`/`v2|…`/`v1`
        // code would be silently mangled instead of falling through unchanged.
        if (compressed.any { it !in B64_ALPHABET && it != '-' && it != '_' && it != '=' }) return compressed
        return try {
            decodeBase64Url(compressed).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            compressed
        }
    }

    private fun decodeBase64Url(input: String): ByteArray {
        val b64 = input.replace('-', '+').replace('_', '/')
        val padded = StringBuilder(b64)
        while (padded.length % 4 != 0) padded.append('=')
        val out = ArrayList<Byte>()
        var acc = 0
        var bits = 0
        for (c in padded) {
            if (c == '=') break
            val v = B64_ALPHABET.indexOf(c)
            if (v < 0) continue
            acc = ((acc shl 6) or v) and 0xFFFFFF
            bits += 6
            while (bits >= 8) {
                bits -= 8
                out.add(((acc shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    /**
     * Web `decodeURIComponent` parity (D-007): `%XX` escapes are UTF-8 *bytes*, so a
     * multi-byte sequence such as `%C3%A9` must decode to "é" — mapping each byte to a
     * char corrupted every non-ASCII name in `#share=`/`amz-profile-` codes. Plain
     * characters are re-encoded to UTF-8 bytes too, so mixed input round-trips.
     */
    private fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val byteValue = value.substring(i + 1, i + 3).toIntOrNull(16)?.takeIf { it in 0..255 }
                if (byteValue != null) {
                    bytes.add(byteValue.toByte())
                    i += 3
                    continue
                }
            }
            bytes.addAll(c.toString().encodeToByteArray().toList())
            i++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    /** Web parity: the 3-char assignment chunks of v5/v6 codes (`<2-char slotIdx><courseIdx>`). */
    private fun parseAssignmentsChunks(
        assignmentsData: String,
        staticSlots: List<String>,
        titles: List<String>
    ): List<FriendClassSlot> {
        val slots = mutableListOf<FriendClassSlot>()
        var i = 0
        while (i < assignmentsData.length) {
            val chunk = assignmentsData.substring(i, minOf(i + 3, assignmentsData.length))
            if (chunk.length == 3) {
                val slotIdx = chunk.substring(0, 2).toIntOrNull(36)
                val courseIdx = chunk.substring(2, 3).toIntOrNull(36)
                if (slotIdx != null && courseIdx != null) {
                    val slotKey = staticSlots.getOrNull(slotIdx)
                    val title = titles.getOrNull(courseIdx)
                    if (slotKey != null && title != null) {
                        val sep = slotKey.indexOf(':')
                        if (sep > 0 && sep < slotKey.length - 1) {
                            val dayKey = slotKey.substring(0, sep)
                            val slotId = slotKey.substring(sep + 1)
                            val timeSlot = SlotMap.map[dayKey]?.get(slotId) ?: SlotMap.getTime(dayKey, slotId)
                            if (timeSlot != null) {
                                slots.add(
                                    FriendClassSlot(
                                        day = DAYS_MAP[dayKey] ?: dayKey,
                                        timeSlot = timeSlot,
                                        courseCode = "",
                                        courseTitle = title,
                                        venue = "",
                                        slotId = slotId
                                    )
                                )
                            }
                        }
                    }
                }
            }
            i += 3
        }
        return slots
    }

    /**
     * Web parity (web `importScheduleCode`, D-007): accepts `v6|…` codes and the
     * `#s=…`/`s=`/`#share=`/`share=` compressed encodings, plus the `v5`, `v3`, `v2` and
     * `amz-profile-` formats for cross-app compatibility. The Kotlin-native format
     * (`name|regNumber|day|timeSlot|courseCode|courseTitle|venue|slotId` per slot, `||`-joined)
     * is the legacy fallback.
     */
    fun importScheduleCode(rawData: String, nickname: String? = null): Friend {
        var qrData = rawData.trim()

        // Plain formats go straight to the readers below. Decompressing a raw v3/v2/v1 or
        // amz-profile code mangles its separators — the web client only decodes the #s=/share=
        // fragments, never the uncompressed code itself.
        val plainCode = qrData.startsWith("v6|") || qrData.startsWith("v5|") ||
            qrData.startsWith("v3|") || qrData.startsWith("v2|") ||
            qrData.startsWith("v1|") || qrData.startsWith("amz-profile-")
        if (!plainCode) {
            // Web parity: compressed share codes and the `#s=`/`s=`/`#share=`/`share=` forms.
            if (qrData.contains("#s=")) {
                qrData = decompressScheduleCode(qrData.split("#s=")[1].split("&")[0])
            } else if (qrData.contains("s=")) {
                qrData = decompressScheduleCode(qrData.split("s=")[1].split("&")[0])
            } else if (qrData.contains("#share=")) {
                qrData = percentDecode(qrData.split("#share=")[1].split("&")[0])
            } else if (qrData.contains("share=")) {
                qrData = percentDecode(qrData.split("share=")[1].split("&")[0])
            } else {
                qrData = decompressScheduleCode(qrData)
            }
        }

        val staticSlots = staticSlotsList()
        var name = ""
        var regNumber = ""
        val classSlots = mutableListOf<FriendClassSlot>()

        if (qrData.startsWith("v6|")) {
            val parts = qrData.split("|")
            if (parts.size < 6) {
                throw Exception("Invalid v6 temporary link format")
            }
            val expiresAtSec = parts[1].toLongOrNull()
            val nowSec = Clock.System.now().toEpochMilliseconds() / 1000
            if (expiresAtSec != null && expiresAtSec != 0L && nowSec > expiresAtSec) {
                val minAgo = maxOf(1L, (nowSec - expiresAtSec + 59) / 60)
                throw Exception("This temporary link expired $minAgo minute${if (minAgo != 1L) "s" else ""} ago. Please request a fresh link.")
            }
            name = parts[2]
            regNumber = parts[3]
            val titles = if (parts[4].isEmpty()) emptyList() else parts[4].split(";")
            classSlots.addAll(parseAssignmentsChunks(parts[5], staticSlots, titles))
        } else if (qrData.startsWith("v5|")) {
            val parts = qrData.split("|")
            if (parts.size < 5) {
                throw Exception("Invalid v5 format")
            }
            name = parts[1]
            regNumber = parts[2]
            val titles = if (parts[3].isEmpty()) emptyList() else parts[3].split(";")
            classSlots.addAll(parseAssignmentsChunks(parts[4], staticSlots, titles))
        } else if (qrData.startsWith("amz-profile-")) {
            val base64 = qrData.replace("amz-profile-", "").trim()
            // Web: decodeURIComponent(decodeBase64(base64)) → [name, reg, titles, assignments].
            val jsonStr = percentDecode(decodeBase64Url(base64).toString(Charsets.UTF_8))
            val payload: JsonArray = try {
                Json.parseToJsonElement(jsonStr).jsonArray
            } catch (_: Exception) {
                throw Exception("Failed to parse QR data: Invalid profile payload")
            }
            if (payload.size < 4) {
                throw Exception("Failed to parse QR data: Invalid profile payload")
            }
            name = (payload[0] as? JsonPrimitive)?.contentOrNull ?: ""
            regNumber = (payload[1] as? JsonPrimitive)?.contentOrNull ?: ""
            val titles: List<String> = (payload[2] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: emptyList()
            val assignments: JsonArray? = payload[3] as? JsonArray
            assignments?.forEach { assignment ->
                val arr = assignment as? JsonArray ?: return@forEach
                if (arr.size != 3) return@forEach
                val shortDay = (arr[0] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                val slotId = (arr[1] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                val courseIdx = (arr[2] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return@forEach
                val title = titles.getOrNull(courseIdx) ?: return@forEach
                val timeSlot = SlotMap.map[shortDay]?.get(slotId) ?: SlotMap.getTime(shortDay, slotId)
                if (timeSlot != null) {
                    classSlots.add(
                        FriendClassSlot(
                            day = DAYS_MAP[shortDay] ?: shortDay,
                            timeSlot = timeSlot,
                            courseCode = "",
                            courseTitle = title,
                            venue = "",
                            slotId = slotId
                        )
                    )
                }
            }
        } else if (qrData.startsWith("v3|")) {
            val parts = qrData.split("|")
            if (parts.size < 5) {
                throw Exception("Invalid v3 format")
            }
            name = parts[1]
            regNumber = parts[2]
            val titles = if (parts[3].isEmpty()) emptyList() else parts[3].split(";")
            val assignmentsData = parts[4]
            if (assignmentsData.isNotEmpty()) {
                assignmentsData.split(";").forEach { aStr ->
                    val aParts = aStr.split(",")
                    if (aParts.size == 3) {
                        val shortDay = aParts[0]
                        val slotId = aParts[1]
                        val courseIdx = aParts[2].toIntOrNull() ?: return@forEach
                        val title = titles.getOrNull(courseIdx) ?: return@forEach
                        val timeSlot = SlotMap.map[shortDay]?.get(slotId) ?: SlotMap.getTime(shortDay, slotId)
                        if (timeSlot != null) {
                            classSlots.add(
                                FriendClassSlot(
                                    day = DAYS_MAP[shortDay] ?: shortDay,
                                    timeSlot = timeSlot,
                                    courseCode = "",
                                    courseTitle = title,
                                    venue = "",
                                    slotId = slotId
                                )
                            )
                        }
                    }
                }
            }
        } else if (qrData.startsWith("v2|")) {
            val parts = qrData.split("|")
            if (parts.size < 5) {
                throw Exception("Invalid v2 format")
            }
            name = parts[1]
            regNumber = parts[2]
            val coursesData = parts[3]
            val assignmentsData = parts[4]
            val courses = mutableListOf<Triple<String, String, String>>()
            if (coursesData.isNotEmpty()) {
                coursesData.split(";").forEach { cStr ->
                    val cParts = cStr.split("~")
                    courses.add(Triple(cParts.getOrNull(0).orEmpty(), cParts.getOrNull(1).orEmpty(), cParts.getOrNull(2).orEmpty()))
                }
            }
            if (assignmentsData.isNotEmpty()) {
                assignmentsData.split(";").forEach { aStr ->
                    val aParts = aStr.split(",")
                    if (aParts.size == 3) {
                        val shortDay = aParts[0]
                        val slotId = aParts[1]
                        val courseIdx = aParts[2].toIntOrNull() ?: return@forEach
                        val course = courses.getOrNull(courseIdx) ?: return@forEach
                        val timeSlot = SlotMap.map[shortDay]?.get(slotId) ?: SlotMap.getTime(shortDay, slotId)
                        if (timeSlot != null) {
                            classSlots.add(
                                FriendClassSlot(
                                    day = DAYS_MAP[shortDay] ?: shortDay,
                                    timeSlot = timeSlot,
                                    courseCode = course.first,
                                    courseTitle = course.second,
                                    venue = course.third,
                                    slotId = slotId
                                )
                            )
                        }
                    }
                }
            }
        } else {
            // Legacy v1 (Kotlin format: name|regNumber|day|timeSlot|courseCode|title|venue|slotId).
            val parts = qrData.split("|")
            if (parts.size < 2) throw Exception("Invalid legacy format")

            name = parts[0]
            regNumber = parts[1]
            val slotsData = if (parts.size > 2) parts.drop(2).joinToString("|") else ""
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
        }

        val colors = listOf(
            "#EC4899", "#10B981", "#A855F7", "#F59E0B",
            "#3B82F6", "#EF4444", "#14B8A6", "#8B5CF6"
        )
        var hash = 0
        for (char in regNumber) {
            hash = char.code + ((hash shl 5) - hash)
        }
        val mod = ((hash % colors.size) + colors.size) % colors.size
        val color = colors[mod]

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

