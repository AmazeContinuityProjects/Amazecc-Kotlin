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
import androidx.compose.material.icons.rounded.VisibilityOff
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
import com.amazecc.app.shared.ui.components.ExamDayBanner
import com.amazecc.app.shared.ui.components.ExamDayGoodLuck
import com.amazecc.app.shared.ui.components.rememberSelectedSemesterExams
import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.utils.examDateParsed
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.utils.AttendanceDay
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.datetime.*
import kotlin.math.max

data class TimelineEvent(
    val type: String, // "class", "free", "lunch", "task"
    val slots: List<String> = emptyList(),
    val startMins: Int,
    val endMins: Int,
    val durationMins: Int,
    val course: AttendanceItem? = null,
    val title: String = "",
    val taskType: String = ""
)

private data class WeekDay(
    val abbrev: String,
    val date: Int,
    val month: Int,
    val isToday: Boolean,
    val fullDate: LocalDate,
    val effectiveAbbrev: String,
    val dayOrderOverride: AttendanceDay?
)

@Composable
fun DailyPlannerScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val attendance = attendanceRes?.attendance ?: emptyList()
    val calendarMonths = calendarRes?.months ?: emptyList()

    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val todayAbbrev = remember(today, calendarRes) {
        AttendanceTimetable.getTodayAttendanceDay(calendarRes).name
    }
    val tasks by AppState.tasks.collectAsState()

    val weekDays = remember(today, calendarRes) {
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        (0..6).map { offset ->
            val d = monday.plus(DatePeriod(days = offset))
            val baseAbbr = when (d.dayOfWeek) {
                DayOfWeek.SUNDAY -> "SUN"; DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"
                DayOfWeek.WEDNESDAY -> "WED"; DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"
                DayOfWeek.SATURDAY -> "SAT"; else -> "MON"
            }
            val dayOrderOverride = AttendanceTimetable.getDayOrderOverrideForDate(d, calendarRes)
            val effectiveAbbrev = AttendanceTimetable.getAttendanceDayForDate(d, calendarRes).name
            WeekDay(baseAbbr, d.dayOfMonth, d.monthNumber, d == today, d, effectiveAbbrev, dayOrderOverride)
        }
    }

    var selectedDate by remember { mutableStateOf(today) }
    val selectedWeekDay = remember(selectedDate, weekDays) {
        weekDays.firstOrNull { it.fullDate == selectedDate } ?: weekDays.firstOrNull { it.isToday } ?: weekDays.first()
    }

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

    fun buildDailySchedule(
        day: String,
        examsForDay: List<com.amazecc.app.shared.model.ExamItem> = emptyList(),
        taskBlocks: List<TimelineEvent> = emptyList()
    ): List<TimelineEvent> {
        val dayClasses = mutableListOf<TimelineEvent>()
        val dayMap = SlotMap.map[day] ?: return emptyList()

        // Exam time ranges for this day (in minutes from midnight)
        val examRanges: List<Pair<Int, Int>> = examsForDay.mapNotNull { exam ->
            com.amazecc.app.shared.utils.ExamUtils.parseExamTimeRange(exam.examTime)
        }

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

        if (dayClasses.isEmpty() && examsForDay.isEmpty() && taskBlocks.isEmpty()) return emptyList()
        dayClasses.sortBy { it.startMins }

        val merged = mutableListOf<TimelineEvent>()
        dayClasses.forEach { item ->
            if (merged.isEmpty()) {
                merged.add(item)
            } else {
                val last = merged.last()
                if (last.course?.courseCode != null && last.course?.courseCode == item.course?.courseCode && kotlin.math.abs(last.endMins - item.startMins) <= 10) {
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
        merged.addAll(taskBlocks)
        merged.sortBy { it.startMins }

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
                val isExamTime = examRanges.any { (exStart, exEnd) ->
                    gapStart < exEnd && gapEnd > exStart
                }
                if (!isExamTime) {
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
            }
            timeline.add(c)
            pointer = c.endMins
        }

        if (pointer < LUNCH_START) {
            val isExamTime = examRanges.any { (exStart, exEnd) ->
                pointer < exEnd && LUNCH_START > exStart
            }
            if (!isExamTime) {
                if (LUNCH_START - pointer > 10) {
                    timeline.add(TimelineEvent("free", emptyList(), pointer, LUNCH_START, LUNCH_START - pointer))
                }
                timeline.add(TimelineEvent("lunch", emptyList(), LUNCH_START, LUNCH_END, 40))
                pointer = LUNCH_END
            }
        }

        if (pointer < DAY_END) {
            val isExamTime = examRanges.any { (exStart, exEnd) ->
                pointer < exEnd && DAY_END > exStart
            }
            if (!isExamTime) {
                val finalGap = DAY_END - pointer
                if (finalGap > 10) {
                    timeline.add(TimelineEvent("free", emptyList(), pointer, DAY_END, finalGap))
                }
            }
        }

        return timeline
    }

    // Get exams for selected date
    val allExams = rememberSelectedSemesterExams()
    val examsByDate = remember(allExams) {
        allExams.filter { it.examDateParsed != null }.groupBy { it.examDateParsed!! }
    }
    val examsForDate = remember(allExams, selectedWeekDay) {
        examsByDate[selectedWeekDay.fullDate] ?: emptyList()
    }

    var showTimetable by remember { mutableStateOf(false) }

    fun minutesFromTime(timeStr: String): Int? {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    val selectedDateStr = selectedWeekDay.fullDate.toString()
    val taskBlocks = remember(tasks, selectedWeekDay) {
        val blocks = mutableListOf<TimelineEvent>()
        tasks.forEach { t ->
            if (t.completed) return@forEach
            if (t.type == "exam" || t.type == "quiz") {
                if (t.showOnTimetable) {
                    val start = minutesFromTime(t.dueTime) ?: 1080
                    val end = start + 60
                    blocks.add(TimelineEvent("task", emptyList(), start, end, end - start, title = t.title, taskType = t.type))
                }
            } else if (t.type == "assignment") {
                t.workSessions.forEach { s ->
                    if (s.date == selectedDateStr) {
                        val start = minutesFromTime(s.startTime) ?: 1080
                        val end = start + s.durationMinutes.coerceAtLeast(10)
                        blocks.add(TimelineEvent("task", emptyList(), start, end, end - start, title = "${t.title} — Study Block", taskType = "study"))
                    }
                }
            }
        }
        blocks.sortedBy { it.startMins }
    }

    val scheduleData = remember(selectedWeekDay, attendance, examsForDate, taskBlocks) { buildDailySchedule(selectedWeekDay.effectiveAbbrev, examsForDate, taskBlocks) }

    val dayTasks = remember(tasks, selectedWeekDay) {
        tasks.filter { it.dueDate == selectedWeekDay.fullDate.toString() && !it.completed }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal Day Selector with Dates
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weekDays, key = { it.fullDate.toString() }) { wd ->
                val isSelected = selectedWeekDay.fullDate == wd.fullDate
                val dayMap = SlotMap.map[wd.effectiveAbbrev] ?: emptyMap<String, String>()
                val classCount = attendance.count { course ->
                    val slots = course.slotName.split("+").map { it.trim() }
                    slots.any { dayMap.containsKey(it) }
                }
                val isHoliday = holidayMap[wd.fullDate] == true
                val isExamDay = examsByDate.containsKey(wd.fullDate)

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = com.amazecc.app.shared.ui.components.bouncySpring()
                    )

                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else colors.surface)
                            .border(
                                1.dp,
                                when {
                                    isSelected -> colors.accent
                                    wd.isToday -> colors.accent.copy(alpha = 0.5f)
                                    else -> colors.border
                                },
                                CircleShape
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { selectedDate = wd.fullDate }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (wd.dayOrderOverride != null) "${wd.abbrev}⚡" else wd.abbrev,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) colors.background.copy(alpha = 0.8f) else if (wd.dayOrderOverride != null) colors.accent else colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                        Text(
                            text = "${wd.date}",
                            style = AmazeTheme.typography.subheading.copy(
                                color = if (isSelected) colors.background else colors.textPrimary,
                                fontWeight = if (wd.isToday) FontWeight.Black else FontWeight.Bold,
                                fontSize = if (wd.isToday) AmazeTheme.fontSize.xl else AmazeTheme.fontSize.lg
                            )
                        )
                    if (isHoliday) {
                        Text(
                            "Holiday",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.chart5,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                    } else if (isExamDay) {
                        Text(
                            "Exam",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else colors.chart1,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                    } else if (classCount > 0) {
                        Text(
                            "$classCount class${if (classCount != 1) "es" else ""}",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) Color.White.copy(alpha=0.7f) else colors.textMuted,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                    } else {
                        Text(
                            "Off",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = if (isSelected) Color.White.copy(alpha=0.5f) else colors.textMuted.copy(alpha=0.5f),
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        if (selectedWeekDay.dayOrderOverride != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.accent.copy(alpha = 0.12f))
                    .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Text(
                        "⚡ ${selectedWeekDay.dayOrderOverride.name} Day Order active for ${selectedWeekDay.abbrev}",
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                }
            }
        }

        if (examsForDate.isNotEmpty() && !showTimetable) {
            ExamDayGoodLuck(
                exams = examsForDate,
                onToggleTimetable = { showTimetable = true }
            )
        } else {
            if (examsForDate.isNotEmpty()) {
                ExamDayBanner(examsForDate)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showTimetable = false },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hide Timetable", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
            }

            if (scheduleData.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = AmazeTheme.fontSize.hero)
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
                        Text("No Classes Scheduled", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Enjoy your day off!", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    items(scheduleData, key = { it.startMins }) { item ->
                        TimelineRow(item)
                    }

                    if (dayTasks.isNotEmpty()) {
                        item { Spacer(Modifier.height(AmazeTheme.spacing.sm)) }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Text(
                                    if (selectedWeekDay.isToday) "Today's Tasks (${dayTasks.size})" else "Tasks on ${selectedWeekDay.fullDate} (${dayTasks.size})",
                                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                )
                            }
                        }
                        items(dayTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                colors = colors,
                                onToggle = { AppState.toggleTaskCompleted(task.id) },
                                onDelete = { AppState.deleteTask(task.id) },
                                showCourse = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            )
                        }
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

        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                .background(
                    if (item.type == "class") colors.surface else colors.surface.copy(alpha = 0.5f),
                    RoundedCornerShape(AmazeTheme.radius.medium)
                )
                .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
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
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
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
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
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
                    item.course?.let { c ->
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
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
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
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                                Text(
                                    c.courseTitle,
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
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
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
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
                "task" -> {
                    val isStudy = item.taskType == "study"
                    val taskColor = if (isStudy) colors.warning else colors.danger
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(taskColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        )
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(taskColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (isStudy) "STUDY" else if (item.taskType == "quiz") "QUIZ" else "EXAM",
                                        style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = taskColor)
                                    )
                                }
                                Text(
                                    "${TimeMath.minutesToTimeStr(item.startMins)} - ${TimeMath.minutesToTimeStr(item.endMins)}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Text(
                                item.title,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                            Text(
                                if (isStudy) "Planned work session" else "Task due ${TimeMath.minutesToTimeStr(item.endMins - 60)}",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
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
