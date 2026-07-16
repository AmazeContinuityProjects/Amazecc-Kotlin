@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
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
import androidx.compose.material.icons.rounded.*
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
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.AttendanceDay
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.TimeMath
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


@Composable
fun AttendanceScreen() {
    var activeView by remember { mutableStateOf("Timetable") }
    val views = listOf("Timetable", "Predictor", "Calendar")
    val colors = AmazeTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Attendance Hub",
            description = "Track your attendance, view timelines and predict shortfalls",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = AppState::refreshCurrentSemester
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            views.forEach { view ->
                val isSelected = activeView == view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { activeView = view }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = view,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) colors.background else colors.textPrimary
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (activeView) {
                "Timetable" -> DailyPlannerScreen()
                "Predictor" -> OverallPredictorScreen()
                "Calendar" -> CalendarScreen(onBack = {}, showHeader = false)
            }
        }
    }
}

@Composable
fun FreePeriodBlock(title: String, time: String) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$title ($time)",
            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textMuted)
        )
    }
}

@Composable
fun OverallPredictorScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val examScheduleRes by AppState.examSchedule.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    val calendarMonths = calendarRes?.months ?: emptyList()
    val examSchedule = examScheduleRes?.schedule ?: emptyMap()

    var selectedMode by remember { mutableStateOf("LID") }
    var skipDates by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val impDates = remember(calendarMonths, examSchedule) {
        computeImportantDates(calendarMonths, examSchedule)
    }

    val cutoffDate = remember(selectedMode, impDates) {
        when (selectedMode) {
            "CAT1" -> impDates["cat i"]?.let { it }
            "CAT2" -> impDates["cat ii"]?.let { it }
            "FAT" -> impDates["fat"]?.let { it }
            "LID" -> {
                val labDate = impDates["lid for laboratory classes"]
                val theoryDate = impDates["lid for theory classes"]
                if (labDate != null && theoryDate != null) {
                    val labVal = labDate.year * 10000 + labDate.month * 100 + labDate.day
                    val theoryVal = theoryDate.year * 10000 + theoryDate.month * 100 + theoryDate.day
                    if (labVal >= theoryVal) labDate else theoryDate
                }
                else labDate ?: theoryDate
            }
            else -> null
        }
    }

    val slotMapTyped = remember {
        SlotMap.map.mapValues { (_, inner) ->
            inner.mapValues { (_, time) -> SlotInfo(time) }
        }
    }
    val dayCardsMap = remember(courses) {
        AttendanceTimetable.buildAttendanceDayCardsMap(
            attendance = courses.map { item ->
                val shortType = when (item.courseType.lowercase()) {
                    "embedded theory" -> "ETH"
                    "embedded lab" -> "ELA"
                    "theory only" -> "TO"
                    "lab only" -> "LO"
                    "soft skill" -> "SS"
                    else -> item.courseType
                }
                mapOf(
                    "courseCode" to item.courseCode,
                    "courseTitle" to item.courseTitle,
                    "courseType" to shortType,
                    "faculty" to item.faculty,
                    "slotName" to (item.slotName ?: ""),
                    "attendancePercentage" to item.attendancePercentage
                )
            },
            slotMap = slotMapTyped
        )
    }

    val allWorkingDays = remember(calendarMonths) {
        buildWorkingDays(calendarMonths)
    }

    val futureClassesMap = remember(allWorkingDays, dayCardsMap, selectedMode, impDates) {
        computeFutureClasses(courses, dayCardsMap, allWorkingDays, selectedMode, impDates)
    }

    val predictions = remember(skipDates, futureClassesMap, courses) {
        courses.map { course ->
            val code = course.courseCode
            val attended = course.attendedClasses
            val total = course.totalClasses
            val futureInfo = futureClassesMap[code]
            val futureCount = futureInfo?.total ?: 0
            val futureDates = futureInfo?.dates ?: emptyList()
            val skipCount = futureDates.count { it.dateVal in skipDates }
            val effectiveAttend = futureCount - skipCount.coerceIn(0, futureCount)
            val predictedAttended = attended + effectiveAttend
            val predictedTotal = total + futureCount
            val predictedPct = if (predictedTotal > 0) (predictedAttended.toDouble() / predictedTotal * 100) else 0.0
            CoursePrediction(course, futureCount, skipCount, predictedAttended, predictedTotal, predictedPct)
        }
    }

    val totalPredictedAttended = predictions.sumOf { it.predictedAttended }
    val totalPredictedTotal = predictions.sumOf { it.predictedTotal }
    val overallPct = if (totalPredictedTotal > 0) totalPredictedAttended.toDouble() / totalPredictedTotal * 100 else 0.0

    val modes = listOf("CAT1", "CAT2", "FAT", "LID")

    val allMonthsList = remember(allWorkingDays) {
        allWorkingDays.map { (y, m, _) -> y to m }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }
    var currentMonthIndex by remember(allMonthsList) { 
        val todayDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val idx = allMonthsList.indexOfFirst { it.first == todayDate.year && it.second == todayDate.monthNumber }
        mutableStateOf(if (idx >= 0) idx else 0) 
    }
    val currentMonthPair = allMonthsList.getOrNull(currentMonthIndex)
    val monthName = currentMonthPair?.let { (y, m) ->
        val mName = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December").getOrNull(m - 1) ?: ""
        "$mName $y"
    } ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
            backgroundColor = colors.surface
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Predicted Overall Attendance ($selectedMode)", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pctFormatted(overallPct),
                    style = AmazeTheme.typography.heading.copy(
                        color = when {
                            overallPct >= 85 -> Color(0xFF10B981)
                            overallPct >= 75 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp
                    )
                )
                Text(
                    text = "$totalPredictedAttended / $totalPredictedTotal classes",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Cutoff Target", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) colors.accent else colors.surface, RoundedCornerShape(8.dp))
                        .clickable { selectedMode = mode }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(mode, color = if (isSelected) Color.White else colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        if (cutoffDate != null) {
            val examLabel = when (selectedMode) {
                "CAT1" -> "CAT I"
                "CAT2" -> "CAT II"
                "FAT" -> "FAT"
                else -> "LID"
            }
            Text(
                text = "$examLabel cutoff: ${cutoffDate.month}/${cutoffDate.day}",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
        }

        val totalWorkingDays = allWorkingDays.size
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayVal = today.year * 10000 + today.monthNumber * 100 + today.dayOfMonth
        val remainingDays = allWorkingDays.count { (y, m, d) ->
            val dateVal = y * 10000 + m * 100 + d
            dateVal >= todayVal && (cutoffDate == null || dateVal <= cutoffDate.year * 10000 + cutoffDate.month * 100 + cutoffDate.day)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accent.copy(alpha = 0.06f))
                .border(1.dp, colors.accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalWorkingDays", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                Text("Cal. Days", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
            }
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(colors.border))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$remainingDays", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Remaining", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
            }
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(colors.border))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${calendarMonths.size}", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Months", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Select dates to skip classes",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
            TextButton(onClick = {
                skipDates = emptySet()
            }) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium))
            }
        }

        if (currentMonthPair != null) {
            val (y, m) = currentMonthPair
            val daysInMonth = allWorkingDays.filter { it.first == y && it.second == m }.map { it.third }
            val firstDayOfWeek = try {
                LocalDate(y, m, 1).dayOfWeek.ordinal
            } catch (e: Exception) { 0 }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (currentMonthIndex > 0) currentMonthIndex-- }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Month", tint = if (currentMonthIndex > 0) colors.textPrimary else colors.textMuted)
                }
                Text(monthName, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                IconButton(onClick = { if (currentMonthIndex < allMonthsList.lastIndex) currentMonthIndex++ }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next Month", tint = if (currentMonthIndex < allMonthsList.lastIndex) colors.textPrimary else colors.textMuted)
                }
            }
            
            val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                daysOfWeek.forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            val lastDayOfMonth = (28..31).reversed().first { d ->
                try {
                    LocalDate(y, m, d)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            
            val totalCells = firstDayOfWeek + lastDayOfMonth
            val rows = (totalCells + 6) / 7
            
            Column(modifier = Modifier.fillMaxWidth()) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val day = cellIndex - firstDayOfWeek + 1
                            if (day in 1..lastDayOfMonth) {
                                val isWorkingDay = daysInMonth.contains(day)
                                val dateVal = y * 10000 + m * 100 + day
                                val isSkipped = skipDates.contains(dateVal)
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                !isWorkingDay -> Color.Transparent
                                                isSkipped -> Color(0xFFEF4444)
                                                else -> colors.accent.copy(alpha = 0.15f)
                                            }
                                        )
                                        .clickable(enabled = isWorkingDay) {
                                            if (isWorkingDay) {
                                                skipDates = if (isSkipped) skipDates - dateVal else skipDates + dateVal
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = AmazeTheme.typography.caption.copy(
                                            color = when {
                                                !isWorkingDay -> colors.textMuted
                                                isSkipped -> Color.White
                                                else -> colors.textPrimary
                                            },
                                            fontWeight = if (isWorkingDay) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(predictions) { pred ->
                SimpleCoursePredictorCard(
                    prediction = pred,
                    colors = colors
                )
            }
        }
    }
}

private data class CoursePrediction(
    val course: AttendanceItem,
    val futureClasses: Int,
    val skipCount: Int,
    val predictedAttended: Int,
    val predictedTotal: Int,
    val predictedPct: Double
)

@Composable
private fun SimpleCoursePredictorCard(
    prediction: CoursePrediction,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val course = prediction.course
    val currentPct = course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0
    val projectedPct = prediction.predictedPct

    AmazeCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: ${course.attendedClasses}/${course.totalClasses}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    if (prediction.skipCount > 0) {
                        Text("Skips: ${prediction.skipCount}", style = AmazeTheme.typography.caption.copy(color = Color(0xFFEF4444)))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pctFormatted(projectedPct),
                    style = AmazeTheme.typography.subheading.copy(
                        color = when {
                            projectedPct >= 85 -> Color(0xFF10B981)
                            projectedPct >= 75 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        },
                        fontWeight = FontWeight.Black
                    )
                )
                Text(
                    text = "${pctFormatted(currentPct)} now",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 10.sp)
                )
            }
        }
    }
}

@Composable
private fun SkipButton(text: String, onClick: () -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    }
}

private fun pctFormatted(value: Double): String {
    val i = kotlin.math.round(value * 100).toLong()
    val whole = i / 100
    val frac = (i % 100).coerceIn(0, 99)
    return "$whole.${frac.toString().padStart(2, '0')}%"
}

private data class SimpleDate(val month: Int, val day: Int, val year: Int)

private fun computeImportantDates(months: List<CalendarMonth>, examSchedule: Map<String, List<ExamItem>> = emptyMap()): Map<String, SimpleDate> {
    val monthIndex = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    val imp = mutableMapOf<String, SimpleDate>()
    val keywords = listOf("cat i", "cat ii", "fat", "lid for laboratory classes", "lid for theory classes")
    for (month in months) {
        val monthStr = month.month.lowercase()
        val m = monthIndex[monthStr.take(3)] ?: continue
        val y = monthStr.split(" ").lastOrNull()?.toIntOrNull() ?: continue
        for (day in month.days) {
            for (ev in day.events) {
                val text = ev.text.lowercase()
                for (kw in keywords) {
                    if (text.contains(kw) && !imp.containsKey(kw)) {
                        imp[kw] = SimpleDate(m, day.date, y)
                    }
                }
            }
        }
    }
    // Fallback: try to extract CAT/FAT dates from exam schedule
    if (!imp.containsKey("cat i") || !imp.containsKey("cat ii") || !imp.containsKey("fat")) {
        for ((_, items) in examSchedule) {
            for (item in items) {
                val parts = item.examDate.split("-")
                if (parts.size == 3) {
                    val ey = parts[0].toIntOrNull() ?: continue
                    val em = parts[1].toIntOrNull() ?: continue
                    val ed = parts[2].toIntOrNull() ?: continue
                    val label = item.courseTitle.lowercase()
                    when {
                        label.contains("cat 1") || label.contains("cat i") -> if (!imp.containsKey("cat i")) imp["cat i"] = SimpleDate(em, ed, ey)
                        label.contains("cat 2") || label.contains("cat ii") -> if (!imp.containsKey("cat ii")) imp["cat ii"] = SimpleDate(em, ed, ey)
                        label.contains("fat") -> if (!imp.containsKey("fat")) imp["fat"] = SimpleDate(em, ed, ey)
                    }
                }
            }
        }
    }
    return imp
}

private fun buildWorkingDays(months: List<CalendarMonth>): List<Triple<Int, Int, Int>> {
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
                t == "instructional day" || txt.contains("instructional day") || txt.contains("working")
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

private fun computeFutureClasses(
    courses: List<AttendanceItem>,
    dayCardsMap: Map<AttendanceDay, List<CourseAttendanceInfo>>,
    allWorkingDays: List<Triple<Int, Int, Int>>,
    selectedMode: String,
    impDates: Map<String, SimpleDate>
): Map<String, FutureClassInfo> {
    fun dayOfWeekToAbbr(y: Int, m: Int, d: Int): String? {
        return try {
            val day = LocalDate(y, m, d).dayOfWeek
            when (day) {
                DayOfWeek.SUNDAY -> "SUN"
                DayOfWeek.MONDAY -> "MON"
                DayOfWeek.TUESDAY -> "TUE"
                DayOfWeek.WEDNESDAY -> "WED"
                DayOfWeek.THURSDAY -> "THU"
                DayOfWeek.FRIDAY -> "FRI"
                DayOfWeek.SATURDAY -> "SAT"
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun dayAbbrToName(abbr: String): String = when (abbr) {
        "MON" -> "Monday"; "TUE" -> "Tuesday"; "WED" -> "Wednesday"
        "THU" -> "Thursday"; "FRI" -> "Friday"; "SAT" -> "Saturday"
        "SUN" -> "Sunday"; else -> abbr
    }

    val result = mutableMapOf<String, FutureClassInfo>()

    for (course in courses) {
        val code = course.courseCode
        val isLab = code.endsWith("(L)") || course.courseType == "Lab"

        val courseCutoff = when (selectedMode) {
            "CAT1" -> impDates["cat i"]
            "CAT2" -> impDates["cat ii"]
            "FAT" -> impDates["fat"]
            "LID" -> if (isLab) impDates["lid for laboratory classes"] else impDates["lid for theory classes"]
            else -> null
        }

        val courseDays = dayCardsMap.entries
            .filter { (_, list) -> list.any { it.courseCode == code } }
            .map { it.key }
        val courseDayAbbrs = courseDays.map { it.name }

        val futureDates = mutableListOf<FutureDate>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayVal = today.year * 10000 + today.monthNumber * 100 + today.dayOfMonth
        for ((y, m, d) in allWorkingDays) {
            val dateVal = y * 10000 + m * 100 + d
            if (dateVal < todayVal) continue
            if (courseCutoff != null) {
                val cutoffVal = courseCutoff.year * 10000 + courseCutoff.month * 100 + courseCutoff.day
                if (dateVal > cutoffVal) continue
            }
            val abbr = dayOfWeekToAbbr(y, m, d) ?: continue
            if (abbr in courseDayAbbrs) {
                val display = "${m}/${d}"
                futureDates.add(FutureDate(dateVal, display, dayAbbrToName(abbr)))
                if (isLab) futureDates.add(FutureDate(dateVal, "$display (Lab)", dayAbbrToName(abbr)))
            }
        }

        val count = futureDates.size
        result[code] = FutureClassInfo(count, futureDates)
    }

    return result
}

private data class FutureDate(val dateVal: Int, val display: String, val dayAbbr: String)
private data class FutureClassInfo(val total: Int, val dates: List<FutureDate> = emptyList())

@Composable
fun TimetableGridScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val timetableRes by AppState.timetable.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")
    val dayFull = mapOf("MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", "THU" to "Thursday", "FRI" to "Friday", "SAT" to "Saturday")

    // Build a map: day -> (slotCode -> courseInfo)
    val daySlotMap = remember(courses, timetableRes) {
        val map = mutableMapOf<String, MutableMap<String, AttendanceItem>>()
        for (day in days) {
            map[day] = mutableMapOf()
            val daySlots = SlotMap.map[day] ?: continue
            for (course in courses) {
                val slotRaw = (course.slotName ?: "")
                val slots = slotRaw.split("+")
                for (slot in slots) {
                    val trimmed = slot.trim().uppercase()
                    if (trimmed in daySlots) {
                        map[day]?.set(trimmed, course)
                    }
                }
            }
        }
        map
    }

    // Collect all unique time ranges per day, sorted
    val dayTimeSlots = remember {
        val map = mutableMapOf<String, List<Pair<String, String>>>() // day -> list of (slotCode, timeRange)
        for (day in days) {
            val slots = SlotMap.map[day]?.entries?.sortedBy { TimeMath.toMinutes(it.value.split("-")[0]) } ?: emptyList()
            map[day] = slots.map { it.key to it.value }
        }
        map
    }

        var selectedDay by remember { mutableStateOf<String?>(null) }
    var showTimetableDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeButton(
            text = "View Full Timetable",
            onClick = { showTimetableDialog = true },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        // Day selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                val isSelected = selectedDay == day
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .border(if (isSelected) 0.dp else 1.dp, colors.border, RoundedCornerShape(8.dp))
                        .clickable { selectedDay = if (isSelected) null else day }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.take(3),
                        color = if (isSelected) Color.White else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (selectedDay == null) {
            // Overview: show all days summary
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { day ->
                    val dayCourses = daySlotMap[day]?.values?.distinct() ?: emptyList()
                    val daySlots = dayTimeSlots[day] ?: emptyList()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                dayFull[day] ?: day,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (dayCourses.isEmpty()) {
                                Text("No classes", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                            } else {
                                dayCourses.forEach { course ->
                                    val slotRaw = (course.slotName ?: "")
                                    val slots = slotRaw.split("+")
                                    val times = slots.mapNotNull { s ->
                                        val trimmed = s.trim().uppercase()
                                        daySlots.firstOrNull { it.first == trimmed }?.second
                                    }
                                    val timeStr = if (times.isNotEmpty()) times.joinToString(", ") else slotRaw
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colors.accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                            Text(course.courseTitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1)
                                        }
                                        Text(timeStr, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontSize = 10.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Detailed day view: show each time slot
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Text(
                            "${dayFull[selectedDay] ?: selectedDay} — ${dayTimeSlots[selectedDay]?.size ?: 0} slots",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val slots = dayTimeSlots[selectedDay] ?: emptyList()
                items(slots) { (slotCode, timeRange) ->
                    val course = daySlotMap[selectedDay]?.get(slotCode)
                    val hasClass = course != null
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hasClass) colors.accent.copy(alpha = 0.06f) else colors.surface)
                            .border(1.dp, if (hasClass) colors.accent.copy(alpha = 0.2f) else colors.border, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.width(64.dp)) {
                                Text(slotCode, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = if (hasClass) colors.accent else colors.textMuted))
                                Text(timeRange, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 10.sp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (hasClass) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary), maxLines = 1)
                                    Text("${course.courseCode} • ${course.attendedClasses}/${course.totalClasses}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 85 -> Color(0xFF10B981).copy(alpha = 0.12f)
                                                (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 75 -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                                else -> Color(0xFFEF4444).copy(alpha = 0.12f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        course.attendancePercentage,
                                        color = when {
                                            (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 85 -> Color(0xFF10B981)
                                            (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 75 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    "Free",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = courses,
            timetableCourses = timetableRes?.courseInfo ?: emptyList(),
            onDismiss = { showTimetableDialog = false }
        )
    }
}

}
