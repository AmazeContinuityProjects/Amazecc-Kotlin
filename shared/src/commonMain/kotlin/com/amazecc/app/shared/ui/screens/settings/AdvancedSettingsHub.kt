package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.ApiTarget
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.AttendanceDisplayMode
import com.amazecc.app.shared.state.HomePillStyle
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.ui.screens.m3e.M3EAmazeIdSignInScreen
import com.amazecc.app.shared.ui.screens.m3e.M3EDocumentScannerScreen
import com.amazecc.app.shared.utils.ExportImportManager
import com.amazecc.app.shared.utils.rememberFileImporter
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.vtop.VtopSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * D-037 — post-onboarding settings reachability.
 *
 * These controls were previously scattered across onboarding and the unrouted
 * legacy `SettingsScreen`. This hub is self-contained: it reads/writes
 * AppState, SettingsManager and SyncEngine directly, so M3E Settings only has
 * to render it once (see the wiring note in WEB_PARITY_DRIFT_REGISTER.md). It
 * also hosts the D-002 Amaze ID sign-in and D-009 document scanner so both are
 * reachable without touching the router.
 */
@Composable
fun AdvancedSettingsHub(modifier: Modifier = Modifier) {
    AmazeM3ETheme {
        val colors = M3ETheme.colors
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<String?>(null) }
        var showAuth by remember { mutableStateOf(false) }
        var showScanner by remember { mutableStateOf(false) }
        var showLogoutConfirm by remember { mutableStateOf(false) }
        var showClearConfirm by remember { mutableStateOf(false) }

        val saveFile = rememberFileSaver()
        val importFile = rememberFileImporter { text ->
            if (text == null) return@rememberFileImporter
            scope.launch {
                val result = withContext(Dispatchers.Default) { ExportImportManager.importFromJson(text) }
                status = result.fold(
                    onSuccess = { counts -> "Restored ${counts.settingsImported} settings and ${counts.tasksImported} tasks." },
                    onFailure = { "Import failed: ${it.message ?: "invalid backup file"}" },
                )
            }
        }

        val uiScale by AppState.uiScale.collectAsState()
        val cgpaHidden by AppState.cgpaHidden.collectAsState()
        val gpaGoal by AppState.gpaGoal.collectAsState()
        val customTarget by AppState.customAttendanceTarget.collectAsState()
        val busSubscriber by AppState.isBusSubscriber.collectAsState()
        val attendanceMode by AppState.attendanceDisplayMode.collectAsState()
        val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
        val homePillStyle by AppState.homePillStyle.collectAsState()
        val homeTasksInline by AppState.homeTasksInline.collectAsState()
        val animationsEnabled by AppState.animationsEnabled.collectAsState()
        val hapticEnabled by AppState.hapticEnabled.collectAsState()
        val privacyMode by AppState.privacyMode.collectAsState()
        val selectedSemester by AppState.selectedSemester.collectAsState()
        val semesterMap by AppState.semesterMap.collectAsState()
        var apiTargetName by remember { mutableStateOf(SettingsManager.getApiTargetName()) }
        var vtopSource by remember { mutableStateOf(AmazeClient.vtopSource) }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HubCard("Display & Layout", Icons.Rounded.Visibility, colors) {
                Text("UI scale · ${(uiScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                Slider(
                    value = uiScale,
                    onValueChange = { AppState.changeUiScale(it.coerceIn(0.75f, 1.4f)) },
                    valueRange = 0.75f..1.4f,
                    steps = 12,
                )
                ToggleRow("Animations", "Respect the in-app motion preference", animationsEnabled) { AppState.setAnimationsEnabled(it) }
                ToggleRow("Haptic feedback", "Vibration on interactive controls", hapticEnabled) { AppState.setHapticEnabled(it) }
                ToggleRow("Privacy mode", "Mask fee amounts across the app", privacyMode) { AppState.setPrivacyMode(it) }
            }

            HubCard("Dashboard", Icons.Rounded.DashboardCustomize, colors) {
                SectionLabel("Class card density")
                M3EConnectedButtonGroup(
                    items = HomePillStyle.entries,
                    selectedItem = homePillStyle,
                    onItemSelected = { AppState.setHomePillStyle(it) },
                    labelProvider = { if (it == HomePillStyle.COMPACT) "Compact" else "Detailed" },
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Tasks on home", "Show today's tasks under the timetable", homeTasksInline) { AppState.setHomeTasksInline(it) }
            }

            HubCard("Bottom Navigation", Icons.Rounded.Navigation, colors) {
                Text("Pinned tabs (max 4). Changes apply to the main tab bar immediately.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                val candidates = listOf(Screen.HOME, Screen.ATTENDANCE, Screen.ACADEMICS, Screen.TASKS, Screen.CALENDAR, Screen.SOCIAL, Screen.PROFILE)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidates.forEach { screen ->
                        val selected = screen in pinnedTabs
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val updated = if (selected) pinnedTabs - screen else pinnedTabs + screen
                                if (updated.size <= 4) AppState.setPinnedNavTabs(updated)
                                else status = "At most four tabs can be pinned."
                            },
                            label = { Text(screen.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            HubCard("Academics & Semester", Icons.Rounded.School, colors) {
                SectionLabel("Target semester")
                val semIds = semesterMap.keys.toList().sortedDescending()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    semIds.take(6).forEach { id ->
                        FilterChip(
                            selected = id == selectedSemester,
                            onClick = { AppState.selectSemester(id) },
                            label = { Text(semesterMap[id] ?: id, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                ToggleRow("Hide CGPA", "Mask the CGPA on grades and home", cgpaHidden) { AppState.setCgpaHidden(it) }
                var goalDraft by remember(gpaGoal) { mutableStateOf(gpaGoal) }
                OutlinedTextField(
                    value = goalDraft,
                    onValueChange = { goalDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GPA goal") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = hubFieldColors(colors),
                    trailingIcon = {
                        TextButton(onClick = {
                            val value = goalDraft.toDoubleOrNull()
                            if (value != null && value in 0.0..10.0) {
                                AppState.setGpaGoal(value.toString())
                                status = "GPA goal saved."
                            } else status = "Enter a GPA between 0 and 10."
                        }) { Text("Save") }
                    },
                )
                ToggleRow("Bus subscriber", "Use the 85% attendance target", busSubscriber) { AppState.setBusSubscriber(it) }
                val targetActive = customTarget != null
                ToggleRow(
                    "Custom attendance target",
                    if (targetActive) "Override the default target" else "Using the default target",
                    targetActive,
                ) { enabled -> AppState.setCustomAttendanceTarget(if (enabled) 75f else null) }
                if (targetActive) {
                    Text("Target · ${(customTarget ?: 75f).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                    Slider(
                        value = customTarget ?: 75f,
                        onValueChange = { AppState.setCustomAttendanceTarget(it.coerceIn(40f, 100f)) },
                        valueRange = 40f..100f,
                        steps = 11,
                    )
                }
                SectionLabel("Attendance display")
                M3EConnectedButtonGroup(
                    items = AttendanceDisplayMode.entries,
                    selectedItem = attendanceMode,
                    onItemSelected = { AppState.setAttendanceDisplayMode(it) },
                    labelProvider = { if (it == AttendanceDisplayMode.PERCENTAGE) "Percentage" else "Fraction" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HubCard("Credentials", Icons.Rounded.AccountCircle, colors) {
                val username = SettingsManager.getString(SettingsManager.KEY_USERNAME)
                val moodle = SettingsManager.getMoodleCredentials()
                val library = SettingsManager.getLibraryCredentials()
                InfoRow("VTOP", if (username.isNotBlank()) username else "Not saved")
                InfoRow("Moodle", moodle?.first ?: "Not linked")
                InfoRow("Library", library?.first ?: "Not linked")
                ElevatedButton(
                    onClick = {
                        SettingsManager.remove(SettingsManager.KEY_USERNAME)
                        SettingsManager.remove(SettingsManager.KEY_PASSWORD)
                        SettingsManager.clearMoodleCredentials()
                        SettingsManager.clearLibraryCredentials()
                        status = "Saved logins forgotten. Local academic data was kept."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface),
                ) { Text("Forget my logins (keeps local data)") }
            }

            HubCard("Data, Sync & Alerts", Icons.Rounded.Sync, colors) {
                ToggleRow(
                    "Manual captcha",
                    "Type the VTOP captcha yourself instead of auto-solving",
                    SettingsManager.isVtopManualCaptcha(),
                ) { SettingsManager.setVtopManualCaptcha(it) }
                SectionLabel("VTOP source")
                M3EConnectedButtonGroup(
                    items = VtopSource.entries.toList(),
                    selectedItem = vtopSource,
                    onItemSelected = {
                        AmazeClient.setVtopSource(it)
                        vtopSource = it
                    },
                    labelProvider = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("API target")
                M3EConnectedButtonGroup(
                    items = ApiTarget.entries,
                    selectedItem = ApiTarget.fromName(apiTargetName),
                    onItemSelected = {
                        AmazeClient.setApiTarget(it)
                        apiTargetName = SettingsManager.getApiTargetName()
                    },
                    labelProvider = { it.label },
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("Sync modules")
                SyncModule.entries.filter { it.cacheKey != null }.forEach { module ->
                    ToggleRow(module.displayName, "Include in sync profiles", SyncEngine.isModuleEnabled(module)) {
                        SyncEngine.setModuleEnabled(module, it)
                    }
                }
            }

            HubCard("Backup & Transfer", Icons.Rounded.Description, colors) {
                ElevatedButton(
                    onClick = {
                        scope.launch {
                            val json = withContext(Dispatchers.Default) { runCatching { ExportImportManager.buildBackupJson(false) } }
                            status = json.fold(
                                onSuccess = { text ->
                                    if (saveFile(ExportImportManager.backupFileName(), text.encodeToByteArray())) "Backup saved to Downloads."
                                    else "Backup could not be saved."
                                },
                                onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) { Text("Export settings & tasks") }
                ElevatedButton(onClick = { importFile() }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                    Text("Import backup")
                }
            }

            // D-002 / D-009 reachability without router changes.
            HubCard("Account & Tools", Icons.Rounded.Login, colors) {
                ActionRow("Sign in with Amaze ID", "OIDC device code / PKCE / token entry", Icons.Rounded.Login) { showAuth = true }
                ActionRow("Document scanner", "Import → crop → edit → PDF", Icons.Rounded.CameraAlt) { showScanner = true }
            }

            HubCard("Danger Zone", Icons.Rounded.DeleteSweep, colors, danger = true) {
                ActionRow("Clear local caches", "Remove cached academic and module data", Icons.Rounded.DeleteSweep) { showClearConfirm = true }
                ActionRow("Log out", "End the VTOP session and return to sign-in", Icons.Rounded.Login) { showLogoutConfirm = true }
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                TextButton(onClick = { status = null }) { Text("Dismiss") }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showAuth) {
            M3EDismissibleSheet(
                isOpen = showAuth,
                onDismiss = { showAuth = false },
                title = "Sign in with Amaze ID"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    M3EAmazeIdSignInScreen()
                    TextButton(onClick = { showAuth = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
        if (showScanner) {
            M3EDismissibleSheet(
                isOpen = showScanner,
                onDismiss = { showScanner = false },
                title = "Document scanner"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    M3EDocumentScannerScreen()
                    TextButton(onClick = { showScanner = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
        if (showLogoutConfirm) {
            M3EDismissibleSheet(
                isOpen = showLogoutConfirm,
                onDismiss = { showLogoutConfirm = false },
                title = "Log out?"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("You will be returned to the sign-in screen.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
                        TextButton(onClick = { showLogoutConfirm = false; AppState.logout() }) { Text("Log out") }
                    }
                }
            }
        }
        if (showClearConfirm) {
            M3EDismissibleSheet(
                isOpen = showClearConfirm,
                onDismiss = { showClearConfirm = false },
                title = "Clear local cache?"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Removes cached attendance, marks, calendar and module data but keeps credentials and preferences.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            SettingsManager.clearLocalCache()
                            status = "Local cache cleared."
                            showClearConfirm = false
                        }) { Text("Clear") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    icon: ImageVector,
    colors: M3EColorPalette,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = if (danger) colors.error else colors.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = if (danger) colors.error else colors.onSurface)
            }
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = M3ETheme.colors.onSurfaceVariant)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = M3ETheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary, checkedTrackColor = colors.primaryContainer),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = M3ETheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun hubFieldColors(colors: M3EColorPalette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.onSurface,
    unfocusedTextColor = colors.onSurface,
    focusedContainerColor = colors.surfaceContainerHigh,
    unfocusedContainerColor = colors.surfaceContainerLow,
    focusedBorderColor = colors.primary,
    unfocusedBorderColor = colors.outlineVariant,
    focusedLabelColor = colors.primary,
    unfocusedLabelColor = colors.onSurfaceVariant,
)
