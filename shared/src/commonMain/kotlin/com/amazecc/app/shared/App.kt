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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import com.amazecc.app.shared.domain.overhaul.M3EDestination
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.screens.*
import com.amazecc.app.shared.ui.screens.m3e.*
import com.amazecc.app.shared.ui.screens.academics.*
import com.amazecc.app.shared.ui.components.MainTabPager
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.components.AppBackHandlerDismissesOverlaysOnEscape
import com.amazecc.app.shared.ui.components.PushPromptModal
import com.amazecc.app.shared.ui.screens.onboarding.OnboardingScreen
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.utils.requestNotificationPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * D-116: Screen still has no TIMETABLE constant, so that one surface keeps a named
 * LIFO overlay id. D-153 moved the academic report to a real Screen route, so it is
 * no longer an overlay and it keeps the bottom nav bar visible.
 */
private const val TIMETABLE_OVERLAY_ID = "m3e-timetable"

/** The one destination ↔ Screen mapping used by the canonical M3E scaffold. */
private fun screenForM3eDestination(destination: M3EDestination): Screen = when (destination) {
    M3EDestination.HOME -> Screen.HOME
    M3EDestination.ATTENDANCE -> Screen.ATTENDANCE
    M3EDestination.ACADEMICS -> Screen.ACADEMICS
    // TIMETABLE is a named overlay route; MORE is its visible home while open.
    M3EDestination.TIMETABLE, M3EDestination.MORE -> Screen.MORE
}

private fun m3eDestinationForScreen(screen: Screen): M3EDestination = when (screen) {
    Screen.HOME -> M3EDestination.HOME
    // D-138: the predictor keeps the Attendance tab lit in the bottom bar.
    Screen.ATTENDANCE, Screen.COURSE_ATTENDANCE, Screen.ATTENDANCE_PREDICTOR -> M3EDestination.ATTENDANCE
    Screen.ACADEMICS, Screen.CURRICULUM, Screen.COURSE_DETAIL, Screen.COURSE_DASHBOARD,
    Screen.GRADES, Screen.GPA_PREDICTOR, Screen.STUDENT_REPORT -> M3EDestination.ACADEMICS
    else -> M3EDestination.MORE
}

@Composable
fun App() {
    // D-057: "g" + letter shortcut sequence tracker (one per composition; resets on Escape).
    val sequenceTracker = remember { AppState.AppShortcutSequenceTracker() }
    val appScope = rememberCoroutineScope()

    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val customAccent by AppState.customAccentColor.collectAsState()
    val customPalette by AppState.customPalette.collectAsState()
    val currentScreen by AppState.currentScreen.collectAsState()
    val academicsStateHolder = rememberSaveableStateHolder()
    val academicsContent: @Composable () -> Unit = {
        academicsStateHolder.SaveableStateProvider(Screen.ACADEMICS.name) { M3EAcademicsScreen() }
    }
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.LOGIN) academicsStateHolder.removeState(Screen.ACADEMICS.name)
    }
    // Which home the Home tab renders. Read as a flow so switching the style in
    // Settings repaints the tab rather than waiting for the next navigation.
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

    // D-071: durable screen history — restore the newest entry on start (signed in only).
    LaunchedEffect(Unit) {
        AppState.restoreFromHistory()
    }

    // D-054: web HomeEntryController — a signed-out guest sees the landing surface first,
    // not the timed Login splash. One-shot so "Get Started"/"Try demo" can dismiss it.
    LaunchedEffect(Unit) {
        if (!SessionManager.isLoggedIn && AppState.currentScreen.value == Screen.SPLASH) {
            AppState.openGuestLanding()
        }
    }

    // D-070/D-086: the sync sheet registers as a LIFO overlay; Android back/Escape cancels
    // via AppState.closeTopOverlay() -> cancelSync(). The inline sheet's X sets
    // showSyncDialog=false but leaves isSyncing=true (so shouldShow would keep it visible);
    // that dismissal is treated as a cancel while a sweep is running. No park state exists.
    LaunchedEffect(Unit) {
        SyncEngine.showSyncDialog.collect { open ->
            if (open) {
                AppState.pushOverlay(AppState.SYNC_SHEET_OVERLAY_ID)
            } else {
                if (AppState.isSyncing.value || AppState.isLoading.value) AppState.cancelSync()
                AppState.popOverlay(AppState.SYNC_SHEET_OVERLAY_ID)
            }
        }
    }

    // D-056: every dismissible surface owns an overlay entry, so back closes the top one.
    val commandPaletteIsOpen by AppState.commandPaletteOpen.collectAsState()
    val shortcutsHelpIsOpen by AppState.shortcutsHelpOpen.collectAsState()
    LaunchedEffect(commandPaletteIsOpen) {
        if (commandPaletteIsOpen) AppState.pushOverlay(AppState.COMMAND_PALETTE_OVERLAY_ID)
        else AppState.popOverlay(AppState.COMMAND_PALETTE_OVERLAY_ID)
    }
    LaunchedEffect(shortcutsHelpIsOpen) {
        if (shortcutsHelpIsOpen) AppState.pushOverlay(AppState.SHORTCUTS_HELP_OVERLAY_ID)
        else AppState.popOverlay(AppState.SHORTCUTS_HELP_OVERLAY_ID)
    }

    // D-069: web parity PushPromptModal — auto-open 3s after mount when the 3-day dedup
    // window has elapsed and the user is signed in.
    LaunchedEffect(Unit) {
        if (!SessionManager.isLoggedIn) return@LaunchedEffect
        if (!AppState.pushPromptDue()) return@LaunchedEffect
        delay(AppState.PUSH_PROMPT_DELAY_MS)
        if (SessionManager.isLoggedIn && AppState.pushPromptDue()) AppState.openPushPrompt()
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
                    // D-057: web parity — "g"+letter, Alt+letter, Alt+B/P/T, "/", "?", Escape.
                    handleAppGlobalShortcut(event, sequenceTracker)
                },
                color = colors.background
            ) {
                // D-120/D-114: the TRUE root M3E provider. Every per-screen
                // AmazeM3ETheme() wrapper inherits this palette preset, and this is
                // where reduced motion is resolved before tabSwitchSpec is built.
                com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme(
                    isDark = com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge.isDarkThemeActive(),
                    palettePreset = com.amazecc.app.shared.ui.m3e.theme.palettePresetForAccent(currentAccent)
                ) {
                    val overlayStack by AppState.overlayStack.collectAsState()
                    val timetableOpen = TIMETABLE_OVERLAY_ID in overlayStack
                    val currentDestination = when {
                        timetableOpen -> M3EDestination.TIMETABLE
                        else -> m3eDestinationForScreen(currentScreen)
                    }
                    val tabSwitchSpec =
                        com.amazecc.app.shared.ui.m3e.theme.M3ETheme.motion.tabSwitchTransform()

                    Box(modifier = Modifier.fillMaxSize()) {
                        // D-119: one canonical scaffold owns system-bar/IME insets and
                        // switches the nav from a bottom bar to a rail on wide windows.
                        M3EAppScaffold(
                            currentDestination = currentDestination,
                            onDestinationSelected = { destination ->
                                when (destination) {
                                    M3EDestination.TIMETABLE -> {
                                        AppState.pushOverlay(TIMETABLE_OVERLAY_ID)
                                    }
                                    else -> {
                                        AppState.popOverlay(TIMETABLE_OVERLAY_ID)
                                        AppState.navigateTo(screenForM3eDestination(destination))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            navigationVisible = currentScreen != Screen.LOGIN &&
                                currentScreen != Screen.SPLASH &&
                                currentScreen != Screen.ONBOARDING &&
                                !timetableOpen
                        ) {
                    // Global Spotlight Search Overlay
                    val commandPaletteOpen by AppState.commandPaletteOpen.collectAsState()

                    if (commandPaletteOpen) {
                        com.amazecc.app.shared.ui.components.CommandPalette(
                            isOpen = commandPaletteOpen,
                            onClose = { AppState.closeCommandPalette() },
                            commands = com.amazecc.app.shared.ui.components.rememberGlobalCommands()
                        )
                    }

                    // Destination handlers are registered after the screen stack fallback.
                    AppBackHandler(enabled = true) { AppState.navigateBack() }

                    // Crossfade screen transitions. tabSwitchSpec is resolved in the
                    // root M3E theme provider above (D-114), not here.
                    Box(modifier = Modifier.fillMaxSize()) {
                        val tabList = AppState.tabScreens
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                if (targetState in tabList && initialState in tabList) {
                                    tabSwitchSpec
                                } else {
                                    fadeIn() togetherWith fadeOut()
                                }
                            }
                        ) { targetScreen ->
                        if (targetScreen in tabList) {
                            MainTabPager(
                                tabScreens = tabList,
                                currentScreen = targetScreen,
                                onScreenChange = { AppState.navigateTo(it) },
                                academicsContent = academicsContent
                            )
                        } else {
                            when (targetScreen) {
                                Screen.SPLASH -> M3ESplashScreen()
                                Screen.LOGIN -> M3ELoginScreen()
                                Screen.ONBOARDING -> OnboardingScreen()
                                Screen.PAYMENTS -> M3EPaymentsScreenHost()
                                Screen.HOSTEL -> M3EHostelScreen()
                                Screen.CABSHARE -> M3ECabShareHost()
                                Screen.TRANSPORT -> M3ETransportScreen()
                                Screen.EVENTS -> M3EEventHubScreen()
                                Screen.SETTINGS -> M3ESettingsScreen()
                                Screen.QBANK -> M3EQBankWorkspaceHost()
                                Screen.SOCIAL -> M3ESocialScreenHost()
                                // D-164: dedicated server-backed Friend Finder route (normal screen;
                                // keeps the bottom nav mounted, no LIFO overlay).
                                Screen.FRIEND_FINDER -> M3EFriendFinderHost()
                                // D-173: normal screen route — the host uses M3EPageShell chrome,
                                // so the app bar and bottom nav stay mounted (no LIFO overlay).
                                Screen.GEOTAG_PHOTO -> M3EGeotagPhotoHost()
                                Screen.FFCS_PLANNER -> M3EFfcsPlannerScreenHost()
                                Screen.FREE_CLASSROOMS -> M3EFreeClassroomsHost()
                                Screen.CALENDAR -> M3ECalendarScreenHost()
                                Screen.GRADES -> M3EGradesHistoryScreen()
                                Screen.GPA_PREDICTOR -> M3EMarksPredictorScreen()
                                Screen.ATTENDANCE_PREDICTOR -> M3EAttendancePredictorHost()
                                Screen.COURSE_DETAIL -> M3ECourseDetailScreen()
                                Screen.COURSE_ATTENDANCE -> M3ECourseAttendanceScreenHost()
                                Screen.CIRCULARS -> M3ECircularsScreenHost()
                                Screen.CURRICULUM -> M3ECurriculumScreen()
                                Screen.OD_TRACKER -> M3EODTrackerScreenHost()
                                Screen.COURSE_DASHBOARD -> M3ECourseDashboardScreenHost()
                                Screen.FACULTY_INFO -> M3EFacultyLiveHost()
                                Screen.FEEDBACK_STATUS -> M3EFeedbackStatusScreenHost()
                                Screen.FRESHER_WELCOME -> M3EFresherWelcomeScreenHost()
                                Screen.DOCUMENTS -> M3EDocumentsScreenHost()
                                Screen.CONTACTS -> M3EContactsHost()
                                Screen.ABOUT -> M3EAboutScreenHost()
                                Screen.CLUB_HUB -> M3EClubHubScreen()
                                Screen.CLUB_DETAIL -> M3EClubDetailScreenHost()
                                Screen.MOODLE -> M3EMoodleScreenHost()
                                Screen.TASKS -> M3ETasksScreen()
                                Screen.EXAM_SCHEDULE -> M3EExamScheduleHost()
                                Screen.CHANGELOG -> M3EChangelogScreenHost()
                                Screen.HALL_OF_FAME -> M3EHallOfFameScreenHost()
                                // D-153: normal route — the host owns its state and its
                                // M3E app bar; back follows the regular AppState back stack.
                                Screen.STUDENT_REPORT -> M3EStudentReportHost(
                                    onClose = { AppState.navigateBack(); Unit }
                                )
                                Screen.HOME -> M3EHomeScreen()
                                Screen.ATTENDANCE -> M3EAttendanceScreen()
                                Screen.ACADEMICS -> academicsContent()
                                Screen.LIBRARIES -> M3ELibrariesScreenHost()
                                Screen.PROFILE -> M3EProfileHost()
                                Screen.MORE -> M3EMoreHubScreen()
                            }
                        }
                    }
                    }

                    // D-054: guest landing for entry intents while signed out (web parity: GuestExperience).
                    val guestLandingOpen by AppState.guestLandingOpen.collectAsState()
                    if (guestLandingOpen && !SessionManager.isLoggedIn) {
                        GuestLanding()
                    }

                    // D-069: auto-opened push prompt (web parity: PushPromptModal).
                    val pushPromptOpen by AppState.pushPromptOpen.collectAsState()
                    if (pushPromptOpen) {
                        PushPromptModal(
                            onEnable = {
                                AppState.closePushPrompt()
                                appScope.launch {
                                    requestNotificationPermissions()
                                    AppState.rescheduleNotifications()
                                }
                            },
                            onDismiss = AppState::closePushPrompt
                        )
                    }

                    // D-057: shortcuts help sheet ("?").
                    val shortcutsHelpOpen by AppState.shortcutsHelpOpen.collectAsState()
                    ShortcutsHelpSheet(isOpen = shortcutsHelpOpen, onClose = AppState::closeShortcutsHelp)

                    // D-070: overlay-first back — the top overlay closes before any screen back.
                    val appLibraryOpen by AppState.isAppLibraryOpen.collectAsState()
                    AppBackHandler(enabled = overlayStack.isNotEmpty() || appLibraryOpen) {
                        if (AppState.closeTopOverlay()) return@AppBackHandler
                        if (AppState.isAppLibraryOpen.value) {
                            AppState.closeAppLibrary()
                            return@AppBackHandler
                        }
                    }

                    } // M3EAppScaffold content — navigation and insets owned by the scaffold.

                    // D-116: the timetable is the only remaining full-screen LIFO overlay.
                    // The academic report is a normal Screen route with the nav bar visible.
                    if (timetableOpen) {
                        M3ETimetableHost(
                            modifier = Modifier.fillMaxSize(),
                            onClose = { AppState.popOverlay(TIMETABLE_OVERLAY_ID) }
                        )
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

                    // One-time alert for newly added exam venue/seat entries from credentials
                    val pendingSeatAlerts by AppState.pendingExamSeatAlerts.collectAsState()
                    com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet(
                        isOpen = pendingSeatAlerts.isNotEmpty() && currentScreen != Screen.LOGIN,
                        onDismiss = { AppState.dismissExamSeatAlerts() },
                        title = "Exam Venue & Seat",
                        dismissable = true
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { AppState.dismissExamSeatAlerts() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Got it", style = AmazeTheme.typography.smallLabel)
                            }
                        }
                    }

                    // One-time alert when the FFCS registration slot appears or changes
                    val pendingFfcs by AppState.pendingFfcsAlert.collectAsState()
                    com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet(
                        isOpen = pendingFfcs != null && currentScreen != Screen.LOGIN,
                        onDismiss = { AppState.dismissFfcsAlert() },
                        title = "FFCS Registration Coming Up",
                        dismissable = true
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { AppState.dismissFfcsAlert() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Got it", style = AmazeTheme.typography.smallLabel)
                            }
                        }
                    }

                    com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet(
                        isOpen = !isLoading && syncError != null && currentScreen != Screen.LOGIN,
                        onDismiss = { AppState.dismissError() },
                        title = "Sync Error",
                        dismissable = true
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                            Spacer(Modifier.height(16.dp))
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
                    }
                }
            }
        }
    }
}
}

// ── D-057: global keyboard shortcuts (web parity: lib/shortcuts.ts + useGlobalShortcuts.ts) ──

/** Physical-key fallback for Alt+letter, where the modifier may suppress the unicode value. */
private val ALT_KEY_LETTERS: Map<Key, String> = mapOf(
    Key.A to "a", Key.B to "b", Key.C to "c", Key.D to "d", Key.E to "e", Key.F to "f",
    Key.G to "g", Key.H to "h", Key.I to "i", Key.J to "j", Key.K to "k", Key.L to "l",
    Key.M to "m", Key.N to "n", Key.O to "o", Key.P to "p", Key.Q to "q", Key.R to "r",
    Key.S to "s", Key.T to "t", Key.U to "u", Key.V to "v", Key.W to "w", Key.X to "x",
    Key.Y to "y", Key.Z to "z"
)

/** Web `letterOf(e)`: the letter a key event stands for, independent of dead keys. */
private fun letterOf(e: KeyEvent): String {
    val codePoint = e.utf16CodePoint
    val unicode = if (codePoint >= 32) codePoint.toChar().lowercase() else ""
    return if (unicode.isNotEmpty() && unicode[0].isLetter()) unicode
    else ALT_KEY_LETTERS[e.key] ?: unicode
}

private fun onAltLetter(letter: String) {
    when (letter) {
        // Web: Alt+B toggles CGPAHidden, Alt+P toggles privacyMode, Alt+T toggles the theme.
        "b" -> AppState.setCgpaHidden(!AppState.cgpaHidden.value)
        "p" -> AppState.togglePrivacyMode()
        "t" -> AppState.toggleAppTheme()
        else -> AppState.APP_SHORTCUTS
            .firstOrNull { it.alt && it.letter.equals(letter, ignoreCase = true) }
            ?.let { AppState.navigateTo(it.screen) }
    }
}

/**
 * D-057/D-070 — app-wide keys: "g" + letter (1.5s sequence), Alt+letter, Alt+B/P/T,
 * "/" (palette), "?" (shortcuts help), Ctrl/Cmd+K (palette) and Android-only Escape.
 */
private fun handleAppGlobalShortcut(
    event: KeyEvent,
    sequenceTracker: AppState.AppShortcutSequenceTracker
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val letter = letterOf(event)
    when {
        (event.isMetaPressed || event.isCtrlPressed) && letter == "k" -> {
            AppState.openCommandPalette()
            return true
        }
        event.key == Key.Escape -> {
            if (AppBackHandlerDismissesOverlaysOnEscape) {
                AppState.closeShortcutsHelp()
                sequenceTracker.reset()
                if (AppState.closeTopOverlay()) return true
                if (AppState.isAppLibraryOpen.value) {
                    AppState.closeAppLibrary()
                    return true
                }
                AppState.navigateBack()
                return true
            }
            // Desktop/iOS: no Escape dismissal of modal sheets — documented contract.
            return false
        }
        // Modified keys are never "typing" — these must run before the plain-letter guard.
        event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed -> {
            onAltLetter(letter)
            return true
        }
        letter == "/" -> {
            AppState.openCommandPalette()
            return true
        }
        letter == "?" -> {
            AppState.openShortcutsHelp()
            return true
        }
        // "g" + letter: only consume a plain letter that starts or completes a sequence,
        // and never while a modal input surface owns the keyboard.
        (letter == "g" || sequenceTracker.isPending()) &&
            !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed && !event.isShiftPressed &&
            !AppState.commandPaletteOpen.value && !AppState.shortcutsHelpOpen.value &&
            !AppState.guestLandingOpen.value -> {
            sequenceTracker.feed(letter)?.let { target ->
                AppState.navigateTo(target.screen)
                return true
            }
            if (letter == "g") return true // consume the prefix; a non-match falls through
            return false
        }
        // Never steal plain letters the user is typing.
        letter.isNotEmpty() && letter[0].isLetter() -> return false
    }
    return false
}

// ── D-054: guest landing surface (web parity: GuestExperience / HomeEntryController) ──

@Composable
private fun GuestLanding() {
    val colors = AmazeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            "Your VIT life, all in one place.",
            style = AmazeTheme.typography.subheading.copy(
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        )
        Text(
            "Attendance, marks, hostel and campus life — together.",
            style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                AppState.closeGuestLanding()
                AppState.clearPendingEntryIntent()
                AppState.navigateTo(Screen.LOGIN)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started", style = AmazeTheme.typography.smallLabel)
        }
        OutlinedButton(
            onClick = {
                AppState.closeGuestLanding()
                AppState.clearPendingEntryIntent()
                AppState.enterDemoMode()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try demo", style = AmazeTheme.typography.smallLabel)
        }
        OutlinedButton(
            onClick = { openExternalUrl("https://amazecc.app/ffcs/") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("FFCS Planner", style = AmazeTheme.typography.smallLabel)
        }
    }
}

// ── D-057: "?" shortcuts help sheet ──

@Composable
private fun ShortcutsHelpSheet(isOpen: Boolean, onClose: () -> Unit) {
    val colors = AmazeTheme.colors
    com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet(
        isOpen = isOpen,
        onDismiss = onClose,
        title = "Keyboard shortcuts",
        overlayId = AppState.SHORTCUTS_HELP_OVERLAY_ID
    ) {
            Text("g, then a letter", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            Text("Home, Attendance, Academics, Grades, OD hours, Events, Library, Payments, Tasks, Free classrooms, Settings.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Text("Alt + letter", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            Text("Quick jump: Home, Attendance, Academics, Grades, OD hours, Events, Library, Settings.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Text("Alt + B / P / T", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            Text("Toggle CGPA in stats / privacy mode / dark theme.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Text("?  /  /  /  Ctrl+K", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            Text("This help / command palette / command palette.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Text("Escape", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            Text("Close the top overlay or go back (Android).", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Got it", style = AmazeTheme.typography.smallLabel)
            }
    }
}
