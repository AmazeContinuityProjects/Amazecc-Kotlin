package com.amazecc.app.shared.ui.screens.academics

import kotlinx.datetime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import kotlinx.serialization.json.*

@Composable
fun CourseAttendanceScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val courseCode = AppState.selectedCourseCode.value
    val course = attendanceRes?.attendance?.find { it.courseCode == courseCode }

    if (course == null) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Course not found", color = colors.textSecondary)
            }
        }
        return
    }

    var activeTab by remember { mutableStateOf("Predictor") }
    val tabs = listOf("Predictor", "Log", "Notes")

    // Predictor state
    val calendarMonths = calendarRes?.months ?: emptyList()
    val impDates = remember(calendarMonths) { computeImportantDates(calendarMonths) }
    val allWorkingDays = remember(calendarMonths) { buildWorkingDays(calendarMonths) }
    var mode by remember { mutableStateOf("LID") }

    val slotMapTyped = remember {
        SlotMap.map.mapValues { (_, inner) ->
            inner.mapValues { (_, time) -> SlotInfo(time) }
        }
    }
    val dayCardsMap = remember {
        attendanceRes?.attendance?.let { att ->
            AttendanceTimetable.buildAttendanceDayCardsMap(
                attendance = att.map { item ->
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
                            "attendancePercentage" to item.attendancePercentage,
                            "venue" to (item.slotVenue ?: "")
                        )
                },
                slotMap = slotMapTyped
            )
        } ?: emptyMap()
    }

    // Find course days
    val courseDays = remember(dayCardsMap, courseCode) {
        dayCardsMap.entries
            .filter { (_, list) -> list.any { it.courseCode == courseCode } }
            .map { it.key.name }
    }

    // Future class dates for this course up to cutoff
    val cutoffDate = remember(mode, impDates) {
        when (mode) {
            "CAT1" -> impDates["cat i"]; "CAT2" -> impDates["cat ii"]
              "LID" -> {
                  val lab = impDates["lid for laboratory classes"]
                  val theory = impDates["lid for theory classes"]
                  val isLab = course.courseCode.endsWith("(L)") || course.courseType == "Lab"
                  if (isLab) {
                      lab ?: theory
                  } else {
                      theory ?: lab
                  }
              }
            else -> null
        }
    }

    val futureClassDates = remember(allWorkingDays, courseDays, cutoffDate) {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val todayVal = today.year * 10000 + today.monthNumber * 100 + today.dayOfMonth
        for ((y, m, d) in allWorkingDays) {
            val dv = y * 10000 + m * 100 + d
            if (dv < todayVal) continue
            if (cutoffDate != null) {
                val cv = cutoffDate.year * 10000 + cutoffDate.month * 100 + cutoffDate.day
                if (dv > cv) continue
            }
            val abbr = kotlinx.datetime.LocalDate(y, m, d).let { dt ->
                when (dt.dayOfWeek) {
                    kotlinx.datetime.DayOfWeek.SUNDAY -> "SUN"
                    kotlinx.datetime.DayOfWeek.MONDAY -> "MON"
                    kotlinx.datetime.DayOfWeek.TUESDAY -> "TUE"
                    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "WED"
                    kotlinx.datetime.DayOfWeek.THURSDAY -> "THU"
                    kotlinx.datetime.DayOfWeek.FRIDAY -> "FRI"
                    kotlinx.datetime.DayOfWeek.SATURDAY -> "SAT"
                    else -> ""
                }
            }
            if (abbr in courseDays) result.add(Triple(y, m, d))
        }
        result
    }

    var skipDates by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isLab = courseCode?.endsWith("(L)") == true || course.courseType == "Lab"
    val multiplier = if (isLab) 2 else 1
    val futureCount = futureClassDates.size * multiplier
    val skipCount = skipDates.size * multiplier

    val predictedAttended = course.attendedClasses + (futureCount - skipCount)
    val predictedTotal = course.totalClasses + futureCount
    val predictedPct = if (predictedTotal > 0) predictedAttended.toDouble() / predictedTotal * 100 else 0.0
    val currentPct = course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = course.courseTitle,
            description = "${course.courseCode} • ${course.slotName ?: ""}",
            showBackButton = true,
            showSyncButton = false
        )

        // Stats bar
        AmazeCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            backgroundColor = colors.surface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PctStat("Current", pctFormatted(currentPct), Color(0xFF3B82F6))
                PctStat("Future", "$futureCount classes", Color(0xFF8B5CF6))
                PctStat("Projected", pctFormatted(predictedPct), projectedColor(predictedPct))
            }
        }

        TabRow(
            selectedTabIndex = tabs.indexOf(activeTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(tab, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        when (activeTab) {
            "Predictor" -> {
            PredictorTab(
                mode = mode,
                    onModeChange = { mode = it },
                    futureDates = futureClassDates,
                    skipDates = skipDates,
                    onToggleSkip = { key ->
                        skipDates = if (key in skipDates) skipDates - key else skipDates + key
                    },
                    predictedPct = predictedPct,
                    predictedAttended = predictedAttended,
                    predictedTotal = predictedTotal,
                    colors = colors
                )
            }
            "Log" -> LogTab(course = course, colors = colors)
            "Notes" -> NotesTab(courseCode = courseCode ?: "", colors = colors)
        }
    }
}

@Composable
private fun PctStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
        )
        Text(
            label,
            style = AmazeTheme.typography.caption.copy(color = AmazeTheme.colors.textSecondary, fontSize = 10.sp)
        )
    }
}

private fun projectedColor(pct: Double): Color = when {
    pct >= 85 -> Color(0xFF10B981)
    pct >= 75 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

@Composable
private fun PredictorTab(
    mode: String,
    onModeChange: (String) -> Unit,
    futureDates: List<Triple<Int, Int, Int>>,
    skipDates: Set<Int>,
    onToggleSkip: (Int) -> Unit,
    predictedPct: Double,
    predictedAttended: Int,
    predictedTotal: Int,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val modes = listOf("CAT1", "CAT2", "LID")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Cutoff Target", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            modes.forEach { m ->
                val sel = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (sel) colors.accent else colors.surface, RoundedCornerShape(8.dp))
                        .clickable { onModeChange(m) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(m, color = if (sel) Color.White else colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Future Classes (${futureDates.size}) — tap to mark skip",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (futureDates.isEmpty()) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No future classes found up to this cutoff.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(futureDates.sortedBy { it.first * 10000 + it.second * 100 + it.third }) { (y, m, d) ->
                    val key = y * 10000 + m * 100 + d
                    val skipped = key in skipDates
                    val dateStr = "${m}/${d}/$y"
                    val weekday = try {
                        when (kotlinx.datetime.LocalDate(y, m, d).dayOfWeek) {
                            kotlinx.datetime.DayOfWeek.SUNDAY -> "Sun"
                            kotlinx.datetime.DayOfWeek.MONDAY -> "Mon"
                            kotlinx.datetime.DayOfWeek.TUESDAY -> "Tue"
                            kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Wed"
                            kotlinx.datetime.DayOfWeek.THURSDAY -> "Thu"
                            kotlinx.datetime.DayOfWeek.FRIDAY -> "Fri"
                            kotlinx.datetime.DayOfWeek.SATURDAY -> "Sat"
                            else -> "?"
                        }
                    } catch (_: Exception) { "?" }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (skipped) colors.danger.copy(alpha = 0.12f) else colors.surface)
                            .border(
                                1.dp,
                                if (skipped) colors.danger.copy(alpha = 0.3f) else colors.border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleSkip(key) }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (skipped) colors.danger.copy(alpha = 0.2f) else colors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$d",
                                    style = AmazeTheme.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (skipped) colors.danger else colors.accent
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(weekday, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                                Text(dateStr, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            if (skipped) {
                                Box(
                                    modifier = Modifier
                                        .background(colors.danger.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("SKIP", color = colors.danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF10B981).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("ATTEND", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Projected", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text("$predictedAttended / $predictedTotal", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Text(
                    pctFormatted(predictedPct),
                    style = AmazeTheme.typography.subheading.copy(
                        color = projectedColor(predictedPct),
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun LogTab(
    course: AttendanceItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    // Try to parse viewLinkRaw for detailed attendance
    val detailedDays = remember(course.viewLinkRaw) {
        try {
            val arr = course.viewLinkRaw?.jsonArray
            arr?.mapNotNull { elem ->
                val obj = elem.jsonObject
                val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Pair(date, status)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatChip("Attended", course.attendedClasses.toString(), Color(0xFF10B981), colors)
            StatChip("Total", course.totalClasses.toString(), colors.accent, colors)
            StatChip("Avg", course.attendancePercentage, Color(0xFF3B82F6), colors)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (detailedDays.isNotEmpty()) {
            Text(
                "Attendance Log (${detailedDays.size} entries)",
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(detailedDays.sortedByDescending { it.first }) { (date, status) ->
                    val isPresent = status.lowercase() in listOf("present", "p")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(date, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = 13.sp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isPresent) Color(0xFF10B981).copy(alpha = 0.12f)
                                    else colors.danger.copy(alpha = 0.12f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isPresent) "Present" else "Absent",
                                color = if (isPresent) Color(0xFF10B981) else colors.danger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Detailed log unavailable.\nCheck your sync settings.",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmazeTheme.typography.subheading.copy(color = color, fontWeight = FontWeight.Bold))
        Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 10.sp))
    }
}

@Composable
private fun NotesTab(
    courseCode: String,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var notesSaved by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Notes for $courseCode\n\n(Local storage not yet implemented)", color = colors.textSecondary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (notesSaved) {
            Text("Notes saved in memory!", color = Color(0xFF10B981), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        AmazeButton(
            text = "Save Notes",
            onClick = { notesSaved = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Save
        )
    }
}

// Reused helpers from AttendanceScreen.kt
data class PredDate(val month: Int, val day: Int, val year: Int)

private fun computeImportantDates(months: List<CalendarMonth>): Map<String, PredDate> {
    val monthIndex = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )
    val imp = mutableMapOf<String, PredDate>()
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
                        imp[kw] = PredDate(m, day.date, y)
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

private fun pctFormatted(value: Double): String {
    val i = kotlin.math.round(value * 100).toLong()
    val whole = i / 100
    val frac = (i % 100).coerceIn(0, 99)
    return "$whole.${frac.toString().padStart(2, '0')}%"
}
