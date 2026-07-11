package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ActionCard
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.MetricCard

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
    val syncStatus by AppState.syncStatus.collectAsState()
    val syncError by AppState.error.collectAsState()

    val attendance = attendanceRes?.attendance.orEmpty()
    val overallAttendance = remember(attendance) {
        val validPercentages = attendance.mapNotNull { it.attendancePercentage?.toDoubleOrNull() }
        if (validPercentages.isEmpty()) null else validPercentages.average()
    }
    val overallAttendanceLabel = overallAttendance?.let { "${it.toInt()}%" } ?: "-"
    val attendanceProgress = ((overallAttendance ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val criticalCourses = remember(attendance) {
        attendance.filter { (it.attendancePercentage?.toDoubleOrNull() ?: 100.0) < 75.0 }
    }
    val cgpa = marksRes?.cgpa?.cgpa ?: "-"
    val credits = remember(marksRes) {
        val earned = marksRes?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0
        val nonGraded = marksRes?.cgpa?.nonGradedRequirement?.toDoubleOrNull() ?: 0.0
        (earned + nonGraded).takeIf { it > 0.0 }?.toInt()?.toString() ?: "-"
    }
    val nextClasses = timetableRes?.courseInfo.orEmpty().take(3)
    val assignmentsDue = lmsRes?.assignments.orEmpty().count { it.status.equals("Pending", ignoreCase = true) }
    val libraryIssues = libraryRes?.booksIssued?.size ?: 0
    val walletBalance = paymentsRes?.walletBalance ?: "-"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        DashboardHero(
            studentId = authorizedID ?: "Student",
            semester = selectedSemester,
            status = syncStatus ?: "Student OS ready",
            attendance = overallAttendanceLabel,
            attendanceProgress = attendanceProgress,
            onRefresh = { AppState.loadAllData() }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedDashboardItem(index = 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "ATTENDANCE",
                            value = overallAttendanceLabel,
                            caption = "${attendance.size} courses tracked",
                            statusText = if (criticalCourses.isEmpty()) "SAFE" else "${criticalCourses.size} LOW",
                            statusColor = if (criticalCourses.isEmpty()) colors.success else colors.warning,
                            onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                            modifier = Modifier.width(176.dp)
                        )
                        MetricCard(
                            title = "CGPA",
                            value = cgpa,
                            caption = "$credits credits earned",
                            onClick = { AppState.navigateTo(Screen.MARKS) },
                            modifier = Modifier.width(176.dp)
                        )
                        MetricCard(
                            title = "LMS",
                            value = "$assignmentsDue",
                            caption = "Pending assignments",
                            onClick = { AppState.navigateTo(Screen.LMS) },
                            modifier = Modifier.width(176.dp)
                        )
                    }
                }
            }

            if (syncError != null || criticalCourses.isNotEmpty()) {
                item {
                    AnimatedDashboardItem(index = 1) {
                        AlertDock(
                            syncError = syncError,
                            criticalCourses = criticalCourses
                        )
                    }
                }
            }

            item {
                AnimatedDashboardItem(index = 2) {
                    SectionTitle(
                        title = "Today",
                        caption = "Classes, deadlines, and quick context"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TodayPreview(
                        classes = nextClasses.map { course ->
                            DashboardClassItem(
                                code = course.courseCode,
                                title = course.course,
                                meta = course.slotVenue ?: course.facultyDetails ?: "Slot details pending"
                            )
                        },
                        assignmentsDue = assignmentsDue
                    )
                }
            }

            item {
                AnimatedDashboardItem(index = 3) {
                    SectionTitle(
                        title = "Quick actions",
                        caption = "Fast paths for between-class usage"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactAction(
                            label = "Attendance",
                            icon = Icons.Rounded.CheckCircle,
                            onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactAction(
                            label = "Timetable",
                            icon = Icons.Rounded.DateRange,
                            onClick = { AppState.navigateTo(Screen.TIMETABLE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactAction(
                            label = "Payments",
                            icon = Icons.Rounded.ShoppingCart,
                            onClick = { AppState.navigateTo(Screen.PAYMENTS) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactAction(
                            label = "Settings",
                            icon = Icons.Rounded.Settings,
                            onClick = { AppState.navigateTo(Screen.PROFILE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                AnimatedDashboardItem(index = 4) {
                    SectionTitle(
                        title = "Academics",
                        caption = "AmazeCC study workspace"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ActionCard(
                        title = "Course Dashboard",
                        description = "Marks, grades, attendance, and course-level overview",
                        icon = Icons.Rounded.Star,
                        onClick = { AppState.navigateTo(Screen.MARKS) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionCard(
                        title = "Attendance Tracker",
                        description = "Color-coded percentages and low-attendance subjects",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionCard(
                        title = "Class Timetable",
                        description = "Daily classes, room details, and semester slots",
                        icon = Icons.Rounded.DateRange,
                        onClick = { AppState.navigateTo(Screen.TIMETABLE) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                AnimatedDashboardItem(index = 5) {
                    SectionTitle(
                        title = "Campus life",
                        caption = "Services and resident tools"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        CampusTile(
                            title = "Library",
                            value = "$libraryIssues issued",
                            icon = Icons.Rounded.Book,
                            onClick = { AppState.navigateTo(Screen.LIBRARY) },
                            modifier = Modifier.weight(1f)
                        )
                        CampusTile(
                            title = "Wallet",
                            value = walletBalance,
                            icon = Icons.Rounded.ShoppingCart,
                            onClick = { AppState.navigateTo(Screen.PAYMENTS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        CampusTile(
                            title = "Hostel",
                            value = "Mess & leave",
                            icon = Icons.Rounded.Home,
                            onClick = { AppState.navigateTo(Screen.HOSTEL) },
                            modifier = Modifier.weight(1f)
                        )
                        CampusTile(
                            title = "Transport",
                            value = "Routes",
                            icon = Icons.Rounded.Info,
                            onClick = { AppState.navigateTo(Screen.TRANSPORT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHero(
    studentId: String,
    semester: String,
    status: String,
    attendance: String,
    attendanceProgress: Float,
    onRefresh: () -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = radius.large, bottomEnd = radius.large))
            .background(colors.surface)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                        text = "Welcome back",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = studentId,
                        style = AmazeTheme.typography.heading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AmazeButton(
                    text = "Sync",
                    onClick = onRefresh,
                    variant = ButtonVariant.SECONDARY,
                    icon = Icons.Rounded.Refresh,
                    modifier = Modifier.width(104.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            AmazeCard(backgroundColor = colors.elevatedSurface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Student OS",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = status,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AmazeBadge(text = semester, variant = BadgeVariant.INFO)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = attendance,
                            style = AmazeTheme.typography.display.copy(color = colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Overall attendance",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertDock(
    syncError: String?,
    criticalCourses: List<AttendanceItem>
) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = if (syncError != null) colors.dangerSurface else colors.warningSurface) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (syncError != null) colors.dangerText else colors.warningText
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (syncError != null) "Sync needs attention" else "Attendance watchlist",
                    style = AmazeTheme.typography.body.copy(
                        color = if (syncError != null) colors.dangerText else colors.warningText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = syncError ?: criticalCourses.take(2).joinToString { "${it.courseCode} ${it.attendancePercentage ?: "-"}%" },
                    style = AmazeTheme.typography.caption.copy(
                        color = if (syncError != null) colors.dangerText else colors.warningText
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TodayPreview(
    classes: List<DashboardClassItem>,
    assignmentsDue: Int
) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DateRange, contentDescription = null, tint = colors.accent)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (classes.isEmpty()) "No timetable preview available" else "Upcoming classes",
                    style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                AmazeBadge(
                    text = "$assignmentsDue LMS",
                    variant = if (assignmentsDue > 0) BadgeVariant.WARNING else BadgeVariant.SUCCESS
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (classes.isEmpty()) {
                Text(
                    text = "Sync timetable data to see the next class cards here.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            } else {
                classes.forEachIndexed { index, item ->
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
                                text = item.code,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = item.title,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.meta,
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
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
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
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold)
            )
            Text(
                text = value,
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    caption: String
) {
    val colors = AmazeTheme.colors
    Column {
        Text(
            text = title,
            style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black)
        )
        Text(
            text = caption,
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )
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
        enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = index * 45)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 260, delayMillis = index * 45),
                initialOffsetY = { it / 6 }
            )
    ) {
        content()
    }
}

private data class DashboardClassItem(
    val code: String,
    val title: String,
    val meta: String
)
