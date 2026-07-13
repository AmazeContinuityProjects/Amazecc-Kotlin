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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.CommandPalette
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.CourseAttendanceInfo
import com.amazecc.app.shared.utils.SlotInfo

@Composable
fun DashboardScreen() {
    val colors = AmazeTheme.colors
    val authorizedID by SessionManager.authorizedID.collectAsState()
    
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    
    val overallAttendance = remember(attendanceRes) {
        val list = attendanceRes?.attendance ?: emptyList()
        if (list.isEmpty()) 0f
        else {
            var totalAtt = 0
            var totalCls = 0
            for (item in list) {
                totalAtt += item.attendedClasses ?: 0
                totalCls += item.totalClasses ?: 0
            }
            if (totalCls == 0) 0f else (totalAtt.toFloat() / totalCls.toFloat()) * 100f
        }
    }
    
    val cgpa = marksRes?.cgpa?.cgpa ?: "—"
    val credits = marksRes?.cgpa?.creditsEarned ?: "—"
    
    val courses = attendanceRes?.attendance ?: emptyList()
    var showCommandPalette by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Space at top
        Spacer(modifier = Modifier.height(16.dp))
        
        // Custom Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (authorizedID ?: "U").take(2).uppercase(),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Good Evening,", // Placeholder for dynamic greeting
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
                Text(
                    text = authorizedID ?: "User",
                    style = AmazeTheme.typography.subheading.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            IconButton(
                onClick = { /* Refresh logic */ },
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Spotlight Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .clickable { showCommandPalette = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Spotlight Search (Cmd + K)",
                    style = AmazeTheme.typography.body.copy(color = colors.textMuted)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Insights Dock (Short Cards)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item { InsightCard("CGPA", cgpa, Icons.Rounded.Star) }
                item { InsightCard("Credits", credits.toString(), Icons.Rounded.Info) }
                item { InsightCard("ODs", "0 Approved", Icons.Rounded.CheckCircle) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Attendance Summary
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                        CircularProgressIndicator(
                            progress = { overallAttendance / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = if (overallAttendance >= 75f) colors.success else colors.danger,
                            trackColor = colors.border,
                            strokeWidth = 8.dp
                        )
                        Text(
                            text = "%",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overall Attendance",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            text = if (overallAttendance >= 75f) "You're on track!" else "Critical condition!",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { AppState.navigateTo(Screen.ATTENDANCE) }, // Will change to Hub
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Predict Attendance", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Today's Classes
            val todayClasses = remember(courses) {
                val slotMapTyped = SlotMap.map.mapValues { (_, inner) ->
                    inner.mapValues { (_, time) -> SlotInfo(time) }
                }
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

            Text(
                text = "Today's Classes (${todayClasses.size})",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (todayClasses.isEmpty()) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FreeBreakfast, null, tint = colors.textMuted, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No classes scheduled for today!", style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                        Text("Enjoy your day off", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todayClasses.forEach { cls ->
                        val pct = cls.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 0.0
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cls.courseTitle ?: "", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), maxLines = 1)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(cls.time, style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Medium))
                                        Text(cls.slotName ?: "", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (pct >= 75) Color(0xFF10B981).copy(alpha = 0.12f)
                                            else if (pct >= 50) Color(0xFFF59E0B).copy(alpha = 0.12f)
                                            else Color(0xFFEF4444).copy(alpha = 0.12f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${cls.attendancePercentage ?: "?"}", color = if (pct >= 75) Color(0xFF10B981) else if (pct >= 50) Color(0xFFF59E0B) else Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Course Attendance
            Text(
                text = "Course Attendance",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (courses.isEmpty()) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No course data available.", color = colors.textSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    courses.take(4).forEach { course ->
                        CourseAttendanceRow(course)
                    }
                    if (courses.size > 4) {
                        TextButton(
                            onClick = { AppState.navigateTo(Screen.ATTENDANCE) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("View All Courses", color = colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Actions Grid
            Text(
                text = "Quick Actions",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionBtn(Modifier.weight(1f), "Predict Att.", Icons.Rounded.CheckCircle)
                    QuickActionBtn(Modifier.weight(1f), "GPA Calc", Icons.Rounded.Star)
                    QuickActionBtn(Modifier.weight(1f), "Apply Leave", Icons.AutoMirrored.Rounded.ExitToApp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionBtn(Modifier.weight(1f), "Bus Routes", Icons.Rounded.Info)
                    QuickActionBtn(Modifier.weight(1f), "Wishlist", Icons.Rounded.Favorite)
                    QuickActionBtn(Modifier.weight(1f), "Hostel", Icons.Rounded.Home)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Free Classrooms Widget
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(colors.accent.copy(alpha=0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.accent)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Find Free Classrooms", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Locate an empty spot to sit and study.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textMuted)
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showCommandPalette) {
        CommandPalette(onDismiss = { showCommandPalette = false })
    }
}

@Composable
fun InsightCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = colors.accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            Text(value, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
    }
}

@Composable
fun CourseAttendanceRow(course: AttendanceItem) {
    val colors = AmazeTheme.colors
    val percentage = course.attendancePercentage?.toDoubleOrNull() ?: 0.0
    
    val badgeColor = when {
        percentage >= 85.0 -> colors.success
        percentage >= 75.0 -> colors.warning
        else -> colors.danger
    }
    
    val isCritical = percentage < 75.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCritical) colors.danger.copy(alpha = 0.05f) else colors.surface, 
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp, 
                if (isCritical) colors.danger.copy(alpha = 0.2f) else colors.border, 
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseTitle,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Classes: /",
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "%",
                style = AmazeTheme.typography.caption.copy(color = badgeColor, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun QuickActionBtn(modifier: Modifier = Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable { /* action */ }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = title, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
    }
}


