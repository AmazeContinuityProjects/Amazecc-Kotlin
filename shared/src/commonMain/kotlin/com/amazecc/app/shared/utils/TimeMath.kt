package com.amazecc.app.shared.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object TimeMath {

    fun toMinutes(timeStr: String?): Int {
        if (timeStr.isNullOrBlank()) return 0
        val parts = timeStr.split(":")
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
