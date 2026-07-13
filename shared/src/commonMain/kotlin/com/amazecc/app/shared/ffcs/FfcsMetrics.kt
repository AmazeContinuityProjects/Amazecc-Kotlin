package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath
import kotlin.math.max

object FfcsMetrics {
    
    fun calculateTimetableMetrics(courses: List<AddedCourse>): TimetableMetrics {
        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        var halfDays = 0
        var totalGaps = 0
        val gapsPerDay = mutableMapOf<String, Int>()

        days.forEach { day ->
            val dayMap = SlotMap.map[day] ?: emptyMap()
            val daySlots = mutableListOf<Pair<Int, Int>>()

            courses.forEach { course ->
                course.slots.forEach { slot ->
                    val timeStr = dayMap[slot]
                    if (timeStr != null) {
                        val parts = timeStr.split("-")
                        if (parts.size == 2) {
                            daySlots.add(Pair(TimeMath.toMinutes(parts[0]), TimeMath.toMinutes(parts[1])))
                        }
                    }
                }
            }

            if (daySlots.isEmpty()) {
                halfDays++ // Completely free day counts as a half day conceptually (or 2 half days in React, let's say 1)
            } else {
                daySlots.sortBy { it.first }
                val merged = mutableListOf<Pair<Int, Int>>()
                daySlots.forEach { slot ->
                    if (merged.isEmpty()) {
                        merged.add(slot)
                    } else {
                        val last = merged.last()
                        if (max(last.first, slot.first) < kotlin.math.min(last.second, slot.second) || kotlin.math.abs(last.second - slot.first) <= 10) {
                            merged[merged.size - 1] = Pair(last.first, max(last.second, slot.second))
                        } else {
                            merged.add(slot)
                        }
                    }
                }

                val LUNCH_START = 800
                val LUNCH_END = 840
                
                // Calculate gaps
                var gapsToday = 0
                for (i in 0 until merged.size - 1) {
                    val gapStart = merged[i].second
                    val gapEnd = merged[i+1].first
                    var gapMins = gapEnd - gapStart
                    
                    if (gapStart <= LUNCH_START && gapEnd >= LUNCH_END) {
                        gapMins -= 40
                    }
                    if (gapMins > 10) {
                        gapsToday += (gapMins / 60)
                    }
                }
                
                totalGaps += gapsToday
                gapsPerDay[day] = gapsToday

                // Calculate half day
                val firstClass = merged.first().first
                val lastClass = merged.last().second
                if (lastClass <= LUNCH_START || firstClass >= LUNCH_END) {
                    halfDays++
                }
            }
        }

        return TimetableMetrics(halfDays, totalGaps, gapsPerDay)
    }
}
