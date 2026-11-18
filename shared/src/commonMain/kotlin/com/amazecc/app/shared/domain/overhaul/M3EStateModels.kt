package com.amazecc.app.shared.domain.overhaul

import kotlinx.datetime.LocalDate

/**
 * Heavily-typed immutable state models for AmazeCC's M3 Expressive overhaul.
 * Every screen accepts an immutable state instance and emits user action events.
 * Zero references to Android SDK, Ktor, or singletons.
 */

// ==========================================
// 1. HOME SCREEN IMMUTABLE STATE
// ==========================================

data class M3EUserIdentityState(
    val name: String,
    val regNumber: String,
    val programme: String,
    val school: String,
    val campus: String,
    val avatarUrl: String? = null
)

data class M3EWeekDay(
    val date: LocalDate,
    val dayName: String,     // "Mon", "Tue"
    val dayNumber: Int,      // 29
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasClasses: Boolean,
    val isHoliday: Boolean,
    val examCount: Int = 0,
    val holidayName: String? = null
)

data class M3EQuickStatsState(
    val attendancePercentage: Double,
    val cgpa: Double,
    val creditsEarned: Int,
    val safeBunksAvailable: Int,
    val attendanceStatusText: String
)

data class M3EClassTimelineItem(
    val courseCode: String,
    val courseTitle: String,
    val slot: String,
    val venue: String,
    val faculty: String,
    val startTime: String,
    val endTime: String,
    val isHappeningNow: Boolean = false,
    val isCompleted: Boolean = false
)

data class M3EInsightSlide(
    val title: String,
    val subtitle: String,
    val badge: String,
    val actionText: String? = null,
    val priorityColorHex: String? = null
)

data class M3EHomeState(
    val identity: M3EUserIdentityState,
    val weekDays: List<M3EWeekDay>,
    val selectedDayDate: LocalDate,
    val quickStats: M3EQuickStatsState,
    val todayClasses: List<M3EClassTimelineItem>,
    val insights: List<M3EInsightSlide>,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EHomeAction {
    data class SelectDay(val date: LocalDate) : M3EHomeAction
    data object RefreshData : M3EHomeAction
    data class ClickCourse(val courseCode: String) : M3EHomeAction
    data object OpenAttendanceHub : M3EHomeAction
    data object OpenTimetableHub : M3EHomeAction
    data object OpenAcademicsHub : M3EHomeAction
    data object DismissError : M3EHomeAction
}

// ==========================================
// 2. ATTENDANCE SCREEN IMMUTABLE STATE
// ==========================================

enum class M3ECardTone { GOOD, WARNING, CRITICAL }

data class M3ECourseAttendanceCardState(
    val courseCode: String,
    val courseTitle: String,
    val badge: String, // "TH", "LO", "EMB"
    val attendedClasses: Int,
    val totalClasses: Int,
    val percentage: Double,
    val tone: M3ECardTone,
    val faculty: String,
    val slot: String,
    val bunkSummary: String,
    val isEmbedded: Boolean = false,
    val theoryPct: Double? = null,
    val labPct: Double? = null
)

data class M3EBunkSummary(
    val totalBunkableClasses: Int,
    val criticalCoursesCount: Int,
    val warningCoursesCount: Int,
    val targetPercentage: Float = 75f
)

enum class AttendanceFilter { ALL, SAFE, AT_RISK, EMBEDDED }

data class M3EAttendanceState(
    val semesterName: String,
    val overallPercentage: Double,
    val bunkSummary: M3EBunkSummary,
    val courses: List<M3ECourseAttendanceCardState>,
    val activeFilter: AttendanceFilter = AttendanceFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

sealed interface M3EAttendanceAction {
    data class SetFilter(val filter: AttendanceFilter) : M3EAttendanceAction
    data class UpdateSearch(val query: String) : M3EAttendanceAction
    data class CourseClicked(val courseCode: String) : M3EAttendanceAction
    data object Refresh : M3EAttendanceAction
}

// ==========================================
// 3. NAVIGATION CHROME IMMUTABLE STATE
// ==========================================

enum class M3EDestination(val label: String) {
    HOME("Home"),
    ATTENDANCE("Attendance"),
    TIMETABLE("Timetable"),
    ACADEMICS("Academics"),
    MORE("More")
}

data class M3ENavigationState(
    val currentDestination: M3EDestination,
    val unreadNotificationsCount: Int = 0
)
