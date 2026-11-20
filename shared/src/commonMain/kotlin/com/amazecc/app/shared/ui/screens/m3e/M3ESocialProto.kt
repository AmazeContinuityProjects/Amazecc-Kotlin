package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.state.BusyEntry

/** One common-free block derived from real server busy maps (never invented). */
@Immutable
data class M3ESocialFreeSlotUi(
    val day: String,
    val timeRange: String,
    /** Handles/peers whose busy maps were intersected to produce this block. */
    val freeWith: List<String> = emptyList()
)

/**
 * Pure helpers for the server-backed Social surfaces (D-003/D-006/D-064).
 * No state, no I/O, no Compose side effects — safe to call from a host coroutine or a preview.
 */
object M3ESocialProto {
    /** Web `HANDLE_RE`: AMZ-XXXX-XXXX over a Crockford-style alphabet without I/L/O/U. */
    private val handleRe = Regex("^AMZ-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")

    fun isValidHandle(value: String?): Boolean = handleRe.matches(value.orEmpty().trim().uppercase())

    /** Web `normaliseHandle`: never mangle an already-valid handle. */
    fun normaliseHandle(raw: String?): String {
        val upper = raw.orEmpty().trim().uppercase()
        if (handleRe.matches(upper)) return upper
        val body = upper.removePrefix("AMZ").filter { it.isLetterOrDigit() }
        if (body.length == 8) {
            val candidate = "AMZ-${body.take(4)}-${body.takeLast(4)}"
            if (handleRe.matches(candidate)) return candidate
        }
        return upper
    }

    /** Web `handleFromInvite`: a bare handle in any case/spacing, or a `/?add=` invite link. */
    fun handleFromInvite(raw: String?): String {
        val text = raw.orEmpty().trim()
        val marker = "add="
        val linkPart = when {
            text.contains("?$marker") -> text.substringAfter("?$marker")
            text.contains("&$marker") -> text.substringAfter("&$marker")
            else -> null
        }
        if (linkPart != null) {
            val encoded = linkPart.substringBefore("&").substringBefore("#")
            val candidate = normaliseHandle(decodePercent(encoded))
            if (isValidHandle(candidate)) return candidate
        }
        return normaliseHandle(text)
    }

    /** Web `buildInviteLink`: only the public handle travels in the link. */
    fun inviteLink(handle: String): String =
        "https://amazecc.app/?add=$handle"

    private fun decodePercent(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)?.takeIf { it in 0..255 }
                if (byte != null) {
                    bytes.add(byte.toByte())
                    i += 3
                    continue
                }
            }
            bytes.addAll(c.toString().encodeToByteArray().toList())
            i++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    /** "8:00", "1:25" → minutes since midnight. Timetable hours are 08:00–17:35, so 1–7 is PM. */
    private fun slotMinutes(value: String): Int? {
        val clean = value.trim().uppercase().replace("AM", "").replace("PM", "").trim()
        val parts = clean.split(":")
        var hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (hour in 1..7) hour += 12
        return hour * 60 + minute
    }

    /** The `DAY:SLOT` key covering the given wall-clock minute, or null outside class hours. */
    fun currentSlotKey(dayKey: String, minutesNow: Int): String? {
        val slots = SlotMap.map[dayKey] ?: return null
        for ((slotId, range) in slots) {
            val bounds = range.split("-")
            if (bounds.size != 2) continue
            val start = slotMinutes(bounds[0]) ?: continue
            val end = slotMinutes(bounds[1]) ?: continue
            if (minutesNow in start..end) return "$dayKey:$slotId"
        }
        return null
    }

    /** Peers free in the current slot (both sides' busy maps must be free). */
    fun freeNowHandles(
        mine: Map<String, BusyEntry>,
        others: List<Pair<String, Map<String, BusyEntry>>>,
        slotKey: String?
    ): List<String> {
        if (slotKey == null || mine.containsKey(slotKey)) return emptyList()
        return others.filterNot { (_, busy) -> busy.containsKey(slotKey) }.map { it.first }
    }

    /**
     * Common-free grid from the server's `busyMap` objects (D-006/D-064). Keys are
     * `DAY:SLOT` (e.g. `MON:A1`); a present key means occupied, even in COARSE mode
     * where the value is empty. A block is emitted only when at least two consecutive
     * theory slots are free for everyone — the same >= 2 rule as `SocialUtils`.
     */
    fun commonFreeSlots(
        mine: Map<String, BusyEntry>,
        others: List<Pair<String, Map<String, BusyEntry>>>
    ): List<M3ESocialFreeSlotUi> {
        val busy = mine.keys.toMutableSet()
        others.forEach { (_, map) -> busy.addAll(map.keys) }
        if (busy.isEmpty()) return emptyList()

        val out = mutableListOf<M3ESocialFreeSlotUi>()
        val weekdayLabels = mapOf(
            "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday",
            "THU" to "Thursday", "FRI" to "Friday"
        )
        SlotMap.weekdays.forEach { day ->
            val slots = SlotMap.map[day]?.keys?.sorted().orEmpty()
            var run = 0
            var firstSlot = ""
            var lastSlot = ""
            fun flush() {
                if (run >= 2) {
                    val start = SlotMap.getTime(day, firstSlot)?.substringBefore("-")?.trim().orEmpty()
                    val end = SlotMap.getTime(day, lastSlot)?.substringAfter("-")?.trim().orEmpty()
                    if (start.isNotBlank() && end.isNotBlank()) {
                        out += M3ESocialFreeSlotUi(
                            day = weekdayLabels[day] ?: day,
                            timeRange = "$start - $end",
                            freeWith = others.map { it.first }
                        )
                    }
                }
                run = 0
            }
            slots.forEach { slot ->
                if (slot in busy) {
                    flush()
                } else {
                    if (run == 0) firstSlot = slot
                    lastSlot = slot
                    run++
                }
            }
            flush()
        }
        return out
    }
}
