package com.amazecc.app.shared

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.screens.*
import com.amazecc.app.shared.ui.screens.libraries.LibrariesScreen
import com.amazecc.app.shared.ui.screens.transport.TransportScreen
import com.amazecc.app.shared.ui.screens.academics.FreeClassroomsScreen
import com.amazecc.app.shared.ui.screens.academics.*
import com.amazecc.app.shared.ui.screens.cabshare.CabShareScreen
import com.amazecc.app.shared.ui.screens.events.EventHubScreen
import com.amazecc.app.shared.ui.screens.hostel.HostelScreen
import com.amazecc.app.shared.ui.screens.moodle.MoodleScreen
import com.amazecc.app.shared.ui.screens.more.MoreScreen
import com.amazecc.app.shared.ui.screens.onboarding.OnboardingScreen
import com.amazecc.app.shared.ui.screens.payments.PaymentsScreen

@Composable
fun App() {
    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val currentScreen by AppState.currentScreen.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val syncError by AppState.error.collectAsState()

    val uiScale by AppState.uiScale.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Observe SyncEngine outside of AppState.init to avoid classloading deadlocks
    LaunchedEffect(Unit) {
        AppState.observeSyncEngine()
    }
    // Load cached data outside of AppState.init — many referenced flows are declared after the init block
    LaunchedEffect(Unit) {
        AppState.loadFromCache()
    }

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
                Scaffold(containerColor = colors.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Sync Notification Overlay
                    com.amazecc.app.shared.ui.components.SyncNotification()
                    // Sync Progress Popup (per-module detail + percentage)
                    com.amazecc.app.shared.ui.components.SyncProgressPopup(
                        onDismiss = { SyncEngine.setShowSyncDialog(false) },
                        onSaveOffline = { AppState.saveOffline() },
                        onSyncAll = {
                            SyncEngine.resetAllStates()
                            AppState.loadAllData()
                        }
                    )

                    // Crossfade screen transitions
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            }
                        ) { targetScreen ->
                        when (targetScreen) {
                            Screen.SPLASH -> SplashScreen()
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
                            Screen.SETTINGS -> com.amazecc.app.shared.ui.screens.settings.SettingsScreen()
                            Screen.QBANK -> QBankScreen()
                            Screen.SOCIAL -> SocialScreen()
                            Screen.FFCS_PLANNER -> FfcsPlannerScreen()
                            Screen.FREE_CLASSROOMS -> FreeClassroomsScreen { AppState.navigateTo(Screen.ACADEMICS) }
                            Screen.CALENDAR -> CalendarScreen(onBack = { AppState.navigateTo(Screen.ACADEMICS) })
                            Screen.GRADES -> GradesScreen()
                            Screen.GPA_PREDICTOR -> GPAPredictorScreen()
                            Screen.COURSE_DETAIL -> CourseDetailScreen { AppState.navigateTo(Screen.ACADEMICS) }
                            Screen.COURSE_ATTENDANCE -> CourseAttendanceScreen()
                            Screen.MAKEUP_COMPRE -> MakeupCompreScreen()
                            Screen.CIRCULARS -> CircularsScreen()
                            Screen.CURRICULUM -> CurriculumScreen()
                            Screen.OD_TRACKER -> ODTrackerScreen()
                            Screen.COURSE_DASHBOARD -> CourseDashboardScreen { AppState.navigateTo(Screen.ACADEMICS) }
                            Screen.MARKS_TIMELINE -> MarksTimelineScreen()
                            Screen.VITOL -> VitolScreen()
                            Screen.FACULTY_INFO -> FacultyInfoScreen()
                            Screen.COURSE_MANAGEMENT -> CourseManagementScreen()
                            Screen.PROJECTS -> ProjectsScreen()
                            Screen.WISHLIST -> WishlistScreen()
                            Screen.FEEDBACK_STATUS -> FeedbackStatusScreen()
                            Screen.FRESHER_WELCOME -> FresherWelcomeScreen()
                            Screen.DOCUMENTS -> DocumentsScreen()
                            Screen.ABOUT -> AboutScreen()
                            Screen.CLUB_HUB -> com.amazecc.app.shared.ui.screens.more.ClubHubScreen()
                            Screen.CLUB_DETAIL -> com.amazecc.app.shared.ui.screens.events.ClubDetailScreen()
                            Screen.MOODLE -> MoodleScreen()
                            Screen.TASKS -> TasksScreen()
                            else -> {}
                        }
                    }
                    }

                    // Floating top header overlay
                    if (currentScreen != Screen.LOGIN && currentScreen != Screen.SPLASH && currentScreen != Screen.HOME) {
                        val headerTitle by AppState.headerTitle.collectAsState()
                        if (headerTitle.isNotEmpty()) {
                            val headerDesc by AppState.headerDescription.collectAsState()
                            val headerBack by AppState.headerShowBack.collectAsState()
                            val headerSync by AppState.headerShowSync.collectAsState()
                            val headerRefresh by AppState.headerOnRefresh.collectAsState()
                            val headerModules by AppState.headerSyncModules.collectAsState()
                            com.amazecc.app.shared.ui.components.FloatingScreenHeader(
                                title = headerTitle,
                                description = headerDesc,
                                showBackButton = headerBack,
                                showSyncButton = headerSync,
                                onRefresh = headerRefresh,
                                syncModules = headerModules,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }

                    // Floating nav bar overlay
                    if (currentScreen != Screen.LOGIN && currentScreen != Screen.SPLASH) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            com.amazecc.app.shared.ui.components.BottomNavigationBar()
                        }
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
                                                fontSize = 11.sp,
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
