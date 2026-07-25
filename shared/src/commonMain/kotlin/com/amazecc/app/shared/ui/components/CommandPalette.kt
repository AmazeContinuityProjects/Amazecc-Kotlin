package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
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

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = 1f,
            animationSpec = bouncySpring(),
            label = "scale"
        )
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "alpha"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface.copy(alpha = 0.95f))
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header / Search Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = 18.sp),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text("What do you need?", style = AmazeTheme.typography.body.copy(color = colors.textMuted, fontSize = 18.sp))
                                }
                                innerTextField()
                            },
                            singleLine = true
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    
                    // Results
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (courseResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📚 COURSES",
                                    style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }
                            items(courseResults, key = { it.courseCode }) { item ->
                                AmazeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        AppState.openCourseDetail(item.courseCode)
                                        onDismiss()
                                    },
                                    variant = com.amazecc.app.shared.ui.components.CardVariant.GLASS
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.accent.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Class, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.courseCode, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                            Text(item.courseTitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                        AmazeBadge(
                                            text = item.attendancePct,
                                            variant = com.amazecc.app.shared.ui.components.BadgeVariant.INFO
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                        
                        if (taskResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "✅ TASKS",
                                    style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }
                            items(taskResults, key = { it.title + it.courseCode }) { item ->
                                AmazeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        AppState.navigateTo(Screen.TASKS)
                                        onDismiss()
                                    },
                                    variant = com.amazecc.app.shared.ui.components.CardVariant.GLASS
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.TaskAlt, null, tint = colors.success, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                            Text("${item.courseCode} • Due ${item.dueDate}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }

                        if (filteredCommands.isNotEmpty()) {
                            item {
                                Text(
                                    text = "🚀 SCREENS",
                                    style = AmazeTheme.typography.categoryLabel.copy(color = colors.textMuted),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }
                            items(filteredCommands, key = { it.label }) { cmd ->
                                AmazeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        AppState.navigateTo(cmd.screen)
                                        onDismiss()
                                    },
                                    variant = com.amazecc.app.shared.ui.components.CardVariant.GLASS
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.textSecondary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(cmd.icon, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(cmd.label, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                                    }
                                }
                            }
                        }
                        
                        if (courseResults.isEmpty() && taskResults.isEmpty() && filteredCommands.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(12.dp))
                                        Text("No results found for \"$query\"", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
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
