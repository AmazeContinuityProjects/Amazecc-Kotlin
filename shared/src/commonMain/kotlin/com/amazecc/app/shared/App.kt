package com.amazecc.app.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.screens.*
import com.amazecc.app.shared.ui.screens.m3e.*
import com.amazecc.app.shared.ui.screens.academics.*
import com.amazecc.app.shared.ui.components.MainTabPager
import com.amazecc.app.shared.ui.screens.onboarding.OnboardingScreen
import com.amazecc.app.shared.ui.m3e.components.M3ENavigationBar

@Composable
fun App() {
    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val customAccent by AppState.customAccentColor.collectAsState()
    val customPalette by AppState.customPalette.collectAsState()
    val currentScreen by AppState.currentScreen.collectAsState()
    // Which home the Home tab renders. Read as a flow so switching the style in
    // Settings repaints the tab rather than waiting for the next navigation.
    val homeViewMode by AppState.homeViewMode.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val syncError by AppState.error.collectAsState()

    val uiScale by AppState.uiScale.collectAsState()
    val hapticEnabled by AppState.hapticEnabled.collectAsState()
    val animationsEnabled by AppState.animationsEnabled.collectAsState()
    val heroColorEnabled by AppState.heroColorEnabled.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Observe SyncEngine outside of AppState.init to avoid classloading deadlocks
    LaunchedEffect(Unit) {
        AppState.observeSyncEngine()
    }
    // Load cached data outside of AppState.init — many referenced flows are declared after the init block.
    // Runs on a background dispatcher: ~24 cache JSON decodes (some multi-MB) must not pin the main thread.
    // Pure local-first start: app boots purely from local storage cache; manual sync is the trigger.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            AppState.loadFromCache()
        }
    }

    AmazeTheme(
        appTheme = currentTheme,
        accentTheme = currentAccent,
        customAccent = customAccent,
        customPalette = customPalette,
        hapticEnabled = hapticEnabled,
        animationsEnabled = animationsEnabled,
        heroColorEnabled = heroColorEnabled
    ) {
        val colors = AmazeTheme.colors
        
        val currentDensity = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                density = currentDensity.density * uiScale,
                fontScale = currentDensity.fontScale * uiScale
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && 
                        (event.isMetaPressed || event.isCtrlPressed) && 
                        event.key == Key.K) {
                        AppState.openCommandPalette()
                        true
                    } else false
                },
                color = colors.background
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    containerColor = colors.background
                ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // Global Spotlight Search Overlay
                    val commandPaletteOpen by AppState.commandPaletteOpen.collectAsState()

                    if (commandPaletteOpen) {
                        com.amazecc.app.shared.ui.components.CommandPalette(
                            isOpen = commandPaletteOpen,
                            onClose = { AppState.closeCommandPalette() },
                            commands = com.amazecc.app.shared.ui.components.rememberGlobalCommands()
                        )
                    }

                    // Crossfade screen transitions
                    Box(modifier = Modifier.fillMaxSize()) {
                        val tabList = AppState.tabScreens
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                if (targetState in tabList && initialState in tabList) {
                                    fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                                } else {
                                    fadeIn() togetherWith fadeOut()
                                }
                            }
                        ) { targetScreen ->
                        if (targetScreen in tabList) {
                            MainTabPager(
                                tabScreens = tabList,
                                currentScreen = targetScreen,
                                onScreenChange = { AppState.navigateTo(it) }
                            )
                        } else {
                            when (targetScreen) {
                                Screen.SPLASH -> M3ESplashScreen()
                                Screen.LOGIN -> M3ELoginScreen()
                                Screen.ONBOARDING -> OnboardingScreen()
                                Screen.PAYMENTS -> M3EPaymentsScreenHost()
                                Screen.HOSTEL -> M3EHostelScreen()
                                Screen.CABSHARE -> M3ECabShareScreen()
                                Screen.TRANSPORT -> M3ETransportScreen()
                                Screen.EVENTS -> M3EEventHubScreen()
                                Screen.SETTINGS -> M3ESettingsScreen()
                                Screen.QBANK -> M3EQBankWorkspaceHost()
                                Screen.SOCIAL -> M3ESocialScreenHost()
                                Screen.FFCS_PLANNER -> M3EFfcsPlannerScreenHost()
                                Screen.FREE_CLASSROOMS -> M3EFreeClassroomsHost()
                                Screen.CALENDAR -> M3ECalendarScreenHost()
                                Screen.GRADES -> M3EGradesHistoryScreen()
                                Screen.GPA_PREDICTOR -> M3EMarksPredictorScreen()
                                Screen.COURSE_DETAIL -> M3ECourseDetailScreen()
                                Screen.COURSE_ATTENDANCE -> M3ECourseAttendanceScreenHost()
                                Screen.CIRCULARS -> M3ECircularsScreenHost()
                                Screen.CURRICULUM -> M3ECurriculumScreen()
                                Screen.OD_TRACKER -> M3EODTrackerScreenHost()
                                Screen.COURSE_DASHBOARD -> M3ECourseDashboardScreenHost()
                                Screen.FACULTY_INFO -> M3EFacultyScreen()
                                Screen.FEEDBACK_STATUS -> M3EFeedbackStatusScreenHost()
                                Screen.FRESHER_WELCOME -> M3EFresherWelcomeScreenHost()
                                Screen.DOCUMENTS -> M3EDocumentsScreenHost()
                                Screen.ABOUT -> M3EAboutScreenHost()
                                Screen.CLUB_HUB -> M3EClubHubScreen()
                                Screen.CLUB_DETAIL -> M3EClubDetailScreenHost()
                                Screen.MOODLE -> M3EMoodleScreenHost()
                                Screen.TASKS -> M3ETasksScreen()
                                Screen.EXAM_SCHEDULE -> M3EExamScheduleHost()
                                Screen.CHANGELOG -> M3EChangelogScreenHost()
                                Screen.HALL_OF_FAME -> M3EHallOfFameScreenHost()
                                Screen.HOME -> M3EHomeScreen()
                                Screen.HOME_LEGACY -> M3EHomeScreen()
                                Screen.ATTENDANCE -> M3EAttendanceScreen()
                                Screen.ACADEMICS -> M3EAcademicsScreen()
                                Screen.LIBRARIES -> M3ELibrariesScreenHost()
                                Screen.PROFILE -> M3EProfileHost()
                                Screen.MORE -> M3EMoreHubScreen()
                            }
                        }
                    }
                    }

                    // These overlays are composed outside every per-screen AmazeM3ETheme wrapper.
                    // Provide the M3E palette at the root so they follow light/dark instead of
                    // falling back to M3ETheme's dark-only default palette.
                    com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme(
                        isDark = com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge.isDarkThemeActive()
                    ) {
                        // Floating M3E nav bar overlay
                        if (currentScreen != Screen.LOGIN && currentScreen != Screen.SPLASH && currentScreen != Screen.ONBOARDING) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 12.dp)
                            ) {
                                M3ENavigationBar()
                            }
                        }

                        // Material 3 Expressive Sync Notification & Modal (replaces legacy popup)
                        if (currentScreen != Screen.LOGIN && currentScreen != Screen.SPLASH) {
                            com.amazecc.app.shared.ui.m3e.components.M3ESyncNotification(
                                onDismiss = { SyncEngine.setShowSyncDialog(false) },
                                onSaveOffline = { AppState.saveOffline() },
                                onSyncAll = {
                                    // Never wipe the module ledger while a sweep is still writing
                                    // to it — that made the ring drop to 0% and stay stale.
                                    // beginSweep() resets the relevant states itself.
                                    SyncEngine.setShowSyncDialog(true)
                                    AppState.loadAllData()
                                }
                            )
                        }
                    }

                    // One-time alert for newly added exam venue/seat entries from credentials
                    val pendingSeatAlerts by AppState.pendingExamSeatAlerts.collectAsState()
                    if (pendingSeatAlerts.isNotEmpty() && currentScreen != Screen.LOGIN) {
                        AlertDialog(
                            onDismissRequest = { AppState.dismissExamSeatAlerts() },
                            containerColor = colors.surface,
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Exam Venue & Seat",
                                        style = AmazeTheme.typography.subheading.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    )
                                    IconButton(onClick = { AppState.dismissExamSeatAlerts() }) {
                                        Icon(Icons.Rounded.Close, "Dismiss", tint = colors.textSecondary)
                                    }
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        "Your exam venue and seat details were updated:",
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    pendingSeatAlerts.forEach { alert ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(colors.accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Event,
                                                    null,
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    alert.account,
                                                    style = AmazeTheme.typography.body.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.textPrimary
                                                    )
                                                )
                                                if (alert.venueDate.isNotBlank() || alert.seatLocation.isNotBlank()) {
                                                    Text(
                                                        buildString {
                                                            if (alert.venueDate.isNotBlank()) append(alert.venueDate)
                                                            if (alert.seatLocation.isNotBlank()) {
                                                                if (isNotEmpty()) append("  •  ")
                                                                append("Seat: ${alert.seatLocation}")
                                                            }
                                                        },
                                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { AppState.dismissExamSeatAlerts() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Got it", style = AmazeTheme.typography.smallLabel)
                                }
                            }
                        )
                    }

                    // One-time alert when the FFCS registration slot appears or changes
                    val pendingFfcs by AppState.pendingFfcsAlert.collectAsState()
                    if (pendingFfcs != null && currentScreen != Screen.LOGIN) {
                        AlertDialog(
                            onDismissRequest = { AppState.dismissFfcsAlert() },
                            containerColor = colors.surface,
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "FFCS Registration Coming Up",
                                        style = AmazeTheme.typography.subheading.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    )
                                    IconButton(onClick = { AppState.dismissFfcsAlert() }) {
                                        Icon(Icons.Rounded.Close, "Dismiss", tint = colors.textSecondary)
                                    }
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        "Your FFCS course registration slot has been scheduled:",
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(colors.accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.HowToReg,
                                                null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                pendingFfcs?.date.orEmpty(),
                                                style = AmazeTheme.typography.body.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary
                                                )
                                            )
                                            Text(
                                                buildString {
                                                    append("Registration slot: ")
                                                    if (!pendingFfcs?.fromTime.isNullOrBlank()) append(pendingFfcs?.fromTime)
                                                    if (!pendingFfcs?.toTime.isNullOrBlank()) {
                                                        if (isNotEmpty()) append(" - ")
                                                        append(pendingFfcs?.toTime)
                                                    }
                                                },
                                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "You'll be reminded a day before and on the day of registration.",
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { AppState.dismissFfcsAlert() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Got it", style = AmazeTheme.typography.smallLabel)
                                }
                            }
                        )
                    }

                    if (!isLoading && syncError != null && currentScreen != Screen.LOGIN) {
                        AlertDialog(
                            onDismissRequest = { AppState.dismissError() },
                            containerColor = colors.surface,
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Sync Error",
                                        style = AmazeTheme.typography.subheading.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.dangerText
                                        )
                                    )
                                    IconButton(onClick = { AppState.dismissError() }) {
                                        Icon(Icons.Rounded.Close, "Dismiss", tint = colors.textSecondary)
                                    }
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        "Some data failed to sync. You can copy the details below or report the issue to the developer.",
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 250.dp)
                                            .background(colors.background, RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = syncError ?: "",
                                            style = AmazeTheme.typography.smallLabel.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = AmazeTheme.fontSize.xs,
                                                color = colors.dangerText
                                            )
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(syncError ?: ""))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy", style = AmazeTheme.typography.smallLabel)
                                    }
                                    Button(
                                        onClick = {
                                            val report = "AmazeCC Error Report\n\n${syncError ?: ""}\n\n---\nApp: AmazeCC\nPlatform: Android"
                                            clipboardManager.setText(AnnotatedString(report))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.BugReport, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Report", style = AmazeTheme.typography.smallLabel)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}
