package com.amazecc.app.shared.ui.screens.academics

import com.amazecc.app.shared.domain.Exam
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.utils.AttendanceTimetable
import kotlin.math.round

fun pctFormatted(value: Double): String {
    val i = round(value * 100).toLong()
    val whole = i / 100
    val frac = (i % 100).coerceIn(0, 99)
    return "$whole.${frac.toString().padStart(2, '0')}%"
}

data class SimpleDate(val month: Int, val day: Int, val year: Int)

fun computeImportantDates(months: List<CalendarMonth>, examSchedule: Map<String, List<Exam>> = emptyMap()): Map<String, SimpleDate> {
    val monthIndex = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    val imp = mutableMapOf<String, SimpleDate>()
    val catIPattern = Regex("cat\\s*[-–]?\\s*i(?!i)", RegexOption.IGNORE_CASE)
    val catIIPattern = Regex("cat\\s*[-–]?\\s*ii", RegexOption.IGNORE_CASE)
    val fatPattern = Regex("\\bfat\\b", RegexOption.IGNORE_CASE)
    val lidLabPattern = Regex("lid\\s+for\\s+laboratory classes", RegexOption.IGNORE_CASE)
    val lidTheoryPattern = Regex("lid\\s+for\\s+theory classes", RegexOption.IGNORE_CASE)

    fun oneDayBefore(m: Int, d: Int, y: Int): SimpleDate {
        return try {
            if (d > 1) {
                SimpleDate(m, d - 1, y)
            } else {
                val prevMonth = if (m > 1) m - 1 else 12
                val prevYear = if (m > 1) y else y - 1
                val daysInPrevMonth = when (prevMonth) {
                    1, 3, 5, 7, 8, 10, 12 -> 31
                    4, 6, 9, 11 -> 30
                    2 -> if (prevYear % 4 == 0 && (prevYear % 100 != 0 || prevYear % 400 == 0)) 29 else 28
                    else -> 30
                }
                SimpleDate(prevMonth, daysInPrevMonth, prevYear)
            }
        } catch (_: Exception) { SimpleDate(m, d, y) }
    }

    for (month in months) {
        val monthStr = month.month.lowercase()
        val m = monthIndex[monthStr.take(3)] ?: continue
        val y = monthStr.split(" ").lastOrNull()?.toIntOrNull() ?: continue
        for (day in month.days) {
            for (ev in day.events) {
                val text = ev.text
                when {
                    catIIPattern.containsMatchIn(text) && !imp.containsKey("cat ii") ->
                        imp["cat ii"] = oneDayBefore(m, day.date, y)
                    catIPattern.containsMatchIn(text) && !imp.containsKey("cat i") ->
                        imp["cat i"] = oneDayBefore(m, day.date, y)
                    fatPattern.containsMatchIn(text) && !imp.containsKey("fat") ->
                        imp["fat"] = SimpleDate(m, day.date, y)
                    lidLabPattern.containsMatchIn(text) && !imp.containsKey("lid for laboratory classes") ->
                        imp["lid for laboratory classes"] = SimpleDate(m, day.date, y)
                    lidTheoryPattern.containsMatchIn(text) && !imp.containsKey("lid for theory classes") ->
                        imp["lid for theory classes"] = SimpleDate(m, day.date, y)
                }
            }
        }
    }
    if (!imp.containsKey("cat i") || !imp.containsKey("cat ii") || !imp.containsKey("fat")) {
        for ((_, items) in examSchedule) {
            for (item in items) {
                val parts = item.date.split("-")
                if (parts.size == 3) {
                    val ey = parts[0].toIntOrNull() ?: continue
                    val em = parts[1].toIntOrNull() ?: continue
                    val ed = parts[2].toIntOrNull() ?: continue
                    val label = item.courseTitle
                    when {
                        catIIPattern.containsMatchIn(label) && !imp.containsKey("cat ii") ->
                            imp["cat ii"] = oneDayBefore(em, ed, ey)
                        catIPattern.containsMatchIn(label) && !imp.containsKey("cat i") ->
                            imp["cat i"] = oneDayBefore(em, ed, ey)
                        fatPattern.containsMatchIn(label) && !imp.containsKey("fat") ->
                            imp["fat"] = SimpleDate(em, ed, ey)
                    }
                }
            }
        }
    }
    return imp
}

fun buildWorkingDays(months: List<CalendarMonth>): List<Triple<Int, Int, Int>> {
    val monthIndex = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    val results = mutableListOf<Triple<Int, Int, Int>>()
    for (month in months) {
        val monthStr = month.month.lowercase()
        val m = monthIndex[monthStr.take(3)] ?: continue
        val y = monthStr.split(" ").lastOrNull()?.toIntOrNull() ?: continue
        for (day in month.days) {
            val isWorking = day.events.any {
                val t = it.type.lowercase()
                val txt = it.text.lowercase()
                t == "instructional day" || txt.contains("instructional day") || txt.contains("working") || txt.contains("order") || AttendanceTimetable.getDayOrderLabelFromText(it.text) != null
            }
            val isHoliday = day.events.any {
                val t = it.type.lowercase()
                val txt = it.text.lowercase()
                t.contains("holiday") || txt.contains("holiday") || txt.contains("pooja") || txt.contains("vacation")
            }
            if (isWorking && !isHoliday) {
                results.add(Triple(y, m, day.date))
            }
        }
    }
    return results
}
