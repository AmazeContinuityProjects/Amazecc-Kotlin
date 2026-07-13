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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.AttendanceDay
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.utils.SlotInfo

@Composable
fun AttendanceScreen() {
    val colors = AmazeTheme.colors
    var activeSubTab by remember { mutableStateOf("Timeline") }
    val tabs = listOf("Timeline", "Predictor", "Timetable Grid")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Attendance Hub",
            description = "Track your attendance, view timelines and predict shortfalls",
            showBackButton = true,
            showSyncButton = true
        )

        TabRow(
            selectedTabIndex = tabs.indexOf(activeSubTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeSubTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            when (activeSubTab) {
                "Timeline" -> DailyPlannerScreen()
                "Predictor" -> OverallPredictorScreen()
                "Timetable Grid" -> TimetableGridScreen()
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
            text = " ()",
            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textMuted)
        )
    }
}

@Composable
fun OverallPredictorScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendarData.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    val calendarMonths = calendarRes?.months ?: emptyList()

    var selectedMode by remember { mutableStateOf("LID") }
    var skips by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var resetTrigger by remember { mutableStateOf(0) }

    // Compute important event dates from calendar
    val impDates = remember(calendarMonths) {
        computeImportantDates(calendarMonths)
    }

    val cutoffDate = remember(selectedMode, impDates) {
        when (selectedMode) {
            "CAT1" -> impDates["cat i"]?.let { parseDate(it) }
            "CAT2" -> impDates["cat ii"]?.let { parseDate(it) }
            "LID" -> {
                val labDate = impDates["lid for laboratory classes"]?.let { parseDate(it) }
                val theoryDate = impDates["lid for theory classes"]?.let { parseDate(it) }
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

    // Build day cards map for course-weekday mapping
    val slotMapTyped = remember {
        SlotMap.map.mapValues { (_, inner) ->
            inner.mapValues { (_, time) -> SlotInfo(time) }
        }
    }
    val dayCardsMap = remember(courses) {
        AttendanceTimetable.buildAttendanceDayCardsMap(
            attendance = courses.map { item ->
                mapOf(
                    "courseCode" to item.courseCode,
                    "courseTitle" to item.courseTitle,
                    "courseType" to item.courseType,
                    "faculty" to item.faculty,
                    "slotName" to (item.slotVenue?.split("\\s+".toRegex())?.firstOrNull() ?: item.slotName),
                    "attendancePercentage" to item.attendancePercentage
                )
            },
            slotMap = slotMapTyped
        )
    }

    // All working days from calendar months
    val allWorkingDays = remember(calendarMonths) {
        buildWorkingDays(calendarMonths)
    }

    // Future classes per course
    val futureClassesMap = remember(allWorkingDays, dayCardsMap, cutoffDate, resetTrigger) {
        computeFutureClasses(courses, dayCardsMap, allWorkingDays, cutoffDate)
    }

    // Predictions per course
    val predictions = remember(skips, futureClassesMap, courses) {
        courses.map { course ->
            val code = course.courseCode
            val attended = course.attendedClasses
            val total = course.totalClasses
            val futureInfo = futureClassesMap[code]
            val futureCount = futureInfo?.total ?: 0
            val skipCount = skips[code] ?: 0
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

    val modes = listOf("CAT1", "CAT2", "LID")

    Column(modifier = Modifier.fillMaxSize()) {
        // Overall summary card
        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = colors.surface
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Predicted Overall Attendance ($selectedMode)", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.1f%%".format(overallPct),
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

        // Mode selector
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
                    Text(mode, color = if (isSelected) Color.White else colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Cutoff date info
        if (cutoffDate != null) {
            Text(
                text = "Cutoff: ${cutoffDate.month}/${cutoffDate.day}",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                skips = emptyMap()
                resetTrigger++
            }) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Skips", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium))
            }
        }

        // Course list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(predictions) { pred ->
                CoursePredictorCard(
                    prediction = pred,
                    onSkipChange = { newSkip ->
                        skips = skips + (pred.course.courseCode to newSkip.coerceIn(0, pred.futureClasses))
                    },
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
private fun CoursePredictorCard(
    prediction: CoursePrediction,
    onSkipChange: (Int) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val course = prediction.course
    val currentPct = course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0
    val projectedPct = prediction.predictedPct

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { AppState.openCourseAttendance(course.courseCode) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: ${course.attendedClasses}/${course.totalClasses}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text("•", color = colors.textMuted)
                    Text("Future: ${prediction.futureClasses}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkipButton("-", onClick = { onSkipChange(prediction.skipCount - 1) }, colors)
                    Box(
                        modifier = Modifier
                            .background(colors.background, RoundedCornerShape(6.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("${prediction.skipCount}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    SkipButton("+", onClick = { onSkipChange(prediction.skipCount + 1) }, colors)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "skip${if (prediction.skipCount != 1) "s" else ""}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f%%".format(projectedPct),
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
                    text = "%.1f%% now".format(currentPct),
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

private data class SimpleDate(val month: Int, val day: Int, val year: Int)

private fun computeImportantDates(months: List<CalendarMonth>): Map<String, SimpleDate> {
    val monthIndex = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    val imp = mutableMapOf<String, SimpleDate>()
    val keywords = listOf("cat i", "cat ii", "lid for laboratory classes", "lid for theory classes")
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
    return imp
}

private fun parseDate(date: SimpleDate): SimpleDate? = date

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
    cutoffDate: SimpleDate?
): Map<String, FutureClassInfo> {
    val weekdayNameMap = mapOf(
        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun"
    )
    val day3toFull = mapOf(
        "MON" to AttendanceDay.MON, "TUE" to AttendanceDay.TUE, "WED" to AttendanceDay.WED,
        "THU" to AttendanceDay.THU, "FRI" to AttendanceDay.FRI, "SAT" to AttendanceDay.SAT,
        "SUN" to AttendanceDay.SUN
    )

    // Map day number → day abbreviation for dayCardsMap
    fun dayOfWeekToAbbr(y: Int, m: Int, d: Int): String? {
        // Use Zeller's congruence or kotlinx datetime
        return try {
            val date = kotlinx.datetime.LocalDate(y, m, d)
            val names = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
            names[date.dayOfWeek.value % 7]
        } catch (e: Exception) { null }
    }

    val result = mutableMapOf<String, FutureClassInfo>()

    for (course in courses) {
        val code = course.courseCode
        val slotRaw = course.slotVenue?.split("\\s+".toRegex())?.firstOrNull() ?: course.slotName
        val isLab = code.endsWith("(L)") || course.courseType == "Lab"

        // Find which days this course has classes
        val courseDays = dayCardsMap.entries
            .filter { (_, list) -> list.any { it.courseCode == code } }
            .map { it.key }
        val courseDayAbbrs = courseDays.map { it.name }

        var futureCount = 0
        for ((y, m, d) in allWorkingDays) {
            if (cutoffDate != null) {
                val dateVal = y * 10000 + m * 100 + d
                val cutoffVal = cutoffDate.year * 10000 + cutoffDate.month * 100 + cutoffDate.day
                if (dateVal > cutoffVal) continue
            }
            val abbr = dayOfWeekToAbbr(y, m, d) ?: continue
            if (abbr in courseDayAbbrs) {
                futureCount++
            }
        }

        if (isLab) futureCount *= 2
        result[code] = FutureClassInfo(futureCount)
    }

    return result
}

private data class FutureClassInfo(val total: Int)

@Composable
fun TimetableGridScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Weekly Matrix View", 
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Matrix view is optimized for large screens or horizontal orientation. Here are your listed courses:",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }
        
        item {
            Text("Course Reference", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
        
        items(courses) { course ->
            AmazeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppState.openCourseAttendance(course.courseCode) }
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(colors.accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Attended: ${course.attendedClasses}/${course.totalClasses} (${course.attendancePercentage})", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
        }
    }
}
