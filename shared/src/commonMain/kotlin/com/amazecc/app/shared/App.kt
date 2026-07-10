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

@Composable
fun App() {
    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val currentScreen by AppState.currentScreen.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val syncStatus by AppState.syncStatus.collectAsState()
    val syncError by AppState.error.collectAsState()

    AmazeTheme(
        appTheme = currentTheme,
        accentTheme = currentAccent
    ) {
        val colors = AmazeTheme.colors
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Crossfade screen transitions
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    }
                ) { targetScreen ->
                    when (targetScreen) {
                        Screen.LOGIN -> LoginScreen()
                        Screen.DASHBOARD -> DashboardScreen()
                        Screen.ATTENDANCE -> AttendanceScreen()
                        Screen.MARKS -> MarksGradesScreen()
                        Screen.TIMETABLE -> TimetableScreen()
                        Screen.HOSTEL -> HostelScreen()
                        Screen.PAYMENTS -> PaymentsScreen()
                        Screen.LIBRARY -> LibraryScreen()
                        Screen.TRANSPORT -> TransportScreen()
                        Screen.LMS -> LMSScreen()
                        Screen.PROFILE -> ProfileScreen()
                    }
                }

                // Global Loading Overlay
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
                            text = syncStatus ?: "Syncing data...",
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
