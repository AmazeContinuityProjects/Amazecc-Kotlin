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
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.config.SlotMap
import kotlin.math.roundToInt

@Suppress("unused")
@Composable
fun MarksGradesScreen() = AcademicsScreen()

@Suppress("unused")
@Composable
fun CalendarScreen() = AcademicsScreen()

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
    val timetableCourses = timetableRes?.courseInfo ?: emptyList()
    val attendanceCourses = attendanceRes?.attendance ?: emptyList()
    val months = calendarRes?.months ?: emptyList()

    val currentCgpa = marksRes?.cgpa?.cgpa?.toDoubleOrNull() ?: 0.0
    val creditsEarned = marksRes?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0
    val totalRequiredCredits = 160.0
    val attendanceRows = attendanceRes?.attendance ?: emptyList()
    val avgAttendance = if (attendanceRows.isNotEmpty()) {
        attendanceRows.sumOf { it.attendancePercentage.toDoubleOrNull() ?: 0.0 } / attendanceRows.size
    } else 0.0

    val hubCards = listOf(
        HubCard("course-dashboard", "Course Hub", "Your one-stop hub — courses, grades, arrears, projects and more.", Icons.Rounded.Dashboard, Color.White, colors.accent, true),
        HubCard("grades", "Grade History", "Analyze your academic performance and past grades.", Icons.Rounded.History, Color(0xFF9333EA), Color(0xFFF3E8FF)),
        HubCard("curriculum", "Curriculum", "Track your completed courses and credit requirements.", Icons.AutoMirrored.Rounded.MenuBook, Color(0xFF16A34A), Color(0xFFDCFCE7)),
        HubCard("predictor", "CGPA Predictor", "Estimate your future CGPA based on expected grades.", Icons.AutoMirrored.Rounded.TrendingUp, Color(0xFFEA580C), Color(0xFFFFEDD5)),
        HubCard("qbank", "Question Bank", "Access and search past year question papers.", Icons.Rounded.Storage, Color(0xFFDC2626), Color(0xFFFEE2E2)),
        HubCard("arrear", "Arrear Management", "View arrear schedule, details and grades.", Icons.Rounded.Warning, Color(0xFFD97706), Color(0xFFFEF3C7)),
        HubCard("makeup", "Makeup & Compre", "Makeup exam eligibility, schedule and compre info.", Icons.Rounded.School, Color(0xFF0891B2), Color(0xFFCFFAFE)),
        HubCard("circulars", "Circulars", "Academic notices and circulars from VTOP.", Icons.Rounded.Campaign, Color(0xFF6366F1), Color(0xFFEEF2FF)),
        HubCard("od-tracker", "OD Tracker", "Track on-duty hours, lab and theory.", Icons.Rounded.TaskAlt, Color(0xFFEC4899), Color(0xFFFDF2F8)),
        HubCard("marks-timeline", "Marks Timeline", "Assessment history and grade trend.", Icons.Rounded.Timeline, Color(0xFF14B8A6), Color(0xFFF0FDFA)),
        HubCard("vitol", "VITOL Wallet", "Digital wallet balance and transactions.", Icons.Rounded.AccountBalanceWallet, Color(0xFF8B5CF6), Color(0xFFF5F3FF))
    )

    var currentView by remember { mutableStateOf<String?>(null) }
    var showTimetableDialog by remember { mutableStateOf(false) }
    if (currentView == "course-dashboard") {
        CourseDashboardScreen(onBack = { currentView = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Academics Hub",
            description = "Student OS",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshCurrentSemester
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats overview
            item {
                StatsOverviewCard(currentCgpa, avgAttendance, creditsEarned, totalRequiredCredits)
            }

            // Hub grid
            item {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    userScrollEnabled = false
                ) {
                    items(hubCards) { card ->
                        HubCardItem(card = card, onClick = {
                            when (card.id) {
                                "course-dashboard" -> currentView = "course-dashboard"
                                "grades" -> AppState.navigateTo(Screen.GRADES)
                                "predictor" -> AppState.navigateTo(Screen.GPA_PREDICTOR)
                                "arrear" -> AppState.navigateTo(Screen.ARREAR)
                                "makeup" -> AppState.navigateTo(Screen.MAKEUP_COMPRE)
                                "circulars" -> AppState.navigateTo(Screen.CIRCULARS)
                                "curriculum" -> AppState.navigateTo(Screen.CURRICULUM)
                                "od-tracker" -> AppState.navigateTo(Screen.OD_TRACKER)
                                "marks-timeline" -> AppState.navigateTo(Screen.MARKS_TIMELINE)
                                "vitol" -> AppState.navigateTo(Screen.VITOL)
                                "qbank" -> AppState.navigateTo(Screen.QBANK)
                            }
                        })
                    }
                }
            }

            // ── Internal Marks Section ──
            item {
                SectionHeader("Internal Marks", Icons.Rounded.Grade)
            }

            if (courses.isEmpty()) {
                item {
                    EmptyState("No internal marks records.")
                }
            } else {
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
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

            // ── Grade History Section ──
            item {
                SectionHeader("Grade History", Icons.Rounded.History)
            }

            if (gpaRecords.isEmpty()) {
                item {
                    EmptyState("No GPA & grade history records.")
                }
            } else {
                gpaRecords.forEach { (semId, semResult) ->
                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(semId, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("GPA: ${semResult?.gpa ?: "-"}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.accent))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                semResult?.grades?.forEach { grade ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

            // ── Timetable Section ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Schedule", Icons.Rounded.CalendarMonth)
                    TextButton(onClick = { showTimetableDialog = true }) {
                        Icon(Icons.Rounded.CalendarViewWeek, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Full Week", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = AmazeTheme.colors.accent))
                    }
                }
            }

            if (timetableCourses.isEmpty() && attendanceCourses.isEmpty()) {
                item {
                    EmptyState("No timetable data found. Tap refresh to sync.")
                }
            } else {
                if (timetableCourses.isNotEmpty()) {
                    timetableCourses.forEach { c ->
                        item {
                            TimetableCard(
                                code = c.courseCode,
                                title = c.course,
                                faculty = c.facultyDetails,
                                venue = c.slotVenue,
                                slotCode = c.courseCode.take(4),
                                onClick = { showTimetableDialog = true }
                            )
                        }
                    }
                } else {
                    attendanceCourses.forEach { c ->
                        item {
                            TimetableCard(
                                code = c.courseCode,
                                title = c.courseTitle,
                                faculty = c.faculty,
                                venue = c.slotVenue ?: "—",
                                slotCode = c.slotName,
                                onClick = { showTimetableDialog = true }
                            )
                        }
                    }
                }
            }

            // ── Academic Calendar Section ──
            item {
                SectionHeader("Academic Calendar", Icons.Rounded.Event)
            }

            if (months.isEmpty()) {
                item {
                    EmptyState("No calendar events found.")
                }
            } else {
                items(months) { monthData ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(monthData.month, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Spacer(modifier = Modifier.height(12.dp))
                            monthData.days.forEach { dayData ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(dayData.date.toString(), style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        dayData.events.forEach { event ->
                                            Text(event.type, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                            Text(event.text, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showTimetableDialog) {
        TimetableDialog(
            attendanceCourses = attendanceCourses,
            timetableCourses = timetableCourses,
            onDismiss = { showTimetableDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    val colors = AmazeTheme.colors
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
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
    val whole = cgpa.toInt()
    val frac = ((cgpa - whole) * 100).roundToInt()
    return "$whole.${if (frac < 10) "0" else ""}$frac"
}

@Composable
fun StatsOverviewCard(cgpa: Double, attendance: Double, credits: Double, required: Double) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBox("CGPA", cgpaFormatted(cgpa), Icons.Rounded.EmojiEvents, Color(0xFF10B981))
            StatBox("Attendance", "${attendance.roundToInt()}%", Icons.Rounded.Percent, colors.accent)
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
private fun TimetableCard(code: String, title: String, faculty: String, venue: String, slotCode: String, onClick: () -> Unit = {}) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(slotCode, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(code, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                Text(title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Faculty: $faculty", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Text("Venue: $venue", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            }
        }
    }
}

@Composable
fun TimetableDialog(
    attendanceCourses: List<AttendanceItem>,
    timetableCourses: List<Any>,
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    var selectedDay by remember { mutableStateOf("MON") }

    val dayCourses = remember(selectedDay, attendanceCourses) {
        attendanceCourses.filter { it.slotName.uppercase().take(3) == selectedDay }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            color = colors.background,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekly Timetable", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = colors.textSecondary)
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(days) { day ->
                        val isSelected = selectedDay == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.accent else colors.surface)
                                .clickable { selectedDay = day }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                day,
                                color = if (isSelected) Color.White else colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (dayCourses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No classes on $selectedDay", color = colors.textMuted)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dayCourses) { course ->
                            val time = SlotMap.map[selectedDay]?.get(course.slotName) ?: "—"
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(course.slotName.take(3), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = colors.accent)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                        Text(course.courseTitle, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(time, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.accent)
                                        Text(course.faculty, fontSize = 10.sp, color = colors.textSecondary)
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
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(card.bgColor),
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
