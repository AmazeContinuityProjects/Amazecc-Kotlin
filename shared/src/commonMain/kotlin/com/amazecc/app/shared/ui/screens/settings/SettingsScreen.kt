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
import androidx.compose.foundation.shape.RoundedCornerShape
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

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear Local Cache?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all cached attendance, marks, calendar, and module data from device storage.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.clearAll()
                    UserStore.clear()
                    showClearCacheConfirm = false
                }) {
                    Text("Clear All", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(AmazeTheme.radius.large)
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
            containerColor = colors.surface,
            shape = RoundedCornerShape(AmazeTheme.radius.large)
        )
    }
}
