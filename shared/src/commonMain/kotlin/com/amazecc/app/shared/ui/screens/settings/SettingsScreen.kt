package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.*
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch

enum class SettingsSubScreen(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    APPEARANCE("Appearance & Theme", "Color themes, accent colors & OLED pitch black", Icons.Rounded.Palette),
    INTERACTIONS("Interactions & Feel", "Haptic feedback & bouncy spring animations", Icons.Rounded.Vibration),
    DISPLAY("Display & Layout Scale", "Attendance display format & UI zoom slider", Icons.Rounded.Visibility),
    BOTTOM_NAV("Bottom Navigation", "Customize pinned bottom bar tabs & reorder", Icons.Rounded.Navigation),
    ACADEMICS("Academics & Semester", "Target semester & default calendar dropdowns", Icons.Rounded.School),
    NOTIFICATIONS("Notifications & Reminders", "Class, assignment, task alerts & time offsets", Icons.Rounded.Notifications),
    CREDENTIALS("Persistent Credentials", "Saved VTOP, Moodle LMS & Library accounts", Icons.Rounded.Lock),
    SYNC("Data Sync & Engine Hub", "Sync progress, module status & cache management", Icons.Rounded.Sync),
    DANGER("Danger Zone & Account", "Clear local cache & log out session", Icons.Rounded.Warning)
}

@Composable
fun SettingsScreen() {
    val colors = AmazeTheme.colors
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    var preferredCalendarName by remember { mutableStateOf(SettingsManager.getPreferredCalendar() ?: "") }
    val semesterMap by AppState.semesterMap.collectAsState()

    var selectedTabsList by remember(pinnedTabs) { mutableStateOf(pinnedTabs) }
    var showAddModuleDialog by remember { mutableStateOf(false) }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    val notifPermissionManager = LocalNotificationPermissionManager.current
    val pendingToggleAction = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var showPushPrompt by remember { mutableStateOf(false) }

    if (showPushPrompt) {
        PushPromptModal(
            onEnable = {
                showPushPrompt = false
                notifPermissionManager?.requestPermission()
                pendingToggleAction.value?.let { action -> action(true) }
                pendingToggleAction.value = null
                AppState.rescheduleNotifications()
            },
            onDismiss = {
                showPushPrompt = false
                pendingToggleAction.value = null
            }
        )
    }

    val availableTabs = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE,
        Screen.PAYMENTS, Screen.CABSHARE, Screen.TRANSPORT, Screen.CALENDAR,
        Screen.FFCS_PLANNER, Screen.FREE_CLASSROOMS, Screen.QBANK, Screen.SOCIAL,
        Screen.PROJECTS, Screen.WISHLIST
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
        ) {
            ScreenHeader(
                title = currentSubScreen?.title ?: "App Settings",
                description = currentSubScreen?.description ?: "Customize your experience & app preferences",
                showBackButton = true,
                showSyncButton = false,
                onBackOverride = if (currentSubScreen != null) { { currentSubScreen = null } } else null
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeaderSpacer()

                if (currentSubScreen == null) {
                    // ═══════════════════════════════════════════
                    // MAIN SETTINGS HUB (SUBMENU CARDS)
                    // ═══════════════════════════════════════════
                    AmazeSearchInput(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search all settings (e.g., Theme, Semester, Sync, Haptics)..."
                    )

                    val filteredSubScreens = SettingsSubScreen.entries.filter { sub ->
                        searchQuery.isBlank() ||
                                sub.title.contains(searchQuery, ignoreCase = true) ||
                                sub.description.contains(searchQuery, ignoreCase = true)
                    }

                    filteredSubScreens.forEach { subScreen ->
                        SubmenuCard(
                            subScreen = subScreen,
                            onClick = { currentSubScreen = subScreen },
                            colors = colors
                        )
                    }
                } else {
                    // ═══════════════════════════════════════════
                    // DEDICATED SUB-PAGE VIEWS
                    // ═══════════════════════════════════════════
                    when (currentSubScreen) {
                        SettingsSubScreen.APPEARANCE -> {
                            val activeTheme by AppState.theme.collectAsState()
                            val activeAccent by AppState.accent.collectAsState()

                            SettingsSection("Color Theme", Icons.Rounded.Palette, colors) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            AppTheme.LIGHT to "Light",
                                            AppTheme.DARK to "Dark",
                                            AppTheme.SYSTEM to "System"
                                        ).forEach { (theme, label) ->
                                            val isSelected = activeTheme == theme
                                            AmazeButton(
                                                text = label,
                                                onClick = { AppState.changeTheme(theme) },
                                                modifier = Modifier.weight(1f),
                                                variant = if (isSelected) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                                            )
                                        }
                                    }
                                    val isAmoled = activeTheme == AppTheme.AMOLED
                                    AmazeButton(
                                        text = if (isAmoled) "✦ AMOLED Pure Black" else "AMOLED Pure Black",
                                        onClick = { AppState.changeTheme(AppTheme.AMOLED) },
                                        modifier = Modifier.fillMaxWidth(),
                                        variant = if (isAmoled) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                                    )
                                }
                            }

                            SettingsSection("Accent Colors", Icons.Rounded.ColorLens, colors) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AccentSwatch("Ocean", AccentTheme.OCEAN, activeAccent, colors, Modifier.weight(1f))
                                    AccentSwatch("Forest", AccentTheme.FOREST, activeAccent, colors, Modifier.weight(1f))
                                    AccentSwatch("Lavender", AccentTheme.LAVENDER, activeAccent, colors, Modifier.weight(1f))
                                    AccentSwatch("Sunset", AccentTheme.SUNSET, activeAccent, colors, Modifier.weight(1f))
                                }
                            }
                        }

                        SettingsSubScreen.INTERACTIONS -> {
                            val hapticEnabled by AppState.hapticEnabled.collectAsState()
                            val animationsEnabled by AppState.animationsEnabled.collectAsState()

                            SettingsSection("Touch & Motion Physics", Icons.Rounded.Vibration, colors) {
                                SettingsSimpleToggle(
                                    label = "Haptic Feedback",
                                    description = "Vibrate on button taps, card presses & navigation",
                                    checked = hapticEnabled,
                                    onCheckedChange = { AppState.setHapticEnabled(it) },
                                    colors = colors
                                )

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                SettingsSimpleToggle(
                                    label = "Spring Animations",
                                    description = "Bouncy press-scale physics on interactive cards & swatches",
                                    checked = animationsEnabled,
                                    onCheckedChange = { AppState.setAnimationsEnabled(it) },
                                    colors = colors
                                )
                            }
                        }

                        SettingsSubScreen.DISPLAY -> {
                            val cgpaHidden by AppState.cgpaHidden.collectAsState()
                            val attendanceMode by AppState.attendanceDisplayMode.collectAsState()
                            val currentScale by AppState.uiScale.collectAsState()

                            SettingsSection("Display Preferences", Icons.Rounded.Visibility, colors) {
                                SettingsToggle("Hide CGPA on Dashboard", cgpaHidden, { AppState.setCgpaHidden(it) }, colors)

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Attendance Format", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                                    AmazeSegmentedControl(
                                        items = listOf(
                                            AttendanceDisplayMode.PERCENTAGE to "Percentage (%)",
                                            AttendanceDisplayMode.FRACTION to "Fraction (x/y)"
                                        ),
                                        selectedItem = attendanceMode,
                                        onItemSelected = { AppState.setAttendanceDisplayMode(it) }
                                    )
                                }

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("UI Scale & Zoom", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        IconButton(
                                            onClick = { AppState.changeUiScale((currentScale - 0.05f).coerceIn(0.7f, 1.5f)) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.ZoomOut, "Decrease scale", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                        Slider(
                                            value = currentScale,
                                            onValueChange = { AppState.changeUiScale(it) },
                                            valueRange = 0.7f..1.5f,
                                            steps = 7,
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
                                        )
                                        IconButton(
                                            onClick = { AppState.changeUiScale((currentScale + 0.05f).coerceIn(0.7f, 1.5f)) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.ZoomIn, "Increase scale", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Current Scale: ${(currentScale * 100).toInt()}%", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                                        TextButton(onClick = { AppState.changeUiScale(1.0f) }) {
                                            Text("Reset (100%)", color = colors.accent, fontSize = AmazeTheme.fontSize.xs, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        SettingsSubScreen.BOTTOM_NAV -> {
                            SettingsSection("Bottom Navigation Customizer", Icons.Rounded.Navigation, colors) {
                                // Live Preview
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                        .background(colors.navBackground)
                                        .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.medium))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("LIVE BOTTOM BAR PREVIEW", style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Rounded.Home, "Home", tint = colors.accent, modifier = Modifier.size(20.dp))
                                            Text("Home", color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        selectedTabsList.forEach { tab ->
                                            val (icon, label) = getScreenIconAndLabel(tab)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(icon, label, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                                                Text(label, color = colors.textSecondary, fontSize = 10.sp, maxLines = 1)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Rounded.Grid3x3, "More", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                                            Text("More", color = colors.textSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(AmazeTheme.spacing.xs))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Pinned Tabs (${selectedTabsList.size}/4)", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base)
                                    if (selectedTabsList.size < 4) {
                                        AmazeButton(
                                            text = "+ Add Module",
                                            onClick = { showAddModuleDialog = true },
                                            variant = ButtonVariant.PRIMARY,
                                            height = 36.dp
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                if (selectedTabsList.isEmpty()) {
                                    Text(
                                        "No additional tabs pinned. Tap '+ Add Module' to pin quick access tabs.",
                                        color = colors.textMuted,
                                        fontSize = AmazeTheme.fontSize.xs
                                    )
                                }

                                selectedTabsList.forEachIndexed { index, tab ->
                                    val (icon, label) = getScreenIconAndLabel(tab)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                            .background(colors.accent.copy(alpha = 0.12f))
                                            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.DragHandle, "Handle bar", tint = colors.accent, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Icon(icon, label, tint = colors.accent, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(label, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, modifier = Modifier.weight(1f))

                                        if (index > 0) {
                                            IconButton(
                                                onClick = {
                                                    val newList = selectedTabsList.toMutableList()
                                                    val temp = newList[index]
                                                    newList[index] = newList[index - 1]
                                                    newList[index - 1] = temp
                                                    selectedTabsList = newList
                                                    AppState.setPinnedNavTabs(newList)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        if (index < selectedTabsList.size - 1) {
                                            IconButton(
                                                onClick = {
                                                    val newList = selectedTabsList.toMutableList()
                                                    val temp = newList[index]
                                                    newList[index] = newList[index + 1]
                                                    newList[index + 1] = temp
                                                    selectedTabsList = newList
                                                    AppState.setPinnedNavTabs(newList)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val newList = selectedTabsList.filter { it != tab }
                                                selectedTabsList = newList
                                                AppState.setPinnedNavTabs(newList)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Rounded.Close, "Unpin", tint = colors.dangerText, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }

                        SettingsSubScreen.ACADEMICS -> {
                            val semIds = semesterMap.keys.toList().sortedDescending()

                            SettingsSection("Academics & Semester Dropdowns", Icons.Rounded.School, colors) {
                                Text("Select your target semester and default academic calendar from the dropdowns below.", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                                Spacer(Modifier.height(8.dp))

                                var semesterExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = semesterMap[selectedSemester] ?: selectedSemester,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Target Semester", color = colors.textSecondary) },
                                        trailingIcon = {
                                            IconButton(onClick = { semesterExpanded = !semesterExpanded }) {
                                                Icon(if (semesterExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = colors.accent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().clickable { semesterExpanded = !semesterExpanded },
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
                                        expanded = semesterExpanded,
                                        onDismissRequest = { semesterExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.88f).background(colors.surface)
                                    ) {
                                        semIds.forEach { semId ->
                                            val isSelected = semId == selectedSemester
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = semesterMap[semId] ?: semId,
                                                        color = if (isSelected) colors.accent else colors.textPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Rounded.Check, null, tint = colors.accent) }
                                                } else null,
                                                onClick = {
                                                    semesterExpanded = false
                                                    if (semId != selectedSemester) AppState.selectSemester(semId)
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(AmazeTheme.spacing.sm))

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

                        SettingsSubScreen.NOTIFICATIONS -> {
                            SettingsSection("Push Notifications & Class Alerts", Icons.Rounded.Notifications, colors) {
                                var classNotif by remember { mutableStateOf(SettingsManager.isNotifClassRemindersEnabled()) }
                                ToggleRow(
                                    title = "Class Reminders",
                                    subtitle = "Notify before each scheduled class starts",
                                    icon = Icons.Rounded.Schedule,
                                    checked = classNotif,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !classNotif && notifPermissionManager != null) {
                                            pendingToggleAction.value = { _ -> classNotif = true; SettingsManager.setNotifClassRemindersEnabled(true) }
                                            showPushPrompt = true
                                        } else {
                                            classNotif = enabled; SettingsManager.setNotifClassRemindersEnabled(enabled)
                                            if (!enabled) AppState.rescheduleNotifications()
                                        }
                                    },
                                    colors = colors
                                )

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                var assignNotif by remember { mutableStateOf(SettingsManager.isNotifAssignmentRemindersEnabled()) }
                                ToggleRow(
                                    title = "Assignment Reminders",
                                    subtitle = "Remind before assignment deadlines",
                                    icon = Icons.Rounded.Assignment,
                                    checked = assignNotif,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !assignNotif && notifPermissionManager != null) {
                                            pendingToggleAction.value = { _ -> assignNotif = true; SettingsManager.setNotifAssignmentRemindersEnabled(true) }
                                            showPushPrompt = true
                                        } else {
                                            assignNotif = enabled; SettingsManager.setNotifAssignmentRemindersEnabled(enabled)
                                            if (!enabled) AppState.rescheduleNotifications()
                                        }
                                    },
                                    colors = colors
                                )

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                var taskNotif by remember { mutableStateOf(SettingsManager.isNotifTaskRemindersEnabled()) }
                                ToggleRow(
                                    title = "Task Reminders",
                                    subtitle = "Remind about tasks on due date",
                                    icon = Icons.Rounded.TaskAlt,
                                    checked = taskNotif,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !taskNotif && notifPermissionManager != null) {
                                            pendingToggleAction.value = { _ -> taskNotif = true; SettingsManager.setNotifTaskRemindersEnabled(true) }
                                            showPushPrompt = true
                                        } else {
                                            taskNotif = enabled; SettingsManager.setNotifTaskRemindersEnabled(enabled)
                                            if (!enabled) AppState.rescheduleNotifications()
                                        }
                                    },
                                    colors = colors
                                )
                            }

                            SettingsSection("Class Reminder Lead Time", Icons.Rounded.Timer, colors) {
                                var offsetMinutes by remember { mutableStateOf(SettingsManager.getNotifOffsetMinutes()) }
                                Text("Select how many minutes early class alerts should trigger:", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                                Spacer(Modifier.height(4.dp))
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
                            }

                            SettingsSection("System Verification", Icons.Rounded.BugReport, colors) {
                                Text("Send a mock test notification to confirm push alerts are active.", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                                Spacer(Modifier.height(4.dp))
                                AmazeButton("Send Test Notification", onClick = { scope.launch { snackbarHostState.showSnackbar(AppState.sendTestNotification()) } }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        SettingsSubScreen.CREDENTIALS -> {
                            SettingsSection("Persistent Credentials", Icons.Rounded.Lock, colors) {
                                val savedUsername = SettingsManager.getString(SettingsManager.KEY_USERNAME)
                                val moodleCreds = SettingsManager.getMoodleCredentials()
                                val libCreds = SettingsManager.getLibraryCredentials()

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("VTOP Credentials", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                                            Text(if (savedUsername.isNotBlank()) savedUsername else "No credentials saved", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                                        }
                                        AmazeBadge(
                                            text = if (savedUsername.isNotBlank()) "CONNECTED" else "NOT SAVED",
                                            variant = if (savedUsername.isNotBlank()) BadgeVariant.SUCCESS else BadgeVariant.WARNING
                                        )
                                    }

                                    var showCredEditor by remember { mutableStateOf(false) }
                                    if (showCredEditor) {
                                        var username by remember { mutableStateOf(savedUsername) }
                                        var password by remember { mutableStateOf("") }
                                        Spacer(Modifier.height(4.dp))
                                        AmazeTextField(value = username, onValueChange = { username = it }, label = "Registration Number", placeholder = "e.g. 21BCE0001", modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                        AmazeTextField(value = password, onValueChange = { password = it }, label = "Password", placeholder = "••••••••", modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AmazeButton("Cancel", onClick = { showCredEditor = false }, variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                                            AmazeButton("Save & Overwrite", onClick = { SettingsManager.saveCredentials(username, password); showCredEditor = false }, modifier = Modifier.weight(1f))
                                        }
                                    } else {
                                        AmazeButton("Edit VTOP Credentials", onClick = { showCredEditor = true }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                                    }
                                }

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Moodle LMS Account", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                                        Text(if (moodleCreds != null) "Linked as ${moodleCreds.first}" else "Not linked", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                                    }
                                    if (moodleCreds != null) {
                                        TextButton(onClick = { SettingsManager.clearMoodleCredentials(); AppState.updateMoodleData(com.amazecc.app.shared.model.MoodleRes(success = false, message = "Cleared")) }) {
                                            Text("Unlink", color = colors.dangerText, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(onClick = { AppState.navigateTo(Screen.MOODLE) }) {
                                            Text("Link", color = colors.accent, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Library Account", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                                        Text(if (libCreds != null) "Linked as ${libCreds.first}" else "Not linked", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                                    }
                                    if (libCreds != null) {
                                        TextButton(onClick = { SettingsManager.clearLibraryCredentials(); AppState.saveLibraryCredentials("", "") }) {
                                            Text("Unlink", color = colors.dangerText, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(onClick = { AppState.navigateTo(Screen.LIBRARIES) }) {
                                            Text("Link", color = colors.accent, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        SettingsSubScreen.SYNC -> {
                            val moduleStates by SyncEngine.moduleStates.collectAsState()
                            val syncProgress by SyncEngine.syncProgress.collectAsState()

                            SettingsSection("Data Sync & Engine Hub", Icons.Rounded.Sync, colors) {
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

                                Spacer(Modifier.height(AmazeTheme.spacing.xs))

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

                                val modulesToShow = SyncModule.entries.filter { it.cacheKey != null }
                                modulesToShow.forEach { module ->
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
                                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                            .background(if (isLoading) colors.accent.copy(alpha = 0.06f) else colors.surface)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                    Spacer(Modifier.height(2.dp))
                                }
                            }
                        }

                        SettingsSubScreen.DANGER -> {
                            SettingsSection("Danger Zone", Icons.Rounded.Warning, colors) {
                                AmazeButton(
                                    "Clear All Local Caches",
                                    onClick = { showClearCacheConfirm = true },
                                    variant = ButtonVariant.DANGER,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                AmazeButton(
                                    "Log Out Student Session",
                                    onClick = { showLogoutConfirm = true },
                                    variant = ButtonVariant.DANGER,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        null -> {}
                    }

                }

                FooterSpacer()
            }
        }
    }

    if (showAddModuleDialog) {
        val unpinned = availableTabs.filter { !selectedTabsList.contains(it) }
        AlertDialog(
            onDismissRequest = { showAddModuleDialog = false },
            title = { Text("Add Module to Bottom Bar", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (unpinned.isEmpty()) {
                        Text("All available modules are already pinned.", color = colors.textSecondary)
                    } else {
                        unpinned.forEach { screen ->
                            val (icon, label) = getScreenIconAndLabel(screen)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                    .clickable {
                                        val newList = selectedTabsList + screen
                                        selectedTabsList = newList
                                        AppState.setPinnedNavTabs(newList)
                                        showAddModuleDialog = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, label, tint = colors.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(label, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddModuleDialog = false }) {
                    Text("Close", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear Local Cache?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all cached attendance, marks, calendar, and module data from device storage.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { SettingsManager.clearAll(); showClearCacheConfirm = false }) {
                    Text("Clear All", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log Out Session?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("You will be logged out of AmazeCC and returned to the login screen.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { AppState.logout(); showLogoutConfirm = false }) {
                    Text("Log Out", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun SubmenuCard(
    subScreen: SettingsSubScreen,
    onClick: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = subScreen.icon,
                    contentDescription = subScreen.title,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subScreen.title,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    text = subScreen.description,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open sub-page",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Text(title, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.md)
        }
        Spacer(Modifier.height(6.dp))
        AmazeCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}


@Composable
private fun SettingsSimpleToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = colors.textPrimary, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold))
            Text(description, color = colors.textSecondary, style = AmazeTheme.typography.caption)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accent,
                checkedTrackColor = colors.accent.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val notifPermissionManager = LocalNotificationPermissionManager.current
    val handleToggle: (Boolean) -> Unit = { newChecked ->
        if (newChecked) {
            notifPermissionManager?.requestPermission()
        }
        onCheckedChange(newChecked)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.textPrimary, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = handleToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.accent, checkedTrackColor = colors.accent.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun AccentSwatch(name: String, accent: AccentTheme, current: AccentTheme, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    val selected = accent == current
    val swatchColor = when (accent) {
        AccentTheme.OCEAN -> colors.accent
        AccentTheme.FOREST -> colors.success
        AccentTheme.LAVENDER -> colors.info
        AccentTheme.SUNSET -> colors.warning
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (selected) swatchColor.copy(alpha = 0.18f) else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) swatchColor else colors.border,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { AppState.changeAccent(accent) }
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Text(
                text = name,
                color = if (selected) swatchColor else colors.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = AmazeTheme.fontSize.xs,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column {
                Text(title, color = colors.textPrimary, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base))
                Text(subtitle, color = colors.textSecondary, style = AmazeTheme.typography.caption.copy(fontSize = AmazeTheme.fontSize.xs))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.accent, checkedTrackColor = colors.accent.copy(alpha = 0.3f)),
            modifier = Modifier.scale(0.85f)
        )
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
