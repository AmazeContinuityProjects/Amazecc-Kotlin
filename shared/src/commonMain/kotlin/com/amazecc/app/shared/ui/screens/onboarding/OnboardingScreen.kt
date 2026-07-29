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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.AttendanceDisplayMode
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

    val moduleStates by com.amazecc.app.shared.state.SyncEngine.moduleStates.collectAsState()
    val syncSteps = remember(moduleStates) {
        moduleStates.filterKeys { it != com.amazecc.app.shared.state.SyncModule.EVENTS }
            .entries.map { (module, state) ->
                AppState.SyncStep(
                    name = module.displayName,
                    status = when (state.status) {
                        com.amazecc.app.shared.state.SyncStatus.SUCCESS -> "done"
                        com.amazecc.app.shared.state.SyncStatus.ERROR -> "failed"
                        com.amazecc.app.shared.state.SyncStatus.LOADING -> "syncing"
                        else -> "pending"
                    }
                )
            }
    }
    LaunchedEffect(Unit) {
        AppState.loadAllData()
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
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(pages[page].icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.md))
                        Column {
                            Text(pages[page].title, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(pages[page].subtitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.sectionGap))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.indices.forEach { i ->
                        val isSelected = i == currentPage
                        val isPast = i <= currentPage
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp)
                        val color by animateColorAsState(targetValue = if (isPast) colors.accent else colors.border)
                        Box(
                            modifier = Modifier
                                .size(width, 8.dp)
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(color)
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
                    2 -> ResidentialNotifPage(colors, residentialStatus, { residentialStatus = it }, classNotif, { classNotif = it }, assignNotif, { assignNotif = it }, offsetMinutes, { offsetMinutes = it })
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
                AmazeButton(
                    text = "Back",
                    onClick = { currentPage-- },
                    variant = ButtonVariant.GHOST,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (currentPage < pages.size - 1) {
                AmazeButton(
                    text = "Next",
                    onClick = { currentPage++ },
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Rounded.ArrowForward
                )
            } else {
                AmazeButton(
                    text = "Get Started",
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
                        SettingsManager.setNotifOffsetMinutes(offsetMinutes)
                        SettingsManager.setOnboardingComplete(true)
                        AppState.navigateTo(Screen.HOME)
                    },
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.Rounded.RocketLaunch
                )
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
        Spacer(Modifier.height(AmazeTheme.spacing.md))
        Box(
            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(AmazeTheme.radius.extraLarge)).background(colors.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        Text("We're setting up everything\nfor you in the background", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(AmazeTheme.spacing.lg)) {
                Text("Sync Progress", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                val doneCount = syncSteps.count { it.status == "done" }
                val total = syncSteps.size
                val progress = if (total > 0) doneCount.toFloat() / total else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)), color = colors.accent, trackColor = colors.border)
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                Text("$doneCount of $total modules synced", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                syncSteps.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (step.status) { "done" -> Icons.Rounded.CheckCircle; "failed" -> Icons.Rounded.Error; "syncing" -> Icons.Rounded.Sync; else -> Icons.Rounded.RadioButtonUnchecked }
                        val iconColor = when (step.status) { "done" -> colors.chart1; "failed" -> colors.chart5; "syncing" -> colors.accent; else -> colors.border }
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
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
    attendanceMode: AttendanceDisplayMode, onAttendanceModeChange: (AttendanceDisplayMode) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))

        Text("Theme", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(AppTheme.LIGHT to Icons.Rounded.LightMode, AppTheme.DARK to Icons.Rounded.DarkMode, AppTheme.SYSTEM to Icons.Rounded.BrightnessAuto).forEach { (theme, icon) ->
                val isSelected = selectedTheme == theme
                AmazeCard(
                    modifier = Modifier.weight(1f),
                    variant = if (isSelected) CardVariant.ACCENT else CardVariant.DEFAULT,
                    onClick = { onThemeChange(theme); AppState.changeTheme(theme) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = AmazeTheme.spacing.md)
                    ) {
                        Icon(icon, null, tint = if (isSelected) Color.White else colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        Text(theme.name, color = if (isSelected) Color.White else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        Text("Accent Color", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AccentTheme.OCEAN to colors.accent, AccentTheme.FOREST to colors.success, AccentTheme.LAVENDER to colors.info, AccentTheme.SUNSET to colors.chart1).forEach { (accent, accentColor) ->
                val isSelected = selectedAccent == accent
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAccentChange(accent); AppState.changeAccent(accent) }) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(accentColor).border(if (isSelected) 3.dp else 0.dp, if (isSelected) colors.textPrimary else Color.Transparent, CircleShape).padding(if (isSelected) 0.dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    Text(accent.name, style = AmazeTheme.typography.smallLabel.copy(color = if (isSelected) colors.textPrimary else colors.textSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        Text("UI Scale", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.85f to "Small", 1.0f to "Default", 1.15f to "Large").forEach { (scale, label) ->
                val isSelected = uiScale == scale
                AmazeButton(
                    text = label,
                    onClick = { onUiScaleChange(scale); AppState.changeUiScale(scale) },
                    modifier = Modifier.weight(1f),
                    variant = if (isSelected) com.amazecc.app.shared.ui.components.ButtonVariant.PRIMARY else com.amazecc.app.shared.ui.components.ButtonVariant.SECONDARY
                )
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        Text("Display", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { onCgpaHiddenChange(!cgpaHidden) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (cgpaHidden) colors.chart5.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(if (cgpaHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = if (cgpaHidden) colors.chart5 else colors.accent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(AmazeTheme.spacing.md))
                    Column(modifier = Modifier.weight(1f)) { Text("Hide CGPA", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)); Text("Keep your CGPA private on dashboard", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
                    Switch(checked = cgpaHidden, onCheckedChange = onCgpaHiddenChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.border)
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Percent, null, tint = colors.accent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(AmazeTheme.spacing.md))
                    Text("Attendance Display", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AttendanceDisplayMode.PERCENTAGE to "Percentage", AttendanceDisplayMode.FRACTION to "Fraction").forEach { (mode, label) ->
                        val isSelected = attendanceMode == mode
                        AmazeButton(
                            text = label,
                            onClick = { onAttendanceModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            variant = if (isSelected) com.amazecc.app.shared.ui.components.ButtonVariant.PRIMARY else com.amazecc.app.shared.ui.components.ButtonVariant.SECONDARY
                        )
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
    offsetMinutes: Int, onOffsetChange: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Text("Residential Status", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Helps us show relevant campus info", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("hosteller" to Icons.Rounded.Apartment, "dayscholar" to Icons.Rounded.Home, "unknown" to Icons.AutoMirrored.Rounded.HelpOutline).forEach { (status, icon) ->
                val isSelected = residentialStatus == status
                AmazeButton(
                    text = when (status) { "hosteller" -> "Hosteller"; "dayscholar" -> "Day Scholar"; else -> "Not Sure" },
                    icon = icon,
                    onClick = { onResidentialChange(status) },
                    modifier = Modifier.weight(1f),
                    variant = if (isSelected) com.amazecc.app.shared.ui.components.ButtonVariant.PRIMARY else com.amazecc.app.shared.ui.components.ButtonVariant.SECONDARY
                )
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        Text("Notification Preferences", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("We'll remind you so you never miss out", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(4.dp)) {
                ToggleRow("Class Reminders", "Notify before each class starts", Icons.Rounded.Schedule, classNotif, onClassNotifChange, colors)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("Assignment Reminders", "Remind before deadlines", Icons.AutoMirrored.Rounded.Assignment, assignNotif, onAssignNotifChange, colors)
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.md))
        Text("Remind me before class", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { preset ->
                val isSelected = offsetMinutes == preset
                AmazeButton(
                    text = "$preset min",
                    onClick = { onOffsetChange(preset) },
                    variant = if (isSelected) com.amazecc.app.shared.ui.components.ButtonVariant.PRIMARY else com.amazecc.app.shared.ui.components.ButtonVariant.SECONDARY
                )
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
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Text("Pin your favorite modules", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("These appear in your bottom nav bar (max 4)", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.md))

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
                    AmazeCard(
                        modifier = Modifier.weight(1f),
                        variant = if (isSelected) CardVariant.ACCENT else CardVariant.DEFAULT,
                        onClick = { if (isSelected) onSelectionChange(selectedModules - module) else if (selectedModules.size < 4) onSelectionChange(selectedModules + module) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Icon(icon, null, tint = if (isSelected) Color.White else colors.textSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text(module.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (isSelected) Color.White else colors.textSecondary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                            if (isSelected) { Spacer(Modifier.height(AmazeTheme.spacing.xs)); Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
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
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Text("Link Your Accounts", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Optional — you can always set these up later in Settings", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sectionGap))

        // Moodle
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.chart2.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = colors.chart2, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) { Text("Moodle", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)); Text("Course materials & assignments", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                if (!moodleLinked) {
                    AmazeTextField(value = moodleUser, onValueChange = onMoodleUserChange, label = "Username", placeholder = "", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    AmazeTextField(value = moodlePass, onValueChange = onMoodlePassChange, label = "Password", placeholder = "", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AmazeTheme.spacing.md))
                    val me = moodleError; if (me != null) { Spacer(Modifier.height(AmazeTheme.spacing.xs)); Text(me, style = AmazeTheme.typography.caption.copy(color = colors.dangerText)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmazeButton(text = "Skip", onClick = { onMoodleUserChange(""); onMoodlePassChange("") }, enabled = !moodleLoading, variant = ButtonVariant.GHOST)
                        if (moodleLoading) {
                            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp) }
                        } else {
                            AmazeButton(text = "Link Account", onClick = {
                                if (moodleUser.isBlank() || moodlePass.isBlank()) { moodleError = "Please fill in both fields"; return@AmazeButton }
                                moodleLoading = true; moodleError = null
                                scope.launch {
                                    AppState.saveMoodleCredentials(moodleUser, moodlePass)
                                    moodleLoading = false
                                    moodleLinked = true
                                }
                            })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text("Moodle linked", style = AmazeTheme.typography.smallLabel.copy(color = colors.chart1, fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { moodleLinked = false; moodleError = null }) { Text("Unlink", style = AmazeTheme.typography.smallLabel.copy(color = colors.dangerText)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.md))

        // Library
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.chart4.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalLibrary, null, tint = colors.chart4, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) { Text("Library", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)); Text("Borrowed books & due dates", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                if (!libLinked) {
                    AmazeTextField(value = libUser, onValueChange = onLibUserChange, label = "Library ID", placeholder = "", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    AmazeTextField(value = libPass, onValueChange = onLibPassChange, label = "Password", placeholder = "", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AmazeTheme.spacing.md))
                    val le = libError; if (le != null) { Spacer(Modifier.height(AmazeTheme.spacing.xs)); Text(le, style = AmazeTheme.typography.caption.copy(color = colors.dangerText)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmazeButton(text = "Skip", onClick = { onLibUserChange(""); onLibPassChange("") }, enabled = !libLoading, variant = ButtonVariant.GHOST)
                        if (libLoading) {
                            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp) }
                        } else {
                            AmazeButton(text = "Link Account", onClick = {
                                if (libUser.isBlank() || libPass.isBlank()) { libError = "Please fill in both fields"; return@AmazeButton }
                                libLoading = true; libError = null
                                scope.launch {
                                    AppState.saveLibraryCredentials(libUser, libPass)
                                    libLoading = false
                                    libLinked = true
                                }
                            })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
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
        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(AmazeTheme.radius.large)).background(colors.chart1.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = colors.chart1, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(AmazeTheme.spacing.sectionGap))
        Text("You're all set!", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Black, color = colors.textPrimary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Text("Your preferences have been saved.\nTap Get Started to dive in!", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sync Summary", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                val doneCount = syncSteps.count { it.status == "done" }
                val failedCount = syncSteps.count { it.status == "failed" }
                val activeCount = syncSteps.count { it.status == "syncing" }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SyncStat("Completed", "$doneCount", colors.chart1, colors)
                    SyncStat("Failed", "$failedCount", if (failedCount > 0) colors.chart5 else colors.textMuted, colors)
                    SyncStat("In Progress", "$activeCount", colors.accent, colors)
                }
                if (syncSteps.isNotEmpty()) {
                    Spacer(Modifier.height(AmazeTheme.spacing.md))
                    syncSteps.forEach { step ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (step.status) { "done" -> colors.chart1; "failed" -> colors.chart5; "syncing" -> colors.accent; else -> colors.border }
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(step.name, style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary))
                            Spacer(Modifier.weight(1f))
                            val label = when (step.status) { "done" -> "Synced"; "failed" -> "Failed"; "syncing" -> Strings.loading; else -> "Pending" }
                            val labelColor = when (step.status) { "done" -> colors.chart1; "failed" -> colors.chart5; "syncing" -> colors.accent; else -> colors.textMuted }
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
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(if (checked) colors.chart5.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (checked) colors.chart5 else colors.accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) { Text(title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)); Text(subtitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)) }
        Switch(checked = checked, onCheckedChange = handleToggle, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun SyncStat(label: String, value: String, valueColor: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = valueColor))
        Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
    }
}