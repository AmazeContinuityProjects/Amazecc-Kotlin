package com.amazecc.app.shared.ffcs

import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.utils.TimeMath
import kotlin.math.max
import kotlin.math.min

object FfcsMetrics {

    fun calculateTimetableMetrics(courses: List<AddedCourse>): TimetableMetrics {
        val days = listOf("MON", "TUE", "WED", "THU", "FRI")
        var halfDays = 0
        var totalGaps = 0
        val gapsPerDay = mutableMapOf<String, Int>()
        val gapDetails = mutableListOf<GapDetail>()
        var buildingDashes = 0
        val dashDetails = mutableListOf<DashDetail>()
        var isLongWeekend = true

        val LUNCH_START = 800
        val LUNCH_END = 840
        val BLOCKS = mapOf(
            "AB1" to "Academic Block 1",
            "AB2" to "Academic Block 2",
            "AB3" to "Academic Block 3",
            "AB4" to "Academic Block 4",
            "MAB" to "MBA Block",
            "ADB" to "Admin Block",
            "SJT" to "SJT Block",
            "TT" to "Tech Tower",
            "SMV" to "SMV Block",
            "MB" to "Main Building",
            "CDMM" to "CDMM Block",
            "CRC" to "CRC Block"
        )

        fun extractBlock(venue: String): String? {
            val clean = venue.trim().uppercase()
            for ((prefix, _) in BLOCKS) {
                if (clean.startsWith(prefix)) return prefix
            }
            return null
        }

        days.forEach { day ->
            val dayMap = SlotMap.map[day] ?: emptyMap()
            val daySlots = mutableListOf<Triple<Int, Int, AddedCourse>>()

            courses.forEach { course ->
                course.slots.forEach { slot ->
                    val timeStr = dayMap[slot]
                    if (timeStr != null) {
                        val parts = timeStr.split("-")
                        if (parts.size == 2) {
                            daySlots.add(
                                Triple(
                                    TimeMath.toMinutes(parts[0]),
                                    TimeMath.toMinutes(parts[1]),
                                    course
                                )
                            )
                        }
                    }
                }
            }

            if (daySlots.isEmpty()) {
                halfDays++
                return@forEach
            }

            isLongWeekend = false

            daySlots.sortBy { it.first }
            val merged = mutableListOf<Pair<Int, Int>>()
            val mergedCourses = mutableListOf<AddedCourse>()

            daySlots.forEach { (start, end, course) ->
                if (merged.isEmpty()) {
                    merged.add(Pair(start, end))
                    mergedCourses.add(course)
                } else {
                    val last = merged.last()
                    if (start <= last.second + 10) {
                        merged[merged.size - 1] = Pair(last.first, max(last.second, end))
                    } else {
                        merged.add(Pair(start, end))
                        mergedCourses.add(course)
                    }
                }
            }

            var gapsToday = 0
            for (i in 0 until merged.size - 1) {
                val gapStart = merged[i].second
                val gapEnd = merged[i + 1].first
                var gapMins = gapEnd - gapStart

                if (gapStart <= LUNCH_START && gapEnd >= LUNCH_END) {
                    gapMins -= 40
                }

                if (gapMins > 10) {
                    gapsToday += gapMins / 60
                    gapDetails.add(
                        GapDetail(
                            day = day,
                            startMin = gapStart,
                            endMin = gapEnd,
                            durationMins = gapMins,
                            fromClass = mergedCourses.getOrNull(i)?.code,
                            toClass = mergedCourses.getOrNull(i + 1)?.code
                        )
                    )
                }
            }

            totalGaps += gapsToday
            gapsPerDay[day] = gapsToday

            val firstClass = merged.first().first
            val lastClass = merged.last().second
            if (lastClass <= LUNCH_START || firstClass >= LUNCH_END) {
                halfDays++
            }

            val dayCourses = daySlots.map { it.third }.distinct()
            for (i in 0 until dayCourses.size - 1) {
                val fromBlock = extractBlock(dayCourses[i].venue)
                val toBlock = extractBlock(dayCourses[i + 1].venue)
                if (fromBlock != null && toBlock != null && fromBlock != toBlock) {
                    buildingDashes++
                    dashDetails.add(
                        DashDetail(
                            fromClass = dayCourses[i].code,
                            toClass = dayCourses[i + 1].code,
                            fromTime = dayCourses[i].slots.firstOrNull() ?: "",
                            toTime = dayCourses[i + 1].slots.firstOrNull() ?: "",
                            day = day,
                            fromBlock = fromBlock,
                            toBlock = toBlock
                        )
                    )
                }
            }
        }

        val mondayCourses = courses.filter { c ->
            c.slots.any { SlotMap.map["MON"]?.containsKey(it) == true }
        }
        val fridayCourses = courses.filter { c ->
            c.slots.any { SlotMap.map["FRI"]?.containsKey(it) == true }
        }
        if (fridayCourses.isEmpty() || mondayCourses.isEmpty()) {
            isLongWeekend = true
        }

        return TimetableMetrics(
            halfDays = halfDays,
            gaps = totalGaps,
            gapsPerDay = gapsPerDay,
            gapDetails = gapDetails,
            buildingDashes = buildingDashes,
            dashDetails = dashDetails,
            socialScore = 0,
            isLongWeekend = isLongWeekend
        )
    }
}
