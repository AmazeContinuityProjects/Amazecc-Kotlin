package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.bouncySpring
import kotlinx.datetime.*

@Composable
fun TasksScreen() {
    val colors = AmazeTheme.colors
    val tasks by AppState.tasks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("date") } // "date" or "priority"

    val baseFilteredTasks = when (filter) {
        "today" -> tasks.filter { it.dueDate == Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() }
        "pending" -> tasks.filter { !it.completed }
        "done" -> tasks.filter { it.completed }
        else -> tasks
    }

    val filteredTasks = remember(baseFilteredTasks, sortMode) {
        when (sortMode) {
            "priority" -> baseFilteredTasks.sortedWith(
                compareBy<HomeworkTask> { it.completed }
                    .thenByDescending {
                        when (it.priority) {
                            "high" -> 3
                            "medium" -> 2
                            "low" -> 1
                            else -> 0
                        }
                    }.thenBy { it.dueDate }
            )
            else -> baseFilteredTasks.sortedWith(
                compareBy<HomeworkTask> { it.completed }.thenBy { it.dueDate }
            )
        }
    }

    val pendingCount = tasks.count { !it.completed }
    val overdueCount = tasks.count {
        !it.completed && try {
            val d = it.dueDate.split("-").map { s -> s.toInt() }
            LocalDate(d[0], d[1], d[2]) < Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        } catch (_: Exception) { false }
    }

    val totalWorkloadMins = filteredTasks.filter { !it.completed }.sumOf { it.estimatedMinutes }
    val workloadText = if (totalWorkloadMins > 0) {
        val h = totalWorkloadMins / 60
        val m = totalWorkloadMins % 60
        if (h > 0) "${h}h ${m}m" else "${m}m"
    } else ""

    val tabs = listOf("all" to "All", "pending" to "Pending", "today" to "Today", "done" to "Done")

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = "Tasks & Reminders", description = "Workload, reminders and to-dos", showBackButton = true)

        Column(modifier = Modifier.fillMaxSize()) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            if (pendingCount > 0 || overdueCount > 0 || workloadText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.info.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("$pendingCount pending", style = AmazeTheme.typography.smallLabel.copy(color = colors.info, fontWeight = FontWeight.Bold))
                        }
                    }
                    if (overdueCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.danger.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("$overdueCount overdue", style = AmazeTheme.typography.smallLabel.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                        }
                    }
                    if (workloadText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.chart3.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Workload: $workloadText", style = AmazeTheme.typography.smallLabel.copy(color = colors.chart3, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Filter tabs & Sort
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { (key, label) ->
                        val isSelected = filter == key
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f, animationSpec = bouncySpring())

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accent else colors.surface)
                                .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                                .clickable(interactionSource = interactionSource, indication = null, onClick = { filter = key })
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = if (isSelected) colors.background else colors.textPrimary), maxLines = 1)
                        }
                    }
                }
                
                IconButton(
                    onClick = { sortMode = if (sortMode == "date") "priority" else "date" },
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.surface).border(1.dp, colors.border, CircleShape)
                ) {
                    Icon(
                        if (sortMode == "priority") Icons.Rounded.Sort else Icons.Rounded.CalendarToday,
                        contentDescription = "Sort by ${if (sortMode == "priority") "Date" else "Priority"}",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filteredTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.textMuted.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No tasks here", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary))
                        Text("Stay organized by adding assignments and reminders", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            colors = colors,
                            onToggle = { AppState.toggleTaskCompleted(task.id) },
                            onDelete = { AppState.deleteTask(task.id) }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddTaskDialog(onDismiss = { showAddDialog = false })
        }

        // FAB
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = colors.accent,
                contentColor = colors.background
            ) {
                Icon(Icons.Rounded.Add, "Add task")
            }
        }
    }
}

@Composable
fun TaskCard(
    task: HomeworkTask,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    showCourse: Boolean = true
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val typeColor = when (task.type) {
        "homework" -> colors.chart2
        "assignment" -> colors.chart3
        "test" -> colors.danger
        "self_study" -> colors.chart1
        else -> colors.chart4
    }
    
    val typeIcon = when (task.type) {
        "homework" -> Icons.Rounded.AssignmentTurnedIn
        "assignment" -> Icons.Rounded.Assignment
        "test" -> Icons.Rounded.Quiz
        "self_study" -> Icons.Rounded.MenuBook
        else -> Icons.Rounded.Task
    }
    
    val typeLabel = when (task.type) {
        "homework" -> "Homework"
        "assignment" -> "Assignment"
        "test" -> "Test"
        "self_study" -> "Self Study"
        else -> "Reminder"
    }
    
    val priorityColor = when (task.priority) {
        "high" -> colors.danger
        "medium" -> colors.chart3
        "low" -> colors.success
        else -> colors.textMuted
    }

    val isOverdue = !task.completed && try {
        val d = task.dueDate.split("-").map { s -> s.toInt() }
        LocalDate(d[0], d[1], d[2]) < Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    } catch (_: Exception) { false }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Delete Task") },
            text = { Text("Delete \"${task.title}\"?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = colors.textMuted) }
            }
        )
    }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onToggle() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (task.completed) colors.chart1.copy(alpha = 0.2f) else colors.border)
                    .border(2.dp, if (task.completed) colors.chart1 else priorityColor, CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.completed) {
                    Icon(Icons.Rounded.Check, null, tint = colors.chart1, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(typeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(10.dp))
                                Text(typeLabel, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = typeColor, fontSize = 9.sp))
                            }
                        }
                        
                        if (task.estimatedMinutes > 0 && !task.completed) {
                            val h = task.estimatedMinutes / 60
                            val m = task.estimatedMinutes % 60
                            val estStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.accent.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.Timer, null, tint = colors.accent, modifier = Modifier.size(10.dp))
                                    Text(estStr, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = 9.sp))
                                }
                            }
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Delete, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    task.title,
                    style = AmazeTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (task.completed) colors.textMuted else colors.textPrimary,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val dueLabel = try {
                        val d = task.dueDate.split("-").map { s -> s.toInt() }
                        val date = LocalDate(d[0], d[1], d[2])
                        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                        val diff = today.until(date, DateTimeUnit.DAY)
                        val monthsSince = today.until(date, DateTimeUnit.MONTH)
                        when {
                            diff == 0 -> "Due today"
                            diff == 1 -> "Due tomorrow"
                            diff == -1 -> "Due yesterday"
                            diff > 0 && diff <= 7 -> "Due in $diff days"
                            diff < 0 && diff >= -7 -> "${-diff} days overdue"
                            monthsSince == 1 -> "Due tomorrow"
                            monthsSince == -1 -> "Due yesterday"
                            diff > 0 -> date.toString()
                            else -> date.toString()
                        }
                    } catch (_: Exception) { task.dueDate }

                    Text(
                        dueLabel,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isOverdue) colors.danger else colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    if (showCourse && task.courseTitle.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.border)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(task.courseCode, style = AmazeTheme.typography.smallLabel.copy(fontSize = 9.sp, color = colors.textMuted))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit) {
    val colors = AmazeTheme.colors
    val courseCodes = remember { AppState.attendance.value?.attendance?.map { it.courseCode to it.courseTitle }?.distinct()?.sortedBy { it.first } ?: emptyList() }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCourseCode by remember { mutableStateOf("") }
    var selectedCourseTitle by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("homework") }
    var priority by remember { mutableStateOf("medium") }
    var estimatedMinutesStr by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var courseExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("New Task", style = AmazeTheme.typography.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Black, color = colors.textPrimary))

                // Type Chips
                val types = listOf("homework" to "Homework", "assignment" to "Assignment", "test" to "Test", "self_study" to "Learn")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { (key, label) ->
                        val sel = taskType == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) colors.accent else colors.background)
                                .border(1.dp, if (sel) colors.accent else colors.border, RoundedCornerShape(8.dp))
                                .clickable { taskType = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = if (sel) colors.background else colors.textPrimary, fontSize = 10.sp), maxLines = 1)
                        }
                    }
                }

                // Title & Desc
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textMuted,
                        cursorColor = colors.accent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textMuted,
                        cursorColor = colors.accent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Est Minutes
                    OutlinedTextField(
                        value = estimatedMinutesStr,
                        onValueChange = { if (it.all { char -> char.isDigit() }) estimatedMinutesStr = it },
                        label = { Text("Est. Mins") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("60", color = colors.textMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textMuted,
                            cursorColor = colors.accent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                        )
                    )

                    // Due Date
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textMuted,
                            cursorColor = colors.accent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                        )
                    )
                }

                // Priority Chips
                Text("Priority", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary))
                val priorities = listOf("high" to "High", "medium" to "Medium", "low" to "Low")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    priorities.forEach { (key, label) ->
                        val sel = priority == key
                        val pColor = when(key) { "high" -> colors.danger; "medium" -> colors.chart3; else -> colors.success }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) pColor else colors.background)
                                .border(1.dp, if (sel) pColor else colors.border, RoundedCornerShape(8.dp))
                                .clickable { priority = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = if (sel) Color.White else colors.textPrimary), maxLines = 1)
                        }
                    }
                }

                // Course selector
                ExposedDropdownMenuBox(
                    expanded = courseExpanded,
                    onExpandedChange = { courseExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedCourseCode.isNotEmpty()) "$selectedCourseCode - $selectedCourseTitle" else "Select course (optional)",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textMuted,
                            cursorColor = colors.accent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = courseExpanded,
                        onDismissRequest = { courseExpanded = false }
                    ) {
                        courseCodes.forEach { (code, ct) ->
                            DropdownMenuItem(
                                text = { Text("$code - $ct", color = colors.textPrimary) },
                                onClick = {
                                    selectedCourseCode = code
                                    selectedCourseTitle = ct
                                    courseExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                AppState.addTask(
                                    HomeworkTask(
                                        id = "task_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}",
                                        courseCode = selectedCourseCode,
                                        courseTitle = selectedCourseTitle,
                                        title = title,
                                        description = description,
                                        dueDate = dueDate,
                                        type = taskType,
                                        priority = priority,
                                        estimatedMinutes = estimatedMinutesStr.toIntOrNull() ?: 0,
                                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
                                    )
                                )
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, disabledContainerColor = colors.border),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create Task", color = if (title.isNotBlank()) colors.background else colors.textMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CourseTasksTab(courseCodes: List<String>, courseTitle: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val tasks by AppState.tasks.collectAsState()
    val courseTasks = tasks.filter { it.courseCode in courseCodes }.sortedBy { it.dueDate }
    var showAdd by remember { mutableStateOf(false) }
    val spacing = AmazeTheme.spacing

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.pageHorizontal)) {
        Spacer(Modifier.height(spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tasks & Reminders",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            AmazeButton(
                "Add",
                onClick = { showAdd = true },
                variant = com.amazecc.app.shared.ui.components.ButtonVariant.SECONDARY
            )
        }

        Spacer(Modifier.height(spacing.sm))

        if (courseTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.textMuted.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No tasks for this course", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(courseTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        colors = colors,
                        onToggle = { AppState.toggleTaskCompleted(task.id) },
                        onDelete = { AppState.deleteTask(task.id) },
                        showCourse = false
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(onDismiss = { showAdd = false })
    }
}
