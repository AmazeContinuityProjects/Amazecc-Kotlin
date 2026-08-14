package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.*
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.SemesterPickerSheet
import com.amazecc.app.shared.utils.ExportImportManager
import com.amazecc.app.shared.utils.rememberFileImporter
import com.amazecc.app.shared.utils.rememberFileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AcademicsPage() {
    val colors = AmazeTheme.colors
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    var preferredCalendarName by remember { mutableStateOf(SettingsManager.getPreferredCalendar() ?: "") }
    val semIds = semesterMap.keys.toList().sortedDescending()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Semester & Calendar")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Select your target semester and default academic calendar from the dropdowns below.",
                    color = colors.textSecondary,
                    fontSize = AmazeTheme.fontSize.sm
                )

                var showSemesterSheet by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = semesterMap[selectedSemester] ?: selectedSemester,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Semester", color = colors.textSecondary) },
                        supportingText = {
                            Text(
                                "$selectedSemester is ${semesterMap[selectedSemester] ?: AppState.deriveSemesterName(selectedSemester)}",
                                color = colors.textMuted,
                                fontSize = AmazeTheme.fontSize.xs
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showSemesterSheet = true }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.accent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showSemesterSheet = true },
                        shape = RoundedCornerShape(AmazeTheme.radius.small),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }

                if (showSemesterSheet) {
                    SemesterPickerSheet(
                        semIds = semIds,
                        selectedId = selectedSemester,
                        colors = colors,
                        onDismiss = { showSemesterSheet = false },
                        onSelect = { semId ->
                            showSemesterSheet = false
                            if (semId != selectedSemester) AppState.selectSemester(semId)
                        }
                    )
                }

                var calendarExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = preferredCalendarName.ifEmpty { AmazeClient.calendarTypes.first().second },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Academic Calendar", color = colors.textSecondary) },
                        trailingIcon = {
                            IconButton(onClick = { calendarExpanded = !calendarExpanded }) {
                                Icon(if (calendarExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = colors.accent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { calendarExpanded = !calendarExpanded },
                        shape = RoundedCornerShape(AmazeTheme.radius.small),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    DropdownMenu(
                        expanded = calendarExpanded,
                        onDismissRequest = { calendarExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.88f).background(colors.surface)
                    ) {
                        AmazeClient.calendarTypes.forEach { (_, name) ->
                            val isSelected = preferredCalendarName == name || (preferredCalendarName.isEmpty() && AmazeClient.calendarTypes.first().second == name)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = name,
                                        color = if (isSelected) colors.accent else colors.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Rounded.Check, null, tint = colors.accent) }
                                } else null,
                                onClick = {
                                    calendarExpanded = false
                                    preferredCalendarName = name
                                    SettingsManager.savePreferredCalendar(name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataSyncPage(
    snackbarHostState: SnackbarHostState,
    hasPermissionManager: Boolean,
    requestPushToggle: ((Boolean) -> Unit) -> Unit
) {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Notifications & Alerts")
        SettingsGroupCard {
            var classNotif by remember { mutableStateOf(SettingsManager.isNotifClassRemindersEnabled()) }
            SettingsSwitchRow(
                icon = Icons.Rounded.Schedule,
                title = "Class Reminders",
                subtitle = "Notify before each scheduled class starts",
                tint = colors.accent,
                checked = classNotif,
                onCheckedChange = { enabled ->
                    if (enabled && !classNotif && hasPermissionManager) {
                        requestPushToggle { _ -> classNotif = true; SettingsManager.setNotifClassRemindersEnabled(true) }
                    } else {
                        classNotif = enabled; SettingsManager.setNotifClassRemindersEnabled(enabled)
                        if (!enabled) AppState.rescheduleNotifications()
                    }
                }
            )
            SettingsRowDivider()

            var assignNotif by remember { mutableStateOf(SettingsManager.isNotifAssignmentRemindersEnabled()) }
            SettingsSwitchRow(
                icon = Icons.Rounded.Assignment,
                title = "Assignment Reminders",
                subtitle = "Remind before assignment deadlines",
                tint = colors.accent,
                checked = assignNotif,
                onCheckedChange = { enabled ->
                    if (enabled && !assignNotif && hasPermissionManager) {
                        requestPushToggle { _ -> assignNotif = true; SettingsManager.setNotifAssignmentRemindersEnabled(true) }
                    } else {
                        assignNotif = enabled; SettingsManager.setNotifAssignmentRemindersEnabled(enabled)
                        if (!enabled) AppState.rescheduleNotifications()
                    }
                }
            )
            SettingsRowDivider()

            var taskNotif by remember { mutableStateOf(SettingsManager.isNotifTaskRemindersEnabled()) }
            SettingsSwitchRow(
                icon = Icons.Rounded.TaskAlt,
                title = "Task Reminders",
                subtitle = "Remind about tasks on due date",
                tint = colors.accent,
                checked = taskNotif,
                onCheckedChange = { enabled ->
                    if (enabled && !taskNotif && hasPermissionManager) {
                        requestPushToggle { _ -> taskNotif = true; SettingsManager.setNotifTaskRemindersEnabled(true) }
                    } else {
                        taskNotif = enabled; SettingsManager.setNotifTaskRemindersEnabled(enabled)
                        if (!enabled) AppState.rescheduleNotifications()
                    }
                }
            )
            SettingsRowDivider()

            var examNotif by remember { mutableStateOf(SettingsManager.isNotifExamRemindersEnabled()) }
            SettingsSwitchRow(
                icon = Icons.Rounded.EventSeat,
                title = "Exam Reminders",
                subtitle = "Remind 24h prior & at reporting time; suppresses class reminders on exam days",
                tint = colors.accent,
                checked = examNotif,
                onCheckedChange = { enabled ->
                    if (enabled && !examNotif && hasPermissionManager) {
                        requestPushToggle { _ -> examNotif = true; SettingsManager.setNotifExamRemindersEnabled(true); AppState.rescheduleNotifications() }
                    } else {
                        examNotif = enabled; SettingsManager.setNotifExamRemindersEnabled(enabled)
                        AppState.rescheduleNotifications()
                    }
                }
            )
        }

        SettingsGroupLabel("Class Reminder Lead Time")
        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var offsetMinutes by remember { mutableStateOf(SettingsManager.getNotifOffsetMinutes()) }
                Text("Select how many minutes early class alerts should trigger:", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(5, 10, 15, 30, 60)
                    presets.forEach { preset ->
                        val selected = offsetMinutes == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(if (selected) colors.accent else colors.surface)
                                .border(if (selected) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.xs))
                                .clickable {
                                    offsetMinutes = preset
                                    SettingsManager.setNotifOffsetMinutes(preset)
                                    AppState.rescheduleNotifications()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset}m",
                                style = AmazeTheme.typography.smallLabel.copy(
                                    color = if (selected) Color.White else colors.textPrimary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
                AmazeButton(
                    text = "Send Test Notification",
                    onClick = { scope.launch { snackbarHostState.showSnackbar(AppState.sendTestNotification()) } },
                    variant = ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }

        SettingsGroupLabel("Sync Engine & Storage")
        SettingsGroupCard {
            val moduleStates by SyncEngine.moduleStates.collectAsState()
            val syncProgress by SyncEngine.syncProgress.collectAsState()

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.accent.copy(alpha = 0.08f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync Engine Status", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base)
                        Text(syncProgress.displayText, color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                    }
                    Text("${syncProgress.percentage.toInt()}%", fontWeight = FontWeight.Bold, color = colors.accent, fontSize = AmazeTheme.fontSize.lg)
                }

                Spacer(Modifier.height(AmazeTheme.spacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AmazeButton("Sync All Data", onClick = {
                        SyncEngine.setShowSyncDialog(true, minimized = true)
                        SyncEngine.resetAllStates()
                        AppState.loadAllData()
                    }, modifier = Modifier.weight(1f), variant = ButtonVariant.PRIMARY)
                    AmazeButton("Configure", onClick = {
                        SyncEngine.setShowSyncDialog(true, minimized = false)
                    }, modifier = Modifier.weight(1f), variant = ButtonVariant.SECONDARY)
                }

                Spacer(Modifier.height(AmazeTheme.spacing.sm))

                SettingsRow(
                    icon = Icons.Rounded.Timelapse,
                    title = "Auto-Sync Schedule",
                    subtitle = "Light & full sync profiles, recurrence & times",
                    tint = colors.accent,
                    onClick = { SyncEngine.setShowSyncDialog(true, minimized = false) }
                )
            }
        }

        SettingsGroupLabel("Sync Modules")
        SettingsGroupCard {
            val moduleStates by SyncEngine.moduleStates.collectAsState()

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                val modulesToShow = SyncModule.entries.filter { it.cacheKey != null }
                modulesToShow.forEachIndexed { index, module ->
                    val state = moduleStates[module] ?: ModuleState()
                    val dotColor = when (state.status) {
                        SyncStatus.SUCCESS -> colors.success
                        SyncStatus.ERROR -> colors.danger
                        SyncStatus.LOADING -> colors.accent
                        SyncStatus.IDLE -> colors.textMuted
                    }
                    val isLoading = state.status == SyncStatus.LOADING

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text(module.displayName, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm, modifier = Modifier.weight(1f))
                        if (state.lastSynced != null) {
                            Text(formatModuleTime(state.lastSynced), color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro)
                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                        }
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accent, strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = {
                                    SyncEngine.setShowSyncDialog(true, minimized = true)
                                    AppState.loadAllData()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, "Sync", tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    if (index < modulesToShow.lastIndex) SettingsRowDivider()
                }
            }
        }

        SettingsGroupLabel("Backup & Export")
        SettingsGroupCard {
            var exportStatus by remember { mutableStateOf<String?>(null) }
            var exportFailed by remember { mutableStateOf(false) }
            var importStatus by remember { mutableStateOf<String?>(null) }
            var importFailed by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val saveFile = rememberFileSaver()
            val fileImporter = rememberFileImporter { text ->
                if (text == null) return@rememberFileImporter
                scope.launch {
                    importStatus = "Importing backup..."
                    importFailed = false
                    withContext(Dispatchers.Default) { ExportImportManager.importFromJson(text) }
                        .onSuccess { result ->
                            importStatus = "Restored ${result.settingsImported} settings and ${result.tasksImported} tasks"
                        }
                        .onFailure { error ->
                            importFailed = true
                            importStatus = "Import failed: ${error.message ?: "invalid file"}"
                        }
                }
            }
            val doExport: (Boolean) -> Unit = { includeCache ->
                scope.launch {
                    exportStatus = "Exporting..."
                    exportFailed = false
                    val backupJson = withContext(Dispatchers.Default) {
                        runCatching { ExportImportManager.buildBackupJson(includeCache) }
                    }
                    val saved = backupJson.fold(
                        onSuccess = { json -> saveFile(ExportImportManager.backupFileName(), json.encodeToByteArray()) },
                        onFailure = { false }
                    )
                    exportFailed = !saved
                    val errorMessage = backupJson.exceptionOrNull()?.message
                    exportStatus = when {
                        saved -> "Backup saved to Downloads"
                        errorMessage != null -> "Export failed - $errorMessage"
                        else -> "Export failed - file could not be saved"
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Create a JSON backup of your preferences and tasks, or restore from an existing backup file. Credentials and session data are never included.",
                    color = colors.textSecondary,
                    fontSize = AmazeTheme.fontSize.sm
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AmazeButton("Export Custom", onClick = { doExport(false) }, modifier = Modifier.weight(1f), variant = ButtonVariant.PRIMARY)
                    AmazeButton("Export Full", onClick = { doExport(true) }, modifier = Modifier.weight(1f), variant = ButtonVariant.SECONDARY)
                }

                SettingsRow(
                    icon = Icons.Rounded.FileOpen,
                    title = "Import Backup",
                    subtitle = "Restore settings & tasks from a JSON file",
                    tint = colors.accent,
                    onClick = { fileImporter() }
                )

                val statusColor = when {
                    (exportFailed || importFailed) -> colors.danger
                    exportStatus != null || importStatus != null -> colors.success
                    else -> colors.textMuted
                }
                val statusText = exportStatus ?: importStatus
                if (statusText != null) {
                    Text(statusText, color = statusColor, fontSize = AmazeTheme.fontSize.xs)
                }
            }
        }
    }
}

private fun formatModuleTime(instant: kotlinx.datetime.Instant): String {
    val now = kotlinx.datetime.Clock.System.now()
    val diff = now - instant
    val seconds = diff.inWholeSeconds
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
