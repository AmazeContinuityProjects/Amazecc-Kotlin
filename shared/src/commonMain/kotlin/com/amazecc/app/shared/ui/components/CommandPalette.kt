package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme

data class CommandPaletteItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen
)

data class CoursePaletteItem(
    val courseCode: String,
    val courseTitle: String,
    val attendancePct: String
)

data class TaskPaletteItem(
    val title: String,
    val courseCode: String,
    val dueDate: String
)

@Composable
fun CommandPalette(
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    var query by remember { mutableStateOf("") }
    
    val attendanceRes by AppState.attendance.collectAsState()
    val tasks by AppState.tasks.collectAsState()

    val allCommands = remember {
        listOf(
            CommandPaletteItem("Home", Icons.Rounded.Home, Screen.HOME),
            CommandPaletteItem("Attendance", Icons.AutoMirrored.Rounded.FactCheck, Screen.ATTENDANCE),
            CommandPaletteItem("Academics Hub", Icons.Rounded.School, Screen.ACADEMICS),
            CommandPaletteItem("Payments", Icons.Rounded.CreditCard, Screen.PAYMENTS),
            CommandPaletteItem("Library", Icons.AutoMirrored.Rounded.LibraryBooks, Screen.LIBRARIES),
            CommandPaletteItem("Hostel", Icons.Rounded.Apartment, Screen.HOSTEL),
            CommandPaletteItem("Transport", Icons.Rounded.DirectionsBus, Screen.TRANSPORT),
            CommandPaletteItem("Cab Share", Icons.Rounded.DirectionsCar, Screen.CABSHARE),
            CommandPaletteItem("Events", Icons.Rounded.Event, Screen.EVENTS),
            CommandPaletteItem("QBank", Icons.Rounded.Topic, Screen.QBANK),
            CommandPaletteItem("Social", Icons.Rounded.People, Screen.SOCIAL),
            CommandPaletteItem("Profile", Icons.Rounded.Person, Screen.PROFILE),
            CommandPaletteItem("Grades", Icons.Rounded.History, Screen.GRADES),
            CommandPaletteItem("CGPA Predictor", Icons.AutoMirrored.Rounded.TrendingUp, Screen.GPA_PREDICTOR),
            CommandPaletteItem("Makeup & Compre", Icons.Rounded.School, Screen.MAKEUP_COMPRE),
            CommandPaletteItem("Circulars", Icons.Rounded.Campaign, Screen.CIRCULARS),
            CommandPaletteItem("Curriculum", Icons.AutoMirrored.Rounded.MenuBook, Screen.CURRICULUM),
            CommandPaletteItem("OD Tracker", Icons.Rounded.TaskAlt, Screen.OD_TRACKER),
            CommandPaletteItem("Course Hub", Icons.Rounded.Dashboard, Screen.COURSE_DASHBOARD),
            CommandPaletteItem("Marks Timeline", Icons.Rounded.Timeline, Screen.MARKS_TIMELINE),
            CommandPaletteItem("VITOL Wallet", Icons.Rounded.AccountBalanceWallet, Screen.VITOL),
            CommandPaletteItem("Faculty Info", Icons.Rounded.People, Screen.FACULTY_INFO),
            CommandPaletteItem("Course Management", Icons.Rounded.School, Screen.COURSE_MANAGEMENT),
            CommandPaletteItem("Projects", Icons.Rounded.WorkspacePremium, Screen.PROJECTS),
            CommandPaletteItem("Wishlist", Icons.Rounded.Favorite, Screen.WISHLIST),
            CommandPaletteItem("Feedback", Icons.Rounded.RateReview, Screen.FEEDBACK_STATUS),
            CommandPaletteItem("Fresher Welcome", Icons.Rounded.Star, Screen.FRESHER_WELCOME),
            CommandPaletteItem("Documents", Icons.Rounded.Description, Screen.DOCUMENTS),
            CommandPaletteItem("About", Icons.Rounded.Info, Screen.ABOUT)
        )
    }

    val courseResults: List<CoursePaletteItem> = remember(attendanceRes, query) {
        val list = attendanceRes?.attendance ?: emptyList()
        val mapped = list.map { CoursePaletteItem(it.courseCode, it.courseTitle, "${it.attendancePercentage}%") }
        if (query.isBlank()) {
            mapped
        } else {
            mapped.filter { 
                it.courseCode.contains(query, ignoreCase = true) || it.courseTitle.contains(query, ignoreCase = true)
            }
        }
    }

    val taskResults: List<TaskPaletteItem> = remember(tasks, query) {
        val mapped = tasks.map { TaskPaletteItem(it.title, it.courseCode, it.dueDate) }
        if (query.isBlank()) {
            mapped.take(5)
        } else {
            mapped.filter {
                it.title.contains(query, ignoreCase = true) || it.courseCode.contains(query, ignoreCase = true)
            }
        }
    }

    val filteredCommands: List<CommandPaletteItem> = remember(query) {
        if (query.isBlank()) allCommands
        else allCommands.filter { it.label.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            AmazeTextField(
                value = query,
                onValueChange = { query = it },
                label = "",
                placeholder = "Spotlight Search (Courses, Screens, Tasks)...",
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (courseResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "📚 COURSES",
                            style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                    items(courseResults) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accent.copy(alpha = 0.08f))
                                .clickable {
                                    AppState.navigateTo(Screen.COURSE_ATTENDANCE)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Class, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.courseCode, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                Text(item.courseTitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                            Text(item.attendancePct, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                if (taskResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "✅ TASKS",
                            style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                    items(taskResults) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .clickable {
                                    AppState.navigateTo(Screen.TASKS)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.TaskAlt, null, tint = colors.success, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                                Text("${item.courseCode} • Due ${item.dueDate}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "🚀 SCREENS & NAVIGATION",
                        style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                    )
                }
                items(filteredCommands) { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                AppState.navigateTo(cmd.screen)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cmd.icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(cmd.label, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
