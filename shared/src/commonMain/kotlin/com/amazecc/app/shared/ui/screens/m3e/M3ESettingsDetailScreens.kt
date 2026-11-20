package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.ApiTarget
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.state.HomePillStyle
import com.amazecc.app.shared.ui.m3e.components.SettingsActionRow
import com.amazecc.app.shared.ui.m3e.components.SettingsGroupHeader
import com.amazecc.app.shared.ui.m3e.components.SettingsInfoRow
import com.amazecc.app.shared.ui.m3e.components.SettingsSegmentedRow
import com.amazecc.app.shared.ui.m3e.components.SettingsToggleRow
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.AppIconTheme

/**
 * Body of one settings detail screen in the Android-Settings-style master/detail flow.
 *
 * Worker A owns the [com.amazecc.app.shared.ui.m3e.components.SettingsDetailScaffold]
 * (back arrow + title + 140 dp nav clearance) and calls this function from its content
 * slot. Every setting here is a flat row under a [SettingsGroupHeader]: no nested
 * cards, no stacked padding — each primitive owns its own 16 dp start edge and divider.
 */
@Composable
fun M3ESettingsDetailScreen(
    id: String,
    state: M3ESettingsState,
    onAction: (M3ESettingsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        when (id) {
            "theme" -> {
                SettingsGroupHeader(title = "Appearance")
                SettingsSegmentedRow(
                    title = "Theme mode",
                    items = M3EThemeMode.entries,
                    selectedItem = state.selectedThemeMode,
                    onItemSelected = { onAction(M3ESettingsAction.SelectThemeMode(it)) },
                    labelProvider = { it.label },
                    icon = Icons.Rounded.DarkMode
                )
                SettingsSegmentedRow(
                    title = "Palette",
                    items = M3EPalettePreset.entries,
                    selectedItem = state.selectedPalettePreset,
                    onItemSelected = { onAction(M3ESettingsAction.SelectPalettePreset(it)) },
                    labelProvider = { it.label },
                    icon = Icons.Rounded.ColorLens
                )
                SettingsSegmentedRow(
                    title = "Timetable pill style",
                    items = HomePillStyle.entries,
                    selectedItem = state.homePillStyle,
                    onItemSelected = { onAction(M3ESettingsAction.SelectHomePillStyle(it)) },
                    labelProvider = { if (it == HomePillStyle.COMPACT) "Compact" else "Detailed" },
                    icon = Icons.Rounded.Brush
                )

                SettingsGroupHeader(title = "Identity")
                AppIconChoiceRow(state = state, onAction = onAction)
            }

            "privacy" -> {
                SettingsGroupHeader(title = "Privacy")
                SettingsToggleRow(
                    title = "Privacy mode",
                    subtitle = "Masks fee amounts in Fee Payments. App-wide blur for grades, attendance and marks is a separate privacy-mode workstream and may not be active yet.",
                    checked = state.privacyModeEnabled,
                    onCheckedChange = { onAction(M3ESettingsAction.TogglePrivacyMode(it)) }
                )
            }

            "pomodoro" -> {
                SettingsGroupHeader(title = "Focus defaults")
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PomodoroNumberField(
                            label = "Focus (min)",
                            value = state.pomodoroFocusMinutes,
                            min = 5,
                            max = 120,
                            modifier = Modifier.weight(1f),
                            onCommit = { onAction(M3ESettingsAction.UpdatePomodoroFocusMinutes(it)) }
                        )
                        PomodoroNumberField(
                            label = "Break (min)",
                            value = state.pomodoroBreakMinutes,
                            min = 1,
                            max = 60,
                            modifier = Modifier.weight(1f),
                            onCommit = { onAction(M3ESettingsAction.UpdatePomodoroBreakMinutes(it)) }
                        )
                        PomodoroNumberField(
                            label = "Rounds",
                            value = state.pomodoroRounds,
                            min = 1,
                            max = 16,
                            modifier = Modifier.weight(1f),
                            onCommit = { onAction(M3ESettingsAction.UpdatePomodoroRounds(it)) }
                        )
                    }
                }
            }

            "storage" -> {
                SettingsGroupHeader(title = "Storage")
                SettingsInfoRow(label = "Cache size", value = state.cacheSizeText)
                SettingsActionRow(
                    title = "Clear Cache",
                    icon = Icons.Rounded.CleaningServices,
                    onClick = { onAction(M3ESettingsAction.ClearCache) }
                )
                SettingsActionRow(
                    title = "Run diagnostics",
                    icon = Icons.Rounded.HealthAndSafety,
                    busy = state.isDiagnosticsRunning,
                    onClick = { onAction(M3ESettingsAction.RunDiagnostics) }
                )
                val status = state.diagnosticStatus
                if (!status.isNullOrBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = M3ETheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            "api_target" -> {
                SettingsGroupHeader(title = "Backend")
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = state.honoApiBaseUrl,
                        onValueChange = { onAction(M3ESettingsAction.UpdateHonoApiUrl(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Worker Base URL", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = M3ETheme.colors.onSurface,
                            unfocusedTextColor = M3ETheme.colors.onSurface,
                            focusedContainerColor = M3ETheme.colors.surfaceContainerHigh,
                            unfocusedContainerColor = M3ETheme.colors.surfaceContainerLow,
                            focusedBorderColor = M3ETheme.colors.primary,
                            unfocusedBorderColor = M3ETheme.colors.outlineVariant,
                            focusedLabelColor = M3ETheme.colors.primary,
                            unfocusedLabelColor = M3ETheme.colors.onSurfaceVariant
                        )
                    )
                }
                SettingsSegmentedRow(
                    title = "Mount",
                    items = ApiTarget.entries,
                    selectedItem = ApiTarget.fromName(state.apiTargetName),
                    onItemSelected = { onAction(M3ESettingsAction.SelectApiTarget(it)) },
                    labelProvider = { it.label },
                    icon = Icons.Rounded.CloudSync
                )
            }

            "notifications" -> {
                SettingsGroupHeader(title = "Alerts")
                SettingsToggleRow(
                    title = "Class Schedule Alerts",
                    subtitle = "Remind before upcoming lecture starts",
                    checked = state.classAlertsEnabled,
                    onCheckedChange = { onAction(M3ESettingsAction.ToggleClassAlerts(it)) }
                )
                SettingsSegmentedRow(
                    title = "Class reminder lead",
                    items = listOf(10, 15, 30),
                    selectedItem = state.classReminderLeadMinutes,
                    onItemSelected = { onAction(M3ESettingsAction.UpdateClassReminderLeadMinutes(it)) },
                    labelProvider = { "$it min before" }
                )
                SettingsToggleRow(
                    title = "Low Attendance Warnings",
                    subtitle = "Alert when any course drops below 75%",
                    checked = state.attendanceAlertsEnabled,
                    onCheckedChange = { onAction(M3ESettingsAction.ToggleAttendanceAlerts(it)) }
                )
                SettingsToggleRow(
                    title = "Exam & Seating Notifications",
                    subtitle = "Remind CAT / FAT schedule 24h prior",
                    checked = state.examRemindersEnabled,
                    onCheckedChange = { onAction(M3ESettingsAction.ToggleExamReminders(it)) }
                )

                SettingsGroupHeader(title = "Sound & quiet hours")
                SettingsToggleRow(
                    title = "Quiet Hours Mode",
                    subtitle = "New on-device reminders are not scheduled during the quiet window",
                    checked = state.quietHoursEnabled,
                    onCheckedChange = { onAction(M3ESettingsAction.ToggleQuietHours(it)) },
                    icon = Icons.Rounded.Brightness4
                )
                if (state.quietHoursEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuietHoursTimeField(
                                value = state.quietHoursStart,
                                isStart = true,
                                onAction = onAction
                            )
                            Text(
                                text = "to",
                                style = MaterialTheme.typography.labelMedium,
                                color = M3ETheme.colors.onSurfaceVariant
                            )
                            QuietHoursTimeField(
                                value = state.quietHoursEnd,
                                isStart = false,
                                onAction = onAction
                            )
                        }
                    }
                }
                Text(
                    text = "Reminders are local alarms on this device, not server push. Quiet Hours stops new alarms from being scheduled inside the window; alarms already scheduled still fire. The test alert uses the same local path.",
                    style = MaterialTheme.typography.labelSmall,
                    color = M3ETheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                SettingsActionRow(
                    title = "Send test notification",
                    subtitle = if (state.quietHoursEnabled) {
                        "Turn off Quiet Hours to send a test alert."
                    } else {
                        "Sends a real local notification about 5 seconds after you tap (notification permission required)."
                    },
                    icon = Icons.Rounded.NotificationsActive,
                    enabled = !state.quietHoursEnabled,
                    onClick = { onAction(M3ESettingsAction.SendTestNotification) }
                )
            }

            "app_icon" -> {
                SettingsGroupHeader(title = "Identity")
                AppIconChoiceRow(state = state, onAction = onAction)
            }

            "export_import" -> {
                SettingsGroupHeader(title = "Backup")
                SettingsActionRow(
                    title = "Save preferences",
                    subtitle = "Backup application preferences and layout options to a file",
                    icon = Icons.Rounded.Download,
                    onClick = { onAction(M3ESettingsAction.ExportSettings) }
                )
                SettingsActionRow(
                    title = "Restore / Import Settings",
                    subtitle = "Import and restore configuration from a settings backup JSON file",
                    icon = Icons.Rounded.Upload,
                    onClick = { onAction(M3ESettingsAction.ImportSettings) }
                )
            }

            "keyboard" -> {
                SettingsGroupHeader(title = "Physical keyboard")
                val shortcuts = listOf(
                    "Ctrl / Cmd + K" to "Open command palette",
                    "/" to "Open command palette",
                    "?" to "Open this shortcuts help",
                    "g then letter" to "Jump to a screen (e.g. g, a → Attendance)",
                    "Alt + P" to "Toggle privacy mode",
                    "Alt + T" to "Toggle light / dark theme",
                    "Alt + B" to "Toggle hidden CGPA",
                    "Alt + letter" to "Jump to a screen with an Alt binding",
                    "Esc" to "Dismiss overlay / go back (Android)"
                )
                shortcuts.forEach { (key, description) ->
                    SettingsInfoRow(label = key, value = description)
                }
                SettingsActionRow(
                    title = "View all shortcuts",
                    icon = Icons.Rounded.Keyboard,
                    onClick = { onAction(M3ESettingsAction.OpenShortcutsHelp) }
                )
            }

            "advanced" -> {
                com.amazecc.app.shared.ui.screens.settings.AdvancedSettingsHub()
            }

            else -> {
                Text(
                    text = "Unknown settings section",
                    style = MaterialTheme.typography.bodyMedium,
                    color = M3ETheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AppIconChoiceRow(
    state: M3ESettingsState,
    onAction: (M3ESettingsAction) -> Unit
) {
    SettingsSegmentedRow(
        title = "App icon",
        items = AppIconTheme.entries,
        selectedItem = state.appIconTheme,
        onItemSelected = { onAction(M3ESettingsAction.SelectAppIconTheme(it)) },
        labelProvider = { it.label },
        icon = Icons.Rounded.Brush
    )
}

@Composable
private fun PomodoroNumberField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    onCommit: (Int) -> Unit
) {
    val colors = M3ETheme.colors
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { raw ->
                val parsed = raw.toIntOrNull()
                if (parsed != null) onCommit(parsed.coerceIn(min, max))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun QuietHoursTimeField(
    value: String,
    isStart: Boolean,
    onAction: (M3ESettingsAction) -> Unit
) {
    val colors = M3ETheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            // Keep the HH:mm shape the web stores; a 6-char `HHMM` from the time picker
            // is normalized on commit instead of being rejected.
            val normalized = normalizeTimeInput(text)
            if (normalized != null) {
                onAction(
                    if (isStart) {
                        M3ESettingsAction.UpdateQuietHoursStart(normalized)
                    } else {
                        M3ESettingsAction.UpdateQuietHoursEnd(normalized)
                    }
                )
            }
        },
        modifier = Modifier.width(100.dp),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerLow,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant
        )
    )
}

/** Accepts `HH:mm` or `HHMM`; returns `HH:mm` or null while unparseable. */
private fun normalizeTimeInput(text: String): String? {
    val digits = text.filter { it.isDigit() }
    if (digits.length != 4) return null
    return "${digits.take(2)}:${digits.takeLast(2)}"
}
