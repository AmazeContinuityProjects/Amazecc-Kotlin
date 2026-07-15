@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.utils.SlotInfo
import kotlinx.coroutines.delay
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

    val overallAttendance = remember(courses) {
        if (courses.isEmpty()) 0f
        else {
            var totalAtt = 0
            var totalCls = 0
            for (item in courses) {
                    totalAtt += item.attendedClasses
                    totalCls += item.totalClasses
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
                    onClick = { AppState.loadAllData() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                ) {
                    val isSyncing by AppState.isLoading.collectAsState()
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Sync All Data",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
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
                item { GlassMetricCard("CGPA", cgpa, Icons.Rounded.Star, colors) }
                item { GlassMetricCard("Credits", credits, Icons.Rounded.Info, colors) }
                item { GlassMetricCard("ODs", "0", Icons.Rounded.CheckCircle, colors) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val animatedAttendance by animateFloatAsState(
                targetValue = overallAttendance / 100f,
                animationSpec = tween(1500)
            )

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
                            progress = { animatedAttendance },
                            modifier = Modifier.fillMaxSize(),
                            color = if (overallAttendance >= 75f) colors.success
                            else if (overallAttendance >= 50f) colors.warning
                            else colors.danger,
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
                                color = if (overallAttendance >= 75f) colors.success
                                else if (overallAttendance >= 50f) colors.warning
                                else colors.danger,
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

            var tick by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000)
                    tick++
                }
            }

            val currentClass = remember(todayClasses, tick) {
                AttendanceTimetable.findCurrentClass(todayClasses)
            }
            val nextClass = remember(todayClasses, tick) {
                AttendanceTimetable.findNextClass(todayClasses)
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
                        val isCurrent = cls == currentClass
                        val isNext = cls == nextClass
                        val pct = cls.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isCurrent) colors.accent.copy(alpha = 0.06f)
                                    else colors.surface
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) colors.accent.copy(alpha = 0.4f) else colors.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.accent.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                "LIVE",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.accent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    if (isNext) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.warning.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                "UP NEXT",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.warning,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        cls.courseTitle ?: "",
                                        style = AmazeTheme.typography.body.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                     Box(
                                        modifier = Modifier
                                            .background(
                                                if (pct >= 75) colors.success.copy(alpha = 0.12f)
                                                else if (pct >= 50) colors.warning.copy(alpha = 0.12f)
                                                else colors.danger.copy(alpha = 0.12f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            "${cls.attendancePercentage ?: "?"}",
                                            color = if (pct >= 75) colors.success
                                            else if (pct >= 50) colors.warning
                                            else colors.danger,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(if (isCurrent) 36.dp else 32.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colors.accent)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                cls.time,
                                                style = AmazeTheme.typography.caption.copy(
                                                    color = if (isCurrent) colors.accent else colors.textPrimary,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                            Text(
                                                cls.slotName ?: "",
                                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                            )
                                        }
                                        if (isCurrent) {
                                            val remaining = remember(tick) {
                                                AttendanceTimetable.remainingMinutes(cls.time)
                                            }
                                            val minsStr = if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m" else "${remaining} min"
                                            Text(
                                                "$minsStr left",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.accent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        if (isNext) {
                                            val until = remember(tick) {
                                                AttendanceTimetable.minutesUntil(cls.time)
                                            }
                                            val minsStr = if (until >= 60) "${until / 60}h ${until % 60}m" else "${until} min"
                                            Text(
                                                "Starts in $minsStr",
                                                style = AmazeTheme.typography.smallLabel.copy(
                                                    color = colors.warning,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
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
                    GlassActionCard(Modifier.weight(1f), "Predict Att.", Icons.Rounded.CheckCircle, colors, onClick = { AppState.navigateTo(Screen.GPA_PREDICTOR) })
                    GlassActionCard(Modifier.weight(1f), "GPA Calc", Icons.Rounded.Star, colors, onClick = { AppState.navigateTo(Screen.GRADES) })
                    GlassActionCard(Modifier.weight(1f), "Apply Leave", Icons.AutoMirrored.Rounded.ExitToApp, colors, onClick = { AppState.navigateTo(Screen.HOSTEL) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassActionCard(Modifier.weight(1f), "Bus Routes", Icons.Rounded.Info, colors, onClick = { AppState.navigateTo(Screen.TRANSPORT) })
                    GlassActionCard(Modifier.weight(1f), "Wishlist", Icons.Rounded.Favorite, colors, onClick = { AppState.navigateTo(Screen.WISHLIST) })
                    GlassActionCard(Modifier.weight(1f), "Hostel", Icons.Rounded.Home, colors, onClick = { AppState.navigateTo(Screen.HOSTEL) })
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
                    .clickable { AppState.navigateTo(Screen.FREE_CLASSROOMS) }
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
        percentage >= 85.0 -> colors.success
        percentage >= 75.0 -> colors.warning
        else -> colors.danger
    }
    val isCritical = percentage < 75.0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCritical) colors.danger.copy(alpha = 0.05f) else colors.glassSurface)
            .border(
                1.dp,
                if (isCritical) colors.danger.copy(alpha = 0.2f) else colors.glassBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable { AppState.openCourseAttendance(course.courseCode) }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = course.courseTitle,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Classes: ${course.attendedClasses} / ${course.totalClasses}",
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, badgeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
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
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
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
