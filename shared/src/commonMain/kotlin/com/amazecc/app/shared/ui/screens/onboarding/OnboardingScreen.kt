package com.amazecc.app.shared.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.AttendanceDisplayMode
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.SyncModule
import com.amazecc.app.shared.state.SyncScheduler
import com.amazecc.app.shared.state.SyncStatus
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.LocalNotificationPermissionManager
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EMotion
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingAccountState
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingAction
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingAutoSyncState
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingBadge
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingChoiceChip
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingCustomAccentSheet
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingFeature
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingPickerField
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingProfile
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingSectionLabel
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingSemesterSheet
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingState
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingSyncStep
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingSwitchRow
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingTimePickerSheet
import com.amazecc.app.shared.ui.screens.m3e.M3EOnboardingTone
import com.amazecc.app.shared.ui.screens.m3e.m3eScreenIconAndLabel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.ui.tooling.preview.Preview

private data class M3EOnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val phaseLabel: String
)

private val ONBOARDING_PHASES = listOf("Discover", "Plan", "Tell")

private val ONBOARDING_PAGES = listOf(
    M3EOnboardingPage("Welcome to AmazeCC", "Your all-in-one campus companion", Icons.Rounded.AutoAwesome, ONBOARDING_PHASES[0]),
    M3EOnboardingPage("Discover AmazeCC", "Everything in one place", Icons.Rounded.Explore, ONBOARDING_PHASES[0]),
    M3EOnboardingPage("Personalize", "Theme, accent & display preferences", Icons.Rounded.Palette, ONBOARDING_PHASES[1]),
    M3EOnboardingPage("Academic Preferences", "Semester & default calendar type", Icons.Rounded.CalendarMonth, ONBOARDING_PHASES[1]),
    M3EOnboardingPage("Residence & Alerts", "Notifications & residential info", Icons.Rounded.Notifications, ONBOARDING_PHASES[1]),
    M3EOnboardingPage("Choose Your Modules", "Pin your most-used sections", Icons.Rounded.Apps, ONBOARDING_PHASES[2]),
    M3EOnboardingPage("Link Accounts", "Optional: Moodle & Library login", Icons.Rounded.Link, ONBOARDING_PHASES[2]),
    M3EOnboardingPage("Auto Sync", "Keep data fresh on a schedule", Icons.Rounded.Timelapse, ONBOARDING_PHASES[2]),
    M3EOnboardingPage("You're All Set!", "Your data is loading in the background", Icons.Rounded.CheckCircle, ONBOARDING_PHASES[2])
)

private val PINNABLE_MODULES = listOf(
    Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE,
    Screen.PAYMENTS, Screen.CABSHARE, Screen.TRANSPORT, Screen.CALENDAR,
    Screen.FFCS_PLANNER, Screen.FREE_CLASSROOMS, Screen.QBANK, Screen.SOCIAL
)

/**
 * Public entry point. This is the ONLY composable that reads singletons or writes settings.
 * All nine inner pages are stateless and communicate exclusively through [M3EOnboardingState]
 * and [M3EOnboardingAction].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val isDark = AppState.theme.value == AppTheme.DARK || (AppState.theme.value == AppTheme.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())

    AmazeM3ETheme(isDark = isDark) {
        val notifPermissionManager = LocalNotificationPermissionManager.current
        val scope = rememberCoroutineScope()

        // ── Flow-backed reads live in the stateful wrapper only ──
        var currentPage by remember { mutableStateOf(0) }
        // D-051: system back walks the onboarding pages, then returns to login instead of exiting.
        com.amazecc.app.shared.ui.components.AppBackHandler(enabled = true) {
            if (currentPage > 0) currentPage -= 1
            else AppState.navigateTo(com.amazecc.app.shared.state.Screen.LOGIN)
        }
        val moduleStates by SyncEngine.moduleStates.collectAsState()
        val customAccent by AppState.customAccentColor.collectAsState()
        val semesterMap by AppState.semesterMap.collectAsState()
        val rawProfiles by SyncEngine.profiles.collectAsState()

        // Personalization
        var selectedTheme by remember { mutableStateOf(AppState.theme.value) }
        var selectedAccent by remember { mutableStateOf(AppState.accent.value) }
        var uiScale by remember { mutableStateOf(AppState.uiScale.value) }
        var cgpaHidden by remember { mutableStateOf(AppState.cgpaHidden.value) }
        var attendanceMode by remember { mutableStateOf(AppState.attendanceDisplayMode.value) }
        var hapticsEnabled by remember { mutableStateOf(AppState.hapticEnabled.value) }
        var animationsEnabled by remember { mutableStateOf(AppState.animationsEnabled.value) }
        var showCustomAccent by remember { mutableStateOf(false) }

        // Academic preferences
        var selectedSemester by remember { mutableStateOf(AppState.selectedSemester.value) }
        var selectedCalendar by remember { mutableStateOf(SettingsManager.getPreferredCalendar() ?: AmazeClient.calendarTypes.first().second) }
        var showSemesterSheet by remember { mutableStateOf(false) }

        // Residential & notifications
        var residentialStatus by remember { mutableStateOf(AppState.residentialStatus.value) }
        var classNotif by remember { mutableStateOf(SettingsManager.isNotifClassRemindersEnabled()) }
        var assignNotif by remember { mutableStateOf(SettingsManager.isNotifAssignmentRemindersEnabled()) }
        var taskNotif by remember { mutableStateOf(SettingsManager.isNotifTaskRemindersEnabled()) }
        var examNotif by remember { mutableStateOf(SettingsManager.isNotifExamRemindersEnabled()) }
        var offsetMinutes by remember { mutableStateOf(SettingsManager.getNotifOffsetMinutes()) }

        // Modules
        var selectedModules by remember { mutableStateOf(AppState.pinnedNavTabs.value) }

        // Optional accounts
        var moodleUser by remember { mutableStateOf("") }
        var moodlePass by remember { mutableStateOf("") }
        var libUser by remember { mutableStateOf("") }
        var libPass by remember { mutableStateOf("") }
        var moodleAccount by remember { mutableStateOf(M3EOnboardingAccountState()) }
        var libAccount by remember { mutableStateOf(M3EOnboardingAccountState()) }

        // Auto sync schedule
        var autoSyncEnabled by remember { mutableStateOf(SyncScheduler.isEnabled()) }
        var lightDaily by remember { mutableStateOf(SyncScheduler.isLightDaily()) }
        var lightInterval by remember { mutableStateOf(SyncScheduler.lightIntervalDays()) }
        var lightHour by remember { mutableStateOf(SyncScheduler.lightHour()) }
        var lightMinute by remember { mutableStateOf(SyncScheduler.lightMinute()) }
        var lightProfileId by remember { mutableStateOf(SyncScheduler.lightProfileId()) }
        var fullDay by remember { mutableStateOf(SyncScheduler.fullDayOfWeek()) }
        var fullHour by remember { mutableStateOf(SyncScheduler.fullHour()) }
        var fullMinute by remember { mutableStateOf(SyncScheduler.fullMinute()) }
        var fullProfileId by remember { mutableStateOf(SyncScheduler.fullProfileId()) }
        var showLightTimePicker by remember { mutableStateOf(false) }
        var showFullTimePicker by remember { mutableStateOf(false) }

        val syncSteps = remember(moduleStates) {
            moduleStates.filterKeys { it != SyncModule.EVENTS }
                .entries.map { (module, state) ->
                    M3EOnboardingSyncStep(
                        name = module.displayName,
                        status = when (state.status) {
                            SyncStatus.SUCCESS -> "done"
                            SyncStatus.ERROR -> "failed"
                            SyncStatus.LOADING -> "syncing"
                            SyncStatus.SKIPPED -> "skipped"
                            else -> "pending"
                        }
                    )
                }
        }
        val profiles = remember(rawProfiles) {
            rawProfiles.map { M3EOnboardingProfile(id = it.id, name = it.name) }
        }

        LaunchedEffect(Unit) {
            AppState.loadAllData()
        }

        val state = M3EOnboardingState(
            currentPage = currentPage,
            syncSteps = syncSteps,
            theme = selectedTheme,
            accent = selectedAccent,
            customAccentColor = customAccent,
            uiScale = uiScale,
            cgpaHidden = cgpaHidden,
            attendanceMode = attendanceMode,
            hapticsEnabled = hapticsEnabled,
            animationsEnabled = animationsEnabled,
            showCustomAccentSheet = showCustomAccent,
            semester = selectedSemester,
            semesterMap = semesterMap,
            calendar = selectedCalendar,
            calendarTypes = AmazeClient.calendarTypes,
            showSemesterSheet = showSemesterSheet,
            residentialStatus = residentialStatus,
            classNotif = classNotif,
            assignNotif = assignNotif,
            taskNotif = taskNotif,
            examNotif = examNotif,
            notifOffsetMinutes = offsetMinutes,
            availableModules = PINNABLE_MODULES,
            selectedModules = selectedModules,
            moodleUser = moodleUser,
            moodlePass = moodlePass,
            libraryUser = libUser,
            libraryPass = libPass,
            moodle = moodleAccount,
            library = libAccount,
            autoSync = M3EOnboardingAutoSyncState(
                enabled = autoSyncEnabled,
                lightDaily = lightDaily,
                lightIntervalDays = lightInterval,
                lightHour = lightHour,
                lightMinute = lightMinute,
                lightProfileId = lightProfileId,
                fullDayOfWeek = fullDay,
                fullHour = fullHour,
                fullMinute = fullMinute,
                fullProfileId = fullProfileId,
                profiles = profiles,
                showLightTimePicker = showLightTimePicker,
                showFullTimePicker = showFullTimePicker
            )
        )

        val onAction: (M3EOnboardingAction) -> Unit = { action ->
            when (action) {
                M3EOnboardingAction.Next -> if (currentPage < ONBOARDING_PAGES.lastIndex) currentPage++
                M3EOnboardingAction.Back -> if (currentPage > 0) currentPage--
                M3EOnboardingAction.Finish -> {
                    AppState.changeTheme(selectedTheme)
                    AppState.changeAccent(selectedAccent)
                    AppState.changeUiScale(uiScale)
                    AppState.setCgpaHidden(cgpaHidden)
                    AppState.setAttendanceDisplayMode(attendanceMode)
                    AppState.setHapticEnabled(hapticsEnabled)
                    AppState.setAnimationsEnabled(animationsEnabled)
                    AppState.setResidentialStatus(residentialStatus)
                    AppState.setPinnedNavTabs(selectedModules)
                    SettingsManager.savePreferredCalendar(selectedCalendar)
                    if (selectedSemester != AppState.selectedSemester.value) AppState.selectSemester(selectedSemester)
                    SettingsManager.setNotifClassRemindersEnabled(classNotif)
                    SettingsManager.setNotifAssignmentRemindersEnabled(assignNotif)
                    SettingsManager.setNotifTaskRemindersEnabled(taskNotif)
                    SettingsManager.setNotifExamRemindersEnabled(examNotif)
                    SettingsManager.setNotifOffsetMinutes(offsetMinutes)
                    SettingsManager.setOnboardingComplete(true)
                    AppState.navigateTo(Screen.HOME)
                }

                is M3EOnboardingAction.SetTheme -> {
                    selectedTheme = action.theme
                    AppState.changeTheme(action.theme)
                }
                is M3EOnboardingAction.SetAccent -> {
                    selectedAccent = action.accent
                    AppState.changeAccent(action.accent)
                }
                is M3EOnboardingAction.SetCustomAccent -> {
                    AppState.setCustomAccent(action.color)
                    selectedAccent = AccentTheme.CUSTOM
                    AppState.changeAccent(AccentTheme.CUSTOM)
                    showCustomAccent = false
                }
                is M3EOnboardingAction.SetCustomAccentSheet -> showCustomAccent = action.show
                is M3EOnboardingAction.SetUiScale -> {
                    uiScale = action.scale
                    AppState.changeUiScale(action.scale)
                }
                is M3EOnboardingAction.SetCgpaHidden -> {
                    cgpaHidden = action.hidden
                    AppState.setCgpaHidden(action.hidden)
                }
                is M3EOnboardingAction.SetAttendanceMode -> attendanceMode = action.mode
                is M3EOnboardingAction.SetHaptics -> {
                    hapticsEnabled = action.enabled
                    AppState.setHapticEnabled(action.enabled)
                }
                is M3EOnboardingAction.SetAnimations -> {
                    animationsEnabled = action.enabled
                    AppState.setAnimationsEnabled(action.enabled)
                }

                is M3EOnboardingAction.SetSemester -> selectedSemester = action.id
                is M3EOnboardingAction.SetSemesterSheet -> showSemesterSheet = action.show
                is M3EOnboardingAction.SetCalendar -> selectedCalendar = action.name

                is M3EOnboardingAction.SetResidentialStatus -> {
                    residentialStatus = action.status
                    AppState.setResidentialStatus(action.status)
                }
                is M3EOnboardingAction.SetClassNotif -> {
                    if (action.enabled) notifPermissionManager?.requestPermission()
                    classNotif = action.enabled
                }
                is M3EOnboardingAction.SetAssignNotif -> {
                    if (action.enabled) notifPermissionManager?.requestPermission()
                    assignNotif = action.enabled
                }
                is M3EOnboardingAction.SetTaskNotif -> {
                    if (action.enabled) notifPermissionManager?.requestPermission()
                    taskNotif = action.enabled
                }
                is M3EOnboardingAction.SetExamNotif -> {
                    if (action.enabled) notifPermissionManager?.requestPermission()
                    examNotif = action.enabled
                }
                is M3EOnboardingAction.SetNotifOffset -> offsetMinutes = action.minutes

                is M3EOnboardingAction.ToggleModule -> {
                    selectedModules = if (action.module in selectedModules) {
                        selectedModules - action.module
                    } else if (selectedModules.size < 4) {
                        selectedModules + action.module
                    } else {
                        selectedModules
                    }
                }
                is M3EOnboardingAction.SetMoodleUser -> moodleUser = action.value
                is M3EOnboardingAction.SetMoodlePass -> moodlePass = action.value
                is M3EOnboardingAction.SetLibraryUser -> libUser = action.value
                is M3EOnboardingAction.SetLibraryPass -> libPass = action.value
                M3EOnboardingAction.LinkMoodle -> {
                    if (moodleUser.isBlank() || moodlePass.isBlank()) {
                        moodleAccount = moodleAccount.copy(error = "Please fill in both fields")
                    } else {
                        moodleAccount = moodleAccount.copy(loading = true, error = null)
                        scope.launch {
                            AppState.saveMoodleCredentials(moodleUser, moodlePass)
                            moodleAccount = moodleAccount.copy(loading = false, linked = true)
                        }
                    }
                }
                M3EOnboardingAction.SkipMoodle -> {
                    moodleUser = ""
                    moodlePass = ""
                    moodleAccount = moodleAccount.copy(error = null)
                }
                M3EOnboardingAction.UnlinkMoodle -> moodleAccount = moodleAccount.copy(linked = false, error = null)
                M3EOnboardingAction.LinkLibrary -> {
                    if (libUser.isBlank() || libPass.isBlank()) {
                        libAccount = libAccount.copy(error = "Please fill in both fields")
                    } else {
                        libAccount = libAccount.copy(loading = true, error = null)
                        scope.launch {
                            AppState.saveLibraryCredentials(libUser, libPass)
                            libAccount = libAccount.copy(loading = false, linked = true)
                        }
                    }
                }
                M3EOnboardingAction.SkipLibrary -> {
                    libUser = ""
                    libPass = ""
                    libAccount = libAccount.copy(error = null)
                }
                M3EOnboardingAction.UnlinkLibrary -> libAccount = libAccount.copy(linked = false, error = null)

                is M3EOnboardingAction.SetAutoSyncEnabled -> {
                    SyncScheduler.setEnabled(action.enabled)
                    autoSyncEnabled = action.enabled
                }
                is M3EOnboardingAction.SetLightDaily -> {
                    SyncScheduler.setLightRecurrence(action.daily)
                    lightDaily = action.daily
                }
                is M3EOnboardingAction.SetLightInterval -> {
                    SyncScheduler.setLightIntervalDays(action.days)
                    lightInterval = action.days
                }
                is M3EOnboardingAction.SetLightTimePicker -> showLightTimePicker = action.show
                is M3EOnboardingAction.SetLightTime -> {
                    SyncScheduler.setLightTime(action.hour, action.minute)
                    lightHour = action.hour
                    lightMinute = action.minute
                    showLightTimePicker = false
                }
                is M3EOnboardingAction.SetLightProfile -> {
                    SyncScheduler.setLightProfileId(action.id)
                    lightProfileId = action.id
                }
                is M3EOnboardingAction.SetFullDay -> {
                    SyncScheduler.setFullDayOfWeek(action.day)
                    fullDay = action.day
                }
                is M3EOnboardingAction.SetFullTimePicker -> showFullTimePicker = action.show
                is M3EOnboardingAction.SetFullTime -> {
                    SyncScheduler.setFullTime(action.hour, action.minute)
                    fullHour = action.hour
                    fullMinute = action.minute
                    showFullTimePicker = false
                }
                is M3EOnboardingAction.SetFullProfile -> {
                    SyncScheduler.setFullProfileId(action.id)
                    fullProfileId = action.id
                }
            }
        }

        M3EOnboardingContent(state = state, onAction = onAction)
    }
}

// ─── Stateless M3E shell ───

@Composable
private fun M3EOnboardingContent(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLowest)
            .statusBarsPadding()
    ) {
        M3EOnboardingHeader(state = state)
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.currentPage,
                transitionSpec = { onboardingPageTransition(targetState, initialState) },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> M3EOnboardingWelcomePage(state = state, onAction = onAction)
                    1 -> M3EOnboardingDiscoverPage(state = state, onAction = onAction)
                    2 -> M3EOnboardingPersonalizePage(state = state, onAction = onAction)
                    3 -> M3EOnboardingAcademicPrefsPage(state = state, onAction = onAction)
                    4 -> M3EOnboardingResidentialNotifPage(state = state, onAction = onAction)
                    5 -> M3EOnboardingModulesPage(state = state, onAction = onAction)
                    6 -> M3EOnboardingAccountsPage(state = state, onAction = onAction)
                    7 -> M3EOnboardingAutoSyncPage(state = state, onAction = onAction)
                    8 -> M3EOnboardingCompletionPage(state = state, onAction = onAction)
                }
            }
        }
        M3EOnboardingFooter(state = state, onAction = onAction)
    }
}

/** M3E spring-driven page transition (spatial for position, effects for alpha). */
private fun onboardingPageTransition(target: Int, initial: Int): ContentTransform {
    val direction = if (target > initial) 1 else -1
    val enter = slideInHorizontally(
        initialOffsetX = { it / 4 * direction },
        animationSpec = M3EMotion.spatialDefault()
    ) + fadeIn(animationSpec = M3EMotion.effectsDefault())
    val exit = slideOutHorizontally(
        targetOffsetX = { -it / 4 * direction },
        animationSpec = M3EMotion.spatialDefault()
    ) + fadeOut(animationSpec = M3EMotion.effectsDefault())
    return enter togetherWith exit
}

@Composable
private fun M3EOnboardingHeader(state: M3EOnboardingState) {
    val colors = M3ETheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column {
            AnimatedContent(
                targetState = state.currentPage,
                transitionSpec = { onboardingPageTransition(targetState, initialState) }
            ) { index ->
                val p = ONBOARDING_PAGES[index]
                Column {
                    Text(
                        text = "PHASE ${ONBOARDING_PHASES.indexOf(p.phaseLabel) + 1} OF 3 · ${p.phaseLabel.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(p.icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                p.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = colors.onSurface
                            )
                            Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            M3EWavyLinearProgress(
                progress = (state.currentPage + 1).toFloat() / ONBOARDING_PAGES.size,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ONBOARDING_PAGES.indices.forEach { i ->
                    val isSelected = i == state.currentPage
                    val isPast = i <= state.currentPage
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = M3EMotion.spatialDefault(),
                        label = "OnboardingPageDot"
                    )
                    val color by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isPast) colors.primary else colors.outlineVariant,
                        animationSpec = M3EMotion.effectsDefault(),
                        label = "OnboardingPageDotColor"
                    )
                    Box(Modifier.size(width, 6.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}

@Composable
private fun M3EOnboardingFooter(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.currentPage > 0) {
            OutlinedButton(
                onClick = { onAction(M3EOnboardingAction.Back) },
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back", fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        if (state.currentPage < ONBOARDING_PAGES.lastIndex) {
            Button(
                onClick = { onAction(M3EOnboardingAction.Next) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
            ) {
                Text("Next", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        } else {
            Button(
                onClick = { onAction(M3EOnboardingAction.Finish) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
            ) {
                Icon(Icons.Rounded.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Started", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Page 0: Welcome ───
@Composable
private fun M3EOnboardingWelcomePage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        M3ESurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerLevel = SurfaceContainerLevel.LOW,
            cornerRadius = 28.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Your campus, fully synced",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    M3EOnboardingBadge("DISCOVER", colors.primary)
                }
                Text(
                    "We're setting up everything for you in the background — attendance, timetable, grades & more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SYNC PROGRESS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    val done = state.syncSteps.count { it.status == "done" }
                    Text(
                        "$done/${state.syncSteps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
                val done = state.syncSteps.count { it.status == "done" }
                val total = state.syncSteps.size
                M3EWavyLinearProgress(
                    progress = if (total > 0) done.toFloat() / total else 0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.syncSteps.forEach { step ->
                        val (icon, tint, label) = when (step.status) {
                            "done" -> Triple(Icons.Rounded.CheckCircle, colors.success, "Synced")
                            "failed" -> Triple(Icons.Rounded.Error, colors.error, "Failed")
                            "syncing" -> Triple(Icons.Rounded.Sync, colors.primary, "Syncing")
                            else -> Triple(Icons.Rounded.RadioButtonUnchecked, colors.outline, "Pending")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                step.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = tint)
                        }
                    }
                }
            }
        }
    }
}

// ─── Page 1: Discover ───
@Composable
private fun M3EOnboardingDiscoverPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val features = listOf(
        M3EOnboardingFeature("Course Hub", "Courses, attendance, marks & faculty", Icons.AutoMirrored.Rounded.MenuBook, M3EOnboardingTone.PRIMARY),
        M3EOnboardingFeature("Curriculum", "Credits & degree progress", Icons.Rounded.AccountTree, M3EOnboardingTone.SUCCESS),
        M3EOnboardingFeature("Attendance Log", "Per-course breakdown & predictor", Icons.Rounded.FactCheck, M3EOnboardingTone.SECONDARY),
        M3EOnboardingFeature("Exam Schedule", "Seat plans & countdowns", Icons.Rounded.EventSeat, M3EOnboardingTone.TERTIARY),
        M3EOnboardingFeature("Tasks", "Assignments, deadlines & reminders", Icons.Rounded.TaskAlt, M3EOnboardingTone.WARNING),
        M3EOnboardingFeature("CGPA Predictor", "What-if grade projections", Icons.Rounded.Insights, M3EOnboardingTone.PRIMARY),
        M3EOnboardingFeature("QBank", "Practice quizzes & tests", Icons.Rounded.Quiz, M3EOnboardingTone.SECONDARY),
        M3EOnboardingFeature("Campus Life", "Events, clubs, transport & more", Icons.Rounded.Celebration, M3EOnboardingTone.ERROR)
    )
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "Here's what AmazeCC brings to your campus life — everything syncs automatically from VTOP.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        features.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { feature ->
                    val (accent, container) = onboardingToneColors(feature.tone, colors)
                    M3ESurfaceCard(
                        modifier = Modifier.weight(1f),
                        containerLevel = SurfaceContainerLevel.STANDARD,
                        cornerRadius = 24.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(container),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(feature.icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            }
                            Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Text(feature.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun onboardingToneColors(tone: M3EOnboardingTone, colors: com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette): Pair<Color, Color> =
    when (tone) {
        M3EOnboardingTone.PRIMARY -> colors.primary to colors.primaryContainer
        M3EOnboardingTone.SECONDARY -> colors.secondary to colors.secondaryContainer
        M3EOnboardingTone.TERTIARY -> colors.tertiary to colors.tertiaryContainer
        M3EOnboardingTone.SUCCESS -> colors.success to colors.successContainer
        M3EOnboardingTone.WARNING -> colors.warning to colors.warningContainer
        M3EOnboardingTone.ERROR -> colors.error to colors.errorContainer
    }

// ─── Page 2: Personalization ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun M3EOnboardingPersonalizePage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        M3EOnboardingSectionLabel("COLOR THEME")
        M3EConnectedButtonGroup(
            items = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM),
            selectedItem = state.theme,
            onItemSelected = { onAction(M3EOnboardingAction.SetTheme(it)) },
            labelProvider = { it.name },
            modifier = Modifier.fillMaxWidth()
        )

        M3EOnboardingSectionLabel("ACCENT COLOR")
        val accents = listOf(
            AccentTheme.OCEAN to colors.primary,
            AccentTheme.FOREST to colors.success,
            AccentTheme.VERDANT to colors.tertiary,
            AccentTheme.LAVENDER to colors.secondary,
            AccentTheme.SUNSET to colors.warning,
            AccentTheme.CUSTOM to state.customAccentColor
        )
        accents.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (accent, swatch) ->
                    val isSelected = state.accent == accent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable {
                            if (accent == AccentTheme.CUSTOM) {
                                onAction(M3EOnboardingAction.SetCustomAccentSheet(true))
                            } else {
                                onAction(M3EOnboardingAction.SetAccent(accent))
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) colors.onSurface else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            accent.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        M3EOnboardingSectionLabel("FEEL")
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
            Column {
                M3EOnboardingSwitchRow(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on taps, card presses & navigation",
                    icon = Icons.Rounded.Vibration,
                    checked = state.hapticsEnabled,
                    onCheckedChange = { onAction(M3EOnboardingAction.SetHaptics(it)) },
                    tint = colors.primary
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                M3EOnboardingSwitchRow(
                    title = "Spring Animations",
                    subtitle = "Bouncy press-scale physics on interactive cards",
                    icon = Icons.Rounded.Animation,
                    checked = state.animationsEnabled,
                    onCheckedChange = { onAction(M3EOnboardingAction.SetAnimations(it)) },
                    tint = colors.tertiary
                )
            }
        }

        M3EOnboardingSectionLabel("UI SCALE")
        M3EConnectedButtonGroup(
            items = listOf(0.85f, 1.0f, 1.15f),
            selectedItem = state.uiScale,
            onItemSelected = { onAction(M3EOnboardingAction.SetUiScale(it)) },
            labelProvider = { when (it) { 0.85f -> "Small"; 1.0f -> "Default"; else -> "Large" } },
            modifier = Modifier.fillMaxWidth()
        )

        M3EOnboardingSectionLabel("DISPLAY")
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                M3EOnboardingSwitchRow(
                    title = "Hide CGPA",
                    subtitle = "Keep your CGPA private on dashboard",
                    icon = if (state.cgpaHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    checked = state.cgpaHidden,
                    onCheckedChange = { onAction(M3EOnboardingAction.SetCgpaHidden(it)) },
                    tint = colors.error
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                Text("Attendance Display", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                M3EConnectedButtonGroup(
                    items = listOf(AttendanceDisplayMode.PERCENTAGE, AttendanceDisplayMode.FRACTION),
                    selectedItem = state.attendanceMode,
                    onItemSelected = { onAction(M3EOnboardingAction.SetAttendanceMode(it)) },
                    labelProvider = { if (it == AttendanceDisplayMode.PERCENTAGE) "Percentage" else "Fraction" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (state.showCustomAccentSheet) {
        M3EOnboardingCustomAccentSheet(
            initial = state.customAccentColor,
            onSelected = { onAction(M3EOnboardingAction.SetCustomAccent(it)) },
            onDismiss = { onAction(M3EOnboardingAction.SetCustomAccentSheet(false)) }
        )
    }
}

// ─── Page 3: Academic Preferences ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun M3EOnboardingAcademicPrefsPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    val semIds = state.semesterMap.keys.toList().sortedDescending()
    val semesterOptions = semIds.map { it to (state.semesterMap[it] ?: it) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        M3EOnboardingSectionLabel("SEMESTER", colors.success)
        Text("Your data is synced for this semester. Change anytime in Settings", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        M3ESurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerLevel = SurfaceContainerLevel.STANDARD,
            onClick = { onAction(M3EOnboardingAction.SetSemesterSheet(true)) }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.successContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.success, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.semesterMap[state.semester] ?: state.semester,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(state.semester, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        if (semIds.isEmpty()) {
            Text("Semesters are still syncing — your current semester stays selected", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        M3EOnboardingSectionLabel("DEFAULT CALENDAR")
        Text("Which class-group calendar to show on the Calendar page", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        M3EConnectedButtonGroup(
            items = state.calendarTypes.map { it.second },
            selectedItem = state.calendar,
            onItemSelected = { onAction(M3EOnboardingAction.SetCalendar(it)) },
            labelProvider = { it },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (state.showSemesterSheet) {
        M3EOnboardingSemesterSheet(
            options = semesterOptions,
            selectedId = state.semester,
            onDismiss = { onAction(M3EOnboardingAction.SetSemesterSheet(false)) },
            onSelect = { onAction(M3EOnboardingAction.SetSemester(it)) }
        )
    }
}

// ─── Page 4: Residential & Notifications ───
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun M3EOnboardingResidentialNotifPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        M3EOnboardingSectionLabel("RESIDENTIAL STATUS")
        Text("Helps us show relevant campus info", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        M3EConnectedButtonGroup(
            items = listOf("hosteller", "dayscholar", "unknown"),
            selectedItem = state.residentialStatus,
            onItemSelected = { onAction(M3EOnboardingAction.SetResidentialStatus(it)) },
            labelProvider = { when (it) { "hosteller" -> "Hosteller"; "dayscholar" -> "Day Scholar"; else -> "Not Sure" } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        M3EOnboardingSectionLabel("NOTIFICATIONS & ALERTS")
        Text("We'll remind you so you never miss out", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
            Column {
                M3EOnboardingSwitchRow("Class Reminders", "Notify before each class starts", Icons.Rounded.Schedule, state.classNotif, { onAction(M3EOnboardingAction.SetClassNotif(it)) }, colors.primary)
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                M3EOnboardingSwitchRow("Assignment Reminders", "Remind before deadlines", Icons.AutoMirrored.Rounded.Assignment, state.assignNotif, { onAction(M3EOnboardingAction.SetAssignNotif(it)) }, colors.secondary)
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                M3EOnboardingSwitchRow("Task Reminders", "Remind about tasks on due date", Icons.Rounded.TaskAlt, state.taskNotif, { onAction(M3EOnboardingAction.SetTaskNotif(it)) }, colors.tertiary)
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                M3EOnboardingSwitchRow("Exam Reminders", "Remind 24h prior & at reporting time", Icons.Rounded.EventSeat, state.examNotif, { onAction(M3EOnboardingAction.SetExamNotif(it)) }, colors.warning)
            }
        }

        Spacer(Modifier.height(8.dp))
        M3EOnboardingSectionLabel("CLASS REMINDER LEAD TIME")
        Text("How many minutes early class alerts should trigger", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        M3EConnectedButtonGroup(
            items = listOf(5, 10, 15, 30, 60),
            selectedItem = state.notifOffsetMinutes,
            onItemSelected = { onAction(M3EOnboardingAction.SetNotifOffset(it)) },
            labelProvider = { "$it min" },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Page 5: Modules ───
@Composable
private fun M3EOnboardingModulesPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        M3EOnboardingSectionLabel("PINNED TABS")
        Spacer(Modifier.height(4.dp))
        Text("These appear in your bottom nav bar (max 4)", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        state.availableModules.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { module ->
                    val selected = module in state.selectedModules
                    val (icon, label) = m3eScreenIconAndLabel(module)
                    M3ESurfaceCard(
                        modifier = Modifier.weight(1f),
                        containerLevel = if (selected) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.STANDARD,
                        cornerRadius = 24.dp,
                        borderColor = if (selected) colors.primary else null,
                        onClick = { onAction(M3EOnboardingAction.ToggleModule(module)) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selected) colors.primary else colors.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
                            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─── Page 6: Accounts ───
@Composable
private fun M3EOnboardingAccountsPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        M3EOnboardingSectionLabel("LINK YOUR ACCOUNTS")
        Spacer(Modifier.height(4.dp))
        Text("Optional — you can always set these up later in Settings", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        M3EOnboardingAccountCard(
            title = "Moodle",
            subtitle = "Course materials & assignments",
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            tint = colors.secondary,
            account = state.moodle,
            user = state.moodleUser,
            pass = state.moodlePass,
            userLabel = "Username",
            onUserChange = { onAction(M3EOnboardingAction.SetMoodleUser(it)) },
            onPassChange = { onAction(M3EOnboardingAction.SetMoodlePass(it)) },
            onLink = { onAction(M3EOnboardingAction.LinkMoodle) },
            onSkip = { onAction(M3EOnboardingAction.SkipMoodle) },
            onUnlink = { onAction(M3EOnboardingAction.UnlinkMoodle) },
            linkedLabel = "Moodle linked"
        )
        Spacer(Modifier.height(16.dp))
        M3EOnboardingAccountCard(
            title = "Library",
            subtitle = "Borrowed books & due dates",
            icon = Icons.Rounded.LocalLibrary,
            tint = colors.tertiary,
            account = state.library,
            user = state.libraryUser,
            pass = state.libraryPass,
            userLabel = "Library ID",
            onUserChange = { onAction(M3EOnboardingAction.SetLibraryUser(it)) },
            onPassChange = { onAction(M3EOnboardingAction.SetLibraryPass(it)) },
            onLink = { onAction(M3EOnboardingAction.LinkLibrary) },
            onSkip = { onAction(M3EOnboardingAction.SkipLibrary) },
            onUnlink = { onAction(M3EOnboardingAction.UnlinkLibrary) },
            linkedLabel = "Library linked"
        )
    }
}

@Composable
private fun M3EOnboardingAccountCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    account: M3EOnboardingAccountState,
    user: String,
    pass: String,
    userLabel: String,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onLink: () -> Unit,
    onSkip: () -> Unit,
    onUnlink: () -> Unit,
    linkedLabel: String
) {
    val colors = M3ETheme.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outlineVariant,
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerLow,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        cursorColor = colors.primary
    )
    M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (!account.linked) {
                OutlinedTextField(
                    value = user,
                    onValueChange = onUserChange,
                    label = { Text(userLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = onPassChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (account.error != null) {
                    Text(account.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSkip, enabled = !account.loading) {
                        Text("Skip", fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    }
                    if (account.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
                    } else {
                        Button(
                            onClick = onLink,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                        ) {
                            Text("Link Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(linkedLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.success)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onUnlink) {
                        Text("Unlink", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.error)
                    }
                }
            }
        }
    }
}

// ─── Page 7: Auto Sync ───
private fun formatClock(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun M3EOnboardingAutoSyncPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val auto = state.autoSync
    val scroll = rememberScrollState()
    val weekdays = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        M3EOnboardingSectionLabel("AUTO SYNC")
        Text("Keep your data fresh in the background — light refreshes daily, a full sync runs weekly", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.STANDARD) {
            M3EOnboardingSwitchRow(
                title = "Auto Sync",
                subtitle = "Background sync on a schedule",
                icon = Icons.Rounded.Schedule,
                checked = auto.enabled,
                onCheckedChange = { onAction(M3EOnboardingAction.SetAutoSyncEnabled(it)) },
                tint = colors.primary
            )
        }
        if (!auto.enabled) {
            Text("Auto sync is turned off — nothing runs on a schedule.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }

        // Daily quick sync
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("DAILY QUICK SYNC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 1.2.sp)
                        Text("Refreshes the chosen profile once a day", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                M3EConnectedButtonGroup(
                    items = listOf(true, false),
                    selectedItem = auto.lightDaily,
                    onItemSelected = { onAction(M3EOnboardingAction.SetLightDaily(it)) },
                    labelProvider = { if (it) "Every Day" else "Every N Days" },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!auto.lightDaily) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Every", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        IconButton(onClick = { onAction(M3EOnboardingAction.SetLightInterval(auto.lightIntervalDays - 1)) }, enabled = auto.lightIntervalDays > 1) {
                            Icon(Icons.Rounded.Remove, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                        Text("${auto.lightIntervalDays} day${if (auto.lightIntervalDays > 1) "s" else ""}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        IconButton(onClick = { onAction(M3EOnboardingAction.SetLightInterval(auto.lightIntervalDays + 1)) }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                M3EOnboardingPickerField(
                    value = formatClock(auto.lightHour, auto.lightMinute),
                    label = "Sync time",
                    icon = Icons.Rounded.Schedule,
                    onClick = { onAction(M3EOnboardingAction.SetLightTimePicker(true)) },
                    modifier = Modifier.fillMaxWidth()
                )
                M3EOnboardingSectionLabel("PROFILE", colors.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(auto.profiles, key = { it.id }) { profile ->
                        M3EOnboardingChoiceChip(
                            label = profile.name,
                            selected = profile.id == auto.lightProfileId,
                            onClick = { onAction(M3EOnboardingAction.SetLightProfile(profile.id)) }
                        )
                    }
                }
            }
        }

        // Weekly full sync
        M3ESurfaceCard(modifier = Modifier.fillMaxWidth(), containerLevel = SurfaceContainerLevel.HIGH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.tertiaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("WEEKLY FULL SYNC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.tertiary, letterSpacing = 1.2.sp)
                        Text("Full refresh of everything, once a week", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                M3EConnectedButtonGroup(
                    items = weekdays.map { it.second },
                    selectedItem = auto.fullDayOfWeek,
                    onItemSelected = { onAction(M3EOnboardingAction.SetFullDay(it)) },
                    labelProvider = { value -> weekdays.firstOrNull { it.second == value }?.first ?: value.toString() },
                    modifier = Modifier.fillMaxWidth()
                )
                M3EOnboardingPickerField(
                    value = formatClock(auto.fullHour, auto.fullMinute),
                    label = "Sync time",
                    icon = Icons.Rounded.Schedule,
                    onClick = { onAction(M3EOnboardingAction.SetFullTimePicker(true)) },
                    modifier = Modifier.fillMaxWidth()
                )
                M3EOnboardingSectionLabel("PROFILE", colors.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(auto.profiles, key = { it.id }) { profile ->
                        M3EOnboardingChoiceChip(
                            label = profile.name,
                            selected = profile.id == auto.fullProfileId,
                            onClick = { onAction(M3EOnboardingAction.SetFullProfile(profile.id)) },
                            tint = colors.tertiary
                        )
                    }
                }
            }
        }
        Text(
            "Syncs also run when the app opens if a scheduled time was missed. You can tune all of this later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }

    if (auto.showLightTimePicker) {
        M3EOnboardingTimePickerSheet(
            title = "Daily Sync Time",
            initial = LocalTime(auto.lightHour, auto.lightMinute),
            onSelected = { onAction(M3EOnboardingAction.SetLightTime(it.hour, it.minute)) },
            onDismiss = { onAction(M3EOnboardingAction.SetLightTimePicker(false)) }
        )
    }
    if (auto.showFullTimePicker) {
        M3EOnboardingTimePickerSheet(
            title = "Weekly Sync Time",
            initial = LocalTime(auto.fullHour, auto.fullMinute),
            onSelected = { onAction(M3EOnboardingAction.SetFullTime(it.hour, it.minute)) },
            onDismiss = { onAction(M3EOnboardingAction.SetFullTimePicker(false)) }
        )
    }
}

// ─── Page 8: Completion ───
@Composable
private fun M3EOnboardingCompletionPage(
    state: M3EOnboardingState,
    onAction: (M3EOnboardingAction) -> Unit
) {
    val colors = M3ETheme.colors
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        M3ESurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerLevel = SurfaceContainerLevel.HIGH,
            cornerRadius = 28.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("You're all set!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = colors.onSurface, modifier = Modifier.weight(1f))
                    M3EOnboardingBadge("TELL", colors.tertiary)
                }
                Text("Your preferences are saved. Tap Get Started to dive in!", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    M3EOnboardingCompletionStat("Completed", state.syncSteps.count { it.status == "done" }.toString(), colors.primary)
                    M3EOnboardingCompletionStat("Failed", state.syncSteps.count { it.status == "failed" }.toString(), colors.error)
                    M3EOnboardingCompletionStat("In Progress", state.syncSteps.count { it.status == "syncing" }.toString(), colors.tertiary)
                }
                if (state.syncSteps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.syncSteps.forEach { step ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val tint = when (step.status) {
                                    "done" -> colors.success
                                    "failed" -> colors.error
                                    "syncing" -> colors.primary
                                    else -> colors.outline
                                }
                                Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
                                Spacer(Modifier.width(10.dp))
                                Text(step.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                                val label = when (step.status) { "done" -> "Synced"; "failed" -> "Failed"; "syncing" -> "Syncing"; else -> "Pending" }
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = tint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M3EOnboardingCompletionStat(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = M3ETheme.colors.onSurfaceVariant)
    }
}

// ─── IDE Previews ───
private fun previewOnboardingState(page: Int) = M3EOnboardingState(
    currentPage = page,
    syncSteps = listOf(
        M3EOnboardingSyncStep("Session", "done"),
        M3EOnboardingSyncStep("Attendance", "syncing"),
        M3EOnboardingSyncStep("Timetable", "pending")
    ),
    theme = AppTheme.SYSTEM,
    accent = AccentTheme.OCEAN,
    customAccentColor = Color(0xFF0B57D0),
    uiScale = 1.0f,
    cgpaHidden = false,
    attendanceMode = AttendanceDisplayMode.PERCENTAGE,
    hapticsEnabled = true,
    animationsEnabled = true,
    semester = "CH20262701",
    semesterMap = mapOf("CH20262701" to "Fall Semester 2026-27"),
    calendar = "Class Group 1",
    calendarTypes = listOf("1" to "Class Group 1", "2" to "Class Group 2"),
    residentialStatus = "hosteller",
    classNotif = true,
    assignNotif = true,
    taskNotif = false,
    examNotif = true,
    notifOffsetMinutes = 10,
    availableModules = PINNABLE_MODULES,
    selectedModules = listOf(Screen.ATTENDANCE, Screen.ACADEMICS),
    moodleUser = "",
    moodlePass = "",
    libraryUser = "",
    libraryPass = "",
    autoSync = M3EOnboardingAutoSyncState(
        enabled = true,
        lightDaily = true,
        lightIntervalDays = 1,
        lightHour = 8,
        lightMinute = 30,
        lightProfileId = "p1",
        fullDayOfWeek = 1,
        fullHour = 2,
        fullMinute = 0,
        fullProfileId = "p1",
        profiles = listOf(M3EOnboardingProfile("p1", "Main Profile"))
    )
)

@Preview
@Composable
private fun M3EOnboardingScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EOnboardingContent(state = previewOnboardingState(0), onAction = {})
    }
}

@Preview
@Composable
private fun M3EOnboardingScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EOnboardingContent(state = previewOnboardingState(2), onAction = {})
    }
}
