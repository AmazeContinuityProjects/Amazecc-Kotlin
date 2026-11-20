package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.*
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsScreen() {
    val colors = AmazeTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    // Deep link from the command palette: apply the requested settings section
    val settingsSectionTarget by AppState.settingsSectionTarget.collectAsState()

    AppBackHandler(enabled = currentSubScreen != null) {
        currentSubScreen = null
    }

    LaunchedEffect(settingsSectionTarget) {
        if (settingsSectionTarget != null) {
            currentSubScreen = SettingsSubScreen.entries.firstOrNull { it.name == settingsSectionTarget }
            AppState.consumeSettingsSectionTarget()
        }
    }

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
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0.dp)
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
                onBackOverride = if (currentSubScreen != null) { { currentSubScreen = null } } else null,
                enabledScreens = setOf(Screen.SETTINGS)
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

                AnimatedContent(
                    targetState = currentSubScreen,
                    transitionSpec = {
                        if (targetState == null) {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 3 } + fadeOut())
                        }
                    },
                    label = "settingsNav"
                ) { sub ->
                    if (sub == null) {
                        SettingsHub(
                            onOpenSubScreen = { currentSubScreen = it }
                        )
                    } else {
                        when (sub) {
                            SettingsSubScreen.APPEARANCE -> AppearancePage(onOpenSubScreen = { currentSubScreen = it })
                            SettingsSubScreen.PALETTE -> PaletteEditorScreen()
                            SettingsSubScreen.DISPLAY -> DisplayPage()
                            SettingsSubScreen.DASHBOARD -> DashboardPage()
                            SettingsSubScreen.BOTTOM_NAV -> BottomNavPage()
                            SettingsSubScreen.ACADEMICS -> AcademicsPage()
                            SettingsSubScreen.VTOP_SOURCE -> VtopSourcePage()
                            SettingsSubScreen.DATA_SYNC -> DataSyncPage(
                                snackbarHostState = snackbarHostState,
                                hasPermissionManager = notifPermissionManager != null,
                                requestPushToggle = { action ->
                                    pendingToggleAction.value = action
                                    showPushPrompt = true
                                }
                            )
                            SettingsSubScreen.CREDENTIALS -> CredentialsPage()
                            SettingsSubScreen.ABOUT -> AboutPage()
                            SettingsSubScreen.DANGER -> DangerPage(
                                onClearCache = { showClearCacheConfirm = true },
                                onLogout = { showLogoutConfirm = true }
                            )
                        }
                    }
                }

                FooterSpacer()
            }
        }
    }

    if (showClearCacheConfirm || showLogoutConfirm) {
        // This legacy screen is composed outside App.kt's per-screen M3E theme wrapper;
        // provide the palette so the shared sheet follows the app's light/dark setting.
        AmazeM3ETheme(isDark = M3EStateBridge.isDarkThemeActive()) {
            if (showClearCacheConfirm) {
                M3EDismissibleSheet(
                    isOpen = showClearCacheConfirm,
                    onDismiss = { showClearCacheConfirm = false },
                    title = "Clear Local Cache?"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("This will remove all cached attendance, marks, calendar, and module data from device storage.", color = colors.textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showClearCacheConfirm = false }) {
                                Text("Cancel", color = colors.textSecondary)
                            }
                            TextButton(onClick = {
                                SettingsManager.clearAll()
                                UserStore.clear()
                                showClearCacheConfirm = false
                            }) {
                                Text("Clear All", color = colors.danger, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (showLogoutConfirm) {
                M3EDismissibleSheet(
                    isOpen = showLogoutConfirm,
                    onDismiss = { showLogoutConfirm = false },
                    title = "Log Out Session?"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("You will be logged out of AmazeCC and returned to the login screen.", color = colors.textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showLogoutConfirm = false }) {
                                Text("Cancel", color = colors.textSecondary)
                            }
                            TextButton(onClick = { AppState.logout(); showLogoutConfirm = false }) {
                                Text("Log Out", color = colors.danger, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
