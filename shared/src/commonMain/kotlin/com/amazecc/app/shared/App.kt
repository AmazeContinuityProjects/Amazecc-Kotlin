package com.amazecc.app.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.screens.*
import com.amazecc.app.shared.ui.screens.libraries.LibrariesScreen
import com.amazecc.app.shared.ui.screens.transport.TransportScreen
import com.amazecc.app.shared.ui.screens.academics.FreeClassroomsScreen

@Composable
fun App() {
    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val currentScreen by AppState.currentScreen.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    val syncError by AppState.error.collectAsState()

    val uiScale by AppState.uiScale.collectAsState()

    AmazeTheme(
        appTheme = currentTheme,
        accentTheme = currentAccent
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
                modifier = Modifier.fillMaxSize(),
                color = colors.background
            ) {
                Scaffold(
                    bottomBar = {
                        if (currentScreen != Screen.LOGIN) {
                            com.amazecc.app.shared.ui.components.BottomNavigationBar()
                        }
                    },
                containerColor = colors.background
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // Sync Notification Overlay
                    com.amazecc.app.shared.ui.components.SyncNotification()

                    // Crossfade screen transitions
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { targetScreen ->
                        when (targetScreen) {
                            Screen.LOGIN -> LoginScreen()
                            Screen.ONBOARDING -> OnboardingScreen()
                            Screen.HOME -> DashboardScreen()
                            Screen.ATTENDANCE -> AttendanceScreen()
                            Screen.ACADEMICS -> AcademicsScreen()
                            Screen.PAYMENTS -> PaymentsScreen()
                            Screen.LIBRARIES -> LibrariesScreen()
                            Screen.HOSTEL -> HostelScreen()
                            Screen.CABSHARE -> CabShareScreen()
                            Screen.TRANSPORT -> TransportScreen()
                            Screen.MORE -> MoreScreen()
                            Screen.PROFILE -> ProfileScreen()
                            Screen.EVENTS -> EventHubScreen()
                            Screen.QBANK -> QBankScreen()
                            Screen.SOCIAL -> SocialScreen()
                            Screen.FFCS_PLANNER -> FfcsPlannerScreen()
                            Screen.FREE_CLASSROOMS -> FreeClassroomsScreen(onBack = { AppState.navigateBack() })
                        }
                    }

                    // Global Loading Overlay (Legacy)
                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.background.copy(alpha = 0.6f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = syncStatus ?: "Loading...",
                                color = colors.textPrimary
                            )
                        }
                    }

                    if (!isLoading && syncError != null && currentScreen != Screen.LOGIN) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .background(colors.dangerSurface, MaterialTheme.shapes.small)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = syncError ?: "",
                                color = colors.dangerText
                            )
                        }
                    }
                }
            }
        }
    }
}
}
