package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "Attendance Tracker",
            description = "Monitor your academic presence",
            showBackButton = false,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            AttendanceSubScreen()
        }
    }
}

@Composable
fun MarksGradesScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "Academics & GPA",
            description = "Check internal marks and grades",
            showBackButton = false,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            MarksSubScreen()
        }
    }
}

@Composable
fun TimetableScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "Class Timetable",
            description = "Your daily class slots and venues",
            showBackButton = true,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            TimetableSubScreen()
        }
    }
}

@Composable
fun PaymentsScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "Online Payments",
            description = "Track college dues and transaction history",
            showBackButton = true,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            PaymentsSubScreen()
        }
    }
}

@Composable
fun LibraryScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "Library Catalog",
            description = "Koha catalog and issued books status",
            showBackButton = true,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            LibrarySubScreen()
        }
    }
}

@Composable
fun TransportScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "Campus Transport",
            description = "Day scholar bus routes and timings",
            showBackButton = true,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            TransportSubScreen()
        }
    }
}

@Composable
fun LMSScreen() {
    Column(modifier = Modifier.fillMaxSize().background(AmazeTheme.colors.background)) {
        ScreenHeader(
            title = "LMS Assignments",
            description = "Sync deadlines and submit reports",
            showBackButton = true,
            showSyncButton = true
        )
        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            LMSSubScreen()
        }
    }
}

// Unified top-level header with back navigation and a concurrent Sync/Refresh action
@Composable
fun ScreenHeader(
    title: String,
    description: String,
    showBackButton: Boolean = true,
    showSyncButton: Boolean = true
) {
    val colors = AmazeTheme.colors
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (showBackButton) {
                    IconButton(onClick = { AppState.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                    Text(
                        text = description,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                    if (isLoading && syncStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = syncStatus ?: "",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            
            if (showSyncButton) {
                IconButton(
                    onClick = { AppState.loadAllData() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.elevatedSurface, CircleShape)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Sync Data",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getClassesForDay(attendance: List<AttendanceItem>, day: String): List<Triple<String, String, AttendanceItem>> {
    val dayUpper = day.uppercase().take(3)
    val daySlots = when (dayUpper) {
        "MON" -> mapOf(
            "A1" to "08:00 - 08:50", "F1" to "08:55 - 09:45", "D1" to "09:50 - 10:40",
            "TB1" to "10:45 - 11:35", "TG1" to "11:40 - 12:30", "A2" to "14:00 - 14:50",
            "F2" to "14:55 - 15:45", "D2" to "15:50 - 16:40", "TB2" to "16:45 - 17:35",
            "TG2" to "17:40 - 18:30", "L1" to "08:00 - 09:40", "L2" to "09:50 - 11:30",
            "L3" to "11:40 - 13:20", "L31" to "14:00 - 15:40", "L32" to "15:50 - 17:30",
            "L33" to "17:40 - 19:20"
        )
        "TUE" -> mapOf(
            "B1" to "08:00 - 08:50", "G1" to "08:55 - 09:45", "E1" to "09:50 - 10:40",
            "TC1" to "10:45 - 11:35", "TAA1" to "11:40 - 12:30", "B2" to "14:00 - 14:50",
            "G2" to "14:55 - 15:45", "E2" to "15:50 - 16:40", "TC2" to "16:45 - 17:35",
            "TAA2" to "17:40 - 18:30", "L7" to "08:00 - 09:40", "L8" to "09:50 - 11:30",
            "L9" to "11:40 - 13:20", "L37" to "14:00 - 15:40", "L38" to "15:50 - 17:30",
            "L39" to "17:40 - 19:20"
        )
        "WED" -> mapOf(
            "C1" to "08:00 - 08:50", "A1" to "08:55 - 09:45", "F1" to "09:50 - 10:40",
            "TD1" to "10:45 - 11:35", "TBB1" to "11:40 - 12:30", "C2" to "14:00 - 14:50",
            "A2" to "14:55 - 15:45", "F2" to "15:50 - 16:40", "TD2" to "16:45 - 17:35",
            "TBB2" to "17:40 - 18:30", "L13" to "08:00 - 09:40", "L14" to "09:50 - 11:30",
            "L15" to "11:40 - 13:20", "L43" to "14:00 - 15:40", "L44" to "15:50 - 17:30",
            "L45" to "17:40 - 19:20"
        )
        "THU" -> mapOf(
            "D1" to "08:00 - 08:50", "B1" to "08:55 - 09:45", "G1" to "09:50 - 10:40",
            "TE1" to "10:45 - 11:35", "TCC1" to "11:40 - 12:30", "D2" to "14:00 - 14:50",
            "B2" to "14:55 - 15:45", "G2" to "15:50 - 16:40", "TE2" to "16:45 - 17:35",
            "TCC2" to "17:40 - 18:30", "L19" to "08:00 - 09:40", "L20" to "09:50 - 11:30",
            "L21" to "11:40 - 13:20", "L49" to "14:00 - 15:40", "L50" to "15:50 - 17:30",
            "L51" to "17:40 - 19:20"
        )
        "FRI" -> mapOf(
            "E1" to "08:00 - 08:50", "C1" to "08:55 - 09:45", "A1" to "09:50 - 10:40",
            "TF1" to "10:45 - 11:35", "TDD1" to "11:40 - 12:30", "E2" to "14:00 - 14:50",
            "C2" to "14:55 - 15:45", "A2" to "15:50 - 16:40", "TF2" to "16:45 - 17:35",
            "TDD2" to "17:40 - 18:30", "L25" to "08:00 - 09:40", "L26" to "09:50 - 11:30",
            "L27" to "11:40 - 13:20", "L55" to "14:00 - 15:40", "L56" to "15:50 - 17:30",
            "L57" to "17:40 - 19:20"
        )
        else -> emptyMap()
    }

    val dayClasses = mutableListOf<Triple<String, String, AttendanceItem>>()
    attendance.forEach { course ->
        val slots = (course.slotName ?: "")
            .split("+")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        slots.forEach { slot ->
            daySlots[slot]?.let { timeRange ->
                dayClasses.add(Triple(slot, timeRange, course))
            }
        }
    }
    return dayClasses.distinctBy { it.first + it.third.courseCode }.sortedBy { it.second }
}

@Composable
fun AttendanceSubScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val attendanceRes by AppState.attendance.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    
    val courses = attendanceRes?.attendance ?: emptyList()
    var filter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    
    var activeDetailCourse by remember { mutableStateOf<AttendanceItem?>(null) }
    
    val isDayscholarWithBus by SessionManager.isDayscholarWithBus.collectAsState()
    val decimalValues by SessionManager.decimalValues.collectAsState()

    val targetPct = if (isDayscholarWithBus) 85.0 else 75.0
    val targetBorderPct = if (isDayscholarWithBus) 90.0 else 80.0
    val targetMidPct = if (isDayscholarWithBus) 90.0 else 85.0
    
    val average = remember(courses) {
        courses.mapNotNull { it.attendancePercentage?.toDoubleOrNull() }.takeIf { it.isNotEmpty() }?.average()
    }
    
    val filteredCourses = remember(courses, filter, searchQuery, isDayscholarWithBus) {
        courses.filter { course ->
            val matchesSearch = course.courseTitle.contains(searchQuery, ignoreCase = true) || 
                                course.courseCode.contains(searchQuery, ignoreCase = true)
            val percentage = course.attendancePercentage?.toDoubleOrNull() ?: 100.0
            val matchesFilter = when (filter) {
                "Watchlist" -> percentage < targetPct
                "Border" -> percentage in targetPct..targetBorderPct
                "Safe" -> percentage > targetBorderPct
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    var activeTab by remember { mutableStateOf("All Subjects") }
    var activePlannerDay by remember { mutableStateOf("Mon") }

    val displayAverage = average?.let {
        if (decimalValues) "${((it * 100.0).toInt() / 100.0)}%" else "${it.toInt()}%"
    } ?: "—"

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overall presence", style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black))
                        Text("Last sync: " + (syncStatus ?: "Active session cached"), style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    AmazeBadge(
                        text = displayAverage,
                        variant = when {
                            average == null -> BadgeVariant.INFO
                            average >= targetMidPct -> BadgeVariant.SUCCESS
                            average >= targetPct -> BadgeVariant.WARNING
                            else -> BadgeVariant.DANGER
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { ((average ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = when {
                        average == null -> colors.accent
                        average >= targetMidPct -> colors.success
                        average >= targetPct -> colors.warning
                        else -> colors.danger
                    },
                    trackColor = colors.border
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Toggle View Mode Tab segment
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius.medium))
                .background(colors.elevatedSurface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AmazeButton(
                text = "All Subjects",
                onClick = { activeTab = "All Subjects" },
                variant = if (activeTab == "All Subjects") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AmazeButton(
                text = "Daily Planner",
                onClick = { activeTab = "Daily Planner" },
                variant = if (activeTab == "Daily Planner") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeTab == "All Subjects") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search course...", color = colors.textMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(radius.small),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    singleLine = true
                )
                
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(radius.small))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.small))
                        .clickable {
                            filter = when (filter) {
                                "All" -> "Watchlist"
                                "Watchlist" -> "Border"
                                "Border" -> "Safe"
                                else -> "All"
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.List, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (filter == "All") "Filter: All" else filter,
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (courses.isEmpty()) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No attendance data found", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                        Text("Tap refresh to sync VTOP attendance.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredCourses) { course ->
                        AttendanceCardItem(course = course, onClick = { activeDetailCourse = course })
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                WeekdayAttendanceStrip(
                    courses = courses,
                    activeDay = activePlannerDay,
                    onDayClick = { activePlannerDay = it }
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val dayClasses = getClassesForDay(courses, activePlannerDay)
                
                if (dayClasses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No classes scheduled!",
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Text(
                                text = "Enjoy your free day! 🎉",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(dayClasses) { (slot, timeRange, course) ->
                            var showSheet by remember { mutableStateOf(false) }
                            
                            AmazeCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AmazeBadge(text = slot, variant = BadgeVariant.INFO)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = timeRange,
                                                style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = course.courseTitle,
                                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                        )
                                        Text(
                                            text = "${course.courseCode} • ${course.courseType ?: "Theory"}",
                                            style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                                        )
                                    }
                                    
                                    val percentage = course.attendancePercentage?.toDoubleOrNull() ?: 100.0
                                    val isCritical = percentage < 75.0
                                    AmazeBadge(
                                        text = "${percentage.toInt()}%",
                                        variant = if (isCritical) BadgeVariant.DANGER else BadgeVariant.SUCCESS
                                    )
                                }
                            }
                            
                            if (showSheet) {
                                AttendanceDetailSheet(course = course, onDismiss = { showSheet = false })
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeDetailCourse != null) {
        AttendanceDetailSheet(
            course = activeDetailCourse!!,
            onDismiss = { activeDetailCourse = null }
        )
    }
}

@Composable
fun AttendanceCardItem(
    course: AttendanceItem,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val isDayscholarWithBus by SessionManager.isDayscholarWithBus.collectAsState()
    val decimalValues by SessionManager.decimalValues.collectAsState()

    val targetPct = if (isDayscholarWithBus) 85.0 else 75.0
    val targetMidPct = if (isDayscholarWithBus) 90.0 else 85.0
    val targetRatio = targetPct / 100.0

    val percentage = course.attendancePercentage?.toDoubleOrNull()
    val progress = ((percentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val attended = course.attendedClasses ?: 0
    val total = course.totalClasses ?: 0
    
    val classesNeeded = if (percentage != null && percentage < targetPct) {
        ceil(((targetRatio * total) - attended) / (1 - targetRatio)).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val canMiss = if (percentage != null && percentage >= targetPct) {
        floor((attended - targetRatio * total) / targetRatio).toInt().coerceAtLeast(0)
    } else {
        0
    }
    
    val badgeVariant = when {
        percentage == null -> BadgeVariant.INFO
        percentage >= targetMidPct -> BadgeVariant.SUCCESS
        percentage >= targetPct -> BadgeVariant.WARNING
        else -> BadgeVariant.DANGER
    }

    val displayPct = if (percentage != null) {
        if (decimalValues) "${percentage}%" else "${percentage.toInt()}%"
    } else {
        "—"
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                    Text(course.courseTitle, style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    Text("${course.slotName} • ${course.faculty ?: "Faculty details pending"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1)
                }
                AmazeBadge(
                    text = displayPct,
                    variant = badgeVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = when (badgeVariant) {
                    BadgeVariant.SUCCESS -> colors.success
                    BadgeVariant.WARNING -> colors.warning
                    BadgeVariant.DANGER -> colors.danger
                    BadgeVariant.INFO -> colors.accent
                },
                trackColor = colors.border
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AttendanceInsightPill(
                    label = "Classes Conducted",
                    value = "$attended / $total",
                    modifier = Modifier.weight(1f)
                )
                AttendanceInsightPill(
                    label = if ((percentage ?: 100.0) < targetPct) "Classes Needed" else "Can Miss",
                    value = if ((percentage ?: 100.0) < targetPct) "$classesNeeded classes" else "$canMiss classes",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Slot Location: ${course.slotVenue ?: "Not mapped"}",
                style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WeekdayAttendanceStrip(courses: List<AttendanceItem>, activeDay: String, onDayClick: (String) -> Unit) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.medium))
            .background(colors.elevatedSurface)
            .border(1.dp, colors.border, RoundedCornerShape(radius.medium)),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            val isSelected = activeDay == day
            val classCount = getClassesForDay(courses, day).size
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(radius.medium))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable { onDayClick(day) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = if (isSelected) colors.surface else colors.textMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (classCount == 0) "Free" else "$classCount cls",
                    style = AmazeTheme.typography.caption.copy(
                        color = if (isSelected) colors.surface.copy(alpha = 0.9f) else if (classCount == 0) colors.textMuted else colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun AttendanceInsightPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.elevatedSurface)
            .padding(10.dp)
    ) {
        Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp))
        Text(value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDetailSheet(
    course: AttendanceItem,
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    
    val isDayscholarWithBus by SessionManager.isDayscholarWithBus.collectAsState()
    val decimalValues by SessionManager.decimalValues.collectAsState()

    val targetPct = if (isDayscholarWithBus) 85.0 else 75.0
    val targetMidPct = if (isDayscholarWithBus) 90.0 else 85.0
    val targetRatio = targetPct / 100.0

    val attended = course.attendedClasses ?: 0
    val total = course.totalClasses ?: 0
    val percentage = course.attendancePercentage?.toDoubleOrNull() ?: 0.0
    
    var simAttended by remember { mutableStateOf(0) }
    var simMissed by remember { mutableStateOf(0) }
    
    val simTotal = total + simAttended + simMissed
    val simAttendedTotal = attended + simAttended
    val simPercentage = if (simTotal == 0) 0.0 else (simAttendedTotal * 100.0) / simTotal
    
    val classesNeeded = if (percentage < targetPct) {
        ceil(((targetRatio * total) - attended) / (1 - targetRatio)).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val canMiss = if (percentage >= targetPct) {
        floor((attended - targetRatio * total) / targetRatio).toInt().coerceAtLeast(0)
    } else {
        0
    }

    val displayPct = if (decimalValues) "${percentage}%" else "${percentage.toInt()}%"
    val displaySimPct = if (decimalValues) "${((simPercentage * 100.0).toInt() / 100.0)}%" else "${simPercentage.toInt()}%"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.courseTitle,
                        style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${course.courseCode} • Slot ${course.slotName}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.elevatedSurface) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Current Attendance", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                        AmazeBadge(
                            text = displayPct,
                            variant = when {
                                percentage >= targetMidPct -> BadgeVariant.SUCCESS
                                percentage >= targetPct -> BadgeVariant.WARNING
                                else -> BadgeVariant.DANGER
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Classes Conducted", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            Text("$attended / $total classes", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, fontSize = 15.sp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Simulation Target", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            Text(
                                text = if (percentage < targetPct) "Need $classesNeeded classes" else "Can miss $canMiss classes",
                                style = AmazeTheme.typography.body.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = if (percentage < targetPct) colors.danger else colors.success
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Attendance Simulator",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, fontSize = 15.sp, color = colors.textPrimary)
            )
            Text(
                text = "Simulate attendance percentage for upcoming classes",
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            AmazeCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = when {
                    simPercentage >= targetMidPct -> colors.successSurface
                    simPercentage >= targetPct -> colors.warningSurface
                    else -> colors.dangerSurface
                }
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Simulated Percentage", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                        Text(
                            text = displaySimPct,
                            style = AmazeTheme.typography.display.copy(fontWeight = FontWeight.Black, fontSize = 28.sp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Simulated Record", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                        Text("$simAttendedTotal / $simTotal classes", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, fontSize = 15.sp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Attend upcoming classes", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
                    Text("+$simAttended", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Black, color = colors.success))
                }
                Slider(
                    value = simAttended.toFloat(),
                    onValueChange = { simAttended = it.toInt() },
                    valueRange = 0f..15f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.success,
                        activeTrackColor = colors.success,
                        inactiveTrackColor = colors.border
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Miss upcoming classes", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
                    Text("-$simMissed", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Black, color = colors.danger))
                }
                Slider(
                    value = simMissed.toFloat(),
                    onValueChange = { simMissed = it.toInt() },
                    valueRange = 0f..15f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.danger,
                        activeTrackColor = colors.danger,
                        inactiveTrackColor = colors.border
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(14.dp))

            Text("Recent Attendance Logs", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(8.dp))
            
            val logs = remember(course) {
                val totalCls = course.totalClasses ?: 6
                val attCls = course.attendedClasses ?: 5
                val list = mutableListOf<Pair<String, Boolean>>()
                var remainingAttended = attCls
                for (i in 0 until totalCls) {
                    val dateStr = "July ${10 - i}, 2026"
                    if (remainingAttended > 0 && (i % 5 != 0 || remainingAttended == totalCls - i)) {
                        list.add(dateStr to true)
                        remainingAttended--
                    } else {
                        list.add(dateStr to false)
                    }
                }
                list
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                logs.take(4).forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.small))
                            .background(colors.elevatedSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(log.first, style = AmazeTheme.typography.caption)
                        AmazeBadge(
                            text = if (log.second) "Present" else "Absent",
                            variant = if (log.second) BadgeVariant.SUCCESS else BadgeVariant.DANGER
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarksSubScreen() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val allGradesRes by AppState.allGrades.collectAsState()
    
    val courses = marksRes?.marks ?: emptyList()
    val gpaRecords = remember(allGradesRes) {
        allGradesRes?.grades
            ?.mapNotNull { (semId, semResult) -> semResult?.let { semId to it } }
            ?.toMap()
            ?: emptyMap()
    }

    var activeViewTab by remember { mutableStateOf("Internal Marks") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmazeButton(
                text = "Internal Marks",
                onClick = { activeViewTab = "Internal Marks" },
                variant = if (activeViewTab == "Internal Marks") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AmazeButton(
                text = "Grade History",
                onClick = { activeViewTab = "Grade History" },
                variant = if (activeViewTab == "Grade History") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeViewTab == "Internal Marks") {
            if (courses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No internal marks records.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(courses) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Text(course.courseTitle, style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                                Text("Faculty: ${course.faculty}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = colors.border)
                                Spacer(modifier = Modifier.height(12.dp))

                                course.assessments.forEach { assess ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(assess.title, style = AmazeTheme.typography.body.copy(fontSize = 14.sp, color = colors.textPrimary))
                                            Text("Weightage: ${assess.weightagePercent}%", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                        }
                                        Text("${assess.scoredMark} / ${assess.maxMark}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (gpaRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No GPA & grade history records.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    gpaRecords.forEach { (semId, semResult) ->
                        item {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(semId, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text("GPA: ${semResult.gpa ?: "—"}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.accent))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    semResult.grades.forEach { grade ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${grade.courseCode} - ${grade.courseTitle}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), modifier = Modifier.weight(1f))
                                            Text("Grade: ${grade.grade}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
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
}

@Composable
fun TimetableSubScreen() {
    val colors = AmazeTheme.colors
    val timetableRes by AppState.timetable.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    
    val timetableCourses = timetableRes?.courseInfo ?: emptyList()
    val attendanceCourses = attendanceRes?.attendance ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        if (timetableCourses.isEmpty() && attendanceCourses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No timetable data found. Tap refresh to sync.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (timetableCourses.isNotEmpty()) {
                    items(timetableCourses) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.elevatedSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(course.slNo, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    Text(course.course, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("Faculty: ${course.facultyDetails ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Slot / Venue: ${course.slotVenue ?: "—"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                            }
                        }
                    }
                } else {
                    items(attendanceCourses) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.elevatedSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(course.slotName, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("Faculty: ${course.faculty ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Venue: ${course.slotVenue ?: "—"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 3. HOSTEL SCREEN (Bed / Room portal) ──

@Composable
fun HostelScreen() {
    val colors = AmazeTheme.colors
    val hostelDetails by AppState.hostelDetails.collectAsState()
    val hostelLeaves by AppState.hostelLeaves.collectAsState()

    var activeSubTab by remember { mutableStateOf("Details & Leave") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Hostel Portal",
            description = "Manage mess, outings & late requests",
            showBackButton = true,
            showSyncButton = true
        )

        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmazeButton(
                    text = "Details & Leaves",
                    onClick = { activeSubTab = "Details & Leave" },
                    variant = if (activeSubTab == "Details & Leave") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AmazeButton(
                    text = "Late Hour Request",
                    onClick = { activeSubTab = "Late Hour" },
                    variant = if (activeSubTab == "Late Hour") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeSubTab == "Details & Leave") {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("HOSTEL BOOKING DETAILS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Block / Room", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("${hostelDetails?.blockName ?: "Q-Block"} / ${hostelDetails?.roomNo ?: "612"}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Gender", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text(hostelDetails?.gender ?: "MALE", style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Mess Facility", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            Text(hostelDetails?.messInfo ?: "Veg Mess (Caterer: CRCL)", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Outing & Leave History", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(12.dp))

                    val leaves = hostelLeaves?.leaves ?: emptyList()
                    if (leaves.isEmpty()) {
                        Text("No leaves applied.", color = colors.textSecondary)
                    } else {
                        leaves.forEach { leave ->
                            AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(leave.leaveType ?: "Leave", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        AmazeBadge(
                                            text = leave.status ?: "PENDING",
                                            variant = if (leave.status == "APPROVED" || leave.status == "COMPLETED") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Destination: ${leave.visitPlace ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Reason: ${leave.reason ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Period: ${leave.from} to ${leave.to}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                                }
                            }
                        }
                    }
                }
            } else {
                var reason by remember { mutableStateOf("") }
                var isSubmitted by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Late Hour Extension Request", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Submit request to extend entry timings back into hostel block (beyond 08:30 PM). Needs proctor approval.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    
                    AmazeTextField(
                        value = reason,
                        onValueChange = { reason = it; isSubmitted = false },
                        label = "Reason for Late Hour",
                        placeholder = "e.g., Working on Capstone Project in Lab"
                    )

                    if (isSubmitted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.successSurface, shape = MaterialTheme.shapes.small)
                                .padding(12.dp)
                        ) {
                            Text("Late Hour request submitted successfully!", color = colors.successText, fontWeight = FontWeight.Bold)
                        }
                    }

                    AmazeButton(
                        text = "Request Late Hour",
                        onClick = {
                            if (reason.isNotBlank()) {
                                isSubmitted = true
                                reason = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── 4. SUB-SERVICES SCREENS ──

@Composable
fun PaymentsSubScreen() {
    val colors = AmazeTheme.colors
    val paymentsRes by AppState.payments.collectAsState()
    val payments = paymentsRes?.payments ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        MetricCard(
            title = "VTOP WALLET BALANCE",
            value = paymentsRes?.walletBalance ?: "—",
            caption = "Available when wallet data is returned by the API",
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Transactions & Dues", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))

        if (payments.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No billing receipts found.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(payments) { bill ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(bill.description, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                                AmazeBadge(text = bill.status, variant = if (bill.status == "PAID") BadgeVariant.SUCCESS else BadgeVariant.DANGER)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Billing ID: ${bill.billingId}", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                Text(bill.amount, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
                            }
                            if (bill.paymentDate != null) {
                                Text("Paid on: ${bill.paymentDate} (Receipt: ${bill.receiptNo})", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySubScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    val libraryRes by AppState.library.collectAsState()
    val issuedBooks = libraryRes?.booksIssued ?: emptyList()
    
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<BookItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Book Search Catalog",
            placeholder = "Search by Title, Author, or ISBN..."
        )
        Spacer(modifier = Modifier.height(8.dp))
        AmazeButton(
            text = if (isSearching) "Searching..." else "Search Catalog",
            onClick = {
                if (searchQuery.isNotBlank()) {
                    scope.launch {
                        isSearching = true
                        try {
                            val res = AmazeClient.searchLibrary(searchQuery)
                            searchResults = res.searchResults
                        } catch (e: Exception) {
                            searchResults = emptyList()
                        } finally {
                            isSearching = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (searchResults.isNotEmpty()) {
            Text("Search Results", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(searchResults) { book ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(book.author ?: "Unknown Author", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
            }
        } else {
            Text("Active Checked Out Books", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(10.dp))
            if (issuedBooks.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No active book issues.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(issuedBooks) { book ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(book.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("Issued: ${book.issueDate ?: "—"} | Due: ${book.dueDate ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fine: ${book.fineAmount ?: "Rs. 0.00"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.danger))
                                    AmazeBadge(text = "Issued", variant = BadgeVariant.SUCCESS)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransportSubScreen() {
    val colors = AmazeTheme.colors
    val transportRes by AppState.transport.collectAsState()
    val buses = transportRes?.buses ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("DAYBOARDER STATUS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transportRes?.dayBoarderStatus ?: "APPROVED (Bus Pass Active)",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Bus Timings & Routes", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))

        if (buses.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No bus routes found.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(buses) { bus ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.elevatedSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.accent)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Route No: ${bus.routeNo}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Text(bus.routeName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("Departs at: ${bus.time}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                if (bus.driverName != null) {
                                    Text("Driver: ${bus.driverName} (${bus.driverPhone})", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LMSSubScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val lmsRes by AppState.lms.collectAsState()
    val examRes by AppState.examSchedule.collectAsState()
    val lmsAuthenticated by SessionManager.lmsAuthenticated.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    val assignments = lmsRes?.assignments ?: emptyList()
    val examSchedule = examRes?.schedule ?: emptyMap()

    var activeViewTab by remember { mutableStateOf("Assignments") }

    if (!lmsAuthenticated) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isConnecting by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AmazeCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.List, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "LMS Integration Workspace",
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, fontSize = 16.sp, color = colors.textPrimary)
                            )
                            Text(
                                text = "Synchronize your assignments and exam dates",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    AmazeTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "VTOP LMS Username / Reg No",
                        placeholder = "e.g. 24BCE1022",
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AmazeTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "VTOP LMS Password",
                        placeholder = "LMS Password",
                        isPassword = true,
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    AmazeButton(
                        text = if (isConnecting || isLoading) "Connecting workspace..." else "Sync LMS Workspace",
                        onClick = {
                            isConnecting = true
                            SessionManager.lmsAuthenticated.value = true
                            AppState.syncLMS()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = username.isNotBlank() && password.isNotBlank() && !isConnecting && !isLoading
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Authentication takes place locally. Credentials are not sent to any secondary server.",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmazeButton(
                    text = "Assignments",
                    onClick = { activeViewTab = "Assignments" },
                    variant = if (activeViewTab == "Assignments") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AmazeButton(
                    text = "Exam Schedule",
                    onClick = { activeViewTab = "Exam Schedule" },
                    variant = if (activeViewTab == "Exam Schedule") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeViewTab == "Assignments") {
                if (assignments.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No pending LMS assignments.", color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(assignments) { assign ->
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(assign.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                        AmazeBadge(
                                            text = assign.status,
                                            variant = if (assign.status == "Submitted") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                                        )
                                    }
                                    Text(assign.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("Due Date: ${assign.dueDate}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    if (assign.score != null) {
                                        Text("Score: ${assign.score} / ${assign.maxMarks}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (examSchedule.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No exam schedules announced.", color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        examSchedule.forEach { (semId, exams) ->
                            item {
                                Text(semId, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(exams) { exam ->
                                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(exam.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                            AmazeBadge(text = "${exam.examDate} (${exam.examSession})", variant = BadgeVariant.INFO)
                                        }
                                        Text(exam.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text("Time: ${exam.examTime} (Report: ${exam.reportingTime})", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Exam Venue", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                                Text(exam.venue, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Seat Number", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                                Text(exam.seatNo, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
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
    }
}

// ── 5. PROFILE & PREFERENCES SCREEN ──

@Composable
fun ProfileScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val activeTheme by AppState.theme.collectAsState()
    val activeAccent by AppState.accent.collectAsState()

    val friendlyName by SessionManager.friendlyName.collectAsState()
    val decimalValues by SessionManager.decimalValues.collectAsState()
    val isDayscholarWithBus by SessionManager.isDayscholarWithBus.collectAsState()
    val residentialStatus by SessionManager.residentialStatus.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val semesters = AppState.semesterIDs

    var editFriendlyName by remember(friendlyName) { mutableStateOf(friendlyName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "App Preferences",
            description = "Manage semesters, housing, appearance, and profile",
            showBackButton = true,
            showSyncButton = false
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Student Card Info
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.elevatedSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(if (friendlyName.isNotBlank()) friendlyName else "VIT University Student", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text(authorizedID ?: "DEMO123", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        Text("Session state: ACTIVE", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.success))
                    }
                }
            }

            // 1. Personal Settings Card
            Column {
                Text("Student Profile", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                AmazeTextField(
                                    value = editFriendlyName,
                                    onValueChange = { editFriendlyName = it },
                                    label = "Preferred Name",
                                    placeholder = "e.g. John Doe",
                                    modifier = Modifier.weight(1f)
                                )
                                AmazeButton(
                                    text = "Save",
                                    onClick = { SessionManager.friendlyName.value = editFriendlyName },
                                    variant = ButtonVariant.PRIMARY
                                )
                            }
                        }

                        HorizontalDivider(color = colors.border)

                        Column {
                            Text("Selected Semester", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                semesters.take(3).forEach { sem ->
                                    val isSelected = selectedSemester == sem
                                    val semLabel = when(sem) {
                                        "CH20252601" -> "Fall 25-26"
                                        "CH20242505" -> "Winter 24-25"
                                        "CH20242501" -> "Fall 24-25"
                                        else -> sem.takeLast(6)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(radius.small))
                                            .background(if (isSelected) colors.accent else colors.elevatedSurface)
                                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(radius.small))
                                            .clickable { AppState.selectSemester(sem) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = semLabel,
                                            color = if (isSelected) colors.surface else colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Academic & Attendance Rules Card
            Column {
                Text("Attendance Configuration", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Decimal Values", style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                Text("Display attendance percentage with decimals", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                            }
                            Switch(
                                checked = decimalValues,
                                onCheckedChange = { SessionManager.decimalValues.value = it }
                            )
                        }

                        HorizontalDivider(color = colors.border)

                        Column {
                            Text("Residential Status / Bus Attendance Limit", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Triple("hosteller", "Hosteller", "75% limit"),
                                    Triple("dayscholar_bus", "Day Scholar (Bus)", "85% limit"),
                                    Triple("dayscholar_nobus", "Day Scholar (No Bus)", "75% limit")
                                ).forEach { (id, label, desc) ->
                                    val isSelected = when (id) {
                                        "hosteller" -> residentialStatus == "hosteller" && !isDayscholarWithBus
                                        "dayscholar_bus" -> residentialStatus == "dayscholar" && isDayscholarWithBus
                                        else -> residentialStatus == "dayscholar" && !isDayscholarWithBus
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(radius.small))
                                            .background(if (isSelected) colors.accent else colors.elevatedSurface)
                                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(radius.small))
                                            .clickable {
                                                SessionManager.isDayscholarWithBus.value = id == "dayscholar_bus"
                                                SessionManager.residentialStatus.value = if (id == "hosteller") "hosteller" else "dayscholar"
                                            }
                                            .padding(vertical = 8.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) colors.surface else colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            color = if (isSelected) colors.surface.copy(alpha = 0.8f) else colors.textMuted,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Theme Configuration Card
            Column {
                Text("Select App Theme", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmazeButton(
                        text = "System",
                        onClick = { AppState.changeTheme(AppTheme.SYSTEM) },
                        variant = if (activeTheme == AppTheme.SYSTEM) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Light",
                        onClick = { AppState.changeTheme(AppTheme.LIGHT) },
                        variant = if (activeTheme == AppTheme.LIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmazeButton(
                        text = "Dark",
                        onClick = { AppState.changeTheme(AppTheme.DARK) },
                        variant = if (activeTheme == AppTheme.DARK) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Midnight",
                        onClick = { AppState.changeTheme(AppTheme.MIDNIGHT) },
                        variant = if (activeTheme == AppTheme.MIDNIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Accent Palette Selection Card
            Column {
                Text("Select Accent Palette", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmazeButton(
                        text = "Ocean",
                        onClick = { AppState.changeAccent(AccentTheme.OCEAN) },
                        variant = if (activeAccent == AccentTheme.OCEAN) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Forest",
                        onClick = { AppState.changeAccent(AccentTheme.FOREST) },
                        variant = if (activeAccent == AccentTheme.FOREST) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Lavender",
                        onClick = { AppState.changeAccent(AccentTheme.LAVENDER) },
                        variant = if (activeAccent == AccentTheme.LAVENDER) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AmazeButton(
                    text = "Sunset (Orange)",
                    onClick = { AppState.changeAccent(AccentTheme.SUNSET) },
                    variant = if (activeAccent == AccentTheme.SUNSET) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(12.dp))

            AmazeButton(
                text = "Close Student Session",
                onClick = { AppState.logout() },
                variant = ButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PostLoginOnboardingScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val scope = rememberCoroutineScope()
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()

    var friendlyName by remember { mutableStateOf("") }
    var selectedSem by remember { mutableStateOf("CH20252601") }
    var resStatus by remember { mutableStateOf("hosteller") } // "hosteller", "dayscholar_bus", "dayscholar_nobus"
    var showDecimalPct by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Title Section
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(radius.large))
                .background(colors.elevatedSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to AmazeCC",
            style = AmazeTheme.typography.display.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Configure your college preferences to personalize your dashboard.",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 1. Friendly name input
                Column {
                    AmazeTextField(
                        value = friendlyName,
                        onValueChange = { friendlyName = it },
                        label = "Preferred Name",
                        placeholder = "e.g. John Doe",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Semester selection dropdown
                Column {
                    Text(
                        text = "Active Academic Semester",
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val semesters = AppState.semesterIDs
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        semesters.take(3).forEach { sem ->
                            val isSelected = selectedSem == sem
                            val semLabel = when(sem) {
                                "CH20252601" -> "Fall 25-26"
                                "CH20242505" -> "Winter 24-25"
                                "CH20242501" -> "Fall 24-25"
                                else -> sem.takeLast(6)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(radius.small))
                                    .background(if (isSelected) colors.accent else colors.elevatedSurface)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(radius.small))
                                    .clickable { selectedSem = sem }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = semLabel,
                                    color = if (isSelected) colors.surface else colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 3. Residential / Housing Selector
                Column {
                    Text(
                        text = "Residential Status",
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("hosteller", "Hosteller", "75% limit"),
                            Triple("dayscholar_bus", "Day Scholar (Bus)", "85% limit"),
                            Triple("dayscholar_nobus", "Day Scholar (No Bus)", "75% limit")
                        ).forEach { (id, label, desc) ->
                            val isSelected = resStatus == id
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(radius.small))
                                    .background(if (isSelected) colors.accent else colors.elevatedSurface)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(radius.small))
                                    .clickable { resStatus = id }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.surface else colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = if (isSelected) colors.surface.copy(alpha = 0.8f) else colors.textMuted,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 4. Decimal Values precision switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.small))
                        .background(colors.elevatedSurface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.small))
                        .clickable { showDecimalPct = !showDecimalPct }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Decimal Attendance Precision",
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            text = "Show exact values (e.g. 75.24%) instead of whole numbers",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                        )
                    }
                    AmazeBadge(
                        text = if (showDecimalPct) "ON" else "OFF",
                        variant = if (showDecimalPct) BadgeVariant.SUCCESS else BadgeVariant.INFO
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = syncStatus ?: "Syncing VTOP details...",
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
            }
        } else {
            AmazeButton(
                text = "Let's Go! 🚀",
                onClick = {
                    scope.launch {
                        // Save preferences
                        SessionManager.friendlyName.value = friendlyName
                        SessionManager.decimalValues.value = showDecimalPct
                        SessionManager.isDayscholarWithBus.value = resStatus == "dayscholar_bus"
                        SessionManager.residentialStatus.value = if (resStatus == "hosteller") "hosteller" else "dayscholar"
                        
                        // Select chosen semester (which fetches the semester data)
                        AppState.selectSemester(selectedSem)
                        
                        // Complete onboarding state
                        SessionManager.postLoginCompleted.value = true
                        
                        // Navigate to dashboard
                        AppState.switchTopLevel(Screen.DASHBOARD)
                    }
                },
                variant = ButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
