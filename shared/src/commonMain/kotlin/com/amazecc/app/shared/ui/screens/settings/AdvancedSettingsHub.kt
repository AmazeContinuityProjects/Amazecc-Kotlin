package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.ApiTarget
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.AttendanceDisplayMode
import com.amazecc.app.shared.state.HomePillStyle
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.SettingsActionRow
import com.amazecc.app.shared.ui.m3e.components.SettingsGroupHeader
import com.amazecc.app.shared.ui.m3e.components.SettingsInfoRow
import com.amazecc.app.shared.ui.m3e.components.SettingsSegmentedRow
import com.amazecc.app.shared.ui.m3e.components.SettingsToggleRow
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.ui.screens.m3e.M3EAmazeIdSignInScreen
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
 * also hosts the D-002 Amaze ID sign-in so it is reachable without touching the router.
 *
 * The hub is now rendered *inside* a settings detail screen, so it deliberately
 * owns no cards and no nested scrolling: groups are [SettingsGroupHeader]s plus
 * flat rows that each own exactly 16 dp of horizontal padding.
 */
@Composable
fun AdvancedSettingsHub(modifier: Modifier = Modifier) {
    AmazeM3ETheme {
        val colors = M3ETheme.colors
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<String?>(null) }
        var showAuth by remember { mutableStateOf(false) }
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

        // D-121: a corrupt/legacy persisted scale must never reach Slider, which requires
        // finite values. Coerce at the read site so the control cannot crash composition.
        val rawUiScale by AppState.uiScale.collectAsState()
        val uiScale = if (rawUiScale.isFinite()) rawUiScale.coerceIn(0.75f, 1.4f) else 1.0f
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
        val residentialStatus by AppState.residentialStatus.collectAsState()
        val selectedSemester by AppState.selectedSemester.collectAsState()
        val semesterMap by AppState.semesterMap.collectAsState()
        var apiTargetName by remember { mutableStateOf(SettingsManager.getApiTargetName()) }
        var vtopSource by remember { mutableStateOf(AmazeClient.vtopSource) }

        // D-121: no verticalScroll here. The parent settings detail screen already
        // scrolls; a nested scroll container is a known source of nested-scroll /
        // intrinsic-measurement failures inside a lazily composed item. Group headers
        // add their own top spacing, so only a small gap is needed between groups.
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsGroupHeader("Display & Layout")
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("UI scale · ${(uiScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                Slider(
                    value = uiScale,
                    onValueChange = { AppState.changeUiScale(it.coerceIn(0.75f, 1.4f)) },
                    valueRange = 0.75f..1.4f,
                    steps = 12,
                )
            }
            SettingsToggleRow(
                title = "Animations",
                subtitle = "Respect the in-app motion preference",
                checked = animationsEnabled,
                onCheckedChange = { AppState.setAnimationsEnabled(it) },
            )
            SettingsToggleRow(
                title = "Haptic feedback",
                subtitle = "Vibration on interactive controls",
                checked = hapticEnabled,
                onCheckedChange = { AppState.setHapticEnabled(it) },
            )
            SettingsToggleRow(
                title = "Privacy mode",
                subtitle = "Mask fee amounts across the app",
                checked = privacyMode,
                onCheckedChange = { AppState.setPrivacyMode(it) },
            )

            SettingsGroupHeader("Dashboard")
            SettingsSegmentedRow(
                title = "Class card density",
                items = HomePillStyle.entries,
                selectedItem = homePillStyle,
                onItemSelected = { AppState.setHomePillStyle(it) },
                labelProvider = { if (it == HomePillStyle.COMPACT) "Compact" else "Detailed" },
            )
            SettingsToggleRow(
                title = "Tasks on home",
                subtitle = "Show today's tasks under the timetable",
                checked = homeTasksInline,
                onCheckedChange = { AppState.setHomeTasksInline(it) },
            )

            SettingsGroupHeader("Bottom Navigation")
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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

            SettingsGroupHeader("Academics & Semester")
            SectionLabel("Target semester")
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
            }
            // D-122: web "Academic Calendar" (settings.calendarType). The
            // id/name pairs are the same eight the web ProfilePage offers.
            val calendars = com.amazecc.app.shared.api.AmazeClient.calendarTypes
            var selectedCalendar by remember {
                mutableStateOf(SettingsManager.getPreferredCalendar() ?: calendars.first().second)
            }
            SettingsSegmentedRow(
                title = "Default academic calendar",
                items = calendars.map { it.second },
                selectedItem = selectedCalendar,
                onItemSelected = { name ->
                    val id = calendars.first { it.second == name }.first
                    SettingsManager.savePreferredCalendar(name)
                    SettingsManager.updateWebSetting { it.copy(calendarType = id) }
                    selectedCalendar = name
                },
                labelProvider = { it },
            )

            // D-122: web "Target Attendance Goal" (75/80/85/90). Writes the
            // same AppState target the attendance derivers read, so the bunk
            // margin and safe/critical badges change immediately.
            val targetOptions = listOf(75, 80, 85, 90)
            val activeTarget = AppState.effectiveAttendanceTarget(busSubscriber).toInt()
            SettingsSegmentedRow(
                title = "Attendance target",
                items = targetOptions,
                selectedItem = activeTarget,
                onItemSelected = { pct ->
                    AppState.setBusSubscriber(false)
                    AppState.setCustomAttendanceTarget(pct.toFloat())
                    SettingsManager.updateWebSetting { it.copy(targetAttendance = pct) }
                },
                labelProvider = { "$it%" },
            )

            // D-122: web "Hostel vs Day Scholar" (settings.residentialStatus).
            SettingsSegmentedRow(
                title = "Residential status",
                items = listOf("hosteller", "dayscholar"),
                selectedItem = residentialStatus.lowercase(),
                onItemSelected = { status ->
                    AppState.setResidentialStatus(status)
                    SettingsManager.updateWebSetting { it.copy(residentialStatus = status) }
                },
                labelProvider = { it.replaceFirstChar { c -> c.uppercase() } },
            )

            SettingsToggleRow(
                title = "Hide CGPA",
                subtitle = "Mask the CGPA on grades and home",
                checked = cgpaHidden,
                onCheckedChange = { AppState.setCgpaHidden(it) },
            )
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
            }
            SettingsToggleRow(
                title = "Bus subscriber",
                subtitle = "Use the 85% attendance target",
                checked = busSubscriber,
                onCheckedChange = { AppState.setBusSubscriber(it) },
            )
            val targetActive = customTarget != null
            SettingsToggleRow(
                title = "Custom attendance target",
                subtitle = if (targetActive) "Override the default target" else "Using the default target",
                checked = targetActive,
                onCheckedChange = { enabled -> AppState.setCustomAttendanceTarget(if (enabled) 75f else null) },
            )
            if (targetActive) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Target · ${(customTarget ?: 75f).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                    Slider(
                        value = customTarget ?: 75f,
                        onValueChange = { AppState.setCustomAttendanceTarget(it.coerceIn(40f, 100f)) },
                        valueRange = 40f..100f,
                        steps = 11,
                    )
                }
            }
            SettingsSegmentedRow(
                title = "Attendance display",
                items = AttendanceDisplayMode.entries,
                selectedItem = attendanceMode,
                onItemSelected = { AppState.setAttendanceDisplayMode(it) },
                labelProvider = { if (it == AttendanceDisplayMode.PERCENTAGE) "Percentage" else "Fraction" },
            )

            SettingsGroupHeader("Credentials")
            val username = SettingsManager.getString(SettingsManager.KEY_USERNAME)
            val moodle = SettingsManager.getMoodleCredentials()
            val library = SettingsManager.getLibraryCredentials()
            SettingsInfoRow(label = "VTOP", value = if (username.isNotBlank()) username else "Not saved")
            SettingsInfoRow(label = "Moodle", value = moodle?.first ?: "Not linked")
            SettingsInfoRow(label = "Library", value = library?.first ?: "Not linked")
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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

            SettingsGroupHeader("Data, Sync & Alerts")
            SettingsToggleRow(
                title = "Manual captcha",
                subtitle = "Type the VTOP captcha yourself instead of auto-solving",
                checked = SettingsManager.isVtopManualCaptcha(),
                onCheckedChange = { SettingsManager.setVtopManualCaptcha(it) },
            )
            // D-122: the web exposes eleven push categories; Kotlin only
            // schedules local alarms for the four it can act on. These two
            // are the ones missing from the M3E surface; the rest are reported
            // as unported rather than rendered as inert switches.
            SettingsToggleRow(
                title = "Assignment reminders",
                subtitle = "Local alarm for pending Moodle / VTOP assignments",
                checked = SettingsManager.isNotifAssignmentRemindersEnabled(),
                onCheckedChange = { enabled ->
                    SettingsManager.setNotifAssignmentRemindersEnabled(enabled)
                    SettingsManager.updateWebSetting { it.copy(notifyAssignments = enabled) }
                },
            )
            SettingsToggleRow(
                title = "Task reminders",
                subtitle = "Local alarm for tasks due today",
                checked = SettingsManager.isNotifTaskRemindersEnabled(),
                onCheckedChange = { enabled -> SettingsManager.setNotifTaskRemindersEnabled(enabled) },
            )
            SettingsSegmentedRow(
                title = "VTOP source",
                items = VtopSource.entries.toList(),
                selectedItem = vtopSource,
                onItemSelected = {
                    AmazeClient.setVtopSource(it)
                    vtopSource = it
                },
                labelProvider = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            )
            SettingsSegmentedRow(
                title = "API target",
                items = ApiTarget.entries,
                selectedItem = ApiTarget.fromName(apiTargetName),
                onItemSelected = {
                    AmazeClient.setApiTarget(it)
                    apiTargetName = SettingsManager.getApiTargetName()
                },
                labelProvider = { it.label },
            )
            SectionLabel("Sync modules")
            SyncModule.entries.filter { it.cacheKey != null }.forEach { module ->
                SettingsToggleRow(
                    title = module.displayName,
                    subtitle = "Include in sync profiles",
                    checked = SyncEngine.isModuleEnabled(module),
                    onCheckedChange = { SyncEngine.setModuleEnabled(module, it) },
                )
            }

            SettingsGroupHeader("Backup & Transfer")
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            SettingsGroupHeader("Account & Tools")
            SettingsActionRow(
                title = "Sign in with Amaze ID",
                subtitle = "OIDC device code / PKCE / token entry",
                icon = Icons.Rounded.Login,
                onClick = { showAuth = true },
            )
            SettingsGroupHeader("Danger Zone")
            SettingsActionRow(
                title = "Clear local caches",
                subtitle = "Remove cached academic and module data",
                icon = Icons.Rounded.DeleteSweep,
                danger = true,
                onClick = { showClearConfirm = true },
            )
            SettingsActionRow(
                title = "Log out",
                subtitle = "End the VTOP session and return to sign-in",
                icon = Icons.Rounded.Login,
                danger = true,
                onClick = { showLogoutConfirm = true },
            )

            status?.let {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    TextButton(onClick = { status = null }) { Text("Dismiss") }
                }
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

/** Sub-section caption for controls that are not themselves a settings row. */
@Composable
private fun SectionLabel(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = M3ETheme.colors.onSurfaceVariant)
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
