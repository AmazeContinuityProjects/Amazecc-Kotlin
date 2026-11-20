package com.amazecc.app.shared.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The one place a time string becomes minutes since midnight.
 *
 * VTOP and the app disagree about how times are written, so there is deliberately more than one
 * entry point and each names its input format. Collapsing them into a single function would be
 * wrong rather than tidy: the formats are genuinely ambiguous with each other.
 *
 * | function | input | `"07:00"` | `"6:00 PM"` |
 * |---|---|---|---|
 * | [toMinutes] | bare VIT slot, no meridian | 1140 | 1080 (meridian ignored) |
 * | [toMilitaryMinutes] | strict 24-hour `HH:mm` | 420 | null |
 * | [toClockMinutes] | `HH:MM [AM|PM]` | null | 1080 |
 *
 * [toMinutes] is the VIT timetable heuristic: a bare `1`-`7` is an afternoon period, because VIT
 * slot maps never schedule before 8 AM and always write the meridian nowhere. [toMilitaryMinutes]
 * deliberately has no such rule - `TaskModels.startTime` is documented as `HH:mm`, and running the
 * heuristic over it would turn 07:00 into 19:00.
 *
 * [parseTimeToMinutes] is the one **web-parity** entry point, mirroring `timeUtils.ts`. It has no
 * VIT heuristic: a bare `08:00` is 08:00, and an explicit `am`/`pm` is honoured. New callers that
 * consume web-shaped data should use it rather than picking one of the three legacy formats.
 */
object TimeMath {

    /** Bare VIT slot time: `"9:00"`, `"12:30"`. Applies the afternoon heuristic, never null. */
    fun toMinutes(timeStr: String?): Int {
        if (timeStr.isNullOrBlank()) return 0
        // Trim first: callers split "9:00 - 9:50" on "-" and hand over " 9:50", where
        // " 9".toIntOrNull() is null and the hour would silently become 0.
        val parts = timeStr.trim().split(":")
        val hs = parts.getOrNull(0) ?: "0"
        val ms = parts.getOrNull(1) ?: "0"

        var h = hs.toIntOrNull() ?: 0
        val m = ms.toIntOrNull() ?: 0
        
        // 12-hour format mapping for VIT timetable
        // If hour is between 1 and 7 (inclusive), we assume PM (13:00 - 19:00).
        // 12 is 12:00 PM.
        // 8 to 11 are AM.
        val isPM = h == 12 || (h in 1..7)
        if (isPM && h != 12) h += 12
        
        return h * 60 + m
    }

    /** Strict 24-hour `HH:mm`: `"07:00"` -> 420, `"18:30"` -> 1110. Null when unparseable. */
    fun toMilitaryMinutes(timeStr: String?): Int? {
        val parts = timeStr?.trim()?.split(":") ?: return null
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** `"06:00 PM"` -> 1080, `"12:00 AM"` -> 0. Null when there is no usable clock time. */
    fun toClockMinutes(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        val match = CLOCK.find(timeStr) ?: return null
        var h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        val meridian = match.groupValues[3].uppercase()
        if (h !in 1..12 || m !in 0..59) return null
        if (meridian == "PM" && h != 12) h += 12
        if (meridian == "AM" && h == 12) h = 0
        return h * 60 + m
    }

    /**
     * The single web-parity parser (`timeUtils.ts parseTimeToMinutes`).
     *
     * Accepts an explicit meridian (`"8:00 AM"`, `"08:30pm"`), a bare 24-hour clock (`"08:00"`),
     * or a raw number of minutes; results are clamped to 0..1439. Unlike [toMinutes], a bare
     * `"07:00"` is 07:00 — no VIT afternoon shift.
     */
    fun parseTimeToMinutes(value: Any?): Int? {
        if (value == null) return null
        if (value is Number) {
            val minutes = value.toDouble()
            if (minutes.isNaN()) return null
            return clampMinutes(kotlin.math.floor(minutes).toInt())
        }

        val trimmed = value.toString().trim()
        if (trimmed.isEmpty()) return null

        TWELVE_HOUR.matchEntire(trimmed)?.let { match ->
            var hours = match.groupValues[1].toInt()
            val mins = match.groupValues[2].toInt()
            val isPm = match.groupValues[3].equals("pm", ignoreCase = true)
            if (hours == 12) hours = 0
            return clampMinutes((if (isPm) hours + 12 else hours) * 60 + mins)
        }

        TWENTY_FOUR_HOUR.matchEntire(trimmed)?.let { match ->
            val hours = match.groupValues[1].toInt()
            val mins = match.groupValues[2].toInt()
            return clampMinutes(hours * 60 + mins)
        }

        return null
    }

    private fun clampMinutes(value: Int): Int = maxOf(0, minOf(1439, value))

    private val TWELVE_HOUR = Regex("""^(\d{1,2}):(\d{2})\s*(am|pm)$""", RegexOption.IGNORE_CASE)
    private val TWENTY_FOUR_HOUR = Regex("""^(\d{1,2}):(\d{2})$""")

    /** `"9:00 - 9:50"` -> (540, 590), via [toMinutes]. Either side may fall back to 0. */
    fun toRange(timeRange: String?): Pair<Int, Int> {
        val parts = timeRange.orEmpty().split("-")
        return toMinutes(parts.getOrNull(0)) to toMinutes(parts.getOrNull(1))
    }

    /** Minutes since midnight right now, in the system time zone. */
    fun nowMinutes(): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.hour * 60 + now.minute
    }

    private val CLOCK = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""", RegexOption.IGNORE_CASE)

    fun minutesToTimeStr(mins: Int): String {
        var h = mins / 60
        val m = mins % 60
        val ampm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0) h = 12
        val mStr = m.toString().padStart(2, '0')
        return "$h:$mStr $ampm"
    }

    fun formatDuration(mins: Int): String {
        val hrs = mins / 60
        val remainingMins = mins % 60
        val out = StringBuilder()
        if (hrs > 0) {
            out.append("$hrs hr${if (hrs > 1) "s" else ""}")
        }
        if (remainingMins > 0) {
            if (out.isNotEmpty()) out.append(" ")
            out.append("$remainingMins min${if (remainingMins > 1) "s" else ""}")
        }
        return out.toString()
    }
}
