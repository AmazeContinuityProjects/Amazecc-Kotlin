package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ETaskItem
import com.amazecc.app.shared.domain.overhaul.M3ETaskKind
import com.amazecc.app.shared.domain.overhaul.M3ETaskStatus
import com.amazecc.app.shared.domain.overhaul.M3ETasksAction
import com.amazecc.app.shared.domain.overhaul.M3ETasksFilter
import com.amazecc.app.shared.domain.overhaul.M3ETasksState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Unified Tasks & Moodle Assignment Planner.
 * 100% Stateless: receives [M3ETasksState] and emits [M3ETasksAction].
 * Features tri-state task status, overdue warnings, Moodle assignment links, and filter strips.
 */
@Composable
fun M3ETasksScreen(
    state: M3ETasksState,
    onAction: (M3ETasksAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tasks & Planner",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "VTOP & Moodle Academic Deadlines",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElevatedButton(
                        onClick = { onAction(M3ETasksAction.OpenCreateDialog) },
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Task",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3ETasksAction.Refresh) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Search Field
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onAction(M3ETasksAction.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search tasks or course codes...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.primary
                    )
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedPlaceholderColor = colors.onSurfaceVariant,
                    unfocusedPlaceholderColor = colors.onSurfaceVariant
                )
            )
        }

        // 3. Filter Segment
        item {
            M3EConnectedButtonGroup(
                items = M3ETasksFilter.entries,
                selectedItem = state.activeFilter,
                onItemSelected = { onAction(M3ETasksAction.SelectFilter(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Progress Summary Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Tasks (${state.filteredTasks.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                Text(
                    text = "${state.completedCount} Completed • ${state.pendingCount} Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary
                )
            }
        }

        // 5. Tasks List
        if (state.filteredTasks.isEmpty()) {
            item {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Assignment,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No tasks in this view",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.filteredTasks) { task ->
                TaskItemCard(
                    task = task,
                    onToggleStatus = { onAction(M3ETasksAction.ToggleTaskStatus(task.id)) },
                    onDelete = { onAction(M3ETasksAction.DeleteTask(task.id)) }
                )
            }
        }

        // Bottom Spacing for floating navigation bar clearance (140dp)
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
        }
    }
}

@Composable
private fun TaskItemCard(
    task: M3ETaskItem,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val isDone = task.status == M3ETaskStatus.DONE

    M3ESurfaceCard(
        containerLevel = if (isDone) SurfaceContainerLevel.LOW else SurfaceContainerLevel.STANDARD,
        cornerRadius = 20.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Icon Toggle Button
            IconButton(
                onClick = onToggleStatus,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = when (task.status) {
                        M3ETaskStatus.DONE -> Icons.Rounded.CheckCircle
                        M3ETaskStatus.IN_PROGRESS -> Icons.Rounded.HourglassBottom
                        M3ETaskStatus.PENDING -> Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = "Toggle status",
                    tint = when (task.status) {
                        M3ETaskStatus.DONE -> colors.primary
                        M3ETaskStatus.IN_PROGRESS -> colors.tertiary
                        M3ETaskStatus.PENDING -> colors.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Task Details Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Course Code Badge & Kind Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.courseCode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = colors.onPrimaryContainer
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.kind.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp
                            ),
                            color = colors.onSurfaceVariant
                        )
                    }

                    if (task.isOverdue) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.errorContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OVERDUE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                ),
                                color = colors.onError
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isDone) colors.onSurfaceVariant else colors.onSurface
                )

                // Due Date & Slot
                if (!task.dueDate.isNullOrBlank() || !task.slotName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!task.dueDate.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = if (task.isOverdue) colors.error else colors.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Due: ${task.dueDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (task.isOverdue) colors.error else colors.onSurfaceVariant
                                )
                            }
                        }

                        if (!task.slotName.isNullOrBlank()) {
                            Text(
                                text = "• Slot ${task.slotName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                // Notes preview
                if (!task.notes.isNullOrBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Actions: Delete
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete task",
                    tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3ETasksScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ETasksScreen(
            state = createMockTasksState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3ETasksScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ETasksScreen(
            state = createMockTasksState(),
            onAction = {}
        )
    }
}

private fun createMockTasksState(): M3ETasksState {
    val sampleTasks = listOf(
        M3ETaskItem(
            id = "task_01",
            title = "Digital Assignment 2: B+ Tree Indexing implementation",
            kind = M3ETaskKind.DIGITAL_ASSIGNMENT,
            status = M3ETaskStatus.PENDING,
            courseCode = "CSE3001",
            courseTitle = "Database Management Systems",
            dueDate = "29 Nov 2026, 11:59 PM",
            slotName = "D1+TD1",
            isOverdue = false,
            moodleUrl = "https://moodle.vit.ac.in/mod/assign/view.php?id=123",
            notes = "Upload PDF write-up and GitHub zip archive"
        ),
        M3ETaskItem(
            id = "task_02",
            title = "CAT-2 Module 4: Dynamic Programming & Greedy Algorithms",
            kind = M3ETaskKind.STUDY,
            status = M3ETaskStatus.IN_PROGRESS,
            courseCode = "CSE2004",
            courseTitle = "Design and Analysis of Algorithms",
            dueDate = "01 Dec 2026",
            slotName = "A2+TA2",
            isOverdue = false,
            notes = "Revise Bellman-Ford, Floyd-Warshall, and Knapsack problems"
        ),
        M3ETaskItem(
            id = "task_03",
            title = "Lab Exercise 6: Socket Programming in C",
            kind = M3ETaskKind.HOMEWORK,
            status = M3ETaskStatus.DONE,
            courseCode = "CSE1004",
            courseTitle = "Network and Communication",
            dueDate = "20 Nov 2026",
            slotName = "L31+L32",
            isOverdue = false
        )
    )

    return M3ETasksState(
        tasks = sampleTasks,
        filteredTasks = sampleTasks,
        activeFilter = M3ETasksFilter.ALL,
        searchQuery = "",
        pendingCount = 2,
        completedCount = 1
    )
}
