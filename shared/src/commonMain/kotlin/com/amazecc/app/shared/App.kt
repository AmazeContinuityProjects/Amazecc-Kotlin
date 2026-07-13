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
import com.amazecc.app.shared.ui.screens.academics.*
import com.amazecc.app.shared.ui.screens.cabshare.CabShareScreen
import com.amazecc.app.shared.ui.screens.events.EventHubScreen
import com.amazecc.app.shared.ui.screens.hostel.HostelScreen
import com.amazecc.app.shared.ui.screens.more.MoreScreen
import com.amazecc.app.shared.ui.screens.onboarding.OnboardingScreen
import com.amazecc.app.shared.ui.screens.payments.PaymentsScreen

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
                        if (currentScreen != Screen.LOGIN && currentScreen != Screen.SPLASH) {
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
                            Screen.QBANK -> QBankScreen()
                            Screen.SOCIAL -> SocialScreen()
                            Screen.FFCS_PLANNER -> FfcsPlannerScreen()
                            Screen.FREE_CLASSROOMS -> FreeClassroomsScreen { AppState.navigateTo(Screen.ACADEMICS) }
                            Screen.CALENDAR -> CalendarScreen { AppState.navigateTo(Screen.ACADEMICS) }
                            Screen.GLASS_MORPH -> GlassMorphismScreen()
                            Screen.GRADES -> GradesScreen()
                            Screen.GPA_PREDICTOR -> GPAPredictorScreen()
                            Screen.COURSE_ATTENDANCE -> CourseAttendanceScreen()
                            Screen.ARREAR -> ArrearScreen()
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
                            Screen.ACTIVITY_TREE -> ActivityTreeScreen()
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
