package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.amazecc.app.shared.state.AttendanceDisplayMode
import com.amazecc.app.shared.state.DashboardWidget
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AppTheme

/**
 * Immutable state for the Material 3 Expressive onboarding flow.
 *
 * The [com.amazecc.app.shared.ui.screens.onboarding.OnboardingScreen] wrapper is the only
 * place that talks to AppState / SettingsManager / SyncScheduler. Every inner page is
 * stateless and only receives a slice of this model plus an [onAction] emitter.
 */
data class M3EOnboardingState(
    val currentPage: Int = 0,
    val syncSteps: List<M3EOnboardingSyncStep> = emptyList(),

    // Personalization
    val theme: AppTheme,
    val accent: AccentTheme,
    val customAccentColor: Color,
    val uiScale: Float,
    val cgpaHidden: Boolean,
    val attendanceMode: AttendanceDisplayMode,
    val hapticsEnabled: Boolean,
    val animationsEnabled: Boolean,
    val showCustomAccentSheet: Boolean = false,

    // Academic preferences
    val semester: String,
    val semesterMap: Map<String, String>,
    val calendar: String,
    val calendarTypes: List<Pair<String, String>>,
    val showSemesterSheet: Boolean = false,

    // Residential & notifications
    val residentialStatus: String,
    val classNotif: Boolean,
    val assignNotif: Boolean,
    val taskNotif: Boolean,
    val examNotif: Boolean,
    val notifOffsetMinutes: Int,

    // Modules & home screen
    val availableModules: List<Screen>,
    val selectedModules: List<Screen>,
    val widgetOrder: List<DashboardWidget>,

    // Optional account linking
    val moodleUser: String,
    val moodlePass: String,
    val libraryUser: String,
    val libraryPass: String,
    val moodle: M3EOnboardingAccountState = M3EOnboardingAccountState(),
    val library: M3EOnboardingAccountState = M3EOnboardingAccountState(),

    // Background sync schedule
    val autoSync: M3EOnboardingAutoSyncState
)

/** A single onboarding sync row, mirrored from AppState.SyncStep. */
data class M3EOnboardingSyncStep(val name: String, val status: String)

/** M3E tonal role used to accent a discover feature tile. */
enum class M3EOnboardingTone { PRIMARY, SECONDARY, TERTIARY, SUCCESS, WARNING, ERROR }

/** A feature advertised on the Discover page. */
data class M3EOnboardingFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tone: M3EOnboardingTone
)

/** Credential-linking lifecycle for one external service. */
data class M3EOnboardingAccountState(
    val linked: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

/** Lightweight profile identity used by the auto-sync schedule pickers. */
data class M3EOnboardingProfile(val id: String, val name: String)

/** All settings owned by the Auto Sync page. */
data class M3EOnboardingAutoSyncState(
    val enabled: Boolean,
    val lightDaily: Boolean,
    val lightIntervalDays: Int,
    val lightHour: Int,
    val lightMinute: Int,
    val lightProfileId: String,
    val fullDayOfWeek: Int,
    val fullHour: Int,
    val fullMinute: Int,
    val fullProfileId: String,
    val profiles: List<M3EOnboardingProfile> = emptyList(),
    val showLightTimePicker: Boolean = false,
    val showFullTimePicker: Boolean = false
)

/** Every user intention emitted by a stateless onboarding page. */
sealed interface M3EOnboardingAction {
    data object Next : M3EOnboardingAction
    data object Back : M3EOnboardingAction
    data object Finish : M3EOnboardingAction

    // Personalization
    data class SetTheme(val theme: AppTheme) : M3EOnboardingAction
    data class SetAccent(val accent: AccentTheme) : M3EOnboardingAction
    data class SetCustomAccent(val color: Color) : M3EOnboardingAction
    data class SetCustomAccentSheet(val show: Boolean) : M3EOnboardingAction
    data class SetUiScale(val scale: Float) : M3EOnboardingAction
    data class SetCgpaHidden(val hidden: Boolean) : M3EOnboardingAction
    data class SetAttendanceMode(val mode: AttendanceDisplayMode) : M3EOnboardingAction
    data class SetHaptics(val enabled: Boolean) : M3EOnboardingAction
    data class SetAnimations(val enabled: Boolean) : M3EOnboardingAction

    // Academic preferences
    data class SetSemester(val id: String) : M3EOnboardingAction
    data class SetSemesterSheet(val show: Boolean) : M3EOnboardingAction
    data class SetCalendar(val name: String) : M3EOnboardingAction

    // Residential & notifications
    data class SetResidentialStatus(val status: String) : M3EOnboardingAction
    data class SetClassNotif(val enabled: Boolean) : M3EOnboardingAction
    data class SetAssignNotif(val enabled: Boolean) : M3EOnboardingAction
    data class SetTaskNotif(val enabled: Boolean) : M3EOnboardingAction
    data class SetExamNotif(val enabled: Boolean) : M3EOnboardingAction
    data class SetNotifOffset(val minutes: Int) : M3EOnboardingAction

    // Modules & widgets
    data class ToggleModule(val module: Screen) : M3EOnboardingAction
    data class ToggleWidget(val widget: DashboardWidget) : M3EOnboardingAction

    // Accounts
    data class SetMoodleUser(val value: String) : M3EOnboardingAction
    data class SetMoodlePass(val value: String) : M3EOnboardingAction
    data class SetLibraryUser(val value: String) : M3EOnboardingAction
    data class SetLibraryPass(val value: String) : M3EOnboardingAction
    data object LinkMoodle : M3EOnboardingAction
    data object SkipMoodle : M3EOnboardingAction
    data object UnlinkMoodle : M3EOnboardingAction
    data object LinkLibrary : M3EOnboardingAction
    data object SkipLibrary : M3EOnboardingAction
    data object UnlinkLibrary : M3EOnboardingAction

    // Auto sync
    data class SetAutoSyncEnabled(val enabled: Boolean) : M3EOnboardingAction
    data class SetLightDaily(val daily: Boolean) : M3EOnboardingAction
    data class SetLightInterval(val days: Int) : M3EOnboardingAction
    data class SetLightTimePicker(val show: Boolean) : M3EOnboardingAction
    data class SetLightTime(val hour: Int, val minute: Int) : M3EOnboardingAction
    data class SetLightProfile(val id: String) : M3EOnboardingAction
    data class SetFullDay(val day: Int) : M3EOnboardingAction
    data class SetFullTimePicker(val show: Boolean) : M3EOnboardingAction
    data class SetFullTime(val hour: Int, val minute: Int) : M3EOnboardingAction
    data class SetFullProfile(val id: String) : M3EOnboardingAction
}
