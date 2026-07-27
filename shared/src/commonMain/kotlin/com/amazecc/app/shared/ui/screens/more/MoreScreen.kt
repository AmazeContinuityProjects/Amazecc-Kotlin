package com.amazecc.app.shared.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.bouncySpring

@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().background(colors.background).padding(paddingValues)) {
        ScreenHeader(
            title = "More",
            description = "Modules, Communities & Info",
            showBackButton = false,
            showSyncButton = false
        )

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 88.dp)) {
            com.amazecc.app.shared.ui.components.HeaderSpacer()

            Text("App Library", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.chart1))
            Spacer(modifier = Modifier.height(12.dp))

            val modules = listOf(
                Pair(Screen.CALENDAR, Icons.Rounded.CalendarMonth to "Calendar"),
                Pair(Screen.PAYMENTS, Icons.Rounded.CreditCard to "Payments"),
                Pair(Screen.LIBRARIES, Icons.AutoMirrored.Rounded.LibraryBooks to "Library"),
                Pair(Screen.HOSTEL, Icons.Rounded.Apartment to "Hostel"),
                Pair(Screen.TRANSPORT, Icons.Rounded.DirectionsBus to "Transport"),
                Pair(Screen.CABSHARE, Icons.Rounded.DirectionsCar to "Cabshare"),
                Pair(Screen.EVENTS, Icons.Rounded.Event to "Events"),
                Pair(Screen.QBANK, Icons.Rounded.Topic to "QBank"),
                Pair(Screen.SOCIAL, Icons.Rounded.People to "Social"),
                Pair(Screen.FFCS_PLANNER, Icons.Rounded.ViewTimeline to "FFCS"),
                Pair(Screen.FREE_CLASSROOMS, Icons.Rounded.MeetingRoom to "Classes"),
                Pair(Screen.MOODLE, Icons.AutoMirrored.Rounded.MenuBook to "Moodle")
            )

            val chunkedModules = modules.chunked(3)
            AmazeCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    chunkedModules.forEach { rowModules ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowModules.forEach { (screen, iconAndLabel) ->
                                val (icon, label) = iconAndLabel
                                ModuleIcon(
                                    icon = icon,
                                    label = label,
                                    onClick = { AppState.navigateTo(screen) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowModules.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Services & Tools", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.chart2))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard(title = "Faculty Info", description = "Faculty directory by school", icon = Icons.Rounded.People, onClick = { AppState.navigateTo(Screen.FACULTY_INFO) })
                    Spacer(Modifier.height(4.dp))
                    ActionCard(title = "Feedback", description = "Course feedback status", icon = Icons.Rounded.RateReview, onClick = { AppState.navigateTo(Screen.FEEDBACK_STATUS) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Communities", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.chart3))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { AppState.openClubHub("Directory") }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.chart2, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Club Hub", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.chart2))
                        Text("Explore student clubs and chapters", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth(), onClick = { AppState.openClubHub("Feed") }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = colors.chart4, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Community Feed", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.chart4))
                        Text("Latest posts from AmazeCC members", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Notifications", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.chart4))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    var classNotif by remember { mutableStateOf(SettingsManager.isNotifClassRemindersEnabled()) }
                    ToggleRow(
                        title = "Class Reminders",
                        subtitle = "Notify before each class starts",
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
                        subtitle = "Remind about homework and tasks on due date",
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
            }

            Spacer(modifier = Modifier.height(12.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var offsetMinutes by remember { mutableStateOf(SettingsManager.getNotifOffsetMinutes()) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.chart2.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Timer, null, tint = colors.chart2, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remind Before Class", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text("How many minutes early", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(5, 10, 15, 30, 60)
                        presets.forEach { preset ->
                            val selected = offsetMinutes == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) colors.accent else colors.surface)
                                    .border(if (selected) 0.dp else 1.dp, colors.border, RoundedCornerShape(8.dp))
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
            }

            Spacer(modifier = Modifier.height(12.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.chart1.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.BugReport, null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Diagnostic Test", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text("Send a mock notification to verify permissions are active and working.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                    AmazeButton("Send Test Notification", onClick = { scope.launch { snackbarHostState.showSnackbar(AppState.sendTestNotification()) } }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }



            Spacer(modifier = Modifier.height(24.dp))
            Text("Settings & Info", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.chart5))
            Spacer(modifier = Modifier.height(12.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ClickableRow(title = "App Settings", icon = Icons.Rounded.Settings, onClick = { AppState.navigateTo(Screen.SETTINGS) }, colors = colors)
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    ClickableRow(title = "About AmazeCC", icon = Icons.Rounded.Info, onClick = { AppState.navigateTo(Screen.ABOUT) }, colors = colors)
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    ClickableRow(title = "Fresher's Welcome", icon = Icons.Rounded.Star, onClick = { AppState.navigateTo(Screen.FRESHER_WELCOME) }, colors = colors)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    AmazeButton("Log Out", onClick = { AppState.logout() }, variant = ButtonVariant.DANGER, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
    }
}

@Composable
fun ModuleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    val moduleColors = listOf(colors.chart1, colors.chart2, colors.chart3, colors.chart4, colors.chart5)
    val colorIndex = label.hashCode().mod(moduleColors.size).let { if (it < 0) it + moduleColors.size else it }
    val neonColor = moduleColors[colorIndex]

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = bouncySpring()
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(neonColor.copy(alpha = 0.14f))
                .border(1.dp, neonColor.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = neonColor, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(color = neonColor, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = colors.textPrimary, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold))
                Text(subtitle, color = colors.textSecondary, style = AmazeTheme.typography.caption)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.accent, checkedTrackColor = colors.accent.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun ClickableRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.chart5.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.chart5, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
    }
}
