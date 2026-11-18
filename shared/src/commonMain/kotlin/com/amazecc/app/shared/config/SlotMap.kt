package com.amazecc.app.shared.config

object SlotMap {
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI")
    val dayLabels = mapOf(
        "MON" to "Mon", "TUE" to "Tue", "WED" to "Wed",
        "THU" to "Thu", "FRI" to "Fri", "SAT" to "Sat", "SUN" to "Sun"
    )

    val map = mapOf(
        "MON" to mapOf(
            "A1" to "8:00-8:50",
            "F1" to "8:55-9:45",
            "D1" to "9:50-10:40",
            "TB1" to "10:45-11:35",
            "TG1" to "11:40-12:30",
            "S11" to "12:35-1:25",
            "A2" to "2:00-2:50",
            "F2" to "2:55-3:45",
            "D2" to "3:50-4:40",
            "TB2" to "4:45-5:35",
            "TG2" to "5:40-6:30",
            "S3" to "6:35-7:25",
            "L1" to "8:00-8:50",
            "L2" to "8:50-9:40",
            "L3" to "9:50-10:40",
            "L4" to "10:40-11:30",
            "L5" to "11:40-12:30",
            "L6" to "12:30-1:20",
            "L31" to "2:00-2:50",
            "L32" to "2:50-3:40",
            "L33" to "3:50-4:40",
            "L34" to "4:40-5:30",
            "L35" to "5:40-6:30",
            "L36" to "6:30-7:20",
        ),
        "TUE" to mapOf(
            "B1" to "8:00-8:50",
            "G1" to "8:55-9:45",
            "E1" to "9:50-10:40",
            "TC1" to "10:45-11:35",
            "TAA1" to "11:40-12:30",
            "L12" to "12:30-1:20",
            "B2" to "2:00-2:50",
            "G2" to "2:55-3:45",
            "E2" to "3:50-4:40",
            "TC2" to "4:45-5:35",
            "TAA2" to "5:40-6:30",
            "S1" to "6:35-7:25",
            "L7" to "8:00-8:50",
            "L8" to "8:50-9:40",
            "L9" to "9:50-10:40",
            "L10" to "10:40-11:30",
            "L11" to "11:40-12:30",
            "L37" to "2:00-2:50",
            "L38" to "2:50-3:40",
            "L39" to "3:50-4:40",
            "L40" to "4:40-5:30",
            "L41" to "5:40-6:30",
            "L42" to "6:30-7:20",
        ),
        "WED" to mapOf(
            "C1" to "8:00-8:50",
            "A1" to "8:55-9:45",
            "F1" to "9:50-10:40",
            "TD1" to "10:45-11:35",
            "TBB1" to "11:40-12:30",
            "L18" to "12:30-1:20",
            "C2" to "2:00-2:50",
            "A2" to "2:55-3:45",
            "F2" to "3:50-4:40",
            "TD2" to "4:45-5:35",
            "TBB2" to "5:40-6:30",
            "S4" to "6:35-7:25",
            "L13" to "8:00-8:50",
            "L14" to "8:50-9:40",
            "L15" to "9:50-10:40",
            "L16" to "10:40-11:30",
            "L17" to "11:40-12:30",
            "L43" to "2:00-2:50",
            "L44" to "2:50-3:40",
            "L45" to "3:50-4:40",
            "L46" to "4:40-5:30",
            "L47" to "5:40-6:30",
            "L48" to "6:30-7:20",
        ),
        "THU" to mapOf(
            "D1" to "8:00-8:50",
            "B1" to "8:55-9:45",
            "G1" to "9:50-10:40",
            "TE1" to "10:45-11:35",
            "TCC1" to "11:40-12:30",
            "L24" to "12:30-1:20",
            "D2" to "2:00-2:50",
            "B2" to "2:55-3:45",
            "G2" to "3:50-4:40",
            "TE2" to "4:45-5:35",
            "TCC2" to "5:40-6:30",
            "S2" to "6:35-7:25",
            "L19" to "8:00-8:50",
            "L20" to "8:50-9:40",
            "L21" to "9:50-10:40",
            "L22" to "10:40-11:30",
            "L23" to "11:40-12:30",
            "L49" to "2:00-2:50",
            "L50" to "2:50-3:40",
            "L51" to "3:50-4:40",
            "L52" to "4:40-5:30",
            "L53" to "5:40-6:30",
            "L54" to "6:30-7:20",
        ),
        "FRI" to mapOf(
            "E1" to "8:00-8:50",
            "C1" to "8:55-9:45",
            "TA1" to "9:50-10:40",
            "TF1" to "10:45-11:35",
            "TDD1" to "11:40-12:30",
            "S15" to "12:35-1:25",
            "E2" to "2:00-2:50",
            "C2" to "2:55-3:45",
            "TA2" to "3:50-4:40",
            "TF2" to "4:45-5:35",
            "TDD2" to "5:40-6:30",
            "L25" to "8:00-8:50",
            "L26" to "8:50-9:40",
            "L27" to "9:50-10:40",
            "L28" to "10:40-11:30",
            "L29" to "11:40-12:30",
            "L30" to "12:30-1:20",
            "L55" to "2:00-2:50",
            "L56" to "2:50-3:40",
            "L57" to "3:50-4:40",
            "L58" to "4:40-5:30",
            "L59" to "5:40-6:30",
            "L60" to "6:30-7:20",
        ),
        "SAT" to mapOf(
            "X11" to "8:00-8:50",
            "X12" to "8:55-9:45",
            "Y11" to "9:50-10:40",
            "Y12" to "10:45-11:35",
            "S8" to "11:40-12:30",
            "S8B" to "12:35-1:25",
            "X21" to "2:00-2:50",
            "Z21" to "2:55-3:45",
            "Y21" to "3:50-4:40",
            "W21" to "4:45-5:35",
            "W22" to "5:40-6:30",
            "Z22" to "6:35-7:25",
            "L71" to "8:00-8:50",
            "L72" to "8:50-9:40",
            "L73" to "9:50-10:40",
            "L74" to "10:40-11:30",
            "L75" to "11:40-12:30",
            "L76" to "12:30-1:20",
            "L77" to "2:00-2:50",
            "L78" to "2:50-3:40",
            "L79" to "3:50-4:40",
            "L80" to "4:40-5:30",
            "L81" to "5:40-6:30",
            "L82" to "6:30-7:20",
        ),
        "SUN" to mapOf(
            "Y11" to "8:00-8:50",
            "Y12" to "8:55-9:45",
            "X11" to "9:50-10:40",
            "X12" to "10:45-11:35",
            "S10" to "11:40-12:30",
            "S10B" to "12:35-1:25",
            "Y21" to "2:00-2:50",
            "Z21" to "2:55-3:45",
            "X21" to "3:50-4:40",
            "W21" to "4:45-5:35",
            "W22" to "5:40-6:30",
            "Z22" to "6:35-7:25",
            "L83" to "8:00-8:50",
            "L84" to "8:50-9:40",
            "L85" to "9:50-10:40",
            "L86" to "10:40-11:30",
            "L87" to "11:40-12:30",
            "L88" to "12:30-1:20",
            "L89" to "2:00-2:50",
            "L90" to "2:50-3:40",
            "L91" to "3:50-4:40",
            "L92" to "4:40-5:30",
            "L93" to "5:40-6:30",
            "L94" to "6:30-7:20",
        ),
    )

    /** Total number of slots across all 7 days (24+23+23+23+23+24+24 = 164). */
    const val TOTAL_SLOTS: Int = 164

    /** Slots on one day. The per-day sum equals [TOTAL_SLOTS]. */
    fun totalSlotsAcrossDay(day: String): Int = map[normaliseDay(day) ?: day.uppercase()]?.size ?: 0

    /** `"DAY:SLOTID"`, e.g. `"MON:A1"` or `"MON:TEE"`. */
    fun slotKey(day: String, slotId: String): String =
        "${normaliseDay(day) ?: day.uppercase()}:${com.amazecc.app.shared.domain.normaliseSlot(slotId)}"

    /** Parses `"DAY:SLOTID"` into a `(day, slotId)` pair. */
    fun parseSlotKey(key: String): Pair<String, String>? {
        val i = key.indexOf(':')
        if (i <= 0 || i == key.length - 1) return null
        val d = normaliseDay(key.substring(0, i)) ?: key.substring(0, i).uppercase()
        val s = com.amazecc.app.shared.domain.normaliseSlot(key.substring(i + 1))
        return d to s
    }

    /** True when the key resolves against the vocabulary (under direct or Law School spelling). */
    fun isValidKey(key: String): Boolean {
        val (day, slotId) = parseSlotKey(key) ?: return false
        return getTime(day, slotId) != null
    }

    /**
     * `"8:00"` -> 480.
     * Rule: In `config.json`, hours 1-7 are PM, 8-11 are AM, and 12 is PM.
     */
    fun toMinutes(t: String?): Int {
        if (t.isNullOrBlank()) return 0
        val parts = t.trim().split(":")
        val hs = parts.getOrNull(0) ?: "0"
        val ms = parts.getOrNull(1) ?: "0"
        var h = hs.toIntOrNull() ?: 0
        val m = ms.toIntOrNull() ?: 0
        if (h == 12 || (h in 1..7)) {
            if (h != 12) h += 12
        }
        return h * 60 + m
    }

    /** Minutes -> `"1:20 PM"`, for display. */
    fun minutesToTimeStr(mins: Int): String {
        if (mins < 0) return ""
        var h = (mins / 60) % 24
        val m = mins % 60
        val ampm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0) h = 12
        val mm = m.toString().padStart(2, '0')
        return "$h:$mm $ampm"
    }

    fun fmt(t: String?): String = minutesToTimeStr(toMinutes(t))

    fun slotRange(time: String?): Pair<Int, Int> {
        if (time == null || !time.contains("-")) return 0 to 0
        val parts = time.split("-")
        return toMinutes(parts.getOrNull(0)) to toMinutes(parts.getOrNull(1))
    }

    fun slotSpanMinutes(time: String?): Int {
        val (start, end) = slotRange(time)
        return maxOf(0, end - start)
    }

    fun slotSpanHours(time: String?): Double = slotSpanMinutes(time) / 60.0

    val FULL_DAY_TO_KEY: Map<String, String> = mapOf(
        "monday" to "MON", "tuesday" to "TUE", "wednesday" to "WED",
        "thursday" to "THU", "friday" to "FRI", "saturday" to "SAT", "sunday" to "SUN",
        "mon" to "MON", "tue" to "TUE", "wed" to "WED",
        "thu" to "THU", "fri" to "FRI", "sat" to "SAT", "sun" to "SUN"
    )

    fun normaliseDay(day: String?): String? {
        if (day == null) return null
        val trimmed = day.trim().lowercase()
        return FULL_DAY_TO_KEY[trimmed] ?: day.trim().uppercase().takeIf { it in days }
    }

    /**
     * Resolves the slot time string (e.g. `"8:00-8:50"`) for a given day and slot,
     * fully integrated with the Law School schema translator:
     * - `A` -> `A1`
     * - `TEE` -> `S11`
     * - `TFF` -> `S15`
     * - `TAA` -> `TAA1`, etc.
     */
    fun getTime(day: String, slotId: String): String? {
        val d = normaliseDay(day) ?: return null
        val daySlots = map[d] ?: return null
        val norm = com.amazecc.app.shared.domain.normaliseSlot(slotId)
        val direct = daySlots[norm]
        if (direct != null) return direct

        // Law School translation buddy (checks all valid spellings)
        val spellings = com.amazecc.app.shared.domain.slotSpellings(norm)
        for (spelling in spellings) {
            val t = daySlots[spelling]
            if (t != null) return t
        }
        return null
    }

    /**
     * Resolves the start and end minutes for a compound slot (e.g. `"A1+TA1"` or `"A+TA"` or `"L31+L32"`) on a given day.
     */
    fun resolveRange(day: String, slotStr: String): Pair<Int, Int>? {
        val tokens = com.amazecc.app.shared.domain.splitSlots(slotStr)
        if (tokens.isEmpty()) return null
        var minStart: Int? = null
        var maxEnd: Int? = null
        for (token in tokens) {
            val rawTime = getTime(day, token) ?: continue
            val (s, e) = slotRange(rawTime)
            if (s > 0 || e > 0) {
                if (minStart == null || s < minStart) minStart = s
                if (maxEnd == null || e > maxEnd) maxEnd = e
            }
        }
        return if (minStart != null && maxEnd != null) minStart to maxEnd else null
    }

    /**
     * Returns all days where the slot (under either schema or Law School spelling) occurs.
     */
    fun daysForSlot(slotId: String): List<String> {
        val norm = com.amazecc.app.shared.domain.normaliseSlot(slotId)
        val spellings = com.amazecc.app.shared.domain.slotSpellings(norm).toSet()
        return days.filter { day ->
            val daySlots = map[day] ?: return@filter false
            spellings.any { daySlots.containsKey(it) }
        }
    }

    /**
     * Finds the slot covering the specified day and minute of day (0..1439).
     */
    fun slotCoveringMinute(day: String, minute: Int): String? {
        val d = normaliseDay(day) ?: return null
        val daySlots = map[d] ?: return null
        for ((slotId, rawTime) in daySlots) {
            val (start, end) = slotRange(rawTime)
            if (minute in start until end) {
                return slotKey(d, slotId)
            }
        }
        return null
    }
}
