package com.amazecc.app.shared.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.ui.components.AmazeTextField
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen() {
    val colors = AmazeTheme.colors
    var currentPage by remember { mutableStateOf(0) }

    // Personalization
    var selectedTheme by remember { mutableStateOf(AppState.theme.value) }
    var selectedAccent by remember { mutableStateOf(AppState.accent.value) }
    var uiScale by remember { mutableStateOf(AppState.uiScale.value) }
    var cgpaHidden by remember { mutableStateOf(AppState.cgpaHidden.value) }
    var attendanceMode by remember { mutableStateOf(AppState.attendanceDisplayMode.value) }

    // Residential & Notifications
    var residentialStatus by remember { mutableStateOf(AppState.residentialStatus.value) }
    var classNotif by remember { mutableStateOf(false) }
    var assignNotif by remember { mutableStateOf(false) }
    var vitolNotif by remember { mutableStateOf(false) }
    var offsetMinutes by remember { mutableStateOf(15) }

    // Modules
    val availableModules = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.HOSTEL,
        Screen.CABSHARE, Screen.TRANSPORT, Screen.PAYMENTS, Screen.PROFILE,
        Screen.EVENTS, Screen.QBANK, Screen.SOCIAL
    )
    var selectedModules by remember { mutableStateOf(AppState.pinnedNavTabs.value) }

    // Accounts
    var moodleUser by remember { mutableStateOf("") }
    var moodlePass by remember { mutableStateOf("") }
    var libUser by remember { mutableStateOf("") }
    var libPass by remember { mutableStateOf("") }

    val syncSteps by AppState.onboardingSyncSteps.collectAsState()

    LaunchedEffect(Unit) {
        AppState.startOnboardingSync()
    }

    val pages = listOf(
        OnboardingPage("Welcome to AmazeCC", "Your all-in-one campus companion", Icons.Rounded.CloudSync),
        OnboardingPage("Personalize", "Theme, accent & display preferences", Icons.Rounded.Palette),
        OnboardingPage("Residence & Alerts", "Notifications & residential info", Icons.Rounded.Notifications),
        OnboardingPage("Choose Your Modules", "Pin your most-used sections", Icons.Rounded.Apps),
        OnboardingPage("Link Accounts", "Optional: Moodle & Library login", Icons.Rounded.Link),
        OnboardingPage("You're All Set!", "Your data is loading in the background", Icons.Rounded.CheckCircle)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accent.copy(alpha = 0.08f))
                .padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                AnimatedContent(targetState = currentPage, transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }) { page ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(pages[page].icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(pages[page].title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(pages[page].subtitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == currentPage) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i <= currentPage) colors.accent else colors.border)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> WelcomePage(colors, syncSteps)
                    1 -> PersonalizationPage(colors, selectedTheme, { selectedTheme = it }, selectedAccent, { selectedAccent = it }, uiScale, { uiScale = it }, cgpaHidden, { cgpaHidden = it }, attendanceMode, { attendanceMode = it })
                    2 -> ResidentialNotifPage(colors, residentialStatus, { residentialStatus = it }, classNotif, { classNotif = it }, assignNotif, { assignNotif = it }, vitolNotif, { vitolNotif = it }, offsetMinutes, { offsetMinutes = it })
                    3 -> ModulesPage(colors, availableModules, selectedModules) { selectedModules = it }
                    4 -> AccountsPage(colors, moodleUser, { moodleUser = it }, moodlePass, { moodlePass = it }, libUser, { libUser = it }, libPass, { libPass = it })
                    5 -> CompletionPage(colors, syncSteps)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                TextButton(onClick = { currentPage-- }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (currentPage < pages.size - 1) {
                Button(
                    onClick = { currentPage++ },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Next", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            } else {
                Button(
                    onClick = {
                        AppState.changeTheme(selectedTheme)
                        AppState.changeAccent(selectedAccent)
                        AppState.changeUiScale(uiScale)
                        AppState.setCgpaHidden(cgpaHidden)
                        AppState.setAttendanceDisplayMode(attendanceMode)
                        AppState.setResidentialStatus(residentialStatus)
                        AppState.setPinnedNavTabs(selectedModules)
                        SettingsManager.setNotifClassRemindersEnabled(classNotif)
                        SettingsManager.setNotifAssignmentRemindersEnabled(assignNotif)
                        SettingsManager.setNotifVitolRemindersEnabled(vitolNotif)
                        SettingsManager.setNotifOffsetMinutes(offsetMinutes)
                        SettingsManager.setOnboardingComplete(true)
                        AppState.navigateTo(Screen.HOME)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.chart1)
                ) {
                    Icon(Icons.Rounded.RocketLaunch, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Get Started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Page 0: Welcome ───
@Composable
private fun WelcomePage(colors: com.amazecc.app.shared.theme.AmazeColors, syncSteps: List<AppState.SyncStep>) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(AmazeTheme.radius.extraLarge)).background(colors.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("We're setting up everything\nfor you in the background", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.medium)).background(colors.surface).padding(AmazeTheme.spacing.lg)
        ) {
            Column {
                Text("Sync Progress", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.height(14.dp))
                val doneCount = syncSteps.count { it.status == "done" }
                val total = syncSteps.size
                val progress = if (total > 0) doneCount.toFloat() / total else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = colors.accent, trackColor = colors.border)
                Spacer(Modifier.height(4.dp))
                Text("$doneCount of $total modules synced", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                Spacer(Modifier.height(14.dp))
                syncSteps.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (step.status) { "done" -> Icons.Rounded.CheckCircle; "failed" -> Icons.Rounded.Error; "syncing" -> Icons.Rounded.Sync; else -> Icons.Rounded.RadioButtonUnchecked }
                        val iconColor = when (step.status) { "done" -> colors.chart1; "failed" -> colors.chart5; "syncing" -> colors.accent; else -> colors.border }
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(step.name, style = AmazeTheme.typography.smallLabel.copy(color = if (step.status == "pending") colors.textMuted else colors.textPrimary))
                        if (step.status == "syncing") { Spacer(Modifier.weight(1f)); Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.accent)) }
                    }
                }
            }
        }
    }
}

// ─── Page 1: Personalization ───
@Composable
private fun PersonalizationPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    selectedTheme: AppTheme, onThemeChange: (AppTheme) -> Unit,
    selectedAccent: AccentTheme, onAccentChange: (AccentTheme) -> Unit,
    uiScale: Float, onUiScaleChange: (Float) -> Unit,
    cgpaHidden: Boolean, onCgpaHiddenChange: (Boolean) -> Unit,
    attendanceMode: String, onAttendanceModeChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(8.dp))

        Text("Theme", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(AppTheme.LIGHT to Icons.Rounded.LightMode, AppTheme.DARK to Icons.Rounded.DarkMode, AppTheme.SYSTEM to Icons.Rounded.BrightnessAuto).forEach { (theme, icon) ->
                val isSelected = selectedTheme == theme
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(if (isSelected) colors.accent else colors.surface).border(if (isSelected) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small)).clickable { onThemeChange(theme); AppState.changeTheme(theme) }.padding(vertical = AmazeTheme.spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = if (isSelected) Color.White else colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(theme.name, color = if (isSelected) Color.White else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Accent Color", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AccentTheme.OCEAN to Color(0xFF0EA5E9), AccentTheme.FOREST to Color(0xFF10B981), AccentTheme.LAVENDER to Color(0xFF8B5CF6), AccentTheme.SUNSET to Color(0xFFF97316)).forEach { (accent, accentColor) ->
                val isSelected = selectedAccent == accent
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAccentChange(accent); AppState.changeAccent(accent) }) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(accentColor).border(if (isSelected) 3.dp else 0.dp, if (isSelected) colors.accent else Color.Transparent, CircleShape).padding(if (isSelected) 0.dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(accent.name, style = AmazeTheme.typography.smallLabel.copy(color = if (isSelected) colors.accent else colors.textSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("UI Scale", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.85f to "Small", 1.0f to "Default", 1.15f to "Large").forEach { (scale, label) ->
                val isSelected = uiScale == scale
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(if (isSelected) colors.accent else colors.surface).border(if (isSelected) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.xs)).clickable { onUiScaleChange(scale); AppState.changeUiScale(scale) }.padding(horizontal = AmazeTheme.spacing.lg, vertical = AmazeTheme.spacing.sm)
                ) {
                    Text(label, color = if (isSelected) Color.White else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Display", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { onCgpaHiddenChange(!cgpaHidden) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (cgpaHidden) colors.chart5.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Icon(if (cgpaHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = if (cgpaHidden) colors.chart5 else colors.accent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) { Text("Hide CGPA", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)); Text("Keep your CGPA private on dashboard", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
                    Switch(checked = cgpaHidden, onCheckedChange = onCgpaHiddenChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Percent, null, tint = colors.accent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text("Attendance Display", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("percentage" to "Percentage", "fraction" to "Fraction").forEach { (mode, label) ->
                        val isSelected = attendanceMode == mode
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (isSelected) colors.accent else colors.surface).border(if (isSelected) 0.dp else 1.dp, colors.border, RoundedCornerShape(8.dp)).clickable { onAttendanceModeChange(mode) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (isSelected) Color.White else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        }
    }
}

// ─── Page 2: Residential & Notifications ───
@Composable
private fun ResidentialNotifPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    residentialStatus: String, onResidentialChange: (String) -> Unit,
    classNotif: Boolean, onClassNotifChange: (Boolean) -> Unit,
    assignNotif: Boolean, onAssignNotifChange: (Boolean) -> Unit,
    vitolNotif: Boolean, onVitolNotifChange: (Boolean) -> Unit,
    offsetMinutes: Int, onOffsetChange: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Residential Status", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(4.dp))
        Text("Helps us show relevant campus info", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("hosteller" to Icons.Rounded.Apartment, "dayscholar" to Icons.Rounded.Home, "unknown" to Icons.AutoMirrored.Rounded.HelpOutline).forEach { (status, icon) ->
                val isSelected = residentialStatus == status
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (isSelected) colors.accent else colors.surface).border(if (isSelected) 0.dp else 1.dp, colors.border, RoundedCornerShape(14.dp)).clickable { onResidentialChange(status) }.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = if (isSelected) Color.White else colors.textSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(when (status) { "hosteller" -> "Hosteller"; "dayscholar" -> "Day Scholar"; else -> "Not Sure" }, color = if (isSelected) Color.White else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Notification Preferences", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(4.dp))
        Text("We'll remind you so you never miss out", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(4.dp)) {
            Column {
                ToggleRow("Class Reminders", "Notify before each class starts", Icons.Rounded.Schedule, classNotif, onClassNotifChange, colors)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("Assignment Reminders", "Remind before deadlines", Icons.AutoMirrored.Rounded.Assignment, assignNotif, onAssignNotifChange, colors)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("VITOL Reminders", "Low balance alerts", Icons.Rounded.AccountBalanceWallet, vitolNotif, onVitolNotifChange, colors)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Remind me before class", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { preset ->
                val isSelected = offsetMinutes == preset
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (isSelected) colors.accent else colors.surface).border(if (isSelected) 0.dp else 1.dp, colors.border, RoundedCornerShape(10.dp)).clickable { onOffsetChange(preset) }.padding(horizontal = 16.dp, vertical = 10.dp)
                ) { Text("$preset min", color = if (isSelected) Color.White else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold)) }
            }
        }
    }
}

// ─── Page 3: Modules ───
@Composable
private fun ModulesPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    availableModules: List<Screen>,
    selectedModules: List<Screen>,
    onSelectionChange: (List<Screen>) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Pin your favorite modules", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(4.dp))
        Text("These appear in your bottom nav bar (max 4)", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(16.dp))

        val moduleIcons = mapOf(
            Screen.ATTENDANCE to Icons.Rounded.TaskAlt, Screen.ACADEMICS to Icons.AutoMirrored.Rounded.MenuBook,
            Screen.LIBRARIES to Icons.Rounded.LocalLibrary, Screen.HOSTEL to Icons.Rounded.Apartment,
            Screen.CABSHARE to Icons.Rounded.DirectionsCar, Screen.TRANSPORT to Icons.Rounded.DirectionsBus,
            Screen.PAYMENTS to Icons.Rounded.AccountBalance, Screen.PROFILE to Icons.Rounded.Person,
            Screen.EVENTS to Icons.Rounded.Event, Screen.QBANK to Icons.Rounded.Storage,
            Screen.SOCIAL to Icons.Rounded.People
        )

        val chunked = availableModules.chunked(2)
        chunked.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { module ->
                    val isSelected = selectedModules.contains(module)
                    val icon = moduleIcons[module] ?: Icons.Rounded.Widgets
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (isSelected) colors.accent.copy(alpha = 0.1f) else colors.surface).border(1.dp, if (isSelected) colors.accent.copy(alpha = 0.3f) else colors.border, RoundedCornerShape(14.dp)).clickable { if (isSelected) onSelectionChange(selectedModules - module) else if (selectedModules.size < 4) onSelectionChange(selectedModules + module) }.padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, tint = if (isSelected) colors.accent else colors.textSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(module.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (isSelected) colors.accent else colors.textSecondary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                            if (isSelected) { Spacer(Modifier.height(4.dp)); Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ─── Page 4: Accounts ───
@Composable
private fun AccountsPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    moodleUser: String, onMoodleUserChange: (String) -> Unit,
    moodlePass: String, onMoodlePassChange: (String) -> Unit,
    libUser: String, onLibUserChange: (String) -> Unit,
    libPass: String, onLibPassChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var moodleLinked by remember { mutableStateOf(false) }
    var moodleLoading by remember { mutableStateOf(false) }
    var moodleError by remember { mutableStateOf<String?>(null) }
    var libLinked by remember { mutableStateOf(false) }
    var libLoading by remember { mutableStateOf(false) }
    var libError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Link Your Accounts", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(4.dp))
        Text("Optional — you can always set these up later in Settings", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(20.dp))

        // Moodle
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.chart2.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = colors.chart2, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) { Text("Moodle", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)); Text("Course materials & assignments", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
                }
                Spacer(Modifier.height(12.dp))
                if (!moodleLinked) {
                    AmazeTextField(value = moodleUser, onValueChange = onMoodleUserChange, label = "Username", placeholder = "", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    AmazeTextField(value = moodlePass, onValueChange = onMoodlePassChange, label = "Password", placeholder = "", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    val me = moodleError; if (me != null) { Spacer(Modifier.height(4.dp)); Text(me, style = AmazeTheme.typography.caption.copy(color = colors.dangerText)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onMoodleUserChange(""); onMoodlePassChange("") }, shape = RoundedCornerShape(10.dp), enabled = !moodleLoading) { Text("Skip", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)) }
                        if (moodleLoading) {
                            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp) }
                        } else {
                            Button(onClick = {
                                if (moodleUser.isBlank() || moodlePass.isBlank()) { moodleError = "Please fill in both fields"; return@Button }
                                moodleLoading = true; moodleError = null
                                scope.launch {
                                    AppState.saveMoodleCredentials(moodleUser, moodlePass)
                                    moodleLoading = false
                                    moodleLinked = true
                                }
                            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.chart2)) { Text("Link Account", fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Moodle linked", style = AmazeTheme.typography.smallLabel.copy(color = colors.chart1, fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { moodleLinked = false; moodleError = null }) { Text("Unlink", style = AmazeTheme.typography.smallLabel.copy(color = colors.dangerText)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Library
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.chart4.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalLibrary, null, tint = colors.chart4, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) { Text("Library", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)); Text("Borrowed books & due dates", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
                }
                Spacer(Modifier.height(12.dp))
                if (!libLinked) {
                    AmazeTextField(value = libUser, onValueChange = onLibUserChange, label = "Library ID", placeholder = "", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    AmazeTextField(value = libPass, onValueChange = onLibPassChange, label = "Password", placeholder = "", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    val le = libError; if (le != null) { Spacer(Modifier.height(4.dp)); Text(le, style = AmazeTheme.typography.caption.copy(color = colors.dangerText)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onLibUserChange(""); onLibPassChange("") }, shape = RoundedCornerShape(10.dp), enabled = !libLoading) { Text("Skip", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)) }
                        if (libLoading) {
                            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp) }
                        } else {
                            Button(onClick = {
                                if (libUser.isBlank() || libPass.isBlank()) { libError = "Please fill in both fields"; return@Button }
                                libLoading = true; libError = null
                                scope.launch {
                                    AppState.saveLibraryCredentials(libUser, libPass)
                                    libLoading = false
                                    libLinked = true
                                }
                            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.chart4)) { Text("Link Account", fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Library linked", style = AmazeTheme.typography.smallLabel.copy(color = colors.chart1, fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { libLinked = false; libError = null }) { Text("Unlink", style = AmazeTheme.typography.smallLabel.copy(color = colors.dangerText)) }
                    }
                }
            }
        }
    }
}

// ─── Page 5: Completion ───
@Composable
private fun CompletionPage(colors: com.amazecc.app.shared.theme.AmazeColors, syncSteps: List<AppState.SyncStep>) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(AmazeTheme.radius.large)).background(colors.chart1.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = colors.chart1, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("You're all set!", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
        Spacer(Modifier.height(8.dp))
        Text("Your preferences have been saved.\nTap Get Started to dive in!", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(28.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(20.dp)) {
            Column {
                Text("Sync Summary", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.height(12.dp))
                val doneCount = syncSteps.count { it.status == "done" }
                val failedCount = syncSteps.count { it.status == "failed" }
                val activeCount = syncSteps.count { it.status == "syncing" }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SyncStat("Completed", "$doneCount", colors.chart1, colors)
                    SyncStat("Failed", "$failedCount", if (failedCount > 0) colors.chart5 else colors.textMuted, colors)
                    SyncStat("In Progress", "$activeCount", colors.accent, colors)
                }
                if (syncSteps.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    syncSteps.forEach { step ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (step.status) { "done" -> colors.chart1; "failed" -> colors.chart5; "syncing" -> colors.accent; else -> colors.border }
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            Spacer(Modifier.width(8.dp))
                            Text(step.name, style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary))
                            Spacer(Modifier.weight(1f))
                            val (label, labelColor) = when (step.status) { "done" -> "Synced" to colors.chart1; "failed" -> "Failed" to colors.chart5; "syncing" -> "Loading..." to colors.accent; else -> "Pending" to colors.textMuted }
                            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = labelColor, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

// ─── Shared Components ───
@Composable
private fun ToggleRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val notifPermissionManager = com.amazecc.app.shared.ui.components.LocalNotificationPermissionManager.current
    val handleToggle: (Boolean) -> Unit = { newChecked ->
        if (newChecked) {
            notifPermissionManager?.requestPermission()
        }
        onCheckedChange(newChecked)
    }
    Row(modifier = Modifier.fillMaxWidth().clickable { handleToggle(!checked) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (checked) colors.chart5.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (checked) colors.chart5 else colors.accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)); Text(subtitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
        Switch(checked = checked, onCheckedChange = handleToggle, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun SyncStat(label: String, value: String, valueColor: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = valueColor, fontSize = 24.sp))
        Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
    }
}