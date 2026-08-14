package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.CardVariant
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.HeroCard
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.model.displayCgpa
import com.amazecc.app.shared.model.displayCreditsEarned
import kotlin.math.roundToInt

@Composable
fun MarksGradesScreen() = AcademicsScreen()



@Composable
fun AcademicsScreen() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    val timetableRes by AppState.timetable.collectAsState()
    val allGradesRes by AppState.allGrades.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()

    val courses = marksRes?.marks ?: emptyList()
    val gpaRecords = allGradesRes?.grades ?: emptyMap()
    val attendanceCourses = attendanceRes?.attendance ?: emptyList()
    val months = calendarRes?.months ?: emptyList()

    val currentCgpa = marksRes.displayCgpa
    val creditsEarned = marksRes.displayCreditsEarned
    val totalRequiredCredits = 160.0
    val attendanceRows = attendanceRes?.attendance ?: emptyList()
    val avgAttendance = if (attendanceRows.isNotEmpty()) {
        attendanceRows.sumOf { it.attendancePercentage.replace("%", "").trim().toDoubleOrNull() ?: 0.0 } / attendanceRows.size
    } else 0.0

    val hubCards = listOf(
        HubCard("course-dashboard", "Course Hub", "Your one-stop hub — courses, grades, arrears, projects and more.", Icons.Rounded.Dashboard, Color.White, colors.accent, true),
        HubCard("grades", "Grade History", "Analyze your academic performance and past grades.", Icons.Rounded.History, colors.chart2, colors.chart2.copy(alpha = 0.12f)),
        HubCard("curriculum", "Curriculum", "Track your completed courses and credit requirements.", Icons.AutoMirrored.Rounded.MenuBook, colors.chart1, colors.chart1.copy(alpha = 0.12f)),
        HubCard("predictor", "CGPA Predictor", "Estimate your future CGPA based on expected grades.", Icons.AutoMirrored.Rounded.TrendingUp, colors.chart3, colors.chart3.copy(alpha = 0.12f)),
        HubCard("qbank", "Question Bank", "Access and search past year question papers.", Icons.Rounded.Storage, colors.chart5, colors.chart5.copy(alpha = 0.12f)),
        HubCard("ffcs", "FFCS Planner", "Timetable builder & clash finder.", Icons.Rounded.ViewTimeline, colors.chart1, colors.chart1.copy(alpha = 0.12f)),
        HubCard("free-classrooms", "Free Classrooms", "Empty classroom locator.", Icons.Rounded.MeetingRoom, colors.chart2, colors.chart2.copy(alpha = 0.12f)),
        HubCard("faculty", "Faculty Directory", "Faculty cabin & ratings.", Icons.Rounded.People, colors.chart5, colors.chart5.copy(alpha = 0.12f)),
        HubCard("moodle", "Moodle LMS", "Course materials & assignments.", Icons.AutoMirrored.Rounded.MenuBook, colors.chart2, colors.chart2.copy(alpha = 0.12f)),
        HubCard("feedback", "Feedback Status", "VTOP faculty feedback status.", Icons.Rounded.RateReview, colors.chart3, colors.chart3.copy(alpha = 0.12f)),
        HubCard("exam-schedule", "Exam Schedule", "Upcoming exams, seating, and venue details.", Icons.Rounded.EventSeat, colors.chart3, colors.chart3.copy(alpha = 0.12f)),
        HubCard("circulars", "Circulars", "Academic notices and circulars from VTOP.", Icons.Rounded.Campaign, colors.chart4, colors.chart4.copy(alpha = 0.12f)),
        HubCard("od-tracker", "OD Tracker", "Track on-duty hours, lab and theory.", Icons.Rounded.TaskAlt, colors.chart4, colors.chart4.copy(alpha = 0.12f)),
        HubCard("tasks", "Tasks & Reminders", "Homework, reminders and daily to-dos.", Icons.Rounded.CheckCircle, colors.chart1, colors.chart1.copy(alpha = 0.12f))
    )

    var currentView by remember { mutableStateOf<String?>(null) }
    if (currentView == "course-dashboard") {
        CourseDashboardScreen(onBack = { currentView = null })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = AmazeTheme.spacing.pageHorizontal, end = AmazeTheme.spacing.pageHorizontal, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.md)
        ) {
            item {
                HeaderSpacer()
            }
            // Stats overview
            item {
                StatsOverviewCard(currentCgpa, avgAttendance, creditsEarned, totalRequiredCredits)
            }

            // Hub grid
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm)
                ) {
                    hubCards.chunked(2).forEach { rowCards ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm)
                        ) {
                            rowCards.forEach { card ->
                                Box(modifier = Modifier.weight(1f)) {
                                    HubCardItem(card = card, onClick = {
                                        when (card.id) {
                                            "course-dashboard" -> currentView = "course-dashboard"
                                            "grades" -> AppState.navigateTo(Screen.GRADES)
                                            "predictor" -> AppState.navigateTo(Screen.GPA_PREDICTOR)
                                            "exam-schedule" -> AppState.navigateTo(Screen.EXAM_SCHEDULE)
                                            "circulars" -> AppState.navigateTo(Screen.CIRCULARS)
                                            "curriculum" -> AppState.navigateTo(Screen.CURRICULUM)
                                            "od-tracker" -> AppState.navigateTo(Screen.OD_TRACKER)
                                            "qbank" -> AppState.navigateTo(Screen.QBANK)
                                            "ffcs" -> AppState.navigateTo(Screen.FFCS_PLANNER)
                                            "free-classrooms" -> AppState.navigateTo(Screen.FREE_CLASSROOMS)
                                            "faculty" -> AppState.navigateTo(Screen.FACULTY_INFO)
                                            "moodle" -> AppState.navigateTo(Screen.MOODLE)
                                            "feedback" -> AppState.navigateTo(Screen.FEEDBACK_STATUS)
                                            "tasks" -> AppState.navigateTo(Screen.TASKS)
                                        }
                                    })
                                }
                            }
                            // Fill empty space if row is not full
                            if (rowCards.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(AmazeTheme.spacing.md)) }
        }
    }


}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    val colors = AmazeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Text(title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    }
}

@Composable
private fun EmptyState(message: String) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(message, color = colors.textSecondary)
        }
    }
}

private fun cgpaFormatted(cgpa: Double): String {
    val i = kotlin.math.round(cgpa * 100).toLong()
    val whole = i / 100
    val frac = (i % 100).coerceIn(0, 99)
    return "$whole.${frac.toString().padStart(2, '0')}"
}

@Composable
fun StatsOverviewCard(cgpa: Double, attendance: Double, credits: Double, required: Double) {
    val colors = AmazeTheme.colors
    val animCgpa by animateFloatAsState(targetValue = cgpa.toFloat(), animationSpec = com.amazecc.app.shared.ui.components.mediumSpring())
    val animAtt by animateFloatAsState(targetValue = attendance.toFloat(), animationSpec = com.amazecc.app.shared.ui.components.mediumSpring())

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox("CGPA", cgpaFormatted(animCgpa.toDouble()), Icons.Rounded.EmojiEvents, colors.chart1)
                StatBox("Attendance", "${animAtt.roundToInt()}%", Icons.Rounded.Percent, colors.accent)
                StatBox("Credits", "${credits.toInt()}/${required.toInt()}", Icons.Rounded.School, colors.chart4)
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
            LinearProgressIndicator(
                progress = { (credits / required).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = colors.chart4,
                trackColor = colors.chart4.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
fun StatBox(label: String, value: String, icon: ImageVector, iconColor: Color) {
    val colors = AmazeTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)).border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
        Text(value, style = AmazeTheme.typography.heading.copy(color = colors.textPrimary, fontWeight = FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
fun HubCardItem(card: HubCard, onClick: () -> Unit) {
    val colors = AmazeTheme.colors
    if (card.prominent) {
        HeroCard(
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AmazeTheme.spacing.cardPadding),
            onClick = onClick
        ) { p ->
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(p.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(card.icon, contentDescription = null, tint = p.text, modifier = Modifier.size(20.dp))
            }
            Text(
                text = card.title,
                style = AmazeTheme.typography.subheading.copy(
                    fontWeight = FontWeight.Bold,
                    color = p.text
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = card.description,
                style = AmazeTheme.typography.caption.copy(
                    color = p.textSecondary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            backgroundColor = null,
            variant = CardVariant.DEFAULT
        ) {
            Column {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(card.bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(card.icon, contentDescription = null, tint = card.color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Text(
                    text = card.title,
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Text(
                    text = card.description,
                    style = AmazeTheme.typography.caption.copy(
                        color = colors.textSecondary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
