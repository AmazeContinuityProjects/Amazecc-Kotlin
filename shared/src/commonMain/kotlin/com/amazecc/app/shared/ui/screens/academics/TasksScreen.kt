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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.amazecc.app.shared.model.WorkSession
import com.mikepenz.markdown.m3.Markdown
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazePill
import com.amazecc.app.shared.ui.components.DatePickerSheet
import com.amazecc.app.shared.ui.components.PickerField
import com.amazecc.app.shared.ui.components.ReminderPickerSheet
import com.amazecc.app.shared.ui.components.TimePickerSheet
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.SheetHeaderRow
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.HeroCard
import com.amazecc.app.shared.ui.components.HeroChip
import com.amazecc.app.shared.ui.components.HeroPalette
import com.amazecc.app.shared.ui.components.HeroPanel
import com.amazecc.app.shared.ui.components.HeroStat
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

    var selectedViewMode by remember { mutableStateOf("list") } // "list", "kanban", "workload", "calendar"
    var filter by remember { mutableStateOf("all") } // "all", "pending", "today", "done", "lms"
    var courseFilter by remember { mutableStateOf<String?>(null) } // null = all courses
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
    }.let { filtered ->
        if (courseFilter == null) filtered else filtered.filter { it.courseCode == courseFilter }
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
    val todayCount = tasks.count { !it.completed && it.dueDate == todayStr }

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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp + BOTTOM_NAV_PADDING),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero — summary card in the course-detail design language
                item(key = "hero_card") {
                    TasksHeroCard(
                        pendingCount = pendingCount,
                        overdueCount = overdueCount,
                        todayCount = todayCount,
                        totalCount = tasks.size,
                        completedCount = tasks.count { it.completed },
                        workloadText = workloadText,
                        selectedViewMode = selectedViewMode,
                        onViewModeChange = { selectedViewMode = it },
                        colors = colors
                    )
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
                    "calendar" -> {
                        item(key = "calendar_content") {
                            CalendarViewContent(
                                tasks = tasks,
                                colors = colors,
                                todayStr = todayStr,
                                onToggle = { AppState.toggleTaskCompleted(it) },
                                onEditTask = { editingTask = it; showBottomSheet = true },
                                onDeleteTask = { AppState.deleteTask(it) }
                            )
                        }
                    }
                    else -> {
                        // Filters & Sort Bar
                        item(key = "filters_bar") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                            FilterPill(
                                                label = label,
                                                selected = filter == key,
                                                colors = colors
                                            ) { filter = key }
                                        }
                                    }

                                    if (courseOptions.size > 1) {
                                        var courseMenuOpen by remember { mutableStateOf(false) }
                                        Box {
                                            FilterPill(
                                                label = if (courseFilter != null) courseFilter!! else "By Course",
                                                selected = courseFilter != null,
                                                colors = colors
                                            ) { courseMenuOpen = true }
                                            DropdownMenu(
                                                expanded = courseMenuOpen,
                                                onDismissRequest = { courseMenuOpen = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("All Courses", fontSize = AmazeTheme.fontSize.sm) },
                                                    onClick = { courseFilter = null; courseMenuOpen = false }
                                                )
                                                courseOptions.forEach { opt ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Column {
                                                                Text(opt.code, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                                                                if (opt.title.isNotBlank()) {
                                                                    Text(opt.title, color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                        },
                                                        onClick = { courseFilter = opt.code; courseMenuOpen = false }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                            .background(if (sortMode == "priority") colors.accent.copy(alpha = 0.15f) else colors.surface)
                                            .border(1.dp, if (sortMode == "priority") colors.accent.copy(alpha = 0.4f) else colors.textMuted.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.small))
                                            .clickable { sortMode = if (sortMode == "date") "priority" else "date" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.Sort,
                                            "Sort",
                                            tint = if (sortMode == "priority") colors.accent else colors.textMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Task Items List — grouped into collapsible course sections
                        if (filteredTasks.isEmpty()) {
                            item(key = "empty_state") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(colors.accent.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.CheckCircleOutline, null, tint = colors.accent, modifier = Modifier.size(32.dp))
                                        }
                                        Spacer(Modifier.height(AmazeTheme.spacing.md))
                                        Text("No tasks found", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                        Text("Tap the + button below to add a homework, exam reminder or task", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                            }
                        } else {
                            items(filteredTasks, key = { it.id }) { task ->
                                AmazeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    TaskItemCard(
                                        task = task,
                                        colors = colors,
                                        tint = taskTypeTint(task.type, colors),
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

// ── View Tab Button (chip on the hero card) ──
@Composable
private fun ViewTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    p: HeroPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
            .background(if (isSelected) p.text else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (isSelected) AmazeTheme.colors.accent else p.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                fontSize = AmazeTheme.fontSize.xs,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AmazeTheme.colors.accent else p.textSecondary
            )
        }
    }
}

// ── Task Item Card with Subtasks & Focus Button (exam-schedule row language) ──
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskItemCard(
    task: HomeworkTask,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    tint: Color,
    onToggle: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(false) }

    val taskOverdue = isTaskOverdue(task)
    val dueToday = !task.completed && task.dueDate == todayString()
    val status: Pair<String, Color>? = when {
        task.completed -> "DONE" to colors.success
        taskOverdue -> "OVERDUE" to colors.danger
        dueToday -> "TODAY" to colors.warning
        else -> null
    }
    val iconTint = when {
        task.completed -> colors.success
        taskOverdue -> colors.danger
        else -> tint
    }
    val priorityColor = when (task.priority) {
        "high" -> colors.danger
        "medium" -> colors.warning
        else -> colors.info
    }
    val doneSub = task.subtasks.count { it.completed }
    val totalSub = task.subtasks.size

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Main Row: 40dp icon circle · code + title + meta · status chip · chevron
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f))
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (task.completed) Icons.Rounded.CheckCircle else taskTypeIcon(task.type),
                null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(AmazeTheme.spacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.courseCode,
                    style = AmazeTheme.typography.smallLabel.copy(color = iconTint, fontWeight = FontWeight.Bold)
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
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(colors.info.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (task.showOnCalendar) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.CalendarMonth, null, tint = colors.accent, modifier = Modifier.size(12.dp))
                }
                if (task.showOnTimetable) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.TableRows, null, tint = colors.accent, modifier = Modifier.size(12.dp))
                }
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = task.title,
                style = AmazeTheme.typography.body.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (task.completed) colors.textMuted else colors.textPrimary,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = task.description,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(4.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 2
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Icon(
                        Icons.Rounded.Schedule,
                        null,
                        tint = if (taskOverdue) colors.danger else colors.textMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Due ${task.dueDate}",
                        style = AmazeTheme.typography.caption.copy(
                            color = if (taskOverdue) colors.danger else colors.textMuted,
                            fontSize = AmazeTheme.fontSize.micro,
                            fontWeight = if (taskOverdue) FontWeight.Bold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (task.estimatedMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Icon(Icons.Rounded.Timer, null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "${task.actualMinutesSpent}/${task.estimatedMinutes}m",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (task.reminderAt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Icon(Icons.Rounded.Alarm, null, tint = colors.warning, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            task.reminderAt,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (status != null) {
                TaskStatusChip(text = status.first, color = status.second)
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(priorityColor)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    task.priority.uppercase(),
                    style = AmazeTheme.typography.smallLabel.copy(
                        fontWeight = FontWeight.Black,
                        color = priorityColor,
                        fontSize = AmazeTheme.fontSize.micro
                    )
                )
            }
        }

        Spacer(Modifier.width(6.dp))
        Icon(
            if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }

    // ── Expanded: details, subtasks & actions
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskMetricTile("Priority", task.priority.uppercase(), priorityColor, colors, Modifier.weight(1f))
                TaskMetricTile("Estimated", "${task.estimatedMinutes}m", colors.textPrimary, colors, Modifier.weight(1f))
                TaskMetricTile("Spent", "${task.actualMinutesSpent}m", colors.textSecondary, colors, Modifier.weight(1f))
            }

            if (task.odHours > 0 || task.workSessions.isNotEmpty() || task.reminderAt != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.reminderAt != null) {
                        TaskMetricTile("Reminder", task.reminderAt, colors.warning, colors, Modifier.weight(1f))
                    }
                    if (task.odHours > 0) {
                        TaskMetricTile("OD Hours", "${task.odHours}h", colors.info, colors, Modifier.weight(1f))
                    }
                    if (task.workSessions.isNotEmpty()) {
                        TaskMetricTile("Sessions", "${task.workSessions.size} planned", colors.success, colors, Modifier.weight(1f))
                    }
                }
            }

            if (task.subtasks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Checklist, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Subtasks ($doneSub/$totalSub)",
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = AmazeTheme.fontSize.xs)
                    )
                    Spacer(Modifier.weight(1f))
                    LinearProgressIndicator(
                        progress = { if (totalSub > 0) doneSub.toFloat() / totalSub else 0f },
                        modifier = Modifier.width(100.dp).height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                        color = colors.success,
                        trackColor = colors.textMuted.copy(alpha = 0.2f)
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                    task.subtasks.forEach { sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleSubtask(sub.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (sub.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = if (sub.completed) colors.success else colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!task.completed) {
                    AmazeButton(
                        text = "Focus",
                        variant = ButtonVariant.SECONDARY,
                        icon = Icons.Rounded.PlayCircle,
                        onClick = onStartFocus,
                        modifier = Modifier.height(36.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                AmazeButton(
                    text = "Edit",
                    variant = ButtonVariant.SECONDARY,
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                    modifier = Modifier.height(36.dp)
                )
                Spacer(Modifier.width(8.dp))
                AmazeButton(
                    text = "Delete",
                    variant = ButtonVariant.DANGER,
                    icon = Icons.Rounded.Delete,
                    onClick = onDelete,
                    modifier = Modifier.height(36.dp)
                )
            }
        }
    }
    }
}

private fun taskTypeIcon(type: String): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    "assignment" -> Icons.Rounded.EditNote
    "quiz" -> Icons.Rounded.Quiz
    "exam" -> Icons.Rounded.EventNote
    "lab" -> Icons.Rounded.Science
    "project" -> Icons.Rounded.Widgets
    "lms_auto" -> Icons.Rounded.CloudSync
    else -> Icons.AutoMirrored.Rounded.MenuBook
}

private fun taskTypeTint(type: String, colors: com.amazecc.app.shared.theme.AmazeColors): Color = when (type) {
    "exam" -> colors.chart3
    "quiz" -> colors.warning
    "lab" -> colors.chart2
    "project" -> colors.chart1
    "assignment" -> colors.chart4
    "lms_auto" -> colors.info
    else -> colors.accent
}

private fun todayString(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

@Composable
private fun TaskStatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = AmazeTheme.typography.smallLabel.copy(
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = AmazeTheme.fontSize.micro
            )
        )
    }
}

@Composable
private fun TaskMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
            .padding(8.dp)
    ) {
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = valueColor, fontSize = AmazeTheme.fontSize.base),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

    Column(modifier = Modifier.fillMaxWidth()) {
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
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(badgeColor))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base))
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${list.size}", color = badgeColor, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(colors.background.copy(alpha = 0.5f))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tasks in column", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                }
            } else {
                list.forEach { task ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.96f else 1f,
                        animationSpec = bouncySpring()
                    )

                    val priorityColor = when (task.priority) {
                        "high" -> colors.danger
                        "medium" -> colors.warning
                        else -> colors.info
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                            .background(colors.surface)
                            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.medium))
                            .clickable(interactionSource = interactionSource, indication = null) { onEditTask(task) }
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        task.courseCode,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.accent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                            .background(colors.accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text(
                                        task.priority.uppercase(),
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = priorityColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                            .background(priorityColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                IconButton(onClick = { onToggleTask(task.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        null,
                                        tint = if (task.completed) colors.success else colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                task.title,
                                style = AmazeTheme.typography.body.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AmazeTheme.fontSize.sm,
                                    color = colors.textPrimary
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Due ${task.dueDate}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro)
                                )
                                if (task.estimatedMinutes > 0) {
                                    Text(
                                        "${task.estimatedMinutes} mins",
                                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Workload Density Content (Weekly Schedule Timeline Objects) ──
@Composable
private fun WorkloadDensityContent(
    tasks: List<HomeworkTask>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    todayStr: String
) {
    val pendingTasks = tasks.filter { !it.completed }
    val groupedByDate = pendingTasks.groupBy { it.dueDate }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Weekly Schedule & Workload Timeline", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
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
            val (densityLabel, densityColor) = when {
                totalMins > 180 -> "HEAVY" to colors.danger
                totalMins > 60 -> "MODERATE" to colors.warning
                totalMins > 0 -> "LIGHT" to colors.success
                else -> "FREE" to colors.info
            }

            AmazeCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(densityColor.copy(alpha = 0.15f))
                                    .border(1.dp, densityColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Schedule, null, tint = densityColor, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    if (date == todayStr) "Today ($date)" else date,
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
                                )
                                Text(
                                    "${dayTasks.size} assigned task(s)",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(densityColor.copy(alpha = 0.15f))
                                    .border(1.dp, densityColor.copy(alpha = 0.3f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(densityLabel, color = densityColor, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                            }
                            Text(
                                "${totalMins}m est.",
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = densityColor, fontSize = AmazeTheme.fontSize.sm)
                            )
                        }
                    }

                    if (dayTasks.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = colors.border.copy(alpha = 0.4f))
                        Spacer(Modifier.height(8.dp))
                        dayTasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• ${task.courseCode}: ${task.title}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${task.estimatedMinutes}m",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Calendar View Content (Monthly grid with task dots) ──
@Composable
private fun CalendarViewContent(
    tasks: List<HomeworkTask>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    todayStr: String,
    onToggle: (String) -> Unit,
    onEditTask: (HomeworkTask) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var monthFirst by remember { mutableStateOf(LocalDate(now.year, now.monthNumber, 1)) }
    var selectedDate by remember { mutableStateOf(todayStr) }

    val pendingByDate = remember(tasks) {
        tasks.filter { !it.completed }.groupBy { it.dueDate }
    }
    val selectedTasks = remember(tasks, selectedDate) {
        tasks.filter { it.dueDate == selectedDate }.sortedBy { it.completed }
    }

    val leadingBlanks = monthFirst.dayOfWeek.isoDayNumber - 1
    val daysInMonth = when (monthFirst.monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if ((monthFirst.year % 4 == 0 && monthFirst.year % 100 != 0) || monthFirst.year % 400 == 0) 29 else 28
    }
    val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val monthLabel = "${monthNames[monthFirst.monthNumber - 1]} ${monthFirst.year}"

    Column(modifier = Modifier.fillMaxWidth()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { monthFirst = monthFirst.plus(-1, DateTimeUnit.MONTH) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous Month", tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Text(monthLabel, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    IconButton(onClick = { monthFirst = monthFirst.plus(1, DateTimeUnit.MONTH) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.ChevronRight, "Next Month", tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayLabels.forEach { label ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                val cells = (0 until leadingBlanks + daysInMonth).map { idx ->
                    val dayNumber = idx - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) dayNumber else null
                }

                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        week.forEach { dayNumber ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (dayNumber == null) {
                                    Spacer(Modifier.height(28.dp))
                                } else {
                                    val dateStr = "${monthFirst.year}-${monthFirst.monthNumber.toString().padStart(2, '0')}-${dayNumber.toString().padStart(2, '0')}"
                                    val dayTasks = pendingByDate[dateStr] ?: emptyList()
                                    val isSelected = selectedDate == dateStr
                                    val isToday = todayStr == dateStr
                                    Column(
                                        modifier = Modifier
                                            .size(width = 34.dp, height = 34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> colors.accent
                                                    isToday -> colors.accent.copy(alpha = 0.15f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { selectedDate = dateStr },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            dayNumber.toString(),
                                            style = AmazeTheme.typography.caption.copy(
                                                color = if (isSelected) Color.White else if (isToday) colors.accent else colors.textPrimary,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = AmazeTheme.fontSize.sm
                                            )
                                        )
                                        if (dayTasks.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                repeat(dayTasks.size.coerceAtMost(3)) {
                                                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color.White else colors.accent))
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
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Tasks on $selectedDate (${selectedTasks.size})",
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        Spacer(Modifier.height(4.dp))

        if (selectedTasks.isEmpty()) {
            Text(
                "No tasks for this day — tap '+' to add one",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            selectedTasks.forEach { task ->
                AmazeCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    TaskItemCard(
                        task = task,
                        colors = colors,
                        tint = taskTypeTint(task.type, colors),
                        onToggle = { onToggle(task.id) },
                        onToggleSubtask = { subId -> AppState.toggleSubtaskCompleted(task.id, subId) },
                        onStartFocus = {},
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task.id) }
                    )
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
    var showCoursePicker by remember { mutableStateOf(false) }
    var customCategories by remember { mutableStateOf<List<CourseOption>>(emptyList()) }

    var descPreview by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var sessionDatePickerFor by remember { mutableStateOf<Int?>(null) }
    var sessionTimePickerFor by remember { mutableStateOf<Int?>(null) }
    var editingSubtaskId by remember { mutableStateOf<String?>(null) }
    var editingSubtaskText by remember { mutableStateOf("") }

    var reminderAt by remember { mutableStateOf(taskToEdit?.reminderAt ?: "") }
    var reminderRepeat by remember { mutableStateOf(taskToEdit?.reminderRepeat ?: "none") }
    var showOnCalendar by remember { mutableStateOf(taskToEdit?.showOnCalendar ?: false) }
    var showOnTimetable by remember { mutableStateOf(taskToEdit?.showOnTimetable ?: false) }
    var includeRegularClasses by remember { mutableStateOf(taskToEdit?.includeRegularClasses ?: false) }
    var workSessionsState by remember {
        mutableStateOf(taskToEdit?.workSessions?.toMutableList() ?: mutableStateListOf<WorkSession>())
    }
    var odHoursText by remember { mutableStateOf(taskToEdit?.odHours?.takeIf { it > 0 }?.toString() ?: "") }

    val tomorrow = try {
        Clock.System.now().plus(1, DateTimeUnit.DAY, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    } catch (_: Exception) { dueDate }

    val currentCourseOpt = remember(selectedCourse, courseOptions, customCategories) {
        (courseOptions + customCategories).firstOrNull { it.code == selectedCourse }
            ?: CourseOption(selectedCourse, selectedCourse)
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

    val isDirty by remember(taskToEdit) {
        derivedStateOf {
            title != (taskToEdit?.title ?: "") ||
                description != (taskToEdit?.description ?: "") ||
                selectedCourse != (taskToEdit?.courseCode ?: courseOptions.firstOrNull()?.code ?: "GENERAL") ||
                type != (taskToEdit?.type ?: "homework") ||
                priority != (taskToEdit?.priority ?: "medium") ||
                dueDate != (taskToEdit?.dueDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) ||
                estimatedMinsText != (taskToEdit?.estimatedMinutes?.toString() ?: "30") ||
                reminderAt != (taskToEdit?.reminderAt ?: "") ||
                reminderRepeat != (taskToEdit?.reminderRepeat ?: "none") ||
                showOnCalendar != (taskToEdit?.showOnCalendar ?: false) ||
                showOnTimetable != (taskToEdit?.showOnTimetable ?: false) ||
                includeRegularClasses != (taskToEdit?.includeRegularClasses ?: false) ||
                subtasksList != (taskToEdit?.subtasks ?: emptyList<Subtask>()) ||
                workSessionsState.toList() != (taskToEdit?.workSessions ?: emptyList<WorkSession>()) ||
                odHoursText != (taskToEdit?.odHours?.takeIf { it > 0 }?.toString() ?: "")
        }
    }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val buildTask: () -> HomeworkTask = {
        HomeworkTask(
            id = taskToEdit?.id ?: ("task_" + Clock.System.now().toEpochMilliseconds()),
            courseCode = selectedCourse,
            courseTitle = currentCourseOpt.title.ifBlank { selectedCourse },
            title = title.trim(),
            description = description.trim(),
            dueDate = dueDate.trim(),
            type = type,
            priority = priority,
            estimatedMinutes = estimatedMinsText.toIntOrNull() ?: 30,
            completed = taskToEdit?.completed ?: false,
            subtasks = subtasksList,
            createdAt = taskToEdit?.createdAt ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
            reminderAt = reminderAt.trim().ifBlank { null },
            reminderRepeat = reminderRepeat,
            showOnCalendar = showOnCalendar,
            showOnTimetable = showOnTimetable,
            includeRegularClasses = includeRegularClasses,
            workSessions = workSessionsState.toList(),
            odHours = odHoursText.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
        )
    }

    val requestDismiss = {
        if (isDirty) showDiscardDialog = true
        else onDismiss()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (taskToEdit == null) Icons.Rounded.AddTask else Icons.Rounded.Edit,
                            null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (taskToEdit == null) "New Task" else "Edit Task",
                            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            text = if (taskToEdit == null) "Plan it, track it, get it done" else "Update the details below",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                }
                IconButton(onClick = requestDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Close", tint = colors.textMuted)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Details ──
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // Course selector row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(alpha = 0.08f))
                            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { showCoursePicker = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = colors.accent, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentCourseOpt.code,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (currentCourseOpt.title.isNotBlank()) currentCourseOpt.title else "Tap to change course or add a custom category",
                                style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Rounded.ArrowDropDown, "Select Course", tint = colors.accent)
                    }

            // Title & Description Fields
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title", color = colors.textSecondary) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Description with Markdown preview toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DESCRIPTION", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AmazePill("Edit", !descPreview, colors, icon = Icons.Rounded.Edit, onClick = { descPreview = false })
                            AmazePill("Preview", descPreview, colors, icon = Icons.Rounded.Visibility, onClick = { descPreview = true })
                        }
                    }
                    if (descPreview) {
                        if (description.isBlank()) {
                            Text(
                                "Nothing to preview yet — type some markdown in the editor.",
                                style = AmazeTheme.typography.caption.copy(color = colors.textMuted),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.background.copy(alpha = 0.4f))
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Markdown(
                                    content = description,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Optional) — markdown supported", color = colors.textSecondary) },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Schedule ──
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormSectionHeader("TYPE", Icons.Rounded.Category, colors)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("homework" to "Homework", "assignment" to "Assignment", "quiz" to "Quiz", "exam" to "Exam", "lab" to "Lab", "project" to "Project").forEach { (key, label) ->
                            AmazePill(
                                label = label,
                                selected = type == key,
                                colors = colors,
                                icon = taskTypeIcon(key),
                                onClick = { type = key }
                            )
                        }
                    }

                    if (type == "quiz" || type == "exam") {
                        FormSectionHeader("VISIBILITY", Icons.Rounded.CalendarViewMonth, colors)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Show on Calendar", style = AmazeTheme.typography.body.copy(fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary))
                            Switch(checked = showOnCalendar, onCheckedChange = { showOnCalendar = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Show on Timetable", style = AmazeTheme.typography.body.copy(fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary))
                            Switch(checked = showOnTimetable, onCheckedChange = { showOnTimetable = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Include regular classes", style = AmazeTheme.typography.body.copy(fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary))
                            Switch(checked = includeRegularClasses, onCheckedChange = { includeRegularClasses = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
                        }
                    }

                    FormSectionHeader("PRIORITY", Icons.Rounded.Flag, colors)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "high" to (Icons.Rounded.ArrowUpward to "High"),
                            "medium" to (Icons.Rounded.Remove to "Medium"),
                            "low" to (Icons.Rounded.ArrowDownward to "Low")
                        ).forEach { (key, pair) ->
                            val (icon, label) = pair
                            val pillColor = when (key) {
                                "high" -> colors.danger
                                "medium" -> colors.warning
                                else -> colors.success
                            }
                            AmazePill(
                                label = label,
                                selected = priority == key,
                                colors = colors,
                                icon = icon,
                                tint = pillColor,
                                onClick = { priority = key }
                            )
                        }
                    }

                    FormSectionHeader("DUE", Icons.Rounded.Event, colors)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PickerField(
                            value = dueDate,
                            label = "Due Date",
                            colors = colors,
                            icon = Icons.Rounded.Event,
                            modifier = Modifier.weight(1.4f),
                            onClick = { showDueDatePicker = true }
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

                    if (type == "assignment") {
                        FormSectionHeader("WORK SESSIONS", Icons.Rounded.HourglassBottom, colors)
                        if (workSessionsState.isEmpty()) {
                            Text(
                                "Plan when you'll work on this before the deadline. Pick dates + time per session.",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        } else {
                            workSessionsState.forEachIndexed { idx, session ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.background.copy(alpha = 0.4f))
                                        .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Session ${idx + 1}", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro))
                                        IconButton(onClick = { workSessionsState.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Rounded.DeleteOutline, "Remove Session", tint = colors.danger, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        PickerField(
                                            value = session.date,
                                            label = "Date",
                                            colors = colors,
                                            icon = Icons.Rounded.Event,
                                            modifier = Modifier.weight(1.2f),
                                            onClick = { sessionDatePickerFor = idx }
                                        )
                                        PickerField(
                                            value = session.startTime,
                                            label = "Start",
                                            colors = colors,
                                            icon = Icons.Rounded.Schedule,
                                            modifier = Modifier.weight(1f),
                                            onClick = { sessionTimePickerFor = idx }
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Duration", style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                                        Spacer(Modifier.width(2.dp))
                                        listOf(30, 60, 90).forEach { mins ->
                                            AmazePill(
                                                label = "${mins}m",
                                                selected = session.durationMinutes == mins,
                                                colors = colors,
                                                onClick = { workSessionsState[idx] = session.copy(durationMinutes = mins) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        val newDate = try {
                            val lastSession = workSessionsState.lastOrNull()
                            if (lastSession != null) {
                                val d = LocalDate.parse(lastSession.date)
                                d.plus(1, DateTimeUnit.DAY).toString()
                            } else {
                                val dd = LocalDate.parse(tomorrow)
                                val dl = try { LocalDate.parse(dueDate.trim()) } catch (_: Exception) { dd }
                                if (dd < dl) dd.toString() else dl.toString()
                            }
                        } catch (_: Exception) { tomorrow }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AmazeButton(
                                text = "Add Session",
                                variant = ButtonVariant.SECONDARY,
                                onClick = {
                                    workSessionsState.add(WorkSession(date = newDate, startTime = "18:00", durationMinutes = 30))
                                },
                                icon = Icons.Rounded.Add,
                                modifier = Modifier.weight(1f).height(36.dp)
                            )
                            AmazeButton(
                                text = "Auto-Split Till Deadline",
                                variant = ButtonVariant.SECONDARY,
                                onClick = {
                                    workSessionsState.clear()
                                    try {
                                        val deadline = LocalDate.parse(dueDate.trim())
                                        var day = LocalDate.parse(tomorrow)
                                        var count = 0
                                        while (day < deadline && count < 14) {
                                            workSessionsState.add(WorkSession(date = day.toString(), startTime = "18:00", durationMinutes = 30))
                                            day = day.plus(1, DateTimeUnit.DAY)
                                            count++
                                        }
                                    } catch (_: Exception) {}
                                },
                                icon = Icons.Rounded.Schema,
                                modifier = Modifier.weight(1f).height(36.dp)
                            )
                        }
                    }

            if (type == "assignment" || type == "project" || type == "lab") {
                        FormSectionHeader("OD HOURS", Icons.Rounded.HourglassTop, colors, trailing = "optional")
                        OutlinedTextField(
                            value = odHoursText,
                            onValueChange = { odHoursText = it },
                            label = { Text("Hours (e.g. 2.5)", color = colors.textSecondary) },
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    FormSectionHeader("REMINDER", Icons.Rounded.NotificationsActive, colors)
                    PickerField(
                        value = reminderAt,
                        label = "Remind me on",
                        colors = colors,
                        icon = Icons.Rounded.Schedule,
                        onClick = { showReminderPicker = true }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("none" to "None", "daily" to "Daily", "weekly" to "Weekly", "custom" to "Custom").forEach { (key, label) ->
                            AmazePill(
                                label = label,
                                selected = reminderRepeat == key,
                                colors = colors,
                                onClick = { reminderRepeat = key }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Subtasks Checklist ──
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val doneCount = subtasksList.count { it.completed }
                    FormSectionHeader(
                        "SUBTASKS",
                        Icons.Rounded.Checklist,
                        colors,
                        trailing = if (subtasksList.isNotEmpty()) "$doneCount/${subtasksList.size} done" else null
                    )
                    if (subtasksList.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { if (subtasksList.isEmpty()) 0f else doneCount.toFloat() / subtasksList.size },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                            color = colors.accent,
                            trackColor = colors.border.copy(alpha = 0.5f)
                        )
                    }

                    // Add row
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = subtaskInput,
                            onValueChange = { subtaskInput = it },
                            label = { Text("Add a step...", color = colors.textSecondary) },
                            colors = textFieldColors,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    if (subtaskInput.isNotBlank()) {
                                        val newSub = Subtask("sub_" + Clock.System.now().toEpochMilliseconds(), subtaskInput.trim())
                                        subtasksList = subtasksList + newSub
                                        subtaskInput = ""
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        AmazePill(
                            label = "Add",
                            selected = false,
                            colors = colors,
                            icon = Icons.Rounded.Add,
                            onClick = {
                                if (subtaskInput.isNotBlank()) {
                                    val newSub = Subtask("sub_" + Clock.System.now().toEpochMilliseconds(), subtaskInput.trim())
                                    subtasksList = subtasksList + newSub
                                    subtaskInput = ""
                                }
                            }
                        )
                    }

                    if (subtasksList.isNotEmpty()) {
                        subtasksList.forEachIndexed { idx, sub ->
                            if (editingSubtaskId == sub.id) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = editingSubtaskText,
                                        onValueChange = { editingSubtaskText = it },
                                        colors = textFieldColors,
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            if (editingSubtaskText.isNotBlank()) {
                                                subtasksList = subtasksList.map { if (it.id == sub.id) it.copy(title = editingSubtaskText.trim()) else it }
                                            }
                                            editingSubtaskId = null
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Check, "Save Subtask", tint = colors.success, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { editingSubtaskId = null }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Rounded.Close, "Cancel", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                        .background(colors.background.copy(alpha = 0.5f))
                                        .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
                                        .clickable {
                                            editingSubtaskId = sub.id
                                            editingSubtaskText = sub.title
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${idx + 1}", fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold, color = colors.accent)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Checkbox(
                                        checked = sub.completed,
                                        onCheckedChange = { checked ->
                                            subtasksList = subtasksList.map { if (it.id == sub.id) it.copy(completed = checked) else it }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                    Text(
                                        sub.title,
                                        style = AmazeTheme.typography.caption.copy(
                                            color = if (sub.completed) colors.textMuted else colors.textPrimary,
                                            fontSize = AmazeTheme.fontSize.sm,
                                            textDecoration = if (sub.completed) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (idx > 0) {
                                        IconButton(onClick = {
                                            val list = subtasksList.toMutableList()
                                            val tmp = list[idx]
                                            list[idx] = list[idx - 1]
                                            list[idx - 1] = tmp
                                            subtasksList = list
                                        }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, "Move Up", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (idx < subtasksList.lastIndex) {
                                        IconButton(onClick = {
                                            val list = subtasksList.toMutableList()
                                            val tmp = list[idx]
                                            list[idx] = list[idx + 1]
                                            list[idx + 1] = tmp
                                            subtasksList = list
                                        }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, "Move Down", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(onClick = { subtasksList = subtasksList.filter { it.id != sub.id } }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Rounded.Close, "Remove", tint = colors.danger, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }

        // Sticky footer
        HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AmazeButton(
                text = if (taskToEdit == null) "Create Task" else "Save Changes",
                onClick = {
                    val t = buildTask()
                    if (t.title.isNotBlank()) onSave(t)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    if (taskToEdit == null) "Discard new task?" else "Discard changes?",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "You have unsaved changes. Save them or go back to keep editing.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    val t = buildTask()
                    if (t.title.isNotBlank()) {
                        onSave(t)
                        onDismiss()
                    }
                }) {
                    Text("Save", color = colors.success, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Keep Editing", color = colors.textPrimary)
                    }
                    TextButton(onClick = { showDiscardDialog = false; onDismiss() }) {
                        Text("Discard", color = colors.danger, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    if (showCoursePicker) {
        CoursePickerSheet(
            courseOptions = courseOptions,
            selectedCode = selectedCourse,
            customCategories = customCategories,
            colors = colors,
            onDismiss = { showCoursePicker = false },
            onSelect = { opt ->
                selectedCourse = opt.code
                showCoursePicker = false
            },
            onAddCustom = { customCategories = customCategories + it }
        )
    }

    if (showDueDatePicker) {
        DatePickerSheet(
            title = "Due Date",
            initial = parseLocalDate(dueDate),
            colors = colors,
            onSelected = {
                dueDate = it.toString()
                showDueDatePicker = false
            },
            onDismiss = { showDueDatePicker = false }
        )
    }

    if (showReminderPicker) {
        val parts = reminderAt.split(" ")
        ReminderPickerSheet(
            initialDate = parts.firstOrNull()?.let { parseLocalDate(it) },
            initialTime = parts.getOrNull(1)?.let { parseLocalTime(it) },
            colors = colors,
            onSelected = { d, t ->
                reminderAt = "${d.toString()} ${formatTime(t)}"
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false }
        )
    }

    sessionDatePickerFor?.let { idx ->
        val session = workSessionsState.getOrNull(idx) ?: return@let
        DatePickerSheet(
            title = "Session ${idx + 1} Date",
            initial = parseLocalDate(session.date),
            colors = colors,
            onSelected = {
                workSessionsState[idx] = session.copy(date = it.toString())
                sessionDatePickerFor = null
            },
            onDismiss = { sessionDatePickerFor = null }
        )
    }

    sessionTimePickerFor?.let { idx ->
        val session = workSessionsState.getOrNull(idx) ?: return@let
        TimePickerSheet(
            title = "Session ${idx + 1} Start",
            initial = parseLocalTime(session.startTime),
            colors = colors,
            onSelected = {
                workSessionsState[idx] = session.copy(startTime = formatTime(it))
                sessionTimePickerFor = null
            },
            onDismiss = { sessionTimePickerFor = null }
        )
    }
}

// ── Course Picker Bottom Sheet (slides up, like the rest of the app) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoursePickerSheet(
    courseOptions: List<CourseOption>,
    selectedCode: String,
    customCategories: List<CourseOption>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onDismiss: () -> Unit,
    onSelect: (CourseOption) -> Unit,
    onAddCustom: (CourseOption) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCustomEntry by remember { mutableStateOf(false) }
    var customCode by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }

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

    val builtIn = listOf(CourseOption("GENERAL", "General Academic Task"))
    val allOptions = remember(courseOptions, customCategories) {
        (builtIn + customCategories + courseOptions).distinctBy { it.code }
    }
    val filteredOptions = remember(allOptions, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allOptions
        else allOptions.filter { it.code.lowercase().contains(q) || it.title.lowercase().contains(q) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            SheetHeaderRow(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                title = "Select Course",
                subtitle = "Pick a category for this task",
                colors = colors,
                onClose = onDismiss
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search courses...", color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

                Spacer(Modifier.height(10.dp))

                if (!showCustomEntry) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                            .background(colors.accent.copy(alpha = 0.08f))
                            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.medium))
                            .clickable { showCustomEntry = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AddCircle, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add a custom category (not in your curriculum)",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = customCode,
                                onValueChange = { customCode = it },
                                label = { Text("Code (e.g. PD1001)", color = colors.textSecondary) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.weight(1.1f)
                            )
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = { customTitle = it },
                                label = { Text("Title (optional)", color = colors.textSecondary) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmazeButton(
                                text = "Cancel",
                                variant = ButtonVariant.SECONDARY,
                                onClick = { showCustomEntry = false },
                                modifier = Modifier.weight(1f).height(38.dp)
                            )
                            AmazeButton(
                                text = "Add & Select",
                                onClick = {
                                    val code = customCode.trim().uppercase()
                                    if (code.isNotBlank()) {
                                        val opt = CourseOption(code, customTitle.trim())
                                        onAddCustom(opt)
                                        onSelect(opt)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(38.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (filteredOptions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No courses match \"$searchQuery\"", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredOptions, key = { it.code }) { opt ->
                            val isSelected = opt.code == selectedCode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                    .background(if (isSelected) colors.accent.copy(alpha = 0.1f) else colors.background.copy(alpha = 0.4f))
                                    .border(1.dp, if (isSelected) colors.accent.copy(alpha = 0.45f) else colors.border.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.medium))
                                    .clickable { onSelect(opt) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(opt.code, style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                    if (opt.title.isNotBlank()) {
                                        Text(opt.title, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                if (isSelected) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = colors.textMuted.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
            }
        }
    }
}

private fun parseLocalDate(s: String): LocalDate? = try { LocalDate.parse(s) } catch (_: Exception) { null }

private fun parseLocalTime(s: String): LocalTime? = try { LocalTime.parse(s) } catch (_: Exception) { null }

private fun formatTime(t: LocalTime): String =
    "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

@Composable
private fun FormSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent, fontSize = AmazeTheme.fontSize.xs)
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro)
            )
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
    AmazeCard(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        TaskItemCard(
            task = task,
            colors = colors,
            tint = taskTypeTint(task.type, colors),
            onToggle = onToggle,
            onToggleSubtask = { AppState.toggleSubtaskCompleted(task.id, it) },
            onStartFocus = {},
            onEdit = {},
            onDelete = onDelete,
            modifier = Modifier.fillMaxWidth()
        )
    }
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

// ── Hero: Tasks & Reminders summary (course-detail design language) ──
@Composable
private fun TasksHeroCard(
    pendingCount: Int,
    overdueCount: Int,
    todayCount: Int,
    totalCount: Int,
    completedCount: Int,
    workloadText: String,
    selectedViewMode: String,
    onViewModeChange: (String) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val doneRatio = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    HeroCard(colors = colors, modifier = Modifier.fillMaxWidth()) { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TaskAlt, null, tint = p.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Tasks & Reminders",
                color = p.textSecondary,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            HeroChip(
                text = when {
                    overdueCount > 0 -> "$overdueCount OVERDUE"
                    pendingCount == 0 -> "ALL CLEAR"
                    else -> "$pendingCount PENDING"
                },
                p = p
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroStat("Pending", "$pendingCount", p.text, Modifier.weight(1f), valueSize = AmazeTheme.fontSize.xl)
            HeroStat("Today", "$todayCount", p.textSecondary, Modifier.weight(1f), valueSize = AmazeTheme.fontSize.xl)
            HeroStat("Overdue", "$overdueCount", p.textSecondary, Modifier.weight(1f), valueSize = AmazeTheme.fontSize.xl)
        }

        HeroPanel(p = p, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Est. Workload",
                        color = p.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.sm
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(workloadText, color = p.text, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Completed",
                        color = p.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.sm
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("$completedCount/$totalCount", color = p.text, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl)
                }
            }
            LinearProgressIndicator(
                progress = { doneRatio },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                color = p.progress,
                trackColor = p.progressTrack
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                .background(p.panelBg)
                .border(1.dp, p.panelBorder, RoundedCornerShape(AmazeTheme.radius.medium))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ViewTabButton("List", Icons.Rounded.List, selectedViewMode == "list", p, Modifier.weight(1f)) { onViewModeChange("list") }
            ViewTabButton("Kanban", Icons.Rounded.ViewColumn, selectedViewMode == "kanban", p, Modifier.weight(1f)) { onViewModeChange("kanban") }
            ViewTabButton("Calendar", Icons.Rounded.CalendarMonth, selectedViewMode == "calendar", p, Modifier.weight(1f)) { onViewModeChange("calendar") }
            ViewTabButton("Workload", Icons.Rounded.Analytics, selectedViewMode == "workload", p, Modifier.weight(1f)) { onViewModeChange("workload") }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.surface)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.4f) else colors.textMuted.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.small))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = AmazeTheme.fontSize.xs,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.accent else colors.textSecondary
        )
    }
}

private fun isTaskOverdue(task: HomeworkTask): Boolean {
    if (task.completed) return false
    return try {
        val d = task.dueDate.split("-").map { s -> s.toInt() }
        LocalDate(d[0], d[1], d[2]) < Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    } catch (_: Exception) { false }
}
