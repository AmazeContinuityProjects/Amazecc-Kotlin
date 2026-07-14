@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.CommandPalette
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DashboardScreen() {
    val colors = AmazeTheme.colors
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val profile by AppState.studentProfile.collectAsState()

    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val allSemesterAttendance by AppState.allSemesterAttendance.collectAsState()

    val courses = attendanceRes?.attendance ?: emptyList()
    val allCourses = remember(allSemesterAttendance, courses) {
        val semesterCourses = allSemesterAttendance.values
            .filterNotNull()
            .flatMap { it.attendance.orEmpty() }
        courses + semesterCourses
    }

    val overallAttendance = remember(allCourses) {
        if (allCourses.isEmpty()) 0f
        else {
            var totalAtt = 0
            var totalCls = 0
            for (item in allCourses) {
                totalAtt += item.attendedClasses ?: 0
                totalCls += item.totalClasses ?: 0
            }
            if (totalCls == 0) 0f else (totalAtt.toFloat() / totalCls.toFloat()) * 100f
        }
    }

    val cgpa = marksRes?.cgpa?.cgpa ?: "—"
    val credits = marksRes?.cgpa?.creditsEarned ?: "—"

    val avatarText = (profile?.name ?: authorizedID ?: "U").take(2).uppercase()
    val greeting = remember {
        "Good ${getGreeting()}${if (profile?.name != null) ", ${profile!!.name.split(" ").first()}" else ""}"
    }

    var showCommandPalette by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Profile Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(colors.accent, colors.accent.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f), colors.accent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = AmazeTheme.typography.body.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = profile?.regNo ?: authorizedID ?: "Student",
                        style = AmazeTheme.typography.subheading.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                IconButton(
                    onClick = { showCommandPalette = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Metric Cards Row ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item { GlassMetricCard("CGPA", cgpa.toString(), Icons.Rounded.Star, colors) }
                item { GlassMetricCard("Credits", credits.toString(), Icons.Rounded.Info, colors) }
                item { GlassMetricCard("ODs", "0", Icons.Rounded.CheckCircle, colors) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Attendance Ring Card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(88.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { overallAttendance / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = if (overallAttendance >= 75f) Color(0xFF10B981)
                            else if (overallAttendance >= 50f) Color(0xFFF59E0B)
                            else Color(0xFFEF4444),
                            trackColor = colors.border,
                            strokeWidth = 8.dp
                        )
                        Text(
                            text = "${overallAttendance.toInt()}%",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 16.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overall Attendance",
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (overallAttendance >= 75f) "You're on track!"
                            else if (overallAttendance >= 50f) "Needs improvement!"
                            else "Critical!",
                            style = AmazeTheme.typography.caption.copy(
                                color = if (overallAttendance >= 75f) Color(0xFF10B981)
                                else if (overallAttendance >= 50f) Color(0xFFF59E0B)
                                else Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Predict", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Today's Classes ──
            val slotMapTyped = remember {
                SlotMap.map.mapValues { (_, inner) ->
                    inner.mapValues { (_, time) -> SlotInfo(time) }
                }
            }
            val todayClasses = remember(courses) {
                AttendanceTimetable.getTodayAttendanceClasses(
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Classes",
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                if (todayClasses.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${todayClasses.size} classes",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (todayClasses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FreeBreakfast, null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No classes today!", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                        Text("Enjoy your day off", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todayClasses.forEach { cls ->
                        val pct = cls.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (pct >= 75) Color(0xFF10B981)
                                            else if (pct >= 50) Color(0xFFF59E0B)
                                            else Color(0xFFEF4444)
                                        )
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        cls.courseTitle ?: "",
                                        style = AmazeTheme.typography.body.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        ),
                                        maxLines = 1
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            cls.time,
                                            style = AmazeTheme.typography.caption.copy(
                                                color = colors.accent,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            cls.slotName ?: "",
                                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (pct >= 75) Color(0xFF10B981).copy(alpha = 0.12f)
                                            else if (pct >= 50) Color(0xFFF59E0B).copy(alpha = 0.12f)
                                            else Color(0xFFEF4444).copy(alpha = 0.12f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "${cls.attendancePercentage ?: "?"}",
                                        color = if (pct >= 75) Color(0xFF10B981)
                                        else if (pct >= 50) Color(0xFFF59E0B)
                                        else Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Course Attendance ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Course Attendance",
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${allCourses.size} courses",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (allCourses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Text("No course data available.", color = colors.textSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allCourses.take(4).forEach { course ->
                        CourseGlassCard(course, colors)
                    }
                    if (allCourses.size > 4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .clickable { AppState.navigateTo(Screen.ATTENDANCE) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "View All ${allCourses.size} Courses",
                                style = AmazeTheme.typography.body.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Quick Actions ──
            Text(
                text = "Quick Actions",
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassActionCard(Modifier.weight(1f), "Predict Att.", Icons.Rounded.CheckCircle, colors)
                    GlassActionCard(Modifier.weight(1f), "GPA Calc", Icons.Rounded.Star, colors)
                    GlassActionCard(Modifier.weight(1f), "Apply Leave", Icons.AutoMirrored.Rounded.ExitToApp, colors)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassActionCard(Modifier.weight(1f), "Bus Routes", Icons.Rounded.Info, colors)
                    GlassActionCard(Modifier.weight(1f), "Wishlist", Icons.Rounded.Favorite, colors)
                    GlassActionCard(Modifier.weight(1f), "Hostel", Icons.Rounded.Home, colors)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Free Classrooms Widget ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable { }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MeetingRoom, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Find Free Classrooms",
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            "Locate an empty spot to sit and study.",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showCommandPalette) {
        CommandPalette(onDismiss = { showCommandPalette = false })
    }
}

private fun getGreeting(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return when {
        now.hour < 12 -> "Morning"
        now.hour < 17 -> "Afternoon"
        else -> "Evening"
    }
}

@Composable
private fun GlassMetricCard(title: String, value: String, icon: ImageVector, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    title,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 22.sp
                )
            )
        }
    }
}

@Composable
private fun CourseGlassCard(
    course: AttendanceItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val percentage = course.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0
    val badgeColor = when {
        percentage >= 85.0 -> Color(0xFF10B981)
        percentage >= 75.0 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val isCritical = percentage < 75.0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCritical) Color(0xFFEF4444).copy(alpha = 0.05f) else colors.surface)
            .border(
                1.dp,
                if (isCritical) Color(0xFFEF4444).copy(alpha = 0.2f) else colors.border,
                RoundedCornerShape(16.dp)
            )
            .clickable { AppState.openCourseAttendance(course.courseCode) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.courseTitle,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Classes: ${course.attendedClasses}/${course.totalClasses}",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${percentage.toInt()}%",
                    style = AmazeTheme.typography.caption.copy(
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun GlassActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable { }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            title,
            style = AmazeTheme.typography.caption.copy(
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}
