package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun CourseDashboardScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()

    val courses = marksRes?.marks ?: emptyList()
    val attendanceData = attendanceRes?.attendance ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Course Dashboard",
            description = "Unified view of your ongoing subjects",
            showBackButton = true,
            showSyncButton = false
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(courses) { course ->
                val attInfo = attendanceData.find { it.courseCode == course.courseCode }
                CourseOverviewCard(course, attInfo)
            }
        }
    }
}

@Composable
fun CourseOverviewCard(course: MarksCourseItem, attendanceInfo: AttendanceItem?) {
    val colors = AmazeTheme.colors
    val attPct = attendanceInfo?.attendancePercentage?.toDoubleOrNull() ?: 0.0

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Book, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                    Text(course.courseTitle, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                if (attPct > 0) {
                    val attColor = if (attPct >= 75) Color(0xFF10B981) else Color(0xFFEF4444)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${attPct.toInt()}%", style = AmazeTheme.typography.heading.copy(color = attColor, fontSize = 18.sp))
                        Text("Attendance", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(16.dp))

            // Assessments
            if (course.assessments.isEmpty()) {
                Text("No assessment data available.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            } else {
                course.assessments.forEach { asm ->
                    val asmPct = if (asm.maxMark.toDoubleOrNull() != null && asm.maxMark.toDouble() > 0) {
                        ((asm.scoredMark.toDoubleOrNull() ?: 0.0) / asm.maxMark.toDouble()) * 100
                    } else 0.0
                    val isGood = asmPct >= 70

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            if (isGood) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (isGood) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asm.title, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = 14.sp))
                            Text("Weightage: ${asm.weightagePercent}%", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${asm.scoredMark} / ${asm.maxMark}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp))
                        }
                    }
                }
            }
        }
    }
}
