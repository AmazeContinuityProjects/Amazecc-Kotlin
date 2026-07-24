package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.datetime.*
import kotlin.math.max

data class TimelineEvent(
    val type: String, // "class", "free", "lunch"
    val slots: List<String> = emptyList(),
    val startMins: Int,
    val endMins: Int,
    val durationMins: Int,
    val course: AttendanceItem? = null
)

private data class WeekDay(
    val abbrev: String,
    val date: Int,
    val month: Int,
    val isToday: Boolean,
    val fullDate: LocalDate
)

@Composable
fun DailyPlannerScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val attendance = attendanceRes?.attendance ?: emptyList()
    val calendarMonths = calendarRes?.months ?: emptyList()

    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val todayAbbrev = remember(today) {
        when (today.dayOfWeek) {
            DayOfWeek.SUNDAY -> "SUN"; DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"
            DayOfWeek.WEDNESDAY -> "WED"; DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"
            DayOfWeek.SATURDAY -> "SAT"; else -> "MON"
        }
    }

    val weekDays = remember(today) {
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        (0..6).map { offset ->
            val d = monday.plus(DatePeriod(days = offset))
            val abbr = when (d.dayOfWeek) {
                DayOfWeek.SUNDAY -> "SUN"; DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"
                DayOfWeek.WEDNESDAY -> "WED"; DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"
                DayOfWeek.SATURDAY -> "SAT"; else -> "MON"
            }
            WeekDay(abbr, d.dayOfMonth, d.monthNumber, d == today, d)
        }
    }

    var selectedDay by remember { mutableStateOf(todayAbbrev) }

    // Check calendar for holiday/working day info
    val holidayMap = remember(calendarMonths) {
        val map = mutableMapOf<LocalDate, Boolean>()
        for (m in calendarMonths) {
            val parts = m.month.split(" ")
            val monthNum = when (parts.firstOrNull()?.take(3)?.lowercase()) {
                "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
                "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
                else -> null
            }
            val year = parts.lastOrNull()?.toIntOrNull() ?: continue
            if (monthNum == null) continue
            for (day in m.days) {
                val isHoliday = day.events.any { e ->
                    e.type.contains("holiday", true) || e.text.contains("holiday", true)
                }
                val localDate = try {
                    LocalDate(year, monthNum, day.date)
                } catch (_: Exception) { null } ?: continue
                if (isHoliday) map[localDate] = true
            }
        }
        map
    }

    fun buildDailySchedule(day: String): List<TimelineEvent> {
        val dayClasses = mutableListOf<TimelineEvent>()
        val dayMap = SlotMap.map[day] ?: return emptyList()

        attendance.forEach { course ->
            val slots = course.slotName.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            slots.forEach { slot ->
                val timeStr = dayMap[slot]
                if (timeStr != null) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = TimeMath.toMinutes(parts[0])
                        val end = TimeMath.toMinutes(parts[1])
                        dayClasses.add(
                            TimelineEvent("class", listOf(slot), start, end, end - start, course)
                        )
                    }
                }
            }
        }

        if (dayClasses.isEmpty()) return emptyList()
        dayClasses.sortBy { it.startMins }

        val merged = mutableListOf<TimelineEvent>()
        dayClasses.forEach { item ->
            if (merged.isEmpty()) {
                merged.add(item)
            } else {
                val last = merged.last()
                if (last.course?.courseCode == item.course?.courseCode && kotlin.math.abs(last.endMins - item.startMins) <= 10) {
                    val updated = last.copy(
                        endMins = max(last.endMins, item.endMins),
                        slots = last.slots + item.slots,
                        durationMins = max(last.endMins, item.endMins) - last.startMins
                    )
                    merged[merged.size - 1] = updated
                } else {
                    merged.add(item)
                }
            }
        }

        val DAY_START = 480 // 8:00 AM
        val LUNCH_START = 800 // 1:20 PM
        val LUNCH_END = 840 // 2:00 PM
        val DAY_END = 1160 // 7:20 PM

        val timeline = mutableListOf<TimelineEvent>()
        var pointer = DAY_START

        merged.forEach { c ->
            val gapStart = pointer
            val gapEnd = c.startMins
            val gap = gapEnd - gapStart

            if (gap > 10) {
                if (gapStart < LUNCH_END && gapEnd > LUNCH_START) {
                    if (gapStart < LUNCH_START && LUNCH_START - gapStart > 10) {
                        timeline.add(TimelineEvent("free", emptyList(), gapStart, LUNCH_START, LUNCH_START - gapStart))
                    }
                    timeline.add(TimelineEvent("lunch", emptyList(), LUNCH_START, LUNCH_END, 40))
                    if (gapEnd > LUNCH_END && gapEnd - LUNCH_END > 10) {
                        timeline.add(TimelineEvent("free", emptyList(), LUNCH_END, gapEnd, gapEnd - LUNCH_END))
                    }
                } else {
                    timeline.add(TimelineEvent("free", emptyList(), gapStart, gapEnd, gap))
                }
            }
            timeline.add(c)
            pointer = c.endMins
        }

        if (pointer < LUNCH_START) {
            if (LUNCH_START - pointer > 10) {
                timeline.add(TimelineEvent("free", emptyList(), pointer, LUNCH_START, LUNCH_START - pointer))
            }
            timeline.add(TimelineEvent("lunch", emptyList(), LUNCH_START, LUNCH_END, 40))
            pointer = LUNCH_END
        }

        if (pointer < DAY_END) {
            val finalGap = DAY_END - pointer
            if (finalGap > 10) {
                timeline.add(TimelineEvent("free", emptyList(), pointer, DAY_END, finalGap))
            }
        }

        return timeline
    }

    val scheduleData = remember(selectedDay, attendance) { buildDailySchedule(selectedDay) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal Day Selector with Dates
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weekDays) { wd ->
                val isSelected = selectedDay == wd.abbrev
                val dayMap = SlotMap.map[wd.abbrev] ?: emptyMap<String, String>()
                val classCount = attendance.count { course ->
                    val slots = course.slotName.split("+").map { it.trim() }
                    slots.any { dayMap.containsKey(it) }
                }
                val isHoliday = holidayMap[wd.fullDate] == true

                Column(
                    modifier = Modifier
                        .background(
                            if (isSelected) colors.accent else colors.surface,
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            when {
                                isSelected -> colors.accent
                                wd.isToday -> colors.accent.copy(alpha = 0.4f)
                                else -> colors.border
                            },
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedDay = wd.abbrev }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = wd.abbrev,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isSelected) Color.White.copy(alpha=0.8f) else colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "${wd.date}",
                        style = AmazeTheme.typography.subheading.copy(
                            color = if (isSelected) Color.White else colors.textPrimary,
                            fontWeight = if (wd.isToday) FontWeight.Black else FontWeight.Bold,
                            fontSize = if (wd.isToday) 18.sp else 16.sp
                        )
                    )
                    if (isHoliday) {
                        Text(
                            "Holiday",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.chart5,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        )
                    } else if (classCount > 0) {
                        Text(
                            "$classCount class${if (classCount != 1) "es" else ""}",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) Color.White.copy(alpha=0.7f) else colors.textMuted,
                                fontSize = 8.sp
                            )
                        )
                    } else {
                        Text(
                            "Off",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) Color.White.copy(alpha=0.5f) else colors.textMuted.copy(alpha=0.5f),
                                fontSize = 8.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (scheduleData.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Classes Scheduled", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Enjoy your day off!", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(scheduleData) { item ->
                    TimelineRow(item)
                }

                val todayTasks = AppState.todayTasks
                if (todayTasks.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Today's Tasks (${todayTasks.size})", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        }
                    }
                    items(todayTasks) { task ->
                        TaskCard(
                            task = task,
                            colors = colors,
                            onToggle = { AppState.toggleTaskCompleted(task.id) },
                            onDelete = { AppState.deleteTask(task.id) },
                            showCourse = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineRow(item: TimelineEvent) {
    val colors = AmazeTheme.colors

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Timeline connector
        Box(modifier = Modifier.width(12.dp), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.width(1.5.dp).fillMaxHeight().background(colors.border))
            Box(modifier = Modifier.padding(top = 24.dp).size(7.dp).clip(CircleShape).background(if (item.type == "class") colors.accent else colors.border))
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (item.type == "class") colors.surface else colors.surface.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .clickable {
                    if (item.type == "class") item.course?.courseCode?.let { AppState.openCourseDetail(it) }
                }
        ) {
            when (item.type) {
                "free" -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Coffee, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Free Period", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(
                                "${TimeMath.minutesToTimeStr(item.startMins)} - ${TimeMath.minutesToTimeStr(item.endMins)} (${TimeMath.formatDuration(item.durationMins)})",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
                "lunch" -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = colors.chart3, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Lunch Break", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(
                                "${TimeMath.minutesToTimeStr(item.startMins)} - ${TimeMath.minutesToTimeStr(item.endMins)} (${TimeMath.formatDuration(item.durationMins)})",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
                "class" -> {
                    val c = item.course ?: return
                    val total = c.totalClasses
                    val attended = c.attendedClasses
                    val attPct = if (total > 0) ((attended.toFloat() / total) * 100).toInt() else 0
                    val isSafe = attPct >= 75
                    val typeColor = courseTypeColor(c.courseType, colors)
                    val typeLabel = courseTypeLabel(c.courseType)

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(typeColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        )
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(typeColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        typeLabel,
                                        style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = typeColor)
                                    )
                                }
                                Text(
                                    "${TimeMath.minutesToTimeStr(item.startMins)} - ${TimeMath.minutesToTimeStr(item.endMins)}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                c.courseTitle,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val slotStr = item.slots.joinToString(" + ")
                                val venue = c.slotVenue
                                val slotAndVenue = if (!venue.isNullOrBlank()) "$slotStr • $venue" else slotStr
                                Text(
                                    slotAndVenue,
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                attPct >= 85 -> colors.chart1.copy(alpha = 0.12f)
                                                attPct >= 75 -> colors.chart3.copy(alpha = 0.12f)
                                                else -> colors.chart5.copy(alpha = 0.12f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "$attPct%",
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                attPct >= 85 -> colors.chart1
                                                attPct >= 75 -> colors.chart3
                                                else -> colors.chart5
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun courseTypeColor(type: String, colors: com.amazecc.app.shared.theme.AmazeColors): Color {
    return when {
        type.contains("Theory", ignoreCase = true) && !type.contains("Embedded", ignoreCase = true) -> colors.chart2
        type.contains("Lab", ignoreCase = true) && !type.contains("Embedded", ignoreCase = true) -> colors.chart5
        type.contains("Embedded", ignoreCase = true) -> colors.chart3
        type.contains("Project", ignoreCase = true) -> colors.chart4
        type.contains("Soft Skills", ignoreCase = true) -> colors.chart1
        else -> colors.accent
    }
}

private fun courseTypeLabel(type: String): String {
    return when {
        type.contains("Embedded Theory", ignoreCase = true) -> "ETH"
        type.contains("Embedded Lab", ignoreCase = true) -> "ELA"
        type.contains("Embedded", ignoreCase = true) -> "EMB"
        type.contains("Theory Only", ignoreCase = true) -> "TH"
        type.contains("Lab Only", ignoreCase = true) -> "LO"
        type.contains("Project", ignoreCase = true) -> "PJT"
        type.contains("Soft Skills", ignoreCase = true) -> "SS"
        type.contains("Option Course", ignoreCase = true) -> "OC"
        else -> type.take(3).uppercase()
    }
}
