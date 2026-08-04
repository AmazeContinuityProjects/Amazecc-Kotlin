package com.amazecc.app.shared.ui.screens.academics

import kotlinx.datetime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.parseViewLink
import kotlinx.serialization.json.*

@Composable
fun CourseAttendanceScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val courseCode = AppState.selectedCourseCode.value
    val course = attendanceRes?.attendance?.find { it.courseCode == courseCode }

    if (course == null) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Text("Course not found", color = colors.textSecondary)
            }
        }
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
    ) {
        ScreenHeader(
            title = course.courseTitle,
            description = "${course.courseCode} • ${course.slotName ?: ""}",
            showBackButton = true,
            showSyncButton = false
        )

        HeaderSpacer()

        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            EmbeddedCourseAttendanceView(course = course)
        }
    }
}

@Composable
fun EmbeddedCourseAttendanceView(course: AttendanceItem) {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val courseCode = course.courseCode

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
                if (isLab) lab ?: theory else theory ?: lab
            }
            else -> null
        }
    }

    val futureClassDates = remember(allWorkingDays, courseDays, cutoffDate) {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val todayVal = today.year * 10000 + today.monthNumber * 100 + today.dayOfMonth
        for ((y, m, d) in allWorkingDays) {
            val dv = y * 10000 + m * 100 + d
            if (dv < todayVal) continue
            if (cutoffDate != null) {
                val cv = cutoffDate.year * 10000 + cutoffDate.month * 100 + cutoffDate.day
                if (dv > cv) continue
            }
            val abbr = LocalDate(y, m, d).let { dt ->
                when (dt.dayOfWeek) {
                    DayOfWeek.SUNDAY -> "SUN"
                    DayOfWeek.MONDAY -> "MON"
                    DayOfWeek.TUESDAY -> "TUE"
                    DayOfWeek.WEDNESDAY -> "WED"
                    DayOfWeek.THURSDAY -> "THU"
                    DayOfWeek.FRIDAY -> "FRI"
                    DayOfWeek.SATURDAY -> "SAT"
                    else -> ""
                }
            }
            if (abbr in courseDays) result.add(Triple(y, m, d))
        }
        result
    }

    var skipDates by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isLab = courseCode.endsWith("(L)") || course.courseType == "Lab"
    val multiplier = if (isLab) 2 else 1
    val futureCount = futureClassDates.size * multiplier
    val skipCount = skipDates.size * multiplier

    val predictedAttended = course.attendedClasses + (futureCount - skipCount)
    val predictedTotal = course.totalClasses + futureCount
    val predictedPct = if (predictedTotal > 0) predictedAttended.toDouble() / predictedTotal * 100 else 0.0
    val currentPct = course.attendancePercentage.replace("%", "").toDoubleOrNull() ?: 0.0

    // Independent What-If state
    var whatIfAttend by remember { mutableStateOf(0f) }
    var whatIfMiss by remember { mutableStateOf(0f) }

    val whatIfTotal = course.totalClasses + whatIfAttend.toInt() + whatIfMiss.toInt()
    val whatIfAttended = course.attendedClasses + whatIfAttend.toInt()
    val whatIfPct = if (whatIfTotal > 0) whatIfAttended.toDouble() / whatIfTotal * 100 else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = BOTTOM_NAV_PADDING)
    ) {
        // Bunk-O-Meter Hero Section
        AmazeCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            backgroundColor = colors.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                    val animatedPct by animateFloatAsState(
                        targetValue = currentPct.toFloat(),
                        animationSpec = tween(1500)
                    )
                    val sweepAngle = (animatedPct / 100f) * 240f
                    val baseColor = colors.border
                    val progressColor = projectedColor(currentPct, colors)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = baseColor,
                            startAngle = 150f,
                            sweepAngle = 240f,
                            useCenter = false,
                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = 150f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            pctFormatted(currentPct),
                            style = AmazeTheme.typography.display.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 36.sp,
                                color = progressColor
                            )
                        )
                        Text(
                            if (currentPct >= 75) "SAFE" else "DANGER",
                            style = AmazeTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (currentPct >= 75) colors.success else colors.danger
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PctStat("Attended", "${course.attendedClasses}", colors.accent)
                    PctStat("Total", "${course.totalClasses}", colors.textPrimary)
                    PctStat("Target (75%)", "${kotlin.math.ceil(course.totalClasses * 0.75).toInt()}", colors.warning)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = activeTab == tab
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
                            onClick = { activeTab = tab }
                        )
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isSelected) colors.background else colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        when (activeTab) {
            "Predictor" -> PredictorSection(
                mode = mode,
                onModeChange = { mode = it },
                currentPct = currentPct,
                predictedPct = predictedPct,
                futureClassDates = futureClassDates,
                skipDates = skipDates,
                onSkipToggle = { d -> skipDates = if (d in skipDates) skipDates - d else skipDates + d },
                whatIfAttend = whatIfAttend,
                onWhatIfAttendChange = { whatIfAttend = it },
                whatIfMiss = whatIfMiss,
                onWhatIfMissChange = { whatIfMiss = it },
                whatIfPct = whatIfPct,
                whatIfAttended = whatIfAttended,
                whatIfTotal = whatIfTotal,
                colors = colors,
                multiplier = multiplier
            )
            "Log" -> LogSection(course, colors)
            "Notes" -> NotesSection(courseCode, colors)
        }
    }
}

// ═══════════════════════════════════════════
// Predictor Section
// ═══════════════════════════════════════════

@Composable
private fun PredictorSection(
    mode: String,
    onModeChange: (String) -> Unit,
    currentPct: Double,
    predictedPct: Double,
    futureClassDates: List<Triple<Int, Int, Int>>,
    skipDates: Set<Int>,
    onSkipToggle: (Int) -> Unit,
    whatIfAttend: Float,
    onWhatIfAttendChange: (Float) -> Unit,
    whatIfMiss: Float,
    onWhatIfMissChange: (Float) -> Unit,
    whatIfPct: Double,
    whatIfAttended: Int,
    whatIfTotal: Int,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    multiplier: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Chips (LID, CAT 1, CAT 2, END)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LID" to "LID (Lab/Theory Cutoff)", "CAT1" to "CAT 1", "CAT2" to "CAT 2", "END" to "End of Sem").forEach { (key, label) ->
                val isSel = mode == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(if (isSel) colors.accent else colors.surface)
                        .border(1.dp, if (isSel) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                        .clickable { onModeChange(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        key,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isSel) colors.background else colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // Predicted Attendance Result Card
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Projected Attendance", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Based on remaining working days up to cutoff", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        Text(pctFormatted(currentPct), style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    Icon(Icons.Rounded.ArrowForward, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Predicted", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        Text(
                            pctFormatted(predictedPct),
                            style = AmazeTheme.typography.heading.copy(
                                fontWeight = FontWeight.Black,
                                color = projectedColor(predictedPct, colors)
                            )
                        )
                    }
                }
            }
        }

        // What-If Simulator Card
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("What-If Simulator", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("Attend Future Classes: ${whatIfAttend.toInt()}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                Slider(
                    value = whatIfAttend,
                    onValueChange = onWhatIfAttendChange,
                    valueRange = 0f..20f,
                    steps = 19,
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Miss Future Classes: ${whatIfMiss.toInt()}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                Slider(
                    value = whatIfMiss,
                    onValueChange = onWhatIfMissChange,
                    valueRange = 0f..20f,
                    steps = 19,
                    colors = SliderDefaults.colors(thumbColor = colors.danger, activeTrackColor = colors.danger)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(projectedColor(whatIfPct, colors).copy(alpha = 0.12f))
                        .border(1.dp, projectedColor(whatIfPct, colors).copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Simulated Outcome", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            Text("$whatIfAttended / $whatIfTotal classes", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                        Text(
                            pctFormatted(whatIfPct),
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Black,
                                color = projectedColor(whatIfPct, colors)
                            )
                        )
                    }
                }
            }
        }

        // Calendar Dates Grid Card
        if (futureClassDates.isNotEmpty()) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Future Classes (${futureClassDates.size} sessions)", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Tap a date to mark as skipped/bunked", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    Spacer(modifier = Modifier.height(12.dp))

                    val monthNames = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        futureClassDates.chunked(4).forEach { chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                chunk.forEach { (y, m, d) ->
                                    val key = y * 10000 + m * 100 + d
                                    val isSkipped = key in skipDates
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                            .background(if (isSkipped) colors.danger.copy(alpha = 0.15f) else colors.surface)
                                            .border(1.dp, if (isSkipped) colors.danger else colors.border, RoundedCornerShape(AmazeTheme.radius.small))
                                            .clickable { onSkipToggle(key) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "${monthNames.getOrElse(m) { "" }} $d",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSkipped) colors.danger else colors.textPrimary,
                                                    fontSize = 11.sp
                                                )
                                            )
                                            Text(
                                                if (isSkipped) "Bunked" else "Attend",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = if (isSkipped) colors.danger else colors.success,
                                                    fontSize = 9.sp
                                                )
                                            )
                                        }
                                    }
                                }
                                if (chunk.size < 4) {
                                    repeat(4 - chunk.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Log Section
// ═══════════════════════════════════════════

@Composable
private fun LogSection(
    course: AttendanceItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val historyList = remember(course.viewLinkRaw) {
        try {
            val raw = parseViewLink(course.viewLinkRaw)
            val list = mutableListOf<Pair<String, String>>()
            if (raw is JsonArray) {
                raw.forEach { elem ->
                    val obj = elem.jsonObject
                    val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    list.add(date to status)
                }
            } else if (raw is JsonObject) {
                raw.forEach { (date, statusElem) ->
                    val stat = statusElem.jsonPrimitive.content
                    list.add(date to stat)
                }
            }
            if (list.isEmpty() && course.totalClasses > 0) {
                val attended = course.attendedClasses
                val total = course.totalClasses
                val presentCount = attended.coerceIn(0, total)
                for (i in 0 until total) {
                    val day = 1 + i
                    val date = "Class $day"
                    val status = if (i < presentCount) "Present" else "Absent"
                    list.add(date to status)
                }
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    if (historyList.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.History, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No attendance logs available", color = colors.textSecondary)
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        historyList.forEach { (date, status) ->
            val isPresent = status.equals("Present", ignoreCase = true) || status.equals("P", ignoreCase = true)
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(date, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(if (isPresent) colors.success.copy(alpha = 0.15f) else colors.danger.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (isPresent) "Present" else "Absent",
                            color = if (isPresent) colors.success else colors.danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Notes Section
// ═══════════════════════════════════════════

@Composable
private fun NotesSection(
    courseCode: String,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var notesText by remember { mutableStateOf(SettingsManager.getCourseNote(courseCode)) }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Personal Course Notes", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(4.dp))
            Text("Saved locally on your device for $courseCode", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notesText,
                onValueChange = {
                    notesText = it
                    SettingsManager.saveCourseNote(courseCode, it)
                },
                placeholder = { Text("Write your notes for this course...", color = colors.textMuted) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(AmazeTheme.radius.medium),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent)
            )
        }
    }
}

@Composable
fun PctStat(label: String, value: String, color: Color) {
    val colors = AmazeTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = color))
        Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
    }
}

fun projectedColor(pct: Double, colors: com.amazecc.app.shared.theme.AmazeColors): Color = when {
    pct >= 85.0 -> colors.success
    pct >= 75.0 -> colors.warning
    else -> colors.danger
}
