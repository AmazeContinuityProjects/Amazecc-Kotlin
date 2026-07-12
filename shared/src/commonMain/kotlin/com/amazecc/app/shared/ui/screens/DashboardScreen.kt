package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.DashboardWidget
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val colors = AmazeTheme.colors
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    val timetableRes by AppState.timetable.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val libraryRes by AppState.library.collectAsState()
    val paymentsRes by AppState.payments.collectAsState()
    val lmsRes by AppState.lms.collectAsState()
    val examRes by AppState.examSchedule.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    
    val syncStatus by AppState.syncStatus.collectAsState()
    val syncError by AppState.error.collectAsState()

    // Customization states
    val widgetOrder by SessionManager.dashboardWidgets.collectAsState()
    val hiddenWidgets by SessionManager.hiddenWidgets.collectAsState()
    val collapsedWidgets by SessionManager.collapsedWidgets.collectAsState()
    val compactMetrics by SessionManager.compactMetricsView.collectAsState()

    var isCustomizingOpen by remember { mutableStateOf(false) }

    // Computations
    val attendance = attendanceRes?.attendance.orEmpty()
    val overallAttendance = remember(attendance) {
        val validPercentages = attendance.mapNotNull { it.attendancePercentage?.toDoubleOrNull() }
        if (validPercentages.isEmpty()) null else validPercentages.average()
    }
    val overallAttendanceLabel = overallAttendance?.let { "${it.toInt()}%" } ?: "—"
    val attendanceProgress = ((overallAttendance ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    
    val criticalCourses = remember(attendance) {
        attendance.filter { (it.attendancePercentage?.toDoubleOrNull() ?: 100.0) < 75.0 }
    }
    val hideCGPA by SessionManager.hideCGPA.collectAsState()
    val cgpa = marksRes?.cgpa?.cgpa ?: "—"
    val credits = remember(marksRes) {
        val earned = marksRes?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0
        val nonGraded = marksRes?.cgpa?.nonGradedRequirement?.toDoubleOrNull() ?: 0.0
        (earned + nonGraded).takeIf { it > 0.0 }?.toInt()?.toString() ?: "—"
    }
    
    val nextClasses = timetableRes?.courseInfo.orEmpty().take(3)
    val assignmentsDue = lmsRes?.assignments.orEmpty().count { it.status.equals("Pending", ignoreCase = true) }
    val libraryIssues = libraryRes?.booksIssued?.size ?: 0
    val walletBalance = paymentsRes?.walletBalance ?: "—"
    val examsList = remember(examRes) {
        examRes?.schedule?.values?.flatten().orEmpty()
    }
    
    val calendarEvents = remember(calendarRes) {
        calendarRes?.months?.flatMap { it.days.flatMap { d -> d.events } }.orEmpty().take(4)
    }

    val greeting = remember {
        val hour = 14 // Mocked local context or current hour
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Hero Header
        DashboardHero(
            studentId = authorizedID ?: "Student",
            semester = selectedSemester,
            status = syncStatus ?: "All modules synchronized",
            attendance = overallAttendanceLabel,
            attendanceProgress = attendanceProgress,
            onRefresh = { AppState.loadAllData() },
            onCustomizeClick = { isCustomizingOpen = true }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            widgetOrder.forEachIndexed { index, widget ->
                if (widget !in hiddenWidgets) {
                    val isCollapsed = widget in collapsedWidgets
                    
                    AnimatedDashboardItem(index = index) {
                        DashboardSectionWrapper(
                            widget = widget,
                            isCollapsed = isCollapsed,
                            onCollapseToggle = { SessionManager.toggleWidgetCollapse(widget) }
                        ) {
                            when (widget) {
                                DashboardWidget.GREETING -> {
                                    GreetingWidget(greeting = greeting, studentId = authorizedID ?: "DEMO123")
                                }
                                DashboardWidget.METRICS -> {
                                    MetricsWidget(
                                        attendanceLabel = overallAttendanceLabel,
                                        attendanceCount = attendance.size,
                                        criticalCount = criticalCourses.size,
                                        cgpa = cgpa,
                                        credits = credits,
                                        assignments = assignmentsDue,
                                        compact = compactMetrics,
                                        hideCGPA = hideCGPA
                                    )
                                }
                                DashboardWidget.ALERTS -> {
                                    if (syncError != null || criticalCourses.isNotEmpty()) {
                                        AlertWidget(syncError = syncError, criticalCourses = criticalCourses)
                                    }
                                }
                                DashboardWidget.TODAY_CLASSES -> {
                                    TodayClassesWidget(classes = nextClasses, assignmentsDue = assignmentsDue)
                                }
                                DashboardWidget.UPCOMING_EXAMS -> {
                                    UpcomingExamsWidget(exams = examsList)
                                }
                                DashboardWidget.QUICK_ACTIONS -> {
                                    QuickActionsWidget()
                                }
                                DashboardWidget.ACADEMICS_HUB -> {
                                    AcademicsHubWidget()
                                }
                                DashboardWidget.CAMPUS_SERVICES -> {
                                    CampusServicesWidget(libraryCount = libraryIssues, walletBalance = walletBalance)
                                }
                                DashboardWidget.RECENT_ACTIVITY -> {
                                    RecentActivityWidget(events = calendarEvents, syncStatus = syncStatus)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCustomizingOpen) {
        CustomizeDashboardSheet(
            onDismiss = { isCustomizingOpen = false }
        )
    }
}

// ── CUSTOMIZATION SHEET ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeDashboardSheet(
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    
    val widgetOrder by SessionManager.dashboardWidgets.collectAsState()
    val hiddenWidgets by SessionManager.hiddenWidgets.collectAsState()
    val compactMetrics by SessionManager.compactMetricsView.collectAsState()

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
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = colors.accent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Customize Dashboard",
                        style = AmazeTheme.typography.heading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    )
                    Text(
                        text = "Show/hide and reorder home screen widgets",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Metric card size config
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.medium))
                    .background(colors.elevatedSurface)
                    .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                    .clickable { SessionManager.compactMetricsView.value = !compactMetrics }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Compact Summary Metrics", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                    Text("Arrange CGPA & Attendance in a grid list", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                Switch(
                    checked = compactMetrics,
                    onCheckedChange = { SessionManager.compactMetricsView.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.border
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Reorder & Toggle Widgets", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(widgetOrder) { widget ->
                    val visible = widget !in hiddenWidgets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.small))
                            .background(colors.elevatedSurface)
                            .border(1.dp, colors.border, RoundedCornerShape(radius.small))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drag/Reorder buttons
                        IconButton(
                            onClick = { SessionManager.moveWidgetUp(widget) },
                            enabled = widgetOrder.indexOf(widget) > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up", tint = colors.textPrimary)
                        }
                        IconButton(
                            onClick = { SessionManager.moveWidgetDown(widget) },
                            enabled = widgetOrder.indexOf(widget) < widgetOrder.lastIndex,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down", tint = colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = widget.displayName,
                            style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        // Toggle visible
                        IconButton(
                            onClick = { SessionManager.toggleWidgetVisibility(widget) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (visible) "Hide" else "Show",
                                tint = if (visible) colors.accent else colors.textMuted
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            AmazeButton(text = "Save Preferences", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── WIDGETS WRAPPER & CONTAINER ──

@Composable
private fun DashboardSectionWrapper(
    widget: DashboardWidget,
    isCollapsed: Boolean,
    onCollapseToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    if (widget == DashboardWidget.GREETING) {
        content()
        return
    }

    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCollapseToggle() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val title = when (widget) {
                DashboardWidget.METRICS -> "Summary stats"
                DashboardWidget.ALERTS -> "Attention alerts"
                DashboardWidget.TODAY_CLASSES -> "Upcoming classes"
                DashboardWidget.UPCOMING_EXAMS -> "Upcoming exams"
                DashboardWidget.QUICK_ACTIONS -> "Quick actions"
                DashboardWidget.ACADEMICS_HUB -> "Academics"
                DashboardWidget.CAMPUS_SERVICES -> "Campus life"
                DashboardWidget.RECENT_ACTIVITY -> "Recent updates"
                else -> ""
            }
            Text(
                text = title,
                style = AmazeTheme.typography.subheading.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            )
            Icon(
                imageVector = if (isCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!isCollapsed) {
            content()
        }
    }
}

// ── SPECIFIC WIDGETS ──

@Composable
private fun DashboardHero(
    studentId: String,
    semester: String,
    status: String,
    attendance: String,
    attendanceProgress: Float,
    onRefresh: () -> Unit,
    onCustomizeClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = radius.large, bottomEnd = radius.large))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(bottomStart = radius.large, bottomEnd = radius.large))
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        style = AmazeTheme.typography.heading.copy(color = colors.accent, fontWeight = FontWeight.Black)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AmazeCC Student OS",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = studentId,
                        style = AmazeTheme.typography.heading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onCustomizeClick,
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                        .size(40.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Customize", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.medium))
                        .background(colors.elevatedSurface)
                        .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Sync",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AmazeCard(backgroundColor = colors.elevatedSurface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LATEST SYNC FEEDBACK",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = status,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AmazeBadge(text = semester, variant = BadgeVariant.INFO)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = attendance,
                            style = AmazeTheme.typography.display.copy(color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { attendanceProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = colors.accent,
                                trackColor = colors.border
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Overall academic attendance",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingWidget(greeting: String, studentId: String) {
    val colors = AmazeTheme.colors
    val friendlyName by SessionManager.friendlyName.collectAsState()
    val displayName = if (friendlyName.isNotBlank()) friendlyName else "Student"

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$greeting, $displayName!",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    text = "Welcome to your student workspace dashboard ($studentId).",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }
    }
}

@Composable
private fun MetricsWidget(
    attendanceLabel: String,
    attendanceCount: Int,
    criticalCount: Int,
    cgpa: String,
    credits: String,
    assignments: Int,
    compact: Boolean,
    hideCGPA: Boolean = false
) {
    val colors = AmazeTheme.colors
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricRowItem(title = "Attendance", value = attendanceLabel, sub = "$attendanceCount tracked", modifier = Modifier.weight(1f))
                MetricRowItem(title = "CGPA", value = cgpa, sub = "$credits credits", modifier = Modifier.weight(1f), isBlur = hideCGPA)
                MetricRowItem(title = "Assignments", value = "$assignments Due", sub = "LMS workspace", modifier = Modifier.weight(1f))
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "ATTENDANCE",
                value = attendanceLabel,
                caption = "$attendanceCount courses tracked",
                statusText = if (criticalCount == 0) "SAFE" else "$criticalCount LOW",
                statusColor = if (criticalCount == 0) colors.success else colors.danger,
                onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                modifier = Modifier.width(160.dp)
            )
            MetricCard(
                title = "CGPA",
                value = cgpa,
                caption = "$credits credits earned",
                statusText = "Academics",
                onClick = { AppState.navigateTo(Screen.MARKS) },
                modifier = Modifier.width(160.dp),
                isBlur = hideCGPA
            )
            MetricCard(
                title = "PENDING ASSIGNMENTS",
                value = "$assignments",
                caption = "Tasks to submit",
                statusText = if (assignments > 0) "PENDING" else "ALL DONE",
                statusColor = if (assignments > 0) colors.warning else colors.success,
                onClick = { AppState.navigateTo(Screen.LMS) },
                modifier = Modifier.width(160.dp)
            )
        }
    }
}

@Composable
private fun MetricRowItem(title: String, value: String, sub: String, modifier: Modifier = Modifier, isBlur: Boolean = false) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.small))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(radius.small))
            .padding(10.dp)
    ) {
        Column {
            Text(title.uppercase(), style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 9.sp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                modifier = if (isBlur) Modifier.blur(6.dp) else Modifier
            )
            Text(sub, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 10.sp))
        }
    }
}

@Composable
private fun AlertWidget(syncError: String?, criticalCourses: List<AttendanceItem>) {
    val colors = AmazeTheme.colors
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (syncError != null) colors.dangerSurface else colors.warningSurface
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (syncError != null) colors.dangerText else colors.warningText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (syncError != null) "Sync Connection Error" else "Attendance watchlist warning",
                    style = AmazeTheme.typography.body.copy(
                        color = if (syncError != null) colors.dangerText else colors.warningText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = syncError ?: "The following subjects are below the 75% requirement: " +
                            criticalCourses.joinToString { "${it.courseCode} (${it.attendancePercentage}%)" },
                    style = AmazeTheme.typography.caption.copy(
                        color = if (syncError != null) colors.dangerText else colors.warningText,
                        fontSize = 12.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TodayClassesWidget(classes: List<CourseItem>, assignmentsDue: Int) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (classes.isEmpty()) "No classes today" else "Timetable Preview",
                    style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                AmazeBadge(
                    text = "$assignmentsDue Assignments",
                    variant = if (assignmentsDue > 0) BadgeVariant.WARNING else BadgeVariant.SUCCESS
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (classes.isEmpty()) {
                Text(
                    text = "No scheduled class sessions found for today. Make sure to sync latest data.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            } else {
                classes.forEachIndexed { index, course ->
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.accent)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.courseCode,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = course.course,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = course.slotVenue ?: course.facultyDetails ?: "No slot venue details",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingExamsWidget(exams: List<ExamItem>) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Exam Schedule Preview",
                    style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (exams.isEmpty()) {
                Text(
                    text = "No upcoming exams registered. Sync exam calendar from services.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            } else {
                exams.take(2).forEachIndexed { i, exam ->
                    if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.elevatedSurface)
                            .padding(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(exam.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                            Text(exam.examDate, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        }
                        Text(exam.courseTitle, style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(
                            text = "Session: ${exam.examSession} • Venue: ${exam.venue} • Seat: ${exam.seatNo}",
                            style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsWidget() {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            CompactAction(
                label = "Academics Hub",
                icon = Icons.Rounded.Star,
                onClick = { AppState.navigateTo(Screen.MARKS) },
                modifier = Modifier.weight(1f)
            )
            CompactAction(
                label = "Timetable Info",
                icon = Icons.Rounded.DateRange,
                onClick = { AppState.navigateTo(Screen.TIMETABLE) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            CompactAction(
                label = "Hostel & Leaves",
                icon = Icons.Rounded.Home,
                onClick = { AppState.navigateTo(Screen.HOSTEL) },
                modifier = Modifier.weight(1f)
            )
            CompactAction(
                label = "Payments Dues",
                icon = Icons.Rounded.ShoppingCart,
                onClick = { AppState.navigateTo(Screen.PAYMENTS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AcademicsHubWidget() {
    Column {
        ActionCard(
            title = "Weekly Attendance Tracker",
            description = "Detailed insights, limits, and safe-attendance projections.",
            icon = Icons.Rounded.CheckCircle,
            onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        ActionCard(
            title = "Internal Marks & Grades",
            description = "Detailed breakdown of assessments and overall credit reports.",
            icon = Icons.Rounded.Star,
            onClick = { AppState.navigateTo(Screen.MARKS) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CampusServicesWidget(libraryCount: Int, walletBalance: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        CampusTile(
            title = "KOHA Library",
            value = "$libraryCount books checked out",
            icon = Icons.Rounded.Book,
            onClick = { AppState.navigateTo(Screen.LIBRARY) },
            modifier = Modifier.weight(1f)
        )
        CampusTile(
            title = "V-Wallet Balance",
            value = walletBalance,
            icon = Icons.Rounded.ShoppingCart,
            onClick = { AppState.navigateTo(Screen.PAYMENTS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecentActivityWidget(events: List<CalendarEvent>, syncStatus: String?) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Calendar notices & recent activity",
                    style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (events.isEmpty()) {
                Text(
                    text = syncStatus ?: "Active student session synced. No current events listed.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            } else {
                events.forEachIndexed { i, event ->
                    if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (event.type.equals("Holiday", ignoreCase = true)) colors.danger else colors.accent)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = event.text,
                            style = AmazeTheme.typography.caption.copy(color = colors.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = event.type,
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = modifier, onClick = onClick, backgroundColor = colors.surface) {
        Column(horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                    .background(colors.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CampusTile(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = modifier, onClick = onClick, backgroundColor = colors.surface) {
        Column {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
            )
            Text(
                text = value,
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AnimatedDashboardItem(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 280, delayMillis = index * 50)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 320, delayMillis = index * 50),
                initialOffsetY = { it / 10 }
            ) +
            scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(durationMillis = 280, delayMillis = index * 50)
            )
    ) {
        content()
    }
}
