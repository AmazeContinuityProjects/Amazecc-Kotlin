package com.amazecc.app.shared.utils

import com.amazecc.app.shared.model.CalendarRes

object IcsUtils {
    fun generateIcs(calendarData: CalendarRes): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//AmazeCC//Academic Calendar//EN")
        
        calendarData.months.forEach { month ->
            val monthStr = month.month
            val parts = monthStr.split(" ")
            val mName = parts.firstOrNull() ?: ""
            val year = parts.lastOrNull()?.toIntOrNull() ?: 2026
            val monthNum = getMonthNum(mName)

            month.days.forEach { day ->
                val dayNum = day.date
                day.events.forEach { event ->
                    sb.appendLine("BEGIN:VEVENT")
                    sb.appendLine("SUMMARY:${event.text}")
                    if (event.category.isNotBlank()) {
                        sb.appendLine("DESCRIPTION:${event.category}")
                    }
                    val dateString = "${year.toString().padStart(4, '0')}${monthNum.toString().padStart(2, '0')}${dayNum.toString().padStart(2, '0')}"
                    sb.appendLine("DTSTART;VALUE=DATE:$dateString")
                    sb.appendLine("DTEND;VALUE=DATE:$dateString")
                    sb.appendLine("END:VEVENT")
                }
            }
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun getMonthNum(monthName: String): Int {
        val m = monthName.lowercase()
        return when {
            m.startsWith("jan") -> 1
            m.startsWith("feb") -> 2
            m.startsWith("mar") -> 3
            m.startsWith("apr") -> 4
            m.startsWith("may") -> 5
            m.startsWith("jun") -> 6
            m.startsWith("jul") -> 7
            m.startsWith("aug") -> 8
            m.startsWith("sep") -> 9
            m.startsWith("oct") -> 10
            m.startsWith("nov") -> 11
            m.startsWith("dec") -> 12
            else -> 1
        }
    }
}
