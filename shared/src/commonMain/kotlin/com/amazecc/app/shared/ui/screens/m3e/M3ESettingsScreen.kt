package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive System & Settings Hub.
 * 100% Stateless: accepts [M3ESettingsState] and emits [M3ESettingsAction].
 * Features theme mode, M3E palette presets, API target, offline cache purger,
 * privacy mode, pomodoro defaults, export/import, and the storage & diagnostics card.
 */
@Composable
fun M3ESettingsScreen(
    state: M3ESettingsState,
    onAction: (M3ESettingsAction) -> Unit,
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
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = if (state.settingsSearchQuery.isBlank()) {
                            "Appearance, Engine, Storage & Privacy"
                        } else {
                            "Filtered — the cards below match your search"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.appVersion,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }
        }

        // Settings search — the list below is filtered by the host to the
        // matching sections, so typing narrows what is rendered.
        item(key = "search") {
            SettingsSearchField(
                query = state.settingsSearchQuery,
                resultCount = state.visibleSettingsSections.size,
                onQueryChange = { onAction(M3ESettingsAction.UpdateSettingsSearch(it)) },
                onClear = { onAction(M3ESettingsAction.UpdateSettingsSearch("")) }
            )
        }

        // Settings depth cards, driven by the section list. The host passes the
        // full list when the search is empty and the filtered list otherwise.
        state.visibleSettingsSections.forEach { section ->
            item(key = section.id) {
                when (section.id) {
                    "privacy" -> PrivacyModeCard(state, onAction)
                    "pomodoro" -> PomodoroCard(state, onAction)
                    "theme" -> ThemeCard(state, onAction)
                    "storage" -> StorageCard(state, onAction)
                    "api_target" -> ApiTargetCard(state, onAction)
                    "notifications" -> NotificationsCard(state, onAction)
                    "app_icon" -> AppIconCard(state, onAction)
                    "export_import" -> ExportImportCard(state, onAction)
                    "keyboard" -> KeyboardShortcutsCard(onAction)
                    else -> Unit
                }
            }
        }

        // D-037 / D-002 / D-009: consolidated advanced settings, Amaze ID sign-in and scanner.
        item(key = "advanced_settings") {
            com.amazecc.app.shared.ui.screens.settings.AdvancedSettingsHub()
        }

        // Bottom Spacing for floating navigation bar clearance (140dp)
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
        }
    }
}

// ==========================================
// Helpers
// ==========================================

@Composable
private fun SectionToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = colors.primary, checkedTrackColor = colors.primaryContainer))
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search settings…") },
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search settings") },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = onClear) { Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Clear search") } }
        } else null,
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
    if (query.isNotBlank() && resultCount == 0) {
        Text(text = "No settings match — try another word", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

// ==========================================
// CARDS (stateless; the host owns all state)
// ==========================================

@Composable
private fun ThemeCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Theme & Display Mode", Icons.Rounded.DarkMode)
            M3EConnectedButtonGroup(
                items = M3EThemeMode.entries,
                selectedItem = state.selectedThemeMode,
                onItemSelected = { onAction(M3ESettingsAction.SelectThemeMode(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            SectionTitleRow("M3 Expressive Color Palette", Icons.Rounded.ColorLens)
            M3EConnectedButtonGroup(
                items = M3EPalettePreset.entries,
                selectedItem = state.selectedPalettePreset,
                onItemSelected = { preset ->
                    // D-114: the root AmazeM3ETheme derives its palette from AppState.accent,
                    // so this action is all that is needed for the whole tree to recompose.
                    onAction(M3ESettingsAction.SelectPalettePreset(preset))
                },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            SectionTitleRow("Timetable Class Density (Pill Style)", Icons.Rounded.Brush)
            M3EConnectedButtonGroup(
                items = com.amazecc.app.shared.state.HomePillStyle.entries,
                selectedItem = state.homePillStyle,
                onItemSelected = { onAction(M3ESettingsAction.SelectHomePillStyle(it)) },
                labelProvider = { if (it == com.amazecc.app.shared.state.HomePillStyle.COMPACT) "Compact" else "Detailed" },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PrivacyModeCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        SectionToggleRow(
            title = "Privacy Mode",
            subtitle = "Masks fee amounts in Fee Payments. App-wide blur for grades, attendance and marks is a separate privacy-mode workstream and may not be active yet.",
            checked = state.privacyModeEnabled,
            onCheckedChange = { onAction(M3ESettingsAction.TogglePrivacyMode(it)) }
        )
    }
}

@Composable
private fun PomodoroCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Pomodoro Focus & Study Settings", Icons.Rounded.Timer)
            Text(text = "Customize the default focus interval, break length and rounds for study sessions", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun StorageCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Storage & Cache (${state.cacheSizeText})", Icons.Rounded.Storage)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedButton(
                    onClick = { onAction(M3ESettingsAction.ClearCache) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.onSurface
                    )
                ) {
                    Icon(imageVector = Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Cache", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }

                ElevatedButton(
                    onClick = { onAction(M3ESettingsAction.RunDiagnostics) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                ) {
                    if (state.isDiagnosticsRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.onPrimaryContainer, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Rounded.HealthAndSafety, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Diagnostics", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            if (!state.diagnosticStatus.isNullOrBlank()) {
                Text(text = state.diagnosticStatus, style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
        }
    }
}

@Composable
private fun ApiTargetCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.CloudDone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Text("API Target", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        color = colors.onPrimaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = state.honoApiBaseUrl,
                onValueChange = { onAction(M3ESettingsAction.UpdateHonoApiUrl(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Worker Base URL", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedLabelColor = colors.primary,
                    unfocusedLabelColor = colors.onSurfaceVariant
                )
            )

            SectionTitleRow("Mount", Icons.Rounded.CloudSync)
            M3EConnectedButtonGroup(
                items = com.amazecc.app.shared.domain.ApiTarget.entries,
                selectedItem = com.amazecc.app.shared.domain.ApiTarget.fromName(state.apiTargetName),
                onItemSelected = { onAction(M3ESettingsAction.SelectApiTarget(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NotificationsCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Notifications & Reminders", Icons.Rounded.Notifications)

            SectionToggleRow(
                title = "Class Schedule Alerts",
                subtitle = "Remind before upcoming lecture starts",
                checked = state.classAlertsEnabled,
                onCheckedChange = { onAction(M3ESettingsAction.ToggleClassAlerts(it)) }
            )

            // Class-reminder lead time (web parity — PushNotificationManager).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Class Reminder Lead", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                M3EConnectedButtonGroup(
                    items = listOf(10, 15, 30),
                    selectedItem = state.classReminderLeadMinutes,
                    onItemSelected = { onAction(M3ESettingsAction.UpdateClassReminderLeadMinutes(it)) },
                    labelProvider = { "$it min before" },
                    modifier = Modifier.weight(0.6f)
                )
            }

            SectionToggleRow(
                title = "Low Attendance Warnings",
                subtitle = "Alert when any course drops below 75%",
                checked = state.attendanceAlertsEnabled,
                onCheckedChange = { onAction(M3ESettingsAction.ToggleAttendanceAlerts(it)) }
            )

            SectionToggleRow(
                title = "Exam & Seating Notifications",
                subtitle = "Remind CAT / FAT schedule 24h prior",
                checked = state.examRemindersEnabled,
                onCheckedChange = { onAction(M3ESettingsAction.ToggleExamReminders(it)) }
            )
        }
    }

    // D-120: these are real on-device (AlarmManager) controls, not FCM push.
    // Quiet hours is checked when a reminder is scheduled; alarms already set
    // still fire. The test button is disabled while quiet hours is on so we
    // never report "sent" for an alert that the quiet-hours rule suppressed.
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Sound & Quiet Hours", Icons.Rounded.Brightness4)
            SectionToggleRow(
                title = "Quiet Hours Mode",
                subtitle = "New on-device reminders are not scheduled during the quiet window",
                checked = state.quietHoursEnabled,
                onCheckedChange = { onAction(M3ESettingsAction.ToggleQuietHours(it)) }
            )
            if (state.quietHoursEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Quiet Hours Range:", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietHoursTimeField(state.quietHoursStart, true, onAction)
                        Text(text = "to", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        QuietHoursTimeField(state.quietHoursEnd, false, onAction)
                    }
                }
            }
            Text(
                text = "Reminders are local alarms on this device, not server push. Quiet Hours stops new alarms from being scheduled inside the window; alarms already scheduled still fire. The test alert uses the same local path.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }

    ElevatedButton(
        onClick = { onAction(M3ESettingsAction.SendTestNotification) },
        enabled = !state.quietHoursEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface
        )
    ) {
        Icon(imageVector = Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (state.quietHoursEnabled) "Test alerts muted during Quiet Hours" else "Send Test Notification",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
    Text(
        text = if (state.quietHoursEnabled) {
            "Turn off Quiet Hours to send a test alert."
        } else {
            "Sends a real local notification about 5 seconds after you tap (notification permission required)."
        },
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
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
                onAction(if (isStart) M3ESettingsAction.UpdateQuietHoursStart(normalized) else M3ESettingsAction.UpdateQuietHoursEnd(normalized))
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

@Composable
private fun AppIconCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Application Icon", Icons.Rounded.Brush)
            Text(
                text = "Choose which launcher icon is shown on this device. The change takes effect on the home screen immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            M3EConnectedButtonGroup(
                items = com.amazecc.app.shared.utils.AppIconTheme.entries,
                selectedItem = state.appIconTheme,
                onItemSelected = { onAction(M3ESettingsAction.SelectAppIconTheme(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExportImportCard(state: M3ESettingsState, onAction: (M3ESettingsAction) -> Unit) {
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitleRow("Backup / Export Settings", Icons.Rounded.Upload)
            SettingsRow("Save preferences", "Backup application preferences and layout options to a file", Icons.Rounded.Download) {
                onAction(M3ESettingsAction.ExportSettings)
            }
            SettingsRow("Restore / Import Settings", "Import and restore configuration from a settings backup JSON file", Icons.Rounded.Upload) {
                onAction(M3ESettingsAction.ImportSettings)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickableA11y(onClickLabel = "Open $title", onClick = onClick)
            .heightIn(min = 48.dp)
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** The app's actual physical-keyboard bindings (App.kt `handleAppGlobalShortcut`). */
@Composable
private fun KeyboardShortcutsCard(onAction: (M3ESettingsAction) -> Unit) {
    val colors = M3ETheme.colors
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
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitleRow("Keyboard Shortcuts", Icons.Rounded.Keyboard)
            shortcuts.forEach { (key, description) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = key, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ElevatedButton(
                onClick = { onAction(M3ESettingsAction.OpenShortcutsHelp) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface
                )
            ) {
                Text("View all shortcuts", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun SectionTitleRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = M3ETheme.colors.primary, modifier = Modifier.size(20.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = M3ETheme.colors.onSurface)
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3ESettingsScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ESettingsScreen(
            state = createMockSettingsState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3ESettingsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ESettingsScreen(
            state = createMockSettingsState(),
            onAction = {}
        )
    }
}

private fun createMockSettingsState() = M3ESettingsState(
    selectedThemeMode = M3EThemeMode.DARK_OLED,
    selectedPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
    honoApiBaseUrl = "https://hono.amazecc.com",
    cacheSizeText = "14.2 MB",
    isDiagnosticsRunning = false,
    diagnosticStatus = "Not checked",
    visibleSettingsSections = com.amazecc.app.shared.domain.overhaul.allSettingsSections(),
    privacyModeEnabled = false,
    classReminderLeadMinutes = 15,
    quietHoursEnabled = false,
    appIconTheme = com.amazecc.app.shared.utils.AppIconTheme.DEFAULT
)
