package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.NamedCalendar
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val colors = AmazeTheme.colors
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val scope = rememberCoroutineScope()

    var availableCalendars by remember { mutableStateOf<List<NamedCalendar>>(emptyList()) }
    var preferredCalendarName by remember { mutableStateOf(SettingsManager.getPreferredCalendar() ?: "") }
    LaunchedEffect(selectedSemester) {
        scope.launch {
            try {
                val res = AmazeClient.getCalendars(semesterId = selectedSemester)
                if (res.success) availableCalendars = res.calendars
            } catch (_: Exception) {}
        }
    }

    var selectedTabs by remember { mutableStateOf(pinnedTabs.toSet()) }

    val availableTabs = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE,
        Screen.PAYMENTS, Screen.CABSHARE, Screen.TRANSPORT, Screen.CALENDAR,
        Screen.FFCS_PLANNER, Screen.FREE_CLASSROOMS, Screen.QBANK, Screen.SOCIAL,
        Screen.PROJECTS, Screen.WISHLIST
    )

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { AppState.navigateBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("App Settings", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Customize your app experience", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsSection("Appearance", Icons.Rounded.Palette, colors) {
                val activeTheme by AppState.theme.collectAsState()
                val activeAccent by AppState.accent.collectAsState()

                SettingsRow("Theme", colors) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeButton("Light", { AppState.changeTheme(AppTheme.LIGHT) }, modifier = Modifier.weight(1f), variant = if (activeTheme == AppTheme.LIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                        AmazeButton("Dark", { AppState.changeTheme(AppTheme.DARK) }, modifier = Modifier.weight(1f), variant = if (activeTheme == AppTheme.DARK) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                    }
                }

                SettingsRow("Accent Color", colors) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccentSwatch("Ocean", AccentTheme.OCEAN, activeAccent, colors, Modifier.weight(1f))
                        AccentSwatch("Forest", AccentTheme.FOREST, activeAccent, colors, Modifier.weight(1f))
                        AccentSwatch("Lavender", AccentTheme.LAVENDER, activeAccent, colors, Modifier.weight(1f))
                        AccentSwatch("Sunset", AccentTheme.SUNSET, activeAccent, colors, Modifier.weight(1f))
                    }
                }
            }

            SettingsSection("Display", Icons.Rounded.Visibility, colors) {
                val cgpaHidden by AppState.cgpaHidden.collectAsState()
                val attendanceMode by AppState.attendanceDisplayMode.collectAsState()

                SettingsToggle("Hide CGPA", cgpaHidden, { AppState.setCgpaHidden(it) }, colors)
                SettingsRow("Attendance Display", colors) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeButton("Percentage", { AppState.setAttendanceDisplayMode("percentage") }, modifier = Modifier.weight(1f), variant = if (attendanceMode == "percentage") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                        AmazeButton("Fraction", { AppState.setAttendanceDisplayMode("fraction") }, modifier = Modifier.weight(1f), variant = if (attendanceMode == "fraction") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                    }
                }
            }

            SettingsSection("Navigation Bar", Icons.Rounded.Navigation, colors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select up to 4 tabs for the bottom bar. Home is always pinned.", color = colors.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Available (${selectedTabs.size}/4)", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                availableTabs.forEach { tab ->
                    val (icon, label) = getScreenIconAndLabel(tab)
                    val isSelected = selectedTabs.contains(tab)
                    val isEnabled = isSelected || selectedTabs.size < 4

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.glassSurface else colors.surface)
                            .clickable(enabled = isEnabled || isSelected) {
                                val newSet = if (isSelected) selectedTabs - tab
                                else if (selectedTabs.size < 4) selectedTabs + tab else selectedTabs
                                selectedTabs = newSet
                                AppState.setPinnedNavTabs(newSet.toList())
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, label, tint = if (isSelected) colors.accent else if (!isEnabled) colors.textMuted else colors.textPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = if (isSelected) colors.textPrimary else if (!isEnabled) colors.textMuted else colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Checkbox(checked = isSelected, onCheckedChange = null, enabled = isEnabled, colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.textMuted, checkmarkColor = colors.background))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            SettingsSection("Academic Calendar", Icons.Rounded.CalendarMonth, colors) {
                Text("Choose which calendar to display by default", color = colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (availableCalendars.isEmpty()) {
                    Text("Loading calendars…", color = colors.textMuted, fontSize = 12.sp)
                } else {
                    availableCalendars.forEachIndexed { _, cal ->
                        val isSelected = preferredCalendarName == cal.name ||
                            (preferredCalendarName.isEmpty() && availableCalendars.firstOrNull() == cal)
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surface)
                                .clickable {
                                    preferredCalendarName = cal.name
                                    SettingsManager.savePreferredCalendar(cal.name)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (isSelected) colors.accent else colors.border))
                            Spacer(Modifier.width(12.dp))
                            Text(cal.name, color = if (isSelected) colors.accent else colors.textPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            SettingsSection("Data Sync", Icons.Rounded.Sync, colors) {
                val syncArrear by AppState.syncArrear.collectAsState()
                val syncExam by AppState.syncExam.collectAsState()
                val syncProfile by AppState.syncProfile.collectAsState()
                val syncAdditional by AppState.syncAdditional.collectAsState()

                SettingsToggle("Arrear Data", syncArrear, { AppState.setSyncArrear(it) }, colors)
                SettingsToggle("Exam Schedule", syncExam, { AppState.setSyncExam(it) }, colors)
                SettingsToggle("Profile Data", syncProfile, { AppState.setSyncProfile(it) }, colors)
                SettingsToggle("Additional (Projects/Wishlist)", syncAdditional, { AppState.setSyncAdditional(it) }, colors)

                Spacer(Modifier.height(8.dp))
                AmazeButton("Sync All Data", onClick = { AppState.loadAllData() }, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection("UI & Zoom", Icons.Rounded.ZoomIn, colors) {
                val currentScale by AppState.uiScale.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ZoomOut, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    Slider(
                        value = currentScale,
                        onValueChange = { AppState.changeUiScale(it) },
                        valueRange = 0.7f..1.5f,
                        steps = 7,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                    )
                    Icon(Icons.Rounded.ZoomIn, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom: ", color = colors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = "${(currentScale * 100).toInt()}",
                        onValueChange = { text ->
                            val pct = text.filter { it.isDigit() }.take(3).toIntOrNull()
                            if (pct != null) AppState.changeUiScale(pct.coerceIn(70, 150) / 100f)
                        },
                        modifier = Modifier.width(72.dp).height(48.dp),
                        singleLine = true,
                        textStyle = AmazeTheme.typography.body.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, cursorColor = colors.accent),
                        suffix = { Text("%", color = colors.textMuted, fontSize = 11.sp) }
                    )
                }
            }

            SettingsSection("Credentials", Icons.Rounded.Lock, colors) {
                val savedUsername = SettingsManager.getString(SettingsManager.KEY_USERNAME)
                val savedPassword = SettingsManager.getString(SettingsManager.KEY_PASSWORD)

                SettingsRow("Saved Credentials", colors) {
                    Text(if (savedUsername.isNotBlank()) "$savedUsername / ****" else "No credentials saved", color = colors.textSecondary, fontSize = 12.sp)
                }

                var showCredEditor by remember { mutableStateOf(false) }
                if (showCredEditor) {
                    var username by remember { mutableStateOf(savedUsername) }
                    var password by remember { mutableStateOf("") }
                    AmazeTextField(value = username, onValueChange = { username = it }, label = "Registration Number", placeholder = "", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    AmazeTextField(value = password, onValueChange = { password = it }, label = "Password", placeholder = "", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    AmazeButton("Save & Overwrite", onClick = { SettingsManager.saveCredentials(username, password) }, modifier = Modifier.fillMaxWidth())
                }
                AmazeButton(if (showCredEditor) "Cancel" else "Edit Credentials", onClick = { showCredEditor = !showCredEditor }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection("Danger Zone", Icons.Rounded.Warning, colors) {
                AmazeButton("Clear All Caches", onClick = { SettingsManager.clearAll() }, variant = ButtonVariant.DANGER, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AmazeButton("Close Student Session", onClick = { AppState.logout() }, variant = ButtonVariant.DANGER, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, colors: com.amazecc.app.shared.theme.AmazeColors, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRow(label: String, colors: com.amazecc.app.shared.theme.AmazeColors, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.accent, checkedTrackColor = colors.accent.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun AccentSwatch(name: String, accent: AccentTheme, current: AccentTheme, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    val selected = accent == current
    val swatchColor = when (accent) {
        AccentTheme.OCEAN -> Color(0xFF3B82F6)
        AccentTheme.FOREST -> Color(0xFF10B981)
        AccentTheme.LAVENDER -> Color(0xFF8B5CF6)
        AccentTheme.SUNSET -> Color(0xFFF59E0B)
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.surface)
            .border(if (selected) 1.dp else 0.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { AppState.changeAccent(accent) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(swatchColor))
            Spacer(Modifier.height(4.dp))
            Text(name, color = if (selected) colors.accent else colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
        }
    }
}
