package com.amazecc.app.shared.ui.screens.academics

import kotlinx.datetime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
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
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.parseViewLink
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

    // Independent What-If state
    var whatIfAttend by remember { mutableStateOf(0f) }
    var whatIfMiss by remember { mutableStateOf(0f) }
    
    val whatIfTotal = course.totalClasses + whatIfAttend.toInt() + whatIfMiss.toInt()
    val whatIfAttended = course.attendedClasses + whatIfAttend.toInt()
    val whatIfPct = if (whatIfTotal > 0) whatIfAttended.toDouble() / whatIfTotal * 100 else 0.0

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

        // Bunk-O-Meter Hero Section
        AmazeCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
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
                    val progressColor = projectedColor(currentPct)
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
                                color = if (currentPct >= 75) Color(0xFF10B981) else colors.danger
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PctStat("Attended", "${course.attendedClasses}", Color(0xFF3B82F6))
                    PctStat("Total", "${course.totalClasses}", colors.textPrimary)
                    PctStat("Target (75%)", "${kotlin.math.ceil(course.totalClasses * 0.75).toInt()}", Color(0xFFF59E0B))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
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
                        text = tab,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = if (isSelected) colors.background else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                androidx.compose.animation.fadeIn(animationSpec = tween(300)) togetherWith androidx.compose.animation.fadeOut(animationSpec = tween(300))
            }
        ) { tab ->
            when (tab) {
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
                        whatIfAttend = whatIfAttend,
                        onWhatIfAttendChange = { whatIfAttend = it },
                        whatIfMiss = whatIfMiss,
                        onWhatIfMissChange = { whatIfMiss = it },
                        whatIfPct = whatIfPct,
                        whatIfTotal = whatIfTotal,
                        whatIfAttended = whatIfAttended,
                        currentAttended = course.attendedClasses,
                        currentTotal = course.totalClasses,
                        colors = colors
                    )
                }
                "Log" -> LogTab(course = course, colors = colors)
                "Notes" -> NotesTab(courseCode = courseCode ?: "", colors = colors)
            }
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
    whatIfAttend: Float,
    onWhatIfAttendChange: (Float) -> Unit,
    whatIfMiss: Float,
    onWhatIfMissChange: (Float) -> Unit,
    whatIfPct: Double,
    whatIfTotal: Int,
    whatIfAttended: Int,
    currentAttended: Int,
    currentTotal: Int,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val modes = listOf("CAT1", "CAT2", "LID")
    val scrollState = androidx.compose.foundation.rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Calculate, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Interactive What-If", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp))
                }
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("If I attend", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text("${whatIfAttend.toInt()} classes", style = AmazeTheme.typography.body.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                    }
                    Text(
                        pctFormatted(whatIfPct),
                        style = AmazeTheme.typography.heading.copy(
                            color = projectedColor(whatIfPct),
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (whatIfAttend >= 1f) onWhatIfAttendChange(whatIfAttend - 1f) }) {
                        Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Decrease", tint = Color(0xFF10B981))
                    }
                    Slider(
                        value = whatIfAttend,
                        onValueChange = onWhatIfAttendChange,
                        valueRange = 0f..50f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981), 
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF10B981).copy(alpha = 0.2f)
                        )
                    )
                    IconButton(onClick = { if (whatIfAttend < 50f) onWhatIfAttendChange(whatIfAttend + 1f) }) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Increase", tint = Color(0xFF10B981))
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("And miss", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text("${whatIfMiss.toInt()} classes", style = AmazeTheme.typography.body.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                    }
                    Text("$whatIfAttended / $whatIfTotal", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (whatIfMiss >= 1f) onWhatIfMissChange(whatIfMiss - 1f) }) {
                        Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Decrease", tint = colors.danger)
                    }
                    Slider(
                        value = whatIfMiss,
                        onValueChange = onWhatIfMissChange,
                        valueRange = 0f..50f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.danger, 
                            activeTrackColor = colors.danger,
                            inactiveTrackColor = colors.danger.copy(alpha = 0.2f)
                        )
                    )
                    IconButton(onClick = { if (whatIfMiss < 50f) onWhatIfMissChange(whatIfMiss + 1f) }) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Increase", tint = colors.danger)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Date-based Predictor", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            modes.forEach { m ->
                val sel = mode == m
                val bg by androidx.compose.animation.animateColorAsState(if (sel) colors.accent else colors.surface)
                val tc by androidx.compose.animation.animateColorAsState(if (sel) Color.White else colors.textPrimary)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bg, RoundedCornerShape(12.dp))
                        .border(1.dp, if (sel) colors.accent else colors.border, RoundedCornerShape(12.dp))
                        .clickable { onModeChange(m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(m, color = tc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Timeline (${futureDates.size} upcoming)",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
            )
            Text("Tap to toggle skip", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (futureDates.isEmpty()) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No future classes found up to this cutoff.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                futureDates.sortedBy { it.first * 10000 + it.second * 100 + it.third }.forEach { (y, m, d) ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogTab(
    course: AttendanceItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    // Try to parse viewLinkRaw for detailed attendance
    val detailedDays = remember(course.viewLinkRaw) {
        try {
            val raw = parseViewLink(course.viewLinkRaw)
            val arr = raw?.jsonArray
            arr?.mapNotNull { elem ->
                val obj = elem.jsonObject
                val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Pair(date, status)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    val chronoSorted = remember(detailedDays) { detailedDays.sortedBy { it.first } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (chronoSorted.isNotEmpty()) {
            AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Attendance Heatmap", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp))
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chronoSorted.forEach { (_, status) ->
                            val isPresent = status.lowercase() in listOf("present", "p")
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isPresent) Color(0xFF10B981) else colors.danger)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(Modifier.width(6.dp))
                            Text("Present", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.danger))
                            Spacer(Modifier.width(6.dp))
                            Text("Absent", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Timeline (${detailedDays.size} entries)",
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(detailedDays.sortedByDescending { it.first }) { (date, status) ->
                    val isPresent = status.lowercase() in listOf("present", "p")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isPresent) Color(0xFF10B981).copy(alpha = 0.12f) else colors.danger.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPresent) Icons.Rounded.Check else Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = if (isPresent) Color(0xFF10B981) else colors.danger,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(date, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp))
                                Text("Class Attended", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isPresent) Color(0xFF10B981)
                                    else colors.danger,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isPresent) "PRESENT" else "ABSENT",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
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
