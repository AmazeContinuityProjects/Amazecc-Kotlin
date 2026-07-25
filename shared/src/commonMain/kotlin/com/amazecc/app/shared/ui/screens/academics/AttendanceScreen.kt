package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.utils.AttendanceDay
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.utils.SlotInfo
import kotlinx.datetime.*
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
        HeaderSpacer()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            views.forEach { view ->
                val isSelected = activeView == view
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = bouncySpring()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(if (isSelected) colors.accent else colors.surface)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent else colors.border,
                            CircleShape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { activeView = view }
                        )
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = view,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = if (isSelected) colors.background else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (activeView) {
                "Timetable" -> DailyPlannerScreen()
                "Predictor" -> OverallPredictorScreen()
                "Calendar" -> CalendarScreen(onBack = {}, showHeader = false, autoFetch = false)
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
    var skipDates by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var expandedCourse by remember { mutableStateOf<String?>(null) }
    var resetTrigger by remember { mutableStateOf(0) }

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
                mapOf(
                    "courseCode" to item.courseCode,
                    "courseTitle" to item.courseTitle,
                    "courseType" to item.courseType,
                    "faculty" to item.faculty,
                    "slotName" to (item.slotName ?: ""),
                    "attendancePercentage" to item.attendancePercentage,
                    "venue" to (item.slotVenue ?: "")
                )
            },
            slotMap = slotMapTyped
        )
    }

    val allWorkingDays = remember(calendarMonths) {
        buildWorkingDays(calendarMonths)
    }

    val futureClassesMap = remember(allWorkingDays, dayCardsMap, selectedMode, impDates, resetTrigger, calendarRes) {
        computeFutureClasses(courses, dayCardsMap, allWorkingDays, selectedMode, impDates, calendarRes)
    }

    val predictions = remember(skipDates, futureClassesMap, courses) {
        courses.map { course ->
            val code = course.courseCode
            val attended = course.attendedClasses
            val total = course.totalClasses
            val futureInfo = futureClassesMap[code]
            val futureCount = futureInfo?.total ?: 0
            val futureDates = futureInfo?.dates ?: emptyList()
            val skippedSkipDates = skipDates[code] ?: emptySet()
            val skipCount = futureDates.count { it.display in skippedSkipDates }
            val effectiveAttend = futureCount - skipCount.coerceIn(0, futureCount)
            val predictedAttended = attended + effectiveAttend
            val predictedTotal = total + futureCount
            val predictedPct = if (predictedTotal > 0) (predictedAttended.toDouble() / predictedTotal * 100) else 0.0
            CoursePrediction(course, futureCount, skipCount, predictedAttended, predictedTotal, predictedPct, futureDates)
        }
    }

    val totalPredictedAttended = predictions.sumOf { it.predictedAttended }
    val totalPredictedTotal = predictions.sumOf { it.predictedTotal }
    val overallPct = if (totalPredictedTotal > 0) totalPredictedAttended.toDouble() / totalPredictedTotal * 100 else 0.0

    val modes = listOf("CAT1", "CAT2", "FAT", "LID")

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 88.dp)) {
        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = colors.surface
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Predicted Overall Attendance ($selectedMode)", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pctFormatted(overallPct),
                    style = AmazeTheme.typography.heading.copy(
                        color = when {
                            overallPct >= 85 -> colors.chart1
                            overallPct >= 75 -> colors.chart3
                            else -> colors.chart5
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

        Spacer(modifier = Modifier.height(16.dp))

        Text("Cutoff Target", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = bouncySpring()
                )
                val displayLabel = when (mode) {
                    "CAT1" -> "CAT - I"
                    "CAT2" -> "CAT - II"
                    "FAT" -> "FAT"
                    else -> "LID"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(if (isSelected) colors.accent else colors.surface)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { selectedMode = mode }
                        )
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        displayLabel,
                        color = if (isSelected) colors.background else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }

        if (cutoffDate != null) {
            Spacer(modifier = Modifier.height(4.dp))
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
        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayVal = today.year * 10000 + today.month.number * 100 + today.dayOfMonth
        val remainingDays = allWorkingDays.count { (y, m, d) ->
            val dateVal = y * 10000 + m * 100 + d
            dateVal >= todayVal && (cutoffDate == null || dateVal <= cutoffDate.year * 10000 + cutoffDate.month * 100 + cutoffDate.day)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.06f))
                .border(1.dp, colors.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(16.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        // ── Multi-Day Batch Simulator Card ──
        var batchBunkDays by remember { mutableStateOf(1f) }
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TaskAlt, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Multi-Day Bunk Simulator", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp))
                }
                Spacer(Modifier.height(4.dp))
                Text("Simulate skipping upcoming working days across all courses at once.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip Next ${batchBunkDays.toInt()} Working Day${if (batchBunkDays.toInt() > 1) "s" else ""}", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                    Slider(
                        value = batchBunkDays,
                        onValueChange = { batchBunkDays = it },
                        valueRange = 1f..7f,
                        steps = 5,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AmazeButton(
                        text = "Simulate ${batchBunkDays.toInt()} Day Bunk",
                        onClick = {
                            val daysToBunkCount = batchBunkDays.toInt()
                            val newSkipMap = mutableMapOf<String, Set<String>>()
                            predictions.forEach { pred ->
                                val datesToSkip = pred.futureDates.take(daysToBunkCount).map { it.display }.toSet()
                                newSkipMap[pred.course.courseCode] = datesToSkip
                            }
                            skipDates = newSkipMap
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.PRIMARY
                    )
                    if (skipDates.isNotEmpty()) {
                        AmazeButton(
                            text = "Reset",
                            onClick = { skipDates = emptyMap(); resetTrigger++ },
                            modifier = Modifier.weight(0.4f),
                            variant = ButtonVariant.SECONDARY
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tap a course to choose which dates to skip",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
                TextButton(onClick = {
                    skipDates = emptyMap()
                    resetTrigger++
                }) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            predictions.forEach { pred ->
                val isExpanded = expandedCourse == pred.course.courseCode
                ExpandedCoursePredictorCard(
                    prediction = pred,
                    isExpanded = isExpanded,
                    skipDates = skipDates[pred.course.courseCode] ?: emptySet(),
                    onToggleExpand = {
                        expandedCourse = if (isExpanded) null else pred.course.courseCode
                    },
                    onToggleSkipDate = { dateKey ->
                        val current = skipDates[pred.course.courseCode]?.toMutableSet() ?: mutableSetOf()
                        if (dateKey in current) current.remove(dateKey) else current.add(dateKey)
                        skipDates = skipDates + (pred.course.courseCode to current)
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
    val predictedPct: Double,
    val futureDates: List<FutureDate> = emptyList()
)

@Composable
private fun ExpandedCoursePredictorCard(
    prediction: CoursePrediction,
    isExpanded: Boolean,
    skipDates: Set<String>,
    onToggleExpand: () -> Unit,
    onToggleSkipDate: (String) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val course = prediction.course
    val currentPct = course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0
    val projectedPct = prediction.predictedPct

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpand
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current: ${course.attendedClasses}/${course.totalClasses}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text(if (prediction.skipCount > 0) "Skips: ${prediction.skipCount}" else "", style = AmazeTheme.typography.caption.copy(color = if (prediction.skipCount > 0) colors.chart5 else colors.textSecondary))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = pctFormatted(projectedPct),
                        style = AmazeTheme.typography.subheading.copy(
                            color = when {
                                projectedPct >= 85 -> colors.chart1
                                projectedPct >= 75 -> colors.chart3
                                else -> colors.chart5
                            },
                            fontWeight = FontWeight.Black
                        )
                    )
                    Text(
                        text = "${pctFormatted(currentPct)} now",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 10.sp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isExpanded && prediction.futureDates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Future classes — tap to mark skip",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                )
                Spacer(modifier = Modifier.height(8.dp))
                prediction.futureDates.forEach { fd ->
                    val isSkipped = fd.display in skipDates
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleSkipDate(fd.display) }
                            .background(if (isSkipped) colors.chart5.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSkipped) colors.chart5 else colors.chart1)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(fd.display, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                                Text(fd.dayAbbr, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 11.sp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSkipped) colors.chart5.copy(alpha = 0.15f) else colors.chart1.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isSkipped) "SKIP" else "ATTEND",
                                color = if (isSkipped) colors.chart5 else colors.chart1,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("unused")
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
    // Match various spellings: "CAT I", "CAT - I", "CAT-I", "CAT1", etc.
    val catIPattern = Regex("cat\\s*[-–]?\\s*i(?!i)", RegexOption.IGNORE_CASE)
    val catIIPattern = Regex("cat\\s*[-–]?\\s*ii", RegexOption.IGNORE_CASE)
    val fatPattern = Regex("\\bfat\\b", RegexOption.IGNORE_CASE)
    val lidLabPattern = Regex("lid\\s+for\\s+lab", RegexOption.IGNORE_CASE)
    val lidTheoryPattern = Regex("lid\\s+for\\s+theory", RegexOption.IGNORE_CASE)

    fun oneDayBefore(m: Int, d: Int, y: Int): SimpleDate {
        return try {
            // Manually decrement day, handling month boundaries
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
    // Fallback: exam schedule
    if (!imp.containsKey("cat i") || !imp.containsKey("cat ii") || !imp.containsKey("fat")) {
        for ((_, items) in examSchedule) {
            for (item in items) {
                val parts = item.examDate.split("-")
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
    impDates: Map<String, SimpleDate>,
    calendar: com.amazecc.app.shared.model.CalendarRes? = null
): Map<String, FutureClassInfo> {
    fun dayOfWeekToAbbr(y: Int, m: Int, d: Int): String? {
        return try {
            val date = LocalDate(y, m, d)
            val attDay = AttendanceTimetable.getAttendanceDayForDate(date, calendar)
            attDay.name
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
        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        // Use start of today so we include today's remaining classes
        val todayVal = today.year * 10000 + today.month.number * 100 + today.dayOfMonth
        for ((y, m, d) in allWorkingDays) {
            val dateVal = y * 10000 + m * 100 + d
            // Only count instructional days from today onwards (>= today)
            if (dateVal < todayVal) continue
            if (courseCutoff != null) {
                val cutoffVal = courseCutoff.year * 10000 + courseCutoff.month * 100 + courseCutoff.day
                if (dateVal > cutoffVal) continue
            }
            val abbr = dayOfWeekToAbbr(y, m, d) ?: continue
            if (abbr in courseDayAbbrs) {
                val display = "${m}/${d}"
                // For labs, count as 2 slots (theory + practical in same day)
                futureDates.add(FutureDate(dateVal, display, dayAbbrToName(abbr)))
                if (isLab) futureDates.add(FutureDate(dateVal, "$display (Lab2)", dayAbbrToName(abbr)))
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

    val calendarRes by AppState.calendar.collectAsState()
    val todayDate = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val mondayDate = remember(todayDate) { todayDate.minus(DatePeriod(days = todayDate.dayOfWeek.ordinal)) }

    // Map each calendar day of week to any Day Order override for this week's dates
    val weekDayOverrides = remember(mondayDate, calendarRes) {
        val map = mutableMapOf<String, AttendanceDay>()
        (0..6).forEach { offset ->
            val date = mondayDate.plus(DatePeriod(days = offset))
            val override = AttendanceTimetable.getDayOrderOverrideForDate(date, calendarRes)
            if (override != null) {
                val calendarDayAbbr = when (date.dayOfWeek) {
                    DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
                    DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
                    DayOfWeek.SUNDAY -> "SUN"; else -> ""
                }
                if (calendarDayAbbr.isNotEmpty()) {
                    map[calendarDayAbbr] = override
                }
            }
        }
        map
    }

    // Build a map: day -> (slotCode -> courseInfo)
    val daySlotMap = remember(courses, timetableRes, weekDayOverrides) {
        val map = mutableMapOf<String, MutableMap<String, AttendanceItem>>()
        for (day in days) {
            map[day] = mutableMapOf()
            val effectiveDay = weekDayOverrides[day]?.name ?: day
            val daySlots = SlotMap.map[effectiveDay] ?: continue
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
    val dayTimeSlots = remember(weekDayOverrides) {
        val map = mutableMapOf<String, List<Pair<String, String>>>() // day -> list of (slotCode, timeRange)
        for (day in days) {
            val effectiveDay = weekDayOverrides[day]?.name ?: day
            val slots = SlotMap.map[effectiveDay]?.entries?.sortedBy { TimeMath.toMinutes(it.value.split("-")[0]) } ?: emptyList()
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
                val overrideForDay = weekDayOverrides[day]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .border(if (isSelected) 0.dp else 1.dp, if (overrideForDay != null) colors.accent.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(8.dp))
                        .clickable { selectedDay = if (isSelected) null else day }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (overrideForDay != null) "${day.take(3)}⚡" else day.take(3),
                        color = if (isSelected) Color.White else if (overrideForDay != null) colors.accent else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (selectedDay == null) {
            // Overview: show all days summary
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(days) { day ->
                    val dayCourses = daySlotMap[day]?.values?.distinct() ?: emptyList()
                    val daySlots = dayTimeSlots[day] ?: emptyList()
                    val overrideForDay = weekDayOverrides[day]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, if (overrideForDay != null) colors.accent.copy(alpha = 0.4f) else colors.border, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    dayFull[day] ?: day,
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                )
                                if (overrideForDay != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "⚡ ${overrideForDay.name} Order",
                                            style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        )
                                    }
                                }
                            }
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { AppState.openCourseDetail(course.courseCode) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colors.accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            val venueStr = course.slotVenue?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                                            Text("${course.courseCode}$venueStr", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
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
                            .clickable { if (course != null) AppState.openCourseDetail(course.courseCode) }
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
                                    val venueStr = course.slotVenue?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                                    Text("${course.courseCode} • ${course.attendedClasses}/${course.totalClasses}$venueStr", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 85 -> colors.chart1.copy(alpha = 0.12f)
                                                (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 75 -> colors.chart3.copy(alpha = 0.12f)
                                                else -> colors.chart5.copy(alpha = 0.12f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        course.attendancePercentage,
                                        color = when {
                                            (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 85 -> colors.chart1
                                            (course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0) >= 75 -> colors.chart3
                                            else -> colors.chart5
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
