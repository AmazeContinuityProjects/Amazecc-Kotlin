package com.amazecc.app.shared.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Port of AmazeCC's `src/lib/attendancePredictor.ts`.
 *
 * Provides:
 * 1. Classes required calculation to reach target attendance (75%, 80%, 85%, etc.)
 * 2. Safe-to-miss (bunkable) margin calculation above threshold
 * 3. Schedule, milestone, and calendar projection engine
 */

const val CAT_LOCK_OFFSET_DAYS: Int = 3

data class MilestoneDates(
    val cat1Date: Long? = null,
    val cat2Date: Long? = null,
    val lidLabDate: Long? = null,
    val lidTheoryDate: Long? = null,
)

data class PredictorDayEvent(
    val text: String? = null,
    val category: String? = null,
    val title: String? = null,
    val detail: String? = null,
)

data class DayLike(
    val date: Long? = null,
    val weekday: String? = null,
    val events: List<PredictorDayEvent>? = null,
)

data class PredictorCourse(
    val courseCode: String,
    val courseTitle: String? = null,
    val courseType: String? = null,
    val slotName: String? = null,
    val faculty: String? = null,
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
)

data class ScheduleInput(
    val course: PredictorCourse,
    val dayCardsMap: Map<String, List<Map<String, String>>>? = null,
    val workingDays: List<DayLike> = emptyList(),
    val effectiveMap: Map<String, String> = emptyMap(),
    val dateStates: Map<Long, Int>? = null,
    val skips: Set<Long> = emptySet(),
    val ceiling: Long? = null,
    val lockDate: Long? = null,
    val threshold: Double = 75.0,
)

data class ScheduleResult(
    val futureDays: Int,
    val missedDays: Int,
    val meetingDays: List<String>,
    val attended: Int,
    val total: Int,
    val currentPct: Double,
    val predictedAttended: Int,
    val predictedTotal: Int,
    val predictedPct: Double,
    val deltaPct: Double,
    val safeBunks: Int,
    val classesNeeded: Int,
    val isLab: Boolean,
    val skippedCount: Int,
)

data class OverallSummary(
    val currentPct: Double,
    val predictedPct: Double,
    val delta: Double,
    val safeCount: Int,
    val atRiskCount: Int,
    val totalCourses: Int,
    val totalSafeBunks: Int,
    val remainingDays: Int,
)

object AttendancePredictor {

    /**
     * Calculate classes required to reach target attendance percentage (e.g. 75, 80, 85).
     * If attendance is already >= target, returns 0.
     */
    fun calculateClassesRequired(
        attended: Int,
        total: Int,
        targetPct: Double,
        isLab: Boolean = false,
    ): Int {
        if (total <= 0) return 0
        val ratio = targetPct / 100.0
        if (ratio <= 0.0 || ratio >= 1.0) return 0
        val currentPct = (attended.toDouble() / total.toDouble()) * 100.0
        if (currentPct >= targetPct) return 0
        val raw = ceil(((ratio * total - attended) / (1.0 - ratio)) - 1e-9).toInt()
        val needed = raw.coerceAtLeast(0)
        return if (isLab) ceil(needed / 2.0).toInt() else needed
    }

    /**
     * Calculate safe-to-miss margin (classes a student can skip while staying above threshold).
     * If attendance is below target, returns 0.
     */
    fun calculateSafeBunks(
        attended: Int,
        total: Int,
        targetPct: Double,
        isLab: Boolean = false,
    ): Int {
        if (total <= 0) return 0
        val ratio = targetPct / 100.0
        if (ratio <= 0.0 || ratio >= 1.0) return 0
        val currentPct = (attended.toDouble() / total.toDouble()) * 100.0
        if (currentPct < targetPct) return 0
        val raw = floor(((attended - ratio * total) / ratio) + 1e-9).toInt()
        val safe = raw.coerceAtLeast(0)
        return if (isLab) floor(safe / 2.0).toInt() else safe
    }

    /** Truncates epoch timestamp to midnight local/UTC day boundary. */
    fun startOfDay(timestamp: Long): Long {
        // One day in millis = 86400000
        val msPerDay = 86_400_000L
        val offset = java.util.TimeZone.getDefault().getOffset(timestamp)
        val localTime = timestamp + offset
        val startOfLocalDay = (localTime / msPerDay) * msPerDay
        return startOfLocalDay - offset
    }

    /** Stable join key: YYYY-M-D */
    fun dayKey(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return "$y-$m-$d"
    }

    fun makeTimestamp(year: Int, month0Indexed: Int, day: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, month0Indexed)
        cal.set(java.util.Calendar.DAY_OF_MONTH, day)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun lockDateFor(milestone: Long?, offsetDays: Int = 0): Long? {
        if (milestone == null) return null
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = milestone
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_MONTH, -offsetDays)
        return cal.timeInMillis
    }

    fun courseCeiling(mode: String, isLab: Boolean, dates: MilestoneDates): Long? {
        return when (mode.uppercase()) {
            "CAT1" -> lockDateFor(dates.cat1Date, CAT_LOCK_OFFSET_DAYS)
            "CAT2" -> lockDateFor(dates.cat2Date, CAT_LOCK_OFFSET_DAYS)
            "LID", "ALL" -> if (isLab) dates.lidLabDate else dates.lidTheoryDate
            else -> if (isLab) dates.lidLabDate else dates.lidTheoryDate
        }
    }

    fun gridCeiling(mode: String, dates: MilestoneDates): Long? {
        return when (mode.uppercase()) {
            "CAT1" -> lockDateFor(dates.cat1Date, CAT_LOCK_OFFSET_DAYS)
            "CAT2" -> lockDateFor(dates.cat2Date, CAT_LOCK_OFFSET_DAYS)
            "LID", "ALL" -> {
                val lab = dates.lidLabDate ?: 0L
                val theory = dates.lidTheoryDate ?: 0L
                val max = maxOf(lab, theory)
                if (max > 0) max else null
            }
            else -> null
        }
    }

    fun courseLockDate(mode: String, isLab: Boolean, dates: MilestoneDates): Long? =
        courseCeiling(mode, isLab, dates)

    fun normalizeDay(raw: String?): String {
        val s = raw?.trim() ?: ""
        return if (s.length >= 3) s.substring(0, 3).uppercase() else s.uppercase()
    }

    private val DAY_ORDER_MAP = mapOf(
        "monday" to "MON",
        "tuesday" to "TUE",
        "wednesday" to "WED",
        "thursday" to "THU",
        "friday" to "FRI",
    )

    fun effectiveWeekday(
        weekday: String?,
        events: List<PredictorDayEvent>?,
    ): String {
        val own = normalizeDay(weekday)
        if (own != "SAT" || events.isNullOrEmpty()) return own

        for (ev in events) {
            val haystack = "${ev.text.orEmpty()} ${ev.category.orEmpty()} ${ev.title.orEmpty()} ${ev.detail.orEmpty()}"
            val regex = Regex("""\b(monday|tuesday|wednesday|thursday|friday)\b""", RegexOption.IGNORE_CASE)
            val match = regex.find(haystack)
            if (match != null) {
                val mapped = DAY_ORDER_MAP[match.value.lowercase()]
                if (mapped != null) return mapped
            }
        }
        return own
    }

    fun buildEffectiveDayMap(days: List<DayLike>?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (d in days.orEmpty()) {
            val date = d.date ?: continue
            map[dayKey(date)] = effectiveWeekday(d.weekday, d.events)
        }
        return map
    }

    fun isLabCourse(courseCode: String?, courseType: String?): Boolean {
        if (Regex("""\blab\b""", RegexOption.IGNORE_CASE).containsMatchIn(courseType.orEmpty())) return true
        return courseCode.orEmpty().endsWith("(L)")
    }

    fun isProjectableCourse(courseCode: String?, slotName: String?, totalClasses: Int): Boolean {
        return !courseCode.isNullOrBlank() && slotName != "NILL" && totalClasses >= 0
    }

    fun skipSetFor(skips: Map<String, Set<Long>>?, courseCode: String): Set<Long> =
        skips?.get(courseCode).orEmpty()

    fun toggleCourseSkip(
        skips: Map<String, Set<Long>>,
        courseCode: String,
        timestamp: Long,
    ): Map<String, Set<Long>> {
        val result = skips.toMutableMap()
        val currentSet = (result[courseCode] ?: emptySet()).toMutableSet()
        if (currentSet.contains(timestamp)) {
            currentSet.remove(timestamp)
        } else {
            currentSet.add(timestamp)
        }
        result[courseCode] = currentSet
        return result
    }

    fun skipCountFor(skips: Map<String, Set<Long>>?, courseCode: String): Int =
        skips?.get(courseCode)?.size ?: 0

    fun courseMeetingDays(
        courseCode: String,
        dayCardsMap: Map<String, List<Map<String, String>>>?,
    ): List<String> {
        if (courseCode.isBlank() || dayCardsMap == null) return emptyList()
        return dayCardsMap.keys
            .filter { day -> (dayCardsMap[day] ?: emptyList()).any { it["courseCode"] == courseCode } }
            .map { normalizeDay(it) }
    }

    fun courseMeetsOn(
        date: Long,
        courseCode: String,
        dayCardsMap: Map<String, List<Map<String, String>>>?,
        effectiveMap: Map<String, String>,
    ): Boolean {
        val meeting = courseMeetingDays(courseCode, dayCardsMap)
        if (meeting.isEmpty()) return false
        val eff = effectiveMap[dayKey(date)] ?: return false
        return meeting.contains(eff)
    }

    fun courseMeetingDates(
        courseCode: String,
        dayCardsMap: Map<String, List<Map<String, String>>>?,
        workingDays: List<DayLike>?,
        effectiveMap: Map<String, String>,
        ceiling: Long? = null,
        lockDate: Long? = null,
    ): List<Long> {
        val meeting = courseMeetingDays(courseCode, dayCardsMap)
        if (meeting.isEmpty()) return emptyList()

        val lockTime = lockDate?.let { startOfDay(it) }
        val ceilingTime = ceiling?.let { startOfDay(it) }

        val out = mutableListOf<Long>()
        for (day in workingDays.orEmpty()) {
            val date = day.date ?: continue
            val time = startOfDay(date)
            if (ceilingTime != null && time > ceilingTime) continue
            if (lockTime != null && time >= lockTime) continue
            val eff = effectiveMap[dayKey(date)] ?: continue
            if (!meeting.contains(eff)) continue
            out.add(date)
        }
        out.sort()
        return out
    }

    fun projectCourse(input: ScheduleInput): ScheduleResult {
        val course = input.course
        val attended = course.attendedClasses
        val total = course.totalClasses
        val isLab = isLabCourse(course.courseCode, course.courseType)
        val meetingDays = courseMeetingDays(course.courseCode, input.dayCardsMap)
        val currentPct = if (total > 0) (attended.toDouble() / total.toDouble()) * 100.0 else 0.0

        if (meetingDays.isEmpty()) {
            return ScheduleResult(
                futureDays = 0,
                missedDays = 0,
                meetingDays = meetingDays,
                attended = attended,
                total = total,
                currentPct = currentPct,
                predictedAttended = attended,
                predictedTotal = total,
                predictedPct = currentPct,
                deltaPct = 0.0,
                safeBunks = 0,
                classesNeeded = 0,
                isLab = isLab,
                skippedCount = input.skips.size,
            )
        }

        val lockTime = input.lockDate?.let { startOfDay(it) }
        val ceilingTime = input.ceiling?.let { startOfDay(it) }

        fun withinWindow(t: Long): Boolean {
            val start = startOfDay(t)
            if (ceilingTime != null && start > ceilingTime) return false
            if (lockTime != null && start >= lockTime) return false
            return true
        }

        var futureDays = 0
        var missedDays = 0

        for (day in input.workingDays) {
            val date = day.date ?: continue
            if (!withinWindow(date)) continue
            val eff = input.effectiveMap[dayKey(date)] ?: continue
            if (!meetingDays.contains(eff)) continue

            val time = startOfDay(date)
            futureDays++

            if (input.skips.contains(time)) {
                missedDays++
                continue
            }

            val state = input.dateStates?.get(time)
            if (state == 1) missedDays++
        }

        val weight = if (isLab) 2 else 1
        val futureClasses = futureDays * weight
        val missedClasses = minOf(missedDays * weight, futureClasses)

        val predictedAttended = attended + (futureClasses - missedClasses)
        val predictedTotal = total + futureClasses
        val predictedPct = if (predictedTotal > 0) (predictedAttended.toDouble() / predictedTotal.toDouble()) * 100.0 else 0.0

        val ratio = input.threshold / 100.0
        var safeBunks = 0
        var classesNeeded = 0
        if (ratio > 0.0 && ratio < 1.0) {
            if (predictedPct >= input.threshold) {
                val raw = floor(((predictedAttended - ratio * predictedTotal) / ratio) + 1e-9).toInt()
                safeBunks = maxOf(0, if (isLab) floor(raw / 2.0).toInt() else raw)
            } else {
                val raw = ceil(((ratio * predictedTotal - predictedAttended) / (1.0 - ratio)) - 1e-9).toInt()
                classesNeeded = maxOf(0, if (isLab) ceil(raw / 2.0).toInt() else raw)
            }
        }

        return ScheduleResult(
            futureDays = futureDays,
            missedDays = missedDays,
            meetingDays = meetingDays,
            attended = attended,
            total = total,
            currentPct = currentPct,
            predictedAttended = predictedAttended,
            predictedTotal = predictedTotal,
            predictedPct = predictedPct,
            deltaPct = predictedPct - currentPct,
            safeBunks = safeBunks,
            classesNeeded = classesNeeded,
            isLab = isLab,
            skippedCount = input.skips.size,
        )
    }

    fun summarise(courses: List<ScheduleResult>, threshold: Double): OverallSummary {
        var attended = 0
        var total = 0
        var predictedAttended = 0
        var predictedTotal = 0
        var safeCount = 0
        var totalSafeBunks = 0
        var maxCeilingDays = 0

        for (c in courses) {
            attended += c.attended
            total += c.total
            predictedAttended += c.predictedAttended
            predictedTotal += c.predictedTotal
            if (c.predictedPct >= threshold) {
                safeCount++
                totalSafeBunks += c.safeBunks
            }
            maxCeilingDays = maxOf(maxCeilingDays, c.futureDays)
        }

        val currentPct = if (total > 0) (attended.toDouble() / total.toDouble()) * 100.0 else 0.0
        val predictedPct = if (predictedTotal > 0) (predictedAttended.toDouble() / predictedTotal.toDouble()) * 100.0 else 0.0

        return OverallSummary(
            currentPct = currentPct,
            predictedPct = predictedPct,
            delta = predictedPct - currentPct,
            safeCount = safeCount,
            atRiskCount = courses.size - safeCount,
            totalCourses = courses.size,
            totalSafeBunks = totalSafeBunks,
            remainingDays = maxCeilingDays,
        )
    }

    fun formatPct(value: Double, decimals: Boolean = true): String {
        val factor = if (decimals) 100.0 else 10.0
        val rounded = round(value * factor) / factor
        return if (decimals) {
            val parts = rounded.toString().split(".")
            val dec = if (parts.size > 1) parts[1].padEnd(2, '0').substring(0, 2) else "00"
            "${parts[0]}.$dec"
        } else {
            val parts = rounded.toString().split(".")
            val dec = if (parts.size > 1) parts[1].padEnd(1, '0').substring(0, 1) else "0"
            "${parts[0]}.$dec"
        }
    }
}
