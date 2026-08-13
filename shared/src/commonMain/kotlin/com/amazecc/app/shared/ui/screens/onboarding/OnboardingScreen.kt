package com.amazecc.app.shared.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.AttendanceDisplayMode
import com.amazecc.app.shared.state.DashboardWidget
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.state.SyncScheduler
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val phaseLabel: String
)

private data class DiscoverFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color
)

private val PHASES = listOf("Discover", "Plan", "Tell")

@OptIn(ExperimentalLayoutApi::class)
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
    var hapticsEnabled by remember { mutableStateOf(AppState.hapticEnabled.value) }
    var animationsEnabled by remember { mutableStateOf(AppState.animationsEnabled.value) }

    // Academic Preferences
    var selectedSemester by remember { mutableStateOf(AppState.selectedSemester.value) }
    var selectedCalendar by remember { mutableStateOf(SettingsManager.getPreferredCalendar() ?: AmazeClient.calendarTypes.first().second) }

    // Residential & Notifications
    var residentialStatus by remember { mutableStateOf(AppState.residentialStatus.value) }
    var classNotif by remember { mutableStateOf(SettingsManager.isNotifClassRemindersEnabled()) }
    var assignNotif by remember { mutableStateOf(SettingsManager.isNotifAssignmentRemindersEnabled()) }
    var taskNotif by remember { mutableStateOf(SettingsManager.isNotifTaskRemindersEnabled()) }
    var examNotif by remember { mutableStateOf(SettingsManager.isNotifExamRemindersEnabled()) }
    var offsetMinutes by remember { mutableStateOf(SettingsManager.getNotifOffsetMinutes()) }

    // Modules
    val availableModules = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE,
        Screen.PAYMENTS, Screen.CABSHARE, Screen.TRANSPORT, Screen.CALENDAR,
        Screen.FFCS_PLANNER, Screen.FREE_CLASSROOMS, Screen.QBANK, Screen.SOCIAL,
        Screen.PROJECTS, Screen.WISHLIST
    )
    var selectedModules by remember { mutableStateOf(AppState.pinnedNavTabs.value) }

    // Accounts
    var moodleUser by remember { mutableStateOf("") }
    var moodlePass by remember { mutableStateOf("") }
    var libUser by remember { mutableStateOf("") }
    var libPass by remember { mutableStateOf("") }

    val moduleStates by SyncEngine.moduleStates.collectAsState()
    val syncSteps = remember(moduleStates) {
        moduleStates.filterKeys { it != SyncModule.EVENTS }
            .entries.map { (module, state) ->
                AppState.SyncStep(
                    name = module.displayName,
                    status = when (state.status) {
                        SyncStatus.SUCCESS -> "done"
                        SyncStatus.ERROR -> "failed"
                        SyncStatus.LOADING -> "syncing"
                        else -> "pending"
                    }
                )
            }
    }
    LaunchedEffect(Unit) {
        AppState.loadAllData()
    }

    val pages = listOf(
        OnboardingPage("Welcome to AmazeCC", "Your all-in-one campus companion", Icons.Rounded.AutoAwesome, PHASES[0]),
        OnboardingPage("Discover AmazeCC", "Everything in one place", Icons.Rounded.Explore, PHASES[0]),
        OnboardingPage("Personalize", "Theme, accent & display preferences", Icons.Rounded.Palette, PHASES[1]),
        OnboardingPage("Academic Preferences", "Semester & default calendar type", Icons.Rounded.CalendarMonth, PHASES[1]),
        OnboardingPage("Residence & Alerts", "Notifications & residential info", Icons.Rounded.Notifications, PHASES[1]),
        OnboardingPage("Choose Your Modules", "Pin your most-used sections", Icons.Rounded.Apps, PHASES[2]),
        OnboardingPage("Dashboard Widgets", "Pick what your home screen shows", Icons.Rounded.DashboardCustomize, PHASES[2]),
        OnboardingPage("Link Accounts", "Optional: Moodle & Library login", Icons.Rounded.Link, PHASES[2]),
        OnboardingPage("Auto Sync", "Keep data fresh on a schedule", Icons.Rounded.Timelapse, PHASES[2]),
        OnboardingPage("You're All Set!", "Your data is loading in the background", Icons.Rounded.CheckCircle, PHASES[2])
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
                    Column {
                        Text(
                            "PHASE ${PHASES.indexOf(pages[page].phaseLabel) + 1} OF 3 · ${pages[page].phaseLabel.uppercase()}",
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.micro,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(6.dp))
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
                    1 -> DiscoverPage(colors)
                    2 -> PersonalizationPage(colors, selectedTheme, { selectedTheme = it }, selectedAccent, { selectedAccent = it }, uiScale, { uiScale = it }, cgpaHidden, { cgpaHidden = it }, attendanceMode, { attendanceMode = it }, hapticsEnabled, { hapticsEnabled = it }, animationsEnabled, { animationsEnabled = it })
                    3 -> AcademicPrefsPage(colors, selectedSemester, { selectedSemester = it }, selectedCalendar, { selectedCalendar = it })
                    4 -> ResidentialNotifPage(colors, residentialStatus, { residentialStatus = it }, classNotif, { classNotif = it }, assignNotif, { assignNotif = it }, taskNotif, { taskNotif = it }, examNotif, { examNotif = it }, offsetMinutes, { offsetMinutes = it })
                    5 -> ModulesPage(colors, availableModules, selectedModules) { selectedModules = it }
                    6 -> DashboardWidgetsPage(colors)
                    7 -> AccountsPage(colors, moodleUser, { moodleUser = it }, moodlePass, { moodlePass = it }, libUser, { libUser = it }, libPass, { libPass = it })
                    8 -> AutoSyncPage(colors)
                    9 -> CompletionPage(colors, syncSteps)
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
                        AppState.setHapticEnabled(hapticsEnabled)
                        AppState.setAnimationsEnabled(animationsEnabled)
                        AppState.setResidentialStatus(residentialStatus)
                        AppState.setPinnedNavTabs(selectedModules)
                        SettingsManager.savePreferredCalendar(selectedCalendar)
                        if (selectedSemester != AppState.selectedSemester.value) AppState.selectSemester(selectedSemester)
                        SettingsManager.setNotifClassRemindersEnabled(classNotif)
                        SettingsManager.setNotifAssignmentRemindersEnabled(assignNotif)
                        SettingsManager.setNotifTaskRemindersEnabled(taskNotif)
                        SettingsManager.setNotifExamRemindersEnabled(examNotif)
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

// ─── Shared Onboarding Components ───

@Composable
private fun OnboardingSectionLabel(text: String, colors: com.amazecc.app.shared.theme.AmazeColors, tint: Color = colors.textSecondary) {
    Text(
        text,
        style = AmazeTheme.typography.smallLabel.copy(
            fontWeight = FontWeight.Bold,
            color = tint,
            letterSpacing = 1.sp
        )
    )
}

@Composable
private fun OnboardingHeroCard(
    icon: ImageVector,
    title: String,
    badge: String?,
    tint: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    content: @Composable ColumnScope.() -> Unit
) {
    val heroGradient = remember(tint) {
        Brush.linearGradient(colors = listOf(tint, tint.copy(alpha = 0.6f)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(heroGradient)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = Color.White.copy(alpha = 0.9f),
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)
                )
                if (badge != null) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(badge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun FrostedPanel(colors: com.amazecc.app.shared.theme.AmazeColors, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(AmazeTheme.radius.medium))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

// ─── Page 0: Welcome ───
@Composable
private fun WelcomePage(colors: com.amazecc.app.shared.theme.AmazeColors, syncSteps: List<AppState.SyncStep>) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)
    ) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        OnboardingHeroCard(Icons.Rounded.AutoAwesome, "Welcome to AmazeCC", "DISCOVER", colors.accent, colors) {
            Text(
                "We're setting up everything for you in the background — attendance, timetable, grades & more.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = AmazeTheme.fontSize.sm,
                lineHeight = 20.sp
            )
            FrostedPanel(colors) {
                val doneCount = syncSteps.count { it.status == "done" }
                val total = syncSteps.size
                val progress = if (total > 0) doneCount.toFloat() / total else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SYNC PROGRESS",
                        style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text("$doneCount/$total", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                syncSteps.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (step.status) {
                            "done" -> Icons.Rounded.CheckCircle
                            "failed" -> Icons.Rounded.Error
                            "syncing" -> Icons.Rounded.Sync
                            else -> Icons.Rounded.RadioButtonUnchecked
                        }
                        Icon(
                            icon,
                            null,
                            tint = Color.White.copy(alpha = when (step.status) {
                                "done" -> 0.95f; "syncing" -> 0.9f; "failed" -> 0.6f; else -> 0.4f
                            }),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            step.name,
                            color = Color.White.copy(alpha = if (step.status == "pending") 0.45f else 0.9f),
                            fontSize = AmazeTheme.fontSize.xs,
                            modifier = Modifier.weight(1f)
                        )
                        if (step.status == "syncing") {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        } else {
                            val label = when (step.status) { "done" -> "Synced"; "failed" -> "Failed"; else -> "Pending" }
                            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ─── Page 1: Discover ───
@Composable
private fun DiscoverPage(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val features = listOf(
        DiscoverFeature("Course Hub", "Courses, attendance, marks & faculty", Icons.AutoMirrored.Rounded.MenuBook, colors.accent),
        DiscoverFeature("Curriculum", "Credits & degree progress", Icons.Rounded.AccountTree, colors.success),
        DiscoverFeature("Attendance Log", "Per-course breakdown & predictor", Icons.Rounded.FactCheck, colors.info),
        DiscoverFeature("Exam Schedule", "Seat plans & countdowns", Icons.Rounded.EventSeat, colors.chart1),
        DiscoverFeature("Tasks", "Assignments, deadlines & reminders", Icons.Rounded.TaskAlt, colors.chart2),
        DiscoverFeature("CGPA Predictor", "What-if grade projections", Icons.Rounded.Insights, colors.warning),
        DiscoverFeature("QBank", "Practice quizzes & tests", Icons.Rounded.Quiz, colors.chart4),
        DiscoverFeature("Campus Life", "Events, clubs, transport & more", Icons.Rounded.Celebration, colors.danger)
    )
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Text(
            "Here's what AmazeCC brings to your campus life — everything syncs automatically from VTOP.",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(AmazeTheme.spacing.md))
        features.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { feature ->
                    DiscoverFeatureCard(feature, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DiscoverFeatureCard(feature: DiscoverFeature, modifier: Modifier = Modifier) {
    val gradient = remember(feature.tint) {
        Brush.linearGradient(colors = listOf(feature.tint, feature.tint.copy(alpha = 0.55f)))
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(gradient)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(feature.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
        Text(feature.description, color = Color.White.copy(alpha = 0.8f), fontSize = AmazeTheme.fontSize.micro, lineHeight = 15.sp)
    }
}

// ─── Page 2: Personalization ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalizationPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    selectedTheme: AppTheme, onThemeChange: (AppTheme) -> Unit,
    selectedAccent: AccentTheme, onAccentChange: (AccentTheme) -> Unit,
    uiScale: Float, onUiScaleChange: (Float) -> Unit,
    cgpaHidden: Boolean, onCgpaHiddenChange: (Boolean) -> Unit,
    attendanceMode: AttendanceDisplayMode, onAttendanceModeChange: (AttendanceDisplayMode) -> Unit,
    hapticsEnabled: Boolean, onHapticsChange: (Boolean) -> Unit,
    animationsEnabled: Boolean, onAnimationsChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val customAccent by AppState.customAccentColor.collectAsState()
    var showCustomAccent by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))

        OnboardingSectionLabel("COLOR THEME", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(AppTheme.LIGHT to Icons.Rounded.LightMode, AppTheme.DARK to Icons.Rounded.DarkMode, AppTheme.SYSTEM to Icons.Rounded.BrightnessAuto).forEach { (theme, icon) ->
                val isSelected = selectedTheme == theme
                AmazeCard(
                    modifier = Modifier.weight(1f),
                    variant = CardVariant.DEFAULT,
                    onClick = { onThemeChange(theme); AppState.changeTheme(theme) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = AmazeTheme.spacing.md)
                    ) {
                        Icon(icon, null, tint = if (isSelected) colors.accent else colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        Text(theme.name, color = if (isSelected) colors.accent else colors.textPrimary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("ACCENT COLOR", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AccentTheme.OCEAN to colors.accent, AccentTheme.FOREST to colors.success, AccentTheme.LAVENDER to colors.info, AccentTheme.SUNSET to colors.chart1).forEach { (accent, accentColor) ->
                val isSelected = selectedAccent == accent
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAccentChange(accent); AppState.changeAccent(accent) }) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(accentColor).border(if (isSelected) 3.dp else 0.dp, if (isSelected) colors.textPrimary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    Text(accent.name, style = AmazeTheme.typography.smallLabel.copy(color = if (isSelected) colors.textPrimary else colors.textSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showCustomAccent = true }) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(customAccent, customAccent.copy(alpha = 0.45f))))
                        .border(if (selectedAccent == AccentTheme.CUSTOM) 3.dp else 0.dp, if (selectedAccent == AccentTheme.CUSTOM) colors.textPrimary else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Palette, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                Text("Custom", style = AmazeTheme.typography.smallLabel.copy(color = if (selectedAccent == AccentTheme.CUSTOM) colors.textPrimary else colors.textSecondary, fontWeight = if (selectedAccent == AccentTheme.CUSTOM) FontWeight.Bold else FontWeight.Normal))
            }
        }
        if (showCustomAccent) {
            ColorPickerSheet(
                title = "Custom Accent",
                initial = customAccent,
                colors = colors,
                onSelected = { color ->
                    AppState.setCustomAccent(color)
                    onAccentChange(AccentTheme.CUSTOM)
                    showCustomAccent = false
                },
                onDismiss = { showCustomAccent = false }
            )
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("FEEL", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(4.dp)) {
                ToggleRow("Haptic Feedback", "Vibrate on button taps, card presses & navigation", Icons.Rounded.Vibration, hapticsEnabled, onHapticsChange, colors, requestPermission = false)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("Spring Animations", "Bouncy press-scale physics on interactive cards", Icons.Rounded.Animation, animationsEnabled, onAnimationsChange, colors, requestPermission = false)
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("UI SCALE", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.85f to "Small", 1.0f to "Default", 1.15f to "Large").forEach { (scale, label) ->
                AmazePill(
                    label = label,
                    selected = uiScale == scale,
                    colors = colors,
                    onClick = { onUiScaleChange(scale); AppState.changeUiScale(scale) }
                )
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("DISPLAY", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { onCgpaHiddenChange(!cgpaHidden) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (cgpaHidden) colors.chart5.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (cgpaHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = if (cgpaHidden) colors.chart5 else colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide CGPA", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                        Text("Keep your CGPA private on dashboard", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    Switch(checked = cgpaHidden, onCheckedChange = onCgpaHiddenChange, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.border)
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Percent, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.md))
                        Text("Attendance Display", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(AttendanceDisplayMode.PERCENTAGE to "Percentage", AttendanceDisplayMode.FRACTION to "Fraction").forEach { (mode, label) ->
                            AmazePill(
                                label = label,
                                selected = attendanceMode == mode,
                                colors = colors,
                                onClick = { onAttendanceModeChange(mode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Page 3: Academic Preferences ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcademicPrefsPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    selectedSemester: String, onSemesterChange: (String) -> Unit,
    selectedCalendar: String, onCalendarChange: (String) -> Unit
) {
    val semesterMap by AppState.semesterMap.collectAsState()
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        OnboardingSectionLabel("SEMESTER", colors, tint = colors.success)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Your data is synced for this semester. Change anytime in Settings", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        val semIds = semesterMap.keys.toList().sortedDescending()
        if (semIds.isEmpty()) {
            Text("Semesters are still syncing — your current semester stays selected", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            semIds.forEach { semId ->
                AmazePill(
                    label = semesterMap[semId] ?: semId,
                    selected = semId == selectedSemester,
                    colors = colors,
                    tint = colors.success,
                    onClick = { onSemesterChange(semId) }
                )
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("DEFAULT CALENDAR", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Which class-group calendar to show on the Calendar page", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmazeClient.calendarTypes.forEach { (_, name) ->
                AmazePill(
                    label = name,
                    selected = selectedCalendar == name,
                    colors = colors,
                    onClick = { onCalendarChange(name) }
                )
            }
        }
    }
}

// ─── Page 4: Residential & Notifications ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResidentialNotifPage(
    colors: com.amazecc.app.shared.theme.AmazeColors,
    residentialStatus: String, onResidentialChange: (String) -> Unit,
    classNotif: Boolean, onClassNotifChange: (Boolean) -> Unit,
    assignNotif: Boolean, onAssignNotifChange: (Boolean) -> Unit,
    taskNotif: Boolean, onTaskNotifChange: (Boolean) -> Unit,
    examNotif: Boolean, onExamNotifChange: (Boolean) -> Unit,
    offsetMinutes: Int, onOffsetChange: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        OnboardingSectionLabel("RESIDENTIAL STATUS", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Helps us show relevant campus info", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("hosteller" to Icons.Rounded.Apartment, "dayscholar" to Icons.Rounded.Home, "unknown" to Icons.AutoMirrored.Rounded.HelpOutline).forEach { (status, icon) ->
                AmazePill(
                    label = when (status) { "hosteller" -> "Hosteller"; "dayscholar" -> "Day Scholar"; else -> "Not Sure" },
                    selected = residentialStatus == status,
                    colors = colors,
                    icon = icon,
                    onClick = { onResidentialChange(status) }
                )
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("NOTIFICATIONS & ALERTS", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("We'll remind you so you never miss out", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        AmazeCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.DEFAULT) {
            Column(modifier = Modifier.padding(4.dp)) {
                ToggleRow("Class Reminders", "Notify before each class starts", Icons.Rounded.Schedule, classNotif, onClassNotifChange, colors)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("Assignment Reminders", "Remind before deadlines", Icons.AutoMirrored.Rounded.Assignment, assignNotif, onAssignNotifChange, colors)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("Task Reminders", "Remind about tasks on due date", Icons.Rounded.TaskAlt, taskNotif, onTaskNotifChange, colors)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.border)
                ToggleRow("Exam Reminders", "Remind 24h prior & at reporting time", Icons.Rounded.EventSeat, examNotif, onExamNotifChange, colors)
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.lg))
        OnboardingSectionLabel("CLASS REMINDER LEAD TIME", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("How many minutes early class alerts should trigger", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5, 10, 15, 30, 60).forEach { preset ->
                AmazePill(
                    label = "$preset min",
                    selected = offsetMinutes == preset,
                    colors = colors,
                    onClick = { onOffsetChange(preset) }
                )
            }
        }
    }
}

// ─── Page 5: Modules ───
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
        OnboardingSectionLabel("PINNED TABS", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("These appear in your bottom nav bar (max 4)", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.md))

        val chunked = availableModules.chunked(2)
        chunked.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { module ->
                    val isSelected = selectedModules.contains(module)
                    val (icon, label) = getScreenIconAndLabel(module)
                    AmazeCard(
                        modifier = Modifier.weight(1f),
                        variant = CardVariant.DEFAULT,
                        onClick = { if (isSelected) onSelectionChange(selectedModules - module) else if (selectedModules.size < 4) onSelectionChange(selectedModules + module) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Icon(icon, null, tint = if (isSelected) colors.accent else colors.textSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text(label, color = if (isSelected) colors.accent else colors.textSecondary, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, maxLines = 1)
                            if (isSelected) { Spacer(Modifier.height(AmazeTheme.spacing.xs)); Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
        }
    }
}

// ─── Page 6: Dashboard Widgets ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardWidgetsPage(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val widgetOrder by AppState.widgetOrder.collectAsState()
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        OnboardingSectionLabel("HOME SCREEN WIDGETS", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Toggle the widgets you want on your home screen — reorder them later in Settings", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardWidget.entries.forEach { widget ->
                AmazePill(
                    label = getWidgetTitle(widget),
                    selected = widget in widgetOrder,
                    colors = colors,
                    onClick = { AppState.setWidgetEnabled(widget, widget !in widgetOrder) }
                )
            }
        }
        Spacer(Modifier.height(AmazeTheme.spacing.md))
        Text(
            "${widgetOrder.size} of ${DashboardWidget.entries.size} widgets visible",
            style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
        )
    }
}

// ─── Page 7: Accounts ───
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
        OnboardingSectionLabel("LINK YOUR ACCOUNTS", colors)
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

// ─── Page 8: Auto Sync ───
private fun formatClock(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoSyncPage(colors: com.amazecc.app.shared.theme.AmazeColors) {
    var refreshKey by remember { mutableStateOf(0) }
    val refresh = { refreshKey++ }

    val profiles by SyncEngine.profiles.collectAsState()
    val enabled = remember(refreshKey) { SyncScheduler.isEnabled() }
    val lightDaily = remember(refreshKey) { SyncScheduler.isLightDaily() }
    val lightInterval = remember(refreshKey) { SyncScheduler.lightIntervalDays() }
    val lightHour = remember(refreshKey) { SyncScheduler.lightHour() }
    val lightMinute = remember(refreshKey) { SyncScheduler.lightMinute() }
    val lightProfileId = remember(refreshKey) { SyncScheduler.lightProfileId() }
    val fullDay = remember(refreshKey) { SyncScheduler.fullDayOfWeek() }
    val fullHour = remember(refreshKey) { SyncScheduler.fullHour() }
    val fullMinute = remember(refreshKey) { SyncScheduler.fullMinute() }
    val fullProfileId = remember(refreshKey) { SyncScheduler.fullProfileId() }

    var showLightTimePicker by remember { mutableStateOf(false) }
    var showFullTimePicker by remember { mutableStateOf(false) }

    val weekdays = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        OnboardingSectionLabel("AUTO SYNC", colors)
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text("Keep your data fresh in the background — light refreshes daily, a full sync runs weekly", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(Modifier.height(AmazeTheme.spacing.md))

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (enabled) colors.accent.copy(alpha = 0.15f) else colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            null,
                            tint = if (enabled) colors.accent else colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Auto Sync", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Background sync on a schedule", style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary))
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { SyncScheduler.setEnabled(it); refresh() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.background
                    )
                )
            }
        }

        if (!enabled) {
            Text(
                "Auto sync is turned off — nothing runs on a schedule.",
                style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
            )
        }

        Spacer(Modifier.height(AmazeTheme.spacing.md))

        // ── Daily quick sync ──
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bolt, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "DAILY QUICK SYNC",
                            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent, letterSpacing = 1.sp)
                        )
                        Text(
                            "Refreshes the chosen profile once a day",
                            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazePill(
                        label = "Every Day",
                        selected = lightDaily,
                        colors = colors,
                        onClick = { SyncScheduler.setLightRecurrence(true); refresh() }
                    )
                    AmazePill(
                        label = "Every N Days",
                        selected = !lightDaily,
                        colors = colors,
                        onClick = { SyncScheduler.setLightRecurrence(false); refresh() }
                    )
                }

                if (!lightDaily) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Every", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        IconButton(
                            onClick = { SyncScheduler.setLightIntervalDays(lightInterval - 1); refresh() },
                            enabled = lightInterval > 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Remove, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "$lightInterval day${if (lightInterval > 1) "s" else ""}",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        IconButton(
                            onClick = { SyncScheduler.setLightIntervalDays(lightInterval + 1); refresh() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                PickerField(
                    value = formatClock(lightHour, lightMinute),
                    label = "Sync time",
                    colors = colors,
                    icon = Icons.Rounded.Schedule,
                    onClick = { showLightTimePicker = true }
                )

                Text(
                    "PROFILE",
                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted, letterSpacing = 1.sp, fontSize = AmazeTheme.fontSize.micro)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(profiles, key = { it.id }) { profile ->
                        AmazePill(
                            label = profile.name,
                            selected = profile.id == lightProfileId,
                            colors = colors,
                            onClick = { SyncScheduler.setLightProfileId(profile.id); refresh() }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.md))

        // ── Weekly full sync ──
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(AmazeTheme.radius.medium),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.chart1.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DateRange, null, tint = colors.chart1, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "WEEKLY FULL SYNC",
                            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.chart1, letterSpacing = 1.sp)
                        )
                        Text(
                            "Full refresh of everything, once a week",
                            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekdays.forEach { (label, value) ->
                        AmazePill(
                            label = label,
                            selected = fullDay == value,
                            colors = colors,
                            tint = colors.chart1,
                            onClick = { SyncScheduler.setFullDayOfWeek(value); refresh() }
                        )
                    }
                }

                PickerField(
                    value = formatClock(fullHour, fullMinute),
                    label = "Sync time",
                    colors = colors,
                    icon = Icons.Rounded.Schedule,
                    onClick = { showFullTimePicker = true }
                )

                Text(
                    "PROFILE",
                    style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textMuted, letterSpacing = 1.sp, fontSize = AmazeTheme.fontSize.micro)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(profiles, key = { it.id }) { profile ->
                        AmazePill(
                            label = profile.name,
                            selected = profile.id == fullProfileId,
                            colors = colors,
                            onClick = { SyncScheduler.setFullProfileId(profile.id); refresh() }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AmazeTheme.spacing.md))
        Text(
            "Syncs also run when the app opens if a scheduled time was missed. You can tune all of this later in Settings.",
            style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
        )
    }

    if (showLightTimePicker) {
        TimePickerSheet(
            title = "Daily Sync Time",
            initial = LocalTime(lightHour, lightMinute),
            colors = colors,
            onSelected = { time ->
                SyncScheduler.setLightTime(time.hour, time.minute)
                refresh()
            },
            onDismiss = { showLightTimePicker = false }
        )
    }

    if (showFullTimePicker) {
        TimePickerSheet(
            title = "Weekly Sync Time",
            initial = LocalTime(fullHour, fullMinute),
            colors = colors,
            onSelected = { time ->
                SyncScheduler.setFullTime(time.hour, time.minute)
                refresh()
            },
            onDismiss = { showFullTimePicker = false }
        )
    }
}

// ─── Page 9: Completion ───
@Composable
private fun CompletionPage(colors: com.amazecc.app.shared.theme.AmazeColors, syncSteps: List<AppState.SyncStep>) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)
    ) {
        Spacer(Modifier.height(AmazeTheme.spacing.sm))
        OnboardingHeroCard(Icons.Rounded.CheckCircle, "You're all set!", "TELL", colors.accent, colors) {
            Text(
                "Your preferences are saved. Tap Get Started to dive in!",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = AmazeTheme.fontSize.sm
            )
            FrostedPanel(colors) {
                val doneCount = syncSteps.count { it.status == "done" }
                val failedCount = syncSteps.count { it.status == "failed" }
                val activeCount = syncSteps.count { it.status == "syncing" }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CompletionStat("Completed", "$doneCount", colors)
                    CompletionStat("Failed", "$failedCount", colors)
                    CompletionStat("In Progress", "$activeCount", colors)
                }
                if (syncSteps.isNotEmpty()) {
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    syncSteps.forEach { step ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val alpha = when (step.status) { "done" -> 0.95f; "syncing" -> 0.9f; "failed" -> 0.55f; else -> 0.4f }
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
                            Spacer(Modifier.width(10.dp))
                            Text(step.name, color = Color.White.copy(alpha = if (step.status == "pending") 0.45f else 0.9f), fontSize = AmazeTheme.fontSize.xs, modifier = Modifier.weight(1f))
                            val label = when (step.status) { "done" -> "Synced"; "failed" -> "Failed"; "syncing" -> "Syncing"; else -> "Pending" }
                            Text(label, color = Color.White.copy(alpha = if (step.status == "pending") 0.4f else 0.75f), fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionStat(label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = Color.White))
        Text(label, style = AmazeTheme.typography.smallLabel.copy(color = Color.White.copy(alpha = 0.7f)))
    }
}

// ─── Shared Components ───
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    requestPermission: Boolean = true
) {
    val notifPermissionManager = LocalNotificationPermissionManager.current
    val handleToggle: (Boolean) -> Unit = { newChecked ->
        if (newChecked && requestPermission) {
            notifPermissionManager?.requestPermission()
        }
        onCheckedChange(newChecked)
    }
    Row(modifier = Modifier.fillMaxWidth().clickable { handleToggle(!checked) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(if (checked) colors.chart5.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (checked) colors.chart5 else colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
            Text(subtitle, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        }
        Switch(checked = checked, onCheckedChange = handleToggle, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White))
    }
}