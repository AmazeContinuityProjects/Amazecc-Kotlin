package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

data class HubCard(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val bgColor: Color,
    val prominent: Boolean = false
)

@Composable
fun AcademicsHubScreen(onNavigate: (String) -> Unit) {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()

    val currentCgpa = marksRes?.cgpa?.cgpa?.toDoubleOrNull() ?: 0.0
    val creditsEarned = marksRes?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0
    val totalRequiredCredits = 160.0
    val degreeCompletePercent = if (totalRequiredCredits > 0) ((creditsEarned / totalRequiredCredits) * 100).coerceAtMost(100.0) else 0.0

    val attendanceRows = attendanceRes?.attendance ?: emptyList()
    val avgAttendance = if (attendanceRows.isNotEmpty()) {
        attendanceRows.sumOf { it.attendancePercentage.toDoubleOrNull() ?: 0.0 } / attendanceRows.size
    } else 0.0

    val cards = listOf(
        HubCard("course-dashboard", "Course Hub", "Your one-stop hub — courses, grades, arrears, projects and more.", Icons.Rounded.Dashboard, Color.White, colors.accent, true),
        HubCard("grades", "Grade History", "Analyze your academic performance and past grades.", Icons.Rounded.History, Color(0xFF9333EA), Color(0xFFF3E8FF)),
        HubCard("curriculum", "Curriculum", "Track your completed courses and credit requirements.", Icons.Rounded.MenuBook, Color(0xFF16A34A), Color(0xFFDCFCE7)),
        HubCard("predictor", "CGPA Predictor", "Estimate your future CGPA based on expected grades.", Icons.Rounded.TrendingUp, Color(0xFFEA580C), Color(0xFFFFEDD5)),
        HubCard("qbank", "Question Bank", "Access and search past year question papers.", Icons.Rounded.Storage, Color(0xFFDC2626), Color(0xFFFEE2E2)),
        HubCard("arrear", "Arrear Management", "View arrear schedule, details and grades.", Icons.Rounded.Warning, Color(0xFFD97706), Color(0xFFFEF3C7)),
        HubCard("makeup", "Makeup & Compre", "Makeup exam eligibility, schedule and compre info.", Icons.Rounded.School, Color(0xFF0891B2), Color(0xFFCFFAFE))
    )

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Academics Hub",
            description = "Student OS",
            showBackButton = false,
            showSyncButton = true
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                StatsOverviewCard(currentCgpa, avgAttendance, creditsEarned, totalRequiredCredits)
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(cards) { card ->
                HubCardItem(card = card, onClick = { onNavigate(card.id) })
            }
        }
    }
}

@Composable
fun StatsOverviewCard(cgpa: Double, attendance: Double, credits: Double, required: Double) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBox("CGPA", "%.2f".format(cgpa), Icons.Rounded.EmojiEvents, Color(0xFF10B981))
            StatBox("Attendance", "%.0f%%".format(attendance), Icons.Rounded.Percent, colors.accent)
            StatBox("Credits", "${credits.toInt()}/${required.toInt()}", Icons.Rounded.School, Color(0xFF9333EA))
        }
    }
}

@Composable
fun StatBox(label: String, value: String, icon: ImageVector, iconColor: Color) {
    val colors = AmazeTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, style = AmazeTheme.typography.heading.copy(fontSize = 18.sp, color = colors.textPrimary))
        Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
    }
}

@Composable
fun HubCardItem(card: HubCard, onClick: () -> Unit) {
    val colors = AmazeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (card.prominent) colors.accent else colors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(card.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(card.icon, contentDescription = null, tint = card.color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = card.title,
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (card.prominent) Color.White else colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.description,
                style = AmazeTheme.typography.caption.copy(
                    color = if (card.prominent) Color.White.copy(alpha = 0.8f) else colors.textSecondary,
                    lineHeight = 16.sp
                )
            )
        }
    }
}
