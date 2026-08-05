package com.amazecc.app.shared.utils

import kotlinx.datetime.*
import kotlinx.serialization.json.*

object AnalyzeCalendar {

    private val HOLIDAY_KEYWORDS = listOf(
        "holiday", "pooja", "puja", "ayudha", "diwali", "pongal", "eid", "christmas", "good friday",
        "independence", "republic", "onam", "holi", "ramadan", "ganesh", "maha shivaratri", "vesak",
        "vacation", "term end", "no instructional", "noinstructional"
    )

    private fun normalize(str: String?): String {
        if (str == null) return ""
        val regex = Regex("[^a-z0-9\\s]")
        return regex.replace(str.lowercase(), " ").trim()
    }

    private fun isHolidayEvent(e: JsonObject?): Boolean {
        if (e == null) return false
        val type = (e["type"]?.jsonPrimitive?.content ?: "").lowercase()
        val text = normalize(e["text"]?.jsonPrimitive?.content)
        val cat = normalize(e["category"]?.jsonPrimitive?.content)
        
        if (type.contains("holiday")) return true
        if (type.contains("no instructional")) return true
        if (cat.contains("no instructional")) return true
        
        for (kw in HOLIDAY_KEYWORDS) {
            if (text.contains(kw) || cat.contains(kw)) return true
        }
        return false
    }

    private fun isInstructionalEvent(e: JsonObject?): Boolean {
        if (e == null) return false
        val type = (e["type"]?.jsonPrimitive?.content ?: "").lowercase()
        val cat = normalize(e["category"]?.jsonPrimitive?.content)
        
        if (type == "instructional day") return true
        if (cat.contains("working")) return true
        return false
    }

    private val MONTH_NAME_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    data class AnalyzedDay(
        val date: Int,
        val weekday: String,
        val type: String, // "working", "holiday", "other"
        val events: JsonArray
    )

    data class CalendarSummary(
        val total: Int,
        var working: Int,
        var holiday: Int,
        var other: Int
    )

    data class CalendarResult(
        val month: String,
        val year: Int,
        val days: MutableList<AnalyzedDay> = mutableListOf(),
        val summary: CalendarSummary
    )

    data class ImportantEvent(
        val event: String,
        val date: Int,
        val weekday: String,
        val month: String,
        val year: Int
    )

    data class AnalyzeCalendarReturn(
        val result: CalendarResult,
        val importantEvents: Map<String, ImportantEvent>
    )

    data class AnalyzeAllCalendarsReturn(
        val results: List<CalendarResult>,
        val importantEvents: Map<String, ImportantEvent>
    )

    fun analyzeCalendar(calendar: JsonObject? = null): AnalyzeCalendarReturn {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        
        // ---- YEAR ----
        val yearStr = calendar?.get("month")?.jsonPrimitive?.content?.split(" ")?.lastOrNull()
            ?: calendar?.get("year")?.jsonPrimitive?.content
        val year = yearStr?.toIntOrNull() ?: now.year

        // ---- MONTH ----
        var monthIndex: Int = now.monthNumber
        try {
            val mRaw = calendar?.get("month")?.jsonPrimitive?.content
            if (mRaw == null) {
                monthIndex = now.monthNumber
            } else {
                val mInt = mRaw.toIntOrNull()
                if (mInt != null) {
                    if (mInt in 1..12) monthIndex = mInt
                } else {
                    val prefix = mRaw.lowercase().take(3)
                    monthIndex = MONTH_NAME_MAP[prefix] ?: now.monthNumber
                }
            }
        } catch (_: Exception) {
            monthIndex = now.monthNumber
        }

        // ---- DATES ----
        val daysInMonth = mutableListOf<LocalDate>()
        try {
            var currentDate = LocalDate(year, monthIndex, 1)
            val endMonth = currentDate.month
            while (currentDate.month == endMonth) {
                daysInMonth.add(currentDate)
                currentDate = currentDate.plus(DatePeriod(days = 1))
            }
        } catch (_: Exception) {
            val totalDays = calendar?.get("totalDays")?.jsonPrimitive?.content?.toIntOrNull() ?: 31
            for (i in 1..totalDays) {
                daysInMonth.add(LocalDate(year, monthIndex, i))
            }
        }

        // ---- OUTPUT ----
        val monthNameStr = calendar?.get("month")?.jsonPrimitive?.content ?: daysInMonth.firstOrNull()?.month?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
        val result = CalendarResult(
            month = monthNameStr,
            year = year,
            summary = CalendarSummary(daysInMonth.size, 0, 0, 0)
        )

        val calDaysArray = calendar?.get("days") as? JsonArray ?: JsonArray(emptyList())

        for (dateObj in daysInMonth) {
            val date = dateObj.dayOfMonth
            val dayName = when (dateObj.dayOfWeek) {
                DayOfWeek.SUNDAY -> "Sun"
                DayOfWeek.MONDAY -> "Mon"
                DayOfWeek.TUESDAY -> "Tue"
                DayOfWeek.WEDNESDAY -> "Wed"
                DayOfWeek.THURSDAY -> "Thu"
                DayOfWeek.FRIDAY -> "Fri"
                DayOfWeek.SATURDAY -> "Sat"
                else -> "Sun"
            }

            var dayInfo: JsonObject? = null
            for (elem in calDaysArray) {
                val d = elem.jsonObject
                if (d["date"]?.jsonPrimitive?.content?.toIntOrNull() == date) {
                    dayInfo = d
                    break
                }
            }

            val events = dayInfo?.get("events") as? JsonArray ?: JsonArray(emptyList())

            val hasHoliday = events.any { isHolidayEvent(it.jsonObject) }
            val hasInstructional = events.any { isInstructionalEvent(it.jsonObject) }
            val isEmpty = events.isEmpty()

            var dayType = "other"
            if (hasHoliday || isEmpty) dayType = "holiday"
            else if (hasInstructional) dayType = "working"

            result.days.add(AnalyzedDay(
                date = date,
                weekday = dayName,
                type = dayType,
                events = events
            ))

            when (dayType) {
                "working" -> result.summary.working++
                "holiday" -> result.summary.holiday++
                "other" -> result.summary.other++
            }
        }

        val importantEventNames = listOf(
            mapOf("key" to "cat i", "display" to "CAT I", "aliases" to "cat-1,cat 1"),
            mapOf("key" to "cat ii", "display" to "CAT II", "aliases" to "cat-2,cat 2"),
            mapOf("key" to "cat iii", "display" to "CAT III", "aliases" to "cat-3,cat 3"),
            mapOf("key" to "fat", "display" to "FAT"),
            mapOf("key" to "lid for laboratory classes", "display" to "LID FOR LABORATORY CLASSES", "aliases" to "lid for lab"),
            mapOf("key" to "lid for theory classes", "display" to "LID FOR THEORY CLASSES"),
            mapOf("key" to "mid term test", "display" to "MID TERM TEST")
        )

        val importantEvents = mutableMapOf<String, ImportantEvent>()

        for (day in result.days) {
            for (evElem in day.events) {
                val ev = evElem.jsonObject
                val text = normalize(ev["text"]?.jsonPrimitive?.content)
                
                for (impDef in importantEventNames) {
                    val key = impDef["key"] as String
                    val display = impDef["display"] as String
                    val aliases = impDef["aliases"]?.split(",") ?: emptyList()
                    
                    val matched = text.contains(key) || aliases.any { text.contains(it) }
                    if (matched && !importantEvents.containsKey(key)) {
                        importantEvents[key] = ImportantEvent(
                            event = display,
                            date = day.date,
                            weekday = day.weekday,
                            month = result.month,
                            year = result.year
                        )
                    }
                }
            }
        }
        
        return AnalyzeCalendarReturn(result, importantEvents)
    }

    fun analyzeAllCalendars(calendars: JsonElement?): AnalyzeAllCalendarsReturn {
        if (calendars == null) return AnalyzeAllCalendarsReturn(emptyList(), emptyMap())
        
        val calArray = if (calendars is JsonArray) {
            calendars
        } else if (calendars is JsonObject && calendars.containsKey("calendars")) {
            calendars["calendars"] as? JsonArray ?: JsonArray(emptyList())
        } else if (calendars is JsonObject) {
            JsonArray(listOf(calendars))
        } else {
            JsonArray(emptyList())
        }

        val results = mutableListOf<CalendarResult>()
        val importantEvents = mutableMapOf<String, ImportantEvent>()

        for (elem in calArray) {
            val cal = elem.jsonObject
            val monthsArray = cal["months"] as? JsonArray ?: JsonArray(listOf(cal))
            for (monthElem in monthsArray) {
                val monthObj = monthElem.jsonObject
                val (result, imp) = analyzeCalendar(monthObj)
                results.add(result)
                for ((key, valImp) in imp) {
                    if (!importantEvents.containsKey(key)) {
                        importantEvents[key] = valImp
                    }
                }
            }
        }

        return AnalyzeAllCalendarsReturn(results, importantEvents)
    }
}
