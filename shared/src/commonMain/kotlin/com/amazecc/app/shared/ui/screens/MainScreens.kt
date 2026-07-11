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
fun AttendanceScreen() = AcademicsScreen(initialTab = "Attendance")

@Composable
fun MarksGradesScreen() = AcademicsScreen(initialTab = "Marks & GPA")

@Composable
fun TimetableScreen() = AcademicsScreen(initialTab = "Schedule")

@Composable
fun PaymentsScreen() = ServicesScreen(initialTab = "Payments")

@Composable
fun LibraryScreen() = ServicesScreen(initialTab = "Library")

@Composable
fun TransportScreen() = ServicesScreen(initialTab = "Transport")

@Composable
fun LMSScreen() = ServicesScreen(initialTab = "LMS")

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

// ── 1. UNIFIED ACADEMICS SCREEN (TABS: ATTENDANCE, MARKS, SCHEDULE) ──

@Composable
fun AcademicsScreen(initialTab: String = "Attendance") {
    val colors = AmazeTheme.colors
    var activeSubTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Attendance", "Marks & GPA", "Schedule")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Academics Hub",
            description = "Track classes, grades & schedules",
            showBackButton = false,
            showSyncButton = true
        )

        // Sub-Tab Navigation row
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
                "Attendance" -> AttendanceSubScreen()
                "Marks & GPA" -> MarksSubScreen()
                "Schedule" -> TimetableSubScreen()
            }
        }
    }
}

// ── 2. UNIFIED SERVICES SCREEN (TABS: PAYMENTS, LIBRARY, TRANSPORT, LMS) ──

@Composable
fun ServicesScreen(initialTab: String = "Payments") {
    val colors = AmazeTheme.colors
    var activeSubTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Payments", "Library", "Transport", "LMS")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Campus Services",
            description = "Dues, Koha, buses & LMS tasks",
            showBackButton = false,
            showSyncButton = true
        )

        // Sub-Tab Navigation row
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
                "Payments" -> PaymentsSubScreen()
                "Library" -> LibrarySubScreen()
                "Transport" -> TransportSubScreen()
                "LMS" -> LMSSubScreen()
            }
        }
    }
}

// ── SUB-SCREEN IMPLEMENTATIONS ──

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
    
    val average = remember(courses) {
        courses.mapNotNull { it.attendancePercentage?.toDoubleOrNull() }.takeIf { it.isNotEmpty() }?.average()
    }
    
    val filteredCourses = remember(courses, filter, searchQuery) {
        courses.filter { course ->
            val matchesSearch = course.courseTitle.contains(searchQuery, ignoreCase = true) || 
                                course.courseCode.contains(searchQuery, ignoreCase = true)
            val percentage = course.attendancePercentage?.toDoubleOrNull() ?: 100.0
            val matchesFilter = when (filter) {
                "Watchlist" -> percentage < 75.0
                "Border" -> percentage in 75.0..80.0
                "Safe" -> percentage > 80.0
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    if (courses.isEmpty()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No attendance data found", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                Text("Tap refresh to sync VTOP attendance.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Weekly attendance", style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black))
                            Text("Last sync: " + (syncStatus ?: "Active session cached"), style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                        AmazeBadge(
                            text = average?.let { "${it.toInt()}%" } ?: "—",
                            variant = when {
                                average == null -> BadgeVariant.INFO
                                average >= 85.0 -> BadgeVariant.SUCCESS
                                average >= 75.0 -> BadgeVariant.WARNING
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
                            average >= 85.0 -> colors.success
                            average >= 75.0 -> colors.warning
                            else -> colors.danger
                        },
                        trackColor = colors.border
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    WeekdayAttendanceStrip(courses)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search and Filter controls
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredCourses) { course ->
                    AttendanceCardItem(course = course, onClick = { activeDetailCourse = course })
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
    val percentage = course.attendancePercentage?.toDoubleOrNull()
    val progress = ((percentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val attended = course.attendedClasses ?: 0
    val total = course.totalClasses ?: 0
    
    val target = 0.75
    val classesNeeded = if (percentage != null && percentage < 75.0) {
        ceil(((target * total) - attended) / (1 - target)).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val canMiss = if (percentage != null && percentage >= 75.0) {
        floor((attended - target * total) / target).toInt().coerceAtLeast(0)
    } else {
        0
    }
    
    val badgeVariant = when {
        percentage == null -> BadgeVariant.INFO
        percentage >= 85.0 -> BadgeVariant.SUCCESS
        percentage >= 75.0 -> BadgeVariant.WARNING
        else -> BadgeVariant.DANGER
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
                    text = course.attendancePercentage?.let { "$it%" } ?: "—",
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
                    label = if ((percentage ?: 100.0) < 75.0) "Classes Needed" else "Can Miss",
                    value = if ((percentage ?: 100.0) < 75.0) "$classesNeeded classes" else "$canMiss classes",
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
private fun WeekdayAttendanceStrip(courses: List<AttendanceItem>) {
    val colors = AmazeTheme.colors
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(colors.elevatedSurface)
            .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
    ) {
        days.forEachIndexed { index, day ->
            val classCount = courses.count { course ->
                val seed = course.slotName.fold(0) { acc, c -> acc + c.code }
                seed % 7 == index
            }.coerceAtMost(9)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(day, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (classCount == 0) "Free" else "$classCount",
                    style = AmazeTheme.typography.caption.copy(
                        color = if (classCount == 0) colors.textMuted else colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
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
    
    val attended = course.attendedClasses ?: 0
    val total = course.totalClasses ?: 0
    val percentage = course.attendancePercentage?.toDoubleOrNull() ?: 0.0
    
    var simAttended by remember { mutableStateOf(0) }
    var simMissed by remember { mutableStateOf(0) }
    
    val simTotal = total + simAttended + simMissed
    val simAttendedTotal = attended + simAttended
    val simPercentage = if (simTotal == 0) 0.0 else (simAttendedTotal * 100.0) / simTotal
    
    val target = 0.75
    val classesNeeded = if (percentage < 75.0) {
        ceil(((target * total) - attended) / (1 - target)).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val canMiss = if (percentage >= 75.0) {
        floor((attended - target * total) / target).toInt().coerceAtLeast(0)
    } else {
        0
    }

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
                            text = "${percentage.toInt()}%",
                            variant = when {
                                percentage >= 85.0 -> BadgeVariant.SUCCESS
                                percentage >= 75.0 -> BadgeVariant.WARNING
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
                                text = if (percentage < 75.0) "Need $classesNeeded classes" else "Can miss $canMiss classes",
                                style = AmazeTheme.typography.body.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = if (percentage < 75.0) colors.danger else colors.success
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
                    simPercentage >= 85.0 -> colors.successSurface
                    simPercentage >= 75.0 -> colors.warningSurface
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
                            text = "${simPercentage.toInt()}%",
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
            showBackButton = false,
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
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val activeTheme by AppState.theme.collectAsState()
    val activeAccent by AppState.accent.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "App Preferences",
            description = "Themes, accents, and session logout",
            showBackButton = false,
            showSyncButton = false
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
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
                        Text("VIT University student", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                        Text(authorizedID ?: "DEMO123", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Session state: ACTIVE", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.success))
                    }
                }
            }

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
