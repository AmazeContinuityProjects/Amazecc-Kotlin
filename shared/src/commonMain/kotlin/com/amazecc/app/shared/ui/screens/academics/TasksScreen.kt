package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.Subtask
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.bouncySpring
import kotlinx.coroutines.delay
import kotlinx.datetime.*

data class CourseOption(
    val code: String,
    val title: String
) {
    val displayLabel: String get() = if (title.isNotBlank()) "$code - $title" else code
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen() {
    val colors = AmazeTheme.colors
    val tasks by AppState.tasks.collectAsState()
    val attendance by AppState.attendance.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<HomeworkTask?>(null) }
    var focusTask by remember { mutableStateOf<HomeworkTask?>(null) }

    var selectedViewMode by remember { mutableStateOf("list") } // "list", "kanban", "workload"
    var filter by remember { mutableStateOf("all") } // "all", "pending", "today", "done", "lms"
    var sortMode by remember { mutableStateOf("date") } // "date", "priority"

    val todayStr = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() }

    val courseOptions = remember(attendance) {
        val options = attendance?.attendance?.map { CourseOption(it.courseCode, it.courseTitle) }?.distinctBy { it.code } ?: emptyList()
        if (options.isEmpty()) listOf(
            CourseOption("GENERAL", "General Academic Task"),
            CourseOption("CSE1001", "Problem Solving and Programming"),
            CourseOption("ECE2002", "Digital Logic Design"),
            CourseOption("MAT3001", "Advanced Multivariable Calculus")
        ) else options
    }

    val baseFiltered = when (filter) {
        "today" -> tasks.filter { it.dueDate == todayStr }
        "pending" -> tasks.filter { !it.completed }
        "done" -> tasks.filter { it.completed }
        "lms" -> tasks.filter { it.isAutoSynced }
        else -> tasks
    }

    val filteredTasks = remember(baseFiltered, sortMode) {
        when (sortMode) {
            "priority" -> baseFiltered.sortedWith(
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
            else -> baseFiltered.sortedWith(
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

    val totalWorkloadMins = tasks.filter { !it.completed }.sumOf { it.estimatedMinutes }
    val workloadText = if (totalWorkloadMins > 0) {
        val h = totalWorkloadMins / 60
        val m = totalWorkloadMins % 60
        if (h > 0) "${h}h ${m}m" else "${m}m"
    } else "0m"

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp + BOTTOM_NAV_PADDING)
            ) {
                // Top Summary & Workload Metrics Card
                item(key = "metrics_card") {
                    AmazeCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.TaskAlt, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Academic Workload", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text("$pendingCount pending • Total Est. $workloadText", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }

                                if (overdueCount > 0) {
                                    Surface(
                                        color = colors.danger.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "$overdueCount OVERDUE",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = AmazeTheme.typography.smallLabel.copy(color = colors.danger, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.micro)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // View Mode Segmented Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.background.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ViewTabButton("list", "List View", Icons.Rounded.List, selectedViewMode == "list", colors, Modifier.weight(1f)) { selectedViewMode = "list" }
                                ViewTabButton("kanban", "Kanban", Icons.Rounded.ViewColumn, selectedViewMode == "kanban", colors, Modifier.weight(1f)) { selectedViewMode = "kanban" }
                                ViewTabButton("workload", "Workload", Icons.Rounded.Analytics, selectedViewMode == "workload", colors, Modifier.weight(1f)) { selectedViewMode = "workload" }
                            }
                        }
                    }
                }

                // Render Content Based on Selected View Mode
                when (selectedViewMode) {
                    "kanban" -> {
                        item(key = "kanban_content") {
                            KanbanBoardContent(
                                tasks = tasks,
                                colors = colors,
                                onToggleTask = { AppState.toggleTaskCompleted(it) },
                                onEditTask = { editingTask = it; showBottomSheet = true },
                                onDeleteTask = { AppState.deleteTask(it) }
                            )
                        }
                    }
                    "workload" -> {
                        item(key = "workload_content") {
                            WorkloadDensityContent(
                                tasks = tasks,
                                colors = colors,
                                todayStr = todayStr
                            )
                        }
                    }
                    else -> {
                        // Filters & Sort Bar
                        item(key = "filters_bar") {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(
                                            listOf(
                                                "all" to "All (${tasks.size})",
                                                "pending" to "Pending ($pendingCount)",
                                                "today" to "Today",
                                                "done" to "Done",
                                                "lms" to "LMS Auto"
                                            ),
                                            key = { it.first }
                                        ) { (key, label) ->
                                            FilterChip(
                                                selected = filter == key,
                                                onClick = { filter = key },
                                                label = { Text(label, fontSize = AmazeTheme.fontSize.xs, fontWeight = if (filter == key) FontWeight.Bold else FontWeight.Normal) }
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { sortMode = if (sortMode == "date") "priority" else "date" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.Sort,
                                            "Sort",
                                            tint = if (sortMode == "priority") colors.accent else colors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Task Items List
                        if (filteredTasks.isEmpty()) {
                            item(key = "empty_state") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.CheckCircleOutline, null, tint = colors.success.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(10.dp))
                                        Text("No tasks found", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text("Tap '+' below to add a new homework or task", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                            }
                        } else {
                            items(filteredTasks, key = { it.id }) { task ->
                                TaskItemCard(
                                    task = task,
                                    colors = colors,
                                    onToggle = { AppState.toggleTaskCompleted(task.id) },
                                    onToggleSubtask = { subId -> AppState.toggleSubtaskCompleted(task.id, subId) },
                                    onStartFocus = { focusTask = task },
                                    onEdit = { editingTask = task; showBottomSheet = true },
                                    onDelete = { AppState.deleteTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Add Task Button (Bottom Right)
        FloatingActionButton(
            onClick = { editingTask = null; showBottomSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 85.dp + BOTTOM_NAV_PADDING, end = 20.dp),
            containerColor = colors.accent,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Add, "Add Task", modifier = Modifier.size(26.dp))
        }
    }

    // Modal Bottom Sheet Pane arriving smoothly from the bottom!
    if (showBottomSheet) {
        AddTaskBottomSheet(
            taskToEdit = editingTask,
            courseOptions = courseOptions,
            colors = colors,
            onDismiss = { showBottomSheet = false },
            onSave = { task ->
                if (editingTask != null) {
                    AppState.updateTask(task.id) { task }
                } else {
                    AppState.addTask(task)
                }
                showBottomSheet = false
            }
        )
    }

    // Pomodoro Focus Timer Modal
    if (focusTask != null) {
        AmazeFocusTimerModal(
            task = focusTask!!,
            colors = colors,
            onDismiss = { focusTask = null },
            onSessionComplete = { minutes ->
                AppState.addFocusTime(focusTask!!.id, minutes)
                focusTask = null
            }
        )
    }
}

// ── View Tab Button ──
@Composable
private fun ViewTabButton(
    key: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isSelected) colors.accent else colors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = AmazeTheme.fontSize.xs, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) colors.textPrimary else colors.textMuted)
        }
    }
}

// ── Task Item Card with Subtasks & Focus Button ──
@Composable
private fun TaskItemCard(
    task: HomeworkTask,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onToggle: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
) {
    var expandedSubtasks by remember { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        "high" -> colors.danger
        "medium" -> colors.warning
        else -> colors.textMuted
    }

    AmazeCard(
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.completed,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.success,
                        uncheckedColor = colors.textMuted
                    ),
                    modifier = Modifier.scale(0.85f)
                )

                Spacer(Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.courseCode,
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                fontSize = AmazeTheme.fontSize.micro
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = task.type.uppercase(),
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                        if (task.isAutoSynced) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LMS AUTO",
                                style = AmazeTheme.typography.smallLabel.copy(
                                    fontWeight = FontWeight.Black,
                                    color = colors.info,
                                    fontSize = AmazeTheme.fontSize.micro
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.info.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = task.title,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (task.completed) colors.textMuted else colors.textPrimary,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )

                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(priorityColor)
                )
            }

            // Subtask Progress Bar (if subtasks exist)
            if (task.subtasks.isNotEmpty()) {
                val doneSub = task.subtasks.count { it.completed }
                val totalSub = task.subtasks.size
                val subProgress = doneSub.toFloat() / totalSub.toFloat()

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expandedSubtasks = !expandedSubtasks },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (expandedSubtasks) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Subtasks ($doneSub/$totalSub)",
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = AmazeTheme.fontSize.xs)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { subProgress },
                        modifier = Modifier.width(100.dp).height(4.dp).clip(CircleShape),
                        color = colors.success,
                        trackColor = colors.background
                    )
                }

                if (expandedSubtasks) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp)) {
                        task.subtasks.forEach { sub ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onToggleSubtask(sub.id) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sub.completed,
                                    onCheckedChange = { onToggleSubtask(sub.id) },
                                    modifier = Modifier.scale(0.75f),
                                    colors = CheckboxDefaults.colors(checkedColor = colors.success)
                                )
                                Text(
                                    sub.title,
                                    style = AmazeTheme.typography.caption.copy(
                                        color = if (sub.completed) colors.textMuted else colors.textPrimary,
                                        textDecoration = if (sub.completed) TextDecoration.LineThrough else TextDecoration.None,
                                        fontSize = AmazeTheme.fontSize.xs
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Footer Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Due ${task.dueDate}", style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))

                    if (task.estimatedMinutes > 0) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.Timer, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "${task.actualMinutesSpent}/${task.estimatedMinutes}m",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!task.completed) {
                        IconButton(onClick = onStartFocus, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.PlayCircle, "Focus Session", tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Edit, "Edit Task", tint = colors.textMuted, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Delete, "Delete Task", tint = colors.danger, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

// ── Kanban Board View Content ──
@Composable
private fun KanbanBoardContent(
    tasks: List<HomeworkTask>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onToggleTask: (String) -> Unit,
    onEditTask: (HomeworkTask) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    val toDo = tasks.filter { !it.completed && it.actualMinutesSpent == 0 }
    val inProgress = tasks.filter { !it.completed && it.actualMinutesSpent > 0 }
    val completed = tasks.filter { it.completed }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        KanbanColumn("To Do (${toDo.size})", toDo, colors.warning, colors, onToggleTask, onEditTask, onDeleteTask)
        Spacer(Modifier.height(12.dp))
        KanbanColumn("In Progress (${inProgress.size})", inProgress, colors.accent, colors, onToggleTask, onEditTask, onDeleteTask)
        Spacer(Modifier.height(12.dp))
        KanbanColumn("Completed (${completed.size})", completed, colors.success, colors, onToggleTask, onEditTask, onDeleteTask)
    }
}

@Composable
private fun KanbanColumn(
    title: String,
    list: List<HomeworkTask>,
    badgeColor: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onToggleTask: (String) -> Unit,
    onEditTask: (HomeworkTask) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(badgeColor))
                Spacer(Modifier.width(8.dp))
                Text(title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base))
            }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Text("No tasks", style = AmazeTheme.typography.caption.copy(color = colors.textMuted), modifier = Modifier.padding(vertical = 8.dp))
            } else {
                list.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.background.copy(alpha = 0.5f))
                            .clickable { onEditTask(task) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary))
                            Text("${task.courseCode} • Due ${task.dueDate}", style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary))
                        }
                        IconButton(onClick = { onToggleTask(task.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (task.completed) colors.success else colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Workload Density Content ──
@Composable
private fun WorkloadDensityContent(
    tasks: List<HomeworkTask>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    todayStr: String
) {
    val pendingTasks = tasks.filter { !it.completed }
    val groupedByDate = pendingTasks.groupBy { it.dueDate }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("7-Day Workload Density", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(8.dp))

        val days = (0..6).map { offset ->
            try {
                val d = Clock.System.now().plus(offset, DateTimeUnit.DAY, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault()).date
                d.toString()
            } catch (_: Exception) { todayStr }
        }

        days.forEach { date ->
            val dayTasks = groupedByDate[date] ?: emptyList()
            val totalMins = dayTasks.sumOf { it.estimatedMinutes }
            val densityColor = when {
                totalMins > 180 -> colors.danger
                totalMins > 60 -> colors.warning
                totalMins > 0 -> colors.success
                else -> colors.textMuted
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).border(1.dp, colors.border, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (date == todayStr) "Today ($date)" else date, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm))
                        Text("${dayTasks.size} tasks assigned", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${totalMins}m est.", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = densityColor, fontSize = AmazeTheme.fontSize.sm))
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(densityColor))
                    }
                }
            }
        }
    }
}

// ── Modal Bottom Sheet Add/Edit Task Pane ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskBottomSheet(
    taskToEdit: HomeworkTask?,
    courseOptions: List<CourseOption>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onDismiss: () -> Unit,
    onSave: (HomeworkTask) -> Unit
) {
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var selectedCourse by remember { mutableStateOf(taskToEdit?.courseCode ?: courseOptions.firstOrNull()?.code ?: "GENERAL") }
    var type by remember { mutableStateOf(taskToEdit?.type ?: "homework") }
    var priority by remember { mutableStateOf(taskToEdit?.priority ?: "medium") }
    var dueDate by remember { mutableStateOf(taskToEdit?.dueDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
    var estimatedMinsText by remember { mutableStateOf(taskToEdit?.estimatedMinutes?.toString() ?: "30") }

    var subtaskInput by remember { mutableStateOf("") }
    var subtasksList by remember { mutableStateOf(taskToEdit?.subtasks ?: emptyList()) }
    var expandedCourseDropdown by remember { mutableStateOf(false) }

    val currentCourseOpt = remember(selectedCourse, courseOptions) {
        courseOptions.firstOrNull { it.code == selectedCourse } ?: courseOptions.firstOrNull() ?: CourseOption(selectedCourse, selectedCourse)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedContainerColor = colors.background.copy(alpha = 0.5f),
        unfocusedContainerColor = colors.background.copy(alpha = 0.3f),
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (taskToEdit == null) "New Task / Homework" else "Edit Task",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Close", tint = colors.textMuted)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Descriptive Course Selector Dropdown
            Text("COURSE", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.background.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .clickable { expandedCourseDropdown = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentCourseOpt.displayLabel,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Rounded.ArrowDropDown, "Select Course", tint = colors.accent)
                    }
                }

                DropdownMenu(
                    expanded = expandedCourseDropdown,
                    onDismissRequest = { expandedCourseDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .background(colors.surface)
                ) {
                    courseOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(opt.code, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
                                    if (opt.title.isNotBlank()) {
                                        Text(opt.title, color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            },
                            onClick = {
                                selectedCourse = opt.code
                                expandedCourseDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Title & Description Fields
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title", color = colors.textSecondary) },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)", color = colors.textSecondary) },
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Type Selector
            Text("TYPE", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("homework" to "Homework", "quiz" to "Quiz", "exam" to "Exam", "lab" to "Lab", "project" to "Project").forEach { (key, label) ->
                    FilterChip(
                        selected = type == key,
                        onClick = { type = key },
                        label = { Text(label, fontSize = AmazeTheme.fontSize.xs) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Priority Selector
            Text("PRIORITY", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("high" to "🔥 High", "medium" to "⚡ Medium", "low" to "🌱 Low").forEach { (key, label) ->
                    FilterChip(
                        selected = priority == key,
                        onClick = { priority = key },
                        label = { Text(label, fontSize = AmazeTheme.fontSize.xs) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Due Date & Estimated Minutes
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)", color = colors.textSecondary) },
                    colors = textFieldColors,
                    modifier = Modifier.weight(1.5f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = estimatedMinsText,
                    onValueChange = { estimatedMinsText = it },
                    label = { Text("Est. Mins", color = colors.textSecondary) },
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(14.dp))

            // Subtasks Checklist Builder
            Text("SUBTASKS CHECKLIST", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = subtaskInput,
                    onValueChange = { subtaskInput = it },
                    label = { Text("Add step...", color = colors.textSecondary) },
                    colors = textFieldColors,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (subtaskInput.isNotBlank()) {
                            val newSub = Subtask("sub_" + Clock.System.now().toEpochMilliseconds(), subtaskInput.trim())
                            subtasksList = subtasksList + newSub
                            subtaskInput = ""
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.AddCircle, "Add Subtask", tint = colors.accent)
                }
            }

            if (subtasksList.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    subtasksList.forEach { sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${sub.title}", style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm))
                            IconButton(onClick = { subtasksList = subtasksList.filter { it.id != sub.id } }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, "Remove", tint = colors.danger, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Save Task Button
            AmazeButton(
                text = if (taskToEdit == null) "Create Task" else "Save Changes",
                onClick = {
                    if (title.isNotBlank()) {
                        val est = estimatedMinsText.toIntOrNull() ?: 30
                        val newTask = HomeworkTask(
                            id = taskToEdit?.id ?: ("task_" + Clock.System.now().toEpochMilliseconds()),
                            courseCode = selectedCourse,
                            courseTitle = selectedCourse,
                            title = title.trim(),
                            description = description.trim(),
                            dueDate = dueDate.trim(),
                            type = type,
                            priority = priority,
                            estimatedMinutes = est,
                            completed = taskToEdit?.completed ?: false,
                            subtasks = subtasksList,
                            createdAt = taskToEdit?.createdAt ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
                        )
                        onSave(newTask)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Pomodoro Focus Timer Modal Overlay ──
@Composable
private fun AmazeFocusTimerModal(
    task: HomeworkTask,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onDismiss: () -> Unit,
    onSessionComplete: (Int) -> Unit
) {
    var timerSeconds by remember { mutableStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var elapsedMins by remember { mutableStateOf(0) }

    LaunchedEffect(isRunning) {
        while (isRunning && timerSeconds > 0) {
            delay(1000L)
            timerSeconds--
            if (timerSeconds % 60 == 0) elapsedMins++
        }
        if (timerSeconds == 0 && isRunning) {
            isRunning = false
            onSessionComplete(25)
        }
    }

    val mins = timerSeconds / 60
    val secs = timerSeconds % 60
    val timeFormatted = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    val progress = 1f - (timerSeconds.toFloat() / (25f * 60f))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, colors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Focus Session", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Close", tint = colors.textMuted)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(task.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                Text(task.courseCode, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))

                Spacer(Modifier.height(20.dp))

                // Progress Circle & Timer Text
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = colors.accent,
                        strokeWidth = 8.dp,
                        trackColor = colors.accent.copy(alpha = 0.15f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(timeFormatted, style = AmazeTheme.typography.heading.copy(fontSize = AmazeTheme.fontSize.x3l, fontWeight = FontWeight.Black, color = colors.textPrimary))
                        Text(if (isRunning) "FOCUSING" else "PAUSED", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Controls
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmazeButton(
                        text = if (isRunning) "Pause" else "Start",
                        onClick = { isRunning = !isRunning },
                        modifier = Modifier.width(110.dp).height(40.dp)
                    )
                    AmazeButton(
                        text = "Finish & Save",
                        variant = ButtonVariant.SECONDARY,
                        onClick = {
                            onSessionComplete(if (elapsedMins > 0) elapsedMins else 1)
                        },
                        modifier = Modifier.width(120.dp).height(40.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit
) {
    val attendance by AppState.attendance.collectAsState()
    val courseOptions = remember(attendance) {
        val options = attendance?.attendance?.map { CourseOption(it.courseCode, it.courseTitle) }?.distinctBy { it.code } ?: emptyList()
        if (options.isEmpty()) listOf(
            CourseOption("GENERAL", "General Academic Task"),
            CourseOption("CSE1001", "Problem Solving and Programming"),
            CourseOption("ECE2002", "Digital Logic Design"),
            CourseOption("MAT3001", "Advanced Multivariable Calculus")
        ) else options
    }
    val colors = AmazeTheme.colors

    AddTaskBottomSheet(
        taskToEdit = null,
        courseOptions = courseOptions,
        colors = colors,
        onDismiss = onDismiss,
        onSave = { task ->
            AppState.addTask(task)
            onDismiss()
        }
    )
}

@Composable
fun TaskCard(
    task: HomeworkTask,
    colors: com.amazecc.app.shared.theme.AmazeColors = AmazeTheme.colors,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    showCourse: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
) {
    TaskItemCard(
        task = task,
        colors = colors,
        onToggle = onToggle,
        onToggleSubtask = { AppState.toggleSubtaskCompleted(task.id, it) },
        onStartFocus = {},
        onEdit = {},
        onDelete = onDelete,
        modifier = modifier
    )
}

@Composable
fun CourseTasksTab(
    courseCodes: List<String>,
    courseTitle: String = "",
    colors: com.amazecc.app.shared.theme.AmazeColors = AmazeTheme.colors
) {
    val tasks by AppState.tasks.collectAsState()
    val courseTasks = remember(tasks, courseCodes) {
        tasks.filter { t -> courseCodes.any { c -> t.courseCode.equals(c, ignoreCase = true) } }
    }

    if (courseTasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No tasks for $courseTitle", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            courseTasks.forEach { task ->
                TaskCard(
                    task = task,
                    colors = colors,
                    onToggle = { AppState.toggleTaskCompleted(task.id) },
                    onDelete = { AppState.deleteTask(task.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }
    }
}
